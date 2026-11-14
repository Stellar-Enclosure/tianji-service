package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Direct participant and closed-row checks; controlled JDBC is not PostgreSQL uniqueness or lock acceptance. */
class ToolActionApprovalSqlTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z"), CREATED = NOW.minusSeconds(60), UPDATED = NOW.minusSeconds(5);
    private static final String ISSUER = "https://idp.example/20", RUN = "10000000-0000-0000-0000-000000000001", DISPATCH_RUN = "10000000-0000-0000-0000-000000000002", HASH = "a".repeat(64);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER),
            CONSUMER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "approval-consumer", ISSUER),
            EXECUTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-executor", "https://service.example/20");
    private static final ToolDescriptor TOOL = descriptor(true);
    private static final ToolActionRequest ACTION = action(TOOL, 20);
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final ToolActionIntentSql.DispatchVO DISPATCH = new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 1, "d".repeat(64), EXECUTOR);

    @Test void approval_participants_require_the_exact_owning_transaction_before_storage_access() throws Exception {
        try (var fixture = new Fixture()) {
            assertUnavailable(() -> fixture.sql.lockForApproval(ACTION, TOOL, NOW.plusSeconds(10)));
            assertUnavailable(() -> fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)));
            assertUnavailable(() -> fixture.sql.completeApprovedWrite(dispatched("DISPATCHING", 8, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10)));
            var foreign = mock(DataSource.class); TransactionSynchronizationManager.setActualTransactionActive(true);
            TransactionSynchronizationManager.bindResource(foreign, fixture.connection);
            try { assertUnavailable(() -> fixture.sql.lockForApproval(ACTION, TOOL, NOW.plusSeconds(10))); }
            finally { TransactionSynchronizationManager.unbindResource(foreign); TransactionSynchronizationManager.setActualTransactionActive(false); }
            assertTrue(fixture.queries.isEmpty()); assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void approval_lock_uses_exact_owner_then_fresh_join_and_keeps_expired_waiting_history_readable() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.stored.put("expires_at", NOW.minusSeconds(1));
            var row = fixture.inTransaction(() -> fixture.sql.lockForApproval(ACTION, TOOL, NOW.plusSeconds(10)));
            assertEquals("AWAITING_APPROVAL", row.state()); assertNull(row.approval()); assertNull(row.dispatch()); assertEquals(NOW.minusSeconds(1), row.expiresAt());
            assertEquals(2, fixture.queries.size()); assertTrue(fixture.queries.getFirst().sql().contains("FOR UPDATE"));
            assertTrue(fixture.queries.get(1).sql().contains("LEFT JOIN tianji_agent.agent_tool_invocation i"));
            for (var query : fixture.queries) assertEquals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId())), Arrays.asList(query.arguments()));
            verify(fixture.jdbc.get()).setQueryTimeout(5); assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void lock_rejects_non_aa_actions_identity_drift_and_expired_technical_windows_after_waiting() throws Exception {
        try (var fixture = new Fixture()) {
            var ordinary = descriptor(false);
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.sql.lockForApproval(action(ordinary, 20), ordinary, NOW.plusSeconds(10))));
            var changed = action(TOOL, 21);
            var conflict = assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> fixture.sql.lockForApproval(changed, TOOL, NOW.plusSeconds(10))));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, conflict.code()); assertNull(conflict.getCause());
            fixture.queries.clear(); fixture.afterLock = () -> fixture.databaseNow.set(NOW.plusSeconds(11));
            assertUnavailable(() -> fixture.inTransaction(() -> fixture.sql.lockForApproval(ACTION, TOOL, NOW.plusSeconds(10))));
            assertEquals(1, fixture.queries.size()); assertFalse(fixture.queries.getFirst().sql().contains("LEFT JOIN")); assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void consuming_keeps_original_nanosecond_proof_and_sha_but_indexes_only_conservative_microseconds() throws Exception {
        try (var fixture = new Fixture()) {
            var approval = approval(); var proof = proof(approval); var codec = new ToolExecutionJsonCodec();
            fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval, DISPATCH, NOW, NOW.plusSeconds(10)); return null; });
            assertEquals(1, fixture.updates.size()); var write = fixture.updates.getFirst();
            String json = Arrays.stream(write.arguments()).filter(value -> value instanceof String text && text.startsWith("{") && text.contains("approvalReceiptId")).map(Object::toString).findFirst().orElseThrow();
            assertEquals(codec.approval(proof, ACTION), json); assertEquals(proof, codec.approval(json, codec.approvalSha256(proof, ACTION), ACTION));
            assertEquals(NOW.plusSeconds(20).plusNanos(123_456_789), codec.approval(json, codec.approvalSha256(proof, ACTION), ACTION).approval().expiresAt());
            assertArguments(write, approval.sourceNamespaceSha256(), approval.approval().approvalReceiptId(), codec.approvalSha256(proof, ACTION), Timestamp.from(NOW), Timestamp.from(approval.approval().expiresAt().truncatedTo(ChronoUnit.MICROS)));
            assertFalse(Arrays.asList(write.arguments()).contains(Timestamp.from(approval.approval().expiresAt())));
            assertNotEquals(CONSUMER, EXECUTOR); assertArguments(write, EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
            assertTrue(fixture.queries.isEmpty()); verifyNoInteractions(fixture.ledger.budgets());
        }
    }

    @Test void consume_is_one_full_identity_compare_and_set_of_all_approval_columns_and_the_dispatch_link() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; });
            var write = fixture.updates.getFirst(); String set = write.sql().substring(0, write.sql().indexOf("WHERE"));
            for (String field : List.of("approval_source_namespace_sha256", "approval_receipt_id", "approval_proof_sha256", "approval_proof", "approval_approved", "approval_consumed_at", "approval_expires_at",
                    "dispatch_epoch", "dispatch_nonce_sha256", "dispatch_run_id", "dispatch_invocation_id", "executor_issuer", "executor_id")) assertTrue(set.contains(field), field);
            assertTrue(set.contains("state='DISPATCHING'")); assertTrue(set.contains("revision=revision+1")); assertTrue(set.contains("side_effect_outcome='UNKNOWN'"));
            assertPredicates(write.sql(), List.of("tenant_id=?", "owner_type=?", "owner_issuer=?", "owner_id=?", "intent_id=?", "action_key=?", "business_key=?", "business_version=?", "request_sha256=?", "logical_identity_sha256=?",
                    "tool_key=?", "tool_version=?", "tool_sha256=?", "binding_sha256=?", "risk='APPROVAL_REQUIRED'", "approval_policy_ref=?", "state='AWAITING_APPROVAL'", "revision=?", "dispatch_epoch=0",
                    "approval_source_namespace_sha256 IS NULL", "approval_receipt_id IS NULL", "approval_proof_sha256 IS NULL", "approval_proof IS NULL", "approval_approved IS NULL", "approval_consumed_at IS NULL", "approval_expires_at IS NULL",
                    "dispatch_nonce_sha256 IS NULL", "dispatch_run_id IS NULL", "dispatch_invocation_id IS NULL", "executor_issuer IS NULL", "executor_id IS NULL", "origin_run_id=?", "origin_snapshot_sha256=?", "created_at=?", "updated_at=?", "expires_at=?"));
            assertArguments(write, OWNER.tenantId(), OWNER.subjectType().name(), OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId()), ACTION.requestSha256(), ACTION.businessVersion(),
                    TOOL.key(), TOOL.version(), TOOL.reference().contentSha256(), TOOL.policy().bindingSha256(), TOOL.policy().approvalPolicyRef(), 7L, RUN, HASH, Timestamp.from(CREATED), Timestamp.from(UPDATED), Timestamp.from(NOW.plusSeconds(30)));
            assertTrue(fixture.queries.isEmpty(), "The action mutator must not inspect an intermediate invocation/action join");
        }
    }

    @Test void runtime_approval_cas_consumes_the_same_proof_and_links_only_the_technical_call() throws Exception {
        try (var fixture = new Fixture()) {
            var legacy = waiting(7, NOW.plusSeconds(30));
            var runtime = new ToolActionIntentSql.Row(ACTION, TOOL, legacy.origin(), ToolExecutionJsonCodec.ActionProfile.RUNTIME_V2,
                    legacy.state(), legacy.revision(), legacy.createdAt(), legacy.updatedAt(), legacy.expiresAt(),
                    null, null, "NONE", null, null, null);
            var call = UUID.fromString("20000000-0000-0000-0000-000000000003");
            var dispatch = new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, null, call, 1, DISPATCH.nonceSha256(), EXECUTOR);
            var observed = approval();
            fixture.inTransaction(() -> {
                fixture.sql.beginApprovedWrite(runtime, observed, dispatch, NOW, NOW.plusSeconds(10));
                return null;
            });
            assertEquals(1, fixture.updates.size());
            var update = fixture.updates.getFirst();
            var set = update.sql().substring(0, update.sql().indexOf("WHERE"));
            assertTrue(set.contains("dispatch_call_id=?"));
            assertFalse(set.contains("dispatch_invocation_id=?"));
            assertPredicates(update.sql(), List.of("action_profile='RUNTIME_V2'", "state='AWAITING_APPROVAL'", "revision=?",
                    "dispatch_invocation_id IS NULL", "dispatch_call_id IS NULL", "approval_receipt_id IS NULL", "approval_proof IS NULL",
                    "origin_run_id=?", "origin_snapshot_sha256=?", "logical_identity_sha256=?"));
            assertArguments(update, observed.sourceNamespaceSha256(), observed.approval().approvalReceiptId(),
                    new ToolExecutionJsonCodec().approvalSha256(proof(observed), ACTION), call, runtime.revision(),
                    OWNER.issuerFingerprint(), ACTION.requestSha256(), DISPATCH.nonceSha256(), EXECUTOR.issuerFingerprint());
        }
    }

    @Test void only_an_empty_waiting_action_and_first_same_tenant_dispatch_generation_can_consume() throws Exception {
        try (var fixture = new Fixture()) {
            for (String state : List.of("PREPARED", "DISPATCHING", "UNKNOWN", "APPLIED")) assertThrows(IllegalArgumentException.class,
                    () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(dispatched(state, 8, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            var foreign = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, EXECUTOR.subjectId(), "https://service.example/21");
            for (var dispatch : List.of(new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 2, DISPATCH.nonceSha256(), EXECUTOR), new ToolActionIntentSql.DispatchVO(DISPATCH_RUN, INVOCATION, 1, DISPATCH.nonceSha256(), foreign))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), dispatch, NOW, NOW.plusSeconds(10)); return null; }));
            }
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(Long.MAX_VALUE, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void proof_must_match_the_complete_action_and_be_current_at_the_actual_database_consumption_time() throws Exception {
        for (var observation : List.of(approval(action(TOOL, 21), NOW.minusSeconds(1), NOW.plusSeconds(20), NOW.plusSeconds(30)),
                approval(ACTION, NOW.plusNanos(1_000), NOW.plusSeconds(20), NOW.plusSeconds(30)),
                approval(ACTION, NOW.minusSeconds(1), NOW.minusNanos(1), NOW.plusSeconds(30)),
                approval(ACTION, NOW.minusSeconds(1), NOW.plusSeconds(20), NOW.minusNanos(1)))) {
            try (var fixture = new Fixture()) {
                assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), observation, DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
                assertTrue(fixture.updates.isEmpty());
            }
        }
        try (var fixture = new Fixture()) {
            var subMicro = approval(ACTION, NOW.minusSeconds(1), NOW.plusNanos(999), NOW.plusSeconds(30));
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), subMicro, DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void mutator_timestamps_are_microsecond_exact_monotonic_nonfuture_and_consume_cannot_extend_first_expiry() throws Exception {
        try (var fixture = new Fixture()) {
            for (var time : List.of(NOW.plusNanos(1_000), NOW.minusNanos(1), UPDATED.minusSeconds(1))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), DISPATCH, time, NOW.plusSeconds(10)); return null; }));
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(dispatched("DISPATCHING", 8, NOW.plusSeconds(30)), unknown(null), time, NOW.plusSeconds(10)); return null; }));
            }
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            fixture.primary = false;
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            fixture.primary = true;
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(dispatched("DISPATCHING", 8, NOW.plusSeconds(30)), unknown(null), NOW, NOW); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void full_history_reads_keep_original_nanosecond_proof_after_expiry_and_compare_only_the_index_floor() throws Exception {
        try (var fixture = new Fixture()) {
            var observation = historicalApproval(); var consumed = NOW.minusSeconds(30);
            fixture.seed(dispatched("DISPATCHING", 8, NOW.minusSeconds(1)), observation, consumed);
            var originalJson = fixture.stored.get("approval_proof"); var originalSha = fixture.stored.get("approval_proof_sha256");
            var found = fixture.inTransaction(() -> fixture.sql.historical(ACTION, TOOL, NOW.plusSeconds(10))).orElseThrow();
            assertEquals(proof(observation), found.approval().proof()); assertEquals(originalSha, found.approval().proofSha256()); assertEquals(consumed, found.approval().consumedAt());
            assertEquals(originalJson, fixture.stored.get("approval_proof")); assertEquals(observation.approval().expiresAt().truncatedTo(ChronoUnit.MICROS), fixture.stored.get("approval_expires_at"));
            assertTrue(found.approval().proof().approval().expiresAt().isBefore(NOW)); assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void legacy_microsecond_history_still_reads_and_a_floor_at_consumption_is_rejected() throws Exception {
        try (var fixture = new Fixture()) {
            var observation = approval(ACTION, NOW.minusSeconds(31), NOW.minusSeconds(11), NOW.minusSeconds(10));
            fixture.seed(dispatched("DISPATCHING", 8, NOW.minusSeconds(1)), observation, NOW.minusSeconds(30));
            assertEquals(proof(observation), fixture.inTransaction(() -> fixture.sql.historical(ACTION, TOOL, NOW.plusSeconds(10))).orElseThrow().approval().proof());
            var subMicro = approval(ACTION, NOW.minusSeconds(31), NOW.minusSeconds(30).plusNanos(999), NOW.minusSeconds(10));
            fixture.seed(dispatched("DISPATCHING", 8, NOW.minusSeconds(1)), subMicro, NOW.minusSeconds(30));
            assertUnavailable(() -> fixture.inTransaction(() -> fixture.sql.historical(ACTION, TOOL, NOW.plusSeconds(10))));
        }
    }

    @Test void late_completion_preserves_all_consumed_approval_fields_and_actual_cost_without_authorizing_or_settling() throws Exception {
        var outcomes = List.of(new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "process-1"), "receipt-1", null, new BigDecimal("12.12500000")),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "BUSINESS_REJECTED", null),
                new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of(), null, "KNOWN_NO_EFFECT", new BigDecimal("0.5000")), unknown(null));
        for (var outcome : outcomes) try (var fixture = new Fixture()) {
            var expected = dispatched("UNKNOWN", 9, NOW.minusSeconds(1));
            fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(expected, outcome, NOW, NOW.plusSeconds(10)); return null; });
            assertEquals(1, fixture.updates.size()); var write = fixture.updates.getFirst(); String set = write.sql().substring(0, write.sql().indexOf("WHERE"));
            assertFalse(set.contains("approval_")); assertFalse(write.sql().contains("expires_at>")); assertFalse(write.sql().contains("lease_expires_at"));
            assertPredicates(write.sql(), List.of("risk='APPROVAL_REQUIRED'", "approval_policy_ref=?", "approval_source_namespace_sha256=?", "approval_receipt_id=?", "approval_proof_sha256=?", "approval_approved IS TRUE", "approval_consumed_at=?", "approval_expires_at=?",
                    "tenant_id=?", "owner_type=?", "owner_issuer=?", "owner_id=?", "intent_id=?", "request_sha256=?", "state=?", "revision=?", "dispatch_epoch=?", "dispatch_nonce_sha256=?", "dispatch_run_id=?", "dispatch_invocation_id=?", "executor_issuer=?", "executor_id=?"));
            String state = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : outcome.status().name();
            String effect = outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED ? "CONFIRMED" : outcome.status() == ToolExecutionOutcome.Status.UNKNOWN ? "UNKNOWN" : "NONE";
            assertArguments(write, state, effect, expected.approval().proof().source().namespaceSha256(), expected.approval().proof().approval().approvalReceiptId(), expected.approval().proofSha256(), Timestamp.from(expected.approval().consumedAt()),
                    Timestamp.from(expected.approval().proof().approval().expiresAt().truncatedTo(ChronoUnit.MICROS)), DISPATCH.nonceSha256(), DISPATCH.runId(), DISPATCH.invocationId(), EXECUTOR.issuerFingerprint(), EXECUTOR.subjectId());
            String json = Arrays.stream(write.arguments()).filter(value -> value instanceof String text && text.startsWith("{") && text.contains("actualCost")).map(Object::toString).findFirst().orElseThrow();
            assertEquals(outcome, new ToolExecutionJsonCodec().outcome(json, TOOL)); assertTrue(fixture.queries.isEmpty());
            verify(fixture.ledger, never()).executionMaterial(any(), any()); verifyNoInteractions(fixture.ledger.budgets());
        }
    }

    @Test void known_or_waiting_actions_and_revision_overflow_cannot_be_rewritten_by_the_completion_participant() throws Exception {
        try (var fixture = new Fixture()) {
            for (var expected : List.of(waiting(7, NOW.plusSeconds(30)), dispatched("PREPARED", 8, NOW.plusSeconds(30)), dispatched("APPLIED", 9, NOW.plusSeconds(30)))) {
                assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(expected, unknown(null), NOW, NOW.plusSeconds(10)); return null; }));
            }
            assertUnavailable(() -> fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(dispatched("DISPATCHING", Long.MAX_VALUE, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10)); return null; }));
            assertTrue(fixture.updates.isEmpty());
        }
    }

    @Test void duplicate_receipt_or_zero_cas_is_a_sanitized_conflict_and_storage_failure_remains_unavailable() throws Exception {
        for (String failure : List.of("duplicate", "cas", "storage")) try (var fixture = new Fixture()) {
            if (failure.equals("duplicate")) fixture.updateFailure = new DuplicateKeyException("private receipt/source");
            if (failure.equals("cas")) fixture.updateCount = 0;
            if (failure.equals("storage")) fixture.updateFailure = new DataAccessResourceFailureException("private JDBC");
            var rejected = assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> { fixture.sql.beginApprovedWrite(waiting(7, NOW.plusSeconds(30)), approval(), DISPATCH, NOW, NOW.plusSeconds(10)); return null; }));
            assertEquals(failure.equals("storage") ? AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE : AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, rejected.code());
            assertNull(rejected.getCause()); assertFalse(rejected.getMessage().contains("private")); verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit(); assertTrue(fixture.queries.isEmpty());
        }
        try (var fixture = new Fixture()) {
            fixture.updateCount = 0;
            var rejected = assertThrows(AgentLedgerException.class, () -> fixture.inTransaction(() -> { fixture.sql.completeApprovedWrite(dispatched("DISPATCHING", 8, NOW.plusSeconds(30)), unknown(null), NOW, NOW.plusSeconds(10)); return null; }));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, rejected.code()); assertNull(rejected.getCause()); verify(fixture.connection).rollback();
        }
    }

    private static void assertPredicates(String sql, List<String> predicates) {
        String where = sql.substring(sql.indexOf("WHERE")); for (String predicate : predicates) assertTrue(where.contains(predicate), "Missing guarded predicate: " + predicate);
    }
    private static void assertArguments(StatementVO write, Object... expected) {
        var actual = Arrays.asList(write.arguments()); for (var value : expected) assertTrue(actual.contains(value), "Missing bounded approval argument");
    }
    private static void assertUnavailable(org.junit.jupiter.api.function.Executable work) {
        var rejected = assertThrows(AgentLedgerException.class, work); assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, rejected.code()); assertNull(rejected.getCause());
    }
    private static ToolExecutionOutcome unknown(BigDecimal cost) { return new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", cost); }
    private static ToolDescriptor descriptor(boolean approved) {
        return new ToolDescriptor(approved ? "process.approvedStart" : "process.start", 1, approved ? "approved_start" : "start_process", "Pinned write",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(approved ? ToolPolicy.Risk.APPROVAL_REQUIRED : ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, approved ? "approval-v1" : null));
    }
    private static ToolActionRequest action(ToolDescriptor descriptor, long amount) { return new ToolActionRequest(OWNER, descriptor, "private-invoice-1", "business-v1", Map.of("amount", amount)); }
    private static ApprovedObservation approval() { return approval(ACTION, NOW.minusSeconds(1), NOW.plusSeconds(20).plusNanos(123_456_789), NOW.plusSeconds(30)); }
    private static ApprovedObservation historicalApproval() { return approval(ACTION, NOW.minusSeconds(31), NOW.minusSeconds(11).plusNanos(123_456_789), NOW.minusSeconds(10)); }
    private static ApprovedObservation approval(ToolActionRequest action, Instant verified, Instant expires, Instant consumerExpires) {
        var binding = new ToolApprovalBinding(CONSUMER, "approval-v1", "b".repeat(64), "approval-release", "approveInvoice", "c".repeat(64), "review");
        var query = binding.query(action, "approval-receipt-1"); var source = query.sourceStart();
        var dto = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), source.commandId(),
                "approval-instance", "approval-task", binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(), OrchestrationRequestDigests.start(source), true, verified.minusSeconds(1), verified, expires);
        return new ApprovedObservation("process-primary", ISSUER, "https://process.example/api", binding, dto, CONSUMER, consumerExpires);
    }
    private static ToolExecutionJsonCodec.ApprovalProofVO proof(ApprovedObservation value) {
        return new ToolExecutionJsonCodec.ApprovalProofVO(1, new ToolExecutionJsonCodec.ApprovalSourceVO(value.sourceId(), value.tenantId(), value.sourceIssuer(), value.sourceResource()), value.binding(), value.approval());
    }
    private static ToolActionIntentSql.Row waiting(long revision, Instant expiry) { return new ToolActionIntentSql.Row(ACTION, TOOL, RUN, HASH, "AWAITING_APPROVAL", revision, CREATED, UPDATED, expiry, null, null, "NONE", null, null, null); }
    private static ToolActionIntentSql.Row dispatched(String state, long revision, Instant expiry) {
        var approval = historicalApproval(); var codec = new ToolExecutionJsonCodec(); var proof = proof(approval);
        var consumed = new ToolActionIntentSql.ApprovalConsumptionVO(proof, codec.approvalSha256(proof, ACTION), NOW.minusSeconds(30));
        var outcome = state.equals("APPLIED") ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "process-1"), "receipt-1", null, BigDecimal.ZERO) : state.equals("UNKNOWN") ? unknown(null) : null;
        boolean dispatched = !state.equals("PREPARED");
        return new ToolActionIntentSql.Row(ACTION, TOOL, RUN, HASH, state, revision, CREATED, UPDATED, expiry, consumed, dispatched ? DISPATCH : null,
                state.equals("APPLIED") ? "CONFIRMED" : dispatched ? "UNKNOWN" : "NONE", outcome, outcome == null ? null : outcome.errorCode(), outcome == null ? null : UPDATED);
    }

    private record StatementVO(String sql, Object[] arguments) { }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final List<StatementVO> queries = new ArrayList<>(), updates = new ArrayList<>();
        final Map<String, Object> stored = new HashMap<>();
        final MockedConstruction<JdbcTemplate> templates;
        final ToolActionIntentSql sql;
        Runnable afterLock = () -> { };
        boolean primary = true;
        int updateCount = 1;
        RuntimeException updateFailure;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            when(ledger.usesDataSource(source)).thenReturn(true); when(ledger.budgets()).thenReturn(mock(AgentBudgetService.class));
            when(ledger.executionMaterial(eq(OWNER), any())).thenAnswer(call -> material(((GetAgentRunQuery) call.getArgument(1)).runId()));
            seed(waiting(7, NOW.plusSeconds(30)), null, null);
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenAnswer(call -> primary);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(databaseNow.get()));
                doAnswer(call -> {
                    String query = call.getArgument(0); Object[] arguments = (Object[]) call.getRawArguments()[2]; queries.add(new StatementVO(query, arguments.clone())); var mapper = (RowMapper<?>) call.getArgument(1);
                    if (!Arrays.asList(arguments).equals(List.of(20L, "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), UUID.fromString(ACTION.intentId())))) return List.of();
                    if (query.startsWith("SELECT intent_id FROM tianji_agent.agent_tool_action_intent")) { afterLock.run(); return List.of(mapper.mapRow(result(Map.of("intent_id", UUID.fromString(ACTION.intentId()))), 0)); }
                    if (query.contains("FROM tianji_agent.agent_tool_action_intent a")) return List.of(mapper.mapRow(result(stored), 0));
                    throw new AssertionError("Unexpected approval participant query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> { updates.add(new StatementVO(call.getArgument(0), ((Object[]) call.getRawArguments()[1]).clone())); if (updateFailure != null) throw updateFailure; return updateCount; })
                        .when(template).update(anyString(), any(Object[].class));
            });
            sql = new ToolActionIntentSql(source, ledger);
        }
        <T> T inTransaction(Supplier<T> work) { return new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> work.get()); }
        void seed(ToolActionIntentSql.Row row, ApprovedObservation approval, Instant consumed) {
            stored.clear(); var codec = new ToolExecutionJsonCodec();
            stored.put("tenant_id", OWNER.tenantId()); stored.put("intent_id", UUID.fromString(ACTION.intentId())); stored.put("owner_type", "USER"); stored.put("owner_issuer", OWNER.issuerFingerprint()); stored.put("owner_id", OWNER.subjectId());
            stored.put("action_key", ACTION.actionKey()); stored.put("business_key", ACTION.businessKey()); stored.put("business_version", ACTION.businessVersion()); stored.put("tool_key", TOOL.key()); stored.put("tool_version", TOOL.version());
            stored.put("tool_sha256", TOOL.reference().contentSha256()); stored.put("binding_sha256", TOOL.policy().bindingSha256()); stored.put("request_sha256", ACTION.requestSha256());
            stored.put("logical_identity_sha256", OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1", OWNER.identityFingerprint(), ACTION.actionKey(), ACTION.businessKey(), ACTION.businessVersion()), Map.of()));
            stored.put("descriptor", codec.descriptor(TOOL)); stored.put("arguments", codec.arguments(TOOL, ACTION.arguments())); stored.put("origin_run_id", RUN); stored.put("origin_snapshot_sha256", HASH);
            stored.put("risk", "APPROVAL_REQUIRED"); stored.put("approval_policy_ref", TOOL.policy().approvalPolicyRef()); stored.put("state", row.state()); stored.put("revision", row.revision());
            stored.put("created_at", row.createdAt()); stored.put("updated_at", row.updatedAt()); stored.put("expires_at", row.expiresAt()); stored.put("side_effect_outcome", row.sideEffectOutcome()); stored.put("closed_at", row.closedAt());
            if (approval != null) {
                var proof = proof(approval); stored.put("approval_source_namespace_sha256", approval.sourceNamespaceSha256()); stored.put("approval_receipt_id", approval.approval().approvalReceiptId());
                stored.put("approval_proof_sha256", codec.approvalSha256(proof, ACTION)); stored.put("approval_proof", codec.approval(proof, ACTION)); stored.put("approval_approved", true);
                stored.put("approval_consumed_at", consumed); stored.put("approval_expires_at", approval.approval().expiresAt().truncatedTo(ChronoUnit.MICROS));
            }
            stored.put("dispatch_epoch", row.dispatch() == null ? 0L : row.dispatch().epoch());
            if (row.dispatch() != null) {
                stored.put("dispatch_run_id", DISPATCH.runId()); stored.put("dispatch_invocation_id", DISPATCH.invocationId()); stored.put("dispatch_nonce_sha256", DISPATCH.nonceSha256());
                stored.put("executor_issuer", EXECUTOR.issuerFingerprint()); stored.put("executor_id", EXECUTOR.subjectId()); stored.put("dispatch_parameters_sha256", TOOL.parametersSha256(ACTION.arguments()));
            }
            stored.put("result", row.outcome() == null ? null : codec.outcome(TOOL, row.outcome())); stored.put("business_receipt_id", row.outcome() == null ? null : row.outcome().businessReceiptId()); stored.put("last_error_code", row.lastErrorCode());
            for (String alias : List.of("origin_owner_matches", "dispatch_owner_matches", "dispatch_identity_matches", "dispatch_fact_matches")) stored.put(alias, true);
        }
        private AgentLedgerService.ExecutionMaterial material(String id) {
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 2, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(TOOL.versionKey()), "retry-1", TOOL.policy().approvalPolicyRef());
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(TOOL.reference()), "route-1", "policy-1"), policy, Map.of());
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("fixed task", null, null), null); Instant deadline = NOW.minusSeconds(1);
            var authorization = new AgentRunAuthorization.Request(OWNER, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, deadline);
            var run = new AgentRunDTO(id, "assistant", 1, null, AgentRunStatusEnum.COMPLETED, null, false, 1, 2, 0, deadline, CREATED.minusSeconds(600), NOW.minusSeconds(2));
            return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
        }
        @Override public void close() { templates.close(); }
    }
    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = (Instant) values.get(call.getArgument(0)); return value == null ? null : Timestamp.from(value); });
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(call -> values.get(call.getArgument(0))); return result;
    }
}
