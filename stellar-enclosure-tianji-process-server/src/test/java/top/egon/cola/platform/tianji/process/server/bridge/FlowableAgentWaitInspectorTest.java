package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.eventsubscription.api.EventSubscriptionQuery;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.*;

class FlowableAgentWaitInspectorTest {
    private final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class);
    private final ProcessReleaseService releases = mock(ProcessReleaseService.class);
    private final VerifiedProcessModels models = mock(VerifiedProcessModels.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final HistoryService history = mock(HistoryService.class);
    private final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class, RETURNS_SELF);
    private final ProcessInstance instance = mock(ProcessInstance.class);
    private final HistoricActivityInstanceQuery timerHistory = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
    private final HistoricActivityInstanceQuery messageHistory = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
    private final EventSubscriptionQuery subscriptions = mock(EventSubscriptionQuery.class, RETURNS_SELF);
    private final ExecutionQuery executions = mock(ExecutionQuery.class, RETURNS_SELF);
    private final FlowableAgentWaitInspector inspector = new FlowableAgentWaitInspector(process, releases, models);
    private AgentDispatchStore.Intent intent;

    private void prepare() throws Exception {
        var original = seed("job-one");
        var seed = new AgentDispatchStore.Seed(original.binding(), original.instanceId(), original.occurrenceId(), original.releaseKey(),
                original.processDefinitionId(), original.processModelSha256(), "worker", original.workerInputSha256(), original.businessKey(),
                original.correlationId(), original.inputs());
        intent = new AgentDispatchStore.Intent(seed, NOW, NOW.plusSeconds(60), AgentDispatchStore.State.ACKNOWLEDGED, "run-one", null);
        var definition = new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN, "agentDispatch", seed.processDefinitionId(),
                "deployment-one", 1, seed.processModelSha256(), Map.of("amount", ValidatedProcessModel.ValueType.INTEGER));
        when(releases.getForProcessDefinition(20, seed.processDefinitionId())).thenReturn(new ProcessReleaseService.Release(seed.releaseKey(),
                "e".repeat(64), NOW, List.of(definition)));
        try (var xml = getClass().getResourceAsStream("/fixtures/agent-dispatch-wait.bpmn20.xml")) {
            assertNotNull(xml);
            when(models.runningProcess(20, definition)).thenReturn(new ProcessModelValidator().validateBpmn(xml.readAllBytes(), "agentDispatch"));
        }
        when(process.getHistoryService()).thenReturn(history);
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
        when(process.isDbHistoryUsed()).thenReturn(true);
        when(process.getRuntimeService()).thenReturn(runtime);
        when(runtime.createProcessInstanceQuery()).thenReturn(instances);
        when(instances.singleResult()).thenReturn(instance);
        when(instance.getBusinessKey()).thenReturn(seed.businessKey());
        when(history.createHistoricActivityInstanceQuery()).thenReturn(timerHistory, messageHistory);
        when(timerHistory.listPage(0, 2)).thenReturn(List.of());
        when(messageHistory.listPage(0, 2)).thenReturn(List.of());
        when(runtime.createEventSubscriptionQuery()).thenReturn(subscriptions);
        when(subscriptions.listPage(0, 2)).thenReturn(List.of());
        when(runtime.createExecutionQuery()).thenReturn(executions);
    }

    @Test
    void active_process_without_a_subscription_is_advancing_and_does_not_supply_an_execution() throws Exception {
        prepare();
        var observed = inspector.inspect(intent);
        assertEquals(FlowableAgentWaitInspector.State.ADVANCING, observed.state());
        assertNull(observed.executionId());
        verify(instances).processInstanceTenantId("20");
        verify(timerHistory).activityTenantId("20");
        verify(subscriptions).tenantId("20");
        verify(subscriptions).processDefinitionId(intent.seed().processDefinitionId());
    }

    @Test
    void completed_timer_wins_even_while_the_process_remains_on_a_human_task() throws Exception {
        prepare();
        var timer = completed("waitTimeout", null);
        when(timerHistory.listPage(0, 2)).thenReturn(List.of(timer));
        assertEquals(FlowableAgentWaitInspector.State.TIMER_WON, inspector.inspect(intent).state());
        verify(runtime, never()).createEventSubscriptionQuery();
    }

    @Test
    void a_cancelled_timer_is_not_a_timeout_and_only_the_exact_live_execution_is_returned() throws Exception {
        prepare();
        var timer = completed("waitTimeout", "event-based-gateway-cancel");
        when(timerHistory.listPage(0, 2)).thenReturn(List.of(timer));
        var subscription = subscription();
        when(subscriptions.listPage(0, 2)).thenReturn(List.of(subscription));
        var execution = mock(Execution.class);
        when(execution.getId()).thenReturn("execution-one");
        when(execution.getTenantId()).thenReturn("20");
        when(execution.getProcessInstanceId()).thenReturn(intent.seed().instanceId());
        when(execution.getActivityId()).thenReturn("waitResult");
        when(executions.singleResult()).thenReturn(execution);
        var observed = inspector.inspect(intent);
        assertEquals(FlowableAgentWaitInspector.State.WAITING, observed.state());
        assertEquals("execution-one", observed.executionId());
        verify(executions).executionId("execution-one");
        verify(executions).executionTenantId("20");
        verify(executions).processDefinitionId(intent.seed().processDefinitionId());
    }

    @Test
    void cross_tenant_or_ambiguous_subscriptions_are_rejected() throws Exception {
        prepare();
        var subscription = subscription();
        when(subscription.getTenantId()).thenReturn("21");
        when(subscriptions.listPage(0, 2)).thenReturn(List.of(subscription));
        assertThrows(ProcessConflictException.class, () -> inspector.inspect(intent));
        verify(runtime, never()).createExecutionQuery();
        prepare();
        var duplicate = subscription();
        when(subscriptions.listPage(0, 2)).thenReturn(List.of(duplicate, duplicate));
        assertThrows(ProcessConflictException.class, () -> inspector.inspect(intent));
    }

    @Test
    void disappearing_executions_are_retried_without_reusing_a_stale_subscription_pointer() throws Exception {
        prepare();
        var subscription = subscription();
        when(subscriptions.listPage(0, 2)).thenReturn(List.of(subscription));
        when(executions.singleResult()).thenReturn(null);
        var observed = inspector.inspect(intent);
        assertEquals(FlowableAgentWaitInspector.State.ADVANCING, observed.state());
        assertNull(observed.executionId());
    }

    @Test
    void ambiguous_activity_history_is_rejected_instead_of_selecting_a_different_occurrence() throws Exception {
        prepare();
        var completed = completed("waitTimeout", null);
        when(timerHistory.listPage(0, 2)).thenReturn(List.of(completed, completed));
        assertThrows(ProcessConflictException.class, () -> inspector.inspect(intent));
    }

    @Test
    void consumed_messages_and_suspended_or_ended_instances_are_distinguished() throws Exception {
        prepare();
        var message = completed("waitResult", null);
        when(messageHistory.listPage(0, 2)).thenReturn(List.of(message));
        assertEquals(FlowableAgentWaitInspector.State.MESSAGE_CONSUMED, inspector.inspect(intent).state());
        prepare();
        when(instance.isSuspended()).thenReturn(true);
        assertEquals(FlowableAgentWaitInspector.State.SUSPENDED, inspector.inspect(intent).state());
        prepare();
        when(instances.singleResult()).thenReturn(null);
        assertEquals(FlowableAgentWaitInspector.State.PROCESS_ENDED, inspector.inspect(intent).state());
    }

    @Test
    void insufficient_or_asynchronous_history_prevents_the_bridge_from_using_incomplete_facts() throws Exception {
        prepare();
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(intent));
        when(process.getHistoryLevel()).thenReturn(HistoryLevel.ACTIVITY);
        when(process.isAsyncHistoryEnabled()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(intent));
        verifyNoInteractions(releases);
    }

    @Test
    void schema_drift_cannot_turn_the_terminal_status_target_into_a_process_input() throws Exception {
        prepare();
        var release = releases.getForProcessDefinition(20, intent.seed().processDefinitionId());
        var original = release.definitions().getFirst();
        var model = models.runningProcess(20, original);
        var changed = new ProcessReleaseService.Definition(original.type(), original.definitionKey(), original.engineDefinitionId(),
                original.engineDeploymentId(), original.engineVersion(), original.modelSha256(),
                Map.of("amount", ValidatedProcessModel.ValueType.INTEGER, "agentStatus", ValidatedProcessModel.ValueType.STRING));
        when(releases.getForProcessDefinition(20, intent.seed().processDefinitionId())).thenReturn(
                new ProcessReleaseService.Release(release.releaseKey(), release.manifestSha256(), release.createdAt(), List.of(changed)));
        when(models.runningProcess(20, changed)).thenReturn(model);
        assertThrows(IllegalArgumentException.class, () -> inspector.inspect(intent));
        verify(runtime, never()).createProcessInstanceQuery();
    }

    private HistoricActivityInstance completed(String activity, String reason) {
        var value = mock(HistoricActivityInstance.class);
        when(value.getTenantId()).thenReturn("20");
        when(value.getProcessInstanceId()).thenReturn(intent.seed().instanceId());
        when(value.getProcessDefinitionId()).thenReturn(intent.seed().processDefinitionId());
        when(value.getActivityId()).thenReturn(activity);
        when(value.getEndTime()).thenReturn(Date.from(NOW));
        when(value.getDeleteReason()).thenReturn(reason);
        return value;
    }
    private EventSubscription subscription() {
        var value = mock(EventSubscription.class);
        when(value.getTenantId()).thenReturn("20");
        when(value.getProcessInstanceId()).thenReturn(intent.seed().instanceId());
        when(value.getProcessDefinitionId()).thenReturn(intent.seed().processDefinitionId());
        when(value.getActivityId()).thenReturn("waitResult");
        when(value.getEventType()).thenReturn("message");
        when(value.getEventName()).thenReturn("TianjiAgentFinishedV1");
        when(value.getExecutionId()).thenReturn("execution-one");
        return value;
    }
}
