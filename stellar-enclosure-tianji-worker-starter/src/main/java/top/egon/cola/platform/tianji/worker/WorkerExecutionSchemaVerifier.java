package top.egon.cola.platform.tianji.worker;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/** Read-only check of the explicitly provisioned business receipt schema. */
public final class WorkerExecutionSchemaVerifier {
    private static final String V3 = "V3__add_worker_effect_receipt.sql";
    private static final String V3_SHA256 = "919f17a54f5eb1fcbfa8b39f092fb61bad53b4f40662a5c40376e9a2c9775172";
    private static final List<String> EFFECT_HISTORY = List.of("V1__create_worker_execution_receipt.sql",
            "V2__add_nontransactional_worker_intent.sql", V3);
    private static final Map<String, Column> EFFECT_COLUMNS = Map.ofEntries(
            column("source_id", "character varying(128)", true, true),
            column("tenant_id", "bigint", true, false),
            column("effect_key", "character varying(128)", true, true),
            column("capability_key", "character varying(128)", true, true),
            column("capability_version", "bigint", true, false),
            column("input_schema_sha256", "character(64)", true, true),
            column("business_input_sha256", "character(64)", true, true),
            column("state", "character varying(16)", true, true),
            column("result_json", "jsonb", false, false),
            column("business_receipt_id", "character varying(255)", false, true),
            column("revision", "bigint", true, false),
            column("created_at", "timestamp with time zone", true, false),
            column("updated_at", "timestamp with time zone", true, false),
            column("output_schema_sha256", "character(64)", true, true),
            column("effect_mode", "character varying(32)", true, true),
            column("handler_version", "character varying(128)", true, true),
            column("target_id", "character varying(128)", false, true),
            column("effect_fingerprint", "character(64)", true, true),
            column("result_sha256", "character(64)", false, true),
            column("dispatch_generation", "bigint", true, false),
            column("last_reconciliation", "jsonb", false, false),
            column("reconciled_at", "timestamp(6) with time zone", false, false));
    private static final Map<String, String> EFFECT_CONSTRAINTS = Map.ofEntries(
            Map.entry("tianji_worker_effect_receipt_pkey", "PRIMARY KEY (source_id, tenant_id, effect_key)"),
            Map.entry("worker_effect_identity_check", "CHECK ((((source_id)::text ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'::text) AND (tenant_id > 0) AND ((effect_key)::text ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'::text) AND ((capability_key)::text ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'::text) AND (capability_version >= 1) AND (capability_version <= '999999999999999'::bigint) AND (btrim((handler_version)::text) <> ''::text) AND ((handler_version)::text !~ '[[:cntrl:]]'::text) AND (input_schema_sha256 ~ '^[0-9a-f]{64}$'::text) AND (output_schema_sha256 ~ '^[0-9a-f]{64}$'::text) AND (business_input_sha256 ~ '^[0-9a-f]{64}$'::text) AND (effect_fingerprint ~ '^[0-9a-f]{64}$'::text)))"),
            Map.entry("worker_effect_lifecycle_check", "CHECK (((revision >= 1) AND (dispatch_generation >= 1) AND (dispatch_generation <= '999999999999999'::bigint) AND (updated_at >= created_at) AND (((effect_mode)::text = 'EXTERNAL_IDEMPOTENT'::text) OR ((dispatch_generation = 1) AND ((state)::text = 'COMPLETED'::text)))))"),
            Map.entry("worker_effect_mode_check", "CHECK (((((effect_mode)::text = 'TRANSACTIONAL_RECEIPT'::text) AND (target_id IS NULL)) OR (((effect_mode)::text = ANY (ARRAY['READ_ONLY'::text, 'EXTERNAL_IDEMPOTENT'::text])) AND (target_id IS NOT NULL) AND ((target_id)::text ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'::text))))"),
            Map.entry("worker_effect_reconciliation_check", "CHECK ((((last_reconciliation IS NULL) AND (reconciled_at IS NULL)) OR (((effect_mode)::text = 'EXTERNAL_IDEMPOTENT'::text) AND (last_reconciliation IS NOT NULL) AND (reconciled_at IS NOT NULL) AND (reconciled_at >= created_at) AND (reconciled_at <= updated_at) AND (jsonb_typeof(last_reconciliation) = 'object'::text) AND (octet_length((last_reconciliation)::text) <= 16384) AND (last_reconciliation ?& ARRAY['status'::text, 'dispatchGeneration'::text, 'authorityRef'::text, 'observedAt'::text, 'validUntil'::text, 'evidenceSha256'::text]) AND ((last_reconciliation - ARRAY['status'::text, 'dispatchGeneration'::text, 'authorityRef'::text, 'observedAt'::text, 'validUntil'::text, 'evidenceSha256'::text]) = '{}'::jsonb) AND (jsonb_typeof((last_reconciliation -> 'status'::text)) = 'string'::text) AND ((last_reconciliation ->> 'status'::text) = ANY (ARRAY['APPLIED'::text, 'NOT_APPLIED_FINAL'::text])) AND (jsonb_typeof((last_reconciliation -> 'dispatchGeneration'::text)) = 'number'::text) AND ((last_reconciliation ->> 'dispatchGeneration'::text) ~ '^[1-9][0-9]{0,14}$'::text) AND (jsonb_typeof((last_reconciliation -> 'authorityRef'::text)) = 'string'::text) AND ((last_reconciliation ->> 'authorityRef'::text) ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$'::text) AND (jsonb_typeof((last_reconciliation -> 'observedAt'::text)) = 'string'::text) AND (jsonb_typeof((last_reconciliation -> 'validUntil'::text)) = 'string'::text) AND (jsonb_typeof((last_reconciliation -> 'evidenceSha256'::text)) = 'string'::text) AND ((last_reconciliation ->> 'evidenceSha256'::text) ~ '^[0-9a-f]{64}$'::text))))"),
            Map.entry("worker_effect_result_check", "CHECK (((((state)::text = ANY (ARRAY['STARTED'::text, 'UNKNOWN'::text])) AND (result_json IS NULL) AND (business_receipt_id IS NULL) AND (result_sha256 IS NULL)) OR (((state)::text = 'COMPLETED'::text) AND (result_json IS NOT NULL) AND (business_receipt_id IS NOT NULL) AND (result_sha256 IS NOT NULL) AND (btrim((business_receipt_id)::text) <> ''::text) AND ((business_receipt_id)::text !~ '[[:cntrl:]]'::text) AND (result_sha256 ~ '^[0-9a-f]{64}$'::text) AND (jsonb_typeof(result_json) = 'object'::text) AND (octet_length(regexp_replace((result_json)::text, '(\"(?:[^\"\\\\]|\\\\.)*\")| '::text, '\\1'::text, 'g'::text)) <= 65536))))"));
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final String schema;

    public WorkerExecutionSchemaVerifier(DataSource source, String schema) {
        if (source == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.source = source;
        this.jdbc = new JdbcTemplate(source);
        this.schema = schema;
    }

    public void verify() {
        try {
            int expected = checksum("V1__create_worker_execution_receipt.sql");
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

    /** The optional external/read-only path needs its separately provisioned V2 intent table. */
    public void verifyNonTransactional() {
        verifyIntentReadable();
        try {
            String table = '"' + schema + "\".tianji_worker_nontransactional_intent";
            Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'INSERT')", Boolean.class, table);
            if (!Boolean.TRUE.equals(permitted)) throw invalid();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Worker non-transactional schema verification failed", failure);
        }
    }

    /** Transactional handlers must see any conflicting intent after V2 is installed. */
    public void verifyIntentReadable() {
        verify();
        try {
            int expected = checksum("V2__add_nontransactional_worker_intent.sql");
            List<Boolean> history = jdbc.query("SELECT success,checksum FROM \"" + schema + "\".tianji_worker_schema_history "
                            + "WHERE version='2' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                            && rs.getObject("checksum") != null && rs.getInt("checksum") == expected);
            if (history.size() != 1 || !history.getFirst()) throw invalid();
            verifyIntentColumns();
            verifyIntentConstraints();
            String table = '"' + schema + "\".tianji_worker_nontransactional_intent";
            Boolean permitted = jdbc.queryForObject("SELECT has_table_privilege(current_user, ?, 'SELECT')", Boolean.class, table);
            if (!Boolean.TRUE.equals(permitted)) throw invalid();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Worker non-transactional schema verification failed", failure);
        }
    }

    /** Checks V3 on a stable catalog snapshot without changing the schema or its history. */
    public void verifyEffectsV3() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw invalid();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setTimeout(10);
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only')='off'
                            AND current_setting('server_encoding')='UTF8'
                            AND current_setting('session_replication_role')='origin'
                            AND current_setting('standard_conforming_strings')='on'
                        """, Boolean.class))) throw invalid();
                jdbc.queryForObject("SELECT set_config('search_path','pg_catalog',true)", String.class);
                verifyEffectHistory();
                verifyIntentReadable();
                verifyEffectColumns();
                verifyEffectConstraints();
                verifyEffectRole();
            });
        } catch (DataAccessException | TransactionException failure) {
            throw new IllegalStateException("Worker effect schema verification failed", failure);
        }
    }

    private void verifyEffectHistory() {
        var migrations = jdbc.query("SELECT installed_rank,version,type,script,success,checksum FROM ONLY \"" + schema
                        + "\".tianji_worker_schema_history ORDER BY installed_rank",
                (rs, row) -> new HistoryRowVO(rs.getInt("installed_rank"), rs.getString("version"), rs.getString("type"),
                        rs.getString("script"), rs.getBoolean("success"), rs.getObject("checksum", Integer.class)));
        // Existing owner installs may have an explicit version-0 Flyway baseline.
        var first = migrations.isEmpty() ? null : migrations.getFirst();
        boolean baseline = first != null && first.rank() == 1 && "0".equals(first.version())
                && "BASELINE".equals(first.type()) && first.success() && first.checksum() == null;
        int offset = baseline ? 1 : 0;
        if (migrations.size() != EFFECT_HISTORY.size() + offset) throw invalid();
        for (int index = 0; index < EFFECT_HISTORY.size(); index++) {
            var installed = migrations.get(index + offset);
            var expected = EFFECT_HISTORY.get(index);
            if (installed.rank() != index + offset + 1 || !Integer.toString(index + 1).equals(installed.version())
                    || !"SQL".equals(installed.type()) || !expected.equals(installed.file()) || !installed.success()
                    || !Objects.equals(checksum(expected), installed.checksum())) throw invalid();
        }
    }

    private void verifyEffectColumns() {
        var actual = new HashMap<String, Column>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid,a.atttypmod) AS type, a.attnotnull,
                       a.attidentity, a.attgenerated, a.atthasdef, a.attcollation='pg_catalog."C"'::regcollation AS c_collation
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                WHERE n.nspname=? AND t.relname='tianji_worker_effect_receipt' AND t.relkind='r'
                """, rs -> {
            if (!rs.getString("attidentity").isEmpty() || !rs.getString("attgenerated").isEmpty()
                    || rs.getBoolean("atthasdef") || actual.put(rs.getString("attname"), new Column(
                        rs.getString("type"), rs.getBoolean("attnotnull"), rs.getBoolean("c_collation"))) != null) throw invalid();
        }, schema);
        if (!EFFECT_COLUMNS.equals(actual)) throw invalid();
    }

    private void verifyEffectConstraints() {
        var actual = new HashMap<String, String>();
        jdbc.query("""
                SELECT c.conname, c.contype, c.convalidated, c.connoinherit, c.condeferrable, c.condeferred,
                       COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       pg_get_constraintdef(c.oid,false) AS expression
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_worker_effect_receipt' AND c.contype<>'n'
                """, rs -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced")
                    || "c".equals(rs.getString("contype")) && rs.getBoolean("connoinherit")
                    || rs.getBoolean("condeferrable") || rs.getBoolean("condeferred")
                    || actual.put(rs.getString("conname"), rs.getString("expression")) != null) throw invalid();
        }, schema);
        if (!EFFECT_CONSTRAINTS.equals(actual)) throw invalid();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=1 AND bool_and(x.relname='tianji_worker_effect_receipt_pkey' AND am.amname='btree'
                    AND i.indisprimary AND i.indisunique AND i.indisvalid AND i.indisready AND i.indislive AND i.indimmediate
                    AND i.indnkeyatts=3 AND i.indnatts=3 AND i.indexprs IS NULL AND i.indpred IS NULL
                    AND NOT COALESCE((to_jsonb(i)->>'indnullsnotdistinct')::boolean,false)
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indoption) flag(value) WHERE flag.value<>0)
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indclass) op(oid) JOIN pg_opclass o ON o.oid=op.oid WHERE NOT o.opcdefault)
                    AND NOT EXISTS (SELECT 1 FROM unnest(i.indkey) WITH ORDINALITY k(num,pos)
                        JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num
                        WHERE i.indcollation[(k.pos-1)::integer]<>a.attcollation))
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_class x ON x.oid=i.indexrelid
                JOIN pg_am am ON am.oid=x.relam JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_worker_effect_receipt'
                """, Boolean.class, schema))) throw invalid();
    }

    private void verifyEffectRole() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*)=2 AND bool_and(c.relkind='r' AND c.relpersistence='p' AND NOT c.relispartition
                    AND NOT c.relrowsecurity AND NOT c.relforcerowsecurity AND NOT pg_has_role(c.relowner,'MEMBER')
                    AND has_schema_privilege(n.oid,'USAGE') AND NOT has_schema_privilege(n.oid,'CREATE')
                    AND NOT pg_has_role(n.nspowner,'MEMBER')
                    AND NOT EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=current_user
                        AND (r.rolsuper OR r.rolbypassrls OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication))
                    AND has_table_privilege(c.oid,'SELECT') AND NOT has_table_privilege(c.oid,'DELETE,TRUNCATE,TRIGGER,REFERENCES')
                    AND NOT has_any_column_privilege(c.oid,'REFERENCES,SELECT WITH GRANT OPTION,INSERT WITH GRANT OPTION,UPDATE WITH GRANT OPTION')
                    AND CASE WHEN c.relname='tianji_worker_effect_receipt'
                        THEN has_table_privilege(c.oid,'INSERT') AND has_table_privilege(c.oid,'UPDATE')
                        ELSE NOT has_any_column_privilege(c.oid,'INSERT,UPDATE') END
                    AND CASE WHEN current_setting('server_version_num')::integer>=170000
                        THEN NOT has_table_privilege(c.oid,'MAINTAIN') ELSE true END
                    AND NOT EXISTS (SELECT 1 FROM pg_inherits h WHERE h.inhrelid=c.oid OR h.inhparent=c.oid)
                    AND NOT EXISTS (SELECT 1 FROM pg_rewrite r WHERE r.ev_class=c.oid)
                    AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid))
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
                WHERE n.nspname=? AND c.relname IN ('tianji_worker_effect_receipt','tianji_worker_schema_history')
                """, Boolean.class, schema))) throw invalid();
    }

    private void verifyIntentColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column("source_id", "character varying(128)", true, true),
                column("tenant_id", "bigint", true, false),
                column("idempotency_key", "character varying(128)", true, true),
                column("effect_safety", "character varying(32)", true, false),
                column("target_id", "character varying(128)", true, true),
                column("handler_version", "character varying(128)", true, true),
                column("input_sha256", "character(64)", true, true),
                column("created_at", "timestamp with time zone", true, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid, a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname='tianji_worker_nontransactional_intent' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"),
                    rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        }, schema);
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyIntentConstraints() {
        Map<String, String> actual = new HashMap<>();
        jdbc.query("""
                SELECT c.conname, c.convalidated, pg_get_constraintdef(c.oid) AS definition
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_worker_nontransactional_intent' AND c.contype IN ('p','u','c')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            actual.put(rs.getString("conname"), rs.getString("definition"));
        }, schema);
        Map<String, String> expected = Map.of(
                "tianji_worker_nontransactional_intent_pkey", "PRIMARY KEY (source_id, tenant_id, idempotency_key)",
                "tianji_worker_nontransactional_intent_tenant_id_check", "CHECK ((tenant_id > 0))",
                "tianji_worker_nontransactional_intent_input_sha256_check", "CHECK ((input_sha256 ~ '^[0-9a-f]{64}$'::text))",
                "tianji_worker_nontransactional_intent_effect_safety_check", "CHECK (((effect_safety)::text = ANY ((ARRAY['READ_ONLY'::character varying, 'EXTERNAL_IDEMPOTENT'::character varying])::text[])))");
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
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

    private static int checksum(String file) {
        try (InputStream stream = WorkerExecutionSchemaVerifier.class.getClassLoader()
                .getResourceAsStream("db/tianji-worker/" + file)) {
            if (stream == null) throw invalid();
            if (V3.equals(file)) {
                byte[] bytes = stream.readNBytes(32_769);
                if (bytes.length > 32_768 || !V3_SHA256.equals(WorkerDigests.rawSha256(bytes))) throw invalid();
                return checksum(new java.io.ByteArrayInputStream(bytes));
            }
            return checksum(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("Worker execution migration resource is unavailable", failure);
        }
    }

    private static int checksum(InputStream stream) throws IOException {
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
    }

    private static Map.Entry<String, Column> column(String name, String type, boolean required, boolean cCollation) {
        return Map.entry(name, new Column(type, required, cCollation));
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Worker execution schema verification failed"); }
    private record HistoryRowVO(int rank, String version, String type, String file, boolean success, Integer checksum) { }

    private record Column(String type, boolean required, boolean cCollation) { }
}
