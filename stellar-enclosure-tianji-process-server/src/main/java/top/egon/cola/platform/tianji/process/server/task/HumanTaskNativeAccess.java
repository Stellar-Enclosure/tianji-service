package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.engine.TaskService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.api.runtime.PlanItemInstanceState;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskSelectionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Same-transaction native reference scan and row fence. It never updates Flowable tables or opens another transaction. */
public final class HumanTaskNativeAccess {
    private static final String PREFIX = "tianji.api.process.task.";
    private static final Set<String> WRITES = Set.of(PREFIX + "claim", PREFIX + "unclaim", PREFIX + "transfer", PREFIX + "complete");
    private static final Set<String> READS = Set.of(PREFIX + "get", PREFIX + "query", PREFIX + "adminQuery");
    private final DataSource source;
    private final SpringProcessEngineConfiguration engine;
    private final TaskService tasks;
    private final CmmnTaskService caseTasks;
    private final String provenanceTable;
    private final String definitionTable;
    private final String planItemTable;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final String taskTable;
    private final String identityTable;
    private final String receiptTable;

    public HumanTaskNativeAccess(DataSource source, SpringProcessEngineConfiguration engine) { this(source, engine, null); }

    public HumanTaskNativeAccess(DataSource source, SpringProcessEngineConfiguration engine, SpringCmmnEngineConfiguration cmmn) {
        this.source = Objects.requireNonNull(source, "source"); this.engine = Objects.requireNonNull(engine, "engine"); this.tasks = Objects.requireNonNull(engine.getTaskService(), "taskService");
        String schema = engine.getDatabaseSchema(); var configured = engine.getDataSource();
        if (configured instanceof TransactionAwareDataSourceProxy proxy) configured = proxy.getTargetDataSource();
        String prefix = engine.getDatabaseTablePrefix();
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || configured != source || !(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() != source || prefix != null && !prefix.isEmpty() && !prefix.equals(schema + ".")) throw new IllegalArgumentException("Human task native storage must match its exact owning engine");
        taskTable = '"' + schema + "\".act_ru_task"; identityTable = '"' + schema + "\".act_ru_identitylink"; receiptTable = '"' + schema + "\".tianji_process_command_receipt";
        provenanceTable = '\"' + schema + "\".tianji_case_start_provenance"; definitionTable = '\"' + schema + "\".tianji_process_release_definition";
        planItemTable = '\"' + schema + "\".act_cmmn_ru_plan_item_inst";
        if (cmmn != null) {
            var configuredCase = cmmn.getDataSource(); if (configuredCase instanceof TransactionAwareDataSourceProxy proxy) configuredCase = proxy.getTargetDataSource();
            String casePrefix = cmmn.getDatabaseTablePrefix();
            if (configuredCase != source || cmmn.getTransactionManager() != engine.getTransactionManager() || !schema.equals(cmmn.getDatabaseSchema())
                    || casePrefix != null && !casePrefix.isEmpty() && !casePrefix.equals(schema + ".")) throw new IllegalArgumentException("Case task storage must share the exact native transaction");
            caseTasks = Objects.requireNonNull(cmmn.getCmmnTaskService(), "cmmnTaskService");
        } else caseTasks = null;
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); named = new NamedParameterJdbcTemplate(jdbc);
    }

    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public boolean usesEngine(SpringProcessEngineConfiguration candidate) { return engine == candidate; }

    /** Rows are references only. Model policy and current-user read rules still decide which scanned tasks can be returned. */
    public List<String> candidates(HumanTaskContext context, ListHumanTasksQuery query, int maximum) {
        if (query.scopeType() == HumanTaskScopeTypeEnum.CASE) return caseCandidates(context, query, maximum);
        boolean admin = context.resourceKey().equals(PREFIX + "adminQuery");
        if (!admin && !context.resourceKey().equals(PREFIX + "query") || query.scopeType() != HumanTaskScopeTypeEnum.PROCESS || maximum < 1 || maximum > 50) throw new IllegalArgumentException("Human task scan is not supported in this context");
        current(context);
        if (!admin && query.selection() == HumanTaskSelectionEnum.CANDIDATE && context.roleIds().isEmpty()) return List.of();
        var parameters = parameters(context).addValue("maximum", maximum).addValue("userKey", context.userKey());
        var sql = new StringBuilder("SELECT t.id_ FROM ").append(taskTable).append(" t ").append(originJoin()).append(" WHERE ").append(nativeScope());
        if (!admin) {
            String candidate = context.roleIds().isEmpty() ? "FALSE" : "t.assignee_ IS NULL AND EXISTS (SELECT 1 FROM " + identityTable
                    + " i WHERE i.task_id_=t.id_ AND i.type_='candidate' AND i.user_id_ IS NULL AND i.group_id_ IN (:groups))";
            if (!context.roleIds().isEmpty()) parameters.addValue("groups", context.candidateGroups());
            if (query.selection() == HumanTaskSelectionEnum.CANDIDATE) sql.append(" AND (").append(candidate).append(')');
            else if (query.selection() == HumanTaskSelectionEnum.ASSIGNED) sql.append(" AND t.assignee_=:userKey");
            else sql.append(" AND (t.assignee_=:userKey OR (").append(candidate).append("))");
        } else if (query.selection() == HumanTaskSelectionEnum.CANDIDATE) sql.append(" AND t.assignee_ IS NULL");
        else if (query.selection() == HumanTaskSelectionEnum.ASSIGNED) sql.append(" AND t.assignee_=:userKey");
        if (query.releaseVersion() != null) { sql.append(" AND s.release_key=:release"); parameters.addValue("release", query.releaseVersion()); }
        if (query.scopeId() != null) { sql.append(" AND t.proc_inst_id_=:scope"); parameters.addValue("scope", query.scopeId()); }
        if (query.taskDefinitionKey() != null) { sql.append(" AND t.task_def_key_=:taskKey"); parameters.addValue("taskKey", query.taskDefinitionKey()); }
        if (query.afterTaskId() != null) { sql.append(" AND t.id_ COLLATE \"C\">:after"); parameters.addValue("after", query.afterTaskId()); }
        sql.append(" ORDER BY t.id_ COLLATE \"C\" LIMIT :maximum");
        var result = named.query(sql.toString(), parameters, (rs, number) -> rs.getString(1)); current(context); return List.copyOf(result);
    }

    public Optional<Task> find(HumanTaskContext context, String taskId, boolean lock) {
        if (taskId == null || taskId.isBlank() || !taskId.equals(taskId.trim()) || taskId.length() > 255) throw new IllegalArgumentException("Human task reference is invalid");
        if (!(lock ? WRITES : READS).contains(context.resourceKey())) throw new IllegalStateException("Human task context cannot use this native fence");
        current(context);
        String sql = "SELECT t.id_ FROM " + taskTable + " t " + originJoin() + " WHERE " + nativeScope() + " AND t.id_=:task" + (lock ? " FOR UPDATE OF t" : "");
        var rows = named.query(sql, parameters(context).addValue("task", taskId), (rs, number) -> rs.getString(1));
        current(context); if (rows.size() > 1) throw unavailable(); if (rows.isEmpty()) return Optional.empty();
        var task = tasks.createTaskQuery().taskId(taskId).taskTenantId(Long.toString(context.actor().tenantId())).singleResult();
        current(context); if (task == null) return Optional.empty(); if (!taskId.equals(task.getId()) || !Long.toString(context.actor().tenantId()).equals(task.getTenantId())) throw unavailable();
        return Optional.of(task);
    }

    /** Case-only discovery may use a write context without locking; its caller acquires CaseScope before requesting the row fence. */
    public Optional<Task> findCase(HumanTaskContext context, String taskId, boolean lock) {
        if (caseTasks == null) throw new IllegalArgumentException("Formal Case task support is not configured");
        if (taskId == null || taskId.isBlank() || !taskId.equals(taskId.trim()) || taskId.length() > 255) throw new IllegalArgumentException("Human task reference is invalid");
        if (lock ? !WRITES.contains(context.resourceKey()) : !WRITES.contains(context.resourceKey()) && !READS.contains(context.resourceKey())) throw new IllegalStateException("Human task context cannot use this native fence");
        current(context);
        String sql = "SELECT t.id_ FROM " + taskTable + " t " + caseOriginJoin() + " WHERE " + caseScope() + " AND t.id_=:task" + (lock ? " FOR UPDATE OF t" : "");
        var values = parameters(context).addValue("task", taskId).addValue("humanTaskKind", PlanItemDefinitionType.HUMAN_TASK).addValue("activeState", PlanItemInstanceState.ACTIVE);
        var rows = named.query(sql, values, (rs, number) -> rs.getString(1));
        current(context); if (rows.size() > 1) throw unavailable(); if (rows.isEmpty()) return Optional.empty();
        var task = caseTasks.createTaskQuery().taskId(taskId).taskTenantId(Long.toString(context.actor().tenantId())).singleResult();
        current(context); if (task == null) return Optional.empty();
        if (!taskId.equals(task.getId()) || !Long.toString(context.actor().tenantId()).equals(task.getTenantId()) || !"cmmn".equals(task.getScopeType())
                || task.getScopeId() == null || task.getScopeDefinitionId() == null || task.getSubScopeId() == null || task.getTaskDefinitionKey() == null
                || task.getProcessInstanceId() != null || task.getProcessDefinitionId() != null || task.getExecutionId() != null || task.getParentTaskId() != null) throw unavailable();
        return Optional.of(task);
    }

    private List<String> caseCandidates(HumanTaskContext context, ListHumanTasksQuery query, int maximum) {
        boolean admin = context.resourceKey().equals(PREFIX + "adminQuery");
        if (caseTasks == null || !admin && !context.resourceKey().equals(PREFIX + "query") || maximum < 1 || maximum > 50) throw new IllegalArgumentException("Case task scan is not supported in this context");
        current(context); if (!admin && query.selection() == HumanTaskSelectionEnum.CANDIDATE && context.roleIds().isEmpty()) return List.of();
        var values = parameters(context).addValue("maximum", maximum).addValue("userKey", context.userKey())
                .addValue("humanTaskKind", PlanItemDefinitionType.HUMAN_TASK).addValue("activeState", PlanItemInstanceState.ACTIVE);
        var sql = new StringBuilder("SELECT t.id_ FROM ").append(taskTable).append(" t ").append(caseOriginJoin()).append(" WHERE ").append(caseScope());
        if (!admin) {
            String candidate = context.roleIds().isEmpty() ? "FALSE" : "t.assignee_ IS NULL AND EXISTS (SELECT 1 FROM " + identityTable
                    + " i WHERE i.task_id_=t.id_ AND i.type_='candidate' AND i.user_id_ IS NULL AND i.group_id_ IN (:groups))";
            if (!context.roleIds().isEmpty()) values.addValue("groups", context.candidateGroups());
            if (query.selection() == HumanTaskSelectionEnum.CANDIDATE) sql.append(" AND (").append(candidate).append(')');
            else if (query.selection() == HumanTaskSelectionEnum.ASSIGNED) sql.append(" AND t.assignee_=:userKey");
            else sql.append(" AND (t.assignee_=:userKey OR (").append(candidate).append("))");
        } else if (query.selection() == HumanTaskSelectionEnum.CANDIDATE) sql.append(" AND t.assignee_ IS NULL");
        else if (query.selection() == HumanTaskSelectionEnum.ASSIGNED) sql.append(" AND t.assignee_=:userKey");
        if (query.releaseVersion() != null) { sql.append(" AND p.release_key=:release"); values.addValue("release", query.releaseVersion()); }
        if (query.scopeId() != null) { sql.append(" AND t.scope_id_=:scope"); values.addValue("scope", query.scopeId()); }
        if (query.taskDefinitionKey() != null) { sql.append(" AND t.task_def_key_=:taskKey"); values.addValue("taskKey", query.taskDefinitionKey()); }
        if (query.afterTaskId() != null) { sql.append(" AND t.id_ COLLATE \"C\">:after"); values.addValue("after", query.afterTaskId()); }
        sql.append(" ORDER BY t.id_ COLLATE \"C\" LIMIT :maximum");
        var result = named.query(sql.toString(), values, (rs, number) -> rs.getString(1)); current(context); return List.copyOf(result);
    }
    private String caseOriginJoin() {
        return "JOIN " + planItemTable + " i ON i.id_=t.sub_scope_id_ AND i.case_inst_id_=t.scope_id_ AND i.case_def_id_=t.scope_definition_id_ AND i.tenant_id_=t.tenant_id_ "
                + "AND i.item_definition_id_=t.task_def_key_ AND i.item_definition_type_=:humanTaskKind AND i.state_=:activeState AND i.ended_time_ IS NULL "
                + "JOIN " + provenanceTable + " p ON p.tenant_id=:tenant AND p.owner_issuer=:issuer AND p.case_instance_id=t.scope_id_ "
                + "JOIN " + receiptTable + " o ON o.tenant_id=p.tenant_id AND o.subject_type=p.owner_type AND o.subject_issuer=p.owner_issuer AND o.subject_id=p.owner_subject AND o.command_type=p.owner_start_command_type AND o.command_id=p.owner_start_command_id "
                + "AND o.state='APPLIED' AND o.request_sha256=p.owner_start_request_sha256 AND o.release_key=p.release_key AND o.correlation_id=p.correlation_id "
                + "AND ((p.start_kind='DIRECT' AND o.command_type='CASE_START' AND o.resource_id=p.case_instance_id) OR (p.start_kind='LINKED_BPMN' AND o.command_type='START' AND o.resource_id=p.parent_process_instance_id)) "
                + "JOIN " + receiptTable + " e ON e.tenant_id=p.tenant_id AND e.subject_type=p.executor_type AND e.subject_issuer=p.executor_issuer AND e.subject_id=p.executor_subject AND e.command_type='CASE_START' AND e.command_id=p.case_start_command_id "
                + "AND e.state='APPLIED' AND e.request_sha256=p.case_start_request_sha256 AND e.resource_id=p.case_instance_id AND e.release_key=p.release_key AND e.correlation_id=p.correlation_id "
                + "JOIN " + definitionTable + " d ON d.tenant_id=p.tenant_id AND d.release_key=p.release_key AND d.model_type='CMMN' AND d.definition_key=p.case_definition_key AND d.engine_definition_id=p.case_engine_definition_id AND d.model_sha256=p.case_model_sha256";
    }
    private static String caseScope() { return "t.tenant_id_=:nativeTenant AND t.scope_type_='cmmn' AND t.scope_id_=p.case_instance_id AND t.scope_definition_id_=p.case_engine_definition_id AND t.sub_scope_id_ IS NOT NULL AND t.proc_inst_id_ IS NULL AND t.proc_def_id_ IS NULL AND t.execution_id_ IS NULL AND t.parent_task_id_ IS NULL"; }

    public Instant current(HumanTaskContext context) {
        Objects.requireNonNull(context, "context");
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw new IllegalStateException("Human task native access requires its owning JDBC transaction");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Instant now = Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant();
        if (Thread.currentThread().isInterrupted() || !context.deadlineAt().isAfter(now)) throw unavailable(); return now;
    }
    private String originJoin() { return "JOIN " + receiptTable + " s ON s.tenant_id=:tenant AND s.subject_issuer=:issuer AND s.command_type='START' AND s.state='APPLIED' AND s.resource_id=t.proc_inst_id_"; }
    private static String nativeScope() { return "t.tenant_id_=:nativeTenant AND t.scope_type_ IS NULL AND t.scope_id_ IS NULL AND t.parent_task_id_ IS NULL"; }
    private static MapSqlParameterSource parameters(HumanTaskContext context) { return new MapSqlParameterSource().addValue("tenant", context.actor().tenantId()).addValue("issuer", context.actor().issuerFingerprint()).addValue("nativeTenant", Long.toString(context.actor().tenantId())); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Human task native source or deadline is unavailable"); }
}
