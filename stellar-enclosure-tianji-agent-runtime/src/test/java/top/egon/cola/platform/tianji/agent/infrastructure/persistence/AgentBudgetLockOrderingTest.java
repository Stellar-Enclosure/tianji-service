package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.*;

/** Controlled transactions prove the common lock order; real lock competition is an opt-in PostgreSQL check. */
class AgentBudgetLockOrderingTest {
    private static final AgentBudgetSettlement ACTUAL = new AgentBudgetSettlement(4L, 3L, new BigDecimal("0.02"));

    @Test void a_new_reservation_locks_the_owned_run_before_policy_or_budget_access() throws Exception {
        var fixture = new Fixture();
        assertTrue(fixture.service.reserveBudget(ACTOR, LEASE, REQUEST).newlyCreated());
        var order = inOrder(fixture.repository, fixture.ledger, fixture.connection);
        order.verify(fixture.repository).lockRunForBudget(ACTOR, RUN.runId());
        order.verify(fixture.ledger).snapshot(ACTOR, new GetAgentRunQuery(RUN.runId()));
        order.verify(fixture.repository).insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any());
        order.verify(fixture.repository).reserveBudget(ACTOR, LEASE, REQUEST, POLICY);
        order.verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
    }

    @Test void reservation_replay_keeps_the_same_run_first_order_and_does_not_reserve_twice() throws Exception {
        var fixture = new Fixture();
        when(fixture.repository.insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any())).thenReturn(0);
        assertFalse(fixture.service.reserveBudget(ACTOR, LEASE, REQUEST).newlyCreated());
        var order = inOrder(fixture.repository, fixture.ledger, fixture.connection);
        order.verify(fixture.repository).lockRunForBudget(ACTOR, RUN.runId());
        order.verify(fixture.ledger).snapshot(ACTOR, new GetAgentRunQuery(RUN.runId()));
        order.verify(fixture.repository).insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any());
        order.verify(fixture.repository).budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false);
        order.verify(fixture.repository).validLease(ACTOR, LEASE);
        order.verify(fixture.connection).commit();
        verify(fixture.repository, never()).reserveBudget(any(), any(), any(), any());
    }

    @Test void reserved_settlement_locks_the_run_before_reading_or_locking_the_budget() throws Exception {
        var fixture = new Fixture();
        var settled = settled(AgentBudgetReservation.State.SETTLED, ACTUAL);
        when(fixture.repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false))
                .thenReturn(Optional.of(reserved()), Optional.of(settled));
        assertEquals(settled, fixture.service.settleBudget(ACTOR, LEASE, REQUEST.reservationId(), ACTUAL));
        var order = inOrder(fixture.repository, fixture.connection);
        order.verify(fixture.repository).lockRunForBudget(ACTOR, RUN.runId());
        order.verify(fixture.repository).budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false);
        order.verify(fixture.repository).lockLeaseForSettlement(ACTOR, LEASE);
        order.verify(fixture.repository).budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), true);
        order.verify(fixture.repository).adjustBudget(eq(ACTOR), eq(LEASE), eq(AgentBudgetRequest.Kind.MODEL), eq(-16L), eq(-7L), eq(new BigDecimal("-0.08")));
        order.verify(fixture.repository).settleBudget(eq(ACTOR.tenantId()), eq(RUN.runId()), eq(REQUEST.reservationId()), eq(AgentBudgetReservation.State.SETTLED), eq(ACTUAL), any());
        order.verify(fixture.connection).commit();
    }

    @Test void settled_and_unknown_replays_lock_identity_without_requiring_a_live_lease() throws Exception {
        for (var state : List.of(AgentBudgetReservation.State.SETTLED, AgentBudgetReservation.State.UNKNOWN)) {
            var fixture = new Fixture();
            var actual = state == AgentBudgetReservation.State.SETTLED ? ACTUAL : new AgentBudgetSettlement(null, null, null);
            var stored = settled(state, actual);
            when(fixture.repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false)).thenReturn(Optional.of(stored));
            var expiredHint = new AgentRunLease(LEASE.runId(), LEASE.owner(), LEASE.epoch(), NOW.minusSeconds(1));
            assertEquals(stored, fixture.service.settleBudget(ACTOR, expiredHint, REQUEST.reservationId(), actual));
            var order = inOrder(fixture.repository, fixture.connection);
            order.verify(fixture.repository).lockRunForBudget(ACTOR, RUN.runId());
            order.verify(fixture.repository).budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false);
            order.verify(fixture.connection).commit();
            verify(fixture.repository, never()).validLease(any(), any());
            verify(fixture.repository, never()).lockLeaseForSettlement(any(), any());
            verify(fixture.repository, never()).adjustBudget(any(), any(), any(), anyLong(), anyLong(), any());
            verifyNoInteractions(fixture.ledger);
        }
    }

    @Test void the_new_lock_does_not_weaken_settlement_content_or_epoch_checks() throws Exception {
        var fixture = new Fixture();
        when(fixture.repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false))
                .thenReturn(Optional.of(settled(AgentBudgetReservation.State.SETTLED, ACTUAL)));
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, assertThrows(AgentLedgerException.class,
                () -> fixture.service.settleBudget(ACTOR, LEASE, REQUEST.reservationId(), new AgentBudgetSettlement(5L, 3L, new BigDecimal("0.02")))).code());
        var otherEpoch = new AgentRunLease(LEASE.runId(), LEASE.owner(), LEASE.epoch() + 1, LEASE.expiresAt());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> fixture.service.settleBudget(ACTOR, otherEpoch, REQUEST.reservationId(), ACTUAL)).code());
        verify(fixture.repository, times(2)).lockRunForBudget(ACTOR, RUN.runId());
        verify(fixture.connection, times(2)).rollback();
        verify(fixture.connection, never()).commit();
        verify(fixture.repository, never()).adjustBudget(any(), any(), any(), anyLong(), anyLong(), any());
    }

    @Test void missing_owned_runs_stop_before_private_policy_or_budget_reads_and_writes() throws Exception {
        var fixture = new Fixture();
        when(fixture.repository.lockRunForBudget(ACTOR, RUN.runId())).thenReturn(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> fixture.service.reserveBudget(ACTOR, LEASE, REQUEST)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> fixture.service.settleBudget(ACTOR, LEASE, REQUEST.reservationId(), ACTUAL)).code());
        verify(fixture.repository, times(2)).lockRunForBudget(ACTOR, RUN.runId());
        verifyNoMoreInteractions(fixture.repository);
        verifyNoInteractions(fixture.ledger);
        verify(fixture.connection, times(2)).rollback();
        verify(fixture.connection, never()).commit();
    }

    @Test void budget_preparation_can_join_one_existing_same_source_transaction_without_an_early_commit() throws Exception {
        var fixture = new Fixture();
        var outer = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
        assertTrue(Boolean.TRUE.equals(outer.execute(status -> {
            assertTrue(TransactionSynchronizationManager.hasResource(fixture.source));
            assertTrue(fixture.service.reserveBudget(ACTOR, LEASE, REQUEST).newlyCreated());
            try { verify(fixture.connection, never()).commit(); }
            catch (java.sql.SQLException failure) { throw new AssertionError(failure); }
            return true;
        })));
        verify(fixture.source).getConnection();
        verify(fixture.connection).commit();
        verify(fixture.connection, never()).rollback();
        verify(fixture.repository).lockRunForBudget(ACTOR, RUN.runId());
    }

    @Test void the_repository_lock_binds_every_owner_namespace_field_and_keeps_terminal_rows_lockable() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class))).thenReturn(List.of(RUN.runId()));
        var repository = new JdbcAgentLedgerRepository(jdbc, new AgentJsonCodec());
        assertTrue(repository.lockRunForBudget(ACTOR, RUN.runId()));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(ACTOR.tenantId()), eq(ACTOR.subjectType().name()), eq(ACTOR.issuerFingerprint()), eq(ACTOR.subjectId()), eq(RUN.runId()));
        String normalized = sql.getValue().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        for (var field : List.of("tenant_id", "subject_type", "subject_issuer", "subject_id", "run_id")) assertTrue(normalized.contains(field + "=?"));
        assertTrue(normalized.endsWith("forupdate"));
        for (var forbidden : List.of("status", "lease_", "deadline", "cancel_requested")) assertFalse(normalized.contains(forbidden));
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), any(Object[].class))).thenReturn(List.of());
        assertFalse(repository.lockRunForBudget(ACTOR, RUN.runId()));
    }

    private static AgentBudgetReservation settled(AgentBudgetReservation.State state, AgentBudgetSettlement actual) {
        return new AgentBudgetReservation(REQUEST.reservationId(), LEASE.epoch(), REQUEST.kind(), REQUEST.toolVersion(), REQUEST.inputTokens(), REQUEST.outputTokens(), REQUEST.estimatedCost(),
                state, actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false);
    }

    private static final class Fixture {
        final DataSource source = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final JdbcAgentLedgerRepository repository = mock(JdbcAgentLedgerRepository.class);
        final AgentBudgetService service;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
            when(repository.lockRunForBudget(ACTOR, RUN.runId())).thenReturn(true);
            when(repository.databaseNow()).thenReturn(NOW);
            when(ledger.snapshot(ACTOR, new GetAgentRunQuery(RUN.runId()))).thenReturn(SNAPSHOT);
            when(repository.insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any())).thenReturn(1);
            when(repository.reserveBudget(ACTOR, LEASE, REQUEST, POLICY)).thenReturn(1);
            when(repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false)).thenReturn(Optional.of(reserved()));
            when(repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), true)).thenReturn(Optional.of(reserved()));
            when(repository.validLease(ACTOR, LEASE)).thenReturn(true);
            when(repository.lockLeaseForSettlement(ACTOR, LEASE)).thenReturn(true);
            when(repository.adjustBudget(any(), any(), any(), anyLong(), anyLong(), any())).thenReturn(1);
            service = new AgentBudgetService(ledger, repository, new TransactionTemplate(new DataSourceTransactionManager(source)));
        }
    }
}
