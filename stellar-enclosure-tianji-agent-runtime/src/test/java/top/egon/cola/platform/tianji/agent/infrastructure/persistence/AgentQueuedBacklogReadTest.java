package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AgentQueuedBacklogReadTest {
    @Test
    void reads_one_bounded_primary_scalar_after_setting_physical_read_only() throws SQLException {
        try (var fixture = new Fixture()) {
            assertEquals(257, fixture.ledger.sampleQueuedBacklog());
            assertEquals(List.of("SET TRANSACTION READ ONLY", "SET LOCAL statement_timeout = '100ms'", "count"), fixture.calls);
            verify(fixture.connection).commit();
        }
    }

    @Test
    void real_empty_is_zero_and_failed_or_untrusted_reads_are_unavailable() throws SQLException {
        try (var fixture = new Fixture()) {
            fixture.count = 0;
            assertEquals(0, fixture.ledger.sampleQueuedBacklog());
            fixture.count = null;
            unavailable(fixture.ledger);
            fixture.count = 258;
            unavailable(fixture.ledger);
            fixture.failure = true;
            unavailable(fixture.ledger);
        }
    }

    @Test
    void refuses_ambient_and_interrupted_calls_before_jdbc() throws SQLException {
        try (var fixture = new Fixture()) {
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            transaction.executeWithoutResult(status -> unavailable(fixture.ledger));
            TransactionSynchronizationManager.initSynchronization();
            try {
                unavailable(fixture.ledger);
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            Thread.currentThread().interrupt();
            try {
                unavailable(fixture.ledger);
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            assertTrue(fixture.calls.isEmpty());
        }
    }

    private static void unavailable(AgentLedgerService ledger) {
        var rejected = assertThrows(AgentLedgerException.class, ledger::sampleQueuedBacklog);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, rejected.code());
        assertEquals("Agent queued backlog observation is unavailable", rejected.getMessage());
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final List<String> calls = new ArrayList<>();
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private Integer count = 257;
        private boolean failure;

        private Fixture() throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::query),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = new AgentLedgerService(source);
        }

        private Object query(InvocationOnMock invocation) {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            Object[] args = invocation.getRawArguments();
            String sql = (String) args[0];
            if (invocation.getMethod().getName().equals("execute")) {
                calls.add(sql);
                return null;
            }
            assertEquals("queryForObject", invocation.getMethod().getName());
            calls.add("count");
            assertTrue(sql.contains("CASE WHEN NOT pg_catalog.pg_is_in_recovery()"));
            assertTrue(sql.contains("pg_catalog.current_setting('transaction_read_only') = 'on'"));
            assertTrue(sql.contains("count(*)::integer"));
            assertTrue(sql.contains("SELECT 1 FROM tianji_agent.agent_run"));
            assertTrue(sql.contains("status = 'QUEUED' AND cancel_requested_at IS NULL"));
            assertTrue(sql.contains("deadline_at > pg_catalog.statement_timestamp()"));
            assertTrue(sql.contains("LIMIT ?"));
            assertFalse(sql.contains("FOR UPDATE"));
            assertFalse(sql.contains("subject_id"));
            assertFalse(sql.contains("agent_execution_grant"));
            assertSame(Integer.class, args[1]);
            assertArrayEquals(new Object[]{257}, (Object[]) args[2]);
            if (failure) throw new DataAccessResourceFailureException("private fixture message");
            return count;
        }

        @Override
        public void close() {
            jdbc.close();
        }
    }
}
