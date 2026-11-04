package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Finite catalogue fixtures exercise the real verifier without creating a database or native engine. */
class CaseStorageConfigurationTest {
    private static final String FILE = "V8__add_formal_cmmn_case_support.sql";
    private static final String PROVENANCE = "tianji_case_start_provenance";
    private static final String PLAN = "tianji_case_plan_item_receipt";
    private static final String INBOX = "tianji_case_completion_inbox";

    @Test void final_resource_has_the_complete_frozen_three_table_contract_and_two_distinct_due_indexes() throws Exception {
        String sql = migration(); var catalog = new Catalog();
        assertEquals(31, catalog.rows.get("columns:" + PROVENANCE).size());
        assertEquals(24, catalog.rows.get("columns:" + PLAN).size());
        assertEquals(17, catalog.rows.get("columns:" + INBOX).size());
        assertEquals(9, catalog.rows.entrySet().stream().filter(e -> e.getKey().startsWith("keys:")).flatMap(e -> e.getValue().stream()).filter(r -> "f".equals(r.get("contype"))).count());
        assertEquals(8, catalog.rows.entrySet().stream().filter(e -> e.getKey().startsWith("indexes:")).mapToInt(e -> e.getValue().size()).sum());
        assertTrue(sql.contains("completion_command_id varchar(128) COLLATE \"C\" NOT NULL"));
        assertTrue(sql.contains("case_completion_orphan_due_idx"));
        assertTrue(sql.contains("case_completion_due_idx"));
        assertFalse(sql.contains("ON DELETE CASCADE"));
        assertFalse(Pattern.compile("(?m)^\\s*(GRANT|CREATE ROLE|ALTER ROLE|DELETE|UPDATE|TRUNCATE)\\s").matcher(sql).find());
        var occurrence = catalog.named("indexes:" + PROVENANCE, "case_provenance_occurrence_idx");
        assertEquals(List.of("tenant_id", "parent_process_instance_id", "source_occurrence_id"), occurrence.get("fields"));
    }

    @Test void exact_final_catalog_is_accepted_and_base_verification_is_still_invoked() throws Exception {
        check(new Catalog(), false);
    }

    @Test void every_column_type_nullability_collation_generation_and_default_is_authoritative() throws Exception {
        for (String table : List.of(PROVENANCE, PLAN, INBOX)) {
            rejects(c -> c.named("columns:" + table, "tenant_id").put("type", "integer"));
            rejects(c -> c.named("columns:" + table, "tenant_id").put("attnotnull", false));
            rejects(c -> c.named("columns:" + table, "case_instance_id").put("collname", "default"));
            rejects(c -> c.named("columns:" + table, "case_instance_id").put("default_value", "'guessed'::text"));
            rejects(c -> c.named("columns:" + table, "tenant_id").put("attidentity", "a"));
            rejects(c -> c.rows.get("columns:" + table).removeLast());
        }
        rejects(c -> c.named("columns:" + INBOX, "completion_command_id").put("type", "character(64)"));
        rejects(c -> c.named("columns:" + INBOX, "terminal_at").put("type", "timestamp(3) with time zone"));
    }

    @Test void exact_checks_reject_missing_case_action_guard_weakened_boolean_grouping_and_sql_unknown_holes() throws Exception {
        rejects(c -> c.rows.get("checks:tianji_human_task_receipt").removeIf(r -> "human_task_case_kind".equals(r.get("name"))));
        rejects(c -> c.named("checks:tianji_human_task_receipt", "human_task_case_kind").put("expression", "model_type <> 'CMMN' OR task_kind = ANY(ARRAY['ACTION','APPROVAL'])"));
        rejects(c -> c.named("checks:" + PROVENANCE, "case_provenance_linked_fields").put("expression", "start_kind='DIRECT' OR source_id IS NOT NULL"));
        for (String removed : List.of("decision_code IS NOT NULL", "applied_command_type IS NOT NULL", "terminal_status IS NOT NULL")) {
            rejects(c -> {
                var row = c.named("checks:" + INBOX, "case_completion_state_fields");
                row.put("expression", ((String) row.get("expression")).replace(removed, "TRUE"));
            });
        }
        rejects(c -> c.named("checks:" + INBOX, "case_completion_terminal_fields").put("convalidated", false));
        rejects(c -> c.named("checks:tianji_process_release_definition", "tianji_process_release_definition_model_type_check").put("expression", "model_type=ANY(ARRAY['BPMN','DMN'])"));
    }

    @Test void foreign_keys_must_retain_full_ordered_identity_same_schema_no_action_and_immediate_enforcement() throws Exception {
        for (String field : List.of("target_schema", "deletion", "confupdtype", "confmatchtype")) {
            rejects(c -> c.named("keys:" + INBOX, "case_completion_command_fkey").put(field, "drift"));
        }
        rejects(c -> c.named("keys:" + INBOX, "case_completion_command_fkey").put("fields", List.of("tenant_id", "completion_command_id")));
        rejects(c -> c.named("keys:" + INBOX, "case_completion_command_fkey").put("condeferrable", true));
        rejects(c -> c.named("keys:" + INBOX, "case_completion_command_fkey").put("convalidated", false));
        rejects(c -> c.rows.get("keys:" + PLAN).removeLast());
    }

    @Test void both_due_indexes_and_native_index_properties_are_required() throws Exception {
        for (String index : List.of("case_completion_due_idx", "case_completion_orphan_due_idx")) {
            rejects(c -> c.rows.get("indexes:" + INBOX).removeIf(r -> index.equals(r.get("name"))));
            rejects(c -> c.named("indexes:" + INBOX, index).put("predicate", "state='PENDING' OR state='DEAD'"));
            rejects(c -> c.named("indexes:" + INBOX, index).put("fields", List.of("tenant_id", "completion_command_id")));
            for (String flag : List.of("indisvalid", "indisready", "indislive", "default_classes", "column_collations", "ascending")) {
                rejects(c -> c.named("indexes:" + INBOX, index).put(flag, false));
            }
        }
        rejects(c -> c.named("indexes:" + PROVENANCE, "case_provenance_occurrence_idx").put("indisunique", false));
    }

    @Test void migration_identity_environment_and_each_runtime_table_privilege_gate_fail_closed() throws Exception {
        rejects(c -> c.history.put("success", false));
        rejects(c -> c.history.put("checksum", 0));
        rejects(c -> c.history.put("script", "V8__other.sql"));
        rejects(c -> c.history.put("type", "JDBC"));
        rejects(c -> c.history.put("version", "9"));
        rejects(c -> c.environment = false);
        for (String table : List.of(PROVENANCE, PLAN, INBOX)) rejects(c -> c.invalidRole = table);
        var catalog = new Catalog(); check(catalog, false);
        assertTrue(catalog.queries.stream().anyMatch(sql -> sql.contains("NOT has_table_privilege(c.oid,'DELETE')")));
        assertTrue(catalog.queries.stream().anyMatch(sql -> sql.contains("NOT has_table_privilege(c.oid,'UPDATE')")));
        assertTrue(catalog.queries.stream().anyMatch(sql -> sql.contains("rolsuper") && sql.contains("relowner") && sql.contains("relrowsecurity")));
    }

    @Test void case_factory_is_installed_only_after_native_initialization_and_updates_both_pointers() throws Exception {
        var fixture = new FactoryFixture();
        var off = new org.springframework.mock.env.MockEnvironment(); fixture.configure(off);
        assertNull(fixture.installer.get()); verifyNoInteractions(fixture.provider);
        fixture.configure(off.withProperty("stellar.tianji.process.cases.enabled", "true"));
        var installer = fixture.installer.get(); assertNotNull(installer); assertEquals(Integer.MAX_VALUE, installer.getPriority());
        var original = fixture.factory.get(); installer.beforeInit(fixture.engine); assertSame(original, fixture.factory.get());
        installer.configure(fixture.engine);
        assertInstanceOf(top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory.class, fixture.factory.get());
        assertSame(fixture.factory.get(), fixture.parserFactory.get());
        assertDoesNotThrow(() -> top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory.requireConfigured(fixture.engine));
        verifyNoInteractions(fixture.provider);
    }

    @Test void case_factory_installation_refuses_an_unknown_factory_or_a_different_parser_pointer() throws Exception {
        for (boolean custom : List.of(false, true)) {
            var fixture = new FactoryFixture();
            if (custom) fixture.factory.set(new org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory() {});
            else fixture.parserFactory.set(new org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory());
            fixture.configure(new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.process.cases.enabled", "true"));
            var installer = fixture.installer.get(); assertNotNull(installer);
            assertThrows(IllegalStateException.class, () -> installer.configure(fixture.engine));
        }
    }

    private static final class FactoryFixture {
        final DataSource source = mock(DataSource.class);
        final org.springframework.jdbc.datasource.DataSourceTransactionManager manager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        final org.flowable.cmmn.spring.SpringCmmnEngineConfiguration engine = mock(org.flowable.cmmn.spring.SpringCmmnEngineConfiguration.class);
        final org.flowable.cmmn.engine.impl.parser.CmmnParserImpl parser = mock(org.flowable.cmmn.engine.impl.parser.CmmnParserImpl.class);
        final java.util.concurrent.atomic.AtomicReference<org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory> factory = new java.util.concurrent.atomic.AtomicReference<>(new org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory());
        final java.util.concurrent.atomic.AtomicReference<org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory> parserFactory = new java.util.concurrent.atomic.AtomicReference<>(factory.get());
        final java.util.concurrent.atomic.AtomicReference<org.flowable.common.engine.impl.EngineConfigurator> installer = new java.util.concurrent.atomic.AtomicReference<>();
        final org.springframework.beans.factory.ObjectProvider<top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        FactoryFixture() {
            when(engine.getDataSource()).thenReturn(source); when(engine.getTransactionManager()).thenReturn(manager); when(engine.getDatabaseSchema()).thenReturn("tianji_process"); when(engine.getDatabaseSchemaUpdate()).thenReturn("false"); when(engine.getBeans()).thenReturn(Map.of());
            when(engine.getExpressionManager()).thenReturn(mock(org.flowable.common.engine.impl.el.ExpressionManager.class));
            when(engine.getClassDelegateFactory()).thenReturn(mock(org.flowable.cmmn.engine.impl.delegate.CmmnClassDelegateFactory.class));
            when(engine.getCmmnRuntimeService()).thenReturn(mock(org.flowable.cmmn.api.CmmnRuntimeService.class)); when(engine.getCmmnHistoryService()).thenReturn(mock(org.flowable.cmmn.api.CmmnHistoryService.class)); when(engine.getCmmnTaskService()).thenReturn(mock(org.flowable.cmmn.api.CmmnTaskService.class));
            when(engine.getHistoryLevel()).thenReturn(org.flowable.common.engine.impl.history.HistoryLevel.AUDIT);
            when(engine.getCmmnParser()).thenReturn(parser); when(engine.getActivityBehaviorFactory()).thenAnswer(i -> factory.get()); when(parser.getActivityBehaviorFactory()).thenAnswer(i -> parserFactory.get());
            doAnswer(i -> { factory.set(i.getArgument(0)); return engine; }).when(engine).setActivityBehaviorFactory(any());
            doAnswer(i -> { parserFactory.set(i.getArgument(0)); return null; }).when(parser).setActivityBehaviorFactory(any());
            doAnswer(i -> { installer.set(i.getArgument(0)); return engine; }).when(engine).addConfigurator(any(org.flowable.common.engine.impl.EngineConfigurator.class));
        }
        void configure(org.springframework.core.env.Environment environment) throws Exception {
            var outbox = new top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure(source, "source", manager, "manager");
            var properties = new top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties(); properties.getStorage().getJdbc().setSchema("tianji_process");
            try (var construction = mockConstruction(JdbcTemplate.class, (jdbc, ignored) -> when(jdbc.queryForObject("SELECT current_schema()", String.class)).thenReturn("tianji_process"))) {
                new ProcessStorageConfiguration().caseStorageGuard(outbox, properties, environment, provider).configure(engine);
            }
        }
    }

    private static void rejects(Consumer<Catalog> mutate) throws Exception {
        var catalog = new Catalog(); mutate.accept(catalog); check(catalog, true);
    }
    private static void check(Catalog catalog, boolean reject) {
        try (var construction = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(catalog::answer))) {
            var verifier = spy(new ProcessApiSchemaVerifier(mock(DataSource.class), "tianji_process"));
            doNothing().when(verifier).verify();
            if (reject) assertThrows(IllegalStateException.class, verifier::verifyCase);
            else { assertDoesNotThrow(verifier::verifyCase); verify(verifier).verify(); }
        }
    }
    private static String migration() throws Exception {
        try (var input = CaseStorageConfigurationTest.class.getResourceAsStream("/db/tianji-process/" + FILE)) {
            assertNotNull(input); return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The input catalogue is derived independently from the reviewed DDL, then one actual catalogue field is corrupted. */
    private static final class Catalog {
        private final Map<String, List<Map<String, Object>>> rows = new HashMap<>();
        private final Map<String, Object> history = new HashMap<>();
        private final List<String> queries = new ArrayList<>();
        private boolean environment = true;
        private String invalidRole;

        Catalog() throws Exception {
            String sql = migration(); CRC32 crc = new CRC32(); sql.lines().forEach(line -> crc.update(line.getBytes(StandardCharsets.UTF_8)));
            history.putAll(Map.of("version", "8", "success", true, "checksum", (int) crc.getValue(), "script", FILE, "type", "SQL"));
            var tables = Pattern.compile("CREATE TABLE ([a-z_]+) \\(").matcher(sql);
            while (tables.find()) {
                String table = tables.group(1); int open = tables.end() - 1; String body = sql.substring(open + 1, closing(sql, open));
                var columns = new ArrayList<Map<String, Object>>();
                var fields = Pattern.compile("(?m)^    ([a-z_0-9]+) (bigint|integer|varchar\\(\\d+\\)|char\\(\\d+\\)|timestamptz\\(6\\))([^\\n]*)").matcher(body);
                while (fields.find()) {
                    String type = fields.group(2).replace("varchar(", "character varying(").replace("char(", "character(").replace("timestamptz(6)", "timestamp(6) with time zone");
                    var row = row(fields.group(1)); row.put("attname", fields.group(1)); row.put("type", type); row.put("attnotnull", fields.group(3).contains("NOT NULL"));
                    row.put("collname", fields.group(3).contains("COLLATE \"C\"") ? "C" : null); row.put("attidentity", ""); row.put("attgenerated", ""); row.put("default_value", null); columns.add(row);
                }
                rows.put("columns:" + table, columns); rows.put("checks:" + table, checks(body));
                var keys = new ArrayList<Map<String, Object>>();
                var matcher = Pattern.compile("CONSTRAINT ([a-z_]+)\\s+(PRIMARY KEY|FOREIGN KEY)\\s*\\(([^)]+)\\)").matcher(body);
                while (matcher.find()) {
                    var row = row(matcher.group(1)); boolean foreign = "FOREIGN KEY".equals(matcher.group(2)); row.put("contype", foreign ? "f" : "p");
                    row.put("fields", fields(matcher.group(3))); row.put("target_schema", null); row.put("target", null); row.put("target_fields", List.of());
                    row.put("deletion", ""); row.put("confupdtype", ""); row.put("confmatchtype", "s"); row.put("condeferrable", false); row.put("condeferred", false);
                    if (foreign) {
                        var reference = Pattern.compile("\\s*REFERENCES ([a-z_]+)\\s*\\(([^)]+)\\)").matcher(body.substring(matcher.end())); assertTrue(reference.find());
                        row.put("target_schema", "tianji_process"); row.put("target", reference.group(1)); row.put("target_fields", fields(reference.group(2))); row.put("deletion", "a"); row.put("confupdtype", "a");
                    }
                    keys.add(row);
                }
                rows.put("keys:" + table, keys);
                var indexes = new ArrayList<Map<String, Object>>();
                var primary = keys.stream().filter(k -> "p".equals(k.get("contype"))).findFirst().orElseThrow();
                indexes.add(index((String) primary.get("name"), (List<String>) primary.get("fields"), true, true, null)); rows.put("indexes:" + table, indexes);
            }
            var indexes = Pattern.compile("CREATE (UNIQUE )?INDEX ([a-z_]+)\\s+ON ([a-z_]+)\\s*\\(([^)]+)\\)(?:\\s+WHERE ([^;]+))?;").matcher(sql);
            while (indexes.find()) rows.get("indexes:" + indexes.group(3)).add(index(indexes.group(2), fields(indexes.group(4)), indexes.group(1) != null, false, indexes.group(5)));
            rows.put("checks:tianji_process_release_definition", checks(sql.substring(sql.indexOf("ALTER TABLE tianji_process_release_definition"), sql.indexOf("ALTER TABLE tianji_human_task_receipt"))));
            rows.get("checks:tianji_process_release_definition").getFirst().put("name", "tianji_process_release_definition_model_type_check");
            rows.get("checks:tianji_process_release_definition").getFirst().put("conname", "tianji_process_release_definition_model_type_check");
            var taskChecks = checks(sql.substring(sql.indexOf("ALTER TABLE tianji_human_task_receipt"), sql.indexOf("CREATE TABLE")));
            for (var row : taskChecks) if (((String) row.get("name")).endsWith("_v8")) { String name = ((String) row.get("name")).replace("_v8", ""); row.put("name", name); row.put("conname", name); }
            rows.put("checks:tianji_human_task_receipt", taskChecks);
        }
        Map<String, Object> named(String key, String name) { return rows.get(key).stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow(() -> new AssertionError("Unknown catalogue fixture: " + key + '/' + name)); }
        Object answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            if (args.length == 0 || !(args[0] instanceof String sql)) return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            queries.add(sql); List<Map<String, Object>> selected;
            String table = Arrays.stream(args).filter(String.class::isInstance).map(String.class::cast).filter(s -> s.startsWith("tianji_")).reduce((a,b) -> b).orElse("");
            if (sql.contains("pg_is_in_recovery()")) return environment;
            if (sql.contains("has_table_privilege")) return !table.equals(invalidRole);
            if (sql.contains("schema_history")) selected = List.of(history);
            else if (sql.contains("attidentity")) selected = rows.getOrDefault("columns:" + table, List.of());
            else if (sql.contains("c.contype='c'")) selected = rows.getOrDefault("checks:" + table, List.of());
            else if (sql.contains("c.contype IN ('p','u','f')")) selected = rows.getOrDefault("keys:" + table, List.of());
            else if (sql.contains("FROM pg_index")) selected = rows.getOrDefault("indexes:" + table, List.of());
            else throw new AssertionError("Unrecognized catalogue query: " + sql);
            var callback = Arrays.stream(args).filter(RowCallbackHandler.class::isInstance).map(RowCallbackHandler.class::cast).findFirst();
            if (callback.isPresent()) { for (var row : selected) callback.orElseThrow().processRow(resultSet(row)); return null; }
            var mapper = Arrays.stream(args).filter(RowMapper.class::isInstance).map(RowMapper.class::cast).findFirst().orElseThrow();
            var result = new ArrayList<>(); for (var row : selected) result.add(mapper.mapRow(resultSet(row), result.size())); return result;
        }
        private static Map<String, Object> row(String name) { var row = new LinkedHashMap<String, Object>(); row.put("name", name); row.put("conname", name); row.put("relname", name); row.put("convalidated", true); return row; }
        private static Map<String, Object> index(String name, List<String> fields, boolean unique, boolean primary, String predicate) {
            var row = row(name); row.put("fields", fields); row.put("indisunique", unique); row.put("indisprimary", primary); row.put("predicate", predicate); row.put("amname", "btree"); row.put("indnkeyatts", fields.size()); row.put("indnatts", fields.size());
            for (String flag : List.of("indisvalid", "indisready", "indislive", "default_classes", "column_collations", "ascending")) row.put(flag, true); return row;
        }
        private static List<Map<String, Object>> checks(String body) {
            var rows = new ArrayList<Map<String, Object>>(); var check = Pattern.compile("CONSTRAINT ([a-z_0-9]+)\\s+CHECK\\s*\\(").matcher(body);
            while (check.find()) { var row = row(check.group(1)); int open = check.end() - 1; row.put("expression", body.substring(open + 1, closing(body, open))); rows.add(row); } return rows;
        }
        private static List<String> fields(String value) { return Arrays.stream(value.split(",")).map(String::trim).toList(); }
        private static int closing(String value, int open) {
            int depth = 0; boolean quoted = false;
            for (int i=open;i<value.length();i++) { char c=value.charAt(i); if (c=='\'') { if (quoted && i+1<value.length() && value.charAt(i+1)=='\'') i++; else quoted=!quoted; } else if (!quoted) { if(c=='(')depth++; else if(c==')' && --depth==0)return i; } }
            throw new AssertionError("Unbalanced fixture DDL");
        }
        private static ResultSet resultSet(Map<String, Object> row) throws Exception {
            var rs = mock(ResultSet.class);
            when(rs.getString(anyString())).thenAnswer(i -> row.get(i.getArgument(0)) == null ? null : row.get(i.getArgument(0)).toString());
            when(rs.getObject(anyString())).thenAnswer(i -> row.get(i.getArgument(0)));
            when(rs.getBoolean(anyString())).thenAnswer(i -> Boolean.TRUE.equals(row.get(i.getArgument(0))));
            when(rs.getInt(anyString())).thenAnswer(i -> row.get(i.getArgument(0)) instanceof Number n ? n.intValue() : 0);
            when(rs.getArray(anyString())).thenAnswer(i -> { Object value=row.get(i.getArgument(0)); if(value==null)return null; var array=mock(java.sql.Array.class); when(array.getArray()).thenReturn(((List<?>)value).toArray(String[]::new));return array; });
            return rs;
        }
    }
}
