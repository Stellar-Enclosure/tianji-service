package top.egon.cola.platform.tianji.agent.application.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.autoconfigure.AgentReconciliationProperties;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Schedules bounded reconciliation. The ledger verifies each persisted profile under the run lock
 * before expiring a deadline or settling a lost lease and its outstanding calls. */
public final class AgentRunReconciler implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AgentRunReconciler.class);
    private final AgentLedgerService ledger;
    private final TaskScheduler scheduler;
    private final int batchSize;
    private final Duration interval;
    private final Duration shutdownTimeout;
    private final Object monitor = new Object();
    private boolean running;
    private boolean stopping;
    private long generation;
    private Thread active;
    private ScheduledFuture<?> scheduled;

    public AgentRunReconciler(AgentLedgerService ledger, TaskScheduler scheduler, AgentReconciliationProperties properties) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(properties, "properties");
        if (properties.getBatchSize() < 1 || properties.getBatchSize() > 1_000
                || properties.getInterval() == null || properties.getInterval().compareTo(Duration.ofMillis(250)) < 0
                || properties.getInterval().compareTo(Duration.ofHours(1)) > 0
                || properties.getShutdownTimeout() == null || properties.getShutdownTimeout().isNegative()
                || properties.getShutdownTimeout().compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Agent reconciliation settings are invalid");
        }
        this.batchSize = properties.getBatchSize();
        this.interval = properties.getInterval();
        this.shutdownTimeout = properties.getShutdownTimeout();
    }

    @Override
    public void start() {
        synchronized (monitor) {
            if (running) return;
            if (stopping) throw new IllegalStateException("Agent reconciler is stopping");
            running = true;
            long currentGeneration = ++generation;
            try {
                scheduled = Objects.requireNonNull(scheduler.scheduleWithFixedDelay(() -> poll(currentGeneration), interval),
                        "Agent reconciliation could not be scheduled");
            } catch (RuntimeException failure) {
                running = false;
                throw failure;
            }
        }
    }

    @Override
    public void stop() {
        ScheduledFuture<?> future;
        long deadline = System.nanoTime() + shutdownTimeout.toNanos();
        synchronized (monitor) {
            while (stopping && active != Thread.currentThread()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return;
                try { TimeUnit.NANOSECONDS.timedWait(monitor, remaining); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
            if (stopping) return;
            if (!running && active == null) return;
            running = false;
            generation++;
            stopping = true;
            future = scheduled;
            scheduled = null;
        }
        if (future != null) {
            try { future.cancel(false); }
            catch (RuntimeException failure) {
                log.warn("Agent reconciliation stage=UNSCHEDULE outcome=ERROR errorType={}", failure.getClass().getSimpleName());
            }
        }
        synchronized (monitor) {
            try {
                while (active != null && active != Thread.currentThread()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        log.warn("Agent reconciliation stage=SHUTDOWN outcome=TIMEOUT");
                        break;
                    }
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                stopping = false;
                monitor.notifyAll();
            }
        }
    }

    @Override
    public boolean isRunning() {
        synchronized (monitor) { return running; }
    }

    /** The dispatcher and reconciler must operate on the same ledger instance. */
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    @Override
    public int getPhase() { return Integer.MAX_VALUE - 200; }

    private void poll(long expectedGeneration) {
        synchronized (monitor) {
            if (!running || generation != expectedGeneration || active != null) return;
            active = Thread.currentThread();
        }
        try {
            ledger.reconcileDue(batchSize);
        } catch (RuntimeException failure) {
            log.warn("Agent reconciliation stage=POLL outcome=ERROR errorType={}", failure.getClass().getSimpleName());
        } finally {
            synchronized (monitor) {
                active = null;
                monitor.notifyAll();
            }
        }
    }
}
