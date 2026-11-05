package top.egon.cola.platform.tianji.process.server;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskStorageVerifier;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static top.egon.cola.platform.tianji.process.server.task.HumanTaskStorageVerifier.signature;

/** Read-only startup verification of the platform's migration history and critical storage constraints. */
public final class ProcessApiSchemaVerifier {
    private static final String RELEASE = "tianji_process_release";
    private static final String DEFINITION = "tianji_process_release_definition";
    private static final String RECEIPT = "tianji_process_command_receipt";
    private static final String WORKER = "tianji_worker_lease_receipt";
    private static final String CASE_FILE = "V8__add_formal_cmmn_case_support.sql";
    private static final String CASE_SHA256 = "d2ef3198b53e681835239ed374e126d226cd40afef658819dbfbdd6372d82a44";
    private static final String CANCEL_FILE = "V9__add_process_cancellation_receipts.sql";
    private static final String CANCEL_SHA256 = "dd07ea808cb19f85e983f99a38950add4229208e66dc3c10a60970c1ffc0711b";
    private static final String CANCEL_RECEIPT = "tianji_process_cancel_receipt";
    private static final String RECOVERY_FILE = "V10__add_process_worker_recovery_receipts.sql";
    private static final String RECOVERY_SHA256 = "5a3511659af4c44b2455e55e15d373d6c643fea29ed513b658c59595b5ef6dc9";
    private static final String RECOVERY_RECEIPT = "tianji_process_worker_recovery_receipt";
    private static final String DISPATCH = "tianji_process_agent_dispatch";
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

    /** Verifies only the recovery capability's audit and native storage. */
    public void verifyWorkerRecovery() {
        jdbc.setQueryTimeout(5);
        try {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))
                    || laterMigrations().size() != 3) throw invalid();
            var audit = tableContracts(recoveryMigration(), Map.of(RECOVERY_RECEIPT, 32)).get(RECOVERY_RECEIPT);
            verifyCaseColumns(RECOVERY_RECEIPT, audit.columns());
            verifyCaseChecks(RECOVERY_RECEIPT, audit.checks(), false);
            verifyCaseKeys(RECOVERY_RECEIPT, audit.keys());
            verifyCaseIndexes(RECOVERY_RECEIPT, audit.indexes());
            verifyCaseRole(RECOVERY_RECEIPT, false, true);
            verifyRecoveryNativeStorage();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Process Worker recovery storage schema verification failed", failure);
        }
    }

    private void verifyRecoveryNativeStorage() {
        String idType = "character varying(64)";
        String identifier = "character varying(255)";
        String lock = " AND has_any_column_privilege(c.oid,'UPDATE')";
        verifyRecoveryNativeTable("act_ru_execution", Map.ofEntries(
                Map.entry("id_", idType), Map.entry("rev_", "integer"), Map.entry("proc_inst_id_", idType),
                Map.entry("proc_def_id_", idType), Map.entry("parent_id_", idType), Map.entry("tenant_id_", identifier),
                Map.entry("business_key_", identifier), Map.entry("suspension_state_", "integer"),
                Map.entry("act_id_", identifier)), lock);
        var job = Map.ofEntries(Map.entry("id_", idType), Map.entry("rev_", "integer"), Map.entry("type_", identifier),
                Map.entry("tenant_id_", identifier), Map.entry("process_instance_id_", idType), Map.entry("proc_def_id_", idType),
                Map.entry("execution_id_", idType), Map.entry("element_id_", identifier), Map.entry("scope_id_", identifier),
                Map.entry("sub_scope_id_", identifier), Map.entry("scope_type_", identifier),
                Map.entry("scope_definition_id_", identifier), Map.entry("handler_type_", identifier),
                Map.entry("handler_cfg_", "character varying(4000)"), Map.entry("correlation_id_", identifier));
        var deadletter = new HashMap<>(job);
        deadletter.put("exception_msg_", "character varying(4000)");
        verifyRecoveryNativeTable("act_ru_deadletter_job", deadletter, lock + " AND has_table_privilege(c.oid,'DELETE')");
        var external = new HashMap<>(job);
        external.put("retries_", "integer");
        external.put("lock_owner_", identifier);
        external.put("lock_exp_time_", "timestamp without time zone");
        verifyRecoveryNativeTable("act_ru_external_job", external, " AND has_table_privilege(c.oid,'INSERT')");
        for (String table : List.of("act_ru_job", "act_ru_timer_job", "act_ru_suspended_job")) {
            verifyRecoveryNativeTable(table, Map.of("id_", idType), "");
        }
    }

    private void verifyRecoveryNativeTable(String table, Map<String, String> columns, String extraPrivileges) {
        String pairs = String.join(",", java.util.Collections.nCopies(columns.size(), "(?,?)"));
        String sql = """
                SELECT NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                  AND NOT c.relispartition AND c.relpersistence='p'
                  AND has_schema_privilege(n.oid,'USAGE') AND has_table_privilege(c.oid,'SELECT')
                  AND EXISTS (SELECT 1 FROM pg_index i JOIN pg_attribute a ON a.attrelid=c.oid
                    WHERE i.indrelid=c.oid AND i.indisprimary AND i.indisunique
                      AND i.indisvalid AND i.indisready AND i.indislive AND i.indnkeyatts=1 AND i.indnatts=1
                      AND a.attname='id_' AND a.attnotnull AND NOT a.attisdropped AND i.indkey[0]=a.attnum)
                  AND (SELECT count(*) FROM pg_attribute a WHERE a.attrelid=c.oid AND a.attnum>0 AND NOT a.attisdropped
                      AND (a.attname::text,format_type(a.atttypid,a.atttypmod)) IN (
                """ + pairs + "))=?"
                + extraPrivileges + " FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace"
                + " WHERE n.nspname=? AND c.relname=? AND c.relkind='r'";
        var parameters = new ArrayList<Object>();
        columns.forEach((name, type) -> {
            parameters.add(name);
            parameters.add(type);
        });
        parameters.add(columns.size());
        parameters.add(schema);
        parameters.add(table);
        if (!Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, parameters.toArray()))) throw invalid();
    }

    /** Cancellation reads immutable Case provenance without enabling the Case write capability. */
    public void verifyCancellation() {
        jdbc.setQueryTimeout(5);
        verifyAgentDispatch();
        try {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))
                    || laterMigrations().size() < 2) throw invalid();
            String migration = cancellationMigration();
            var audit = tableContracts(migration, Map.of(CANCEL_RECEIPT, 26)).get(CANCEL_RECEIPT);
            verifyCaseColumns(CANCEL_RECEIPT, audit.columns());
            verifyCaseChecks(CANCEL_RECEIPT, audit.checks(), false);
            verifyCaseKeys(CANCEL_RECEIPT, audit.keys());
            verifyCaseIndexes(CANCEL_RECEIPT, audit.indexes());
            verifyCaseRole(CANCEL_RECEIPT, false, true);
            verifyCancellationProvenanceRead();
            verifyCaseRole(DISPATCH, true, false);
            verifyCaseIndexes(DISPATCH, Map.of("tianji_agent_dispatch_scope_open_idx",
                    new CaseIndex(List.of("tenant_id", "scope_type", "scope_id", "occurrence_id"), false, false,
                            signature("state <> 'CLOSED'"))), false);
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Process cancellation storage schema verification failed", failure);
        }
    }

    private void verifyCancellationProvenanceRead() {
        String migration = caseMigration(), table = "tianji_case_start_provenance";
        var provenance = caseContracts(migration).get(table);
        verifyCaseColumns(table, provenance.columns());
        verifyCaseChecks(table, provenance.checks(), false);
        verifyCaseKeys(table, provenance.keys());
        verifyCaseIndexes(table, provenance.indexes());
        verifyCaseRole(table, false, false);
        var checks = caseChecks(migration.substring(migration.indexOf("ALTER TABLE tianji_process_release_definition"),
                migration.indexOf("ALTER TABLE tianji_human_task_receipt")));
        String expression = checks.remove("tianji_process_release_definition_model_type_v8");
        if (expression == null || !checks.isEmpty()) throw invalid();
        verifyCaseChecks(DEFINITION, Map.of("tianji_process_release_definition_model_type_check", expression), true);
    }

    /** Formal Case activation verifies the complete additive schema without installing or repairing it. */
    public void verifyCase() {
        jdbc.setQueryTimeout(5);
        verify();
        try {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))
                    || !hasCaseMigration()) throw invalid();
            String migration = caseMigration();
            var contracts = caseContracts(migration);
            for (var entry : contracts.entrySet()) {
                String table = entry.getKey(); var contract = entry.getValue();
                verifyCaseColumns(table, contract.columns());
                verifyCaseChecks(table, contract.checks(), false);
                verifyCaseKeys(table, contract.keys());
                verifyCaseIndexes(table, contract.indexes());
                verifyCaseRole(table, "tianji_case_completion_inbox".equals(table));
            }
            var releaseChecks = caseChecks(migration.substring(migration.indexOf("ALTER TABLE tianji_process_release_definition"), migration.indexOf("ALTER TABLE tianji_human_task_receipt")));
            String releaseExpression = releaseChecks.remove("tianji_process_release_definition_model_type_v8");
            if (releaseExpression == null || !releaseChecks.isEmpty()) throw invalid();
            verifyCaseChecks(DEFINITION, Map.of("tianji_process_release_definition_model_type_check", releaseExpression), true);
            verifyCaseChecks("tianji_human_task_receipt", HumanTaskStorageVerifier.caseChecks(migration), true);
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Case storage schema verification failed", failure);
        }
    }

    /** Case-off task readers retain the exact V8 task authority throughout the known later prefix. */
    public boolean hasCaseMigration() { return !laterMigrations().isEmpty(); }

    private List<CaseVersion> laterMigrations() {
        var versions = jdbc.query("SELECT version,type,script,success,checksum FROM \"" + schema + "\".tianji_process_schema_history "
                        + "WHERE version IS NULL OR version NOT IN ('1','2','3','4','5','6','7') ORDER BY installed_rank",
                (rs, row) -> new CaseVersion(rs.getString("version"), rs.getString("type"), rs.getString("script"), rs.getBoolean("success"),
                        rs.getObject("checksum") == null ? null : rs.getInt("checksum")));
        var numbers = List.of("8", "9", "10");
        var files = List.of(CASE_FILE, CANCEL_FILE, RECOVERY_FILE);
        if (versions.size() > numbers.size()) throw invalid();
        for (int index = 0; index < versions.size(); index++) {
            var version = versions.get(index);
            String number = numbers.get(index), file = files.get(index);
            if (!number.equals(version.version()) || !"SQL".equals(version.type()) || !file.equals(version.script())
                    || !version.success() || version.checksum() == null || version.checksum() != checksum(file)) throw invalid();
            switch (number) {
                case "8" -> caseMigration();
                case "9" -> cancellationMigration();
                case "10" -> recoveryMigration();
                default -> throw invalid();
            }
        }
        return versions;
    }

    private void verifyCaseColumns(String table, Map<String, CaseColumn> expected) {
        var actual = new HashMap<String, CaseColumn>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,a.attidentity,a.attgenerated,
                       co.collname,pg_get_expr(d.adbin,d.adrelid) AS default_value
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                LEFT JOIN pg_attrdef d ON d.adrelid=t.oid AND d.adnum=a.attnum
                WHERE n.nspname=? AND t.relname=? AND t.relkind='r'
                """, rs -> {
            if (!rs.getString("attidentity").isEmpty() || !rs.getString("attgenerated").isEmpty()
                    || actual.put(rs.getString("attname"), new CaseColumn(rs.getString("type"), rs.getBoolean("attnotnull"),
                    "C".equals(rs.getString("collname")), rs.getString("default_value"))) != null) throw invalid();
        }, schema, table);
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyCaseChecks(String table, Map<String, String> expected, boolean modelAuthorityOnly) {
        var actual = new HashMap<String, String>();
        String authority = modelAuthorityOnly ? " AND EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid=t.oid AND a.attnum=ANY(c.conkey) AND a.attname IN ('model_type','start_command_type'))" : "";
        jdbc.query("SELECT c.conname,c.convalidated,pg_get_expr(c.conbin,c.conrelid) AS expression FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace "
                        + "WHERE n.nspname=? AND t.relname=? AND c.contype='c'" + authority, rs -> {
            if (!rs.getBoolean("convalidated") || actual.put(rs.getString("conname"), signature(rs.getString("expression"))) != null) throw invalid();
        }, schema, table);
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyCaseKeys(String table, Set<CaseKey> expected) {
        var actual = new HashSet<CaseKey>();
        jdbc.query("""
                SELECT c.conname,c.contype,c.convalidated,c.condeferrable,c.condeferred,c.confupdtype,c.confmatchtype,
                       CASE WHEN c.contype='f' THEN c.confdeltype::text ELSE '' END AS deletion,
                       tn.nspname AS target_schema,tt.relname AS target,
                       ARRAY(SELECT a.attname FROM unnest(c.conkey) WITH ORDINALITY k(num,ord)
                             JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.ord) AS fields,
                       ARRAY(SELECT a.attname FROM unnest(c.confkey) WITH ORDINALITY k(num,ord)
                             JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.ord) AS target_fields
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class tt ON tt.oid=c.confrelid LEFT JOIN pg_namespace tn ON tn.oid=tt.relnamespace
                WHERE n.nspname=? AND t.relname=? AND c.contype IN ('p','u','f')
                """, rs -> {
            boolean foreign = "f".equals(rs.getString("contype"));
            if (!rs.getBoolean("convalidated") || rs.getBoolean("condeferrable") || rs.getBoolean("condeferred")
                    || foreign && (!"a".equals(rs.getString("confupdtype")) || !"a".equals(rs.getString("deletion")) || !"s".equals(rs.getString("confmatchtype")))) throw invalid();
            if (!actual.add(new CaseKey(rs.getString("conname"), rs.getString("contype"), caseArray(rs.getArray("fields")),
                    rs.getString("target_schema"), rs.getString("target"), caseArray(rs.getArray("target_fields"))))) throw invalid();
        }, schema, table);
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyCaseIndexes(String table, Map<String, CaseIndex> expected) { verifyCaseIndexes(table, expected, true); }

    private void verifyCaseIndexes(String table, Map<String, CaseIndex> expected, boolean exact) {
        var actual = new HashMap<String, CaseIndex>();
        jdbc.query("""
                SELECT x.relname,i.indisunique,i.indisprimary,i.indisvalid,i.indisready,i.indislive,am.amname,i.indnkeyatts,i.indnatts,
                       pg_get_expr(i.indpred,i.indrelid) AS predicate,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indclass::oid[]) k(oid) JOIN pg_opclass o ON o.oid=k.oid WHERE NOT o.opcdefault) AS default_classes,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indcollation::oid[]) WITH ORDINALITY co(oid,ord)
                         JOIN unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,ord) USING(ord)
                         JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num WHERE co.oid<>a.attcollation) AS column_collations,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indoption::smallint[]) o(options) WHERE options<>0) AS ascending,
                       ARRAY(SELECT a.attname FROM unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,ord)
                             LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num ORDER BY k.ord) AS fields
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_am am ON am.oid=x.relam
                WHERE n.nspname=? AND t.relname=?
                """, rs -> {
            if (!rs.getBoolean("indisvalid") || !rs.getBoolean("indisready") || !rs.getBoolean("indislive") || !rs.getBoolean("default_classes")
                    || !rs.getBoolean("column_collations") || !rs.getBoolean("ascending") || !"btree".equals(rs.getString("amname"))
                    || rs.getInt("indnkeyatts") != rs.getInt("indnatts")) throw invalid();
            String predicate = rs.getString("predicate");
            if (actual.put(rs.getString("relname"), new CaseIndex(caseArray(rs.getArray("fields")), rs.getBoolean("indisunique"),
                    rs.getBoolean("indisprimary"), predicate == null ? null : signature(predicate))) != null) throw invalid();
        }, schema, table);
        if (exact ? !expected.equals(actual) : !actual.entrySet().containsAll(expected.entrySet())) throw invalid();
    }

    private void verifyCaseRole(String table, boolean mutable) { verifyCaseRole(table, mutable, true, false); }

    private void verifyCaseRole(String table, boolean mutable, boolean requireInsert) { verifyCaseRole(table, mutable, requireInsert, true); }

    private void verifyCaseRole(String table, boolean mutable, boolean requireInsert, boolean cancellation) {
        String columns = cancellation ? " AND NOT has_any_column_privilege(c.oid,'REFERENCES')"
                + (mutable ? "" : " AND NOT has_any_column_privilege(c.oid,'UPDATE')") : "";
        String insert = requireInsert ? " AND has_table_privilege(c.oid,'INSERT')" : "";
        String schemaOwnership = RECOVERY_RECEIPT.equals(table) ? " AND NOT pg_has_role(n.nspowner,'MEMBER')" : "";
        String update = mutable ? "has_table_privilege(c.oid,'UPDATE')" : "NOT has_table_privilege(c.oid,'UPDATE')";
        Boolean valid = jdbc.queryForObject("""
                SELECT NOT c.relrowsecurity AND NOT c.relforcerowsecurity AND NOT c.relispartition AND c.relpersistence='p'
                  AND has_schema_privilege(n.oid,'USAGE')
                  AND NOT EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname=current_user AND (r.rolsuper OR r.rolbypassrls))
                  AND NOT pg_has_role(c.relowner,'MEMBER')
                  AND has_table_privilege(c.oid,'SELECT')
                  AND NOT has_table_privilege(c.oid,'DELETE') AND NOT has_table_privilege(c.oid,'TRUNCATE')
                  AND NOT has_table_privilege(c.oid,'TRIGGER') AND NOT has_table_privilege(c.oid,'REFERENCES')
                  AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid
                    AND ((NOT tr.tgisinternal AND tr.tgenabled<>'D') OR (tr.tgisinternal AND tr.tgenabled<>'O')))
                """ + columns + insert + schemaOwnership + " AND " + update + " FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=? AND c.relkind='r'",
                Boolean.class, schema, table);
        if (!Boolean.TRUE.equals(valid)) throw invalid();
    }

    /** Descriptors are taken only from the byte-pinned packaged migration, never from caller SQL or database expressions. */
    private Map<String, CaseTable> caseContracts(String sql) {
        return tableContracts(sql, Map.of("tianji_case_start_provenance", 31,
                "tianji_case_plan_item_receipt", 24, "tianji_case_completion_inbox", 17));
    }

    private Map<String, CaseTable> tableContracts(String sql, Map<String, Integer> sizes) {
        var contracts = new HashMap<String, CaseTable>();
        var tables = Pattern.compile("CREATE TABLE ([a-z_]+) \\(").matcher(sql);
        while (tables.find()) {
            String table = tables.group(1); int open = tables.end() - 1; String body = sql.substring(open + 1, caseClosing(sql, open));
            var columns = new HashMap<String, CaseColumn>();
            var fields = Pattern.compile("(?m)^    ([a-z_0-9]+) (bigint|integer|boolean|varchar\\(\\d+\\)|char\\(\\d+\\)|timestamptz\\(6\\))([^\\n]*)").matcher(body);
            while (fields.find()) {
                String type = fields.group(2).replace("varchar(", "character varying(").replace("char(", "character(").replace("timestamptz(6)", "timestamp(6) with time zone");
                if (columns.put(fields.group(1), new CaseColumn(type, fields.group(3).contains("NOT NULL"), fields.group(3).contains("COLLATE \"C\""), null)) != null) throw invalid();
            }
            var keys = new HashSet<CaseKey>(); var indexes = new HashMap<String, CaseIndex>();
            var keyMatcher = Pattern.compile("CONSTRAINT ([a-z_]+)\\s+(PRIMARY KEY|FOREIGN KEY)\\s*\\(([^)]+)\\)").matcher(body);
            while (keyMatcher.find()) {
                String name = keyMatcher.group(1); var names = caseFields(keyMatcher.group(3)); boolean foreign = "FOREIGN KEY".equals(keyMatcher.group(2));
                if (foreign) {
                    var reference = Pattern.compile("\\s*REFERENCES ([a-z_]+)\\s*\\(([^)]+)\\)").matcher(body.substring(keyMatcher.end()));
                    if (!reference.lookingAt()) throw invalid(); keys.add(new CaseKey(name, "f", names, schema, reference.group(1), caseFields(reference.group(2))));
                } else { keys.add(new CaseKey(name, "p", names, null, null, List.of())); indexes.put(name, new CaseIndex(names, true, true, null)); }
            }
            var indexMatcher = Pattern.compile("CREATE (UNIQUE )?INDEX ([a-z_]+)\\s+ON ([a-z_]+)\\s*\\(([^)]+)\\)(?:\\s+WHERE ([^;]+))?;").matcher(sql);
            while (indexMatcher.find()) if (table.equals(indexMatcher.group(3))) {
                String predicate = indexMatcher.group(5);
                if (indexes.put(indexMatcher.group(2), new CaseIndex(caseFields(indexMatcher.group(4)), indexMatcher.group(1) != null, false,
                        predicate == null ? null : signature(predicate))) != null) throw invalid();
            }
            contracts.put(table, new CaseTable(Map.copyOf(columns), Map.copyOf(caseChecks(body)), Set.copyOf(keys), Map.copyOf(indexes)));
        }
        if (!contracts.keySet().equals(sizes.keySet())) throw invalid();
        for (var entry : sizes.entrySet()) if (contracts.get(entry.getKey()).columns().size() != entry.getValue()) throw invalid();
        return Map.copyOf(contracts);
    }

    private static HashMap<String, String> caseChecks(String sql) {
        var checks = new HashMap<String, String>(); var matcher = Pattern.compile("CONSTRAINT ([a-z_0-9]+)\\s+CHECK\\s*\\(").matcher(sql);
        while (matcher.find()) { int open = matcher.end() - 1; if (checks.put(matcher.group(1), signature(sql.substring(open + 1, caseClosing(sql, open)))) != null) throw invalid(); }
        return checks;
    }
    private static String caseMigration() {
        try (var input = ProcessApiSchemaVerifier.class.getResourceAsStream("/db/tianji-process/" + CASE_FILE)) {
            if (input == null) throw invalid(); byte[] bytes = input.readAllBytes();
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!CASE_SHA256.equals(actual)) throw invalid(); return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NoSuchAlgorithmException failure) { throw new IllegalStateException("Case migration resource is unavailable", failure); }
    }
    private static String cancellationMigration() {
        try (var input = ProcessApiSchemaVerifier.class.getResourceAsStream("/db/tianji-process/" + CANCEL_FILE)) {
            if (input == null) throw invalid(); byte[] bytes = input.readAllBytes();
            if (!CANCEL_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) throw invalid();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Process cancellation migration resource is unavailable", failure);
        }
    }
    private static String recoveryMigration() {
        try (var input = ProcessApiSchemaVerifier.class.getResourceAsStream("/db/tianji-process/" + RECOVERY_FILE)) {
            if (input == null) throw invalid();
            byte[] bytes = input.readAllBytes();
            if (!RECOVERY_SHA256.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) throw invalid();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Process Worker recovery migration resource is unavailable", failure);
        }
    }

    private static int caseClosing(String value, int open) {
        int depth = 0; boolean quoted = false;
        for (int i = open; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') { if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') i++; else quoted = !quoted; }
            else if (!quoted) { if (c == '(') depth++; else if (c == ')' && --depth == 0) return i; }
        }
        throw invalid();
    }
    private static List<String> caseFields(String value) { return Arrays.stream(value.split(",")).map(String::trim).toList(); }
    private static List<String> caseArray(java.sql.Array value) throws java.sql.SQLException { return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray())); }
    private record CaseVersion(String version, String type, String script, boolean success, Integer checksum) { }
    private record CaseColumn(String type, boolean required, boolean cCollation, String defaultValue) { }
    private record CaseKey(String name, String type, List<String> fields, String targetSchema, String target, List<String> targetFields) { }
    private record CaseIndex(List<String> fields, boolean unique, boolean primary, String predicate) { }
    private record CaseTable(Map<String, CaseColumn> columns, Map<String, String> checks, Set<CaseKey> keys, Map<String, CaseIndex> indexes) { }

    /** Worker activation also requires the separately deployed V4 lease schema. */
    public void verifyWorker() {
        verify();
        try {
            int expectedChecksum = checksum("V4__add_worker_lease_receipts.sql");
            List<Boolean> versions = jdbc.query("SELECT success,checksum FROM \"" + schema + "\".tianji_process_schema_history "
                            + "WHERE version='4' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                            && rs.getObject("checksum") != null && rs.getInt("checksum") == expectedChecksum);
            if (versions.size() != 1 || !versions.getFirst()) throw invalid();
            verifyWorkerColumns();
            verifyWorkerConstraints();
            verifyWorkerIndex();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Worker lease schema verification failed", failure);
        }
    }

    /** Linked dispatch is opt-in and never installs its V5 schema at startup. */
    public void verifyAgentDispatch() {
        verifyWorker();
        try {
            int expectedChecksum = checksum("V5__add_agent_dispatch_intent.sql");
            List<Boolean> versions = jdbc.query("SELECT success,checksum FROM \"" + schema + "\".tianji_process_schema_history "
                            + "WHERE version='5' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                            && rs.getObject("checksum") != null && rs.getInt("checksum") == expectedChecksum);
            if (versions.size() != 1 || !versions.getFirst()) throw invalid();
            verifyAgentDispatchColumns();
            verifyAgentDispatchConstraints();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Agent dispatch schema verification failed", failure);
        }
    }

    public void verifyAgentResultInbox() {
        verifyAgentDispatch();
        try {
            int expectedChecksum = checksum("V6__add_agent_result_inbox.sql");
            List<Boolean> versions = jdbc.query("SELECT success,checksum FROM \"" + schema + "\".tianji_process_schema_history "
                            + "WHERE version='6' AND type='SQL'", (rs, row) -> rs.getBoolean("success")
                            && rs.getObject("checksum") != null && rs.getInt("checksum") == expectedChecksum);
            if (versions.size() != 1 || !versions.getFirst()) throw invalid();
            verifyAgentInboxColumns();
            verifyAgentInboxConstraints();
        } catch (DataAccessException failure) {
            throw new IllegalStateException("Agent result Inbox schema verification failed", failure);
        }
    }

    private void verifyAgentInboxColumns() {
        Map<String, Column> expected = new HashMap<>();
        expected.put("tenant_id", new Column("bigint", true, false));
        expected.put("terminal_revision", new Column("bigint", true, false));
        for (String name : List.of("event_id", "producer_issuer", "owner_issuer", "event_sha256")) {
            expected.put(name, new Column("character(64)", true, true));
        }
        for (String name : List.of("producer_id", "source_id", "run_id")) {
            expected.put(name, new Column("character varying(128)", true, true));
        }
        for (String name : List.of("producer_subject", "owner_subject")) {
            expected.put(name, new Column("character varying(256)", true, true));
        }
        for (String name : List.of("scope_id", "occurrence_id")) expected.put(name, new Column("character varying(255)", true, true));
        for (String name : List.of("received_at", "next_attempt_at", "retry_not_after")) {
            expected.put(name, new Column("timestamp with time zone", true, false));
        }
        expected.put("scope_type", new Column("character varying(8)", true, false));
        expected.put("state", new Column("character varying(16)", true, false));
        expected.put("event_payload", new Column("jsonb", true, false));
        expected.put("attempt_count", new Column("integer", true, false));
        expected.put("decided_at", new Column("timestamp with time zone", false, false));
        expected.put("decision_code", new Column("character varying(64)", false, true));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname='tianji_process_agent_result_inbox' AND t.relkind='r'
                """, rs -> {
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        }, schema);
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyAgentInboxConstraints() {
        Set<String> actual = new HashSet<>();
        boolean[] dispatchForeignKey = {false};
        jdbc.query("""
                SELECT c.contype,c.convalidated,pg_get_constraintdef(c.oid) AS definition,
                       c.confrelid=to_regclass(format('%I.%I',?,'tianji_process_agent_dispatch')) AS dispatch_target
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_process_agent_result_inbox' AND c.contype IN ('p','u','c','f')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            String definition = rs.getString("definition");
            actual.add(definition);
            if ("f".equals(rs.getString("contype")) && rs.getBoolean("dispatch_target")
                    && definition.startsWith("FOREIGN KEY (tenant_id, source_id, scope_type, scope_id, occurrence_id) REFERENCES ")
                    && definition.endsWith("(tenant_id, source_id, scope_type, scope_id, occurrence_id)")) dispatchForeignKey[0] = true;
        }, schema, schema);
        Set<String> expected = new HashSet<>(Set.of("PRIMARY KEY (tenant_id, event_id)",
                "UNIQUE (tenant_id, owner_issuer, owner_subject, run_id)", "CHECK ((tenant_id > 0))",
                "CHECK ((terminal_revision > 0))", "CHECK (((scope_type)::text = 'BPMN'::text))",
                "CHECK (((jsonb_typeof(event_payload) = 'object'::text) AND (octet_length((event_payload)::text) <= 65536)))",
                "CHECK (((attempt_count >= 0) AND (attempt_count <= 1000)))",
                "CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'APPLIED'::character varying, 'IGNORED_LATE'::character varying, 'DEAD'::character varying])::text[])))",
                "CHECK (((retry_not_after >= (received_at + '00:01:00'::interval)) AND (retry_not_after <= (received_at + '720:00:00'::interval))))",
                "CHECK (((((state)::text = 'PENDING'::text) AND (decided_at IS NULL) AND (decision_code IS NULL)) OR (((state)::text = ANY ((ARRAY['APPLIED'::character varying, 'IGNORED_LATE'::character varying, 'DEAD'::character varying])::text[])) AND (decided_at IS NOT NULL) AND (decision_code IS NOT NULL))))"));
        for (String name : List.of("event_id", "producer_issuer", "owner_issuer", "event_sha256")) {
            expected.add("CHECK ((" + name + " ~ '^[0-9a-f]{64}$'::text))");
        }
        if (!actual.containsAll(expected) || !dispatchForeignKey[0]) throw invalid();
        Integer index = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname=? AND t.relname='tianji_process_agent_result_inbox' AND x.relname='tianji_agent_result_inbox_due_idx'
                  AND i.indisvalid AND i.indisready
                  AND pg_get_indexdef(i.indexrelid) LIKE '% (tenant_id, producer_issuer, producer_subject, producer_id, source_id, next_attempt_at, event_id) WHERE %'
                  AND pg_get_expr(i.indpred,i.indrelid)=?
                """, Integer.class, schema, "((state)::text = 'PENDING'::text)");
        if (index == null || index != 1) throw invalid();
    }

    private void verifyAgentDispatchColumns() {
        String table = "tianji_process_agent_dispatch";
        Map<String, Column> expected = new HashMap<>();
        for (String name : List.of("tenant_id", "agent_definition_version", "submission_timeout_ms")) {
            expected.put(table + "." + name, new Column("bigint", true, false));
        }
        for (String name : List.of("subject_issuer", "process_model_sha256", "worker_input_sha256", "seed_sha256", "agent_definition_sha256")) {
            expected.put(table + "." + name, new Column("character(64)", true, true));
        }
        for (String name : List.of("source_id", "idempotency_key", "release_key", "element_id", "topic", "schema_version",
                "agent_definition_key", "correlation_id")) {
            expected.put(table + "." + name, new Column("character varying(128)", true, true));
        }
        for (String name : List.of("scope_id", "occurrence_id", "process_definition_id")) {
            expected.put(table + "." + name, new Column("character varying(255)", true, true));
        }
        for (String name : List.of("subject_id", "business_key")) {
            expected.put(table + "." + name, new Column("character varying(256)", true, true));
        }
        for (String name : List.of("created_at", "dispatch_not_after", "next_check_at")) {
            expected.put(table + "." + name, new Column("timestamp with time zone", true, false));
        }
        expected.put(table + ".scope_type", new Column("character varying(8)", true, false));
        expected.put(table + ".state", new Column("character varying(16)", true, false));
        expected.put(table + ".instruction", new Column("text", true, false));
        expected.put(table + ".input_data", new Column("jsonb", true, false));
        expected.put(table + ".run_id", new Column("character varying(128)", false, true));
        expected.put(table + ".closed_at", new Column("timestamp with time zone", false, false));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname, format_type(a.atttypid,a.atttypmod) AS type, a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname='tianji_process_agent_dispatch' AND t.relkind='r'
                """, rs -> {
            actual.put(table + "." + rs.getString("attname"),
                    new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        }, schema);
        if (!expected.equals(actual)) throw invalid();
    }

    private void verifyAgentDispatchConstraints() {
        Set<String> actual = new HashSet<>();
        boolean[] releaseForeignKey = {false};
        jdbc.query("""
                SELECT c.contype,c.convalidated,pg_get_constraintdef(c.oid) AS definition,
                       c.confrelid=to_regclass(format('%I.%I',?,'tianji_process_release')) AS release_target
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_process_agent_dispatch' AND c.contype IN ('p','u','c','f')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            String definition = rs.getString("definition");
            actual.add(definition);
            if ("f".equals(rs.getString("contype")) && rs.getBoolean("release_target")
                    && definition.startsWith("FOREIGN KEY (tenant_id, release_key) REFERENCES ")
                    && definition.endsWith("(tenant_id, release_key)")) releaseForeignKey[0] = true;
        }, schema, schema);
        Set<String> expected = new HashSet<>(Set.of(
                "PRIMARY KEY (tenant_id, source_id, scope_type, scope_id, occurrence_id)",
                "UNIQUE (tenant_id, occurrence_id)",
                "UNIQUE (tenant_id, subject_issuer, subject_id, idempotency_key)",
                "UNIQUE (tenant_id, subject_issuer, subject_id, run_id)",
                "CHECK ((tenant_id > 0))", "CHECK (((scope_type)::text = 'BPMN'::text))",
                "CHECK ((agent_definition_version > 0))", "CHECK ((octet_length(instruction) <= 65536))",
                "CHECK (((jsonb_typeof(input_data) = 'object'::text) AND (octet_length((input_data)::text) <= 131072)))",
                "CHECK (((submission_timeout_ms >= 1000) AND (submission_timeout_ms <= 900000)))",
                "CHECK ((dispatch_not_after = (created_at + ((submission_timeout_ms)::double precision * '00:00:00.001'::interval))))",
                "CHECK (((state)::text = ANY ((ARRAY['PENDING'::character varying, 'SUBMITTED'::character varying, 'ACKNOWLEDGED'::character varying, 'CLOSING'::character varying, 'CLOSED'::character varying])::text[])))",
                "CHECK (((((state)::text = 'PENDING'::text) AND (run_id IS NULL) AND (closed_at IS NULL)) OR (((state)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'ACKNOWLEDGED'::character varying])::text[])) AND (run_id IS NOT NULL) AND (closed_at IS NULL)) OR (((state)::text = 'CLOSING'::text) AND (closed_at IS NULL)) OR (((state)::text = 'CLOSED'::text) AND (closed_at IS NOT NULL))))"));
        for (String name : List.of("subject_issuer", "process_model_sha256", "worker_input_sha256", "seed_sha256", "agent_definition_sha256")) {
            expected.add("CHECK ((" + name + " ~ '^[0-9a-f]{64}$'::text))");
        }
        if (!actual.containsAll(expected) || !releaseForeignKey[0]) throw invalid();
        Integer index = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname=? AND t.relname='tianji_process_agent_dispatch' AND x.relname='tianji_agent_dispatch_due_idx'
                  AND i.indisvalid AND i.indisready
                  AND pg_get_indexdef(i.indexrelid) LIKE '% (tenant_id, subject_issuer, subject_id, source_id, next_check_at, occurrence_id) WHERE %'
                  AND pg_get_expr(i.indpred,i.indrelid)=?
                """, Integer.class, schema, "((state)::text <> 'CLOSED'::text)");
        if (index == null || index != 1) throw invalid();
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

    private void verifyWorkerColumns() {
        Map<String, Column> expected = Map.ofEntries(
                column(WORKER, "token_sha256", "character(64)", true, true),
                column(WORKER, "tenant_id", "bigint", true, false),
                column(WORKER, "subject_issuer", "character(64)", true, true),
                column(WORKER, "subject_id", "character varying(256)", true, true),
                column(WORKER, "topic", "character varying(128)", true, true),
                column(WORKER, "schema_version", "character varying(128)", true, true),
                column(WORKER, "job_id", "character varying(255)", true, true),
                column(WORKER, "occurrence_id", "character varying(255)", true, true),
                column(WORKER, "scope_type", "character varying(8)", true, false),
                column(WORKER, "scope_id", "character varying(255)", true, true),
                column(WORKER, "release_key", "character varying(128)", true, true),
                column(WORKER, "definition_id", "character varying(255)", true, true),
                column(WORKER, "element_id", "character varying(128)", true, true),
                column(WORKER, "idempotency_key", "character varying(128)", true, true),
                column(WORKER, "model_sha256", "character(64)", true, true),
                column(WORKER, "input_sha256", "character(64)", true, true),
                column(WORKER, "native_owner", "character varying(255)", true, true),
                column(WORKER, "leased_at", "timestamp with time zone", true, false),
                column(WORKER, "lease_expires_at", "timestamp with time zone", true, false),
                column(WORKER, "state", "character varying(24)", true, false),
                column(WORKER, "result_sha256", "character(64)", false, true),
                column(WORKER, "decided_at", "timestamp with time zone", false, false),
                column(WORKER, "business_receipt_id", "character varying(255)", false, true),
                column(WORKER, "failure_kind", "character varying(24)", false, false),
                column(WORKER, "failure_code", "character varying(64)", false, true));
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT t.relname, a.attname, format_type(a.atttypid, a.atttypmod) AS type,
                       a.attnotnull, co.collname
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum > 0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname=? AND t.relname='tianji_worker_lease_receipt' AND t.relkind IN ('r', 'p')
                """, rs -> {
            actual.put(rs.getString("relname") + "." + rs.getString("attname"),
                    new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        }, schema);
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
    }

    private void verifyWorkerConstraints() {
        Map<String, String> actual = new HashMap<>();
        boolean[] releaseForeignKey = {false};
        jdbc.query("""
                SELECT c.conname, c.contype, c.convalidated, pg_get_constraintdef(c.oid) AS definition,
                       c.confrelid = to_regclass(format('%I.%I', ?, 'tianji_process_release')) AS release_target
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname=? AND t.relname='tianji_worker_lease_receipt' AND c.contype IN ('p','u','c','f')
                """, rs -> {
            if (!rs.getBoolean("convalidated")) throw invalid();
            String definition = rs.getString("definition");
            actual.put(rs.getString("conname"), definition);
            if ("f".equals(rs.getString("contype")) && rs.getBoolean("release_target")
                    && definition.startsWith("FOREIGN KEY (tenant_id, release_key) REFERENCES ")
                    && definition.endsWith("(tenant_id, release_key)")) releaseForeignKey[0] = true;
        }, schema, schema);
        Map<String, String> expected = Map.ofEntries(
                Map.entry("tianji_worker_lease_receipt_pkey", "PRIMARY KEY (token_sha256)"),
                Map.entry("tianji_worker_lease_receipt_tenant_id_job_id_native_owner_key", "UNIQUE (tenant_id, job_id, native_owner)"),
                Map.entry("tianji_worker_lease_receipt_tenant_id_check", "CHECK ((tenant_id > 0))"),
                Map.entry("tianji_worker_lease_receipt_token_sha256_check", "CHECK ((token_sha256 ~ '^[0-9a-f]{64}$'::text))"),
                Map.entry("tianji_worker_lease_receipt_subject_issuer_check", "CHECK ((subject_issuer ~ '^[0-9a-f]{64}$'::text))"),
                Map.entry("tianji_worker_lease_receipt_model_sha256_check", "CHECK ((model_sha256 ~ '^[0-9a-f]{64}$'::text))"),
                Map.entry("tianji_worker_lease_receipt_input_sha256_check", "CHECK ((input_sha256 ~ '^[0-9a-f]{64}$'::text))"),
                Map.entry("tianji_worker_lease_receipt_result_sha256_check", "CHECK ((result_sha256 ~ '^[0-9a-f]{64}$'::text))"),
                Map.entry("tianji_worker_lease_receipt_scope_type_check", "CHECK (((scope_type)::text = ANY ((ARRAY['BPMN'::character varying, 'CMMN'::character varying])::text[])))"),
                Map.entry("tianji_worker_lease_receipt_state_check", "CHECK (((state)::text = ANY ((ARRAY['LEASED'::character varying, 'EXPIRED'::character varying, 'COMPLETED'::character varying, 'RETRY_SCHEDULED'::character varying, 'BUSINESS_REJECTED'::character varying, 'NEEDS_MANUAL'::character varying])::text[])))"),
                Map.entry("tianji_worker_lease_bounds_check", "CHECK (((lease_expires_at > leased_at) AND (lease_expires_at <= (leased_at + '00:15:00'::interval))))"),
                Map.entry("tianji_worker_lease_state_fields_check", "CHECK (((((state)::text = 'LEASED'::text) AND (decided_at IS NULL) AND (result_sha256 IS NULL) AND (business_receipt_id IS NULL) AND (failure_kind IS NULL) AND (failure_code IS NULL)) OR (((state)::text = 'EXPIRED'::text) AND (decided_at IS NOT NULL) AND (result_sha256 IS NULL) AND (business_receipt_id IS NULL) AND (failure_kind IS NULL) AND (failure_code IS NULL)) OR (((state)::text = 'COMPLETED'::text) AND (decided_at IS NOT NULL) AND (result_sha256 IS NOT NULL) AND (business_receipt_id IS NOT NULL) AND (failure_kind IS NULL) AND (failure_code IS NULL)) OR (((state)::text = 'RETRY_SCHEDULED'::text) AND (decided_at IS NOT NULL) AND (result_sha256 IS NOT NULL) AND (business_receipt_id IS NULL) AND (failure_kind IS NOT NULL) AND ((failure_kind)::text = 'RETRYABLE'::text) AND (failure_code IS NOT NULL)) OR (((state)::text = 'BUSINESS_REJECTED'::text) AND (decided_at IS NOT NULL) AND (result_sha256 IS NOT NULL) AND (business_receipt_id IS NULL) AND (failure_kind IS NOT NULL) AND ((failure_kind)::text = 'BUSINESS_REJECTED'::text) AND (failure_code IS NOT NULL)) OR (((state)::text = 'NEEDS_MANUAL'::text) AND (decided_at IS NOT NULL) AND (result_sha256 IS NOT NULL) AND (business_receipt_id IS NULL) AND (failure_kind IS NOT NULL) AND ((failure_kind)::text = ANY ((ARRAY['RETRYABLE'::character varying, 'BUSINESS_REJECTED'::character varying, 'UNKNOWN'::character varying])::text[])) AND (failure_code IS NOT NULL))))"));
        for (var entry : expected.entrySet()) if (!entry.getValue().equals(actual.get(entry.getKey()))) throw invalid();
        if (!releaseForeignKey[0]) throw invalid();
    }

    private void verifyWorkerIndex() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid
                JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid
                WHERE n.nspname=? AND t.relname='tianji_worker_lease_receipt'
                  AND x.relname='tianji_worker_lease_active_job_idx' AND i.indisunique AND i.indisvalid AND i.indisready
                  AND pg_get_indexdef(i.indexrelid) LIKE '% (tenant_id, job_id) WHERE %'
                  AND pg_get_expr(i.indpred, i.indrelid)=?
                """, Integer.class, schema, "((state)::text = 'LEASED'::text)");
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
