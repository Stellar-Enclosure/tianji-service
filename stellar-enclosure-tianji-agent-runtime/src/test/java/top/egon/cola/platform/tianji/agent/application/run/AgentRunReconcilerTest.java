package top.egon.cola.platform.tianji.agent.application.run;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.autoconfigure.AgentReconciliationProperties;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(10)
class AgentRunReconcilerTest {
    @Test
    void starts_once_stops_scheduling_and_recovers_after_a_sanitized_failure() {
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return future;
        });
        when(ledger.reconcileDue(17)).thenThrow(new IllegalStateException("private-value")).thenReturn(1);
        var properties = properties(17, Duration.ofSeconds(2), Duration.ofSeconds(1));
        var reconciler = new AgentRunReconciler(ledger, scheduler, properties);
        Logger logger = (Logger) LoggerFactory.getLogger(AgentRunReconciler.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            reconciler.start();
            reconciler.start();
            assertTrue(reconciler.isRunning());
            verify(scheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(2)));
            task.get().run();
            task.get().run();
            verify(ledger, times(2)).reconcileDue(17);
            assertTrue(logs.list.stream().anyMatch(entry -> entry.getLevel() == Level.WARN
                    && entry.getFormattedMessage().contains("IllegalStateException")));
            assertTrue(logs.list.stream().noneMatch(entry -> entry.getFormattedMessage().contains("private-value")));
            reconciler.stop();
            assertFalse(reconciler.isRunning());
            verify(future).cancel(false);
            task.get().run();
            verifyNoMoreInteractions(ledger);
        } finally {
            reconciler.stop();
            logger.detachAppender(logs);
            logs.stop();
        }
    }

    @Test
    void overlapping_polls_are_skipped_and_stop_waits_for_the_active_transaction() throws Exception {
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return future;
        });
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ledger.reconcileDue(3)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 1;
        });
        var reconciler = new AgentRunReconciler(ledger, scheduler, properties(3, Duration.ofSeconds(1), Duration.ofSeconds(3)));
        reconciler.start();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var active = pool.submit(task.get());
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            task.get().run();
            verify(ledger, times(1)).reconcileDue(3);
            var stopped = pool.submit(() -> { reconciler.stop(); });
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> verify(future).cancel(false));
            assertFalse(stopped.isDone());
            release.countDown();
            active.get(3, TimeUnit.SECONDS);
            stopped.get(3, TimeUnit.SECONDS);
            assertFalse(reconciler.isRunning());
        } finally {
            release.countDown();
            reconciler.stop();
        }
    }

    @Test
    void rejects_unbounded_or_invalid_configuration_and_schedule_failure() {
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRunReconciler(ledger, scheduler, properties(0, Duration.ofSeconds(1), Duration.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRunReconciler(ledger, scheduler, properties(1, Duration.ofMillis(1), Duration.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentRunReconciler(ledger, scheduler, properties(1, Duration.ofSeconds(1), Duration.ofSeconds(31))));
        var reconciler = new AgentRunReconciler(ledger, scheduler, properties(1, Duration.ofSeconds(1), Duration.ZERO));
        assertThrows(NullPointerException.class, reconciler::start);
        assertFalse(reconciler.isRunning());
    }

    @Test
    void a_callback_from_a_stopped_generation_cannot_run_after_restart() {
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        List<Runnable> tasks = new ArrayList<>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        var reconciler = new AgentRunReconciler(ledger, scheduler, properties(2, Duration.ofSeconds(1), Duration.ZERO));
        reconciler.start();
        reconciler.stop();
        reconciler.start();
        try {
            tasks.getFirst().run();
            verifyNoInteractions(ledger);
            tasks.getLast().run();
            verify(ledger).reconcileDue(2);
        } finally {
            reconciler.stop();
        }
    }

    @Test
    void shutdown_timeout_stops_admission_without_interrupting_an_active_poll() throws Exception {
        AgentLedgerService ledger = mock(AgentLedgerService.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        AtomicReference<Runnable> task = new AtomicReference<>();
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(invocation -> {
            task.set(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ledger.reconcileDue(4)).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 0;
        });
        var reconciler = new AgentRunReconciler(ledger, scheduler, properties(4, Duration.ofSeconds(1), Duration.ZERO));
        reconciler.start();
        try (var pool = Executors.newSingleThreadExecutor()) {
            var active = pool.submit(task.get());
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            reconciler.stop();
            assertFalse(reconciler.isRunning());
            assertFalse(active.isDone());
            task.get().run();
            verify(ledger, times(1)).reconcileDue(4);
            release.countDown();
            active.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            reconciler.stop();
        }
    }

    private static AgentReconciliationProperties properties(int batch, Duration interval, Duration shutdown) {
        var result = new AgentReconciliationProperties();
        result.setBatchSize(batch);
        result.setInterval(interval);
        result.setShutdownTimeout(shutdown);
        return result;
    }
}
