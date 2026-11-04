package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.CaseCompletionInbox.*;

class CaseCompletionInboxTest {
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00.123456Z");
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner", "https://idp.example/20");
    private static final ProcessActor SERVICE = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "case-worker", "https://idp.example/20");
    private static final String COMMAND = ProcessRequestDigest.hash(List.of("TIANJI_CASE_COMPLETION_V1", "20", "case-one"), Map.of());
    private static final DueKey KEY = new DueKey(20, COMMAND);
    private static final RegistrationKey REGISTRATION = new RegistrationKey(20, SERVICE.issuerFingerprint(), SERVICE.subjectId(), "source-one");
    private static final TerminalFacts COMPLETED = new TerminalFacts(TerminalStatus.COMPLETED, HASH, NOW.minusSeconds(1));
    private static final TerminalFacts TERMINATED = new TerminalFacts(TerminalStatus.TERMINATED, HASH, NOW.minusSeconds(1));

    @Test void identifiers_and_terminal_facts_reject_invalid_boundary_values() {
        assertThrows(IllegalArgumentException.class, () -> new DueKey(0, COMMAND));
        assertThrows(IllegalArgumentException.class, () -> new DueKey(20, "A".repeat(64)));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationKey(20, "LEGACY", "worker", "source"));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationKey(20, HASH, " worker", "source"));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationKey(20, HASH, "worker", "source\n"));
        assertThrows(IllegalArgumentException.class, () -> new TerminalFacts(TerminalStatus.COMPLETED, "bad", NOW));
        assertThrows(IllegalArgumentException.class, () -> new TerminalFacts(null, HASH, NOW));
        assertThrows(IllegalArgumentException.class, () -> new AppliedCommand(HASH, " execution"));
    }

    @Test void legal_delivery_shapes_include_both_late_forms_and_pending_verified_completion() {
        assertDoesNotThrow(() -> entry(State.PENDING, null, 0, null, null));
        assertDoesNotThrow(() -> entry(State.PENDING, COMPLETED, 3, null, null));
        assertDoesNotThrow(() -> entry(State.IGNORED_LATE, null, 0, DecisionCode.WAIT_ENDED, null));
        assertDoesNotThrow(() -> entry(State.IGNORED_LATE, COMPLETED, 0, DecisionCode.MESSAGE_CONSUMED, null));
        assertDoesNotThrow(() -> entry(State.DEAD, TERMINATED, 0, DecisionCode.CASE_TERMINATED, null));
        assertDoesNotThrow(() -> entry(State.APPLIED, COMPLETED, 0, DecisionCode.MESSAGE_APPLIED, "CASE_COMPLETION_SERVICE"));
    }

    @Test void nullable_authority_fields_cannot_bypass_applied_dead_or_terminated_invariants() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.APPLIED, COMPLETED, 0, null, "CASE_COMPLETION_SERVICE")),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.APPLIED, COMPLETED, 0, DecisionCode.MESSAGE_APPLIED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.APPLIED, null, 0, DecisionCode.MESSAGE_APPLIED, "CASE_COMPLETION_SERVICE")),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.DEAD, null, 0, null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.DEAD, null, 0, DecisionCode.CASE_TERMINATED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.PENDING, TERMINATED, 0, null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.IGNORED_LATE, TERMINATED, 0, DecisionCode.WAIT_ENDED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.DEAD, COMPLETED, 0, DecisionCode.MESSAGE_APPLIED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> entry(State.PENDING, null, -1, null, null)));
    }

    @Test void summaries_do_not_expose_source_service_or_native_identifiers() {
        assertEquals("CaseCompletionInbox.Entry[redacted]", entry(State.PENDING, null, 0, null, null).toString());
        assertFalse(REGISTRATION.toString().contains(SERVICE.subjectId()));
        assertFalse(KEY.toString().contains(COMMAND));
        assertFalse(COMPLETED.toString().contains(HASH));
    }

    @Test void application_requires_the_owning_source_transaction_before_any_jdbc_read() throws Exception {
        try (var f = new Fixture()) {
            assertThrows(IllegalStateException.class, () -> f.store.find(KEY));
            assertThrows(IllegalStateException.class, () -> f.store.lock(KEY));
            assertThrows(IllegalStateException.class, f.store::databaseNow);
            verify(f.jdbc.get(), never()).query(anyString(), any(RowMapper.class), any(Object[].class));
        }
    }

    @Test void find_and_lock_read_exact_tenant_key_and_lock_only_the_mutable_inbox() throws Exception {
        try (var f = new Fixture()) {
            var expected = entry(State.PENDING, null, 0, null, null); f.rows(expected, expected);
            assertEquals(Optional.of(expected), f.tx.execute(s -> f.store.find(KEY)));
            assertEquals(expected, f.tx.execute(s -> f.store.lock(KEY)));
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("tenant_id=?") && s.contains("completion_command_id=?") && !s.contains("FOR UPDATE")));
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("FOR UPDATE") && s.contains("tianji_case_completion_inbox")));
            assertFalse(f.sql.stream().anyMatch(s -> s.contains("FOR UPDATE") && s.contains("tianji_case_start_provenance")));
            verify(f.provenance.get(), atLeastOnce()).find(20, "case-one");
        }
    }

    @Test void jdbc_reader_rejects_partial_terminal_groups_and_sql_unknown_counterexamples() throws Exception {
        try (var f = new Fixture()) {
            for (var change : List.<Map<String, Object>>of(
                    Map.of("state", "APPLIED", "terminal_status", "COMPLETED", "terminal_sha256", HASH, "terminal_at", Timestamp.from(NOW), "decided_at", Timestamp.from(NOW), "applied_command_type", "CASE_COMPLETION_SERVICE"),
                    Map.of("state", "DEAD", "decided_at", Timestamp.from(NOW)),
                    Map.of("state", "DEAD", "decided_at", Timestamp.from(NOW), "decision_code", "CASE_TERMINATED"),
                    Map.of("terminal_status", "COMPLETED"))) {
                var row = row(entry(State.PENDING, null, 0, null, null)); row.putAll(change); f.before = row; f.updated = false;
                assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.find(KEY)));
            }
        }
    }

    @Test void preparation_is_atomic_linked_service_only_and_uses_rename_independent_completion_identity() throws Exception {
        try (var f = new Fixture()) {
            var pending = entry(State.PENDING, null, 0, null, null); f.rows(pending, pending);
            assertEquals(pending, f.tx.execute(s -> f.store.prepare(linked("source-one"))));
            verify(f.jdbc.get()).update(argThat(s -> s.startsWith("INSERT INTO") && s.contains("'PENDING'") && !s.contains("ON CONFLICT DO UPDATE")), any(Object[].class));
            assertTrue(f.bindings.stream().anyMatch(values -> java.util.Arrays.asList(values).contains(COMMAND)));
            clearInvocations(f.jdbc.get());
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.prepare(direct())));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void preparation_rechecks_durable_provenance_instead_of_trusting_a_constructed_entry() throws Exception {
        try (var f = new Fixture()) {
            when(f.provenance.get().find(20, "case-one")).thenReturn(Optional.of(linked("changed-source")));
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.prepare(linked("source-one"))));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void active_and_not_ready_wait_observations_preserve_failures_and_have_no_short_expiry() throws Exception {
        try (var f = new Fixture()) {
            for (var facts : new TerminalFacts[]{null, COMPLETED}) {
                var before = entry(State.PENDING, facts, 7, null, null);
                var after = new Entry(KEY, "case-one", REGISTRATION, State.PENDING, facts, NOW, NOW.plusSeconds(1), 7, null, null, null);
                f.rows(before, after);
                assertEquals(after, f.tx.execute(s -> f.store.reschedule(before, Duration.ofSeconds(1))));
                assertEquals(7, after.failureCount());
            }
            assertFalse(f.sql.stream().anyMatch(s -> s.toLowerCase().contains("retention") || s.toLowerCase().contains("expire")));
        }
    }

    @Test void completed_facts_can_be_saved_while_pending_but_cannot_be_changed_or_replaced_by_termination() throws Exception {
        try (var f = new Fixture()) {
            var before = entry(State.PENDING, null, 2, null, null);
            var after = new Entry(KEY, "case-one", REGISTRATION, State.PENDING, COMPLETED, NOW, NOW.plusSeconds(1), 2, null, null, null);
            f.rows(before, after);
            assertEquals(after, f.tx.execute(s -> f.store.reschedule(before, COMPLETED, Duration.ofSeconds(1))));
            clearInvocations(f.jdbc.get()); f.rows(after, after);
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.reschedule(after, TERMINATED, Duration.ofSeconds(1))));
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.reschedule(after,
                    new TerminalFacts(TerminalStatus.COMPLETED, "b".repeat(64), COMPLETED.terminalAt()), Duration.ofSeconds(1))));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void only_technical_failures_increment_the_budget_and_threshold_decides_dead() throws Exception {
        try (var f = new Fixture()) {
            var before = entry(State.PENDING, COMPLETED, 9, null, null);
            var after = entry(State.DEAD, COMPLETED, 10, DecisionCode.RETRY_EXHAUSTED, null); f.rows(before, after);
            assertEquals(after, f.tx.execute(s -> f.store.rescheduleFailure(before, Duration.ofSeconds(1), 10)));
            assertTrue(f.bindings.stream().anyMatch(v -> java.util.Arrays.asList(v).contains(10)));
        }
    }

    @Test void parent_late_decisions_accept_active_child_and_previously_verified_completed_child() throws Exception {
        try (var f = new Fixture()) {
            for (var facts : new TerminalFacts[]{null, COMPLETED}) {
                var before = entry(State.PENDING, facts, 0, null, null);
                var after = entry(State.IGNORED_LATE, facts, 0, DecisionCode.WAIT_ENDED, null); f.rows(before, after);
                assertEquals(after, f.tx.execute(s -> f.store.finish(before, State.IGNORED_LATE, DecisionCode.WAIT_ENDED)));
            }
            assertFalse(f.sql.stream().anyMatch(s -> s.contains("INSERT INTO") || s.contains("UPDATE") && s.contains("tianji_process_command_receipt")));
        }
    }

    @Test void whole_case_termination_is_dead_and_can_never_be_applied_as_success() throws Exception {
        try (var f = new Fixture()) {
            var before = entry(State.PENDING, null, 0, null, null);
            var after = entry(State.DEAD, TERMINATED, 0, DecisionCode.CASE_TERMINATED, null); f.rows(before, after);
            assertEquals(after, f.tx.execute(s -> f.store.finish(before, State.DEAD, DecisionCode.CASE_TERMINATED, TERMINATED)));
            clearInvocations(f.jdbc.get()); f.rows(before, before);
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.finish(before, State.APPLIED, DecisionCode.MESSAGE_APPLIED, TERMINATED, new AppliedCommand(HASH, "wait-execution"))));
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.finish(before, State.DEAD, DecisionCode.CASE_TERMINATED)));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void applied_requires_explicit_command_proof_and_exact_durable_service_command_binding() throws Exception {
        try (var f = new Fixture()) {
            var before = entry(State.PENDING, COMPLETED, 0, null, null);
            var after = entry(State.APPLIED, COMPLETED, 0, DecisionCode.MESSAGE_APPLIED, "CASE_COMPLETION_SERVICE"); f.rows(before, after);
            var proof = new AppliedCommand("b".repeat(64), "wait-execution");
            assertEquals(after, f.tx.execute(s -> f.store.finish(before, State.APPLIED, DecisionCode.MESSAGE_APPLIED, COMPLETED, proof)));
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("tianji_process_command_receipt") && s.contains("state='APPLIED'") && s.contains("command_type='CASE_COMPLETION_SERVICE'")));
            assertTrue(f.bindings.stream().anyMatch(v -> java.util.Arrays.asList(v).contains(proof.requestSha256()) && java.util.Arrays.asList(v).contains(proof.resourceId())));
            clearInvocations(f.jdbc.get()); f.rows(before, before);
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.finish(before, State.APPLIED, DecisionCode.MESSAGE_APPLIED, COMPLETED)));
            assertThrows(IllegalArgumentException.class, () -> f.tx.execute(s -> f.store.finish(before, State.DEAD, DecisionCode.SOURCE_UNAVAILABLE, COMPLETED, proof)));
            f.appliedCommandValid = false;
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.finish(before, State.APPLIED, DecisionCode.MESSAGE_APPLIED, COMPLETED, proof)));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void terminal_rows_and_stale_discovery_cannot_be_revived_or_overwritten() throws Exception {
        try (var f = new Fixture()) {
            var terminal = entry(State.DEAD, null, 0, DecisionCode.SOURCE_UNAVAILABLE, null); f.rows(terminal, terminal);
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.reschedule(terminal, Duration.ofSeconds(1))));
            var stale = entry(State.PENDING, null, 0, null, null);
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.finish(stale, State.IGNORED_LATE, DecisionCode.WAIT_ENDED)));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void discovery_is_bounded_claims_with_skip_locked_and_releases_its_transaction_before_apply() throws Exception {
        try (var f = new Fixture()) {
            f.rows(entry(State.PENDING, null, 0, null, null), entry(State.PENDING, null, 0, null, null));
            assertEquals(List.of(KEY), f.store.due(REGISTRATION, 32, Duration.ofSeconds(1)));
            verify(f.connection).commit();
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("FOR UPDATE SKIP LOCKED") && s.contains("ORDER BY next_attempt_at,completion_command_id") && s.contains("LIMIT ?")));
            assertTrue(f.bindings.stream().anyMatch(v -> java.util.Arrays.asList(v).contains(32) && java.util.Arrays.asList(v).contains(1000L)));
            assertThrows(IllegalArgumentException.class, () -> f.store.due(REGISTRATION, 33, Duration.ofSeconds(1)));
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> f.store.due(REGISTRATION, 1, Duration.ofSeconds(1))));
        }
    }

    @Test void orphan_discovery_survives_empty_registry_and_uses_only_bounded_parameterized_active_tuples() throws Exception {
        try (var f = new Fixture()) {
            f.rows(entry(State.PENDING, null, 0, null, null), entry(State.PENDING, null, 0, null, null));
            assertEquals(List.of(KEY), f.store.orphanDue(Set.of(), 32, Duration.ofSeconds(1)));
            assertEquals(List.of(KEY), f.store.orphanDue(Set.of(REGISTRATION), 32, Duration.ofSeconds(1)));
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("NOT EXISTS") && s.contains("VALUES") && !s.contains(SERVICE.subjectId())));
            assertTrue(f.sql.stream().anyMatch(s -> s.contains("ORDER BY next_attempt_at,tenant_id,completion_command_id") && s.contains("SKIP LOCKED")));
            var registrations = new java.util.HashSet<RegistrationKey>();
            for (int i = 0; i < 257; i++) registrations.add(new RegistrationKey(20, HASH, "worker", "source-" + i));
            assertThrows(IllegalArgumentException.class, () -> f.store.orphanDue(registrations, 32, Duration.ofSeconds(1)));
        }
    }

    private static Entry entry(State state, TerminalFacts facts, int failures, DecisionCode code, String appliedType) {
        return new Entry(KEY, "case-one", REGISTRATION, state, facts, NOW, NOW, failures, state == State.PENDING ? null : NOW, code, appliedType);
    }
    private static CaseStartProvenanceStore.Entry linked(String sourceId) {
        var seed = new CaseStartProvenanceStore.Seed("case-one", "LINKED_BPMN", "business", "correlation", "release", "governedCase", "case-def", HASH,
                OWNER, "START", "parent-start", HASH, SERVICE, "case-start", HASH, sourceId, "parent", "occurrence", "sourceElement", "workflow", "parent-def", HASH);
        return new CaseStartProvenanceStore.Entry(seed, NOW);
    }
    private static CaseStartProvenanceStore.Entry direct() {
        var seed = new CaseStartProvenanceStore.Seed("case-one", "DIRECT", "business", "correlation", "release", "governedCase", "case-def", HASH,
                SERVICE, "CASE_START", "case-start", HASH, SERVICE, "case-start", HASH, null, null, null, null, null, null, null);
        return new CaseStartProvenanceStore.Entry(seed, NOW);
    }
    private static Map<String, Object> row(Entry entry) {
        var row = new HashMap<String, Object>(); row.put("tenant_id", entry.key().tenantId()); row.put("completion_command_id", entry.key().completionCommandId());
        row.put("case_instance_id", entry.caseInstanceId()); row.put("service_type", "SERVICE"); row.put("service_issuer", entry.registration().serviceIssuer()); row.put("service_subject", entry.registration().serviceSubject()); row.put("source_id", entry.registration().sourceId());
        row.put("state", entry.state().name()); row.put("terminal_status", entry.terminalFacts() == null ? null : entry.terminalFacts().status().name());
        row.put("terminal_sha256", entry.terminalFacts() == null ? null : entry.terminalFacts().sha256()); row.put("terminal_at", entry.terminalFacts() == null ? null : Timestamp.from(entry.terminalFacts().terminalAt()));
        row.put("created_at", Timestamp.from(entry.createdAt())); row.put("next_attempt_at", Timestamp.from(entry.nextAttemptAt())); row.put("failure_count", entry.failureCount());
        row.put("decided_at", entry.decidedAt() == null ? null : Timestamp.from(entry.decidedAt())); row.put("decision_code", entry.decisionCode() == null ? null : entry.decisionCode().name()); row.put("applied_command_type", entry.appliedCommandType()); return row;
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>(); final AtomicReference<CaseStartProvenanceStore> provenance = new AtomicReference<>();
        final org.mockito.MockedConstruction<JdbcTemplate> statements;
        final org.mockito.MockedConstruction<CaseStartProvenanceStore> origins;
        final org.mockito.MockedConstruction<ProcessApiSchemaVerifier> schema;
        final TransactionTemplate tx; final CaseCompletionInbox store;
        final List<String> sql = new ArrayList<>(); final List<Object[]> bindings = new ArrayList<>();
        Map<String, Object> before = Map.of(), after = Map.of(); boolean updated; boolean appliedCommandValid = true;
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            statements = mockConstruction(JdbcTemplate.class, (value, ignored) -> {
                jdbc.set(value);
                when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(value.queryForObject(eq("SELECT clock_timestamp()"), eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
                when(value.queryForObject(contains("tianji_process_command_receipt"), eq(Boolean.class), any(Object[].class))).thenAnswer(i -> { sql.add(i.getArgument(0)); bindings.add(arguments(i, 2)); return appliedCommandValid; });
                when(value.update(anyString(), any(Object[].class))).thenAnswer(i -> { sql.add(i.getArgument(0)); bindings.add(arguments(i, 1)); updated = true; return 1; });
                when(value.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class))).thenAnswer(i -> {
                    String text = i.getArgument(0); sql.add(text); bindings.add(arguments(i, 2)); var values = updated ? after : before;
                    if (values.isEmpty()) return List.of();
                    var rs = mock(ResultSet.class);
                    when(rs.getString(anyString())).thenAnswer(call -> { Object result = values.get(call.getArgument(0)); return result == null ? null : result.toString(); });
                    when(rs.getLong(anyString())).thenAnswer(call -> ((Number) values.getOrDefault(call.getArgument(0), 0L)).longValue());
                    when(rs.getInt(anyString())).thenAnswer(call -> ((Number) values.getOrDefault(call.getArgument(0), 0)).intValue());
                    when(rs.getObject(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
                    when(rs.getTimestamp(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
                    RowMapper<Object> mapper = i.getArgument(1); return List.of(mapper.mapRow(rs, 0));
                });
            });
            origins = mockConstruction(CaseStartProvenanceStore.class, (value, ignored) -> {
                provenance.set(value); when(value.find(20, "case-one")).thenReturn(Optional.of(linked("source-one")));
            });
            schema = mockConstruction(ProcessApiSchemaVerifier.class);
            var manager = new DataSourceTransactionManager(source); tx = new TransactionTemplate(manager); store = new CaseCompletionInbox(source, manager, "tianji_process");
        }
        void rows(Entry original, Entry changed) { before = row(original); after = row(changed); updated = false; }
        private static Object[] arguments(org.mockito.invocation.InvocationOnMock call, int first) {
            Object[] all = call.getArguments(); return java.util.Arrays.copyOfRange(all, first, all.length);
        }
        @Override public void close() { schema.close(); origins.close(); statements.close(); }
    }
}
