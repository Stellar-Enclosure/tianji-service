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
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;
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

    public OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                             String producerId, Map<String, String> destinations) {
        this(source, infrastructure, outbox, producerId, destinations, AgentResultSpanObservation.disabled());
    }

    OutboxAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox,
                                      String producerId, Map<String, String> destinations,
                                      AgentResultSpanObservation observation) {
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
    }

    @Override public String producerId() { return producerId; }
    @Override public boolean usesDataSource(DataSource candidate) { return candidate == source; }

    @Override public void publish(AgentTerminalEvent event) {
        guard.requireSelectedTransaction();
        if (event == null || !producerId.equals(event.producerId())) throw invalid();
        String destination = destinations.get(event.link().sourceId());
        if (destination == null) throw invalid();
        String payload = encode(event);
        var receipt = outbox.enqueue(OutboxMessage.builder().messageId(event.eventId()).idempotencyKey(event.eventId())
                .channel(CHANNEL).destination(destination).schemaVersion("1").payload(payload)
                .headers(Map.of("tianji-event-fingerprint", event.fingerprint()))
                .traceId(observation.enqueueCarrier()).build());
        if (receipt == null || !event.eventId().equals(receipt.messageId()) || !event.eventId().equals(receipt.idempotencyKey())) {
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

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent terminal Outbox binding is invalid"); }
}
