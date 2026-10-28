package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.autoconfigure.AgentReconciliationProperties;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.QUEUED;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.SUSPENDED;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.TIMED_OUT;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(60)
class AgentReconcilerPostgresTest {
    @Test
    void two_schedulers_reconcile_expired_runs_once_without_dispatching_them() throws Exception {
        var source = PostgresTestDatabase.dataSource();
        var ledger = new AgentLedgerService(source);
        var jdbc = new JdbcTemplate(source);
        TrustedAgentActor owner = new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, "reconciler");
        ledger.publish(owner, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        AgentRunDTO queued = start(ledger, owner, "expired-queue");
        AgentRunDTO running = start(ledger, owner, "expired-lease");
        AgentRunDTO untouched = start(ledger, owner, "not-due");
        ledger.claim(owner, new GetAgentRunQuery(running.runId()), running.revision(), "worker", Duration.ofSeconds(30));
        jdbc.update("UPDATE tianji_agent.agent_run SET deadline_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                owner.tenantId(), queued.runId());
        jdbc.update("UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                owner.tenantId(), running.runId());

        AtomicReference<Runnable> firstTask = new AtomicReference<>();
        AtomicReference<Runnable> secondTask = new AtomicReference<>();
        var first = reconciler(ledger, firstTask);
        var second = reconciler(ledger, secondTask);
        first.start();
        second.start();
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { gate.await(); firstTask.get().run(); return null; });
            var two = pool.submit(() -> { gate.await(); secondTask.get().run(); return null; });
            gate.countDown();
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            first.stop();
            second.stop();
        }
        assertEquals(TIMED_OUT, ledger.get(owner, new GetAgentRunQuery(queued.runId())).status());
        assertEquals(SUSPENDED, ledger.get(owner, new GetAgentRunQuery(running.runId())).status());
        assertEquals(QUEUED, ledger.get(owner, new GetAgentRunQuery(untouched.runId())).status());
        assertEquals(2, ledger.events(owner, new AgentEventsQuery(queued.runId(), 0, 10)).size());
        assertEquals(3, ledger.events(owner, new AgentEventsQuery(running.runId(), 0, 10)).size());
        assertEquals(1, ledger.events(owner, new AgentEventsQuery(untouched.runId(), 0, 10)).size());
    }

    private static AgentRunDTO start(AgentLedgerService ledger, TrustedAgentActor owner, String key) {
        return ledger.start(owner, new StartAgentRunCommand("review", 1, null, key, null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null));
    }

    private static AgentRunReconciler reconciler(AgentLedgerService ledger, AtomicReference<Runnable> task) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return future;
        });
        var properties = new AgentReconciliationProperties();
        properties.setBatchSize(1_000);
        return new AgentRunReconciler(ledger, scheduler, properties);
    }
}
