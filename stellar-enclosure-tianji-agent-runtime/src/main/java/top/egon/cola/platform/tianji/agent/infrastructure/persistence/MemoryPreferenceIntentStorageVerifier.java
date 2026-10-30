package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only verification of the independent preference intent table; never provisions, repairs or updates history. */
public final class MemoryPreferenceIntentStorageVerifier {
    static final String FILE = "V12__add_memory_preference_intent.sql";
    static final String TABLE = "agent_memory_preference_intent";
    private static final List<String> NATURAL = List.of("tenant_id", "actor_issuer", "actor_type", "actor_id", "run_id", "scope_id");
    private static final Set<String> DEFAULTS = Set.of("lease_epoch", "next_index", "attempt_count");
    private final DataSource source;

    public MemoryPreferenceIntentStorageVerifier(DataSource source) { this.source = Objects.requireNonNull(source, "source"); }

    public void verify() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Preference intent verification requires its own transaction");
        String sql = migration(); var expected = contract(sql); var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(15);
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw invalid();
                jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
                var history = jdbc.query("SELECT success,checksum,type,script FROM tianji_agent.flyway_schema_history WHERE version='12'", (rs, row) ->
                        rs.getBoolean("success") && "SQL".equals(rs.getString("type")) && FILE.equals(rs.getString("script"))
                                && rs.getObject("checksum") != null && rs.getInt("checksum") == MemoryStorageVerifier.checksum(sql));
                if (history.size() != 1 || !history.getFirst()) throw invalid();
                columns(jdbc, expected); checks(jdbc, expected); keys(jdbc); indexes(jdbc); privileges(jdbc);
            });
        } catch (DataAccessException failed) { throw invalid(); }
    }

    static MemoryStorageVerifier.Table contract(String sql) {
        var tables = MemoryStorageVerifier.contract(sql);
        if (!tables.keySet().equals(Set.of(TABLE))) throw invalid();
        return tables.get(TABLE);
    }

    private static void columns(JdbcTemplate jdbc, MemoryStorageVerifier.Table expected) {
        var actual = new HashMap<String, MemoryStorageVerifier.Column>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,co.collname,a.attgenerated,a.attidentity,
                       a.atthasdef,pg_get_expr(d.adbin,d.adrelid,false) AS default_value,t.relkind,t.relpersistence,t.relispartition
                FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE n.nspname='tianji_agent' AND t.relname=? AND a.attnum>0 AND NOT a.attisdropped
                """, rs -> {
            String name = rs.getString("attname");
            if (!"r".equals(rs.getString("relkind")) || !"p".equals(rs.getString("relpersistence")) || rs.getBoolean("relispartition")
                    || !rs.getString("attgenerated").isEmpty() || !rs.getString("attidentity").isEmpty() || rs.getBoolean("atthasdef") != DEFAULTS.contains(name)
                    || DEFAULTS.contains(name) && !"0".equals(rs.getString("default_value"))) throw invalid();
            actual.put(name, new MemoryStorageVerifier.Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname"))));
        }, TABLE);
        if (!expected.columns().equals(actual)) throw invalid();
    }

    private static void checks(JdbcTemplate jdbc, MemoryStorageVerifier.Table expected) {
        var actual = new HashMap<String, String>();
        jdbc.query("""
                SELECT c.conname,c.convalidated,COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       pg_get_expr(c.conbin,c.conrelid,false) AS expression
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced")) throw invalid();
            actual.put(rs.getString("conname"), MemoryStorageVerifier.booleanSignature(rs.getString("expression")));
        }, TABLE);
        if (!expected.checks().equals(actual)) throw invalid();
    }

    private static void keys(JdbcTemplate jdbc) {
        var keys = jdbc.query("""
                SELECT c.contype,c.convalidated,COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       c.condeferrable,c.condeferred,c.confdeltype,COALESCE(tn.nspname,'') AS target_schema,
                       COALESCE(tt.relname,'') AS target,
                       ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.ord) AS columns,
                       ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.ord) AS target_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class tt ON tt.oid=c.confrelid LEFT JOIN pg_namespace tn ON tn.oid=tt.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype IN ('p','u','f')
                """, (rs, row) -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced") || rs.getBoolean("condeferrable") || rs.getBoolean("condeferred")) throw invalid();
            String type = rs.getString("contype");
            return new Key(type, array(rs.getArray("columns")), type.equals("f") ? rs.getString("target_schema") : "", rs.getString("target"), array(rs.getArray("target_columns")), type.equals("f") ? rs.getString("confdeltype") : "");
        }, TABLE);
        var expected = Set.of(new Key("p", List.of("intent_id"), "", "", List.of(), ""), new Key("u", NATURAL, "", "", List.of(), ""),
                new Key("f", List.of("tenant_id", "run_id"), "tianji_agent", "agent_run", List.of("tenant_id", "run_id"), "a"));
        if (keys.size() != expected.size() || !Set.copyOf(keys).equals(expected)) throw invalid();
    }

    private static void indexes(JdbcTemplate jdbc) {
        var indexes = jdbc.query("""
                SELECT am.amname,i.indisunique,i.indisprimary,i.indisvalid,i.indisready,i.indislive,i.indnatts=i.indnkeyatts AS no_include,
                       pg_get_expr(i.indpred,i.indrelid,false) AS predicate,
                       ARRAY(SELECT COALESCE(a.attname::text,'<expression>') FROM unnest(i.indkey) WITH ORDINALITY k(num,ord)
                             LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num ORDER BY k.ord) AS columns,
                       NOT EXISTS (SELECT 1 FROM unnest(i.indkey) WITH ORDINALITY k(num,ord) LEFT JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=k.num
                                   WHERE a.attnum IS NULL OR i.indcollation[(k.ord-1)::int]<>a.attcollation) AS matching_collations,
                       ARRAY(SELECT n.nspname::text||'.'||o.opcname::text FROM unnest(i.indclass) WITH ORDINALITY k(num,ord)
                             JOIN pg_opclass o ON o.oid=k.num JOIN pg_namespace n ON n.oid=o.opcnamespace ORDER BY k.ord) AS opclasses
                FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                JOIN pg_class ix ON ix.oid=i.indexrelid JOIN pg_am am ON am.oid=ix.relam WHERE n.nspname='tianji_agent' AND t.relname=?
                """, (rs, row) -> {
            if (!"btree".equals(rs.getString("amname")) || !rs.getBoolean("indisvalid") || !rs.getBoolean("indisready") || !rs.getBoolean("indislive")
                    || !rs.getBoolean("no_include") || !rs.getBoolean("matching_collations")) throw invalid();
            return new Index(array(rs.getArray("columns")), rs.getBoolean("indisunique"), rs.getBoolean("indisprimary"),
                    rs.getString("predicate") == null ? "" : MemoryStorageVerifier.booleanSignature(rs.getString("predicate")), array(rs.getArray("opclasses")));
        }, TABLE);
        String ready = MemoryStorageVerifier.booleanSignature("state = ANY (ARRAY['READY', 'CLAIMED'])");
        String pending = MemoryStorageVerifier.booleanSignature("state = ANY (ARRAY['RESERVED', 'READY', 'CLAIMED'])");
        var expected = Set.of(new Index(List.of("intent_id"), true, true, "", List.of("pg_catalog.uuid_ops")),
                new Index(NATURAL, true, false, "", List.of("pg_catalog.int8_ops", "pg_catalog.bpchar_ops", "pg_catalog.text_ops", "pg_catalog.text_ops", "pg_catalog.text_ops", "pg_catalog.text_ops")),
                new Index(List.of("tenant_id", "actor_issuer", "state", "next_attempt_at", "intent_id"), false, false, ready, List.of("pg_catalog.int8_ops", "pg_catalog.bpchar_ops", "pg_catalog.text_ops", "pg_catalog.timestamptz_ops", "pg_catalog.uuid_ops")),
                new Index(List.of("tenant_id", "actor_issuer", "deadline_at", "intent_id"), false, false, pending, List.of("pg_catalog.int8_ops", "pg_catalog.bpchar_ops", "pg_catalog.timestamptz_ops", "pg_catalog.uuid_ops")),
                new Index(List.of("tenant_id", "actor_issuer", "grant_expires_at", "intent_id"), false, false, ready, List.of("pg_catalog.int8_ops", "pg_catalog.bpchar_ops", "pg_catalog.timestamptz_ops", "pg_catalog.uuid_ops")));
        if (indexes.size() != expected.size() || !Set.copyOf(indexes).equals(expected)) throw invalid();
    }

    private static void privileges(JdbcTemplate jdbc) {
        var safe = jdbc.query("""
                SELECT NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                    AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid AND ((NOT tr.tgisinternal AND tr.tgenabled<>'D') OR (tr.tgisinternal AND tr.tgenabled<>'O')))
                    AND has_table_privilege(current_user,c.oid,'SELECT') AND has_table_privilege(current_user,c.oid,'INSERT')
                    AND has_table_privilege(current_user,c.oid,'UPDATE') AND has_table_privilege(current_user,c.oid,'DELETE')
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='tianji_agent' AND c.relname=?
                """, (rs, row) -> rs.getBoolean(1), TABLE);
        if (safe.size() != 1 || !safe.getFirst()) throw invalid();
    }

    static String migration() {
        try (var input = MemoryPreferenceIntentStorageVerifier.class.getResourceAsStream("/db/tianji-agent/" + FILE)) {
            if (input == null) throw invalid(); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) { throw invalid(); }
    }
    private static List<String> array(java.sql.Array value) throws SQLException { return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray())); }
    private record Key(String type, List<String> columns, String targetSchema, String target, List<String> targetColumns, String deletion) { }
    private record Index(List<String> columns, boolean unique, boolean primary, String predicate, List<String> opclasses) { }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory preference intent storage is unavailable or changed"); }
}
