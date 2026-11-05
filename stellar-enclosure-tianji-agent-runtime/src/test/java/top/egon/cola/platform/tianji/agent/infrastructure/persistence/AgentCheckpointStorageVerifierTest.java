package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AgentCheckpointStorageVerifierTest {
    private static final String MIGRATION = "V16__add_agent_checkpoint.sql";
    private static final int CHECKSUM = -415542202;
    private static final List<String> RUN = List.of("tenant_id", "run_id");
    private static final List<String> CHECKPOINT = List.of("tenant_id", "run_id", "attempt", "checkpoint_no");
    private static final List<String> SESSION = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
            "app_name", "session_id", "sequence");
    private static final List<String> SESSION_EVENT = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id",
            "app_name", "session_id", "session_event_sequence");

    @Test
    void requires_immutable_v16_resource() throws Exception {
        byte[] bytes = migration();
        assertEquals(4053, bytes.length);
        assertEquals("8521cd49e7ae4401f7dd9847b27918eeeca26b2ef926aa3d43821d900ed2ca36",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertEquals(CHECKSUM, AgentStorageVerifier.checksum(MIGRATION));
        String sql = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(2, sql.split("ON DELETE CASCADE", -1).length - 1);
        assertFalse(sql.contains("GRANT "));
        assertFalse(sql.contains("ALTER TABLE "));
    }

    @Test
    void rejects_missing_v16_history() throws Exception {
        var catalog = new Catalog(false);
        catalog.history.clear();
        check(catalog, true);
    }

    @Test
    void admits_exact_primary_catalog_and_insert_only_role() throws Exception {
        var catalog = new Catalog();
        check(catalog, false);
        assertTrue(catalog.observedRoleSql.contains("has_table_privilege(c.oid,'INSERT')"));
        assertTrue(catalog.observedRoleSql.contains("NOT has_any_column_privilege(c.oid,'SELECT,UPDATE,REFERENCES,INSERT WITH GRANT OPTION')"));
        assertTrue(catalog.observedRoleSql.contains("NOT pg_has_role(c.relowner,'MEMBER')"));
        assertTrue(catalog.observedRoleSql.contains("has_table_privilege(c.oid,'MAINTAIN')"));
        assertTrue(catalog.observedRoleSql.contains("pg_trigger tr WHERE tr.tgconstraint=fk.oid"));
        assertTrue(catalog.observedRoleSql.contains("count(*)=4 AND bool_and(tr.tgisinternal AND tr.tgenabled='O')"));
        assertTrue(catalog.observedIndexSql.contains("agent_checkpoint_session_event_idx"));
        assertTrue(catalog.observedIndexSql.contains("i.indpred IS NULL"));
    }

    @Test
    void rejects_history_schema_and_role_drift() throws Exception {
        rejects(catalog -> catalog.primary = false);
        rejects(catalog -> catalog.history.add(new HashMap<>(catalog.history.getFirst())));
        rejects(catalog -> catalog.history.getFirst().put("success", false));
        rejects(catalog -> catalog.history.getFirst().put("type", "JDBC"));
        rejects(catalog -> catalog.history.getFirst().put("script", "V16__other.sql"));
        rejects(catalog -> catalog.history.getFirst().put("checksum", CHECKSUM + 1));
        rejects(catalog -> catalog.columns.removeLast());
        rejects(catalog -> catalog.columns.get(1).put("collation_schema", "spoofed"));
        rejects(catalog -> catalog.columns.getFirst().put("default_value", "0"));
        rejects(catalog -> catalog.columns.getFirst().put("attidentity", "a"));
        rejects(catalog -> catalog.keys.getLast().put("ref_columns", List.of("tenant_id", "session_id")));
        rejects(catalog -> catalog.keys.getLast().put("confdeltype", "a"));
        rejects(catalog -> catalog.constraintShape = false);
        rejects(catalog -> catalog.indexShape = false);
        rejects(catalog -> catalog.checks.getFirst().put("definition", "CHECK (tenant_id >= 0)"));
        rejects(catalog -> catalog.checks.getFirst().put("convalidated", false));
        rejects(catalog -> catalog.permitted = false);
    }

    @Test
    void refuses_to_join_an_ambient_transaction() {
        var source = mock(DataSource.class);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(AgentLedgerException.class, () -> new AgentStorageVerifier(source).verifyCheckpointCapture());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void rejects(Consumer<Catalog> mutation) throws Exception {
        var catalog = new Catalog();
        mutation.accept(catalog);
        check(catalog, true);
    }

    private static void check(Catalog catalog, boolean rejected) throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        try (var templates = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            var verifier = spy(new AgentStorageVerifier(source));
            doNothing().when(verifier).verify();
            if (rejected) {
                var error = assertThrows(AgentLedgerException.class, verifier::verifyCheckpointCapture);
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, error.code());
                assertEquals("Agent storage verification failed", error.getMessage());
            } else {
                assertDoesNotThrow(verifier::verifyCheckpointCapture);
                verify(verifier).verify();
            }
        }
    }

    private static byte[] migration() throws Exception {
        try (var input = AgentCheckpointStorageVerifierTest.class.getResourceAsStream("/db/tianji-agent/" + MIGRATION)) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private static final class Catalog {
        private final List<Map<String, Object>> history = new ArrayList<>(List.of(
                row("type", "SQL", "script", MIGRATION, "success", true, "checksum", CHECKSUM)));
        private final List<Map<String, Object>> keys = new ArrayList<>(List.of(
                row("relname", "agent_checkpoint", "contype", "p", "convalidated", true,
                        "indisvalid", true, "indisready", true, "columns", CHECKPOINT),
                row("relname", "agent_checkpoint", "contype", "f", "convalidated", true,
                        "ref_schema", "tianji_agent", "ref_table", "agent_run", "confdeltype", "c",
                        "columns", RUN, "ref_columns", RUN),
                row("relname", "agent_checkpoint", "contype", "f", "convalidated", true,
                        "ref_schema", "tianji_agent", "ref_table", "agent_session_event", "confdeltype", "c",
                        "columns", SESSION_EVENT, "ref_columns", SESSION)));
        private final List<Map<String, Object>> columns = new ArrayList<>();
        private final List<Map<String, Object>> checks = new ArrayList<>();
        private boolean primary = true;
        private boolean constraintShape = true;
        private boolean indexShape = true;
        private boolean permitted = true;
        private String observedRoleSql;
        private String observedIndexSql;

        Catalog() throws Exception {
            this(true);
        }

        Catalog(boolean loadSchema) throws Exception {
            if (!loadSchema) return;
            String sql = new String(migration(), StandardCharsets.UTF_8);
            var definitions = Pattern.compile("(?m)^    ([a-z_0-9]+) (bigint|integer|jsonb|varchar\\([0-9]+\\)|char\\(64\\)|timestamptz\\(6\\))"
                    + "( COLLATE \"C\")? NOT NULL,$").matcher(sql);
            while (definitions.find()) {
                String type = definitions.group(2).replace("varchar", "character varying").replace("char(", "character(");
                if ("timestamptz(6)".equals(type)) type = "timestamp(6) with time zone";
                boolean c = definitions.group(3) != null;
                columns.add(row("attname", definitions.group(1), "type", type, "attnotnull", true, "attidentity", "",
                        "attgenerated", "", "collname", c ? "C" : null, "collation_schema", c ? "pg_catalog" : null));
            }
            assertEquals(29, columns.size());
            var expressions = Pattern.compile("(?ms)^    CONSTRAINT (agent_checkpoint_[a-z_]+) (CHECK .*?)(?=,\\n    CONSTRAINT|\\n\\);)").matcher(sql);
            while (expressions.find()) {
                checks.add(row("conname", expressions.group(1), "convalidated", true,
                        "definition", expressions.group(2).replace("length(run_id)", "length((run_id)::text)")));
            }
            assertEquals(9, checks.size());
        }

        Object answer(InvocationOnMock call) throws Exception {
            Object[] args = call.getArguments();
            if (!(args[0] instanceof String sql)) throw new AssertionError("Unexpected catalog operation");
            if (sql.contains("pg_is_in_recovery()")) return primary;
            if (sql.contains("WHERE version='16'")) {
                var mapper = (RowMapper<?>) args[1];
                var found = new ArrayList<>();
                for (var row : history) found.add(mapper.mapRow(resultSet(row), found.size()));
                return found;
            }
            if (sql.contains("count(*)=3")) return constraintShape;
            if (sql.contains("FROM pg_index i JOIN pg_class t")) {
                observedIndexSql = sql;
                return indexShape;
            }
            if (sql.contains("has_any_column_privilege")) {
                observedRoleSql = sql;
                return permitted;
            }
            var callback = (RowCallbackHandler) args[1];
            List<Map<String, Object>> rows;
            if (sql.contains("pg_get_constraintdef")) rows = checks;
            else if (sql.contains("pg_attribute a ON a.attrelid=t.oid AND a.attnum>0")) rows = columns;
            else if (sql.contains("c.contype IN ('p', 'u', 'f')")) rows = keys;
            else throw new AssertionError("Unexpected catalog query");
            for (var row : rows) callback.processRow(resultSet(row));
            return null;
        }
    }

    private static Map<String, Object> row(Object... pairs) {
        var fields = new HashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) fields.put((String) pairs[i], pairs[i + 1]);
        return fields;
    }

    private static ResultSet resultSet(Map<String, Object> row) throws Exception {
        var result = mock(ResultSet.class);
        when(result.getObject(anyString())).thenAnswer(call -> row.get(call.getArgument(0)));
        when(result.getString(anyString())).thenAnswer(call -> (String) row.get(call.getArgument(0)));
        when(result.getBoolean(anyString())).thenAnswer(call -> Boolean.TRUE.equals(row.get(call.getArgument(0))));
        when(result.getInt(anyString())).thenAnswer(call -> ((Number) row.getOrDefault(call.getArgument(0), 0)).intValue());
        for (String key : List.of("columns", "ref_columns")) {
            if (row.get(key) instanceof List<?> values) {
                var array = mock(Array.class);
                when(array.getArray()).thenReturn(values.toArray(String[]::new));
                when(result.getArray(key)).thenReturn(array);
            }
        }
        return result;
    }
}
