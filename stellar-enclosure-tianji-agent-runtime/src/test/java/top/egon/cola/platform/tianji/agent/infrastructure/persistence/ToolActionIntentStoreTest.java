package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;
import java.lang.reflect.Modifier;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled JDBC proves boundary/ordering/rollback requests; real SQL and races belong to the opt-in PG fixture. */
class ToolActionIntentStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", HASH = "a".repeat(64);
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final TrustedAgentActor RUNTIME_OWNER = TrustedAgentActor.verified(
            20, TrustedAgentActor.SubjectType.SERVICE, "process-source", "https://idp.example/20");
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.start", 1, "start_process", "Fixed operation",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of(), Set.of()),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, null));
    private static final String DISPATCH_RUN = "10000000-0000-0000-0000-000000000002", NONCE = "d".repeat(64);
    private static final UUID DISPATCH_INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor EXECUTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "executor", "https://executor.example/20");
    private static final ToolDescriptor RESULT_TOOL = historyTool(false), APPROVAL_TOOL = historyTool(true);
    private static final ToolActionRequest ACTION = new ToolActionRequest(ACTOR, TOOL, "invoice-1", "v1", Map.of("amount", 20L));

    @Test void different_sources_unverified_storage_and_ambient_transactions_are_refused_before_authority_or_sql() throws Exception {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var authority = mock(ToolExecutionAuthorization.class);
        assertThrows(IllegalArgumentException.class, () -> new ToolActionIntentStore(source, ledger, authority));
        when(ledger.usesDataSource(source)).thenReturn(true);
        var store = new ToolActionIntentStore(source, ledger, authority);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.current(run(RUN), lease(RUN), ACTION, TOOL)).code());
        verifyNoInteractions(authority, source);
        try (var fixture = new Fixture()) {
            var ready = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> ready.current(run(RUN), lease(RUN), ACTION, TOOL)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            verifyNoInteractions(fixture.source);
        }
    }

    @Test void independent_prepare_authority_runs_outside_transactions_and_denials_never_read_private_records() throws Exception {
        for (var decision : List.of(ToolExecutionAuthorization.Decision.DENY, ToolExecutionAuthorization.Decision.UNAVAILABLE)) {
            try (var fixture = new Fixture()) {
                var store = fixture.store((request, boundary) -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(ToolExecutionAuthorization.Boundary.PREPARE, boundary);
                    assertEquals(run(RUN), request.run()); assertEquals(lease(RUN), request.lease());
                    assertEquals(TOOL.reference(), request.tool()); assertEquals(HASH, request.bindingSha256());
                    assertEquals(ACTION.requestSha256(), request.parametersSha256()); assertEquals(ACTION.intentId(), request.actionIntentId());
                    assertEquals(NOW.plusSeconds(10), request.deadlineAt()); return decision;
                });
                var error = assertThrows(AgentLedgerException.class, () -> store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
                assertEquals(decision == ToolExecutionAuthorization.Decision.DENY ? AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code());
                assertNull(error.getCause()); verifyNoInteractions(fixture.source);
            }
        }
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> { throw new IllegalStateException("private arguments"); });
            var failure = assertThrows(AgentLedgerException.class, () -> store.current(run(RUN), lease(RUN), ACTION, TOOL));
            assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("private arguments")); verifyNoInteractions(fixture.source);
        }
    }

    @Test void the_monotonic_budget_expires_even_when_the_wall_clock_moves_backwards_during_authorization() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> { fixture.wall.set(NOW.minusSeconds(60)); fixture.nano.set(10_000_000_001L); return ToolExecutionAuthorization.Decision.ALLOW; });
            assertThrows(AgentLedgerException.class, () -> store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
            verifyNoInteractions(fixture.source);
        }
    }

    @Test void a_renewed_database_lease_accepts_the_old_java_expiry_hint_and_replay_keeps_the_first_expiry() throws Exception {
        try (var fixture = new Fixture()) {
            var requests = new AtomicLong(); var store = fixture.store((request, boundary) -> { requests.incrementAndGet(); return ToolExecutionAuthorization.Decision.ALLOW; });
            Instant expiry = NOW.plusSeconds(300).plusNanos(999);
            var first = store.prepare(run(RUN), lease(RUN), ACTION, TOOL, expiry);
            assertNotNull(first); assertEquals(ACTION.intentId(), first.intentId()); assertEquals(ACTION.requestSha256(), first.requestSha256());
            assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), first.expiresAt()); assertEquals(RUN, first.originRunId());
            assertEquals(first, store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(400)));
            assertEquals(first, store.current(run(RUN), lease(RUN), ACTION, TOOL).orElseThrow());
            assertEquals(1, fixture.inserts.get()); assertEquals(3, requests.get());
            assertEquals("ToolActionIntentStore.PreparedActionResult[redacted]", first.toString());
            verify(fixture.jdbc.get(), atLeastOnce()).query(contains("a.owner_issuer=? AND a.owner_id=? AND a.intent_id=?"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                    eq(20L), eq("USER"), eq(ACTOR.issuerFingerprint()), eq("alice"), eq(UUID.fromString(ACTION.intentId())));
        }
    }

    @Test void same_owner_new_run_replays_the_original_fact_without_rebinding_it_to_the_new_source() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            var first = store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300));
            assertNotNull(first);
            String next = "10000000-0000-0000-0000-000000000002"; fixture.run.set(run(next));
            var replay = store.prepare(run(next), lease(next), ACTION, TOOL, NOW.plusSeconds(400));
            assertNotNull(replay);
            assertEquals(first, replay); assertEquals(RUN, replay.originRunId()); assertEquals(1, fixture.inserts.get());
        }
    }

    @Test void changed_content_conflicts_and_an_expired_fact_does_not_receive_a_new_deadline_or_permit() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            var first = store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(1));
            var changed = new ToolActionRequest(ACTOR, TOOL, "invoice-1", "v1", Map.of("amount", 21L));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                    () -> store.prepare(run(RUN), lease(RUN), changed, TOOL, NOW.plusSeconds(300))).code());
            fixture.databaseNow.set(NOW.plusSeconds(2));
            assertEquals(first, store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
            assertEquals(1, fixture.inserts.get()); assertEquals(NOW.plusSeconds(1), first.expiresAt());
        }
    }

    @Test void lock_waits_cannot_outlive_either_the_authority_window_or_the_actual_database_lease() throws Exception {
        for (boolean authorityExpires : List.of(true, false)) {
            try (var fixture = new Fixture()) {
                var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
                fixture.advisoryWait.set(() -> fixture.databaseNow.set(NOW.plusSeconds(authorityExpires ? 11 : 2)));
                if (!authorityExpires) fixture.databaseLease.set(NOW.plusSeconds(1));
                assertThrows(AgentLedgerException.class, () -> store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
                assertEquals(0, fixture.inserts.get()); verify(fixture.connection).rollback();
            }
        }
    }

    @Test void stale_epoch_cancelled_source_and_bad_storage_never_return_a_prepared_fact() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            fixture.sourceActive.set(false);
            assertThrows(AgentLedgerException.class, () -> store.current(run(RUN), lease(RUN), ACTION, TOOL));
            assertEquals(0, fixture.inserts.get());
            verify(fixture.jdbc.get()).query(argThat(sql -> sql != null && sql.replaceAll("\\s+", " ")
                            .contains("cancel_requested_at IS NULL FOR SHARE")),
                    org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
                    eq(20L), eq("USER"), eq(ACTOR.issuerFingerprint()), eq("alice"), eq(RUN), eq("worker"), eq(1L));
        }
    }

    @Test void insertion_failure_rolls_back_and_never_exposes_private_driver_details() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            fixture.failInsert.set(true);
            var failure = assertThrows(AgentLedgerException.class, () -> store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("private arguments")); verify(fixture.connection).rollback();
        }
    }

    @Test void malformed_source_snapshot_cause_is_not_exposed_through_action_preparation() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
            when(fixture.ledger.executionMaterial(eq(ACTOR), any())).thenThrow(new IllegalStateException("Invalid snapshot", new IllegalArgumentException("private original input")));
            var failure = assertThrows(AgentLedgerException.class, () -> store.prepare(run(RUN), lease(RUN), ACTION, TOOL, NOW.plusSeconds(300)));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
            assertFalse(failure.getMessage().contains("private original input")); assertEquals(0, fixture.inserts.get());
            verify(fixture.jdbc.get(), never()).query(contains("FROM tianji_agent.agent_tool_action_intent a"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
            verify(fixture.connection).rollback();
        }
    }

    @Test void readiness_publishes_only_a_verified_profile_and_clears_it_after_failed_reverification() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.seed("PREPARED");
            var authority = mock(ToolExecutionAuthorization.class);
            var store = fixture.store(authority);
            assertTrue(store.historical(fixture.action, fixture.tool).isPresent());
            fixture.storageProfile.set(ToolExecutionStorageVerifier.SchemaProfile.V17);
            store.verifyStorage();
            assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool));
            fixture.row.get().put("stored_action_profile", "\"LEGACY_V1\"");
            fixture.row.get().put("stored_dispatch_call_id", "null");
            assertTrue(store.historical(fixture.action, fixture.tool).isPresent());
            fixture.storageProfile.set(null);
            assertThrows(AgentLedgerException.class, store::verifyStorage);
            assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool));
            verifyNoInteractions(authority);
        }
    }

    @Test void v17_history_reads_one_exact_runtime_receipt_and_preserves_legacy_boundaries() throws Exception {
        try (var fixture = new Fixture(RESULT_TOOL, RUNTIME_OWNER)) {
            var stored = fixture.seed("APPLIED");
            var codec = new ToolExecutionJsonCodec();
            var runtime = ToolExecutionJsonCodec.ActionProfile.RUNTIME_V2;
            var origin = fixture.seedRuntimeSource(RUN, "origin");
            var dispatch = fixture.seedRuntimeSource(DISPATCH_RUN, "dispatch");
            stored.put("stored_action_profile", "\"RUNTIME_V2\"");
            stored.put("stored_dispatch_call_id", "\"" + DISPATCH_INVOCATION + "\"");
            stored.remove("dispatch_invocation_id");
            stored.put("origin_snapshot_sha256", origin.get("snapshot_sha256"));
            stored.put("origin_runtime_snapshot_sha256", origin.get("snapshot_sha256"));
            stored.put("dispatch_runtime_snapshot_sha256", dispatch.get("snapshot_sha256"));
            stored.put("runtime_dispatch_matches", true);
            stored.put("runtime_call_state", "SUCCEEDED");
            stored.put("runtime_input_sha256", fixture.tool.parametersSha256(fixture.action.arguments()));
            stored.put("dispatch_identity_matches", false);
            stored.put("dispatch_fact_matches", false);
            var outcome = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED,
                    Map.of("instanceId", "private-instance"), "business-receipt-1", null, null);
            stored.put("result", codec.outcome(runtime, fixture.tool, outcome));
            stored.put("runtime_result_sha256", codec.runtimeWriteResultSha256(
                    fixture.action.intentId(), fixture.action.requestSha256(), fixture.tool, outcome));
            var authority = mock(ToolExecutionAuthorization.class);
            var store = fixture.store(authority);
            assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool));
            assertTrue(fixture.queries.stream().noneMatch(sql -> sql.contains("agent_runtime_call_receipt")));
            fixture.storageProfile.set(ToolExecutionStorageVerifier.SchemaProfile.V17);
            store.verifyStorage();
            var row = store.historical(fixture.action, fixture.tool).orElseThrow();
            assertEquals(runtime, row.profile());
            assertEquals(outcome, row.outcome());
            assertEquals(DISPATCH_INVOCATION, row.dispatch().callId());
            assertNull(row.dispatch().invocationId());
            assertEquals(1L, fixture.queries.stream().filter(sql -> sql.contains("agent_runtime_call_receipt")).count());
            assertEquals(List.of(RUN, DISPATCH_RUN), fixture.runtimeSourceReads);
            assertNotEquals(origin.get("snapshot_sha256"), dispatch.get("snapshot_sha256"));
            for (var drift : Map.of(
                    "stored_action_profile", "\"UNKNOWN\"",
                    "stored_dispatch_call_id", "null",
                    "dispatch_invocation_id", DISPATCH_INVOCATION,
                    "origin_runtime_snapshot_sha256", "0".repeat(64),
                    "dispatch_runtime_snapshot_sha256", "0".repeat(64),
                    "runtime_dispatch_matches", false,
                    "runtime_input_sha256", "0".repeat(64),
                    "runtime_call_state", "FAILED",
                    "runtime_result_sha256", "0".repeat(64)).entrySet()) {
                Object previous = stored.put(drift.getKey(), drift.getValue());
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool), drift.getKey()).code());
                if (previous == null) stored.remove(drift.getKey()); else stored.put(drift.getKey(), previous);
            }
            for (var evidence : List.of(origin, dispatch)) {
                var otherRun = evidence == origin ? dispatch : origin;
                for (var drift : Map.of(
                        "execution_snapshot", otherRun.get("execution_snapshot"),
                        "request_hash", "0".repeat(64),
                        "snapshot_sha256", "0".repeat(64),
                        "content_sha256", "0".repeat(64),
                        "template_sha256", "0".repeat(64),
                        "configuration_sha256", "0".repeat(64),
                        "node_execution_ref", "unrelated-node",
                        "attempt_deadline_at", NOW.plusSeconds(1),
                        "safety_policy", "{}",
                        "specification", "{}").entrySet()) {
                    Object previous = evidence.put(drift.getKey(), drift.getValue());
                    assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                            assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool), drift.getKey()).code());
                    evidence.put(drift.getKey(), previous);
                }
            }
            var replay = store.historical(fixture.action, fixture.tool).orElseThrow();
            assertEquals(row.action().requestSha256(), replay.action().requestSha256());
            assertEquals(row.dispatch(), replay.dispatch());
            assertEquals(row.outcome(), replay.outcome());
            verify(fixture.ledger, never()).executionMaterial(any(), any());
            verifyNoInteractions(authority);
            verify(fixture.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void historical_rows_cover_all_eight_states_without_current_authority_or_mutations() throws Exception {
        for (String state : List.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED")) {
            try (var fixture = new Fixture(state.equals("AWAITING_APPROVAL") ? APPROVAL_TOOL : RESULT_TOOL)) {
                fixture.seed(state); var authority = mock(ToolExecutionAuthorization.class); var store = fixture.store(authority);
                var value = store.historical(fixture.action, fixture.tool).orElseThrow();
                assertEquals(state, value.state()); assertEquals(fixture.action.requestSha256(), value.action().requestSha256()); assertEquals(RUN, value.originRunId());
                if (state.equals("APPLIED")) {
                    assertEquals(ToolExecutionOutcome.Status.SUCCEEDED, value.outcome().status()); assertEquals(Map.of("instanceId", "private-instance"), value.outcome().output());
                    assertEquals(0, new BigDecimal("0.1250").compareTo(value.outcome().actualCost())); assertEquals("business-receipt-1", value.outcome().businessReceiptId());
                }
                if (state.equals("UNKNOWN")) assertNull(value.outcome().actualCost());
                assertEquals("ToolActionIntentRow[redacted]", value.toString()); assertFalse(value.toString().contains("private-instance"));
                assertEquals(1, fixture.queries.stream().filter(sql -> sql.contains("FROM tianji_agent.agent_tool_action_intent a")).count());
                assertTrue(fixture.queries.stream().noneMatch(sql -> sql.startsWith("SELECT lease_expires_at") || sql.contains("FOR UPDATE")));
                verifyNoInteractions(authority); verify(fixture.jdbc.get(), never()).update(anyString(), any(Object[].class));
                verify(fixture.connection).commit(); verify(fixture.connection, never()).setReadOnly(true);
            }
        }
    }

    @Test void consumed_approval_and_late_terminal_history_remain_readable_after_every_old_expiry() throws Exception {
        for (String state : List.of("PREPARED", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED")) {
            try (var fixture = new Fixture(APPROVAL_TOOL)) {
                fixture.seed(state); fixture.databaseNow.set(NOW.plusSeconds(600)); fixture.wall.set(NOW.plusSeconds(600));
                fixture.sourceDeadline.set(NOW.minusSeconds(30)); fixture.sourceTerminal.set(true); fixture.sourceActive.set(false);
                fixture.databaseLease.set(NOW.minusSeconds(1));
                var authority = mock(ToolExecutionAuthorization.class); var store = fixture.store(authority);
                var row = store.historical(fixture.action, fixture.tool).orElseThrow();
                assertNotNull(row.approval()); assertEquals(NOW.minusSeconds(10), row.approval().consumedAt());
                assertTrue(row.approval().proof().approval().expiresAt().isBefore(fixture.wall.get()));
                assertEquals("ToolActionIntentApprovalConsumption[redacted]", row.approval().toString());
                if (row.dispatch() != null) { assertEquals(EXECUTOR, row.dispatch().executor()); assertEquals(DISPATCH_RUN, row.dispatch().runId()); }
                assertEquals(state, row.state()); verifyNoInteractions(authority); assertEquals(0, fixture.inserts.get());
                assertTrue(fixture.queries.stream().noneMatch(sql -> sql.startsWith("SELECT lease_expires_at")));
            }
        }
    }

    @Test void known_failures_accept_pre_dispatch_or_dispatched_facts_and_generic_nonzero_fees() throws Exception {
        for (boolean approval : List.of(false, true)) for (boolean dispatched : List.of(false, true)) for (String state : List.of("REJECTED", "FAILED_KNOWN")) {
            try (var fixture = new Fixture(approval ? APPROVAL_TOOL : RESULT_TOOL)) {
                fixture.seed(state, dispatched, approval && dispatched);
                var row = fixture.store(mock(ToolExecutionAuthorization.class)).historical(fixture.action, fixture.tool).orElseThrow();
                assertEquals(dispatched, row.dispatch() != null); assertEquals(approval && dispatched, row.approval() != null);
                assertEquals(ToolExecutionOutcome.Status.valueOf(state), row.outcome().status()); assertEquals("NONE", row.sideEffectOutcome());
                assertTrue(row.outcome().actualCost().signum() > 0); assertNotNull(row.closedAt());
            }
        }
    }

    @Test void public_metadata_reads_use_the_full_parser_and_refuse_corrupt_private_result_groups() throws Exception {
        for (String variant : List.of("effect", "receipt", "error", "missing-result", "wrong-status", "wrong-output", "missing-close", "close-time")) {
            try (var fixture = new Fixture(RESULT_TOOL)) {
                var stored = fixture.seed("APPLIED");
                switch (variant) {
                    case "effect" -> stored.put("side_effect_outcome", "NONE");
                    case "receipt" -> stored.put("business_receipt_id", "other-receipt");
                    case "error" -> stored.put("last_error_code", "FORGED_ERROR");
                    case "missing-result" -> stored.remove("result");
                    case "wrong-status" -> stored.put("result", new ToolExecutionJsonCodec().outcome(fixture.tool,
                            new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, "REJECTED", null)));
                    case "wrong-output" -> stored.put("result", stored.get("result").toString().replace("\"private-instance\"", "17"));
                    case "missing-close" -> stored.remove("closed_at");
                    case "close-time" -> stored.put("closed_at", NOW.plusSeconds(1));
                    default -> throw new AssertionError("Unexpected private result corruption");
                }
                var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
                var failure = assertThrows(AgentLedgerException.class, () -> store.current(run(RUN), lease(RUN), fixture.action, fixture.tool));
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code()); assertNull(failure.getCause());
                assertFalse(failure.getMessage().contains("private-instance")); assertFalse(failure.getMessage().contains("business-receipt"));
            }
        }
    }

    @Test void partial_or_inconsistent_consumed_approval_groups_are_never_projected_as_facts() throws Exception {
        var variants = new ArrayList<>(List.of("approval_source_namespace_sha256", "approval_receipt_id", "approval_proof_sha256", "approval_proof",
                "approval_approved", "approval_consumed_at", "approval_expires_at"));
        variants.addAll(List.of("false", "early-consume", "expired-consume", "before-created", "wrong-expiry", "wrong-source", "wrong-proof", "wrong-receipt"));
        for (String variant : variants) {
            try (var fixture = new Fixture(APPROVAL_TOOL)) {
                var stored = fixture.seed("PREPARED");
                switch (variant) {
                    case "false" -> stored.put("approval_approved", false);
                    case "early-consume" -> stored.put("approval_consumed_at", NOW.minusSeconds(21));
                    case "expired-consume" -> stored.put("approval_consumed_at", NOW.plusSeconds(10));
                    case "before-created" -> stored.put("approval_consumed_at", NOW.minusSeconds(61));
                    case "wrong-expiry" -> stored.put("approval_expires_at", NOW.plusSeconds(11));
                    case "wrong-source" -> stored.put("approval_source_namespace_sha256", "e".repeat(64));
                    case "wrong-proof" -> stored.put("approval_proof_sha256", "e".repeat(64));
                    case "wrong-receipt" -> stored.put("approval_receipt_id", "another-approval");
                    default -> stored.remove(variant);
                }
                var store = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW);
                assertThrows(AgentLedgerException.class, () -> store.current(run(RUN), lease(RUN), fixture.action, fixture.tool));
            }
        }
    }

    @Test void dispatch_and_approval_presence_must_match_the_state_and_write_risk() throws Exception {
        for (String column : List.of("dispatch_run_id", "dispatch_invocation_id", "dispatch_nonce_sha256", "executor_issuer", "executor_id")) {
            try (var fixture = new Fixture(RESULT_TOOL)) {
                fixture.seed("APPLIED").remove(column);
                assertThrows(AgentLedgerException.class, () -> fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW)
                        .current(run(RUN), lease(RUN), fixture.action, fixture.tool));
            }
        }
        for (String variant : List.of("zero", "negative", "nonce", "executor", "prepared-dispatched", "applied-not-dispatched", "write-awaiting", "write-proof")) {
            try (var fixture = new Fixture(RESULT_TOOL)) {
                var stored = fixture.seed("APPLIED");
                switch (variant) {
                    case "zero" -> stored.put("dispatch_epoch", 0L);
                    case "negative" -> stored.put("dispatch_epoch", -1L);
                    case "nonce" -> stored.put("dispatch_nonce_sha256", "bad-hash");
                    case "executor" -> stored.put("executor_id", " executor ");
                    case "prepared-dispatched" -> fixture.seed("PREPARED", true, false);
                    case "applied-not-dispatched" -> fixture.seed("APPLIED", false, false);
                    case "write-awaiting" -> fixture.seed("AWAITING_APPROVAL", false, false);
                    case "write-proof" -> stored.put("approval_receipt_id", "unexpected");
                    default -> throw new AssertionError("Unexpected dispatch corruption");
                }
                assertThrows(AgentLedgerException.class, () -> fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW)
                        .current(run(RUN), lease(RUN), fixture.action, fixture.tool));
            }
        }
        for (String state : List.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING")) {
            try (var fixture = new Fixture(APPROVAL_TOOL)) {
                fixture.seed(state, state.equals("DISPATCHING"), state.equals("AWAITING_APPROVAL"));
                assertThrows(AgentLedgerException.class, () -> fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW)
                        .current(run(RUN), lease(RUN), fixture.action, fixture.tool));
            }
        }
    }

    @Test void each_joined_backlink_boolean_and_the_independent_parameter_digest_is_required() throws Exception {
        for (String column : List.of("origin_owner_matches", "dispatch_owner_matches", "dispatch_identity_matches", "dispatch_fact_matches", "dispatch_parameters_sha256")) {
            for (boolean missing : List.of(false, true)) try (var fixture = new Fixture(RESULT_TOOL)) {
                var stored = fixture.seed("APPLIED");
                if (missing) stored.remove(column);
                else stored.put(column, column.equals("dispatch_parameters_sha256") ? fixture.action.requestSha256() : false);
                assertNotEquals(fixture.action.requestSha256(), fixture.tool.parametersSha256(fixture.action.arguments()));
                assertThrows(AgentLedgerException.class, () -> fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW)
                        .current(run(RUN), lease(RUN), fixture.action, fixture.tool));
            }
        }
    }

    @Test void immutable_origin_and_dispatch_material_are_checked_without_any_current_permission_lookup() throws Exception {
        for (String variant : List.of("snapshot", "origin-owner", "dispatch-owner", "policy")) {
            try (var fixture = new Fixture(RESULT_TOOL)) {
                fixture.seed("APPLIED");
                var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", "https://idp.example/20");
                switch (variant) {
                    case "snapshot" -> fixture.sourceSnapshot.set("e".repeat(64));
                    case "origin-owner" -> fixture.sourceOwners.put(RUN, other);
                    case "dispatch-owner" -> fixture.sourceOwners.put(DISPATCH_RUN, other);
                    case "policy" -> fixture.sourceAllowed.set(false);
                    default -> throw new AssertionError("Unexpected source drift");
                }
                var authority = mock(ToolExecutionAuthorization.class);
                assertThrows(AgentLedgerException.class, () -> fixture.store(authority).historical(fixture.action, fixture.tool));
                verifyNoInteractions(authority);
            }
        }
    }

    @Test void history_is_full_owner_scoped_and_different_input_never_replays_the_private_body() throws Exception {
        try (var fixture = new Fixture(RESULT_TOOL)) {
            fixture.seed("APPLIED"); var authority = mock(ToolExecutionAuthorization.class); var store = fixture.store(authority);
            var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20");
            var foreign = new ToolActionRequest(other, fixture.tool, fixture.action.businessKey(), fixture.action.businessVersion(), fixture.action.arguments());
            assertTrue(store.historical(foreign, fixture.tool).isEmpty());
            var changed = new ToolActionRequest(ACTOR, fixture.tool, fixture.action.businessKey(), fixture.action.businessVersion(), Map.of("amount", 21L));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> store.historical(changed, fixture.tool)).code());
            verifyNoInteractions(authority); assertEquals(0, fixture.inserts.get());
        }
    }

    @Test void history_rejects_unverified_ambient_and_wrong_transaction_sources_before_reading_private_records() throws Exception {
        try (var fixture = new Fixture(RESULT_TOOL)) {
            fixture.seed("APPLIED"); var authority = mock(ToolExecutionAuthorization.class); var unready = fixture.store(authority, false);
            assertThrows(AgentLedgerException.class, () -> unready.historical(fixture.action, fixture.tool));
            var ready = fixture.store(authority); TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> ready.historical(fixture.action, fixture.tool)); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
            var participant = new ToolActionIntentSql(fixture.source, fixture.ledger);
            assertThrows(AgentLedgerException.class, () -> participant.historical(fixture.action, fixture.tool, NOW.plusSeconds(10)));
            var other = mock(DataSource.class); TransactionSynchronizationManager.setActualTransactionActive(true); TransactionSynchronizationManager.bindResource(other, fixture.connection);
            try { assertThrows(AgentLedgerException.class, () -> participant.historical(fixture.action, fixture.tool, NOW.plusSeconds(10))); }
            finally { TransactionSynchronizationManager.unbindResource(other); TransactionSynchronizationManager.setActualTransactionActive(false); }
            verifyNoInteractions(authority, fixture.source); assertTrue(fixture.queries.isEmpty());
        }
    }

    @Test void historical_reads_have_their_own_wall_monotonic_and_database_window_and_never_expire_rows() throws Exception {
        for (String variant : List.of("database", "monotonic", "primary")) {
            try (var fixture = new Fixture(RESULT_TOOL)) {
                fixture.seed("APPLIED"); var authority = mock(ToolExecutionAuthorization.class); var store = fixture.store(authority);
                switch (variant) {
                    case "database" -> fixture.afterJoined.set(() -> fixture.databaseNow.set(NOW.plusSeconds(11)));
                    case "monotonic" -> fixture.afterJoined.set(() -> { fixture.wall.set(NOW.minusSeconds(60)); fixture.nano.set(10_000_000_001L); });
                    case "primary" -> fixture.storageActive.set(false);
                    default -> throw new AssertionError("Unexpected history window");
                }
                assertThrows(AgentLedgerException.class, () -> store.historical(fixture.action, fixture.tool));
                verifyNoInteractions(authority); verify(fixture.connection).rollback(); verify(fixture.jdbc.get(), never()).update(anyString(), any(Object[].class));
            }
        }
    }

    @Test void prepare_locks_only_the_action_before_a_fresh_join_and_never_uses_a_pre_wait_tuple() throws Exception {
        try (var fixture = new Fixture(RESULT_TOOL)) {
            fixture.seed("PREPARED");
            fixture.rowLockWait.set(() -> fixture.seed("APPLIED").put("revision", 8L));
            var result = fixture.store((request, boundary) -> ToolExecutionAuthorization.Decision.ALLOW)
                    .prepare(run(RUN), lease(RUN), fixture.action, fixture.tool, NOW.plusSeconds(400));
            assertEquals("APPLIED", result.state()); assertEquals(8, result.revision()); assertEquals(0, fixture.inserts.get());
            int lock = -1, joined = -1;
            for (int index = 0; index < fixture.queries.size(); index++) {
                String query = fixture.queries.get(index);
                if (query.startsWith("SELECT intent_id FROM tianji_agent.agent_tool_action_intent")) lock = index;
                if (query.contains("FROM tianji_agent.agent_tool_action_intent a")) { joined = index; assertFalse(query.contains("FOR UPDATE")); }
            }
            assertTrue(lock >= 0 && joined > lock);
            String sql = fixture.queries.get(joined).replaceAll("\\s+", " ");
            for (String clause : List.of("origin_owner_matches", "dispatch_owner_matches", "dispatch_identity_matches", "dispatch_fact_matches", "dispatch_parameters_sha256",
                    "LEFT JOIN tianji_agent.agent_run o", "LEFT JOIN tianji_agent.agent_run d", "LEFT JOIN tianji_agent.agent_tool_invocation i",
                    "a.result IS NOT DISTINCT FROM i.outcome", "a.business_receipt_id IS NOT DISTINCT FROM i.business_receipt_id",
                    "a.last_error_code IS NOT DISTINCT FROM i.error_code", "'APPLIED'", "'SUCCEEDED'")) assertTrue(sql.contains(clause), clause);
            assertEquals(9, ToolActionIntentStore.PreparedActionResult.class.getRecordComponents().length);
            assertFalse(result.toString().contains("private-instance")); assertFalse(result.toString().contains("business-receipt"));
        }
    }

    @Test void full_history_stays_package_private_and_public_current_still_requires_current_authority() throws Exception {
        assertFalse(Modifier.isPublic(ToolActionIntentStore.class.getDeclaredMethod("historical", ToolActionRequest.class, ToolDescriptor.class).getModifiers()));
        assertEquals(15, ToolActionIntentSql.Row.class.getRecordComponents().length);
        try (var fixture = new Fixture(RESULT_TOOL)) {
            fixture.seed("APPLIED"); var authority = mock(ToolExecutionAuthorization.class);
            when(authority.authorize(any(), any())).thenReturn(ToolExecutionAuthorization.Decision.DENY);
            var store = fixture.store(authority);
            assertNotNull(store.historical(fixture.action, fixture.tool).orElseThrow().outcome()); verifyNoInteractions(authority);
            assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                    () -> store.current(run(RUN), lease(RUN), fixture.action, fixture.tool)).code());
            verify(authority).authorize(any(), eq(ToolExecutionAuthorization.Boundary.PREPARE));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TrustedAgentActor owner;
        final ToolDescriptor tool;
        final ToolActionRequest action;
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AtomicReference<AgentRunAuthorization.Request> run = new AtomicReference<>(run(RUN));
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW), databaseLease = new AtomicReference<>(NOW.plusSeconds(30)), wall = new AtomicReference<>(NOW);
        final AtomicReference<Instant> sourceDeadline = new AtomicReference<>(NOW.plusSeconds(600));
        final AtomicReference<String> sourceSnapshot = new AtomicReference<>(HASH);
        final Map<String, TrustedAgentActor> sourceOwners = new HashMap<>();
        final Map<String, Map<String, Object>> runtimeSources = new HashMap<>();
        final List<String> runtimeSourceReads = new ArrayList<>();
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> row = new AtomicReference<>();
        final AtomicReference<Runnable> advisoryWait = new AtomicReference<>(() -> { }), rowLockWait = new AtomicReference<>(() -> { }), afterJoined = new AtomicReference<>(() -> { });
        final AtomicBoolean sourceActive = new AtomicBoolean(true), failInsert = new AtomicBoolean(), storageActive = new AtomicBoolean(true), sourceAllowed = new AtomicBoolean(true), sourceTerminal = new AtomicBoolean();
        final AtomicLong inserts = new AtomicLong(), nano = new AtomicLong();
        final List<String> queries = new ArrayList<>();
        final MockedConstruction<JdbcTemplate> templates;
        final MockedConstruction<ToolExecutionStorageVerifier> verifiers;
        final AtomicReference<ToolExecutionStorageVerifier.SchemaProfile> storageProfile =
                new AtomicReference<>(ToolExecutionStorageVerifier.SchemaProfile.LEGACY);

        Fixture() throws Exception { this(TOOL); }
        Fixture(ToolDescriptor selected) throws Exception { this(selected, ACTOR); }
        Fixture(ToolDescriptor selected, TrustedAgentActor owner) throws Exception {
            this.owner = owner;
            tool = selected; action = new ToolActionRequest(owner, tool, "invoice-1", "v1", Map.of("amount", 20L));
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); when(ledger.usesDataSource(source)).thenReturn(true);
            when(ledger.executionMaterial(eq(owner), any())).thenAnswer(call -> {
                GetAgentRunQuery query = call.getArgument(1); return material(query == null ? run.get().runId() : query.runId());
            });
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenAnswer(call -> storageActive.get());
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(databaseNow.get()));
                doAnswer(call -> {
                    String sql = call.getArgument(0); queries.add(sql); RowMapper<?> mapper = call.getArgument(1);
                    if (sql.startsWith("SELECT lease_expires_at")) return sourceActive.get() ? List.of(mapper.mapRow(result(Map.of("lease_expires_at", databaseLease.get())), 0)) : List.of();
                    if (sql.startsWith("SELECT intent_id FROM tianji_agent.agent_tool_action_intent")) {
                        rowLockWait.get().run(); var current = matching(call);
                        return current == null ? List.of() : List.of(mapper.mapRow(result(Map.of("intent_id", current.get("intent_id"))), 0));
                    }
                    if (sql.startsWith("SELECT * FROM tianji_agent.agent_tool_action_intent") || sql.contains("FROM tianji_agent.agent_tool_action_intent a")) {
                        var current = matching(call);
                        if (current == null) return List.of();
                        var mapped = mapper.mapRow(result(new HashMap<>(current)), 0); afterJoined.get().run(); return List.of(mapped);
                    }
                    if (sql.contains("JOIN tianji_agent.agent_runtime_binding b USING (tenant_id,run_id)")) {
                        var values = (Object[]) call.getRawArguments()[2];
                        assertEquals(5, values.length);
                        assertEquals(owner.tenantId(), values[0]);
                        assertEquals(owner.subjectType().name(), values[1]);
                        assertEquals(owner.issuerFingerprint(), values[2]);
                        assertEquals(owner.subjectId(), values[3]);
                        String runId = (String) values[4];
                        runtimeSourceReads.add(runId);
                        var evidence = runtimeSources.get(runId);
                        return evidence == null ? List.of() : List.of(mapper.mapRow(result(evidence), 0));
                    }
                    throw new AssertionError("Unexpected controlled tool intent query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> { advisoryWait.get().run(); return null; }).when(template).query(startsWith("SELECT pg_advisory_xact_lock"), any(RowCallbackHandler.class), any(Object[].class));
                doAnswer(call -> {
                    if (failInsert.get()) throw new DataIntegrityViolationException("private arguments");
                    Object[] values = (Object[]) call.getRawArguments()[1];
                    var names = List.of("tenant_id", "intent_id", "owner_type", "owner_issuer", "owner_id", "action_key", "business_key", "business_version", "tool_key", "tool_version", "tool_sha256", "binding_sha256", "request_sha256", "logical_identity_sha256", "descriptor", "arguments", "origin_run_id", "origin_snapshot_sha256", "risk", "approval_policy_ref", "state", "created_at", "updated_at", "expires_at");
                    var stored = new HashMap<String, Object>(); for (int i = 0; i < names.size(); i++) stored.put(names.get(i), values[i]);
                    stored.put("revision", 1L); stored.put("dispatch_epoch", 0L); stored.put("side_effect_outcome", "NONE"); aliases(stored, false);
                    row.set(stored); inserts.incrementAndGet(); return 1;
                }).when(template).update(startsWith("INSERT INTO tianji_agent.agent_tool_action_intent"), any(Object[].class));
            });
            verifiers = mockConstruction(ToolExecutionStorageVerifier.class, (verifier, context) ->
                    when(verifier.verifyAndReturnProfile()).thenAnswer(call -> storageProfile.get()));
        }

        private Map<String, Object> matching(org.mockito.invocation.InvocationOnMock call) {
            var stored = row.get(); if (stored == null) return null;
            Object[] values = (Object[]) call.getRawArguments()[2];
            return values.length == 5 && Objects.equals(stored.get("tenant_id"), values[0]) && Objects.equals(stored.get("owner_type"), values[1])
                    && Objects.equals(stored.get("owner_issuer"), values[2]) && Objects.equals(stored.get("owner_id"), values[3]) && Objects.equals(stored.get("intent_id"), values[4]) ? stored : null;
        }
        private Map<String, Object> seedRuntimeSource(String runId, String occurrence) {
            var codec = new AgentJsonCodec();
            var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                    "required", List.of(), "additionalProperties", false));
            var limits = new TechnicalLimitsVO(Duration.ofMinutes(3), Duration.ofSeconds(10),
                    Duration.ofSeconds(10), 2, 4, 1, 4096, 1024);
            var configuration = new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                    new AgentRuntimeTemplateCommand.TemplateDependencyRefVO("instructions", 1, HASH), "registered-model",
                    List.of(new AgentRuntimeTemplateCommand.TemplateDependencyRefVO(tool.key(), tool.version(),
                            tool.reference().contentSha256())), null);
            var template = new AgentRuntimeTemplateCommand("runtime-tools", 1, RuntimeProfileEnum.RUNTIME_V2,
                    "runtime-executor", schema, schema, schema, configuration, limits);
            String templateSha = template.contentSha256();
            String schemaSha = codec.runtimeSchemaHash(schema);
            Instant createdAt = NOW.minusSeconds(60);
            Instant deadline = NOW.plusSeconds(90);
            var link = new AgentProcessLink("process-source", WorkerScopeTypeEnum.BPMN, "instance",
                    occurrence, templateSha, deadline.minusSeconds(30));
            var command = new LinkedAgentRuntimeCommand("submit-" + occurrence, link, "node-" + occurrence, template.getTemplateKey(),
                    template.getTemplateVersion(), templateSha, HASH, schemaSha, schemaSha, Map.of(), Map.of(), limits, deadline, "correlation");
            var snapshot = codec.runtimeSnapshot(owner.identityFingerprint(), runId, command, template);
            var evidence = new HashMap<String, Object>();
            evidence.put("definition_key", template.getTemplateKey());
            evidence.put("definition_version", template.getTemplateVersion());
            evidence.put("request_hash", codec.runtimeRequestHash(command));
            evidence.put("correlation_id", command.getCorrelationId());
            evidence.put("created_at", createdAt);
            evidence.put("deadline_at", deadline);
            evidence.put("execution_snapshot", codec.writeRuntimeSnapshot(snapshot));
            evidence.put("snapshot_sha256", snapshot.snapshotSha256());
            evidence.put("template_sha256", templateSha);
            evidence.put("configuration_sha256", command.getConfigurationSha256());
            evidence.put("node_execution_ref", command.getNodeExecutionRef());
            evidence.put("attempt_deadline_at", deadline);
            evidence.put("safety_policy", codec.write(limits.toWire(), AgentJsonCodec.MAX_SNAPSHOT_BYTES));
            evidence.put("content_sha256", templateSha);
            evidence.put("specification", new String(template.canonicalBytes(), StandardCharsets.UTF_8));
            runtimeSources.put(runId, evidence);
            return evidence;
        }

        private AgentLedgerService.ExecutionMaterial material(String id) {
            var actor = sourceOwners.getOrDefault(id, owner); var refs = sourceAllowed.get() ? List.of(tool.reference()) : List.<DefinitionResourceRef>of();
            var versions = sourceAllowed.get() ? Set.of(tool.versionKey()) : Set.<String>of();
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(10), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 2, 1000, 100,
                    BigDecimal.ONE, "USD", 1, versions, "retry-1", tool.policy().approvalPolicyRef());
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), refs, "route-1", "policy-1"), policy, Map.of());
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("fixed task", null, null), null);
            var authorization = new AgentRunAuthorization.Request(actor, id, "assistant", 1, HASH, HASH, sourceSnapshot.get(), "policy-1", false, sourceDeadline.get());
            var stored = new AgentRunDTO(id, "assistant", 1, null, sourceTerminal.get() ? AgentRunStatusEnum.COMPLETED : AgentRunStatusEnum.RUNNING,
                    null, !sourceActive.get(), 1, 2, 0, sourceDeadline.get(), NOW.minusSeconds(600), sourceTerminal.get() ? NOW.minusSeconds(1) : null);
            return new AgentLedgerService.ExecutionMaterial(stored, snapshot, authorization);
        }
        Map<String, Object> seed(String state) { return seed(state, Set.of("DISPATCHING", "APPLIED", "UNKNOWN").contains(state), tool.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !state.equals("AWAITING_APPROVAL")); }
        Map<String, Object> seed(String state, boolean dispatched, boolean approved) {
            var codec = new ToolExecutionJsonCodec(); var stored = new HashMap<String, Object>();
            stored.put("tenant_id", owner.tenantId()); stored.put("intent_id", UUID.fromString(action.intentId()));
            stored.put("owner_type", owner.subjectType().name()); stored.put("owner_issuer", owner.issuerFingerprint()); stored.put("owner_id", owner.subjectId());
            stored.put("action_key", action.actionKey()); stored.put("business_key", action.businessKey()); stored.put("business_version", action.businessVersion());
            stored.put("tool_key", tool.key()); stored.put("tool_version", tool.version()); stored.put("tool_sha256", tool.reference().contentSha256()); stored.put("binding_sha256", tool.policy().bindingSha256());
            stored.put("request_sha256", action.requestSha256()); stored.put("logical_identity_sha256", OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1", owner.identityFingerprint(), action.actionKey(), action.businessKey(), action.businessVersion()), Map.of()));
            stored.put("descriptor", codec.descriptor(tool)); stored.put("arguments", codec.arguments(tool, action.arguments()));
            stored.put("origin_run_id", RUN); stored.put("origin_snapshot_sha256", HASH); stored.put("risk", tool.policy().risk().name()); stored.put("approval_policy_ref", tool.policy().approvalPolicyRef());
            stored.put("state", state); stored.put("revision", 7L); stored.put("created_at", NOW.minusSeconds(60)); stored.put("updated_at", NOW);
            stored.put("expires_at", state.equals("EXPIRED") ? NOW.minusSeconds(1) : NOW.plusSeconds(300));
            stored.put("dispatch_epoch", dispatched ? 2L : 0L);
            if (dispatched) {
                stored.put("dispatch_run_id", DISPATCH_RUN); stored.put("dispatch_invocation_id", DISPATCH_INVOCATION); stored.put("dispatch_nonce_sha256", NONCE);
                stored.put("executor_issuer", EXECUTOR.issuerFingerprint()); stored.put("executor_id", EXECUTOR.subjectId());
            }
            String effect = state.equals("APPLIED") ? "CONFIRMED" : Set.of("DISPATCHING", "UNKNOWN").contains(state) ? "UNKNOWN" : "NONE"; stored.put("side_effect_outcome", effect);
            ToolExecutionOutcome outcome = state.equals("APPLIED") ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", "private-instance"), "business-receipt-1", null, new BigDecimal("0.1250"))
                    : Set.of("REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state) ? new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(state), Map.of(), null, "CONTROLLED_RESULT", state.equals("UNKNOWN") ? null : new BigDecimal("0.5000")) : null;
            if (outcome != null) { stored.put("result", codec.outcome(tool, outcome)); stored.put("business_receipt_id", outcome.businessReceiptId()); stored.put("last_error_code", outcome.errorCode()); }
            if (state.equals("EXPIRED")) stored.put("last_error_code", "INTENT_EXPIRED");
            if (!Set.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING").contains(state)) stored.put("closed_at", NOW.minusSeconds(1));
            if (approved) {
                var proof = proof(action); var encoded = codec.approval(proof, action);
                stored.put("approval_proof", encoded); stored.put("approval_proof_sha256", codec.approvalSha256(proof, action));
                stored.put("approval_source_namespace_sha256", proof.source().namespaceSha256()); stored.put("approval_receipt_id", proof.approval().approvalReceiptId());
                stored.put("approval_approved", true); stored.put("approval_consumed_at", NOW.minusSeconds(10)); stored.put("approval_expires_at", proof.approval().expiresAt());
            }
            aliases(stored, dispatched); row.set(stored); return stored;
        }
        private void aliases(Map<String, Object> stored, boolean dispatched) {
            for (String name : List.of("origin_owner_matches", "dispatch_owner_matches", "dispatch_identity_matches", "dispatch_fact_matches")) stored.put(name, true);
            if (dispatched) stored.put("dispatch_parameters_sha256", tool.parametersSha256(action.arguments()));
        }
        ToolActionIntentStore store(ToolExecutionAuthorization authority) { return store(authority, true); }
        ToolActionIntentStore store(ToolExecutionAuthorization authority, boolean ready) {
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return wall.get(); }
            };
            var store = new ToolActionIntentStore(source, ledger, authority, clock, nano::get); if (ready) store.verifyStorage(); return store;
        }
        @Override public void close() { verifiers.close(); templates.close(); }
    }

    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value instanceof Timestamp timestamp ? timestamp : value == null ? null : Timestamp.from((Instant) value); });
        when(result.getObject(anyString(), any(Class.class))).thenAnswer(call -> values.get(call.getArgument(0)));
        return result;
    }
    private static ToolDescriptor historyTool(boolean approval) {
        return new ToolDescriptor(approval ? "process.approvedHistory" : "process.history", 1, approval ? "approved_history" : "write_history", "Fixed history output",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(approval ? ToolPolicy.Risk.APPROVAL_REQUIRED : ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, approval ? "approval-1" : null));
    }
    private static ToolExecutionJsonCodec.ApprovalProofVO proof(ToolActionRequest action) {
        var consumer = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "approval-consumer", "https://idp.example/20");
        var binding = new ToolApprovalBinding(consumer, "approval-1", "b".repeat(64), "approval-release", "approval_flow", "c".repeat(64), "approve");
        var query = binding.query(action, "approval-receipt"); var command = query.sourceStart();
        var approval = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(),
                query.requesterType(), query.requesterSubjectId(), command.commandId(), "scope-1", "task-1", binding.releaseVersion(),
                binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(), OrchestrationRequestDigests.start(command), true,
                NOW.minusSeconds(25), NOW.minusSeconds(20), NOW.plusSeconds(10));
        return new ToolExecutionJsonCodec.ApprovalProofVO(1, new ToolExecutionJsonCodec.ApprovalSourceVO("process-main", 20, "https://idp.example/20", "https://process.example/api"), binding, approval);
    }
    private static AgentRunAuthorization.Request run(String id) { return new AgentRunAuthorization.Request(ACTOR, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(600)); }
    private static AgentRunLease lease(String id) { return new AgentRunLease(id, "worker", 1, NOW.minusSeconds(1)); }
}
