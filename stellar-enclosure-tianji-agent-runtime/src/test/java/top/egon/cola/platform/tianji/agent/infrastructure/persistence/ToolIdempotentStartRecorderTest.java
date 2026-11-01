package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.AccountingState;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.IdempotentStartResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.WriteAttemptResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
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
import static top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport.FailureKind.*;

/** Real recorder/Store/Spring/budget flow over the shared controlled JDBC Rig.
 * The Action participant and transport are mocks; this does not prove cryptographic identity, HTTP, or physical PostgreSQL acceptance. */
class ToolIdempotentStartRecorderTest {
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RUN = ToolInvocationStoreTest.RUN, OTHER_RUN = "10000000-0000-0000-0000-000000000002";
    private static final String RECEIPT = "tianji:ps-start:v1:" + "a".repeat(64), INSTANCE = "private-process-instance";
    private static final ToolDescriptor TOOL = tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, true);
    private static final VerifiedExecutor EXECUTOR = new VerifiedExecutor(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "start-executor", "https://process-idp.example/20"), NOW.plusSeconds(15));

    @Test void nonce_and_fact_commits_precede_one_shot_and_accounting_without_fabricating_global_state() throws Exception {
        try (var scene = new Scene()) {
            var result = scene.execute(); var own = result.ownAttempt(); assertNotNull(own);
            assertEquals(scene.request.action().intentId(), result.actionIntentId()); assertEquals("PREPARED", result.actionStateAtPreparation()); assertEquals(1, result.actionRevisionAtPreparation());
            assertEquals("SUCCEEDED", own.state()); assertEquals(3, own.revision()); assertEquals(1, own.dispatchEpoch()); assertTrue(own.operationInvokedThisCall());
            assertEquals(Map.of("instanceId", INSTANCE), own.outcome().output()); assertEquals(RECEIPT, own.outcome().businessReceiptId()); assertEquals(BigDecimal.ZERO, own.outcome().actualCost());
            assertEquals(AccountingState.SETTLED, own.accountingState()); assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals(3, scene.rig.base.intentRow.get().revision());
            assertEquals(1, scene.transport.starts.get()); assertTrue(scene.trace.indexOf("begin-ack") < scene.trace.indexOf("operation"));
            assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("accounting")); assertEquals(1, scene.accounts.get());
            assertTrue(scene.nonce.get().matches("[0-9a-f]{64}")); assertEquals(ToolInvocationStore.dispatchNonceSha256(scene.nonce.get()), scene.attempt.get().dispatchNonceSha256());
            assertFalse(result.toString().contains(RECEIPT)); assertFalse(result.toString().contains(INSTANCE)); assertFalse(own.toString().contains(scene.nonce.get()));
            assertEquals(NOW.plusSeconds(20), scene.transport.request.get().deadlineAt()); assertEquals(scene.request.action(), scene.transport.request.get().action());
            assertEquals(scene.request.invocationId(), scene.transport.request.get().invocationId());
        }
    }

    @Test void profile_readiness_affinity_and_ambient_boundaries_fail_before_preparation() throws Exception {
        for (var descriptor : List.of(tool(ToolPolicy.Risk.READ_ONLY, ProcessClient.START_RESOURCE_KEY, true),
                tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, true),
                tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, "other.write", true),
                tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, false))) {
            try (var scene = new Scene(descriptor, true)) {
                clearInvocations(scene.rig.base.source, scene.rig.base.capabilities, scene.rig.base.authority);
                var failure = assertThrows(IllegalArgumentException.class, scene::execute); assertFalse(failure.getMessage().contains("Read-only"));
                verifyNoInteractions(scene.rig.base.source, scene.rig.base.capabilities, scene.rig.base.authority); assertEquals(0, scene.transport.calls.get());
            }
        }
        try (var scene = new Scene(TOOL, false)) { assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, scene::execute); assertEquals(0, scene.rig.base.budgetInserts.get()); }
        try (var scene = new Scene()) {
            var paid = new PreparationRequestVO(scene.request.binding(), scene.request.lease(), TOOL, scene.request.arguments(), scene.request.action(), scene.request.invocationId(), new BigDecimal("0.01"), scene.request.intentExpiry());
            assertThrows(IllegalArgumentException.class, () -> scene.rig.selected(paid, () -> scene.recorder.executeIdempotentStart(scene.transport, paid)));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, scene::execute); } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertEquals(0, scene.rig.base.budgetInserts.get());
            assertTrue(scene.recorder.usesPreparations(scene.preparations)); assertTrue(scene.recorder.usesLedger(scene.rig.base.ledger)); assertTrue(scene.recorder.usesCapabilities(scene.rig.base.capabilities));
        }
    }

    @Test void write_values_are_closed_and_readonly_result_still_refuses_business_receipts() {
        var success = success();
        var valid = new WriteAttemptResultVO(RUN, UUID.randomUUID(), "SUCCEEDED", 3, 1, NOW, NOW, success, true, AccountingState.SETTLED);
        assertNotNull(new IdempotentStartResultVO(UUID.randomUUID().toString(), "PREPARED", 1, valid));
        for (String state : List.of("AWAITING_APPROVAL", "SUCCEEDED", "other")) assertThrows(IllegalArgumentException.class,
                () -> new IdempotentStartResultVO(UUID.randomUUID().toString(), state, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new IdempotentStartResultVO("not-a-uuid", "PREPARED", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new IdempotentStartResultVO(UUID.randomUUID().toString(), "PREPARED", 0, null));
        for (var wrong : List.of(new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), "business-receipt", null, BigDecimal.ZERO),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("other", INSTANCE), RECEIPT, null, BigDecimal.ZERO),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", 17L), RECEIPT, null, BigDecimal.ZERO),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE, "extra", "value"), RECEIPT, null, BigDecimal.ZERO),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, null),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, new BigDecimal("0.01")))) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new WriteAttemptResultVO(RUN, UUID.randomUUID(), "SUCCEEDED", 3, 1, NOW, NOW, wrong, true, AccountingState.SETTLED));
            assertFalse(failure.getMessage().contains("Read-only"));
        }
        assertThrows(IllegalArgumentException.class, () -> new WriteAttemptResultVO(RUN, UUID.randomUUID(), "FAILED_KNOWN", 2, 0, null, NOW, failed("DISPATCH_NOT_SENT"), false, AccountingState.PENDING));
        assertThrows(IllegalArgumentException.class, () -> new WriteAttemptResultVO(RUN, UUID.randomUUID(), "PREPARED", 1, 0, null, null, null, true, AccountingState.PENDING));
        assertThrows(IllegalArgumentException.class, () -> new WriteAttemptResultVO(RUN, UUID.randomUUID(), "SUCCEEDED", 3, 1, NOW, NOW.minusSeconds(1), success, true, AccountingState.SETTLED));
        assertThrows(IllegalArgumentException.class, () -> new WriteAttemptResultVO(RUN, UUID.randomUUID(), "UNKNOWN", 3, 1, NOW, NOW, success, true, AccountingState.PENDING));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder.RecordResult(RUN, UUID.randomUUID(), "SUCCEEDED", 3, 1, NOW, NOW, success, true, AccountingState.SETTLED));
    }

    @Test void known_operation_failures_and_uncertain_failures_have_distinct_fresh_outcomes() throws Exception {
        for (var kind : FixedBpmnStartToolTransport.FailureKind.values()) try (var scene = new Scene()) {
            scene.transport.operationFailure.set(kind); var own = scene.execute().ownAttempt(); assertNotNull(own); assertNotNull(own.outcome());
            var status = kind == OPERATION_DENIED ? ToolExecutionOutcome.Status.REJECTED : Set.of(OPERATION_NOT_SENT, CANCELLED, DEADLINE_EXCEEDED).contains(kind)
                    ? ToolExecutionOutcome.Status.FAILED_KNOWN : ToolExecutionOutcome.Status.UNKNOWN;
            String code = kind == OPERATION_DENIED ? "DOWNSTREAM_PERMISSION_DENIED" : status == ToolExecutionOutcome.Status.FAILED_KNOWN ? "DISPATCH_NOT_SENT"
                    : kind == OPERATION_INVALID_RESPONSE ? "OUTCOME_INVALID_RESPONSE" : "OUTCOME_UNKNOWN";
            assertEquals(status, own.outcome().status()); assertEquals(code, own.outcome().errorCode()); assertEquals(Map.of(), own.outcome().output()); assertNull(own.outcome().businessReceiptId());
            assertEquals(BigDecimal.ZERO, own.outcome().actualCost()); assertTrue(own.operationInvokedThisCall()); assertEquals(1, scene.transport.starts.get());
        }
        for (boolean malformed : List.of(false, true)) try (var scene = new Scene()) {
            if (malformed) scene.transport.observation.set(null); else scene.transport.privateRuntime.set(true);
            var own = scene.execute().ownAttempt(); assertEquals("UNKNOWN", own.state()); assertEquals(malformed ? "OUTCOME_INVALID_RESPONSE" : "OUTCOME_UNKNOWN", own.outcome().errorCode());
        }
    }

    @Test void dispatch_checks_bind_original_requester_action_and_audit_before_any_start() throws Exception {
        for (String variant : List.of("deny", "unavailable", "audit", "echo")) try (var scene = new Scene()) {
            scene.check.set(value -> {
                if (variant.equals("deny") || variant.equals("unavailable")) return new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.DISPATCH,
                        variant.equals("deny") ? ToolExecutionAuthorization.Decision.DENY : ToolExecutionAuthorization.Decision.UNAVAILABLE, null);
                if (variant.equals("audit")) return new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.ALLOW, null);
                var wrong = new ToolExecutionAuthorization.Request(value.run(), value.lease(), value.tool(), value.bindingSha256(), value.parametersSha256(), null, value.deadlineAt());
                return scene.rig.allowed(wrong, ToolExecutionAuthorization.Boundary.DISPATCH);
            });
            assertCode(variant.equals("deny") ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, scene::execute);
            assertEquals(0, scene.transport.starts.get()); assertEquals(0, scene.accounts.get()); assertEquals("FAILED_KNOWN", scene.rig.row(scene.request).get("state"));
            assertNull(scene.rig.outcome(scene.request).businessReceiptId()); assertEquals(BigDecimal.ZERO, scene.rig.outcome(scene.request).actualCost());
        }
    }

    @Test void terminal_and_unknown_metadata_never_replays_old_body_calls_transport_or_resettles() throws Exception {
        for (String state : List.of("DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN")) try (var scene = new Scene()) {
            var prepared = scene.rig.prepare(scene.request);
            var begun = scene.rig.begin(scene.request, prepared.invocation(), "1".repeat(64), failed("ACTION_NOT_DISPATCHABLE"));
            if (!state.equals("DISPATCHING")) {
                var outcome = state.equals("SUCCEEDED") ? success() : new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(state), Map.of(), null, "CONTROLLED", BigDecimal.ZERO);
                scene.rig.complete(scene.request, begun.attempt(), "1".repeat(64), outcome);
            }
            var result = scene.execute(); assertNotNull(result.ownAttempt()); assertNull(result.ownAttempt().outcome()); assertFalse(result.ownAttempt().operationInvokedThisCall());
            assertEquals(state, result.ownAttempt().state()); assertEquals(0, scene.transport.calls.get()); assertEquals(0, scene.accounts.get());
            var other = scene.request(OTHER_RUN, UUID.randomUUID()); var observed = scene.execute(other);
            assertNull(observed.ownAttempt()); assertEquals(result.actionIntentId(), observed.actionIntentId()); assertEquals(0, scene.transport.calls.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void fresh_grant_check_time_and_original_monotonic_window_are_not_reset_by_dispatch_work() throws Exception {
        for (String variant : List.of("grant", "stale", "future", "monotonic", "evidence")) try (var scene = new Scene()) {
            scene.check.set(value -> {
                var original = scene.rig.allowed(value, ToolExecutionAuthorization.Boundary.DISPATCH); var audit = original.audit();
                if (variant.equals("monotonic")) { scene.rig.base.nano.set(10_000_000_001L); return original; }
                if (variant.equals("evidence")) {
                    var old = scene.rig.evidence(scene.request); scene.rig.base.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), old.grantId(), "ENC:v1:key:B:changed", old.expiresAt())); return original;
                }
                var changed = new ToolExecutionAuthorization.AuditVO(1, audit.localCheckId(), audit.boundary(), audit.decision(), audit.subjectType(),
                        variant.equals("grant") ? "9" + "2".repeat(62) : audit.grantId(), audit.authVersion(), audit.catalogVersion(), audit.policyVersion(),
                        variant.equals("stale") ? NOW.minusSeconds(1) : variant.equals("future") ? NOW.plusSeconds(1) : audit.checkedAt(), audit.expiresAt());
                return new ToolExecutionAuthorization.CheckResult(value, original.boundary(), original.decision(), changed);
            });
            if (variant.equals("monotonic")) assertFailure(DEADLINE_EXCEEDED, scene::execute); else assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, scene::execute);
            assertEquals(0, scene.transport.starts.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void callback_executor_tenant_is_checked_before_dispatch_authority_or_one_shot() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.executor.set(new VerifiedExecutor(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "foreign", "https://process-idp.example/21"), NOW.plusSeconds(15)));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, scene::execute); assertEquals(0, scene.dispatchChecks.get());
            assertEquals(0, scene.transport.starts.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void unknown_result_with_verified_settled_zero_is_metadata_settled_without_another_accounting_write() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.operationFailure.set(OPERATION_RESULT_UNKNOWN); var first = scene.execute(); assertEquals("UNKNOWN", first.ownAttempt().state());
            assertEquals(AccountingState.SETTLED, first.ownAttempt().accountingState()); int accounts = scene.accounts.get();
            var second = scene.execute(); assertNull(second.ownAttempt().outcome()); assertEquals(AccountingState.SETTLED, second.ownAttempt().accountingState());
            assertEquals(accounts, scene.accounts.get()); assertEquals(1, scene.transport.starts.get());
        }
    }

    @Test void global_advanced_state_closes_own_prepared_attempt_as_metadata_without_transport_or_account() throws Exception {
        try (var scene = new Scene()) {
            scene.rig.prepare(scene.request); var other = scene.request(OTHER_RUN, UUID.randomUUID()); var prepared = scene.rig.prepare(other);
            scene.rig.begin(other, prepared.invocation(), "2".repeat(64), failed("ACTION_NOT_DISPATCHABLE")); var action = scene.rig.base.intentRow.get(); var otherRow = new HashMap<>(scene.rig.row(other));
            var value = scene.execute(); assertEquals("DISPATCHING", value.actionStateAtPreparation()); assertEquals("FAILED_KNOWN", value.ownAttempt().state());
            assertNull(value.ownAttempt().outcome()); assertEquals(AccountingState.PENDING, value.ownAttempt().accountingState()); assertEquals(0, scene.accounts.get()); assertEquals(0, scene.transport.calls.get());
            assertSame(action, scene.rig.base.intentRow.get()); assertEquals(otherRow, scene.rig.row(other));
        }
    }

    @Test void intervening_generic_nonzero_closure_before_begin_stays_metadata_and_preserves_the_fee() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.beforeCallback.set(() -> scene.rig.store.closePreparedWriteFailure(scene.request, null,
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "CANCELLED", new BigDecimal("0.03"))));
            var result = scene.execute(); assertEquals("FAILED_KNOWN", result.ownAttempt().state()); assertNull(result.ownAttempt().outcome());
            assertEquals(AccountingState.PENDING, result.ownAttempt().accountingState()); assertEquals(new BigDecimal("0.03"), scene.rig.outcome(scene.request).actualCost());
            assertEquals(0, scene.accounts.get()); assertEquals(0, scene.transport.starts.get());
        }
    }

    @Test void precallback_failure_cleanup_persists_only_metadata_and_never_guesses_a_fee_settlement() throws Exception {
        for (var kind : List.of(CREDENTIAL_UNAVAILABLE, RELEASE_UNAVAILABLE, RELEASE_CHANGED, CANCELLED, DEADLINE_EXCEEDED)) try (var scene = new Scene()) {
            scene.transport.beforeFailure.set(kind); var failure = assertFailure(kind, scene::execute);
            assertEquals("FAILED_KNOWN", scene.rig.row(scene.request).get("state")); assertEquals(0L, scene.rig.row(scene.request).get("dispatch_epoch"));
            assertEquals(kind.name(), scene.rig.outcome(scene.request).errorCode()); assertEquals(BigDecimal.ZERO, scene.rig.outcome(scene.request).actualCost());
            assertEquals(0, scene.accounts.get()); assertEquals(0, scene.transport.starts.get()); assertTrue(failure.getSuppressed().length > 0);
        }
        try (var scene = new Scene()) {
            scene.transport.beforeCallback.set(() -> scene.rig.store.closePreparedWriteFailure(scene.request, null,
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "CANCELLED", new BigDecimal("0.03"))));
            scene.transport.beforeFailure.set(RELEASE_UNAVAILABLE); assertFailure(RELEASE_UNAVAILABLE, scene::execute);
            assertEquals(new BigDecimal("0.03"), scene.rig.outcome(scene.request).actualCost()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void a_committed_begin_expiring_before_one_shot_records_authenticated_not_sent_without_invoking_it() throws Exception {
        try (var scene = new Scene()) {
            scene.afterBegin.set(() -> { scene.rig.base.wall.set(NOW.plusSeconds(11)); scene.rig.base.databaseNow.set(NOW.plusSeconds(11)); scene.rig.base.nano.set(11_000_000_000L); });
            var result = scene.execute(); var own = result.ownAttempt();
            assertEquals("FAILED_KNOWN", own.state()); assertNotNull(own.outcome()); assertEquals("DISPATCH_NOT_SENT", own.outcome().errorCode());
            assertEquals(1, own.dispatchEpoch()); assertFalse(own.operationInvokedThisCall()); assertEquals(0, scene.transport.starts.get()); assertEquals(1, scene.accounts.get());
        }
    }

    @Test void accounting_failure_cannot_roll_back_or_downgrade_the_committed_business_fact() throws Exception {
        try (var scene = new Scene()) {
            scene.failAccount.set(true); var result = scene.execute(); assertEquals("SUCCEEDED", result.ownAttempt().state()); assertEquals(AccountingState.PENDING, result.ownAttempt().accountingState());
            assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals(RECEIPT, scene.rig.outcome(scene.request).businessReceiptId()); assertEquals(1, scene.transport.starts.get());
        }
    }

    @Test void failed_completion_remains_dispatching_and_replay_never_sends_the_operation_again() throws Exception {
        try (var scene = new Scene()) {
            scene.failComplete.set(true); assertFailure(OPERATION_RESULT_UNKNOWN, scene::execute);
            assertEquals("DISPATCHING", scene.rig.row(scene.request).get("state")); assertEquals("DISPATCHING", scene.rig.base.intentRow.get().state()); assertEquals(0, scene.accounts.get());
            scene.failComplete.set(false); var replay = scene.execute(); assertNull(replay.ownAttempt().outcome()); assertEquals("DISPATCHING", replay.ownAttempt().state());
            assertEquals(1, scene.transport.starts.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void valid_late_ack_is_recorded_before_outer_checks_suppress_the_returned_private_value() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.duringStart.set(() -> {
                scene.rig.base.wall.set(NOW.plusSeconds(400)); scene.rig.base.databaseNow.set(NOW.plusSeconds(400)); scene.rig.base.nano.set(400_000_000_000L);
                scene.rig.base.sourceActive.set(false); scene.rig.base.databaseLease.set(NOW.minusSeconds(1)); scene.rig.budgetUnknown(scene.request, null);
                var old = scene.rig.evidence(scene.request); scene.rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4),
                        "9" + "2".repeat(62), "ENC:v1:key:B:new", old.expiresAt())); Thread.currentThread().interrupt();
            });
            try { assertFailure(CANCELLED, scene::execute); assertTrue(Thread.currentThread().isInterrupted()); } finally { Thread.interrupted(); }
            assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals("SUCCEEDED", scene.rig.row(scene.request).get("state")); assertEquals(RECEIPT, scene.rig.outcome(scene.request).businessReceiptId());
            assertNotNull(scene.transport.callbackResult.get()); assertEquals(1, scene.transport.starts.get());
        }
        try (var scene = new Scene()) {
            scene.transport.afterFailure.set(CREDENTIAL_UNAVAILABLE); assertFailure(CREDENTIAL_UNAVAILABLE, scene::execute);
            assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals(RECEIPT, scene.rig.outcome(scene.request).businessReceiptId()); assertEquals(1, scene.transport.starts.get());
        }
    }

    @Test void post_preparation_expiry_cleans_only_own_epoch_zero_without_a_fake_completion_or_account() throws Exception {
        try (var scene = new Scene()) {
            doAnswer(call -> {
                var value = call.callRealMethod(); scene.rig.base.wall.set(NOW.plusSeconds(21)); scene.rig.base.databaseNow.set(NOW.plusSeconds(21)); scene.rig.base.nano.set(21_000_000_000L); return value;
            }).when(scene.preparations).prepareOrLoadIdempotentWrite(any());
            assertFailure(DEADLINE_EXCEEDED, scene::execute); assertEquals("FAILED_KNOWN", scene.rig.row(scene.request).get("state"));
            assertEquals(0L, scene.rig.row(scene.request).get("dispatch_epoch")); assertEquals(0, scene.accounts.get()); assertEquals(0, scene.transport.starts.get());
        }
    }

    @Test void committed_observer_is_status_only_and_null_is_rejected_before_any_work() throws Exception {
        assertTrue(ToolCommittedFactObserver.class.isAnnotationPresent(FunctionalInterface.class));
        assertEquals(0, ToolCommittedFactObserver.class.getDeclaredFields().length);
        var methods = ToolCommittedFactObserver.class.getDeclaredMethods(); assertEquals(1, methods.length);
        assertEquals("onCommitted", methods[0].getName()); assertEquals(void.class, methods[0].getReturnType());
        assertArrayEquals(new Class<?>[]{ToolExecutionOutcome.Status.class}, methods[0].getParameterTypes());
        try (var scene = new Scene()) {
            clearInvocations(scene.preparations, scene.rig.base.capabilities, scene.rig.base.authority, scene.rig.base.source);
            assertThrows(IllegalArgumentException.class, () -> scene.executeObserved(null));
            verifyNoInteractions(scene.preparations, scene.rig.base.capabilities, scene.rig.base.authority, scene.rig.base.source);
            assertEquals(0, scene.transport.calls.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void fresh_start_committed_statuses_are_observed_once_after_commit_and_before_accounting() throws Exception {
        for (var status : List.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN, ToolExecutionOutcome.Status.UNKNOWN)) try (var scene = new Scene()) {
            if (status == ToolExecutionOutcome.Status.REJECTED) scene.transport.operationFailure.set(OPERATION_DENIED);
            if (status == ToolExecutionOutcome.Status.FAILED_KNOWN) scene.transport.operationFailure.set(OPERATION_NOT_SENT);
            if (status == ToolExecutionOutcome.Status.UNKNOWN) scene.transport.operationFailure.set(OPERATION_RESULT_UNKNOWN);
            var observed = new ArrayList<ToolExecutionOutcome.Status>();
            var result = scene.executeObserved(actual -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(scene.trace.contains("complete-ack"));
                assertEquals(status.name(), scene.rig.base.row.get().get("state")); assertEquals(0, scene.accounts.get());
                observed.add(actual); scene.trace.add("observer");
            });
            assertEquals(List.of(status), observed); assertEquals(status, result.ownAttempt().outcome().status());
            assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("observer")); assertTrue(scene.trace.indexOf("observer") < scene.trace.indexOf("accounting"));
            assertEquals(1, scene.transport.starts.get()); assertEquals(1, scene.accounts.get());
        }
        try (var scene = new Scene()) {
            scene.afterBegin.set(() -> { scene.rig.base.wall.set(NOW.plusSeconds(11)); scene.rig.base.databaseNow.set(NOW.plusSeconds(11)); scene.rig.base.nano.set(11_000_000_000L); });
            var observed = new ArrayList<ToolExecutionOutcome.Status>(); var result = scene.executeObserved(observed::add);
            assertEquals(List.of(ToolExecutionOutcome.Status.FAILED_KNOWN), observed); assertFalse(result.ownAttempt().operationInvokedThisCall()); assertEquals(0, scene.transport.starts.get());
        }
    }

    @Test void metadata_early_loser_and_failed_completion_never_notify_the_observer() throws Exception {
        for (String state : List.of("DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN")) try (var scene = new Scene()) {
            var prepared = scene.rig.prepare(scene.request); var begun = scene.rig.begin(scene.request, prepared.invocation(), "1".repeat(64), failed("ACTION_NOT_DISPATCHABLE"));
            if (!state.equals("DISPATCHING")) scene.rig.complete(scene.request, begun.attempt(), "1".repeat(64), state.equals("SUCCEEDED") ? success()
                    : new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(state), Map.of(), null, "CONTROLLED", BigDecimal.ZERO));
            var calls = new AtomicInteger(); assertNull(scene.executeObserved(ignored -> calls.incrementAndGet()).ownAttempt().outcome());
            var other = scene.request(OTHER_RUN, UUID.randomUUID());
            assertNull(scene.rig.selected(other, () -> scene.recorder.executeIdempotentStart(scene.transport, other, ignored -> calls.incrementAndGet())).ownAttempt());
            assertEquals(0, calls.get()); assertEquals(0, scene.transport.starts.get()); assertEquals(0, scene.accounts.get());
        }
        for (String variant : List.of("early", "loser", "completion", "precallback")) try (var scene = new Scene()) {
            if (variant.equals("early")) {
                scene.rig.prepare(scene.request); var other = scene.request(OTHER_RUN, UUID.randomUUID()); var prepared = scene.rig.prepare(other);
                scene.rig.begin(other, prepared.invocation(), "2".repeat(64), failed("ACTION_NOT_DISPATCHABLE"));
            }
            if (variant.equals("loser")) scene.transport.beforeCallback.set(() -> scene.rig.store.closePreparedWriteFailure(scene.request, null, failed("CANCELLED")));
            if (variant.equals("completion")) scene.failComplete.set(true);
            if (variant.equals("precallback")) scene.transport.beforeFailure.set(RELEASE_UNAVAILABLE);
            var calls = new AtomicInteger();
            if (variant.equals("completion") || variant.equals("precallback")) assertFailure(variant.equals("completion") ? OPERATION_RESULT_UNKNOWN : RELEASE_UNAVAILABLE,
                    () -> scene.executeObserved(ignored -> calls.incrementAndGet()));
            else assertNull(scene.executeObserved(ignored -> calls.incrementAndGet()).ownAttempt().outcome());
            assertEquals(0, calls.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void observer_failure_after_ack_wins_over_transport_unknown_without_cleanup_accounting_or_fact_downgrade() throws Exception {
        for (boolean ledgerFailure : List.of(false, true)) try (var scene = new Scene()) {
            var calls = new AtomicInteger(); clearInvocations(scene.preparations);
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.executeObserved(status -> {
                calls.incrementAndGet(); assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, status); assertTrue(scene.trace.contains("complete-ack"));
                if (ledgerFailure) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "private observer " + INSTANCE);
                throw new IllegalStateException("private observer " + INSTANCE, new IllegalArgumentException(RECEIPT));
            }));
            assertEquals(1, scene.transport.wrappedCallbackLedger.get(), "The controlled real-port ACK boundary must have converted callback Ledger to UNKNOWN");
            assertEquals(1, calls.get()); assertEquals(0, scene.accounts.get()); assertEquals(0, failure.getSuppressed().length);
            assertFalse(failure.getMessage().contains("private")); assertFalse(failure.getMessage().contains(RECEIPT));
            assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals("SUCCEEDED", scene.rig.row(scene.request).get("state"));
            assertEquals(RECEIPT, scene.rig.outcome(scene.request).businessReceiptId()); assertNull(scene.transport.callbackResult.get());
            verify(scene.preparations, never()).closePreparedWriteFailure(any(), any(), any());
            var replay = scene.executeObserved(ignored -> fail("Metadata must not notify a new observer"));
            assertNull(replay.ownAttempt().outcome()); assertEquals(1, scene.transport.starts.get()); assertEquals(0, scene.accounts.get());
        }
    }

    @Test void observed_facts_remain_observed_when_accounting_or_outer_postchecks_fail() throws Exception {
        for (boolean outerFailure : List.of(false, true)) try (var scene = new Scene()) {
            scene.failAccount.set(!outerFailure); if (outerFailure) scene.transport.afterFailure.set(CREDENTIAL_UNAVAILABLE);
            var calls = new AtomicInteger(); ToolCommittedFactObserver observer = status -> { assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, status); calls.incrementAndGet(); };
            if (outerFailure) assertFailure(CREDENTIAL_UNAVAILABLE, () -> scene.executeObserved(observer));
            else assertEquals(AccountingState.PENDING, scene.executeObserved(observer).ownAttempt().accountingState());
            assertEquals(1, calls.get()); assertEquals(1, scene.accounts.get()); assertEquals("APPLIED", scene.rig.base.intentRow.get().state()); assertEquals(1, scene.transport.starts.get());
        }
    }

    @Test void observer_interrupt_and_runtime_failure_preserve_the_flag_and_the_safe_dependency_failure() throws Exception {
        try (var scene = new Scene()) {
            try {
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.executeObserved(status -> {
                    Thread.currentThread().interrupt(); throw new IllegalStateException("private observer interrupted");
                }));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
            assertEquals(0, scene.accounts.get()); assertEquals("APPLIED", scene.rig.base.intentRow.get().state());
            verify(scene.preparations, never()).closePreparedWriteFailure(any(), any(), any());
        }
    }

    private static ToolDescriptor tool(ToolPolicy.Risk risk, String resource, boolean output) {
        return new ToolDescriptor(risk == ToolPolicy.Risk.READ_ONLY ? "process.readStart" : risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "process.approvedStart" : "process.fixedStart", 1, "start_process", "Fixed START recorder",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of(output ? "instanceId" : "other", ToolSchema.ValueType.STRING), Set.of(output ? "instanceId" : "other")),
                new ToolPolicy(risk, "process.invoice", resource, ToolInvocationStoreTest.HASH, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval-1" : null));
    }
    private static ToolExecutionOutcome success() { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, BigDecimal.ZERO); }
    private static ToolExecutionOutcome failed(String code) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, code, BigDecimal.ZERO); }
    private static final class Scene implements AutoCloseable {
        final ToolWriteInvocationStoreTest.Rig rig;
        final ToolInvocationStore preparations;
        final PreparationRequestVO request;
        final ToolInvocationRecorder recorder;
        final ProbeTransport transport;
        final List<String> trace = new ArrayList<>();
        final AtomicInteger accounts = new AtomicInteger(), dispatchChecks = new AtomicInteger();
        final AtomicBoolean failAccount = new AtomicBoolean(), failComplete = new AtomicBoolean();
        final AtomicReference<Function<ToolExecutionAuthorization.Request, ToolExecutionAuthorization.CheckResult>> check = new AtomicReference<>();
        final AtomicReference<Runnable> afterBegin = new AtomicReference<>(() -> { });
        final AtomicReference<ToolInvocationStore.DispatchAttemptVO> attempt = new AtomicReference<>();
        final AtomicReference<String> nonce = new AtomicReference<>();

        Scene() throws Exception { this(TOOL, true); }
        Scene(ToolDescriptor descriptor, boolean ready) throws Exception {
            rig = new ToolWriteInvocationStoreTest.Rig(descriptor);
            request = request(RUN, UUID.randomUUID()); preparations = spy(rig.store);
            check.set(value -> rig.allowed(value, ToolExecutionAuthorization.Boundary.DISPATCH));
            doAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                ToolExecutionAuthorization.Request value = call.getArgument(0);
                assertEquals(request.binding().run().actor(), value.run().actor()); assertEquals(request.action().intentId(), value.actionIntentId());
                assertEquals(request.parametersSha256(), value.parametersSha256()); assertEquals(NOW.plusSeconds(15), value.deadlineAt());
                dispatchChecks.incrementAndGet(); return check.get().apply(value);
            }).when(rig.base.authority).check(any(), eq(ToolExecutionAuthorization.Boundary.DISPATCH));
            doAnswer(call -> { var value = (ToolInvocationStore.WritePreparationResultVO) call.callRealMethod(); trace.add("prepare-ack"); return value; })
                    .when(preparations).prepareOrLoadIdempotentWrite(any());
            doAnswer(call -> {
                var value = (ToolInvocationStore.WriteDispatchBeginResultVO) call.callRealMethod();
                if (value.started()) { trace.add("begin-ack"); afterBegin.get().run(); } return value;
            }).when(preparations).beginIdempotentWriteDispatch(any(), any(), any(), any(), any(), anyString(), any(), any());
            doAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); attempt.set(call.getArgument(1)); nonce.set(call.getArgument(2));
                if (failComplete.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private completion failed");
                var value = call.callRealMethod(); trace.add("complete-ack"); return value;
            }).when(preparations).completeIdempotentWrite(any(), any(), anyString(), any());
            doAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(trace.contains("complete-ack"), "account only after this call's completion ACK");
                accounts.incrementAndGet(); trace.add("accounting");
                if (failAccount.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private accounting failed");
                return call.callRealMethod();
            }).when(rig.base.budgets).settleBudget(any(), any(), anyString(), any());
            recorder = new ToolInvocationRecorder(rig.base.source, rig.base.ledger, preparations, rig.base.capabilities, rig.base.authority, rig.base.clock(), rig.base.nano::get);
            if (ready) recorder.verifyStorage(); transport = new ProbeTransport(this);
        }
        PreparationRequestVO request(String id, UUID invocationId) {
            var original = rig.request(id, invocationId);
            return new PreparationRequestVO(original.binding(), original.lease(), original.descriptor(), original.arguments(), original.action(), invocationId, BigDecimal.ZERO, original.intentExpiry());
        }
        IdempotentStartResultVO execute() { return execute(request); }
        IdempotentStartResultVO execute(PreparationRequestVO request) { return rig.selected(request, () -> recorder.executeIdempotentStart(transport, request)); }
        IdempotentStartResultVO executeObserved(ToolCommittedFactObserver observer) { return rig.selected(request, () -> recorder.executeIdempotentStart(transport, request, observer)); }
        @Override public void close() { rig.close(); }
    }

    /** Controlled synchronous port stages; AS transport tests independently cover the actual SDK and noncooperative waits. */
    private static final class ProbeTransport implements FixedBpmnStartToolTransport {
        final Scene scene;
        final AtomicInteger calls = new AtomicInteger(), starts = new AtomicInteger(), wrappedCallbackLedger = new AtomicInteger();
        final AtomicReference<Request> request = new AtomicReference<>();
        final AtomicReference<Object> callbackResult = new AtomicReference<>();
        final AtomicReference<FailureKind> beforeFailure = new AtomicReference<>(), operationFailure = new AtomicReference<>(), afterFailure = new AtomicReference<>();
        final AtomicReference<Runnable> beforeCallback = new AtomicReference<>(() -> { }), duringStart = new AtomicReference<>(() -> { });
        final AtomicReference<StartObservation> observation = new AtomicReference<>(new StartObservation(RECEIPT, INSTANCE));
        final AtomicReference<VerifiedExecutor> executor = new AtomicReference<>(EXECUTOR);
        final AtomicBoolean privateRuntime = new AtomicBoolean();

        ProbeTransport(Scene scene) { this.scene = scene; }
        @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); calls.incrementAndGet(); this.request.set(request);
            beforeCallback.get().run();
            if (beforeFailure.get() != null) throw new Failure(beforeFailure.get());
            if (!request.deadlineAt().isAfter(scene.rig.base.wall.get()) || !EXECUTOR.expiresAt().isAfter(scene.rig.base.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            var consumed = new AtomicBoolean(); var acknowledged = new AtomicBoolean(); T result;
            try {
                result = callback.apply(executor.get(), () -> {
                    if (!consumed.compareAndSet(false, true)) throw new Failure(FailureKind.CANCELLED);
                    starts.incrementAndGet(); scene.trace.add("operation"); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertTrue(scene.trace.contains("begin-ack")); assertTrue(scene.rig.base.trace.contains("commit")); assertEquals("DISPATCHING", scene.rig.base.row.get().get("state"));
                    duringStart.get().run();
                    if (operationFailure.get() != null) throw new Failure(operationFailure.get());
                    if (privateRuntime.get()) throw new IllegalStateException("private operation " + INSTANCE);
                    var value = observation.get(); if (value != null) acknowledged.set(true); return value;
                });
            } catch (Failure failure) { throw new Failure(failure.kind()); }
            catch (AgentLedgerException failure) {
                if (acknowledged.get()) { wrappedCallbackLedger.incrementAndGet(); throw new Failure(FailureKind.OPERATION_RESULT_UNKNOWN); }
                throw new AgentLedgerException(failure.code(), "Controlled START callback unavailable");
            }
            catch (RuntimeException failure) { throw new Failure(consumed.get() ? FailureKind.OPERATION_RESULT_UNKNOWN : FailureKind.OPERATION_NOT_SENT); }
            callbackResult.set(result);
            if (afterFailure.get() != null) throw new Failure(afterFailure.get());
            if (Thread.currentThread().isInterrupted()) throw new Failure(FailureKind.CANCELLED);
            if (!request.deadlineAt().isAfter(scene.rig.base.wall.get()) || !EXECUTOR.expiresAt().isAfter(scene.rig.base.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            return result;
        }
    }

    private static FixedBpmnStartToolTransport.Failure assertFailure(FixedBpmnStartToolTransport.FailureKind kind, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(FixedBpmnStartToolTransport.Failure.class, action); assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause()); return failure;
    }
    private static AgentLedgerException assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(AgentLedgerException.class, action); assertEquals(code, failure.code()); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(INSTANCE)); return failure;
    }
}
