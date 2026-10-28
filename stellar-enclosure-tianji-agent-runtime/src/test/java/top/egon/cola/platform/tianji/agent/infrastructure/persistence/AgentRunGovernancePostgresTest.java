package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

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
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentRunGovernancePostgresTest {
    private static AgentLedgerService ledger;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        var dataSource = PostgresTestDatabase.dataSource();
        ledger = new AgentLedgerService(dataSource);
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void queued_cancellation_is_idempotent_and_scoped_to_the_authenticated_actor() throws Exception {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        CancelAgentRunCommand command = new CancelAgentRunCommand(query.runId(), "cancel-1", 0, "requested");
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<CancelAgentRunResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < 30; i++) {
                futures.add(pool.submit(() -> { gate.await(); return ledger.cancel(owner, command); }));
            }
            gate.countDown();
            CancelAgentRunResult first = futures.getFirst().get(30, TimeUnit.SECONDS);
            for (Future<CancelAgentRunResult> future : futures) assertEquals(first, future.get(30, TimeUnit.SECONDS));
            assertTrue(first.accepted());
            assertEquals(CANCELLED, first.status());
        }
        AgentLedgerException conflict = assertThrows(AgentLedgerException.class, () -> ledger.cancel(owner,
                new CancelAgentRunCommand(query.runId(), "cancel-1", 0, "different")));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, conflict.code());
        TrustedAgentActor stranger = new TrustedAgentActor(owner.tenantId(), USER, "other");
        assertThrows(AgentLedgerException.class, () -> ledger.cancel(stranger, command));
        assertEquals(2, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20)).size());
        assertEquals("NONE", ledger.getResult(owner, query).orElseThrow().sideEffectOutcome());
    }

    @Test
    void running_cancellation_fences_completion_and_preserves_the_original_receipt() {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        ClaimedAgentRun claim = ledger.claim(owner, query, 0, "worker-1", Duration.ofSeconds(10));
        CancelAgentRunCommand command = new CancelAgentRunCommand(query.runId(), "cancel-1", 1, "stop");
        CancelAgentRunResult accepted = ledger.cancel(owner, command);
        assertTrue(accepted.accepted());
        assertEquals(RUNNING, accepted.status());
        assertEquals(LeaseHeartbeat.CANCEL_REQUESTED, ledger.heartbeat(owner, claim.lease(), Duration.ofSeconds(10)));
        assertThrows(AgentLedgerException.class, () -> ledger.finish(owner, claim.lease(), accepted.revision(),
                result(query.runId(), COMPLETED)));
        AgentRunDTO cancelled = ledger.finish(owner, claim.lease(), accepted.revision(), result(query.runId(), CANCELLED));
        assertEquals(CANCELLED, cancelled.status());
        assertEquals(accepted, ledger.cancel(owner, command));
        assertThrows(AgentLedgerException.class, () -> ledger.appendEvent(owner, claim.lease(), cancelled.revision(),
                "TEXT_DELTA", "writer", Map.of("text", "late")));
        assertEquals(4, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20)).size());
    }

    @Test
    void expired_lease_is_fenced_and_suspended_without_automatic_retry() {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        ClaimedAgentRun claim = ledger.claim(owner, query, 0, "worker-1", Duration.ofSeconds(10));
        jdbc.update("""
                UPDATE tianji_agent.agent_run SET lease_expires_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, owner.tenantId(), query.runId());
        assertEquals(LeaseHeartbeat.LEASE_LOST, ledger.heartbeat(owner, claim.lease(), Duration.ofSeconds(10)));
        AgentRunDTO suspended = ledger.suspendExpiredLease(owner, query, claim.run().revision());
        assertEquals(SUSPENDED, suspended.status());
        assertEquals("LEASE_EXPIRED", suspended.waitReason());
        assertThrows(AgentLedgerException.class,
                () -> ledger.finish(owner, claim.lease(), suspended.revision(), result(query.runId(), COMPLETED)));
        assertEquals(3, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20)).size());
    }

    @Test
    void authorization_suspension_requires_the_live_owner_and_marks_outstanding_usage_unknown() {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        ClaimedAgentRun claim = ledger.claim(owner, query, 0, "worker-1", Duration.ofSeconds(10));
        ledger.budgets().reserveBudget(owner, claim.lease(), new AgentBudgetRequest("model-call",
                AgentBudgetRequest.Kind.MODEL, null, 10, 10, new java.math.BigDecimal("0.10")));
        var stale = new AgentRunLease(query.runId(), claim.lease().owner(), claim.lease().epoch() + 1,
                claim.lease().expiresAt());
        assertThrows(AgentLedgerException.class, () -> ledger.suspendForAuthorization(owner, stale,
                AgentRunAuthorization.Decision.UNAVAILABLE));
        assertThrows(AgentLedgerException.class, () -> ledger.suspendForAuthorization(owner, claim.lease(),
                AgentRunAuthorization.Decision.ALLOW));
        assertEquals(RUNNING, ledger.get(owner, query).status());
        AgentRunDTO suspended = ledger.suspendForAuthorization(owner, claim.lease(), AgentRunAuthorization.Decision.UNAVAILABLE);
        assertEquals(SUSPENDED, suspended.status());
        assertEquals("AUTHORIZATION_UNAVAILABLE", suspended.waitReason());
        assertEquals("UNKNOWN", jdbc.queryForObject("""
                SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id = ? AND run_id = ?
                """, String.class, owner.tenantId(), query.runId()));
        assertEquals(LeaseHeartbeat.LEASE_LOST, ledger.heartbeat(owner, claim.lease(), Duration.ofSeconds(10)));
        assertThrows(AgentLedgerException.class, () -> ledger.suspendForAuthorization(owner, claim.lease(),
                AgentRunAuthorization.Decision.DENY));
        assertThrows(AgentLedgerException.class, () -> ledger.finish(owner, claim.lease(), suspended.revision(),
                result(query.runId(), COMPLETED)));
        assertEquals(3, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20)).size());
    }

    @Test
    void database_deadline_expires_and_fences_a_running_owner() {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        ClaimedAgentRun claim = ledger.claim(owner, query, 0, "worker-1", Duration.ofSeconds(10));
        jdbc.update("""
                UPDATE tianji_agent.agent_run SET deadline_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, owner.tenantId(), query.runId());
        assertEquals(LeaseHeartbeat.DEADLINE_EXPIRED, ledger.heartbeat(owner, claim.lease(), Duration.ofSeconds(10)));
        AgentRunDTO expired = ledger.expireDeadline(owner, query, claim.run().revision());
        assertEquals(TIMED_OUT, expired.status());
        assertEquals("UNKNOWN", ledger.getResult(owner, query).orElseThrow().sideEffectOutcome());
        assertThrows(AgentLedgerException.class,
                () -> ledger.finish(owner, claim.lease(), expired.revision(), result(query.runId(), COMPLETED)));
    }

    @Test
    void reconciliation_finds_due_runs_and_never_requeues_an_expired_lease() {
        TrustedAgentActor staleOwner = actor();
        GetAgentRunQuery stale = queued(staleOwner);
        ledger.claim(staleOwner, stale, 0, "worker", Duration.ofSeconds(10));
        jdbc.update("""
                UPDATE tianji_agent.agent_run SET lease_expires_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, staleOwner.tenantId(), stale.runId());
        TrustedAgentActor timedOwner = actor();
        GetAgentRunQuery timed = queued(timedOwner);
        jdbc.update("""
                UPDATE tianji_agent.agent_run SET deadline_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, timedOwner.tenantId(), timed.runId());

        assertTrue(ledger.reconcileDue(100) >= 2);
        assertEquals(SUSPENDED, ledger.get(staleOwner, stale).status());
        assertEquals(TIMED_OUT, ledger.get(timedOwner, timed).status());
        ledger.reconcileDue(100);
        assertEquals(3, ledger.events(staleOwner, new AgentEventsQuery(stale.runId(), 0, 10)).size());
        assertEquals(2, ledger.events(timedOwner, new AgentEventsQuery(timed.runId(), 0, 10)).size());
    }

    @Test
    void queue_discovery_excludes_cancelled_claimed_and_expired_runs() {
        TrustedAgentActor pendingOwner = actor();
        GetAgentRunQuery pending = queued(pendingOwner);
        TrustedAgentActor cancelledOwner = actor();
        GetAgentRunQuery cancelled = queued(cancelledOwner);
        ledger.cancel(cancelledOwner, new CancelAgentRunCommand(cancelled.runId(), "cancel", 0, "stop"));
        TrustedAgentActor claimedOwner = actor();
        GetAgentRunQuery claimed = queued(claimedOwner);
        ledger.claim(claimedOwner, claimed, 0, "worker", Duration.ofSeconds(10));
        TrustedAgentActor expiredOwner = actor();
        GetAgentRunQuery expired = queued(expiredOwner);
        jdbc.update("""
                UPDATE tianji_agent.agent_run SET deadline_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, expiredOwner.tenantId(), expired.runId());

        var discovered = ledger.pendingRuns(1000);
        assertTrue(discovered.stream().anyMatch(run -> run.actor().equals(pendingOwner) && run.query().equals(pending)));
        assertFalse(discovered.stream().anyMatch(run -> Set.of(cancelled.runId(), claimed.runId(), expired.runId())
                .contains(run.query().runId())));
    }

    @RepeatedTest(10)
    void cancellation_and_completion_have_one_terminal_winner() throws Exception {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        ClaimedAgentRun claim = ledger.claim(owner, query, 0, "worker-1", Duration.ofSeconds(10));
        CancelAgentRunCommand cancel = new CancelAgentRunCommand(query.runId(), "cancel-race", 1, "stop");
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<Object> cancelResult = pool.submit(() -> attempt(gate, () -> ledger.cancel(owner, cancel)));
            Future<Object> finishResult = pool.submit(() -> attempt(gate,
                    () -> ledger.finish(owner, claim.lease(), 1, result(query.runId(), COMPLETED))));
            gate.countDown();
            cancelResult.get(20, TimeUnit.SECONDS);
            finishResult.get(20, TimeUnit.SECONDS);
        }
        AgentRunDTO current = ledger.get(owner, query);
        if (current.status() == RUNNING) {
            assertTrue(current.cancelRequested());
            current = ledger.finish(owner, claim.lease(), current.revision(), result(query.runId(), CANCELLED));
        }
        assertTrue(current.status() == COMPLETED || current.status() == CANCELLED);
        assertEquals(current.status(), ledger.getResult(owner, query).orElseThrow().status());
        long terminalEvents = ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20)).stream()
                .filter(event -> event.payload().get("status") != null
                        && Set.of("COMPLETED", "CANCELLED").contains(event.payload().get("status"))).count();
        assertEquals(1, terminalEvents);
    }

    @RepeatedTest(10)
    void queued_authorization_denial_and_cancellation_preserve_one_consistent_state() throws Exception {
        TrustedAgentActor owner = actor();
        GetAgentRunQuery query = queued(owner);
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var denied = pool.submit(() -> attempt(gate, () -> ledger.suspendQueuedForAuthorization(owner, query, 0)));
            var cancelled = pool.submit(() -> attempt(gate, () -> ledger.cancel(owner,
                    new CancelAgentRunCommand(query.runId(), "cancel-denied", 0, "stop"))));
            gate.countDown();
            denied.get(20, TimeUnit.SECONDS);
            cancelled.get(20, TimeUnit.SECONDS);
        }
        AgentRunDTO current = ledger.get(owner, query);
        assertTrue(current.status() == SUSPENDED || current.status() == CANCELLED);
        if (current.status() == SUSPENDED) {
            assertEquals("AUTHORIZATION_DENIED", current.waitReason());
            ledger.cancel(owner, new CancelAgentRunCommand(query.runId(), "cancel-denied", current.revision(), "stop"));
        }
        assertEquals(CANCELLED, ledger.get(owner, query).status());
        assertEquals(0, ledger.get(owner, query).attempt());
        var events = ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 20));
        assertEquals(1, events.stream().filter(event -> "CANCELLED".equals(event.payload().get("status"))).count());
        assertTrue(events.stream().filter(event -> "SUSPENDED".equals(event.payload().get("status"))).count() <= 1);
    }

    private static Object attempt(CountDownLatch gate, java.util.concurrent.Callable<?> action) throws Exception {
        gate.await();
        try { return action.call(); } catch (AgentLedgerException failure) { return failure; }
    }

    private static TrustedAgentActor actor() {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, "fixture-user");
    }

    private static GetAgentRunQuery queued(TrustedAgentActor actor) {
        ledger.publish(actor, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand("review", 1, null, "key", null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null));
        return new GetAgentRunQuery(run.runId());
    }

    private static AgentExecutionResult result(String runId, top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum status) {
        return new AgentExecutionResult(runId, status, Map.of(), null, null, null, null, "UNKNOWN");
    }
}
