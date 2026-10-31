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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
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
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.start", 1, "start_process", "Fixed operation",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of(), Set.of()),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", HASH, null));
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
            verify(fixture.jdbc.get(), atLeastOnce()).query(contains("owner_issuer=? AND owner_id=? AND intent_id=?"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
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
            verify(fixture.jdbc.get()).query(contains("cancel_requested_at IS NULL FOR SHARE"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),
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
            verify(fixture.jdbc.get(), never()).query(startsWith("SELECT * FROM tianji_agent.agent_tool_action_intent"), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
            verify(fixture.connection).rollback();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AtomicReference<AgentRunAuthorization.Request> run = new AtomicReference<>(run(RUN));
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW), databaseLease = new AtomicReference<>(NOW.plusSeconds(30)), wall = new AtomicReference<>(NOW);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final AtomicReference<Map<String, Object>> row = new AtomicReference<>();
        final AtomicReference<Runnable> advisoryWait = new AtomicReference<>(() -> { });
        final AtomicBoolean sourceActive = new AtomicBoolean(true), failInsert = new AtomicBoolean();
        final AtomicLong inserts = new AtomicLong(), nano = new AtomicLong();
        final MockedConstruction<JdbcTemplate> templates;
        final MockedConstruction<ToolExecutionStorageVerifier> verifiers;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); when(ledger.usesDataSource(source)).thenReturn(true);
            var material = mock(AgentLedgerService.ExecutionMaterial.class, RETURNS_DEEP_STUBS);
            when(material.authorization()).thenAnswer(call -> run.get());
            when(material.snapshot().definition().dependencies().tools()).thenReturn(List.of(TOOL.reference()));
            when(material.snapshot().effectivePolicy().allowedToolVersions()).thenReturn(Set.of(TOOL.versionKey()));
            when(material.snapshot().effectivePolicy().maxToolCalls()).thenReturn(1);
            when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(material);
            templates = mockConstruction(JdbcTemplate.class, (template, context) -> {
                jdbc.set(template);
                when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenAnswer(call -> Timestamp.from(databaseNow.get()));
                doAnswer(call -> {
                    String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1);
                    if (sql.startsWith("SELECT lease_expires_at")) return sourceActive.get() ? List.of(mapper.mapRow(result(Map.of("lease_expires_at", databaseLease.get())), 0)) : List.of();
                    if (sql.startsWith("SELECT * FROM tianji_agent.agent_tool_action_intent")) return row.get() == null ? List.of() : List.of(mapper.mapRow(result(row.get()), 0));
                    throw new AssertionError("Unexpected controlled tool intent query");
                }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
                doAnswer(call -> { advisoryWait.get().run(); return null; }).when(template).query(startsWith("SELECT pg_advisory_xact_lock"), any(RowCallbackHandler.class), any(Object[].class));
                doAnswer(call -> {
                    if (failInsert.get()) throw new DataIntegrityViolationException("private arguments");
                    Object[] values = (Object[]) call.getRawArguments()[1];
                    var names = List.of("tenant_id", "intent_id", "owner_type", "owner_issuer", "owner_id", "action_key", "business_key", "business_version", "tool_key", "tool_version", "tool_sha256", "binding_sha256", "request_sha256", "logical_identity_sha256", "descriptor", "arguments", "origin_run_id", "origin_snapshot_sha256", "risk", "approval_policy_ref", "state", "created_at", "updated_at", "expires_at");
                    var stored = new HashMap<String, Object>(); for (int i = 0; i < names.size(); i++) stored.put(names.get(i), values[i]);
                    stored.put("revision", 1L); stored.put("side_effect_outcome", "NONE"); row.set(stored); inserts.incrementAndGet(); return 1;
                }).when(template).update(startsWith("INSERT INTO tianji_agent.agent_tool_action_intent"), any(Object[].class));
            });
            verifiers = mockConstruction(ToolExecutionStorageVerifier.class);
        }

        ToolActionIntentStore store(ToolExecutionAuthorization authority) {
            var clock = new Clock() {
                @Override public ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(ZoneId zone) { return this; }
                @Override public Instant instant() { return wall.get(); }
            };
            var store = new ToolActionIntentStore(source, ledger, authority, clock, nano::get); store.verifyStorage(); return store;
        }
        @Override public void close() { verifiers.close(); templates.close(); }
    }

    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value instanceof Timestamp timestamp ? timestamp : value == null ? null : Timestamp.from((Instant) value); });
        when(result.getObject(eq("intent_id"), eq(UUID.class))).thenReturn((UUID) values.get("intent_id")); return result;
    }
    private static AgentRunAuthorization.Request run(String id) { return new AgentRunAuthorization.Request(ACTOR, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(600)); }
    private static AgentRunLease lease(String id) { return new AgentRunLease(id, "worker", 1, NOW.minusSeconds(1)); }
}
