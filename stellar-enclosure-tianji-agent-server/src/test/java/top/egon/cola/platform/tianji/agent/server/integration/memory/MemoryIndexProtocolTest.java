package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.outbox.api.OutboxMessage;
import top.egon.cola.component.outbox.api.OutboxReceipt;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.OutboxJdbcAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.OutboxStateMachineAutoConfiguration;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxAutoConfiguration;
import top.egon.cola.component.outbox.delivery.DeliveryContext;
import top.egon.cola.component.outbox.delivery.DeliveryResult;
import top.egon.cola.component.outbox.dispatch.OutboxDispatcher;
import top.egon.cola.component.outbox.transaction.OutboxAfterCommitBuffer;
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
    @Timeout(45)
    @EnabledIfSystemProperty(named = "tianji.memory.index.outbox.postgres.test.url", matches = ".+")
    void the_real_jdbc_outbox_preserves_the_full_key_and_dispatches_the_exact_reference() {
        String url = System.getProperty("tianji.memory.index.outbox.postgres.test.url");
        String role = System.getProperty("tianji.memory.index.outbox.postgres.test.runtime-user");
        assertTrue(url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}"));
        assertTrue(role != null && role.matches("tianji_it_runtime_[0-9a-f]{12}"));
        var owner = new DriverManagerDataSource(url);
        var admin = new JdbcTemplate(owner);
        assertNull(admin.queryForObject("SELECT to_regnamespace('tianji_agent')::text", String.class));
        var migrations = Flyway.configure().dataSource(owner).schemas("tianji_agent").defaultSchema("tianji_agent")
                .table("tianji_agent_outbox_schema_history").locations("classpath:db/transactional-outbox/postgresql")
                .cleanDisabled(true).load();
        assertEquals(1, migrations.migrate().migrationsExecuted);
        migrations.validate();
        admin.execute("GRANT USAGE ON SCHEMA tianji_agent TO " + role);
        admin.execute("GRANT SELECT, INSERT, UPDATE ON tianji_agent.egon_cola_outbox_message TO " + role);
        admin.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA tianji_agent TO " + role);
        var source = new DriverManagerDataSource(url);
        source.setUsername(role);
        var jdbc = new JdbcTemplate(source);
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_user=? AND NOT r.rolsuper AND NOT r.rolbypassrls AND NOT r.rolcreaterole
                  AND NOT r.rolcreatedb AND NOT r.rolreplication AND NOT pg_has_role(d.datdba,'MEMBER')
                  AND NOT has_database_privilege(d.oid,'CREATE') AND NOT has_schema_privilege('tianji_agent','CREATE')
                FROM pg_roles r CROSS JOIN pg_database d WHERE r.rolname=current_user AND d.datname=current_database()
                """, Boolean.class, role)));
        var event = event();
        var manager = new DataSourceTransactionManager(source);
        var work = mock(MemoryIndexWork.class);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(work).apply(any(), any());
        var handler = new MemoryIndexDeliveryHandler(() -> work, Clock.systemUTC());
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TransactionalOutboxAutoConfiguration.class,
                        OutboxJdbcAutoConfiguration.class, OutboxStateMachineAutoConfiguration.class))
                .withBean(DataSource.class, () -> source).withBean(PlatformTransactionManager.class, () -> manager)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules())
                .withBean(MemoryIndexDeliveryHandler.class, () -> handler)
                .withBean(OutboxAfterCommitBuffer.class, () -> mock(OutboxAfterCommitBuffer.class))
                .withBean("outboxDeliveryExecutor", SyncTaskExecutor.class, SyncTaskExecutor::new)
                .withPropertyValues("egon.cola.component.transactional-outbox.enabled=true",
                        "egon.cola.component.transactional-outbox.storage.type=JDBC",
                        "egon.cola.component.transactional-outbox.storage.jdbc.schema=tianji_agent",
                        "egon.cola.component.transactional-outbox.polling.enabled=false",
                        "egon.cola.component.transactional-outbox.cleanup.enabled=false",
                        "egon.cola.component.transactional-outbox.annotation.enabled=false",
                        "egon.cola.component.transactional-outbox.state-machine.execution-timeout=PT1S",
                        "egon.cola.component.transactional-outbox.state-machine.claim-evaluation-timeout=PT3S")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure(), () -> String.valueOf(ctx.getStartupFailure()));
                    var publisher = new OutboxMemoryIndexPublication(source, ctx.getBean(OutboxInfrastructure.class),
                            ctx.getBean(TransactionalOutbox.class), new MemoryIndexPublication.Binding(MODEL, 2, "agent_memory_vector_v1"));
                    var transaction = new TransactionTemplate(manager);
                    transaction.executeWithoutResult(status -> {
                        publisher.publish(event);
                        status.setRollbackOnly();
                    });
                    assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.egon_cola_outbox_message", Integer.class));
                    transaction.executeWithoutResult(status -> publisher.publish(event));
                    transaction.executeWithoutResult(status -> publisher.publish(event));
                    var stored = jdbc.queryForMap("SELECT message_id,idempotency_key,payload,status FROM tianji_agent.egon_cola_outbox_message");
                    assertEquals(event.eventId().substring("memory-index:".length()), stored.get("message_id"));
                    assertEquals(event.eventId(), stored.get("idempotency_key"));
                    assertEquals(MemoryIndexEventJson.encode(event), stored.get("payload"));
                    assertEquals("PENDING", stored.get("status"));
                    var dispatcher = ctx.getBean(OutboxDispatcher.class);
                    dispatcher.submitDue();
                    dispatcher.submitDue();
                    assertEquals("SUCCEEDED", jdbc.queryForObject("SELECT status FROM tianji_agent.egon_cola_outbox_message", String.class));
                    verify(work).apply(eq(event), any(Instant.class));
                });
    }

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
            OutboxMessage message = call.getArgument(0);
            assertEquals(event.eventId().substring("memory-index:".length()), message.messageId());
            assertEquals(event.eventId(), message.idempotencyKey()); assertEquals(OutboxMemoryIndexPublication.CHANNEL, message.channel());
            assertEquals(OutboxMemoryIndexPublication.DESTINATION, message.destination());
            assertEquals(Map.of(OutboxMemoryIndexPublication.FINGERPRINT, event.fingerprint()), message.headers());
            String json = assertInstanceOf(String.class, message.payload());
            var serializer = new top.egon.cola.component.outbox.serialization.JacksonOutboxMessageSerializer(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());
            assertEquals(json, serializer.serialize(json, message.contentType()).text()); assertEquals(event, MemoryIndexEventJson.decode(json));
            return new OutboxReceipt(message.messageId(), event.eventId(), true);
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
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, "1".repeat(64), event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION)).kind());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, event.fingerprint(), "other")).kind()); verifyNoInteractions(work);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "private dependency detail")).when(work).apply(any(), any());
        var result = handler.deliver(context(event, event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION));
        assertEquals(DeliveryResult.Kind.RETRYABLE_FAILURE, result.kind()); assertFalse(result.message().contains("private dependency detail"));
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "private mismatch detail")).when(work).apply(any(), any());
        assertEquals(DeliveryResult.Kind.PERMANENT_FAILURE, handler.deliver(context(event, event.fingerprint(), OutboxMemoryIndexPublication.DESTINATION)).kind());
    }

    private static DeliveryContext context(MemoryIndexPublication.Event event, String fingerprint, String destination) {
        return context(event, event.eventId().substring("memory-index:".length()), fingerprint, destination);
    }
    private static DeliveryContext context(MemoryIndexPublication.Event event, String messageId, String fingerprint, String destination) {
        return new DeliveryContext(messageId, OutboxMemoryIndexPublication.CHANNEL, destination, MemoryIndexEventJson.encode(event), "application/json", "1",
                Map.of(OutboxMemoryIndexPublication.FINGERPRINT, fingerprint), "trace", 1, 10, NOW.plusSeconds(10));
    }
    private static MemoryIndexPublication.Event event() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var id = MemoryIndexPublication.vectorDocumentId(20, "a".repeat(64), owner, "research", "memory", 1, MODEL);
        return new MemoryIndexPublication.Event(MemoryIndexPublication.Action.BUILD, 20, "a".repeat(64), owner, "research", "memory", 1, "d".repeat(64), MODEL, id, NOW);
    }
}
