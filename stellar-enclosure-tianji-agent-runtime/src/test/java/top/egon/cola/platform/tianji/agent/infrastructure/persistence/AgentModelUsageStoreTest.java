package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.mockito.ArgumentMatchers;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.RowCallbackHandler;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentModelUsageStoreTest {
    private static final TrustedAgentActor ACTOR = new TrustedAgentActor(
            42, TrustedAgentActor.SubjectType.SERVICE, "owner", "a".repeat(64));
    private static final Instant NOW = Instant.parse("2026-11-05T05:50:00.000001Z");
    private static final Instant CREATED = NOW.minusSeconds(2);

    @Test
    void finiteOwnerReadScopeAndDisabledNoV15() {
        var source = mock(DataSource.class);
        var disabled = new AgentLedgerService(source);
        assertFalse(disabled.modelUsageEnabled());
        assertFalse(disabled.acceptsModelUsage("CONFIG", "model-route"));
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                assertThrows(AgentLedgerException.class,
                        () -> disabled.getModelUsage(ACTOR, new GetAgentRunQuery("run"), null)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                assertThrows(AgentLedgerException.class, () -> disabled.listModelUsageByCorrelation(ACTOR,
                        new AgentLedgerService.AgentModelUsageCorrelationQuery("corr", CREATED, NOW, null))).code());
        verifyNoInteractions(source);

        var jdbc = mock(JdbcTemplate.class);
        var repository = new JdbcAgentLedgerRepository(jdbc, new AgentJsonCodec());
        var owned = run("RUNNING", 1, NOW.plusSeconds(1));
        doAnswer(invocation -> {
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            assertTrue(sql.contains("tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"));
            assertTrue(sql.contains("correlation_id COLLATE \"C\"=? AND created_at>=? AND created_at<?"));
            assertTrue(sql.contains("AND (created_at,run_id)<(?,?) ORDER BY created_at DESC, run_id DESC LIMIT ?"));
            assertArrayEquals(new Object[]{42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "corr",
                    Timestamp.from(CREATED), Timestamp.from(NOW), Timestamp.from(CREATED.plusSeconds(1)), "run-after", 21},
                    (Object[]) args[2]);
            return List.of(owned);
        }).when(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.ModelUsageRunRow>>any(),
                any(Object[].class));
        assertEquals(List.of(owned), repository.modelUsageRuns(ACTOR, "corr", CREATED, NOW, CREATED.plusSeconds(1), "run-after", 21));

        var slot = slot("PENDING", null, null, null, null, null, null);
        doAnswer(invocation -> {
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            assertTrue(sql.contains("u.tenant_id=b.tenant_id AND u.run_id=b.run_id"));
            assertTrue(sql.contains("u.reservation_id=b.reservation_id"));
            assertTrue(sql.contains("r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=? AND r.run_id=?"));
            assertTrue(sql.contains("b.kind='MODEL'"));
            assertTrue(sql.contains("b.reservation_id COLLATE \"C\">? ORDER BY b.reservation_id COLLATE \"C\" LIMIT ?"));
            assertArrayEquals(new Object[]{42L, "SERVICE", ACTOR.issuerFingerprint(), "owner", "run", "slot-0", 101},
                    (Object[]) args[2]);
            return List.of(slot);
        }).when(jdbc).query(anyString(), ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.ModelUsageSlotRow>>any(),
                any(Object[].class));
        assertEquals(List.of(slot), repository.modelUsageSlots(ACTOR, "run", "slot-0", 101));

        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.PENDING,
                AgentLedgerService.projectModelUsageSlot(owned, slot, NOW).state());
        var expired = AgentLedgerService.projectModelUsageSlot(run("RUNNING", 1, NOW), slot, NOW);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.UNKNOWN, expired.state());
        assertEquals("RESERVATION_ENDED", expired.safeReason());
        assertNull(expired.usage());
        var advanced = AgentLedgerService.projectModelUsageSlot(run("RUNNING", 2, NOW.plusSeconds(1)), slot, NOW);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.UNKNOWN, advanced.state());

        var historic = new JdbcAgentLedgerRepository.ModelUsageSlotRow("legacy", 1, "SETTLED", CREATED,
                false, null, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(AgentLedgerService.AgentModelUsageStateEnum.NOT_INSTRUMENTED,
                AgentLedgerService.projectModelUsageSlot(owned, historic, NOW).state());
        var unpriced = AgentLedgerService.projectModelUsageSlot(owned,
                slot("UNPRICED", 0L, 0L, null, "PRICE_MISSING", null, null), NOW);
        assertEquals(0L, unpriced.usage().totalTokens());
        assertNull(unpriced.usage().cost());
        var priced = AgentLedgerService.projectModelUsageSlot(owned,
                slot("PRICED", 2L, 3L, new BigDecimal("0.00000250"), null, "b".repeat(64), "USD"), NOW);
        assertEquals(5L, priced.usage().totalTokens());
        assertEquals(new BigDecimal("0.00000250"), priced.usage().cost());
        assertThrows(AgentLedgerException.class, () -> AgentLedgerService.projectModelUsageSlot(owned,
                slot("UNPRICED", Long.MAX_VALUE, 1L, null, "PRICE_MISSING", null, null), NOW));

        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                "corr", NOW.minus(Duration.ofDays(31)).minusNanos(1_000), NOW, null));
        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                " corr", CREATED, NOW, null));
        assertThrows(AgentLedgerException.class, () -> new AgentLedgerService.AgentModelUsageCorrelationQuery(
                "corr", CREATED, NOW.plusNanos(1), null));
    }

    private static JdbcAgentLedgerRepository.ModelUsageRunRow run(String state, long epoch, Instant expires) {
        return new JdbcAgentLedgerRepository.ModelUsageRunRow("run", "corr", CREATED, state, epoch, true, expires);
    }

    private static JdbcAgentLedgerRepository.ModelUsageSlotRow slot(String state, Long input, Long output, BigDecimal cost,
                                                                  String reason, String priceVersion, String currency) {
        return new JdbcAgentLedgerRepository.ModelUsageSlotRow("slot-1", 1, "RESERVED", CREATED, true, 1L, state,
                "model-route", "fixture-model", priceVersion, currency, input, output, cost, reason, CREATED,
                input == null ? null : NOW);
    }
    private static final String V15 = "V15__add_agent_model_usage.sql";
    private static final String V15_SHA256 = "f5b414af7c1b422697b7ab98823d78fbff0f6327e858a1f5bf816e6b75a74238";
    private static final int V15_CRC = 1512602432;

    @Test
    void requiresImmutableV15Resource() throws Exception {
        try (var resource = getClass().getResourceAsStream("/db/tianji-agent/" + V15)) {
            assertNotNull(resource, "immutable V15 model usage resource");
            byte[] bytes = resource.readAllBytes();
            assertEquals(4433, bytes.length);
            assertEquals(V15_SHA256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
        assertEquals(V15_CRC, AgentStorageVerifier.checksum(V15));
    }

    @Test
    void finiteModelUsageReadinessMatrix() throws Exception {
        var valid = new UsageCatalog();
        verifyUsageCatalog(valid, false);
        assertTrue(valid.roleSql.contains("NOT has_table_privilege(c.oid,'UPDATE,DELETE,TRUNCATE,TRIGGER,REFERENCES')"));
        assertTrue(valid.roleSql.contains("NOT pg_has_role(c.relowner,'MEMBER')"));
        assertTrue(valid.roleSql.contains("NOT pg_has_role(n.nspowner,'MEMBER')"));
        assertTrue(valid.roleSql.contains("has_column_privilege(c.oid,a.attnum,'UPDATE')"));
        assertTrue(valid.roleSql.contains("ARRAY['actual_cost','actual_input_tokens','actual_output_tokens','final_sha256','measured_at','safe_reason','state']"));
        rejectsUsageCatalog(c -> c.primary = false);
        rejectsUsageCatalog(c -> c.history.clear());
        rejectsUsageCatalog(c -> c.history.add(new HashMap<>(c.history.getFirst())));
        rejectsUsageCatalog(c -> c.history.getFirst().put("type", "JDBC"));
        rejectsUsageCatalog(c -> c.history.getFirst().put("script", "V15__wrong.sql"));
        rejectsUsageCatalog(c -> c.history.getFirst().put("success", false));
        rejectsUsageCatalog(c -> c.history.getFirst().put("checksum", V15_CRC + 1));
        rejectsUsageCatalog(c -> c.columns.removeLast());
        rejectsUsageCatalog(c -> c.columns.get(8).put("type", "numeric(24,8)"));
        rejectsUsageCatalog(c -> c.columns.get(2).put("collation_schema", "wrong"));
        rejectsUsageCatalog(c -> c.columns.get(10).put("default_value", "'UNKNOWN'::character varying"));
        rejectsUsageCatalog(c -> c.keys.getLast().put("confupdtype", "c"));
        rejectsUsageCatalog(c -> c.keys.getFirst().put("index_count", 2));
        rejectsUsageCatalog(c -> c.checks.getLast().put("definition", "CHECK (state='PENDING')"));
        rejectsUsageCatalog(c -> c.checks.getLast().put("convalidated", false));
        rejectsUsageCatalog(c -> c.permitted = false);

        var source = mock(DataSource.class);
        try (var verifiers = mockConstruction(AgentStorageVerifier.class,
                (mocked, context) -> assertEquals(List.of(source), context.arguments()))) {
            var old = new AgentLedgerService(source);
            assertFalse(old.modelUsageEnabled());
            var publisher = mock(AgentTerminalEventPublisher.class);
            when(publisher.producerId()).thenReturn("agent");
            when(publisher.usesDataSource(source)).thenReturn(true);
            assertFalse(AgentLedgerService.withTerminalEvents(source, publisher).modelUsageEnabled());
            assertTrue(verifiers.constructed().isEmpty());
            var declared = new ArrayList<>(List.of("text-route@1#digest"));
            var enabled = assertDoesNotThrow(() -> AgentLedgerService.withModelUsage(source, publisher, declared));
            declared.clear();
            assertTrue(enabled.modelUsageEnabled());
            assertTrue(enabled.usesModelUsageRoutes(List.of("text-route@1#digest")));
            assertTrue(enabled.acceptsModelUsage("CONFIG", "text-route@1#digest"));
            assertFalse(enabled.acceptsModelUsage("CODE_FIRST", "text-route@1#digest"));
            assertFalse(enabled.acceptsModelUsage("CONFIG", "other"));
            assertTrue(enabled.usesDataSource(source));
            assertTrue(enabled.hasTerminalPublisher(publisher));
            verify(verifiers.constructed().getFirst()).verifyModelUsage();
            assertThrows(AgentLedgerException.class, () -> AgentLedgerService.withModelUsage(source, publisher, List.of()));
            assertThrows(AgentLedgerException.class, () -> AgentLedgerService.withModelUsage(source, publisher, List.of("x","x")));
            assertThrows(AgentLedgerException.class, () -> AgentLedgerService.withModelUsage(source, publisher, List.of("route*")));
            assertThrows(AgentLedgerException.class, () -> AgentLedgerService.withModelUsage(source, publisher, List.of(" route")));
            var foreign = mock(AgentTerminalEventPublisher.class);
            when(foreign.producerId()).thenReturn("agent");
            assertThrows(IllegalArgumentException.class, () -> AgentLedgerService.withModelUsage(source, foreign, List.of("route")));
            assertEquals(1, verifiers.constructed().size());
        }
    }

    private static void verifyUsageCatalog(UsageCatalog catalog, boolean rejected) throws Exception {
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        try (var templates = mockConstruction(JdbcTemplate.class, org.mockito.Mockito.withSettings().defaultAnswer(catalog::answer))) {
            var verifier = spy(new AgentStorageVerifier(source));
            doNothing().when(verifier).verify();
            if (rejected) assertThrows(AgentLedgerException.class, verifier::verifyModelUsage);
            else {
                assertDoesNotThrow(verifier::verifyModelUsage);
                verify(verifier).verify();
            }
        }
    }

    private static void rejectsUsageCatalog(Consumer<UsageCatalog> mutation) throws Exception {
        var catalog = new UsageCatalog();
        mutation.accept(catalog);
        verifyUsageCatalog(catalog, true);
    }

    private static final class UsageCatalog {
        private static final List<String> KEY = List.of("tenant_id","run_id","reservation_id");
        private final List<Map<String, Object>> history = new ArrayList<>(List.of(row("type", "SQL", "script", V15,
                "success", true, "checksum", V15_CRC)));
        private final List<Map<String, Object>> columns = new ArrayList<>(List.of(
                column("tenant_id","bigint",true,null,null),
                column("run_id","character varying(64)",true,"default",null),
                column("reservation_id","character varying(128)",true,"C",null),
                column("lease_epoch","bigint",true,null,null),
                column("model_route_ref","character varying(256)",true,"C",null),
                column("model_name","character varying(512)",true,"C",null),
                column("price_version_ref","character(64)",false,"C",null),
                column("currency","character(3)",false,"C",null),
                column("input_rate_per_million","numeric(32,8)",false,null,null),
                column("output_rate_per_million","numeric(32,8)",false,null,null),
                column("state","character varying(16)",true,"C","'PENDING'::character varying"),
                column("actual_input_tokens","bigint",false,null,null),
                column("actual_output_tokens","bigint",false,null,null),
                column("actual_cost","numeric(24,8)",false,null,null),
                column("safe_reason","character varying(32)",false,"C",null),
                column("final_sha256","character(64)",false,"C",null),
                column("created_at","timestamp(6) with time zone",true,null,null),
                column("measured_at","timestamp(6) with time zone",false,null,null)));
        private final List<Map<String, Object>> keys = new ArrayList<>(List.of(
                row("conname","agent_model_usage_pkey","contype","p","convalidated",true,
                        "columns",KEY,"index_name","agent_model_usage_pkey","indisprimary",true,"indisunique",true,
                        "indisvalid",true,"indisready",true,"indislive",true,"indimmediate",true,"amname","btree",
                        "indnkeyatts",3,"indnatts",3,"plain_index",true,"ascending",true,"default_classes",true,
                        "column_collations",true,"index_count",1),
                row("conname","agent_model_usage_budget_fkey","contype","f","convalidated",true,
                        "columns",KEY,"ref_schema","tianji_agent","ref_table","agent_budget_reservation",
                        "ref_columns",KEY,"confupdtype","a","confdeltype","a","confmatchtype","s")));
        private final List<Map<String, Object>> checks = new ArrayList<>();
        private boolean primary = true;
        private boolean permitted = true;
        private String roleSql;

        UsageCatalog() throws Exception {
            try (var input = getClass().getResourceAsStream("/db/tianji-agent/" + V15)) {
                assertNotNull(input);
                var sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                var expressions = Pattern.compile("(?ms)^    CONSTRAINT (agent_model_usage_[a-z_]+) (CHECK\\s*.*?)"
                        + "(?=,\\n    CONSTRAINT|\\n\\);)").matcher(sql);
                while (expressions.find()) {
                    String expression = expressions.group(2).replace(">= 0 AND actual_cost", ">= (0)::numeric AND actual_cost")
                            .replace(">= 0 AND input_rate_per_million", ">= (0)::numeric AND input_rate_per_million")
                            .replace(">= 0 AND output_rate_per_million", ">= (0)::numeric AND output_rate_per_million");
                    checks.add(row("conname",expressions.group(1),"convalidated",true,"definition",expression));
                }
            }
            assertEquals(List.of("agent_model_usage_scope","agent_model_usage_model","agent_model_usage_price",
                            "agent_model_usage_tokens","agent_model_usage_cost","agent_model_usage_digest",
                            "agent_model_usage_time","agent_model_usage_lifecycle"),
                    checks.stream().map(r -> r.get("conname")).toList());
        }

        Object answer(InvocationOnMock call) throws Throwable {
            Object[] args = call.getArguments();
            if (args.length == 0 || !(args[0] instanceof String sql)) return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
            if (sql.contains("pg_is_in_recovery()")) {
                assertTrue(sql.contains("transaction_read_only") && sql.contains("session_replication_role"));
                return primary;
            }
            if (sql.contains("has_any_column_privilege")) {
                roleSql = sql;
                return permitted;
            }
            if (sql.contains("WHERE version='15'")) {
                assertFalse(sql.contains("AND type"));
                var mapper = (RowMapper<?>) args[1];
                var result = new ArrayList<>();
                for (var row : history) result.add(mapper.mapRow(resultSet(row), result.size()));
                return result;
            }
            var callback = (RowCallbackHandler) args[1];
            List<Map<String, Object>> rows = sql.contains("pg_get_constraintdef") ? checks
                    : sql.contains("confupdtype") ? keys : columns;
            for (var row : rows) callback.processRow(resultSet(row));
            return null;
        }

        private static Map<String, Object> column(String name, String type, boolean required, String collation, String defaultValue) {
            return row("attname",name,"type",type,"attnotnull",required,"collname",collation,
                    "collation_schema",collation == null ? null : "pg_catalog","attidentity","","attgenerated","","default_value",defaultValue);
        }

        private static Map<String, Object> row(Object... pairs) {
            var row = new HashMap<String, Object>();
            for (int i = 0; i < pairs.length; i += 2) row.put((String) pairs[i], pairs[i + 1]);
            return row;
        }

        private static ResultSet resultSet(Map<String, Object> row) throws Exception {
            var result = mock(ResultSet.class);
            when(result.getObject(anyString())).thenAnswer(invocation -> row.get(invocation.getArgument(0)));
            when(result.getString(anyString())).thenAnswer(invocation -> (String) row.get(invocation.getArgument(0)));
            when(result.getBoolean(anyString())).thenAnswer(invocation -> Boolean.TRUE.equals(row.get(invocation.getArgument(0))));
            when(result.getInt(anyString())).thenAnswer(invocation -> ((Number) row.getOrDefault(invocation.getArgument(0),0)).intValue());
            for (String key : List.of("columns","ref_columns")) {
                if (row.get(key) instanceof List<?> items) {
                    var array = mock(java.sql.Array.class);
                    when(array.getArray()).thenReturn(items.toArray(String[]::new));
                    when(result.getArray(key)).thenReturn(array);
                }
            }
            return result;
        }
    }
}
