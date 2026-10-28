package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

/** Read-only startup check for the pre-provisioned Agent ledger and session schema. */
public final class AgentStorageVerifier {
    private static final Map<String, String> MIGRATIONS = Map.of(
            "1", "V1__create_agent_ledger.sql",
            "2", "V2__add_run_governance.sql",
            "3", "V3__add_adk_session_store.sql",
            "4", "V4__add_adk_session_codec_versions.sql",
            "5", "V5__bind_agent_ownership_to_issuer.sql",
            "6", "V6__add_execution_grant_evidence.sql");
    private static final Map<String, String> COLUMNS = Map.ofEntries(
            Map.entry("agent_definition", "tenant_id, definition_key, active_version, created_at"),
            Map.entry("agent_definition_version", "tenant_id, definition_key, version, content_sha256, specification, created_at"),
            Map.entry("agent_run", "tenant_id, run_id, definition_key, definition_version, subject_type, subject_issuer, subject_id, idempotency_key, request_hash, session_id, correlation_id, status, wait_reason, cancel_requested_at, revision, last_event_sequence, attempt, lease_epoch, lease_owner, lease_expires_at, deadline_at, execution_snapshot, result, error_code, created_at, finished_at, model_calls_reserved, tool_calls_reserved, active_model_calls, active_tool_calls, input_tokens_reserved, output_tokens_reserved, estimated_cost_reserved"),
            Map.entry("agent_event", "tenant_id, run_id, sequence, event_id, event_type, schema_version, author, branch, tool_call_id, correlation_id, native_event_id, payload, occurred_at"),
            Map.entry("agent_run_command_receipt", "tenant_id, subject_type, subject_issuer, subject_id, command_id, operation, request_hash, run_id, result, created_at"),
            Map.entry("agent_budget_reservation", "tenant_id, run_id, reservation_id, lease_epoch, kind, tool_version, input_tokens, output_tokens, estimated_cost, state, actual_input_tokens, actual_output_tokens, actual_cost, created_at, settled_at"),
            Map.entry("agent_session", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_state, state_codec_version, revision, last_event_sequence, created_at, updated_at"),
            Map.entry("agent_session_event", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence, native_event_id, event_timestamp_ms, payload"),
            Map.entry("agent_session_scope_state", "tenant_id, app_name, scope_type, subject_type, subject_issuer, subject_id, state_key, state_value, value_codec_version"),
            Map.entry("agent_session_lease", "tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, lease_epoch, lease_owner, lease_expires_at"),
            Map.entry("agent_run_execution_grant", "tenant_id, run_id, binding_sha256, issue_epoch, grant_id, ciphertext, expires_at, updated_at"));
    private static final List<String> SESSION_OWNER = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "app_name", "session_id");
    private static final List<String> RUN_KEY = List.of("tenant_id", "run_id");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AgentStorageVerifier(DataSource source) {
        if (source == null) throw new IllegalArgumentException("Agent DataSource is required");
        jdbc = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    /** Does not create, alter or repair schema objects; migration remains an operator action. */
    public void verify() {
        try {
            transaction.executeWithoutResult(status -> {
                Boolean primary = jdbc.queryForObject(
                        "SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only') = 'off'", Boolean.class);
                if (!Boolean.TRUE.equals(primary)) throw invalid();
                verifyHistory();
                verifyColumns();
                verifyIdentityColumns();
                verifyConstraints();
                verifyNativeEventIndex();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    private void verifyHistory() {
        Map<String, Integer> expected = new HashMap<>();
        MIGRATIONS.forEach((version, file) -> expected.put(version, checksum(file)));
        Map<String, Integer> actual = new HashMap<>();
        jdbc.query("SELECT version, type, checksum, success FROM tianji_agent.flyway_schema_history", rs -> {
            if (!rs.getBoolean("success")) throw invalid();
            String version = rs.getString("version");
            if (version != null && MIGRATIONS.containsKey(version)) {
                if (!"SQL".equals(rs.getString("type")) || rs.getObject("checksum") == null
                        || actual.put(version, rs.getInt("checksum")) != null) throw invalid();
            }
        });
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyColumns() {
        COLUMNS.forEach((table, columns) -> jdbc.query(
                "SELECT " + columns + " FROM tianji_agent." + table + " WHERE false", (rs, row) -> 1));
    }

    private void verifyIdentityColumns() {
        Set<String> expected = new HashSet<>();
        for (String table : List.of("agent_run", "agent_run_command_receipt", "agent_session", "agent_session_event",
                "agent_session_scope_state", "agent_session_lease")) {
            expected.add(table + ".subject_issuer");
            expected.add(table + ".subject_id");
        }
        Set<String> actual = new HashSet<>();
        jdbc.query("""
                SELECT t.relname, a.attname, format_type(a.atttypid, a.atttypmod) AS type,
                       a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid = a.attcollation
                WHERE n.nspname = 'tianji_agent' AND t.relkind IN ('r', 'p')
                  AND a.attname IN ('subject_issuer', 'subject_id')
                """, rs -> {
            String key = rs.getString("relname") + "." + rs.getString("attname");
            if (expected.contains(key)) {
                String type = key.endsWith(".subject_issuer") ? "character varying(64)" : "character varying(256)";
                if (!type.equals(rs.getString("type")) || !rs.getBoolean("attnotnull")
                        || !"C".equals(rs.getString("collname"))) throw invalid();
                actual.add(key);
            }
        });
        if (!actual.equals(expected)) throw invalid();
    }

    private void verifyConstraints() {
        Set<Key> actual = new HashSet<>();
        jdbc.query("""
                SELECT t.relname, c.contype, c.convalidated, c.condeferrable, i.indisvalid, i.indisready,
                       rn.nspname AS ref_schema, rt.relname AS ref_table, c.confdeltype,
                       ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num, pos)
                             JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.pos) AS columns,
                       ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num, pos)
                             JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.pos) AS ref_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_index i ON i.indexrelid=c.conindid
                LEFT JOIN pg_class rt ON rt.oid=c.confrelid LEFT JOIN pg_namespace rn ON rn.oid=rt.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relkind IN ('r', 'p') AND c.contype IN ('p', 'u', 'f')
                """, rs -> {
            String type = rs.getString("contype");
            if (!rs.getBoolean("convalidated") || rs.getBoolean("condeferrable")) return;
            if (!"f".equals(type) && (!rs.getBoolean("indisvalid") || !rs.getBoolean("indisready"))) return;
            if ("f".equals(type) && !"tianji_agent".equals(rs.getString("ref_schema"))) return;
            actual.add(new Key(rs.getString("relname"), type, array(rs.getArray("columns")),
                    rs.getString("ref_table"), array(rs.getArray("ref_columns")),
                    "f".equals(type) ? rs.getString("confdeltype") : null));
        });
        if (!actual.containsAll(requiredConstraints())) throw invalid();
    }

    private static Set<Key> requiredConstraints() {
        Set<Key> keys = new HashSet<>();
        keys.add(key("agent_definition", "p", "tenant_id", "definition_key"));
        keys.add(key("agent_definition_version", "p", "tenant_id", "definition_key", "version"));
        keys.add(key("agent_run", "p", "tenant_id", "run_id"));
        keys.add(key("agent_run", "u", "tenant_id", "subject_type", "subject_issuer", "subject_id", "idempotency_key"));
        keys.add(key("agent_event", "p", "tenant_id", "run_id", "sequence"));
        keys.add(key("agent_event", "u", "tenant_id", "event_id"));
        keys.add(key("agent_run_command_receipt", "p", "tenant_id", "subject_type", "subject_issuer", "subject_id", "operation", "command_id"));
        keys.add(key("agent_budget_reservation", "p", "tenant_id", "run_id", "reservation_id"));
        keys.add(key("agent_session", "p", SESSION_OWNER));
        keys.add(key("agent_session_event", "p", append(SESSION_OWNER, "sequence")));
        keys.add(key("agent_session_event", "u", append(SESSION_OWNER, "native_event_id")));
        keys.add(key("agent_session_scope_state", "p", "tenant_id", "app_name", "scope_type", "subject_type", "subject_issuer", "subject_id", "state_key"));
        keys.add(key("agent_session_lease", "p", SESSION_OWNER));
        keys.add(key("agent_run_execution_grant", "p", RUN_KEY));
        keys.add(foreign("agent_definition_version", List.of("tenant_id", "definition_key"), "agent_definition", List.of("tenant_id", "definition_key"), "a"));
        keys.add(foreign("agent_run", List.of("tenant_id", "definition_key", "definition_version"), "agent_definition_version", List.of("tenant_id", "definition_key", "version"), "a"));
        for (String table : List.of("agent_event", "agent_run_command_receipt", "agent_budget_reservation")) {
            keys.add(foreign(table, RUN_KEY, "agent_run", RUN_KEY, "a"));
        }
        keys.add(foreign("agent_run_execution_grant", RUN_KEY, "agent_run", RUN_KEY, "c"));
        keys.add(foreign("agent_session_event", SESSION_OWNER, "agent_session", SESSION_OWNER, "c"));
        keys.add(foreign("agent_session_lease", SESSION_OWNER, "agent_session", SESSION_OWNER, "c"));
        return keys;
    }

    private void verifyNativeEventIndex() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname='tianji_agent' AND t.relname='agent_event' AND x.relname='agent_event_native_id_idx'
                  AND i.indisunique AND i.indisvalid AND i.indisready AND i.indimmediate
                  AND i.indnkeyatts=3 AND i.indnatts=3
                  AND ARRAY(SELECT a.attname::text FROM unnest(i.indkey) WITH ORDINALITY k(num, pos)
                            JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.num ORDER BY k.pos)
                      = ARRAY['tenant_id', 'run_id', 'native_event_id']
                  AND pg_get_expr(i.indpred, i.indrelid)='(native_event_id IS NOT NULL)'
                """, Integer.class);
        if (count == null || count != 1) throw invalid();
    }

    private static int checksum(String file) {
        try (InputStream stream = AgentStorageVerifier.class.getClassLoader().getResourceAsStream("db/tianji-agent/" + file)) {
            if (stream == null) throw invalid();
            CRC32 crc = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first && line.startsWith("\ufeff")) line = line.substring(1);
                    first = false;
                    crc.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            return (int) crc.getValue();
        } catch (IOException failure) {
            throw invalid();
        }
    }

    private static List<String> array(java.sql.Array value) throws java.sql.SQLException {
        return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray()));
    }
    private static List<String> append(List<String> values, String last) {
        var result = new java.util.ArrayList<>(values);
        result.add(last);
        return List.copyOf(result);
    }
    private static Key key(String table, String type, String... columns) { return key(table, type, List.of(columns)); }
    private static Key key(String table, String type, List<String> columns) { return new Key(table, type, columns, null, List.of(), null); }
    private static Key foreign(String table, List<String> columns, String target, List<String> targetColumns, String deletion) {
        return new Key(table, "f", columns, target, targetColumns, deletion);
    }
    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent storage verification failed");
    }
    private record Key(String table, String type, List<String> columns, String target, List<String> targetColumns, String deletion) { }
}
