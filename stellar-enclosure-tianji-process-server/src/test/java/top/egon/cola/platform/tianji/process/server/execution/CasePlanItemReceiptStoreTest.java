package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
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

class CasePlanItemReceiptStoreTest {
    private static final Instant NOW = Instant.parse("2026-11-04T08:00:00.123456Z");
    private static final String HASH = "a".repeat(64), MODEL = "c".repeat(64);
    private static final ProcessActor ACTOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://idp.example/20");

    @Test void storage_and_owned_transaction_are_required_before_any_statement() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(false);
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> store.find(context(), "cmd", HASH)));
            verifyNoInteractions(f.jdbc.get(), f.provenance.get());
            store.verifyStorage(); clearInvocations(f.jdbc.get(), f.provenance.get());
            assertThrows(IllegalStateException.class, () -> store.find(context(), "cmd", HASH));
            verifyNoInteractions(f.jdbc.get(), f.provenance.get());
            assertTrue(store.usesDataSource(f.source)); assertFalse(store.usesDataSource(mock(DataSource.class)));
        }
    }

    @Test void insert_preserves_all_result_fields_and_audit_then_leaves_base_application_to_its_owner() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(true); var entry = entry(); var values = new AtomicReference<Object[]>();
            when(f.jdbc.get().update(anyString(), any(Object[].class))).thenAnswer(i -> { values.set((Object[]) i.getRawArguments()[1]); return 1; });
            f.tx.executeWithoutResult(s -> store.insert(context(), entry));
            assertNotNull(values.get()); assertEquals(24, values.get().length);
            assertEquals(List.of(20L, "USER", ACTOR.issuerFingerprint(), "operator", "CASE_PLAN_ITEM", "cmd", HASH,
                    "case-1", "item-1", "primaryTask", "START", "release", "governedCase", "ENABLED", HASH,
                    "ACTIVE", "b".repeat(64), Timestamp.from(NOW), "CMMN", MODEL, "case-def", 7L, 2L, 3L), List.of(values.get()));
            verify(f.jdbc.get()).update(startsWith("INSERT INTO \"tianji_process\".tianji_case_plan_item_receipt"), any(Object[].class));
            verify(f.jdbc.get(), never()).update(startsWith("UPDATE "), any(Object[].class));
            verify(f.provenance.get()).find(20, "case-1");
        }
    }

    @Test void invalid_current_role_versions_and_issuer_cannot_insert_an_old_decision() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(true);
            for (var context : List.of(new CaseContext(ACTOR, context().resourceKey(), Set.of("8"), context().deadlineAt(), 2, 3),
                    new CaseContext(ACTOR, context().resourceKey(), Set.of("7"), context().deadlineAt(), 3, 3),
                    new CaseContext(ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://other.example/20"), context().resourceKey(), Set.of("7"), context().deadlineAt(), 2, 3))) {
                assertThrows(IllegalStateException.class, () -> f.tx.executeWithoutResult(s -> store.insert(context, entry())));
            }
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
        }
    }

    @Test void insert_requires_the_exact_pending_base_source_binding_and_rolls_back_on_row_count_failure() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(true);
            f.pending.put("request_sha256", "d".repeat(64));
            assertThrows(ProcessConflictException.class, () -> f.tx.executeWithoutResult(s -> store.insert(context(), entry())));
            verify(f.jdbc.get(), never()).update(anyString(), any(Object[].class));
            f.pending.put("request_sha256", HASH); f.pending.put("state", "APPLIED");
            assertThrows(IllegalStateException.class, () -> f.tx.executeWithoutResult(s -> store.insert(context(), entry())));
            f.pending.put("state", "PENDING"); when(f.jdbc.get().update(anyString(), any(Object[].class))).thenReturn(0);
            assertThrows(IllegalStateException.class, () -> f.tx.executeWithoutResult(s -> store.insert(context(), entry())));
            verify(f.connection, atLeastOnce()).rollback();
        }
    }

    @Test void replay_returns_the_stored_transition_without_native_rows_and_allows_a_new_current_lane_role() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(true); f.replay.add(row());
            var refreshed = new CaseContext(ACTOR, context().resourceKey(), Set.of("8"), context().deadlineAt(), 5, 6);
            assertEquals(Optional.of(entry()), f.tx.execute(s -> store.find(refreshed, "cmd", HASH)));
            verify(f.jdbc.get()).query(argThat(sql -> sql.contains("b.state='APPLIED'") && sql.contains("b.resource_id=r.plan_item_instance_id")
                            && sql.contains("b.request_sha256=r.request_sha256") && sql.contains("b.correlation_id=p.correlation_id")
                            && sql.contains("d.model_sha256=r.definition_sha256") && !sql.contains("FOR UPDATE") && !sql.contains("ACT_CMMN")),
                    org.mockito.ArgumentMatchers.<RowMapper<CasePlanItemReceiptStore.Entry>>any(), eq(20L), eq(ACTOR.issuerFingerprint()), eq("operator"), eq("cmd"));
            assertThrows(ProcessConflictException.class, () -> f.tx.execute(s -> store.find(refreshed, "cmd", "d".repeat(64))));
        }
    }

    @Test void replay_rejects_corrupt_authority_transition_binding_and_ambiguous_results() throws Exception {
        try (var f = new Fixture()) {
            var store = f.store(true);
            var mutations = Map.<String, Object>ofEntries(Map.entry("subject_type", "SERVICE"), Map.entry("command_type", "START"),
                    Map.entry("model_type", "BPMN"), Map.entry("base_state", "PENDING"), Map.entry("base_resource_id", "other-item"),
                    Map.entry("base_request_sha256", "d".repeat(64)), Map.entry("base_release_key", "other-release"),
                    Map.entry("base_correlation_id", "other-correlation"), Map.entry("definition_sha256", "d".repeat(64)),
                    Map.entry("engine_definition_id", "other-def"), Map.entry("authorized_role_id", 0L), Map.entry("resulting_state", "COMPLETED"),
                    Map.entry("resulting_version", HASH), Map.entry("auth_version", -1L));
            for (var change : mutations.entrySet()) {
                var row = row(); row.put(change.getKey(), change.getValue()); f.replay.clear(); f.replay.add(row);
                assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> store.find(context(), "cmd", HASH)), change.getKey());
            }
            f.replay.clear(); f.replay.add(row()); f.replay.add(row());
            assertThrows(IllegalStateException.class, () -> f.tx.execute(s -> store.find(context(), "cmd", HASH)));
        }
    }

    @Test void entry_validation_and_summaries_do_not_turn_historical_audit_into_current_authority() {
        assertThrows(IllegalArgumentException.class, () -> new CasePlanItemReceiptStore.Entry(ACTOR, HASH, result(), MODEL, "case-def", "0", 2, 3));
        assertThrows(IllegalArgumentException.class, () -> new CasePlanItemReceiptStore.Entry(ACTOR, HASH, result(), MODEL, "case-def", "7", -1, 3));
        assertFalse(entry().toString().contains("operator")); assertFalse(entry().toString().contains(MODEL));
    }

    private static CaseContext context() { return new CaseContext(ACTOR, "tianji.api.process.case.actPlanItem", Set.of("7"), NOW.plusSeconds(30), 2, 3); }
    private static CaseOperationResult result() { return new CaseOperationResult("cmd", "case-1", "item-1", "primaryTask", CasePlanItemActionEnum.START, "release", "governedCase", CasePlanItemStateEnum.ENABLED, HASH, CasePlanItemStateEnum.ACTIVE, "b".repeat(64), NOW); }
    private static CasePlanItemReceiptStore.Entry entry() { return new CasePlanItemReceiptStore.Entry(ACTOR, HASH, result(), MODEL, "case-def", "7", 2, 3); }
    private static Map<String,Object> row() {
        var m = new HashMap<String,Object>(); m.put("tenant_id",20L); m.put("subject_type","USER"); m.put("subject_issuer",ACTOR.issuerFingerprint()); m.put("subject_id","operator"); m.put("command_type","CASE_PLAN_ITEM");
        m.put("command_id","cmd"); m.put("request_sha256",HASH); m.put("case_instance_id","case-1"); m.put("plan_item_instance_id","item-1"); m.put("plan_item_definition_key","primaryTask"); m.put("action","START");
        m.put("release_key","release"); m.put("definition_key","governedCase"); m.put("previous_state","ENABLED"); m.put("previous_version",HASH); m.put("resulting_state","ACTIVE"); m.put("resulting_version","b".repeat(64));
        m.put("occurred_at",Timestamp.from(NOW)); m.put("model_type","CMMN"); m.put("definition_sha256",MODEL); m.put("engine_definition_id","case-def"); m.put("authorized_role_id",7L); m.put("auth_version",2L); m.put("policy_version",3L);
        m.put("base_state","APPLIED"); m.put("base_resource_id","item-1"); m.put("base_request_sha256",HASH); m.put("base_release_key","release"); m.put("base_correlation_id","correlation"); m.put("source_correlation_id","correlation"); return m;
    }
    private static ResultSet resultSet(Map<String,Object> row) throws Exception {
        var rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(i -> { var v=row.get(i.getArgument(0)); return v==null?null:v.toString(); });
        when(rs.getLong(anyString())).thenAnswer(i -> { var v=row.get(i.getArgument(0)); return v==null?0L:((Number)v).longValue(); });
        when(rs.getTimestamp(anyString())).thenAnswer(i -> row.get(i.getArgument(0))); return rs;
    }
    private static final class Fixture implements AutoCloseable {
        final DataSource source=mock(DataSource.class); final Connection connection=mock(Connection.class);
        final AtomicReference<JdbcTemplate> jdbc=new AtomicReference<>(); final AtomicReference<CaseStartProvenanceStore> provenance=new AtomicReference<>();
        final org.mockito.MockedConstruction<JdbcTemplate> statements; final org.mockito.MockedConstruction<CaseStartProvenanceStore> origins;
        final org.mockito.MockedConstruction<ProcessApiSchemaVerifier> verifiers; final TransactionTemplate tx;
        final List<Map<String,Object>> replay=new ArrayList<>(); final Map<String,Object> pending=new HashMap<>();
        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            statements=mockConstruction(JdbcTemplate.class,(value,ignored)-> { jdbc.set(value);
                when(value.queryForObject(startsWith("SELECT NOT pg_is_in_recovery()"),eq(Boolean.class))).thenReturn(true);
                when(value.queryForObject(eq("SELECT clock_timestamp()"),eq(Timestamp.class))).thenReturn(Timestamp.from(NOW));
                when(value.query(anyString(),org.mockito.ArgumentMatchers.<RowMapper<Object>>any(),any(Object[].class))).thenAnswer(i -> {
                    String sql=i.getArgument(0); RowMapper<?> mapper=i.getArgument(1); var result=new ArrayList<Object>();
                    if (sql.contains("SELECT r.*")) { for (int n=0;n<replay.size();n++) result.add(mapper.mapRow(resultSet(replay.get(n)),n)); }
                    else result.add(mapper.mapRow(resultSet(pending),0)); return result;
                });
            });
            origins=mockConstruction(CaseStartProvenanceStore.class,(value,ignored)-> { provenance.set(value);
                var seed=new CaseStartProvenanceStore.Seed("case-1","DIRECT","business","correlation","release","governedCase","case-def",MODEL,
                        ACTOR,"CASE_START","start",HASH,ACTOR,"start",HASH,null,null,null,null,null,null,null);
                var proof=new CaseStartProvenanceStore.Entry(seed,NOW.minusSeconds(10));
                when(value.find(20,"case-1")).thenReturn(Optional.of(proof));
            });
            verifiers=mockConstruction(ProcessApiSchemaVerifier.class); tx=new TransactionTemplate(new DataSourceTransactionManager(source));
            pending.put("state","PENDING"); pending.put("resource_id",null); pending.put("request_sha256",HASH); pending.put("release_key","release"); pending.put("correlation_id","correlation");
        }
        CasePlanItemReceiptStore store(boolean ready) { var store=new CasePlanItemReceiptStore(source,"tianji_process"); if(ready)store.verifyStorage(); clearInvocations(jdbc.get(),provenance.get()); return store; }
        @Override public void close() { verifiers.close(); origins.close(); statements.close(); }
    }
}
