package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListProcessInstancesByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO.ReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchBinding;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Pointer;
import top.egon.cola.platform.tianji.process.server.execution.GovernedProcessStartStore.Provenance;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.CorrelationReceipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Internal process entry point; the caller must authenticate and authorize the supplied actor. */
public final class ProcessExecutionService {
    private static final int CORRELATION_PAGE_SIZE = ProcessInstanceCorrelationPageDTO.PAGE_SIZE;
    private static final Duration MAX_GOVERNED_PROOF_WINDOW = Duration.ofSeconds(30);
    private final SpringProcessEngineConfiguration engine;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final TransactionTemplate receiptRead;
    private final GovernedStartPilotProperties pilots;
    private final GovernedStartAuthority governedAuthority;
    private final GovernedProcessStartStore governed;
    private final TransactionTemplate governedWrite;

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator) {
        this(engine, dmn, validator, null);
    }

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator, GovernedStartAuthority governedAuthority) {
        this(engine, dmn, validator, new GovernedStartPilotProperties(List.of()), governedAuthority);
    }

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator, GovernedStartPilotProperties pilots,
                                   GovernedStartAuthority governedAuthority) {
        this.pilots = Objects.requireNonNull(pilots, "pilots");
        if (!pilots.pilots().isEmpty() && governedAuthority == null) {
            throw new IllegalStateException("Governed Process START pilots require an explicit authority");
        }
        this.engine = Objects.requireNonNull(engine, "engine");
        this.models = new VerifiedProcessModels(engine, dmn, validator);
        this.releases = new ProcessReleaseService(engine, dmn, validator);
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || engine.getRuntimeService() == null || engine.getHistoryService() == null
                || !engine.isDbHistoryUsed() || engine.getHistoryLevel() == null
                || !engine.getHistoryLevel().isAtLeast(HistoryLevel.INSTANCE) || engine.isAsyncHistoryEnabled()) {
            throw new IllegalArgumentException("Process execution requires synchronous instance history and a shared JDBC transaction manager");
        }
        this.receipts = new ProcessCommandReceiptStore(new JdbcTemplate(manager.getDataSource()), engine.getDatabaseSchema());
        this.write = new TransactionTemplate(manager);
        this.read = new TransactionTemplate(manager);
        this.read.setReadOnly(true);
        this.read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.receiptRead = new TransactionTemplate(manager);
        // Read-only is enforced on the primary by the receipt store, not a replica routing hint.
        this.receiptRead.setReadOnly(false);
        this.receiptRead.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.receiptRead.setTimeout(2);
        this.governedAuthority = governedAuthority;
        if (governedAuthority == null) {
            this.governed = null;
            this.governedWrite = null;
        } else {
            this.governed = new GovernedProcessStartStore(manager.getDataSource(), engine.getDatabaseSchema());
            this.governedWrite = new TransactionTemplate(manager);
            this.governedWrite.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            this.governedWrite.setTimeout(10);
        }
    }

    /** Allows the host to verify that a replacement execution bean enforces the configured pilots. */
    public boolean enforcesGovernedStartPilots(GovernedStartPilotProperties configured, GovernedStartAuthority authority) {
        return governed != null && pilots.equals(configured) && governedAuthority == authority;
    }

    public Snapshot start(ProcessActor actor, StartCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String channel = pilots.channelFor(actor.tenantId(), command.definitionKey());
        if (channel == null) return Objects.requireNonNull(write.execute(status -> startInTransaction(actor, command)));

        var governedCommand = new GovernedStartCommand(channel, command.definitionKey(), command.businessKey(),
                command.commandId(), command.correlationId(), command.variables());
        return executeGoverned(actor, governedCommand, command.releaseKey()).instance();
    }

    /** Starts a BPMN instance at the active channel revision and pins its provenance with the native instance. */
    public GovernedStartResult startGoverned(ProcessActor actor, GovernedStartCommand command) {
        var result = executeGoverned(actor, command, null);
        var pointer = Objects.requireNonNull(result.pointer());
        return new GovernedStartResult(result.instance(), pointer.channel(), pointer.revision(), pointer.candidateHash());
    }

    private StartOutcome executeGoverned(ProcessActor actor, GovernedStartCommand command, String expectedRelease) {
        if (governed == null) throw unavailable();
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint()) || command == null) throw governedDenied();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Governed Process START requires an independent transaction boundary");
        }
        // The HTTP-shaped command has no channel. An owned provenance row keeps its original scope after a remap.
        var history = expectedRelease == null ? Optional.<Provenance>empty() : governed.find(actor, command.commandId());
        var admitted = history.filter(source -> command.definitionKey().equals(source.pointer().definitionKey()))
                .map(source -> new GovernedStartCommand(source.pointer().channel(), command.definitionKey(),
                        command.businessKey(), command.commandId(), command.correlationId(), command.variables()))
                .orElse(command);
        var query = new StartAuthorizationQuery(actor, admitted.channel(), admitted.definitionKey(), admitted.commandId());
        var grant = governedAuthority.authorize(query);
        if (grant == null || !query.equals(grant.query())) throw governedDenied();
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.now());

        Pointer observed = null;
        WorkerRouteProof proof = null;
        boolean existing = expectedRelease == null ? governed.find(actor, command.commandId()).isPresent()
                : history.isPresent() || governed.hasReceipt(actor, command.commandId());
        if (!existing) {
            observed = governed.current(actor.tenantId(), admitted.channel()).orElse(null);
            if (observed != null && admitted.definitionKey().equals(observed.definitionKey())
                    && (expectedRelease == null || expectedRelease.equals(observed.releaseKey()))) {
                proof = governedAuthority.currentWorker(workerQuery(observed));
            }
        }
        var observedPointer = observed;
        var workerProof = proof;
        return Objects.requireNonNull(governedWrite.execute(status ->
                governedStartInTransaction(actor, admitted, expectedRelease, grant, observedPointer, workerProof, history)));
    }

    public Snapshot get(ProcessActor actor, String instanceId) {
        Objects.requireNonNull(actor, "actor");
        if (!validId(instanceId, 255)) throw invalid();
        return Objects.requireNonNull(read.execute(status -> snapshot(actor, receipts.startByInstance(actor, instanceId))));
    }

    public ProcessStartReceiptDTO getStartReceipt(ProcessActor actor, GetProcessStartReceiptQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        if (query == null) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw startReceiptUnavailable();
        }
        try {
            return Objects.requireNonNull(receiptRead.execute(status -> {
                receipts.beginReceiptObservation();
                var row = receipts.findAppliedStart(actor, query.commandId(), query.requestSha256())
                        .orElseThrow(ProcessResourceNotFoundException::new);
                if (row.recordedAt() == null || Thread.currentThread().isInterrupted()) throw startReceiptUnavailable();
                try {
                    return new ProcessStartReceiptDTO(row.instanceId(), ProcessStartReceiptDTO.StatusEnum.APPLIED,
                            row.recordedAt().toInstant());
                } catch (IllegalArgumentException corruptSource) {
                    throw startReceiptUnavailable();
                }
            }));
        } catch (DataAccessException | TransactionException failure) {
            throw startReceiptUnavailable();
        }
    }

    private static IllegalStateException startReceiptUnavailable() {
        return new IllegalStateException("Process START receipt evidence is unavailable");
    }

    public ProcessInstanceCorrelationPageDTO listByCorrelation(ProcessActor actor, ListProcessInstancesByCorrelationQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AccessDeniedException("Verified Process owner is required");
        }
        if (query == null) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive() || Thread.currentThread().isInterrupted()) {
            throw correlationUnavailable();
        }
        CorrelationCursor cursor = query.cursor() == null ? null : decodeCorrelationCursor(actor, query);
        try {
            return Objects.requireNonNull(receiptRead.execute(status -> correlationPage(actor, query, cursor)));
        } catch (DataAccessException | TransactionException failure) {
            throw correlationUnavailable();
        }
    }

    private ProcessInstanceCorrelationPageDTO correlationPage(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                              CorrelationCursor cursor) {
        Instant observedAt = receipts.beginReceiptObservation();
        if (query.toExclusive().isAfter(observedAt)) throw invalid();
        if (cursor != null && !receipts.hasCorrelationAnchor(actor, query.correlationId(), query.fromInclusive(),
                query.toExclusive(), cursor.submittedAt(), cursor.instanceId())) throw invalid();

        var rows = receipts.listCorrelationReceipts(actor, query.correlationId(), query.fromInclusive(), query.toExclusive(),
                cursor == null ? null : cursor.submittedAt(), cursor == null ? null : cursor.instanceId());
        validateCorrelationRows(rows, query, cursor);
        var boundary = rows.size() > CORRELATION_PAGE_SIZE ? rows.get(CORRELATION_PAGE_SIZE - 1) : null;
        String next = boundary == null ? null
                : encodeCorrelationCursor(actor, query, boundary.submittedAt(), boundary.instanceId());
        try {
            var items = rows.stream().limit(CORRELATION_PAGE_SIZE)
                    .map(row -> new ReceiptDTO(row.instanceId(), row.releaseVersion(), row.submittedAt())).toList();
            return new ProcessInstanceCorrelationPageDTO(query.correlationId(), observedAt, items, next);
        } catch (IllegalArgumentException corruptSource) {
            throw correlationUnavailable();
        }
    }

    private static void validateCorrelationRows(List<CorrelationReceipt> rows, ListProcessInstancesByCorrelationQuery query,
                                                CorrelationCursor cursor) {
        if (rows.size() > CORRELATION_PAGE_SIZE + 1 || Thread.currentThread().isInterrupted()) throw correlationUnavailable();
        Instant previousTime = cursor == null ? null : cursor.submittedAt();
        String previousId = cursor == null ? null : cursor.instanceId();
        var identities = new HashSet<String>();
        for (var row : rows) {
            if (row == null || !validCorrelationInstanceId(row.instanceId()) || row.releaseVersion() == null
                    || !row.releaseVersion().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") || row.submittedAt() == null
                    || row.submittedAt().getNano() % 1_000 != 0 || row.submittedAt().isBefore(query.fromInclusive())
                    || !row.submittedAt().isBefore(query.toExclusive()) || !identities.add(row.instanceId())) {
                throw correlationUnavailable();
            }
            if (previousTime != null && (row.submittedAt().isAfter(previousTime)
                    || row.submittedAt().equals(previousTime) && compareUtf8(row.instanceId(), previousId) >= 0)) {
                throw correlationUnavailable();
            }
            previousTime = row.submittedAt();
            previousId = row.instanceId();
        }
    }

    private static boolean validCorrelationInstanceId(String value) {
        return validId(value, 255) && !".".equals(value) && !"..".equals(value);
    }

    private static int compareUtf8(String left, String right) {
        return Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private static CorrelationCursor decodeCorrelationCursor(ProcessActor actor, ListProcessInstancesByCorrelationQuery query) {
        String token = query.cursor();
        try {
            var bytes = Base64.getUrlDecoder().decode(token.substring(3));
            if (!token.equals("v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))) throw invalid();
            var text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().split("\n", -1);
            if (text.length != 3 || !validCorrelationInstanceId(text[1])) throw invalid();
            Instant submittedAt = ListProcessInstancesByCorrelationQuery.parseUtc(text[0]);
            if (!submittedAt.toString().equals(text[0]) || submittedAt.isBefore(query.fromInclusive())
                    || !submittedAt.isBefore(query.toExclusive())
                    || !correlationCursorHash(actor, query, submittedAt, text[1]).equals(text[2])) throw invalid();
            return new CorrelationCursor(submittedAt, text[1]);
        } catch (IllegalArgumentException | CharacterCodingException invalidCursor) {
            throw invalid();
        }
    }

    private static String encodeCorrelationCursor(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                  Instant submittedAt, String instanceId) {
        var text = submittedAt + "\n" + instanceId + "\n" + correlationCursorHash(actor, query, submittedAt, instanceId);
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String correlationCursorHash(ProcessActor actor, ListProcessInstancesByCorrelationQuery query,
                                                Instant submittedAt, String instanceId) {
        return ProcessRequestDigest.hash(List.of("TIANJI_PROCESS_CORRELATION_CURSOR_V1", Long.toString(actor.tenantId()),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), query.correlationId(),
                query.fromInclusive().toString(), query.toExclusive().toString(), submittedAt.toString(), instanceId), Map.of());
    }

    private static IllegalStateException correlationUnavailable() {
        return new IllegalStateException("Process correlation evidence is unavailable");
    }

    private record CorrelationCursor(Instant submittedAt, String instanceId) { }

    private Snapshot startInTransaction(ProcessActor actor, StartCommand command) {
        Definition binding = explicitDefinition(actor, command.releaseKey(), command.definitionKey());
        Map<String, Object> inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String requestHash = resolvedStartHash(command.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), requestHash,
                command.releaseKey(), command.correlationId())) {
            var existing = receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
            if (!existing.requestHash().equals(requestHash)) throw conflict();
            if (!"APPLIED".equals(existing.state()) || existing.resourceId() == null
                    || !command.releaseKey().equals(existing.releaseKey())
                    || !command.correlationId().equals(existing.correlationId())) throw unavailable();
            return snapshot(actor, existing);
        }
        var applied = startClaimed(actor, binding, command.businessKey(), command.commandId(), inputs);
        return snapshot(actor, applied);
    }

    private Definition explicitDefinition(ProcessActor actor, String releaseKey, String definitionKey) {
        return releases.get(actor, releaseKey).definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.definitionKey().equals(definitionKey)).findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private StartOutcome governedStartInTransaction(ProcessActor actor, GovernedStartCommand command,
                                                     String expectedRelease, StartGrant grant, Pointer observed,
                                                     WorkerRouteProof proof, Optional<Provenance> history) {
        governed.begin(actor.tenantId());
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.transactionTime());
        var existing = receipts.lockIfPresent(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
        if (existing.isPresent()) {
            return replayOwnedStart(actor, command, expectedRelease, grant, existing.orElseThrow(), history);
        }
        if (history.isPresent()) throw unavailable();

        var pointer = governed.current(actor.tenantId(), command.channel()).orElseThrow(ProcessExecutionService::unavailable);
        if (!pointer.equals(observed)) throw new ProcessVersionConflictException();
        if (!command.definitionKey().equals(pointer.definitionKey())) throw unavailable();
        if (expectedRelease != null && !expectedRelease.equals(pointer.releaseKey())) throw new ProcessVersionConflictException();
        requireWorker(pointer, proof);
        var binding = governedDefinition(actor, pointer);
        var inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String resolvedHash = resolvedStartHash(pointer.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), resolvedHash,
                pointer.releaseKey(), command.correlationId())) {
            return replayOwnedStart(actor, command, expectedRelease, grant,
                    receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId()), history);
        }

        requireAdmission(grant, proof);
        var applied = startClaimed(actor, binding, command.businessKey(), command.commandId(), inputs);
        governed.append(actor, command.commandId(), command.correlationId(), pointer, governedHash(command, inputs),
                resolvedHash, grant.auditSha256(), applied.resourceId());
        var instance = checkedSnapshot(actor, command, applied);
        requireAdmission(grant, proof);
        return new StartOutcome(instance, pointer);
    }

    private StartOutcome replayOwnedStart(ProcessActor actor, GovernedStartCommand command, String expectedRelease,
                                          StartGrant grant, Receipt receipt, Optional<Provenance> history) {
        var provenance = governed.find(actor, command.commandId());
        if (history.isPresent() && !history.equals(provenance)) throw conflict();
        StartOutcome result;
        if (provenance.isPresent()) {
            result = replayGoverned(actor, command, expectedRelease, receipt, provenance.orElseThrow());
        } else {
            if (expectedRelease == null) throw conflict();
            result = new StartOutcome(replayExplicit(actor, command, expectedRelease, receipt), null);
        }
        requireCurrent(grant.observedAt(), grant.validUntil(), governed.transactionTime());
        return result;
    }

    private Snapshot replayExplicit(ProcessActor actor, GovernedStartCommand command, String expectedRelease, Receipt receipt) {
        if (!expectedRelease.equals(receipt.releaseKey()) || !command.correlationId().equals(receipt.correlationId())) {
            throw conflict();
        }
        if (!"APPLIED".equals(receipt.state()) || receipt.resourceId() == null) throw unavailable();
        var binding = explicitDefinition(actor, expectedRelease, command.definitionKey());
        var inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        if (!receipt.requestHash().equals(resolvedStartHash(expectedRelease, command.definitionKey(), command.businessKey(),
                command.correlationId(), inputs))) throw conflict();
        return checkedSnapshot(actor, command, receipt);
    }

    private StartOutcome replayGoverned(ProcessActor actor, GovernedStartCommand command, String expectedRelease,
                                        Receipt receipt, Provenance source) {
        var pointer = source.pointer();
        if (!command.channel().equals(pointer.channel()) || !command.definitionKey().equals(pointer.definitionKey())
                || !command.correlationId().equals(receipt.correlationId())
                || expectedRelease != null && !expectedRelease.equals(pointer.releaseKey())) throw conflict();
        if (!source.instanceId().equals(receipt.resourceId()) || !source.resolvedHash().equals(receipt.requestHash())
                || !pointer.releaseKey().equals(receipt.releaseKey())) throw unavailable();
        var inputs = ProcessValues.inputs(governedDefinition(actor, pointer).inputs(), command.variables());
        if (!source.governedHash().equals(governedHash(command, inputs))
                || !source.resolvedHash().equals(resolvedStartHash(pointer.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId(), inputs))) throw conflict();
        return new StartOutcome(checkedSnapshot(actor, command, receipt), pointer);
    }

    private Snapshot checkedSnapshot(ProcessActor actor, GovernedStartCommand command, Receipt receipt) {
        var instance = snapshot(actor, receipt);
        if (!command.definitionKey().equals(instance.definitionKey()) || !command.businessKey().equals(instance.businessKey())
                || !command.correlationId().equals(instance.correlationId())) throw unavailable();
        return instance;
    }

    private record StartOutcome(Snapshot instance, Pointer pointer) { }

    private Definition governedDefinition(ProcessActor actor, Pointer pointer) {
        var release = releases.get(actor, pointer.releaseKey());
        if (pointer.tenantId() != actor.tenantId() || !pointer.manifestHash().equals(release.manifestSha256())) throw unavailable();
        return release.definitions().stream().filter(definition -> definition.type() == Type.BPMN
                && pointer.definitionKey().equals(definition.definitionKey()) && pointer.modelHash().equals(definition.modelSha256()))
                .findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private void requireAdmission(StartGrant grant, WorkerRouteProof proof) {
        Instant now = governed.transactionTime();
        requireCurrent(grant.observedAt(), grant.validUntil(), now);
        requireCurrent(proof.observedAt(), proof.validUntil(), now);
    }

    private static void requireWorker(Pointer pointer, WorkerRouteProof proof) {
        if (proof == null || !workerQuery(pointer).equals(proof.query())) throw governedDenied();
        var binding = proof.binding();
        if (binding.actor().tenantId() != pointer.tenantId() || !binding.topic().equals(pointer.workerTopic())
                || !binding.schemaVersion().equals(pointer.workerSchemaVersion())
                || !pointer.workerBindingHash().equals(PlatformReleaseActivationService.workerFingerprint(
                        binding, proof.registrationSha256()))) throw governedDenied();
    }

    private static WorkerRouteQuery workerQuery(Pointer pointer) {
        return new WorkerRouteQuery(pointer.tenantId(), pointer.channel(), pointer.revision(), pointer.candidateHash(),
                pointer.workerTopic(), pointer.workerSchemaVersion());
    }

    private static void requireCurrent(Instant observedAt, Instant validUntil, Instant now) {
        if (observedAt.isAfter(now) || !now.isBefore(validUntil)
                || Duration.between(observedAt, validUntil).compareTo(MAX_GOVERNED_PROOF_WINDOW) > 0
                || Thread.currentThread().isInterrupted()) throw governedDenied();
    }

    private static AccessDeniedException governedDenied() {
        return new AccessDeniedException("Current governed Process START authority is required");
    }

    private static String governedHash(GovernedStartCommand command, Map<String, Object> inputs) {
        return ProcessRequestDigest.hash(List.of("TIANJI_GOVERNED_PROCESS_START_V1", command.channel(),
                command.definitionKey(), command.businessKey(), command.correlationId()), inputs);
    }

    private static String resolvedStartHash(String release, String definition, String business, String correlation,
                                            Map<String, Object> inputs) {
        return ProcessRequestDigest.hash(List.of(release, definition, business, correlation), inputs);
    }

    private Receipt startClaimed(ProcessActor actor, Definition binding, String businessKey, String commandId,
                                 Map<String, Object> inputs) {
        models.process(actor.tenantId(), binding);
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(binding.engineDefinitionId()).tenantId(Long.toString(actor.tenantId()))
                .businessKey(businessKey).variables(new HashMap<>(inputs)).start();
        if (instance == null || !binding.engineDefinitionId().equals(instance.getProcessDefinitionId())
                || !Long.toString(actor.tenantId()).equals(instance.getTenantId()) || !validId(instance.getId(), 255)) {
            throw new IllegalStateException("Process instance does not match its release");
        }
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.START, commandId, instance.getId(), null);
        return receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, commandId);
    }

    private Snapshot snapshot(ProcessActor actor, Receipt receipt) {
        if (!"APPLIED".equals(receipt.state()) || receipt.resourceId() == null) throw unavailable();
        Release release = releases.get(actor, receipt.releaseKey());
        String tenant = Long.toString(actor.tenantId());
        ProcessInstance current = engine.getRuntimeService().createProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (current != null) {
            Definition definition = releaseDefinition(release, current.getProcessDefinitionId());
            return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), current.getBusinessKey(),
                    receipt.correlationId(), current.isSuspended() ? State.SUSPENDED : State.ACTIVE, receipt.createdAt(), null);
        }
        HistoricProcessInstance history = engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(receipt.resourceId()).processInstanceTenantId(tenant).singleResult();
        if (history == null || history.getEndTime() == null) throw unavailable();
        Definition definition = releaseDefinition(release, history.getProcessDefinitionId());
        State state = history.getDeleteReason() == null ? State.COMPLETED : State.CANCELLED;
        return new Snapshot(receipt.resourceId(), receipt.releaseKey(), definition.definitionKey(), history.getBusinessKey(),
                receipt.correlationId(), state, receipt.createdAt(), history.getEndTime().toInstant());
    }

    private static Definition releaseDefinition(Release release, String definitionId) {
        return release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.engineDefinitionId().equals(definitionId)).findFirst().orElseThrow(ProcessExecutionService::unavailable);
    }

    private static boolean validId(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process request is invalid"); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }

    public enum State { ACTIVE, SUSPENDED, COMPLETED, CANCELLED }

    public record StartCommand(String releaseKey, String definitionKey, String businessKey, String commandId,
                               String correlationId, Map<String, Object> variables) {
        public StartCommand {
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
            variables = checkedStartVariables(definitionKey, businessKey, commandId, correlationId, variables);
        }
    }

    public record GovernedStartCommand(String channel, String definitionKey, String businessKey, String commandId,
                                       String correlationId, Map<String, Object> variables) {
        public GovernedStartCommand {
            if (channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")) throw invalid();
            variables = checkedStartVariables(definitionKey, businessKey, commandId, correlationId, variables);
        }
    }

    private static Map<String, Object> checkedStartVariables(String definitionKey, String businessKey, String commandId,
                                                             String correlationId, Map<String, Object> variables) {
        if (definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                || !validId(businessKey, 255) || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || variables == null) throw invalid();
        try { return Map.copyOf(variables); }
        catch (RuntimeException failure) { throw invalid(); }
    }

    public record StartAuthorizationQuery(ProcessActor actor, String channel, String definitionKey, String commandId) { }

    public record StartGrant(StartAuthorizationQuery query, String auditSha256, Instant observedAt, Instant validUntil) {
        public StartGrant {
            if (query == null || !GovernedProcessStartStore.hash(auditSha256) || observedAt == null || validUntil == null
                    || !validUntil.isAfter(observedAt)) throw invalid();
        }
    }

    public record WorkerRouteQuery(long tenantId, String channel, long revision, String candidateSha256,
                                   String topic, String schemaVersion) { }

    public record WorkerRouteProof(WorkerRouteQuery query, AgentDispatchBinding binding, String registrationSha256,
                                   Instant observedAt, Instant validUntil) {
        public WorkerRouteProof {
            if (query == null || binding == null || !GovernedProcessStartStore.hash(registrationSha256)
                    || observedAt == null || validUntil == null || !validUntil.isAfter(observedAt)) throw invalid();
        }
    }

    public interface GovernedStartAuthority {
        /** Grants the exact authenticated caller current access to start in the selected channel. */
        StartGrant authorize(StartAuthorizationQuery query);

        /**
         * Provides authoritative current evidence for one effective registration and dispatch binding
         * across all Worker actors able to acquire the tenant/topic/schema tuple. The authority must
         * keep earlier bindings usable for their existing instances and deny missing, ambiguous, or
         * unverifiable routes. A binding read scoped only to the selected actor is insufficient.
         */
        WorkerRouteProof currentWorker(WorkerRouteQuery query);
    }

    public record GovernedStartResult(Snapshot instance, String channel, long activationRevision, String candidateSha256) { }

    public record Snapshot(String instanceId, String releaseKey, String definitionKey, String businessKey,
                           String correlationId, State state, Instant submittedAt, Instant finishedAt) { }
}
