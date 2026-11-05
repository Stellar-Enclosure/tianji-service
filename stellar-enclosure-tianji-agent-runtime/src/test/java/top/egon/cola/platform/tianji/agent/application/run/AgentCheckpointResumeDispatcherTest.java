package top.egon.cola.platform.tianji.agent.application.run;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.TakeoverResult;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;

class AgentCheckpointResumeDispatcherTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(7, USER, "fixture", "a".repeat(64));
    private static final ResumeCommand COMMAND = new ResumeCommand("run-1", "resume-1", 3);
    private static final ResumeCommand OTHER = new ResumeCommand("run-1", "resume-2", 4);
    private static final Acknowledgement ACK = new Acknowledgement("run-1", AgentRunStatusEnum.RUNNING, 1, 5);

    private final DataSource source = mock(DataSource.class);
    private final ResumeAuthorization authority = mock(ResumeAuthorization.class);
    private final AdkAgentRunExecutor worker = mock(AdkAgentRunExecutor.class);
    private final AgentCheckpointTakeoverService takeover = mock(AgentCheckpointTakeoverService.class);

    @Test
    void rejects_wrong_source_or_authority_before_claiming() {
        DataSource otherSource = mock(DataSource.class);
        ResumeAuthorization otherAuthority = mock(ResumeAuthorization.class);
        bindCollaborators();
        when(takeover.usesSourceAndAuthority(otherSource, authority)).thenReturn(true);
        try (ThreadPoolExecutor pool = directHandoffPool()) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentCheckpointResumeDispatcher(pool, worker, takeover, otherSource, authority));
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentCheckpointResumeDispatcher(pool, worker, takeover, source, otherAuthority));
            verify(takeover, never()).takeOver(any(), any(), anyString());
            verify(worker, never()).executeResume(any(), any());
            assertEquals(0, pool.getTaskCount());
        }
    }

    @Test
    void rejects_an_unbounded_pool_and_a_caller_runs_policy() {
        bindCollaborators();
        try (var unbounded = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                     new LinkedBlockingQueue<>(), new ThreadPoolExecutor.AbortPolicy());
             var callerRuns = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                     new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.CallerRunsPolicy())) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentCheckpointResumeDispatcher(unbounded, worker, takeover, source, authority));
            assertThrows(IllegalArgumentException.class,
                    () -> new AgentCheckpointResumeDispatcher(callerRuns, worker, takeover, source, authority));
        }
    }

    @Test
    void rejects_without_taking_over_when_no_host_slot_is_available() throws Exception {
        var running = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = directHandoffPool();
             var dispatcher = dispatcher(pool)) {
            pool.execute(() -> {
                running.countDown();
                awaitRelease(release);
            });
            try {
                assertTrue(running.await(5, TimeUnit.SECONDS));
                var rejected = dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture();
                assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, failureCode(rejected));
                verify(takeover, never()).takeOver(any(), any(), anyString());
                verify(worker, never()).executeResume(any(), any());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void rejects_and_removes_a_task_when_the_thread_factory_rejects_after_enqueue() throws Exception {
        var pool = new ThreadPoolExecutor(0, 1, 1, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1),
                ignored -> { throw new RejectedExecutionException("fixture thread factory rejected"); },
                new ThreadPoolExecutor.AbortPolicy());
        try (var dispatcher = dispatcher(pool)) {
            assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED,
                    failureCode(dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture()));
            assertTrue(pool.getQueue().isEmpty());
            verify(takeover, never()).takeOver(any(), any(), anyString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejects_ambient_transactions_and_closed_admission_before_submitting() {
        try (var pool = directHandoffPool();
             var dispatcher = dispatcher(pool)) {
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(OWNER, COMMAND));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.bindResource(source, new Object());
            try {
                assertThrows(IllegalStateException.class, () -> dispatcher.dispatch(OWNER, COMMAND));
            } finally {
                TransactionSynchronizationManager.unbindResource(source);
            }
            dispatcher.close();
            var failure = assertThrows(AgentLedgerException.class, () -> dispatcher.dispatch(OWNER, COMMAND));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertEquals(0, pool.getTaskCount());
            verify(takeover, never()).takeOver(any(), any(), anyString());
        }
    }

    @Test
    void takes_over_then_invokes_the_worker_once_in_the_same_occupied_task() throws Exception {
        var result = result(ACK);
        var claimThread = new AtomicReference<Thread>();
        when(takeover.takeOver(eq(OWNER), eq(COMMAND), anyString())).thenAnswer(invocation -> {
            claimThread.set(Thread.currentThread());
            return result;
        });
        when(worker.executeResume(result, authority)).thenAnswer(invocation -> {
            assertSame(claimThread.get(), Thread.currentThread());
            return Optional.empty();
        });
        try (var pool = directHandoffPool();
             var dispatcher = dispatcher(pool)) {
            var waiting = dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture();
            assertEquals(ACK, waiting.get(5, TimeUnit.SECONDS));
            dispatcher.close();
            assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            assertEquals(1, pool.getTaskCount());
            var order = inOrder(takeover, worker);
            order.verify(takeover).takeOver(eq(OWNER), eq(COMMAND), anyString());
            order.verify(worker).executeResume(result, authority);
        }
    }

    @Test
    void exposes_preclaim_failure_and_preserves_the_persisted_ack_after_worker_failure_without_redrive() throws Exception {
        var result = result(ACK);
        var failure = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "stale fixture command");
        when(takeover.takeOver(eq(OWNER), eq(COMMAND), anyString())).thenReturn(result);
        when(takeover.takeOver(eq(OWNER), eq(OTHER), anyString())).thenThrow(failure);
        when(worker.executeResume(result, authority)).thenThrow(new IllegalStateException("fixture runner failed"));
        try (var pool = queuedPool();
             var dispatcher = dispatcher(pool)) {
            assertEquals(ACK, dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    failureCode(dispatcher.dispatch(OWNER, OTHER).toCompletableFuture()));
            dispatcher.close();
            assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            verify(takeover).takeOver(eq(OWNER), eq(COMMAND), anyString());
            verify(takeover).takeOver(eq(OWNER), eq(OTHER), anyString());
            verify(worker).executeResume(result, authority);
            assertEquals(2, pool.getTaskCount());
        }
    }

    @Test
    void replay_leaves_only_the_original_ack_with_no_additional_host_submission() throws Exception {
        var first = result(ACK);
        var replay = result(ACK);
        when(takeover.takeOver(eq(OWNER), eq(COMMAND), anyString())).thenReturn(first, replay);
        when(worker.executeResume(replay, authority)).thenReturn(Optional.empty());
        try (var pool = queuedPool();
             var dispatcher = dispatcher(pool)) {
            assertEquals(ACK, dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertEquals(ACK, dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture().get(5, TimeUnit.SECONDS));
            dispatcher.close();
            assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            verify(takeover, times(2)).takeOver(eq(OWNER), eq(COMMAND), anyString());
            verify(worker).executeResume(first, authority);
            assertEquals(2, pool.getTaskCount());
        }
    }

    @Test
    void close_cancels_queued_commands_and_drains_started_work_even_after_wait_cancellation() throws Exception {
        var result = result(ACK);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var unrelatedRan = new CountDownLatch(1);
        Runnable unrelated = unrelatedRan::countDown;
        when(takeover.takeOver(eq(OWNER), eq(COMMAND), anyString())).thenReturn(result);
        when(worker.executeResume(result, authority)).thenAnswer(invocation -> {
            started.countDown();
            awaitRelease(release);
            return Optional.empty();
        });
        try (var pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                     new ArrayBlockingQueue<>(2), new ThreadPoolExecutor.AbortPolicy());
             var dispatcher = dispatcher(pool)) {
            var waiting = dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture();
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var queued = dispatcher.dispatch(OWNER, OTHER).toCompletableFuture();
                pool.execute(unrelated);
                assertFalse(queued.isDone());
                assertTrue(waiting.cancel(true));
                dispatcher.close();
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failureCode(queued));
                assertFalse(dispatcher.awaitQuiescence(Duration.ofMillis(10)));
                assertFalse(pool.isShutdown());
                assertSame(unrelated, pool.getQueue().peek());
                assertEquals(1, pool.getQueue().size());
                verify(takeover, never()).takeOver(eq(OWNER), eq(OTHER), anyString());
            } finally {
                release.countDown();
            }
            assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            assertTrue(unrelatedRan.await(5, TimeUnit.SECONDS));
            verify(worker).executeResume(result, authority);
            verify(takeover, never()).takeOver(eq(OWNER), eq(OTHER), anyString());
        }
    }

    @Test
    void close_fences_a_host_worker_that_has_received_but_not_started_the_command() throws Exception {
        var workerReceived = new CountDownLatch(1);
        var releaseWorker = new CountDownLatch(1);
        try (var pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
                     task -> new Thread(() -> {
                         workerReceived.countDown();
                         awaitRelease(releaseWorker);
                         task.run();
                     }), new ThreadPoolExecutor.AbortPolicy());
             var dispatcher = dispatcher(pool)) {
            try {
                var waiting = dispatcher.dispatch(OWNER, COMMAND).toCompletableFuture();
                assertTrue(workerReceived.await(5, TimeUnit.SECONDS));
                assertTrue(pool.getQueue().isEmpty());
                dispatcher.close();
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failureCode(waiting));
                assertTrue(dispatcher.awaitQuiescence(Duration.ofSeconds(2)));
            } finally {
                releaseWorker.countDown();
            }
        }
        verify(takeover, never()).takeOver(any(), any(), anyString());
        verify(worker, never()).executeResume(any(), any());
    }

    private AgentCheckpointResumeDispatcher dispatcher(ThreadPoolExecutor pool) {
        bindCollaborators();
        return new AgentCheckpointResumeDispatcher(pool, worker, takeover, source, authority);
    }

    private void bindCollaborators() {
        when(worker.usesSessionDataSource(source)).thenReturn(true);
        when(takeover.usesSourceAndAuthority(source, authority)).thenReturn(true);
    }

    private static TakeoverResult result(Acknowledgement ack) {
        var result = mock(TakeoverResult.class);
        when(result.acknowledgement()).thenReturn(ack);
        return result;
    }

    private static AgentErrorCodeEnum failureCode(CompletableFuture<?> future) {
        var failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        return assertInstanceOf(AgentLedgerException.class, failure.getCause()).code();
    }

    private static ThreadPoolExecutor directHandoffPool() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadPoolExecutor queuedPool() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.AbortPolicy());
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            assertTrue(release.await(5, TimeUnit.SECONDS), "fixture was not released");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("fixture was interrupted", interrupted);
        }
    }
}
