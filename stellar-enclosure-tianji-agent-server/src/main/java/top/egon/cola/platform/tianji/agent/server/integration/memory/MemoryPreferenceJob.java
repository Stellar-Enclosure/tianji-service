package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One non-reentrant task on the existing maintenance scheduler, with stop admission and bounded drain. */
public final class MemoryPreferenceJob implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(MemoryPreferenceJob.class);
    private final MemoryPreferenceWork work;
    private final TaskScheduler scheduler;
    private final TianjiMemoryPreferenceBackgroundProperties properties;
    private final Clock clock;
    private final Object monitor = new Object();
    private boolean running;
    private boolean stopping;
    private long generation;
    private int cursor;
    private Thread active;
    private ScheduledFuture<?> scheduled;

    public MemoryPreferenceJob(MemoryPreferenceWork work, TaskScheduler scheduler, TianjiMemoryPreferenceBackgroundProperties properties, Clock clock) {
        this.work = Objects.requireNonNull(work, "work"); this.scheduler = Objects.requireNonNull(scheduler, "scheduler"); this.properties = Objects.requireNonNull(properties, "properties"); this.clock = Objects.requireNonNull(clock, "clock");
        if (work.origins().isEmpty()) throw new IllegalArgumentException("Memory preference work has no approved origins");
    }
    @Override public void start() {
        synchronized (monitor) {
            if (running) return; if (stopping || active != null) throw new IllegalStateException("Memory preference work is still draining"); running = true; long expected = ++generation;
            try { scheduled = Objects.requireNonNull(scheduler.scheduleWithFixedDelay(() -> poll(expected), properties.interval()), "Memory preference work could not be scheduled"); }
            catch (RuntimeException failure) { running = false; throw failure; }
        }
    }
    @Override public void stop() {
        long deadline = System.nanoTime() + properties.shutdownTimeout().toNanos(); ScheduledFuture<?> future;
        synchronized (monitor) {
            while (stopping && active != Thread.currentThread()) {
                long nanos = deadline - System.nanoTime(); if (nanos <= 0) return;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, nanos); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
            if (stopping || !running && active == null) return; running = false; generation++; stopping = true; future = scheduled; scheduled = null;
        }
        try {
            if (future != null) {
                try { future.cancel(false); }
                catch (RuntimeException failure) { log.warn("Memory preference stage=UNSCHEDULE outcome=ERROR errorType={}", failure.getClass().getSimpleName()); }
            }
            synchronized (monitor) {
                while (active != null && active != Thread.currentThread()) {
                    long nanos = deadline - System.nanoTime(); if (nanos <= 0) { log.warn("Memory preference stage=SHUTDOWN outcome=TIMEOUT"); break; }
                    try { TimeUnit.NANOSECONDS.timedWait(monitor, nanos); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        } finally { synchronized (monitor) { stopping = false; monitor.notifyAll(); } }
    }
    @Override public boolean isRunning() { synchronized (monitor) { return running; } }
    @Override public int getPhase() { return Integer.MAX_VALUE - 190; }

    private boolean admitted(long expected) { synchronized (monitor) { return running && generation == expected; } }
    private void poll(long expected) {
        synchronized (monitor) { if (!running || generation != expected || active != null) return; active = Thread.currentThread(); }
        try {
            var origins = work.origins(); var deadline = clock.instant().plus(properties.timeout()); int records = properties.batchSize(), facts = properties.batchSize();
            long monotonic = System.nanoTime() + properties.timeout().toNanos();
            for (int count = 0; count < Math.min(16, origins.size()) && records > 0 && facts > 0 && clock.instant().isBefore(deadline) && monotonic - System.nanoTime() > 0; count++) {
                MemoryPreferenceWork.Origin origin;
                synchronized (monitor) { if (!running || generation != expected) break; origin = origins.get(cursor); cursor = (cursor + 1) % origins.size(); }
                try {
                    var result = work.apply(origin, records, facts, deadline, () -> admitted(expected) && monotonic - System.nanoTime() > 0);
                    records -= result.selectedIntents() + result.reconciled(); facts -= result.attemptedFacts();
                } catch (RuntimeException failure) { log.warn("Memory preference stage=POLL outcome=UNAVAILABLE errorType={}", failure.getClass().getSimpleName()); }
            }
        } finally { synchronized (monitor) { active = null; monitor.notifyAll(); } }
    }
}
