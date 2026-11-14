package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.ObservationVO;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartReceiptRead.TargetVO;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization.Permit;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.HistoricalRuntimeStartVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentSql.HistoricalStartReceiptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationSql.StoredReceiptVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Explicit host command. Records positive historical evidence while retaining the original execution facts. */
public final class ToolStartReceiptReconciliationService {
    private static final Duration WINDOW = Duration.ofSeconds(10);
    private static final int MAX_EVIDENCE_BYTES = 4096;
    private static final String STATUS = "APPLIED_OBSERVED";
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(4)
                            .maxNameLength(64).maxStringLength(255).maxNumberLength(24).build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final ToolActionIntentSql intents;
    private final ToolStartReceiptReconciliationSql receipts;
    private final HistoricalToolReceiptAuthorization authority;
    private final FixedBpmnStartReceiptRead process;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final TransactionTemplate reader;
    private final TransactionTemplate writer;
    private volatile boolean runtimeStorage;

    public ToolStartReceiptReconciliationService(DataSource source, AgentLedgerService ledger,
                                                  HistoricalToolReceiptAuthorization authority, FixedBpmnStartReceiptRead process) {
        this(source, new JdbcTemplate(source), new ToolActionIntentSql(source, ledger),
                new ToolStartReceiptReconciliationSql(source), authority, process, Clock.systemUTC(), System::nanoTime);
        requireNoTransaction();
        new AgentStorageVerifier(source).verify();
        runtimeStorage = new ToolExecutionStorageVerifier(source).verifyAndReturnProfile()
                == ToolExecutionStorageVerifier.SchemaProfile.V17;
    }

    ToolStartReceiptReconciliationService(DataSource source, JdbcTemplate jdbc, ToolActionIntentSql intents,
                                         ToolStartReceiptReconciliationSql receipts, HistoricalToolReceiptAuthorization authority,
                                         FixedBpmnStartReceiptRead process, Clock clock, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source, "source");
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.intents = Objects.requireNonNull(intents, "intents");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.process = Objects.requireNonNull(process, "process");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        var manager = new DataSourceTransactionManager(source);
        manager.setEnforceReadOnly(true);
        reader = new TransactionTemplate(manager);
        reader.setReadOnly(true);
        reader.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reader.setTimeout(2);
        writer = new TransactionTemplate(manager);
        writer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        writer.setTimeout(2);
    }

    public ReceiptVO observe(TrustedAgentActor owner, String dispatchRunId, UUID intentId) {
        requireNoTransaction();
        if (owner == null || "LEGACY".equals(owner.issuerFingerprint())) throw denied();
        var run = new GetAgentRunQuery(dispatchRunId);
        if (!run.runId().equals(dispatchRunId) || dispatchRunId.length() > 64 || intentId == null) {
            throw new IllegalArgumentException("Historical START receipt command is invalid");
        }

        var started = clock.instant();
        var window = new WindowVO(started, started.plus(WINDOW).truncatedTo(ChronoUnit.MICROS), nanoTime.getAsLong());
        if (runtimeStorage) {
            var runtime = transact(reader, window, window.deadline(),
                    () -> intents.historicalRuntimeStart(owner, dispatchRunId, intentId, window.deadline()));
            if (runtime.isPresent()) return observeRuntime(runtime.orElseThrow(), window);
        }
        var historical = transact(reader, window, window.deadline(), () -> find(owner, dispatchRunId, intentId, window.deadline()));
        var action = historical.action();
        var dispatch = action.dispatch();
        var read = new FixedBpmnStartReceiptRead.Request(historical.dispatchRun(), action.descriptor(), action.action(), dispatch.executor());
        var target = external(() -> process.bind(read));
        if (!intentId.toString().equals(target.query().commandId())) throw unavailable();
        var requestSha256 = requestHash(historical, target);
        var request = new HistoricalToolReceiptAuthorization.Request(owner, dispatchRunId, intentId, dispatch.executor(),
                requestSha256, action.revision(), historical.invocationRevision(), action.state(), window.deadline());

        var beforeRead = authorize(request, window);
        var observation = external(() -> process.read(read, target, beforeRead.expiresAt())).orElseThrow(
                () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, "Historical START effect is still unknown"));
        if (!target.equals(observation.target()) || !dispatch.executor().equals(observation.executor())) throw unavailable();
        var readUntil = earlier(beforeRead.expiresAt(), observation.expiresAt());
        check(window, readUntil);
        var permit = authorize(request, window);
        var deadline = earlier(readUntil, permit.expiresAt()).truncatedTo(ChronoUnit.MICROS);
        var evidenceSha256 = evidenceHash(requestSha256, observation);
        var evidence = encode(new EvidenceVO(1, STATUS,
                new SourceVO(action.revision(), historical.invocationRevision(), action.state()),
                new ProcessVO(target.query().requestSha256(), observation.receipt().instanceId(),
                        observation.receipt().recordedAt().toString(), evidenceSha256),
                new AuthorityVO(permit.checkId().toString(), permit.bindingSha256(), permit.policyVersion(),
                        permit.checkedAt().toString(), permit.expiresAt().toString(), window.deadline().toString())));

        var result = readCurrentReceipt(owner, dispatchRunId, intentId, historical, window, deadline)
                .map(stored -> validate(stored, historical, observation, requestSha256, evidenceSha256))
                .orElseGet(() -> {
                    if ("APPLIED".equals(action.state())) throw conflict();
                    return transact(writer, window, deadline, () -> receipts.append(historical, requestSha256, evidence, deadline)
                            .map(stored -> validate(stored, historical, observation, requestSha256, evidenceSha256)))
                            .orElseGet(() -> {
                                var winner = readCurrentReceipt(owner, dispatchRunId, intentId, historical, window, deadline)
                                        .orElseThrow(ToolStartReceiptReconciliationService::conflict);
                                return validate(winner, historical, observation, requestSha256, evidenceSha256);
                            });
                });
        check(window, deadline);
        return result;
    }

    private ReceiptVO observeRuntime(HistoricalRuntimeStartVO original, WindowVO window) {
        var row = original.action();
        var action = row.action();
        var dispatch = row.dispatch();
        var intentId = UUID.fromString(action.intentId());
        var read = new FixedBpmnStartReceiptRead.Request(original.dispatchRun(), row.descriptor(), action, dispatch.executor());
        var target = external(() -> process.bind(read));
        if (!intentId.toString().equals(target.query().commandId())) throw unavailable();
        String evidenceScope = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_START_RECEIPT_RECONCILE_V2",
                action.owner().identityFingerprint(), original.dispatchRun().snapshotSha256(), dispatch.runId(),
                dispatch.callId().toString(), Long.toString(dispatch.epoch()), dispatch.nonceSha256(), dispatch.executor().identityFingerprint(),
                action.requestSha256(), target.namespaceSha256(), ProcessStartReceiptClient.START_RECEIPT_RESOURCE_KEY,
                target.query().commandId(), target.query().requestSha256()), Map.of());
        var request = new HistoricalToolReceiptAuthorization.Request(action.owner(), dispatch.runId(), intentId, dispatch.executor(),
                evidenceScope, row.revision(), original.callRevision(), row.state(), window.deadline());
        var beforeRead = authorize(request, window);
        var observation = external(() -> process.read(read, target, beforeRead.expiresAt())).orElseThrow(
                () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SIDE_EFFECT_UNKNOWN, "Historical START effect is still unknown"));
        if (!target.equals(observation.target()) || !dispatch.executor().equals(observation.executor())) throw unavailable();
        var observed = observation.receipt();
        var readUntil = earlier(beforeRead.expiresAt(), observation.expiresAt());
        check(window, readUntil);
        var permit = authorize(request, window);
        var deadline = earlier(readUntil, permit.expiresAt()).truncatedTo(ChronoUnit.MICROS);
        var result = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", observed.instanceId()),
                observation.hostReceiptReference(), null, null).validated(row.descriptor());
        if ("APPLIED".equals(row.state())) {
            var current = transact(reader, window, deadline, () ->
                    intents.historicalRuntimeStart(action.owner(), dispatch.runId(), intentId, deadline).orElseThrow(
                            ToolStartReceiptReconciliationService::conflict));
            original.requireSame(current);
            if (!result.equals(row.outcome())) throw conflict();
            return new ReceiptVO(intentId, dispatch.runId(), observed.instanceId(), observed.recordedAt(), original.finishedAt());
        }
        var runtimeCalls = new AgentRuntimeCallReceiptStore(source, new AgentRuntimeBindingStore(jdbc));
        var technical = new RuntimeCallReceiptBO.OutcomeVO(RuntimeCallReceiptBO.State.SUCCEEDED,
                new ToolExecutionJsonCodec().runtimeWriteResultSha256(action.intentId(), action.requestSha256(), row.descriptor(), result),
                null, null, null);
        var exactDispatch = new RuntimeCallReceiptBO.DispatchVO(dispatch.epoch(), dispatch.nonceSha256(),
                dispatch.executor().issuerFingerprint(), dispatch.executor().subjectId());
        var finished = transact(writer, window, deadline, () -> runtimeCalls.reconcile(action.owner(), dispatch.runId(), dispatch.callId(),
                original.callRevision(), exactDispatch, technical, () -> {
                    check(window, deadline);
                    intents.completeRuntimeStart(original, result, deadline);
                }));
        return new ReceiptVO(intentId, dispatch.runId(), observed.instanceId(), observed.recordedAt(), finished.getFinishedAt());
    }

    private HistoricalStartReceiptVO find(TrustedAgentActor owner, String runId, UUID intent, Instant deadline) {
        return intents.historicalStartReceipt(owner, runId, intent, deadline).orElseThrow(
                () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Historical START source was not found"));
    }

    private Optional<StoredReceiptVO> readCurrentReceipt(TrustedAgentActor owner, String runId, UUID intent,
                                                          HistoricalStartReceiptVO expected, WindowVO window, Instant deadline) {
        return transact(reader, window, deadline, () -> {
            var stored = receipts.read(owner, runId, intent, expected.action().dispatch().epoch(), deadline);
            if (stored.isPresent()) {
                var current = intents.historicalStartReceipt(owner, runId, intent, deadline).orElseThrow(
                        ToolStartReceiptReconciliationService::conflict);
                if (!expected.actionStamp().equals(current.actionStamp()) || !expected.invocationStamp().equals(current.invocationStamp())
                        || !expected.originRunStamp().equals(current.originRunStamp()) || !expected.dispatchRunStamp().equals(current.dispatchRunStamp())) {
                    throw conflict();
                }
            }
            return stored;
        });
    }

    private Permit authorize(HistoricalToolReceiptAuthorization.Request request, WindowVO window) {
        check(window, request.deadlineAt());
        var result = external(() -> authority.authorize(request)).orElseThrow(ToolStartReceiptReconciliationService::denied);
        if (!request.bindingSha256().equals(result.bindingSha256()) || result.checkedAt().isBefore(window.startedAt())
                || result.checkedAt().isAfter(clock.instant()) || result.expiresAt().isAfter(request.deadlineAt())) throw unavailable();
        check(window, result.expiresAt());
        return result;
    }

    private <T> T transact(TransactionTemplate template, WindowVO window, Instant deadline, Supplier<T> operation) {
        check(window, deadline);
        try {
            T result = Objects.requireNonNull(template.execute(status -> {
                jdbc.execute("SET LOCAL statement_timeout = '2s'");
                jdbc.execute("SET LOCAL lock_timeout = '1s'");
                jdbc.execute("SET LOCAL TIME ZONE 'UTC'");
                jdbc.execute("SET LOCAL search_path TO pg_catalog, tianji_agent");
                String readOnly = template.isReadOnly() ? "on" : "off";
                if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT NOT pg_catalog.pg_is_in_recovery()
                          AND pg_catalog.current_setting('transaction_read_only')=?
                          AND pg_catalog.current_setting('server_encoding')='UTF8'
                          AND pg_catalog.current_setting('session_replication_role')='origin'
                        """, Boolean.class, readOnly))) throw unavailable();
                check(window, deadline);
                T value = operation.get();
                check(window, deadline);
                return value;
            }));
            check(window, deadline);
            return result;
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable();
        }
    }

    private void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(source)) throw unavailable();
    }

    private <T> T external(Supplier<T> operation) {
        requireNoTransaction();
        try {
            return Objects.requireNonNull(operation.get());
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private void check(WindowVO window, Instant deadline) {
        long elapsed = nanoTime.getAsLong() - window.startedNanos();
        if (Thread.currentThread().isInterrupted() || elapsed < 0 || elapsed >= WINDOW.toNanos()
                || !clock.instant().isBefore(deadline)) throw unavailable();
    }

    private static String requestHash(HistoricalStartReceiptVO source, TargetVO target) {
        var row = source.action();
        var action = row.action();
        var tool = action.tool();
        var dispatch = row.dispatch();
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_START_RECEIPT_RECONCILE_V1",
                action.owner().identityFingerprint(), row.originRunId(), row.originSnapshotSha256(),
                dispatch.runId(), source.dispatchRun().snapshotSha256(), dispatch.invocationId().toString(),
                Long.toString(dispatch.epoch()), dispatch.nonceSha256(), tool.key(), Long.toString(tool.version()),
                tool.contentSha256(), action.policy().bindingSha256(), action.requestSha256(), source.parametersSha256(),
                dispatch.executor().identityFingerprint(), target.namespaceSha256(),
                ProcessStartReceiptClient.START_RECEIPT_RESOURCE_KEY, target.query().commandId(), target.query().requestSha256()), Map.of());
    }

    private static String evidenceHash(String requestSha256, ObservationVO observation) {
        var receipt = observation.receipt();
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_START_RECEIPT_POSITIVE_EVIDENCE_V1", requestSha256,
                observation.executor().identityFingerprint(), observation.target().namespaceSha256(),
                receipt.status().name(), receipt.instanceId(), receipt.recordedAt().toString()), Map.of());
    }

    private static ReceiptVO validate(StoredReceiptVO stored, HistoricalStartReceiptVO historical,
                                      ObservationVO observation, String requestSha256, String evidenceSha256) {
        if (!requestSha256.equals(stored.requestSha256())) throw conflict();
        var evidence = decode(stored.encodedEvidence());
        var local = historical.action();
        var received = observation.receipt();
        var process = evidence.process();
        var original = evidence.source();
        if (!evidenceSha256.equals(process.evidenceSha256())
                || !observation.target().query().requestSha256().equals(process.requestSha256())
                || !received.instanceId().equals(process.instanceId()) || !received.recordedAt().equals(canonicalInstant(process.recordedAt()))
                || original.actionRevision() > local.revision() || original.invocationRevision() > historical.invocationRevision()) {
            throw conflict();
        }
        if ("APPLIED".equals(local.state()) && (local.outcome() == null
                || !observation.hostReceiptReference().equals(local.outcome().businessReceiptId())
                || !Map.of("instanceId", received.instanceId()).equals(local.outcome().output()))) throw conflict();
        var audit = evidence.authority();
        var originalBinding = new HistoricalToolReceiptAuthorization.Request(local.action().owner(), local.dispatch().runId(),
                UUID.fromString(local.action().intentId()), local.dispatch().executor(), requestSha256, original.actionRevision(),
                original.invocationRevision(), original.priorState(), canonicalInstant(audit.decisionDeadlineAt()));
        if (!originalBinding.bindingSha256().equals(audit.bindingSha256())
                || !stored.observedAt().isBefore(canonicalInstant(audit.expiresAt()))) throw unavailable();
        return new ReceiptVO(UUID.fromString(local.action().intentId()), local.dispatch().runId(),
                received.instanceId(), received.recordedAt(), stored.observedAt());
    }

    private static String encode(EvidenceVO value) {
        try {
            return bounded(JSON.writeValueAsString(value));
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static EvidenceVO decode(String encoded) {
        try {
            var value = JSON.readValue(bounded(encoded), EvidenceVO.class);
            if (value.schemaVersion() != 1 || !STATUS.equals(value.status()) || value.source().actionRevision() < 1
                    || value.source().invocationRevision() < 1 || !List.of("DISPATCHING", "UNKNOWN").contains(value.source().priorState())
                    || !hash(value.process().requestSha256()) || !hash(value.process().evidenceSha256())
                    || !UUID.fromString(value.authority().checkId()).toString().equals(value.authority().checkId())
                    || !hash(value.authority().bindingSha256()) || value.authority().policyVersion() < 1
                    || !canonicalInstant(value.authority().checkedAt()).isBefore(canonicalInstant(value.authority().expiresAt()))
                    || canonicalInstant(value.authority().expiresAt()).isAfter(canonicalInstant(value.authority().decisionDeadlineAt()))) {
                throw unavailable();
            }
            new ProcessStartReceiptDTO(value.process().instanceId(), ProcessStartReceiptDTO.StatusEnum.APPLIED,
                    canonicalInstant(value.process().recordedAt()));
            return value;
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static Instant canonicalInstant(String text) {
        try {
            var instant = Instant.parse(text);
            if (!instant.toString().equals(text)) throw unavailable();
            return instant;
        } catch (RuntimeException invalid) {
            throw unavailable();
        }
    }

    private static String bounded(String value) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_EVIDENCE_BYTES) throw unavailable();
        return value;
    }

    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static Instant earlier(Instant a, Instant b) { return a.isBefore(b) ? a : b; }

    public record ReceiptVO(UUID intentId, String dispatchRunId, String instanceId, Instant recordedAt, Instant observedAt) {
        public ReceiptVO {
            Objects.requireNonNull(intentId, "intentId");
            Objects.requireNonNull(recordedAt, "recordedAt");
            Objects.requireNonNull(observedAt, "observedAt");
            dispatchRunId = new GetAgentRunQuery(dispatchRunId).runId();
            instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        }

        @Override public String toString() { return "HistoricalStartReceipt[redacted]"; }
    }

    private record WindowVO(Instant startedAt, Instant deadline, long startedNanos) { }
    private record EvidenceVO(int schemaVersion, String status, SourceVO source, ProcessVO process, AuthorityVO authority) { }
    private record SourceVO(long actionRevision, long invocationRevision, String priorState) { }
    private record ProcessVO(String requestSha256, String instanceId, String recordedAt, String evidenceSha256) { }
    private record AuthorityVO(String checkId, String bindingSha256, long policyVersion, String checkedAt, String expiresAt,
                               String decisionDeadlineAt) { }

    private static AgentLedgerException denied() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current historical tool receipt authorization was denied");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Historical START receipt verification is unavailable");
    }

    private static AgentLedgerException conflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Historical START receipt evidence changed");
    }
}
