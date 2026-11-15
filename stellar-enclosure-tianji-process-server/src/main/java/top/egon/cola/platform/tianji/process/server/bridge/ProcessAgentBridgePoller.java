package top.egon.cola.platform.tianji.process.server.bridge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Separates new submission from maintenance, while persistent intents and Inbox rows retain cross-node authority. */
@Slf4j
public final class ProcessAgentBridgePoller implements SmartLifecycle, AutoCloseable {
    private final List<AgentStartWorker> workers;
    private final List<AgentResultProducerRegistry.Registration> producers;
    private final AgentResultCorrelationService results;
    private final TaskScheduler dispatchScheduler;
    private final TaskScheduler maintenanceScheduler;
    private final Executor dispatchExecutor;
    private final Executor maintenanceExecutor;
    private final Semaphore dispatchSlots;
    private final Semaphore maintenanceSlots;
    private final Duration interval;
    private final Duration shutdownTimeout;
    private final int batchSize;
    private final boolean initialDispatch;
    private final Runnable requireNativeReady;
    private final Object monitor = new Object();
    private final Set<Job> active = new HashSet<>();
    private boolean running;
    private boolean dispatching;
    private boolean closed;
    private boolean dispatchTick;
    private boolean maintenanceTick;
    private long generation;
    private int dispatchCursor;
    private int maintenanceCursor;
    private ScheduledFuture<?> dispatchFuture;
    private ScheduledFuture<?> maintenanceFuture;

    public ProcessAgentBridgePoller(List<AgentStartWorker> workers, List<AgentResultProducerRegistry.Registration> producers,
                                    AgentResultCorrelationService results, TaskScheduler dispatchScheduler, TaskScheduler maintenanceScheduler,
                                    Executor dispatchExecutor, Executor maintenanceExecutor, int maxDispatch, int maxMaintenance, int batchSize,
                                    Duration interval, Duration shutdownTimeout, boolean dispatchEnabled) {
        this(workers, producers, results, dispatchScheduler, maintenanceScheduler, dispatchExecutor, maintenanceExecutor,
                maxDispatch, maxMaintenance, batchSize, interval, shutdownTimeout, dispatchEnabled, () -> {});
    }

    ProcessAgentBridgePoller(List<AgentStartWorker> workers, List<AgentResultProducerRegistry.Registration> producers,
                            AgentResultCorrelationService results, TaskScheduler dispatchScheduler, TaskScheduler maintenanceScheduler,
                            Executor dispatchExecutor, Executor maintenanceExecutor, int maxDispatch, int maxMaintenance, int batchSize,
                            Duration interval, Duration shutdownTimeout, boolean dispatchEnabled, Runnable requireNativeReady) {
        if (workers == null || workers.isEmpty() || workers.size() > 1000 || producers == null || producers.isEmpty() || producers.size() > 1000
                || workers.stream().anyMatch(Objects::isNull) || producers.stream().anyMatch(Objects::isNull)
                || maxDispatch < 1 || maxDispatch > 32 || maxMaintenance < 1 || maxMaintenance > 32 || batchSize < 1 || batchSize > 32
                || interval == null || interval.compareTo(Duration.ofMillis(250)) < 0 || interval.compareTo(Duration.ofHours(1)) > 0
                || shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0) throw invalid();
        this.workers = List.copyOf(workers); this.producers = List.copyOf(producers);
        this.results = Objects.requireNonNull(results, "results");
        this.dispatchScheduler = Objects.requireNonNull(dispatchScheduler, "dispatchScheduler");
        this.maintenanceScheduler = Objects.requireNonNull(maintenanceScheduler, "maintenanceScheduler");
        this.dispatchExecutor = Objects.requireNonNull(dispatchExecutor, "dispatchExecutor");
        this.maintenanceExecutor = Objects.requireNonNull(maintenanceExecutor, "maintenanceExecutor");
        this.requireNativeReady = Objects.requireNonNull(requireNativeReady, "requireNativeReady");
        if (dispatchScheduler == maintenanceScheduler || dispatchExecutor == maintenanceExecutor) throw invalid();
        this.dispatchSlots = new Semaphore(maxDispatch); this.maintenanceSlots = new Semaphore(maxMaintenance);
        this.interval = interval; this.shutdownTimeout = shutdownTimeout; this.batchSize = batchSize; this.initialDispatch = dispatchEnabled;
    }

    @Override public void start() {
        synchronized (monitor) {
            if (running) return;
            if (closed) throw new IllegalStateException("Process Agent bridge is closed");
            if (!active.isEmpty() || dispatchTick || maintenanceTick) throw new IllegalStateException("Process Agent bridge has unfinished prior operations");
            requireNativeReady.run();
            running = true; dispatching = initialDispatch; long current = ++generation;
            try {
                maintenanceFuture = Objects.requireNonNull(maintenanceScheduler.scheduleWithFixedDelay(() -> maintain(current), interval));
                dispatchFuture = Objects.requireNonNull(dispatchScheduler.scheduleWithFixedDelay(() -> dispatch(current), interval));
            } catch (RuntimeException failure) {
                running = false; dispatching = false; generation++;
                cancel(maintenanceFuture); cancel(dispatchFuture);
                maintenanceFuture = null; dispatchFuture = null;
                throw failure;
            }
        }
    }

    public void pauseDispatch() { synchronized (monitor) { dispatching = false; } }
    public void resumeDispatch() {
        synchronized (monitor) {
            if (!running || closed) throw new IllegalStateException("Process Agent maintenance is not running");
            dispatching = true;
        }
    }
    public boolean isDispatching() { synchronized (monitor) { return running && dispatching; } }

    private void dispatch(long current) {
        synchronized (monitor) { if (!enabled(current, true) || dispatchTick) return; dispatchTick = true; }
        try {
            for (int attempt = 0; attempt < workers.size(); attempt++) {
                Job job;
                synchronized (monitor) {
                    if (!enabled(current, true)) return;
                    job = new Job(Kind.DISPATCH, Math.floorMod(dispatchCursor, workers.size()));
                }
                if (!admit(job, current, dispatchSlots, dispatchExecutor)) return;
                synchronized (monitor) { if (enabled(current, true)) dispatchCursor++; }
            }
        } finally { synchronized (monitor) { dispatchTick = false; monitor.notifyAll(); } }
    }

    private void maintain(long current) {
        synchronized (monitor) { if (!enabled(current, false) || maintenanceTick) return; maintenanceTick = true; }
        try {
            int size = workers.size() + producers.size();
            for (int attempt = 0; attempt < size; attempt++) {
                Job job;
                synchronized (monitor) {
                    if (!enabled(current, false)) return;
                    int index = Math.floorMod(maintenanceCursor, size);
                    int paired = Math.min(workers.size(), producers.size());
                    job = index < 2 * paired ? new Job(index % 2 == 0 ? Kind.CLOSE : Kind.INBOX, index / 2)
                            : workers.size() > paired ? new Job(Kind.CLOSE, index - paired) : new Job(Kind.INBOX, index - paired);
                }
                if (!admit(job, current, maintenanceSlots, maintenanceExecutor)) return;
                synchronized (monitor) { if (enabled(current, false)) maintenanceCursor++; }
            }
        } finally { synchronized (monitor) { maintenanceTick = false; monitor.notifyAll(); } }
    }

    private boolean admit(Job job, long current, Semaphore slots, Executor executor) {
        synchronized (monitor) {
            if (!enabled(current, job.kind() == Kind.DISPATCH)) return false;
            if (active.contains(job)) return true;
            if (!slots.tryAcquire()) return false;
            active.add(job);
        }
        var started = new AtomicBoolean();
        try {
            executor.execute(() -> {
                started.set(true);
                try {
                    synchronized (monitor) { if (!enabled(current, job.kind() == Kind.DISPATCH)) return; }
                    switch (job.kind()) {
                        case DISPATCH -> workers.get(job.index()).runOnce(() -> { synchronized (monitor) { return enabled(current, true); } });
                        case CLOSE -> workers.get(job.index()).reconcileOnce(1, interval.compareTo(Duration.ofSeconds(1)) < 0 ? Duration.ofSeconds(1) : interval);
                        case INBOX -> results.reconcile(producers.get(job.index()), batchSize);
                    }
                } catch (RuntimeException failure) {
                    log.warn("Process Agent bridge operation failed: operation={} type={}", job.kind(), failure.getClass().getName());
                } finally { finished(job, slots); }
            });
            return true;
        } catch (RuntimeException rejected) {
            if (!started.get()) finished(job, slots);
            log.debug("Process Agent bridge admission failed: operation={} type={}", job.kind(), rejected.getClass().getName());
            return false;
        }
    }

    private boolean enabled(long expected, boolean newWork) { return running && generation == expected && (!newWork || dispatching); }
    private void finished(Job job, Semaphore slots) {
        synchronized (monitor) {
            if (active.remove(job)) slots.release();
            monitor.notifyAll();
        }
    }

    @Override public void stop() {
        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        ScheduledFuture<?> dispatch;
        ScheduledFuture<?> maintenance;
        synchronized (monitor) {
            if (closed) return;
            closed = true; running = false; dispatching = false; generation++;
            dispatch = dispatchFuture; maintenance = maintenanceFuture;
            dispatchFuture = null; maintenanceFuture = null;
        }
        cancel(dispatch); cancel(maintenance);
        synchronized (monitor) {
            while (!active.isEmpty() || dispatchTick || maintenanceTick) {
                long remaining = deadline - System.nanoTime(); if (remaining <= 0) break;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
            if (!active.isEmpty()) log.warn("Process Agent bridge stopped waiting for {} operation(s)", active.size());
        }
    }
    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) try { future.cancel(false); }
        catch (RuntimeException failure) { log.warn("Process Agent bridge unschedule failed: type={}", failure.getClass().getName()); }
    }
    @Override public boolean isRunning() { synchronized (monitor) { return running; } }
    // Flowable starts its native executor in an earlier SmartLifecycle phase.
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    @Override public void close() { stop(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Process Agent bridge polling settings are invalid"); }
    private enum Kind { DISPATCH, CLOSE, INBOX }
    private record Job(Kind kind, int index) { }
}
