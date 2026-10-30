package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryRetentionWork;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryRetentionJobTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MemoryRetentionWork.Origin FIRST = new MemoryRetentionWork.Origin(20, "a".repeat(64));
    private static final MemoryRetentionWork.Origin SECOND = new MemoryRetentionWork.Origin(21, "b".repeat(64));

    @Test void maintenance_is_scheduled_once_bounded_fair_and_old_wakeups_do_not_run_after_restart() {
        var work = work(); var scheduler = mock(TaskScheduler.class); var future = mock(ScheduledFuture.class); var task = new AtomicReference<Runnable>();
        doAnswer(call -> { task.set(call.getArgument(0)); return future; }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(work.apply(any(), anyInt(), any())).thenReturn(new MemoryRetentionWork.Result(1, 1, 0));
        var job = new MemoryRetentionJob(work, scheduler, properties(Duration.ofSeconds(1)), CLOCK);
        job.start(); var original = task.get(); job.start(); verify(scheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)); verify(work, never()).apply(any(), anyInt(), any());
        original.run(); verify(work).apply(FIRST, 1, NOW.plusSeconds(2));
        original.run(); verify(work).apply(SECOND, 1, NOW.plusSeconds(2));
        job.stop(); assertFalse(job.isRunning()); verify(future).cancel(false);
        job.start(); original.run(); verify(work, times(2)).apply(any(), anyInt(), any());
        task.get().run(); verify(work, times(3)).apply(any(), anyInt(), any()); job.stop();
    }

    @Test void an_active_batch_drains_without_overlap_and_cannot_restart_until_it_has_finished() throws Exception {
        var work = work(); var scheduler = mock(TaskScheduler.class); var task = new AtomicReference<Runnable>(); var future = mock(ScheduledFuture.class);
        doAnswer(call -> { task.set(call.getArgument(0)); return future; }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(work.apply(any(), anyInt(), any())).thenAnswer(call -> { entered.countDown(); release.await(3, TimeUnit.SECONDS); return new MemoryRetentionWork.Result(1, 1, 0); });
        var job = new MemoryRetentionJob(work, scheduler, properties(Duration.ZERO), CLOCK); job.start();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var active = threads.submit(() -> task.get().run()); assertTrue(entered.await(1, TimeUnit.SECONDS));
            task.get().run(); verify(work, times(1)).apply(any(), anyInt(), any()); job.stop(); assertFalse(job.isRunning());
            assertThrows(IllegalStateException.class, job::start); release.countDown(); active.get(2, TimeUnit.SECONDS);
            job.start(); assertTrue(job.isRunning()); job.stop();
        } finally { release.countDown(); job.stop(); }
    }

    private static MemoryRetentionWork work() { var work = mock(MemoryRetentionWork.class); when(work.origins()).thenReturn(List.of(FIRST, SECOND)); return work; }
    private static TianjiMemoryRetentionProperties properties(Duration shutdown) { return new TianjiMemoryRetentionProperties(Set.of("retention@1#" + "a".repeat(64)), 1, Duration.ofSeconds(1), Duration.ofSeconds(2), shutdown); }
}
