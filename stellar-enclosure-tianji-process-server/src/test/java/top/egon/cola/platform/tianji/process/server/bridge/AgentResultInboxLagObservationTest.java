package top.egon.cola.platform.tianji.process.server.bridge;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.ACTOR;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentDispatchStoreTest.intent;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.PRODUCER;
import static top.egon.cola.platform.tianji.process.server.bridge.AgentResultProducerRegistryTest.event;

class AgentResultInboxLagObservationTest {
    private static final String METER = "tianji.process.agent.result.inbox.decision.delay";
    private static final Instant RECEIVED = Instant.parse("2026-11-05T00:00:00.001234Z");

    @Test
    void lagObservationSeamExists() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.process.server.bridge.AgentResultInboxLagObservation"));
    }

    @Test
    void recordsOnlyCommittedTerminalOutcomesWithFixedTags() {
        var registry = new SimpleMeterRegistry();
        try {
            var observation = AgentResultInboxLagObservation.enabled(() -> registry);
            var outcomes = Map.of(AgentResultReceiptStateEnum.APPLIED, "applied",
                    AgentResultReceiptStateEnum.IGNORED_LATE, "ignored_late", AgentResultReceiptStateEnum.DEAD, "dead");
            var registered = callbacks(() -> observation.recordOnCommit(
                    AgentResultReceiptStateEnum.APPLIED, RECEIVED, RECEIVED.plusNanos(123_000)));
            assertEquals(0, registry.getMeters().size());
            complete(registered, TransactionSynchronization.STATUS_COMMITTED);
            assertEquals(1, registry.getMeters().size());
            complete(callbacks(() -> {
                observation.recordOnCommit(AgentResultReceiptStateEnum.IGNORED_LATE, RECEIVED, RECEIVED.plusNanos(123_000));
                observation.recordOnCommit(AgentResultReceiptStateEnum.DEAD, RECEIVED, RECEIVED.plusNanos(123_000));
            }), TransactionSynchronization.STATUS_COMMITTED);
            assertEquals(3, registry.getMeters().size());
            for (var outcome : outcomes.values()) {
                var timer = registry.get(METER).tag("outcome", outcome).timer();
                assertEquals(1, timer.count());
                assertEquals(123_000, timer.totalTime(TimeUnit.NANOSECONDS), 0.01);
                assertEquals(List.of("outcome"), timer.getId().getTags().stream().map(tag -> tag.getKey()).toList());
            }
        } finally {
            registry.close();
        }
    }

    @Test
    void rollbackAndNonTerminalOrReplayDoNotRecord() throws Exception {
        var registry = new SimpleMeterRegistry();
        try (var schema = mockConstruction(ProcessApiSchemaVerifier.class);
             var templates = mockConstruction(JdbcTemplate.class)) {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            var manager = new DataSourceTransactionManager(source);
            var transaction = new TransactionTemplate(manager);
            var registration = new AgentResultProducerRegistry.Registration(PRODUCER, "agent-east", "process-east", ACTOR);
            var producers = new AgentResultProducerRegistry(List.of(registration));
            var inbox = new AgentResultInbox(source, manager, "tianji_process", mock(AgentDispatchStore.class), producers,
                    Duration.ofDays(1), AgentResultInboxLagObservation.enabled(() -> registry));
            var jdbc = templates.constructed().getFirst();
            var event = event(intent("job-one", AgentDispatchStore.State.ACKNOWLEDGED, "run-one"),
                    "run-one", 3, "b".repeat(64));
            var received = new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, RECEIVED);
            var pending = new AgentResultInbox.Entry(PRODUCER, event, received, RECEIVED.plusSeconds(3600), 0);
            var row = terminalRow(event);
            when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
            when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AgentResultInbox.Entry>>any(), any(Object[].class)))
                    .thenAnswer(call -> List.of(call.<RowMapper<AgentResultInbox.Entry>>getArgument(1).mapRow(row, 0)));

            transaction.executeWithoutResult(status -> {
                assertEquals(AgentResultReceiptStateEnum.APPLIED,
                        inbox.finish(registration, pending, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED").state());
                assertTrue(registry.getMeters().isEmpty());
                status.setRollbackOnly();
            });
            assertTrue(registry.getMeters().isEmpty());
            verify(connection).rollback();

            transaction.executeWithoutResult(status -> {
                assertEquals(AgentResultReceiptStateEnum.APPLIED,
                        inbox.finish(registration, pending, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED").state());
                assertTrue(registry.getMeters().isEmpty());
            });
            verify(connection).commit();
            var timer = registry.get(METER).tag("outcome", "applied").timer();
            assertEquals(1, timer.count());
            assertEquals(123_000, timer.totalTime(TimeUnit.NANOSECONDS), 0.01);

            transaction.executeWithoutResult(status -> assertEquals(AgentResultReceiptStateEnum.APPLIED,
                    inbox.find(registration, event.eventId(), true).receipt().state()));
            when(row.getString("state")).thenReturn("DEAD");
            transaction.executeWithoutResult(status -> assertEquals(AgentResultReceiptStateEnum.DEAD,
                    inbox.finish(registration, pending, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED").state()));
            assertEquals(1, timer.count());
            assertEquals(1, registry.getMeters().size());

            when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
            assertThrows(ProcessConflictException.class, () -> transaction.executeWithoutResult(status ->
                    inbox.finish(registration, pending, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED")));
            verify(jdbc, times(4)).query(anyString(),
                    org.mockito.ArgumentMatchers.<RowMapper<AgentResultInbox.Entry>>any(), any(Object[].class));
            assertEquals(1, timer.count());
        } finally {
            registry.close();
        }
    }

    @Test
    void unavailableRegistryAndMeterFailureAreIsolated() {
        var registry = new SimpleMeterRegistry();
        var failingMeter = new SimpleMeterRegistry();
        try {
            var observation = AgentResultInboxLagObservation.enabled(() -> registry);
            var applied = AgentResultReceiptStateEnum.APPLIED;
            observation.recordOnCommit(applied, RECEIVED, RECEIVED);
            assertTrue(callbacks(() -> {
                observation.recordOnCommit(AgentResultReceiptStateEnum.RECEIVED, RECEIVED, RECEIVED);
                observation.recordOnCommit(applied, RECEIVED, null);
                observation.recordOnCommit(applied, RECEIVED, RECEIVED.minusNanos(1));
                observation.recordOnCommit(applied, Instant.MIN, Instant.MAX);
                AgentResultInboxLagObservation.disabled().recordOnCommit(applied, RECEIVED, RECEIVED);
            }).isEmpty());
            complete(callbacks(() -> observation.recordOnCommit(applied, RECEIVED, RECEIVED)),
                    TransactionSynchronization.STATUS_UNKNOWN);
            assertTrue(registry.getMeters().isEmpty());

            complete(callbacks(() -> observation.recordOnCommit(applied, RECEIVED, RECEIVED)),
                    TransactionSynchronization.STATUS_COMMITTED);
            var timer = registry.get(METER).tag("outcome", "applied").timer();
            assertEquals(1, timer.count());
            assertEquals(0, timer.totalTime(TimeUnit.NANOSECONDS), 0.01);

            failingMeter.config().meterFilter(new MeterFilter() {
                @Override public Meter.Id map(Meter.Id id) {
                    throw new IllegalStateException("private meter diagnostic");
                }
            });
            for (var observer : List.of(
                    AgentResultInboxLagObservation.enabled(() -> null),
                    AgentResultInboxLagObservation.enabled(() -> { throw new IllegalStateException("private registry diagnostic"); }),
                    AgentResultInboxLagObservation.enabled(() -> failingMeter))) {
                assertDoesNotThrow(() -> complete(callbacks(() -> observer.recordOnCommit(applied, RECEIVED, RECEIVED)),
                        TransactionSynchronization.STATUS_COMMITTED));
            }
            assertEquals(1, timer.count());
        } finally {
            registry.close();
            failingMeter.close();
        }
    }

    private static ResultSet terminalRow(AgentTerminalEvent event) throws Exception {
        var row = mock(ResultSet.class);
        var json = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        when(row.getString("event_payload")).thenReturn(json.writeValueAsString(event));
        when(row.getLong("tenant_id")).thenReturn(event.tenantId());
        when(row.getString("event_id")).thenReturn(event.eventId());
        when(row.getString("producer_id")).thenReturn(event.producerId());
        when(row.getString("event_sha256")).thenReturn(event.fingerprint());
        when(row.getString("owner_issuer")).thenReturn(event.ownerIssuerSha256());
        when(row.getString("owner_subject")).thenReturn(event.ownerSubjectId());
        when(row.getString("source_id")).thenReturn(event.link().sourceId());
        when(row.getString("scope_type")).thenReturn(event.link().scopeType().name());
        when(row.getString("scope_id")).thenReturn(event.link().scopeInstanceId());
        when(row.getString("occurrence_id")).thenReturn(event.link().activityOccurrenceId());
        when(row.getString("run_id")).thenReturn(event.runId());
        when(row.getLong("terminal_revision")).thenReturn(event.terminalRevision());
        when(row.getString("producer_subject")).thenReturn(PRODUCER.subjectId());
        when(row.getString("producer_issuer")).thenReturn(PRODUCER.issuerFingerprint());
        when(row.getString("state")).thenReturn("APPLIED");
        when(row.getTimestamp("received_at")).thenReturn(Timestamp.from(RECEIVED));
        when(row.getTimestamp("retry_not_after")).thenReturn(Timestamp.from(RECEIVED.plusSeconds(3600)));
        when(row.getTimestamp("decided_at")).thenReturn(Timestamp.from(RECEIVED.plusNanos(123_000)));
        when(row.getString("decision_code")).thenReturn("MESSAGE_APPLIED");
        return row;
    }

    private static List<TransactionSynchronization> callbacks(Runnable observation) {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            observation.run();
            return TransactionSynchronizationManager.getSynchronizations();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static void complete(List<TransactionSynchronization> callbacks, int status) {
        callbacks.forEach(callback -> callback.afterCompletion(status));
    }
}
