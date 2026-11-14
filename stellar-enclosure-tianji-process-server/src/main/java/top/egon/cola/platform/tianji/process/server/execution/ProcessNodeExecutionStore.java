package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Validated
public class ProcessNodeExecutionStore {
    static final int MAX_DATA = 64 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final String COLUMNS = """
            tenant_id,instance_id,occurrence_id,attempt,node_execution_ref,node_id,kind,parent_scope_id,attempt_scope_id,
            branch_index,iteration_index,excluded,native_job_id,native_subscription_id,capability_key,capability_version,effect_key,
            input_json,input_sha256,input_schema_sha256,output_schema_sha256,configuration_sha256,state,revision,output_json,
            output_sha256,agent_run_id,side_effect_outcome,error_code,started_at,node_deadline_at,attempt_deadline_at,accepted_at,
            finished_at,retry_command_id,retry_request_sha256,worker_effect_recovery
            """;
    private static final String SCOPED = " WHERE tenant_id=? AND instance_id=?";
    private static final String OPEN = " AND state NOT IN ('COMPLETED','FAILED','TIMED_OUT','CANCELLED')";
    private final JdbcTemplate jdbc;
    private final String table;
    private final String executions;

    public ProcessNodeExecutionStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        table = schema + ".tianji_process_node_execution";
        executions = schema + ".act_ru_execution";
    }

    public Instant now() {
        requireTransaction();
        return Objects.requireNonNull(jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()::timestamptz(6)", OffsetDateTime.class)).toInstant();
    }

    public void insertOccurrence(@NotNull @Valid ProcessNodeExecutionBO row) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (!digest("AUTOMATION_NODE_INPUT_V2", row.getInputSchemaSha256(), row.getInput()).equals(row.getInputSha256())
                || row.getOutput() != null || row.getOutputSha256() != null) throw unavailable();
        int inserted = jdbc.update("INSERT INTO " + table + " (" + COLUMNS + ") VALUES ("
                        + "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)",
                row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(), row.getNodeExecutionRef(), row.getNodeId(),
                row.getKind().name(), row.getParentScopeId(), row.getAttemptScopeId(), row.getBranchIndex(), row.getIterationIndex(),
                row.isExcluded(), row.getNativeJobId(), row.getNativeSubscriptionId(), row.getCapabilityKey(), row.getCapabilityVersion(),
                row.getEffectKey(), json(row.getInput(), MAX_DATA), row.getInputSha256(), row.getInputSchemaSha256(),
                row.getOutputSchemaSha256(), row.getConfigurationSha256(), row.getState().name(), row.getRevision(), null, null,
                row.getAgentRunId(), row.getSideEffectOutcome().name(), row.getErrorCode(), utc(row.getStartedAt()), utc(row.getNodeDeadlineAt()),
                utc(row.getAttemptDeadlineAt()), utc(row.getAcceptedAt()), utc(row.getFinishedAt()), row.getRetryCommandId(),
                row.getRetryRequestSha256(), row.getWorkerEffectRecovery() == null ? null : JSON.writeValueAsString(row.getWorkerEffectRecovery()));
        if (inserted != 1) throw unavailable();
    }

    public Optional<ProcessNodeExecutionBO> find(long tenantId, String instanceId, String nodeExecutionRef) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED + " AND node_execution_ref=?",
                this::row, tenantId, instanceId, nodeExecutionRef));
    }

    public Optional<ProcessNodeExecutionBO> findCurrentHuman(long tenantId, String instanceId, String nativeExecutionId, String logicalNodeId) {
        requireTransaction();
        return first(jdbc.query("""
                WITH RECURSIVE ancestry(id_,parent_id_) AS (
                    SELECT id_,parent_id_ FROM %s WHERE id_=? AND proc_inst_id_=?
                    UNION
                    SELECT n.id_,n.parent_id_ FROM %s n JOIN ancestry a ON a.parent_id_=n.id_ WHERE n.proc_inst_id_=?
                )
                SELECT %s FROM %s
                WHERE tenant_id=? AND instance_id=? AND node_id=? AND kind='HUMAN' AND NOT excluded
                  AND attempt_scope_id IN (SELECT id_ FROM ancestry)
                  AND state IN ('WAITING','RESULT_ACCEPTED')
                ORDER BY attempt DESC LIMIT 2
                """.formatted(executions, executions, COLUMNS, table), this::row,
                nativeExecutionId, instanceId, instanceId, tenantId, instanceId, logicalNodeId));
    }

    Optional<ProcessNodeExecutionBO> findAttempt(long tenantId, String instanceId, String attemptScopeId) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED + " AND attempt_scope_id=? LIMIT 2",
                this::row, tenantId, instanceId, attemptScopeId));
    }

    Optional<ProcessNodeExecutionBO> findByOccurrence(long tenantId, String instanceId, String occurrenceId) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED
                        + " AND occurrence_id = ? ORDER BY attempt DESC LIMIT 1", this::row, tenantId, instanceId, occurrenceId));
    }

    public List<ProcessNodeExecutionBO> list(long tenantId, String instanceId, String nodeId, Instant notAfter,
                                            NodePageKeyVO after, int limit) {
        requireTransaction();
        if (limit < 1 || limit > 101 || notAfter == null) throw new IllegalArgumentException("Invalid automation node page");
        StringBuilder sql = new StringBuilder("SELECT ").append(COLUMNS).append(" FROM ").append(table).append(SCOPED)
                .append(" AND started_at<=?");
        var parameters = new ArrayList<>(List.of((Object) tenantId, instanceId, utc(notAfter)));
        if (nodeId != null) {
            sql.append(" AND node_id=?");
            parameters.add(nodeId);
        }
        if (after != null) {
            sql.append(" AND (started_at,occurrence_id,attempt) < (?,?,?)");
            parameters.add(utc(after.startedAt()));
            parameters.add(after.occurrenceId());
            parameters.add(after.attempt());
        }
        sql.append(" ORDER BY started_at DESC,occurrence_id DESC,attempt DESC LIMIT ?");
        parameters.add(limit);
        return jdbc.query(sql.toString(), this::row, parameters.toArray());
    }

    public List<ProcessNodeExecutionBO> open(long tenantId, String instanceId, int limit) {
        requireTransaction();
        if (limit < 1 || limit > 257) throw new IllegalArgumentException("Invalid open node limit");
        return jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED + OPEN
                + " AND NOT excluded ORDER BY started_at,occurrence_id,attempt LIMIT ?", this::row, tenantId, instanceId, limit);
    }

    public boolean completeHuman(long tenantId, String instanceId, String nodeExecutionRef, long expectedRevision,
                                 Map<String, Object> normalizedOutput) {
        requireWrite(tenantId, instanceId);
        var row = find(tenantId, instanceId, nodeExecutionRef).orElse(null);
        if (row == null || row.getKind() != KindEnum.HUMAN || row.getState() != StateEnum.WAITING
                || row.getRevision() != expectedRevision || row.isExcluded()) return false;
        var timestamp = now();
        if (!timestamp.isBefore(row.getAttemptDeadlineAt()) || !timestamp.isBefore(row.getNodeDeadlineAt())) return false;
        return jdbc.update("UPDATE " + table + " SET state='RESULT_ACCEPTED',revision=revision+1,output_json=?::jsonb,output_sha256=?,accepted_at=?"
                        + SCOPED + " AND node_execution_ref=? AND revision=? AND state='WAITING' AND NOT excluded",
                json(normalizedOutput, MAX_DATA), digest("AUTOMATION_NODE_OUTPUT_V2", row.getOutputSchemaSha256(), normalizedOutput),
                utc(timestamp), tenantId, instanceId, nodeExecutionRef, expectedRevision) == 1;
    }

    public int cancelOpen(long tenantId, String instanceId) {
        requireWrite(tenantId, instanceId);
        return jdbc.update("UPDATE " + table + " SET state='CANCELLED',revision=revision+1,finished_at=?"
                + SCOPED + OPEN, utc(now()), tenantId, instanceId);
    }

    void finish(ProcessNodeExecutionBO row, StateEnum state) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (state != StateEnum.COMPLETED && state != StateEnum.TIMED_OUT && state != StateEnum.CANCELLED && state != StateEnum.FAILED) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET state=?,revision=revision+1,finished_at=?" + SCOPED
                        + " AND node_execution_ref=? AND revision=?" + OPEN,
                state.name(), utc(now()), row.getTenantId(), row.getInstanceId(), row.getNodeExecutionRef(), row.getRevision());
        if (changed != 1) throw unavailable();
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())) throw unavailable();
    }

    private void requireWrite(long tenantId, String instanceId) {
        requireTransaction();
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw unavailable();
        AutomationInstanceGuardCommandInterceptor.requireHeld(tenantId, instanceId);
    }

    private ProcessNodeExecutionBO row(ResultSet row, int index) throws SQLException {
        try {
            var item = new ProcessNodeExecutionBO().setTenantId(row.getLong("tenant_id")).setInstanceId(row.getString("instance_id"))
                    .setOccurrenceId(row.getString("occurrence_id")).setAttempt(row.getInt("attempt")).setNodeExecutionRef(row.getString("node_execution_ref"))
                    .setNodeId(row.getString("node_id")).setKind(KindEnum.valueOf(row.getString("kind"))).setParentScopeId(row.getString("parent_scope_id"))
                    .setAttemptScopeId(row.getString("attempt_scope_id")).setBranchIndex(row.getObject("branch_index", Integer.class))
                    .setIterationIndex(row.getObject("iteration_index", Integer.class)).setExcluded(row.getBoolean("excluded"))
                    .setNativeJobId(row.getString("native_job_id")).setNativeSubscriptionId(row.getString("native_subscription_id"))
                    .setCapabilityKey(row.getString("capability_key")).setCapabilityVersion(row.getObject("capability_version", Long.class))
                    .setEffectKey(row.getString("effect_key")).setInput(object(row.getString("input_json"))).setInputSha256(row.getString("input_sha256"))
                    .setInputSchemaSha256(row.getString("input_schema_sha256")).setOutputSchemaSha256(row.getString("output_schema_sha256"))
                    .setConfigurationSha256(row.getString("configuration_sha256")).setState(StateEnum.valueOf(row.getString("state")))
                    .setRevision(row.getLong("revision")).setOutput(object(row.getString("output_json"))).setOutputSha256(row.getString("output_sha256"))
                    .setAgentRunId(row.getString("agent_run_id")).setSideEffectOutcome(SideEffectOutcomeEnum.valueOf(row.getString("side_effect_outcome")))
                    .setErrorCode(row.getString("error_code")).setStartedAt(instant(row,"started_at")).setNodeDeadlineAt(instant(row,"node_deadline_at"))
                    .setAttemptDeadlineAt(instant(row,"attempt_deadline_at")).setAcceptedAt(instant(row,"accepted_at")).setFinishedAt(instant(row,"finished_at"))
                    .setRetryCommandId(row.getString("retry_command_id")).setRetryRequestSha256(row.getString("retry_request_sha256"));
            String recovery = row.getString("worker_effect_recovery");
            if (recovery != null) item.setWorkerEffectRecovery(JSON.readValue(recovery, WorkerEffectRecoveryVO.class));
            if (!digest("AUTOMATION_NODE_INPUT_V2", item.getInputSchemaSha256(), item.getInput()).equals(item.getInputSha256())
                    || (item.getOutput() == null) != (item.getOutputSha256() == null)
                    || item.getOutput() != null && !digest("AUTOMATION_NODE_OUTPUT_V2", item.getOutputSchemaSha256(), item.getOutput()).equals(item.getOutputSha256())) {
                throw unavailable();
            }
            return item;
        } catch (RuntimeException corrupt) {
            throw unavailable();
        }
    }

    static String digest(String domain, String schema, Map<String, Object> value) {
        if (!ProcessInstanceBindingStore.sha256(schema) || value == null) throw unavailable();
        return ProcessTypedJson.sha256(domain, schema.getBytes(StandardCharsets.UTF_8), ProcessTypedJson.canonicalBytes(value, MAX_DATA));
    }

    private static Map<String, Object> object(String text) {
        if (text == null) return null;
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_DATA) throw unavailable();
        Map<String, Object> raw = JSON.readValue(text, OBJECT);
        var result = new LinkedHashMap<String, Object>();
        raw.forEach((key, value) -> result.put(key, freeze(value)));
        return Collections.unmodifiableMap(result);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> object) {
            var result = new LinkedHashMap<>();
            object.forEach((key, child) -> result.put(key, freeze(child)));
            return Collections.unmodifiableMap(result);
        }
        if (value instanceof List<?> list) return list.stream().map(ProcessNodeExecutionStore::freeze).toList();
        return value;
    }

    private static String json(Map<String, Object> value, int max) {
        return new String(ProcessTypedJson.canonicalBytes(value, max), StandardCharsets.UTF_8);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet row, String key) throws SQLException {
        var timestamp = row.getObject(key, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <T> Optional<T> first(List<T> rows) {
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Automation node execution evidence is unavailable or inconsistent");
    }

    public record NodePageKeyVO(Instant startedAt, String occurrenceId, int attempt) {
        public NodePageKeyVO {
            if (startedAt == null || occurrenceId == null || attempt < 1 || attempt > 10) {
                throw new IllegalArgumentException("Invalid automation node page boundary");
            }
        }
    }
}
