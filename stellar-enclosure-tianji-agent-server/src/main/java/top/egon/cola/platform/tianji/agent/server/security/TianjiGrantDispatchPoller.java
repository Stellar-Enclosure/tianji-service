package top.egon.cola.platform.tianji.agent.server.security;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.domain.GrantReadyAgentRun;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

/** Rotates through grant-ready USER runs; the dispatcher and ledger retain claim and lease authority. */
public final class TianjiGrantDispatchPoller implements SmartLifecycle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(TianjiGrantDispatchPoller.class);
    private final AgentLedgerService ledger;
    private final AgentRunDispatcher dispatcher;
    private final TaskScheduler scheduler;
    private final int batchSize;
    private final Duration interval;
    private final Duration shutdownTimeout;
    private final Semaphore slots;
    private final Set<GrantReadyAgentRun> inFlight = ConcurrentHashMap.newKeySet();
    private final Object monitor = new Object();
    private final java.util.concurrent.atomic.AtomicBoolean discoveryFailureLogged = new java.util.concurrent.atomic.AtomicBoolean();
    private boolean running;
    private boolean closed;
    private long generation;
    private Thread activePoll;
    private ScheduledFuture<?> scheduled;
    private GrantReadyAgentRun.Cursor cursor;

    public TianjiGrantDispatchPoller(AgentLedgerService ledger, AgentRunDispatcher dispatcher,
            TianjiExecutionGrantAuthorizer authorizer, TaskScheduler scheduler, int batchSize, int maxInFlight,
            Duration interval, Duration shutdownTimeout) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        if (authorizer == null || !dispatcher.usesAuthorization(authorizer)) {
            throw new IllegalArgumentException("TIANJI_DISPATCH_AUTHORIZER_MISMATCH");
        }
        if (!dispatcher.usesLedger(ledger) || !authorizer.usesLedger(ledger)) {
            throw new IllegalArgumentException("TIANJI_DISPATCH_LEDGER_MISMATCH");
        }
        if (batchSize < 1 || batchSize > 1000 || maxInFlight < 1 || maxInFlight > 128
                || interval == null || interval.compareTo(Duration.ofMillis(250)) < 0
                || interval.compareTo(Duration.ofHours(1)) > 0 || shutdownTimeout == null || shutdownTimeout.isNegative()
                || shutdownTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("TIANJI_DISPATCH_SETTINGS_INVALID");
        }
        this.batchSize = batchSize; this.interval = interval; this.shutdownTimeout = shutdownTimeout;
        this.slots = new Semaphore(maxInFlight);
    }

    @Override public void start() {
        synchronized (monitor) {
            if (running) return;
            if (closed) throw new IllegalStateException("TIANJI_DISPATCH_CLOSED");
            running = true; long expected = ++generation;
            try { scheduled = Objects.requireNonNull(scheduler.scheduleWithFixedDelay(() -> poll(expected), interval)); }
            catch (RuntimeException failed) { running = false; throw failed; }
        }
    }

    private void poll(long expected) {
        GrantReadyAgentRun.Cursor position;
        synchronized (monitor) {
            if (!running || generation != expected || activePoll != null) return;
            activePoll = Thread.currentThread(); position = cursor;
        }
        try {
            var candidates = ledger.grantReadyRuns(position, batchSize);
            discoveryFailureLogged.set(false);
            if (candidates.isEmpty()) {
                synchronized (monitor) { if (running && generation == expected) cursor = null; }
                return;
            }
            for (var candidate : candidates) {
                synchronized (monitor) { if (!running || generation != expected) return; }
                if (!inFlight.add(candidate)) {
                    synchronized (monitor) { if (running && generation == expected) cursor = candidate.cursor(); }
                    continue;
                }
                if (!slots.tryAcquire()) { inFlight.remove(candidate); break; }
                try {
                    dispatcher.dispatch(candidate.actor(), candidate.query()).whenComplete((ignored, failure) -> {
                        if (inFlight.remove(candidate)) slots.release();
                        if (failure != null) log.debug("Agent dispatch failed failureType={}", failure.getClass().getSimpleName());
                    });
                } catch (RuntimeException failed) {
                    if (inFlight.remove(candidate)) slots.release();
                    log.debug("Agent dispatch admission failed failureType={}", failed.getClass().getSimpleName());
                }
                synchronized (monitor) { if (running && generation == expected) cursor = candidate.cursor(); }
            }
        } catch (RuntimeException failed) {
            if (discoveryFailureLogged.compareAndSet(false, true)) {
                log.warn("Agent dispatch discovery failed failureType={}", failed.getClass().getSimpleName());
            }
        } finally {
            synchronized (monitor) { activePoll = null; monitor.notifyAll(); }
        }
    }

    @Override public void stop() {
        ScheduledFuture<?> future;
        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        synchronized (monitor) {
            if (closed) return;
            closed = true; running = false; generation++; future = scheduled; scheduled = null;
        }
        if (future != null) {
            try { future.cancel(false); }
            catch (RuntimeException failed) { log.warn("Agent dispatch unschedule failed failureType={}", failed.getClass().getSimpleName()); }
        }
        dispatcher.close();
        synchronized (monitor) {
            while (activePoll != null && activePoll != Thread.currentThread()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) break;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        long remaining = deadline - System.nanoTime();
        if (remaining > 0 && !dispatcher.awaitQuiescence(Duration.ofNanos(remaining))) {
            log.warn("Agent dispatch did not drain before shutdown timeout");
        }
    }

    @Override public boolean isRunning() { synchronized (monitor) { return running; } }
    @Override public int getPhase() { return Integer.MAX_VALUE - 100; }
    @Override public void close() { stop(); }
}
