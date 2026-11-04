package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.job.api.Job;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessActiveWaitKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
        executionTable = '"' + schema + "\".act_ru_execution";
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
