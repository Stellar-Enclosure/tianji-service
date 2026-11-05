package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile-only in finite gates. Actual execution requires separate authorization and an empty isolated database. */
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@EnabledIfSystemProperty(named = "tianji.postgres.test.model-usage.authorized", matches = "true")
@Timeout(120)
class AgentModelUsagePostgresTest {
    private static final String HISTORY = """
            SELECT version,type,script,checksum,success FROM tianji_agent.flyway_schema_history
            WHERE version IS NOT NULL ORDER BY installed_rank
            """;
    private static final String TABLE = "tianji_agent.agent_model_usage";
    private static Database isolated;

    @Test void v15MigrationOnIsolatedPostgres() {
        var db = modelUsageDatabase();
        var enabled = new AgentStorageVerifier(db.runtime());
        assertDoesNotThrow(enabled::verifyModelUsage);
        assertDoesNotThrow(new AgentStorageVerifier(db.setup())::verify);
        assertThrows(AgentLedgerException.class, new AgentStorageVerifier(db.setup())::verifyModelUsage);

        var jdbc = db.jdbc();
        var tx = new TransactionTemplate(new DataSourceTransactionManager(db.setup()));
        tx.executeWithoutResult(status -> {
            try {
                // These are isolated constraint fixtures, not publication, authorization or settlement proof.
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_definition (tenant_id,definition_key,created_at)
                        VALUES (42,'usage-fixture',clock_timestamp())
                        """);
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_definition_version
                          (tenant_id,definition_key,version,content_sha256,specification,created_at)
                        VALUES (42,'usage-fixture',1,repeat('a',64),'{}'::jsonb,clock_timestamp())
                        """);
                jdbc.update("""
                        INSERT INTO tianji_agent.agent_run
                          (tenant_id,run_id,definition_key,definition_version,subject_type,subject_issuer,subject_id,
                           idempotency_key,request_hash,status,deadline_at,execution_snapshot,created_at)
                        VALUES (42,'usage-v15-fixture','usage-fixture',1,'USER',repeat('b',64),'owner',
                                'usage-fixture',repeat('c',64),'QUEUED',clock_timestamp()+interval '1 minute','{}'::jsonb,clock_timestamp())
                        """);
                for (String slot : List.of("valid", "invalid", "overflow")) {
                    jdbc.update("""
                            INSERT INTO tianji_agent.agent_budget_reservation
                              (tenant_id,run_id,reservation_id,lease_epoch,kind,input_tokens,output_tokens,state,created_at)
                            VALUES (42,'usage-v15-fixture',?,1,'MODEL',0,0,'RESERVED',clock_timestamp())
                            """, slot);
                }
                String base = "INSERT INTO " + TABLE
                        + " (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at) "
                        + "VALUES (42,'usage-v15-fixture',?,1,'fixture-route','fixture-model',clock_timestamp())";
                assertEquals(1, jdbc.update(base, "valid"));
                assertEquals("PENDING", jdbc.queryForObject("SELECT state FROM " + TABLE
                        + " WHERE tenant_id=42 AND run_id='usage-v15-fixture' AND reservation_id='valid'", String.class));
                jdbc.execute("SAVEPOINT usage_invalid");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, "unreserved"));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid");
                }
                jdbc.execute("SAVEPOINT usage_invalid_fields");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            INSERT INTO tianji_agent.agent_model_usage
                              (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at,
                               price_version_ref,currency,input_rate_per_million,output_rate_per_million)
                            VALUES (42,'usage-v15-fixture','invalid',1,'fixture-route','fixture-model',clock_timestamp(),
                                    repeat('d',64),'USD','NaN'::numeric,0)
                            """));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid_fields");
                }
                jdbc.execute("SAVEPOINT usage_invalid_sum");
                try {
                    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                            INSERT INTO tianji_agent.agent_model_usage
                              (tenant_id,run_id,reservation_id,lease_epoch,model_route_ref,model_name,created_at,measured_at,
                               state,safe_reason,actual_input_tokens,actual_output_tokens,final_sha256)
                            VALUES (42,'usage-v15-fixture','overflow',1,'fixture-route','fixture-model',
                                    clock_timestamp(),clock_timestamp(),'UNPRICED','PRICE_MISSING',
                                    9223372036854775807,1,repeat('e',64))
                            """));
                } finally {
                    jdbc.execute("ROLLBACK TO SAVEPOINT usage_invalid_sum");
                }
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    @Test void immutableUsageAndBudgetOnIsolatedPostgres() throws Exception {
        var db = modelUsageDatabase();
        var jdbc = db.jdbc();
        String role = new JdbcTemplate(db.runtime()).queryForObject("SELECT current_user::text", String.class);
        if (role == null || !role.matches("tianji_it_[a-z0-9_]{1,50}")
                || !role.equals(System.getProperty("tianji.postgres.test.model-usage.runtime-role"))) {
            throw new IllegalStateException("Agent model usage I requires its verified isolated role");
        }
        String quoted = '"' + role + '"';
        // Only this disposable I role receives the existing V1/V2 DML needed by the actual service.
        jdbc.execute("GRANT INSERT, UPDATE ON tianji_agent.agent_run,tianji_agent.agent_budget_reservation TO " + quoted);
        jdbc.execute("GRANT INSERT ON tianji_agent.agent_event TO " + quoted);
        var setup = new AgentLedgerService(db.setup());
        var owner = TrustedAgentActor.verified(42015, TrustedAgentActor.SubjectType.USER, "usage-owner", "https://idp.example/42015");
        setup.publish(owner, modelUsageDefinition());
        var ledger = AgentLedgerService.withModelUsage(db.runtime(), null, List.of(USAGE_ROUTE));
        var active = claimUsageRun(ledger, owner, "usage-on");
        var lease = active.lease();
        var price = new AgentModelUsagePriceVO(USAGE_ROUTE, USAGE_MODEL, "USD",
                new BigDecimal("2.00"), new BigDecimal("5.00"));
        var seed = new AgentBudgetService.ModelUsageSeedVO(USAGE_ROUTE, USAGE_MODEL, price);
        var request = modelBudget("priced", 100, 100);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                gate.await();
                return ledger.budgets().reserveBudget(owner, lease, request, null, seed);
            });
            var second = pool.submit(() -> {
                gate.await();
                return ledger.budgets().reserveBudget(owner, lease, request, null, seed);
            });
            gate.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS).newlyCreated() != second.get(10, TimeUnit.SECONDS).newlyCreated());
        }
        assertEquals(1, countSlot(jdbc, TABLE, owner, lease, "priced"));
        assertEquals(1, countSlot(jdbc, "tianji_agent.agent_budget_reservation", owner, lease, "priced"));
        var pending = usageSlot(jdbc, owner, lease, "priced");
        assertEquals("PENDING", pending.get("state"));
        assertEquals("RESERVED", budgetSlot(jdbc, owner, lease, "priced").get("state"));
        assertEquals(price.priceVersionRef(), pending.get("price_version_ref"));
        assertEquals(1, counters(jdbc, owner, lease).get("model_calls_reserved"));

        var changedPrice = new AgentModelUsagePriceVO(USAGE_ROUTE, USAGE_MODEL, "USD",
                new BigDecimal("3.00"), new BigDecimal("5.00"));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                assertThrows(AgentLedgerException.class, () -> ledger.budgets().reserveBudget(owner, lease, request, null,
                        new AgentBudgetService.ModelUsageSeedVO(USAGE_ROUTE, USAGE_MODEL, changedPrice))).code());
        assertEquals(pending, usageSlot(jdbc, owner, lease, "priced"));

        var actual = new AgentBudgetSettlement(20L, 30L, new BigDecimal("0.05"));
        var observed = measured(20L, 30L);
        assertEquals(AgentBudgetReservation.State.SETTLED,
                ledger.budgets().settleBudget(owner, lease, "priced", actual, observed).state());
        var priced = usageSlot(jdbc, owner, lease, "priced");
        assertEquals("PRICED", priced.get("state"));
        assertEquals(20L, priced.get("actual_input_tokens"));
        assertEquals(30L, priced.get("actual_output_tokens"));
        assertEquals(0, new BigDecimal("0.00019000").compareTo((BigDecimal) priced.get("actual_cost")));
        assertEquals(0, new BigDecimal("0.05").compareTo((BigDecimal) budgetSlot(jdbc, owner, lease, "priced").get("actual_cost")));
        assertTrue(priced.get("final_sha256") instanceof String hash && hash.matches("[0-9a-f]{64}"));
        assertTrue(priced.get("measured_at") != null);
        assertEquals(AgentBudgetReservation.State.SETTLED,
                ledger.budgets().settleBudget(owner, lease, "priced", actual, observed).state());
        assertEquals(priced, usageSlot(jdbc, owner, lease, "priced"));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                assertThrows(AgentLedgerException.class, () -> ledger.budgets().settleBudget(owner, lease, "priced",
                        new AgentBudgetSettlement(21L, 30L, new BigDecimal("0.05")), measured(21L, 30L))).code());
        var stale = new AgentRunLease(lease.runId(), lease.owner(), lease.epoch() + 1, lease.expiresAt());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                assertThrows(AgentLedgerException.class, () -> ledger.budgets().settleBudget(owner, stale, "priced", actual, observed)).code());
        assertEquals(priced, usageSlot(jdbc, owner, lease, "priced"));

        // Deliberately remove one isolated role privilege only after the capability was verified.
        // The V2 insert/counter adjustment must roll back when the V15 insert is rejected.
        var beforeSeedFailure = counters(jdbc, owner, lease);
        jdbc.execute("REVOKE INSERT ON " + TABLE + " FROM " + quoted);
        try {
            assertThrows(org.springframework.dao.DataAccessException.class,
                    () -> ledger.budgets().reserveBudget(owner, lease, modelBudget("seed-rollback", 10, 10), null, seed));
        } finally {
            jdbc.execute("GRANT INSERT ON " + TABLE + " TO " + quoted);
        }
        assertEquals(0, countSlot(jdbc, TABLE, owner, lease, "seed-rollback"));
        assertEquals(0, countSlot(jdbc, "tianji_agent.agent_budget_reservation", owner, lease, "seed-rollback"));
        assertEquals(beforeSeedFailure, counters(jdbc, owner, lease));

        var failing = modelBudget("settle-rollback", 10, 10);
        assertTrue(ledger.budgets().reserveBudget(owner, lease, failing, null, seed).newlyCreated());
        var beforeV2 = budgetSlot(jdbc, owner, lease, failing.reservationId());
        var beforeV15 = usageSlot(jdbc, owner, lease, failing.reservationId());
        var beforeCounters = counters(jdbc, owner, lease);
        jdbc.execute("REVOKE UPDATE (measured_at) ON " + TABLE + " FROM " + quoted);
        try {
            assertThrows(org.springframework.dao.DataAccessException.class, () -> ledger.budgets().settleBudget(owner, lease,
                    failing.reservationId(), new AgentBudgetSettlement(1L, 2L, new BigDecimal("0.01")), measured(1L, 2L)));
        } finally {
            jdbc.execute("GRANT UPDATE (measured_at) ON " + TABLE + " TO " + quoted);
        }
        assertEquals(beforeV2, budgetSlot(jdbc, owner, lease, failing.reservationId()));
        assertEquals(beforeV15, usageSlot(jdbc, owner, lease, failing.reservationId()));
        assertEquals(beforeCounters, counters(jdbc, owner, lease));
        assertDoesNotThrow(new AgentStorageVerifier(db.runtime())::verifyModelUsage);

        // A verified zero measurement without a frozen tariff remains known and unpriced.
        var noPrice = new AgentBudgetService.ModelUsageSeedVO(USAGE_ROUTE, USAGE_MODEL, null);
        ledger.budgets().reserveBudget(owner, lease, modelBudget("unpriced", 10, 10), null, noPrice);
        ledger.budgets().settleBudget(owner, lease, "unpriced",
                new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO), measured(0L, 0L));
        var unpriced = usageSlot(jdbc, owner, lease, "unpriced");
        assertEquals("UNPRICED", unpriced.get("state"));
        assertEquals("PRICE_MISSING", unpriced.get("safe_reason"));
        assertEquals(0L, unpriced.get("actual_input_tokens"));
        assertNull(unpriced.get("actual_cost"));

        // A legacy overshoot still commits its original UNKNOWN budget before throwing.
        ledger.budgets().reserveBudget(owner, lease, modelBudget("over", 1, 2), null, seed);
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().settleBudget(owner, lease, "over",
                        new AgentBudgetSettlement(2L, 2L, new BigDecimal("0.05")), measured(2L, 2L))).code());
        assertEquals("UNKNOWN", budgetSlot(jdbc, owner, lease, "over").get("state"));
        assertEquals("PRICED", usageSlot(jdbc, owner, lease, "over").get("state"));

        // Closing the run leaves an unmeasured slot immutable; a matching old budget replay cannot fill it.
        ledger.budgets().reserveBudget(owner, lease, modelBudget("late", 10, 10), null, seed);
        var late = usageSlot(jdbc, owner, lease, "late");
        var current = ledger.get(owner, new GetAgentRunQuery(lease.runId()));
        ledger.finish(owner, lease, current.revision(), new AgentExecutionResult(lease.runId(), AgentRunStatusEnum.COMPLETED,
                Map.of(), null, null, null, null, "UNKNOWN"));
        assertEquals("UNKNOWN", budgetSlot(jdbc, owner, lease, "late").get("state"));
        assertEquals(AgentBudgetReservation.State.UNKNOWN, ledger.budgets().settleBudget(owner, lease, "late",
                new AgentBudgetSettlement(null, null, null),
                new AgentBudgetService.ModelUsageFinalVO(USAGE_ROUTE, USAGE_MODEL, null, null,
                        AgentBudgetService.ModelUsageUnknownReason.MISSING_USAGE)).state());
        assertEquals(late, usageSlot(jdbc, owner, lease, "late"));

        var disabled = new AgentLedgerService(db.runtime());
        assertFalse(disabled.modelUsageEnabled());
        var old = claimUsageRun(disabled, owner, "usage-off");
        assertTrue(disabled.budgets().reserveBudget(owner, old.lease(), modelBudget("legacy", 10, 10)).newlyCreated());
        assertEquals(AgentBudgetReservation.State.SETTLED, disabled.budgets().settleBudget(owner, old.lease(), "legacy",
                new AgentBudgetSettlement(2L, 3L, new BigDecimal("0.01"))).state());
        assertEquals(0, countSlot(jdbc, TABLE, owner, old.lease(), "legacy"));
    }

    private static final String USAGE_ROUTE = "fixture-route@1#digest";
    private static final String USAGE_MODEL = "fixture-model";

    private static AgentDefinitionSpecDTO modelUsageDefinition() {
        return new AgentDefinitionSpecDTO("usage-governed", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), USAGE_ROUTE, "usage-policy@1"),
                new ExecutionPolicyDTO(Duration.ofMinutes(2), Duration.ofSeconds(20), Duration.ofSeconds(10),
                        16, 0, 8192, 8192, new BigDecimal("5.00"), "USD", 4, Set.of(), null, null),
                Map.of("chatModelBeanName", "fixtureModel", "modelName", USAGE_MODEL, "rootAgentName", "fixture",
                        "agents", List.of(Map.of("name", "fixture", "instruction", "fixed fixture"))));
    }

    private static ClaimedAgentRun claimUsageRun(AgentLedgerService ledger, TrustedAgentActor owner, String command) {
        var run = ledger.start(owner, new StartAgentRunCommand("usage-governed", 1, null, command, null, null,
                new AgentInputDTO("fixture", List.of(), Map.of()), null));
        return ledger.claim(owner, new GetAgentRunQuery(run.runId()), run.revision(), "usage-fixture-worker", Duration.ofMinutes(2));
    }

    private static AgentBudgetRequest modelBudget(String slot, long input, long output) {
        return new AgentBudgetRequest(slot, AgentBudgetRequest.Kind.MODEL, null, input, output, new BigDecimal("0.20"));
    }

    private static AgentBudgetService.ModelUsageFinalVO measured(long input, long output) {
        return new AgentBudgetService.ModelUsageFinalVO(USAGE_ROUTE, USAGE_MODEL, input, output, null);
    }

    private static Map<String, Object> usageSlot(JdbcTemplate jdbc, TrustedAgentActor actor, AgentRunLease lease, String slot) {
        return jdbc.queryForMap("""
                SELECT state,lease_epoch,price_version_ref,currency,input_rate_per_million,output_rate_per_million,
                       actual_input_tokens,actual_output_tokens,actual_cost,safe_reason,final_sha256,created_at,measured_at
                FROM tianji_agent.agent_model_usage WHERE tenant_id=? AND run_id=? AND reservation_id=?
                """, actor.tenantId(), lease.runId(), slot);
    }

    private static Map<String, Object> budgetSlot(JdbcTemplate jdbc, TrustedAgentActor actor, AgentRunLease lease, String slot) {
        return jdbc.queryForMap("""
                SELECT state,lease_epoch,actual_input_tokens,actual_output_tokens,actual_cost,settled_at
                FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=? AND reservation_id=?
                """, actor.tenantId(), lease.runId(), slot);
    }

    private static Map<String, Object> counters(JdbcTemplate jdbc, TrustedAgentActor actor, AgentRunLease lease) {
        return jdbc.queryForMap("""
                SELECT model_calls_reserved,active_model_calls,input_tokens_reserved,output_tokens_reserved,estimated_cost_reserved
                FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?
                """, actor.tenantId(), lease.runId());
    }

    private static int countSlot(JdbcTemplate jdbc, String table, TrustedAgentActor actor, AgentRunLease lease, String slot) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table
                + " WHERE tenant_id=? AND run_id=? AND reservation_id=?", Integer.class, actor.tenantId(), lease.runId(), slot);
    }

    /** Keeps one verified disposable schema for the separately authorized I methods. */
    static synchronized Database modelUsageDatabase() {
        if (isolated != null) return isolated;
        String url = System.getProperty("tianji.postgres.test.url");
        String vectorVersion = System.getProperty("tianji.postgres.test.model-usage.vector.version");
        String runtimeRole = System.getProperty("tianji.postgres.test.model-usage.runtime-role");
        if (!"true".equals(System.getProperty("tianji.postgres.test.model-usage.authorized"))
                || url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || vectorVersion == null || !vectorVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")
                || runtimeRole == null || !runtimeRole.matches("tianji_it_[a-z0-9_]{1,50}")) {
            throw new IllegalArgumentException("Agent model usage I requires explicit isolated PostgreSQL and a pre-provisioned limited role");
        }
        var setup = source(url, null);
        var jdbc = new JdbcTemplate(setup);
        jdbc.setQueryTimeout(5);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database() ~ '^tianji_it_[a-z0-9_]{1,40}$'
                  AND inet_server_addr()=inet '127.0.0.1' AND NOT pg_is_in_recovery()
                  AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'
                  AND NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname='tianji_agent')
                """, Boolean.class)));
        assertEquals(vectorVersion, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_user<>? AND EXISTS (SELECT 1 FROM pg_roles WHERE rolname=?
                  AND rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication)
                """, Boolean.class, runtimeRole, runtimeRole)));
        var old = flyway(setup, "14");
        assertEquals(14, old.migrate().migrationsExecuted);
        old.validate();
        var originalHistory = jdbc.queryForList(HISTORY);
        assertEquals(14, originalHistory.size());
        var base = new AgentStorageVerifier(setup);
        assertDoesNotThrow(base::verify);
        assertThrows(AgentLedgerException.class, base::verifyModelUsage);
        var current = flyway(setup, "15");
        assertEquals(1, current.migrate().migrationsExecuted);
        current.validate();
        var fullHistory = jdbc.queryForList(HISTORY);
        assertEquals(15, fullHistory.size());
        assertEquals(originalHistory, fullHistory.subList(0, 14));
        assertEquals("V15__add_agent_model_usage.sql", fullHistory.getLast().get("script"));
        assertEquals(1512602432, fullHistory.getLast().get("checksum"));
        String role = '"' + runtimeRole + '"';
        jdbc.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + role);
        jdbc.execute("""
                GRANT SELECT ON tianji_agent.flyway_schema_history,
                  tianji_agent.agent_definition,tianji_agent.agent_definition_version,tianji_agent.agent_run,
                  tianji_agent.agent_event,tianji_agent.agent_run_command_receipt,tianji_agent.agent_budget_reservation,
                  tianji_agent.agent_session,tianji_agent.agent_session_event,tianji_agent.agent_session_scope_state,
                  tianji_agent.agent_session_lease,tianji_agent.agent_run_execution_grant TO
                """ + role);
        jdbc.execute("GRANT SELECT, INSERT ON " + TABLE + " TO " + role);
        jdbc.execute("GRANT UPDATE (actual_cost,actual_input_tokens,actual_output_tokens,final_sha256,measured_at,safe_reason,state) ON "
                + TABLE + " TO " + role);
        var runtime = source(url, runtimeRole);
        assertDoesNotThrow(new AgentStorageVerifier(runtime)::verifyModelUsage);
        isolated = new Database(setup, runtime, jdbc);
        return isolated;
    }

    private static DriverManagerDataSource source(String url, String username) {
        var source = new DriverManagerDataSource(url);
        if (username != null) source.setUsername(username);
        var props = new Properties();
        props.setProperty("connectTimeout", "5");
        props.setProperty("socketTimeout", "15");
        source.setConnectionProperties(props);
        return source;
    }

    private static Flyway flyway(DriverManagerDataSource source, String version) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").placeholders(Map.of("tianjiMemoryDimensions", "2"))
                .target(version).cleanDisabled(true).load();
    }

    record Database(DriverManagerDataSource setup, DriverManagerDataSource runtime, JdbcTemplate jdbc) { }
}
