package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class ProcessApiSchemaVerifierTest {
    private static final String V8 = "V8__add_formal_cmmn_case_support.sql";
    private static final String V9 = "V9__add_process_cancellation_receipts.sql";
    private static final String V10 = "V10__add_process_worker_recovery_receipts.sql";
    private static final String V10_SHA256 = "5a3511659af4c44b2455e55e15d373d6c643fea29ed513b658c59595b5ef6dc9";
    private static final String RECOVERY_RECEIPT = "tianji_process_worker_recovery_receipt";
    private static final String V9_SHA256 = "dd07ea808cb19f85e983f99a38950add4229208e66dc3c10a60970c1ffc0711b";
    private static final String PROVENANCE = "tianji_case_start_provenance";
    private static final String PLAN_RECEIPT = "tianji_case_plan_item_receipt";
    private static final String CASE_INBOX = "tianji_case_completion_inbox";
    private static final String CANCEL_RECEIPT = "tianji_process_cancel_receipt";
    private static final String DISPATCH = "tianji_process_agent_dispatch";

    @Test
    void history_accepts_only_exact_v8_v9_v10_prefixes() throws Exception {
        assertFalse(assertDoesNotThrow(() -> checkHistory(List.of())));
        assertTrue(assertDoesNotThrow(() -> checkHistory(List.of(history("8", V8, true, checksum(V8))))));
        assertTrue(assertDoesNotThrow(() -> checkHistory(List.of(history("8", V8, true, checksum(V8)),
                history("9", V9, true, checksum(V9))))));
        assertTrue(assertDoesNotThrow(() -> checkHistory(List.of(history("8", V8, true, checksum(V8)),
                history("9", V9, true, checksum(V9)), history("10", V10, true, checksum(V10))))),
                "exact V8/V9/V10 prefix");


        var v8 = history("8", V8, true, checksum(V8));
        var v9 = history("9", V9, true, checksum(V9));
        var v10 = history("10", V10, true, checksum(V10));
        var wrongType = history("10", V10, true, checksum(V10));
        wrongType.put("type", "JDBC");
        for (List<Map<String, Object>> invalid : List.of(
                List.of(v10), List.of(v8, v10), List.of(v8, v10, v9), List.of(v8, v9, v10, v10),
                List.of(v8, v9, history("10", "V10__other.sql", true, checksum(V10))),
                List.of(v8, v9, history("10", V10, false, checksum(V10))),
                List.of(v8, v9, history("10", V10, true, checksum(V10) + 1)),
                List.of(v8, v9, history("10", V10, true, null)),
                List.of(v8, v9, wrongType),
                List.of(v8, v9, v10, history("11", "V11__unknown.sql", true, 0)),
                List.of(v8, v9, v10, history(null, "R__unknown.sql", true, 0)),
                List.of(history("9", V9, true, checksum(V9))),
                List.of(history("8", V8, false, checksum(V8))),
                List.of(history("8", V8, true, checksum(V8)), history("9", V9, false, checksum(V9))),
                List.of(history("8", V8, true, checksum(V8)), history("8", V8, true, checksum(V8))),
                List.of(history("8", V8, true, checksum(V8)), history("9", V9, true, checksum(V9)),
                        history("9", V9, true, checksum(V9))),
                List.of(history("8", V8, true, checksum(V8) + 1)),
                List.of(history("8", V8, true, checksum(V8)), history("9", V9, true, checksum(V9) + 1)),
                List.of(history("8", V8, true, null)),
                List.of(history("8", V8, true, checksum(V8)), history("9", V9, true, null)),
                List.of(history(null, "R__unknown.sql", true, 1)),
                List.of(history("8", V8, true, checksum(V8)), history("9", "V9__wrong.sql", true, checksum(V9))),
                List.of(history("8", "V8__wrong.sql", true, checksum(V8))),
                List.of(history("8", V8, true, checksum(V8)), history("9", V9, true, checksum(V9)),
                        history("8", V8, true, checksum(V8))),
                List.of(history("9", V9, true, checksum(V9)), history("8", V8, true, checksum(V8))),
                List.of(history("8", V8, true, checksum(V8)), history("10", "V10__unknown.sql", true, 1)))) {
            assertThrows(IllegalStateException.class, () -> checkHistory(invalid));
        }
    }

    @Test
    void history_query_rejects_repeatable_rows_and_preserves_actual_install_order() throws Exception {
        var catalog = Catalog.ready();
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            assertTrue(assertDoesNotThrow(() ->
                    new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process").hasCaseMigration()));
        }
        assertNotNull(catalog.historySql);
        assertTrue(catalog.historySql.contains("version IS NULL"));
        assertTrue(catalog.historySql.contains("ORDER BY installed_rank"));
        assertFalse(catalog.historySql.contains("ORDER BY version"));
    }

    @Test
    void cancellation_verifier_pins_the_frozen_v9_resource_identity() throws Exception {
        byte[] bytes = resource(V9).getBytes(StandardCharsets.UTF_8);
        assertEquals(V9_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertEquals(-178365142, checksum(V9));
        assertTrue(Arrays.stream(ProcessApiSchemaVerifier.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .peek(field -> field.setAccessible(true))
                .anyMatch(field -> {
                    try {
                        return V9_SHA256.equals(field.get(null));
                    } catch (IllegalAccessException failure) {
                        throw new AssertionError(failure);
                    }
                }), "verifyCancellation must byte-pin the immutable V9 resource");
    }

    @Test
    void cancellation_readiness_accepts_select_only_provenance_and_exact_v9_boolean_columns() throws Exception {
        var catalog = Catalog.ready();
        catalog.caseInsert = false;
        catalog.caseInboxUpdate = false;
        ProcessApiSchemaVerifier verifier = verifyCancellation(catalog, false);

        assertEquals(26, catalog.rows.get("columns:" + CANCEL_RECEIPT).size());
        assertEquals("boolean", catalog.column(CANCEL_RECEIPT, "accepted").get("type"));
        assertEquals("boolean", catalog.column(CANCEL_RECEIPT, "agent_closure_scheduled").get("type"));
        assertEquals(Set.of("tianji_process_cancel_tenant", "tianji_process_cancel_owner",
                        "tianji_process_cancel_kind", "tianji_process_cancel_command",
                        "tianji_process_cancel_hashes", "tianji_process_cancel_instance",
                        "tianji_process_cancel_release", "tianji_process_cancel_engine",
                        "tianji_process_cancel_business", "tianji_process_cancel_reason",
                        "tianji_process_cancel_result"), catalog.names("checks:" + CANCEL_RECEIPT));
        assertEquals(Set.of("tianji_process_cancel_receipt_pkey", "tianji_process_cancel_command_fk",
                        "tianji_process_cancel_start_fk", "tianji_process_cancel_definition_fk"),
                catalog.names("keys:" + CANCEL_RECEIPT));
        assertEquals(3, catalog.rows.get("keys:" + CANCEL_RECEIPT).stream()
                .filter(row -> "f".equals(row.get("contype"))).count());
        assertEquals(Set.of("tianji_process_cancel_receipt_pkey", "tianji_process_cancel_accepted_idx"),
                catalog.names("indexes:" + CANCEL_RECEIPT));
        assertEquals(Set.of("tianji_agent_dispatch_scope_open_idx"), catalog.names("indexes:" + DISPATCH));
        verify(verifier).verifyAgentDispatch();
        verify(verifier, never()).verifyCase();

        String provenanceRole = catalog.roleSql.get(PROVENANCE);
        assertRoleSafety(provenanceRole);
        assertTrue(provenanceRole.contains("has_table_privilege(c.oid,'SELECT')"));
        assertTrue(provenanceRole.contains("NOT has_table_privilege(c.oid,'UPDATE')"));
        assertColumnPrivilegeSafety(provenanceRole, false);
        assertFalse(provenanceRole.contains("has_table_privilege(c.oid,'INSERT')"));
        assertFalse(catalog.roleSql.containsKey(PLAN_RECEIPT));
        assertFalse(catalog.roleSql.containsKey(CASE_INBOX));
        String auditRole = catalog.roleSql.get(CANCEL_RECEIPT);
        assertRoleSafety(auditRole);
        assertTrue(auditRole.contains("has_table_privilege(c.oid,'SELECT')"));
        assertTrue(auditRole.contains("has_table_privilege(c.oid,'INSERT')"));
        assertTrue(auditRole.contains("NOT has_table_privilege(c.oid,'UPDATE')"));
        assertColumnPrivilegeSafety(auditRole, false);
        String dispatchRole = catalog.roleSql.get(DISPATCH);
        assertRoleSafety(dispatchRole);
        assertTrue(dispatchRole.contains("has_table_privilege(c.oid,'SELECT')"));
        assertTrue(dispatchRole.contains("has_table_privilege(c.oid,'UPDATE')"));
        assertColumnPrivilegeSafety(dispatchRole, true);
        assertFalse(dispatchRole.contains("has_table_privilege(c.oid,'INSERT')"));

        var standardCaseRole = Catalog.ready();
        ProcessApiSchemaVerifier standardVerifier = verifyCancellation(standardCaseRole, false);
        verify(standardVerifier, never()).verifyCase();
    }

    @Test
    void cancellation_readiness_rejects_history_catalog_index_and_role_drift() throws Exception {
        rejects(c -> c.history = List.of(history("8", V8, true, checksumUnchecked(V8))));
        rejects(c -> c.column(PROVENANCE, "executor_subject").put("type", "character varying(128)"));
        rejects(c -> c.rows.get("columns:" + CANCEL_RECEIPT).removeIf(row -> "accepted".equals(row.get("attname"))));
        rejects(c -> c.column(CANCEL_RECEIPT, "accepted").put("type", "integer"));
        rejects(c -> c.rows.get("checks:" + CANCEL_RECEIPT).remove(0));
        rejects(c -> c.rows.get("keys:" + CANCEL_RECEIPT)
                .removeIf(row -> "tianji_process_cancel_start_fk".equals(row.get("name"))));
        rejects(c -> c.named("keys:" + CANCEL_RECEIPT, "tianji_process_cancel_definition_fk")
                .put("target", "tianji_process_release"));
        rejects(c -> c.named("keys:" + CANCEL_RECEIPT, "tianji_process_cancel_start_fk")
                .put("target_fields", List.of("tenant_id", "subject_type", "subject_id", "subject_issuer",
                        "command_type", "command_id")));
        rejects(c -> c.rows.get("indexes:" + CANCEL_RECEIPT)
                .removeIf(row -> "tianji_process_cancel_accepted_idx".equals(row.get("name"))));
        rejects(c -> c.named("indexes:" + CANCEL_RECEIPT, "tianji_process_cancel_accepted_idx")
                .put("predicate", "accepted OR result_status = 'CANCELLED'"));
        rejects(c -> c.rows.get("indexes:" + DISPATCH).clear());
        rejects(c -> c.provenanceSelect = false);
        rejects(c -> c.auditReadWrite = false);
        rejects(c -> c.dispatchReadWrite = false);
        rejects(c -> c.safeRole = false);
    }

    @Test
    void v10_keeps_existing_readiness_without_recovery_grants() throws Exception {
        var catalog = Catalog.ready();
        catalog.history = recoveryHistory();
        catalog.rejectRecoveryQueries = true;
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            var verifier = spy(new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process"));
            doNothing().when(verifier).verify();
            doNothing().when(verifier).verifyAgentDispatch();
            assertTrue(assertDoesNotThrow(verifier::hasCaseMigration));
            assertDoesNotThrow(verifier::verifyCase);
            assertDoesNotThrow(verifier::verifyCancellation);
        }
        assertFalse(catalog.roleSql.containsKey(RECOVERY_RECEIPT));
        assertTrue(catalog.nativeTables.isEmpty());
    }

    @Test
    void recovery_requires_exact_v10_catalog_and_role() throws Exception {
        var catalog = Catalog.recoveryReady();
        verifyRecovery(catalog, false);
        assertEquals(32, catalog.rows.get("columns:" + RECOVERY_RECEIPT).size());
        assertEquals(11, catalog.names("checks:" + RECOVERY_RECEIPT).size());
        assertEquals(4, catalog.names("keys:" + RECOVERY_RECEIPT).size());
        assertEquals(Set.of("tianji_process_worker_recovery_receipt_pkey", "tianji_process_worker_recovery_job_uk"),
                catalog.names("indexes:" + RECOVERY_RECEIPT));
        assertEquals(Set.of(RECOVERY_RECEIPT), catalog.roleSql.keySet());
        String role = catalog.roleSql.get(RECOVERY_RECEIPT);
        assertRoleSafety(role);
        assertColumnPrivilegeSafety(role, false);
        assertTrue(role.contains("has_table_privilege(c.oid,'INSERT')"));
        assertTrue(role.contains("NOT has_table_privilege(c.oid,'UPDATE')"));
        assertTrue(role.contains("NOT pg_has_role(n.nspowner,'MEMBER')"));
        assertEquals(Set.of("act_ru_execution", "act_ru_deadletter_job", "act_ru_external_job",
                "act_ru_job", "act_ru_timer_job", "act_ru_suspended_job"), catalog.nativeTables);
        assertTrue(catalog.nativeSql.get("act_ru_execution").contains("has_any_column_privilege"));
        assertTrue(catalog.nativeSql.get("act_ru_deadletter_job").contains("has_any_column_privilege"));
        assertTrue(catalog.nativeSql.get("act_ru_deadletter_job").contains("'DELETE'"));
        assertTrue(catalog.nativeSql.get("act_ru_external_job").contains("'INSERT'"));

        recoveryRejects(c -> c.history = c.history.subList(0, 2));
        recoveryRejects(c -> c.column(RECOVERY_RECEIPT, "failure_decided_at").put("type", "timestamp without time zone"));
        recoveryRejects(c -> c.column(RECOVERY_RECEIPT, "reason").put("collname", "default"));
        recoveryRejects(c -> c.column(RECOVERY_RECEIPT, "job_id").put("attnotnull", false));
        recoveryRejects(c -> c.column(RECOVERY_RECEIPT, "retries").put("default_value", "1"));
        recoveryRejects(c -> c.rows.get("columns:" + RECOVERY_RECEIPT).add(catalogExtraColumn()));
        recoveryRejects(c -> c.named("checks:" + RECOVERY_RECEIPT, "tianji_process_worker_recovery_reason")
                .put("expression", "reason <> ''"));
        recoveryRejects(c -> c.named("keys:" + RECOVERY_RECEIPT, "tianji_process_worker_recovery_start_fk")
                .put("deletion", "c"));
        recoveryRejects(c -> c.named("keys:" + RECOVERY_RECEIPT, "tianji_process_worker_recovery_definition_fk")
                .put("target_schema", "foreign_schema"));
        recoveryRejects(c -> c.named("indexes:" + RECOVERY_RECEIPT, "tianji_process_worker_recovery_job_uk")
                .put("predicate", "job_id IS NOT NULL"));
        recoveryRejects(c -> c.named("indexes:" + RECOVERY_RECEIPT, "tianji_process_worker_recovery_job_uk")
                .put("indisunique", false));
        recoveryRejects(c -> c.recoveryReadWrite = false);
        recoveryRejects(c -> c.safeRole = false);
        recoveryRejects(c -> c.badNativeTable = "act_ru_external_job");
    }

    @Test
    void recovery_verifier_pins_the_frozen_v10_resource_identity() throws Exception {
        try (InputStream input = ProcessApiSchemaVerifierTest.class.getResourceAsStream("/db/tianji-process/" + V10)) {
            assertNotNull(input);
            assertEquals(V10_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())));
        }
        assertEquals(186112865, checksum(V10));
        var field = ProcessApiSchemaVerifier.class.getDeclaredField("RECOVERY_SHA256");
        field.setAccessible(true);
        assertEquals(V10_SHA256, field.get(null));
    }

    private static void verifyRecovery(Catalog catalog, boolean reject) {
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            var verifier = new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process");
            if (reject) assertThrows(IllegalStateException.class, verifier::verifyWorkerRecovery);
            else assertDoesNotThrow(verifier::verifyWorkerRecovery);
        }
    }

    private static void recoveryRejects(Consumer<Catalog> mutation) throws Exception {
        var catalog = Catalog.recoveryReady();
        mutation.accept(catalog);
        verifyRecovery(catalog, true);
    }

    private static Map<String, Object> catalogExtraColumn() {
        return Catalog.catalogColumn("unrelated", "integer", false);
    }

    private static List<Map<String, Object>> recoveryHistory() throws Exception {
        return List.of(history("8", V8, true, checksum(V8)), history("9", V9, true, checksum(V9)),
                history("10", V10, true, checksum(V10)));
    }

    private static void assertRoleSafety(String sql) {
        assertNotNull(sql);
        for (String required : List.of("NOT c.relrowsecurity", "NOT c.relforcerowsecurity", "NOT c.relispartition",
                "c.relpersistence='p'", "has_schema_privilege(n.oid,'USAGE')", "rolsuper",
                "rolbypassrls", "NOT pg_has_role(c.relowner,'MEMBER')", "NOT has_table_privilege(c.oid,'DELETE')",
                "NOT has_table_privilege(c.oid,'TRUNCATE')", "NOT has_table_privilege(c.oid,'TRIGGER')",
                "NOT has_table_privilege(c.oid,'REFERENCES')", "FROM pg_trigger", "tr.tgisinternal", "tr.tgenabled")) {
            assertTrue(sql.contains(required), required);
        }
    }

    private static void assertColumnPrivilegeSafety(String sql, boolean mutable) {
        assertTrue(sql.contains("NOT has_any_column_privilege(c.oid,'REFERENCES')"));
        if (!mutable) assertTrue(sql.contains("NOT has_any_column_privilege(c.oid,'UPDATE')"));
    }

    @Test
    void full_case_activation_retains_its_three_table_write_grants() throws Exception {
        var ready = Catalog.ready();
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(ready::answer))) {
            var verifier = spy(new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process"));
            doNothing().when(verifier).verify();
            assertDoesNotThrow(verifier::verifyCase);
            verify(verifier).verify();
        }
        for (String table : List.of(PROVENANCE, PLAN_RECEIPT, CASE_INBOX)) {
            assertFalse(ready.roleSql.get(table).contains("has_any_column_privilege"));
        }

        var leastPrivilegeCancellation = Catalog.ready();
        leastPrivilegeCancellation.caseInsert = false;
        leastPrivilegeCancellation.caseInboxUpdate = false;
        try (var construction = mockConstruction(JdbcTemplate.class,
                withSettings().defaultAnswer(leastPrivilegeCancellation::answer))) {
            var verifier = spy(new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process"));
            doNothing().when(verifier).verify();
            assertThrows(IllegalStateException.class, verifier::verifyCase);
        }
    }

    private static boolean checkHistory(List<Map<String, Object>> history) throws Exception {
        var catalog = Catalog.ready();
        catalog.history = history;
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            return new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process").hasCaseMigration();
        }
    }

    private static ProcessApiSchemaVerifier verifyCancellation(Catalog catalog, boolean reject) {
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            var verifier = spy(new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process"));
            doNothing().when(verifier).verify();
            doNothing().when(verifier).verifyAgentDispatch();
            if (reject) assertThrows(IllegalStateException.class, verifier::verifyCancellation);
            else assertDoesNotThrow(verifier::verifyCancellation);
            return verifier;
        }
    }

    private static void rejects(Consumer<Catalog> mutation) throws Exception {
        var catalog = Catalog.ready();
        mutation.accept(catalog);
        verifyCancellation(catalog, true);
    }

    private static Map<String, Object> history(String version, String script, boolean success, Integer checksum) {
        var row = new LinkedHashMap<String, Object>();
        row.put("version", version); row.put("type", "SQL"); row.put("script", script);
        row.put("success", success); row.put("checksum", checksum); return row;
    }

    private static int checksumUnchecked(String resource) {
        try { return checksum(resource); }
        catch (Exception failure) { throw new AssertionError(failure); }
    }

    private static int checksum(String resource) throws Exception {
        String sql = resource(resource); CRC32 crc = new CRC32();
        sql.lines().forEach(line -> crc.update(line.getBytes(StandardCharsets.UTF_8)));
        return (int) crc.getValue();
    }

    private static String resource(String name) throws Exception {
        try (InputStream input = ProcessApiSchemaVerifierTest.class.getResourceAsStream("/db/tianji-process/" + name)) {
            assertNotNull(input, name); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Catalog {
        private final Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        private final Map<String, String> roleSql = new HashMap<>();
        private List<Map<String, Object>> history;
        private String historySql;
        private final Set<String> nativeTables = new java.util.HashSet<>();
        private final Map<String, String> nativeSql = new HashMap<>();
        private boolean rejectRecoveryQueries;
        private boolean recoveryReadWrite = true;
        private String badNativeTable;
        private boolean provenanceSelect = true;
        private boolean auditReadWrite = true;
        private boolean dispatchReadWrite = true;
        private boolean caseInsert = true;
        private boolean caseInboxUpdate = true;
        private boolean safeRole = true;

        static Catalog ready() throws Exception {
            var catalog = new Catalog();
            String v8 = resource(V8);
            catalog.parseTables(v8);
            catalog.parseIndexes(v8);
            catalog.addV9();
            catalog.history = List.of(history("8", V8, true, checksum(V8)),
                    history("9", V9, true, checksum(V9)));
            var releaseChecks = checks(v8.substring(v8.indexOf("ALTER TABLE tianji_process_release_definition"),
                    v8.indexOf("ALTER TABLE tianji_human_task_receipt")));
            releaseChecks.getFirst().put("name", "tianji_process_release_definition_model_type_check");
            releaseChecks.getFirst().put("conname", "tianji_process_release_definition_model_type_check");
            catalog.rows.put("checks:tianji_process_release_definition", releaseChecks);
            var taskChecks = checks(v8.substring(v8.indexOf("ALTER TABLE tianji_human_task_receipt"), v8.indexOf("CREATE TABLE")));
            for (var row : taskChecks) if (((String) row.get("name")).endsWith("_v8")) {
                String renamed = ((String) row.get("name")).replace("_v8", "");
                row.put("name", renamed); row.put("conname", renamed);
            }
            catalog.rows.put("checks:tianji_human_task_receipt", taskChecks);
            return catalog;
        }

        static Catalog recoveryReady() throws Exception {
            var catalog = ready();
            catalog.history = recoveryHistory();
            catalog.addV10();
            return catalog;
        }

        Map<String, Object> column(String table, String name) {
            return rows.get("columns:" + table).stream().filter(row -> name.equals(row.get("attname"))).findFirst().orElseThrow();
        }

        Set<String> names(String key) {
            return rows.get(key).stream().map(row -> (String) row.get("name")).collect(java.util.stream.Collectors.toSet());
        }

        Map<String, Object> named(String key, String name) {
            return rows.get(key).stream().filter(row -> name.equals(row.get("name"))).findFirst().orElseThrow();
        }

        Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            if (args.length == 0 || !(args[0] instanceof String sql)) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            String table = table(sql, args);
            if (sql.contains("pg_is_in_recovery()")) return true;
            if (rejectRecoveryQueries && (RECOVERY_RECEIPT.equals(table) || table.startsWith("act_ru_"))) {
                throw new AssertionError("Legacy readiness queried recovery storage");
            }
            if (table.startsWith("act_ru_")) {
                nativeTables.add(table);
                nativeSql.put(table, sql);
                return !table.equals(badNativeTable);
            }
            if (sql.contains("has_table_privilege")) {
                roleSql.put(table, sql);
                if (!safeRole) return false;
                if (PROVENANCE.equals(table)) {
                    return sql.contains("AND has_table_privilege(c.oid,'INSERT')") ? caseInsert : provenanceSelect;
                }
                if (PLAN_RECEIPT.equals(table)) return caseInsert;
                if (CASE_INBOX.equals(table)) return caseInsert && caseInboxUpdate;
                if (CANCEL_RECEIPT.equals(table)) return auditReadWrite;
                if (DISPATCH.equals(table)) return dispatchReadWrite;
                if (RECOVERY_RECEIPT.equals(table)) return recoveryReadWrite;
                return true;
            }
            if (sql.contains("schema_history")) {
                historySql = sql;
                return map(invocation, history);
            }
            if (sql.contains("attidentity")) return map(invocation, rows.getOrDefault("columns:" + table, List.of()));
            if (sql.contains("c.contype='c'")) return map(invocation, rows.getOrDefault("checks:" + table, List.of()));
            if (sql.contains("c.contype IN ('p','u','f')")) return map(invocation, rows.getOrDefault("keys:" + table, List.of()));
            if (sql.contains("FROM pg_index")) return map(invocation, rows.getOrDefault("indexes:" + table, List.of()));
            throw new AssertionError("Unexpected verifier query: " + sql);
        }

        private Object map(InvocationOnMock invocation, List<Map<String, Object>> selected) throws Exception {
            Object[] args = invocation.getArguments();
            var callback = Arrays.stream(args).filter(RowCallbackHandler.class::isInstance)
                    .map(RowCallbackHandler.class::cast).findFirst();
            if (callback.isPresent()) {
                for (var row : selected) callback.orElseThrow().processRow(resultSet(row));
                return null;
            }
            var mapper = Arrays.stream(args).filter(RowMapper.class::isInstance).map(RowMapper.class::cast).findFirst();
            if (mapper.isPresent()) {
                var result = new ArrayList<>();
                for (var row : selected) result.add(mapper.orElseThrow().mapRow(resultSet(row), result.size()));
                return result;
            }
            if (invocation.getMethod().getName().equals("queryForObject")) return selected.isEmpty() ? 0 : 1;
            throw new AssertionError("Verifier query has no mapper: " + args[0]);
        }

        private static String table(String sql, Object[] args) {
            Set<String> known = Set.of(PROVENANCE, PLAN_RECEIPT, CASE_INBOX, CANCEL_RECEIPT, DISPATCH, RECOVERY_RECEIPT,
                    "act_ru_execution", "act_ru_deadletter_job", "act_ru_external_job",
                    "act_ru_job", "act_ru_timer_job", "act_ru_suspended_job",
                    "tianji_process_release_definition", "tianji_human_task_receipt");
            String argument = Arrays.stream(args).filter(String.class::isInstance).map(String.class::cast)
                    .filter(known::contains).reduce((first, second) -> second).orElse("");
            if (!argument.isEmpty()) return argument;
            return known.stream().filter(sql::contains).findFirst().orElse("");
        }

        /** Hand-authored from the frozen V9 contract so the fixture does not reuse the production parser algorithm. */
        private void addV9() {
            rows.put("columns:" + CANCEL_RECEIPT, new ArrayList<>(List.of(
                    catalogColumn("tenant_id", "bigint", false),
                    catalogColumn("subject_type", "character varying(16)", true),
                    catalogColumn("subject_issuer", "character varying(64)", true),
                    catalogColumn("subject_id", "character varying(256)", true),
                    catalogColumn("command_type", "character varying(32)", true),
                    catalogColumn("command_id", "character varying(128)", true),
                    catalogColumn("request_sha256", "character(64)", true),
                    catalogColumn("process_instance_id", "character varying(255)", true),
                    catalogColumn("start_command_type", "character varying(32)", true),
                    catalogColumn("start_command_id", "character varying(128)", true),
                    catalogColumn("start_request_sha256", "character(64)", true),
                    catalogColumn("release_key", "character varying(128)", true),
                    catalogColumn("model_type", "character varying(8)", true),
                    catalogColumn("definition_key", "character varying(128)", true),
                    catalogColumn("engine_definition_id", "character varying(255)", true),
                    catalogColumn("definition_sha256", "character(64)", true),
                    catalogColumn("business_key", "character varying(255)", true),
                    catalogColumn("correlation_id", "character varying(128)", true),
                    catalogColumn("expected_version", "character(64)", true),
                    catalogColumn("reason", "character varying(512)", true),
                    catalogColumn("accepted", "boolean", false),
                    catalogColumn("result_status", "character varying(16)", true),
                    catalogColumn("result_version", "character(64)", true),
                    catalogColumn("result_finished_at", "timestamp(6) with time zone", false),
                    catalogColumn("decided_at", "timestamp(6) with time zone", false),
                    catalogColumn("agent_closure_scheduled", "boolean", false))));

            rows.put("checks:" + CANCEL_RECEIPT, new ArrayList<>(List.of(
                    catalogCheck("tianji_process_cancel_tenant", "tenant_id > 0"),
                    catalogCheck("tianji_process_cancel_owner", "subject_type = ANY(ARRAY['USER', 'SERVICE']) "
                            + "AND subject_issuer ~ '^[0-9a-f]{64}$' AND subject_id <> '' "
                            + "AND subject_id = btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'"),
                    catalogCheck("tianji_process_cancel_kind", "command_type = 'PROCESS_CANCEL' "
                            + "AND start_command_type = 'START' AND model_type = 'BPMN'"),
                    catalogCheck("tianji_process_cancel_command", "command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' "
                            + "AND start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'"),
                    catalogCheck("tianji_process_cancel_hashes", "request_sha256 ~ '^[0-9a-f]{64}$' "
                            + "AND start_request_sha256 ~ '^[0-9a-f]{64}$' AND definition_sha256 ~ '^[0-9a-f]{64}$' "
                            + "AND expected_version ~ '^[0-9a-f]{64}$' AND result_version ~ '^[0-9a-f]{64}$'"),
                    catalogCheck("tianji_process_cancel_instance", "process_instance_id <> '' "
                            + "AND process_instance_id = btrim(process_instance_id) AND process_instance_id !~ '[[:cntrl:]]'"),
                    catalogCheck("tianji_process_cancel_release", "release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$' "
                            + "AND definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'"),
                    catalogCheck("tianji_process_cancel_engine", "engine_definition_id <> '' "
                            + "AND engine_definition_id = btrim(engine_definition_id) AND engine_definition_id !~ '[[:cntrl:]]'"),
                    catalogCheck("tianji_process_cancel_business", "business_key <> '' AND business_key = btrim(business_key) "
                            + "AND business_key !~ '[[:cntrl:]]' AND correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'"),
                    catalogCheck("tianji_process_cancel_reason", "reason <> '' AND reason = btrim(reason) "
                            + "AND reason !~ '[[:cntrl:]]' AND octet_length(reason) <= 2048"),
                    catalogCheck("tianji_process_cancel_result", "result_status = ANY(ARRAY['COMPLETED', 'CANCELLED']) "
                            + "AND (NOT accepted OR result_status = 'CANCELLED') "
                            + "AND (NOT agent_closure_scheduled OR accepted)"))));

            List<String> commandKey = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
                    "command_type", "command_id");
            List<String> startKey = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
                    "start_command_type", "start_command_id");
            List<String> definitionKey = List.of("tenant_id", "release_key", "model_type", "definition_key");
            rows.put("keys:" + CANCEL_RECEIPT, new ArrayList<>(List.of(
                    catalogKey("tianji_process_cancel_receipt_pkey", "p", commandKey, null, List.of()),
                    catalogKey("tianji_process_cancel_command_fk", "f", commandKey,
                            "tianji_process_command_receipt", commandKey),
                    catalogKey("tianji_process_cancel_start_fk", "f", startKey,
                            "tianji_process_command_receipt", commandKey),
                    catalogKey("tianji_process_cancel_definition_fk", "f", definitionKey,
                            "tianji_process_release_definition", definitionKey))));
            rows.put("indexes:" + CANCEL_RECEIPT, new ArrayList<>(List.of(
                    index("tianji_process_cancel_receipt_pkey", commandKey, true, true, null),
                    index("tianji_process_cancel_accepted_idx", List.of("tenant_id", "process_instance_id"),
                            true, false, "accepted"))));
            rows.put("indexes:" + DISPATCH, new ArrayList<>(List.of(
                    index("tianji_agent_dispatch_scope_open_idx",
                            List.of("tenant_id", "scope_type", "scope_id", "occurrence_id"),
                            false, false, "state <> 'CLOSED'"))));
        }

        /** Independent catalog fixture for the reviewed V10 physical contract. */
        private void addV10() {
            rows.put("columns:" + RECOVERY_RECEIPT, new ArrayList<>(List.of(
                    catalogColumn("tenant_id", "bigint", false),
                    catalogColumn("subject_type", "character varying(16)", true),
                    catalogColumn("subject_issuer", "character varying(64)", true),
                    catalogColumn("subject_id", "character varying(256)", true),
                    catalogColumn("command_type", "character varying(32)", true),
                    catalogColumn("command_id", "character varying(128)", true),
                    catalogColumn("request_sha256", "character(64)", true),
                    catalogColumn("process_instance_id", "character varying(255)", true),
                    catalogColumn("start_command_type", "character varying(32)", true),
                    catalogColumn("start_command_id", "character varying(128)", true),
                    catalogColumn("start_request_sha256", "character(64)", true),
                    catalogColumn("release_key", "character varying(128)", true),
                    catalogColumn("model_type", "character varying(8)", true),
                    catalogColumn("definition_key", "character varying(128)", true),
                    catalogColumn("engine_definition_id", "character varying(255)", true),
                    catalogColumn("definition_sha256", "character(64)", true),
                    catalogColumn("business_key", "character varying(255)", true),
                    catalogColumn("correlation_id", "character varying(128)", true),
                    catalogColumn("job_id", "character varying(255)", true),
                    catalogColumn("occurrence_id", "character varying(255)", true),
                    catalogColumn("execution_id", "character varying(255)", true),
                    catalogColumn("element_id", "character varying(128)", true),
                    catalogColumn("deadletter_revision", "integer", false),
                    catalogColumn("expected_version", "character(64)", true),
                    catalogColumn("evidence_sha256", "character(64)", true),
                    catalogColumn("admission_policy", "character varying(32)", true),
                    catalogColumn("failure_code", "character varying(64)", true),
                    catalogColumn("failure_decided_at", "timestamp(6) with time zone", false),
                    catalogColumn("reason", "character varying(512)", true),
                    catalogColumn("result_status", "character varying(24)", true),
                    catalogColumn("retries", "integer", false),
                    catalogColumn("decided_at", "timestamp(6) with time zone", false))));
            rows.put("checks:" + RECOVERY_RECEIPT, new ArrayList<>(List.of(
                    catalogCheck("tianji_process_worker_recovery_tenant", "tenant_id > 0"),
                    catalogCheck("tianji_process_worker_recovery_owner", "subject_type = ANY(ARRAY['USER', 'SERVICE']) " +
                            "AND subject_issuer ~ '^[0-9a-f]{64}$' " +
                            "AND subject_id <> '' AND subject_id = btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'"),
                    catalogCheck("tianji_process_worker_recovery_kind", "command_type = 'PROCESS_WORKER_RECOVER' AND start_command_type = 'START' AND model_type = 'BPMN'"),
                    catalogCheck("tianji_process_worker_recovery_command", "command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' " +
                            "AND start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' " +
                            "AND correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'"),
                    catalogCheck("tianji_process_worker_recovery_hashes", "request_sha256 ~ '^[0-9a-f]{64}$' AND start_request_sha256 ~ '^[0-9a-f]{64}$' " +
                            "AND definition_sha256 ~ '^[0-9a-f]{64}$' AND expected_version ~ '^[0-9a-f]{64}$' " +
                            "AND evidence_sha256 ~ '^[0-9a-f]{64}$'"),
                    catalogCheck("tianji_process_worker_recovery_release", "release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$' " +
                            "AND definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'"),
                    catalogCheck("tianji_process_worker_recovery_instance", "process_instance_id <> '' AND process_instance_id = btrim(process_instance_id) " +
                            "AND process_instance_id !~ '[[:cntrl:]]' AND process_instance_id <> '.' AND process_instance_id <> '..'"),
                    catalogCheck("tianji_process_worker_recovery_affinity", "engine_definition_id <> '' AND engine_definition_id = btrim(engine_definition_id) " +
                            "AND engine_definition_id !~ '[[:cntrl:]]' " +
                            "AND business_key <> '' AND business_key = btrim(business_key) AND business_key !~ '[[:cntrl:]]' " +
                            "AND job_id <> '' AND job_id = btrim(job_id) AND job_id !~ '[[:cntrl:]]' AND char_length(job_id) <= 64 " +
                            "AND occurrence_id = job_id " +
                            "AND execution_id <> '' AND execution_id = btrim(execution_id) AND execution_id !~ '[[:cntrl:]]' " +
                            "AND element_id <> '' AND element_id = btrim(element_id) AND element_id !~ '[[:cntrl:]]' " +
                            "AND deadletter_revision > 0"),
                    catalogCheck("tianji_process_worker_recovery_evidence", "admission_policy = 'READ_ONLY_EXHAUSTED_RETRY_V1' AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'"),
                    catalogCheck("tianji_process_worker_recovery_reason", "reason <> '' AND reason = btrim(reason) AND octet_length(reason) <= 2048 " +
                            "AND translate(reason, " +
                            "chr(1) || chr(2) || chr(3) || chr(4) || chr(5) || chr(6) || chr(7) || chr(8) " +
                            "|| chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(14) || chr(15) || chr(16) " +
                            "|| chr(17) || chr(18) || chr(19) || chr(20) || chr(21) || chr(22) || chr(23) || chr(24) " +
                            "|| chr(25) || chr(26) || chr(27) || chr(28) || chr(29) || chr(30) || chr(31) || chr(127), '') = reason"),
                    catalogCheck("tianji_process_worker_recovery_result", "result_status = 'RETRY_SCHEDULED' AND retries = 1"))));
            List<String> command = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
                    "command_type", "command_id");
            List<String> start = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
                    "start_command_type", "start_command_id");
            List<String> definition = List.of("tenant_id", "release_key", "model_type", "definition_key");
            String pk = "tianji_process_worker_recovery_receipt_pkey";
            rows.put("keys:" + RECOVERY_RECEIPT, new ArrayList<>(List.of(
                    catalogKey(pk, "p", command, null, List.of()),
                    catalogKey("tianji_process_worker_recovery_command_fk", "f", command,
                            "tianji_process_command_receipt", command),
                    catalogKey("tianji_process_worker_recovery_start_fk", "f", start,
                            "tianji_process_command_receipt", command),
                    catalogKey("tianji_process_worker_recovery_definition_fk", "f", definition,
                            "tianji_process_release_definition", definition))));
            rows.put("indexes:" + RECOVERY_RECEIPT, new ArrayList<>(List.of(
                    index(pk, command, true, true, null),
                    index("tianji_process_worker_recovery_job_uk", List.of("job_id"), true, false, null))));
        }

        private static Map<String, Object> catalogColumn(String name, String type, boolean cCollation) {
            var column = row(name);
            column.put("attname", name);
            column.put("type", type);
            column.put("attnotnull", true);
            column.put("collname", cCollation ? "C" : null);
            column.put("attidentity", "");
            column.put("attgenerated", "");
            column.put("default_value", null);
            return column;
        }

        private static Map<String, Object> catalogCheck(String name, String expression) {
            var check = row(name);
            check.put("expression", expression);
            return check;
        }

        private static Map<String, Object> catalogKey(String name, String type, List<String> fields,
                                                       String target, List<String> targetFields) {
            var key = row(name);
            boolean foreign = "f".equals(type);
            key.put("contype", type);
            key.put("fields", fields);
            key.put("target_schema", foreign ? "tianji_process" : null);
            key.put("target", target);
            key.put("target_fields", targetFields);
            key.put("deletion", foreign ? "a" : "");
            key.put("confupdtype", foreign ? "a" : "");
            key.put("confmatchtype", "s");
            key.put("condeferrable", false);
            key.put("condeferred", false);
            return key;
        }

        private void parseTables(String sql) {
            var matcher = Pattern.compile("CREATE TABLE ([a-z_]+) \\(").matcher(sql);
            while (matcher.find()) {
                String table = matcher.group(1); int open = matcher.end() - 1;
                String body = sql.substring(open + 1, closing(sql, open));
                var columns = new ArrayList<Map<String, Object>>();
                var fields = Pattern.compile("(?m)^    ([a-z_0-9]+) (bigint|integer|boolean|varchar\\(\\d+\\)|char\\(\\d+\\)|timestamptz\\(6\\))([^\\n]*)").matcher(body);
                while (fields.find()) {
                    String type = fields.group(2).replace("varchar(", "character varying(")
                            .replace("char(", "character(").replace("timestamptz(6)", "timestamp(6) with time zone");
                    var row = row(fields.group(1)); row.put("attname", fields.group(1)); row.put("type", type);
                    row.put("attnotnull", fields.group(3).contains("NOT NULL")); row.put("collname", fields.group(3).contains("COLLATE \"C\"") ? "C" : null);
                    row.put("attidentity", ""); row.put("attgenerated", ""); row.put("default_value", null); columns.add(row);
                }
                rows.put("columns:" + table, columns); rows.put("checks:" + table, checks(body));
                var keys = new ArrayList<Map<String, Object>>();
                var key = Pattern.compile("CONSTRAINT ([a-z_]+)\\s+(PRIMARY KEY|FOREIGN KEY)\\s*\\(([^)]+)\\)").matcher(body);
                while (key.find()) {
                    var row = row(key.group(1)); boolean foreign = "FOREIGN KEY".equals(key.group(2));
                    row.put("contype", foreign ? "f" : "p"); row.put("fields", fields(key.group(3)));
                    row.put("target_schema", null); row.put("target", null); row.put("target_fields", List.of());
                    row.put("deletion", ""); row.put("confupdtype", ""); row.put("confmatchtype", "s");
                    row.put("condeferrable", false); row.put("condeferred", false);
                    if (foreign) {
                        var reference = Pattern.compile("\\s*REFERENCES ([a-z_]+)\\s*\\(([^)]+)\\)").matcher(body.substring(key.end()));
                        assertTrue(reference.find()); row.put("target_schema", "tianji_process"); row.put("target", reference.group(1));
                        row.put("target_fields", fields(reference.group(2))); row.put("deletion", "a"); row.put("confupdtype", "a");
                    }
                    keys.add(row);
                }
                rows.put("keys:" + table, keys);
                var indexes = new ArrayList<Map<String, Object>>();
                keys.stream().filter(row -> "p".equals(row.get("contype"))).findFirst().ifPresent(primary ->
                        indexes.add(index((String) primary.get("name"), (List<String>) primary.get("fields"), true, true, null)));
                rows.put("indexes:" + table, indexes);
            }
        }

        private void parseIndexes(String sql) {
            var matcher = Pattern.compile("CREATE (UNIQUE )?INDEX ([a-z_]+)\\s+ON ([a-z_]+)\\s*\\(([^)]+)\\)(?:\\s+WHERE ([^;]+))?;").matcher(sql);
            while (matcher.find()) rows.computeIfAbsent("indexes:" + matcher.group(3), ignored -> new ArrayList<>())
                    .add(index(matcher.group(2), fields(matcher.group(4)), matcher.group(1) != null, false, matcher.group(5)));
        }

        private static List<Map<String, Object>> checks(String body) {
            var result = new ArrayList<Map<String, Object>>();
            var matcher = Pattern.compile("CONSTRAINT ([a-z_0-9]+)\\s+CHECK\\s*\\(").matcher(body);
            while (matcher.find()) {
                var row = row(matcher.group(1)); int open = matcher.end() - 1;
                row.put("expression", body.substring(open + 1, closing(body, open))); result.add(row);
            }
            return result;
        }

        private static Map<String, Object> row(String name) {
            var row = new LinkedHashMap<String, Object>();
            row.put("name", name); row.put("conname", name); row.put("relname", name); row.put("convalidated", true); return row;
        }

        private static Map<String, Object> index(String name, List<String> fields, boolean unique,
                                                 boolean primary, String predicate) {
            var row = row(name); row.put("fields", fields); row.put("indisunique", unique); row.put("indisprimary", primary);
            row.put("predicate", predicate); row.put("amname", "btree"); row.put("indnkeyatts", fields.size()); row.put("indnatts", fields.size());
            for (String flag : List.of("indisvalid", "indisready", "indislive", "default_classes", "column_collations", "ascending")) row.put(flag, true);
            return row;
        }

        private static List<String> fields(String value) {
            return Arrays.stream(value.split(",")).map(String::trim).toList();
        }

        private static int closing(String value, int open) {
            int depth = 0; boolean quoted = false;
            for (int index = open; index < value.length(); index++) {
                char current = value.charAt(index);
                if (current == '\'') {
                    if (quoted && index + 1 < value.length() && value.charAt(index + 1) == '\'') index++;
                    else quoted = !quoted;
                } else if (!quoted) {
                    if (current == '(') depth++;
                    else if (current == ')' && --depth == 0) return index;
                }
            }
            throw new AssertionError("Unbalanced fixture DDL");
        }

        private static ResultSet resultSet(Map<String, Object> row) throws Exception {
            var result = mock(ResultSet.class);
            when(result.getString(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)) == null
                    ? null : row.get(invocation.getArgument(0)).toString());
            when(result.getObject(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));
            when(result.getBoolean(anyString())).thenAnswer(invocation -> Boolean.TRUE.equals(row.get(invocation.getArgument(0))));
            when(result.getInt(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)) instanceof Number number ? number.intValue() : 0);
            when(result.getArray(anyString())).thenAnswer(invocation -> {
                Object value = row.get(invocation.getArgument(0)); if (value == null) return null;
                var array = mock(java.sql.Array.class); when(array.getArray()).thenReturn(((List<?>) value).toArray(String[]::new)); return array;
            });
            return result;
        }
    }
}
