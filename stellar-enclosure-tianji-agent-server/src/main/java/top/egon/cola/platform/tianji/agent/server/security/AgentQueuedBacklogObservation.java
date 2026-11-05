package top.egon.cola.platform.tianji.agent.server.security;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Samples only after the current dispatch discovery, without a scrape-time database call. */
final class AgentQueuedBacklogObservation {
    private static final Logger log = LoggerFactory.getLogger(AgentQueuedBacklogObservation.class);
    static final String METER = "tianji.agent.run.queue.pending.capped";
    private static final long SAMPLE_INTERVAL = Duration.ofSeconds(30).toNanos();
    private static final long MAX_AGE = Duration.ofSeconds(60).toNanos();

    private final AgentLedgerService ledger;
    private final Supplier<MeterRegistry> registries;
    private final LongSupplier monotonicTime;
    private volatile Sample latest;
    private volatile boolean closed;
    private boolean attempted;
    private long lastAttempt;
    private boolean failureReported;

    AgentQueuedBacklogObservation(AgentLedgerService ledger, Supplier<MeterRegistry> registries, LongSupplier monotonicTime) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.registries = Objects.requireNonNull(registries, "registries");
        this.monotonicTime = Objects.requireNonNull(monotonicTime, "monotonicTime");
    }

    void sampleIfDue() {
        if (closed) return;
        long now = monotonicTime.getAsLong();
        if (attempted && now - lastAttempt < SAMPLE_INTERVAL) return;
        attempted = true;
        lastAttempt = now;
        try {
            MeterRegistry registry = registries.get();
            if (registry == null) {
                latest = null;
                return;
            }
            Gauge.builder(METER, this, AgentQueuedBacklogObservation::value)
                    .description("Capped unexpired and uncancelled Agent runs in QUEUED state, sampled on the primary")
                    .register(registry);
            if (!closed) latest = new Sample(ledger.sampleQueuedBacklog(), now);
        } catch (RuntimeException failure) {
            latest = null;
            if (!failureReported) log.warn("Agent queued backlog observation unavailable");
            failureReported = true;
            return;
        }
        failureReported = false;
    }

    void invalidate() {
        latest = null;
    }

    void close() {
        closed = true;
        latest = null;
    }

    private double value() {
        Sample sample = latest;
        if (closed || sample == null) return Double.NaN;
        long age = monotonicTime.getAsLong() - sample.startedAtNanos();
        return age >= 0 && age < MAX_AGE ? sample.count() : Double.NaN;
    }

    private record Sample(int count, long startedAtNanos) { }
}
