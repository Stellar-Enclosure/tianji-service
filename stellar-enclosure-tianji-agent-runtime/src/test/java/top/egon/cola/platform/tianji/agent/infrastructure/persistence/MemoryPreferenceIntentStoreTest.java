package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Controlled JDBC markers validate tuple/epoch/fence boundaries; SQL execution and lock races remain in the opt-in PG fixture. */
class MemoryPreferenceIntentStoreTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final String HASH = "a".repeat(64), RUN = "10000000-0000-0000-0000-000000000001";
    private static final URI RESOURCE = URI.create("https://agent.example/api");

    @Test void unverified_storage_never_reads_or_reserves_owner_evidence() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true);
        var store = new MemoryPreferenceIntentStore(source, ledger);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> store.currentEvidence(run(), binding(), RESOURCE)).code()); verifyNoInteractions(source);
    }

    @Test void a_late_issue_reply_cannot_attach_after_another_reservation_epoch() throws Exception {
        var source = source(); var ledger = ledger(source); var values = values(MemoryPreferenceIntent.State.RESERVED, 2); var jdbc = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(values, jdbc); var verifiers = mockConstruction(MemoryPreferenceIntentStorageVerifier.class)) {
            var store = new MemoryPreferenceIntentStore(source, ledger); store.verifyStorage();
            var ticket = new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(90));
            assertFalse(store.complete(reservation(1), ticket));
            verify(jdbc.get(), never()).update(anyString(), any(Object[].class));
            verify(jdbc.get()).query(contains("cancel_requested_at IS NULL"), org.mockito.ArgumentMatchers.<RowMapper<Integer>>any(), eq(20L), eq(ACTOR.issuerFingerprint()), eq("alice"), eq(RUN));
        }
    }

    @Test void immutable_binding_drift_and_foreign_cancellation_never_mutate_the_current_intent() throws Exception {
        var source = source(); var ledger = ledger(source); var values = values(MemoryPreferenceIntent.State.RESERVED, 1); var jdbc = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(values, jdbc); var verifiers = mockConstruction(MemoryPreferenceIntentStorageVerifier.class)) {
            var store = new MemoryPreferenceIntentStore(source, ledger); store.verifyStorage();
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                    () -> store.currentEvidence(run(), binding(), URI.create("https://AGENT.example/api"))).code());
            var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "other", "https://idp.example/20");
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                    () -> store.cancel(other, reservation(1).intentId(), 1, "cancel", HASH)).code());
            verify(jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void issuance_failure_records_are_epoch_fenced_and_definite_denials_close_only_the_matching_reservation() throws Exception {
        var source = source(); var ledger = ledger(source); var values = values(MemoryPreferenceIntent.State.RESERVED, 2); var jdbc = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(values, jdbc); var verifiers = mockConstruction(MemoryPreferenceIntentStorageVerifier.class)) {
            var store = new MemoryPreferenceIntentStore(source, ledger); store.verifyStorage();
            assertFalse(store.recordIssueFailure(reservation(1), "ISSUE_UNAVAILABLE", false)); verify(jdbc.get(), never()).update(anyString(), any(Object[].class));
            assertTrue(store.recordIssueFailure(reservation(2), "ISSUE_UNAVAILABLE", false));
            verify(jdbc.get()).update(startsWith("UPDATE tianji_agent.agent_memory_preference_intent SET last_error_code="), eq("ISSUE_UNAVAILABLE"), eq(Timestamp.from(NOW)), eq(reservation(2).intentId()));
            assertTrue(store.recordIssueFailure(reservation(2), "OWNER_DENIED", true));
            verify(jdbc.get()).update(contains("grant_id=NULL,ciphertext=NULL"), eq("REJECTED"), eq(Timestamp.from(NOW)), eq(Timestamp.from(NOW)), eq("OWNER_DENIED"), eq(0), isNull(), isNull(), isNull(), eq(reservation(2).intentId()));
        }
    }

    @Test void an_expired_worker_epoch_or_substituted_command_list_cannot_cross_the_fact_fence() throws Exception {
        var source = source(); var ledger = ledger(source); var values = values(MemoryPreferenceIntent.State.CLAIMED, 1); var jdbc = new AtomicReference<JdbcTemplate>();
        values.put("lease_epoch", 2L); var evidence = new MemoryPreferenceIntent.Evidence(reservation(1), ticket());
        var stale = new MemoryPreferenceIntent.Lease(evidence, "worker", 1, NOW.plusSeconds(30), "event", 0, 2, HASH, 3);
        try (var templates = templates(values, jdbc); var verifiers = mockConstruction(MemoryPreferenceIntentStorageVerifier.class)) {
            var store = new MemoryPreferenceIntentStore(source, ledger); store.verifyStorage();
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class, () -> transaction.executeWithoutResult(status -> store.verifyFence(stale))).code());
            var current = new MemoryPreferenceIntent.Lease(evidence, "worker", 2, NOW.plusSeconds(30), "event", 0, 2, HASH, 3);
            assertEquals(current, store.currentLease(current));
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class, () -> store.currentLease(stale)).code());
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class, () -> store.sealCommands(current, 2, "b".repeat(64))).code());
            verify(jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    private static org.mockito.MockedConstruction<JdbcTemplate> templates(Map<String, Object> values, AtomicReference<JdbcTemplate> captured) {
        return mockConstruction(JdbcTemplate.class, (template, context) -> {
            captured.set(template); when(template.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
            when(template.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
            when(template.queryForObject(startsWith("SELECT set_config"), eq(String.class))).thenReturn("pg_catalog,tianji_agent,public");
            doAnswer(call -> {
                String sql = call.getArgument(0); RowMapper<?> mapper = call.getArgument(1);
                if (sql.startsWith("SELECT 1 FROM tianji_agent.agent_run")) return List.of(1);
                if (sql.startsWith("SELECT * FROM tianji_agent.agent_memory_preference_intent")) return List.of(mapper.mapRow(result(values), 0));
                throw new AssertionError("Unexpected controlled intent query");
            }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        });
    }

    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getLong(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0L : value.longValue(); });
        when(result.getInt(anyString())).thenAnswer(call -> { var value = (Number) values.get(call.getArgument(0)); return value == null ? 0 : value.intValue(); });
        when(result.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(values.get(call.getArgument(0))));
        when(result.getTimestamp(anyString())).thenAnswer(call -> { var value = (Instant) values.get(call.getArgument(0)); return value == null ? null : Timestamp.from(value); });
        when(result.getObject(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getObject(eq("intent_id"), eq(UUID.class))).thenReturn((UUID) values.get("intent_id")); return result;
    }

    private static Map<String, Object> values(MemoryPreferenceIntent.State state, long issueEpoch) {
        var reservation = reservation(issueEpoch); var values = new HashMap<String, Object>();
        values.putAll(Map.of("intent_id", reservation.intentId(), "tenant_id", 20L, "actor_issuer", ACTOR.issuerFingerprint(), "actor_type", "USER", "actor_id", "alice", "run_id", RUN,
                "scope_id", "research", "binding_id", "source", "policy_ref", binding().policyRef(), "resource_uri", RESOURCE.toASCIIString()));
        values.putAll(Map.of("definition_key", "assistant", "definition_version", 1L, "definition_sha256", HASH, "request_sha256", HASH, "snapshot_sha256", HASH, "execution_policy_ref", "execution-policy",
                "session_required", true, "run_deadline_at", NOW.plusSeconds(60), "deadline_at", reservation.deadlineAt(), "binding_sha256", reservation.bindingSha256()));
        values.putAll(Map.of("issue_epoch", issueEpoch, "state", state.name(), "revision", 3L, "lease_epoch", 0L, "next_index", 0, "attempt_count", 0, "maximum_attempts", 3, "next_attempt_at", NOW, "created_at", NOW.minusSeconds(1), "updated_at", NOW));
        if (state.sealed()) { values.put("grant_id", ticket().grantId()); values.put("ciphertext", ticket().ciphertext()); values.put("grant_expires_at", ticket().expiresAt()); }
        if (state == MemoryPreferenceIntent.State.CLAIMED) { values.putAll(Map.of("source_event_id", "event", "lease_owner", "worker", "lease_epoch", 1L, "lease_expires_at", NOW.plusSeconds(30), "total_count", 2, "commands_sha256", HASH, "attempt_count", 1)); }
        return values;
    }
    private static DataSource source() throws Exception { var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true); return source; }
    private static AgentLedgerService ledger(DataSource source) { var ledger = mock(AgentLedgerService.class); when(ledger.usesDataSource(source)).thenReturn(true); var material = mock(AgentLedgerService.ExecutionMaterial.class); when(material.authorization()).thenReturn(run()); when(ledger.executionMaterial(eq(ACTOR), any())).thenReturn(material); return ledger; }
    private static AgentRunAuthorization.Request run() { return new AgentRunAuthorization.Request(ACTOR, RUN, "assistant", 1, HASH, HASH, HASH, "execution-policy", true, NOW.plusSeconds(60)); }
    private static MemoryPreferenceSourceBinding binding() { return new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", "preferences@1#" + "b".repeat(64)); }
    private static MemoryPreferenceIntent.Reservation reservation(long epoch) { var deadline = NOW.plusSeconds(120); return new MemoryPreferenceIntent.Reservation(run(), binding(), RESOURCE, deadline, epoch, MemoryPreferenceIntent.intentId(run(), "research"), MemoryPreferenceIntent.fingerprint(run(), binding(), RESOURCE, deadline)); }
    private static AgentExecutionGrant.Ticket ticket() { return new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", NOW.plusSeconds(90)); }
}
