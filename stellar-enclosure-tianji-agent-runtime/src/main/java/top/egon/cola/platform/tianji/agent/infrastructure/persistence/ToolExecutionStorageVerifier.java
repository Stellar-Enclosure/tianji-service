package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Read-only readiness of the independent tool journal; never provisions or repairs storage. */
public final class ToolExecutionStorageVerifier {
    static final String FILE = "V13__add_tool_execution_journal.sql";
    private static final String CAPABILITY = "agent_tool_capability", INTENT = "agent_tool_action_intent", INVOCATION = "agent_tool_invocation";
    private static final Set<String> TABLES = Set.of(CAPABILITY, INTENT, INVOCATION);
    private static final List<String> MIGRATIONS = List.of("V1__create_agent_ledger.sql", "V2__add_run_governance.sql", "V3__add_adk_session_store.sql",
            "V4__add_adk_session_codec_versions.sql", "V5__bind_agent_ownership_to_issuer.sql", "V6__add_execution_grant_evidence.sql",
            "V7__add_process_dispatch_fence.sql", "V8__add_service_execution_grant.sql", "V9__add_context_snapshots.sql",
            "V10__add_memory_facts_and_index.sql", "V11__bind_memory_grant_ticket_to_principal.sql", "V12__add_memory_preference_intent.sql", FILE);
    private final DataSource source;

    public ToolExecutionStorageVerifier(DataSource source) { this.source = Objects.requireNonNull(source, "source"); }

    public void verify() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool journal verification requires its own transaction");
        var expected = contract(migration()); var jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(15);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ); transaction.setReadOnly(false);
        try {
            transaction.executeWithoutResult(status -> {
                if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw invalid();
                jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
                history(jdbc);
                for (String table : TABLES) {
                    columns(jdbc, table, expected.get(table)); checks(jdbc, table, expected.get(table));
                    keys(jdbc, table); indexes(jdbc, table, expected.get(table)); privileges(jdbc, table);
                }
            });
        } catch (DataAccessException | TransactionException failed) { throw invalid(); }
    }

    static Map<String, MemoryStorageVerifier.Table> contract(String sql) {
        var tables = MemoryStorageVerifier.contract(sql);
        if (!tables.keySet().equals(TABLES) || tables.get(CAPABILITY).columns().size() != 27 || tables.get(CAPABILITY).checks().size() != 5
                || tables.get(INTENT).columns().size() != 43 || tables.get(INTENT).checks().size() != 11
                || tables.get(INVOCATION).columns().size() != 34 || tables.get(INVOCATION).checks().size() != 10) throw invalid();
        return Map.copyOf(tables);
    }

    private static void history(JdbcTemplate jdbc) {
        var expected = new HashMap<String, Integer>(); var scripts = new HashMap<String, String>();
        for (int index = 0; index < MIGRATIONS.size(); index++) {
            String version = Integer.toString(index + 1), file = MIGRATIONS.get(index);
            expected.put(version, MemoryStorageVerifier.checksum(migration(file))); scripts.put(version, file);
        }
        var actual = new HashMap<String, Integer>();
        jdbc.query("SELECT version,type,script,checksum,success FROM tianji_agent.flyway_schema_history", rs -> {
            if (!rs.getBoolean("success")) throw invalid();
            String version = rs.getString("version");
            if (version != null && expected.containsKey(version)) {
                if (!"SQL".equals(rs.getString("type")) || !scripts.get(version).equals(rs.getString("script")) || rs.getObject("checksum") == null
                        || actual.put(version, rs.getInt("checksum")) != null) throw invalid();
            }
        });
        if (!expected.equals(actual)) throw invalid();
    }

    private static void columns(JdbcTemplate jdbc, String table, MemoryStorageVerifier.Table expected) {
        var actual = new HashMap<String, MemoryStorageVerifier.Column>();
        jdbc.query("""
                SELECT a.attname,format_type(a.atttypid,a.atttypmod) AS type,a.attnotnull,co.collname,a.attgenerated,a.attidentity,
                       a.atthasdef,pg_get_expr(d.adbin,d.adrelid,false) AS default_value,t.relkind,t.relpersistence,t.relispartition,
                       NOT EXISTS (SELECT 1 FROM pg_inherits inh WHERE inh.inhrelid=t.oid OR inh.inhparent=t.oid) AS no_inheritance
                FROM pg_attribute a JOIN pg_class t ON t.oid=a.attrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_collation co ON co.oid=a.attcollation
                LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                WHERE n.nspname='tianji_agent' AND t.relname=? AND a.attnum>0 AND NOT a.attisdropped
                """, rs -> {
            String name = rs.getString("attname"); boolean defaulted = !CAPABILITY.equals(table) && "dispatch_epoch".equals(name);
            if (!"r".equals(rs.getString("relkind")) || !"p".equals(rs.getString("relpersistence")) || rs.getBoolean("relispartition")
                    || !rs.getBoolean("no_inheritance") || !rs.getString("attgenerated").isEmpty() || !rs.getString("attidentity").isEmpty()
                    || rs.getBoolean("atthasdef") != defaulted || defaulted && !"0".equals(rs.getString("default_value"))) throw invalid();
            if (actual.put(name, new MemoryStorageVerifier.Column(rs.getString("type"), rs.getBoolean("attnotnull"), "C".equals(rs.getString("collname")))) != null) throw invalid();
        }, table);
        if (!expected.columns().equals(actual)) throw invalid();
    }

    private static void checks(JdbcTemplate jdbc, String table, MemoryStorageVerifier.Table expected) {
        var actual = new HashMap<String, String>();
        jdbc.query("""
                SELECT c.conname,c.convalidated,COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       pg_get_expr(c.conbin,c.conrelid,false) AS expression
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype='c'
                """, rs -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced")
                    || actual.put(rs.getString("conname"), MemoryStorageVerifier.booleanSignature(rs.getString("expression"))) != null) throw invalid();
        }, table);
        if (!expected.checks().equals(actual)) throw invalid();
    }

    private static void keys(JdbcTemplate jdbc, String table) {
        var actual = jdbc.query("""
                SELECT c.contype,c.convalidated,COALESCE((to_jsonb(c)->>'conenforced')::boolean,true) AS enforced,
                       c.condeferrable,c.condeferred,c.confdeltype,c.confupdtype,c.confmatchtype,COALESCE(tn.nspname,'') AS target_schema,
                       COALESCE(tt.relname,'') AS target,
                       ARRAY(SELECT a.attname::text FROM unnest(c.conkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=k.num ORDER BY k.ord) AS columns,
                       ARRAY(SELECT a.attname::text FROM unnest(c.confkey) WITH ORDINALITY k(num,ord) JOIN pg_attribute a ON a.attrelid=c.confrelid AND a.attnum=k.num ORDER BY k.ord) AS target_columns
                FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
                LEFT JOIN pg_class tt ON tt.oid=c.confrelid LEFT JOIN pg_namespace tn ON tn.oid=tt.relnamespace
                WHERE n.nspname='tianji_agent' AND t.relname=? AND c.contype IN ('p','u','f')
                """, (rs, row) -> {
            if (!rs.getBoolean("convalidated") || !rs.getBoolean("enforced") || rs.getBoolean("condeferrable") || rs.getBoolean("condeferred")) throw invalid();
            String type = rs.getString("contype"); boolean foreign = "f".equals(type);
            return new Key(type, array(rs.getArray("columns")), foreign ? rs.getString("target_schema") : "", foreign ? rs.getString("target") : "",
                    foreign ? array(rs.getArray("target_columns")) : List.of(), foreign ? rs.getString("confdeltype") : "",
                    foreign ? rs.getString("confupdtype") : "", foreign ? rs.getString("confmatchtype") : "");
        }, table);
        var expected = expectedKeys(table);
        if (actual.size() != expected.size() || !Set.copyOf(actual).equals(expected)) throw invalid();
    }

    private static Set<Key> expectedKeys(String table) {
        return switch (table) {
            case CAPABILITY -> Set.of(key("p", "tenant_id,run_id,tool_key,tool_version"), key("u", "tenant_id,capability_id"),
                    key("u", "tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256"), foreign("tenant_id,run_id", "agent_run", "tenant_id,run_id"));
            case INTENT -> Set.of(key("p", "tenant_id,intent_id"), key("u", "tenant_id,logical_identity_sha256"),
                    key("u", "tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk"),
                    foreign("tenant_id,origin_run_id", "agent_run", "tenant_id,run_id"), foreign("tenant_id,dispatch_run_id", "agent_run", "tenant_id,run_id"),
                    foreign("tenant_id,dispatch_run_id,dispatch_invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id", INVOCATION,
                            "tenant_id,run_id,invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id"));
            case INVOCATION -> Set.of(key("p", "tenant_id,run_id,invocation_id"), key("u", "tenant_id,run_id,reservation_id"),
                    key("u", "tenant_id,run_id,invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id"),
                    foreign("tenant_id,run_id", "agent_run", "tenant_id,run_id"), foreign("tenant_id,run_id,reservation_id", "agent_budget_reservation", "tenant_id,run_id,reservation_id"),
                    foreign("tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,binding_sha256", CAPABILITY,
                            "tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256"),
                    foreign("tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk", INTENT,
                            "tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk"));
            default -> throw invalid();
        };
    }

    private static void indexes(JdbcTemplate jdbc, String table, MemoryStorageVerifier.Table expectedTable) {
        var actual = jdbc.query("""
                SELECT am.amname,i.indisunique,i.indisprimary,i.indisvalid,i.indisready,i.indislive,i.indimmediate,
                       i.indnatts=i.indnkeyatts AS no_include,NOT COALESCE((to_jsonb(i)->>'indnullsnotdistinct')::boolean,false) AS nulls_distinct,
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
                    || !rs.getBoolean("indimmediate") || !rs.getBoolean("no_include") || !rs.getBoolean("nulls_distinct") || !rs.getBoolean("matching_collations")) throw invalid();
            return new Index(array(rs.getArray("columns")), rs.getBoolean("indisunique"), rs.getBoolean("indisprimary"),
                    rs.getString("predicate") == null ? "" : MemoryStorageVerifier.booleanSignature(rs.getString("predicate")), array(rs.getArray("opclasses")));
        }, table);
        var expected = new HashSet<Index>();
        for (var key : expectedKeys(table)) if (!"f".equals(key.type())) expected.add(index(expectedTable, key.columns(), true, "p".equals(key.type()), ""));
        if (INTENT.equals(table)) {
            expected.add(index(expectedTable, columns("tenant_id,approval_source_namespace_sha256,approval_receipt_id"), true, false, "approval_receipt_id IS NOT NULL"));
            expected.add(index(expectedTable, columns("tenant_id,owner_issuer,owner_type,owner_id,created_at,intent_id"), false, false, ""));
            expected.add(index(expectedTable, columns("tenant_id,state,updated_at,intent_id"), false, false, "state = ANY (ARRAY['DISPATCHING','UNKNOWN'])"));
        } else if (INVOCATION.equals(table)) {
            expected.add(index(expectedTable, columns("tenant_id,intent_id,created_at,invocation_id"), false, false, "intent_id IS NOT NULL"));
            expected.add(index(expectedTable, columns("tenant_id,state,updated_at,run_id,invocation_id"), false, false, "state = ANY (ARRAY['DISPATCHING','UNKNOWN'])"));
        }
        if (actual.size() != expected.size() || !Set.copyOf(actual).equals(expected)) throw invalid();
    }

    private static Index index(MemoryStorageVerifier.Table table, List<String> columns, boolean unique, boolean primary, String predicate) {
        var opclasses = new ArrayList<String>();
        for (String name : columns) {
            String type = table.columns().get(name).type();
            String opclass = type.startsWith("character varying(") ? "text_ops" : type.startsWith("character(") ? "bpchar_ops" : switch (type) {
                case "bigint" -> "int8_ops"; case "uuid" -> "uuid_ops"; case "timestamp with time zone" -> "timestamptz_ops"; default -> throw invalid();
            };
            opclasses.add("pg_catalog." + opclass);
        }
        return new Index(columns, unique, primary, predicate.isEmpty() ? "" : MemoryStorageVerifier.booleanSignature(predicate), opclasses);
    }

    private static void privileges(JdbcTemplate jdbc, String table) {
        var safe = jdbc.query("""
                SELECT NOT c.relrowsecurity AND NOT c.relforcerowsecurity
                    AND NOT EXISTS (SELECT 1 FROM pg_trigger tr WHERE tr.tgrelid=c.oid AND ((NOT tr.tgisinternal AND tr.tgenabled<>'D') OR (tr.tgisinternal AND tr.tgenabled<>'O')))
                    AND has_table_privilege(current_user,c.oid,'SELECT') AND has_table_privilege(current_user,c.oid,'INSERT')
                    AND has_table_privilege(current_user,c.oid,'UPDATE') AS safe
                FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='tianji_agent' AND c.relname=?
                """, (rs, row) -> rs.getBoolean("safe"), table);
        if (safe.size() != 1 || !safe.getFirst()) throw invalid();
    }

    static String migration() { return migration(FILE); }
    private static String migration(String file) {
        try (var input = ToolExecutionStorageVerifier.class.getResourceAsStream("/db/tianji-agent/" + file)) {
            if (input == null) throw invalid(); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failed) { throw invalid(); }
    }
    private static List<String> array(java.sql.Array value) throws SQLException { return value == null ? List.of() : List.copyOf(Arrays.asList((String[]) value.getArray())); }
    private static List<String> columns(String value) { return List.of(value.split(",")); }
    private static Key key(String type, String fields) { return new Key(type, columns(fields), "", "", List.of(), "", "", ""); }
    private static Key foreign(String fields, String target, String targetFields) { return new Key("f", columns(fields), "tianji_agent", target, columns(targetFields), "a", "a", "s"); }
    private record Key(String type, List<String> columns, String targetSchema, String target, List<String> targetColumns, String deletion, String update, String match) {
        Key { columns = List.copyOf(columns); targetColumns = List.copyOf(targetColumns); }
    }
    private record Index(List<String> columns, boolean unique, boolean primary, String predicate, List<String> opclasses) {
        Index { columns = List.copyOf(columns); opclasses = List.copyOf(opclasses); }
    }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool execution journal storage is unavailable or changed"); }
}
