package top.egon.cola.platform.tianji.agent.server.integration.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.OutboxJdbcAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryHandler;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.store.OutboxStore;
import top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianji.client.HttpAgentResultClient;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real JDBC Outbox/ledger test with a controlled receiver client, only in a supplied fresh loopback database. */
@EnabledIfSystemProperty(named = "tianji.agent.outbox.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentTerminalOutboxPostgresTest {
    @Test
    void terminal_fence_and_actual_outbox_commit_atomically_then_deliver_outside_the_snapshot_transaction() {
        String url = System.getProperty("tianji.agent.outbox.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Outbox test requires an empty loopback tianji_it_ database");
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        var agent = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true).load();
        agent.migrate(); agent.validate();
        var component = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .table("tianji_agent_outbox_schema_history").baselineOnMigrate(true).baselineVersion("0")
                .locations("classpath:db/transactional-outbox/postgresql").cleanDisabled(true).load();
        assertEquals(1, component.migrate().migrationsExecuted); component.validate();
        var manager = new DataSourceTransactionManager(source);
        var owner = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://idp.example/20");
        var ledgerReference = new AtomicReference<AgentLedgerService>();
        var client = mock(HttpAgentResultClient.class);
        when(client.timeout()).thenReturn(Duration.ofSeconds(3));
        var handler = new AgentResultDeliveryHandler(ledgerReference::get, List.of(new AgentResultDeliveryHandler.Route("process-one", "process-east", owner, client)), Clock.systemUTC());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TransactionalOutboxAutoConfiguration.class,
                        OutboxJdbcAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class))
                .withBean(DataSource.class, () -> source).withBean(PlatformTransactionManager.class, () -> manager)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(DeliveryHandler.class, () -> handler).withBean(OutboxAfterCommitBuffer.class, () -> mock(OutboxAfterCommitBuffer.class))
                .withPropertyValues("egon.cola.component.transactional-outbox.enabled=true", "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_agent", "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.component.transactional-outbox.cleanup.enabled=false", "egon.cola.component.transactional-outbox.annotation.enabled=false",
                        "egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S", "egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT3S")
                .run(context -> assertDoesNotThrow(() -> {
                    assertNull(context.getStartupFailure());
                    var publisher = new OutboxAgentTerminalEventPublisher(source, context.getBean(OutboxInfrastructure.class), context.getBean(TransactionalOutbox.class),
                            "agent-east", Map.of("process-east", "process-one"));
                    var ledger = AgentLedgerService.withTerminalEvents(source, publisher); ledgerReference.set(ledger);
                    var linked = new AgentLinkedSubmissionService(source, ledger);
                    var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10), 5, 2, 1000, 500,
                            new BigDecimal("2.00"), "USD", 2, Set.of(), "retry-v1", "approval-v1");
                    var definition = ledger.publish(owner, new AgentDefinitionSpecDTO("review", 1, "SEQUENTIAL", null, "1", "1",
                            new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"), policy, Map.of("mode", "safe")));
                    var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", definition.contentSha256(),
                            ledger.databaseNow().plusSeconds(30).truncatedTo(ChronoUnit.MICROS));
                    var command = new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-one", "business", "trace",
                            new AgentInputDTO("private input", List.of(), Map.of()), null), link);
                    var query = new LinkedAgentLookupQuery(command.start().idempotencyKey(), link);
                    var run = linked.submit(owner, command);
                    jdbc.execute("""
                            CREATE FUNCTION tianji_agent.test_terminal_outbox_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                            BEGIN RAISE EXCEPTION 'controlled Outbox insert failure'; END $$
                            """);
                    jdbc.execute("CREATE TRIGGER test_terminal_outbox_failure BEFORE INSERT ON tianji_agent.egon_cola_outbox_message "
                            + "FOR EACH ROW EXECUTE FUNCTION tianji_agent.test_terminal_outbox_failure()");
                    assertThrows(RuntimeException.class, () -> linked.close(owner, query));
                    assertEquals(AgentRunStatusEnum.QUEUED, ledger.get(owner, new GetAgentRunQuery(run.runId())).status());
                    assertFalse(linked.resolve(owner, query).orElseThrow().closed());
                    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class));
                    jdbc.execute("DROP TRIGGER test_terminal_outbox_failure ON tianji_agent.egon_cola_outbox_message");
                    assertEquals(AgentRunStatusEnum.CANCELLED, linked.close(owner, query).run().status());
                    var event = ledger.terminalReference(owner, new GetAgentRunQuery(run.runId()), Duration.ofSeconds(5)).orElseThrow();
                    assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class));
                    var store = context.getBean(OutboxStore.class);
                    var claimed = store.claimDue(1, "terminal-test-worker", Duration.ofSeconds(30)).getFirst();
                    assertEquals(event.eventId(), claimed.messageId());
                    assertFalse(claimed.payload().contains("private input"));
                    assertEquals(Map.of("tianji-event-fingerprint", event.fingerprint()), claimed.headers());
                    when(client.receive(event)).thenAnswer(call -> {
                        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                        return new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, Instant.now().truncatedTo(ChronoUnit.MICROS));
                    });
                    var delivery = new DeliveryContext(claimed.messageId(), claimed.channel(), claimed.destination(), claimed.payload(), claimed.contentType(), claimed.schemaVersion(),
                            claimed.headers(), claimed.traceId(), claimed.attemptCount(), claimed.maxAttempts(), Instant.now().plusSeconds(10));
                    assertEquals(DeliveryResult.Kind.SUCCESS, handler.deliver(delivery).kind());
                    assertTrue(store.markSucceeded(claimed.id(), "terminal-test-worker", 0));
                    verify(client).receive(event);
                }));
    }
}
