package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Executes local JDBC business work and its replayable receipt in one owned transaction. */
@Slf4j
@Validated
public final class JdbcWorkerExecutionStore implements WorkerExecutionStore {
    private static final int MAX_RESULT_BYTES = 131_072;
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final String table;
    private final String intentTable;
    private final String schemaHistory;
    private final WorkerEffectReceiptStore effects;
    private final WorkerExecutionSchemaVerifier schemaVerifier;
    private final String sourceId;
    private final long tenantId;
    private final Duration maximumWorkTime;
    private final Duration deadlineSafetyMargin;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private volatile boolean intentReadableVerified;
    private volatile boolean nonTransactionalVerified;
    private volatile boolean effectsVerified;

    public JdbcWorkerExecutionStore(DataSource source, DataSourceTransactionManager manager, String schema, String sourceId,
                                    long tenantId, Duration maximumWorkTime, Duration deadlineSafetyMargin) {
        this(source, manager, schema, sourceId, tenantId, maximumWorkTime, deadlineSafetyMargin, null);
    }

    public JdbcWorkerExecutionStore(DataSource source, DataSourceTransactionManager manager, String schema, String sourceId,
                                    long tenantId, Duration maximumWorkTime, Duration deadlineSafetyMargin,
                                    WorkerEffectOutcomeResolver resolver) {
        if (source == null || manager == null || manager.getDataSource() != source || schema == null
                || !schema.matches("[a-z_][a-z0-9_]{0,62}") || sourceId == null
                || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || tenantId <= 0
                || maximumWorkTime == null || maximumWorkTime.compareTo(Duration.ofSeconds(1)) < 0
                || maximumWorkTime.compareTo(Duration.ofMinutes(15)) > 0 || deadlineSafetyMargin == null
                || deadlineSafetyMargin.compareTo(Duration.ofSeconds(1)) < 0 || deadlineSafetyMargin.compareTo(Duration.ofMinutes(15)) > 0
                || !wholeMillis(maximumWorkTime) || !wholeMillis(deadlineSafetyMargin)) throw invalid();
        this.source = source;
        this.manager = manager;
        this.jdbc = new JdbcTemplate(source);
        this.table = '"' + schema + "\".tianji_worker_execution_receipt";
        this.intentTable = '"' + schema + "\".tianji_worker_nontransactional_intent";
        this.schemaHistory = '"' + schema + "\".tianji_worker_schema_history";
        this.sourceId = sourceId;
        this.tenantId = tenantId;
        this.maximumWorkTime = maximumWorkTime;
        this.deadlineSafetyMargin = deadlineSafetyMargin;
        this.schemaVerifier = new WorkerExecutionSchemaVerifier(source, schema);
        schemaVerifier.verify();
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(Math.max(1, (int) maximumWorkTime.toSeconds()));
        effects = new WorkerEffectReceiptStore(jdbc, transaction, '"' + schema + "\".tianji_worker_effect_receipt", source, resolver);
    }

    @Override
    public WorkerExecutionReceipt executeAutomation(@Valid AutomationWorkerTaskDTO task,
                                                    @Valid AutomationWorkerHandlerRegistration registration) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(registration, "registration");
        if (TransactionSynchronizationManager.isActualTransactionActive() || manager.getDataSource() != source) {
            throw new IllegalStateException("Worker automation must own its business transaction");
        }
        var descriptor = registration.descriptor();
        var snapshot = snapshot(task, registration);
        snapshot.verifyContractHash(tenantId, sourceId);
        if (!descriptor.capabilityKey().equals(snapshot.getCapabilityKey())
                || descriptor.capabilityVersion() != snapshot.getCapabilityVersion()
                || !descriptor.inputSchemaSha256().equals(snapshot.getInputSchemaSha256())
                || !descriptor.outputSchemaSha256().equals(snapshot.getOutputSchemaSha256())
                || !descriptor.handlerVersion().equals(snapshot.getHandlerVersion())
                || descriptor.effectMode() != snapshot.getEffectMode()
                || !Objects.equals(descriptor.targetId(), snapshot.getTargetId())
                || !WorkerDigests.taskInputSha256(snapshot.getInput()).equals(snapshot.getInputSha256())
                || !WorkerDigests.automationEffectFingerprint(descriptor, snapshot.getInput()).equals(snapshot.getEffectFingerprint())) {
            throw invalid();
        }
        requireEffectsV3();
        var now = effects.now();
        var proof = snapshot.getEffectRecovery();
        if (proof != null && (proof.observedAt().isAfter(now) || !now.isBefore(proof.validUntil()))) {
            throw new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, "WORKER_RECOVERY_PROOF_EXPIRED");
        }
        var deadline = earliest(snapshot.getAttemptDeadlineAt(), snapshot.getLeaseExpiresAt());
        if (proof != null) deadline = earliest(deadline, proof.validUntil());
        deadline = earliest(deadline.minus(deadlineSafetyMargin), now.plus(maximumWorkTime));
        requireTime(now, deadline);
        var context = new AutomationWorkerExecutionContextVO(sourceId, tenantId, snapshot.getEffectKey(), snapshot.getInput(), deadline);
        return effects.execute(new WorkerEffectReceiptStore.EffectExecutionVO(registration, context, snapshot.getEffectFingerprint(),
                WorkerDigests.businessInputSha256(snapshot.getInput()), proof));
    }

    private synchronized void requireEffectsV3() {
        if (!effectsVerified) {
            schemaVerifier.verifyEffectsV3();
            effectsVerified = true;
        }
    }

    private static AutomationWorkerTaskDTO snapshot(AutomationWorkerTaskDTO task, AutomationWorkerHandlerRegistration registration) {
        var input = WorkerDigests.immutableTypedObject(task.getInput());
        var normalized = registration.normalizeInput(input);
        if (!Arrays.equals(ProcessTypedJson.canonicalBytes(input), ProcessTypedJson.canonicalBytes(normalized))) throw invalid();
        return new AutomationWorkerTaskDTO(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), task.getCapabilityKey(), task.getCapabilityVersion(), normalized, task.getInputSha256(),
                task.getEffectKey(), task.getAttemptDeadlineAt(), task.getLeaseExpiresAt(), task.getHandlerVersion(),
                task.getEffectMode(), task.getTargetId(), task.getInputSchemaSha256(), task.getOutputSchemaSha256(),
                task.getEffectFingerprint(), task.getEffectRecovery(), task.getTaskContractSha256());
    }

    private static Instant earliest(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    @Override public long tenantId() { return tenantId; }
    @Override public Duration deadlineSafetyMargin() { return deadlineSafetyMargin; }

    @Override
    public WorkerExecutionReceipt execute(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(handler, "handler");
        if (task.tenantId() != tenantId || task.effectSafety() != WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT
                || !task.contract().equals(descriptor.contract())) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Worker execution must own its business transaction");
        }
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(Math.max(1, (int) maximumWorkTime.toSeconds()));
        return Objects.requireNonNull(transaction.execute(status -> {
            Instant now = now();
            Instant leaseDeadline = task.leaseExpiresAt().minus(deadlineSafetyMargin);
            Instant workDeadline = now.plus(maximumWorkTime);
            Instant deadline = leaseDeadline.isBefore(workDeadline) ? leaseDeadline : workDeadline;
            requireTime(now, deadline);
            lockBusinessKey(task, deadline);
            String inputHash = inputHash(task);
            List<StoredReceipt> existing = receipts(task);
            if (existing.size() > 1) throw conflict();
            rejectConflictingIntent(task);
            if (!existing.isEmpty()) return replay(task, descriptor, inputHash, existing.getFirst());

            if (!TransactionSynchronizationManager.hasResource(source)) throw new IllegalStateException("Worker business transaction is not bound to its DataSource");
            WorkerBusinessResultDTO result = Objects.requireNonNull(handler.execute(WorkerExecutionContext.from(task, sourceId, deadline), jdbc), "worker result");
            descriptor.contract().validateOutput(result.output());
            if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                    || status.isRollbackOnly()) throw new IllegalStateException("Worker business transaction is not active");
            requireTime(now(), deadline);
            return insertReceipt(task, descriptor, inputHash, result, deadline);
        }));
    }

    @Override
    public void requireNonTransactional() {
        if (!nonTransactionalVerified) {
            schemaVerifier.verifyNonTransactional();
            intentReadableVerified = true;
            nonTransactionalVerified = true;
        }
    }

    private void requireIntentReadable() {
        if (!intentReadableVerified) {
            schemaVerifier.verifyIntentReadable();
            intentReadableVerified = true;
        }
    }

    private void rejectConflictingIntent(WorkerTaskDTO task) {
        Boolean installed = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + schemaHistory + " WHERE version='2' AND type='SQL')", Boolean.class);
        Boolean exists = jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, intentTable);
        if (!Objects.equals(installed, exists)) throw new IllegalStateException("Worker intent schema is inconsistent");
        if (Boolean.TRUE.equals(installed)) {
            requireIntentReadable();
            if (!intents(task).isEmpty()) throw conflict();
        }
    }

    @Override
    public WorkerExecutionReceipt executeNonTransactional(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor,
                                                           String targetId, NonTransactionalWorkerHandler handler) {
        Objects.requireNonNull(task, "task"); Objects.requireNonNull(descriptor, "descriptor"); Objects.requireNonNull(handler, "handler");
        if (task.tenantId() != tenantId || task.effectSafety() == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT
                || !task.contract().equals(descriptor.contract()) || targetId == null
                || !targetId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Worker external execution must not inherit a transaction");
        }
        requireNonTransactional();
        var transaction = new TransactionTemplate(manager);
        transaction.setTimeout(Math.max(1, (int) maximumWorkTime.toSeconds()));
        Prepared prepared = Objects.requireNonNull(transaction.execute(status -> prepare(task, descriptor, targetId)));
        if (prepared.receipt() != null) return prepared.receipt();

        String externalKey = "twex-" + WorkerDigests.hash(List.of("worker-external-key-v1", sourceId,
                Long.toString(tenantId), targetId, task.idempotencyKey()), Map.of());
        requireTime(now(), prepared.deadline());
        WorkerBusinessResultDTO result = Objects.requireNonNull(handler.execute(
                WorkerExecutionContext.from(task, sourceId, prepared.deadline()), externalKey), "worker result");
        descriptor.contract().validateOutput(result.output());
        return Objects.requireNonNull(transaction.execute(status -> finishNonTransactional(task, descriptor, targetId, prepared, result)));
    }

    private Prepared prepare(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, String targetId) {
        Instant current = now();
        Instant leaseDeadline = task.leaseExpiresAt().minus(deadlineSafetyMargin);
        Instant workDeadline = current.plus(maximumWorkTime);
        Instant deadline = leaseDeadline.isBefore(workDeadline) ? leaseDeadline : workDeadline;
        lockBusinessKey(task, deadline);
        String inputHash = inputHash(task);
        List<StoredIntent> intents = intents(task);
        List<StoredReceipt> existing = receipts(task);
        if (intents.size() > 1 || existing.size() > 1) throw conflict();
        if (intents.isEmpty()) {
            if (!existing.isEmpty()) throw conflict();
            jdbc.update("INSERT INTO " + intentTable + " (source_id,tenant_id,idempotency_key,effect_safety,target_id,handler_version,input_sha256,created_at) "
                            + "VALUES (?,?,?,?,?,?,?,clock_timestamp())", sourceId, tenantId, task.idempotencyKey(),
                    task.effectSafety().name(), targetId, descriptor.handlerVersion(), inputHash);
            intents = intents(task);
        }
        if (intents.size() != 1) throw conflict();
        requireIntent(intents.getFirst(), task, descriptor, targetId, inputHash, existing.isEmpty());
        requireTime(now(), deadline);
        return new Prepared(deadline, inputHash, existing.isEmpty() ? null : replay(task, descriptor, inputHash, existing.getFirst()));
    }

    private WorkerExecutionReceipt finishNonTransactional(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, String targetId,
                                                            Prepared prepared, WorkerBusinessResultDTO result) {
        lockBusinessKey(task, prepared.deadline());
        List<StoredIntent> intents = intents(task);
        if (intents.size() != 1) throw conflict();
        requireIntent(intents.getFirst(), task, descriptor, targetId, prepared.inputHash(), true);
        List<StoredReceipt> existing = receipts(task);
        if (existing.size() > 1) throw conflict();
        if (!existing.isEmpty()) {
            var replay = replay(task, descriptor, prepared.inputHash(), existing.getFirst());
            if (task.effectSafety() == WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT
                    && !resultHash(result).equals(resultHash(replay.result()))) throw conflict();
            return replay;
        }
        return insertReceipt(task, descriptor, prepared.inputHash(), result, prepared.deadline());
    }

    private void requireIntent(StoredIntent intent, WorkerTaskDTO task, WorkerHandlerDescriptor descriptor,
                               String targetId, String inputHash, boolean pending) {
        if (!task.effectSafety().name().equals(intent.effectSafety()) || !targetId.equals(intent.targetId())
                || !inputHash.equals(intent.inputSha256()) || pending && !descriptor.handlerVersion().equals(intent.handlerVersion())) {
            throw conflict();
        }
    }

    private void lockBusinessKey(WorkerTaskDTO task, Instant deadline) {
        Instant current = now();
        requireTime(current, deadline);
        long lockMillis = Math.max(1, Duration.between(current, deadline).toMillis());
        jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, lockMillis + "ms");
        String lockKey = WorkerDigests.hash(List.of("worker-business-lock-v1", sourceId,
                Long.toString(tenantId), task.idempotencyKey()), Map.of());
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "tianji:business-worker:" + lockKey);
        requireTime(now(), deadline);
    }

    private List<StoredReceipt> receipts(WorkerTaskDTO task) {
        return jdbc.query("SELECT task_id,occurrence_id,topic,schema_version,effect_safety,handler_version, "
                        + "input_sha256,result_sha256,business_receipt_id,output_json::text AS output_json,committed_at FROM " + table
                        + " WHERE source_id=? AND tenant_id=? AND idempotency_key=?",
                (rs, row) -> read(rs), sourceId, tenantId, task.idempotencyKey());
    }

    private List<StoredIntent> intents(WorkerTaskDTO task) {
        return jdbc.query("SELECT effect_safety,target_id,handler_version,input_sha256 FROM " + intentTable
                        + " WHERE source_id=? AND tenant_id=? AND idempotency_key=?",
                (rs, row) -> new StoredIntent(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                sourceId, tenantId, task.idempotencyKey());
    }

    private WorkerExecutionReceipt insertReceipt(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, String inputHash,
                                                   WorkerBusinessResultDTO result, Instant deadline) {
        byte[] output;
        try { output = mapper.writeValueAsBytes(result.output()); }
        catch (RuntimeException invalidResult) { throw invalid(); }
        if (output.length > MAX_RESULT_BYTES) throw invalid();
        List<Instant> committed = jdbc.query("INSERT INTO " + table + " (source_id,tenant_id,idempotency_key,task_id,occurrence_id, "
                        + "topic,schema_version,effect_safety,handler_version,input_sha256,result_sha256,business_receipt_id,output_json,committed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),clock_timestamp()) RETURNING committed_at",
                (rs, row) -> rs.getTimestamp("committed_at").toInstant(), sourceId, tenantId, task.idempotencyKey(), task.taskId(),
                task.activityOccurrenceId(), task.topic(), task.schemaVersion(), task.effectSafety().name(), descriptor.handlerVersion(), inputHash,
                resultHash(result), result.businessReceiptId(), new String(output, StandardCharsets.UTF_8));
        if (committed.size() != 1) throw new IllegalStateException("Worker business receipt could not be recorded");
        requireTime(now(), deadline);
        return new WorkerExecutionReceipt(descriptor.handlerVersion(), committed.getFirst(), result, false);
    }

    private WorkerExecutionReceipt replay(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, String inputHash, StoredReceipt stored) {
        if (!task.taskId().equals(stored.taskId()) || !task.activityOccurrenceId().equals(stored.occurrenceId())
                || !task.topic().equals(stored.topic()) || !task.schemaVersion().equals(stored.schemaVersion())
                || !task.effectSafety().name().equals(stored.effectSafety()) || !inputHash.equals(stored.inputSha256())) throw conflict();
        try {
            if (stored.outputJson().getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) throw conflict();
            @SuppressWarnings("unchecked") Map<String, Object> output = mapper.readValue(stored.outputJson(), Map.class);
            var result = new WorkerBusinessResultDTO(stored.businessReceiptId(), output);
            descriptor.contract().validateOutput(result.output());
            if (!resultHash(result).equals(stored.resultSha256())) throw conflict();
            return new WorkerExecutionReceipt(stored.handlerVersion(), stored.committedAt(), result, true);
        } catch (WorkerExecutionConflictException conflict) {
            throw conflict;
        } catch (RuntimeException invalidStored) {
            throw conflict();
        }
    }

    private String inputHash(WorkerTaskDTO task) {
        String scope = task.scopeType() == WorkerScopeTypeEnum.BPMN ? task.processInstanceId() : task.caseInstanceId();
        List<String> fields = new ArrayList<>(List.of("worker-business-input-v2", sourceId, Long.toString(tenantId), task.idempotencyKey(),
                task.taskId(), task.activityOccurrenceId(), task.scopeType().name(), scope, task.topic(), task.schemaVersion(),
                task.effectSafety().name(), task.businessKey(), task.correlationId()));
        addSchema(fields, task.inputSchema());
        addSchema(fields, task.outputSchema());
        return WorkerDigests.hash(fields, task.input());
    }

    private static void addSchema(List<String> fields, Map<String, ProcessValueTypeEnum> schema) {
        fields.add(Integer.toString(schema.size()));
        for (var entry : new TreeMap<>(schema).entrySet()) {
            fields.add(entry.getKey());
            fields.add(entry.getValue().name());
        }
    }

    private static String resultHash(WorkerBusinessResultDTO result) {
        return WorkerDigests.hash(List.of("worker-business-result-v1", result.businessReceiptId()), result.output());
    }

    private Instant now() {
        Timestamp timestamp = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (timestamp == null) throw new IllegalStateException("Worker database clock is unavailable");
        return timestamp.toInstant();
    }
    private static void requireTime(Instant now, Instant deadline) { if (!now.isBefore(deadline)) throw new WorkerExecutionDeadlineException(); }
    private static boolean wholeMillis(Duration value) { return value.toMillis() * 1_000_000L == value.toNanos(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Worker business execution is invalid"); }
    private static WorkerExecutionConflictException conflict() { return new WorkerExecutionConflictException(); }

    private static StoredReceipt read(ResultSet rs) throws SQLException {
        return new StoredReceipt(rs.getString("task_id"), rs.getString("occurrence_id"), rs.getString("topic"), rs.getString("schema_version"),
                rs.getString("effect_safety"), rs.getString("handler_version"), rs.getString("input_sha256"), rs.getString("result_sha256"),
                rs.getString("business_receipt_id"), rs.getString("output_json"), rs.getTimestamp("committed_at").toInstant());
    }

    private record StoredReceipt(String taskId, String occurrenceId, String topic, String schemaVersion, String effectSafety,
                                 String handlerVersion, String inputSha256, String resultSha256, String businessReceiptId,
                                 String outputJson, Instant committedAt) { }
    private record StoredIntent(String effectSafety, String targetId, String handlerVersion, String inputSha256) { }
    private record Prepared(Instant deadline, String inputHash, WorkerExecutionReceipt receipt) { }
}
