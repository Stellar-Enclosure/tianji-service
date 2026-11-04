package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.cmmn.api.CmmnHistoryService;
import org.flowable.cmmn.api.history.HistoricCaseInstance;
import org.flowable.cmmn.api.history.HistoricCaseInstanceQuery;
import org.flowable.cmmn.api.history.HistoricMilestoneInstance;
import org.flowable.cmmn.api.history.HistoricMilestoneInstanceQuery;
import org.flowable.cmmn.api.history.HistoricPlanItemInstance;
import org.flowable.cmmn.api.history.HistoricPlanItemInstanceQuery;
import org.flowable.cmmn.api.runtime.PlanItemDefinitionType;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.execution.CaseNativeAccess;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.VerifiedProcessModels;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox.*;
import static top.egon.cola.platform.tianji.process.server.bridge.FlowableCaseWaitInspectorTest.*;

/** Transaction/correlation markers and native history fixtures, without native engine or PostgreSQL execution. */
class CaseCompletionCorrelationServiceTest {
    static final String COMPLETION_ID = top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest.hash(List.of("TIANJI_CASE_COMPLETION_V1", "20", "case-one"), Map.of());
    static final DueKey KEY = new DueKey(20, COMPLETION_ID);
    static final RegistrationKey REGISTRATION_KEY = new RegistrationKey(20, SERVICE.issuerFingerprint(), SERVICE.subjectId(), "case-source");
    static CaseSourceRegistry.Registration registration() {
        var reg = mock(CaseSourceRegistry.Registration.class);
        when(reg.actor()).thenReturn(SERVICE); when(reg.sourceId()).thenReturn("case-source"); when(reg.inboxKey()).thenReturn(REGISTRATION_KEY);
        when(reg.binding()).thenReturn(new CaseSourceRegistry.BindingKey(20, "release", "linkedProcess", PARENT_HASH, "caseSource", "governedCase", CASE_HASH)); return reg;
    }
    static Entry pending() { return new Entry(KEY, "case-one", REGISTRATION_KEY, State.PENDING, null, NOW, NOW, 0, null, null, null); }
    static FlowableCaseWaitInspector.Observation waitState(FlowableCaseWaitInspector.State state) {
        return new FlowableCaseWaitInspector.Observation(state, "caseSource", "waitCaseResult", "TianjiCaseCompletedV1", "waitCaseTimeout", state == FlowableCaseWaitInspector.State.WAITING ? "wait-execution" : null);
    }

    @Test void parent_late_lifecycle_is_decided_before_observing_an_active_child() throws Exception {
        for (var state : List.of(FlowableCaseWaitInspector.State.TIMER_WON, FlowableCaseWaitInspector.State.MESSAGE_CONSUMED, FlowableCaseWaitInspector.State.PROCESS_ENDED)) {
            try (var f = new Fixture()) {
                when(f.waits.inspect(f.origin)).thenReturn(waitState(state));
                var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.IGNORED_LATE, result.state());
                verify(f.cases.get(), never()).observe(any()); verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
                var order = inOrder(f.provenance, f.inbox); order.verify(f.provenance).lockScope(20, "case-one"); order.verify(f.inbox).lock(KEY);
            }
        }
    }

    @Test void active_child_reschedules_without_a_failure_budget_or_lifetime_expiry() throws Exception {
        try (var f = new Fixture()) {
            f.caseState("active"); var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.PENDING, result.state());
            verify(f.inbox).reschedule(any(), eq(Duration.ofSeconds(1))); verify(f.inbox, never()).rescheduleFailure(any(), any(), anyInt());
            verifyNoInteractions(f.commands.get()); verify(f.history, never()).createHistoricMilestoneInstanceQuery();
        }
    }

    @Test void positive_persisted_case_primary_supporting_and_milestone_facts_deliver_exact_wait_once() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.APPLIED, result.state());
            verify(f.runtime).messageEventReceived("TianjiCaseCompletedV1", "wait-execution", Map.of());
            verify(f.commands.get()).apply(SERVICE, ProcessCommandReceiptStore.Kind.CASE_COMPLETION_SERVICE, COMPLETION_ID, "wait-execution", null);
            var proof = org.mockito.ArgumentCaptor.forClass(AppliedCommand.class);
            verify(f.inbox).finish(any(), eq(State.APPLIED), eq(DecisionCode.MESSAGE_APPLIED), any(TerminalFacts.class), proof.capture());
            assertEquals("wait-execution", proof.getValue().resourceId()); assertEquals(f.requestHash.get(), proof.getValue().requestSha256());
            assertTrue(f.sql.stream().anyMatch(sql -> sql.contains("act_ru_execution") && sql.contains("act_ru_event_subscr") && sql.contains("FOR UPDATE OF")));
            verify(f.milestones).milestoneInstanceCaseInstanceId("case-one"); verify(f.milestones).milestoneInstanceTenantId("20");
        }
    }

    @Test void supplemental_termination_is_a_valid_supporting_path_but_whole_case_termination_is_dead() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); when(f.supplemental.getState()).thenReturn("terminated"); when(f.supplemental.getCompletedTime()).thenReturn(null); when(f.supplemental.getTerminatedTime()).thenReturn(Date.from(NOW));
            assertEquals(State.APPLIED, apply(f).state());
        }
        try (var f = new Fixture()) {
            f.caseState("terminated"); when(f.caseHistory.getState()).thenReturn("terminated");
            var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.DEAD, result.state()); assertEquals(DecisionCode.CASE_TERMINATED, result.decisionCode());
            verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
        }
    }

    @Test void force_completed_case_without_positive_primary_or_milestone_trace_never_sends_success() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); when(f.primary.getCompletedTime()).thenReturn(null);
            var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.DEAD, result.state()); assertEquals(DecisionCode.INCONSISTENT_NATIVE_FACT, result.decisionCode());
            verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
        }
        try (var f = new Fixture()) {
            f.completed(); when(f.milestones.listPage(0, 2)).thenReturn(List.of());
            assertEquals(DecisionCode.INCONSISTENT_NATIVE_FACT, apply(f).decisionCode());
            verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
        }
    }

    @Test void milestone_definition_id_wrong_tenant_and_duplicate_history_are_not_positive_occurrences() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); when(f.milestone.getElementId()).thenReturn("completionMilestone");
            assertEquals(State.DEAD, apply(f).state());
        }
        try (var f = new Fixture()) {
            f.completed(); when(f.primary.getTenantId()).thenReturn("21");
            assertEquals(State.DEAD, apply(f).state());
        }
        try (var f = new Fixture()) {
            f.completed(); when(f.planHistory.listPage(0, 4)).thenReturn(List.of(f.primary, f.supplemental, f.milestonePlan, f.primary));
            assertEquals(State.DEAD, apply(f).state());
        }
    }

    @Test void completed_child_can_keep_verified_facts_pending_until_the_parent_wait_becomes_ready() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); when(f.waits.inspect(f.origin)).thenReturn(waitState(FlowableCaseWaitInspector.State.SUSPENDED));
            var result = assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)); assertNotNull(result); assertEquals(State.PENDING, result.state()); assertNotNull(result.terminalFacts());
            verify(f.inbox).reschedule(any(), any(TerminalFacts.class), eq(Duration.ofSeconds(1))); verifyNoInteractions(f.commands.get());
        }
    }

    @Test void duplicate_applied_intents_do_not_touch_native_history_or_send_another_message() throws Exception {
        try (var f = new Fixture()) {
            var facts = new TerminalFacts(TerminalStatus.COMPLETED, "f".repeat(64), NOW);
            var applied = new Entry(KEY, "case-one", REGISTRATION_KEY, State.APPLIED, facts, NOW, NOW, 0, NOW, DecisionCode.MESSAGE_APPLIED, "CASE_COMPLETION_SERVICE"); f.current.set(applied);
            assertEquals(applied, assertDoesNotThrow(() -> f.service.apply(f.registration, COMPLETION_ID)));
            verifyNoInteractions(f.cases.get(), f.commands.get()); verify(f.waits, never()).inspect(any());
        }
    }

    @Test void explicit_registration_withdrawal_and_orphans_never_borrow_an_actor_or_send_message() throws Exception {
        try (var f = new Fixture()) {
            when(f.registry.current(f.registration)).thenReturn(false);
            assertEquals(DecisionCode.SOURCE_UNAVAILABLE, apply(f).decisionCode());
            verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
        }
        try (var f = new Fixture()) {
            when(f.registry.find(REGISTRATION_KEY)).thenReturn(Optional.empty()); when(f.registry.activeDueKeys()).thenReturn(Set.of());
            var result = assertDoesNotThrow(() -> f.service.applyOrphan(KEY)); assertNotNull(result); assertEquals(DecisionCode.SOURCE_UNAVAILABLE, result.decisionCode());
            verifyNoInteractions(f.cases.get(), f.commands.get()); verify(f.waits, never()).inspect(any());
        }
    }

    @Test void native_failure_rolls_back_then_reschedules_under_the_same_case_scope_without_an_applied_receipt() throws Exception {
        try (var f = new Fixture()) {
            f.completed(); doThrow(new IllegalStateException("controlled native failure")).when(f.runtime).messageEventReceived(anyString(), anyString(), anyMap());
            assertThrows(IllegalStateException.class, () -> f.service.apply(f.registration, COMPLETION_ID));
            verify(f.connection).rollback(); verify(f.inbox).rescheduleFailure(any(), eq(Duration.ofSeconds(1)), eq(10)); verify(f.commands.get(), never()).apply(any(), any(), anyString(), anyString(), any());
            verify(f.provenance, atLeast(2)).lockScope(20, "case-one");
        }
    }

    @Test void reconciliation_chunks_claims_at_32_and_processes_each_discovered_key_at_most_once() throws Exception {
        try (var f = new Fixture()) {
            when(f.inbox.due(REGISTRATION_KEY, 32, Duration.ofSeconds(1))).thenReturn(java.util.Collections.nCopies(32, KEY));
            when(f.inbox.due(REGISTRATION_KEY, 4, Duration.ofSeconds(1))).thenReturn(List.of());
            var result = assertDoesNotThrow(() -> f.service.reconcile(f.registration, 100)); assertNotNull(result);
            verify(f.inbox, atLeastOnce()).due(REGISTRATION_KEY, 32, Duration.ofSeconds(1)); verify(f.inbox, never()).due(any(), eq(100), any());
            verify(f.waits, atMostOnce()).inspect(any());
            assertThrows(IllegalArgumentException.class, () -> f.service.reconcile(f.registration, 101));
        }
    }

    @Test void another_registered_executor_tuple_cannot_apply_or_dead_letter_a_foreign_intent() throws Exception {
        try (var f = new Fixture()) {
            var foreign = mock(CaseSourceRegistry.Registration.class);
            var actor = top.egon.cola.platform.tianji.process.server.identity.ProcessActor.verified(20, top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE, "another-service", "https://service.example/20");
            var key = new RegistrationKey(20, actor.issuerFingerprint(), actor.subjectId(), "another-source");
            when(foreign.actor()).thenReturn(actor); when(foreign.sourceId()).thenReturn("another-source"); when(foreign.inboxKey()).thenReturn(key);
            when(foreign.binding()).thenReturn(new CaseSourceRegistry.BindingKey(20, "release", "linkedProcess", PARENT_HASH, "anotherCaseSource", "governedCase", CASE_HASH));
            when(f.registry.current(foreign)).thenReturn(true);
            assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class, () -> f.service.apply(foreign, COMPLETION_ID));
            verify(f.inbox, never()).finish(any(), any(State.class), any(DecisionCode.class));
            verify(f.inbox, never()).finish(any(), any(State.class), any(DecisionCode.class), any(TerminalFacts.class));
            verify(f.inbox, never()).finish(any(), any(State.class), any(DecisionCode.class), any(TerminalFacts.class), any(AppliedCommand.class));
            verify(f.inbox, never()).rescheduleFailure(any(), any(), anyInt()); verify(f.inbox, never()).reschedule(any(), any(Duration.class));
            verifyNoInteractions(f.cases.get(), f.commands.get()); verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
        }
    }

    @Test void an_owned_intent_missing_its_native_model_still_accounts_a_bounded_failure_without_message() throws Exception {
        for (String stage : List.of("initial-wait", "case-model", "fresh-wait")) {
            try (var f = new Fixture()) {
                var missing = new top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException();
                if (stage.equals("initial-wait")) when(f.waits.inspect(f.origin)).thenThrow(missing);
                else {
                    f.completed();
                    if (stage.equals("case-model")) when(f.models.caseModel(eq(20L), any(ProcessReleaseService.Definition.class))).thenThrow(missing);
                    else when(f.waits.inspect(f.origin)).thenReturn(waitState(FlowableCaseWaitInspector.State.WAITING)).thenThrow(missing);
                }
                assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class, () -> f.service.apply(f.registration, COMPLETION_ID), stage);
                verify(f.inbox).rescheduleFailure(any(), eq(Duration.ofSeconds(1)), eq(10));
                verify(f.provenance, atLeast(2)).lockScope(20, "case-one"); verify(f.connection).rollback();
                verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
                verify(f.commands.get(), never()).apply(any(), any(), anyString(), anyString(), any());
            }
        }
    }

    @Test void repeated_local_registration_metadata_failure_still_consumes_only_the_owned_technical_budget() throws Exception {
        try (var f = new Fixture()) {
            var temporary = new IllegalStateException("controlled local metadata unavailability");
            when(f.registry.current(f.registration)).thenThrow(temporary);
            assertSame(temporary, assertThrows(IllegalStateException.class, () -> f.service.apply(f.registration, COMPLETION_ID)));
            verify(f.inbox).rescheduleFailure(any(), eq(Duration.ofSeconds(1)), eq(10));
            verify(f.provenance, atLeast(2)).lockScope(20, "case-one"); verify(f.connection, atLeastOnce()).rollback();
            verify(f.inbox, never()).finish(any(), eq(State.DEAD), eq(DecisionCode.SOURCE_UNAVAILABLE));
            verify(f.runtime, never()).messageEventReceived(anyString(), anyString(), anyMap());
            verifyNoInteractions(f.cases.get(), f.commands.get());
        }
    }

    @Test void ambient_transactions_are_rejected_before_any_discovery_or_native_action() throws Exception {
        try (var f = new Fixture()) {
            var tx = new TransactionTemplate(f.manager);
            assertThrows(IllegalStateException.class, () -> tx.execute(status -> f.service.apply(f.registration, COMPLETION_ID)));
            verifyNoInteractions(f.cases.get(), f.commands.get()); verify(f.inbox, never()).find(any());
        }
    }

    private static Entry apply(Fixture fixture) {
        var result = assertDoesNotThrow(() -> fixture.service.apply(fixture.registration, COMPLETION_ID)); assertNotNull(result); return result;
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class); final DataSourceTransactionManager manager;
        final SpringProcessEngineConfiguration process = mock(SpringProcessEngineConfiguration.class); final SpringCmmnEngineConfiguration cmmn = mock(SpringCmmnEngineConfiguration.class);
        final RuntimeService runtime = mock(RuntimeService.class); final CmmnHistoryService history = mock(CmmnHistoryService.class);
        final CaseStartProvenanceStore provenance = mock(CaseStartProvenanceStore.class); final CaseCompletionInbox inbox = mock(CaseCompletionInbox.class);
        final CaseSourceRegistry registry = mock(CaseSourceRegistry.class); final CaseSourceRegistry.Registration registration = registration();
        final ProcessReleaseService releases = mock(ProcessReleaseService.class); final VerifiedProcessModels models = mock(VerifiedProcessModels.class); final FlowableCaseWaitInspector waits = mock(FlowableCaseWaitInspector.class);
        final CaseStartProvenanceStore.Entry origin = origin(); final AtomicReference<Entry> current = new AtomicReference<>(pending()); final AtomicReference<String> requestHash = new AtomicReference<>();
        final HistoricCaseInstanceQuery caseHistories = mock(HistoricCaseInstanceQuery.class, RETURNS_SELF); final HistoricCaseInstance caseHistory = mock(HistoricCaseInstance.class);
        final HistoricPlanItemInstanceQuery planHistory = mock(HistoricPlanItemInstanceQuery.class, RETURNS_SELF); final HistoricMilestoneInstanceQuery milestones = mock(HistoricMilestoneInstanceQuery.class, RETURNS_SELF);
        final HistoricPlanItemInstance primary = plan("primary", "primaryTask", PlanItemDefinitionType.HUMAN_TASK), supplemental = plan("supplemental", "supplementalTask", PlanItemDefinitionType.HUMAN_TASK), milestonePlan = plan("milestone", "completionMilestone", PlanItemDefinitionType.MILESTONE);
        final HistoricMilestoneInstance milestone = mock(HistoricMilestoneInstance.class);
        final AtomicReference<CaseNativeAccess> cases = new AtomicReference<>(); final AtomicReference<ProcessCommandReceiptStore> commands = new AtomicReference<>(); final java.util.ArrayList<String> sql = new java.util.ArrayList<>();
        final org.mockito.MockedConstruction<CaseNativeAccess> caseConstruction; final org.mockito.MockedConstruction<ProcessCommandReceiptStore> commandConstruction; final org.mockito.MockedConstruction<JdbcTemplate> jdbcConstruction;
        final CaseCompletionCorrelationService service;
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); manager = new DataSourceTransactionManager(source);
            when(process.getTransactionManager()).thenReturn(manager); when(process.getDataSource()).thenReturn(source); when(process.getDatabaseSchema()).thenReturn("tianji_process"); when(process.getRuntimeService()).thenReturn(runtime);
            when(process.getHistoryService()).thenReturn(mock(org.flowable.engine.HistoryService.class)); when(process.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT); when(process.isDbHistoryUsed()).thenReturn(true);
            when(cmmn.getTransactionManager()).thenReturn(manager); when(cmmn.getDataSource()).thenReturn(source); when(cmmn.getDatabaseSchema()).thenReturn("tianji_process"); when(cmmn.getCmmnHistoryService()).thenReturn(history);
            when(cmmn.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT); when(cmmn.isDbHistoryUsed()).thenReturn(true);
            when(provenance.usesDataSource(source)).thenReturn(true); when(provenance.find(20, "case-one")).thenReturn(Optional.of(origin));
            when(registry.current(registration)).thenReturn(true); when(registry.find(REGISTRATION_KEY)).thenReturn(Optional.of(registration)); when(registry.activeDueKeys()).thenReturn(Set.of(REGISTRATION_KEY));
            when(inbox.find(KEY)).thenAnswer(i -> Optional.of(current.get())); when(inbox.lock(KEY)).thenAnswer(i -> current.get()); when(inbox.databaseNow()).thenReturn(NOW);
            when(inbox.reschedule(any(), any(Duration.class))).thenAnswer(i -> current.get());
            when(inbox.reschedule(any(), any(TerminalFacts.class), any(Duration.class))).thenAnswer(i -> { Entry old = i.getArgument(0); var next = new Entry(KEY, "case-one", REGISTRATION_KEY, State.PENDING, i.getArgument(1), old.createdAt(), NOW, old.failureCount(), null, null, null); current.set(next); return next; });
            when(inbox.finish(any(), any(State.class), any(DecisionCode.class))).thenAnswer(i -> finish(i.getArgument(1), i.getArgument(2), current.get().terminalFacts()));
            when(inbox.finish(any(), any(State.class), any(DecisionCode.class), any(TerminalFacts.class))).thenAnswer(i -> finish(i.getArgument(1), i.getArgument(2), i.getArgument(3)));
            when(inbox.finish(any(), any(State.class), any(DecisionCode.class), any(TerminalFacts.class), any(AppliedCommand.class))).thenAnswer(i -> finish(i.getArgument(1), i.getArgument(2), i.getArgument(3)));
            when(fWait()).thenReturn(waitState(FlowableCaseWaitInspector.State.WAITING));
            var definition = new ProcessReleaseService.Definition(ValidatedProcessModel.Type.CMMN, "governedCase", "case-definition", "case-deployment", 1, CASE_HASH, Map.of());
            when(releases.get(SERVICE, "release")).thenReturn(new ProcessReleaseService.Release("release", "e".repeat(64), NOW, List.of(definition)));
            byte[] bytes; try (var resource = getClass().getResourceAsStream("/fixtures/governed-case.cmmn")) { assertNotNull(resource); bytes = resource.readAllBytes(); }
            when(models.caseModel(20, definition)).thenReturn(new ProcessModelValidator().validateCmmn(bytes, "governedCase"));
            when(history.createHistoricCaseInstanceQuery()).thenReturn(caseHistories); when(caseHistories.singleResult()).thenReturn(caseHistory);
            when(caseHistory.getId()).thenReturn("case-one"); when(caseHistory.getCaseDefinitionId()).thenReturn("case-definition"); when(caseHistory.getTenantId()).thenReturn("20"); when(caseHistory.getBusinessKey()).thenReturn("business"); when(caseHistory.getState()).thenReturn("completed"); when(caseHistory.getEndTime()).thenReturn(Date.from(NOW));
            when(history.createHistoricPlanItemInstanceQuery()).thenReturn(planHistory); when(planHistory.listPage(0, 4)).thenReturn(List.of(primary, supplemental, milestonePlan));
            when(milestonePlan.getCompletedTime()).thenReturn(null); when(milestonePlan.getOccurredTime()).thenReturn(Date.from(NOW));
            when(history.createHistoricMilestoneInstanceQuery()).thenReturn(milestones); when(milestones.listPage(0, 2)).thenReturn(List.of(milestone));
            when(milestone.getId()).thenReturn("milestone-occurrence"); when(milestone.getCaseInstanceId()).thenReturn("case-one"); when(milestone.getCaseDefinitionId()).thenReturn("case-definition"); when(milestone.getTenantId()).thenReturn("20"); when(milestone.getElementId()).thenReturn("milestone"); when(milestone.getTimeStamp()).thenReturn(Date.from(NOW));
            caseConstruction = mockConstruction(CaseNativeAccess.class, (value, ignored) -> cases.set(value));
            commandConstruction = mockConstruction(ProcessCommandReceiptStore.class, (value, ignored) -> { commands.set(value);
                when(value.insert(any(), any(), anyString(), anyString(), anyString(), anyString())).thenAnswer(i -> { requestHash.set(i.getArgument(3)); return true; });
                when(value.lock(any(), any(), anyString())).thenAnswer(i -> new ProcessCommandReceiptStore.Receipt(requestHash.get(), "release", "correlation", "PENDING", null, NOW, null));
            });
            jdbcConstruction = mockConstruction(JdbcTemplate.class, (value, ignored) -> {
                when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(value.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class))).thenAnswer(i -> { sql.add(i.getArgument(0)); return List.of("wait-execution"); });
            });
            service = new CaseCompletionCorrelationService(process, cmmn, releases, models, provenance, inbox, registry, waits, Duration.ofSeconds(1), 10); caseState("active");
        }
        private FlowableCaseWaitInspector.Observation fWait() { return waits.inspect(origin); }
        void caseState(String state) { when(cases.get().observe(origin)).thenReturn(new CaseNativeAccess.Observation("case-one", "case-definition", "20", "business", state, state.equals("active") ? null : NOW, List.of())); }
        void completed() { caseState("completed"); }
        Entry finish(State state, DecisionCode code, TerminalFacts facts) { var next = new Entry(KEY, "case-one", REGISTRATION_KEY, state, facts, NOW, NOW, 0, NOW, code, state == State.APPLIED ? "CASE_COMPLETION_SERVICE" : null); current.set(next); return next; }
        @Override public void close() { jdbcConstruction.close(); commandConstruction.close(); caseConstruction.close(); }
    }
    private static HistoricPlanItemInstance plan(String element, String definition, String kind) {
        var item = mock(HistoricPlanItemInstance.class); when(item.getId()).thenReturn("pi-" + element); when(item.getElementId()).thenReturn(element); when(item.getPlanItemDefinitionId()).thenReturn(definition); when(item.getPlanItemDefinitionType()).thenReturn(kind);
        when(item.getCaseInstanceId()).thenReturn("case-one"); when(item.getCaseDefinitionId()).thenReturn("case-definition"); when(item.getTenantId()).thenReturn("20"); when(item.getState()).thenReturn("completed"); when(item.getCompletedTime()).thenReturn(Date.from(NOW)); when(item.getEndedTime()).thenReturn(Date.from(NOW)); return item;
    }
}
