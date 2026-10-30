package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Verifies the selected primary and the exact Memory schema contract; never applies DDL or repairs history. */
public final class MemoryStorageVerifier {
    private static final String FILE = "V10__add_memory_facts_and_index.sql";
    private static final String SERVICE_FILE = "V11__bind_memory_grant_ticket_to_principal.sql";
    private static final String BASE_VECTOR = "agent_memory_vector_v1";
    private static final List<String> OWNER = List.of("tenant_id", "owner_issuer", "owner_type", "owner_id", "scope_id");
    private static final Set<String> TABLES = Set.of("agent_memory_scope", "agent_memory_entry", "agent_memory_revision", "agent_memory_command_receipt", "agent_memory_index_manifest", "agent_memory_run_grant", BASE_VECTOR);
    private final DataSource source;
    private final int baseDimensions;
    private final String vectorVersion;
    private final MemoryIndexPublication.Binding binding;

    public MemoryStorageVerifier(DataSource source, int baseDimensions, String vectorVersion, MemoryIndexPublication.Binding binding) {
        if (source == null || baseDimensions < 1 || baseDimensions > 8192 || vectorVersion == null
                || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || binding == null) throw new IllegalArgumentException("Memory storage profile is invalid");
        this.source = source; this.baseDimensions = baseDimensions; this.vectorVersion = vectorVersion; this.binding = binding;
    }

    public void verify() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory schema verification requires its own transaction");
        new AgentStorageVerifier(source).verifyContextSnapshots();
        String raw = migration(); String sql = raw.replace("${tianjiMemoryDimensions}", Integer.toString(baseDimensions)); Map<String, Table> expected = contract(sql);
        if (!expected.keySet().equals(TABLES)) throw invalid();
        if (!binding.vectorTable().equals(BASE_VECTOR)) expected.put(binding.vectorTable(), vectorContract(binding.dimensions()));
        else if (binding.dimensions() != baseDimensions) throw invalid();
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(10);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(30);
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'", Boolean.class))) throw invalid();
                jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
                var extension = jdbc.query("SELECT e.extversion,n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace WHERE e.extname='vector'",
                        (rs, row) -> vectorVersion.equals(rs.getString(1)) && "public".equals(rs.getString(2)));
                if (extension.size() != 1 || !extension.getFirst()) throw invalid();
                var history = jdbc.query("SELECT success,checksum FROM tianji_agent.flyway_schema_history WHERE version='10' AND type='SQL'",
                        (rs, row) -> rs.getBoolean(1) && rs.getObject(2) != null && rs.getInt(2) == checksum(raw));
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                if (serviceGrantHistory(jdbc, false)) expected.put("agent_memory_run_grant", serviceGrantContract(expected.get("agent_memory_run_grant"), migration(SERVICE_FILE)));
                for (var table : expected.entrySet()) { columns(jdbc, table.getKey(), table.getValue()); checks(jdbc, table.getKey(), table.getValue()); privileges(jdbc, table.getKey()); }
                keys(jdbc); indexes(jdbc, expected);
                view(jdbc, BASE_VECTOR, baseDimensions);
                if (!binding.vectorTable().equals(BASE_VECTOR)) view(jdbc, binding.vectorTable(), binding.dimensions());
            });
        } catch (DataAccessException failure) { throw invalid(); }
    }

    static void verifyServiceGrantTable(DataSource source) {
        if (source == null || TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory SERVICE schema verification requires its own DataSource transaction");
        var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery()", Boolean.class))) throw invalid();
                jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
                serviceGrantHistory(jdbc, true);
                var base = contract(migration().replace("${tianjiMemoryDimensions}", "1")).get("agent_memory_run_grant");
                checks(jdbc, "agent_memory_run_grant", serviceGrantContract(base, migration(SERVICE_FILE)));
            });
        } catch (DataAccessException failure) { throw invalid(); }
    }

    private static boolean serviceGrantHistory(JdbcTemplate jdbc, boolean required) {
        var history = jdbc.query("SELECT success,checksum,type,script FROM tianji_agent.flyway_schema_history WHERE version='11'", (rs, row) ->
                rs.getBoolean("success") && "SQL".equals(rs.getString("type")) && SERVICE_FILE.equals(rs.getString("script"))
                        && rs.getObject("checksum") != null && rs.getInt("checksum") == checksum(migration(SERVICE_FILE)));
        if (history.isEmpty() && !required) return false;
        if (history.size() != 1 || !history.getFirst()) throw invalid(); return true;
    }

    static Table serviceGrantContract(Table base, String sql) {
        var constraint = Pattern.compile("ALTER TABLE tianji_agent\\.agent_memory_run_grant\\s+ADD CONSTRAINT memory_grant_ticket CHECK\\s*\\(").matcher(sql);
        if (!constraint.find()) throw invalid(); int open = constraint.end() - 1;
        String signature = booleanSignature(sql.substring(open + 1, closing(sql, open))); if (constraint.find()) throw invalid();
        var checks = new HashMap<>(base.checks()); checks.put("memory_grant_ticket", signature); return new Table(base.columns(), Map.copyOf(checks));
    }

    private void columns(JdbcTemplate jdbc, String table, Table expected) {
        Map<String, Column> actual = new HashMap<>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,co.collname,t.relkind,a.atthasdef,a.attgenerated
                FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                WHERE n.nspname='tianji_agent' AND t.relname=? AND a.attnum>0 AND NOT a.attisdropped
                """, rs -> {
            if (!"r".equals(rs.getString("relkind"))) throw invalid();
            String name = rs.getString("attname");
            actual.put(name, new Column(rs.getString("type").replace("public.vector", "vector"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
            if (!expected.columns().containsKey(name) && rs.getBoolean("attnotnull") && !rs.getBoolean("atthasdef") && rs.getString("attgenerated").isEmpty()) throw invalid();
        }, table);
        for (var entry : expected.columns().entrySet()) {
            var found = actual.get(entry.getKey()); var required = entry.getValue();
            if (found == null || !required.type().equals(found.type()) || required.notNull() != found.notNull() || required.cCollation() && !found.cCollation()) throw invalid();
        }
    }

    private static void checks(JdbcTemplate jdbc, String table, Table expected) {
        Map<String, String> actual = new HashMap<>();
        jdbc.query("""
                SELECT c.conname,c.convalidated,COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       pg_get_expr(c.conbin,c.conrelid,false) AS expression
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced")) throw invalid();
            actual.put(rs.getString("conname"), booleanSignature(rs.getString("expression")));
        }, table);
        for (var check : expected.checks().entrySet()) if (!check.getValue().equals(actual.get(check.getKey()))) throw invalid();
    }

    private static void keys(JdbcTemplate jdbc) {
        var actual = jdbc.query("""
                SELECT t.relname,c.contype,c.convalidated,c.condeferrable,c.condeferred,c.confdeltype,
                    COALESCE(nt.nspname,'') AS target_schema,COALESCE(tt.relname,'') AS target,
                    ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num,ord)
                          JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.ord) AS columns,
                    ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num,ord)
                          JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.ord) AS target_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class tt ON tt.oid=c.confrelid LEFT JOIN pg_namespace nt ON nt.oid=tt.relnamespace
                WHERE n.nspname='tianji_agent' AND c.contype IN ('p','u','f')
                """, (rs, row) -> {
            String type = rs.getString("contype"); if (!rs.getBoolean("convalidated")) throw invalid();
            return new Key(rs.getString("relname"), type, array(rs.getArray("columns")), type.equals("f") ? rs.getString("target_schema") : "",
                    rs.getString("target"), array(rs.getArray("target_columns")), type.equals("f") ? rs.getString("confdeltype") : "", rs.getBoolean("condeferrable"), rs.getBoolean("condeferred"));
        });
        var entity = append(OWNER, "memory_id"); var revision = append(entity, "revision");
        var expected = Set.of(primary("agent_memory_scope", OWNER), primary("agent_memory_entry", entity), primary("agent_memory_revision", revision),
                primary("agent_memory_command_receipt", append(OWNER, "actor_type", "actor_id", "operation", "command_id")),
                primary("agent_memory_index_manifest", append(entity, "content_revision", "model_ref")), primary(BASE_VECTOR, List.of("id")),
                new Key("agent_memory_index_manifest", "u", List.of("document_id"), "", "", List.of(), "", false, false),
                foreign("agent_memory_entry", OWNER, "agent_memory_scope", OWNER, "a", false),
                foreign("agent_memory_revision", entity, "agent_memory_entry", entity, "c", false),
                foreign("agent_memory_entry", append(entity, "current_revision"), "agent_memory_revision", revision, "a", true),
                foreign("agent_memory_command_receipt", append(OWNER, "result_memory_id", "result_revision"), "agent_memory_revision", revision, "a", false),
                foreign("agent_memory_index_manifest", append(entity, "content_revision"), "agent_memory_revision", revision, "a", false),
                primary("agent_memory_run_grant", List.of("tenant_id", "run_id", "source_binding_sha256")),
                new Key("agent_memory_run_grant", "u", List.of("tenant_id", "intent_id"), "", "", List.of(), "", false, false),
                foreign("agent_memory_run_grant", List.of("tenant_id", "run_id"), "agent_run", List.of("tenant_id", "run_id"), "c", false));
        if (!actual.containsAll(expected)) throw invalid();
    }

    private void indexes(JdbcTemplate jdbc, Map<String, Table> tables) {
        var indexes = jdbc.query("""
                SELECT t.relname,am.amname,i.indisunique,i.indisvalid,i.indisready,i.indislive,i.indisprimary,
                    pg_get_expr(i.indpred,i.indrelid,false) AS predicate,
                    ARRAY(SELECT COALESCE(a.attname::text,'<expression>') FROM unnest(i.indkey) WITH ORDINALITY k(num,ord)
                          LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num WHERE k.ord<=i.indnkeyatts ORDER BY k.ord) AS columns,
                    ARRAY(SELECT n.nspname::text||'.'||o.opcname::text FROM unnest(i.indclass) WITH ORDINALITY k(num,ord)
                          JOIN pg_opclass o ON o.oid=k.num JOIN pg_namespace n ON n.oid=o.opcnamespace ORDER BY k.ord) AS opclasses,
                    ARRAY(SELECT COALESCE(co.collname::text,'-') FROM unnest(i.indcollation) WITH ORDINALITY k(num,ord)
                          LEFT JOIN pg_collation co ON co.oid=k.num ORDER BY k.ord) AS collations
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_class ix ON ix.oid=i.indexrelid JOIN pg_am am ON am.oid=ix.relam WHERE n.nspname='tianji_agent'
                """, (rs, row) -> new Index(rs.getString("relname"), rs.getString("amname"), array(rs.getArray("columns")), rs.getBoolean("indisunique"),
                rs.getBoolean("indisvalid") && rs.getBoolean("indisready") && rs.getBoolean("indislive"), rs.getBoolean("indisprimary"),
                rs.getString("predicate") == null ? "" : booleanSignature(rs.getString("predicate")), array(rs.getArray("opclasses")), array(rs.getArray("collations"))));
        for (var table : tables.entrySet()) {
            var primary = indexes.stream().filter(index -> index.table().equals(table.getKey()) && index.primary()).toList();
            if (primary.size() != 1 || !valid(primary.getFirst(), table.getValue())
                    || table.getKey().startsWith("agent_memory_vector") && !primary.getFirst().columns().equals(List.of("id"))) throw invalid();
        }
        String predicate = booleanSignature("state = ANY (ARRAY['CANDIDATE', 'ACTIVE'])");
        requireIndex(indexes, tables, "agent_memory_entry", "btree", append(OWNER, "kind", "content_sha256"), true, predicate, null);
        requireIndex(indexes, tables, "agent_memory_entry", "btree", append(List.of("expires_at"), append(OWNER, "memory_id").toArray(String[]::new)), false, predicate, null);
        requireIndex(indexes, tables, "agent_memory_index_manifest", "btree", List.of("document_id"), true, "", null);
        requireIndex(indexes, tables, BASE_VECTOR, "gin", List.of("metadata"), false, "", "pg_catalog.jsonb_path_ops");
        if (!binding.vectorTable().equals(BASE_VECTOR)) requireIndex(indexes, tables, binding.vectorTable(), "gin", List.of("metadata"), false, "", "pg_catalog.jsonb_path_ops");
    }
    private static void requireIndex(List<Index> actual, Map<String, Table> tables, String table, String method, List<String> columns, boolean unique, String predicate, String opclass) {
        if (actual.stream().noneMatch(index -> index.table().equals(table) && index.method().equals(method) && index.columns().equals(columns)
                && index.unique() == unique && index.predicate().equals(predicate) && valid(index, tables.get(table))
                && (opclass == null || index.opclasses().equals(List.of(opclass))))) throw invalid();
    }
    private static boolean valid(Index index, Table table) {
        if (!index.live() || index.opclasses().size() != index.columns().size() || index.collations().size() != index.columns().size()
                || index.opclasses().stream().anyMatch(name -> !name.startsWith("pg_catalog."))) return false;
        for (int i = 0; i < index.columns().size(); i++) {
            var column = table.columns().get(index.columns().get(i));
            if (column == null || column.cCollation() && !index.collations().get(i).equals("C")) return false;
        }
        return true;
    }

    private static void privileges(JdbcTemplate jdbc, String table) {
        var required = table.startsWith("agent_memory_vector") ? List.of("SELECT", "INSERT", "UPDATE", "DELETE") : List.of("SELECT", "INSERT", "UPDATE");
        for (var permission : required) if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,?)", Boolean.class, "tianji_agent." + table, permission))) throw invalid();
    }

    private void view(JdbcTemplate jdbc, String vectorTable, int dimensions) {
        String name = vectorTable + "_active";
        String expectedSql = migration().replace("${tianjiMemoryDimensions}", Integer.toString(dimensions)).replace(BASE_VECTOR, vectorTable);
        var definition = Pattern.compile("CREATE VIEW tianji_agent\\." + Pattern.quote(name) + "\\s+WITH\\s*\\([^)]*\\)\\s+AS\\s*([\\s\\S]+?);").matcher(expectedSql);
        if (!definition.find()) throw invalid(); String expected = viewSignature(definition.group(1));
        var views = jdbc.query("""
                SELECT t.relkind,COALESCE(t.reloptions,ARRAY[]::text[]) AS options,pg_get_viewdef(t.oid,false) AS definition
                FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='tianji_agent' AND t.relname=?
                """, (rs, row) -> "v".equals(rs.getString("relkind")) && array(rs.getArray("options")).containsAll(List.of("security_barrier=true", "security_invoker=true"))
                && expected.equals(viewSignature(rs.getString("definition"))), name);
        if (views.size() != 1 || !views.getFirst()) throw invalid();
        var dependencies = jdbc.query("""
                SELECT DISTINCT rn.nspname||'.'||r.relname AS dependency FROM pg_rewrite w
                JOIN pg_class v ON v.oid=w.ev_class JOIN pg_namespace vn ON vn.oid=v.relnamespace
                JOIN pg_depend d ON d.objid=w.oid AND d.classid='pg_rewrite'::regclass AND d.refclassid='pg_class'::regclass
                JOIN pg_class r ON r.oid=d.refobjid JOIN pg_namespace rn ON rn.oid=r.relnamespace
                WHERE vn.nspname='tianji_agent' AND v.relname=? AND r.oid<>v.oid
                """, (rs, row) -> rs.getString(1), name);
        if (!Set.copyOf(dependencies).equals(Set.of("tianji_agent." + vectorTable, "tianji_agent.agent_memory_entry", "tianji_agent.agent_memory_index_manifest"))) throw invalid();
        Map<String, String> types = new HashMap<>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type FROM pg_attribute a
                JOIN pg_class t ON t.oid=a.attrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND a.attnum>0 AND NOT a.attisdropped
                """, rs -> { types.put(rs.getString(1), rs.getString(2).replace("public.vector", "vector")); }, name);
        if (!types.equals(Map.of("id", "uuid", "content", "text", "metadata", "jsonb", "embedding", "vector(" + dimensions + ')'))
                || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT has_table_privilege(current_user,?,'SELECT')", Boolean.class, "tianji_agent." + name))) throw invalid();
    }

    static String viewSignature(String sql) {
        String value = sql.trim(); if (value.endsWith(";")) value = value.substring(0, value.length() - 1);
        var where = Pattern.compile("\\bWHERE\\b", Pattern.CASE_INSENSITIVE).matcher(value);
        if (!where.find()) throw invalid();
        String head = AgentStorageVerifier.normalizedCheck(value.substring(0, where.start())).replace("tianji_agent.", "");
        return head + ":WHERE:" + booleanSignature(value.substring(where.end()).replace("tianji_agent.", ""));
    }

    private Table vectorContract(int dimensions) { return contract(migration().replace("${tianjiMemoryDimensions}", Integer.toString(dimensions))).get(BASE_VECTOR); }
    static Map<String, Table> contract(String sql) {
        Map<String, Table> tables = new LinkedHashMap<>();
        var starts = Pattern.compile("CREATE TABLE tianji_agent\\.([a-z0-9_]+)\\s*\\(").matcher(sql);
        var column = Pattern.compile("(?m)^\\s*([a-z0-9_]+)\\s+(bigint|integer|boolean|uuid|text|jsonb|timestamptz|char\\([0-9]+\\)|varchar\\([0-9]+\\)|numeric\\([0-9]+,\\s*[0-9]+\\)|public\\.vector\\([0-9]+\\))([^\\n]*)");
        var check = Pattern.compile("CONSTRAINT\\s+([a-z0-9_]+)\\s+CHECK\\s*\\(");
        while (starts.find()) {
            int open = starts.end() - 1; String body = sql.substring(open + 1, closing(sql, open));
            Map<String, Column> columns = new LinkedHashMap<>(); var fields = column.matcher(body);
            while (fields.find()) {
                String type = fields.group(2).replaceAll("\\s+", "").replace("varchar(", "character varying(").replace("char(", "character(")
                        .replace("timestamptz", "timestamp with time zone").replace("public.vector", "vector");
                columns.put(fields.group(1), new Column(type, fields.group(3).contains("NOT NULL") || fields.group(3).contains("PRIMARY KEY"), fields.group(3).contains("COLLATE \"C\"")));
            }
            Map<String, String> checks = new HashMap<>(); var constraints = check.matcher(body);
            while (constraints.find()) { int begin = constraints.end() - 1; checks.put(constraints.group(1), booleanSignature(body.substring(begin + 1, closing(body, begin)))); }
            if (columns.isEmpty() || tables.putIfAbsent(starts.group(1), new Table(Map.copyOf(columns), Map.copyOf(checks))) != null) throw invalid();
        }
        return tables;
    }

    /** Retains AND/OR/NOT grouping; removing every parenthesis would accept a weakened CHECK expression. */
    static String booleanSignature(String expression) {
        String value = expression.trim(); if (value.length() > 32_768) throw invalid();
        while (value.startsWith("(") && closing(value, 0) == value.length() - 1) value = value.substring(1, value.length() - 1).trim();
        for (String operator : List.of("OR", "AND")) {
            var parts = split(value, operator);
            if (parts.size() > 1) return operator + parts.stream().map(MemoryStorageVerifier::booleanSignature).toList();
        }
        if (keyword(value, 0, "NOT")) return "NOT[" + booleanSignature(value.substring(3)) + ']';
        return "ATOM[" + AgentStorageVerifier.normalizedCheck(operators(value)).replace("][]", "]") + ']';
    }
    private static String operators(String value) {
        var normalized = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') {
                normalized.append(c);
                if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') normalized.append(value.charAt(++i)); else quoted = !quoted;
            } else if (!quoted && keyword(value, i, "LIKE")) { normalized.append("~~"); i += 3; }
            else normalized.append(c);
        }
        return normalized.toString();
    }
    private static List<String> split(String value, String operator) {
        var parts = new ArrayList<String>(); int depth = 0, start = 0; boolean quoted = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') { if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') i++; else quoted = !quoted; }
            else if (!quoted) {
                if (c == '(' || c == '[') depth++; else if (c == ')' || c == ']') depth--;
                else if (depth == 0 && keyword(value, i, operator)) { parts.add(value.substring(start, i)); start = i + operator.length(); i = start - 1; }
            }
            if (depth < 0) throw invalid();
        }
        if (quoted || depth != 0) throw invalid(); parts.add(value.substring(start)); return parts;
    }
    private static boolean keyword(String value, int at, String word) {
        return value.regionMatches(true, at, word, 0, word.length()) && (at == 0 || !identifier(value.charAt(at - 1)))
                && (at + word.length() == value.length() || !identifier(value.charAt(at + word.length())));
    }
    private static boolean identifier(char c) { return Character.isLetterOrDigit(c) || c == '_'; }
    private static int closing(String value, int open) {
        int depth = 0; boolean quoted = false;
        for (int i = open; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\'') { if (quoted && i + 1 < value.length() && value.charAt(i + 1) == '\'') i++; else quoted = !quoted; }
            else if (!quoted) { if (c == '(') depth++; else if (c == ')' && --depth == 0) return i; }
        }
        throw invalid();
    }

    private static String migration() {
        String sql = migration(FILE); if (!sql.contains("${tianjiMemoryDimensions}")) throw invalid(); return sql;
    }
    private static String migration(String file) {
        try (var input = MemoryStorageVerifier.class.getResourceAsStream("/db/tianji-agent/" + file)) {
            if (input == null) throw invalid(); String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return text;
        } catch (IOException failure) { throw invalid(); }
    }
    static int checksum(String sql) {
        var crc = new CRC32();
        try (var reader = new BufferedReader(new StringReader(sql))) {
            String line; boolean first = true;
            while ((line = reader.readLine()) != null) { if (first && line.startsWith("\uFEFF")) line = line.substring(1); first = false; crc.update(line.getBytes(StandardCharsets.UTF_8)); }
            return (int) crc.getValue();
        } catch (IOException impossible) { throw invalid(); }
    }
    private static List<String> array(java.sql.Array value) throws SQLException { return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray())); }
    private static List<String> append(List<String> values, String... tail) { var result = new ArrayList<>(values); result.addAll(List.of(tail)); return List.copyOf(result); }
    private static Key primary(String table, List<String> fields) { return new Key(table, "p", fields, "", "", List.of(), "", false, false); }
    private static Key foreign(String table, List<String> fields, String target, List<String> targetFields, String deletion, boolean deferred) { return new Key(table, "f", fields, "tianji_agent", target, targetFields, deletion, deferred, deferred); }
    record Column(String type, boolean notNull, boolean cCollation) { }
    record Table(Map<String, Column> columns, Map<String, String> checks) { }
    private record Key(String table, String type, List<String> columns, String targetSchema, String target, List<String> targetColumns, String deletion, boolean deferrable, boolean deferred) { }
    private record Index(String table, String method, List<String> columns, boolean unique, boolean live, boolean primary, String predicate, List<String> opclasses, List<String> collations) { }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory storage verification failed"); }
}
