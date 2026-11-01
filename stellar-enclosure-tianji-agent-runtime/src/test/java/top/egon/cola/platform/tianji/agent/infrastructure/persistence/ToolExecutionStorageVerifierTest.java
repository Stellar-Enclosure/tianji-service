package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ToolExecutionStorageVerifierTest {
    private static final String CAPABILITY = "agent_tool_capability", INTENT = "agent_tool_action_intent", INVOCATION = "agent_tool_invocation",
            ACCESS = "agent_tool_result_access";

    @Test void the_result_access_companion_is_one_closed_insert_only_table_without_a_result_copy() throws Exception {
        String sql = resultAccessSql(); var contract = ToolExecutionStorageVerifier.resultAccessContract(sql);
        assertEquals(Set.of(ACCESS), contract.keySet());
        var table = contract.get(ACCESS);
        var columns = Set.of("tenant_id", "run_id", "access_id", "reservation_id", "owner_type", "owner_issuer", "owner_id", "capability_id",
                "capability_binding_sha256", "capability_issue_epoch", "lease_epoch", "tool_key", "tool_version", "tool_sha256", "backend_binding_sha256",
                "parameters_sha256", "intent_id", "request_sha256", "risk", "source_action_revision", "source_result_sha256", "authorization_audit",
                "authorization_checked_at", "authorization_expires_at", "created_at");
        assertEquals(columns, table.columns().keySet()); assertEquals(25, table.columns().size());
        assertTrue(table.columns().values().stream().allMatch(MemoryStorageVerifier.Column::notNull));
        for (String column : Set.of("reservation_id", "owner_type", "owner_issuer", "owner_id", "capability_binding_sha256", "tool_key", "tool_sha256",
                "backend_binding_sha256", "parameters_sha256", "request_sha256", "risk", "source_result_sha256")) {
            assertTrue(table.columns().get(column).cCollation(), column);
        }
        assertEquals(Set.of("tool_result_access_identity", "tool_result_access_source", "tool_result_access_authorization", "tool_result_access_time"), table.checks().keySet());
        assertEquals(4, table.checks().size());
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_pkey PRIMARY KEY (tenant_id,run_id,access_id)"));
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_budget_key UNIQUE (tenant_id,run_id,reservation_id)"));
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_run_fkey FOREIGN KEY (tenant_id,run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION"));
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_budget_fkey FOREIGN KEY (tenant_id,run_id,reservation_id) REFERENCES tianji_agent.agent_budget_reservation (tenant_id,run_id,reservation_id) ON DELETE NO ACTION"));
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_capability_fkey FOREIGN KEY (tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) REFERENCES tianji_agent.agent_tool_capability (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) ON DELETE NO ACTION"));
        assertTrue(sql.contains("CONSTRAINT agent_tool_result_access_intent_fkey FOREIGN KEY (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,risk) REFERENCES tianji_agent.agent_tool_action_intent (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk) ON DELETE NO ACTION"));
        assertTrue(sql.contains("CREATE INDEX agent_tool_result_access_intent_idx ON tianji_agent.agent_tool_result_access (tenant_id,intent_id,created_at,run_id,access_id);"));
        assertFalse(sql.contains("ON DELETE CASCADE")); assertFalse(sql.contains("DEFAULT"));
    }

    @Test void the_three_table_contract_preserves_tool_slots_business_intents_and_invocation_receipts() throws Exception {
        String sql = sql(); var contract = ToolExecutionStorageVerifier.contract(sql);
        assertEquals(Set.of(CAPABILITY, INTENT, INVOCATION), contract.keySet());
        assertEquals(27, contract.get(CAPABILITY).columns().size()); assertEquals(5, contract.get(CAPABILITY).checks().size());
        assertEquals(43, contract.get(INTENT).columns().size()); assertEquals(11, contract.get(INTENT).checks().size());
        assertEquals(34, contract.get(INVOCATION).columns().size()); assertEquals(10, contract.get(INVOCATION).checks().size());
        for (var table : contract.values()) {
            assertTrue(table.columns().get("owner_type").cCollation()); assertTrue(table.columns().get("owner_issuer").cCollation());
            assertFalse(table.columns().containsKey("user_token")); assertFalse(table.columns().containsKey("deleted_at"));
        }
        assertFalse(contract.get(CAPABILITY).columns().containsKey("intent_id"));
        assertTrue(contract.get(INTENT).columns().get("logical_identity_sha256").cCollation());
        assertEquals("timestamp with time zone", contract.get(INTENT).columns().get("expires_at").type());
        assertTrue(sql.contains("UNIQUE (tenant_id,logical_identity_sha256)"));
        assertTrue(sql.contains("FOREIGN KEY (tenant_id,dispatch_run_id,dispatch_invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id)"));
        assertFalse(sql.contains("ON DELETE CASCADE"));
    }

    @Test void evidence_namespace_and_optional_groups_cannot_be_weakened_without_changing_the_contract() throws Exception {
        String sql = sql(); var original = ToolExecutionStorageVerifier.contract(sql);
        assertNotNull(original.get(CAPABILITY)); assertNotNull(original.get(INTENT)); assertNotNull(original.get(INVOCATION));
        var changed = ToolExecutionStorageVerifier.contract(sql.replace("'^9[0-9]{62}$'", "'^[89][0-9]{62}$'"));
        assertNotEquals(original.get(CAPABILITY).checks().get("tool_capability_ticket"), changed.get(CAPABILITY).checks().get("tool_capability_ticket"));
        var weakened = ToolExecutionStorageVerifier.contract(sql.replace("AND ciphertext IS NOT NULL AND expires_at IS NOT NULL", "AND ciphertext IS NOT NULL OR expires_at IS NOT NULL"));
        assertNotEquals(original.get(CAPABILITY).checks().get("tool_capability_ticket"), weakened.get(CAPABILITY).checks().get("tool_capability_ticket"));
        assertNotEquals(MemoryStorageVerifier.booleanSignature("(a AND b) OR (c AND d)"), MemoryStorageVerifier.booleanSignature("a AND (b OR c) AND d"));
    }

    @Test void readiness_refuses_an_ambient_transaction_before_touching_the_data_source() {
        var source = mock(DataSource.class); TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> new ToolExecutionStorageVerifier(source).verify()); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source);
    }

    @Test void source_and_catalog_membership_forms_preserve_the_existing_grouped_signature() {
        assertEquals(MemoryStorageVerifier.booleanSignature("owner_type = ANY (ARRAY['USER','SERVICE'])"),
                MemoryStorageVerifier.booleanSignature("((owner_type)::text = ANY ((ARRAY['USER'::character varying, 'SERVICE'::character varying])::text[]))"));
        assertEquals(MemoryStorageVerifier.booleanSignature("expires_at<=created_at+'168:00:00'::interval"),
                MemoryStorageVerifier.booleanSignature("(expires_at <= (created_at + '168:00:00'::interval))"));
    }

    @Test void expired_action_requires_a_nonnull_error_before_comparing_the_terminal_code() throws Exception {
        assertTrue(sql().contains("last_error_code IS NOT NULL AND last_error_code='INTENT_EXPIRED'"),
                "A NULL terminal code must make the EXPIRED branch false rather than SQL UNKNOWN");
    }

    @Test void all_preexisting_migration_bytes_remain_frozen() throws Exception {
        var expected = Map.ofEntries(
                Map.entry("V1__create_agent_ledger.sql", "6dd146caf9b1ef1179f14daf9dfecd8d7bfa40629d7c17d12fcb5d477b93d775"),
                Map.entry("V2__add_run_governance.sql", "4f305619009d2d74c4c35b998153ff3a622b1c8913a648f51399f2a2caeb1472"),
                Map.entry("V3__add_adk_session_store.sql", "f5e7cd1372b488d8bec92a8140879dc0face399d5455fd2dcd997b74552a79bd"),
                Map.entry("V4__add_adk_session_codec_versions.sql", "71400d814a464658c539b240a3f81c18285454176508aa887b61232e7cd6b66f"),
                Map.entry("V5__bind_agent_ownership_to_issuer.sql", "d32dd0f5f6a24498bef76e3ad25d0868f12508a5c3ec3cbd868189881a32f8eb"),
                Map.entry("V6__add_execution_grant_evidence.sql", "f0a5fb9e32efcfb23cda45163164c15ff70393abb462325a953a091523709590"),
                Map.entry("V7__add_process_dispatch_fence.sql", "7f7a0d65a5430d7db0dd679ea81de575f5c83bdfef9864e9cfeef071266551a3"),
                Map.entry("V8__add_service_execution_grant.sql", "03a16259600629ea112289164f602ae19c6155e92fdb522762c99778aac48450"),
                Map.entry("V9__add_context_snapshots.sql", "0e055e689d72951488c03a7bc2e9638dee0e66038fe1b205b0cfbf91433a5ad2"),
                Map.entry("V10__add_memory_facts_and_index.sql", "f26117a6d7c1fc6890d399d886a8b4982964d67744ed77315d95efa217e08c61"),
                Map.entry("V11__bind_memory_grant_ticket_to_principal.sql", "8f3456c9b9b01b20ee37d46371bee108f9d27133eca27814a4e89bcc89f6bdc5"),
                Map.entry("V12__add_memory_preference_intent.sql", "767c1903e08fc9c60cb65802d06c40a60c0e8f9279de9272dd0e9da880dfb122"));
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(entry.getKey()))));
        }
    }

    @Test void the_finalized_tool_journal_version_is_frozen_after_its_single_creation() throws Exception {
        byte[] raw = resource(ToolExecutionStorageVerifier.FILE);
        assertEquals("f0c642e27eb749d569aa3f94a953f6e6f94e00da9304c2e1f3d0f9102c1a26b3", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        assertEquals(new String(raw, StandardCharsets.UTF_8), ToolExecutionStorageVerifier.migration());
    }

    @Test void the_finalized_result_access_journal_is_frozen_after_its_single_creation() throws Exception {
        byte[] raw = resource(ToolExecutionStorageVerifier.RESULT_ACCESS_FILE);
        assertEquals("88876815f5a6662c9b3378bb15f5a6ae6edebc92007a66f43e177a15dff3760a", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
    }

    @Test void readiness_uses_bounded_query_only_repeatable_read_on_the_primary() throws Exception {
        var catalog = new Catalog(); var captured = new AtomicReference<JdbcTemplate>(); var statements = new ArrayList<String>();
        var source = source(); var connection = source.getConnection();
        try (var templates = templates(source, catalog, captured, statements)) {
            new ToolExecutionStorageVerifier(source).verify();
            verify(captured.get()).setQueryTimeout(5); verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(connection, never()).setReadOnly(true); verify(connection).commit(); verify(connection, never()).rollback();
            verify(captured.get(), never()).execute(anyString()); verify(captured.get(), never()).update(anyString(), any(Object[].class));
            assertTrue(statements.stream().allMatch(statement -> statement.startsWith("SELECT ")));
            assertTrue(statements.stream().anyMatch(statement -> statement.contains("NOT c.relrowsecurity AND NOT c.relforcerowsecurity") && statement.contains("pg_trigger")));
        }
    }

    @Test void result_access_readiness_is_optional_for_v13_but_requires_the_complete_v14_history() throws Exception {
        var missing = new Catalog();
        try (var templates = templates(source(), missing, new AtomicReference<>(), new ArrayList<>())) {
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertThrows(AgentLedgerException.class, () -> new ToolExecutionStorageVerifier(missing.source).verifyResultAccess()).code());
        }
        var catalog = new Catalog(true); catalog.updatePrivileges.put(ACCESS, false);
        try (var templates = templates(source(), catalog, new AtomicReference<>(), new ArrayList<>())) {
            new ToolExecutionStorageVerifier(catalog.source).verify();
            new ToolExecutionStorageVerifier(catalog.source).verifyResultAccess();
        }
        assertResultAccessRejected(value -> value.history.getLast().put("checksum", 42));
        assertResultAccessRejected(value -> value.history.add(new HashMap<>(Map.of("version", "15", "success", false))));
    }

    @Test void result_access_catalog_keys_indexes_checks_and_minimum_privileges_fail_closed_on_drift() throws Exception {
        assertResultAccessRejected(catalog -> catalog.columns.get(ACCESS).removeFirst());
        assertResultAccessRejected(catalog -> catalog.columns.get(ACCESS).getFirst().put("atthasdef", true));
        assertResultAccessRejected(catalog -> find(catalog.checks.get(ACCESS), "conname", "tool_result_access_authorization").put("expression", "true"));
        assertResultAccessRejected(catalog -> catalog.keys.get(ACCESS).removeIf(row -> INTENT.equals(row.get("target"))));
        assertResultAccessRejected(catalog -> catalog.indexes.get(ACCESS).removeLast());
        assertResultAccessRejected(catalog -> catalog.tablePrivileges.put(ACCESS, false));
    }

    @Test void changed_or_duplicate_known_history_is_not_ready_but_future_success_does_not_replace_known_versions() throws Exception {
        assertRejected(catalog -> catalog.history.getFirst().put("checksum", 42));
        assertRejected(catalog -> catalog.history.getFirst().put("script", "changed.sql"));
        assertRejected(catalog -> catalog.history.getFirst().put("type", "JDBC"));
        assertRejected(catalog -> catalog.history.add(new HashMap<>(catalog.history.getLast())));
        assertRejected(catalog -> catalog.history.removeFirst());
        assertRejected(catalog -> catalog.history.add(new HashMap<>(Map.of("version", "14", "success", false))));
        var catalog = new Catalog(); catalog.history.add(new HashMap<>(Map.of("version", "14", "success", true)));
        try (var templates = templates(source(), catalog, new AtomicReference<>(), new ArrayList<>())) { new ToolExecutionStorageVerifier(catalog.source).verify(); }
    }

    @Test void exact_columns_defaults_collations_and_table_kind_are_required() throws Exception {
        assertRejected(catalog -> catalog.columns.get(CAPABILITY).removeFirst());
        assertRejected(catalog -> find(catalog.columns.get(CAPABILITY), "attname", "owner_issuer").put("collname", "other"));
        assertRejected(catalog -> find(catalog.columns.get(CAPABILITY), "attname", "owner_id").put("attnotnull", false));
        assertRejected(catalog -> find(catalog.columns.get(INTENT), "attname", "dispatch_epoch").put("default_value", "1"));
        assertRejected(catalog -> catalog.columns.get(CAPABILITY).getFirst().put("attidentity", "a"));
        assertRejected(catalog -> catalog.columns.get(CAPABILITY).getFirst().put("relispartition", true));
        assertRejected(catalog -> catalog.columns.get(CAPABILITY).getFirst().put("no_inheritance", false));
    }

    @Test void weakened_unvalidated_checks_and_changed_cyclic_foreign_keys_are_rejected() throws Exception {
        assertRejected(catalog -> catalog.checks.get(CAPABILITY).getFirst().put("convalidated", false));
        assertRejected(catalog -> catalog.checks.get(CAPABILITY).getFirst().put("enforced", false));
        assertRejected(catalog -> find(catalog.checks.get(CAPABILITY), "conname", "tool_capability_ticket").put("expression", "true"));
        assertRejected(catalog -> catalog.keys.get(INTENT).removeIf(row -> INVOCATION.equals(row.get("target"))));
        assertRejected(catalog -> find(catalog.keys.get(CAPABILITY), "contype", "f").put("confdeltype", "c"));
        assertRejected(catalog -> find(catalog.keys.get(CAPABILITY), "contype", "f").put("confupdtype", "c"));
        assertRejected(catalog -> find(catalog.keys.get(CAPABILITY), "contype", "f").put("confmatchtype", "f"));
        assertRejected(catalog -> find(catalog.keys.get(CAPABILITY), "contype", "f").put("condeferrable", true));
    }

    @Test void indexes_and_row_behavior_cannot_silently_change_the_journal_contract() throws Exception {
        assertRejected(catalog -> catalog.indexes.get(INTENT).getLast().put("predicate", "state='UNKNOWN'"));
        assertRejected(catalog -> catalog.indexes.get(INTENT).getFirst().put("opclasses", new String[]{"pg_catalog.text_ops"}));
        assertRejected(catalog -> catalog.indexes.get(INTENT).getFirst().put("matching_collations", false));
        assertRejected(catalog -> catalog.indexes.get(INTENT).getFirst().put("indisvalid", false));
        assertRejected(catalog -> catalog.indexes.get(INTENT).getFirst().put("no_include", false));
        assertRejected(catalog -> catalog.indexes.get(INTENT).getFirst().put("nulls_distinct", false));
        assertRejected(catalog -> catalog.safePrivileges = false);
        assertRejected(catalog -> catalog.primary = false);
    }

    @Test void database_failure_diagnostics_never_echo_tool_or_credential_content() throws Exception {
        var catalog = new Catalog(); catalog.failure = new DataAccessResourceFailureException("private tool payload and sealed ticket"); var captured = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(source(), catalog, captured, new ArrayList<>())) {
            var failure = assertThrows(AgentLedgerException.class, () -> new ToolExecutionStorageVerifier(catalog.source).verify());
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertEquals("Tool execution journal storage is unavailable or changed", failure.getMessage()); assertNull(failure.getCause());
        }
    }

    private static void assertRejected(Consumer<Catalog> mutation) throws Exception {
        var catalog = new Catalog(); mutation.accept(catalog); var source = source(); var captured = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(source, catalog, captured, new ArrayList<>())) {
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertThrows(AgentLedgerException.class, () -> new ToolExecutionStorageVerifier(source).verify()).code());
            verify(source.getConnection()).rollback();
        }
    }

    private static void assertResultAccessRejected(Consumer<Catalog> mutation) throws Exception {
        var catalog = new Catalog(true); mutation.accept(catalog); var source = source(); var captured = new AtomicReference<JdbcTemplate>();
        try (var templates = templates(source, catalog, captured, new ArrayList<>())) {
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    assertThrows(AgentLedgerException.class, () -> new ToolExecutionStorageVerifier(source).verifyResultAccess()).code());
            verify(source.getConnection()).rollback();
        }
    }

    private static DataSource source() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
        return source;
    }

    private static org.mockito.MockedConstruction<JdbcTemplate> templates(DataSource source, Catalog catalog, AtomicReference<JdbcTemplate> captured, List<String> statements) {
        catalog.source = source;
        return mockConstruction(JdbcTemplate.class, (template, context) -> {
            captured.set(template);
            when(template.queryForObject(anyString(), eq(Boolean.class))).thenAnswer(call -> {
                statements.add(call.getArgument(0)); assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                var holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(source);
                assertNotNull(holder); assertTrue(holder.getTimeToLiveInSeconds() > 0 && holder.getTimeToLiveInSeconds() <= 15);
                if (catalog.failure != null) throw catalog.failure;
                return catalog.primary;
            });
            when(template.queryForObject(anyString(), eq(String.class))).thenAnswer(call -> { statements.add(call.getArgument(0)); return "pg_catalog,tianji_agent,public"; });
            doAnswer(call -> {
                statements.add(call.getArgument(0)); RowCallbackHandler callback = call.getArgument(1);
                for (var row : catalog.history) callback.processRow(result(row)); return null;
            }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowCallbackHandler>any());
            doAnswer(call -> {
                String statement = call.getArgument(0); statements.add(statement); RowCallbackHandler callback = call.getArgument(1);
                String table = ((Object[]) call.getRawArguments()[2])[0].toString();
                var rows = statement.contains("FROM pg_attribute") ? catalog.columns.get(table) : statement.contains("FROM pg_constraint") ? catalog.checks.get(table) : null;
                if (rows == null) throw new AssertionError("Unexpected controlled tool catalog callback");
                for (var row : rows) callback.processRow(result(row)); return null;
            }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowCallbackHandler>any(), any(Object[].class));
            doAnswer(call -> {
                String statement = call.getArgument(0); statements.add(statement); RowMapper<?> mapper = call.getArgument(1);
                String table = ((Object[]) call.getRawArguments()[2])[0].toString();
                boolean mandatoryUpdate = statement.contains("AND has_table_privilege(current_user,c.oid,'UPDATE') AS safe");
                var rows = statement.contains("FROM pg_constraint") ? catalog.keys.get(table) : statement.contains("FROM pg_index") ? catalog.indexes.get(table)
                        : statement.contains("has_table_privilege") ? List.of(Map.<String, Object>of("safe", catalog.safePrivileges && catalog.tablePrivileges.getOrDefault(table, true)
                        && (!mandatoryUpdate || catalog.updatePrivileges.getOrDefault(table, true)),
                        "update_allowed", catalog.updatePrivileges.getOrDefault(table, true))) : null;
                if (rows == null) throw new AssertionError("Unexpected controlled tool catalog query");
                var result = new ArrayList<Object>(); for (var row : rows) result.add(mapper.mapRow(result(row), result.size())); return result;
            }).when(template).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        });
    }

    private static ResultSet result(Map<String, Object> values) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString(anyString())).thenAnswer(call -> { var value = values.get(call.getArgument(0)); return value == null ? null : value.toString(); });
        when(result.getObject(anyString())).thenAnswer(call -> values.get(call.getArgument(0)));
        when(result.getInt(anyString())).thenAnswer(call -> ((Number) values.get(call.getArgument(0))).intValue());
        when(result.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(values.get(call.getArgument(0))));
        when(result.getArray(anyString())).thenAnswer(call -> { var array = mock(java.sql.Array.class); when(array.getArray()).thenReturn(values.get(call.getArgument(0))); return array; });
        return result;
    }

    private static Map<String, Object> find(List<Map<String, Object>> rows, String key, String value) {
        return rows.stream().filter(row -> value.equals(row.get(key))).findFirst().orElseThrow();
    }

    /** Controlled catalog rows prove probe behavior, not PostgreSQL rendering or actual constraint execution. */
    private static final class Catalog {
        final List<Map<String, Object>> history = new ArrayList<>();
        final Map<String, List<Map<String, Object>>> columns = new HashMap<>(), checks = new HashMap<>(), keys = new HashMap<>(), indexes = new HashMap<>();
        final Map<String, Boolean> tablePrivileges = new HashMap<>(), updatePrivileges = new HashMap<>();
        DataSource source; boolean primary = true, safePrivileges = true; RuntimeException failure;

        Catalog() throws Exception { this(false); }

        Catalog(boolean resultAccess) throws Exception {
            String sql = sql() + (resultAccess ? "\n" + resultAccessSql() : ""); var shape = MemoryStorageVerifier.contract(sql);
            var files = new ArrayList<>(List.of("V1__create_agent_ledger.sql", "V2__add_run_governance.sql", "V3__add_adk_session_store.sql", "V4__add_adk_session_codec_versions.sql",
                    "V5__bind_agent_ownership_to_issuer.sql", "V6__add_execution_grant_evidence.sql", "V7__add_process_dispatch_fence.sql", "V8__add_service_execution_grant.sql",
                    "V9__add_context_snapshots.sql", "V10__add_memory_facts_and_index.sql", "V11__bind_memory_grant_ticket_to_principal.sql", "V12__add_memory_preference_intent.sql", ToolExecutionStorageVerifier.FILE));
            if (resultAccess) files.add(ToolExecutionStorageVerifier.RESULT_ACCESS_FILE);
            for (int index = 0; index < files.size(); index++) history.add(new HashMap<>(Map.of("version", Integer.toString(index + 1), "type", "SQL", "script", files.get(index),
                    "success", true, "checksum", MemoryStorageVerifier.checksum(new String(resource(files.get(index)), StandardCharsets.UTF_8)))));
            var starts = Pattern.compile("CREATE TABLE tianji_agent\\.([a-z0-9_]+)\\s*\\(").matcher(sql);
            while (starts.find()) {
                String table = starts.group(1); String body = sql.substring(starts.end(), closing(sql, starts.end() - 1));
                var columnRows = new ArrayList<Map<String, Object>>();
                for (var column : shape.get(table).columns().entrySet()) {
                    var row = new HashMap<String, Object>(); boolean defaulted = !CAPABILITY.equals(table) && "dispatch_epoch".equals(column.getKey());
                    row.putAll(Map.of("attname", column.getKey(), "type", column.getValue().type(), "attnotnull", column.getValue().notNull(),
                            "collname", column.getValue().cCollation() ? "C" : "default", "attgenerated", "", "attidentity", "", "atthasdef", defaulted,
                            "default_value", defaulted ? "0" : "", "relkind", "r", "relpersistence", "p"));
                    row.put("relispartition", false); row.put("no_inheritance", true); columnRows.add(row);
                }
                columns.put(table, columnRows); var checkRows = new ArrayList<Map<String, Object>>();
                var check = Pattern.compile("CONSTRAINT\\s+([a-z0-9_]+)\\s+CHECK\\s*\\(").matcher(body);
                while (check.find()) checkRows.add(new HashMap<>(Map.of("conname", check.group(1), "convalidated", true, "enforced", true,
                        "expression", body.substring(check.end(), closing(body, check.end() - 1)))));
                checks.put(table, checkRows); keys.put(table, new ArrayList<>()); indexes.put(table, new ArrayList<>());
                tablePrivileges.put(table, true); updatePrivileges.put(table, true);
                var key = Pattern.compile("CONSTRAINT\\s+[a-z0-9_]+\\s+(PRIMARY KEY|UNIQUE)\\s*\\(([^)]+)\\)").matcher(body);
                while (key.find()) {
                    var keyRow = key(key.group(1).equals("PRIMARY KEY") ? "p" : "u", key.group(2), "", ""); keys.get(table).add(keyRow);
                    indexes.get(table).add(index(shape.get(table), key.group(2), true, key.group(1).equals("PRIMARY KEY"), ""));
                }
                foreignKeys(body, keys.get(table));
            }
            var alter = Pattern.compile("ALTER TABLE tianji_agent\\.([a-z0-9_]+)(.*?);", Pattern.DOTALL).matcher(sql);
            while (alter.find()) foreignKeys(alter.group(2), keys.get(alter.group(1)));
            var index = Pattern.compile("CREATE (UNIQUE )?INDEX [a-z0-9_]+ ON tianji_agent\\.([a-z0-9_]+) \\(([^)]+)\\)(?: WHERE ([^;]+))?;").matcher(sql);
            while (index.find()) indexes.get(index.group(2)).add(index(shape.get(index.group(2)), index.group(3), index.group(1) != null, false, index.group(4) == null ? "" : index.group(4)));
        }

        private static void foreignKeys(String sql, List<Map<String, Object>> rows) {
            var foreign = Pattern.compile("FOREIGN KEY \\(([^)]+)\\)\\s+REFERENCES tianji_agent\\.([a-z0-9_]+) \\(([^)]+)\\)").matcher(sql);
            while (foreign.find()) rows.add(key("f", foreign.group(1), foreign.group(2), foreign.group(3)));
        }
        private static Map<String, Object> key(String type, String fields, String target, String targetFields) {
            var row = new HashMap<String, Object>(); row.putAll(Map.of("contype", type, "columns", fields.split(","), "target_schema", target.isEmpty() ? "" : "tianji_agent",
                    "target", target, "target_columns", targetFields.isEmpty() ? new String[0] : targetFields.split(","), "convalidated", true, "enforced", true,
                    "condeferrable", false, "condeferred", false));
            row.put("confdeltype", "a"); row.put("confupdtype", "a"); row.put("confmatchtype", "s"); return row;
        }
        private static Map<String, Object> index(MemoryStorageVerifier.Table table, String fields, boolean unique, boolean primary, String predicate) {
            var opclasses = new ArrayList<String>();
            for (String field : fields.split(",")) {
                String type = table.columns().get(field).type(); String opclass = type.startsWith("character varying(") ? "text_ops" : type.startsWith("character(") ? "bpchar_ops"
                        : type.equals("bigint") ? "int8_ops" : type.equals("uuid") ? "uuid_ops" : "timestamptz_ops";
                opclasses.add("pg_catalog." + opclass);
            }
            var row = new HashMap<String, Object>(); row.putAll(Map.of("columns", fields.split(","), "opclasses", opclasses.toArray(String[]::new), "predicate", predicate,
                    "indisunique", unique, "indisprimary", primary, "amname", "btree", "indisvalid", true, "indisready", true, "indislive", true, "indimmediate", true));
            if (predicate.isEmpty()) row.put("predicate", null);
            row.put("no_include", true); row.put("nulls_distinct", true); row.put("matching_collations", true); return row;
        }
    }

    private static int closing(String value, int open) {
        int depth = 0; boolean quoted = false;
        for (int index = open; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\'') { if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '\'') index++; else quoted = !quoted; }
            else if (!quoted) { if (current == '(') depth++; else if (current == ')' && --depth == 0) return index; }
        }
        throw new IllegalArgumentException("Controlled catalog fixture parentheses are incomplete");
    }

    private static byte[] resource(String file) throws Exception {
        try (var input = ToolExecutionStorageVerifierTest.class.getResourceAsStream("/db/tianji-agent/" + file)) {
            assertNotNull(input); return input.readAllBytes();
        }
    }

    private static String sql() throws Exception {
        var resource = ToolExecutionStorageVerifierTest.class.getResource("/db/tianji-agent/" + ToolExecutionStorageVerifier.FILE);
        if (resource != null) {
            try (var input = resource.openStream()) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        }
        String draft = System.getProperty("tianji.tool.journal.ddl");
        if (draft == null) throw new IllegalStateException("Provide the reviewed tool DDL until the final V13 resource is created");
        return Files.readString(Path.of(draft));
    }

    private static String resultAccessSql() throws Exception {
        return new String(resource(ToolExecutionStorageVerifier.RESULT_ACCESS_FILE), StandardCharsets.UTF_8);
    }
}
