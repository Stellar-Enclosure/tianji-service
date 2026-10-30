package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/** Delivers a ledger-confirmed reference; lazy ledger resolution breaks the publisher/Outbox/handler construction cycle. */
public final class AgentResultDeliveryHandler implements DeliveryHandler {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(4096).maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private final Supplier<AgentLedgerService> ledgers;
    private final Map<Key, Route> routes;
    private final Set<String> destinations;
    private final Clock clock;

    public AgentResultDeliveryHandler(Supplier<AgentLedgerService> ledgers, List<Route> routes, Clock clock) {
        this.ledgers = Objects.requireNonNull(ledgers, "ledgers");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (routes == null || routes.isEmpty() || routes.size() > 1000) throw invalid();
        Map<Key, Route> entries = new HashMap<>();
        for (Route route : routes) {
            if (route == null || entries.putIfAbsent(new Key(route.destination(), route.owner().tenantId(), route.sourceId(),
                    route.owner().issuerFingerprint(), route.owner().subjectId()), route) != null) throw invalid();
        }
        this.routes = Map.copyOf(entries);
        this.destinations = entries.keySet().stream().map(Key::destination).collect(Collectors.toUnmodifiableSet());
    }

    @Override public String channel() { return OutboxAgentTerminalEventPublisher.CHANNEL; }

    @Override public void validateDestination(String destination) {
        if (!destinations.contains(destination)) throw invalid();
    }

    public void validateSourceDestination(String sourceId, String destination) {
        if (routes.keySet().stream().noneMatch(key -> key.sourceId().equals(sourceId) && key.destination().equals(destination))) throw invalid();
    }

    @Override public DeliveryResult deliver(DeliveryContext context) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return retry("TIANJI_RESULT_DELIVERY_TRANSACTION");
        AgentTerminalEvent event;
        Route route;
        try {
            if (context == null || !channel().equals(context.channel()) || !"1".equals(context.schemaVersion())
                    || !"application/json".equalsIgnoreCase(context.contentType()) || context.payload() == null
                    || context.payload().getBytes(StandardCharsets.UTF_8).length > 16_384) throw invalid();
            event = JSON.readValue(context.payload(), AgentTerminalEvent.class);
            if (event == null || !event.eventId().equals(context.messageId())
                    || !context.headers().equals(Map.of("tianji-event-fingerprint", event.fingerprint()))) throw invalid();
            route = routes.get(new Key(context.destination(), event.tenantId(), event.link().sourceId(), event.ownerIssuerSha256(), event.ownerSubjectId()));
            if (route == null) throw invalid();
        } catch (RuntimeException invalid) { return permanent("TIANJI_RESULT_ENVELOPE_INVALID"); }
        if (context.deadline() == null) return permanent("TIANJI_RESULT_ENVELOPE_INVALID");
        Duration remaining = Duration.between(clock.instant(), context.deadline());
        Duration readBudget = remaining.minus(route.client().timeout()).minusMillis(100);
        if (readBudget.compareTo(Duration.ofSeconds(1)) < 0) return retry("TIANJI_RESULT_DELIVERY_DEADLINE");
        readBudget = readBudget.compareTo(Duration.ofSeconds(5)) > 0 ? Duration.ofSeconds(5) : readBudget;
        try {
            var reference = Objects.requireNonNull(ledgers.get(), "ledger").terminalReference(route.owner(), new GetAgentRunQuery(event.runId()), readBudget);
            if (reference.isEmpty() || !reference.get().equals(event)) return permanent("TIANJI_RESULT_REFERENCE_INVALID");
        } catch (RuntimeException unavailable) { return retry("TIANJI_RESULT_METADATA_UNAVAILABLE"); }
        if (TransactionSynchronizationManager.isActualTransactionActive()) return retry("TIANJI_RESULT_DELIVERY_TRANSACTION");
        if (Duration.between(clock.instant(), context.deadline()).compareTo(route.client().timeout().plusMillis(100)) < 0) {
            return retry("TIANJI_RESULT_DELIVERY_DEADLINE");
        }
        try {
            var receipt = route.client().receive(event);
            if (receipt == null || !event.eventId().equals(receipt.eventId()) || !event.fingerprint().equals(receipt.eventFingerprint())) {
                return permanent("TIANJI_RESULT_RECEIPT_INVALID");
            }
            // Every receipt state confirms durable reception; the Process Inbox owns application and recovery.
            return DeliveryResult.success();
        } catch (ProcessClientException failure) {
            int status = failure.httpStatus();
            if (Set.of("TIANJI_INVALID_RESPONSE", "TIANJI_RESPONSE_TOO_LARGE").contains(failure.code())
                    || status >= 400 && status < 500 && !Set.of(401, 404, 408, 429).contains(status)
                    || status >= 300 && status < 400) return permanent("TIANJI_RESULT_DELIVERY_REJECTED");
            return retry("TIANJI_RESULT_DELIVERY_UNAVAILABLE");
        } catch (RuntimeException unavailable) { return retry("TIANJI_RESULT_DELIVERY_UNAVAILABLE"); }
    }

    private static DeliveryResult retry(String code) { return DeliveryResult.retryableFailure(code, code); }
    private static DeliveryResult permanent(String code) { return DeliveryResult.permanentFailure(code, code); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result delivery route is invalid"); }
    private record Key(String destination, long tenantId, String sourceId, String ownerIssuer, String ownerSubject) { }

    public record Route(String destination, String sourceId, TrustedAgentActor owner, HttpAgentResultClient client) {
        public Route {
            if (destination == null || !destination.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || sourceId == null
                    || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || owner == null
                    || owner.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(owner.issuerFingerprint())
                    || client == null || client.timeout() == null || client.timeout().compareTo(Duration.ofMillis(100)) < 0
                    || client.timeout().compareTo(Duration.ofSeconds(60)) > 0) throw invalid();
        }
    }
}
