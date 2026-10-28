package top.egon.cola.platform.tianji.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Executes local JDBC business work and its replayable receipt in one owned transaction. */
public final class JdbcWorkerExecutionStore implements WorkerExecutionStore {
    private static final int MAX_RESULT_BYTES = 131_072;
    private final DataSource source;
    private final DataSourceTransactionManager manager;
    private final JdbcTemplate jdbc;
    private final String table;
    private final String sourceId;
    private final long tenantId;
    private final Duration maximumWorkTime;
    private final Duration deadlineSafetyMargin;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public JdbcWorkerExecutionStore(DataSource source, DataSourceTransactionManager manager, String schema, String sourceId,
                                    long tenantId, Duration maximumWorkTime, Duration deadlineSafetyMargin) {
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
        this.sourceId = sourceId;
        this.tenantId = tenantId;
        this.maximumWorkTime = maximumWorkTime;
        this.deadlineSafetyMargin = deadlineSafetyMargin;
        new WorkerExecutionSchemaVerifier(source, schema).verify();
    }

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
            long lockMillis = Math.max(1, Duration.between(now, deadline).toMillis());
            jdbc.queryForObject("SELECT set_config('lock_timeout', ?, true)", String.class, lockMillis + "ms");
            String lockKey = WorkerDigests.hash(List.of("worker-business-lock-v1", sourceId,
                    Long.toString(tenantId), task.idempotencyKey()), Map.of());
            jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { }, "tianji:business-worker:" + lockKey);
            requireTime(now(), deadline);
            String inputHash = inputHash(task);
            List<StoredReceipt> existing = jdbc.query("SELECT task_id,occurrence_id,topic,schema_version,effect_safety,handler_version, "
                            + "input_sha256,result_sha256,business_receipt_id,output_json::text AS output_json,committed_at FROM " + table
                            + " WHERE source_id=? AND tenant_id=? AND idempotency_key=?",
                    (rs, row) -> read(rs), sourceId, tenantId, task.idempotencyKey());
            if (existing.size() > 1) throw conflict();
            if (!existing.isEmpty()) return replay(task, descriptor, inputHash, existing.getFirst());

            if (!TransactionSynchronizationManager.hasResource(source)) throw new IllegalStateException("Worker business transaction is not bound to its DataSource");
            WorkerBusinessResultDTO result = Objects.requireNonNull(handler.execute(WorkerExecutionContext.from(task, sourceId, deadline), jdbc), "worker result");
            descriptor.contract().validateOutput(result.output());
            if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                    || status.isRollbackOnly()) throw new IllegalStateException("Worker business transaction is not active");
            requireTime(now(), deadline);
            byte[] output;
            try { output = mapper.writeValueAsBytes(result.output()); }
            catch (RuntimeException invalidResult) { throw invalid(); }
            if (output.length > MAX_RESULT_BYTES) throw invalid();
            String resultHash = resultHash(result);
            List<Instant> committed = jdbc.query("INSERT INTO " + table + " (source_id,tenant_id,idempotency_key,task_id,occurrence_id, "
                            + "topic,schema_version,effect_safety,handler_version,input_sha256,result_sha256,business_receipt_id,output_json,committed_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),clock_timestamp()) RETURNING committed_at",
                    (rs, row) -> rs.getTimestamp("committed_at").toInstant(), sourceId, tenantId, task.idempotencyKey(), task.taskId(),
                    task.activityOccurrenceId(), task.topic(), task.schemaVersion(), task.effectSafety().name(), descriptor.handlerVersion(), inputHash,
                    resultHash, result.businessReceiptId(), new String(output, StandardCharsets.UTF_8));
            if (committed.size() != 1) throw new IllegalStateException("Worker business receipt could not be recorded");
            requireTime(now(), deadline);
            return new WorkerExecutionReceipt(descriptor.handlerVersion(), committed.getFirst(), result, false);
        }));
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
}
