package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessAgentBridgePollerTest {
    private final AgentStartWorker first = mock(AgentStartWorker.class);
    private final AgentStartWorker second = mock(AgentStartWorker.class);
    private final AgentResultCorrelationService results = mock(AgentResultCorrelationService.class);
    private final TaskScheduler dispatchScheduler = mock(TaskScheduler.class);
    private final TaskScheduler maintenanceScheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> dispatchFuture = mock(ScheduledFuture.class);
    private final ScheduledFuture<?> maintenanceFuture = mock(ScheduledFuture.class);
    private final AtomicReference<Runnable> dispatch = new AtomicReference<>();
    private final AtomicReference<Runnable> maintenance = new AtomicReference<>();
    private final LinkedBlockingQueue<Runnable> newWork = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Runnable> upkeep = new LinkedBlockingQueue<>();
    private final AgentResultProducerRegistry.Registration producer = new AgentResultProducerRegistry.Registration(
            ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "agent", "https://idp.example/20"), "agent-east", "process-east",
            ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20"));

    @BeforeEach void captureSchedulers() {
        when(dispatchScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)))).thenAnswer(call -> {
            dispatch.set(call.getArgument(0)); return dispatchFuture;
        });
        when(maintenanceScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(1)))).thenAnswer(call -> {
            maintenance.set(call.getArgument(0)); return maintenanceFuture;
        });
    }

    @Test
    void saturated_dispatch_keeps_the_next_cursor_and_never_duplicates_in_flight_workers() {
        try (var poller = poller()) {
            poller.start(); dispatch.get().run(); dispatch.get().run();
            assertEquals(1, newWork.size());
            newWork.remove().run();
            verify(first).runOnce(any(BooleanSupplier.class));
            dispatch.get().run();
            assertEquals(1, newWork.size()); newWork.remove().run();
            verify(second).runOnce(any(BooleanSupplier.class));
        }
    }

    @Test
    void maintenance_interleaves_close_and_inbox_even_when_dispatch_is_paused() {
        try (var poller = poller()) {
            poller.start(); poller.pauseDispatch();
            dispatch.get().run(); assertTrue(newWork.isEmpty());
            maintenance.get().run(); assertEquals(1, upkeep.size()); upkeep.remove().run();
            verify(first).reconcileOnce(1, Duration.ofSeconds(1));
            maintenance.get().run(); upkeep.remove().run();
            verify(results).reconcile(producer, 4);
            maintenance.get().run(); upkeep.remove().run();
            verify(second).reconcileOnce(1, Duration.ofSeconds(1));
            assertTrue(poller.isRunning()); assertFalse(poller.isDispatching());
            poller.resumeDispatch(); dispatch.get().run(); newWork.remove().run();
            verify(first).runOnce(any(BooleanSupplier.class));
        }
    }

    @Test
    void stopped_or_paused_queued_callbacks_do_not_open_new_submission() {
        var poller = poller(); poller.start(); dispatch.get().run();
        Runnable queued = newWork.remove(); poller.pauseDispatch(); queued.run();
        verify(first, never()).runOnce(any(BooleanSupplier.class));
        poller.resumeDispatch(); dispatch.get().run();
        Runnable afterStop = newWork.remove(); poller.stop();
        afterStop.run(); dispatch.get().run(); maintenance.get().run();
        verify(second, never()).runOnce(any(BooleanSupplier.class));
        verify(dispatchFuture).cancel(false); verify(maintenanceFuture).cancel(false);
        assertFalse(poller.isRunning()); assertThrows(IllegalStateException.class, poller::start);
    }

    @Test
    void a_failed_close_releases_capacity_for_inbox_reconciliation() {
        doThrow(new IllegalStateException("controlled close failure")).when(first).reconcileOnce(1, Duration.ofSeconds(1));
        try (var poller = poller()) {
            poller.start(); maintenance.get().run(); upkeep.remove().run();
            maintenance.get().run(); upkeep.remove().run();
            verify(results).reconcile(producer, 4);
        }
    }

    @Test
    void fast_polling_retains_the_minimum_durable_close_observation_interval() {
        when(dispatchScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofMillis(250)))).thenAnswer(call -> dispatchFuture);
        when(maintenanceScheduler.scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofMillis(250)))).thenAnswer(call -> {
            maintenance.set(call.getArgument(0)); return maintenanceFuture;
        });
        try (var poller = new ProcessAgentBridgePoller(List.of(first), List.of(producer), results, dispatchScheduler, maintenanceScheduler,
                newWork::add, upkeep::add, 1, 1, 4, Duration.ofMillis(250), Duration.ZERO, false)) {
            poller.start(); maintenance.get().run(); upkeep.remove().run();
            verify(first).reconcileOnce(1, Duration.ofSeconds(1));
        }
    }

    @Test
    void rejected_admission_releases_capacity_and_retries_the_same_worker() {
        var accepting = new java.util.concurrent.atomic.AtomicBoolean();
        try (var poller = new ProcessAgentBridgePoller(List.of(first), List.of(producer), results, dispatchScheduler, maintenanceScheduler,
                task -> { if (!accepting.get()) throw new RejectedExecutionException(); newWork.add(task); }, upkeep::add,
                1, 1, 4, Duration.ofSeconds(1), Duration.ZERO, true)) {
            poller.start(); dispatch.get().run(); assertTrue(newWork.isEmpty());
            accepting.set(true); dispatch.get().run(); newWork.remove().run();
            verify(first).runOnce(any(BooleanSupplier.class));
        }
    }

    @Test
    void in_flight_worker_observes_pause_before_its_next_submission_boundary() {
        var admission = new AtomicReference<BooleanSupplier>();
        when(first.runOnce(any(BooleanSupplier.class))).thenAnswer(call -> {
            admission.set(call.getArgument(0));
            assertTrue(admission.get().getAsBoolean());
            return AgentStartWorker.Outcome.DEFERRED;
        });
        try (var poller = poller()) {
            poller.start(); dispatch.get().run(); newWork.remove().run();
            poller.pauseDispatch(); assertFalse(admission.get().getAsBoolean());
            poller.resumeDispatch(); assertTrue(admission.get().getAsBoolean());
            poller.stop(); assertFalse(admission.get().getAsBoolean());
        }
    }

    @Test
    void dispatch_cannot_start_if_maintenance_scheduling_fails_and_stale_callbacks_are_fenced() {
        when(dispatchScheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenThrow(new IllegalStateException("controlled scheduler failure"));
        try (var poller = poller()) {
            assertThrows(IllegalStateException.class, poller::start);
            assertFalse(poller.isRunning());
            maintenance.get().run(); assertTrue(upkeep.isEmpty());
            verify(maintenanceFuture).cancel(false);
        }
    }

    private ProcessAgentBridgePoller poller() {
        return new ProcessAgentBridgePoller(List.of(first, second), List.of(producer), results, dispatchScheduler, maintenanceScheduler,
                newWork::add, upkeep::add, 1, 1, 4, Duration.ofSeconds(1), Duration.ZERO, true);
    }
}
