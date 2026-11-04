package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The scheduler is a typed fake; these tests never create a poller's background thread. */
class CaseCompletionPollerTest {
    @Test void construction_has_no_thread_schedule_or_identity_provider_resolution() {
        try (var f = new Fixture()) {
            assertFalse(f.poller.isRunning()); verifyNoInteractions(f.scheduler, f.registry, f.results);
        }
    }

    @Test void one_registration_per_tick_round_robins_and_orphans_progress_on_every_tick() {
        try (var f = new Fixture()) {
            var second = CaseCompletionCorrelationServiceTest.registration(); when(f.registry.registrations()).thenReturn(List.of(f.registration, second));
            f.start(); f.callback.get().run(); f.callback.get().run();
            verify(f.results).reconcile(f.registration, 32); verify(f.results).reconcile(second, 32);
            verify(f.results, times(2)).reconcileOrphans(Set.of(CaseCompletionCorrelationServiceTest.REGISTRATION_KEY), 32);
            assertTrue(f.poller.isRunning());
        }
    }

    @Test void empty_sources_still_scan_global_orphans_without_a_synthetic_registration() {
        try (var f = new Fixture()) {
            when(f.registry.registrations()).thenReturn(List.of()); when(f.registry.activeDueKeys()).thenReturn(Set.of());
            f.start(); f.callback.get().run(); verify(f.results).reconcileOrphans(Set.of(), 32); verify(f.results, never()).reconcile(any(), anyInt());
        }
    }

    @Test void a_source_failure_does_not_starve_orphan_recovery() {
        try (var f = new Fixture()) {
            doThrow(new IllegalStateException("controlled source failure")).when(f.results).reconcile(f.registration, 32);
            f.start(); assertDoesNotThrow(() -> f.callback.get().run());
            verify(f.results).reconcileOrphans(anySet(), eq(32));
        }
    }

    @Test void overlapping_callbacks_do_not_admit_a_second_native_correlation() throws Exception {
        try (var f = new Fixture()) {
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            doAnswer(call -> { entered.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS)); return new CaseCompletionCorrelationService.Reconciliation(0, 0, 0, 0, 0, 0); }).when(f.results).reconcile(f.registration, 32);
            f.start(); var first = Thread.ofVirtual().unstarted(() -> f.callback.get().run());
            try {
                first.start(); assertTrue(entered.await(1, TimeUnit.SECONDS)); f.callback.get().run(); verify(f.results).reconcile(f.registration, 32);
            } finally { release.countDown(); first.join(2_000); }
            assertFalse(first.isAlive());
        }
    }

    @Test void failed_scheduling_is_not_readiness_and_stop_closes_only_owned_resources() {
        try (var f = new Fixture()) {
            when(f.scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenThrow(new IllegalStateException("scheduler unavailable"));
            assertThrows(IllegalStateException.class, f.poller::start); assertFalse(f.poller.isRunning());
        }
        var external = new Fixture(false); external.start(); external.poller.stop();
        verify(external.future).cancel(false); verify(external.scheduler, never()).shutdown(); verify(external.scheduler, never()).shutdownNow();
        var owned = new Fixture(); owned.start(); owned.poller.stop();
        verify(owned.future).cancel(false); verify(owned.scheduler).shutdown(); assertFalse(owned.poller.isRunning()); assertThrows(IllegalStateException.class, owned.poller::start);
    }

    @Test void batch_and_interval_bounds_do_not_create_unbounded_work_or_a_new_start_switch() {
        var registry = mock(CaseSourceRegistry.class); var results = mock(CaseCompletionCorrelationService.class); var scheduler = mock(ScheduledExecutorService.class);
        assertThrows(IllegalArgumentException.class, () -> new CaseCompletionPoller(registry, results, Duration.ofMillis(99), 32, scheduler, false));
        assertThrows(IllegalArgumentException.class, () -> new CaseCompletionPoller(registry, results, Duration.ofSeconds(1), 101, scheduler, false));
        verifyNoInteractions(registry, results, scheduler);
    }

    private static final class Fixture implements AutoCloseable {
        final CaseSourceRegistry registry = mock(CaseSourceRegistry.class); final CaseCompletionCorrelationService results = mock(CaseCompletionCorrelationService.class);
        final CaseSourceRegistry.Registration registration = CaseCompletionCorrelationServiceTest.registration();
        final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class); final ScheduledFuture<?> future = mock(ScheduledFuture.class);
        final AtomicReference<Runnable> callback = new AtomicReference<>(); final CaseCompletionPoller poller;
        Fixture() { this(true); }
        Fixture(boolean owned) {
            when(registry.registrations()).thenReturn(List.of(registration)); when(registry.activeDueKeys()).thenReturn(Set.of(CaseCompletionCorrelationServiceTest.REGISTRATION_KEY));
            when(scheduler.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> { callback.set(call.getArgument(0)); return future; });
            poller = new CaseCompletionPoller(registry, results, Duration.ofSeconds(1), 32, scheduler, owned);
        }
        void start() { assertDoesNotThrow(poller::start); assertTrue(poller.isRunning()); assertNotNull(callback.get()); }
        @Override public void close() { poller.close(); }
    }
}
