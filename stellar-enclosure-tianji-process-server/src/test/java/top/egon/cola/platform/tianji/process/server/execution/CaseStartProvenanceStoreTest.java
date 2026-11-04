package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.Entry;
import top.egon.cola.platform.tianji.process.server.execution.CaseStartProvenanceStore.Seed;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseStartProvenanceStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00.123456Z");
    private static final String HASH = "a".repeat(64);
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner", "https://idp.example/20");
    private static final ProcessActor EXECUTOR = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "case-worker", "https://service.example/20");

    @Test void unverified_storage_and_foreign_or_missing_transactions_fail_before_provenance_io() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(false);
            assertTrue(store.usesDataSource(fixture.source)); assertFalse(store.usesDataSource(mock(DataSource.class)));
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")));
            store.verifyStorage();
            assertEquals(1, fixture.verifier.constructed().size());
            assertThrows(IllegalStateException.class, () -> store.find(20, "case-1"));
            assertThrows(IllegalStateException.class, () -> store.lockScope(20, "case-1"));
            var otherSource = mock(DataSource.class); when(otherSource.getConnection()).thenReturn(fixture.connection);
            var otherTransaction = new TransactionTemplate(new DataSourceTransactionManager(otherSource));
            assertThrows(IllegalStateException.class, () -> otherTransaction.execute(status -> store.find(20, "case-1")));
            verify(fixture.jdbc.get(), never()).query(contains("tianji_case_start_provenance"), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class));
        }
    }

    @Test void direct_reads_bind_exact_owner_executor_and_release_and_do_not_lock_immutable_rows() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.row = row(direct()); var store = fixture.store(true);
            var result = assertDoesNotThrow(() -> fixture.transaction.execute(status -> store.find(20, "case-1").orElseThrow()));
            assertEquals(new Entry(direct(), NOW), result);
            verify(fixture.jdbc.get()).query(argThat(sql -> sql.contains("p.tenant_id=?") && sql.contains("p.case_instance_id=?")
                            && sql.contains("e.subject_type=p.executor_type") && sql.contains("e.subject_issuer=p.executor_issuer")
                            && sql.contains("e.subject_id=p.executor_subject") && sql.contains("e.command_type=p.case_start_command_type")
                            && sql.contains("o.command_type=p.owner_start_command_type") && sql.contains("d.release_key=p.release_key")
                            && !sql.contains("FOR UPDATE")), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), eq(20L), eq("case-1"));
            assertFalse(result.toString().contains(OWNER.subjectId())); assertFalse(result.seed().toString().contains(HASH));
            fixture.row = null;
            assertTrue(fixture.transaction.execute(status -> store.find(20, "case-1")).isEmpty());
        }
    }

    @Test void linked_owner_parent_resource_and_service_executor_resource_are_verified_separately() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.row = row(linked()); var store = fixture.store(true);
            var result = assertDoesNotThrow(() -> fixture.transaction.execute(status -> store.find(20, "case-1").orElseThrow()));
            assertEquals(OWNER, result.seed().owner()); assertEquals(EXECUTOR, result.seed().executor());
            assertNotEquals(result.seed().owner().issuerFingerprint(), result.seed().executor().issuerFingerprint());
            assertEquals("parent-1", result.seed().parentProcessInstanceId());
            assertEquals(result, fixture.transaction.execute(status -> store.byExecutor(EXECUTOR, "case-start").orElseThrow()));
            assertEquals(result, fixture.transaction.execute(status -> store.byLinkedOccurrence(20, "parent-1", "activity-1").orElseThrow()));
            verify(fixture.jdbc.get()).query(argThat(sql -> sql.contains("p.parent_process_instance_id=?") && sql.contains("p.source_occurrence_id=?")
                            && !sql.contains("p.source_id=?")), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), eq(20L), eq("parent-1"), eq("activity-1"));
        }
    }

    @Test void every_copied_command_and_model_fact_is_checked_instead_of_trusting_a_join_or_case_id() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true);
            for (var change : List.of(Map.entry("executor_state", "PENDING"), Map.entry("owner_state", "PENDING"),
                    Map.entry("executor_request_sha256", "b".repeat(64)), Map.entry("owner_request_sha256", "b".repeat(64)),
                    Map.entry("executor_resource_id", "parent-1"), Map.entry("owner_resource_id", "case-1"),
                    Map.entry("executor_release_key", "other"), Map.entry("owner_release_key", "other"),
                    Map.entry("executor_correlation_id", "other"), Map.entry("owner_correlation_id", "other"),
                    Map.entry("bound_case_engine_definition_id", "other"), Map.entry("bound_case_model_sha256", "b".repeat(64)),
                    Map.entry("bound_parent_engine_definition_id", "other"), Map.entry("bound_parent_model_sha256", "b".repeat(64)),
                    Map.entry("case_model_type", "BPMN"), Map.entry("parent_model_type", "CMMN"))) {
                fixture.row = row(linked()); fixture.row.put(change.getKey(), change.getValue());
                assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")), change.getKey());
            }
            for (String missing : List.of("source_id", "parent_process_instance_id", "source_occurrence_id", "source_element_id", "parent_model_type",
                    "parent_definition_key", "parent_engine_definition_id", "parent_model_sha256", "bound_case_model_sha256", "bound_parent_model_sha256")) {
                fixture.row = row(linked()); fixture.row.put(missing, null);
                assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")), missing);
            }
            fixture.row = row(direct()); fixture.row.put("owner_resource_id", "parent-1");
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")));
            fixture.row = row(direct()); fixture.row.put("tenant_id", 21L);
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")));
            fixture.row = row(direct()); fixture.row.put("case_instance_id", "other");
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.find(20, "case-1")));
        }
    }

    @Test void inserts_are_immutable_and_use_database_time_and_failures_roll_back_the_caller_transaction() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.row = row(direct()); var store = fixture.store(true);
            when(fixture.jdbc.get().update(anyString(), any(Object[].class))).thenReturn(1);
            assertEquals(new Entry(direct(), NOW), fixture.transaction.execute(status -> store.insert(direct())));
            verify(fixture.jdbc.get()).update(argThat(sql -> sql.startsWith("INSERT INTO \"tianji_process\".tianji_case_start_provenance")
                    && sql.contains("clock_timestamp()") && !sql.contains("ON CONFLICT") && !sql.contains("UPDATE")), any(Object[].class));
            when(fixture.jdbc.get().update(anyString(), any(Object[].class))).thenReturn(0);
            assertThrows(IllegalStateException.class, () -> fixture.transaction.execute(status -> store.insert(direct())));
            verify(fixture.connection).rollback();
        }
    }

    @Test void case_scope_advisory_key_matches_the_fixed_domain_and_never_locks_the_fact_row() throws Exception {
        try (var fixture = new Fixture()) {
            var store = fixture.store(true);
            fixture.transaction.executeWithoutResult(status -> store.lockScope(20, "case-1"));
            verify(fixture.jdbc.get()).query(eq("SELECT pg_advisory_xact_lock(?)"), any(RowCallbackHandler.class), eq(634725036871827310L));
            verify(fixture.jdbc.get(), never()).update(anyString(), any(Object[].class));
            verify(fixture.jdbc.get(), never()).query(contains("FOR UPDATE"), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class));
            when(fixture.jdbc.get().queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> fixture.transaction.executeWithoutResult(status -> store.lockScope(20, "case-1")));
        }
    }

    @Test void immutable_seed_rejects_partial_linked_identity_and_invalid_shared_references() {
        assertDoesNotThrow(CaseStartProvenanceStoreTest::direct); assertDoesNotThrow(CaseStartProvenanceStoreTest::linked);
        assertThrows(IllegalArgumentException.class, () -> seed("DIRECT", OWNER, "START", "start", HASH, OWNER, "case-start", HASH, null));
        assertThrows(IllegalArgumentException.class, () -> seed("DIRECT", OWNER, "CASE_START", "case-start", HASH, EXECUTOR, "case-start", HASH, null));
        assertThrows(IllegalArgumentException.class, () -> seed("LINKED_BPMN", OWNER, "START", "parent-start", HASH, OWNER, "case-start", HASH, "source"));
        assertThrows(IllegalArgumentException.class, () -> seed("LINKED_BPMN", OWNER, "START", "parent-start", HASH, EXECUTOR, "case-start", HASH, null));
        assertThrows(IllegalArgumentException.class, () -> seed("DIRECT", OWNER, "CASE_START", "case-start", "bad", OWNER, "case-start", "bad", null));
        assertThrows(RuntimeException.class, () -> new Entry(direct(), null));
    }

    private static Seed direct() { return seed("DIRECT", OWNER, "CASE_START", "case-start", HASH, OWNER, "case-start", HASH, null); }
    private static Seed linked() { return seed("LINKED_BPMN", OWNER, "START", "parent-start", HASH, EXECUTOR, "case-start", HASH, "source"); }
    private static Seed seed(String kind, ProcessActor owner, String ownerType, String ownerCommand, String ownerHash,
                             ProcessActor executor, String caseCommand, String caseHash, String source) {
        boolean linked = "LINKED_BPMN".equals(kind);
        return new Seed("case-1", kind, "business", "correlation", "release", "governedCase", "case-definition", HASH,
                owner, ownerType, ownerCommand, ownerHash, executor, caseCommand, caseHash, source,
                linked ? "parent-1" : null, linked ? "activity-1" : null, linked ? "caseSource" : null,
                linked ? "parent" : null, linked ? "parent-definition" : null, linked ? HASH : null);
    }
    private static Map<String, Object> row(Seed seed) {
        var row = new HashMap<String, Object>();
        row.put("tenant_id", 20L); row.put("case_instance_id", seed.caseInstanceId()); row.put("start_kind", seed.startKind());
        row.put("business_key", seed.businessKey()); row.put("correlation_id", seed.correlationId()); row.put("release_key", seed.releaseKey());
        row.put("case_model_type", "CMMN"); row.put("case_definition_key", seed.caseDefinitionKey()); row.put("case_engine_definition_id", seed.caseEngineDefinitionId()); row.put("case_model_sha256", seed.caseModelSha256());
        row.put("owner_type", seed.owner().subjectType().name()); row.put("owner_issuer", seed.owner().issuerFingerprint()); row.put("owner_subject", seed.owner().subjectId());
        row.put("owner_start_command_type", seed.ownerStartCommandType()); row.put("owner_start_command_id", seed.ownerStartCommandId()); row.put("owner_start_request_sha256", seed.ownerStartRequestSha256());
        row.put("executor_type", seed.executor().subjectType().name()); row.put("executor_issuer", seed.executor().issuerFingerprint()); row.put("executor_subject", seed.executor().subjectId());
        row.put("case_start_command_type", "CASE_START"); row.put("case_start_command_id", seed.caseStartCommandId()); row.put("case_start_request_sha256", seed.caseStartRequestSha256());
        row.put("source_id", seed.sourceId()); row.put("parent_process_instance_id", seed.parentProcessInstanceId()); row.put("source_occurrence_id", seed.sourceOccurrenceId()); row.put("source_element_id", seed.sourceElementId());
        row.put("parent_model_type", "LINKED_BPMN".equals(seed.startKind()) ? "BPMN" : null); row.put("parent_definition_key", seed.parentDefinitionKey()); row.put("parent_engine_definition_id", seed.parentEngineDefinitionId()); row.put("parent_model_sha256", seed.parentModelSha256());
        row.put("created_at", Timestamp.from(NOW));
        row.put("executor_state", "APPLIED"); row.put("executor_request_sha256", seed.caseStartRequestSha256()); row.put("executor_resource_id", seed.caseInstanceId()); row.put("executor_release_key", seed.releaseKey()); row.put("executor_correlation_id", seed.correlationId());
        row.put("owner_state", "APPLIED"); row.put("owner_request_sha256", seed.ownerStartRequestSha256()); row.put("owner_resource_id", "DIRECT".equals(seed.startKind()) ? seed.caseInstanceId() : seed.parentProcessInstanceId()); row.put("owner_release_key", seed.releaseKey()); row.put("owner_correlation_id", seed.correlationId());
        row.put("bound_case_engine_definition_id", seed.caseEngineDefinitionId()); row.put("bound_case_model_sha256", seed.caseModelSha256()); row.put("bound_parent_engine_definition_id", seed.parentEngineDefinitionId()); row.put("bound_parent_model_sha256", seed.parentModelSha256());
        return row;
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class); final Connection connection = mock(Connection.class);
        final AtomicReference<JdbcTemplate> jdbc = new AtomicReference<>();
        final org.mockito.MockedConstruction<JdbcTemplate> statements;
        final org.mockito.MockedConstruction<ProcessApiSchemaVerifier> verifier;
        final TransactionTemplate transaction; Map<String, Object> row;
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            statements = mockConstruction(JdbcTemplate.class, (value, ignored) -> {
                jdbc.set(value); when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"), eq(Boolean.class))).thenReturn(true);
                when(value.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Entry>>any(), any(Object[].class))).thenAnswer(invocation -> {
                    if (row == null) return List.of();
                    var result = mock(ResultSet.class);
                    when(result.getString(anyString())).thenAnswer(call -> (String) row.get(call.getArgument(0)));
                    when(result.getLong(anyString())).thenAnswer(call -> ((Number) row.get(call.getArgument(0))).longValue());
                    when(result.getTimestamp(anyString())).thenAnswer(call -> (Timestamp) row.get(call.getArgument(0)));
                    RowMapper<Entry> mapper = invocation.getArgument(1); return List.of(mapper.mapRow(result, 0));
                });
            });
            verifier = mockConstruction(ProcessApiSchemaVerifier.class);
            transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        }
        CaseStartProvenanceStore store(boolean verified) { var store = new CaseStartProvenanceStore(source, "tianji_process"); if (verified) store.verifyStorage(); return store; }
        @Override public void close() { verifier.close(); statements.close(); }
    }
}
