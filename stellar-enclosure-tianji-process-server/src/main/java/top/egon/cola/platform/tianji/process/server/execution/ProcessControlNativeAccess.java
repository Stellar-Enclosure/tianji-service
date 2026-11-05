package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.jobexecutor.TimerEventHandler;
import org.flowable.engine.impl.jobexecutor.TriggerTimerEventJobHandler;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobNotFoundException;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded same-source Flowable control facts and root fence. */
public final class ProcessControlNativeAccess {
    private static final String OWN_DELETE_REASON = "TIANJI_PROCESS_CANCELLED";
    private static final String RECOVERY_JOB_COLUMNS = "id_,rev_,type_,tenant_id_,process_instance_id_,proc_def_id_,"
            + "execution_id_,element_id_,scope_id_,sub_scope_id_,scope_type_,scope_definition_id_,"
            + "correlation_id_,handler_type_,handler_cfg_";
    private static final Comparator<ProcessActiveWaitDTO> WAIT_ORDER = Comparator
            .comparing((ProcessActiveWaitDTO wait) -> wait.kind().name())
            .thenComparing(ProcessActiveWaitDTO::activityKey);

    private final DataSource source;
    private final SpringProcessEngineConfiguration engine;
    private final RuntimeService runtime;
    private final HistoryService history;
    private final TaskService tasks;
    private final ManagementService management;
    private final JdbcTemplate jdbc;
    private final String executionTable;
    private final String recoveryTablePrefix;

    public ProcessControlNativeAccess(DataSource source, SpringProcessEngineConfiguration engine) {
        this.source = Objects.requireNonNull(source, "source");
        this.engine = Objects.requireNonNull(engine, "engine");
        DataSource configured = engine.getDataSource();
        if (configured instanceof TransactionAwareDataSourceProxy proxy) configured = proxy.getTargetDataSource();
        String schema = engine.getDatabaseSchema();
        String prefix = engine.getDatabaseTablePrefix();
        RuntimeService runtimeService = engine.getRuntimeService();
        HistoryService historyService = engine.getHistoryService();
        TaskService taskService = engine.getTaskService();
        ManagementService managementService = engine.getManagementService();
        if (configured != source || !(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() != source || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")
                || prefix != null && !prefix.isEmpty() && !prefix.equals(schema + ".")
                || !engine.isDbHistoryUsed() || engine.getHistoryLevel() == null
                || !engine.getHistoryLevel().isAtLeast(HistoryLevel.INSTANCE) || engine.isAsyncHistoryEnabled()
                || runtimeService == null || historyService == null || taskService == null || managementService == null) {
            throw new IllegalArgumentException("Process control native storage must match its exact owning engine");
        }
        runtime = runtimeService;
        history = historyService;
        tasks = taskService;
        management = managementService;
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        recoveryTablePrefix = '"' + schema + "\".";
        executionTable = recoveryTablePrefix + "act_ru_execution";
    }

    public boolean usesDataSource(DataSource candidate) {
        return source == candidate;
    }

    public boolean usesEngine(SpringProcessEngineConfiguration candidate) {
        return engine == candidate;
    }

    public ControlFacts observe(ProcessCommandReceiptStore.TaskOrigin start,
                                ProcessReleaseService.Definition definition,
                                ValidatedProcessModel model) {
        if (model == null) throw invalid();
        Scope scope = scope(start, definition, model);
        requireTransaction();
        var live = runtime.createProcessInstanceQuery().processInstanceId(scope.instanceId())
                .processInstanceTenantId(scope.tenantText()).singleResult();
        if (live == null) {
            TerminalFacts terminal = terminal(scope, null, false);
            requireTransaction();
            return new ControlFacts(null, terminal, List.of());
        }
        if (!scope.instanceId().equals(live.getId()) || !scope.definitionId().equals(live.getProcessDefinitionId())
                || !scope.tenantText().equals(live.getTenantId()) || !textValue(live.getBusinessKey(), 255)) throw unavailable();
        LockedRoot root = readRoot(scope, false).orElseThrow(ProcessControlNativeAccess::unavailable);
        if (!root.businessKey().equals(live.getBusinessKey()) || (root.suspensionState() == 2) != live.isSuspended()) throw unavailable();

        List<Task> taskRows = tasks.createTaskQuery().taskTenantId(scope.tenantText()).processInstanceId(scope.instanceId())
                .processDefinitionId(scope.definitionId()).listPage(0, 257);
        var externalRows = management.createExternalWorkerJobQuery().jobTenantId(scope.tenantText())
                .processInstanceId(scope.instanceId()).processDefinitionId(scope.definitionId()).listPage(0, 257);
        List<Job> timerRows = management.createTimerJobQuery().timers().jobTenantId(scope.tenantText())
                .processInstanceId(scope.instanceId()).processDefinitionId(scope.definitionId()).listPage(0, 257);
        List<EventSubscription> messageRows = runtime.createEventSubscriptionQuery().eventType("message")
                .tenantId(scope.tenantText()).processInstanceId(scope.instanceId()).processDefinitionId(scope.definitionId())
                .listPage(0, 257);
        bounded(taskRows); bounded(externalRows); bounded(timerRows); bounded(messageRows);

        Map<OccurrenceKey, ArtifactType> occurrences = new LinkedHashMap<>();
        Map<String, OccurrenceKey> supportingIds = new HashMap<>();
        for (Task task : taskRows) {
            artifact(scope, occurrences, supportingIds, ArtifactType.HUMAN_TASK, task.getId(), task.getExecutionId(),
                    task.getTaskDefinitionKey(), task.getTenantId(), task.getProcessInstanceId(), task.getProcessDefinitionId());
        }
        for (var job : externalRows) {
            artifact(scope, occurrences, supportingIds, ArtifactType.EXTERNAL_WORKER, job.getId(), job.getExecutionId(),
                    job.getElementId(), job.getTenantId(), job.getProcessInstanceId(), job.getProcessDefinitionId());
        }
        for (Job job : timerRows) {
            artifact(scope, occurrences, supportingIds, ArtifactType.TIMER, job.getId(), job.getExecutionId(),
                    job.getElementId(), job.getTenantId(), job.getProcessInstanceId(), job.getProcessDefinitionId());
        }
        for (EventSubscription subscription : messageRows) {
            if (!"message".equals(subscription.getEventType())) throw unavailable();
            artifact(scope, occurrences, supportingIds, ArtifactType.MESSAGE, subscription.getId(),
                    subscription.getExecutionId(), subscription.getActivityId(), subscription.getTenantId(),
                    subscription.getProcessInstanceId(), subscription.getProcessDefinitionId());
        }
        if (occurrences.size() > 256) throw unavailable();
        if (!occurrences.isEmpty()) verifyExecutions(scope, occurrences.keySet());
        List<ProcessActiveWaitDTO> waits = summaries(occurrences, model);
        requireTransaction();
        return new ControlFacts(root, null, waits);
    }

    public Optional<LockedRoot> lockRoot(ProcessCommandReceiptStore.TaskOrigin start) {
        Scope scope = scope(start, null, null);
        requireTransaction();
        Optional<LockedRoot> result = readRoot(scope, true);
        requireTransaction();
        return result;
    }

    public TerminalFacts terminalHistory(ProcessCommandReceiptStore.TaskOrigin start,
                                         ProcessReleaseService.Definition definition,
                                         String expectedBusinessKey,
                                         boolean requireOwnCancellation) {
        if (!textValue(expectedBusinessKey, 255)) throw invalid();
        Scope scope = scope(start, definition, null);
        requireTransaction();
        TerminalFacts result = terminal(scope, expectedBusinessKey, requireOwnCancellation);
        requireTransaction();
        return result;
    }

    public Instant now() {
        requireTransaction();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null) throw unavailable();
        return value.toInstant();
    }

    Instant beginMigrationObservation() {
        requireBoundMigrationTransaction();
        jdbc.execute("SET TRANSACTION READ ONLY");
        jdbc.execute("SET LOCAL statement_timeout = '2s'");
        requireMigrationReadOnly();
        Timestamp observed = jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()", Timestamp.class);
        if (observed == null) throw unavailable();
        return observed.toInstant();
    }

    void requireMigrationReadOnly() {
        requireBoundMigrationTransaction();
        if (!ZoneOffset.UTC.equals(ZoneId.systemDefault().normalized())
                || !Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT NOT pg_catalog.pg_is_in_recovery()
                            AND pg_catalog.current_schema()=?
                            AND pg_catalog.current_setting('server_encoding')='UTF8'
                            AND pg_catalog.current_setting('session_replication_role')='origin'
                            AND pg_catalog.current_setting('transaction_read_only')='on'
                            AND pg_catalog.current_setting('transaction_isolation')='repeatable read'
                            AND pg_catalog.current_setting('TimeZone')='UTC'
                        """, Boolean.class, engine.getDatabaseSchema()))) throw unavailable();
    }

    private void requireBoundMigrationTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)
                || Thread.currentThread().isInterrupted()) throw unavailable();
    }

    void verifyMigrationReadCoverage() {
        var columns = migrationColumns();
        var parameters = new ArrayList<Object>(columns.size() * 3 + 1);
        for (var column : columns) {
            parameters.add(column.table());
            parameters.add(column.name());
            parameters.add(column.type());
        }
        parameters.add(engine.getDatabaseSchema());
        String rows = String.join(",", java.util.Collections.nCopies(columns.size(), "(?::text,?::text,?::text)"));
        String sql = "WITH required(table_name,column_name,type_name) AS (VALUES " + rows + ") " + """
                SELECT NOT EXISTS (
                    SELECT 1 FROM required r
                    LEFT JOIN pg_catalog.pg_namespace n ON n.nspname=?
                    LEFT JOIN pg_catalog.pg_class c ON c.relnamespace=n.oid AND c.relname=r.table_name
                    LEFT JOIN pg_catalog.pg_attribute a ON a.attrelid=c.oid AND a.attname=r.column_name
                        AND a.attnum>0 AND NOT a.attisdropped
                    WHERE c.oid IS NULL OR c.relkind<>'r' OR c.relpersistence<>'p' OR c.relispartition
                        OR c.relrowsecurity OR c.relforcerowsecurity
                        OR NOT pg_catalog.has_schema_privilege(n.oid,'USAGE')
                        OR NOT pg_catalog.has_table_privilege(c.oid,'SELECT')
                        OR EXISTS (SELECT 1 FROM pg_catalog.pg_inherits WHERE inhrelid=c.oid OR inhparent=c.oid)
                        OR a.atttypid IS DISTINCT FROM pg_catalog.to_regtype(r.type_name)
                )
                """;
        if (!Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters.toArray()))) throw unavailable();
    }

    Optional<LockedRoot> readMigrationRoot(ProcessCommandReceiptStore.TaskOrigin start) {
        var root = readRecoveryRoot(start);
        if (root.isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM "
                + executionTable + " WHERE id_=?)", Boolean.class, start.instanceId()))) throw unavailable();
        return root;
    }

    boolean hasExactPlainTimer(ProcessCommandReceiptStore.TaskOrigin start, LockedRoot root, String activityId, Instant observedAt) {
        var scope = scope(start, null, null);
        if (!definitionValue(activityId)) throw invalid();
        var rows = jdbc.query("""
                SELECT id_,rev_,proc_inst_id_,root_proc_inst_id_,parent_id_,proc_def_id_,tenant_id_,act_id_,
                    suspension_state_,is_active_,is_scope_,is_concurrent_,is_event_scope_,is_mi_root_,
                    super_exec_ IS NULL AND callback_id_ IS NULL AND callback_type_ IS NULL
                        AND reference_id_ IS NULL AND reference_type_ IS NULL AND propagated_stage_inst_id_ IS NULL AS unlinked,
                    lock_owner_ IS NULL AND lock_time_ IS NULL AS unlocked
                FROM %s WHERE proc_inst_id_=? OR root_proc_inst_id_=? LIMIT 3
                """.formatted(executionTable), (rs, row) -> new MigrationExecution(
                rs.getString("id_"), rs.getInt("rev_"), rs.getString("proc_inst_id_"), rs.getString("root_proc_inst_id_"),
                rs.getString("parent_id_"), rs.getString("proc_def_id_"), rs.getString("tenant_id_"), rs.getString("act_id_"),
                rs.getInt("suspension_state_"), rs.getObject("is_active_", Boolean.class),
                rs.getObject("is_scope_", Boolean.class), rs.getObject("is_concurrent_", Boolean.class),
                rs.getObject("is_event_scope_", Boolean.class), rs.getObject("is_mi_root_", Boolean.class),
                rs.getBoolean("unlinked"), rs.getBoolean("unlocked")), scope.instanceId(), scope.instanceId());
        if (rows.stream().anyMatch(row -> !scope.tenantText().equals(row.tenant()) || !textValue(row.id(), 64))) {
            throw unavailable();
        }
        if (rows.size() != 2) return false;
        var owner = rows.stream().filter(row -> scope.instanceId().equals(row.id())).findFirst().orElseThrow(ProcessControlNativeAccess::unavailable);
        var leaf = rows.getFirst().equals(owner) ? rows.getLast() : rows.getFirst();
        if (!sameMigrationExecutionScope(owner, scope, root) || !sameMigrationExecutionScope(leaf, scope, root)) return false;
        if (owner.revision() != root.revision() || owner.parentId() != null || owner.activityId() != null
                || owner.rootId() != null && !scope.instanceId().equals(owner.rootId())
                || !Boolean.TRUE.equals(owner.scope()) || !scope.instanceId().equals(leaf.parentId())
                || !scope.instanceId().equals(leaf.rootId()) || !activityId.equals(leaf.activityId())
                || !Boolean.TRUE.equals(leaf.active()) || !Boolean.FALSE.equals(leaf.scope())) return false;

        // Pinned ordinary timer JSON has only an ASCII activity id; the engine helper needs a command context.
        String handlerConfiguration = "{\"" + TimerEventHandler.PROPERTYNAME_TIMER_ACTIVITY_ID + "\":\"" + activityId + "\"}";
        var timers = jdbc.query("""
                SELECT tenant_id_= ? AND proc_def_id_= ? AND process_instance_id_= ? AND execution_id_= ?
                    AND element_id_= ? AND rev_>0 AND type_='timer' AND handler_type_= ? AND handler_cfg_= ?
                    AND repeat_ IS NULL AND lock_owner_ IS NULL AND lock_exp_time_ IS NULL AND exception_msg_ IS NULL
                    AND scope_id_ IS NULL AND sub_scope_id_ IS NULL AND scope_type_ IS NULL
                    AND scope_definition_id_ IS NULL AND correlation_id_ IS NULL
                    AND duedate_ AT TIME ZONE 'UTC' > ? AS exact_timer
                FROM %sact_ru_timer_job
                WHERE process_instance_id_=? OR execution_id_ IN (?,?) LIMIT 2
                """.formatted(recoveryTablePrefix), (rs, row) -> rs.getBoolean("exact_timer"),
                scope.tenantText(), root.engineDefinitionId(), scope.instanceId(), leaf.id(), activityId,
                TriggerTimerEventJobHandler.TYPE, handlerConfiguration,
                java.time.OffsetDateTime.ofInstant(observedAt, ZoneOffset.UTC), scope.instanceId(), owner.id(), leaf.id());
        return timers.size() == 1 && timers.getFirst() && !hasOtherMigrationState(start, owner.id(), leaf.id());
    }

    private static boolean sameMigrationExecutionScope(MigrationExecution row, Scope scope, LockedRoot root) {
        return scope.instanceId().equals(row.instanceId()) && root.engineDefinitionId().equals(row.definitionId())
                && row.revision() > 0 && row.suspensionState() == 1 && Boolean.FALSE.equals(row.concurrent())
                && Boolean.FALSE.equals(row.eventScope()) && Boolean.FALSE.equals(row.miRoot()) && row.unlinked() && row.unlocked();
    }

    private boolean hasOtherMigrationState(ProcessCommandReceiptStore.TaskOrigin start, String rootId, String leafId) {
        String instance = start.instanceId();
        for (String table : List.of("act_ru_job", "act_ru_suspended_job", "act_ru_deadletter_job", "act_ru_external_job")) {
            if (migrationExists(table, "process_instance_id_=? OR execution_id_ IN (?,?)", instance, rootId, leafId)) return true;
        }
        for (String table : List.of("act_ru_task", "act_ru_event_subscr", "act_ru_variable")) {
            if (migrationExists(table, "proc_inst_id_=? OR execution_id_ IN (?,?)", instance, rootId, leafId)) return true;
        }
        long tenant = start.requester().tenantId();
        return migrationExists("tianji_worker_lease_receipt", "tenant_id=? AND scope_type='BPMN' AND scope_id=?", tenant, instance)
                || migrationExists("tianji_process_agent_dispatch", "tenant_id=? AND scope_type='BPMN' AND scope_id=?", tenant, instance)
                || migrationExists("tianji_process_agent_result_inbox", "tenant_id=? AND scope_type='BPMN' AND scope_id=?", tenant, instance)
                || migrationExists("tianji_case_start_provenance", "tenant_id=? AND start_kind='LINKED_BPMN' AND parent_process_instance_id=?", tenant, instance)
                || migrationExists("tianji_process_worker_recovery_receipt", "tenant_id=? AND process_instance_id=?", tenant, instance);
    }

    private boolean migrationExists(String fixedTable, String fixedPredicate, Object... arguments) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + recoveryTablePrefix + fixedTable
                + " WHERE " + fixedPredicate + ')', Boolean.class, arguments);
        if (found == null) throw unavailable();
        return found;
    }

    private static List<MigrationColumn> migrationColumns() {
        var columns = new ArrayList<MigrationColumn>();
        migrationColumns(columns, "act_ru_execution", "varchar", "id_", "proc_inst_id_", "root_proc_inst_id_", "parent_id_",
                "proc_def_id_", "tenant_id_", "act_id_", "super_exec_", "callback_id_", "callback_type_", "reference_id_",
                "reference_type_", "propagated_stage_inst_id_", "lock_owner_", "business_key_");
        migrationColumns(columns, "act_ru_execution", "int4", "rev_", "suspension_state_");
        migrationColumns(columns, "act_ru_execution", "bool", "is_active_", "is_scope_", "is_concurrent_", "is_event_scope_", "is_mi_root_");
        migrationColumns(columns, "act_ru_execution", "timestamp", "lock_time_");
        migrationColumns(columns, "act_ru_timer_job", "varchar", "tenant_id_", "proc_def_id_", "process_instance_id_", "execution_id_",
                "element_id_", "type_", "handler_type_", "handler_cfg_", "repeat_", "lock_owner_", "exception_msg_", "scope_id_",
                "sub_scope_id_", "scope_type_", "scope_definition_id_", "correlation_id_");
        migrationColumns(columns, "act_ru_timer_job", "int4", "rev_");
        migrationColumns(columns, "act_ru_timer_job", "timestamp", "lock_exp_time_", "duedate_");
        for (String table : List.of("act_ru_job", "act_ru_suspended_job", "act_ru_deadletter_job", "act_ru_external_job")) {
            migrationColumns(columns, table, "varchar", "process_instance_id_", "execution_id_");
        }
        for (String table : List.of("act_ru_task", "act_ru_event_subscr", "act_ru_variable")) {
            migrationColumns(columns, table, "varchar", "proc_inst_id_", "execution_id_");
        }
        for (String table : List.of("tianji_worker_lease_receipt", "tianji_process_agent_dispatch", "tianji_process_agent_result_inbox")) {
            migrationColumns(columns, table, "int8", "tenant_id");
            migrationColumns(columns, table, "varchar", "scope_type", "scope_id");
        }
        migrationColumns(columns, "tianji_case_start_provenance", "int8", "tenant_id");
        migrationColumns(columns, "tianji_case_start_provenance", "varchar", "start_kind", "parent_process_instance_id");
        migrationColumns(columns, "tianji_process_worker_recovery_receipt", "int8", "tenant_id");
        migrationColumns(columns, "tianji_process_worker_recovery_receipt", "varchar", "process_instance_id");
        migrationColumns(columns, "tianji_process_command_receipt", "int8", "tenant_id");
        migrationColumns(columns, "tianji_process_command_receipt", "varchar", "subject_type", "subject_id", "command_type",
                "command_id", "release_key", "correlation_id", "state", "resource_id");
        migrationColumns(columns, "tianji_process_command_receipt", "varchar", "subject_issuer");
        migrationColumns(columns, "tianji_process_command_receipt", "bpchar", "request_sha256");
        migrationColumns(columns, "tianji_process_command_receipt", "timestamptz", "created_at");
        migrationColumns(columns, "tianji_process_release", "int8", "tenant_id");
        migrationColumns(columns, "tianji_process_release", "varchar", "release_key", "state");
        migrationColumns(columns, "tianji_process_release", "bpchar", "manifest_sha256");
        migrationColumns(columns, "tianji_process_release", "timestamptz", "created_at");
        migrationColumns(columns, "tianji_process_release_definition", "int8", "tenant_id");
        migrationColumns(columns, "tianji_process_release_definition", "varchar", "release_key", "model_type", "definition_key",
                "engine_definition_id", "engine_deployment_id");
        migrationColumns(columns, "tianji_process_release_definition", "int4", "engine_version");
        migrationColumns(columns, "tianji_process_release_definition", "bpchar", "model_sha256");
        migrationColumns(columns, "tianji_process_release_definition", "jsonb", "input_schema");
        return columns;
    }

    private static void migrationColumns(List<MigrationColumn> columns, String table, String type, String... names) {
        for (String name : names) columns.add(new MigrationColumn(table, name, "pg_catalog." + type));
    }

    private record MigrationColumn(String table, String name, String type) { }

    private record MigrationExecution(String id, int revision, String instanceId, String rootId, String parentId,
                                      String definitionId, String tenant, String activityId, int suspensionState,
                                      Boolean active, Boolean scope, Boolean concurrent, Boolean eventScope, Boolean miRoot,
                                      boolean unlinked, boolean unlocked) { }

    public void verifyRecoveryTransaction() {
        requireTransaction();
        if (!engine.getDatabaseSchema().equals(jdbc.queryForObject("SELECT current_schema()", String.class))) {
            throw unavailable();
        }
    }

    public Optional<LockedRoot> readRecoveryRoot(ProcessCommandReceiptStore.TaskOrigin start) {
        requireTransaction();
        return readRoot(scope(start, null, null), false);
    }

    public String recoveryHistoryDefinitionId(ProcessCommandReceiptStore.TaskOrigin start) {
        requireTransaction();
        Scope scope = scope(start, null, null);
        var ended = history.createHistoricProcessInstanceQuery().processInstanceId(scope.instanceId())
                .processInstanceTenantId(scope.tenantText()).singleResult();
        if (ended == null || ended.getEndTime() == null || !scope.instanceId().equals(ended.getId())
                || !scope.tenantText().equals(ended.getTenantId()) || !textValue(ended.getProcessDefinitionId(), 255)) {
            throw unavailable();
        }
        return ended.getProcessDefinitionId();
    }

    public List<RecoveryDeadletter> findRecoveryDeadletters(ProcessCommandReceiptStore.TaskOrigin start) {
        requireTransaction();
        Scope scope = scope(start, null, null);
        var rows = jdbc.query("SELECT " + RECOVERY_JOB_COLUMNS + " FROM " + recoveryTablePrefix + "act_ru_deadletter_job"
                        + " WHERE tenant_id_=? AND process_instance_id_=? ORDER BY id_ COLLATE \"C\" LIMIT 129",
                (rs, index) -> recoveryJob(rs), scope.tenantText(), scope.instanceId());
        if (rows.size() > 129 || rows.stream().anyMatch(row -> !scopedRecoveryJob(scope, row))) throw unavailable();
        return List.copyOf(rows);
    }

    public Optional<RecoveryDeadletter> lockRecoveryDeadletter(ProcessCommandReceiptStore.TaskOrigin start,
            ProcessReleaseService.Definition definition, String jobId) {
        requireTransaction();
        Scope scope = scope(start, Objects.requireNonNull(definition, "definition"), null);
        if (!textValue(jobId, 255) || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw invalid();
        var rows = jdbc.query("SELECT " + RECOVERY_JOB_COLUMNS + " FROM " + recoveryTablePrefix + "act_ru_deadletter_job"
                        + " WHERE id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?"
                        + " AND type_='externalWorker' FOR UPDATE",
                (rs, index) -> recoveryJob(rs), jobId, scope.tenantText(), scope.instanceId(), scope.definitionId());
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        if (!scopedRecoveryJob(scope, row) || !jobId.equals(row.jobId()) || !Job.JOB_TYPE_EXTERNAL_WORKER.equals(row.type())) {
            throw unavailable();
        }
        return Optional.of(row);
    }

    public Optional<RecoveryExecution> readRecoveryExecution(ProcessCommandReceiptStore.TaskOrigin start,
            ProcessReleaseService.Definition definition, RecoveryDeadletter job) {
        requireTransaction();
        Scope scope = scope(start, Objects.requireNonNull(definition, "definition"), null);
        if (!scopedRecoveryJob(scope, job) || !textValue(job.executionId(), 255) || !definitionValue(job.elementId())) {
            return Optional.empty();
        }
        var rows = jdbc.query("SELECT id_,act_id_,proc_inst_id_,proc_def_id_,tenant_id_ FROM " + executionTable
                        + " WHERE id_=? AND tenant_id_=? AND proc_inst_id_=? AND proc_def_id_=?",
                (rs, index) -> new RecoveryExecution(rs.getString("id_"), rs.getString("act_id_"),
                        rs.getString("proc_inst_id_"), rs.getString("proc_def_id_"), rs.getString("tenant_id_")),
                job.executionId(), scope.tenantText(), scope.instanceId(), scope.definitionId());
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return Optional.empty();
        var row = rows.getFirst();
        if (!job.executionId().equals(row.executionId()) || !scope.instanceId().equals(row.instanceId())
                || !scope.definitionId().equals(row.definitionId()) || !scope.tenantText().equals(row.tenant())) {
            throw unavailable();
        }
        return job.elementId().equals(row.activityId()) ? Optional.of(row) : Optional.empty();
    }

    public boolean hasRecoveryCollision(String jobId) {
        requireTransaction();
        return !recoveryRuntimePresence(jobId).onlyDeadletter();
    }

    public boolean recoveryFailureCodeMatches(RecoveryDeadletter job, String code) {
        requireTransaction();
        if (job == null || job.revision() == null || job.revision() < 1 || code == null
                || !code.matches("[A-Z][A-Z0-9_]{0,63}")) return false;
        var rows = jdbc.query("SELECT COALESCE(octet_length(exception_msg_) = octet_length(CAST(? AS text))"
                        + " AND exception_msg_ COLLATE \"C\" = CAST(? AS text) COLLATE \"C\", false)"
                        + " FROM " + recoveryTablePrefix + "act_ru_deadletter_job"
                        + " WHERE id_=? AND tenant_id_=? AND process_instance_id_=? AND proc_def_id_=?"
                        + " AND type_='externalWorker' AND rev_=?",
                (rs, index) -> rs.getBoolean(1), code, code, job.jobId(), job.tenant(),
                job.instanceId(), job.definitionId(), job.revision());
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst();
    }

    public void moveAndVerifyRecovery(RecoveryDeadletter fenced) {
        verifyRecoveryTransaction();
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() || Context.getCommandContext() != null
                || fenced == null || fenced.revision() == null || fenced.revision() < 1 || !textValue(fenced.jobId(), 255)
                || !Job.JOB_TYPE_EXTERNAL_WORKER.equals(fenced.type()) || !"external-worker-complete".equals(fenced.handler())) {
            throw unavailable();
        }
        Job moved;
        try {
            moved = management.moveDeadLetterJobToExecutableJob(fenced.jobId(), 1);
        } catch (JobNotFoundException missing) {
            if (fenced.jobId().equals(missing.getJobId())) throw new ProcessVersionConflictException();
            throw unavailable();
        } catch (FlowableOptimisticLockingException race) {
            throw new ProcessVersionConflictException();
        }
        if (moved == null || moved.getRetries() != 1 || !sameMovedRecoveryJob(fenced,
                new RecoveryDeadletter(moved.getId(), fenced.revision(), moved.getJobType(), moved.getTenantId(),
                        moved.getProcessInstanceId(), moved.getProcessDefinitionId(), moved.getExecutionId(), moved.getElementId(),
                        moved.getScopeId(), moved.getSubScopeId(), moved.getScopeType(), moved.getScopeDefinitionId(),
                        moved.getCorrelationId(), moved.getJobHandlerType(), moved.getJobHandlerConfiguration()))) {
            throw unavailable();
        }
        if (!recoveryRuntimePresence(fenced.jobId()).onlyExternal()) throw unavailable();
        var rows = jdbc.query("SELECT " + RECOVERY_JOB_COLUMNS + ", retries_,"
                        + " (lock_owner_ IS NULL AND lock_exp_time_ IS NULL) AS recovery_unlocked"
                        + " FROM " + recoveryTablePrefix + "act_ru_external_job WHERE id_=?",
                (rs, index) -> rs.getInt("retries_") == 1 && rs.getBoolean("recovery_unlocked")
                        && Objects.equals(moved.getCorrelationId(), rs.getString("correlation_id_"))
                        && sameMovedRecoveryJob(fenced, recoveryJob(rs)), fenced.jobId());
        if (rows.size() != 1 || !rows.getFirst()) throw unavailable();
    }

    private RecoveryRuntimePresence recoveryRuntimePresence(String jobId) {
        if (!textValue(jobId, 255)) throw invalid();
        var rows = jdbc.query("SELECT EXISTS (SELECT 1 FROM " + recoveryTablePrefix + "act_ru_deadletter_job WHERE id_=?)"
                        + ", EXISTS (SELECT 1 FROM " + recoveryTablePrefix + "act_ru_external_job WHERE id_=?)"
                        + ", EXISTS (SELECT 1 FROM " + recoveryTablePrefix + "act_ru_job WHERE id_=?)"
                        + ", EXISTS (SELECT 1 FROM " + recoveryTablePrefix + "act_ru_timer_job WHERE id_=?)"
                        + ", EXISTS (SELECT 1 FROM " + recoveryTablePrefix + "act_ru_suspended_job WHERE id_=?)",
                (rs, index) -> new RecoveryRuntimePresence(rs.getBoolean(1), rs.getBoolean(2),
                        rs.getBoolean(3), rs.getBoolean(4), rs.getBoolean(5)), jobId, jobId, jobId, jobId, jobId);
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst();
    }

    private static boolean scopedRecoveryJob(Scope scope, RecoveryDeadletter job) {
        return job != null && textValue(job.jobId(), 255) && scope.tenantText().equals(job.tenant())
                && scope.instanceId().equals(job.instanceId())
                && (scope.definitionId() == null || scope.definitionId().equals(job.definitionId()));
    }

    private static RecoveryDeadletter recoveryJob(ResultSet rs) throws SQLException {
        return new RecoveryDeadletter(rs.getString("id_"), rs.getObject("rev_", Integer.class),
                rs.getString("type_"), rs.getString("tenant_id_"), rs.getString("process_instance_id_"),
                rs.getString("proc_def_id_"), rs.getString("execution_id_"), rs.getString("element_id_"),
                rs.getString("scope_id_"), rs.getString("sub_scope_id_"), rs.getString("scope_type_"),
                rs.getString("scope_definition_id_"), rs.getString("correlation_id_"), rs.getString("handler_type_"),
                rs.getString("handler_cfg_"));
    }

    private static boolean sameMovedRecoveryJob(RecoveryDeadletter expected, RecoveryDeadletter actual) {
        if (expected.correlationId() != null) return expected.equals(actual);
        return (actual.correlationId() == null || textValue(actual.correlationId(), 255))
                && expected.equals(new RecoveryDeadletter(actual.jobId(), actual.revision(), actual.type(), actual.tenant(),
                        actual.instanceId(), actual.definitionId(), actual.executionId(), actual.elementId(), actual.scopeId(),
                        actual.subScopeId(), actual.scopeType(), actual.scopeDefinitionId(), null, actual.handler(), actual.topic()));
    }

    private record RecoveryRuntimePresence(boolean deadletter, boolean external, boolean executable,
                                           boolean timer, boolean suspended) {
        private boolean onlyDeadletter() {
            return deadletter && !external && !executable && !timer && !suspended;
        }

        private boolean onlyExternal() {
            return !deadletter && external && !executable && !timer && !suspended;
        }
    }

    public record RecoveryDeadletter(String jobId, Integer revision, String type, String tenant, String instanceId,
                                     String definitionId, String executionId, String elementId, String scopeId,
                                     String subScopeId, String scopeType, String scopeDefinitionId, String correlationId,
                                     String handler, String topic) { }

    public record RecoveryExecution(String executionId, String activityId, String instanceId,
                                    String definitionId, String tenant) { }

    private Optional<LockedRoot> readRoot(Scope scope, boolean lock) {
        String sql = "SELECT id_,rev_,proc_def_id_,business_key_,suspension_state_,tenant_id_,proc_inst_id_ FROM "
                + executionTable + " WHERE id_=? AND proc_inst_id_=? AND tenant_id_=? AND parent_id_ IS NULL"
                + (lock ? " FOR UPDATE" : "");
        List<LockedRoot> rows = jdbc.query(sql, (rs, row) -> root(rs), scope.instanceId(), scope.instanceId(), scope.tenantText());
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) return Optional.empty();
        LockedRoot result = rows.getFirst();
        if (result.tenantId() != scope.tenantId() || !result.instanceId().equals(scope.instanceId())
                || scope.definitionId() != null && !result.engineDefinitionId().equals(scope.definitionId())) throw unavailable();
        return Optional.of(result);
    }

    private void verifyExecutions(Scope scope, Set<OccurrenceKey> occurrences) {
        Set<String> executionIds = new TreeSet<>();
        occurrences.forEach(key -> executionIds.add(key.executionId()));
        if (executionIds.size() > 256) throw unavailable();
        String placeholders = "?,".repeat(executionIds.size() - 1) + "?";
        List<Object> parameters = new ArrayList<>(3 + executionIds.size());
        parameters.add(scope.tenantText()); parameters.add(scope.instanceId()); parameters.add(scope.definitionId());
        parameters.addAll(executionIds);
        List<ExecutionAffinity> rows = jdbc.query("SELECT id_,act_id_,proc_inst_id_,proc_def_id_,tenant_id_ FROM "
                        + executionTable + " WHERE tenant_id_=? AND proc_inst_id_=? AND proc_def_id_=? AND id_ IN ("
                        + placeholders + ")", (rs, row) -> affinity(rs), parameters.toArray());
        if (rows.size() != executionIds.size()) throw unavailable();
        Map<String, ExecutionAffinity> byId = new HashMap<>();
        for (ExecutionAffinity row : rows) {
            if (!scope.tenantText().equals(row.tenantId()) || !scope.instanceId().equals(row.instanceId())
                    || !scope.definitionId().equals(row.definitionId()) || byId.put(row.executionId(), row) != null) throw unavailable();
        }
        for (OccurrenceKey occurrence : occurrences) {
            ExecutionAffinity row = byId.get(occurrence.executionId());
            if (row == null || !occurrence.activityId().equals(row.activityId())) throw unavailable();
        }
    }

    private static List<ProcessActiveWaitDTO> summaries(Map<OccurrenceKey, ArtifactType> occurrences,
                                                         ValidatedProcessModel model) {
        Map<WaitGroup, Integer> grouped = new TreeMap<>(Comparator
                .comparing((WaitGroup group) -> group.kind().name()).thenComparing(WaitGroup::activityKey));
        for (var entry : occurrences.entrySet()) {
            OccurrenceKey occurrence = entry.getKey();
            ProcessActiveWaitKindEnum kind = classify(entry.getValue(), occurrence.activityId(), model);
            grouped.merge(new WaitGroup(kind, occurrence.activityId()), 1, Integer::sum);
        }
        List<ProcessActiveWaitDTO> result = grouped.entrySet().stream()
                .map(entry -> new ProcessActiveWaitDTO(entry.getKey().activityKey(), entry.getKey().kind(), entry.getValue()))
                .toList();
        if (result.stream().mapToInt(ProcessActiveWaitDTO::occurrences).sum() > 256) throw unavailable();
        return result;
    }

    private static ProcessActiveWaitKindEnum classify(ArtifactType type, String activity,
                                                        ValidatedProcessModel model) {
        return switch (type) {
            case HUMAN_TASK -> {
                if (!model.humanTasks().containsKey(activity)) throw unavailable();
                yield ProcessActiveWaitKindEnum.HUMAN_TASK;
            }
            case EXTERNAL_WORKER -> {
                if (!model.workerTasks().containsKey(activity)) throw unavailable();
                yield ProcessActiveWaitKindEnum.EXTERNAL_WORKER;
            }
            case TIMER -> {
                if (!model.timerWaits().containsKey(activity)) throw unavailable();
                yield ProcessActiveWaitKindEnum.TIMER;
            }
            case MESSAGE -> {
                boolean agent = model.messageWaits().containsKey(activity);
                long cases = model.caseSources().values().stream()
                        .filter(source -> activity.equals(source.messageElementId())).count();
                if (agent == (cases == 1) || cases > 1) throw unavailable();
                yield agent ? ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT
                        : ProcessActiveWaitKindEnum.CASE_COMPLETION_WAIT;
            }
        };
    }

    private static void artifact(Scope scope, Map<OccurrenceKey, ArtifactType> occurrences,
                                 Map<String, OccurrenceKey> supportingIds, ArtifactType type,
                                 String artifactId, String executionId, String activityId,
                                 String tenantId, String instanceId, String definitionId) {
        if (!textValue(artifactId, 255) || !scope.tenantText().equals(tenantId)
                || !scope.instanceId().equals(instanceId) || !scope.definitionId().equals(definitionId)) throw unavailable();
        OccurrenceKey key = new OccurrenceKey(executionId, activityId);
        OccurrenceKey priorId = supportingIds.putIfAbsent(type.name() + ':' + artifactId, key);
        if (priorId != null && !priorId.equals(key)) throw unavailable();
        ArtifactType priorType = occurrences.putIfAbsent(key, type);
        if (priorType != null && priorType != type) throw unavailable();
    }

    private TerminalFacts terminal(Scope scope, String expectedBusinessKey, boolean requireOwnCancellation) {
        var ended = history.createHistoricProcessInstanceQuery().processInstanceId(scope.instanceId())
                .processInstanceTenantId(scope.tenantText()).singleResult();
        if (ended == null || ended.getEndTime() == null || !scope.instanceId().equals(ended.getId())
                || !scope.definitionId().equals(ended.getProcessDefinitionId())
                || !scope.tenantText().equals(ended.getTenantId()) || !textValue(ended.getBusinessKey(), 255)
                || expectedBusinessKey != null && !expectedBusinessKey.equals(ended.getBusinessKey())) throw unavailable();
        DeleteCategory category = ended.getDeleteReason() == null ? DeleteCategory.NORMAL_COMPLETION
                : OWN_DELETE_REASON.equals(ended.getDeleteReason()) ? DeleteCategory.TIANJI_PROCESS_CANCELLED
                : DeleteCategory.PRE_EXISTING_CANCELLATION;
        if (requireOwnCancellation && category != DeleteCategory.TIANJI_PROCESS_CANCELLED) throw unavailable();
        ProcessInstanceStatusEnum status = category == DeleteCategory.NORMAL_COMPLETION
                ? ProcessInstanceStatusEnum.COMPLETED : ProcessInstanceStatusEnum.CANCELLED;
        return new TerminalFacts(scope.tenantId(), scope.instanceId(), scope.definitionId(), ended.getBusinessKey(),
                status, ended.getEndTime().toInstant(), category);
    }

    private static Scope scope(ProcessCommandReceiptStore.TaskOrigin start,
                               ProcessReleaseService.Definition definition,
                               ValidatedProcessModel model) {
        if (start == null || start.requester() == null || "LEGACY".equals(start.requester().issuerFingerprint())
                || !textValue(start.instanceId(), 255)) throw invalid();
        if (definition == null) return new Scope(start.requester().tenantId(), Long.toString(start.requester().tenantId()),
                start.instanceId(), null);
        if (definition.type() != Type.BPMN || !definitionValue(definition.definitionKey())
                || !textValue(definition.engineDefinitionId(), 255)
                || model != null && (model.type() != Type.BPMN || !definition.definitionKey().equals(model.definitionKey()))) throw invalid();
        return new Scope(start.requester().tenantId(), Long.toString(start.requester().tenantId()),
                start.instanceId(), definition.engineDefinitionId());
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() "
                + "AND current_setting('server_encoding')='UTF8' "
                + "AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }

    private static LockedRoot root(ResultSet rs) throws SQLException {
        try {
            String tenant = rs.getString("tenant_id_");
            String id = rs.getString("id_");
            if (!Objects.equals(id, rs.getString("proc_inst_id_"))) throw unavailable();
            return new LockedRoot(Long.parseLong(tenant), id, rs.getString("proc_def_id_"),
                    rs.getString("business_key_"), rs.getInt("rev_"), rs.getInt("suspension_state_"));
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static ExecutionAffinity affinity(ResultSet rs) throws SQLException {
        try {
            return new ExecutionAffinity(rs.getString("id_"), rs.getString("act_id_"),
                    rs.getString("proc_inst_id_"), rs.getString("proc_def_id_"), rs.getString("tenant_id_"));
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static void bounded(List<?> rows) {
        if (rows == null || rows.size() > 256) throw unavailable();
    }

    public enum DeleteCategory {
        NORMAL_COMPLETION,
        TIANJI_PROCESS_CANCELLED,
        PRE_EXISTING_CANCELLATION
    }

    public record OccurrenceKey(String executionId, String activityId) {
        public OccurrenceKey {
            if (!textValue(executionId, 255) || !definitionValue(activityId)) throw invalid();
        }
    }

    public record LockedRoot(long tenantId, String instanceId, String engineDefinitionId,
                             String businessKey, int revision, int suspensionState) {
        public LockedRoot {
            if (tenantId < 1 || !textValue(instanceId, 255) || !textValue(engineDefinitionId, 255)
                    || !textValue(businessKey, 255) || revision < 1
                    || suspensionState != 1 && suspensionState != 2) throw invalid();
        }
    }

    public record TerminalFacts(long tenantId, String instanceId, String engineDefinitionId,
                                String businessKey, ProcessInstanceStatusEnum status, Instant finishedAt,
                                DeleteCategory deleteCategory) {
        public TerminalFacts {
            if (tenantId < 1 || !textValue(instanceId, 255) || !textValue(engineDefinitionId, 255)
                    || !textValue(businessKey, 255) || status == null || finishedAt == null || deleteCategory == null
                    || (deleteCategory == DeleteCategory.NORMAL_COMPLETION) != (status == ProcessInstanceStatusEnum.COMPLETED)
                    || deleteCategory != DeleteCategory.NORMAL_COMPLETION && status != ProcessInstanceStatusEnum.CANCELLED) throw invalid();
        }
    }

    public record ControlFacts(LockedRoot liveRoot, TerminalFacts terminal,
                               List<ProcessActiveWaitDTO> activeWaits) {
        public ControlFacts {
            try { activeWaits = List.copyOf(activeWaits); }
            catch (RuntimeException failure) { throw invalid(); }
            if ((liveRoot == null) == (terminal == null) || terminal != null && !activeWaits.isEmpty()) throw invalid();
            ProcessActiveWaitDTO previous = null;
            int count = 0;
            Set<String> keys = new HashSet<>();
            for (ProcessActiveWaitDTO wait : activeWaits) {
                if (previous != null && WAIT_ORDER.compare(previous, wait) >= 0
                        || !keys.add(wait.kind().name() + '\u0000' + wait.activityKey())) throw invalid();
                count += wait.occurrences(); previous = wait;
            }
            if (count > 256) throw invalid();
        }
    }

    private enum ArtifactType { HUMAN_TASK, EXTERNAL_WORKER, TIMER, MESSAGE }
    private record Scope(long tenantId, String tenantText, String instanceId, String definitionId) { }
    private record ExecutionAffinity(String executionId, String activityId, String instanceId,
                                     String definitionId, String tenantId) { }
    private record WaitGroup(ProcessActiveWaitKindEnum kind, String activityKey) { }

    private static boolean definitionValue(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    }

    private static boolean textValue(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process control native input is invalid");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process control native state is unavailable");
    }
}
