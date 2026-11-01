package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.AccountingState;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Recorder/Store/SQL/Spring budget flow over controlled JDBC and synchronous trusted port probes.
 * These source projections prove no deployed IAM, HTTP or physical PostgreSQL behavior. */
class ToolApprovedStartRecorderTest {
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RUN = ToolInvocationStoreTest.RUN, HOST_APPROVAL = "approval-receipt", HASH = "a".repeat(64);
    private static final String RECEIPT = "tianji:ps-start:v1:" + HASH, INSTANCE = "approved-process-instance";
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.approvedStart", 1, "approved_start", "Fixed approved START",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
            new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.invoice", ProcessClient.START_RESOURCE_KEY, HASH, "approval-1"));
    private static final VerifiedExecutor EXECUTOR = new VerifiedExecutor(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "approved-business-executor", "https://business.example/20"), NOW.plusSeconds(50));

    @Test void first_wait_even_with_a_receipt_finishes_and_same_uuid_never_verifies_or_revives() throws Exception {
        try (var s = new Scene(false)) {
            var statuses = new ArrayList<ToolExecutionOutcome.Status>();
            var first = s.execute(s.request, HOST_APPROVAL, statuses::add);
            assertEquals("AWAITING_APPROVAL", first.actionStateObserved()); assertNull(first.ownAttempt());
            var row = new HashMap<>(s.rig.row(s.request)); var budget = s.rig.base.budgetRows.get(s.request.reservationId());
            assertEquals(AgentBudgetReservation.State.SETTLED, budget.state()); assertEquals(BigDecimal.ZERO, budget.actualCost());
            assertEquals("AWAITING_APPROVAL", s.execute(s.request, HOST_APPROVAL, statuses::add).actionStateObserved());
            assertEquals(row, s.rig.row(s.request)); assertEquals(budget, s.rig.base.budgetRows.get(s.request.reservationId()));
            assertEquals("AWAITING_APPROVAL", s.rig.selected(s.request, () -> s.recorder.executeApprovedStart(s.approvals, s.transport, s.request, HOST_APPROVAL)).actionStateObserved());
            assertEquals(List.of(ToolExecutionOutcome.Status.AWAITING_APPROVAL, ToolExecutionOutcome.Status.AWAITING_APPROVAL), statuses);
            assertEquals(0, s.approvals.calls.get()); assertEquals(0, s.transport.calls.get()); assertEquals(0, s.accounts.get());
            assertEquals(1, s.rig.base.budgetInserts.get());
        }
    }

    @Test void nullable_host_receipt_returns_existing_wait_metadata_without_another_invocation_or_budget() throws Exception {
        try (var s = new Scene(true)) {
            var statuses = new ArrayList<ToolExecutionOutcome.Status>(); var result = s.execute(s.request, null, statuses::add);
            assertEquals("AWAITING_APPROVAL", result.actionStateObserved()); assertNull(result.ownAttempt());
            assertEquals(List.of(ToolExecutionOutcome.Status.AWAITING_APPROVAL), statuses); assertNull(s.rig.row(s.request));
            assertEquals(1, s.rig.base.budgetInserts.get()); assertEquals(0, s.approvals.calls.get()); assertEquals(0, s.accounts.get());
        }
    }

    @Test void fresh_host_composes_independent_services_exact_pdp_and_bounded_once_before_fact_observer_and_account() throws Exception {
        try (var s = new Scene(true)) {
            var statuses = new ArrayList<ToolExecutionOutcome.Status>();
            var result = s.execute(s.request, HOST_APPROVAL, status -> { assertTrue(s.trace.contains("complete-ack")); s.trace.add("observer"); statuses.add(status); });
            assertEquals("DISPATCHING", result.actionStateObserved()); assertEquals("SUCCEEDED", result.ownAttempt().state());
            assertEquals("APPLIED", s.rig.base.intentRow.get().state()); assertEquals(Map.of("instanceId", INSTANCE), result.ownAttempt().outcome().output());
            assertEquals(AccountingState.SETTLED, result.ownAttempt().accountingState()); assertEquals(BigDecimal.ZERO, result.ownAttempt().outcome().actualCost());
            assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), statuses); assertEquals(1, s.approvals.calls.get()); assertEquals(1, s.transport.calls.get());
            assertEquals(1, s.transport.starts.get()); assertEquals(0, s.transport.legacyStarts.get()); assertEquals(1, s.dispatchChecks.get());
            assertEquals(s.checked.get().deadlineAt(), s.transport.actualDeadline.get()); assertEquals(0, s.transport.actualDeadline.get().getNano() % 1_000);
            assertEquals(s.request.parametersSha256(), s.checked.get().parametersSha256()); assertEquals(s.request.action().intentId(), s.checked.get().actionIntentId());
            assertNotEquals(s.core.approval.consumer(), EXECUTOR.actor()); assertEquals(1, s.accounts.get());
            assertTrue(s.trace.indexOf("begin-ack") < s.trace.indexOf("operation")); assertTrue(s.trace.indexOf("complete-ack") < s.trace.indexOf("observer"));
            assertTrue(s.trace.indexOf("observer") < s.trace.indexOf("account")); assertTrue(s.nonce.get().matches("[0-9a-f]{64}"));
            assertEquals(ToolInvocationStore.dispatchNonceSha256(s.nonce.get()), s.attempt.get().dispatchNonceSha256());
            assertFalse(result.toString().contains(INSTANCE)); assertFalse(result.toString().contains(RECEIPT));
        }
    }

    @Test void profile_storage_ambient_and_host_receipt_validation_precede_preparation() throws Exception {
        try (var s = new Scene(false)) {
            for (String receipt : List.of("", " ", " approval-receipt", "x".repeat(256), "private\nreceipt", "\uD800")) {
                assertThrows(IllegalArgumentException.class, () -> s.execute(s.request, receipt, status -> { }));
            }
            assertThrows(IllegalArgumentException.class, () -> s.execute(s.request, null, null));
            assertThrows(IllegalArgumentException.class, () -> s.rig.selected(s.request, () -> s.recorder.executeApprovedStart(null, s.transport, s.request, HOST_APPROVAL)));
            assertThrows(IllegalArgumentException.class, () -> s.rig.selected(s.request, () -> s.recorder.executeApprovedStart(s.approvals, null, s.request, HOST_APPROVAL)));
            var bad = spy(s.request); var descriptor = new ToolDescriptor("bad.start", 1, "bad_start", "Invalid profile", TOOL.input(), TOOL.output(),
                    new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.invoice", ProcessClient.START_RESOURCE_KEY, HASH, null));
            doReturn(descriptor).when(bad).descriptor(); assertThrows(IllegalArgumentException.class, () -> s.execute(bad, HOST_APPROVAL, status -> { }));
            var paid = new PreparationRequestVO(s.request.binding(), s.request.lease(), TOOL, s.request.arguments(), s.request.action(), s.request.invocationId(), new BigDecimal("0.01"), s.request.intentExpiry());
            assertThrows(IllegalArgumentException.class, () -> s.execute(paid, HOST_APPROVAL, status -> { }));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> { })); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertEquals(0, s.rig.base.budgetInserts.get()); assertEquals(0, s.approvals.calls.get());
        }
        try (var s = new Scene(false, false)) { assertThrows(AgentLedgerException.class, () -> s.execute(s.request, null, status -> { })); assertEquals(0, s.rig.base.budgetInserts.get()); }
    }

    @Test void entry_time_is_captured_before_material_access_and_cannot_be_reset_after_it() throws Exception {
        try (var s = new Scene(false)) {
            var material = s.rig.base.ledger.executionMaterial(ToolInvocationStoreTest.ACTOR, new GetAgentRunQuery(RUN)); var once = new AtomicBoolean();
            doAnswer(call -> { if (once.compareAndSet(false, true)) s.advance(21); return material; }).when(s.rig.base.ledger).executionMaterial(eq(ToolInvocationStoreTest.ACTOR), any());
            assertThrows(RuntimeException.class, () -> s.execute(s.request, null, status -> { })); assertEquals(0, s.rig.base.budgetInserts.get());
            assertEquals(0, s.approvals.calls.get()); assertEquals(0, s.transport.calls.get());
        }
    }

    @Test void original_budget_covers_prepare_approval_business_and_dispatch_without_phase_resets() throws Exception {
        try (var s = new Scene(true)) {
            s.afterPrepare.set(() -> s.advance(6)); s.approvals.beforeCallback.set(() -> s.advance(6)); s.transport.beforeCallback.set(() -> s.advance(6)); s.afterPdp.set(() -> s.advance(4));
            assertThrows(RuntimeException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> { }));
            assertNotNull(s.approvals.request.get(),"Approval stage must have been entered before this expiry case");
            assertEquals(NOW.plusSeconds(20), s.approvals.request.get().deadlineAt()); assertEquals(0, s.transport.starts.get());
            assertEquals(1, s.rig.base.budgetInserts.get()); verify(s.preparations, never()).consumeApprovalAndBeginDispatch(any(), any(), any(), any(), any(), anyString(), any());
        }
    }

    @Test void capability_refresh_during_verification_does_not_replace_original_e0_at_dispatch() throws Exception {
        try (var s = new Scene(true)) {
            var old = s.rig.evidence(s.request);
            s.transport.beforeCallback.set(() -> s.rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4),
                    "9"+"4".repeat(62),"ENC:v1:key:new:ticket", NOW.plusSeconds(24))));
            assertThrows(RuntimeException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> { }));
            assertEquals(0, s.dispatchChecks.get()); assertEquals(0, s.transport.starts.get()); assertEquals(1, s.rig.base.budgetInserts.get());
        }
    }

    @Test void source_and_business_preconsume_failures_preserve_the_open_wait_and_do_not_cleanup() throws Exception {
        for (String stage : List.of("approval-denied","approval-unavailable","business")) try (var s = new Scene(true)) {
            var before=s.rig.base.intentRow.get();
            if (stage.equals("business")) s.transport.beforeFailure.set(FixedBpmnStartToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE);
            else s.approvals.beforeFailure.set(stage.equals("approval-denied")?FixedToolApprovalVerifier.FailureKind.APPROVAL_DENIED:FixedToolApprovalVerifier.FailureKind.APPROVAL_UNAVAILABLE);
            assertThrows(RuntimeException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> fail("No fact notification before consumption")));
            assertSame(before, s.rig.base.intentRow.get()); assertEquals(1, s.rig.base.budgetInserts.get()); assertEquals(0, s.transport.starts.get());
            verify(s.preparations, never()).closePreparedWriteFailure(any(), any(), any()); verify(s.preparations, never()).completeApprovedWrite(any(), any(), anyString(), any());
        }
    }

    @Test void current_dispatch_denial_is_not_replaced_by_an_approved_receipt() throws Exception {
        try (var s = new Scene(true)) {
            s.dispatchDecision.set(request -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.DENY, null));
            var failure=assertThrows(AgentLedgerException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> { }));
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, failure.code()); assertEquals("AWAITING_APPROVAL", s.rig.base.intentRow.get().state());
            assertEquals(0, s.transport.starts.get()); assertEquals(1, s.rig.base.budgetInserts.get());
        }
    }

    @Test void consumed_not_sent_denied_unknown_and_success_are_historical_facts_without_receipt_release() throws Exception {
        for (var kind : List.of(FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT, FixedBpmnStartToolTransport.FailureKind.OPERATION_DENIED,
                FixedBpmnStartToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN, FixedBpmnStartToolTransport.FailureKind.OPERATION_INVALID_RESPONSE)) try (var s = new Scene(true)) {
            s.transport.operationFailure.set(kind); var statuses=new ArrayList<ToolExecutionOutcome.Status>(); var result=s.execute(s.request, HOST_APPROVAL, statuses::add);
            var expected=kind==FixedBpmnStartToolTransport.FailureKind.OPERATION_NOT_SENT?ToolExecutionOutcome.Status.FAILED_KNOWN:
                    kind==FixedBpmnStartToolTransport.FailureKind.OPERATION_DENIED?ToolExecutionOutcome.Status.REJECTED:ToolExecutionOutcome.Status.UNKNOWN;
            assertEquals(expected, result.ownAttempt().outcome().status()); assertEquals(List.of(expected), statuses); assertNotNull(s.rig.base.intentRow.get().approval());
            assertEquals(1, s.transport.starts.get()); assertEquals(2, s.rig.base.budgetInserts.get());
            var repeat=s.execute(s.next(), HOST_APPROVAL, status -> fail("Metadata is not a newly committed outcome"));
            assertNull(repeat.ownAttempt()); assertEquals(expected.name(), repeat.actionStateObserved()); assertEquals(1, s.transport.starts.get());
        }
    }

    @Test void cancellation_after_consumption_before_operation_records_not_sent_and_restores_interruption() throws Exception {
        try (var s = new Scene(true)) {
            s.afterBegin.set(() -> Thread.currentThread().interrupt()); var statuses=new ArrayList<ToolExecutionOutcome.Status>();
            try {
                s.execute(s.request, HOST_APPROVAL, statuses::add);
            } catch (RuntimeException withheldAfterFact) {
                assertTrue(s.trace.contains("complete-ack"));
            } finally { assertTrue(Thread.currentThread().isInterrupted()); Thread.interrupted(); }
            assertEquals("FAILED_KNOWN", s.rig.base.intentRow.get().state()); assertEquals(0, s.transport.starts.get());
            assertEquals(List.of(ToolExecutionOutcome.Status.FAILED_KNOWN), statuses); assertNotNull(s.rig.base.intentRow.get().approval());
        }
    }

    @Test void legacy_unbounded_only_operations_are_never_used_as_a_bounded_fallback_after_consumption() throws Exception {
        try (var s = new Scene(true)) {
            s.transport.legacyOnly.set(true); var result=s.execute(s.request, HOST_APPROVAL, status -> { });
            assertEquals("FAILED_KNOWN", result.ownAttempt().state()); assertEquals(0, s.transport.legacyStarts.get()); assertEquals(0, s.transport.starts.get());
        }
    }

    @Test void both_outer_postchecks_can_hide_return_without_losing_committed_observer_truth_or_restarting() throws Exception {
        for (boolean approvalOuter : List.of(false, true)) try (var s = new Scene(true)) {
            if (approvalOuter) s.approvals.afterFailure.set(FixedToolApprovalVerifier.FailureKind.DEADLINE_EXCEEDED);
            else s.transport.afterFailure.set(FixedBpmnStartToolTransport.FailureKind.DEADLINE_EXCEEDED);
            var statuses=new ArrayList<ToolExecutionOutcome.Status>(); assertThrows(RuntimeException.class, () -> s.execute(s.request, HOST_APPROVAL, statuses::add));
            assertEquals("APPLIED", s.rig.base.intentRow.get().state()); assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), statuses);
            assertEquals(1, s.transport.starts.get()); assertEquals(1, s.accounts.get()); assertEquals(2, s.rig.base.budgetInserts.get());
            verify(s.preparations, never()).closePreparedWriteFailure(any(), any(), any());
        }
    }

    @Test void observer_failure_wins_nested_wrapping_and_prevents_accounting_after_a_committed_success() throws Exception {
        try (var s = new Scene(true)) {
            var failure=assertThrows(AgentLedgerException.class, () -> s.execute(s.request, HOST_APPROVAL, status -> { throw new IllegalStateException("private observer"); }));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            assertEquals("APPLIED", s.rig.base.intentRow.get().state()); assertEquals(0, s.accounts.get()); assertEquals(1, s.transport.starts.get());
            assertTrue(s.transport.maskedCallbackLedger.get()>0); verify(s.preparations, never()).closePreparedWriteFailure(any(), any(), any());
        }
    }

    @Test void accounting_failure_retains_successful_fact_and_observer_but_returns_pending() throws Exception {
        try (var s = new Scene(true)) {
            s.failAccount.set(true); var statuses=new ArrayList<ToolExecutionOutcome.Status>(); var result=s.execute(s.request, HOST_APPROVAL, statuses::add);
            assertEquals("SUCCEEDED", result.ownAttempt().state()); assertEquals(AccountingState.PENDING, result.ownAttempt().accountingState());
            assertEquals(List.of(ToolExecutionOutcome.Status.SUCCEEDED), statuses); assertEquals("APPLIED", s.rig.base.intentRow.get().state());
        }
    }

    @Test void a_consumption_loser_uses_actual_advanced_observation_instead_of_a_stale_wait_snapshot() throws Exception {
        try (var s = new Scene(true)) {
            doAnswer(call -> {
                var previous=s.rig.active; var row=s.rig.base.row.get();
                try { var winner=s.core.begin(s.core.request(RUN)); assertTrue(winner.started()); }
                finally { s.rig.active=previous; s.rig.base.row.set(row); }
                return call.callRealMethod();
            })
                    .when(s.preparations).consumeApprovalAndBeginDispatch(any(), any(), any(), any(), any(), anyString(), any());
            var result=s.execute(s.request, HOST_APPROVAL, status -> fail("Loser metadata must not notify AWAIT or a fact"));
            assertEquals("DISPATCHING", result.actionStateObserved()); assertNull(result.ownAttempt()); assertEquals(0, s.transport.starts.get()); assertEquals(0, s.accounts.get());
            assertEquals(2, s.rig.base.budgetInserts.get());
        }
    }

    @Test void known_generic_competition_with_nonzero_cost_is_metadata_only_and_never_settled_as_zero() throws Exception {
        try (var s = new Scene(true)) {
            var begin=s.core.begin(s.request); s.rig.budgetUnknown(s.request, new BigDecimal("0.03"));
            var outcome=new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, new BigDecimal("0.03"));
            s.core.complete(s.request, begin.attempt(),"1".repeat(64), outcome); s.trace.clear(); s.accounts.set(0);
            var result=s.execute(s.request, HOST_APPROVAL, status -> fail("Historical metadata is not a new outcome"));
            assertEquals("APPLIED", result.actionStateObserved()); assertNotNull(result.ownAttempt()); assertNull(result.ownAttempt().outcome());
            assertEquals(0, s.accounts.get()); assertEquals(0, s.approvals.calls.get()); assertEquals(new BigDecimal("0.03"), s.rig.base.intentRow.get().outcome().actualCost());
        }
    }

    @Test void observed_result_values_are_closed_without_relaxing_the_idempotent_start_contract() {
        String intent=UUID.randomUUID().toString(); assertNotNull(new ToolInvocationRecorder.ApprovedStartResultVO(intent,"AWAITING_APPROVAL", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.ApprovedStartResultVO("bad","AWAITING_APPROVAL", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.ApprovedStartResultVO(intent,"SUCCEEDED", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.ApprovedStartResultVO(intent,"AWAITING_APPROVAL", 0, null));
        var own=new ToolInvocationRecorder.WriteAttemptResultVO(RUN, UUID.randomUUID(),"SUCCEEDED", 3, 1, NOW, NOW,
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, BigDecimal.ZERO), true, AccountingState.SETTLED);
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.ApprovedStartResultVO(intent,"AWAITING_APPROVAL", 1, own));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.IdempotentStartResultVO(intent,"AWAITING_APPROVAL", 1, null));
    }

    private static final class Scene implements AutoCloseable {
        final ToolApprovedInvocationStoreTest.Fixture core;
        final ToolWriteInvocationStoreTest.Rig rig;
        final PreparationRequestVO request;
        final ToolInvocationStore preparations;
        final ToolInvocationRecorder recorder;
        final ApprovalProbe approvals;
        final BusinessProbe transport;
        final List<String> trace=new ArrayList<>();
        final AtomicInteger accounts=new AtomicInteger(), dispatchChecks=new AtomicInteger();
        final AtomicBoolean failAccount=new AtomicBoolean();
        final AtomicReference<Runnable> afterPrepare=new AtomicReference<>(() -> { }), afterBegin=new AtomicReference<>(() -> { }), afterPdp=new AtomicReference<>(() -> { });
        final AtomicReference<ToolExecutionAuthorization.Request> checked=new AtomicReference<>();
        final AtomicReference<Function<ToolExecutionAuthorization.Request, ToolExecutionAuthorization.CheckResult>> dispatchDecision=new AtomicReference<>();
        final AtomicReference<ToolInvocationStore.DispatchAttemptVO> attempt=new AtomicReference<>();
        final AtomicReference<String> nonce=new AtomicReference<>();
        Scene(boolean existingWait) throws Exception { this(existingWait, true); }
        Scene(boolean existingWait, boolean ready) throws Exception {
            core=new ToolApprovedInvocationStoreTest.Fixture(TOOL, existingWait, BigDecimal.ZERO); rig=core.rig;
            request=existingWait?core.request(RUN):core.waiting; preparations=spy(rig.store);
            dispatchDecision.set(query -> rig.allowed(query, ToolExecutionAuthorization.Boundary.DISPATCH));
            doAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); ToolExecutionAuthorization.Request query=call.getArgument(0);
                checked.set(query); dispatchChecks.incrementAndGet(); var result=dispatchDecision.get().apply(query); afterPdp.get().run(); return result;
            }).when(rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.DISPATCH));
            doAnswer(call -> { var result=call.callRealMethod(); trace.add("prepare-ack"); afterPrepare.get().run(); return result; })
                    .when(preparations).prepareOrLoadApprovedWrite(any(), any());
            doAnswer(call -> { var result=(ToolInvocationStore.ApprovedWriteDispatchBeginResultVO)call.callRealMethod(); if (result.started()) { trace.add("begin-ack"); afterBegin.get().run(); } return result; })
                    .when(preparations).consumeApprovalAndBeginDispatch(any(), any(), any(), any(), any(), anyString(), any());
            doAnswer(call -> { attempt.set(call.getArgument(1)); nonce.set(call.getArgument(2)); var result=call.callRealMethod(); trace.add("complete-ack"); return result; })
                    .when(preparations).completeApprovedWrite(any(), any(), anyString(), any());
            doAnswer(call -> {
                if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                    assertTrue(trace.contains("complete-ack")); accounts.incrementAndGet(); trace.add("account");
                    if (failAccount.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,"private accounting");
                }
                return call.callRealMethod();
            }).when(rig.base.budgets).settleBudget(any(), any(), anyString(), any());
            recorder=new ToolInvocationRecorder(rig.base.source, rig.base.ledger, preparations, rig.base.capabilities, rig.base.authority, rig.base.clock(), rig.base.nano::get);
            if (ready) recorder.verifyStorage(); approvals=new ApprovalProbe(this); transport=new BusinessProbe(this);
        }
        PreparationRequestVO next() { return core.request(RUN); }
        ToolInvocationRecorder.ApprovedStartResultVO execute(PreparationRequestVO request, String receipt, ToolCommittedFactObserver observer) {
            return rig.selected(request, () -> recorder.executeApprovedStart(approvals, transport, request, receipt, observer));
        }
        void advance(long seconds) { rig.base.wall.set(rig.base.wall.get().plusSeconds(seconds)); rig.base.databaseNow.set(rig.base.databaseNow.get().plusSeconds(seconds)); rig.base.nano.addAndGet(Duration.ofSeconds(seconds).toNanos()); }
        @Override public void close() { core.close(); }
    }

    private static final class ApprovalProbe implements FixedToolApprovalVerifier {
        final Scene scene; final AtomicInteger calls=new AtomicInteger(); final AtomicReference<Request> request=new AtomicReference<>();
        final AtomicReference<ApprovedObservation> observation;
        final AtomicReference<FailureKind> beforeFailure=new AtomicReference<>(), afterFailure=new AtomicReference<>();
        final AtomicReference<Runnable> beforeCallback=new AtomicReference<>(() -> { });
        ApprovalProbe(Scene scene) { this.scene=scene; observation=new AtomicReference<>(scene.core.observation(NOW.plusSeconds(25).plusNanos(987), NOW.plusSeconds(30))); }
        @Override public <T>T withVerifiedApproval(Request request, Callback<T> callback) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); calls.incrementAndGet(); this.request.set(request); scene.trace.add("approval"); beforeCallback.get().run();
            if (beforeFailure.get()!=null) throw new Failure(beforeFailure.get());
            if (!request.deadlineAt().isAfter(scene.rig.base.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            var source=observation.get(); var expiry=source.consumerExpiresAt().isBefore(request.deadlineAt())?source.consumerExpiresAt():request.deadlineAt();
            var bounded=new ApprovedObservation(source.sourceId(), source.sourceIssuer(), source.sourceResource(), source.binding(), source.approval(), source.consumer(), expiry);
            T value;
            try { value=callback.apply(bounded); }
            catch (AgentLedgerException rejected) { throw new AgentLedgerException(rejected.code(),"Controlled approval callback unavailable"); }
            catch (RuntimeException rejected) { throw new Failure(FailureKind.APPROVAL_UNAVAILABLE); }
            if (afterFailure.get()!=null) throw new Failure(afterFailure.get()); return value;
        }
    }

    private static final class BusinessProbe implements FixedBpmnStartToolTransport {
        final Scene scene; final AtomicInteger calls=new AtomicInteger(), starts=new AtomicInteger(), legacyStarts=new AtomicInteger(), maskedCallbackLedger=new AtomicInteger();
        final AtomicReference<Request> request=new AtomicReference<>(); final AtomicReference<Instant> actualDeadline=new AtomicReference<>();
        final AtomicReference<FailureKind> beforeFailure=new AtomicReference<>(), operationFailure=new AtomicReference<>(), afterFailure=new AtomicReference<>();
        final AtomicReference<Runnable> beforeCallback=new AtomicReference<>(() -> { }); final AtomicBoolean legacyOnly=new AtomicBoolean();
        BusinessProbe(Scene scene) { this.scene=scene; }
        @Override public <T>T withVerifiedCall(Request request, Callback<T> callback) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); calls.incrementAndGet(); this.request.set(request); scene.trace.add("business"); beforeCallback.get().run();
            if (beforeFailure.get()!=null) throw new Failure(beforeFailure.get()); var consumed=new AtomicBoolean(); var acknowledged=new AtomicBoolean();
            OneShotStart operation=legacyOnly.get()?() -> { legacyStarts.incrementAndGet(); return new StartObservation(RECEIPT, INSTANCE); }:new OneShotStart() {
                @Override public StartObservation start() { legacyStarts.incrementAndGet(); throw new AssertionError("Recorder must never use an unbounded fallback"); }
                @Override public StartObservation start(Instant deadline) {
                    assertTrue(consumed.compareAndSet(false, true)); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertTrue(scene.trace.contains("begin-ack")); assertEquals("DISPATCHING", scene.rig.base.intentRow.get().state());
                    assertEquals(scene.checked.get().deadlineAt(), deadline); assertFalse(deadline.isAfter(request.deadlineAt())); assertEquals(0, deadline.getNano()%1_000);
                    actualDeadline.set(deadline); starts.incrementAndGet(); scene.trace.add("operation");
                    if (operationFailure.get()!=null) throw new Failure(operationFailure.get()); acknowledged.set(true); return new StartObservation(RECEIPT, INSTANCE);
                }
            };
            T value;
            try { value=callback.apply(EXECUTOR, operation); }
            catch (AgentLedgerException rejected) { if (acknowledged.get()) { maskedCallbackLedger.incrementAndGet(); throw new Failure(FailureKind.OPERATION_RESULT_UNKNOWN); } throw new AgentLedgerException(rejected.code(),"Controlled business callback unavailable"); }
            catch (Failure rejected) { throw new Failure(rejected.kind()); }
            catch (RuntimeException rejected) { throw new Failure(consumed.get()?FailureKind.OPERATION_RESULT_UNKNOWN:FailureKind.OPERATION_NOT_SENT); }
            if (afterFailure.get()!=null) throw new Failure(afterFailure.get()); return value;
        }
    }
}
