package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Store/Invocation SQL/Spring budget flow with controlled JDBC and an Action mock.
 * Action SQL and physical PostgreSQL locking/constraints have separate fixtures. */
class ToolApprovedInvocationStoreTest {
    private static final Instant NOW = ToolInvocationStoreTest.NOW;
    private static final String RUN = ToolInvocationStoreTest.RUN, OTHER_RUN = "10000000-0000-0000-0000-000000000002", HASH = ToolInvocationStoreTest.HASH;
    private static final TrustedAgentActor OWNER = ToolInvocationStoreTest.ACTOR;
    private static final String NONCE = "1".repeat(64);
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.approvedTest", 1, "approved_start", "Controlled approval journal",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
            new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.invoice", "tianji.api.process.instance.start", HASH, "approval-1"));
    private static final TrustedAgentActor CONSUMER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "approval-reader", "https://idp.example/20");
    private static final VerifiedExecutor EXECUTOR = new VerifiedExecutor(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE,
            "business-executor", "https://business-idp.example/20"), NOW.plusSeconds(50));

    @Test void package_only_contract_has_four_fields_and_rejects_unready_ambient_or_non_aa_entry() throws Exception {
        assertFalse(Modifier.isPublic(ToolInvocationStore.class.getDeclaredMethod("consumeApprovalAndBeginDispatch", ToolInvocationStore.PreparationRequestVO.class,
                ApprovedObservation.class, ToolExecutionGrant.EvidenceVO.class, ToolExecutionAuthorization.CheckResult.class, VerifiedExecutor.class,
                String.class, ToolInvocationStore.DispatchWindowVO.class).getModifiers()));
        assertEquals(4, ToolInvocationStore.ApprovedWriteDispatchBeginResultVO.class.getRecordComponents().length);
        try (var f = new Fixture()) {
            var request = f.request(RUN); var window = f.window();
            var unready = f.rig.base.store(false, f.rig.base.authority);
            assertThrows(AgentLedgerException.class, () -> unready.consumeApprovalAndBeginDispatch(request, f.approval, f.rig.evidence(request), f.rig.checked(request, window), EXECUTOR, HASH, window));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> f.begin(request)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
        try (var old = new ToolWriteInvocationStoreTest.Rig()) {
            var request = old.request(RUN, UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> old.store.consumeApprovalAndBeginDispatch(request, null, null, null, EXECUTOR, HASH, null));
        }
    }

    @Test void old_finished_wait_uuid_and_settled_zero_budget_are_never_revived() throws Exception {
        try (var f = new Fixture()) {
            var row = new HashMap<>(f.rig.row(f.waiting)); var budget = f.rig.base.budgetRows.get(f.waiting.reservationId()); var action = f.rig.base.intentRow.get();
            var replay = f.begin(f.waiting);
            assertFalse(replay.started()); assertEquals("AWAITING_APPROVAL", replay.invocation().state());
            assertEquals(row, f.rig.row(f.waiting)); assertEquals(budget, replay.budget()); assertSame(action, f.rig.base.intentRow.get());
            assertEquals(1, f.rig.base.budgetInserts.get()); assertEquals(1, f.rig.base.invocationInserts.get());
        }
    }

    @Test void fresh_host_uuid_reserves_new_budget_and_inserts_invocation_before_action_cas() throws Exception {
        try (var f = new Fixture()) {
            var old = new HashMap<>(f.rig.row(f.waiting)); var oldBudget = f.rig.base.budgetRows.get(f.waiting.reservationId());
            var request = f.request(OTHER_RUN); var started = f.begin(request);
            assertTrue(started.started()); assertEquals("DISPATCHING", started.action().state()); assertEquals(1, started.attempt().dispatchEpoch());
            assertEquals(EXECUTOR.actor(), started.attempt().executor()); assertNotEquals(CONSUMER, started.attempt().executor());
            assertEquals(request.invocationId(), started.attempt().invocationId()); assertEquals(AgentBudgetReservation.State.RESERVED, started.budget().state());
            assertEquals(old, f.rig.row(f.waiting)); assertEquals(oldBudget, f.rig.base.budgetRows.get(f.waiting.reservationId()));
            assertEquals(2, f.rig.base.budgetInserts.get()); assertEquals(2, f.rig.base.invocationInserts.get());
            var trace = f.rig.base.trace;
            assertTrue(trace.indexOf("run-lock") < trace.indexOf("cap-in")); assertTrue(trace.indexOf("cap-in") < trace.indexOf("action-approved-lock"));
            assertTrue(trace.indexOf("approved-inv-insert") < trace.indexOf("action-approved-begin"));
            assertEquals("ToolInvocationStore.ApprovedWriteDispatchBeginResultVO[redacted]", started.toString());
        }
    }

    @Test void another_fresh_uuid_for_an_advanced_action_returns_only_action_metadata() throws Exception {
        try (var f = new Fixture()) {
            var first = f.request(RUN); assertTrue(f.begin(first).started()); var original = f.rig.base.intentRow.get();
            var other = f.begin(f.request(OTHER_RUN));
            assertFalse(other.started()); assertNull(other.invocation()); assertNull(other.budget()); assertEquals("DISPATCHING", other.action().state());
            assertSame(original, f.rig.base.intentRow.get()); assertEquals(2, f.rig.base.budgetInserts.get());
            var own = f.begin(first); assertFalse(own.started()); assertEquals(first.invocationId(), own.invocation().invocationId());
        }
    }

    @Test void a_dispatched_invocation_cannot_be_replayed_against_an_action_with_no_dispatch_backlink() throws Exception {
        try (var f = new Fixture()) {
            var request = f.request(RUN); f.begin(request); var old = f.rig.base.intentRow.get();
            f.rig.base.intentRow.set(new ToolActionIntentSql.Row(old.action(),old.descriptor(),old.originRunId(),old.originSnapshotSha256(),"AWAITING_APPROVAL",old.revision()+1,
                    old.createdAt(),old.updatedAt(),old.expiresAt(),null,null,"NONE",null,null,null));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,assertThrows(AgentLedgerException.class,() -> f.begin(request)).code());
            assertEquals(2,f.rig.base.budgetInserts.get()); assertEquals("DISPATCHING",f.rig.row(request).get("state"));
        }
    }

    @Test void missing_or_expired_waiting_action_cannot_allocate_a_fresh_budget() throws Exception {
        try (var f = new Fixture()) {
            f.rig.base.intentRow.set(null); assertThrows(AgentLedgerException.class, () -> f.begin(f.request(RUN))); assertEquals(1, f.rig.base.budgetInserts.get());
        }
        try (var f = new Fixture()) {
            var row = f.rig.base.intentRow.get(); f.rig.base.intentRow.set(new ToolActionIntentSql.Row(row.action(), row.descriptor(), row.originRunId(), row.originSnapshotSha256(), row.state(), row.revision(),
                    NOW.minusSeconds(60), NOW.minusSeconds(2), NOW.minusSeconds(1), null, null, "NONE", null, null, null));
            var result = f.begin(f.request(RUN)); assertFalse(result.started()); assertNull(result.invocation()); assertEquals(1, f.rig.base.budgetInserts.get());
        }
    }

    @Test void current_run_lease_and_exact_capability_e1_e2_are_required() throws Exception {
        for (String drift : List.of("lease", "capability", "cancel")) try (var f = new Fixture()) {
            if (drift.equals("lease")) f.rig.base.databaseLease.set(NOW.minusSeconds(1));
            else if (drift.equals("cancel")) f.rig.base.sourceActive.set(false);
            else { var ticket = f.rig.evidence(f.request(RUN)); f.rig.base.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(ticket.reservation().binding(), 4), ticket.grantId(), ticket.ciphertext(), ticket.expiresAt())); }
            assertThrows(AgentLedgerException.class, () -> f.begin(f.request(RUN)), drift); assertEquals(1, f.rig.base.budgetInserts.get(), drift);
        }
    }

    @Test void full_dispatch_request_decision_and_grant_are_not_replaceable_by_approval() throws Exception {
        for (String drift : List.of("parameters", "intent", "grant", "deny", "legacy")) try (var f = new Fixture()) {
            var request = f.request(RUN); var window = f.window(); var expected = f.rig.checked(request, window); var query = expected.request();
            if (drift.equals("parameters")) query = new ToolExecutionAuthorization.Request(query.run(), query.lease(), query.tool(), query.bindingSha256(), "b".repeat(64), query.actionIntentId(), query.deadlineAt());
            if (drift.equals("intent")) query = new ToolExecutionAuthorization.Request(query.run(), query.lease(), query.tool(), query.bindingSha256(), query.parametersSha256(), UUID.randomUUID().toString(), query.deadlineAt());
            var audit = expected.audit();
            if (drift.equals("grant")) audit = new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), audit.boundary(), audit.decision(), audit.subjectType(), "9" + "2".repeat(62), audit.authVersion(), null, audit.policyVersion(), audit.checkedAt(), audit.expiresAt());
            var checked = drift.equals("deny") ? new ToolExecutionAuthorization.CheckResult(query, expected.boundary(), ToolExecutionAuthorization.Decision.DENY, null)
                    : new ToolExecutionAuthorization.CheckResult(query, expected.boundary(), expected.decision(), drift.equals("legacy") ? null : audit);
            assertThrows(AgentLedgerException.class, () -> f.consume(request, f.approval, checked, EXECUTOR, window)); assertEquals(1, f.rig.base.budgetInserts.get());
        }
    }

    @Test void deadline_must_be_micro_exact_and_covered_by_observation_executor_and_original_clock_window() throws Exception {
        for (String drift : List.of("nano", "proof", "consumer", "executor", "wall", "monotonic")) try (var f = new Fixture()) {
            var request = f.request(RUN); var window = drift.equals("nano") ? f.window(NOW.plusSeconds(10).plusNanos(1)) : f.window();
            var approval = drift.equals("proof") ? f.observation(NOW.plusSeconds(5), NOW.plusSeconds(19)) : drift.equals("consumer") ? f.observation(NOW.plusSeconds(20), NOW.plusSeconds(5)) : f.approval;
            var executor = drift.equals("executor") ? new VerifiedExecutor(EXECUTOR.actor(), NOW.plusSeconds(5)) : EXECUTOR;
            var checked = f.rig.checked(request, window);
            if (drift.equals("wall")) f.rig.base.wall.set(NOW.plusSeconds(11));
            if (drift.equals("monotonic")) f.rig.base.nano.set(11_000_000_000L);
            assertThrows(AgentLedgerException.class, () -> f.consume(request, approval, checked, executor, window), drift);
            assertEquals(1, f.rig.base.budgetInserts.get());
        }
    }

    @Test void full_nano_proof_and_sha_survive_consumption_without_rewriting_times() throws Exception {
        try (var f = new Fixture()) {
            var proof = f.proof(f.approval); var codec = new ToolExecutionJsonCodec(); String json = codec.approval(proof, f.waiting.action()), sha = codec.approvalSha256(proof, f.waiting.action());
            f.begin(f.request(RUN)); var consumed = f.rig.base.intentRow.get().approval();
            assertEquals(proof, consumed.proof()); assertEquals(sha, consumed.proofSha256()); assertEquals(json, codec.approval(consumed.proof(), f.waiting.action()));
            assertEquals(987_654_321, consumed.proof().approval().expiresAt().getNano());
        }
    }

    @Test void orphan_budget_and_other_invocation_identity_are_not_adopted() throws Exception {
        try (var f = new Fixture()) {
            var request = f.request(RUN); var orphan = new AgentBudgetReservation(request.reservationId(), 1, AgentBudgetRequest.Kind.TOOL, TOOL.versionKey(), 0, 0,
                    request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, false);
            f.rig.base.budgetRows.put(request.reservationId(), orphan);
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> f.begin(request)).code());
            assertEquals(orphan, f.rig.base.budgetRows.get(request.reservationId())); assertNull(f.rig.row(request));
        }
        try (var f = new Fixture()) {
            var row = f.rig.row(f.waiting); row.put("parameters_sha256", "f".repeat(64));
            assertThrows(AgentLedgerException.class, () -> f.begin(f.waiting)); assertEquals(1, f.rig.base.budgetInserts.get());
        }
    }

    @Test void invocation_insert_action_cas_and_duplicate_receipt_failures_roll_back_fresh_budget_and_counters() throws Exception {
        for (String failure : List.of("insert", "action", "duplicate")) try (var f = new Fixture()) {
            var request = f.request(RUN); var before = f.rig.base.intentRow.get(); var wait = new HashMap<>(f.rig.row(f.waiting));
            if (failure.equals("insert")) f.rig.base.failInsert.set(true); else if (failure.equals("action")) f.rig.failActionBegin.set(true); else f.duplicate.set(true);
            assertThrows(AgentLedgerException.class, () -> f.begin(request)); assertSame(before, f.rig.base.intentRow.get());
            assertEquals(wait, f.rig.row(f.waiting)); assertNull(f.rig.row(request)); assertNull(f.rig.base.budgetRows.get(request.reservationId()));
            assertEquals(1, f.rig.base.budgetInserts.get()); assertEquals(1, f.rig.base.invocationInserts.get()); assertTrue(f.rig.base.trace.contains("rollback"));
        }
    }

    @Test void historical_original_nonce_completion_preserves_generic_cost_and_does_not_settle() throws Exception {
        for (BigDecimal cost : new BigDecimal[]{null, new BigDecimal("0.0750"), new BigDecimal("2.5000")}) try (var f = new Fixture()) {
            var request = f.request(RUN); var started = f.begin(request); var approval = f.rig.base.intentRow.get().approval();
            f.rig.base.databaseNow.set(NOW.plusSeconds(60)); f.rig.base.wall.set(NOW.plusSeconds(60)); f.rig.base.sourceActive.set(false);
            if (cost != null && cost.compareTo(request.estimatedCost()) > 0) f.rig.budgetUnknown(request, cost);
            clearInvocations(f.rig.base.capabilities, f.rig.base.authority, f.rig.base.budgets);
            Thread.currentThread().interrupt();
            try {
                var result = f.complete(request, started.attempt(), NONCE, success(cost)); assertEquals(success(cost), result.outcome()); assertTrue(Thread.currentThread().isInterrupted());
                assertEquals("APPLIED", f.rig.base.intentRow.get().state()); assertEquals(approval, f.rig.base.intentRow.get().approval());
            } finally { Thread.interrupted(); }
            assertEquals(success(cost), f.complete(request, started.attempt(), NONCE, success(cost)).outcome());
            verifyNoInteractions(f.rig.base.capabilities, f.rig.base.authority); verify(f.rig.base.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void unknown_never_redrives_but_original_nonce_can_refine_to_known_fact() throws Exception {
        try (var f = new Fixture()) {
            var request = f.request(RUN); var started = f.begin(request);
            var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OPERATION_RESULT_UNKNOWN", null);
            f.complete(request, started.attempt(), NONCE, unknown); var replay = f.begin(f.request(OTHER_RUN)); assertFalse(replay.started()); assertNull(replay.invocation());
            assertEquals(2, f.rig.base.budgetInserts.get());
            assertThrows(AgentLedgerException.class, () -> f.complete(request, started.attempt(), "2".repeat(64), success(null)));
            f.complete(request, started.attempt(), NONCE, success(null));
            assertThrows(AgentLedgerException.class, () -> f.complete(request, started.attempt(), NONCE, new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "DIFFERENT", null)));
        }
    }

    @Test void failed_action_completion_rolls_back_own_fact_and_keeps_original_approval() throws Exception {
        try (var f = new Fixture()) {
            var request = f.request(RUN); var started = f.begin(request); var action = f.rig.base.intentRow.get(); var row = new HashMap<>(f.rig.row(request));
            f.rig.failActionComplete.set(true); assertThrows(AgentLedgerException.class, () -> f.complete(request, started.attempt(), NONCE, success(null)));
            assertEquals(row, f.rig.row(request)); assertSame(action, f.rig.base.intentRow.get());
        }
    }

    @Test void late_original_fact_does_not_replace_a_different_current_action_pointer() throws Exception {
        try (var f = new Fixture()) {
            var request = f.request(RUN); var started = f.begin(request); var old = f.rig.base.intentRow.get();
            var later = f.request(OTHER_RUN); var laterRow = new HashMap<>(f.rig.row(request));
            String nonce = ToolInvocationStore.dispatchNonceSha256("2".repeat(64));
            laterRow.put("run_id", OTHER_RUN); laterRow.put("invocation_id", later.invocationId()); laterRow.put("reservation_id", later.reservationId());
            laterRow.put("dispatch_epoch", 2L); laterRow.put("dispatch_nonce_sha256", nonce);
            f.rig.selected(later, () -> { f.rig.base.row.set(laterRow); return null; });
            var dispatch = new ToolActionIntentSql.DispatchVO(OTHER_RUN,later.invocationId(),2,nonce,EXECUTOR.actor());
            var current = new ToolActionIntentSql.Row(old.action(),old.descriptor(),old.originRunId(),old.originSnapshotSha256(),"DISPATCHING",old.revision()+1,
                    old.createdAt(),old.updatedAt(),old.expiresAt(),old.approval(),dispatch,"UNKNOWN",null,null,null);
            f.rig.base.intentRow.set(current);
            assertEquals("SUCCEEDED", f.complete(request,started.attempt(),NONCE,success(new BigDecimal("0.3500"))).invocation().state());
            assertSame(current,f.rig.base.intentRow.get()); assertEquals("DISPATCHING",f.rig.row(later).get("state"));
            assertEquals(2L,f.rig.row(later).get("dispatch_epoch"));
        }
    }

    @Test void result_value_requires_aa_action_and_paired_matching_invocation_budget() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.ApprovedWriteDispatchBeginResultVO(null, null, null, null));
        try (var f = new Fixture()) {
            var result = f.begin(f.waiting);
            assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.ApprovedWriteDispatchBeginResultVO(result.action(), result.invocation(), null, null));
            assertThrows(IllegalArgumentException.class, () -> new ToolInvocationStore.ApprovedWriteDispatchBeginResultVO(result.action(), null, result.budget(), null));
        }
    }

    private static ToolExecutionOutcome success(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "business-value"), "business-receipt", null, cost); }
    static final class Fixture implements AutoCloseable {
        final ToolWriteInvocationStoreTest.Rig rig = new ToolWriteInvocationStoreTest.Rig(TOOL);
        final ToolInvocationStore.PreparationRequestVO waiting = rig.request(RUN, UUID.randomUUID());
        final ApprovedObservation approval;
        final AtomicBoolean duplicate = new AtomicBoolean();
        Fixture() throws Exception {
            // The reused IDW rig prepares PREPARED actions; this fixture supplies the real AA waiting shape.
            doAnswer(call -> {
                boolean created = rig.base.intentNew.getAndSet(false); var row = rig.base.intentRow.get();
                if (row == null || created) {
                    AgentRunAuthorization.Request run = call.getArgument(0);
                    row = new ToolActionIntentSql.Row(rig.base.action,TOOL,run.runId(),run.snapshotSha256(),"AWAITING_APPROVAL",1,NOW,NOW,call.getArgument(4),null,null,"NONE",null,null,null);
                    rig.base.intentRow.set(row); created = true;
                }
                return new ToolActionIntentSql.PreparationResultVO(row,created);
            }).when(rig.actionSql).prepareWithStatus(any(), any(), any(), any(), any(), any());
            try { rig.selected(waiting, () -> rig.store.prepare(waiting)); }
            catch (RuntimeException | Error initializationFailure) { rig.close(); throw initializationFailure; }
            approval = observation(NOW.plusSeconds(20).plusNanos(987_654_321), NOW.plusSeconds(19).plusNanos(123_456_789));
            when(rig.actionSql.lockForApproval(any(), any(), any())).thenAnswer(call -> {
                rig.base.trace.add("action-approved-lock"); var row = rig.base.intentRow.get();
                if (row == null || !row.action().equals(call.getArgument(0))) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Controlled missing action");
                rig.assertProjection(row); return row;
            });
            doAnswer(call -> {
                rig.base.trace.add("action-approved-begin"); if (rig.failActionBegin.get()) throw new DataIntegrityViolationException("private action body");
                if (duplicate.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Controlled receipt conflict");
                ToolActionIntentSql.Row old = call.getArgument(0); ApprovedObservation observed = call.getArgument(1); ToolActionIntentSql.DispatchVO dispatch = call.getArgument(2); Instant time = call.getArgument(3);
                var proof = proof(observed); var consumed = new ToolActionIntentSql.ApprovalConsumptionVO(proof, new ToolExecutionJsonCodec().approvalSha256(proof, old.action()), time);
                rig.base.intentRow.set(new ToolActionIntentSql.Row(old.action(), old.descriptor(), old.originRunId(), old.originSnapshotSha256(), "DISPATCHING", old.revision()+1,
                        old.createdAt(), time, old.expiresAt(), consumed, dispatch, "UNKNOWN", null, null, null));
                return null;
            }).when(rig.actionSql).beginApprovedWrite(any(), any(), any(), any(), any());
            doAnswer(call -> {
                rig.base.trace.add("action-approved-complete"); if (rig.failActionComplete.get()) throw new DataIntegrityViolationException("private completion");
                ToolActionIntentSql.Row old = call.getArgument(0); ToolExecutionOutcome outcome = call.getArgument(1); Instant time = call.getArgument(2);
                String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
                String effect = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE";
                rig.base.intentRow.set(new ToolActionIntentSql.Row(old.action(), old.descriptor(), old.originRunId(), old.originSnapshotSha256(), state, old.revision()+1,
                        old.createdAt(), time, old.expiresAt(), old.approval(), old.dispatch(), effect, outcome, outcome.errorCode(), time)); return null;
            }).when(rig.actionSql).completeApprovedWrite(any(), any(), any(), any());
            doAnswer(call -> {
                if (rig.base.failInsert.get()) throw new DataIntegrityViolationException("private insert");
                String sql = call.getArgument(0); Object[] args = (Object[]) call.getRawArguments()[1];
                String[] columns = sql.substring(sql.indexOf('(')+1,sql.indexOf(')')).split(",");
                String values = sql.substring(sql.indexOf("VALUES")+6).trim(); values = values.substring(1, values.lastIndexOf(')'));
                String[] expressions = values.split(","); assertEquals(columns.length, expressions.length);
                var row = new HashMap<String,Object>(); int index = 0;
                for (int c=0;c<columns.length;c++) {
                    String expression = expressions[c].trim(); Object value;
                    if (expression.contains("?")) value = args[index++];
                    else if (expression.equalsIgnoreCase("NULL")) value = null;
                    else if (expression.startsWith("'")) value = expression.substring(1,expression.length()-1);
                    else value = Long.parseLong(expression);
                    row.put(columns[c].trim(),value);
                }
                assertEquals(args.length,index); rig.base.trace.add("approved-inv-insert"); rig.base.row.set(row); rig.base.invocationInserts.incrementAndGet(); return 1;
            }).when(rig.base.jdbc.get()).update(argThat((String sql) -> sql.startsWith("INSERT INTO tianji_agent.agent_tool_invocation") && sql.contains("dispatch_nonce_sha256")), any(Object[].class));
            rig.base.trace.clear();
        }
        ToolInvocationStore.PreparationRequestVO request(String run) { return rig.request(run, UUID.randomUUID()); }
        ToolInvocationStore.DispatchWindowVO window() { return window(NOW.plusSeconds(10)); }
        ToolInvocationStore.DispatchWindowVO window(Instant call) { return new ToolInvocationStore.DispatchWindowVO(NOW,0,call.isBefore(NOW.plusSeconds(10))?call:NOW.plusSeconds(10),call); }
        ToolInvocationStore.ApprovedWriteDispatchBeginResultVO begin(ToolInvocationStore.PreparationRequestVO request) { var window = window(); return consume(request,approval,rig.checked(request,window),EXECUTOR,window); }
        ToolInvocationStore.ApprovedWriteDispatchBeginResultVO consume(ToolInvocationStore.PreparationRequestVO request,ApprovedObservation observed,ToolExecutionAuthorization.CheckResult checked,VerifiedExecutor executor,ToolInvocationStore.DispatchWindowVO window) {
            return rig.selected(request,() -> rig.store.consumeApprovalAndBeginDispatch(request,observed,rig.evidence(request),checked,executor,ToolInvocationStore.dispatchNonceSha256(NONCE),window));
        }
        ToolInvocationStore.RecordedResultVO complete(ToolInvocationStore.PreparationRequestVO request,ToolInvocationStore.DispatchAttemptVO attempt,String nonce,ToolExecutionOutcome outcome) {
            return rig.selected(request,() -> rig.store.completeApprovedWrite(request,attempt,nonce,outcome));
        }
        ApprovedObservation observation(Instant expiry,Instant consumerExpiry) {
            var binding = new ToolApprovalBinding(CONSUMER,"approval-1",HASH,"v1","approval_flow","b".repeat(64),"approve");
            var query = binding.query(waiting.action(),"approval-receipt"); var source = query.sourceStart();
            var dto = new VerifiedApprovalDTO(binding.policyRef(),binding.policyBindingSha256(),query.fingerprint(),query.approvalReceiptId(),query.requesterType(),query.requesterSubjectId(),
                    source.commandId(),"scope-1","task-1",source.releaseVersion(),source.definitionKey(),binding.definitionSha256(),binding.taskDefinitionKey(),OrchestrationRequestDigests.start(source),true,NOW.minusSeconds(2),NOW.minusSeconds(1),expiry);
            return new ApprovedObservation("ps-primary","https://idp.example/20","https://process.example/20",binding,dto,CONSUMER,consumerExpiry);
        }
        ToolExecutionJsonCodec.ApprovalProofVO proof(ApprovedObservation observed) { return new ToolExecutionJsonCodec.ApprovalProofVO(1,
                new ToolExecutionJsonCodec.ApprovalSourceVO(observed.sourceId(),observed.tenantId(),observed.sourceIssuer(),observed.sourceResource()),observed.binding(),observed.approval()); }
        @Override public void close() { rig.close(); }
    }
}
