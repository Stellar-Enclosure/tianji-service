package top.egon.cola.platform.tianji.process.server.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One serial maintenance callback bounds work; durable Inbox rows retain cross-node delivery authority. */
public final class CaseCompletionPoller implements SmartLifecycle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(CaseCompletionPoller.class);
    private final CaseSourceRegistry registry;
    private final CaseCompletionCorrelationService results;
    private final Duration interval;
    private final int batchSize;
    private final ScheduledExecutorService scheduler;
    private final boolean owned;
    private final Object monitor = new Object();
    private final AtomicBoolean ticking = new AtomicBoolean();
    private ScheduledFuture<?> future;
    private boolean running;
    private boolean closed;
    private long generation;
    private int cursor;

    public CaseCompletionPoller(CaseSourceRegistry registry, CaseCompletionCorrelationService results, Duration interval, int batchSize) {
        this(registry, results, interval, batchSize, scheduler(), true);
    }
    CaseCompletionPoller(CaseSourceRegistry registry, CaseCompletionCorrelationService results, Duration interval, int batchSize, ScheduledExecutorService scheduler, boolean owned) {
        if (interval == null || interval.compareTo(Duration.ofMillis(100)) < 0 || interval.compareTo(Duration.ofHours(1)) > 0 || interval.toNanos() % 1_000_000 != 0 || batchSize < 1 || batchSize > 100) throw invalid();
        this.registry = Objects.requireNonNull(registry, "registry"); this.results = Objects.requireNonNull(results, "results");
        this.interval = interval; this.batchSize = batchSize; this.scheduler = Objects.requireNonNull(scheduler, "scheduler"); this.owned = owned;
    }
    private static ScheduledExecutorService scheduler() {
        var scheduler = new ScheduledThreadPoolExecutor(1, task -> { var thread = new Thread(task, "tianji-case-completion"); thread.setDaemon(true); return thread; });
        scheduler.setRemoveOnCancelPolicy(true); scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false); scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return scheduler;
    }

    @Override public void start() {
        synchronized (monitor) {
            if (running) return;
            if (closed || ticking.get()) throw new IllegalStateException("Case completion maintenance cannot restart");
            long current = ++generation; running = true;
            try { future = Objects.requireNonNull(scheduler.scheduleWithFixedDelay(() -> tick(current), interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS)); }
            catch (RuntimeException failure) { running = false; generation++; future = null; throw failure; }
        }
    }
    private void tick(long expected) {
        if (!enabled(expected) || !ticking.compareAndSet(false, true)) return;
        try {
            if (!enabled(expected)) return;
            var registrations = registry.registrations();
            if (!registrations.isEmpty()) {
                var registration = registrations.get(Math.floorMod(cursor++, registrations.size()));
                try { if (enabled(expected)) results.reconcile(registration, batchSize); }
                catch (RuntimeException failure) { log.warn("Case completion source maintenance failed: type={}", failure.getClass().getName()); }
            }
            // This also runs for an empty registry, so withdrawn/renamed sources cannot hide durable intents.
            if (enabled(expected)) {
                try { results.reconcileOrphans(registry.activeDueKeys(), batchSize); }
                catch (RuntimeException failure) { log.warn("Case completion orphan maintenance failed: type={}", failure.getClass().getName()); }
            }
        } catch (RuntimeException failure) { log.warn("Case completion maintenance failed: type={}", failure.getClass().getName()); }
        finally { ticking.set(false); }
    }
    private boolean enabled(long expected) { synchronized (monitor) { return running && generation == expected; } }

    @Override public void stop() {
        ScheduledFuture<?> scheduled;
        synchronized (monitor) {
            if (closed) return;
            closed = true; running = false; generation++; scheduled = future; future = null;
        }
        if (scheduled != null) scheduled.cancel(false);
        if (owned) {
            scheduler.shutdown();
            try { if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow(); }
            catch (InterruptedException interrupted) { scheduler.shutdownNow(); Thread.currentThread().interrupt(); }
        }
    }
    @Override public boolean isRunning() { synchronized (monitor) { return running; } }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    @Override public void close() { stop(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case completion polling configuration is invalid"); }
}
