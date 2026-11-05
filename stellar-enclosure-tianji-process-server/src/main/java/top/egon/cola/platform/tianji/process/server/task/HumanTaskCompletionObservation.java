package top.egon.cola.platform.tianji.process.server.task;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Optional local timing for a newly committed Human Task completion. */
public final class HumanTaskCompletionObservation {
    private static final Logger log = LoggerFactory.getLogger(HumanTaskCompletionObservation.class);
    private static final String METER = "tianji.process.human.task.completion.duration";
    private static final String DESCRIPTION = "Native task creation to applied receipt time for committed Human Task completions";
    private static final HumanTaskCompletionObservation DISABLED = new HumanTaskCompletionObservation(null);

    private final Supplier<MeterRegistry> registry;
    private final AtomicBoolean unavailableReported = new AtomicBoolean();

    private HumanTaskCompletionObservation(Supplier<MeterRegistry> registry) {
        this.registry = registry;
    }

    public static HumanTaskCompletionObservation disabled() {
        return DISABLED;
    }

    public static HumanTaskCompletionObservation enabled(Supplier<MeterRegistry> registry) {
        return new HumanTaskCompletionObservation(Objects.requireNonNull(registry, "registry"));
    }

    void recordOnCommit(HumanTaskScopeTypeEnum scope, Instant createdAt, Instant dueAt, Instant appliedAt) {
        if (registry == null || scope == null || createdAt == null || appliedAt == null || appliedAt.isBefore(createdAt)
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;

        long elapsedNanos;
        try {
            elapsedNanos = Duration.between(createdAt, appliedAt).toNanos();
        } catch (ArithmeticException unrepresentable) {
            return;
        }
        String scopeTag = switch (scope) {
            case PROCESS -> "process";
            case CASE -> "case";
        };
        String due = dueAt == null ? "unscheduled" : appliedAt.isAfter(dueAt) ? "late" : "on_time";
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) record(scopeTag, due, elapsedNanos);
                }
            });
        } catch (RuntimeException unavailable) {
            reportUnavailable();
        }
    }

    private void record(String scope, String due, long elapsedNanos) {
        try {
            MeterRegistry available = registry.get();
            if (available != null) {
                Timer.builder(METER).description(DESCRIPTION).tag("scope", scope).tag("due", due).register(available)
                        .record(Duration.ofNanos(elapsedNanos));
            }
        } catch (RuntimeException unavailable) {
            reportUnavailable();
        }
    }

    private void reportUnavailable() {
        if (unavailableReported.compareAndSet(false, true)) {
            log.warn("Process Human Task completion timing observation unavailable");
        }
    }
}
