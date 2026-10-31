package top.egon.cola.platform.tianji.process.server.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Read-only task receipt contract verification. Application startup never creates or repairs a schema. */
public final class HumanTaskStorageVerifier {
    public static final String FILE = "V7__add_human_task_receipts.sql";
    private static final String TABLE = "tianji_human_task_receipt";
    private final DataSource source;
    private final String schema;
    private final String sql;
    private final JdbcTemplate jdbc;

    public HumanTaskStorageVerifier(DataSource source, String schema) { this(source, schema, migration()); }
    HumanTaskStorageVerifier(DataSource source, String schema, String sql) {
        this.source = Objects.requireNonNull(source, "source"); this.sql = Objects.requireNonNull(sql, "sql");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid(); this.schema = schema; jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
    }

    public void verify() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Task schema verification requires its own transaction");
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setReadOnly(true); transaction.setTimeout(15);
        transaction.executeWithoutResult(status -> {
            if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw invalid();
            new ProcessApiSchemaVerifier(source, schema).verify();
            var history = jdbc.query("SELECT success,checksum,script FROM \"" + schema + "\".tianji_process_schema_history WHERE version='7' AND type='SQL'",
                    (rs, number) -> rs.getBoolean("success") && rs.getObject("checksum") != null && rs.getInt("checksum") == checksum(sql) && FILE.equals(rs.getString("script")));
            if (history.size() != 1 || !history.getFirst()) throw invalid();
            var contract = contract(sql); verifyColumns(contract); verifyChecks(contract); verifyKeys(); verifyIndexes(); verifyRole();
        });
    }

    private void verifyColumns(Contract contract) {
        var actual = new HashMap<String, Column>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,a.attidentity,a.attgenerated,co.collname,pg_get_expr(d.adbin,d.adrelid) AS default_value
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum>0 AND NOT a.attisdropped
                LEFT JOIN pg_collation co ON co.oid=a.attcollation LEFT JOIN pg_attrdef d ON d.adrelid=t.oid AND d.adnum=a.attnum
                WHERE n.nspname=? AND t.relname=? AND t.relkind='r'
                """, rs -> {
            if (!rs.getString("attidentity").isEmpty() || !rs.getString("attgenerated").isEmpty()) throw invalid();
            actual.put(rs.getString("attname"), new Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname")),
                    rs.getString("default_value") == null ? null : atom(rs.getString("default_value"))));
        }, schema, TABLE);
        if (!contract.columns().equals(actual)) throw invalid();
    }
    private void verifyChecks(Contract contract) {
        var actual = new HashMap<String, String>();
        jdbc.query("SELECT c.conname,c.convalidated,pg_get_expr(c.conbin,c.conrelid) AS expression FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? AND t.relname=? AND c.contype='c'",
                rs -> { if (!rs.getBoolean("convalidated")) throw invalid(); actual.put(rs.getString("conname"), signature(rs.getString("expression"))); }, schema, TABLE);
        if (!contract.checks().equals(actual)) throw invalid();
    }
    private void verifyKeys() {
        var actual = new HashSet<Key>();
        jdbc.query("""
                SELECT c.contype,c.convalidated,c.condeferrable,c.condeferred,c.confupdtype,CASE WHEN c.contype='f' THEN c.confdeltype::text ELSE '' END AS deletion,tn.nspname AS target_schema,tt.relname AS target,
                  ARRAY(SELECT a.attname FROM unnest(c.conkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.ord) AS fields,
                  ARRAY(SELECT a.attname FROM unnest(c.confkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.ord) AS target_fields
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class tt ON tt.oid=c.confrelid LEFT JOIN pg_namespace tn ON tn.oid=tt.relnamespace
                WHERE n.nspname=? AND t.relname=? AND c.contype IN ('p','u','f')
                """, rs -> {
            if (!rs.getBoolean("convalidated") || rs.getBoolean("condeferrable") || rs.getBoolean("condeferred") || "f".equals(rs.getString("contype")) && !"a".equals(rs.getString("confupdtype"))) throw invalid();
            actual.add(new Key(rs.getString("contype"), array(rs.getArray("fields")), rs.getString("target_schema"), rs.getString("target"), array(rs.getArray("target_fields")), rs.getString("deletion")));
        }, schema, TABLE);
        var own = List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "command_type", "command_id");
        var expected = Set.of(new Key("p", own, null, null, List.of(), ""), foreign(own, "tianji_process_command_receipt", own),
                foreign(List.of("tenant_id", "requester_type", "requester_issuer", "requester_id", "start_command_type", "start_command_id"), "tianji_process_command_receipt", own),
                foreign(List.of("tenant_id", "release_key", "model_type", "definition_key"), "tianji_process_release_definition", List.of("tenant_id", "release_key", "model_type", "definition_key")));
        if (!actual.equals(expected)) throw invalid();
    }
    private Key foreign(List<String> fields, String target, List<String> targetFields) { return new Key("f", fields, schema, target, targetFields, "a"); }

    private void verifyIndexes() {
        var actual = new HashMap<String, Index>();
        jdbc.query("""
                SELECT x.relname,i.indisunique,i.indisprimary,i.indisvalid,i.indisready,i.indislive,am.amname,i.indnkeyatts,i.indnatts,pg_get_expr(i.indpred,i.indrelid) AS predicate,
                  NOT EXISTS (SELECT 1 FROM unnest(i.indclass::oid[]) k(oid) JOIN pg_opclass o ON o.oid=k.oid WHERE NOT o.opcdefault) AS default_classes,
                  NOT EXISTS (SELECT 1 FROM unnest(i.indcollation::oid[]) WITH ORDINALITY co(oid,ord) JOIN unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,ord) USING(ord) JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num WHERE co.oid<>a.attcollation) AS column_collations,
                  ARRAY(SELECT a.attname FROM unnest(i.indkey::smallint[]) WITH ORDINALITY k(num,ord) LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num ORDER BY k.ord) AS fields
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_class x ON x.oid=i.indexrelid JOIN pg_am am ON am.oid=x.relam
                WHERE n.nspname=? AND t.relname=?
                """, rs -> {
            if (!rs.getBoolean("indisvalid") || !rs.getBoolean("indisready") || !rs.getBoolean("indislive") || !rs.getBoolean("default_classes") || !rs.getBoolean("column_collations")
                    || !"btree".equals(rs.getString("amname")) || rs.getInt("indnkeyatts") != rs.getInt("indnatts")) throw invalid();
            actual.put(rs.getString("relname"), new Index(array(rs.getArray("fields")), rs.getBoolean("indisunique"), rs.getBoolean("indisprimary"), rs.getString("predicate") == null ? null : signature(rs.getString("predicate"))));
        }, schema, TABLE);
        var expected = Map.of(TABLE + "_pkey", new Index(List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "command_type", "command_id"), true, true, null),
                "tianji_human_task_approval_idx", new Index(List.of("tenant_id", "subject_issuer", "approval_receipt_id"), true, false, signature("approval_receipt_id IS NOT NULL")),
                "tianji_human_task_completed_idx", new Index(List.of("tenant_id", "subject_issuer", "task_id"), true, false, signature("command_type='TASK_COMPLETE'")));
        if (!expected.equals(actual)) throw invalid();
    }
    private void verifyRole() {
        Boolean valid = jdbc.queryForObject("""
                SELECT NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                  AND has_table_privilege(c.oid,'SELECT') AND has_table_privilege(c.oid,'INSERT')
                  AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid AND ((NOT tr.tgisinternal AND tr.tgenabled<>'D') OR (tr.tgisinternal AND tr.tgenabled<>'O')))
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname=? AND c.relname=? AND c.relkind='r'
                """, Boolean.class, schema, TABLE);
        if (!Boolean.TRUE.equals(valid)) throw invalid();
    }

    static Contract contract(String sql) {
        var columns = new HashMap<String, Column>(); var fields = Pattern.compile("(?m)^\\s*([a-z0-9_]+)\\s+(bigint|boolean|timestamptz|char\\([0-9]+\\)|varchar\\([0-9]+\\))([^\\n]*)").matcher(sql);
        while (fields.find()) {
            String tail = fields.group(3); String value = fields.group(2).replace("varchar(", "character varying(").replace("char(", "character(").replace("timestamptz", "timestamp with time zone");
            var defaults = Pattern.compile("DEFAULT\\s+('[^']*')").matcher(tail);
            columns.put(fields.group(1), new Column(value, tail.contains("NOT NULL"), tail.contains("COLLATE \"C\""), defaults.find() ? defaults.group(1) : null));
        }
        var checks = new HashMap<String, String>(); var constraints = Pattern.compile("CONSTRAINT\\s+([a-z0-9_]+)\\s+CHECK\\s*\\(").matcher(sql);
        while (constraints.find()) { int open = constraints.end() - 1; checks.put(constraints.group(1), signature(sql.substring(open + 1, closing(sql, open)))); }
        if (columns.size() < 30 || checks.size() < 15) throw invalid(); return new Contract(Map.copyOf(columns), Map.copyOf(checks));
    }
    /** AND/OR grouping is retained; whitespace/implicit text casts and redundant atom parentheses are not authority. */
    static String signature(String expression) {
        String value = expression.trim(); if (value.length() > 32_768) throw invalid(); while (value.startsWith("(") && closing(value, 0) == value.length() - 1) value = value.substring(1, value.length() - 1).trim();
        for (var operator : List.of("OR", "AND")) { var parts = split(value, operator); if (parts.size() > 1) return operator + parts.stream().map(HumanTaskStorageVerifier::signature).toList(); }
        if (keyword(value, 0, "NOT")) return "NOT[" + signature(value.substring(3)) + ']'; return "ATOM[" + atom(value).replace("][]", "]") + ']';
    }
    private static String atom(String value) {
        var result = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i);
            if (c == '\'') { result.append(c); if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') result.append(value.charAt(++i)); else quoted = !quoted; }
            else if (quoted) result.append(c); else if (value.startsWith("::character varying", i)) i += 18; else if (value.startsWith("::text", i)) i += 5; else if (!Character.isWhitespace(c) && c != '(' && c != ')') result.append(c);
        }
        if (quoted) throw invalid(); return result.toString();
    }
    private static List<String> split(String value, String word) {
        var parts = new ArrayList<String>(); int depth = 0, start = 0; boolean quote = false;
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i); if (c == '\'') { if (quote && i + 1 < value.length() && value.charAt(i + 1) == '\'') i++; else quote = !quote; }
            else if (!quote) { if (c == '(' || c == '[') depth++; else if (c == ')' || c == ']') depth--; else if (depth == 0 && keyword(value, i, word)) { parts.add(value.substring(start, i)); start = i + word.length(); i = start - 1; } }
            if (depth < 0) throw invalid(); }
        if (quote || depth != 0) throw invalid(); parts.add(value.substring(start)); return parts;
    }
    private static boolean keyword(String value, int at, String word) { return value.regionMatches(true, at, word, 0, word.length()) && (at == 0 || !id(value.charAt(at - 1))) && (at + word.length() == value.length() || !id(value.charAt(at + word.length()))); }
    private static boolean id(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
    private static int closing(String value, int open) { int depth = 0; boolean quote = false; for (int i = open; i < value.length(); i++) { char c = value.charAt(i);
            if (c == '\'') { if (quote && i + 1 < value.length() && value.charAt(i + 1) == '\'') i++; else quote = !quote; } else if (!quote) { if (c == '(') depth++; else if (c == ')' && --depth == 0) return i; } } throw invalid(); }
    static int checksum(String sql) { var crc = new CRC32(); try (var reader = new BufferedReader(new StringReader(sql))) { String line; boolean first = true; while ((line = reader.readLine()) != null) { if (first && line.startsWith("\uFEFF")) line = line.substring(1); first = false; crc.update(line.getBytes(StandardCharsets.UTF_8)); } return (int) crc.getValue(); } catch (java.io.IOException failure) { throw invalid(); } }
    private static List<String> array(java.sql.Array array) throws java.sql.SQLException { return array == null ? List.of() : List.copyOf(Arrays.asList((String[]) array.getArray())); }
    private static String migration() { try (var input = HumanTaskStorageVerifier.class.getResourceAsStream("/db/tianji-process/" + FILE)) { if (input == null) throw invalid(); return new String(input.readAllBytes(), StandardCharsets.UTF_8); } catch (java.io.IOException failure) { throw invalid(); } }
    record Column(String type, boolean required, boolean cCollation, String defaultValue) { }
    record Contract(Map<String, Column> columns, Map<String, String> checks) { }
    private record Key(String type, List<String> fields, String targetSchema, String target, List<String> targetFields, String deletion) { }
    private record Index(List<String> fields, boolean unique, boolean primary, String predicate) { }
    private static IllegalStateException invalid() { return new IllegalStateException("Human task receipt schema verification failed"); }
}
