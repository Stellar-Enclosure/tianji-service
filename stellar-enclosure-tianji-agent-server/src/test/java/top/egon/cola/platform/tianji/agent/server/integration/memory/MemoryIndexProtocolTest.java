package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.OutboxReceipt;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryIndexWork;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemoryIndexProtocolTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String MODEL = "memory-model@1#" + "b".repeat(64);

    @Test
    void compact_reference_protocol_round_trips_and_rejects_unknown_duplicate_and_scalar_coerced_fields() {
        var event = event(); String json = MemoryIndexEventJson.encode(event);
        assertEquals(event, MemoryIndexEventJson.decode(json)); assertTrue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 16_384);
        for (var invalid : List.of(json.replace("\"tenantId\":20", "\"tenantId\":\"20\""), json.replace("\"tenantId\":20", "\"tenantId\":20.0"),
                json.replace("\"tenantId\":20", "\"tenantId\":20,\"tenantId\":21"), json.replace("\"action\":", "\"trusted\":true,\"action\":"), json + " {}")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> MemoryIndexEventJson.decode(invalid)); assertNull(failure.getCause());
        }
    }

    @Test
    void publication_uses_the_selected_transaction_and_actual_raw_json_serializer() throws Exception {
        var source = mock(DataSource.class); var connection = mock(Connection.class); when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var manager = new DataSourceTransactionManager(source); var outbox = mock(TransactionalOutbox.class); var event = event();
        var publisher = new OutboxMemoryIndexPublication(source, new OutboxInfrastructure(source, "dataSource", manager, "transactionManager"), outbox,
                new MemoryIndexPublication.Binding(MODEL, 2, "agent_memory_vector_v1"));
        assertThrows(RuntimeException.class, () -> publisher.publish(event)); verifyNoInteractions(outbox);
        when(outbox.enqueue(any())).thenAnswer(call -> {
            OutboxMessage message = call.getArgument(0); assertEquals(event.eventId(), message.messageId());
            assertEquals(event.eventId(), message.idempotencyKey()); assertEquals(OutboxMemoryIndexPublication.CHANNEL, message.channel());
            assertEquals(OutboxMemoryIndexPublication.DESTINATION, message.destination());
            assertEquals(Map.of(OutboxMemoryIndexPublication.FINGERPRINT, event.fingerprint()), message.headers());
            String json = assertInstanceOf(String.class, message.payload());
            var serializer = new top.egon.cola.component.outbox.serialization.JacksonOutboxMessageSerializer(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
            assertEquals(json, serializer.serialize(json, message.contentType()).text()); assertEquals(event, MemoryIndexEventJson.decode(json));
            return new OutboxReceipt(event.eventId(), event.eventId(), true);
        });
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publish(event)); verify(connection).commit();
        doThrow(new IllegalStateException("controlled queue failure")).when(outbox).enqueue(any());
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publish(event)));
        verify(connection).rollback();
    }

    @Test
    void delivery_rejects_wrong_fingerprints_and_routes_and_classifies_dependency_failures_without_details() {
        var event = event(); var work = mock(MemoryIndexWork.class);
        var handler = new MemoryIndexDeliveryHandler(() -> work, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(context(event, event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION)).kind());
        verify(work).apply(event, NOW.plusSeconds(10)); clearInvocations(work);
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, "c".repeat(64), OutboxMemoryIndexPublication.DESTINATION)).kind());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, event.fingerprint(), "other")).kind()); verifyNoInteractions(work);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private dependency detail")).when(work).apply(any(), any());
        var result = handler.deliver(context(event, event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION));
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, result.kind()); assertFalse(result.message().contains("private dependency detail"));
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "private mismatch detail")).when(work).apply(any(), any());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION)).kind());
    }

    private static DeliveryContext context(MemoryIndexPublication.Event event, String fingerprint, String destination) {
        return new DeliveryContext(event.eventId(), OutboxMemoryIndexPublication.CHANNEL, destination, MemoryIndexEventJson.encode(event), "application/json", "1",
                Map.of(OutboxMemoryIndexPublication.FINGERPRINT, fingerprint), "trace", 1, 10, NOW.plusSeconds(10));
    }
    private static MemoryIndexPublication.Event event() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var id = MemoryIndexPublication.vectorDocumentId(20, "a".repeat(64), owner, "research", "memory", 1, MODEL);
        return new MemoryIndexPublication.Event(MemoryIndexPublication.Action.BUILD, 20, "a".repeat(64), owner, "research", "memory", 1, "d".repeat(64), MODEL, id, NOW);
    }
}
