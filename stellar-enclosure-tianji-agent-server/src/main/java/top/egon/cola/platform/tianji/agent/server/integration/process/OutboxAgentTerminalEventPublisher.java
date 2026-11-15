package top.egon.cola.platform.tianji.agent.server.integration.process;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.transaction.OutboxTransactionGuard;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactQuery;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;

/** Adapts the ledger's synchronous publication seam to the existing transaction-bound JDBC Outbox. */
public final class OutboxAgentTerminalEventPublisher implements AgentTerminalEventPublisher {
    public static final String CHANNEL = "tianji-agent-result";
    private static final JsonMapper WIRE = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();
    private final DataSource source;
    private final TransactionalOutbox outbox;
    private final OutboxTransactionGuard guard;
    private final String producerId;
    private final Map<String, String> destinations;
    private final AgentResultSpanObservation observation;
    private final AgentResultArtifactVerifier artifacts;
    private final Predicate<RuntimeRouteVO> runtimeRoutes;

    public OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                             String producerId, Map<String, String> destinations) {
        this(source, infrastructure, outbox, producerId, destinations, AgentResultSpanObservation.disabled());
    }

    OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                      String producerId, Map<String, String> destinations,
                                      AgentResultSpanObservation observation) {
        this(source, infrastructure, outbox, producerId, destinations, observation, null);
    }

    OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                      String producerId, Map<String, String> destinations,
                                      AgentResultSpanObservation observation, AgentResultArtifactVerifier artifacts) {
        this(source, infrastructure, outbox, producerId, destinations, observation, artifacts, route -> true);
    }

    OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                      String producerId, Map<String, String> destinations,
                                      AgentResultSpanObservation observation, AgentResultArtifactVerifier artifacts,
                                      Predicate<RuntimeRouteVO> runtimeRoutes) {
        if (source == null || infrastructure == null || source != infrastructure.dataSource()
                || !(infrastructure.transactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() != source
                || producerId == null || !producerId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || destinations == null || destinations.isEmpty() || destinations.size() > 1_000) throw invalid();
        destinations.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                    || value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
        });
        this.source = source;
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.guard = new OutboxTransactionGuard(source);
        this.producerId = producerId;
        this.destinations = Map.copyOf(destinations);
        this.observation = Objects.requireNonNull(observation, "observation");
        this.artifacts = artifacts;
        this.runtimeRoutes = Objects.requireNonNull(runtimeRoutes, "runtimeRoutes");
    }

    @Override public String producerId() { return producerId; }
    @Override public boolean usesDataSource(DataSource candidate) { return candidate == source; }

    @Override public String runtimeDestination(TrustedAgentActor owner, String sourceId) {
        String destination = sourceId == null ? null : destinations.get(sourceId);
        if (destination == null || owner == null || owner.subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || !runtimeRoutes.test(new RuntimeRouteVO(owner, sourceId, destination))) return null;
        return destination;
    }

    @Override public AgentResultArtifactEvidenceVO verifyArtifact(AgentResultArtifactQuery query) {
        if (artifacts == null || query == null || query.getPurpose() != AgentResultArtifactQuery.PurposeEnum.PREPARE_TERMINAL
                || !producerId.equals(query.getProducerId()) || query.getDestinationId() == null
                || !query.getDestinationId().equals(runtimeDestination(query.getOwner(), query.getSourceId()))
                || org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            return AgentTerminalEventPublisher.super.verifyArtifact(query);
        }
        return artifacts.verify(query);
    }

    @Override public void publish(AgentTerminalEvent event) {
        guard.requireSelectedTransaction();
        if (event == null || !producerId.equals(event.producerId())) throw invalid();
        String destination = destinations.get(event.link().sourceId());
        if (destination == null) throw invalid();
        enqueue(event.eventId(), event.fingerprint(), destination, "1", encode(event));
    }

    @Override public void publish(AgentTerminalEventV2 event) {
        guard.requireSelectedTransaction();
        if (event == null || !producerId.equals(event.producerId())) throw invalid();
        var owner = new TrustedAgentActor(event.tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                event.ownerSubjectId(), event.ownerIssuerSha256());
        String destination = runtimeDestination(owner, event.link().sourceId());
        if (destination == null) throw invalid();
        enqueue(event.eventId(), event.fingerprint(), destination, "2", new String(event.canonicalBytes(), StandardCharsets.UTF_8));
    }

    private void enqueue(String eventId, String fingerprint, String destination, String version, String payload) {
        var receipt = outbox.enqueue(OutboxMessage.builder().messageId(eventId).idempotencyKey(eventId)
                .channel(CHANNEL).destination(destination).schemaVersion(version).payload(payload)
                .headers(Map.of("tianji-event-fingerprint", fingerprint))
                .traceId(observation.enqueueCarrier()).build());
        if (receipt == null || !eventId.equals(receipt.messageId()) || !eventId.equals(receipt.idempotencyKey())) {
            throw new IllegalStateException("Agent terminal Outbox receipt does not match its event");
        }
    }

    private static String encode(AgentTerminalEvent event) {
        try {
            String json = WIRE.writeValueAsString(event);
            if (json.getBytes(StandardCharsets.UTF_8).length > 16_384) throw invalid();
            return json;
        } catch (JsonProcessingException failure) { throw invalid(); }
    }

    record RuntimeRouteVO(TrustedAgentActor owner, String sourceId, String destination) { }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent terminal Outbox binding is invalid"); }
}
