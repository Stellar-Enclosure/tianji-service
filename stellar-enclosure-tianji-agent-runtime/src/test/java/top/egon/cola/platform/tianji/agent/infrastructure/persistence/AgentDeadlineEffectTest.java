package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.*;

/** Controlled SQL and transactions; actual deadline contention remains an opt-in PostgreSQL check. */
class AgentDeadlineEffectTest {
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery(RUN.runId());
    private static final AgentRunLease EXPIRED_HINT = new AgentRunLease(LEASE.runId(), LEASE.owner(), LEASE.epoch(), NOW.minusSeconds(1));

    @Test void repository_fences_the_exact_owner_run_revision_and_execution_generation_at_database_deadline() {
        var jdbc = mock(JdbcTemplate.class);
        var stored = stored(timedOut(), null);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.StoredRun>>any(), any(Object[].class)))
                .thenReturn(List.of(stored));
        var repository = new JdbcAgentLedgerRepository(jdbc, new AgentJsonCodec());
        assertEquals(Optional.of(stored), repository.expireDeadline(ACTOR, EXPIRED_HINT, RUN.revision(), "result"));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.StoredRun>>any(),
                eq("result"), eq(ACTOR.tenantId()), eq(ACTOR.subjectType().name()), eq(ACTOR.issuerFingerprint()),
                eq(ACTOR.subjectId()), eq(RUN.runId()), eq(RUN.revision()), eq(LEASE.owner()), eq(LEASE.epoch()));
        String normalized = sql.getValue().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        String predicate = normalized.substring(normalized.indexOf("where"), normalized.indexOf("returning"));
        for (var field : List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "run_id", "revision", "lease_owner", "lease_epoch")) {
            assertTrue(predicate.contains(field + "=?"), field);
        }
        assertTrue(predicate.contains("status='running'"));
        assertTrue(predicate.contains("attempt>0"));
        assertTrue(predicate.contains("deadline_at<=clock_timestamp()"));
        assertFalse(predicate.contains("lease_expires_at"));
        assertFalse(predicate.contains("cancel_requested"));
        for (var assignment : List.of("status='timed_out'", "revision=revision+1", "last_event_sequence=last_event_sequence+1",
                "lease_epoch=lease_epoch+1", "lease_owner=null", "lease_expires_at=null", "active_model_calls=0", "active_tool_calls=0",
                "finished_at=clock_timestamp()", "error_code='tianji_run_timed_out'")) assertTrue(normalized.contains(assignment), assignment);
    }

    @Test void repository_returns_empty_on_a_lost_fence_without_an_unfenced_fallback() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.StoredRun>>any(), any(Object[].class)))
                .thenReturn(List.of());
        var repository = new JdbcAgentLedgerRepository(jdbc, new AgentJsonCodec());
        assertTrue(repository.expireDeadline(ACTOR, EXPIRED_HINT, RUN.revision(), "result").isEmpty());
        verify(jdbc, times(1)).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<JdbcAgentLedgerRepository.StoredRun>>any(), any(Object[].class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void all_frozen_effects_commit_with_budget_unknown_and_the_existing_status_event() throws Exception {
        for (String effect : List.of("CONFIRMED", "NONE", "UNKNOWN")) {
            try (var fixture = new Fixture()) {
                assertEquals(timedOut(), fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), effect));
                var resultJson = ArgumentCaptor.forClass(String.class);
                var order = inOrder(fixture.repository, fixture.connection);
                order.verify(fixture.repository).run(ACTOR, RUN.runId());
                order.verify(fixture.repository).expireDeadline(eq(ACTOR), eq(EXPIRED_HINT), eq(RUN.revision()), resultJson.capture());
                order.verify(fixture.repository).markOutstandingBudgetUnknown(ACTOR.tenantId(), RUN.runId());
                order.verify(fixture.repository).databaseNow();
                var event = ArgumentCaptor.forClass(AgentEventDTO.class);
                order.verify(fixture.repository).insertEvent(eq(ACTOR.tenantId()), event.capture());
                order.verify(fixture.connection).commit();
                var result = new AgentJsonCodec().readResult(resultJson.getValue());
                assertEquals(effect, result.sideEffectOutcome());
                assertEquals(AgentRunStatusEnum.TIMED_OUT, result.status());
                assertEquals(AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(), result.errorCode());
                assertEquals(Map.of(), result.output());
                assertEquals("RUN_STATUS_CHANGED", event.getValue().type());
                assertEquals(Map.of("from", "RUNNING", "status", "TIMED_OUT"), event.getValue().payload());
                assertEquals(timedOut().lastEventSequence(), event.getValue().sequence());
                verify(fixture.connection, never()).rollback();
                verify(fixture.repository, never()).expireDeadline(eq(ACTOR), anyString(), anyLong(), anyString());
            }
        }
    }

    @Test void wrong_owner_epoch_or_revision_cannot_write_budget_or_event_or_fall_back() throws Exception {
        for (var lease : List.of(EXPIRED_HINT, new AgentRunLease(LEASE.runId(), "older-worker", LEASE.epoch(), NOW.minusSeconds(1)),
                new AgentRunLease(LEASE.runId(), LEASE.owner(), LEASE.epoch() - 1, NOW.minusSeconds(1)))) {
            try (var fixture = new Fixture()) {
                when(fixture.repository.expireDeadline(eq(ACTOR), eq(lease), anyLong(), anyString())).thenReturn(Optional.empty());
                var failure = assertThrows(AgentLedgerException.class,
                        () -> fixture.service.expireDeadline(ACTOR, lease, QUERY, RUN.revision() + 1, "CONFIRMED"));
                assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, failure.code());
                assertNull(failure.getCause());
                verify(fixture.repository).expireDeadline(eq(ACTOR), eq(lease), eq(RUN.revision() + 1), anyString());
                verify(fixture.repository, never()).expireDeadline(eq(ACTOR), anyString(), anyLong(), anyString());
                verify(fixture.repository, never()).markOutstandingBudgetUnknown(anyLong(), anyString());
                verify(fixture.repository, never()).insertEvent(anyLong(), any());
                verify(fixture.connection).rollback();
                verify(fixture.connection, never()).commit();
            }
        }
    }

    @Test void invalid_commands_fail_before_opening_a_transaction_or_reading_the_run() throws Exception {
        try (var fixture = new Fixture()) {
            var failures = List.<org.junit.jupiter.api.function.Executable>of(
                    () -> fixture.service.expireDeadline(null, EXPIRED_HINT, QUERY, RUN.revision(), "NONE"),
                    () -> fixture.service.expireDeadline(ACTOR, null, QUERY, RUN.revision(), "NONE"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, null, RUN.revision(), "NONE"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, new GetAgentRunQuery("other-run"), RUN.revision(), "NONE"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, -1, "NONE"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), null),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), "confirmed"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), " CONFIRMED"),
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), "PRIVATE_OBSERVER_TEXT"));
            for (var command : failures) {
                var failure = assertThrows(AgentLedgerException.class, command);
                assertEquals(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, failure.code());
                assertNull(failure.getCause());
                assertFalse(failure.getMessage().contains("PRIVATE_OBSERVER_TEXT"));
            }
            verifyNoInteractions(fixture.repository);
            verify(fixture.source, never()).getConnection();
        }
    }

    @Test void event_failure_rolls_back_the_same_deadline_and_budget_transaction() throws Exception {
        try (var fixture = new Fixture()) {
            doThrow(new IllegalStateException("event unavailable")).when(fixture.repository).insertEvent(anyLong(), any());
            assertThrows(IllegalStateException.class, () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), "CONFIRMED"));
            verify(fixture.repository).markOutstandingBudgetUnknown(ACTOR.tenantId(), RUN.runId());
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
        }
    }

    @Test void missing_owned_run_is_not_found_before_deadline_or_budget_writes() throws Exception {
        try (var fixture = new Fixture()) {
            when(fixture.repository.run(ACTOR, RUN.runId())).thenReturn(Optional.empty());
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                    () -> fixture.service.expireDeadline(ACTOR, EXPIRED_HINT, QUERY, RUN.revision(), "NONE")).code());
            verify(fixture.repository, never()).expireDeadline(any(), any(AgentRunLease.class), anyLong(), anyString());
            verify(fixture.repository, never()).markOutstandingBudgetUnknown(anyLong(), anyString());
            verify(fixture.connection).rollback();
        }
    }

    @Test void the_original_deadline_api_keeps_attempted_runs_unknown() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.expireDeadline(ACTOR, QUERY, RUN.revision());
            var resultJson = ArgumentCaptor.forClass(String.class);
            verify(fixture.repository).expireDeadline(eq(ACTOR), eq(RUN.runId()), eq(RUN.revision()), resultJson.capture());
            assertEquals("UNKNOWN", new AgentJsonCodec().readResult(resultJson.getValue()).sideEffectOutcome());
            verify(fixture.repository, never()).expireDeadline(any(), any(AgentRunLease.class), anyLong(), anyString());
            verify(fixture.connection).commit();
        }
    }

    private static AgentRunDTO timedOut() {
        return new AgentRunDTO(RUN.runId(), RUN.definitionKey(), RUN.definitionVersion(), RUN.sessionId(), AgentRunStatusEnum.TIMED_OUT,
                null, false, RUN.attempt(), RUN.revision() + 1, RUN.lastEventSequence() + 1, RUN.deadlineAt(), RUN.createdAt(), NOW);
    }

    private static JdbcAgentLedgerRepository.StoredRun stored(AgentRunDTO run, String resultJson) {
        return new JdbcAgentLedgerRepository.StoredRun(run, "a".repeat(64), resultJson, "correlation", null, LEASE.owner(), LEASE.epoch(), EXPIRED_HINT.expiresAt(), null);
    }

    private static final class Fixture implements AutoCloseable {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final org.mockito.MockedConstruction<JdbcAgentLedgerRepository> construction;
        final JdbcAgentLedgerRepository repository;
        final AgentLedgerService service;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            construction = mockConstruction(JdbcAgentLedgerRepository.class);
            service = new AgentLedgerService(source);
            repository = construction.constructed().getFirst();
            when(repository.run(ACTOR, RUN.runId())).thenReturn(Optional.of(stored(RUN, null)));
            when(repository.databaseNow()).thenReturn(NOW);
            when(repository.expireDeadline(eq(ACTOR), any(AgentRunLease.class), anyLong(), anyString())).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.hasResource(source));
                return Optional.of(stored(timedOut(), invocation.getArgument(3)));
            });
            when(repository.expireDeadline(eq(ACTOR), anyString(), anyLong(), anyString())).thenAnswer(invocation -> Optional.of(stored(timedOut(), invocation.getArgument(3))));
            doAnswer(invocation -> { assertTrue(TransactionSynchronizationManager.hasResource(source)); return null; })
                    .when(repository).insertEvent(anyLong(), any());
        }

        @Override public void close() { construction.close(); }
    }
}
