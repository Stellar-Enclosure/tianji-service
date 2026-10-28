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

    /** Linked process submissions additionally require the separately provisioned V7 fence. */
    public void verifyLinked() {
        verify();
        try {
            transaction.executeWithoutResult(status -> {
                int expected = checksum("V7__add_process_dispatch_fence.sql");
                List<Boolean> history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history "
                                + "WHERE version='7' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                                && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                verifyLinkedColumns();
                Set<Key> constraints = Set.of(
                        key("agent_process_dispatch", "p", "tenant_id", "subject_issuer", "subject_id", "idempotency_key"),
                        key("agent_process_dispatch", "u", "tenant_id", "source_id", "scope_type", "scope_id", "occurrence_id"),
                        key("agent_process_dispatch", "u", "tenant_id", "run_id"),
                        foreign("agent_process_dispatch", RUN_KEY, "agent_run", RUN_KEY, "a"));
                if (!readConstraints().containsAll(constraints)) throw invalid();
                verifyLinkedChecks();
                String table = "tianji_agent.agent_process_dispatch";
                Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'SELECT') "
                        + "AND has_table_privilege(current_user, ?, 'INSERT') AND has_table_privilege(current_user, ?, 'UPDATE')",
                        Boolean.class, table, table, table);
                if (!Boolean.TRUE.equals(permitted)) throw invalid();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    /** SERVICE execution additionally requires the separately provisioned V8 grant store. */
    public void verifyServiceGrants() {
        verifyLinked();
        try {
            transaction.executeWithoutResult(status -> {
                int expected = checksum("V8__add_service_execution_grant.sql");
                List<Boolean> history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history "
                                + "WHERE version='8' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                                && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                verifyServiceGrantColumns();
                Set<Key> constraints = Set.of(key("agent_run_service_execution_grant", "p", "tenant_id", "run_id"),
                        foreign("agent_run_service_execution_grant", RUN_KEY, "agent_process_dispatch", RUN_KEY, "c"));
                if (!readConstraints().containsAll(constraints)) throw invalid();
                Map<String, String> checks = new HashMap<>();
                jdbc.query("""
                        SELECT c.conname, c.convalidated, pg_get_constraintdef(c.oid) AS definition FROM pg_constraint c
                        JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                        WHERE n.nspname='tianji_agent' AND t.relname='agent_run_service_execution_grant' AND c.contype='c'
                        """, rs -> {
                    if (!rs.getBoolean("convalidated")) throw invalid();
                    checks.put(rs.getString("conname"), normalizedCheck(rs.getString("definition")));
                });
                Map<String, String> expectedChecks = Map.of(
                        "agent_service_grant_tenant_positive", "CHECKtenant_id>0",
                        "agent_service_grant_binding_hash", "CHECKbinding_sha256~'^[0-9a-f]{64}$'",
                        "agent_service_grant_epoch_positive", "CHECKissue_epoch>0",
                        "agent_service_grant_id_format", "CHECKgrant_idISNULLORgrant_id~'^8[0-9]{62}$'",
                        "agent_service_grant_cipher_format", "CHECKciphertextISNULLORciphertext~~'ENC:v1:%'",
                        "agent_service_grant_cipher_size", "CHECKciphertextISNULLORoctet_lengthciphertext<=32768",
                        "agent_service_grant_cipher_presence", "CHECKgrant_idISNULL=ciphertextISNULL",
                        "agent_service_grant_expiry_presence", "CHECKgrant_idISNULL=expires_atISNULL");
                for (var entry : expectedChecks.entrySet()) if (!entry.getValue().equals(checks.get(entry.getKey()))) throw invalid();
                String table = "tianji_agent.agent_run_service_execution_grant";
                Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'SELECT') "
                        + "AND has_table_privilege(current_user, ?, 'INSERT') AND has_table_privilege(current_user, ?, 'UPDATE')",
                        Boolean.class, table, table, table);
                if (!Boolean.TRUE.equals(permitted)) throw invalid();
            });
        } catch (DataAccessException failure) {
            throw invalid();
        }
    }

    private void verifyServiceGrantColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("tenant_id", "bigint", true, false), column("run_id", "character varying(64)", true, false),
                column("subject_client_id", "character varying(128)", true, true), column("subject_app_id", "character varying(128)", true, true),
                column("subject_business_code", "character varying(128)", true, true),
                column("subject_application_code", "character varying(128)", true, true),
                column("subject_environment", "character varying(128)", true, true),
                column("subject_resource_uri", "character varying(2048)", true, true),
                column("binding_sha256", "character(64)", true, true), column("issue_epoch", "bigint", true, false),
                column("grant_id", "character varying(63)", false, true), column("ciphertext", "text", false, false),
                column("expires_at", "timestamp with time zone", false, false), column("updated_at", "timestamp with time zone", true, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname='agent_run_service_execution_grant' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname"))));
        });
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    static String normalizedCheck(String definition) {
        if (definition == null) throw invalid();
        var result = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < definition.length(); i++) {
            char value = definition.charAt(i);
            if (value == '\'') {
                result.append(value);
                if (quoted && i + 1 < definition.length() && definition.charAt(i + 1) == '\'') result.append(definition.charAt(++i));
                else quoted = !quoted;
            } else if (quoted) result.append(value);
            else if (definition.startsWith("::character varying", i)) i += "::character varying".length() - 1;
            else if (definition.startsWith("::text", i)) i += "::text".length() - 1;
            else if (!Character.isWhitespace(value) && value != '(' && value != ')') result.append(value);
        }
        if (quoted) throw invalid();
        return result.toString();
    }

    private void verifyLinkedColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("tenant_id", "bigint", true, false),
                column("subject_issuer", "character varying(64)", true, true),
                column("subject_id", "character varying(256)", true, true),
                column("idempotency_key", "character varying(128)", true, true),
                column("source_id", "character varying(128)", true, true),
                column("scope_type", "character varying(8)", true, false),
                column("scope_id", "character varying(255)", true, true),
                column("occurrence_id", "character varying(255)", true, true),
                column("definition_sha256", "character(64)", true, true),
                column("dispatch_not_after", "timestamp with time zone", true, false),
                column("link_sha256", "character(64)", true, true),
                column("request_sha256", "character(64)", false, true),
                column("state", "character varying(8)", true, false),
                column("run_id", "character varying(64)", false, false),
                column("created_at", "timestamp with time zone", true, false),
                column("closed_at", "timestamp with time zone", false, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname='agent_process_dispatch' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname"))));
        });
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyLinkedChecks() {
        Set<String> checks = new HashSet<>();
        jdbc.query("""
                SELECT c.convalidated, pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname='agent_process_dispatch' AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            checks.add(rs.getString("definition"));
        });
        if (!checks.containsAll(Set.of(
                "CHECK ((tenant_id > 0))",
                "CHECK (((subject_issuer)::text ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((definition_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((link_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK ((request_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "CHECK (((scope_type)::text = ANY ((ARRAY['BPMN'::character varying, 'CMMN'::character varying])::text[])))",
                "CHECK (((state)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying])::text[])))",
                "CHECK (((((state)::text = 'OPEN'::text) AND (closed_at IS NULL)) OR (((state)::text = 'CLOSED'::text) AND (closed_at IS NOT NULL))))",
                "CHECK ((((run_id IS NULL) AND (request_sha256 IS NULL)) OR ((run_id IS NOT NULL) AND (request_sha256 IS NOT NULL))))"))) throw invalid();
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
        if (!readConstraints().containsAll(requiredConstraints())) throw invalid();
    }

    private Set<Key> readConstraints() {
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
        return actual;
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
    private static Map.Entry<String, Column> column(String name, String type, boolean required, boolean cCollation) {
        return Map.entry(name, new Column(type, required, cCollation));
    }
    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent storage verification failed");
    }
    private record Key(String table, String type, List<String> columns, String target, List<String> targetColumns, String deletion) { }
    private record Column(String type, boolean required, boolean cCollation) { }
}
