package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.CmmnRuntimeService;
import org.flowable.cmmn.api.CmmnTaskService;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.runtime.PlanItemInstance;
import org.flowable.cmmn.engine.impl.persistence.entity.PlanItemInstanceEntity;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Same-source native observations and row fences. Engine writes always use the owning CMMN service. */
public final class CaseNativeAccess {
    private final DataSource source;
    private final CmmnRuntimeService runtime;
    private final CmmnHistoryService history;
    private final CmmnTaskService tasks;
    private final JdbcTemplate jdbc;
    private final String planTable;
    private final String taskTable;

    public CaseNativeAccess(DataSource source, SpringCmmnEngineConfiguration engine) {
        this.source = Objects.requireNonNull(source, "source"); Objects.requireNonNull(engine, "engine");
        var configured = engine.getDataSource(); if (configured instanceof TransactionAwareDataSourceProxy proxy) configured = proxy.getTargetDataSource();
        String schema = engine.getDatabaseSchema(), prefix = engine.getDatabaseTablePrefix();
        if (configured != source || !(engine.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() != source
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}") || prefix != null && !prefix.isEmpty() && !prefix.equals(schema + ".")) throw new IllegalArgumentException("Case native storage must match its exact owning engine");
        runtime = Objects.requireNonNull(engine.getCmmnRuntimeService(), "runtime"); history = Objects.requireNonNull(engine.getCmmnHistoryService(), "history"); tasks = Objects.requireNonNull(engine.getCmmnTaskService(), "tasks");
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); planTable = '"' + schema + "\".act_cmmn_ru_plan_item_inst"; taskTable = '"' + schema + "\".act_ru_task";
    }
    public Instant current(CaseContext context) {
        Objects.requireNonNull(context, "context"); requireTransaction();
        Instant now = Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant();
        if (!context.deadlineAt().isAfter(now) || Thread.currentThread().isInterrupted()) throw unavailable(); return now;
    }
    public Observation observe(CaseStartProvenanceStore.Entry origin) {
        Objects.requireNonNull(origin, "origin"); requireTransaction(); var seed = origin.seed(); String tenant = Long.toString(seed.owner().tenantId());
        var live = runtime.createCaseInstanceQuery().caseInstanceId(seed.caseInstanceId()).caseDefinitionId(seed.caseEngineDefinitionId()).caseInstanceTenantId(tenant).singleResult();
        String state; Instant finished = null;
        if (live != null) {
            affinity(seed, live.getId(), live.getCaseDefinitionId(), live.getTenantId());
            if (!seed.businessKey().equals(live.getBusinessKey())) throw unavailable(); state = live.getState();
        } else state = null;
        if (!"active".equals(state)) {
            var ended = history.createHistoricCaseInstanceQuery().caseInstanceId(seed.caseInstanceId()).caseDefinitionId(seed.caseEngineDefinitionId()).caseInstanceTenantId(tenant).singleResult();
            if (ended == null || ended.getEndTime() == null || !("completed".equals(ended.getState()) || "terminated".equals(ended.getState()))) throw unavailable();
            affinity(seed, ended.getId(), ended.getCaseDefinitionId(), ended.getTenantId());
            if (!seed.businessKey().equals(ended.getBusinessKey()) || state != null && !state.equals(ended.getState())) throw unavailable();
            state = ended.getState(); finished = ended.getEndTime().toInstant();
        }
        var liveItems = runtime.createPlanItemInstanceQuery().caseInstanceId(seed.caseInstanceId()).caseDefinitionId(seed.caseEngineDefinitionId()).planItemInstanceTenantId(tenant).includeEnded().listPage(0, 17);
        var historicItems = history.createHistoricPlanItemInstanceQuery().planItemInstanceCaseInstanceId(seed.caseInstanceId()).planItemInstanceCaseDefinitionId(seed.caseEngineDefinitionId()).planItemInstanceTenantId(tenant).listPage(0, 17);
        if (liveItems == null || historicItems == null || liveItems.size() > 16 || historicItems.size() > 16) throw unavailable();
        var items = new LinkedHashMap<String, PlanItem>();
        for (var item : liveItems) {
            var copied = runtimeItem(seed, item);
            if (items.put(copied.id(), copied) != null) throw unavailable();
        }
        var historicIds = new HashSet<String>();
        for (var item : historicItems) {
            var copied = historicItem(seed, item); if (!historicIds.add(copied.id())) throw unavailable();
            var liveCopy = items.putIfAbsent(copied.id(), copied);
            if (liveCopy != null && (!liveCopy.elementId().equals(copied.elementId()) || !liveCopy.definitionId().equals(copied.definitionId()) || !liveCopy.kind().equals(copied.kind()))) throw unavailable();
        }
        requireTransaction(); return new Observation(seed.caseInstanceId(), seed.caseEngineDefinitionId(), tenant, seed.businessKey(), state, finished, List.copyOf(items.values()));
    }

    /** Caller first holds the command lock and shared CaseScope advisory lock; fence PI then its possible native task. */
    public void lockPlanItem(CaseStartProvenanceStore.Entry origin, String planItemId) {
        Objects.requireNonNull(origin, "origin"); if (planItemId == null || planItemId.isBlank() || planItemId.length() > 255 || !planItemId.equals(planItemId.trim())) throw new IllegalArgumentException("Case plan item reference is invalid");
        requireTransaction(); var seed = origin.seed(); String tenant = Long.toString(seed.owner().tenantId());
        var rows = jdbc.query("SELECT p.id_ FROM " + planTable + " p WHERE p.case_inst_id_=? AND p.case_def_id_=? AND p.tenant_id_=? AND p.id_=? FOR UPDATE OF p",
                (rs, row) -> rs.getString(1), seed.caseInstanceId(), seed.caseEngineDefinitionId(), tenant, planItemId);
        if (rows.size() != 1 || !planItemId.equals(rows.getFirst())) throw new ProcessConflictException();
        var taskRows = jdbc.query("SELECT t.id_ FROM " + taskTable + " t WHERE t.scope_id_=? AND t.scope_definition_id_=? AND t.tenant_id_=? AND t.sub_scope_id_=? AND t.scope_type_='cmmn' "
                        + "AND t.proc_inst_id_ IS NULL AND t.proc_def_id_ IS NULL AND t.execution_id_ IS NULL AND t.parent_task_id_ IS NULL LIMIT 2 FOR UPDATE OF t",
                (rs, row) -> rs.getString(1), seed.caseInstanceId(), seed.caseEngineDefinitionId(), tenant, planItemId);
        if (taskRows.size() > 1) throw unavailable(); requireTransaction();
    }
    private PlanItem runtimeItem(CaseStartProvenanceStore.Seed seed, PlanItemInstance item) {
        if (!(item instanceof PlanItemInstanceEntity entity) || entity.getRevision() < 1) throw unavailable();
        affinity(seed, item.getCaseInstanceId(), item.getCaseDefinitionId(), item.getTenantId());
        var assignment = assignment(seed, item);
        return new PlanItem(item.getId(), item.getElementId(), item.getPlanItemDefinitionId(), item.getPlanItemDefinitionType(), item.getState(), false, entity.getRevision(),
                instant(item.getCreateTime()), instant(item.getLastAvailableTime()), instant(item.getLastEnabledTime()), instant(item.getLastDisabledTime()), instant(item.getLastStartedTime()),
                instant(item.getCompletedTime()), instant(item.getOccurredTime()), instant(item.getTerminatedTime()), instant(item.getEndedTime()), assignment.assignee(), assignment.revision());
    }
    private PlanItem historicItem(CaseStartProvenanceStore.Seed seed, HistoricPlanItemInstance item) {
        if (item == null) throw unavailable(); affinity(seed, item.getCaseInstanceId(), item.getCaseDefinitionId(), item.getTenantId());
        return new PlanItem(item.getId(), item.getElementId(), item.getPlanItemDefinitionId(), item.getPlanItemDefinitionType(), item.getState(), true, 0,
                instant(item.getCreateTime()), instant(item.getLastAvailableTime()), instant(item.getLastEnabledTime()), instant(item.getLastDisabledTime()), instant(item.getLastStartedTime()),
                instant(item.getCompletedTime()), instant(item.getOccurredTime()), instant(item.getTerminatedTime()), instant(item.getEndedTime()), null, 0);
    }
    private Assignment assignment(CaseStartProvenanceStore.Seed seed, PlanItemInstance item) {
        if (!"active".equals(item.getState()) || !org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK.equals(item.getPlanItemDefinitionType())) return new Assignment(null, 0);
        var rows = tasks.createTaskQuery().taskTenantId(Long.toString(seed.owner().tenantId())).scopeType("cmmn").scopeId(seed.caseInstanceId()).scopeDefinitionId(seed.caseEngineDefinitionId()).subScopeId(item.getId()).listPage(0, 2);
        if (rows == null || rows.size() > 1) throw unavailable(); if (rows.isEmpty()) return new Assignment(null, 0); var task = rows.getFirst();
        if (!(task instanceof TaskEntity entity) || entity.getRevision() < 1 || !Long.toString(seed.owner().tenantId()).equals(task.getTenantId()) || !"cmmn".equals(task.getScopeType())
                || !seed.caseInstanceId().equals(task.getScopeId()) || !seed.caseEngineDefinitionId().equals(task.getScopeDefinitionId()) || !item.getId().equals(task.getSubScopeId())
                || !item.getPlanItemDefinitionId().equals(task.getTaskDefinitionKey()) || task.getProcessInstanceId() != null || task.getProcessDefinitionId() != null || task.getExecutionId() != null
                || task.getParentTaskId() != null || task.getOwner() != null || task.getDelegationState() != null) throw unavailable();
        return new Assignment(task.getAssignee(), entity.getRevision());
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }
    private static void affinity(CaseStartProvenanceStore.Seed seed, String caseId, String definition, String tenant) {
        if (!seed.caseInstanceId().equals(caseId) || !seed.caseEngineDefinitionId().equals(definition) || !Long.toString(seed.owner().tenantId()).equals(tenant)) throw unavailable();
    }
    private static Instant instant(Date value) { return value == null ? null : value.toInstant(); }
    public record Observation(String caseId, String definitionId, String tenantId, String businessKey, String state, Instant finishedAt, List<PlanItem> items) {
        public Observation { items = List.copyOf(items); }
    }
    public record PlanItem(String id, String elementId, String definitionId, String kind, String state, boolean historical, int revision,
                           Instant createdAt, Instant availableAt, Instant enabledAt, Instant disabledAt, Instant startedAt,
                           Instant completedAt, Instant occurredAt, Instant terminatedAt, Instant endedAt, String assignee, int taskRevision) { }
    private record Assignment(String assignee, int revision) { }
    private static IllegalStateException unavailable() { return new IllegalStateException("Native Case state or source is unavailable"); }
}
