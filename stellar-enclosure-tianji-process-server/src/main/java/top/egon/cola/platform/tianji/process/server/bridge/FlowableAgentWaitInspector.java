package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.Objects;

/** Reads exact, release-bound native waiting facts. The caller owns any transaction that applies a result. */
public final class FlowableAgentWaitInspector {
    private final SpringProcessEngineConfiguration process;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final ProcessModelValidator validator;

    public FlowableAgentWaitInspector(SpringProcessEngineConfiguration process, ProcessReleaseService releases,
                                      VerifiedProcessModels models) {
        this(process, releases, models, new ProcessModelValidator());
    }

    public FlowableAgentWaitInspector(SpringProcessEngineConfiguration process, ProcessReleaseService releases,
                                      VerifiedProcessModels models, ProcessModelValidator validator) {
        this.process = Objects.requireNonNull(process, "process");
        this.releases = Objects.requireNonNull(releases, "releases");
        this.models = Objects.requireNonNull(models, "models");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public static void requireHistory(SpringProcessEngineConfiguration process) {
        if (process == null || process.getHistoryService() == null || !process.isDbHistoryUsed()
                || process.getHistoryLevel() == null || !process.getHistoryLevel().isAtLeast(HistoryLevel.ACTIVITY)
                || process.isAsyncHistoryEnabled()) {
            throw new IllegalArgumentException("Agent waits require synchronous Flowable activity history");
        }
    }

    public Observation inspect(AgentDispatchStore.Intent intent) {
        requireHistory(process);
        Objects.requireNonNull(intent, "intent");
        var seed = intent.seed();
        long tenantId = seed.binding().actor().tenantId();
        String tenant = Long.toString(tenantId);
        var release = releases.getForProcessDefinition(tenantId, seed.processDefinitionId());
        if (!seed.releaseKey().equals(release.releaseKey())) throw conflict();
        var definition = release.definitions().stream().filter(item -> item.type() == ValidatedProcessModel.Type.BPMN
                && item.engineDefinitionId().equals(seed.processDefinitionId())).findFirst().orElseThrow(FlowableAgentWaitInspector::conflict);
        if (!seed.processModelSha256().equals(definition.modelSha256())) throw conflict();
        var model = models.runningProcess(tenantId, definition);
        ValidatedProcessModel decision = null;
        if (!model.decisionTasks().isEmpty()) {
            String key = model.decisionTasks().values().iterator().next();
            var binding = release.definitions().stream().filter(item -> item.type() == ValidatedProcessModel.Type.DMN
                    && item.definitionKey().equals(key)).findFirst().orElseThrow(FlowableAgentWaitInspector::conflict);
            decision = models.decision(tenantId, binding);
            validator.validateDecisionBinding(model, definition.inputs(), decision);
        }
        validator.validateWorkerBindings(model, definition.inputs(), decision);
        var waits = model.messageWaits().entrySet().stream().filter(entry -> entry.getValue().workerElementId().equals(seed.elementId())).toList();
        if (waits.size() != 1) throw conflict();
        String activity = waits.getFirst().getKey();
        var wait = waits.getFirst().getValue();
        var instance = process.getRuntimeService().createProcessInstanceQuery().processInstanceId(seed.instanceId())
                .processInstanceTenantId(tenant).processDefinitionId(seed.processDefinitionId()).singleResult();
        if (instance == null) return new Observation(State.PROCESS_ENDED, activity, wait, null);
        if (!seed.businessKey().equals(instance.getBusinessKey())) throw conflict();
        if (completed(tenant, seed, wait.timerElementId())) return new Observation(State.TIMER_WON, activity, wait, null);
        if (completed(tenant, seed, activity)) return new Observation(State.MESSAGE_CONSUMED, activity, wait, null);
        if (instance.isSuspended()) return new Observation(State.SUSPENDED, activity, wait, null);
        var subscriptions = process.getRuntimeService().createEventSubscriptionQuery().tenantId(tenant)
                .processInstanceId(seed.instanceId()).processDefinitionId(seed.processDefinitionId()).activityId(activity)
                .eventType("message").eventName(wait.messageName()).listPage(0, 2);
        if (subscriptions.isEmpty()) return new Observation(State.ADVANCING, activity, wait, null);
        if (subscriptions.size() != 1) throw conflict();
        var subscription = subscriptions.getFirst();
        if (!tenant.equals(subscription.getTenantId()) || !seed.instanceId().equals(subscription.getProcessInstanceId())
                || !seed.processDefinitionId().equals(subscription.getProcessDefinitionId()) || !activity.equals(subscription.getActivityId())
                || !"message".equals(subscription.getEventType()) || !wait.messageName().equals(subscription.getEventName())
                || subscription.getExecutionId() == null) throw conflict();
        var execution = process.getRuntimeService().createExecutionQuery().executionId(subscription.getExecutionId())
                .executionTenantId(tenant).processInstanceId(seed.instanceId()).processDefinitionId(seed.processDefinitionId())
                .activityId(activity).singleResult();
        if (execution == null) return new Observation(State.ADVANCING, activity, wait, null);
        if (!subscription.getExecutionId().equals(execution.getId()) || !tenant.equals(execution.getTenantId()) || !seed.instanceId().equals(execution.getProcessInstanceId())
                || !activity.equals(execution.getActivityId()) || execution.isEnded()) throw conflict();
        return execution.isSuspended() ? new Observation(State.SUSPENDED, activity, wait, null)
                : new Observation(State.WAITING, activity, wait, subscription.getExecutionId());
    }

    private boolean completed(String tenant, AgentDispatchStore.Seed seed, String activity) {
        var history = process.getHistoryService().createHistoricActivityInstanceQuery().activityTenantId(tenant)
                .processInstanceId(seed.instanceId()).processDefinitionId(seed.processDefinitionId()).activityId(activity).finished().listPage(0, 2);
        if (history.size() > 1) throw conflict();
        if (history.isEmpty()) return false;
        HistoricActivityInstance item = history.getFirst();
        if (!tenant.equals(item.getTenantId()) || !seed.instanceId().equals(item.getProcessInstanceId())
                || !seed.processDefinitionId().equals(item.getProcessDefinitionId()) || !activity.equals(item.getActivityId())
                || item.getEndTime() == null) throw conflict();
        // The event gateway records a delete reason for its losing branch; only normal completion wins.
        return item.getDeleteReason() == null;
    }

    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    public enum State { ADVANCING, WAITING, TIMER_WON, MESSAGE_CONSUMED, PROCESS_ENDED, SUSPENDED }
    public record Observation(State state, String activityId, ValidatedProcessModel.MessageWait modelWait, String executionId) { }
}
