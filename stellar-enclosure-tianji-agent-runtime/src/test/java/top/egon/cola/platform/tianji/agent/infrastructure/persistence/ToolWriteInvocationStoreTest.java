package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Store/Invocation SQL/Spring/budget flow with controlled JDBC and a mocked Action participant.
 * Action CAS SQL is covered by ToolActionWriteSqlTest; physical FK/concurrency acceptance remains opt-in PostgreSQL. */
class ToolWriteInvocationStoreTest {
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RUN = ToolInvocationStoreTest.RUN, OTHER_RUN = "10000000-0000-0000-0000-000000000002", HASH = ToolInvocationStoreTest.HASH;
    private static final TrustedAgentActor ACTOR = ToolInvocationStoreTest.ACTOR;
    private static final String NONCE = "1".repeat(64);
    private static final ToolDescriptor TOOL = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE);
    private static final VerifiedExecutor EXECUTOR = new VerifiedExecutor(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "write-executor", "https://executor.example/20"), NOW.plusSeconds(50));

    @Test void package_write_projection_and_risk_guards_do_not_open_a_second_public_or_readonly_path() throws Exception {
        assertFalse(Modifier.isPublic(ToolInvocationStore.class.getDeclaredMethod("prepareOrLoadIdempotentWrite", ToolInvocationStore.PreparationRequestVO.class).getModifiers()));
        assertEquals(3, ToolInvocationStore.WritePreparationResultVO.class.getRecordComponents().length);
        assertEquals(3, ToolInvocationStore.WriteDispatchBeginResultVO.class.getRecordComponents().length);
        for (var risk : List.of(ToolPolicy.Risk.READ_ONLY, ToolPolicy.Risk.APPROVAL_REQUIRED)) try (var rig = new Rig(descriptor(risk))) {
            var request = rig.request(RUN, UUID.randomUUID()); clearInvocations(rig.base.capabilities, rig.base.authority, rig.base.source);
            assertThrows(IllegalArgumentException.class, () -> rig.prepare(request)); verifyNoInteractions(rig.base.capabilities, rig.base.authority, rig.base.source);
        }
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var unready = rig.base.store(false, rig.base.authority);
            assertThrows(AgentLedgerException.class, () -> unready.prepareOrLoadIdempotentWrite(request));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> rig.prepare(request)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertEquals(0, rig.base.budgetInserts.get());
        }
    }

    @Test void fresh_attempts_across_runs_share_one_logical_intent_but_own_their_reservations() throws Exception {
        try (var rig = new Rig()) {
            var firstRequest = rig.request(RUN, UUID.randomUUID()); var secondRequest = rig.request(RUN, UUID.randomUUID()); var otherRequest = rig.request(OTHER_RUN, UUID.randomUUID());
            var first = rig.prepare(firstRequest); assertNotNull(first.invocation());
            var replay = rig.prepare(firstRequest); assertEquals(first, replay);
            var second = rig.prepare(secondRequest); var other = rig.prepare(otherRequest);
            assertNotNull(second.invocation()); assertNotNull(other.invocation()); assertEquals(3, rig.base.invocationInserts.get()); assertEquals(3, rig.base.budgetInserts.get());
            assertEquals(first.action(), second.action()); assertEquals(first.action(), other.action()); assertEquals(RUN, other.action().originRunId());
            assertEquals(first.action().expiresAt(), other.action().expiresAt());
            assertEquals(OTHER_RUN, other.invocation().runId()); assertEquals(firstRequest.action().intentId(), otherRequest.action().intentId());
            assertEquals("ToolInvocationStore.WritePreparationResultVO[redacted]", first.toString());
            var legacy = rig.selected(rig.request(RUN, UUID.randomUUID()), () -> rig.store.prepare(rig.active));
            assertNull(legacy.invocation()); assertEquals(first.action(), legacy.action()); assertEquals(3, rig.base.budgetInserts.get());
        }
    }

    @Test void an_unclaimed_prepared_action_past_its_first_expiry_returns_only_unchanged_metadata() throws Exception {
        try (var rig = new Rig()) {
            rig.base.intentNew.set(false);
            var expired = new ToolActionIntentSql.Row(rig.base.action, TOOL, RUN, HASH, "PREPARED", 4, NOW.minusSeconds(60), NOW.minusSeconds(2), NOW.minusSeconds(1),
                    null, null, "NONE", null, null, null);
            rig.base.intentRow.set(expired);
            var result = rig.prepare(rig.request(OTHER_RUN, UUID.randomUUID()));
            assertNull(result.invocation()); assertNull(result.budget()); assertEquals("PREPARED", result.action().state());
            assertEquals(expired.expiresAt(), result.action().expiresAt()); assertSame(expired, rig.base.intentRow.get());
            assertEquals(0, rig.base.invocationInserts.get()); assertEquals(0, rig.base.budgetInserts.get());
        }
    }

    @Test void write_result_values_require_complete_matching_metadata_and_first_winner_identity() {
        UUID id = UUID.randomUUID(); String reservation = "tool:" + id;
        var action = new ToolActionRequest(ACTOR, TOOL, "invoice-1", "v1", Map.of("amount", 20L));
        var metadata = new ToolActionIntentStore.PreparedActionResult(action.intentId(), action.requestSha256(), TOOL.reference(), ToolPolicy.Risk.IDEMPOTENT_WRITE,
                "PREPARED", 1, RUN, NOW, NOW.plusSeconds(300));
        var prepared = new ToolInvocationStore.RecorderPreparationVO(RUN, id, reservation, "PREPARED", 1, 0, 3, NOW, NOW.plusSeconds(20), null, null);
        var budget = new AgentBudgetReservation(reservation, 1, AgentBudgetRequest.Kind.TOOL, TOOL.versionKey(), 0, 0, new BigDecimal("0.20"), AgentBudgetReservation.State.RESERVED, null, null, null, false);
        assertNotNull(new ToolInvocationStore.WritePreparationResultVO(prepared, metadata, budget));
        assertNotNull(new ToolInvocationStore.WritePreparationResultVO(null, metadata, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WritePreparationResultVO(null, metadata, budget));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WritePreparationResultVO(prepared, metadata, null));
        for (var wrong : List.of(
                new AgentBudgetReservation("tool:" + UUID.randomUUID(), 1, budget.kind(), budget.toolVersion(), 0, 0, budget.estimatedCost(), budget.state(), null, null, null, false),
                new AgentBudgetReservation(reservation, 1, AgentBudgetRequest.Kind.MODEL, null, 0, 0, budget.estimatedCost(), budget.state(), null, null, null, false),
                new AgentBudgetReservation(reservation, 1, budget.kind(), "other@1", 0, 0, budget.estimatedCost(), budget.state(), null, null, null, false),
                new AgentBudgetReservation(reservation, 1, budget.kind(), budget.toolVersion(), 1, 0, budget.estimatedCost(), budget.state(), null, null, null, false))) {
            assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WritePreparationResultVO(prepared, metadata, wrong));
        }
        var dispatched = new ToolInvocationStore.RecorderPreparationVO(RUN, id, reservation, "DISPATCHING", 2, 1, 3, NOW, NOW.plusSeconds(20), NOW, null);
        var claiming = new ToolActionIntentStore.PreparedActionResult(action.intentId(), action.requestSha256(), TOOL.reference(), ToolPolicy.Risk.IDEMPOTENT_WRITE,
                "DISPATCHING", 2, RUN, NOW, NOW.plusSeconds(300));
        var audit = new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.ALLOW,
                ACTOR.subjectType(), ToolInvocationStoreTest.GRANT, 1L, null, 1L, NOW, NOW.plusSeconds(25));
        var attempt = new ToolInvocationStore.DispatchAttemptVO(RUN, id, 1, 3, ToolInvocationStore.dispatchNonceSha256(NONCE), EXECUTOR.actor(), audit, NOW, NOW.plusSeconds(20));
        assertTrue(new ToolInvocationStore.WriteDispatchBeginResultVO(dispatched, claiming, attempt).started());
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WriteDispatchBeginResultVO(prepared, claiming, attempt));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WriteDispatchBeginResultVO(dispatched, metadata, attempt));
        var other = new ToolInvocationStore.DispatchAttemptVO(OTHER_RUN, UUID.randomUUID(), 1, 3, attempt.dispatchNonceSha256(), EXECUTOR.actor(), audit, NOW, NOW.plusSeconds(20));
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.WriteDispatchBeginResultVO(dispatched, claiming, other));
    }

    @Test void only_one_action_claim_returns_a_nonce_and_the_loser_closes_only_its_own_invocation() throws Exception {
        try (var rig = new Rig()) {
            var a = rig.request(RUN, UUID.randomUUID()); var b = rig.request(OTHER_RUN, UUID.randomUUID()); var pa = rig.prepare(a); var pb = rig.prepare(b);
            rig.base.trace.clear(); var winner = rig.begin(a, pa.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            assertTrue(winner.started()); assertEquals("DISPATCHING", winner.action().state()); assertEquals(1, winner.attempt().dispatchEpoch());
            var action = rig.base.intentRow.get(); var aRow = new HashMap<>(rig.row(a));
            var loser = rig.begin(b, pb.invocation(), "2".repeat(64), failure("ACTION_NOT_DISPATCHABLE", new BigDecimal("0.0750")));
            assertFalse(loser.started()); assertEquals("FAILED_KNOWN", loser.invocation().state()); assertEquals(0, loser.invocation().dispatchEpoch());
            assertSame(action, rig.base.intentRow.get()); assertEquals(aRow, rig.row(a));
            assertEquals("ACTION_NOT_DISPATCHABLE", rig.outcome(b).errorCode()); assertEquals(new BigDecimal("0.0750"), rig.outcome(b).actualCost());
            assertEquals("NONE", rig.row(b).get("side_effect_outcome")); assertNull(rig.row(b).get("dispatch_nonce_sha256"));
            assertTrue(rig.base.trace.indexOf("run-lock") < rig.base.trace.indexOf("cap-in")); assertTrue(rig.base.trace.indexOf("cap-in") < rig.base.trace.indexOf("action-lock"));
            assertTrue(rig.base.trace.indexOf("inv-write-dispatch") < rig.base.trace.indexOf("action-write-begin"));
            verify(rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void generic_completion_preserves_null_nonzero_and_over_estimate_facts_without_settlement() throws Exception {
        for (BigDecimal cost : new BigDecimal[]{null, new BigDecimal("0.1250"), new BigDecimal("2.5000")}) try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            if (cost != null && cost.compareTo(request.estimatedCost()) > 0) rig.budgetUnknown(request, cost);
            rig.base.trace.clear(); var completed = rig.complete(request, started.attempt(), NONCE, success(cost));
            assertEquals("SUCCEEDED", completed.invocation().state()); assertEquals(success(cost), completed.outcome()); assertEquals("APPLIED", rig.base.intentRow.get().state());
            assertEquals("CONFIRMED", rig.base.intentRow.get().sideEffectOutcome()); assertEquals("business-receipt", completed.outcome().businessReceiptId());
            assertTrue(rig.base.trace.indexOf("inv-write-complete") < rig.base.trace.indexOf("action-write-complete"));
            assertEquals(completed, rig.complete(request, started.attempt(), NONCE, success(cost)));
            if (cost != null && cost.compareTo(request.estimatedCost()) > 0) assertEquals(AgentBudgetReservation.State.UNKNOWN, completed.budget().state());
            verify(rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void unknown_is_metadata_only_until_the_original_nonce_refines_it_and_never_admits_again() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OPERATION_RESULT_UNKNOWN", null);
            rig.complete(request, started.attempt(), NONCE, unknown);
            var later = rig.prepare(rig.request(OTHER_RUN, UUID.randomUUID())); assertNull(later.invocation()); assertNull(later.budget()); assertEquals("UNKNOWN", later.action().state());
            var own = rig.prepare(request); assertNotNull(own.invocation()); assertEquals("UNKNOWN", own.invocation().state());
            assertFalse(rig.begin(request, own.invocation(), "2".repeat(64), failure("ACTION_NOT_DISPATCHABLE", BigDecimal.ZERO)).started());
            assertEquals(1, rig.base.budgetInserts.get()); assertEquals(1, rig.base.invocationInserts.get());
            assertEquals("SUCCEEDED", rig.complete(request, started.attempt(), NONCE, success(new BigDecimal("0.4000"))).invocation().state());
            var conflict = new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "DIFFERENT_RESULT", null);
            assertThrows(AgentLedgerException.class, () -> rig.complete(request, started.attempt(), NONCE, conflict));
        }
    }

    @Test void every_advanced_action_returns_metadata_without_new_invocation_budget_or_private_body() throws Exception {
        for (String state : List.of("DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED")) try (var rig = new Rig()) {
            rig.seedAction(state); var before = rig.base.intentRow.get();
            var value = rig.prepare(rig.request(OTHER_RUN, UUID.randomUUID()));
            assertNull(value.invocation()); assertNull(value.budget()); assertEquals(state, value.action().state()); assertSame(before, rig.base.intentRow.get());
            assertEquals(0, rig.base.budgetInserts.get()); assertEquals(0, rig.base.invocationInserts.get());
            assertEquals(9, value.action().getClass().getRecordComponents().length);
        }
    }

    @Test void a_preexisting_orphan_reservation_is_never_retrofitted_into_a_write_invocation() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID());
            rig.base.budgetRows.put(request.reservationId(), new AgentBudgetReservation(request.reservationId(), request.lease().epoch(), AgentBudgetRequest.Kind.TOOL,
                    TOOL.versionKey(), 0, 0, request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, false));
            assertThrows(AgentLedgerException.class, () -> rig.prepare(request)); assertEquals(0, rig.base.invocationInserts.get());
            assertTrue(rig.base.trace.contains("rollback"));
        }
    }

    @Test void action_cas_failure_rolls_back_the_prior_invocation_update_in_both_phases() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request);
            rig.failActionBegin.set(true);
            assertThrows(AgentLedgerException.class, () -> rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null)));
            assertEquals("PREPARED", rig.row(request).get("state")); assertNull(rig.row(request).get("dispatch_nonce_sha256")); assertEquals("PREPARED", rig.base.intentRow.get().state());
            rig.failActionBegin.set(false); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            rig.failActionComplete.set(true);
            assertThrows(AgentLedgerException.class, () -> rig.complete(request, started.attempt(), NONCE, success(BigDecimal.ZERO)));
            assertEquals("DISPATCHING", rig.row(request).get("state")); assertNull(rig.row(request).get("outcome")); assertEquals("DISPATCHING", rig.base.intentRow.get().state());
            assertTrue(rig.base.trace.contains("rollback")); assertEquals(1, rig.base.budgetInserts.get());
        }
    }

    @Test void dispatch_requires_current_audit_with_action_identity_full_evidence_and_actual_database_lease() throws Exception {
        for (String variant : List.of("action", "audit", "evidence", "lease", "not-sent")) try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var window = rig.window(request, prepared.invocation()); var checked = rig.checked(request, window);
            if (variant.equals("action")) {
                var old = checked.request(); var wrong = new ToolExecutionAuthorization.Request(old.run(), old.lease(), old.tool(), old.bindingSha256(), old.parametersSha256(), null, old.deadlineAt());
                checked = new ToolExecutionAuthorization.CheckResult(wrong, checked.boundary(), checked.decision(), checked.audit());
            } else if (variant.equals("audit")) checked = new ToolExecutionAuthorization.CheckResult(checked.request(), checked.boundary(), checked.decision(), null);
            else if (variant.equals("evidence")) { var old = rig.evidence(request); rig.base.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), old.grantId(), "ENC:v1:key:B:changed", old.expiresAt())); }
            else if (variant.equals("lease")) rig.base.databaseLease.set(NOW.minusSeconds(1));
            var supplied = checked;
            org.junit.jupiter.api.function.Executable operation = () -> rig.selected(request, () -> rig.store.beginIdempotentWriteDispatch(request, prepared.invocation(), rig.evidence(request),
                    supplied, EXECUTOR, ToolInvocationStore.dispatchNonceSha256(NONCE), failure(variant.equals("not-sent") ? "CANCELLED" : "ACTION_NOT_DISPATCHABLE", null), window));
            if (variant.equals("not-sent")) assertThrows(IllegalArgumentException.class, operation); else assertThrows(AgentLedgerException.class, operation);
            assertEquals("PREPARED", rig.row(request).get("state")); assertEquals("PREPARED", rig.base.intentRow.get().state());
        }
    }

    @Test void completion_authenticates_original_nonce_generation_executor_audit_and_request() throws Exception {
        for (String variant : List.of("nonce", "epoch", "cap", "executor", "audit", "request")) try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var begin = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            var original = begin.attempt(); var audit = original.authorizationAudit();
            if (variant.equals("audit")) audit = new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), audit.boundary(), audit.decision(), audit.subjectType(), audit.grantId(),
                    audit.authVersion(), audit.catalogVersion(), audit.policyVersion(), audit.checkedAt(), audit.expiresAt());
            var executor = variant.equals("executor") ? TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "other", "https://executor.example/20") : original.executor();
            var attempt = new ToolInvocationStore.DispatchAttemptVO(original.runId(), original.invocationId(), variant.equals("epoch") ? 2 : original.dispatchEpoch(),
                    variant.equals("cap") ? 4 : original.capabilityIssueEpoch(), original.dispatchNonceSha256(), executor, audit, original.dispatchedAt(), original.callDeadlineAt());
            var supplied = variant.equals("request") ? rig.changedRequest(request) : request;
            assertThrows(AgentLedgerException.class, () -> rig.complete(supplied, attempt, variant.equals("nonce") ? "2".repeat(64) : NONCE, success(BigDecimal.ZERO)));
            assertEquals("DISPATCHING", rig.row(request).get("state")); assertEquals("DISPATCHING", rig.base.intentRow.get().state());
        }
    }

    @Test void late_completion_ignores_old_live_conditions_and_clears_then_restores_interruption() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            rig.base.sourceActive.set(false); rig.base.databaseLease.set(NOW.minusSeconds(1)); rig.base.databaseNow.set(NOW.plusSeconds(400)); rig.base.wall.set(NOW.plusSeconds(400)); rig.base.nano.set(400_000_000_000L);
            var old = rig.evidence(request); rig.evidences.put(RUN, new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), "9" + "2".repeat(62), "ENC:v1:key:B:new", old.expiresAt()));
            clearInvocations(rig.base.authority, rig.base.capabilities); Thread.currentThread().interrupt();
            try { assertEquals("SUCCEEDED", rig.complete(request, started.attempt(), NONCE, success(null)).invocation().state()); assertTrue(Thread.currentThread().isInterrupted()); }
            finally { Thread.interrupted(); }
            assertEquals("APPLIED", rig.base.intentRow.get().state()); assertTrue(rig.base.intentRow.get().closedAt().isAfter(rig.base.intentRow.get().expiresAt()));
            verifyNoInteractions(rig.base.authority, rig.base.capabilities); verify(rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void authentic_old_generation_fact_is_saved_without_changing_a_valid_new_current_pointer() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            var other = rig.seedFutureGeneration(request); var currentAction = rig.base.intentRow.get(); var otherRow = new HashMap<>(rig.row(other));
            var completed = rig.complete(request, started.attempt(), NONCE, success(BigDecimal.ZERO));
            assertEquals("SUCCEEDED", completed.invocation().state()); assertSame(currentAction, rig.base.intentRow.get()); assertEquals(otherRow, rig.row(other));
            verify(rig.actionSql, never()).completeWrite(eq(currentAction), any(), any(), any());
        }
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request); var started = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            rig.base.intentRow.set(rig.actionRow("PREPARED", null, null, null, null));
            assertThrows(AgentLedgerException.class, () -> rig.complete(request, started.attempt(), NONCE, success(BigDecimal.ZERO)));
            assertEquals("DISPATCHING", rig.row(request).get("state"));
        }
    }

    @Test void cleanup_preserves_supplied_fee_and_global_action_and_never_overwrites_advanced_facts() throws Exception {
        try (var rig = new Rig()) {
            var a = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(a); var action = rig.base.intentRow.get(); var supplied = failure("CANCELLED", new BigDecimal("0.0300"));
            assertEquals("FAILED_KNOWN", rig.close(a, null, supplied).orElseThrow().state()); assertEquals(supplied, rig.outcome(a)); assertSame(action, rig.base.intentRow.get());
            var b = rig.request(OTHER_RUN, UUID.randomUUID()); var pb = rig.prepare(b); var begun = rig.begin(b, pb.invocation(), "2".repeat(64), failure("ACTION_NOT_DISPATCHABLE", null));
            assertTrue(begun.started()); var currentAction = rig.base.intentRow.get(); var before = new HashMap<>(rig.row(b));
            assertEquals("DISPATCHING", rig.close(b, null, supplied).orElseThrow().state()); assertEquals(before, rig.row(b)); assertSame(currentAction, rig.base.intentRow.get());
            assertThrows(IllegalArgumentException.class, () -> rig.close(b, null, failure("UNREGISTERED_CODE", null)));
            verify(rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void committed_begin_ack_keeps_the_original_attempt_even_if_the_local_window_expires_after_commit() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID()); var prepared = rig.prepare(request);
            rig.base.afterCommit.set(() -> { rig.base.wall.set(NOW.plusSeconds(11)); rig.base.databaseNow.set(NOW.plusSeconds(11)); rig.base.nano.set(11_000_000_000L); });
            var begun = rig.begin(request, prepared.invocation(), NONCE, failure("ACTION_NOT_DISPATCHABLE", null));
            assertTrue(begun.started()); assertNotNull(begun.attempt()); assertEquals("DISPATCHING", rig.row(request).get("state"));
            rig.base.afterCommit.set(() -> { });
            var completed = rig.complete(request, begun.attempt(), NONCE, failure("DISPATCH_NOT_SENT", null));
            assertEquals("FAILED_KNOWN", completed.invocation().state()); assertNull(completed.outcome().actualCost()); assertEquals("NONE", rig.base.intentRow.get().sideEffectOutcome());
        }
    }

    @Test void cleanup_absence_is_empty_but_a_primary_failure_is_never_treated_as_absence() throws Exception {
        try (var rig = new Rig()) {
            var request = rig.request(RUN, UUID.randomUUID());
            assertTrue(rig.close(request, null, failure("CANCELLED", null)).isEmpty()); assertEquals(0, rig.base.invocationInserts.get());
            doReturn(false).when(rig.base.jdbc.get()).queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class));
            assertThrows(AgentLedgerException.class, () -> rig.close(request, null, failure("CANCELLED", null)));
            assertEquals(0, rig.base.budgetInserts.get()); verify(rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    private static ToolDescriptor descriptor(ToolPolicy.Risk risk) {
        return new ToolDescriptor(risk == ToolPolicy.Risk.READ_ONLY ? "process.readTest" : risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "process.approvedTest" : "process.writeTest",
                1, "write_test", "Controlled journal", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
                new ToolPolicy(risk, "process.invoice", "tianji.api.process.instance.start", HASH, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval-1" : null));
    }
    private static ToolExecutionOutcome success(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "business-value"), "business-receipt", null, cost); }
    private static ToolExecutionOutcome failure(String code, BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, code, cost); }
    private static final class Rig implements AutoCloseable {
        final ToolInvocationStoreTest.Fixture base;
        final ToolInvocationStore store;
        final ToolActionIntentSql actionSql;
        final Map<String, Map<String, Object>> rows = new HashMap<>();
        final Map<String, ToolExecutionGrant.EvidenceVO> evidences = new HashMap<>();
        final AtomicBoolean failActionBegin = new AtomicBoolean(), failActionComplete = new AtomicBoolean();
        ToolInvocationStore.PreparationRequestVO active;
        Map<String, Map<String, Object>> beforeRows;
        Map<String, AgentBudgetReservation> beforeBudgets;
        ToolActionIntentSql.Row beforeAction;
        boolean beforeNew;
        long beforeBudgetCount, beforeInvocationCount;
        final AgentExecutionSnapshot snapshot;

        Rig() throws Exception { this(TOOL); }
        Rig(ToolDescriptor tool) throws Exception {
            base = new ToolInvocationStoreTest.Fixture(tool);
            snapshot = base.ledger.snapshot(ACTOR, new GetAgentRunQuery(RUN));
            doAnswer(call -> {
                GetAgentRunQuery query = call.getArgument(1); String id = query == null ? RUN : query.runId(); var run = run(id);
                var view = new AgentRunDTO(id, "assistant", 1, null, AgentRunStatusEnum.RUNNING, null, false, 1, 2, 0, run.deadlineAt(), NOW.minusSeconds(1), null);
                return new AgentLedgerService.ExecutionMaterial(view, snapshot, run);
            }).when(base.ledger).executionMaterial(eq(ACTOR), any());
            doReturn(snapshot).when(base.ledger).snapshot(eq(ACTOR), any());
            doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); base.trace.add("cap-out"); var binding = (ToolExecutionGrant.BindingVO) call.getArgument(0); var value = evidence(binding.run().runId()); base.afterOutsideCap.get().run(); return Optional.of(value); })
                    .when(base.capabilities).current(any(), eq(tool));
            doAnswer(call -> { assertTrue(TransactionSynchronizationManager.hasResource(base.source)); base.trace.add("cap-in"); var binding = (ToolExecutionGrant.BindingVO) call.getArgument(0); return Optional.ofNullable(base.insideEvidence.get() == null ? evidence(binding.run().runId()) : base.insideEvidence.get()); })
                    .when(base.capabilities).currentInTransaction(any(), eq(tool));
            doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); base.trace.add("authorize"); return allowed(call.getArgument(0), call.getArgument(1)); })
                    .when(base.authority).check(any(), any());
            doAnswer(call -> { base.trace.add("budget-run-lock"); assertTrue(TransactionSynchronizationManager.hasResource(base.source)); return true; })
                    .when(base.budgetRepository).lockRunForBudget(eq(ACTOR), anyString());
            doReturn(true).when(base.budgetRepository).validLease(eq(ACTOR), any());
            doAnswer(call -> {
                base.trace.add("budget-insert"); AgentRunLease lease = call.getArgument(1); AgentBudgetRequest request = call.getArgument(2);
                if (base.budgetRows.containsKey(request.reservationId())) return 0;
                base.budgetRows.put(request.reservationId(), new AgentBudgetReservation(request.reservationId(), lease.epoch(), request.kind(), request.toolVersion(),
                        request.inputTokens(), request.outputTokens(), request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, false));
                base.budgetInserts.incrementAndGet(); return 1;
            }).when(base.budgetRepository).insertBudgetReservation(eq(ACTOR), any(), any(), any());
            doReturn(1).when(base.budgetRepository).reserveBudget(eq(ACTOR), any(), any(), any());
            doAnswer(call -> Optional.ofNullable(base.budgetRows.get(call.getArgument(2)))).when(base.budgetRepository).budgetReservation(eq(ACTOR), anyString(), anyString(), anyBoolean());
            doAnswer(call -> { if (!call.getArgument(0, Boolean.class)) capture(); return null; }).when(base.connection).setAutoCommit(anyBoolean());
            doAnswer(call -> { base.trace.add("rollback"); restore(); return null; }).when(base.connection).rollback();
            doAnswer(call -> { base.trace.add("commit"); beforeRows = null; base.afterCommit.get().run(); return null; }).when(base.connection).commit();
            store = base.store(true, base.authority); actionSql = base.intents.constructed().getFirst();
            installAction();
            installJdbc();
        }

        private void installAction() {
            doAnswer(call -> {
                base.trace.add("action"); boolean created = base.intentNew.getAndSet(false); var current = base.intentRow.get();
                if (current == null || created) {
                    AgentRunAuthorization.Request run = call.getArgument(0);
                    current = new ToolActionIntentSql.Row(base.action, base.tool, run.runId(), run.snapshotSha256(), "PREPARED", 1, NOW, NOW, call.getArgument(4),
                            null, null, "NONE", null, null, null); base.intentRow.set(current); created = true;
                }
                return new ToolActionIntentSql.PreparationResultVO(current, created);
            }).when(actionSql).prepareWithStatus(any(), any(), any(), any(), any(), any());
            doAnswer(call -> {
                assertFalse(Thread.currentThread().isInterrupted()); base.trace.add("action-lock");
                var current = base.intentRow.get(); ToolActionRequest action = call.getArgument(0);
                if (current == null || !current.action().requestSha256().equals(action.requestSha256())) throw conflict();
                assertProjection(current); return current;
            }).when(actionSql).lockForWrite(any(), any(), any());
            doAnswer(call -> {
                base.trace.add("action-write-begin"); if (failActionBegin.get()) throw conflict();
                ToolActionIntentSql.Row expected = call.getArgument(0); ToolActionIntentSql.DispatchVO dispatch = call.getArgument(1); Instant now = call.getArgument(2);
                if (base.intentRow.get() != expected || !expected.state().equals("PREPARED")) throw conflict();
                var row = lookup(dispatch.runId(), dispatch.invocationId()); assertNotNull(row); assertEquals("DISPATCHING", row.get("state"));
                base.intentRow.set(new ToolActionIntentSql.Row(expected.action(), expected.descriptor(), expected.originRunId(), expected.originSnapshotSha256(), "DISPATCHING",
                        expected.revision() + 1, expected.createdAt(), now, expected.expiresAt(), null, dispatch, "UNKNOWN", null, null, null)); return null;
            }).when(actionSql).beginWrite(any(), any(), any(), any());
            doAnswer(call -> {
                base.trace.add("action-write-complete"); if (failActionComplete.get()) throw conflict();
                ToolActionIntentSql.Row expected = call.getArgument(0); ToolExecutionOutcome outcome = call.getArgument(1); Instant now = call.getArgument(2);
                if (base.intentRow.get() != expected) throw conflict();
                String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
                base.intentRow.set(new ToolActionIntentSql.Row(expected.action(), expected.descriptor(), expected.originRunId(), expected.originSnapshotSha256(), state,
                        expected.revision() + 1, expected.createdAt(), now, expected.expiresAt(), null, expected.dispatch(), effect(outcome), outcome, outcome.errorCode(), now)); return null;
            }).when(actionSql).completeWrite(any(), any(), any(), any());
        }

        private void installJdbc() {
            doAnswer(call -> {
                String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1); Object[] values = (Object[]) call.getRawArguments()[2];
                if (sql.startsWith("SELECT lease_expires_at")) {
                    base.trace.add("run-lock"); base.beforeRunLock.get().run();
                    return base.sourceActive.get() && values[values.length - 1].equals(1L) && values[values.length - 2].equals("worker")
                            ? List.of(mapper.mapRow(result(Map.of("lease_expires_at", base.databaseLease.get())), 0)) : List.of();
                }
                if (sql.startsWith("SELECT run_id FROM tianji_agent.agent_run")) {
                    base.trace.add("identity-lock"); return List.of(mapper.mapRow(result(Map.of("run_id", values[values.length - 1])), 0));
                }
                if (sql.contains("FROM tianji_agent.agent_tool_invocation i")) {
                    base.trace.add("inv-budget-lock"); var row = lookup((String) values[values.length - 2], (UUID) values[values.length - 1]);
                    return row == null ? List.of() : List.of(mapper.mapRow(result(budgetView(row)), 0));
                }
                throw new AssertionError("Unexpected controlled write query");
            }).when(base.jdbc.get()).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
            doAnswer(call -> {
                base.trace.add("invocation-insert"); if (base.failInsert.get()) throw new DataIntegrityViolationException("private invocation insert");
                Object[] values = (Object[]) call.getRawArguments()[1]; var columns = List.of("tenant_id", "run_id", "invocation_id", "owner_type", "owner_issuer", "owner_id", "capability_id", "capability_binding_sha256", "capability_issue_epoch", "lease_epoch", "reservation_id",
                        "tool_key", "tool_version", "tool_sha256", "binding_sha256", "parameters_sha256", "risk", "intent_id", "state", "authorization_audit", "outcome", "created_at", "updated_at", "dispatch_not_after", "finished_at");
                var row = new HashMap<String, Object>(); for (int index = 0; index < columns.size(); index++) row.put(columns.get(index), values[index]);
                row.put("revision", 1L); row.put("dispatch_epoch", 0L); row.put("side_effect_outcome", "NONE"); base.row.set(row);
                base.invocationInserts.incrementAndGet(); return 1;
            }).when(base.jdbc.get()).update(startsWith("INSERT INTO tianji_agent.agent_tool_invocation"), any(Object[].class));
            doAnswer(call -> {
                if (base.failUpdate.get()) throw new DataIntegrityViolationException("private invocation update");
                String sql = call.getArgument(0); Object[] values = (Object[]) call.getRawArguments()[1]; var row = base.row.get(); if (row == null) return 0;
                if (sql.contains("SET state='DISPATCHING'")) {
                    if (!"PREPARED".equals(row.get("state")) || !Long.valueOf(0).equals(row.get("dispatch_epoch"))) return 0;
                    base.trace.add("inv-write-dispatch"); row.put("state", "DISPATCHING"); row.put("dispatch_epoch", 1L); row.put("dispatch_nonce_sha256", values[0]);
                    row.put("executor_issuer", values[1]); row.put("executor_id", values[2]); row.put("authorization_audit", values[3]); row.put("updated_at", values[4]); row.put("dispatched_at", values[5]); row.put("side_effect_outcome", "UNKNOWN");
                } else if (sql.contains("SET state='FAILED_KNOWN'")) {
                    if (!"PREPARED".equals(row.get("state")) || !Long.valueOf(0).equals(row.get("dispatch_epoch"))) return 0;
                    base.trace.add("inv-write-close"); row.put("state", "FAILED_KNOWN"); row.put("outcome", values[0]); row.put("error_code", values[1]);
                    row.put("updated_at", values[2]); row.put("finished_at", values[3]); row.put("side_effect_outcome", "NONE");
                } else if (sql.contains("SET state=?") && sql.contains("business_receipt_id=?")) {
                    base.trace.add("inv-write-complete"); row.put("state", values[0]); row.put("outcome", values[1]); row.put("business_receipt_id", values[2]); row.put("error_code", values[3]);
                    row.put("side_effect_outcome", values[4]); row.put("updated_at", values[5]); row.put("finished_at", values[6]);
                } else throw new AssertionError("Unexpected controlled write update");
                row.put("revision", ((Number) row.get("revision")).longValue() + 1); return 1;
            }).when(base.jdbc.get()).update(startsWith("UPDATE tianji_agent.agent_tool_invocation"), any(Object[].class));
        }

        ToolInvocationStore.PreparationRequestVO request(String id, UUID invocation) {
            var binding = binding(id); var action = base.action;
            return new ToolInvocationStore.PreparationRequestVO(binding, new AgentRunLease(id, "worker", 1, NOW.minusSeconds(1)), base.tool, base.arguments(), action,
                    invocation, new BigDecimal("0.20"), action == null ? null : NOW.plusSeconds(300));
        }
        ToolInvocationStore.PreparationRequestVO changedRequest(ToolInvocationStore.PreparationRequestVO original) {
            var action = new ToolActionRequest(ACTOR, base.tool, base.action.businessKey(), base.action.businessVersion(), Map.of("amount", 21L));
            return new ToolInvocationStore.PreparationRequestVO(original.binding(), original.lease(), base.tool, action.arguments(), action, original.invocationId(), original.estimatedCost(), original.intentExpiry());
        }
        ToolExecutionGrant.BindingVO binding(String id) { return new ToolExecutionGrant.BindingVO(run(id), base.tool.reference(), base.tool.policy().bindingSha256(), "TIANJI_TOOL_PROCESS", URI.create("https://agent.example/20"), "tool.process.execute", null); }
        ToolExecutionGrant.EvidenceVO evidence(String id) { return evidences.computeIfAbsent(id, key -> new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(binding(id), 3), ToolInvocationStoreTest.GRANT, "ENC:v1:key:A:sealed", NOW.plusSeconds(25))); }
        ToolExecutionGrant.EvidenceVO evidence(ToolInvocationStore.PreparationRequestVO request) { return evidence(request.binding().run().runId()); }
        ToolExecutionAuthorization.CheckResult allowed(ToolExecutionAuthorization.Request request, ToolExecutionAuthorization.Boundary boundary) {
            var evidence = evidence(request.run().runId()); return new ToolExecutionAuthorization.CheckResult(request, boundary, ToolExecutionAuthorization.Decision.ALLOW,
                    new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), boundary, ToolExecutionAuthorization.Decision.ALLOW, ACTOR.subjectType(), evidence.grantId(), 1L, null, 1L, base.wall.get(), evidence.expiresAt()));
        }
        ToolInvocationStore.DispatchWindowVO window(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.RecorderPreparationVO metadata) {
            Instant call = metadata.dispatchNotAfter();
            for (var bound : List.of(request.binding().run().deadlineAt(), evidence(request).expiresAt(), EXECUTOR.expiresAt())) if (bound.isBefore(call)) call = bound;
            Instant start = base.wall.get(), deadline = start.plusSeconds(10); if (call.isBefore(deadline)) deadline = call;
            return new ToolInvocationStore.DispatchWindowVO(start, base.nano.get(), deadline, call);
        }
        ToolExecutionAuthorization.CheckResult checked(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.DispatchWindowVO window) {
            return allowed(new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), base.tool.reference(), base.tool.policy().bindingSha256(),
                    request.parametersSha256(), request.action().intentId(), window.callDeadlineAt()), ToolExecutionAuthorization.Boundary.DISPATCH);
        }
        ToolInvocationStore.WritePreparationResultVO prepare(ToolInvocationStore.PreparationRequestVO request) { return selected(request, () -> store.prepareOrLoadIdempotentWrite(request)); }
        ToolInvocationStore.WriteDispatchBeginResultVO begin(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.RecorderPreparationVO expected, String plainNonce, ToolExecutionOutcome notSent) {
            return selected(request, () -> { var window = window(request, expected); return store.beginIdempotentWriteDispatch(request, expected, evidence(request), checked(request, window), EXECUTOR, ToolInvocationStore.dispatchNonceSha256(plainNonce), notSent, window); });
        }
        ToolInvocationStore.RecordedResultVO complete(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.DispatchAttemptVO attempt, String nonce, ToolExecutionOutcome outcome) { return selected(request, () -> store.completeIdempotentWrite(request, attempt, nonce, outcome)); }
        Optional<ToolInvocationStore.RecorderPreparationVO> close(ToolInvocationStore.PreparationRequestVO request, ToolInvocationStore.RecorderPreparationVO expected, ToolExecutionOutcome failure) { return selected(request, () -> store.closePreparedWriteFailure(request, expected, failure)); }
        <T> T selected(ToolInvocationStore.PreparationRequestVO request, java.util.function.Supplier<T> operation) {
            active = request; base.row.set(rows.get(key(request.binding().run().runId(), request.invocationId()))); base.evidence.set(evidence(request));
            try { return operation.get(); }
            finally { String key = key(request.binding().run().runId(), request.invocationId()); if (base.row.get() != null) rows.put(key, base.row.get()); else rows.remove(key); }
        }
        Map<String, Object> row(ToolInvocationStore.PreparationRequestVO request) { return rows.get(key(request.binding().run().runId(), request.invocationId())); }
        Map<String, Object> lookup(String id, UUID invocation) {
            if (active != null && active.binding().run().runId().equals(id) && active.invocationId().equals(invocation)) return base.row.get();
            return rows.get(key(id, invocation));
        }
        ToolExecutionOutcome outcome(ToolInvocationStore.PreparationRequestVO request) { return new ToolExecutionJsonCodec().outcome((String) row(request).get("outcome"), base.tool); }
        Map<String, Object> budgetView(Map<String, Object> row) {
            var view = new HashMap<>(row); var budget = base.budgetRows.get(row.get("reservation_id")); assertNotNull(budget);
            view.put("budget_lease_epoch", budget.leaseEpoch()); view.put("budget_kind", budget.kind().name()); view.put("budget_tool_version", budget.toolVersion()); view.put("budget_input_tokens", budget.inputTokens());
            view.put("budget_output_tokens", budget.outputTokens()); view.put("budget_estimated_cost", budget.estimatedCost()); view.put("budget_state", budget.state().name());
            view.put("budget_actual_input_tokens", budget.actualInputTokens()); view.put("budget_actual_output_tokens", budget.actualOutputTokens()); view.put("budget_actual_cost", budget.actualCost()); return view;
        }
        void budgetUnknown(ToolInvocationStore.PreparationRequestVO request, BigDecimal cost) {
            var old = base.budgetRows.get(request.reservationId()); base.budgetRows.put(old.reservationId(), new AgentBudgetReservation(old.reservationId(), old.leaseEpoch(), old.kind(), old.toolVersion(),
                    old.inputTokens(), old.outputTokens(), old.estimatedCost(), AgentBudgetReservation.State.UNKNOWN, 0L, 0L, cost, false));
        }
        void assertProjection(ToolActionIntentSql.Row action) {
            if (action.dispatch() == null) return; var d = action.dispatch(); var row = lookup(d.runId(), d.invocationId()); assertNotNull(row, "current action backlink must remain valid");
            assertEquals(d.epoch(), ((Number) row.get("dispatch_epoch")).longValue()); assertEquals(d.nonceSha256(), row.get("dispatch_nonce_sha256"));
            assertEquals(action.state().equals("APPLIED") ? "SUCCEEDED" : action.state(), row.get("state"), "no full JOIN may read an intermediate pair");
            var value = row.get("outcome"); var outcome = value == null ? null : new ToolExecutionJsonCodec().outcome(value.toString(), base.tool);
            assertEquals(action.outcome(), outcome);
        }
        ToolActionIntentSql.Row actionRow(String state, ToolActionIntentSql.DispatchVO dispatch, ToolExecutionOutcome outcome, String error, Instant closed) {
            var old = base.intentRow.get(); return new ToolActionIntentSql.Row(base.action, base.tool, old == null ? RUN : old.originRunId(), HASH, state, old == null ? 1 : old.revision() + 1,
                    state.equals("EXPIRED") ? NOW.minusSeconds(60) : NOW, base.databaseNow.get(), state.equals("EXPIRED") ? NOW.minusSeconds(1) : NOW.plusSeconds(300), null, dispatch,
                    outcome == null ? state.equals("DISPATCHING") ? "UNKNOWN" : "NONE" : effect(outcome), outcome, error, closed);
        }
        void seedAction(String state) {
            base.intentNew.set(false); ToolExecutionOutcome outcome = state.equals("APPLIED") ? success(null) : Set.of("REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state)
                    ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(state), Map.of(), null, "CONTROLLED", null) : null;
            boolean dispatched = Set.of("DISPATCHING", "APPLIED", "UNKNOWN").contains(state); var id = UUID.randomUUID();
            var dispatch = dispatched ? new ToolActionIntentSql.DispatchVO(RUN, id, 1, ToolInvocationStore.dispatchNonceSha256(NONCE), EXECUTOR.actor()) : null;
            if (dispatched) {
                var row = new HashMap<String, Object>(); row.put("state", state.equals("APPLIED") ? "SUCCEEDED" : state); row.put("dispatch_epoch", 1L); row.put("dispatch_nonce_sha256", dispatch.nonceSha256());
                row.put("outcome", outcome == null ? null : new ToolExecutionJsonCodec().outcome(base.tool, outcome)); rows.put(key(RUN, id), row);
            }
            base.intentRow.set(actionRow(state, dispatch, outcome, state.equals("EXPIRED") ? "INTENT_EXPIRED" : outcome == null ? null : outcome.errorCode(), Set.of("DISPATCHING").contains(state) ? null : NOW));
        }
        ToolInvocationStore.PreparationRequestVO seedFutureGeneration(ToolInvocationStore.PreparationRequestVO original) {
            var request = request(OTHER_RUN, UUID.randomUUID()); var row = new HashMap<>(row(original)); var nonce = ToolInvocationStore.dispatchNonceSha256("2".repeat(64));
            row.put("run_id", OTHER_RUN); row.put("invocation_id", request.invocationId()); row.put("reservation_id", request.reservationId()); row.put("dispatch_epoch", 2L); row.put("dispatch_nonce_sha256", nonce);
            row.put("capability_id", UUID.fromString(request.binding().capabilityId())); row.put("capability_binding_sha256", request.binding().bindingSha256()); rows.put(key(OTHER_RUN, request.invocationId()), row);
            var dispatch = new ToolActionIntentSql.DispatchVO(OTHER_RUN, request.invocationId(), 2, nonce, EXECUTOR.actor());
            base.intentRow.set(actionRow("DISPATCHING", dispatch, null, null, null)); return request;
        }
        void capture() {
            beforeRows = copy(rows); if (active != null && base.row.get() != null) beforeRows.put(key(active.binding().run().runId(), active.invocationId()), new HashMap<>(base.row.get()));
            beforeBudgets = new HashMap<>(base.budgetRows); beforeAction = base.intentRow.get(); beforeNew = base.intentNew.get(); beforeBudgetCount = base.budgetInserts.get(); beforeInvocationCount = base.invocationInserts.get();
        }
        void restore() {
            if (beforeRows == null) return;
            rows.clear(); rows.putAll(copy(beforeRows)); base.budgetRows.clear(); base.budgetRows.putAll(beforeBudgets); base.intentRow.set(beforeAction); base.intentNew.set(beforeNew);
            base.budgetInserts.set(beforeBudgetCount); base.invocationInserts.set(beforeInvocationCount); base.row.set(active == null ? null : rows.get(key(active.binding().run().runId(), active.invocationId()))); beforeRows = null;
        }
        private static Map<String, Map<String, Object>> copy(Map<String, Map<String, Object>> rows) { var result = new HashMap<String, Map<String, Object>>(); rows.forEach((id, row) -> result.put(id, new HashMap<>(row))); return result; }
        @Override public void close() { base.close(); }
    }

    private static String effect(ToolExecutionOutcome outcome) { return outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE"; }
    private static String key(String run, UUID invocation) { return run + ':' + invocation; }
    private static AgentRunAuthorization.Request run(String id) { return new AgentRunAuthorization.Request(ACTOR, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60)); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "controlled action CAS conflict"); }
    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getBigDecimal(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value instanceof Timestamp timestamp ? timestamp : value == null ? null : Timestamp.from((Instant) value); });
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(call -> values.get(call.getArgument(0))); return result;
    }
}
