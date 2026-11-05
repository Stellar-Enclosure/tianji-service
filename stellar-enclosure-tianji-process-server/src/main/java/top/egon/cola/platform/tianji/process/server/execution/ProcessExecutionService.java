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
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.CorrelationReceipt;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Release;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal process entry point; the caller must authenticate and authorize the supplied actor. */
public final class ProcessExecutionService {
    private static final int CORRELATION_PAGE_SIZE = ProcessInstanceCorrelationPageDTO.PAGE_SIZE;
    private final SpringProcessEngineConfiguration engine;
    private final VerifiedProcessModels models;
    private final ProcessReleaseService releases;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate write;
    private final TransactionTemplate read;
    private final TransactionTemplate receiptRead;

    public ProcessExecutionService(SpringProcessEngineConfiguration engine, SpringDmnEngineConfiguration dmn,
                                   ProcessModelValidator validator) {
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
    }

    public Snapshot start(ProcessActor actor, StartCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        return Objects.requireNonNull(write.execute(status -> startInTransaction(actor, command)));
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
        Release release = releases.get(actor, command.releaseKey());
        Definition binding = release.definitions().stream().filter(item -> item.type() == Type.BPMN
                && item.definitionKey().equals(command.definitionKey())).findFirst().orElseThrow(ProcessExecutionService::unavailable);
        Map<String, Object> inputs = ProcessValues.inputs(binding.inputs(), command.variables());
        String requestHash = ProcessRequestDigest.hash(List.of(command.releaseKey(), command.definitionKey(),
                command.businessKey(), command.correlationId()), inputs);
        if (!receipts.insert(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), requestHash,
                command.releaseKey(), command.correlationId())) {
            var existing = receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId());
            if (!existing.requestHash().equals(requestHash)) throw conflict();
            if (!"APPLIED".equals(existing.state()) || existing.resourceId() == null
                    || !command.releaseKey().equals(existing.releaseKey())
                    || !command.correlationId().equals(existing.correlationId())) throw unavailable();
            return snapshot(actor, existing);
        }
        models.process(actor.tenantId(), binding);
        ProcessInstance instance = engine.getRuntimeService().createProcessInstanceBuilder()
                .processDefinitionId(binding.engineDefinitionId()).tenantId(Long.toString(actor.tenantId()))
                .businessKey(command.businessKey()).variables(new HashMap<>(inputs)).start();
        if (instance == null || !binding.engineDefinitionId().equals(instance.getProcessDefinitionId())
                || !Long.toString(actor.tenantId()).equals(instance.getTenantId()) || !validId(instance.getId(), 255)) {
            throw new IllegalStateException("Process instance does not match its release");
        }
        receipts.apply(actor, ProcessCommandReceiptStore.Kind.START, command.commandId(), instance.getId(), null);
        return snapshot(actor, receipts.lock(actor, ProcessCommandReceiptStore.Kind.START, command.commandId()));
    }

    private Snapshot snapshot(ProcessActor actor, ProcessCommandReceiptStore.Receipt receipt) {
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
            if (releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || !validId(businessKey, 255) || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || correlationId == null || !correlationId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || variables == null) throw invalid();
            try { variables = Map.copyOf(variables); }
            catch (RuntimeException failure) { throw invalid(); }
        }
    }

    public record Snapshot(String instanceId, String releaseKey, String definitionKey, String businessKey,
                           String correlationId, State state, Instant submittedAt, Instant finishedAt) { }
}
