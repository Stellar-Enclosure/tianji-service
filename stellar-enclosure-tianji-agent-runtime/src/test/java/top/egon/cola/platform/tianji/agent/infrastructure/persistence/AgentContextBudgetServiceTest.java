package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.*;

class AgentContextBudgetServiceTest {
    private final DataSource source = mock(DataSource.class);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final JdbcAgentLedgerRepository repository = mock(JdbcAgentLedgerRepository.class);
    private Connection connection;
    private AgentBudgetService service;

    @BeforeEach void setup() throws Exception {
        connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(ledger.databaseNow()).thenReturn(NOW);
        when(repository.databaseNow()).thenReturn(NOW);
        when(repository.lockRunForBudget(ACTOR, RUN.runId())).thenReturn(true);
        when(ledger.snapshot(ACTOR, new GetAgentRunQuery(RUN.runId()))).thenReturn(SNAPSHOT);
        when(ledger.executionMaterial(ACTOR, new GetAgentRunQuery(RUN.runId()))).thenReturn(new AgentLedgerService.ExecutionMaterial(RUN, SNAPSHOT, AUTHORIZATION));
        when(repository.insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any())).thenReturn(1);
        when(repository.reserveBudget(ACTOR, LEASE, REQUEST, POLICY)).thenReturn(1);
        when(repository.budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false)).thenReturn(Optional.of(reserved()));
        when(repository.contextPolicyMatches(eq(ACTOR), eq(RUN.runId()), any(), anyString())).thenReturn(true);
        when(repository.insertContextSnapshot(eq(ACTOR), eq(LEASE), eq(REQUEST.reservationId()), anyString(), anyString())).thenReturn(1);
        when(repository.validLease(ACTOR, LEASE)).thenReturn(true);
        service = new AgentBudgetService(ledger, repository, new TransactionTemplate(new DataSourceTransactionManager(source)));
    }

    @Test
    void budget_counters_and_context_are_prepared_before_one_commit() throws Exception {
        assertTrue(service.reserveBudget(ACTOR, LEASE, REQUEST, metadata()).newlyCreated());
        var order = inOrder(repository, connection);
        order.verify(repository).insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any());
        order.verify(repository).reserveBudget(ACTOR, LEASE, REQUEST, POLICY);
        order.verify(repository).contextPolicyMatches(eq(ACTOR), eq(RUN.runId()), eq(metadata()), anyString());
        order.verify(repository).insertContextSnapshot(eq(ACTOR), eq(LEASE), eq(REQUEST.reservationId()), anyString(), anyString());
        order.verify(repository).budgetReservation(ACTOR, RUN.runId(), REQUEST.reservationId(), false);
        order.verify(connection).commit(); verify(connection, never()).rollback();
    }

    @Test
    void a_context_insert_failure_requests_rollback_of_the_entire_budget_transaction() throws Exception {
        when(repository.insertContextSnapshot(eq(ACTOR), eq(LEASE), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("controlled metadata write failure"));
        assertThrows(IllegalStateException.class, () -> service.reserveBudget(ACTOR, LEASE, REQUEST, metadata()));
        verify(repository).reserveBudget(ACTOR, LEASE, REQUEST, POLICY);
        verify(connection).rollback(); verify(connection, never()).commit();
    }

    @Test
    void policy_drift_or_an_expired_fence_cannot_leave_a_committed_budget_without_context() throws Exception {
        when(repository.contextPolicyMatches(eq(ACTOR), anyString(), any(), anyString())).thenReturn(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> service.reserveBudget(ACTOR, LEASE, REQUEST, metadata())).code());
        verify(repository, never()).insertContextSnapshot(any(), any(), anyString(), anyString(), anyString());
        verify(connection).rollback();
        when(repository.contextPolicyMatches(eq(ACTOR), anyString(), any(), anyString())).thenReturn(true);
        when(repository.insertContextSnapshot(eq(ACTOR), eq(LEASE), anyString(), anyString(), anyString())).thenReturn(0);
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                assertThrows(AgentLedgerException.class, () -> service.reserveBudget(ACTOR, LEASE, REQUEST, metadata())).code());
        verify(connection, times(2)).rollback(); verify(connection, never()).commit();
    }

    @Test
    void replay_requires_the_original_context_and_never_retrofits_an_older_reservation() throws Exception {
        when(repository.insertBudgetReservation(eq(ACTOR), eq(LEASE), eq(REQUEST), any())).thenReturn(0);
        var context = metadata();
        var stored = new AgentContextSnapshotDTO(RUN.runId(), REQUEST.reservationId(), 1, 1, LEASE.epoch(), context, NOW);
        when(repository.contextSnapshot(ACTOR, RUN.runId(), REQUEST.reservationId())).thenReturn(Optional.of(
                new JdbcAgentLedgerRepository.ContextRow(stored, new AgentContextJsonCodec().fingerprint(context))));
        assertFalse(service.reserveBudget(ACTOR, LEASE, REQUEST, context).newlyCreated());
        when(repository.contextSnapshot(ACTOR, RUN.runId(), REQUEST.reservationId())).thenReturn(Optional.empty());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                assertThrows(AgentLedgerException.class, () -> service.reserveBudget(ACTOR, LEASE, REQUEST, context)).code());
        verify(repository, never()).reserveBudget(any(), any(), any(), any());
        verify(repository, never()).insertContextSnapshot(any(), any(), anyString(), anyString(), anyString());
        verify(connection).commit(); verify(connection).rollback();
    }

    @Test
    void mismatched_owner_or_estimate_and_ambient_transactions_are_rejected_before_storage() {
        var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20");
        assertThrows(AgentLedgerException.class, () -> service.reserveBudget(other, LEASE, REQUEST, metadata()));
        assertThrows(AgentLedgerException.class, () -> service.reserveBudget(ACTOR, LEASE,
                new AgentBudgetRequest(REQUEST.reservationId(), AgentBudgetRequest.Kind.MODEL, null, 21, 10, REQUEST.estimatedCost()), metadata()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> service.reserveBudget(ACTOR, LEASE, REQUEST, metadata())); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(source, repository, ledger);
    }

    @Test
    void old_reservations_do_not_query_the_optional_context_schema() throws Exception {
        assertTrue(service.reserveBudget(ACTOR, LEASE, REQUEST).newlyCreated());
        verify(repository, never()).contextPolicyMatches(any(), anyString(), any(), anyString());
        verify(repository, never()).insertContextSnapshot(any(), any(), anyString(), anyString(), anyString());
        verify(repository, never()).contextSnapshot(any(), anyString(), anyString());
        verify(connection).commit();
    }
}
