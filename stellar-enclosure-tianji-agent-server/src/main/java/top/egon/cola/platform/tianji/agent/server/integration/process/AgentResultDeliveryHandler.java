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
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO.DecisionEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactQuery;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

/** Delivers a ledger-confirmed reference; lazy ledger resolution breaks the publisher/Outbox/handler construction cycle. */
public final class AgentResultDeliveryHandler implements DeliveryHandler {
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(4096).maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final JsonMapper RUNTIME_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40)
                            .maxStringLength(16_384).maxNameLength(16_384).maxNumberLength(20).maxTokenCount(32_768).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).build();
    private final Supplier<AgentLedgerService> ledgers;
    private final Map<Key, Route> routes;
    private final Set<String> destinations;
    private final Clock clock;
    private final AgentResultSpanObservation observation;
    private final boolean crossProcessSpanLinksEnabled;
    private final AgentResultArtifactVerifier artifacts;
    private static final AgentResultArtifactEvidenceVO ARTIFACT_UNAVAILABLE =
            new AgentResultArtifactEvidenceVO(DecisionEnum.UNAVAILABLE, null, null, null, null);

    public AgentResultDeliveryHandler(Supplier<AgentLedgerService> ledgers, List<Route> routes, Clock clock) {
        this(ledgers, routes, clock, AgentResultSpanObservation.disabled());
    }

    AgentResultDeliveryHandler(Supplier<AgentLedgerService> ledgers, List<Route> routes, Clock clock,
                               AgentResultSpanObservation observation) {
        this(ledgers, routes, clock, observation, false);
    }

    AgentResultDeliveryHandler(Supplier<AgentLedgerService> ledgers, List<Route> routes, Clock clock,
                               AgentResultSpanObservation observation, boolean crossProcessSpanLinksEnabled) {
        this(ledgers, routes, clock, observation, crossProcessSpanLinksEnabled, null);
    }

    AgentResultDeliveryHandler(Supplier<AgentLedgerService> ledgers, List<Route> routes, Clock clock,
                               AgentResultSpanObservation observation, boolean crossProcessSpanLinksEnabled,
                               AgentResultArtifactVerifier artifacts) {
        this.artifacts = artifacts;
        this.observation = Objects.requireNonNull(observation, "observation");
        this.crossProcessSpanLinksEnabled = crossProcessSpanLinksEnabled;
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

    boolean hasRuntimeRoute(OutboxAgentTerminalEventPublisher.RuntimeRouteVO route) {
        var owner = route.owner();
        return routes.containsKey(new Key(route.destination(), owner.tenantId(), route.sourceId(),
                owner.issuerFingerprint(), owner.subjectId()));
    }

    @Override public DeliveryResult deliver(DeliveryContext context) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return retry("TIANJI_RESULT_DELIVERY_TRANSACTION");
        DeliveryEventVO event;
        Route route;
        try {
            event = decode(context);
            route = routes.get(new Key(context.destination(), event.tenantId(), event.sourceId(), event.ownerIssuer(), event.ownerSubject()));
            if (route == null) throw invalid();
        } catch (RuntimeException invalid) { return permanent("TIANJI_RESULT_ENVELOPE_INVALID"); }
        Duration readBudget = Duration.between(clock.instant(), context.deadline()).minus(route.client().timeout()).minusMillis(100);
        if (readBudget.compareTo(Duration.ofSeconds(1)) < 0) return retry("TIANJI_RESULT_DELIVERY_DEADLINE");
        readBudget = readBudget.compareTo(Duration.ofSeconds(5)) > 0 ? Duration.ofSeconds(5) : readBudget;
        try {
            var ledger = Objects.requireNonNull(ledgers.get(), "ledger");
            var query = new GetAgentRunQuery(event.runId());
            boolean exact = event.runtime() == null
                    ? ledger.terminalReference(route.owner(), query, readBudget).filter(event.legacy()::equals).isPresent()
                    : ledger.terminalReferenceV2(route.owner(), query, readBudget).filter(event.runtime()::equals).isPresent();
            if (!exact) return permanent("TIANJI_RESULT_REFERENCE_INVALID");
        } catch (RuntimeException unavailable) { return retry("TIANJI_RESULT_METADATA_UNAVAILABLE"); }
        if (TransactionSynchronizationManager.isActualTransactionActive()) return retry("TIANJI_RESULT_DELIVERY_TRANSACTION");
        if (!hasDeliveryTime(route, context)) return retry("TIANJI_RESULT_DELIVERY_DEADLINE");

        try (var delivery = observation.delivery(context.traceId())) {
            String traceLink = crossProcessSpanLinksEnabled ? delivery.wireCarrier() : null;
            ArtifactDeliveryProofVO proof = null;
            if (event.runtime() != null) {
                if (!route.currentTransport().getAsBoolean()) return permanent("TIANJI_RESULT_DELIVERY_REJECTED");
                if (event.runtime().artifactRef() != null) {
                    proof = verifyArtifact(route, event.runtime(), context);
                    if (proof.evidence().decision() == DecisionEnum.DENY) return permanent("TIANJI_RESULT_ARTIFACT_DENIED");
                    if (!proof.permits(clock.instant())) return retry("TIANJI_RESULT_ARTIFACT_UNAVAILABLE");
                    if (!route.currentTransport().getAsBoolean()) return permanent("TIANJI_RESULT_DELIVERY_REJECTED");
                }
            }
            if ((observation.isEnabled() || event.runtime() != null) && !hasDeliveryTime(route, context)) {
                return retry("TIANJI_RESULT_DELIVERY_DEADLINE");
            }
            if (proof != null && !proof.permits(clock.instant())) return retry("TIANJI_RESULT_ARTIFACT_UNAVAILABLE");
            var receipt = event.runtime() != null ? route.client().receiveV2(event.runtime(), event.original(), traceLink)
                    : traceLink == null ? route.client().receive(event.legacy()) : route.client().receive(event.legacy(), traceLink);
            if (receipt == null || !event.id().equals(receipt.eventId()) || !event.fingerprint().equals(receipt.eventFingerprint())) {
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

    private boolean hasDeliveryTime(Route route, DeliveryContext context) {
        return Duration.between(clock.instant(), context.deadline()).compareTo(route.client().timeout().plusMillis(100)) >= 0;
    }

    private ArtifactDeliveryProofVO verifyArtifact(Route route, AgentTerminalEventV2 event, DeliveryContext context) {
        if (artifacts == null) return new ArtifactDeliveryProofVO(null, ARTIFACT_UNAVAILABLE);
        var now = clock.instant();
        var limit = context.deadline().minus(route.client().timeout()).minusMillis(100);
        var budget = now.plusSeconds(3);
        var deadline = (budget.isBefore(limit) ? budget : limit).truncatedTo(ChronoUnit.MICROS);
        if (!now.isBefore(deadline)) return new ArtifactDeliveryProofVO(null, ARTIFACT_UNAVAILABLE);
        var query = new AgentResultArtifactQuery(AgentResultArtifactQuery.PurposeEnum.DELIVER_TERMINAL, route.owner(), event.link().sourceId(),
                event.producerId(), route.destination(), event.runId(), event.nodeExecutionRef(), event.outputSchemaSha256(), event.artifactRef(), deadline);
        // The registered verifier is guarded and cannot perform I/O under the preceding ledger snapshot.
        var evidence = artifacts.verify(new AgentResultArtifactQuery(query.getPurpose(), query.getOwner(), query.getSourceId(),
                query.getProducerId(), query.getDestinationId(), query.getRunId(), query.getNodeExecutionRef(),
                query.getOutputSchemaSha256(), query.getArtifactRef(), query.getDeadlineAt()));
        return new ArtifactDeliveryProofVO(query, Objects.requireNonNullElse(evidence, ARTIFACT_UNAVAILABLE));
    }

    private record ArtifactDeliveryProofVO(AgentResultArtifactQuery query, AgentResultArtifactEvidenceVO evidence) {
        boolean permits(Instant now) { return evidence.permits(query, now); }
    }

    private DeliveryEventVO decode(DeliveryContext context) {
        if (context == null || !channel().equals(context.channel()) || !"application/json".equalsIgnoreCase(context.contentType())
                || context.payload() == null || context.deadline() == null) throw invalid();
        byte[] original = context.payload().getBytes(StandardCharsets.UTF_8);
        if (original.length == 0 || original.length > 16_384) throw invalid();
        var decoded = switch (context.schemaVersion()) {
            case "1" -> new DeliveryEventVO(JSON.readValue(context.payload(), AgentTerminalEvent.class), null, original);
            case "2" -> {
                var event = RUNTIME_JSON.readValue(context.payload(), AgentTerminalEventV2.class);
                if (event == null || !Arrays.equals(event.canonicalBytes(), original)) throw invalid();
                yield new DeliveryEventVO(null, event, original);
            }
            default -> throw invalid();
        };
        if (!decoded.id().equals(context.messageId())
                || !Map.of("tianji-event-fingerprint", decoded.fingerprint()).equals(context.headers())) throw invalid();
        return decoded;
    }

    private record DeliveryEventVO(AgentTerminalEvent legacy, AgentTerminalEventV2 runtime, byte[] original) {
        String id() { return runtime == null ? legacy.eventId() : runtime.eventId(); }
        String fingerprint() { return runtime == null ? legacy.fingerprint() : runtime.fingerprint(); }
        long tenantId() { return runtime == null ? legacy.tenantId() : runtime.tenantId(); }
        String sourceId() { return runtime == null ? legacy.link().sourceId() : runtime.link().sourceId(); }
        String ownerIssuer() { return runtime == null ? legacy.ownerIssuerSha256() : runtime.ownerIssuerSha256(); }
        String ownerSubject() { return runtime == null ? legacy.ownerSubjectId() : runtime.ownerSubjectId(); }
        String runId() { return runtime == null ? legacy.runId() : runtime.runId(); }
    }

    private static DeliveryResult retry(String code) { return DeliveryResult.retryableFailure(code, code); }
    private static DeliveryResult permanent(String code) { return DeliveryResult.permanentFailure(code, code); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result delivery route is invalid"); }
    private record Key(String destination, long tenantId, String sourceId, String ownerIssuer, String ownerSubject) { }

    public record Route(String destination, String sourceId, TrustedAgentActor owner, HttpAgentResultClient client,
                        BooleanSupplier currentTransport) {
        public Route(String destination, String sourceId, TrustedAgentActor owner, HttpAgentResultClient client) {
            this(destination, sourceId, owner, client, () -> true);
        }
        public Route {
            if (destination == null || !destination.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || sourceId == null
                    || !sourceId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || owner == null
                    || owner.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(owner.issuerFingerprint())
                    || client == null || currentTransport == null || client.timeout() == null || client.timeout().compareTo(Duration.ofMillis(100)) < 0
                    || client.timeout().compareTo(Duration.ofSeconds(60)) > 0) throw invalid();
        }
    }
}
