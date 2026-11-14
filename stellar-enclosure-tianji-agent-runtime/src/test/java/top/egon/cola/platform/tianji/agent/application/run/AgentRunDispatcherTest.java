package top.egon.cola.platform.tianji.agent.application.run;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.dtp.executor.virtual.BoundedVirtualThreadExecutor;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.PendingAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RunProfileEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;

class AgentRunDispatcherTest {
    @Test
    void discovers_queued_runs_using_the_persisted_owner() throws Exception {
        AdkAgentRunExecutor worker = mock(AdkAgentRunExecutor.class);
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TrustedAgentActor owner = new TrustedAgentActor(42, USER, "owner");
        GetAgentRunQuery query = new GetAgentRunQuery("run-1");
        when(ledger.pendingRuns(5)).thenReturn(List.of(new PendingAgentRun(owner, query)));
        when(ledger.verifyPersistedProfile(owner, query)).thenReturn(RunProfileEnum.LEGACY_V1);
        when(worker.executeQueued(owner, query)).thenReturn(result("run-1"));
        try (BoundedVirtualThreadExecutor pool = new BoundedVirtualThreadExecutor("agent-test", 1);
             AgentRunDispatcher dispatcher = new AgentRunDispatcher(pool, worker, ledger)) {
            assertEquals("run-1", dispatcher.dispatchQueued(5).getFirst().toCompletableFuture()
                    .get(5, TimeUnit.SECONDS).runId());
            verify(worker).executeQueued(owner, query);
        }
    }

    @Test
    void deduplicates_local_dispatch_and_rejects_excess_capacity_without_cancelling_the_run() throws Exception {
        AdkAgentRunExecutor worker = mock(AdkAgentRunExecutor.class);
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        when(worker.executeQueued(any(), any())).thenAnswer(invocation -> {
            executions.incrementAndGet();
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture was not released");
            return result("run-1");
        });
        try (BoundedVirtualThreadExecutor pool = new BoundedVirtualThreadExecutor("agent-test", 1);
             AgentRunDispatcher dispatcher = new AgentRunDispatcher(pool, worker, ledger)) {
            TrustedAgentActor actor = new TrustedAgentActor(1, USER, "alice");
            GetAgentRunQuery first = new GetAgentRunQuery("run-1");
            when(ledger.verifyPersistedProfile(actor, first)).thenReturn(RunProfileEnum.LEGACY_V1);
            var waitA = dispatcher.dispatch(actor, first).toCompletableFuture();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var waitB = dispatcher.dispatch(actor, first).toCompletableFuture();
            waitA.cancel(true);
            var rejected = dispatcher.dispatch(actor, new GetAgentRunQuery("run-2")).toCompletableFuture();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, ((AgentLedgerException) failure.getCause()).code());
            release.countDown();
            assertEquals("run-1", waitB.get(5, TimeUnit.SECONDS).runId());
            assertEquals(1, executions.get());
            dispatcher.close();
            assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            assertThrows(AgentLedgerException.class, () -> dispatcher.dispatch(actor, first));
        } finally {
            release.countDown();
        }
    }

    private static AgentRunDTO result(String id) {
        return new AgentRunDTO(id, "review", 1, null, AgentRunStatusEnum.COMPLETED, null, false,
                1, 2, 3, Instant.now().plusSeconds(10), Instant.now(), Instant.now());
    }
}
