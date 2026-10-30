package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryRetentionWork;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One bounded technical task on the host's existing maintenance scheduler; it never dispatches an Agent or a model. */
public final class MemoryRetentionJob implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(MemoryRetentionJob.class);
    private final MemoryRetentionWork work;
    private final TaskScheduler scheduler;
    private final TianjiMemoryRetentionProperties properties;
    private final Clock clock;
    private final Object monitor = new Object();
    private boolean running;
    private boolean stopping;
    private long generation;
    private int cursor;
    private Thread active;
    private ScheduledFuture<?> scheduled;

    public MemoryRetentionJob(MemoryRetentionWork work, TaskScheduler scheduler, TianjiMemoryRetentionProperties properties, Clock clock) {
        this.work = Objects.requireNonNull(work, "work"); this.scheduler = Objects.requireNonNull(scheduler, "scheduler"); this.properties = Objects.requireNonNull(properties, "properties"); this.clock = Objects.requireNonNull(clock, "clock");
        if (work.origins().isEmpty()) throw new IllegalArgumentException("Memory retention has no approved origins");
    }
    @Override public void start() {
        synchronized (monitor) {
            if (running) return; if (stopping || active != null) throw new IllegalStateException("Memory retention is still draining"); running = true; long expected = ++generation;
            try { scheduled = Objects.requireNonNull(scheduler.scheduleWithFixedDelay(() -> poll(expected), properties.interval()), "Retention could not be scheduled"); }
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
                catch (RuntimeException failure) { log.warn("Memory retention stage=UNSCHEDULE outcome=ERROR errorType={}", failure.getClass().getSimpleName()); }
            }
            synchronized (monitor) {
                while (active != null && active != Thread.currentThread()) {
                    long nanos = deadline - System.nanoTime(); if (nanos <= 0) { log.warn("Memory retention stage=SHUTDOWN outcome=TIMEOUT"); break; }
                    try { TimeUnit.NANOSECONDS.timedWait(monitor, nanos); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        } finally { synchronized (monitor) { stopping = false; monitor.notifyAll(); } }
    }
    @Override public boolean isRunning() { synchronized (monitor) { return running; } }
    @Override public int getPhase() { return Integer.MAX_VALUE - 200; }

    private void poll(long expected) {
        synchronized (monitor) { if (!running || generation != expected || active != null) return; active = Thread.currentThread(); }
        try {
            var origins = work.origins(); var deadline = clock.instant().plus(properties.timeout()); int remaining = properties.batchSize();
            for (int count = 0; count < Math.min(16, origins.size()) && remaining > 0 && clock.instant().isBefore(deadline); count++) {
                MemoryRetentionWork.Origin origin;
                synchronized (monitor) { if (!running || generation != expected) break; origin = origins.get(cursor); cursor = (cursor + 1) % origins.size(); }
                try { remaining -= work.apply(origin, remaining, deadline).selected(); }
                catch (RuntimeException failure) { log.warn("Memory retention stage=EXPIRE outcome=ERROR errorType={}", failure.getClass().getSimpleName()); }
            }
        } finally { synchronized (monitor) { active = null; monitor.notifyAll(); } }
    }
}
