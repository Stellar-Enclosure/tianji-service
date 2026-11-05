package top.egon.cola.platform.tianji.process.server.bridge;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Optional local timing for the winning persisted Inbox decision. */
public final class AgentResultInboxLagObservation {
    private static final Logger log = LoggerFactory.getLogger(AgentResultInboxLagObservation.class);
    private static final String METER = "tianji.process.agent.result.inbox.decision.delay";
    private static final String DESCRIPTION = "Process Agent result Inbox receipt-to-decision duration for committed terminal decisions";
    private static final AgentResultInboxLagObservation DISABLED = new AgentResultInboxLagObservation(null);

    private final Supplier<MeterRegistry> registry;
    private final AtomicBoolean unavailableReported = new AtomicBoolean();

    private AgentResultInboxLagObservation(Supplier<MeterRegistry> registry) {
        this.registry = registry;
    }

    public static AgentResultInboxLagObservation disabled() {
        return DISABLED;
    }

    public static AgentResultInboxLagObservation enabled(Supplier<MeterRegistry> registry) {
        return new AgentResultInboxLagObservation(Objects.requireNonNull(registry, "registry"));
    }

    void recordOnCommit(AgentResultReceiptStateEnum state, Instant receivedAt, Instant decidedAt) {
        if (registry == null || state == null || state == AgentResultReceiptStateEnum.RECEIVED
                || receivedAt == null || decidedAt == null || decidedAt.isBefore(receivedAt)
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) return;

        long elapsedNanos;
        try {
            elapsedNanos = Duration.between(receivedAt, decidedAt).toNanos();
        } catch (ArithmeticException unrepresentable) {
            return;
        }

        String outcome = switch (state) {
            case APPLIED -> "applied";
            case IGNORED_LATE -> "ignored_late";
            case DEAD -> "dead";
            case RECEIVED -> throw new IllegalStateException("Nonterminal Inbox decision observation");
        };
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) record(outcome, elapsedNanos);
                }
            });
        } catch (RuntimeException unavailable) {
            reportUnavailable();
        }
    }

    private void record(String outcome, long elapsedNanos) {
        try {
            MeterRegistry available = registry.get();
            if (available != null) {
                Timer.builder(METER).description(DESCRIPTION).tag("outcome", outcome).register(available)
                        .record(Duration.ofNanos(elapsedNanos));
            }
        } catch (RuntimeException unavailable) {
            reportUnavailable();
        }
    }

    private void reportUnavailable() {
        if (unavailableReported.compareAndSet(false, true)) {
            log.warn("Process Agent result Inbox timing observation unavailable");
        }
    }
}
