package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class AgentInitialClaimTimingTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(42, TrustedAgentActor.SubjectType.USER,
            "owner", "a".repeat(64));
    private static final GetAgentRunQuery RUN = new GetAgentRunQuery("run");
    private static final Instant CREATED = Instant.parse("2026-11-05T12:30:00.123456Z");
    private static final Instant CLAIM_RECORDED = CREATED.plusSeconds(8).plusNanos(1_000);
    private static final Instant DEADLINE = CREATED.plusSeconds(60);
    private static final String WORKER = "worker";

    @Test
    void observes_first_claim_using_existing_clock_after_outer_commit() throws SQLException {
        var observed = new ArrayList<Duration>();
        try (var fixture = new Fixture(observed::add)) {
            doAnswer(call -> {
                assertTrue(observed.isEmpty());
                return null;
            }).when(fixture.connection).commit();
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            transaction.executeWithoutResult(status -> {
                var result = fixture.claim();
                assertEquals(AgentRunStatusEnum.RUNNING, result.run().status());
                assertEquals(1, result.run().attempt());
                assertEquals(1, result.lease().epoch());
                assertTrue(observed.isEmpty());
            });
            assertEquals(List.of(Duration.ofSeconds(8).plusNanos(1_000)), observed);
            assertEquals(List.of("owned-run", "claim", "event-clock", "event"), fixture.calls);
            verify(fixture.connection).commit();
        }
    }

    @Test
    void does_not_sample_lost_cas_failed_event_rollback_or_unknown_commit() throws SQLException {
        var observed = new ArrayList<Duration>();
        try (var fixture = new Fixture(observed::add)) {
            fixture.winner = false;
            assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    assertThrows(AgentLedgerException.class, fixture::claim).code());
            assertEquals(List.of("owned-run", "claim"), fixture.calls);
            assertTrue(observed.isEmpty());
        }
        try (var fixture = new Fixture(observed::add)) {
            fixture.eventFailure = true;
            assertThrows(DataAccessResourceFailureException.class, fixture::claim);
            verify(fixture.connection).rollback();
            assertTrue(observed.isEmpty());
        }
        try (var fixture = new Fixture(observed::add)) {
            var transaction = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            transaction.executeWithoutResult(status -> {
                fixture.claim();
                status.setRollbackOnly();
            });
            verify(fixture.connection).rollback();
            assertTrue(observed.isEmpty());
        }
        try (var fixture = new Fixture(observed::add)) {
            doThrow(new SQLException("private fixture commit failure")).when(fixture.connection).commit();
            assertThrows(TransactionException.class, fixture::claim);
            assertTrue(observed.isEmpty());
        }
    }

    @Test
    void accepts_zero_but_omits_later_attempts_and_invalid_time() throws SQLException {
        var observed = new ArrayList<Duration>();
        try (var fixture = new Fixture(observed::add)) {
            fixture.eventClock = CREATED;
            fixture.claim();
            assertEquals(List.of(Duration.ZERO), observed);
        }
        observed.clear();
        try (var fixture = new Fixture(observed::add)) {
            fixture.previousAttempt = 1;
            assertEquals(2, fixture.claim().run().attempt());
            assertTrue(observed.isEmpty());
        }
        try (var fixture = new Fixture(observed::add)) {
            fixture.eventClock = CREATED.minusNanos(1_000);
            fixture.claim();
            assertTrue(observed.isEmpty());
        }
        try (var fixture = new Fixture(observed::add)) {
            fixture.eventClock = Instant.parse("9999-01-01T00:00:00Z");
            fixture.claim();
            assertTrue(observed.isEmpty());
        }
    }

    @Test
    void observer_failure_cannot_replace_the_committed_claim() throws SQLException {
        try (var fixture = new Fixture(elapsed -> {
            assertEquals(Duration.ofSeconds(8).plusNanos(1_000), elapsed);
            throw new IllegalStateException("private metric failure");
        })) {
            assertEquals(AgentRunStatusEnum.RUNNING, fixture.claim().run().status());
            verify(fixture.connection).commit();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final List<String> calls = new ArrayList<>();
        private final MockedConstruction<JdbcTemplate> jdbc;
        private final AgentLedgerService ledger;
        private boolean winner = true;
        private boolean eventFailure;
        private int previousAttempt;
        private Instant eventClock = CLAIM_RECORDED;

        private Fixture(Consumer<Duration> observer) throws SQLException {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            jdbc = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbcCall),
                    (mock, context) -> assertSame(source, context.arguments().getFirst()));
            ledger = AgentLedgerService.withInitialClaimTiming(source, null, observer);
        }

        private ClaimedAgentRun claim() {
            return ledger.claim(OWNER, RUN, 4, WORKER, Duration.ofSeconds(30));
        }

        private Object jdbcCall(InvocationOnMock invocation) throws SQLException {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            var args = invocation.getRawArguments();
            String sql = (String) args[0];
            return switch (invocation.getMethod().getName()) {
                case "query" -> {
                    var bind = (Object[]) args[2];
                    var mapper = (RowMapper<?>) args[1];
                    if (sql.stripLeading().startsWith("SELECT * FROM tianji_agent.agent_run")) {
                        calls.add("owned-run");
                        assertArrayEquals(new Object[]{42L, "USER", OWNER.issuerFingerprint(), "owner", RUN.runId()}, bind);
                        yield List.of(mapper.mapRow(runRow(false), 0));
                    }
                    calls.add("claim");
                    assertTrue(sql.contains("status = 'QUEUED' AND cancel_requested_at IS NULL"));
                    assertArrayEquals(new Object[]{WORKER, 30_000L, 42L, "USER", OWNER.issuerFingerprint(), "owner",
                            RUN.runId(), 4L}, bind);
                    yield winner ? List.of(mapper.mapRow(runRow(true), 0)) : List.of();
                }
                case "queryForObject" -> {
                    calls.add("event-clock");
                    assertEquals("SELECT clock_timestamp()", sql);
                    assertSame(Timestamp.class, args[1]);
                    yield Timestamp.from(eventClock);
                }
                case "update" -> {
                    calls.add("event");
                    assertTrue(sql.contains("INSERT INTO tianji_agent.agent_event"));
                    var bind = (Object[]) args[1];
                    assertEquals("RUN_STATUS_CHANGED", bind[4]);
                    assertEquals(Timestamp.from(eventClock), bind[11]);
                    if (eventFailure) throw new DataAccessResourceFailureException("private event write failure");
                    yield 1;
                }
                default -> throw new AssertionError("Unexpected Agent claim JDBC operation");
            };
        }

        private ResultSet runRow(boolean claimed) throws SQLException {
            var row = mock(ResultSet.class);
            when(row.getString("run_id")).thenReturn(RUN.runId());
            when(row.getString("definition_key")).thenReturn("expense");
            when(row.getLong("definition_version")).thenReturn(1L);
            when(row.getString("status")).thenReturn(claimed ? "RUNNING" : "QUEUED");
            when(row.getLong("revision")).thenReturn(claimed ? 5L : 4L);
            when(row.getLong("last_event_sequence")).thenReturn(claimed ? 2L : 1L);
            when(row.getInt("attempt")).thenReturn(claimed ? previousAttempt + 1 : previousAttempt);
            when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(CREATED));
            when(row.getTimestamp("deadline_at")).thenReturn(Timestamp.from(DEADLINE));
            when(row.getTimestamp("lease_expires_at")).thenReturn(claimed ? Timestamp.from(CREATED.plusSeconds(38)) : null);
            when(row.getLong("lease_epoch")).thenReturn(claimed ? previousAttempt + 1L : previousAttempt);
            return row;
        }

        @Override
        public void close() {
            jdbc.close();
        }
    }
}
