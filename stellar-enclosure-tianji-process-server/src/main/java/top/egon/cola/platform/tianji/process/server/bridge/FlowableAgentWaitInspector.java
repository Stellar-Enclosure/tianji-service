package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.runtime.Execution;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceBindingBO;
import top.egon.cola.platform.tianji.process.server.execution.AutomationInstanceGuardCommandInterceptor;
import top.egon.cola.platform.tianji.process.server.execution.ProcessControlNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeExecutionBO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessNodeLifecycleListener;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore.ImmutableReleaseVO;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels.VerifiedAutomationModelsVO;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

    /** Takes the exact native root fence before the caller locks its V5/V6 rows. */
    public TypedObservationVO inspectTyped(AutomationInstanceBindingBO root, ImmutableReleaseVO release,
            VerifiedAutomationModelsVO verified, ProcessNodeExecutionBO node, AgentTerminalEventV2 event) {
        requireHistory(process);
        requireTypedIdentity(root, verified, node, event);
        if (release.tenantId() != root.getTenantId() || !root.getReleaseKey().equals(release.releaseVersion())
                || !root.getDefinitionKey().equals(release.rootDefinitionKey())) throw conflict();
        var nodeBinding = release.source().nodeBindings().stream().filter(item -> node.getNodeId().equals(item.nodeId()))
                .findFirst().orElseThrow(FlowableAgentWaitInspector::conflict);
        if (nodeBinding.agent() == null || !node.getInputSchemaSha256().equals(ProcessNodeLifecycleListener.schemaSha256(nodeBinding.inputSchema()))
                || !node.getOutputSchemaSha256().equals(ProcessNodeLifecycleListener.schemaSha256(nodeBinding.outputSchema()))) throw conflict();
        var slot = verified.controlBindings().occurrenceSlots().get(node.getNodeId());
        if (slot == null || slot.waitId() == null) throw conflict();
        if (node.isExcluded() || node.getState() == StateEnum.CANCELLED) return typed(State.PROCESS_ENDED);
        if (node.getState() == StateEnum.TIMED_OUT) return typed(State.TIMER_WON);
        if (node.getState() == StateEnum.RESULT_ACCEPTED || node.getState() == StateEnum.COMPLETED
                || node.getState() == StateEnum.FAILED || node.getState() == StateEnum.WAIT_RECOVERY) return typed(State.MESSAGE_CONSUMED);

        String tenant = Long.toString(root.getTenantId());
        String instanceId = root.getInstanceId();
        String definitionId = verified.root().engineDefinitionId();
        var runtime = process.getRuntimeService();
        var instance = runtime.createProcessInstanceQuery().processInstanceId(instanceId).singleResult();
        if (instance == null) {
            var historical = process.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(instanceId).singleResult();
            if (historical == null || !tenant.equals(historical.getTenantId()) || !definitionId.equals(historical.getProcessDefinitionId())
                    || historical.getEndTime() == null) throw conflict();
            return typed(State.PROCESS_ENDED);
        }
        if (!tenant.equals(instance.getTenantId()) || !definitionId.equals(instance.getProcessDefinitionId())) throw conflict();
        if (instance.isSuspended()) return typed(State.SUSPENDED);
        if (node.getNativeSubscriptionId() == null) return typed(State.ADVANCING);
        if (node.getState() != StateEnum.WAITING || node.getAgentRunId() == null) throw conflict();

        var attempt = runtime.createExecutionQuery().executionId(node.getAttemptScopeId()).singleResult();
        var subscription = runtime.createEventSubscriptionQuery().id(node.getNativeSubscriptionId()).singleResult();
        if (attempt == null && subscription == null) return typed(State.MESSAGE_CONSUMED);
        requireNativeExecution(attempt, instanceId, tenant, slot.attemptOrBodyId());
        var occurrence = runtime.createExecutionQuery().executionId(attempt.getParentId()).singleResult();
        requireNativeExecution(occurrence, instanceId, tenant, slot.scopeId());
        String expectedOccurrence = "occ-" + nodeIdentityHash("AUTOMATION_NODE_OCCURRENCE_V2",
                List.of(root.getTenantId(), instanceId, node.getNodeId(), occurrence.getId()));
        if (!Objects.equals(node.getParentScopeId(), occurrence.getParentId()) || !expectedOccurrence.equals(node.getOccurrenceId())) {
            throw conflict();
        }
        if (subscription == null) return typed(State.MESSAGE_CONSUMED);
        if (subscription.getExecutionId() == null) throw conflict();
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager)) throw conflict();
        var checked = new ProcessControlNativeAccess(manager.getDataSource(), process).requireEventWait(root, definitionId, slot.waitId(),
                subscription.getExecutionId(), node.getNativeSubscriptionId(), node.getAttemptScopeId());
        return new TypedObservationVO(State.WAITING, new TypedWaitVO(nodeBinding, checked, instance.getBusinessKey()));
    }

    /** Rechecks ACK and the immutable V5 transport/sidecar link after the caller locks its dispatch row. */
    public static boolean validateTypedDispatch(AgentDispatchStore.Intent dispatch, AutomationInstanceBindingBO root,
            VerifiedAutomationModelsVO verified, ProcessNodeExecutionBO node, AgentTerminalEventV2 event) {
        requireTypedIdentity(root, verified, node, event);
        var seed = dispatch.seed();
        var slot = verified.controlBindings().occurrenceSlots().get(node.getNodeId());
        if (slot == null || slot.activityId() == null || node.getNativeJobId() == null
                || !seed.instanceId().equals(root.getInstanceId()) || !seed.binding().actor().subjectId().equals(event.ownerSubjectId())
                || seed.binding().actor().tenantId() != root.getTenantId()
                || !seed.binding().actor().issuerFingerprint().equals(event.ownerIssuerSha256())
                || !seed.occurrenceId().equals(node.getNativeJobId()) || !root.getReleaseKey().equals(seed.releaseKey())
                || !verified.root().engineDefinitionId().equals(seed.processDefinitionId())
                || !verified.root().modelSha256().equals(seed.processModelSha256())
                || !slot.activityId().equals(seed.elementId())
                || !dispatch.query().equals(new LinkedAgentLookupQuery(event.idempotencyKey(), event.link()))
                || !seed.binding().definitionKey().equals(event.definitionKey())
                || seed.binding().definitionVersion() != event.definitionVersion()
                || dispatch.dispatchNotAfter().isAfter(node.getAttemptDeadlineAt())
                || !seed.inputs().equals(Map.of("nodeExecutionRef", node.getNodeExecutionRef(), "inputSha256", node.getInputSha256(),
                        "configurationSha256", node.getConfigurationSha256()))
                || dispatch.runId() != null && !dispatch.runId().equals(event.runId())
                || node.getAgentRunId() != null && !node.getAgentRunId().equals(dispatch.runId())) throw conflict();
        var item = root.getEffectiveManifest().get(node.getNodeId());
        if (!(item instanceof Map<?, ?> configuration) || !(configuration.get("templateRef") instanceof Map<?, ?> templateRef)
                || !Arrays.equals(ProcessTypedJson.canonicalBytes(templateRef, 4096),
                        ProcessTypedJson.canonicalBytes(Map.of("key", event.definitionKey(), "version", event.definitionVersion(),
                                "contentSha256", event.link().definitionContentSha256()), 4096))) throw conflict();
        if (dispatch.state() != AgentDispatchStore.State.ACKNOWLEDGED) return false;
        if (!event.runId().equals(node.getAgentRunId())) throw conflict();
        return true;
    }

    private static void requireTypedIdentity(AutomationInstanceBindingBO root, VerifiedAutomationModelsVO verified,
            ProcessNodeExecutionBO node, AgentTerminalEventV2 event) {
        AutomationInstanceGuardCommandInterceptor.requireHeld(root.getTenantId(), root.getInstanceId());
        if (node.getKind() != KindEnum.AGENT || node.getTenantId() != root.getTenantId() || event.tenantId() != root.getTenantId()
                || !root.getInstanceId().equals(node.getInstanceId()) || !root.getInstanceId().equals(event.link().scopeInstanceId())
                || !root.getDefinitionKey().equals(verified.root().definitionKey())
                || !root.getConfigurationSha256().equals(node.getConfigurationSha256())
                || !event.configurationSha256().equals(node.getConfigurationSha256())
                || !event.nodeExecutionRef().equals(node.getNodeExecutionRef()) || !event.inputSha256().equals(node.getInputSha256())
                || !event.outputSchemaSha256().equals(node.getOutputSchemaSha256())) throw conflict();
        String expectedRef = "node-" + nodeIdentityHash("AUTOMATION_NODE_EXECUTION_V2",
                List.of(root.getTenantId(), root.getInstanceId(), node.getOccurrenceId(), node.getAttempt()));
        if (!expectedRef.equals(node.getNodeExecutionRef())) throw conflict();
    }

    private static void requireNativeExecution(Execution execution, String instanceId, String tenant, String activityId) {
        if (execution == null || execution.isEnded() || !tenant.equals(execution.getTenantId())
                || !instanceId.equals(execution.getProcessInstanceId()) || !instanceId.equals(execution.getRootProcessInstanceId())
                || !activityId.equals(execution.getActivityId()) || execution.getParentId() == null) throw conflict();
    }

    private static String nodeIdentityHash(String domain, List<?> parts) {
        return ProcessTypedJson.sha256(domain, ProcessTypedJson.canonicalBytes(parts));
    }

    private static TypedObservationVO typed(State state) {
        return new TypedObservationVO(state, null);
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
    public record TypedObservationVO(State state, TypedWaitVO waitEvidence) { }
    public record TypedWaitVO(AutomationNodeBindingVO nodeBinding, EventSubscription subscription, String nativeBusinessKey) { }
}
