package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.*;

class AgentSessionContextHistoryTest {
    private final AgentContextSourceDTO source = source("a".repeat(64), NOW);
    private final AgentRunDTO run = new AgentRunDTO(RUN.runId(), RUN.definitionKey(), RUN.definitionVersion(), "session", RUN.status(), null, false, 1, 2, 0, RUN.deadlineAt(), NOW, null);
    private final AgentRunAuthorization.Request authorization = new AgentRunAuthorization.Request(ACTOR, run.runId(), "assistant", 1, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy", true, run.deadlineAt());

    @Test void duplicate_observations_merge_only_after_current_run_session_and_transaction_fences() throws Exception {
        var dataSource = mock(DataSource.class); var connection = connection(dataSource);
        try (var repositories = mockConstruction(JdbcAgentLedgerRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.lockLeaseForSettlement(ACTOR, LEASE)).thenReturn(true);
            when(repository.sessionContextHistory(ACTOR, "session")).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return new JdbcAgentLedgerRepository.ContextHistoryRows(List.of(row(source), row(source("a".repeat(64), NOW.plusSeconds(1)))), true, false, true, NOW.minusSeconds(1));
            });
        })) {
            var ledger = ledger(dataSource); var history = ledger.sessionContextHistory(ACTOR, LEASE, authorization, "session", Duration.ofSeconds(2));
            assertEquals(List.of(source), history.sources()); assertEquals(Set.of("invocation"), history.invocationIds()); assertTrue(history.complete()); assertTrue(history.tracked()); assertFalse(history.sharedState());
            verify(connection).commit(); verify(repositories.constructed().getFirst(), times(2)).lockLeaseForSettlement(ACTOR, LEASE);
        }
    }

    @Test void conflicting_saved_projection_or_expired_lease_requests_rollback_without_source_io() throws Exception {
        var dataSource = mock(DataSource.class); var connection = connection(dataSource);
        try (var repositories = mockConstruction(JdbcAgentLedgerRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.lockLeaseForSettlement(ACTOR, LEASE)).thenReturn(true);
            when(repository.sessionContextHistory(ACTOR, "session")).thenAnswer(call -> new JdbcAgentLedgerRepository.ContextHistoryRows(List.of(row(source), row(source("b".repeat(64), NOW))), true, false, true, NOW));
        })) {
            var ledger = ledger(dataSource); assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                    () -> ledger.sessionContextHistory(ACTOR, LEASE, authorization, "session", Duration.ofSeconds(2))).code());
            verify(connection).rollback(); verify(connection, never()).commit();
            doReturn(false).when(repositories.constructed().getFirst()).lockLeaseForSettlement(ACTOR, LEASE);
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                    () -> ledger.sessionContextHistory(ACTOR, LEASE, authorization, "session", Duration.ofSeconds(2))).code());
            verify(repositories.constructed().getFirst(), times(1)).sessionContextHistory(ACTOR, "session");
        }
    }

    @Test void a_new_session_generation_does_not_borrow_native_event_coverage_from_an_old_one() throws Exception {
        var dataSource = mock(DataSource.class); connection(dataSource);
        try (var repositories = mockConstruction(JdbcAgentLedgerRepository.class, (repository, ignored) -> {
            when(repository.databaseNow()).thenReturn(NOW); when(repository.lockLeaseForSettlement(ACTOR, LEASE)).thenReturn(true);
            when(repository.sessionContextHistory(ACTOR, "session")).thenAnswer(call -> new JdbcAgentLedgerRepository.ContextHistoryRows(List.of(row(source)), true, true, true, NOW.plusSeconds(1)));
        })) {
            var ledger = ledger(dataSource); var history = ledger.sessionContextHistory(ACTOR, LEASE, authorization, "session", Duration.ofSeconds(2));
            assertTrue(history.invocationIds().isEmpty()); assertTrue(history.sharedState()); assertEquals(1, history.sources().size());
        }
    }

    private AgentLedgerService ledger(DataSource source) {
        var ledger = spy(new AgentLedgerService(source)); doReturn(new AgentLedgerService.ExecutionMaterial(run, SNAPSHOT, authorization)).when(ledger).executionMaterial(eq(ACTOR), any()); return ledger;
    }
    private static Connection connection(DataSource source) throws Exception { var connection = mock(Connection.class); when(connection.getAutoCommit()).thenReturn(true); when(source.getConnection()).thenReturn(connection); return connection; }
    private static JdbcAgentLedgerRepository.ContextRow row(AgentContextSourceDTO source) {
        var metadata = mock(AgentContextMetadataDTO.class); when(metadata.nativeSessionId()).thenReturn("session"); when(metadata.nativeInvocationId()).thenReturn("invocation"); when(metadata.sources()).thenReturn(List.of(source));
        var snapshot = mock(AgentContextSnapshotDTO.class); when(snapshot.metadata()).thenReturn(metadata); when(snapshot.capturedAt()).thenReturn(NOW);
        return new JdbcAgentLedgerRepository.ContextRow(snapshot, "c".repeat(64));
    }
    private static AgentContextSourceDTO source(String rendered, Instant observed) { return new AgentContextSourceDTO(AgentContextSourceKindEnum.MEMORY, AgentContextRoleEnum.USER,
            "memory:fact", "1", "opaque-reference", "a".repeat(64), rendered, 10, observed); }
}
