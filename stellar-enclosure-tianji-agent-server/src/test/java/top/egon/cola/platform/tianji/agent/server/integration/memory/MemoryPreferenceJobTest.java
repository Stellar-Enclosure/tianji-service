package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryPreferenceJobTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final MemoryPreferenceWork.Origin FIRST = new MemoryPreferenceWork.Origin(20, "a".repeat(64));
    private static final MemoryPreferenceWork.Origin SECOND = new MemoryPreferenceWork.Origin(21, "b".repeat(64));

    @Test void scheduling_uses_the_existing_host_once_rotates_origins_and_fences_old_callbacks_and_admission() {
        var work = work(); var scheduler = mock(TaskScheduler.class); var future = mock(ScheduledFuture.class); var task = new AtomicReference<Runnable>(); var admission = new AtomicReference<BooleanSupplier>();
        doAnswer(call -> { task.set(call.getArgument(0)); return future; }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(work.apply(any(), anyInt(), anyInt(), any(), any())).thenAnswer(call -> { admission.set(call.getArgument(4)); assertTrue(admission.get().getAsBoolean()); return new MemoryPreferenceWork.Result(1, 1, 1, 0); });
        var job = new MemoryPreferenceJob(work, scheduler, properties(1, Duration.ofSeconds(1)), CLOCK);
        job.start(); var original = task.get(); job.start(); verify(scheduler, times(1)).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)); verify(work, never()).apply(any(), anyInt(), anyInt(), any(), any());
        original.run(); verify(work).apply(eq(FIRST), eq(1), eq(1), eq(NOW.plusSeconds(2)), any()); original.run(); verify(work).apply(eq(SECOND), eq(1), eq(1), any(), any());
        job.stop(); assertFalse(job.isRunning()); assertFalse(admission.get().getAsBoolean()); verify(future).cancel(false);
        job.start(); original.run(); verify(work, times(2)).apply(any(), anyInt(), anyInt(), any(), any()); task.get().run(); verify(work, times(3)).apply(any(), anyInt(), anyInt(), any(), any()); job.stop();
    }

    @Test void cleanup_and_attempted_facts_reduce_separate_global_tick_budgets() {
        var work = work(); var scheduler = mock(TaskScheduler.class); var task = new AtomicReference<Runnable>();
        doAnswer(call -> { task.set(call.getArgument(0)); return mock(ScheduledFuture.class); }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(work.apply(eq(FIRST), anyInt(), anyInt(), any(), any())).thenReturn(new MemoryPreferenceWork.Result(1, 2, 1, 0));
        when(work.apply(eq(SECOND), anyInt(), anyInt(), any(), any())).thenReturn(new MemoryPreferenceWork.Result(0, 0, 0, 2));
        var job = new MemoryPreferenceJob(work, scheduler, properties(3, Duration.ZERO), CLOCK); job.start();
        task.get().run(); verify(work).apply(eq(FIRST), eq(3), eq(3), any(), any()); verify(work).apply(eq(SECOND), eq(2), eq(1), any(), any()); job.stop();
    }

    @Test void active_work_has_no_overlap_and_restart_waits_for_real_drain_after_a_bounded_stop() throws Exception {
        var work = work(); var scheduler = mock(TaskScheduler.class); var task = new AtomicReference<Runnable>(); var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { task.set(call.getArgument(0)); return mock(ScheduledFuture.class); }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        var admission = new AtomicReference<BooleanSupplier>(); when(work.apply(any(), anyInt(), anyInt(), any(), any())).thenAnswer(call -> { admission.set(call.getArgument(4)); entered.countDown(); release.await(3, TimeUnit.SECONDS); return new MemoryPreferenceWork.Result(1, 1, 1, 0); });
        var job = new MemoryPreferenceJob(work, scheduler, properties(1, Duration.ZERO), CLOCK); job.start();
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var active = threads.submit(() -> task.get().run()); assertTrue(entered.await(1, TimeUnit.SECONDS)); task.get().run(); verify(work, times(1)).apply(any(), anyInt(), anyInt(), any(), any());
            job.stop(); assertFalse(job.isRunning()); assertFalse(admission.get().getAsBoolean()); assertThrows(IllegalStateException.class, job::start);
            release.countDown(); active.get(2, TimeUnit.SECONDS); job.start(); assertTrue(job.isRunning()); job.stop();
        } finally { release.countDown(); job.stop(); }
    }

    @Test void empty_or_failed_origins_are_bounded_to_sixteen_and_the_next_tick_continues_fairly() {
        var work = work(); var origins = java.util.stream.LongStream.range(1, 21).mapToObj(id -> new MemoryPreferenceWork.Origin(id, "a".repeat(64))).toList(); when(work.origins()).thenReturn(origins);
        var scheduler = mock(TaskScheduler.class); var task = new AtomicReference<Runnable>();
        doAnswer(call -> { task.set(call.getArgument(0)); return mock(ScheduledFuture.class); }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
        when(work.apply(any(), anyInt(), anyInt(), any(), any())).thenReturn(new MemoryPreferenceWork.Result(0, 0, 0, 0));
        var job = new MemoryPreferenceJob(work, scheduler, properties(3, Duration.ZERO), CLOCK); job.start(); task.get().run(); verify(work, times(16)).apply(any(), anyInt(), anyInt(), any(), any());
        clearInvocations(work); doThrow(new IllegalStateException("fixture unavailable")).when(work).apply(eq(origins.get(16)), anyInt(), anyInt(), any(), any());
        task.get().run(); var order = inOrder(work); order.verify(work).origins(); order.verify(work).apply(eq(origins.get(16)), anyInt(), anyInt(), any(), any()); order.verify(work).apply(eq(origins.get(17)), anyInt(), anyInt(), any(), any()); job.stop();
    }

    @Test void scheduling_failure_is_closed_and_background_property_bounds_reject_unbounded_work() {
        var work = work(); var scheduler = mock(TaskScheduler.class); when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenThrow(new IllegalStateException("Scheduling unavailable"));
        var job = new MemoryPreferenceJob(work, scheduler, properties(1, Duration.ZERO), CLOCK); assertThrows(IllegalStateException.class, job::start); assertFalse(job.isRunning());
        verify(work, never()).apply(any(), anyInt(), anyInt(), any(), any());
        var defaults = new TianjiMemoryPreferenceBackgroundProperties(0, 0, null, null, null, null, null, null, null, 0); assertEquals(16, defaults.batchSize()); assertEquals(Duration.ofHours(1), defaults.grantLifetime());
        assertThrows(IllegalArgumentException.class, () -> properties(101, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new TianjiMemoryPreferenceBackgroundProperties(1, 1, null, Duration.ofSeconds(1), null, null, null, null, Duration.ofSeconds(2), 1));
    }

    private static MemoryPreferenceWork work() { var work = mock(MemoryPreferenceWork.class); when(work.origins()).thenReturn(List.of(FIRST, SECOND)); return work; }
    private static TianjiMemoryPreferenceBackgroundProperties properties(int batch, Duration shutdown) { return new TianjiMemoryPreferenceBackgroundProperties(batch, 8, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofSeconds(1), shutdown, Duration.ofMinutes(10), Duration.ofSeconds(1), 1); }
}
