package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.AccountingState;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder.StartResultAccessVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.DisclosureWindowVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.RecordedAccessVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.StartResultAccessRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Facade ordering and value contracts over a controlled Store; actual access SQL is covered separately. */
class ToolResultAccessRecorderTest {
    private static final String RUN = ToolInvocationStoreTest.RUN, HASH = ToolInvocationStoreTest.HASH;
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RECEIPT = "tianji:ps-start:v1:" + "d".repeat(64);
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.access", 1, "access_start", "Original START result",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", ProcessClient.START_RESOURCE_KEY, HASH, null));

    @Test void exact_store_ledger_affinity_is_required_and_ambient_transactions_do_not_read_a_body() {
        var store = mock(ToolResultAccessStore.class); var ledger = mock(AgentLedgerService.class);
        assertThrows(IllegalArgumentException.class, () -> new ToolResultAccessRecorder(store, ledger));
        when(store.usesLedger(ledger)).thenReturn(true);
        var recorder = new ToolResultAccessRecorder(store, ledger);
        assertTrue(recorder.usesStore(store)); assertTrue(recorder.usesLedger(ledger));
        clearInvocations(store, ledger);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> recorder.recordKnownStartResultAccess(request())); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(store, ledger);
    }

    @Test void source_cost_is_not_the_local_access_cost_and_only_access_is_settled_after_the_fact_commit() {
        for (BigDecimal sourceCost : new BigDecimal[]{null, BigDecimal.ZERO, new BigDecimal("2.5000")}) {
            try (var scene = new Scene(success(sourceCost))) {
                var result = scene.recorder.recordKnownStartResultAccess(scene.request);
                assertSame(scene.source, result.sourceOutcome()); assertEquals(sourceCost, result.sourceOutcome().actualCost());
                assertEquals(RECEIPT, result.sourceOutcome().businessReceiptId()); assertEquals(Map.of("instanceId", "source-instance"), result.sourceOutcome().output());
                assertEquals(scene.request.action().intentId(), result.actionIntentId()); assertEquals(scene.request.accessId(), result.accessId());
                assertEquals(7, result.sourceActionRevision()); assertEquals(HASH, result.sourceResultSha256());
                assertEquals(AccountingState.SETTLED, result.accessAccountingState());
                assertEquals(List.of("fact-commit", "access-account", "egress"), scene.trace);
                verify(scene.budgets).settleBudget(scene.request.binding().run().actor(), scene.request.lease(), scene.request.reservationId(), new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
                verify(scene.store).checkDisclosureWindow(same(scene.window));
                assertFalse(result.toString().contains(RECEIPT)); assertFalse(result.toString().contains("source-instance"));
            }
        }
    }

    @Test void known_negative_outcomes_are_returned_verbatim_with_their_original_fee() {
        for (var status : List.of(ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) {
            var source = new ToolExecutionOutcome(status, Map.of(), null, "ORIGINAL_NEGATIVE", new BigDecimal("0.0300"));
            try (var scene = new Scene(source)) {
                var result = scene.recorder.recordKnownStartResultAccess(scene.request);
                assertSame(source, result.sourceOutcome()); assertEquals(new BigDecimal("0.0300"), result.sourceOutcome().actualCost());
                assertEquals(AccountingState.SETTLED, result.accessAccountingState()); assertEquals(List.of("fact-commit", "access-account", "egress"), scene.trace);
            }
        }
    }

    @Test void accounting_failure_returns_pending_and_still_checks_the_same_fresh_egress_window() {
        try (var scene = new Scene(success(null))) {
            scene.failAccounting.set(true);
            var result = scene.recorder.recordKnownStartResultAccess(scene.request);
            assertSame(scene.source, result.sourceOutcome()); assertEquals(AccountingState.PENDING, result.accessAccountingState());
            assertEquals(List.of("fact-commit", "access-account", "egress"), scene.trace);
            verify(scene.store).checkDisclosureWindow(same(scene.window));
        }
    }

    @Test void a_later_exact_access_retry_reauthorizes_through_store_and_retries_only_local_accounting() {
        try (var scene = new Scene(success(BigDecimal.ONE))) {
            scene.failAccounting.set(true); assertEquals(AccountingState.PENDING, scene.recorder.recordKnownStartResultAccess(scene.request).accessAccountingState());
            scene.failAccounting.set(false); assertEquals(AccountingState.SETTLED, scene.recorder.recordKnownStartResultAccess(scene.request).accessAccountingState());
            verify(scene.store, times(2)).recordKnownStartResultAccess(scene.request);
            verify(scene.budgets, times(2)).settleBudget(any(), any(), eq(scene.request.reservationId()), eq(new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO)));
            assertSame(scene.source, scene.recorded.sourceOutcome());
        }
    }

    @Test void already_settled_zero_access_still_uses_fresh_store_gate_without_repeating_settlement() {
        try (var scene = new Scene(success(null))) {
            var settled = budget(scene.request, AgentBudgetReservation.State.SETTLED, BigDecimal.ZERO);
            scene.recorded = scene.recorded(settled);
            var result = scene.recorder.recordKnownStartResultAccess(scene.request);
            assertEquals(AccountingState.SETTLED, result.accessAccountingState()); assertEquals(List.of("fact-commit", "egress"), scene.trace);
            verifyNoInteractions(scene.budgets);
        }
    }

    @Test void post_accounting_expiry_suppresses_body_without_undoing_the_recorded_fact_or_settlement() {
        try (var scene = new Scene(success(BigDecimal.ONE))) {
            scene.rejectEgress.set(true);
            var failure = assertThrows(AgentLedgerException.class, () -> scene.recorder.recordKnownStartResultAccess(scene.request));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertFalse(failure.getMessage().contains(RECEIPT));
            assertEquals(List.of("fact-commit", "access-account", "egress"), scene.trace);
            verify(scene.store, times(1)).recordKnownStartResultAccess(scene.request);
            verify(scene.budgets, times(1)).settleBudget(any(), any(), eq(scene.request.reservationId()), any());
        }
    }

    @Test void interruption_during_accounting_is_preserved_and_the_egress_gate_rejects_the_body() {
        try (var scene = new Scene(success(null))) {
            scene.interruptAccounting.set(true);
            try {
                assertThrows(AgentLedgerException.class, () -> scene.recorder.recordKnownStartResultAccess(scene.request));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertEquals(List.of("fact-commit", "access-account", "egress"), scene.trace);
        }
    }

    @Test void store_denial_never_reaches_access_accounting_or_public_result_construction() {
        try (var scene = new Scene(success(null))) {
            doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current access denied"))
                    .when(scene.store).recordKnownStartResultAccess(scene.request);
            var failure = assertThrows(AgentLedgerException.class, () -> scene.recorder.recordKnownStartResultAccess(scene.request));
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, failure.code()); verifyNoInteractions(scene.budgets);
            verify(scene.store, never()).checkDisclosureWindow(any());
        }
    }

    @Test void public_result_values_accept_only_known_start_shapes_and_preserve_source_cost() {
        UUID id = UUID.randomUUID(); String intent = UUID.randomUUID().toString();
        var original = success(new BigDecimal("0.0300"));
        var value = new StartResultAccessVO(RUN, id, intent, 1, HASH, original, AccountingState.PENDING);
        assertSame(original, value.sourceOutcome()); assertEquals(new BigDecimal("0.0300"), value.sourceOutcome().actualCost());
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(" ", id, intent, 1, HASH, success(null), AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, null, intent, 1, HASH, success(null), AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, id, "not-an-intent", 1, HASH, success(null), AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, id, intent, 0, HASH, success(null), AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, id, intent, 1, "bad", success(null), AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, id, intent, 1, HASH, success(null), null));
        for (var wrong : List.of(new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "UNKNOWN", null),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("other", "instance"), RECEIPT, null, BigDecimal.ZERO),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "instance"), "unverified-receipt", null, BigDecimal.ZERO))) {
            assertThrows(IllegalArgumentException.class, () -> new StartResultAccessVO(RUN, id, intent, 1, HASH, wrong, AccountingState.PENDING));
        }
    }

    @Test void null_observer_is_rejected_before_any_store_or_accounting_work() {
        try (var scene = new Scene(success(null))) {
            clearInvocations(scene.store, scene.ledger, scene.budgets);
            assertThrows(IllegalArgumentException.class, () -> scene.observe(null));
            verifyNoInteractions(scene.store, scene.ledger, scene.budgets); assertTrue(scene.trace.isEmpty());
        }
    }

    @Test void every_authorized_new_or_exact_access_observes_its_known_status_before_accounting_and_egress() {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN)) {
            var source = status == ToolExecutionOutcome.Status.SUCCEEDED ? success(new BigDecimal("0.0300"))
                    : new ToolExecutionOutcome(status, Map.of(), null, "ORIGINAL_NEGATIVE", new BigDecimal("0.0300"));
            try (var scene = new Scene(source)) {
                var observed = new ArrayList<ToolExecutionOutcome.Status>();
                ToolCommittedFactObserver observer = actual -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertEquals("fact-commit", scene.trace.getLast());
                    observed.add(actual); scene.trace.add("observer");
                };
                for (int read = 0; read < 2; read++) {
                    var result = scene.observe(observer); assertSame(source, result.sourceOutcome()); assertEquals(new BigDecimal("0.0300"), result.sourceOutcome().actualCost());
                }
                assertEquals(List.of(status, status), observed);
                assertEquals(List.of("fact-commit", "observer", "access-account", "egress", "fact-commit", "observer", "access-account", "egress"), scene.trace);
                verify(scene.store, times(2)).recordKnownStartResultAccess(scene.request);
                verify(scene.budgets, times(2)).settleBudget(any(), any(), eq(scene.request.reservationId()), any());
            }
        }
    }

    @Test void an_already_settled_access_is_still_observed_before_egress_without_settlement() {
        try (var scene = new Scene(success(null))) {
            scene.recorded = scene.recorded(budget(scene.request, AgentBudgetReservation.State.SETTLED, BigDecimal.ZERO));
            var observed = new ArrayList<ToolExecutionOutcome.Status>();
            var result = scene.observe(status -> { observed.add(status); scene.trace.add("observer"); });
            assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), observed); assertEquals(AccountingState.SETTLED, result.accessAccountingState());
            assertEquals(List.of("fact-commit", "observer", "egress"), scene.trace); verifyNoInteractions(scene.budgets);
        }
    }

    @Test void throwing_observer_keeps_the_fact_but_returns_safe_dependency_without_accounting_or_egress() {
        for (boolean interrupted : List.of(false, true)) try (var scene = new Scene(success(BigDecimal.ONE))) {
            var original = scene.recorded;
            try {
                var failure = assertThrows(AgentLedgerException.class, () -> scene.observe(status -> {
                    assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, status); scene.trace.add("observer");
                    if (interrupted) Thread.currentThread().interrupt();
                    throw new IllegalStateException("private observer source-instance", new IllegalArgumentException(RECEIPT));
                }));
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause()); assertEquals(0, failure.getSuppressed().length);
                assertFalse(failure.getMessage().contains("private")); assertFalse(failure.getMessage().contains(RECEIPT)); assertFalse(failure.getMessage().contains("source-instance"));
                assertEquals(interrupted, Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertSame(original, scene.recorded); assertEquals(List.of("fact-commit", "observer"), scene.trace); verifyNoInteractions(scene.budgets);
            verify(scene.store, never()).checkDisclosureWindow(any()); verify(scene.store).recordKnownStartResultAccess(scene.request);
        }
    }

    @Test void denial_or_mismatched_fact_identity_never_notifies_an_observer() {
        for (boolean denied : List.of(false, true)) try (var scene = new Scene(success(null))) {
            if (denied) doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current access denied")).when(scene.store).recordKnownStartResultAccess(scene.request);
            else {
                var prior = scene.recorded;
                scene.recorded = new RecordedAccessVO("10000000-0000-0000-0000-000000000002", prior.accessId(), prior.actionIntentId(), prior.sourceActionRevision(), prior.sourceResultSha256(), prior.sourceOutcome(), prior.accessBudget(), prior.window());
            }
            var observed = new ArrayList<ToolExecutionOutcome.Status>();
            var failure = assertThrows(AgentLedgerException.class, () -> scene.observe(observed::add));
            assertEquals(denied ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertTrue(observed.isEmpty()); verifyNoInteractions(scene.budgets); verify(scene.store, never()).checkDisclosureWindow(any());
        }
    }

    @Test void committed_access_stays_observed_when_later_disclosure_fails_or_the_observer_sets_interrupt() {
        for (boolean interrupted : List.of(false, true)) try (var scene = new Scene(success(null))) {
            scene.rejectEgress.set(!interrupted); var observed = new ArrayList<ToolExecutionOutcome.Status>();
            try {
                assertThrows(AgentLedgerException.class, () -> scene.observe(status -> {
                    observed.add(status); scene.trace.add("observer"); if (interrupted) Thread.currentThread().interrupt();
                }));
                assertEquals(interrupted, Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), observed); assertEquals(List.of("fact-commit", "observer", "access-account", "egress"), scene.trace);
            verify(scene.store).recordKnownStartResultAccess(scene.request); verify(scene.budgets).settleBudget(any(), any(), eq(scene.request.reservationId()), any());
        }
    }

    private static StartResultAccessRequestVO request() {
        var actor = ToolInvocationStoreTest.ACTOR;
        var run = new AgentRunAuthorization.Request(actor, RUN, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60));
        var binding = new ToolExecutionGrant.BindingVO(run, TOOL.reference(), TOOL.policy().bindingSha256(), "TIANJI_TOOL_PROCESS", URI.create("https://agent.example/20"), "tool.process.execute", null);
        var action = new ToolActionRequest(actor, TOOL, "source-invoice", "v1", Map.of("amount", 20L));
        return new StartResultAccessRequestVO(binding, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(30)), TOOL, action, UUID.randomUUID());
    }
    private static ToolExecutionOutcome success(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "source-instance"), RECEIPT, null, cost); }
    private static AgentBudgetReservation budget(StartResultAccessRequestVO request, AgentBudgetReservation.State state, BigDecimal cost) {
        return new AgentBudgetReservation(request.reservationId(), request.lease().epoch(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0,
                BigDecimal.ZERO, state, state == AgentBudgetReservation.State.RESERVED ? null : 0L, state == AgentBudgetReservation.State.RESERVED ? null : 0L, cost, false);
    }
    private static final class Scene implements AutoCloseable {
        final ToolResultAccessStore store = mock(ToolResultAccessStore.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final StartResultAccessRequestVO request = request();
        final DisclosureWindowVO window = new DisclosureWindowVO(NOW.plusSeconds(10), 10_000_000_000L);
        final ToolExecutionOutcome source;
        final ToolResultAccessRecorder recorder;
        final List<String> trace = new ArrayList<>();
        final AtomicBoolean failAccounting = new AtomicBoolean(), rejectEgress = new AtomicBoolean(), interruptAccounting = new AtomicBoolean();
        RecordedAccessVO recorded;
        boolean observerCall;

        Scene(ToolExecutionOutcome source) {
            this.source = source; recorded = recorded(budget(request, AgentBudgetReservation.State.RESERVED, null));
            when(store.usesLedger(ledger)).thenReturn(true); when(ledger.budgets()).thenReturn(budgets);
            when(store.recordKnownStartResultAccess(request)).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); trace.add("fact-commit"); return recorded;
            });
            when(budgets.settleBudget(eq(request.binding().run().actor()), eq(request.lease()), eq(request.reservationId()), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertFalse(trace.isEmpty()); assertEquals(observerCall ? "observer" : "fact-commit", trace.getLast());
                trace.add("access-account"); assertEquals(new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO), call.getArgument(3));
                if (interruptAccounting.get()) Thread.currentThread().interrupt();
                if (failAccounting.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Private accounting failure");
                return budget(request, AgentBudgetReservation.State.SETTLED, BigDecimal.ZERO);
            });
            doAnswer(call -> {
                assertSame(window, call.getArgument(0)); trace.add("egress");
                if (rejectEgress.get() || Thread.currentThread().isInterrupted()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current disclosure window is unavailable");
                return null;
            }).when(store).checkDisclosureWindow(any());
            recorder = new ToolResultAccessRecorder(store, ledger);
        }
        RecordedAccessVO recorded(AgentBudgetReservation budget) { return new RecordedAccessVO(RUN, request.accessId(), request.action().intentId(), 7, HASH, source, budget, window); }
        StartResultAccessVO observe(ToolCommittedFactObserver observer) {
            observerCall = true;
            try { return recorder.recordKnownStartResultAccess(request, observer); }
            finally { observerCall = false; }
        }
        @Override public void close() { Thread.interrupted(); }
    }
}
