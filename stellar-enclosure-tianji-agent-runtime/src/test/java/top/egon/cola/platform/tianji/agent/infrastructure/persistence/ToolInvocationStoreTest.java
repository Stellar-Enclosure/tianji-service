package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
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
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real Spring/budget transactions with controlled JDBC; actual SQL, catalogs and races remain opt-in PostgreSQL checks. */
class ToolInvocationStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T14:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", HASH = "a".repeat(64), GRANT = "9" + "1".repeat(62);
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final ToolDescriptor READ = descriptor(ToolPolicy.Risk.READ_ONLY);
    private static final ToolDescriptor WRITE = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE);
    private static final ToolDescriptor APPROVAL = descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED);

    @Test void request_handoffs_normalize_arguments_and_keep_parameter_and_action_fingerprints_distinct() {
        var values = new HashMap<String, Object>(); values.put("amount", 20);
        var action = new ToolActionRequest(ACTOR, WRITE, "invoice-1", "v1", values);
        var request = request(WRITE, values, action, INVOCATION, new BigDecimal("0.20"), NOW.plusSeconds(300));
        values.put("amount", 21);
        assertEquals(Map.of("amount", 20L), request.arguments());
        assertThrows(UnsupportedOperationException.class, () -> request.arguments().put("amount", 30L));
        assertEquals(OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_INVOCATION_PARAMETERS_V1", WRITE.key(), Long.toString(WRITE.version()), WRITE.reference().contentSha256()), Map.of("amount", 20L)), request.parametersSha256());
        assertNotEquals(action.requestSha256(), request.parametersSha256());
        assertEquals("tool:" + INVOCATION, request.reservationId());
        assertEquals("ToolInvocationStore.PreparationRequestVO[redacted]", request.toString());
        assertThrows(IllegalArgumentException.class, () -> request(READ, Map.of("amount", 20L), action, INVOCATION, BigDecimal.ZERO, NOW.plusSeconds(300)));
        assertThrows(IllegalArgumentException.class, () -> request(WRITE, Map.of("amount", 20L), null, INVOCATION, BigDecimal.ZERO, NOW.plusSeconds(300)));
        assertThrows(IllegalArgumentException.class, () -> request(WRITE, Map.of("amount", 21L), action, INVOCATION, BigDecimal.ZERO, NOW.plusSeconds(300)));
        assertThrows(IllegalArgumentException.class, () -> request(WRITE, Map.of("amount", 20L), action, INVOCATION, BigDecimal.ZERO, null));
        assertThrows(IllegalArgumentException.class, () -> request(READ, Map.of("amount", 20L), null, INVOCATION, new BigDecimal("-0.01"), null));
    }

    @Test void source_affinities_readiness_and_ambient_transactions_are_checked_before_authority() throws Exception {
        try (var fixture = new Fixture(READ)) {
            when(fixture.ledger.usesDataSource(fixture.source)).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> fixture.store(false, fixture.authority));
            when(fixture.ledger.usesDataSource(fixture.source)).thenReturn(true);
            when(fixture.capabilities.usesLedger(fixture.ledger)).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> fixture.store(false, fixture.authority));
            when(fixture.capabilities.usesLedger(fixture.ledger)).thenReturn(true);
            var store = fixture.store(false, fixture.authority);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
            store.verifyStorage();
            assertTrue(store.usesDataSource(fixture.source)); assertTrue(store.usesLedger(fixture.ledger));
            assertTrue(store.usesCapabilities(fixture.capabilities)); assertTrue(store.usesAuthorization(fixture.authority));
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> store.prepare(fixture.request(INVOCATION))); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            verifyNoInteractions(fixture.source, fixture.authority);
        }
    }

    @Test void legacy_allow_and_explicit_denials_never_open_the_preparation_transaction() throws Exception {
        try (var fixture = new Fixture(READ)) {
            var store = fixture.store(true, (request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
            verifyNoInteractions(fixture.source);
        }
        for (var decision : List.of(ToolExecutionAuthorization.Decision.DENY, ToolExecutionAuthorization.Decision.UNAVAILABLE)) {
            try (var fixture = new Fixture(READ)) {
                fixture.check.set(request -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.PREPARE, decision, null));
                var store = fixture.store(true, fixture.authority);
                assertCode(decision == ToolExecutionAuthorization.Decision.DENY ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        () -> store.prepare(fixture.request(INVOCATION)));
                verifyNoInteractions(fixture.source);
            }
        }
    }

    @Test void the_actual_check_request_grant_and_fresh_audit_window_must_match() throws Exception {
        for (var variant : List.of("request", "boundary", "grant", "old", "future", "expiry")) {
            try (var fixture = new Fixture(READ)) {
                fixture.check.set(request -> {
                    var returned = variant.equals("request") ? new ToolExecutionAuthorization.Request(request.run(), request.lease(), request.tool(), request.bindingSha256(),
                            "b".repeat(64), request.actionIntentId(), request.deadlineAt()) : request;
                    var boundary = variant.equals("boundary") ? ToolExecutionAuthorization.Boundary.DISPATCH : ToolExecutionAuthorization.Boundary.PREPARE;
                    Instant checked = variant.equals("old") ? NOW.minusNanos(1) : variant.equals("future") ? NOW.plusNanos(1) : NOW;
                    Instant expiry = variant.equals("expiry") ? fixture.evidence.get().expiresAt().plusSeconds(1) : fixture.evidence.get().expiresAt();
                    var audit = audit(boundary, variant.equals("grant") ? "9" + "2".repeat(62) : GRANT, checked, expiry);
                    return new ToolExecutionAuthorization.CheckResult(returned, boundary, ToolExecutionAuthorization.Decision.ALLOW, audit);
                });
                var store = fixture.store(true, fixture.authority);
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
                verifyNoInteractions(fixture.source);
            }
        }
    }

    @Test void a_read_only_invocation_and_real_tool_budget_share_one_commit_after_the_run_lock() throws Exception {
        try (var fixture = new Fixture(READ)) {
            var result = fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION));
            assertNull(result.action()); var invocation = result.invocation(); assertNotNull(invocation);
            assertEquals(RUN, invocation.runId()); assertEquals(INVOCATION, invocation.invocationId()); assertEquals("tool:" + INVOCATION, invocation.reservationId());
            assertEquals("PREPARED", invocation.state()); assertEquals(1, invocation.revision()); assertEquals(3, invocation.capabilityIssueEpoch());
            assertEquals(NOW, invocation.createdAt()); assertEquals(NOW.plusSeconds(20), invocation.dispatchNotAfter()); assertNull(invocation.finishedAt()); assertTrue(invocation.newlyCreated());
            assertEquals("ToolInvocationStore.PreparedInvocationResult[redacted]", invocation.toString());
            assertEquals("ToolInvocationStore.PreparationResult[redacted]", result.toString());
            assertTrue(fixture.trace.indexOf("cap-out") < fixture.trace.indexOf("authorize"));
            assertTrue(fixture.trace.indexOf("authorize") < fixture.trace.indexOf("run-lock"));
            assertTrue(fixture.trace.indexOf("run-lock") < fixture.trace.indexOf("cap-in"));
            assertTrue(fixture.trace.indexOf("cap-in") < fixture.trace.indexOf("budget-insert"));
            assertTrue(fixture.trace.indexOf("budget-insert") < fixture.trace.indexOf("invocation-insert"));
            assertEquals(1, fixture.budgetInserts.get()); assertEquals(1, fixture.invocationInserts.get());
            assertEquals(fixture.request(INVOCATION).parametersSha256(), fixture.row.get().get("parameters_sha256"));
            assertEquals(0L, fixture.row.get().get("dispatch_epoch")); assertEquals("NONE", fixture.row.get().get("side_effect_outcome"));
            assertNull(fixture.row.get().get("outcome")); assertNull(fixture.row.get().get("executor_id"));
            verify(fixture.source).getConnection(); verify(fixture.connection).commit(); verify(fixture.connection, never()).rollback();
            verify(fixture.jdbc.get(), atLeastOnce()).query(contains("cancel_requested_at IS NULL FOR UPDATE"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                    eq(20L), eq("USER"), eq(ACTOR.issuerFingerprint()), eq("alice"), eq(RUN), eq("worker"), eq(1L));
        }
    }

    @Test void exact_host_uuid_replays_first_facts_and_input_or_estimate_drift_never_reserves_again() throws Exception {
        try (var fixture = new Fixture(READ)) {
            var store = fixture.store(true, fixture.authority); var first = store.prepare(fixture.request(INVOCATION)).invocation();
            fixture.databaseNow.set(NOW.plusSeconds(1)); fixture.wall.set(NOW.plusSeconds(1));
            var replay = store.prepare(fixture.request(INVOCATION)).invocation();
            assertEquals(first.createdAt(), replay.createdAt()); assertEquals(first.dispatchNotAfter(), replay.dispatchNotAfter()); assertFalse(replay.newlyCreated());
            var changedInput = request(READ, Map.of("amount", 21L), null, INVOCATION, new BigDecimal("0.20"), null);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> store.prepare(changedInput));
            var changedCost = request(READ, Map.of("amount", 20L), null, INVOCATION, new BigDecimal("0.21"), null);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> store.prepare(changedCost));
            assertEquals(1, fixture.budgetInserts.get()); assertEquals(1, fixture.invocationInserts.get());
            verify(fixture.budgets).reserveBudget(any(), any(), any());
        }
    }

    @Test void a_write_replay_preserves_the_first_invocation_and_other_call_ids_only_receive_the_existing_action_fact() throws Exception {
        try (var fixture = new Fixture(WRITE)) {
            var store = fixture.store(true, fixture.authority); var first = store.prepare(fixture.request(INVOCATION));
            assertNotNull(first.action()); assertTrue(first.invocation().newlyCreated());
            var replay = store.prepare(fixture.request(INVOCATION));
            assertFalse(replay.invocation().newlyCreated()); assertEquals(first.action(), replay.action());
            var differentCall = store.prepare(fixture.request(UUID.randomUUID()));
            assertNull(differentCall.invocation()); assertEquals(first.action(), differentCall.action());
            assertEquals(1, fixture.budgetInserts.get()); assertEquals(1, fixture.invocationInserts.get());
            assertTrue(fixture.trace.indexOf("action") < fixture.trace.indexOf("budget-insert"));
        }
    }

    @Test void every_existing_action_state_is_a_fact_without_a_new_invocation_or_budget() throws Exception {
        for (var state : List.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED")) {
            try (var fixture = new Fixture(state.equals("AWAITING_APPROVAL") ? APPROVAL : WRITE)) {
                fixture.intentNew.set(false);
                fixture.intentRow.set(new ToolActionIntentSql.Row(fixture.action, fixture.tool, RUN, run().snapshotSha256(), state, 3,
                        NOW.minusSeconds(60), NOW.minusSeconds(30), state.equals("EXPIRED") ? NOW.minusSeconds(1) : NOW.plusSeconds(30)));
                var result = fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION));
                assertNull(result.invocation()); assertEquals(state, result.action().state()); assertEquals(3, result.action().revision());
                assertEquals(0, fixture.budgetInserts.get()); assertEquals(0, fixture.invocationInserts.get());
                verifyNoInteractions(fixture.budgetRepository);
                verify(fixture.connection).commit();
            }
        }
    }

    @Test void a_new_approval_wait_is_finished_and_releases_known_unused_capacity_in_the_same_commit() throws Exception {
        try (var fixture = new Fixture(APPROVAL)) {
            var result = fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION));
            assertEquals("AWAITING_APPROVAL", result.action().state()); assertEquals("AWAITING_APPROVAL", result.invocation().state()); assertEquals(NOW, result.invocation().finishedAt());
            var outcome = new ToolExecutionJsonCodec().outcome((String) fixture.row.get().get("outcome"), fixture.tool);
            assertEquals(new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO), outcome);
            var budget = fixture.budgetRows.get("tool:" + INVOCATION);
            assertEquals(AgentBudgetReservation.State.SETTLED, budget.state()); assertEquals(0L, budget.actualInputTokens()); assertEquals(0L, budget.actualOutputTokens()); assertEquals(0, BigDecimal.ZERO.compareTo(budget.actualCost()));
            verify(fixture.budgets).settleBudget(ACTOR, lease(), "tool:" + INVOCATION, new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
            verify(fixture.source).getConnection(); verify(fixture.connection).commit(); verify(fixture.connection, never()).rollback();
        }
    }

    @Test void capability_epoch_ciphertext_or_expiry_changes_are_refused_even_with_the_same_grant_id() throws Exception {
        for (var change : List.of("epoch", "ciphertext", "expiry")) {
            try (var fixture = new Fixture(WRITE)) {
                var evidence = fixture.evidence.get();
                fixture.insideEvidence.set(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(evidence.reservation().binding(), change.equals("epoch") ? 4 : 3),
                        GRANT, change.equals("ciphertext") ? "ENC:v1:key:B:changed" : evidence.ciphertext(), change.equals("expiry") ? evidence.expiresAt().minusSeconds(1) : evidence.expiresAt()));
                var store = fixture.store(true, fixture.authority);
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
                assertEquals(0, fixture.budgetInserts.get()); assertEquals(0, fixture.invocationInserts.get());
                assertFalse(fixture.trace.contains("action")); verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit();
            }
        }
    }

    @Test void the_short_ticket_window_uses_the_original_monotonic_start_and_cannot_reset_after_capability_read() throws Exception {
        try (var fixture = new Fixture(READ)) {
            fixture.evidence.set(new ToolExecutionGrant.EvidenceVO(fixture.evidence.get().reservation(), GRANT, fixture.evidence.get().ciphertext(), NOW.plusSeconds(3)));
            fixture.afterOutsideCap.set(() -> fixture.nano.set(2_000_000_000L));
            fixture.check.set(request -> {
                assertEquals(NOW.plusSeconds(3), request.deadlineAt());
                var result = fixture.allowed(request); fixture.nano.set(3_000_000_001L); return result;
            });
            var store = fixture.store(true, fixture.authority);
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
            verifyNoInteractions(fixture.source);
        }
    }

    @Test void dispatch_fact_deadlines_are_bounded_by_live_ticket_and_business_intent_without_extending_replay() throws Exception {
        try (var fixture = new Fixture(READ)) {
            fixture.evidence.set(new ToolExecutionGrant.EvidenceVO(fixture.evidence.get().reservation(), GRANT, fixture.evidence.get().ciphertext(), NOW.plusSeconds(3).plusNanos(999)));
            var result = fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION));
            assertEquals(NOW.plusSeconds(3), result.invocation().dispatchNotAfter());
        }
        try (var fixture = new Fixture(WRITE)) {
            var shortIntent = request(WRITE, fixture.arguments(), fixture.action, INVOCATION, new BigDecimal("0.20"), NOW.plusSeconds(2).plusNanos(999));
            var store = fixture.store(true, fixture.authority); var result = store.prepare(shortIntent);
            assertEquals(NOW.plusSeconds(2), result.invocation().dispatchNotAfter());
            String originalAudit = (String) fixture.row.get().get("authorization_audit");
            fixture.wall.set(NOW.plusSeconds(3)); fixture.databaseNow.set(NOW.plusSeconds(3)); fixture.nano.set(3_000_000_000L);
            var replay = store.prepare(shortIntent);
            assertEquals(result.invocation().createdAt(), replay.invocation().createdAt());
            assertEquals(result.invocation().dispatchNotAfter(), replay.invocation().dispatchNotAfter());
            assertEquals(result.action(), replay.action()); assertFalse(replay.invocation().newlyCreated());
            assertEquals(originalAudit, fixture.row.get().get("authorization_audit"));
            assertEquals(1, fixture.budgetInserts.get()); assertEquals(1, fixture.invocationInserts.get());
        }
    }

    @Test void lock_waits_recheck_the_actual_database_lease_and_authority_deadline() throws Exception {
        for (boolean authorityExpiry : List.of(false, true)) {
            try (var fixture = new Fixture(READ)) {
                if (!authorityExpiry) fixture.databaseLease.set(NOW.plusSeconds(2));
                fixture.beforeRunLock.set(() -> fixture.databaseNow.set(NOW.plusSeconds(authorityExpiry ? 11 : 3)));
                var store = fixture.store(true, fixture.authority);
                assertThrows(AgentLedgerException.class, () -> store.prepare(fixture.request(INVOCATION)));
                assertEquals(0, fixture.budgetInserts.get()); assertEquals(0, fixture.invocationInserts.get());
                verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit();
            }
        }
        try (var fixture = new Fixture(READ)) {
            fixture.sourceActive.set(false);
            assertCode(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, () -> fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION)));
            assertEquals(0, fixture.budgetInserts.get());
        }
    }

    @Test void a_budget_without_its_invocation_is_never_retrofitted_into_a_journal() throws Exception {
        try (var fixture = new Fixture(READ)) {
            var request = fixture.request(INVOCATION); fixture.budgetRows.put(request.reservationId(), budget(request));
            var store = fixture.store(true, fixture.authority);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> store.prepare(request));
            assertEquals(0, fixture.invocationInserts.get()); verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit();
        }
    }

    @Test void an_invocation_insert_failure_rolls_back_the_same_connection_as_action_and_budget_work() throws Exception {
        try (var fixture = new Fixture(WRITE)) {
            fixture.failInsert.set(true);
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION)));
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private arguments"));
            assertTrue(fixture.trace.contains("action")); assertTrue(fixture.trace.contains("budget-insert"));
            verify(fixture.source).getConnection(); verify(fixture.connection).rollback(); verify(fixture.connection, never()).commit();
        }
    }

    @Test void changed_or_dispatched_persisted_rows_cannot_be_replayed_as_preparation_facts() throws Exception {
        for (var drift : List.of("state", "nonce", "audit", "budget")) {
            try (var fixture = new Fixture(READ)) {
                var store = fixture.store(true, fixture.authority); store.prepare(fixture.request(INVOCATION));
                switch (drift) {
                    case "state" -> fixture.row.get().put("state", "DISPATCHING");
                    case "nonce" -> fixture.row.get().put("dispatch_nonce_sha256", HASH);
                    case "audit" -> fixture.row.get().put("authorization_audit", "{}");
                    case "budget" -> {
                        var prior = fixture.budgetRows.get("tool:" + INVOCATION);
                        fixture.budgetRows.put(prior.reservationId(), new AgentBudgetReservation(prior.reservationId(), prior.leaseEpoch(), AgentBudgetRequest.Kind.MODEL, null,
                                prior.inputTokens(), prior.outputTokens(), prior.estimatedCost(), prior.state(), null, null, null, false));
                    }
                    default -> throw new AssertionError("Unexpected drift fixture");
                }
                assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> store.prepare(fixture.request(INVOCATION)));
                assertEquals(1, fixture.invocationInserts.get()); assertEquals(1, fixture.budgetInserts.get());
            }
        }
    }

    @Test void malformed_private_source_causes_are_sanitized_before_any_action_or_budget_change() throws Exception {
        try (var fixture = new Fixture(WRITE)) {
            when(fixture.ledger.executionMaterial(eq(ACTOR), any())).thenThrow(new IllegalStateException("private original input", new IllegalArgumentException("private arguments")));
            var failure = assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> fixture.store(true, fixture.authority).prepare(fixture.request(INVOCATION)));
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private"));
            assertEquals(0, fixture.budgetInserts.get()); assertFalse(fixture.trace.contains("action")); verify(fixture.connection).rollback();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ToolDescriptor tool;
        final ToolActionRequest action;
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final ToolCapabilityStore capabilities = mock(ToolCapabilityStore.class);
        final ToolExecutionAuthorization authority = mock(ToolExecutionAuthorization.class);
        final JdbcAgentLedgerRepository budgetRepository = mock(JdbcAgentLedgerRepository.class);
        final AgentBudgetService budgets;
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> row = new AtomicReference<>();
        final Map<String, AgentBudgetReservation> budgetRows = new HashMap<>();
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW), databaseLease = new AtomicReference<>(NOW.plusSeconds(30)), wall = new AtomicReference<>(NOW);
        final AtomicReference<ToolExecutionGrant.EvidenceVO> evidence, insideEvidence = new AtomicReference<>();
        final AtomicReference<ToolActionIntentSql.Row> intentRow = new AtomicReference<>();
        final AtomicBoolean intentNew = new AtomicBoolean(true), sourceActive = new AtomicBoolean(true), failInsert = new AtomicBoolean();
        final AtomicLong nano = new AtomicLong(), budgetInserts = new AtomicLong(), invocationInserts = new AtomicLong();
        final List<String> trace = new ArrayList<>();
        final AtomicReference<Runnable> beforeRunLock = new AtomicReference<>(() -> { }), afterOutsideCap = new AtomicReference<>(() -> { });
        final AtomicReference<Function<ToolExecutionAuthorization.Request, ToolExecutionAuthorization.CheckResult>> check = new AtomicReference<>();
        final MockedConstruction<JdbcTemplate> templates;
        final MockedConstruction<ToolExecutionStorageVerifier> verifiers;
        final MockedConstruction<ToolActionIntentSql> intents;

        Fixture(ToolDescriptor tool) throws Exception {
            this.tool = tool;
            action = tool.policy().risk() == ToolPolicy.Risk.READ_ONLY ? null : new ToolActionRequest(ACTOR, tool, "invoice-1", "v1", arguments());
            evidence = new AtomicReference<>(new ToolExecutionGrant.EvidenceVO(new ToolExecutionGrant.ReservationVO(binding(tool), 3), GRANT, "ENC:v1:key:A:sealed", NOW.plusSeconds(25)));
            var policy = new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(20), 4, 5, 1000, 100, BigDecimal.TEN, "USD", 2, Set.of(tool.versionKey()), "retry", tool.policy().approvalPolicyRef());
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(tool.reference()), "route", "policy-1"), policy, Map.of());
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("private original input", null, null), null);
            var view = new AgentRunDTO(RUN, "assistant", 1, null, AgentRunStatusEnum.RUNNING, null, false, 1, 2, 0, run().deadlineAt(), NOW.minusSeconds(1), null);
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            doAnswer(call -> { trace.add("commit"); return null; }).when(connection).commit();
            doAnswer(call -> { trace.add("rollback"); return null; }).when(connection).rollback();
            when(ledger.usesDataSource(source)).thenReturn(true); when(capabilities.usesDataSource(source)).thenReturn(true); when(capabilities.usesLedger(ledger)).thenReturn(true);
            when(ledger.executionMaterial(ACTOR, new GetAgentRunQuery(RUN))).thenReturn(new AgentLedgerService.ExecutionMaterial(view, snapshot, run()));
            when(ledger.snapshot(ACTOR, new GetAgentRunQuery(RUN))).thenReturn(snapshot);
            when(ledger.databaseNow()).thenAnswer(call -> databaseNow.get());
            when(capabilities.current(eq(binding(tool)), eq(tool))).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); trace.add("cap-out");
                var value = evidence.get(); afterOutsideCap.get().run(); return Optional.of(value);
            });
            when(capabilities.currentInTransaction(eq(binding(tool)), eq(tool))).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(source));
                trace.add("cap-in"); return Optional.ofNullable(insideEvidence.get() == null ? evidence.get() : insideEvidence.get());
            });
            check.set(this::allowed);
            when(authority.check(any(), eq(ToolExecutionAuthorization.Boundary.PREPARE))).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); trace.add("authorize"); return check.get().apply(call.getArgument(0));
            });
            when(budgetRepository.lockRunForBudget(ACTOR, RUN)).thenAnswer(call -> { trace.add("budget-run-lock"); assertTrue(TransactionSynchronizationManager.hasResource(source)); return true; });
            when(budgetRepository.databaseNow()).thenAnswer(call -> databaseNow.get());
            when(budgetRepository.validLease(ACTOR, lease())).thenReturn(true);
            when(budgetRepository.lockLeaseForSettlement(ACTOR, lease())).thenReturn(true);
            when(budgetRepository.insertBudgetReservation(eq(ACTOR), eq(lease()), any(), any())).thenAnswer(call -> {
                trace.add("budget-insert"); var request = (AgentBudgetRequest) call.getArgument(2);
                if (budgetRows.containsKey(request.reservationId())) return 0;
                budgetRows.put(request.reservationId(), new AgentBudgetReservation(request.reservationId(), lease().epoch(), request.kind(), request.toolVersion(), request.inputTokens(), request.outputTokens(), request.estimatedCost(),
                        AgentBudgetReservation.State.RESERVED, null, null, null, false)); budgetInserts.incrementAndGet(); return 1;
            });
            when(budgetRepository.reserveBudget(eq(ACTOR), eq(lease()), any(), eq(policy))).thenReturn(1);
            when(budgetRepository.budgetReservation(eq(ACTOR), eq(RUN), anyString(), anyBoolean())).thenAnswer(call -> Optional.ofNullable(budgetRows.get(call.getArgument(2))));
            when(budgetRepository.adjustBudget(eq(ACTOR), eq(lease()), eq(AgentBudgetRequest.Kind.TOOL), anyLong(), anyLong(), any())).thenReturn(1);
            doAnswer(call -> {
                String id = call.getArgument(2); var state = (AgentBudgetReservation.State) call.getArgument(3); var actual = (AgentBudgetSettlement) call.getArgument(4); var old = budgetRows.get(id);
                budgetRows.put(id, new AgentBudgetReservation(id, old.leaseEpoch(), old.kind(), old.toolVersion(), old.inputTokens(), old.outputTokens(), old.estimatedCost(), state,
                        actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false)); return null;
            }).when(budgetRepository).settleBudget(eq(ACTOR.tenantId()), eq(RUN), anyString(), any(), any(), any());
            budgets = spy(new AgentBudgetService(ledger, budgetRepository, new TransactionTemplate(new DataSourceTransactionManager(source))));
            when(ledger.budgets()).thenReturn(budgets);
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(databaseNow.get()));
                when(template.queryForObject(startsWith("SELECT set_config("), eq(String.class))).thenReturn("pg_catalog,tianji_agent,public");
                doAnswer(call -> {
                    String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1);
                    if (sql.startsWith("SELECT lease_expires_at")) {
                        trace.add("run-lock"); beforeRunLock.get().run();
                        return sourceActive.get() ? List.of(mapper.mapRow(result(Map.of("lease_expires_at", databaseLease.get())), 0)) : List.of();
                    }
                    if (sql.contains("FROM tianji_agent.agent_tool_invocation i")) {
                        if (row.get() == null) return List.of();
                        Object[] bindings = (Object[]) call.getRawArguments()[2];
                        if (!java.util.Objects.equals(row.get().get("run_id"), bindings[bindings.length - 2])
                                || !java.util.Objects.equals(row.get().get("invocation_id"), bindings[bindings.length - 1])) return List.of();
                        var values = new HashMap<>(row.get()); var stored = budgetRows.get(values.get("reservation_id"));
                        if (stored != null) {
                            values.put("budget_lease_epoch", stored.leaseEpoch()); values.put("budget_kind", stored.kind().name()); values.put("budget_tool_version", stored.toolVersion());
                            values.put("budget_input_tokens", stored.inputTokens()); values.put("budget_output_tokens", stored.outputTokens()); values.put("budget_estimated_cost", stored.estimatedCost()); values.put("budget_state", stored.state().name());
                            values.put("budget_actual_input_tokens", stored.actualInputTokens()); values.put("budget_actual_output_tokens", stored.actualOutputTokens()); values.put("budget_actual_cost", stored.actualCost());
                        }
                        return List.of(mapper.mapRow(result(values), 0));
                    }
                    throw new AssertionError("Unexpected controlled invocation query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> {
                    trace.add("invocation-insert"); if (failInsert.get()) throw new DataIntegrityViolationException("private arguments");
                    Object[] values = (Object[]) call.getRawArguments()[1];
                    var names = List.of("tenant_id", "run_id", "invocation_id", "owner_type", "owner_issuer", "owner_id", "capability_id", "capability_binding_sha256", "capability_issue_epoch", "lease_epoch", "reservation_id",
                            "tool_key", "tool_version", "tool_sha256", "binding_sha256", "parameters_sha256", "risk", "intent_id", "state", "authorization_audit", "outcome", "created_at", "updated_at", "dispatch_not_after", "finished_at");
                    var stored = new HashMap<String, Object>(); for (int i = 0; i < names.size(); i++) stored.put(names.get(i), values[i]);
                    stored.put("revision", 1L); stored.put("dispatch_epoch", 0L); stored.put("side_effect_outcome", "NONE");
                    row.set(stored); invocationInserts.incrementAndGet(); return 1;
                }).when(template).update(startsWith("INSERT INTO tianji_agent.agent_tool_invocation"), any(Object[].class));
            });
            verifiers = mockConstruction(ToolExecutionStorageVerifier.class);
            intents = mockConstruction(ToolActionIntentSql.class, (intent, context) -> when(intent.prepareWithStatus(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
                trace.add("action"); boolean created = intentNew.getAndSet(false); var current = intentRow.get();
                if (current == null || created) {
                    Instant expiry = ((Instant) call.getArgument(4)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                    current = new ToolActionIntentSql.Row(action, tool, RUN, run().snapshotSha256(), tool.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED ? "AWAITING_APPROVAL" : "PREPARED", 1, NOW, NOW, expiry);
                    intentRow.set(current);
                }
                return new ToolActionIntentSql.PreparationResultVO(current, created);
            }));
        }

        Map<String, Object> arguments() { return Map.of("amount", 20L); }
        ToolInvocationStore.PreparationRequestVO request(UUID id) { return ToolInvocationStoreTest.request(tool, arguments(), action, id, new BigDecimal("0.20"), action == null ? null : NOW.plusSeconds(300)); }
        ToolExecutionAuthorization.CheckResult allowed(ToolExecutionAuthorization.Request request) {
            return new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.PREPARE, ToolExecutionAuthorization.Decision.ALLOW,
                    audit(ToolExecutionAuthorization.Boundary.PREPARE, GRANT, wall.get(), evidence.get().expiresAt()));
        }
        ToolInvocationStore store(boolean ready, ToolExecutionAuthorization selected) {
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return wall.get(); }
            };
            var store = new ToolInvocationStore(source, ledger, capabilities, selected, clock, nano::get); if (ready) store.verifyStorage(); return store;
        }
        @Override public void close() { intents.close(); verifiers.close(); templates.close(); }
    }

    private static ToolDescriptor descriptor(ToolPolicy.Risk risk) {
        String key = risk == ToolPolicy.Risk.READ_ONLY ? "process.get" : risk == ToolPolicy.Risk.IDEMPOTENT_WRITE ? "process.start" : "process.approvedStart";
        return new ToolDescriptor(key, 1, key.replace('.', '_'), "Fixed operation", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of(), Set.of()), new ToolPolicy(risk, "process.invoice", "tianji.api.process.instance.start", HASH, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval-1" : null));
    }
    private static AgentRunAuthorization.Request run() { return new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60)); }
    private static AgentRunLease lease() { return new AgentRunLease(RUN, "worker", 1, NOW.minusSeconds(1)); }
    private static ToolExecutionGrant.BindingVO binding(ToolDescriptor tool) { return new ToolExecutionGrant.BindingVO(run(), tool.reference(), tool.policy().bindingSha256(), "TIANJI_TOOL_PROCESS", URI.create("https://agent.example/20"), "tool.process.execute", null); }
    private static ToolInvocationStore.PreparationRequestVO request(ToolDescriptor tool, Map<String, Object> values, ToolActionRequest action, UUID id, BigDecimal cost, Instant expiry) {
        return new ToolInvocationStore.PreparationRequestVO(binding(tool), lease(), tool, values, action, id, cost, expiry);
    }
    private static AgentBudgetReservation budget(ToolInvocationStore.PreparationRequestVO request) {
        return new AgentBudgetReservation(request.reservationId(), lease().epoch(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0, request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, false);
    }
    private static ToolExecutionAuthorization.AuditVO audit(ToolExecutionAuthorization.Boundary boundary, String grant, Instant checked, Instant expiry) {
        return new ToolExecutionAuthorization.AuditVO(1, UUID.randomUUID(), boundary, ToolExecutionAuthorization.Decision.ALLOW, TrustedAgentActor.SubjectType.USER, grant, 5L, null, 7L, checked, expiry);
    }
    private static AgentLedgerException assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(AgentLedgerException.class, operation); assertEquals(code, failure.code()); return failure;
    }
    private static ResultSet result(Map<String, Object> values) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(rs.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(rs.getTimestamp(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value instanceof Timestamp timestamp ? timestamp : value == null ? null : Timestamp.from((Instant) value); });
        when(rs.getObject(anyString(), eq(UUID.class))).thenAnswer(call -> values.get(call.getArgument(0)));
        when(rs.getObject(anyString(), eq(Long.class))).thenAnswer(call -> values.get(call.getArgument(0)));
        when(rs.getBigDecimal(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        return rs;
    }
}
