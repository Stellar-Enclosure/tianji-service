package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Validated
public class ProcessNodeExecutionStore {
    static final int MAX_DATA = 64 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private static final Set<String> RECOVERY_FIELDS = Set.of("mode", "observedGeneration", "observedAt", "validUntil",
            "authorityRef", "evidenceSha256", "businessReceiptId", "resultSha256");
    private static final Set<KindEnum> LOCAL_RESULT_KINDS = Set.of(KindEnum.RULE, KindEnum.AND_JOIN, KindEnum.LOOP,
            KindEnum.MULTI_INSTANCE, KindEnum.SUBPROCESS);
    private static final String COLUMNS = """
            tenant_id,instance_id,occurrence_id,attempt,node_execution_ref,node_id,kind,parent_scope_id,attempt_scope_id,
            branch_index,iteration_index,excluded,native_job_id,native_subscription_id,capability_key,capability_version,effect_key,
            input_json,input_sha256,input_schema_sha256,output_schema_sha256,configuration_sha256,state,revision,output_json,
            output_sha256,agent_run_id,side_effect_outcome,error_code,started_at,node_deadline_at,attempt_deadline_at,accepted_at,
            finished_at,retry_command_id,retry_request_sha256,worker_effect_recovery
            """;
    private static final String SCOPED = " WHERE tenant_id=? AND instance_id=?";
    private static final String OPEN = " AND state NOT IN ('COMPLETED','FAILED','TIMED_OUT','CANCELLED')";
    private static final String WORKER = " AND kind IN ('SERVICE','RULE') AND capability_key IS NOT NULL AND NOT excluded";
    private static final String WAIT_CAS = SCOPED + """
              AND occurrence_id=? AND attempt=? AND node_execution_ref=? AND node_id=? AND parent_scope_id=? AND attempt_scope_id=?
              AND configuration_sha256=? AND input_schema_sha256=? AND output_schema_sha256=? AND input_sha256=? AND input_json=?::jsonb
              AND started_at=? AND attempt_deadline_at=? AND node_deadline_at=? AND revision=?
              AND native_subscription_id IS NOT DISTINCT FROM ?::varchar AND state='WAITING' AND NOT excluded
              AND side_effect_outcome='NONE' AND error_code IS NULL
              AND output_json IS NULL AND output_sha256 IS NULL AND accepted_at IS NULL AND finished_at IS NULL
            """;
    private static final String EVENT_WAIT_CAS = WAIT_CAS + " AND kind='EVENT_WAIT' AND native_job_id IS NULL";
    private static final String AGENT_WAIT_CAS = WAIT_CAS + " AND kind='AGENT' AND native_job_id IS NOT DISTINCT FROM ?::varchar"
            + " AND agent_run_id IS NOT DISTINCT FROM ?::varchar";
    private final JdbcTemplate jdbc;
    private final String schema;
    private final String table;
    private final String executions;

    public ProcessNodeExecutionStore(JdbcTemplate jdbc, String schema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Invalid Process schema");
        this.schema = schema;
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
                row.getRetryRequestSha256(), recoveryJson(row.getWorkerEffectRecovery()));
        if (inserted != 1) throw unavailable();
    }

    public Optional<ProcessNodeExecutionBO> find(long tenantId, String instanceId, String nodeExecutionRef) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED + " AND node_execution_ref=?",
                this::row, tenantId, instanceId, nodeExecutionRef));
    }

    public Optional<ProcessNodeExecutionBO> findRetry(long tenantId, String instanceId, String commandId) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED + " AND retry_command_id=? LIMIT 2",
                this::row, tenantId, instanceId, commandId));
    }

    void insertRecoveredAttempt(ProcessNodeExecutionBO prior, ProcessNodeExecutionBO next) {
        requireWrite(prior.getTenantId(), prior.getInstanceId());
        var latest = findByOccurrence(prior.getTenantId(), prior.getInstanceId(), prior.getOccurrenceId())
                .orElseThrow(ProcessNodeExecutionStore::unavailable);
        var timestamp = now();
        var proof = next.getWorkerEffectRecovery();
        if (!prior.equals(latest) || prior.getState() != StateEnum.WAIT_RECOVERY || prior.getNativeSubscriptionId() == null
                || prior.getNativeJobId() == null || prior.isExcluded() || prior.getCapabilityKey() == null
                || !timestamp.isBefore(prior.getNodeDeadlineAt())
                || (prior.getSideEffectOutcome() == SideEffectOutcomeEnum.UNKNOWN) != (proof != null)
                || proof != null && (timestamp.isBefore(proof.observedAt()) || !timestamp.isBefore(proof.validUntil())
                        || proof.validUntil().isAfter(prior.getNodeDeadlineAt()))) {
            throw new AutomationOperationException(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED);
        }
        if (next.getAttempt() != prior.getAttempt() + 1 || next.getTenantId() != prior.getTenantId()
                || !prior.getInstanceId().equals(next.getInstanceId()) || !prior.getOccurrenceId().equals(next.getOccurrenceId())
                || !prior.getParentScopeId().equals(next.getParentScopeId()) || !prior.getNodeId().equals(next.getNodeId())
                || prior.getKind() != next.getKind() || !prior.getCapabilityKey().equals(next.getCapabilityKey())
                || !Objects.equals(prior.getCapabilityVersion(), next.getCapabilityVersion())
                || !prior.getEffectKey().equals(next.getEffectKey()) || !prior.getInputSha256().equals(next.getInputSha256())
                || !prior.getInputSchemaSha256().equals(next.getInputSchemaSha256())
                || !prior.getOutputSchemaSha256().equals(next.getOutputSchemaSha256())
                || !prior.getConfigurationSha256().equals(next.getConfigurationSha256())
                || !prior.getNodeDeadlineAt().equals(next.getNodeDeadlineAt())
                || next.getState() != StateEnum.WAITING || next.getSideEffectOutcome() != SideEffectOutcomeEnum.NONE
                || next.getNativeJobId() != null || next.getNativeSubscriptionId() != null || next.getRetryCommandId() == null
                || !next.getRetryCommandId().matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || !ProcessInstanceBindingStore.sha256(next.getRetryRequestSha256())) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET state='FAILED',revision=revision+1,finished_at=?"
                        + SCOPED + WORKER + " AND occurrence_id=? AND attempt=? AND node_execution_ref=? AND attempt_scope_id=?"
                        + " AND native_job_id=? AND native_subscription_id=? AND revision=? AND state='WAIT_RECOVERY'",
                utc(timestamp), prior.getTenantId(), prior.getInstanceId(), prior.getOccurrenceId(), prior.getAttempt(),
                prior.getNodeExecutionRef(), prior.getAttemptScopeId(), prior.getNativeJobId(), prior.getNativeSubscriptionId(), prior.getRevision());
        if (changed != 1) throw new AutomationOperationException(AutomationOperationException.Code.TIANJI_NODE_RETRY_BLOCKED);
        insertOccurrence(next);
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

    ProcessNodeExecutionBO requireCompletedSource(long tenantId, String instanceId, String configurationSha256,
                                                    String parentScopeId, String nodeId) {
        requireWrite(tenantId, instanceId);
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED
                        + " AND configuration_sha256=? AND parent_scope_id=? AND node_id=?"
                        + " AND state='COMPLETED' AND NOT excluded AND output_json IS NOT NULL AND accepted_at IS NOT NULL LIMIT 2",
                this::row, tenantId, instanceId, configurationSha256, parentScopeId, nodeId))
                .orElseThrow(ProcessNodeExecutionStore::unavailable);
    }

    public ProcessNodeExecutionBO requireWorkerAttempt(long tenantId, String instanceId, String nativeJobId) {
        requireWrite(tenantId, instanceId);
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED
                + WORKER + " AND native_job_id=? LIMIT 2", this::row, tenantId, instanceId, nativeJobId))
                .orElseThrow(ProcessNodeExecutionStore::unavailable);
    }

    public ProcessNodeExecutionBO requireWorkerAttempt(long tenantId, String instanceId, String nodeExecutionRef,
                                                        String nativeJobId) {
        requireWrite(tenantId, instanceId);
        return findWorkerAttempt(tenantId, instanceId, nodeExecutionRef, nativeJobId)
                .orElseThrow(ProcessNodeExecutionStore::unavailable);
    }

    public Optional<ProcessNodeExecutionBO> findWorkerAttempt(long tenantId, String instanceId, String nodeExecutionRef,
                                                               String nativeJobId) {
        requireTransaction();
        return first(jdbc.query("SELECT " + COLUMNS + " FROM " + table + SCOPED
                + WORKER + " AND node_execution_ref=? AND native_job_id=? LIMIT 2",
                this::row, tenantId, instanceId, nodeExecutionRef, nativeJobId));
    }

    void bindWorkerJob(ProcessNodeExecutionBO row, String nativeJobId) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (nativeJobId == null || nativeJobId.isBlank() || row.getNativeJobId() != null) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET native_job_id=?,revision=revision+1" + SCOPED + WORKER
                        + " AND occurrence_id=? AND attempt=? AND node_execution_ref=? AND attempt_scope_id=? AND revision=?"
                        + " AND state='WAITING' AND native_job_id IS NULL",
                nativeJobId, row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(),
                row.getNodeExecutionRef(), row.getAttemptScopeId(), row.getRevision());
        if (changed != 1) throw unavailable();
    }

    void lockWorkerJob(ProcessNodeExecutionBO row) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        List<String> found = jdbc.queryForList("SELECT id_ FROM " + schema + ".act_ru_external_job"
                        + " WHERE id_=? AND tenant_id_=? AND process_instance_id_=? FOR UPDATE", String.class,
                row.getNativeJobId(), Long.toString(row.getTenantId()), row.getInstanceId());
        if (found.size() != 1) throw unavailable();
    }

    void releaseWorkerScopeLock(ProcessNodeExecutionBO row, String definitionId, String owner, Instant expiresAt) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        jdbc.update("UPDATE " + executions + " SET lock_owner_=NULL,lock_time_=NULL"
                        + " WHERE id_=? AND proc_inst_id_=? AND parent_id_ IS NULL AND tenant_id_=? AND proc_def_id_=?"
                        + " AND lock_owner_=? AND lock_time_=?",
                row.getInstanceId(), row.getInstanceId(), Long.toString(row.getTenantId()), definitionId, owner,
                Timestamp.from(expiresAt));
    }

    public String acceptWorkerResult(ProcessNodeExecutionBO row, String nativeJobId,
                                     Map<String, Object> normalizedOutput, boolean confirmedWrite) {
        Instant timestamp = requireWaitingWorker(row, nativeJobId, true);
        String outputSha256 = AutomationWorkerBusinessResultDTO.nodeOutputSha256(row.getOutputSchemaSha256(), normalizedOutput);
        int changed = jdbc.update("UPDATE " + table + " SET state='RESULT_ACCEPTED',revision=revision+1,"
                        + "output_json=?::jsonb,output_sha256=?,accepted_at=?,side_effect_outcome=?" + workerCas(),
                json(normalizedOutput, MAX_DATA), outputSha256, utc(timestamp),
                confirmedWrite ? SideEffectOutcomeEnum.CONFIRMED.name() : SideEffectOutcomeEnum.NONE.name(),
                row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(), row.getNodeExecutionRef(),
                row.getAttemptScopeId(), nativeJobId, row.getRevision());
        if (changed != 1) throw unavailable();
        return resultRef(row, outputSha256);
    }

    public void acceptWorkerFailure(ProcessNodeExecutionBO row, String nativeJobId, AutomationWorkerFailureCommand.KindEnum kind,
                                    String errorCode, boolean retryScheduled) {
        if (kind == null || kind == AutomationWorkerFailureCommand.KindEnum.UNKNOWN || retryScheduled) throw unavailable();
        Instant timestamp = requireWaitingWorker(row, nativeJobId, true);
        finishWorkerWait(row, nativeJobId, StateEnum.FAILED, SideEffectOutcomeEnum.NONE, errorCode, null, timestamp);
    }

    List<WorkerLeaseStore.RecoveryLeaseEvidence> workerHistory(ProcessNodeExecutionBO row) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        return WorkerLeaseStore.readRecoveryHistory(jdbc, schema, row.getTenantId(), row.getNativeJobId());
    }

    void retireWorkerAttempt(ProcessNodeExecutionBO row, StateEnum state, SideEffectOutcomeEnum outcome,
                             String errorCode, String subscriptionId) {
        Instant timestamp = requireWaitingWorker(row, row.getNativeJobId(), false);
        finishWorkerWait(row, row.getNativeJobId(), state, outcome, errorCode, subscriptionId, timestamp);
    }

    private void finishWorkerWait(ProcessNodeExecutionBO row, String nativeJobId, StateEnum state,
                                  SideEffectOutcomeEnum outcome, String errorCode, String subscriptionId, Instant timestamp) {
        if (errorCode == null || !errorCode.matches("[A-Z][A-Z0-9_]{0,127}$")
                || state != StateEnum.FAILED && state != StateEnum.TIMED_OUT && state != StateEnum.WAIT_RECOVERY
                || (state == StateEnum.WAIT_RECOVERY) != (subscriptionId != null)) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET state=?,revision=revision+1,side_effect_outcome=?,error_code=?,"
                        + "native_subscription_id=?,finished_at=?" + workerCas(),
                state.name(), outcome.name(), errorCode, subscriptionId, state == StateEnum.WAIT_RECOVERY ? null : utc(timestamp),
                row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(), row.getNodeExecutionRef(),
                row.getAttemptScopeId(), nativeJobId, row.getRevision());
        if (changed != 1) throw unavailable();
    }

    private Instant requireWaitingWorker(ProcessNodeExecutionBO row, String nativeJobId, boolean beforeDeadline) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (nativeJobId == null || !nativeJobId.equals(row.getNativeJobId()) || row.getState() != StateEnum.WAITING
                || row.isExcluded() || row.getCapabilityKey() == null) throw unavailable();
        Instant timestamp = now();
        if (beforeDeadline && (!timestamp.isBefore(row.getAttemptDeadlineAt()) || !timestamp.isBefore(row.getNodeDeadlineAt()))) {
            throw unavailable();
        }
        return timestamp;
    }

    private static String workerCas() {
        return SCOPED + WORKER + " AND occurrence_id=? AND attempt=? AND node_execution_ref=? AND attempt_scope_id=?"
                + " AND native_job_id=? AND revision=? AND state='WAITING'";
    }

    public static String resultRef(ProcessNodeExecutionBO row, String outputSha256) {
        return "result-" + ProcessTypedJson.sha256("AUTOMATION_WORKER_NODE_RESULT_REF_V2", ProcessTypedJson.canonicalBytes(
                List.of(row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(),
                        row.getNodeExecutionRef(), row.getNativeJobId(), outputSha256)));
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

    /** Binds the ACK of the same verified V5 dispatch before Flowable enters the generated wait. */
    public void bindAgentDispatch(@NotNull @Valid ProcessNodeExecutionBO row, @NotBlank @Size(max = 255) String nativeJobId,
                                  @NotBlank @Size(max = 64) String runId) {
        requireWaitingNode(row, KindEnum.AGENT);
        if (!exactReference(nativeJobId, 255) || !exactReference(runId, 64)
                || row.getNativeJobId() != null && !nativeJobId.equals(row.getNativeJobId())
                || row.getAgentRunId() != null && !runId.equals(row.getAgentRunId())) throw unavailable();
        if (runId.equals(row.getAgentRunId())) {
            if (row.getNativeJobId() == null || !matchesAgentWait(row)) throw unavailable();
            return;
        }
        if (row.getNativeSubscriptionId() != null || !withinDeadline(row, waitingNodeTime(row))) throw unavailable();
        updateWait(" SET native_job_id=?,agent_run_id=?,revision=revision+1", AGENT_WAIT_CAS, agentWaitIdentity(row), nativeJobId, runId);
    }

    void bindAgentSubscription(ProcessNodeExecutionBO row, String nativeSubscriptionId) {
        requireWaitingNode(row, KindEnum.AGENT);
        if (!exactReference(nativeSubscriptionId, 255) || row.getNativeSubscriptionId() != null
                || row.getAgentRunId() == null || row.getNativeJobId() == null || !withinDeadline(row, waitingNodeTime(row))) throw unavailable();
        updateWait(" SET native_subscription_id=?,revision=revision+1", AGENT_WAIT_CAS, agentWaitIdentity(row), nativeSubscriptionId);
    }

    /** Persists the authorized terminal fact before the same transaction advances its exact native wait. */
    public boolean acceptAgentResult(@NotNull @Valid ProcessNodeExecutionBO row, @NotBlank @Size(max = 255) String nativeSubscriptionId,
                                     @NotNull @Valid AgentTerminalEventV2 terminal, Map<String, Object> normalizedOutput) {
        requireWaitingNode(row, KindEnum.AGENT);
        if (!exactReference(nativeSubscriptionId, 255) || !nativeSubscriptionId.equals(row.getNativeSubscriptionId())
                || row.getNativeJobId() == null || terminal.tenantId() != row.getTenantId()
                || !terminal.link().scopeInstanceId().equals(row.getInstanceId())
                || !terminal.link().activityOccurrenceId().equals(row.getNativeJobId())
                || !terminal.runId().equals(row.getAgentRunId()) || !terminal.nodeExecutionRef().equals(row.getNodeExecutionRef())
                || !terminal.configurationSha256().equals(row.getConfigurationSha256())
                || !terminal.inputSha256().equals(row.getInputSha256())
                || !terminal.outputSchemaSha256().equals(row.getOutputSchemaSha256())) throw unavailable();
        boolean completed = terminal.status() == AgentRunStatusEnum.COMPLETED;
        if (completed != (normalizedOutput != null)) throw unavailable();
        String output = normalizedOutput == null ? null : json(normalizedOutput, MAX_DATA);
        String outputHash = normalizedOutput == null ? null
                : digest("AUTOMATION_NODE_OUTPUT_V2", row.getOutputSchemaSha256(), normalizedOutput);
        Instant timestamp = waitingNodeTime(row);
        if (!withinDeadline(row, timestamp)) {
            if (!matchesAgentWait(row)) throw unavailable();
            return false;
        }
        updateWait(" SET state=?,revision=revision+1,output_json=?::jsonb,output_sha256=?,accepted_at=?,finished_at=?,"
                        + "side_effect_outcome=?,error_code=?", AGENT_WAIT_CAS, agentWaitIdentity(row),
                completed ? "RESULT_ACCEPTED" : "FAILED", output, outputHash, utc(timestamp), completed ? null : utc(timestamp),
                terminal.sideEffectOutcome().wire(), terminal.errorCode());
        return true;
    }

    private boolean matchesAgentWait(ProcessNodeExecutionBO row) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table + AGENT_WAIT_CAS + ")",
                Boolean.class, agentWaitIdentity(row)));
    }

    void parkFailedAgentResult(ProcessNodeExecutionBO row) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (row.getKind() != KindEnum.AGENT || row.getState() != StateEnum.FAILED || row.getAcceptedAt() == null
                || row.getFinishedAt() == null || row.getErrorCode() == null || row.getNativeSubscriptionId() == null
                || row.getAgentRunId() == null || row.isExcluded()) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET state='WAIT_RECOVERY',finished_at=NULL,revision=revision+1" + SCOPED
                        + " AND kind='AGENT' AND state='FAILED' AND NOT excluded AND occurrence_id=? AND attempt=?"
                        + " AND node_execution_ref=? AND attempt_scope_id=? AND agent_run_id=? AND native_subscription_id=?"
                        + " AND revision=? AND accepted_at=? AND finished_at=? AND error_code=?",
                row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(), row.getNodeExecutionRef(), row.getAttemptScopeId(),
                row.getAgentRunId(), row.getNativeSubscriptionId(), row.getRevision(), utc(row.getAcceptedAt()), utc(row.getFinishedAt()), row.getErrorCode());
        if (changed != 1) throw unavailable();
    }

    private static Object[] agentWaitIdentity(ProcessNodeExecutionBO row) {
        var parameters = new ArrayList<Object>();
        Collections.addAll(parameters, waitIdentity(row));
        parameters.add(row.getNativeJobId());
        parameters.add(row.getAgentRunId());
        return parameters.toArray();
    }

    /** Associates the exact native message subscription after Flowable creates it under the root guard. */
    void bindEventSubscription(@NotNull @Valid ProcessNodeExecutionBO row, @NotBlank @Size(max = 255) String nativeSubscriptionId) {
        requireWaitingNode(row, KindEnum.EVENT_WAIT);
        if (!exactReference(nativeSubscriptionId, 255) || row.getNativeSubscriptionId() != null
                || !withinDeadline(row, waitingNodeTime(row))) throw unavailable();
        updateWait(" SET native_subscription_id=?,revision=revision+1", EVENT_WAIT_CAS, waitIdentity(row), nativeSubscriptionId);
    }

    /** Persists the final typed input and output before the exact native wait leaves the same transaction. */
    public boolean acceptEventResult(@NotNull @Valid ProcessNodeExecutionBO row, @NotBlank @Size(max = 255) String nativeSubscriptionId,
                                     @NotNull Map<String, Object> normalizedInput, @NotNull Map<String, Object> normalizedOutput) {
        requireWaitingNode(row, KindEnum.EVENT_WAIT);
        if (!exactReference(nativeSubscriptionId, 255) || !nativeSubscriptionId.equals(row.getNativeSubscriptionId())) throw unavailable();
        String input = json(normalizedInput, MAX_DATA);
        String output = json(normalizedOutput, MAX_DATA);
        String inputHash = digest("AUTOMATION_NODE_INPUT_V2", row.getInputSchemaSha256(), normalizedInput);
        String outputHash = digest("AUTOMATION_NODE_OUTPUT_V2", row.getOutputSchemaSha256(), normalizedOutput);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT octet_length((?::jsonb)::text)<=? AND octet_length((?::jsonb)::text)<=?",
                Boolean.class, input, MAX_DATA, output, MAX_DATA))) {
            throw new IllegalArgumentException("Automation event result exceeds its storage limit");
        }
        Instant timestamp = waitingNodeTime(row);
        if (!withinDeadline(row, timestamp)) {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM " + table + EVENT_WAIT_CAS + ")",
                    Boolean.class, waitIdentity(row)))) throw unavailable();
            return false;
        }
        updateWait(" SET state='RESULT_ACCEPTED',revision=revision+1,input_json=?::jsonb,input_sha256=?,"
                        + "output_json=?::jsonb,output_sha256=?,accepted_at=?", EVENT_WAIT_CAS, waitIdentity(row),
                input, inputHash, output, outputHash, utc(timestamp));
        return true;
    }

    private void requireWaitingNode(ProcessNodeExecutionBO row, KindEnum kind) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (row.getKind() != kind || row.getState() != StateEnum.WAITING || row.isExcluded()
                || !digest("AUTOMATION_NODE_INPUT_V2", row.getInputSchemaSha256(), row.getInput()).equals(row.getInputSha256())) {
            throw unavailable();
        }
    }

    private static boolean exactReference(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit && value.chars().noneMatch(Character::isISOControl);
    }

    private Instant waitingNodeTime(ProcessNodeExecutionBO row) {
        Instant timestamp = now();
        if (timestamp.isBefore(row.getStartedAt())) throw unavailable();
        return timestamp;
    }

    private static boolean withinDeadline(ProcessNodeExecutionBO row, Instant timestamp) {
        return timestamp.isBefore(row.getAttemptDeadlineAt()) && timestamp.isBefore(row.getNodeDeadlineAt());
    }

    private void updateWait(String changes, String cas, Object[] identity, Object... values) {
        var parameters = new ArrayList<Object>();
        Collections.addAll(parameters, values);
        Collections.addAll(parameters, identity);
        if (jdbc.update("UPDATE " + table + changes + cas, parameters.toArray()) != 1) throw unavailable();
    }

    private static Object[] waitIdentity(ProcessNodeExecutionBO row) {
        return new Object[]{row.getTenantId(), row.getInstanceId(), row.getOccurrenceId(), row.getAttempt(),
                row.getNodeExecutionRef(), row.getNodeId(), row.getParentScopeId(), row.getAttemptScopeId(), row.getConfigurationSha256(),
                row.getInputSchemaSha256(), row.getOutputSchemaSha256(), row.getInputSha256(), json(row.getInput(), MAX_DATA),
                utc(row.getStartedAt()), utc(row.getAttemptDeadlineAt()), utc(row.getNodeDeadlineAt()), row.getRevision(), row.getNativeSubscriptionId()};
    }

    void completeLocal(ProcessNodeExecutionBO row, Map<String, Object> normalizedOutput) {
        requireWrite(row.getTenantId(), row.getInstanceId());
        if (!LOCAL_RESULT_KINDS.contains(row.getKind()) || row.getCapabilityKey() != null
                || row.getState() != StateEnum.RUNNING || row.isExcluded()) throw unavailable();
        var timestamp = now();
        if (timestamp.isBefore(row.getStartedAt()) || !timestamp.isBefore(row.getAttemptDeadlineAt())
                || !timestamp.isBefore(row.getNodeDeadlineAt())) throw unavailable();
        int changed = jdbc.update("UPDATE " + table + " SET state='COMPLETED',revision=revision+1,"
                        + "output_json=?::jsonb,output_sha256=?,accepted_at=?,finished_at=?" + SCOPED
                        + " AND configuration_sha256=? AND parent_scope_id=? AND node_id=? AND kind=?"
                        + " AND occurrence_id=? AND attempt=? AND node_execution_ref=? AND attempt_scope_id=? AND revision=?"
                        + " AND state='RUNNING' AND NOT excluded AND capability_key IS NULL"
                        + " AND output_json IS NULL AND output_sha256 IS NULL AND accepted_at IS NULL",
                json(normalizedOutput, MAX_DATA), digest("AUTOMATION_NODE_OUTPUT_V2", row.getOutputSchemaSha256(), normalizedOutput),
                utc(timestamp), utc(timestamp), row.getTenantId(), row.getInstanceId(), row.getConfigurationSha256(),
                row.getParentScopeId(), row.getNodeId(), row.getKind().name(), row.getOccurrenceId(), row.getAttempt(),
                row.getNodeExecutionRef(), row.getAttemptScopeId(), row.getRevision());
        if (changed != 1) throw unavailable();
    }

    void excludeMultiChildren(long tenantId, String instanceId, String configurationSha256, Collection<String> nodeExecutionRefs) {
        requireWrite(tenantId, instanceId);
        if (nodeExecutionRefs.isEmpty()) return;
        var references = Set.copyOf(nodeExecutionRefs);
        if (references.size() != nodeExecutionRefs.size() || !ProcessInstanceBindingStore.sha256(configurationSha256)) {
            throw unavailable();
        }
        var timestamp = utc(now());
        // V14 ties finished_at to terminal state. Keep previously accepted and terminal evidence.
        var matched = jdbc.queryForObject("""
                WITH targets AS (
                    SELECT node_execution_ref,finished_at FROM %1$s
                    WHERE tenant_id=? AND instance_id=? AND configuration_sha256=? AND node_execution_ref=ANY(?)
                ), updated AS (
                    UPDATE %1$s node
                    SET excluded=true,revision=node.revision+1,
                        state=CASE WHEN target.finished_at IS NULL THEN 'CANCELLED' ELSE node.state END,
                        finished_at=COALESCE(target.finished_at,?)
                    FROM targets target
                    WHERE node.tenant_id=? AND node.instance_id=? AND node.configuration_sha256=?
                        AND node.node_execution_ref=target.node_execution_ref AND (NOT node.excluded OR target.finished_at IS NULL)
                        AND (SELECT count(*) FROM targets)=?
                    RETURNING node.node_execution_ref
                )
                SELECT count(*) FROM targets
                """.formatted(table), Long.class, tenantId, instanceId, configurationSha256,
                new SqlArrayValue("varchar", references.toArray()), timestamp, tenantId, instanceId, configurationSha256,
                references.size());
        if (!Objects.equals(matched, (long) references.size())) throw unavailable();
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
            if (recovery != null) item.setWorkerEffectRecovery(recovery(recovery));
            if (!digest("AUTOMATION_NODE_INPUT_V2", item.getInputSchemaSha256(), item.getInput()).equals(item.getInputSha256())
                    || (item.getOutput() == null) != (item.getOutputSha256() == null)
                    || item.getOutput() != null && !digest("AUTOMATION_NODE_OUTPUT_V2", item.getOutputSchemaSha256(),
                            item.getOutput()).equals(item.getOutputSha256())) {
                throw unavailable();
            }
            return item;
        } catch (RuntimeException corrupt) {
            throw unavailable();
        }
    }

    private static String recoveryJson(WorkerEffectRecoveryVO proof) {
        if (proof == null) return null;
        var value = new LinkedHashMap<String, Object>();
        value.put("mode", proof.mode().name());
        value.put("observedGeneration", proof.observedGeneration());
        value.put("observedAt", proof.observedAt().toString());
        value.put("validUntil", proof.validUntil().toString());
        value.put("authorityRef", proof.authorityRef());
        value.put("evidenceSha256", proof.evidenceSha256());
        value.put("businessReceiptId", proof.businessReceiptId());
        value.put("resultSha256", proof.resultSha256());
        return json(value, 4096);
    }

    private static WorkerEffectRecoveryVO recovery(String json) {
        Map<String, Object> value = JSON.readValue(json, OBJECT);
        if (!RECOVERY_FIELDS.equals(value.keySet()) || !(value.get("observedGeneration") instanceof Number generation)) throw unavailable();
        return new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.valueOf((String) value.get("mode")),
                generation.longValue(), Instant.parse((String) value.get("observedAt")), Instant.parse((String) value.get("validUntil")),
                (String) value.get("authorityRef"), (String) value.get("evidenceSha256"),
                (String) value.get("businessReceiptId"), (String) value.get("resultSha256"));
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
