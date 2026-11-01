package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchAttemptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchBeginResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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

/** Real Store/budget transactions over the existing controlled JDBC fixture; the transport is an explicit trusted-port substitute. */
class ToolInvocationRecorderTest {
    private static final Instant NOW = Instant.parse("2026-10-31T14:00:00Z");
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String PRIVATE_OUTPUT = "sensitive-result";
    private static final ToolDescriptor TOOL = new ToolDescriptor("decision.lookup", 1, "lookup_decision", "Fixed nonbillable lookup",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("value", ToolSchema.ValueType.STRING), Set.of("value")),
            new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.lookup", "tianji.api.process.decision.evaluate", "a".repeat(64), null));
    private static final ReadOnlyDecisionToolTransport.VerifiedExecutor EXECUTOR = new ReadOnlyDecisionToolTransport.VerifiedExecutor(
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "dmn-executor", "https://process-idp.example/20"), NOW.plusSeconds(15));

    private static final ToolDescriptor MCP_TOOL = new ToolDescriptor("mcp.lookup", 1, "lookup_catalog", "Fixed nonbillable MCP lookup",
            TOOL.input(), TOOL.output(), new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "mcp.lookup", ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY, "b".repeat(64), null));
    private static final ReadOnlyDecisionToolTransport.VerifiedExecutor MCP_EXECUTOR = new ReadOnlyDecisionToolTransport.VerifiedExecutor(
            TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "mcp-executor", "https://mcp-idp.example/20"), NOW.plusSeconds(15));

    @Test void affinity_readiness_ambient_transactions_and_nonzero_prices_are_rejected_before_preparation() throws Exception {
        try (var scene = new Scene(false)) {
            assertTrue(scene.recorder.usesDataSource(scene.storage.source)); assertTrue(scene.recorder.usesLedger(scene.storage.ledger));
            assertTrue(scene.recorder.usesPreparations(scene.preparations)); assertTrue(scene.recorder.usesCapabilities(scene.storage.capabilities)); assertTrue(scene.recorder.usesAuthorization(scene.storage.authority));
            assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder(mock(DataSource.class), scene.storage.ledger, scene.preparations, scene.storage.capabilities, scene.storage.authority));
            assertThrows(IllegalArgumentException.class, () -> new ToolInvocationRecorder(scene.storage.source, scene.storage.ledger, scene.preparations, scene.storage.capabilities, mock(ToolExecutionAuthorization.class)));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            scene.recorder.verifyStorage();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertThrows(IllegalArgumentException.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.storage.request(INVOCATION)));
            assertEquals(0, scene.storage.invocationInserts.get()); assertEquals(0, scene.transport.calls.get());
        }
    }

    @Test void dispatch_commits_before_the_one_shot_and_the_known_outcome_commits_before_accounting() throws Exception {
        try (var scene = new Scene()) {
            var result = scene.recorder.executeReadOnly(scene.transport, scene.request);
            assertEquals("SUCCEEDED", result.state()); assertEquals(1, result.dispatchEpoch()); assertTrue(result.operationInvoked());
            assertEquals(Map.of("value", PRIVATE_OUTPUT), result.outcome().output()); assertNull(result.outcome().businessReceiptId()); assertEquals(BigDecimal.ZERO, result.outcome().actualCost());
            assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, result.accountingState());
            assertTrue(scene.trace.indexOf("begin-ack") < scene.trace.indexOf("operation"));
            assertTrue(scene.trace.indexOf("operation") < scene.trace.indexOf("complete-ack"));
            assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("accounting"));
            assertEquals(1, scene.transport.evaluations.get()); assertEquals(1, scene.storage.budgetInserts.get()); assertEquals(1, scene.storage.invocationInserts.get());
            assertNotNull(scene.nonce.get()); assertTrue(scene.nonce.get().matches("[0-9a-f]{64}"));
            assertEquals(ToolInvocationStore.dispatchNonceSha256(scene.nonce.get()), scene.storage.row.get().get("dispatch_nonce_sha256"));
            assertNotEquals(scene.nonce.get(), scene.storage.row.get().get("dispatch_nonce_sha256"));
            assertEquals(EXECUTOR.actor().issuerFingerprint(), scene.storage.row.get().get("executor_issuer")); assertEquals(EXECUTOR.actor().subjectId(), scene.storage.row.get().get("executor_id"));
            var audit = new ToolExecutionJsonCodec().audit((String) scene.storage.row.get().get("authorization_audit"));
            assertEquals(ToolExecutionAuthorization.Boundary.DISPATCH, audit.boundary()); assertEquals(scene.request.binding().run().actor().subjectType(), audit.subjectType());
            assertEquals("ToolInvocationRecorder.RecordResult[redacted]", result.toString()); assertFalse(result.toString().contains(PRIVATE_OUTPUT)); assertFalse(result.toString().contains(scene.nonce.get()));
            assertEquals(scene.request.binding().run().runId(), scene.transport.request.get().correlationId());
            assertEquals(NOW.plusSeconds(20), scene.transport.request.get().deadlineAt());
        }
    }

    @Test void a_known_terminal_replay_rechecks_current_requester_authority_without_reinvoking_or_recharging() throws Exception {
        try (var scene = new Scene()) {
            var first = scene.recorder.executeReadOnly(scene.transport, scene.request);
            var replay = scene.recorder.executeReadOnly(scene.transport, scene.request);
            assertEquals(first.outcome(), replay.outcome()); assertFalse(replay.operationInvoked()); assertEquals(first.dispatchEpoch(), replay.dispatchEpoch());
            assertEquals(1, scene.transport.evaluations.get()); assertEquals(2, scene.dispatchChecks.get()); assertEquals(1, scene.storage.budgetInserts.get());
            verify(scene.storage.budgets).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void denied_or_unaudited_replay_never_returns_an_old_private_outcome_or_changes_its_fact() throws Exception {
        for (boolean deny : List.of(true, false)) {
            try (var scene = new Scene()) {
                scene.recorder.executeReadOnly(scene.transport, scene.request); String original = (String) scene.storage.row.get().get("outcome");
                scene.check.set(request -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.DISPATCH,
                        deny ? ToolExecutionAuthorization.Decision.DENY : ToolExecutionAuthorization.Decision.ALLOW, null));
                var failure = assertCode(deny ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
                assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(PRIVATE_OUTPUT));
                assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(original, scene.storage.row.get().get("outcome")); assertEquals(1, scene.transport.evaluations.get());
            }
        }
    }

    @Test void no_match_and_transport_failures_are_recorded_with_fixed_readonly_semantics() throws Exception {
        for (var variant : List.of("no-match", "denied", "rejected", "cancelled", "deadline", "invalid", "unknown", "runtime", "bad-output")) {
            try (var scene = new Scene()) {
                String state, code;
                switch (variant) {
                    case "no-match" -> { scene.transport.observation.set(new ReadOnlyDecisionToolTransport.DecisionObservation(ReadOnlyDecisionToolTransport.Status.NO_MATCH,
                            "30000000-0000-0000-0000-000000000001", NOW, NOW, Map.of(), null)); state = "REJECTED"; code = "DECISION_NO_MATCH"; }
                    case "denied" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_DENIED); state = "REJECTED"; code = "DOWNSTREAM_PERMISSION_DENIED"; }
                    case "rejected" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_REJECTED); state = "REJECTED"; code = "DOWNSTREAM_REJECTED"; }
                    case "cancelled" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.CANCELLED); state = "FAILED_KNOWN"; code = "DISPATCH_NOT_SENT"; }
                    case "deadline" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.DEADLINE_EXCEEDED); state = "FAILED_KNOWN"; code = "DISPATCH_NOT_SENT"; }
                    case "invalid" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_INVALID_RESPONSE); state = "UNKNOWN"; code = "OUTCOME_INVALID_RESPONSE"; }
                    case "unknown" -> { scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN); state = "UNKNOWN"; code = "OUTCOME_UNKNOWN"; }
                    case "runtime" -> { scene.transport.privateRuntime.set(true); state = "UNKNOWN"; code = "OUTCOME_UNKNOWN"; }
                    case "bad-output" -> { scene.transport.observation.set(new ReadOnlyDecisionToolTransport.DecisionObservation(ReadOnlyDecisionToolTransport.Status.MATCHED,
                            "30000000-0000-0000-0000-000000000001", NOW, NOW, Map.of("wrong", PRIVATE_OUTPUT), "row:1")); state = "UNKNOWN"; code = "OUTCOME_INVALID_RESPONSE"; }
                    default -> throw new AssertionError("Unexpected outcome fixture");
                }
                var result = scene.recorder.executeReadOnly(scene.transport, scene.request);
                assertEquals(state, result.state()); assertEquals(code, result.outcome().errorCode()); assertEquals(Map.of(), result.outcome().output());
                assertNull(result.outcome().businessReceiptId()); assertEquals(BigDecimal.ZERO, result.outcome().actualCost()); assertTrue(result.operationInvoked());
                assertEquals(1, scene.transport.evaluations.get()); assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, result.accountingState());
            }
        }
    }

    @Test void unknown_replay_is_metadata_only_and_never_resends_or_resettles_but_keeps_verified_accounting_truth() throws Exception {
        for (boolean settlementFails : List.of(false, true)) {
            try (var scene = new Scene()) {
                if (settlementFails) when(scene.storage.budgetRepository.lockLeaseForSettlement(any(), any())).thenReturn(false);
                scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN);
                var first = scene.recorder.executeReadOnly(scene.transport, scene.request);
                var replay = scene.recorder.executeReadOnly(scene.transport, scene.request);
                assertEquals("UNKNOWN", replay.state()); assertNull(replay.outcome()); assertFalse(replay.operationInvoked());
                assertEquals(settlementFails ? ToolInvocationRecorder.AccountingState.PENDING : ToolInvocationRecorder.AccountingState.SETTLED, first.accountingState());
                assertEquals(first.accountingState(), replay.accountingState());
                assertEquals(1, scene.transport.calls.get()); assertEquals(1, scene.transport.evaluations.get()); assertEquals(1, scene.dispatchChecks.get());
                verify(scene.storage.budgets).settleBudget(any(), any(), anyString(), any());
            }
        }
    }

    @Test void a_committed_dispatch_without_a_completion_ack_is_not_executed_again() throws Exception {
        try (var scene = new Scene()) {
            scene.failCompletion.set(true);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertNotNull(scene.storage.row.get(), "Expected a committed dispatch fact before the completion failure");
            assertEquals("DISPATCHING", scene.storage.row.get().get("state")); assertEquals(1, scene.transport.evaluations.get());
            scene.failCompletion.set(false);
            var replay = scene.recorder.executeReadOnly(scene.transport, scene.request);
            assertEquals("DISPATCHING", replay.state()); assertNull(replay.outcome()); assertFalse(replay.operationInvoked()); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, replay.accountingState());
            assertEquals(1, scene.transport.calls.get()); assertEquals(1, scene.transport.evaluations.get()); verify(scene.storage.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void a_begin_commit_followed_by_an_expired_local_window_records_known_no_send_with_the_original_nonce() throws Exception {
        try (var scene = new Scene()) {
            scene.afterBegin.set(() -> { scene.storage.wall.set(NOW.plusSeconds(11)); scene.storage.databaseNow.set(NOW.plusSeconds(11)); scene.storage.nano.set(11_000_000_000L); });
            var result = scene.recorder.executeReadOnly(scene.transport, scene.request);
            assertEquals("FAILED_KNOWN", result.state()); assertEquals("DISPATCH_NOT_SENT", result.outcome().errorCode()); assertFalse(result.operationInvoked());
            assertEquals(0, scene.transport.evaluations.get()); assertEquals(1, result.dispatchEpoch()); assertNotNull(scene.nonce.get());
            assertEquals(ToolInvocationStore.dispatchNonceSha256(scene.nonce.get()), scene.storage.row.get().get("dispatch_nonce_sha256"));
        }
    }

    @Test void late_known_facts_survive_cancellation_capability_reissue_and_thread_interruption_before_accounting() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.beforeOutcome.set(() -> {
                scene.storage.sourceActive.set(false); scene.storage.wall.set(NOW.plusSeconds(70)); scene.storage.databaseNow.set(NOW.plusSeconds(70)); scene.storage.nano.set(70_000_000_000L);
                var old = scene.storage.evidence.get(); scene.storage.evidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), old.grantId(), "ENC:v1:key:B:changed", old.expiresAt()));
                when(scene.storage.budgetRepository.lockLeaseForSettlement(any(), any())).thenReturn(false);
                Thread.currentThread().interrupt();
            });
            try {
                assertThrows(ReadOnlyDecisionToolTransport.Failure.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
                assertTrue(Thread.currentThread().isInterrupted()); assertEquals("SUCCEEDED", scene.storage.row.get().get("state"));
                assertEquals(3L, scene.storage.row.get().get("capability_issue_epoch")); assertEquals(1, scene.transport.evaluations.get());
                var callbackResult = (ToolInvocationRecorder.RecordResult) scene.transport.callbackResult.get();
                assertEquals(ToolInvocationRecorder.AccountingState.PENDING, callbackResult.accountingState()); assertEquals("SUCCEEDED", callbackResult.state());
                assertEquals(Map.of("value", PRIVATE_OUTPUT), new ToolExecutionJsonCodec().outcome((String) scene.storage.row.get().get("outcome"), TOOL).output());
                assertFalse(scene.completionSawInterrupt.get()); assertTrue(scene.storage.trace.contains("commit"));
            } finally { Thread.interrupted(); }
        }
    }

    @Test void accounting_failure_cannot_roll_back_or_downgrade_a_committed_known_outcome() throws Exception {
        try (var scene = new Scene()) {
            when(scene.storage.budgetRepository.lockLeaseForSettlement(any(), any())).thenReturn(false);
            var result = scene.recorder.executeReadOnly(scene.transport, scene.request);
            assertEquals("SUCCEEDED", result.state()); assertEquals("SUCCEEDED", scene.storage.row.get().get("state"));
            assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.accountingState()); assertEquals(AgentBudgetReservation.State.RESERVED, scene.storage.budgetRows.get(scene.request.reservationId()).state());
            assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("accounting"));
        }
    }

    @Test void original_nonce_and_history_protect_unknown_refinement_and_known_completion_is_idempotent() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.operationFailure.set(ReadOnlyDecisionToolTransport.FailureKind.OPERATION_RESULT_UNKNOWN);
            scene.recorder.executeReadOnly(scene.transport, scene.request);
            var attempt = scene.attempt.get(); String nonce = scene.nonce.get(); String wrong = (nonce.charAt(0) == '0' ? "1" : "0") + nonce.substring(1);
            var known = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", PRIVATE_OUTPUT), null, null, BigDecimal.ZERO);
            assertThrows(AgentLedgerException.class, () -> scene.preparations.completeReadOnly(scene.request, attempt, wrong, known));
            var wrongEpoch = new DispatchAttemptVO(attempt.runId(), attempt.invocationId(), attempt.dispatchEpoch() + 1, attempt.capabilityIssueEpoch(), attempt.dispatchNonceSha256(), attempt.executor(), attempt.authorizationAudit(), attempt.dispatchedAt(), attempt.callDeadlineAt());
            assertThrows(AgentLedgerException.class, () -> scene.preparations.completeReadOnly(scene.request, wrongEpoch, nonce, known));
            var wrongExecutor = new DispatchAttemptVO(attempt.runId(), attempt.invocationId(), attempt.dispatchEpoch(), attempt.capabilityIssueEpoch(), attempt.dispatchNonceSha256(),
                    TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "other-executor", "https://process-idp.example/20"), attempt.authorizationAudit(), attempt.dispatchedAt(), attempt.callDeadlineAt());
            assertThrows(AgentLedgerException.class, () -> scene.preparations.completeReadOnly(scene.request, wrongExecutor, nonce, known));
            assertEquals("UNKNOWN", scene.storage.row.get().get("state"));
            var refined = scene.preparations.completeReadOnly(scene.request, attempt, nonce, known);
            assertEquals("SUCCEEDED", refined.invocation().state()); assertEquals(known, refined.outcome());
            var replay = scene.preparations.completeReadOnly(scene.request, attempt, nonce, known);
            assertEquals(refined.invocation().revision(), replay.invocation().revision());
            assertThrows(AgentLedgerException.class, () -> scene.preparations.completeReadOnly(scene.request, attempt, nonce,
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "DIFFERENT_RESULT", BigDecimal.ZERO)));
        }
    }

    @Test void transport_failure_before_callback_closes_only_the_matching_undispatched_preparation() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.beforeFailure.set(ReadOnlyDecisionToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE);
            var failure = assertThrows(ReadOnlyDecisionToolTransport.Failure.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertEquals(ReadOnlyDecisionToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE, failure.kind()); assertNull(failure.getCause());
            assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch"));
            assertNull(scene.storage.row.get().get("dispatch_nonce_sha256")); assertNull(scene.storage.row.get().get("executor_id")); assertEquals(0, scene.transport.evaluations.get());
            assertEquals(ToolExecutionAuthorization.Boundary.PREPARE, new ToolExecutionJsonCodec().audit((String) scene.storage.row.get().get("authorization_audit")).boundary());
            assertEquals(AgentBudgetReservation.State.SETTLED, scene.storage.budgetRows.get(scene.request.reservationId()).state());
        }
    }

    @Test void outer_postcallback_suppression_preserves_the_committed_fact_without_returning_its_private_outcome() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.afterFailure.set(ReadOnlyDecisionToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE);
            var failure = assertThrows(ReadOnlyDecisionToolTransport.Failure.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertEquals(ReadOnlyDecisionToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE, failure.kind()); assertFalse(failure.getMessage().contains(PRIVATE_OUTPUT)); assertNull(failure.getCause());
            assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(Map.of("value", PRIVATE_OUTPUT),
                    new ToolExecutionJsonCodec().outcome((String) scene.storage.row.get().get("outcome"), TOOL).output());
            assertEquals(1, scene.transport.evaluations.get()); assertEquals(1L, scene.storage.row.get().get("dispatch_epoch"));
        }
    }

    @Test void changed_evidence_and_original_monotonic_window_expiry_never_start_a_new_operation() throws Exception {
        for (boolean expired : List.of(false, true)) {
            try (var scene = new Scene()) {
                scene.beforeCheck.set(() -> {
                    if (expired) scene.storage.nano.set(10_000_000_001L);
                    else {
                        var old = scene.storage.evidence.get();
                        scene.storage.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(old.reservation().binding(), 4), old.grantId(), old.ciphertext(), old.expiresAt()));
                    }
                });
                assertThrows(RuntimeException.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
                assertNotNull(scene.storage.row.get(), "Expected preparation bookkeeping even when dispatch is rejected");
                assertEquals(0, scene.transport.evaluations.get()); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertNull(scene.storage.row.get().get("dispatch_nonce_sha256"));
                assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state"));
            }
        }
    }

    @Test void an_expired_known_fact_cannot_be_disclosed_and_prepare_ack_expiry_is_cleaned_without_fake_dispatch() throws Exception {
        try (var scene = new Scene()) {
            scene.recorder.executeReadOnly(scene.transport, scene.request);
            scene.storage.wall.set(NOW.plusSeconds(21)); scene.storage.databaseNow.set(NOW.plusSeconds(21)); scene.storage.nano.set(21_000_000_000L);
            assertThrows(ReadOnlyDecisionToolTransport.Failure.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(1, scene.transport.evaluations.get());
        }
        try (var scene = new Scene()) {
            doAnswer(call -> {
                var value = call.callRealMethod(); scene.storage.wall.set(NOW.plusSeconds(21)); scene.storage.databaseNow.set(NOW.plusSeconds(21)); scene.storage.nano.set(21_000_000_000L); return value;
            }).when(scene.preparations).prepareOrLoadForRecorder(any());
            assertThrows(ReadOnlyDecisionToolTransport.Failure.class, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertEquals(0, scene.transport.evaluations.get());
        }
    }

    @Test void decision_null_operation_keeps_its_existing_prebegin_unavailable_cleanup() throws Exception {
        try (var scene = new Scene()) {
            scene.transport.nullOperation.set(true);
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnly(scene.transport, scene.request));
            assertNull(failure.getCause()); assertEquals("Read-only tool invocation could not complete", failure.getMessage());
            assertEquals(0, scene.dispatchChecks.get()); assertFalse(scene.trace.contains("begin-ack")); assertEquals(0, scene.transport.evaluations.get());
            assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertNull(scene.storage.row.get().get("dispatch_nonce_sha256"));
            assertEquals("AUTHORIZATION_UNAVAILABLE", scene.storage.row.get().get("error_code"));
        }
    }

    @Test void mcp_requires_readiness_own_transactions_exact_resource_and_explicit_zero_cost_before_preparation() throws Exception {
        try (var scene = new Scene(MCP_TOOL, false)) {
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            scene.recorder.verifyStorage();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request)); assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertThrows(IllegalArgumentException.class, () -> scene.recorder.executeReadOnlyMcp(null, scene.request));
            assertThrows(IllegalArgumentException.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, null));
            for (BigDecimal cost : java.util.Arrays.asList(null, new BigDecimal("0.20"))) {
                var request = new PreparationRequestVO(scene.request.binding(), scene.request.lease(), MCP_TOOL, scene.request.arguments(), null, INVOCATION, cost, null);
                assertThrows(IllegalArgumentException.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, request));
            }
            assertEquals(0, scene.storage.invocationInserts.get()); assertEquals(0, scene.mcp.calls.get());
        }
        try (var scene = new Scene()) {
            assertThrows(IllegalArgumentException.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals(0, scene.storage.invocationInserts.get()); assertEquals(0, scene.mcp.calls.get());
        }
    }

    @Test void mcp_dispatch_commits_before_consuming_the_typed_call_and_records_its_fact_before_accounting() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            assertEquals("SUCCEEDED", result.state()); assertTrue(result.operationInvoked()); assertEquals(1, result.dispatchEpoch());
            assertEquals(Map.of("value", PRIVATE_OUTPUT), result.outcome().output()); assertNull(result.outcome().businessReceiptId()); assertEquals(BigDecimal.ZERO, result.outcome().actualCost());
            assertEquals(ToolInvocationRecorder.AccountingState.SETTLED, result.accountingState());
            assertTrue(scene.trace.indexOf("begin-ack") < scene.trace.indexOf("mcp-operation")); assertTrue(scene.trace.indexOf("mcp-operation") < scene.trace.indexOf("complete-ack")); assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("accounting"));
            assertEquals(1, scene.mcp.evaluations.get()); assertEquals(0, scene.transport.evaluations.get()); assertEquals(1, scene.storage.budgetInserts.get());
            assertEquals(ToolInvocationStore.dispatchNonceSha256(scene.nonce.get()), scene.storage.row.get().get("dispatch_nonce_sha256"));
            assertEquals(MCP_EXECUTOR.actor().issuerFingerprint(), scene.storage.row.get().get("executor_issuer")); assertEquals(MCP_EXECUTOR.actor().subjectId(), scene.storage.row.get().get("executor_id"));
            assertEquals(scene.request.binding().run(), scene.mcp.request.get().run()); assertEquals(scene.request.arguments(), scene.mcp.request.get().arguments());
            assertEquals(scene.request.invocationId(), scene.mcp.request.get().invocationId()); assertEquals(NOW.plusSeconds(20), scene.mcp.request.get().deadlineAt()); assertEquals(NOW.plusSeconds(15), scene.mcp.callDeadline.get());
        }
    }

    @Test void mcp_nullable_typed_operation_is_rejected_before_begin_even_when_executor_is_verified() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.nullOperation.set(true);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertNull(scene.storage.row.get().get("dispatch_nonce_sha256"));
            assertEquals(0, scene.mcp.evaluations.get()); assertFalse(scene.trace.contains("begin-ack"));
        }
    }

    @Test void mcp_invalid_executor_and_current_requester_denial_do_not_begin_or_consume_a_call() throws Exception {
        for (int variant = 0; variant < 3; variant++) try (var scene = new Scene(MCP_TOOL)) {
            if (variant == 0) scene.mcp.executor.set(null);
            else if (variant == 1) scene.mcp.executor.set(new ReadOnlyDecisionToolTransport.VerifiedExecutor(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other-executor", "https://mcp-idp.example/21"), NOW.plusSeconds(15)));
            else scene.check.set(request -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.DENY, null));
            assertCode(variant == 2 ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertEquals(0, scene.mcp.evaluations.get());
        }
    }

    @Test void mcp_success_tool_error_unknown_and_invalid_observations_have_distinct_fixed_outcomes() throws Exception {
        for (String variant : List.of("success", "tool-error", "unknown", "null", "bad-output")) try (var scene = new Scene(MCP_TOOL)) {
            String state = "SUCCEEDED", code = null;
            switch (variant) {
                case "tool-error" -> { scene.mcp.observation.set(new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.TOOL_ERROR, Map.of())); state = "FAILED_KNOWN"; code = "DOWNSTREAM_TOOL_ERROR"; }
                case "unknown" -> { scene.mcp.observation.set(new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.UNKNOWN, Map.of())); state = "UNKNOWN"; code = "OUTCOME_UNKNOWN"; }
                case "null" -> { scene.mcp.observation.set(null); state = "UNKNOWN"; code = "OUTCOME_INVALID_RESPONSE"; }
                case "bad-output" -> { scene.mcp.observation.set(new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.SUCCESS, Map.of("wrong", PRIVATE_OUTPUT))); state = "UNKNOWN"; code = "OUTCOME_INVALID_RESPONSE"; }
                default -> { }
            }
            var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            assertEquals(state, result.state()); assertEquals(code, result.outcome().errorCode()); assertTrue(result.operationInvoked()); assertEquals(1, scene.mcp.evaluations.get());
            assertEquals(state.equals("SUCCEEDED") ? Map.of("value", PRIVATE_OUTPUT) : Map.of(), result.outcome().output()); assertNull(result.outcome().businessReceiptId()); assertEquals(BigDecimal.ZERO, result.outcome().actualCost());
        }
    }

    @Test void mcp_only_explicit_not_sent_proves_no_dispatch_after_the_typed_operation_is_invoked() throws Exception {
        for (var kind : ReadOnlyMcpToolTransport.FailureKind.values()) try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.operationFailure.set(kind); var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            String state = "UNKNOWN", code = kind == ReadOnlyMcpToolTransport.FailureKind.OPERATION_INVALID_RESPONSE ? "OUTCOME_INVALID_RESPONSE" : "OUTCOME_UNKNOWN";
            if (kind == ReadOnlyMcpToolTransport.FailureKind.OPERATION_NOT_SENT) { state = "FAILED_KNOWN"; code = "DISPATCH_NOT_SENT"; }
            else if (kind == ReadOnlyMcpToolTransport.FailureKind.OPERATION_DENIED) { state = "REJECTED"; code = "DOWNSTREAM_PERMISSION_DENIED"; }
            assertEquals(state, result.state(), kind.name()); assertEquals(code, result.outcome().errorCode(), kind.name()); assertTrue(result.operationInvoked()); assertEquals(1, scene.mcp.evaluations.get()); assertEquals(Map.of(), result.outcome().output());
        }
        try (var scene = new Scene(MCP_TOOL)) { scene.mcp.privateRuntime.set(true); var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request); assertEquals("UNKNOWN", result.state()); assertEquals("OUTCOME_UNKNOWN", result.outcome().errorCode()); }
    }

    @Test void mcp_floors_the_effective_dispatch_deadline_and_passes_the_same_value_to_authority_store_and_call() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            Instant raw = NOW.plusSeconds(15).plusNanos(123_456_789), floor = raw.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            scene.mcp.executor.set(new ReadOnlyDecisionToolTransport.VerifiedExecutor(MCP_EXECUTOR.actor(), raw)); scene.dispatchDeadline.set(floor);
            var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            assertEquals("SUCCEEDED", result.state()); assertEquals(floor, scene.mcp.callDeadline.get()); assertEquals(floor, scene.attempt.get().callDeadlineAt());
            var audit = new ToolExecutionJsonCodec().audit((String) scene.storage.row.get().get("authorization_audit")); assertFalse(audit.expiresAt().isBefore(floor));
        }
    }

    @Test void mcp_a_future_submicrosecond_capability_floor_expires_before_dispatch_authorization_or_begin() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            doAnswer(call -> {
                var prepared = call.callRealMethod(); var old = scene.storage.evidence.get();
                scene.storage.evidence.set(new ToolExecutionGrant.EvidenceVO(old.reservation(), old.grantId(), old.ciphertext(), NOW.plusNanos(500))); return prepared;
            }).when(scene.preparations).prepareOrLoadForRecorder(any());
            var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals(ReadOnlyMcpToolTransport.FailureKind.DEADLINE_EXCEEDED, failure.kind()); assertNull(failure.getCause());
            assertEquals(0, scene.dispatchChecks.get()); assertFalse(scene.trace.contains("begin-ack")); assertEquals(0, scene.mcp.evaluations.get());
            assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state"));
        }
    }

    @Test void mcp_shared_prebegin_deadline_and_cancel_checks_are_translated_to_its_own_port_failure() throws Exception {
        for (boolean cancelled : List.of(true, false)) try (var scene = new Scene(MCP_TOOL)) {
            scene.beforeCheck.set(() -> { if (cancelled) Thread.currentThread().interrupt(); else scene.storage.nano.set(10_000_000_001L); });
            try {
                var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
                assertEquals(cancelled ? ReadOnlyMcpToolTransport.FailureKind.CANCELLED : ReadOnlyMcpToolTransport.FailureKind.DEADLINE_EXCEEDED, failure.kind()); assertNull(failure.getCause());
                assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertEquals(0, scene.mcp.evaluations.get());
                if (cancelled) assertTrue(Thread.currentThread().isInterrupted());
            } finally { Thread.interrupted(); }
        }
    }

    @Test void mcp_begin_ack_window_expiry_records_known_no_send_without_consuming_a_native_operation() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            scene.afterBegin.set(() -> { scene.storage.wall.set(NOW.plusSeconds(11)); scene.storage.databaseNow.set(NOW.plusSeconds(11)); scene.storage.nano.set(11_000_000_000L); });
            var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            assertEquals("FAILED_KNOWN", result.state()); assertEquals("DISPATCH_NOT_SENT", result.outcome().errorCode()); assertFalse(result.operationInvoked()); assertEquals(0, scene.mcp.evaluations.get());
            assertEquals(ToolInvocationStore.dispatchNonceSha256(scene.nonce.get()), scene.storage.row.get().get("dispatch_nonce_sha256"));
        }
    }

    @Test void mcp_pre_callback_profile_and_credential_failures_close_only_the_matching_prepared_row() throws Exception {
        for (var kind : List.of(ReadOnlyMcpToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE, ReadOnlyMcpToolTransport.FailureKind.PROFILE_UNAVAILABLE, ReadOnlyMcpToolTransport.FailureKind.PROFILE_CHANGED)) try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.beforeFailure.set(kind); var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals(kind, failure.kind()); assertNull(failure.getCause()); assertEquals("FAILED_KNOWN", scene.storage.row.get().get("state")); assertEquals(kind.name(), scene.storage.row.get().get("error_code"));
            assertEquals(0L, scene.storage.row.get().get("dispatch_epoch")); assertEquals(0, scene.mcp.evaluations.get()); assertEquals(AgentBudgetReservation.State.SETTLED, scene.storage.budgetRows.get(scene.request.reservationId()).state());
        }
    }

    @Test void mcp_known_replay_rechecks_requester_access_and_unknown_replay_never_resends_or_recharges() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            var first = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request); var replay = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            assertEquals(first.outcome(), replay.outcome()); assertFalse(replay.operationInvoked()); assertEquals(1, scene.mcp.evaluations.get()); assertEquals(2, scene.dispatchChecks.get());
            scene.check.set(request -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.DENY, null));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request)); assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(1, scene.mcp.evaluations.get());
        }
        try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.operationFailure.set(ReadOnlyMcpToolTransport.FailureKind.CANCELLED); scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request);
            var replay = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request); assertEquals("UNKNOWN", replay.state()); assertNull(replay.outcome()); assertFalse(replay.operationInvoked()); assertEquals(1, scene.mcp.calls.get()); assertEquals(1, scene.mcp.evaluations.get());
            verify(scene.storage.budgets).settleBudget(any(), any(), anyString(), any());
        }
    }

    @Test void mcp_outer_suppression_and_late_cancellation_preserve_a_committed_fact_before_accounting() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.afterFailure.set(ReadOnlyMcpToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE);
            var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request)); assertEquals(ReadOnlyMcpToolTransport.FailureKind.CREDENTIAL_UNAVAILABLE, failure.kind());
            assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(Map.of("value", PRIVATE_OUTPUT), new ToolExecutionJsonCodec().outcome((String) scene.storage.row.get().get("outcome"), MCP_TOOL).output());
        }
        try (var scene = new Scene(MCP_TOOL)) {
            scene.mcp.beforeOutcome.set(() -> { scene.storage.sourceActive.set(false); scene.storage.wall.set(NOW.plusSeconds(70)); scene.storage.databaseNow.set(NOW.plusSeconds(70)); scene.storage.nano.set(70_000_000_000L); when(scene.storage.budgetRepository.lockLeaseForSettlement(any(), any())).thenReturn(false); Thread.currentThread().interrupt(); });
            try {
                var failure = assertThrows(ReadOnlyMcpToolTransport.Failure.class, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request)); assertEquals(ReadOnlyMcpToolTransport.FailureKind.CANCELLED, failure.kind()); assertTrue(Thread.currentThread().isInterrupted());
                assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertFalse(scene.completionSawInterrupt.get()); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, ((ToolInvocationRecorder.RecordResult) scene.mcp.callbackResult.get()).accountingState());
                assertTrue(scene.trace.indexOf("complete-ack") < scene.trace.indexOf("accounting")); assertEquals(1, scene.mcp.evaluations.get());
            } finally { Thread.interrupted(); }
        }
    }

    @Test void mcp_missing_completion_ack_stays_dispatching_and_accounting_failure_keeps_a_known_fact() throws Exception {
        try (var scene = new Scene(MCP_TOOL)) {
            scene.failCompletion.set(true); assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request));
            assertEquals("DISPATCHING", scene.storage.row.get().get("state")); scene.failCompletion.set(false);
            var replay = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request); assertNull(replay.outcome()); assertFalse(replay.operationInvoked()); assertEquals(1, scene.mcp.evaluations.get()); verify(scene.storage.budgets, never()).settleBudget(any(), any(), anyString(), any());
        }
        try (var scene = new Scene(MCP_TOOL)) {
            when(scene.storage.budgetRepository.lockLeaseForSettlement(any(), any())).thenReturn(false);
            var result = scene.recorder.executeReadOnlyMcp(scene.mcp, scene.request); assertEquals("SUCCEEDED", result.state()); assertEquals("SUCCEEDED", scene.storage.row.get().get("state")); assertEquals(ToolInvocationRecorder.AccountingState.PENDING, result.accountingState());
        }
    }

    private static final class Scene implements AutoCloseable {
        final ToolInvocationStoreTest.Fixture storage;
        final ToolInvocationStore preparations;
        final PreparationRequestVO request;
        final ToolInvocationRecorder recorder;
        final ProbeTransport transport;
        final McpProbeTransport mcp;
        final AtomicReference<Instant> dispatchDeadline = new AtomicReference<>(NOW.plusSeconds(15));
        final List<String> trace = new ArrayList<>();
        final AtomicInteger dispatchChecks = new AtomicInteger();
        final AtomicReference<Function<ToolExecutionAuthorization.Request, ToolExecutionAuthorization.CheckResult>> check = new AtomicReference<>();
        final AtomicReference<Runnable> beforeCheck = new AtomicReference<>(() -> { }), afterBegin = new AtomicReference<>(() -> { });
        final AtomicReference<DispatchAttemptVO> attempt = new AtomicReference<>();
        final AtomicReference<String> nonce = new AtomicReference<>();
        final AtomicBoolean completionSawInterrupt = new AtomicBoolean(), failCompletion = new AtomicBoolean();

        Scene() throws Exception { this(true); }
        Scene(boolean ready) throws Exception { this(TOOL, ready); }
        Scene(ToolDescriptor tool) throws Exception { this(tool, true); }
        Scene(ToolDescriptor tool, boolean ready) throws Exception {
            storage = new ToolInvocationStoreTest.Fixture(tool);
            request = storage.recorderRequest(INVOCATION);
            preparations = spy(storage.store(true, storage.authority));
            check.set(value -> new ToolExecutionAuthorization.CheckResult(value, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.ALLOW,
                    new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.ALLOW,
                            value.run().actor().subjectType(), storage.evidence.get().grantId(), 5L, null, 7L, storage.wall.get(), storage.evidence.get().expiresAt())));
            when(storage.authority.check(any(), eq(ToolExecutionAuthorization.Boundary.DISPATCH))).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); var value = (ToolExecutionAuthorization.Request) call.getArgument(0);
                assertEquals(request.binding().run().actor(), value.run().actor()); assertEquals(dispatchDeadline.get(), value.deadlineAt()); assertNull(value.actionIntentId());
                assertEquals(request.parametersSha256(), value.parametersSha256()); dispatchChecks.incrementAndGet(); beforeCheck.get().run(); return check.get().apply(value);
            });
            doAnswer(call -> {
                var value = (DispatchBeginResultVO) call.callRealMethod();
                if (value.started()) { trace.add("begin-ack"); afterBegin.get().run(); }
                return value;
            }).when(preparations).beginReadOnlyDispatch(any(), any(), any(), any(), any(), anyString(), any());
            doAnswer(call -> {
                completionSawInterrupt.set(Thread.currentThread().isInterrupted()); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                attempt.set(call.getArgument(1)); nonce.set(call.getArgument(2));
                if (failCompletion.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private failed commit");
                var value = call.callRealMethod(); trace.add("complete-ack"); return value;
            }).when(preparations).completeReadOnly(any(), any(), anyString(), any());
            doAnswer(call -> {
                trace.add("accounting"); assertTrue(trace.contains("complete-ack") || !"DISPATCHING".equals(storage.row.get().get("state")));
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return call.callRealMethod();
            }).when(storage.budgets).settleBudget(any(), any(), anyString(), any());
            recorder = new ToolInvocationRecorder(storage.source, storage.ledger, preparations, storage.capabilities, storage.authority, storage.clock(), storage.nano::get);
            if (ready) recorder.verifyStorage();
            transport = new ProbeTransport(this); mcp = new McpProbeTransport(this);
        }
        @Override public void close() { storage.close(); }
    }

    private static final class ProbeTransport implements ReadOnlyDecisionToolTransport {
        final Scene scene;
        final AtomicInteger calls = new AtomicInteger(), evaluations = new AtomicInteger();
        final AtomicReference<Request> request = new AtomicReference<>();
        final AtomicReference<Object> callbackResult = new AtomicReference<>();
        final AtomicReference<FailureKind> beforeFailure = new AtomicReference<>(), operationFailure = new AtomicReference<>(), afterFailure = new AtomicReference<>();
        final AtomicBoolean privateRuntime = new AtomicBoolean(), nullOperation = new AtomicBoolean();
        final AtomicReference<Runnable> beforeOutcome = new AtomicReference<>(() -> { });
        final AtomicReference<DecisionObservation> observation = new AtomicReference<>(new DecisionObservation(Status.MATCHED,
                "30000000-0000-0000-0000-000000000001", NOW, NOW, Map.of("value", PRIVATE_OUTPUT), "row:1"));

        ProbeTransport(Scene scene) { this.scene = scene; }
        @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
            calls.incrementAndGet(); this.request.set(request); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (beforeFailure.get() != null) throw new Failure(beforeFailure.get());
            if (!request.deadlineAt().isAfter(scene.storage.wall.get()) || !EXECUTOR.expiresAt().isAfter(scene.storage.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            T result = callback.apply(EXECUTOR, nullOperation.get() ? null : () -> {
                evaluations.incrementAndGet(); scene.trace.add("operation"); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("DISPATCHING", scene.storage.row.get().get("state")); assertTrue(scene.trace.contains("begin-ack")); assertTrue(scene.storage.trace.contains("commit"));
                beforeOutcome.get().run();
                if (operationFailure.get() != null) throw new Failure(operationFailure.get());
                if (privateRuntime.get()) throw new IllegalStateException("private operation " + PRIVATE_OUTPUT);
                return observation.get();
            });
            callbackResult.set(result);
            if (afterFailure.get() != null) throw new Failure(afterFailure.get());
            if (Thread.currentThread().isInterrupted()) throw new Failure(FailureKind.CANCELLED);
            if (!request.deadlineAt().isAfter(scene.storage.wall.get()) || !EXECUTOR.expiresAt().isAfter(scene.storage.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            return result;
        }
    }

    private static final class McpProbeTransport implements ReadOnlyMcpToolTransport {
        final Scene scene;
        final AtomicInteger calls = new AtomicInteger(), evaluations = new AtomicInteger();
        final AtomicReference<Request> request = new AtomicReference<>();
        final AtomicReference<Object> callbackResult = new AtomicReference<>();
        final AtomicReference<FailureKind> beforeFailure = new AtomicReference<>(), operationFailure = new AtomicReference<>(), afterFailure = new AtomicReference<>();
        final AtomicReference<ReadOnlyDecisionToolTransport.VerifiedExecutor> executor = new AtomicReference<>(MCP_EXECUTOR);
        final AtomicReference<Instant> callDeadline = new AtomicReference<>();
        final AtomicBoolean privateRuntime = new AtomicBoolean(), nullOperation = new AtomicBoolean();
        final AtomicReference<Runnable> beforeOutcome = new AtomicReference<>(() -> { });
        final AtomicReference<CallObservation> observation = new AtomicReference<>(new CallObservation(Status.SUCCESS, Map.of("value", PRIVATE_OUTPUT)));
        McpProbeTransport(Scene scene) { this.scene = scene; }
        @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
            calls.incrementAndGet(); this.request.set(request); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (beforeFailure.get() != null) throw new Failure(beforeFailure.get());
            if (!request.deadlineAt().isAfter(scene.storage.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            OneShotCall operation = deadline -> {
                evaluations.incrementAndGet(); callDeadline.set(deadline); scene.trace.add("mcp-operation"); assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertNotNull(deadline); assertEquals(0, deadline.getNano() % 1_000); assertFalse(deadline.isAfter(executor.get().expiresAt())); assertFalse(deadline.isAfter(request.deadlineAt()));
                assertEquals("DISPATCHING", scene.storage.row.get().get("state")); assertTrue(scene.trace.contains("begin-ack")); assertTrue(scene.storage.trace.contains("commit"));
                beforeOutcome.get().run(); if (operationFailure.get() != null) throw new Failure(operationFailure.get());
                if (privateRuntime.get()) throw new IllegalStateException("private MCP operation " + PRIVATE_OUTPUT);
                return observation.get();
            };
            T result = callback.apply(executor.get(), nullOperation.get() ? null : operation); callbackResult.set(result);
            if (afterFailure.get() != null) throw new Failure(afterFailure.get());
            if (Thread.currentThread().isInterrupted()) throw new Failure(FailureKind.CANCELLED);
            if (!request.deadlineAt().isAfter(scene.storage.wall.get()) || !executor.get().expiresAt().isAfter(scene.storage.wall.get())) throw new Failure(FailureKind.DEADLINE_EXCEEDED);
            return result;
        }
    }

    private static AgentLedgerException assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(AgentLedgerException.class, action); assertEquals(code, failure.code()); return failure;
    }
}
