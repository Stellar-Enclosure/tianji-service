package top.egon.cola.platform.tianji.agent.server.integration.memory;

import lombok.extern.slf4j.Slf4j;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryIndexWork;

import java.time.Clock;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Uses the component's existing delivery lifecycle; it owns no polling loop, lease table or retry scheduler. */
@Slf4j
public final class MemoryIndexDeliveryHandler implements DeliveryHandler {
    private final Supplier<MemoryIndexWork> work;
    private final Clock clock;
    public MemoryIndexDeliveryHandler(Supplier<MemoryIndexWork> work, Clock clock) { this.work = Objects.requireNonNull(work, "work"); this.clock = Objects.requireNonNull(clock, "clock"); }
    @Override public String channel() { return OutboxMemoryIndexPublication.CHANNEL; }
    @Override public void validateDestination(String destination) { if (!OutboxMemoryIndexPublication.DESTINATION.equals(destination)) throw new IllegalArgumentException("Memory index destination is invalid"); }

    @Override public DeliveryResult deliver(DeliveryContext context) {
        if (context == null || !channel().equals(context.channel()) || !OutboxMemoryIndexPublication.DESTINATION.equals(context.destination())
                || !"1".equals(context.schemaVersion()) || !"application/json".equals(context.contentType()) || context.attempt() < 1
                || context.maxAttempts() < context.attempt() || context.maxAttempts() > 1000
                || !context.headers().keySet().equals(Set.of(OutboxMemoryIndexPublication.FINGERPRINT))) return invalid();
        if (!admitted(context)) return deadline();
        final top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication.Event event;
        try {
            event = MemoryIndexEventJson.decode(context.payload());
            if (!OutboxMemoryIndexPublication.transportMessageId(event).equals(context.messageId())
                    || !event.fingerprint().equals(context.headers().get(OutboxMemoryIndexPublication.FINGERPRINT))) return invalid();
        } catch (IllegalArgumentException malformed) { return invalid(); }
        try {
            if (!admitted(context)) return deadline();
            Objects.requireNonNull(work.get(), "Memory index work").apply(event, context.deadline()); return DeliveryResult.success();
        } catch (AgentLedgerException failed) {
            return switch (failed.code()) {
                case TIANJI_IDEMPOTENCY_CONFLICT, TIANJI_PERMISSION_DENIED, TIANJI_FEATURE_NOT_AVAILABLE, TIANJI_BUDGET_EXCEEDED -> DeliveryResult.permanentFailure(failed.code().name(), "Memory index work requires explicit reconciliation");
                default -> DeliveryResult.retryableFailure(failed.code().name(), "Memory index dependency or continuation work is unavailable");
            };
        } catch (RuntimeException unavailable) {
            log.debug("Memory index delivery dependency unavailable");
            return DeliveryResult.retryableFailure("MEMORY_INDEX_UNAVAILABLE", "Memory index dependency is unavailable");
        }
    }
    private boolean admitted(DeliveryContext context) {
        return context.deadline() != null && clock.instant().isBefore(context.deadline()) && !Thread.currentThread().isInterrupted();
    }

    private static DeliveryResult deadline() {
        return DeliveryResult.retryableFailure("MEMORY_INDEX_DEADLINE", "Memory index delivery budget is unavailable");
    }

    private static DeliveryResult invalid() { return DeliveryResult.permanentFailure("MEMORY_INDEX_PROTOCOL_INVALID", "Memory index protocol is invalid"); }
}
