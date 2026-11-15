package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.OutboxReceipt;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxAgentTerminalEventPublisherTest {
    private final DataSource source = mock(DataSource.class);
    private final DataSourceTransactionManager manager = new DataSourceTransactionManager(source);
    private final TransactionalOutbox outbox = mock(TransactionalOutbox.class);

    @Test
    void outbox_json_round_trips_through_the_process_side_mapper_without_changing_its_fingerprint() throws Exception {
        var event = event("agent-east");
        var writer = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        byte[] payload = writer.writeValueAsBytes(event);
        assertTrue(payload.length < 16_384);
        var reader = tools.jackson.databind.json.JsonMapper.builder()
                .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        var restored = reader.readValue(payload, AgentTerminalEvent.class);
        assertEquals(event, restored);
        assertEquals(event.fingerprint(), restored.fingerprint());
    }

    @Test
    void publishes_only_a_compact_reference_in_the_exact_transaction_and_logical_route() throws Exception {
        var connection = connection();
        var publisher = publisher();
        var event = event("agent-east");
        assertTrue(publisher.usesDataSource(source));
        assertFalse(publisher.usesDataSource(mock(DataSource.class)));
        assertThrows(RuntimeException.class, () -> publisher.publish(event));
        verifyNoInteractions(outbox);
        when(outbox.enqueue(any())).thenAnswer(call -> {
            OutboxMessage message = call.getArgument(0);
            assertEquals(event.eventId(), message.messageId());
            assertEquals(event.eventId(), message.idempotencyKey());
            assertEquals(OutboxAgentTerminalEventPublisher.CHANNEL, message.channel());
            assertEquals("process-destination", message.destination());
            assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), message.headers());
            String payload = assertInstanceOf(String.class, message.payload());
            var serializer = new top.egon.cola.component.outbox.serialization.JacksonOutboxMessageSerializer(
                    new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
            assertEquals(payload, serializer.serialize(payload, message.contentType()).text());
            assertTrue(payload.contains("2026-10-30T00:01:00Z"));
            assertEquals(event, tools.jackson.databind.json.JsonMapper.builder().build().readValue(payload, AgentTerminalEvent.class));
            return new OutboxReceipt(event.eventId(), event.eventId(), true);
        });
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publish(event));
        verify(connection).commit();
        verify(connection, never()).rollback();
    }

    @Test
    void storage_or_receipt_failure_rolls_back_and_unknown_routes_never_fall_back() throws Exception {
        var connection = connection();
        var publisher = publisher();
        when(outbox.enqueue(any())).thenThrow(new IllegalStateException("controlled storage failure"));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> publisher.publish(event("agent-east"))));
        verify(connection).rollback();
        reset(outbox);
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> publisher.publish(event("another-agent"))));
        var unknownRoute = new OutboxAgentTerminalEventPublisher(source, infrastructure(), outbox, "agent-east", Map.of("other-source", "other-target"));
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> unknownRoute.publish(event("agent-east"))));
        verifyNoInteractions(outbox);
        when(outbox.enqueue(any())).thenReturn(new OutboxReceipt("wrong", "wrong", false));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> publisher.publish(event("agent-east"))));
    }

    @Test
    void activation_is_explicit_and_mismatched_storage_or_arbitrary_destinations_are_rejected() {
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentTerminalOutboxConfiguration.class).run(context ->
                assertTrue(context.getBeansOfType(OutboxAgentTerminalEventPublisher.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentTerminalOutboxConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.terminal-events.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        new ApplicationContextRunner().withUserConfiguration(TianjiAgentTerminalOutboxConfiguration.class)
                .withPropertyValues("stellar.tianji.agent.runtime-v2.enabled=true").run(context ->
                        assertTrue(context.getStartupFailure().getMessage().contains("Agent Runtime v2 admission")));
        assertThrows(IllegalArgumentException.class, () -> new OutboxAgentTerminalEventPublisher(mock(DataSource.class), infrastructure(),
                outbox, "agent-east", Map.of("process-east", "process-destination")));
        assertThrows(IllegalArgumentException.class, () -> new OutboxAgentTerminalEventPublisher(source, infrastructure(), outbox,
                "agent-east", Map.of("process-east", "https://unconfigured.example/callback")));
    }

    @Test
    void traceCarrierIsOptionalAndTransactionOwned() throws Exception {
        var connection = connection();
        var event = event("agent-east");
        var observation = mock(AgentResultSpanObservation.class);
        String carrier = "tianji-link-v1:00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01";
        var enabled = new OutboxAgentTerminalEventPublisher(source, infrastructure(), outbox, "agent-east",
                Map.of("process-east", "process-destination"), observation);
        assertThrows(RuntimeException.class, () -> enabled.publish(event));
        assertThrows(IllegalArgumentException.class, () -> new TransactionTemplate(manager)
                .executeWithoutResult(status -> enabled.publish(event("another-agent"))));
        verifyNoInteractions(observation, outbox);

        when(observation.enqueueCarrier()).thenReturn(carrier);
        var messages = new ArrayList<OutboxMessage>();
        when(outbox.enqueue(any())).thenAnswer(call -> {
            messages.add(call.getArgument(0));
            return new OutboxReceipt(event.eventId(), event.eventId(), messages.size() == 1);
        });
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher().publish(event));
        new TransactionTemplate(manager).executeWithoutResult(status -> enabled.publish(event));
        assertEquals(2, messages.size());
        var original = messages.getFirst();
        var observed = messages.getLast();
        assertNull(original.traceId());
        assertEquals(carrier, observed.traceId());
        assertEquals(original.messageId(), observed.messageId());
        assertEquals(original.idempotencyKey(), observed.idempotencyKey());
        assertEquals(original.channel(), observed.channel());
        assertEquals(original.destination(), observed.destination());
        assertEquals(original.schemaVersion(), observed.schemaVersion());
        assertEquals(original.contentType(), observed.contentType());
        assertEquals(original.payload(), observed.payload());
        assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), observed.headers());
        assertEquals(original.headers(), observed.headers());
        verify(observation, times(1)).enqueueCarrier();
        verify(connection, times(2)).commit();
    }

    @Test
    void both_v2_success_shapes_enqueue_the_exact_canonical_bytes_under_the_original_route() throws Exception {
        var connection = connection();
        var publisher = publisher();
        var artifact = new AgentResultArtifactRefVO("artifact-one", "d".repeat(64), "application/json", 42);
        for (var event : java.util.List.of(eventV2("b".repeat(64), null), eventV2("b".repeat(64), artifact))) {
            assertThrows(RuntimeException.class, () -> publisher.publish(event));
            doAnswer(call -> {
                OutboxMessage message = call.getArgument(0);
                assertEquals(event.eventId(), message.messageId());
                assertEquals(event.eventId(), message.idempotencyKey());
                assertEquals("process-destination", message.destination());
                assertEquals("2", message.schemaVersion());
                assertEquals("application/json", message.contentType());
                assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), message.headers());
                assertArrayEquals(event.canonicalBytes(), assertInstanceOf(String.class, message.payload()).getBytes(StandardCharsets.UTF_8));
                return new OutboxReceipt(event.eventId(), event.eventId(), true);
            }).when(outbox).enqueue(any());
            new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publish(event));
        }
        verify(connection, times(2)).commit();
    }

    static AgentTerminalEventV2 eventV2(String issuer, AgentResultArtifactRefVO artifact) {
        var legacy = event("agent-east");
        Map<String, Object> output = artifact == null ? Map.of("count", 1, "text", "结果") : null;
        var result = AgentTerminalEventV2.resultSha256(legacy.status(), output, artifact, null, SideEffectOutcomeEnum.NONE);
        return new AgentTerminalEventV2(2, legacy.producerId(), AgentTerminalEventV2.eventIdFor(legacy.producerId(), 20, issuer,
                "process-worker", legacy.runId(), legacy.terminalRevision()), 20, issuer, "process-worker",
                legacy.idempotencyKey(), legacy.link(), legacy.definitionKey(), legacy.definitionVersion(), legacy.runId(),
                legacy.terminalRevision(), "node-one", "e".repeat(64), "f".repeat(64), "c".repeat(64), legacy.status(),
                legacy.finishedAt(), result, output, artifact, null, SideEffectOutcomeEnum.NONE);
    }

    private Connection connection() throws Exception {
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        return connection;
    }
    private OutboxInfrastructure infrastructure() { return new OutboxInfrastructure(source, "dataSource", manager, "transactionManager"); }
    private OutboxAgentTerminalEventPublisher publisher() {
        return new OutboxAgentTerminalEventPublisher(source, infrastructure(), outbox, "agent-east", Map.of("process-east", "process-destination"));
    }
    private static AgentTerminalEvent event(String producer) {
        var finished = Instant.parse("2026-10-30T00:01:00Z");
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), finished.minusSeconds(30));
        return new AgentTerminalEvent(1, producer, AgentTerminalEvent.eventIdFor(producer, 20, "b".repeat(64), "process-worker", "run-one", 3),
                20, "b".repeat(64), "process-worker", "intent", link, "research", 2, "run-one", 3,
                AgentRunStatusEnum.COMPLETED, finished, "c".repeat(64), null, "UNKNOWN");
    }
}
