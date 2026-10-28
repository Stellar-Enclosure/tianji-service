package top.egon.cola.platform.tianji.process.server;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/** Read-only startup verification of the platform's migration history and critical storage constraints. */
public final class ProcessApiSchemaVerifier {
    private static final String RELEASE = "tianji_process_release";
    private static final String DEFINITION = "tianji_process_release_definition";
    private static final String RECEIPT = "tianji_process_command_receipt";
    private final JdbcTemplate jdbc;
    private final String schema;

    public ProcessApiSchemaVerifier(DataSource source, String schema) {
        if (source == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.jdbc = new JdbcTemplate(source);
        this.schema = schema;
    }

    public void verify() {
        try {
            verifyHistory();
            verifyColumns();
            verifyConstraints();
            verifyReceiptIndex();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Process API schema verification failed", failure);
        }
    }

    private void verifyHistory() {
        Map<String, Integer> expected = Map.of("1", checksum("V1__create_process_release_store.sql"),
                "2", checksum("V2__add_decision_receipt_result.sql"),
                "3", checksum("V3__bind_process_receipts_to_issuer.sql"));
        Map<String, Integer> actual = new HashMap<>();
        jdbc.query("SELECT version, checksum, success FROM \"" + schema + "\".tianji_process_schema_history "
                        + "WHERE version IN ('1','2','3') AND type='SQL'", rs -> {
                    if (!rs.getBoolean("success") || rs.getObject("checksum") == null
                            || actual.put(rs.getString("version"), rs.getInt("checksum")) != null) throw invalid();
                });
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column(RELEASE, "tenant_id", "bigint", true, false),
                column(RELEASE, "release_key", "character varying(128)", true, true),
                column(RELEASE, "state", "character varying(16)", true, false),
                column(RELEASE, "manifest_sha256", "character(64)", true, false),
                column(RELEASE, "publisher_type", "character varying(16)", true, false),
                column(RELEASE, "publisher_id", "character varying(256)", true, true),
                column(RELEASE, "publisher_issuer", "character varying(64)", true, true),
                column(RELEASE, "created_at", "timestamp with time zone", true, false),
                column(DEFINITION, "tenant_id", "bigint", true, false),
                column(DEFINITION, "release_key", "character varying(128)", true, true),
                column(DEFINITION, "model_type", "character varying(8)", true, false),
                column(DEFINITION, "definition_key", "character varying(128)", true, true),
                column(DEFINITION, "engine_definition_id", "character varying(255)", true, true),
                column(DEFINITION, "engine_deployment_id", "character varying(255)", true, true),
                column(DEFINITION, "engine_version", "integer", true, false),
                column(DEFINITION, "model_sha256", "character(64)", true, false),
                column(DEFINITION, "input_schema", "jsonb", true, false),
                column(RECEIPT, "tenant_id", "bigint", true, false),
                column(RECEIPT, "subject_type", "character varying(16)", true, false),
                column(RECEIPT, "subject_issuer", "character varying(64)", true, true),
                column(RECEIPT, "subject_id", "character varying(256)", true, true),
                column(RECEIPT, "command_type", "character varying(32)", true, true),
                column(RECEIPT, "command_id", "character varying(128)", true, true),
                column(RECEIPT, "request_sha256", "character(64)", true, false),
                column(RECEIPT, "release_key", "character varying(128)", true, true),
                column(RECEIPT, "correlation_id", "character varying(128)", true, true),
                column(RECEIPT, "state", "character varying(16)", true, false),
                column(RECEIPT, "resource_id", "character varying(255)", false, true),
                column(RECEIPT, "created_at", "timestamp with time zone", true, false),
                column(RECEIPT, "decision_result", "jsonb", false, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT t.relname, a.attname, format_type(a.atttypid, a.atttypmod) AS type,
                       a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname IN ('tianji_process_release', 'tianji_process_release_definition',
                    'tianji_process_command_receipt') AND t.relkind IN ('r', 'p')
                """, rs -> {
                    actual.put(rs.getString("relname") + "." + rs.getString("attname"),
                            new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
                }, schema);
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyConstraints() {
        Map<String, Set<String>> actual = new HashMap<>();
        Set<String> foreignKeys = new HashSet<>();
        jdbc.query("""
                SELECT t.relname, c.contype, c.convalidated, pg_get_constraintdef(c.oid) AS definition,
                       c.confrelid = to_regclass(format('%I.%I', ?, 'tianji_process_release')) AS release_target
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname IN ('tianji_process_release', 'tianji_process_release_definition',
                    'tianji_process_command_receipt') AND c.contype IN ('p','u','c','f')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            String table = rs.getString("relname");
            String definition = rs.getString("definition");
            actual.computeIfAbsent(table, ignored -> new HashSet<>()).add(definition);
            if ("f".equals(rs.getString("contype")) && rs.getBoolean("release_target")
                    && definition.startsWith("FOREIGN KEY (tenant_id, release_key) REFERENCES ")
                    && definition.endsWith("(tenant_id, release_key)")) foreignKeys.add(table);
        }, schema, schema);
        if (!actual.getOrDefault(RELEASE, Set.of()).containsAll(Set.of("PRIMARY KEY (tenant_id, release_key)",
                        "CHECK ((((publisher_issuer)::text = 'LEGACY'::text) OR ((publisher_issuer)::text ~ '^[0-9a-f]{64}$'::text)))"))
                || !actual.getOrDefault(DEFINITION, Set.of()).containsAll(Set.of(
                        "PRIMARY KEY (tenant_id, release_key, model_type, definition_key)",
                        "UNIQUE (model_type, engine_definition_id)", "UNIQUE (model_type, engine_deployment_id)",
                        "CHECK ((jsonb_typeof(input_schema) = 'object'::text))"))
                || !actual.getOrDefault(RECEIPT, Set.of()).containsAll(Set.of(
                        "PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)",
                        "CHECK ((((subject_issuer)::text = 'LEGACY'::text) OR ((subject_issuer)::text ~ '^[0-9a-f]{64}$'::text)))",
                        "CHECK (((((state)::text = 'PENDING'::text) AND (resource_id IS NULL)) OR (((state)::text = 'APPLIED'::text) AND (resource_id IS NOT NULL))))",
                        "CHECK (((((command_type)::text = 'DECISION'::text) AND ((state)::text = 'PENDING'::text) AND (decision_result IS NULL)) OR (((command_type)::text = 'DECISION'::text) AND ((state)::text = 'APPLIED'::text) AND (decision_result IS NOT NULL) AND (jsonb_typeof(decision_result) = 'object'::text)) OR (((command_type)::text <> 'DECISION'::text) AND (decision_result IS NULL))))"))
                || !foreignKeys.containsAll(Set.of(DEFINITION, RECEIPT))) throw invalid();
    }

    private void verifyReceiptIndex() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname=? AND t.relname='tianji_process_command_receipt'
                  AND x.relname='tianji_process_start_receipt_instance_idx' AND i.indisunique AND i.indisvalid AND i.indisready
                  AND pg_get_indexdef(i.indexrelid) LIKE '% (tenant_id, resource_id) WHERE %'
                  AND pg_get_expr(i.indpred, i.indrelid)=?
                """, Integer.class, schema, "(((command_type)::text = 'START'::text) AND ((state)::text = 'APPLIED'::text))");
        if (count == null || count != 1) throw invalid();
    }

    private static int checksum(String name) {
        String path = "db/tianji-process/" + name;
        try (InputStream source = ProcessApiSchemaVerifier.class.getClassLoader().getResourceAsStream(path)) {
            if (source == null) throw invalid();
            CRC32 crc = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
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
            throw new IllegalStateException("Process API migration resources are unavailable", failure);
        }
    }

    private static Map.Entry<String, Column> column(String table, String name, String type, boolean required, boolean cCollation) {
        return Map.entry(table + "." + name, new Column(type, required, cCollation));
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Process API schema verification failed"); }
    private record Column(String type, boolean required, boolean cCollation) { }
}
