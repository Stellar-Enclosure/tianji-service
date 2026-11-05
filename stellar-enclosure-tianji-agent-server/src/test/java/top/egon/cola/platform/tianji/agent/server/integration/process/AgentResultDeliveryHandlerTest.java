package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentResultDeliveryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final HttpAgentResultClient client = mock(HttpAgentResultClient.class);
    private final TrustedAgentActor owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private AgentTerminalEvent event;
    private AgentResultDeliveryHandler handler;

    @BeforeEach void setup() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        event = new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, owner.issuerFingerprint(), owner.subjectId(), "run-one", 3),
                20, owner.issuerFingerprint(), owner.subjectId(), "intent", link, "research", 2, "run-one", 3, AgentRunStatusEnum.COMPLETED, NOW, "b".repeat(64), null, "UNKNOWN");
        when(client.timeout()).thenReturn(Duration.ofSeconds(3));
        handler = new AgentResultDeliveryHandler(() -> ledger, List.of(new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)),
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(ledger.terminalReference(owner, new GetAgentRunQuery(event.runId()), Duration.ofSeconds(5))).thenReturn(Optional.of(event));
    }
    @AfterEach void clear() { TransactionSynchronizationManager.setActualTransactionActive(false); }

    @Test
    void a_durable_receipt_is_success_without_claiming_native_application_or_forwarding_headers() {
        when(client.receive(event)).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
        });
        assertEquals(OutboxAgentTerminalEventPublisher.CHANNEL, handler.channel());
        assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(context()).kind());
        verify(ledger).terminalReference(owner, new GetAgentRunQuery(event.runId()), Duration.ofSeconds(5));
        verify(client).receive(event);
        assertThrows(IllegalArgumentException.class, () -> handler.validateDestination("https://unregistered.example/"));
    }

    @Test
    void invalid_or_credential_bearing_envelopes_never_reach_the_ledger_or_network() {
        var original = context();
        for (var invalid : List.of(
                context(original.payload(), Map.of("Authorization", "private-token", "tianji-event-fingerprint", event.fingerprint()), NOW.plusSeconds(30)),
                context(original.payload(), Map.of("tianji-event-fingerprint", "c".repeat(64)), NOW.plusSeconds(30)),
                context(original.payload() + " {}", original.headers(), NOW.plusSeconds(30)),
                context(" ".repeat(16_385), original.headers(), NOW.plusSeconds(30)))) {
            assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(invalid).kind());
        }
        verifyNoInteractions(ledger);
        verify(client, never()).receive(any());
    }

    @Test
    void a_stale_or_forged_reference_is_not_delivered_even_with_a_valid_envelope_fingerprint() {
        when(ledger.terminalReference(owner, new GetAgentRunQuery(event.runId()), Duration.ofSeconds(5))).thenReturn(Optional.empty());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context()).kind());
        verify(client, never()).receive(any());
    }

    @Test
    void ambient_transactions_expired_deadlines_and_metadata_failure_are_deferred_without_http() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, handler.deliver(context()).kind());
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, handler.deliver(context(context().payload(), context().headers(), NOW.plusSeconds(3))).kind());
        verifyNoInteractions(ledger);
        when(ledger.terminalReference(owner, new GetAgentRunQuery(event.runId()), Duration.ofSeconds(5))).thenThrow(new IllegalStateException("private database details"));
        var failure = handler.deliver(context());
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, failure.kind());
        assertFalse(failure.message().contains("private"));
        verify(client, never()).receive(any());
    }

    @Test
    void transport_policy_distinguishes_denial_conflict_retryable_auth_and_dependency_errors() {
        for (int status : List.of(401, 403, 404, 409, 429, 503, 0)) {
            doThrow(new ProcessClientException(status, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null)).when(client).receive(event);
            var outcome = handler.deliver(context());
            assertEquals(status == 403 || status == 409 ? DeliveryResult.Kind.PERMANENT_FAILURE : DeliveryResult.Kind.RETRYABLE_FAILURE, outcome.kind());
        }
        doThrow(new ProcessClientException(0, "TIANJI_INVALID_RESPONSE", null, null)).when(client).receive(event);
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context()).kind());
    }

    @Test
    void lazy_ledger_resolution_allows_the_publisher_outbox_handler_graph_to_initialize() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        var infrastructure = new OutboxInfrastructure(source, "dataSource", manager, "transactionManager");
        try (var context = new GenericApplicationContext()) {
            context.registerBean(TransactionalOutbox.class, () -> {
                context.getBean(DeliveryHandlerRegistry.class);
                return mock(TransactionalOutbox.class);
            });
            context.registerBean(DeliveryHandlerRegistry.class, () -> new DeliveryHandlerRegistry(List.of(context.getBean(AgentResultDeliveryHandler.class))));
            context.registerBean(OutboxAgentTerminalEventPublisher.class, () -> new OutboxAgentTerminalEventPublisher(source, infrastructure,
                    context.getBean(TransactionalOutbox.class), "agent-east", Map.of("process-east", "process-one")));
            context.registerBean(AgentLedgerService.class, () -> AgentLedgerService.withTerminalEvents(source, context.getBean(OutboxAgentTerminalEventPublisher.class)));
            context.registerBean(AgentResultDeliveryHandler.class, () -> new AgentResultDeliveryHandler(() -> context.getBean(AgentLedgerService.class),
                    List.of(new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)), Clock.fixed(NOW, ZoneOffset.UTC)));
            assertDoesNotThrow(context::refresh);
            assertTrue(context.getBean(AgentLedgerService.class).hasTerminalPublisher(context.getBean(OutboxAgentTerminalEventPublisher.class)));
            verifyNoInteractions(source);
        }
    }

    @Test
    void verifiedAttemptLinksWithoutChangingWireOrRetry() {
        String carrier = "tianji-link-v1:00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01";
        var observation = mock(AgentResultSpanObservation.class);
        var close = mock(AgentResultSpanObservation.Delivery.class);
        when(observation.isEnabled()).thenReturn(true);
        when(observation.delivery(carrier)).thenReturn(close);
        var enabled = new AgentResultDeliveryHandler(() -> ledger, List.of(
                new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)),
                Clock.fixed(NOW, ZoneOffset.UTC), observation);

        var original = context();
        var invalid = new DeliveryContext(original.messageId(), original.channel(), original.destination(),
                original.payload(), original.contentType(), original.schemaVersion(), Map.of(), carrier,
                original.attempt(), original.maxAttempts(), original.deadline());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, enabled.deliver(invalid).kind());
        verifyNoInteractions(ledger, observation);
        verify(client, never()).receive(any());

        var context = new DeliveryContext(original.messageId(), original.channel(), original.destination(),
                original.payload(), original.contentType(), original.schemaVersion(), original.headers(), carrier,
                original.attempt(), original.maxAttempts(), original.deadline());
        when(client.receive(event)).thenThrow(new ProcessClientException(503, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null));
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, enabled.deliver(context).kind());
        var calls = inOrder(ledger, observation, client, close);
        calls.verify(ledger).terminalReference(owner, new GetAgentRunQuery(event.runId()), Duration.ofSeconds(5));
        calls.verify(observation).delivery(carrier);
        calls.verify(client).receive(event);
        calls.verify(close).close();
        verify(client, times(1)).receive(event);
    }

    @Test
    void terminalSpanLinksFlagDefaultsOffWithoutEnablingPublication() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var stellar = assertInstanceOf(Map.class, yaml.getObject().get("stellar"));
        var tianji = assertInstanceOf(Map.class, stellar.get("tianji"));
        var agent = assertInstanceOf(Map.class, tianji.get("agent"));
        var terminal = assertInstanceOf(Map.class, agent.get("terminal-events"));
        assertEquals(Map.of("span-links-enabled", false), terminal);
    }

    private DeliveryContext context() { return context(mapper.writeValueAsString(event), Map.of("tianji-event-fingerprint", event.fingerprint()), NOW.plusSeconds(30)); }
    private DeliveryContext context(String payload, Map<String, String> headers, Instant deadline) {
        return new DeliveryContext(event.eventId(), handler.channel(), "process-one", payload, "application/json", "1", headers, null, 1, 5, deadline);
    }
}
