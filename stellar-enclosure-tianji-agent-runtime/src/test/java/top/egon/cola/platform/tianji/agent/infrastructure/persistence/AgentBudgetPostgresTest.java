package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest.Kind.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentBudgetPostgresTest {
    private static AgentLedgerService ledger;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        var dataSource = PostgresTestDatabase.dataSource();
        ledger = new AgentLedgerService(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void concurrent_reservations_cannot_oversell_model_tokens_or_call_count() throws Exception {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of(), 10);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 60; i++) {
                String id = "call-" + i;
                futures.add(pool.submit(() -> {
                    gate.await();
                    try { return ledger.budgets().reserveBudget(actor, claim.lease(), model(id, 100, 100, "0.20")); }
                    catch (AgentLedgerException rejected) { return rejected; }
                }));
            }
            gate.countDown();
            int accepted = 0;
            for (Future<Object> future : futures) {
                Object result = future.get(30, TimeUnit.SECONDS);
                if (result instanceof AgentBudgetReservation) accepted++;
                else assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, ((AgentLedgerException) result).code());
            }
            assertEquals(5, accepted);
        }
        Map<String, Object> row = counters(actor, claim);
        assertEquals(5, ((Number) row.get("model_calls_reserved")).intValue());
        assertEquals(500L, ((Number) row.get("input_tokens_reserved")).longValue());
        assertEquals(500L, ((Number) row.get("output_tokens_reserved")).longValue());
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_budget_reservation WHERE tenant_id = ?",
                Integer.class, actor.tenantId()));
    }

    @Test
    void reservation_and_settlement_are_idempotent_and_refund_only_verified_unused_amounts() {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of());
        AgentBudgetRequest request = model("call", 100, 100, "0.20");
        assertTrue(ledger.budgets().reserveBudget(actor, claim.lease(), request).newlyCreated());
        assertFalse(ledger.budgets().reserveBudget(actor, claim.lease(), request).newlyCreated());
        AgentLedgerException collision = assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(actor, claim.lease(), model("call", 101, 100, "0.20")));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, collision.code());
        AgentBudgetSettlement actual = new AgentBudgetSettlement(20L, 30L, new BigDecimal("0.05"));
        assertEquals(AgentBudgetReservation.State.SETTLED,
                ledger.budgets().settleBudget(actor, claim.lease(), "call", actual).state());
        assertEquals(AgentBudgetReservation.State.SETTLED,
                ledger.budgets().settleBudget(actor, claim.lease(), "call", actual).state());
        assertThrows(AgentLedgerException.class, () -> ledger.budgets().settleBudget(actor, claim.lease(), "call",
                new AgentBudgetSettlement(21L, 30L, new BigDecimal("0.05"))));
        Map<String, Object> row = counters(actor, claim);
        assertEquals(20L, ((Number) row.get("input_tokens_reserved")).longValue());
        assertEquals(30L, ((Number) row.get("output_tokens_reserved")).longValue());
        assertEquals(0, ((BigDecimal) row.get("estimated_cost_reserved")).compareTo(new BigDecimal("0.05")));
    }

    @Test
    void unknown_or_excess_usage_never_frees_an_unverified_budget() {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of());
        ledger.budgets().reserveBudget(actor, claim.lease(), model("unknown", 100, 100, "0.20"));
        assertEquals(AgentBudgetReservation.State.UNKNOWN,
                ledger.budgets().settleBudget(actor, claim.lease(), "unknown", new AgentBudgetSettlement(null, null, null)).state());
        assertEquals(100L, ((Number) counters(actor, claim).get("output_tokens_reserved")).longValue());
        ledger.budgets().reserveBudget(actor, claim.lease(), model("over", 100, 100, "0.20"));
        AgentLedgerException exceeded = assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().settleBudget(actor, claim.lease(), "over", new AgentBudgetSettlement(100L, 600L, null)));
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, exceeded.code());
        assertEquals(700L, ((Number) counters(actor, claim).get("output_tokens_reserved")).longValue());
        assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(actor, claim.lease(), model("next", 1, 1, "0.01")));
    }

    @Test
    void tool_reservations_enforce_allowed_version_and_call_limit() {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of("read@1"));
        AgentBudgetRequest forbidden = new AgentBudgetRequest("bad", TOOL, "write@1", 0, 0, BigDecimal.ZERO);
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED,
                assertThrows(AgentLedgerException.class, () -> ledger.budgets().reserveBudget(actor, claim.lease(), forbidden)).code());
        ledger.budgets().reserveBudget(actor, claim.lease(), new AgentBudgetRequest("one", TOOL, "read@1", 0, 0, BigDecimal.ZERO));
        ledger.budgets().reserveBudget(actor, claim.lease(), new AgentBudgetRequest("two", TOOL, "read@1", 0, 0, BigDecimal.ZERO));
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(actor, claim.lease(),
                        new AgentBudgetRequest("three", TOOL, "read@1", 0, 0, BigDecimal.ZERO))).code());
    }

    @Test
    void parallel_limit_is_released_only_when_a_call_settles() {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of(), 2);
        ledger.budgets().reserveBudget(actor, claim.lease(), model("one", 10, 20, "0.10"));
        ledger.budgets().reserveBudget(actor, claim.lease(), model("two", 10, 20, "0.10"));
        assertThrows(AgentLedgerException.class,
                () -> ledger.budgets().reserveBudget(actor, claim.lease(), model("three", 10, 20, "0.10")));
        ledger.budgets().settleBudget(actor, claim.lease(), "one", new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05")));
        assertTrue(ledger.budgets().reserveBudget(actor, claim.lease(), model("three", 10, 20, "0.10")).newlyCreated());
    }

    @RepeatedTest(10)
    void settlement_and_terminal_commit_do_not_deadlock_or_refund_unknown_work() throws Exception {
        TrustedAgentActor actor = actor();
        ClaimedAgentRun claim = claim(actor, Set.of());
        ledger.budgets().reserveBudget(actor, claim.lease(), model("race", 100, 100, "0.20"));
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> settlement = pool.submit(() -> {
                gate.await();
                try { return ledger.budgets().settleBudget(actor, claim.lease(), "race",
                        new AgentBudgetSettlement(20L, 30L, new BigDecimal("0.05"))); }
                catch (AgentLedgerException failure) { return failure; }
            });
            Future<AgentRunDTO> completion = pool.submit(() -> {
                gate.await();
                return ledger.finish(actor, claim.lease(), claim.run().revision(),
                        new AgentExecutionResult(claim.run().runId(),
                                top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED,
                                Map.of(), null, null, null, null, "UNKNOWN"));
            });
            gate.countDown();
            Object settlementResult = settlement.get(10, TimeUnit.SECONDS);
            assertNotNull(settlementResult);
            assertEquals(top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED,
                    completion.get(10, TimeUnit.SECONDS).status());
        }
        String state = jdbc.queryForObject("""
                SELECT state FROM tianji_agent.agent_budget_reservation
                WHERE tenant_id = ? AND run_id = ? AND reservation_id = 'race'
                """, String.class, actor.tenantId(), claim.run().runId());
        assertTrue(Set.of("SETTLED", "UNKNOWN").contains(state));
        if (state.equals("UNKNOWN")) {
            assertEquals(100L, ((Number) counters(actor, claim).get("output_tokens_reserved")).longValue());
        }
    }

    private static AgentBudgetRequest model(String id, long input, long output, String cost) {
        return new AgentBudgetRequest(id, MODEL, null, input, output, new BigDecimal(cost));
    }

    private static TrustedAgentActor actor() {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, "fixture-user");
    }

    private static ClaimedAgentRun claim(TrustedAgentActor actor, Set<String> tools) {
        return claim(actor, tools, 2);
    }

    private static ClaimedAgentRun claim(TrustedAgentActor actor, Set<String> tools, int maxParallelism) {
        AgentDefinitionSpecDTO base = AgentJsonCodecTest.spec(1, Map.of(), tools);
        ExecutionPolicyDTO policy = base.policy();
        ExecutionPolicyDTO adjusted = new ExecutionPolicyDTO(policy.totalTimeout(), policy.modelIdleTimeout(),
                policy.toolTimeout(), policy.maxModelCalls(), policy.maxToolCalls(), policy.maxInputTokens(),
                policy.maxOutputTokens(), policy.maxEstimatedCost(), policy.currency(), maxParallelism,
                policy.allowedToolVersions(), policy.retryPolicyRef(), policy.approvalPolicyRef());
        ledger.publish(actor, new AgentDefinitionSpecDTO(base.definitionKey(), base.version(), base.executionMode(),
                base.codeRef(), base.inputSchemaVersion(), base.outputSchemaVersion(), base.dependencies(),
                adjusted, base.configuration()));
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand("review", 1, null, "key", null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null));
        return ledger.claim(actor, new GetAgentRunQuery(run.runId()), 0, "worker", Duration.ofSeconds(10));
    }

    private static Map<String, Object> counters(TrustedAgentActor actor, ClaimedAgentRun claim) {
        return jdbc.queryForMap("""
                SELECT model_calls_reserved, input_tokens_reserved, output_tokens_reserved, estimated_cost_reserved
                FROM tianji_agent.agent_run WHERE tenant_id = ? AND run_id = ?
                """, actor.tenantId(), claim.run().runId());
    }
}
