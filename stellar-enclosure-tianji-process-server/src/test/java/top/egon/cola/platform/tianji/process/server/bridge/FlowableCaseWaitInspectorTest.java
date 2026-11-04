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
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Finite released/native reference fixtures; no engine, listener or database is started. */
class FlowableCaseWaitInspectorTest {
    static final Instant NOW = Instant.parse("2026-11-04T00:00:00Z");
    static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner", "https://idp.example/20");
    static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "case-service", "https://service.example/20");
    static final String CASE_HASH = "a".repeat(64), PARENT_HASH = "b".repeat(64);
    static CaseStartProvenanceStore.Entry origin() {
        return new CaseStartProvenanceStore.Entry(new CaseStartProvenanceStore.Seed("case-one", "LINKED_BPMN", "business", "correlation", "release", "governedCase", "case-definition", CASE_HASH,
                OWNER, "START", "parent-start", "c".repeat(64), SERVICE, "case-start", "d".repeat(64), "case-source", "parent-one", "source-occurrence", "caseSource", "linkedProcess", "parent-definition", PARENT_HASH), NOW);
    }

    @Test void normally_ended_same_id_source_and_the_independent_live_message_execution_are_required() {
        var f = new Fixture(); f.waiting();
        var observation = assertDoesNotThrow(() -> f.inspector.inspect(origin()));
        assertNotNull(observation); assertEquals(FlowableCaseWaitInspector.State.WAITING, observation.state());
        assertEquals("wait-execution", observation.executionId()); assertNotEquals(origin().seed().sourceOccurrenceId(), observation.executionId());
        verify(f.sourceHistory).activityInstanceId("source-occurrence"); verify(f.sourceHistory).activityId("caseSource");
        verify(f.executions).executionId("wait-execution"); verify(f.executions, never()).executionId("source-occurrence");
        verify(f.subscriptions).activityId("waitCaseResult"); verify(f.subscriptions).eventName("TianjiCaseCompletedV1");
    }

    @Test void unended_deleted_wrong_tenant_or_different_source_occurrence_never_supplies_a_wait() {
        var f = new Fixture(); f.waiting();
        when(f.source.getEndTime()).thenReturn(null); assertThrows(ProcessConflictException.class, () -> f.inspector.inspect(origin()));
        when(f.source.getEndTime()).thenReturn(Date.from(NOW)); when(f.source.getDeleteReason()).thenReturn("deleted"); assertThrows(ProcessConflictException.class, () -> f.inspector.inspect(origin()));
        when(f.source.getDeleteReason()).thenReturn(null); when(f.source.getTenantId()).thenReturn("21"); assertThrows(ProcessConflictException.class, () -> f.inspector.inspect(origin()));
        when(f.source.getTenantId()).thenReturn("20"); when(f.source.getId()).thenReturn("other-occurrence"); assertThrows(ProcessConflictException.class, () -> f.inspector.inspect(origin()));
        verify(f.runtime, never()).createEventSubscriptionQuery();
    }

    @Test void timer_and_message_normal_completion_take_precedence_over_an_active_parent() {
        var timer = new Fixture(); var timerOccurrence = completed("waitCaseTimeout", null); when(timer.timerHistory.listPage(0, 2)).thenReturn(List.of(timerOccurrence));
        assertEquals(FlowableCaseWaitInspector.State.TIMER_WON, observe(timer).state());
        verify(timer.runtime, never()).createEventSubscriptionQuery();
        var message = new Fixture(); var messageOccurrence = completed("waitCaseResult", null); when(message.messageHistory.listPage(0, 2)).thenReturn(List.of(messageOccurrence));
        assertEquals(FlowableCaseWaitInspector.State.MESSAGE_CONSUMED, observe(message).state());
    }

    @Test void deleted_losing_timer_is_not_success_and_missing_subscription_is_advancing() {
        var f = new Fixture(); var cancelled = completed("waitCaseTimeout", "event-gateway-cancel"); when(f.timerHistory.listPage(0, 2)).thenReturn(List.of(cancelled));
        var result = assertDoesNotThrow(() -> f.inspector.inspect(origin())); assertNotNull(result);
        assertEquals(FlowableCaseWaitInspector.State.ADVANCING, result.state()); assertNull(result.executionId());
    }

    @Test void ended_parent_and_suspended_parent_remain_distinct_without_reusing_an_execution() {
        var ended = new Fixture(); when(ended.instances.singleResult()).thenReturn(null);
        assertEquals(FlowableCaseWaitInspector.State.PROCESS_ENDED, observe(ended).state());
        var suspended = new Fixture(); when(suspended.instance.isSuspended()).thenReturn(true);
        assertEquals(FlowableCaseWaitInspector.State.SUSPENDED, observe(suspended).state());
        verify(suspended.runtime, never()).createEventSubscriptionQuery();
    }

    @Test void cross_tenant_duplicate_subscriptions_and_disappearing_execution_do_not_resume_the_parent() {
        var foreign = new Fixture(); foreign.waiting(); when(foreign.subscription.getTenantId()).thenReturn("21"); assertThrows(ProcessConflictException.class, () -> foreign.inspector.inspect(origin()));
        var duplicate = new Fixture(); duplicate.waiting(); when(duplicate.subscriptions.listPage(0, 2)).thenReturn(List.of(duplicate.subscription, duplicate.subscription));
        assertThrows(ProcessConflictException.class, () -> duplicate.inspector.inspect(origin()));
        var missing = new Fixture(); missing.waiting(); when(missing.executions.singleResult()).thenReturn(null);
        assertEquals(FlowableCaseWaitInspector.State.ADVANCING, observe(missing).state());
    }

    @Test void immutable_release_hash_case_key_and_synchronous_history_are_rechecked() {
        var f = new Fixture(); when(f.model.caseSources()).thenReturn(Map.of("caseSource", new ValidatedProcessModel.CaseSource("anotherCase", "waitCaseResult", "TianjiCaseCompletedV1", "waitCaseTimeout", Duration.ofMinutes(5))));
        assertThrows(ProcessConflictException.class, () -> f.inspector.inspect(origin()));
        var async = new Fixture(); when(async.process.isAsyncHistoryEnabled()).thenReturn(true); assertThrows(IllegalArgumentException.class, () -> async.inspector.inspect(origin()));
    }

    private static FlowableCaseWaitInspector.Observation observe(Fixture fixture) {
        var result = assertDoesNotThrow(() -> fixture.inspector.inspect(origin())); assertNotNull(result); return result;
    }

    static HistoricActivityInstance completed(String activity, String deleteReason) {
        var item = mock(HistoricActivityInstance.class); when(item.getId()).thenReturn(activity + "-history"); when(item.getTenantId()).thenReturn("20");
        when(item.getProcessInstanceId()).thenReturn("parent-one"); when(item.getProcessDefinitionId()).thenReturn("parent-definition"); when(item.getActivityId()).thenReturn(activity);
        when(item.getEndTime()).thenReturn(Date.from(NOW)); when(item.getDeleteReason()).thenReturn(deleteReason); return item;
    }
    private static final class Fixture {
        final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class);
        final ProcessReleaseService releases = mock(ProcessReleaseService.class); final VerifiedProcessModels models = mock(VerifiedProcessModels.class);
        final RuntimeService runtime = mock(RuntimeService.class); final HistoryService history = mock(HistoryService.class);
        final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class, RETURNS_SELF); final ProcessInstance instance = mock(ProcessInstance.class);
        final HistoricActivityInstanceQuery sourceHistory = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF), timerHistory = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF), messageHistory = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        final HistoricActivityInstance source = completed("caseSource", null);
        final EventSubscriptionQuery subscriptions = mock(EventSubscriptionQuery.class, RETURNS_SELF); final EventSubscription subscription = mock(EventSubscription.class);
        final ExecutionQuery executions = mock(ExecutionQuery.class, RETURNS_SELF); final Execution execution = mock(Execution.class);
        final ValidatedProcessModel model = mock(ValidatedProcessModel.class);
        final FlowableCaseWaitInspector inspector;
        Fixture() {
            var binding = new ProcessReleaseService.Definition(ValidatedProcessModel.Type.BPMN, "linkedProcess", "parent-definition", "parent-deployment", 1, PARENT_HASH, Map.of());
            when(releases.getForProcessDefinition(20, "parent-definition")).thenReturn(new ProcessReleaseService.Release("release", "e".repeat(64), NOW, List.of(binding)));
            when(models.runningProcess(20, binding)).thenReturn(model); when(model.type()).thenReturn(ValidatedProcessModel.Type.BPMN);
            when(model.caseSources()).thenReturn(Map.of("caseSource", new ValidatedProcessModel.CaseSource("governedCase", "waitCaseResult", "TianjiCaseCompletedV1", "waitCaseTimeout", Duration.ofMinutes(5))));
            when(model.definitionKey()).thenReturn("linkedProcess"); when(model.inputs()).thenReturn(Map.of()); when(model.outputs()).thenReturn(Map.of());
            when(process.getHistoryService()).thenReturn(history); when(process.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT); when(process.isDbHistoryUsed()).thenReturn(true); when(process.getRuntimeService()).thenReturn(runtime);
            when(history.createHistoricActivityInstanceQuery()).thenReturn(sourceHistory, timerHistory, messageHistory);
            when(source.getId()).thenReturn("source-occurrence"); when(sourceHistory.listPage(0, 2)).thenReturn(List.of(source)); when(timerHistory.listPage(0, 2)).thenReturn(List.of()); when(messageHistory.listPage(0, 2)).thenReturn(List.of());
            when(runtime.createProcessInstanceQuery()).thenReturn(instances); when(instances.singleResult()).thenReturn(instance); when(instance.getId()).thenReturn("parent-one"); when(instance.getProcessDefinitionId()).thenReturn("parent-definition"); when(instance.getTenantId()).thenReturn("20"); when(instance.getBusinessKey()).thenReturn("business");
            when(runtime.createEventSubscriptionQuery()).thenReturn(subscriptions); when(subscriptions.listPage(0, 2)).thenReturn(List.of()); when(runtime.createExecutionQuery()).thenReturn(executions);
            inspector = new FlowableCaseWaitInspector(process, releases, models, new ProcessModelValidator());
        }
        void waiting() {
            when(subscription.getId()).thenReturn("subscription-one"); when(subscription.getTenantId()).thenReturn("20"); when(subscription.getProcessInstanceId()).thenReturn("parent-one"); when(subscription.getProcessDefinitionId()).thenReturn("parent-definition"); when(subscription.getActivityId()).thenReturn("waitCaseResult"); when(subscription.getEventType()).thenReturn("message"); when(subscription.getEventName()).thenReturn("TianjiCaseCompletedV1"); when(subscription.getExecutionId()).thenReturn("wait-execution");
            when(subscriptions.listPage(0, 2)).thenReturn(List.of(subscription)); when(executions.singleResult()).thenReturn(execution); when(execution.getId()).thenReturn("wait-execution"); when(execution.getTenantId()).thenReturn("20"); when(execution.getProcessInstanceId()).thenReturn("parent-one"); when(execution.getActivityId()).thenReturn("waitCaseResult");
        }
    }
}
