package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.spring.SpringProcessEngineConfiguration;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.Objects;

/** Reads exact released Case-source and parent-wait references inside its caller's Process transaction. */
public final class FlowableCaseWaitInspector {
    private final SpringProcessEngineConfiguration process;
    private final ProcessReleaseService releases;
    private final VerifiedProcessModels models;
    private final ProcessModelValidator validator;

    public FlowableCaseWaitInspector(SpringProcessEngineConfiguration process, ProcessReleaseService releases, VerifiedProcessModels models, ProcessModelValidator validator) {
        this.process = Objects.requireNonNull(process, "process"); this.releases = Objects.requireNonNull(releases, "releases");
        this.models = Objects.requireNonNull(models, "models"); this.validator = Objects.requireNonNull(validator, "validator");
    }

    public Observation inspect(CaseStartProvenanceStore.Entry provenance) {
        FlowableAgentWaitInspector.requireHistory(process); Objects.requireNonNull(provenance, "provenance"); var seed = provenance.seed();
        if (!"LINKED_BPMN".equals(seed.startKind())) throw conflict();
        long tenantId = seed.owner().tenantId(); String tenant = Long.toString(tenantId);
        var release = releases.getForProcessDefinition(tenantId, seed.parentEngineDefinitionId());
        if (!seed.releaseKey().equals(release.releaseKey())) throw conflict();
        var definitions = release.definitions().stream().filter(value -> value.type() == ValidatedProcessModel.Type.BPMN && value.engineDefinitionId().equals(seed.parentEngineDefinitionId())).toList();
        if (definitions.size() != 1) throw conflict(); var definition = definitions.getFirst();
        if (!seed.parentDefinitionKey().equals(definition.definitionKey()) || !seed.parentModelSha256().equals(definition.modelSha256())) throw conflict();
        var model = models.runningProcess(tenantId, definition); var source = model.caseSources().get(seed.sourceElementId());
        if (source == null || !seed.caseDefinitionKey().equals(source.caseDefinitionKey())) throw conflict();
        var decisions = model.decisionTasks().values().stream().distinct().toList(); if (decisions.size() > 1) throw conflict();
        ValidatedProcessModel decision = null;
        if (!decisions.isEmpty()) {
            String key = decisions.getFirst(); var decisionBinding = release.definitions().stream().filter(value -> value.type() == ValidatedProcessModel.Type.DMN && value.definitionKey().equals(key)).toList();
            if (decisionBinding.size() != 1) throw conflict(); decision = models.decision(tenantId, decisionBinding.getFirst()); validator.validateDecisionBinding(model, definition.inputs(), decision);
        }
        validator.validateWorkerBindings(model, definition.inputs(), decision); validator.validateHumanTaskBindings(model, definition.inputs(), decision);
        var sourceRows = process.getHistoryService().createHistoricActivityInstanceQuery().activityInstanceId(seed.sourceOccurrenceId()).activityTenantId(tenant)
                .processInstanceId(seed.parentProcessInstanceId()).processDefinitionId(seed.parentEngineDefinitionId()).activityId(seed.sourceElementId()).finished().listPage(0, 2);
        if (sourceRows == null || sourceRows.size() != 1) throw conflict(); var historicalSource = sourceRows.getFirst();
        if (!seed.sourceOccurrenceId().equals(historicalSource.getId()) || !normal(historicalSource, seed, tenant, seed.sourceElementId())) throw conflict();
        var instance = process.getRuntimeService().createProcessInstanceQuery().processInstanceId(seed.parentProcessInstanceId())
                .processInstanceTenantId(tenant).processDefinitionId(seed.parentEngineDefinitionId()).singleResult();
        if (instance == null) return observation(State.PROCESS_ENDED, seed, source, null);
        if (!seed.parentProcessInstanceId().equals(instance.getId()) || !seed.parentEngineDefinitionId().equals(instance.getProcessDefinitionId()) || !tenant.equals(instance.getTenantId()) || !seed.businessKey().equals(instance.getBusinessKey())) throw conflict();
        if (completed(seed, tenant, source.timerElementId())) return observation(State.TIMER_WON, seed, source, null);
        if (completed(seed, tenant, source.messageElementId())) return observation(State.MESSAGE_CONSUMED, seed, source, null);
        if (instance.isSuspended()) return observation(State.SUSPENDED, seed, source, null);
        var subscriptions = process.getRuntimeService().createEventSubscriptionQuery().tenantId(tenant).processInstanceId(seed.parentProcessInstanceId())
                .processDefinitionId(seed.parentEngineDefinitionId()).activityId(source.messageElementId()).eventType("message").eventName(source.messageName()).listPage(0, 2);
        if (subscriptions == null || subscriptions.size() > 1) throw conflict();
        if (subscriptions.isEmpty()) return observation(State.ADVANCING, seed, source, null); var subscription = subscriptions.getFirst();
        if (!tenant.equals(subscription.getTenantId()) || !seed.parentProcessInstanceId().equals(subscription.getProcessInstanceId()) || !seed.parentEngineDefinitionId().equals(subscription.getProcessDefinitionId())
                || !source.messageElementId().equals(subscription.getActivityId()) || !"message".equals(subscription.getEventType()) || !source.messageName().equals(subscription.getEventName())
                || subscription.getExecutionId() == null || subscription.getExecutionId().isBlank()) throw conflict();
        var execution = process.getRuntimeService().createExecutionQuery().executionId(subscription.getExecutionId()).executionTenantId(tenant)
                .processInstanceId(seed.parentProcessInstanceId()).processDefinitionId(seed.parentEngineDefinitionId()).activityId(source.messageElementId()).singleResult();
        if (execution == null) return observation(State.ADVANCING, seed, source, null);
        if (!subscription.getExecutionId().equals(execution.getId()) || !tenant.equals(execution.getTenantId()) || !seed.parentProcessInstanceId().equals(execution.getProcessInstanceId())
                || !source.messageElementId().equals(execution.getActivityId()) || execution.isEnded()) throw conflict();
        return observation(execution.isSuspended() ? State.SUSPENDED : State.WAITING, seed, source, execution.isSuspended() ? null : subscription.getExecutionId());
    }

    private boolean completed(CaseStartProvenanceStore.Seed seed, String tenant, String activity) {
        var rows = process.getHistoryService().createHistoricActivityInstanceQuery().activityTenantId(tenant).processInstanceId(seed.parentProcessInstanceId())
                .processDefinitionId(seed.parentEngineDefinitionId()).activityId(activity).finished().listPage(0, 2);
        if (rows == null || rows.size() > 1) throw conflict(); if (rows.isEmpty()) return false;
        var item = rows.getFirst();
        if (!tenant.equals(item.getTenantId()) || !seed.parentProcessInstanceId().equals(item.getProcessInstanceId()) || !seed.parentEngineDefinitionId().equals(item.getProcessDefinitionId()) || !activity.equals(item.getActivityId()) || item.getEndTime() == null) throw conflict();
        return item.getDeleteReason() == null;
    }
    private static boolean normal(HistoricActivityInstance item, CaseStartProvenanceStore.Seed seed, String tenant, String activity) {
        return item != null && tenant.equals(item.getTenantId()) && seed.parentProcessInstanceId().equals(item.getProcessInstanceId()) && seed.parentEngineDefinitionId().equals(item.getProcessDefinitionId())
                && activity.equals(item.getActivityId()) && item.getEndTime() != null && item.getDeleteReason() == null;
    }
    private static Observation observation(State state, CaseStartProvenanceStore.Seed seed, ValidatedProcessModel.CaseSource source, String execution) {
        return new Observation(state, seed.sourceElementId(), source.messageElementId(), source.messageName(), source.timerElementId(), execution);
    }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    public enum State { WAITING, ADVANCING, SUSPENDED, PROCESS_ENDED, TIMER_WON, MESSAGE_CONSUMED }
    public record Observation(State state, String sourceElementId, String waitActivityId, String messageName, String timerActivityId, String executionId) {
        public Observation {
            if (state == null || sourceElementId == null || waitActivityId == null || messageName == null || timerActivityId == null || (state == State.WAITING) != (executionId != null)) throw new IllegalArgumentException("Case parent-wait observation is invalid");
        }
    }
}
