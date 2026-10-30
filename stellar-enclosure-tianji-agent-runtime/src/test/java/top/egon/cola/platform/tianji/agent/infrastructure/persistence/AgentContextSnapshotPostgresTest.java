package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextWindowDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.ACTOR;

/** Requires an explicitly supplied empty loopback database; it never creates or starts a database process. */
@EnabledIfSystemProperty(named = "tianji.context.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentContextSnapshotPostgresTest {
    @Test
    void v9_upgrade_atomic_capture_parallel_numbering_scope_and_live_fences_are_enforced() throws Exception {
        String url = System.getProperty("tianji.context.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Context tests require an empty loopback tianji_it_ database");
        var source = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        var before = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("8").cleanDisabled(true).load();
        assertEquals(8, before.migrate().migrationsExecuted); before.validate();
        var checksums = jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        assertThrows(AgentLedgerException.class, () -> new AgentStorageVerifier(source).verifyContextSnapshots());
        var after = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).load();
        assertEquals(1, after.migrate().migrationsExecuted); after.validate();
        assertEquals(checksums, jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7','8') ORDER BY installed_rank"));
        var ledger = new AgentLedgerService(source); ledger.verifyContextSnapshotStorage();
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(2), Duration.ofSeconds(20), Duration.ofSeconds(5), 32, 0, 10_000, 1000,
                new BigDecimal("100"), "USD", 32, Set.of(), "retry-v1", "approval-v1");
        ledger.publish(ACTOR, new AgentDefinitionSpecDTO("context-test", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of()));
        var run = claim(ledger, "atomic");
        atomicFailureAndReplay(jdbc, ledger, run);
        parallelCaptureAndScope(ledger, run);
        liveFences(jdbc, ledger);
        jdbc.execute("ALTER TABLE tianji_agent.agent_context_snapshot DROP CONSTRAINT agent_context_call_positive");
        assertThrows(AgentLedgerException.class, ledger::verifyContextSnapshotStorage);
    }

    private static void atomicFailureAndReplay(JdbcTemplate jdbc, AgentLedgerService ledger, ClaimedAgentRun run) {
        jdbc.execute("CREATE FUNCTION tianji_agent.test_context_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'controlled context insert failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_context_failure AFTER INSERT ON tianji_agent.agent_context_snapshot FOR EACH ROW EXECUTE FUNCTION tianji_agent.test_context_failure()");
        var request = request("first"); var metadata = metadata(ledger, run, request.reservationId(), 100, "f".repeat(64));
        assertThrows(RuntimeException.class, () -> ledger.budgets().reserveBudget(ACTOR, run.lease(), request, metadata));
        assertEquals(0, jdbc.queryForObject("SELECT model_calls_reserved FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), run.run().runId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), run.run().runId()));
        assertTrue(ledger.contextSnapshots(ACTOR, new AgentContextSnapshotsQuery(run.run().runId(), 0, 5)).items().isEmpty());
        jdbc.execute("DROP TRIGGER test_context_failure ON tianji_agent.agent_context_snapshot");
        assertTrue(ledger.budgets().reserveBudget(ACTOR, run.lease(), request, metadata).newlyCreated());
        assertFalse(ledger.budgets().reserveBudget(ACTOR, run.lease(), request, metadata).newlyCreated());
        assertEquals(1, ledger.contextSnapshots(ACTOR, new AgentContextSnapshotsQuery(run.run().runId(), 0, 5)).items().size());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(ACTOR, run.lease(), request("window-drift"), metadata(ledger, run, "window-drift", 101, "f".repeat(64)))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(ACTOR, run.lease(), request("source-drift"), metadata(ledger, run, "source-drift", 100, "0".repeat(64)))).code());
        assertEquals(1, jdbc.queryForObject("SELECT model_calls_reserved FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?", Integer.class, ACTOR.tenantId(), run.run().runId()));
    }

    private static void parallelCaptureAndScope(AgentLedgerService ledger, ClaimedAgentRun run) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 20; i++) {
                String call = "parallel-" + i;
                futures.add(executor.submit(() -> ledger.budgets().reserveBudget(ACTOR, run.lease(), request(call), metadata(ledger, run, call, 100, "f".repeat(64)))));
            }
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        List<Integer> numbers = new ArrayList<>(); int cursor = 0;
        do {
            var page = ledger.contextSnapshots(ACTOR, new AgentContextSnapshotsQuery(run.run().runId(), cursor, 5));
            page.items().forEach(value -> numbers.add(value.modelCallNumber()));
            if (page.nextAfterModelCall() == null) break;
            cursor = page.nextAfterModelCall();
        } while (true);
        assertEquals(java.util.stream.IntStream.rangeClosed(1, 21).boxed().toList(), numbers);
        for (var other : List.of(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://other-idp.example/20"))) {
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                    () -> ledger.contextSnapshots(other, new AgentContextSnapshotsQuery(run.run().runId(), 0, 5))).code());
        }
    }

    private static void liveFences(JdbcTemplate jdbc, AgentLedgerService ledger) {
        var expired = claim(ledger, "expired"); var expiredMetadata = metadata(ledger, expired, "expired-call", 100, "f".repeat(64));
        jdbc.update("UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?", ACTOR.tenantId(), expired.run().runId());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(ACTOR, expired.lease(), request("expired-call"), expiredMetadata)).code());
        var cancelled = claim(ledger, "cancelled"); var cancelledMetadata = metadata(ledger, cancelled, "cancelled-call", 100, "f".repeat(64));
        ledger.cancel(ACTOR, new CancelAgentRunCommand(cancelled.run().runId(), "cancel-context", cancelled.run().revision(), "controlled context cancellation"));
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(ACTOR, cancelled.lease(), request("cancelled-call"), cancelledMetadata)).code());
        assertTrue(ledger.contextSnapshots(ACTOR, new AgentContextSnapshotsQuery(expired.run().runId(), 0, 5)).items().isEmpty());
        assertTrue(ledger.contextSnapshots(ACTOR, new AgentContextSnapshotsQuery(cancelled.run().runId(), 0, 5)).items().isEmpty());
    }

    private static ClaimedAgentRun claim(AgentLedgerService ledger, String key) {
        var run = ledger.start(ACTOR, new StartAgentRunCommand("context-test", 1, null, key, null, "context-trace", new AgentInputDTO("private-context-input", null, null), null));
        return ledger.claim(ACTOR, new GetAgentRunQuery(run.runId()), run.revision(), "context-worker", Duration.ofSeconds(60));
    }
    private static AgentBudgetRequest request(String call) { return new AgentBudgetRequest(call, AgentBudgetRequest.Kind.MODEL, null, 20, 10, new BigDecimal("0.1")); }
    private static AgentContextMetadataDTO metadata(AgentLedgerService ledger, ClaimedAgentRun run, String call, long window, String sourcePolicy) {
        var material = ledger.executionMaterial(ACTOR, new GetAgentRunQuery(run.run().runId())); var authorization = material.authorization();
        return new AgentContextMetadataDTO(1, AgentContextMetadataDTO.PROJECTION_V1, ACTOR.identityFingerprint(), run.run().runId(), call, run.lease().epoch(),
                "writer", "invocation", "session", 0, "d".repeat(64), null, authorization.definitionSha256(), authorization.requestSha256(), authorization.snapshotSha256(),
                "e".repeat(64), sourcePolicy, "0".repeat(64), "1".repeat(64), new AgentContextWindowDTO("model", "route", "policy", window, 5), 20, 10,
                List.of(new AgentContextSourceDTO(AgentContextSourceKindEnum.DEFINITION, AgentContextRoleEnum.SYSTEM, "context-test", "1", "published-version",
                        authorization.definitionSha256(), "e".repeat(64), 10, null)), List.of());
    }
}
