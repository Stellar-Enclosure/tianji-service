package top.egon.cola.platform.tianji.worker;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/** Read-only check of the explicitly provisioned business receipt schema. */
public final class WorkerExecutionSchemaVerifier {
    private final JdbcTemplate jdbc;
    private final String schema;

    public WorkerExecutionSchemaVerifier(DataSource source, String schema) {
        if (source == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.jdbc = new JdbcTemplate(source);
        this.schema = schema;
    }

    public void verify() {
        try {
            int expected = checksum();
            List<Boolean> history = jdbc.query("SELECT success,checksum FROM \"" + schema + "\".tianji_worker_schema_history "
                            + "WHERE version='1' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                            && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
            if (history.size() != 1 || !history.getFirst()) throw invalid();
            verifyColumns();
            verifyConstraints();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Worker execution schema verification failed", failure);
        }
    }

    private void verifyColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("source_id", "character varying(128)", true, true),
                column("tenant_id", "bigint", true, false),
                column("idempotency_key", "character varying(128)", true, true),
                column("task_id", "character varying(255)", true, true),
                column("occurrence_id", "character varying(255)", true, true),
                column("topic", "character varying(128)", true, true),
                column("schema_version", "character varying(128)", true, true),
                column("effect_safety", "character varying(32)", true, false),
                column("handler_version", "character varying(128)", true, true),
                column("input_sha256", "character(64)", true, true),
                column("result_sha256", "character(64)", true, true),
                column("business_receipt_id", "character varying(255)", true, true),
                column("output_json", "jsonb", true, false),
                column("committed_at", "timestamp with time zone", true, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname='tianji_worker_execution_receipt' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname"))));
        }, schema);
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyConstraints() {
        Map<String, String> actual = new HashMap<>();
        jdbc.query("""
                SELECT c.conname, c.convalidated, pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_worker_execution_receipt' AND c.contype IN ('p','u','c')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            actual.put(rs.getString("conname"), rs.getString("definition"));
        }, schema);
        Map<String, String> expected = Map.of(
                "tianji_worker_execution_receipt_pkey", "PRIMARY KEY (source_id, tenant_id, idempotency_key)",
                "tianji_worker_execution_receipt_tenant_id_check", "CHECK ((tenant_id > 0))",
                "tianji_worker_execution_receipt_input_sha256_check", "CHECK ((input_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "tianji_worker_execution_receipt_result_sha256_check", "CHECK ((result_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "tianji_worker_execution_receipt_effect_safety_check", "CHECK (((effect_safety)::text = ANY ((ARRAY['READ_ONLY'::character varying, 'TRANSACTIONAL_RECEIPT'::character varying, 'EXTERNAL_IDEMPOTENT'::character varying])::text[])))",
                "tianji_worker_execution_receipt_output_json_check", "CHECK (((jsonb_typeof(output_json) = 'object'::text) AND (octet_length((output_json)::text) <= 131072)))");
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private static int checksum() {
        try (InputStream stream = WorkerExecutionSchemaVerifier.class.getClassLoader()
                .getResourceAsStream("db/tianji-worker/V1__create_worker_execution_receipt.sql")) {
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
            throw new IllegalStateException("Worker execution migration resource is unavailable", failure);
        }
    }

    private static Map.Entry<String, Column> column(String name, String type, boolean required, boolean cCollation) {
        return Map.entry(name, new Column(type, required, cCollation));
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Worker execution schema verification failed"); }
    private record Column(String type, boolean required, boolean cCollation) { }
}
