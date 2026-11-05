package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.*;

class AgentCheckpointTakeoverServiceTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(7, TrustedAgentActor.SubjectType.USER, "fixture", "a".repeat(64));
    private static final String RUN = "00000000-0000-0000-0000-000000000007";
    private static final String SESSION = "fixture-session";
    private static final String INVOCATION = "fixture-invocation";
    private static final String WORKER = "fixture-worker";
    private static final GetAgentRunQuery QUERY = new GetAgentRunQuery(RUN);
    private static final ResumeCommand COMMAND = new ResumeCommand(RUN, "resume-1", 3);
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(90);
    private static final Instant EXPIRY = NOW.plusSeconds(15);
    private static final AgentRunAuthorization.Request EXECUTION = new AgentRunAuthorization.Request(
            OWNER, RUN, "fixture", 1, "b".repeat(64), "c".repeat(64), "d".repeat(64), "policy:v1", true, DEADLINE);
    private static final AgentCheckpointObservationService.ResumeMatch MATCH = match(11);

    @Test
    void claims_same_attempt_under_both_fences_and_replay_never_mints_launch() throws Exception {
        try (var fixture = new Fixture()) {
            var result = fixture.service.takeOver(OWNER, COMMAND, WORKER);

            assertEquals(new Acknowledgement(RUN, AgentRunStatusEnum.RUNNING, 1, 5), result.acknowledgement());
            var launch = result.takeLaunch().orElseThrow();
            assertEquals(new AgentRunLease(RUN, WORKER, 3, EXPIRY), launch.runLease());
            assertSame(fixture.sessionLease, launch.sessionLease());
            assertEquals(INVOCATION, launch.invocationId());
            assertEquals(MATCH, launch.checkpoint());
            assertTrue(result.takeLaunch().isEmpty());
            assertEquals(List.of("lockRun", "lockSession", "lockSessionLease", "lockScope", "match",
                    "reserveReceipt", "advance", "acquire", "SUSPENDED->QUEUED", "QUEUED->RUNNING", "completeReceipt"),
                    fixture.actions);
            assertEquals(List.of(4L, 5L), fixture.eventSequences);
            verify(fixture.connection, times(2)).commit();
            verify(fixture.source, times(2)).getConnection();
            verify(fixture.connection, never()).rollback();

            var replay = fixture.service.takeOver(OWNER, COMMAND, WORKER);
            assertEquals(result.acknowledgement(), replay.acknowledgement());
            assertTrue(replay.takeLaunch().isEmpty());
            assertEquals(1, fixture.authorizationCalls);
            assertEquals(1, fixture.actions.stream().filter("advance"::equals).count());
            assertEquals(1, fixture.actions.stream().filter("acquire"::equals).count());
        }
    }

    @Test
    void execution_handle_requires_the_issuing_source_and_authority_and_rechecks_current_permit() throws Exception {
        try (var fixture = new Fixture()) {
            var result = fixture.service.takeOver(OWNER, COMMAND, WORKER);
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () ->
                    result.consumeForExecution(mock(DataSource.class), fixture.authority, true));
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () ->
                    result.consumeForExecution(fixture.source, ignored -> Optional.empty(), true));
            var execution = result.consumeForExecution(fixture.source, fixture.authority, true).orElseThrow();
            assertEquals(EXECUTION, execution.request());
            assertEquals(INVOCATION, execution.invocationId());
            assertSame(fixture.sessionLease, execution.sessionLease());
            assertEquals(EXPIRY, execution.runLease().expiresAt());
            assertTrue(result.consumeForExecution(fixture.source, fixture.authority, true).isEmpty());
            var material = mock(AgentLedgerService.ExecutionMaterial.class);
            when(material.authorization()).thenReturn(EXECUTION);
            when(material.run()).thenReturn(resumedRun(5));
            assertTrue(execution.matchesClaimed(material));
            when(material.run()).thenReturn(resumedRun(6));
            assertFalse(execution.matchesClaimed(material));

            assertEquals(AgentRunAuthorization.Decision.ALLOW, execution.authorizeResume(NOW));
            fixture.decide = request -> Optional.of(new ResumePermit(request, EXPIRY.minusMillis(1)));
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, execution.authorizeResume(NOW));
            fixture.decide = request -> Optional.empty();
            assertEquals(AgentRunAuthorization.Decision.DENY, execution.authorizeResume(NOW));
            int calls = fixture.authorizationCalls;
            assertEquals(AgentRunAuthorization.Decision.UNAVAILABLE, execution.authorizeResume(EXPIRY));
            assertEquals(calls, fixture.authorizationCalls);
            var replay = fixture.service.takeOver(OWNER, COMMAND, WORKER);
            assertTrue(replay.consumeForExecution(fixture.source, fixture.authority, true).isEmpty());
            assertEquals(calls, fixture.authorizationCalls);
        }
    }

    private static AgentRunDTO resumedRun(long revision) {
        return new AgentRunDTO(RUN, "fixture", 1, SESSION, AgentRunStatusEnum.RUNNING, null, false,
                1, revision, 5, DEADLINE, NOW, null);
    }

    @Test
    void receipt_key_reuse_with_changed_expected_revision_conflicts_before_authorization() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.takeOver(OWNER, COMMAND, WORKER);
            int mutations = fixture.actions.size();
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () ->
                    fixture.service.takeOver(OWNER, new ResumeCommand(RUN, COMMAND.commandId(), 4), WORKER));
            assertEquals(1, fixture.authorizationCalls);
            assertEquals(mutations, fixture.actions.size());
        }
    }

    @Test
    void stale_cut_fails_before_writing() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.actual = Optional.of(match(12));
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertEquals(List.of("lockRun", "lockSession", "lockSessionLease", "lockScope", "match"), fixture.actions);
            verify(fixture.connection).rollback();
            verify(fixture.connection).commit();
        }
    }

    @Test
    void denied_or_misbound_authority_never_enters_write_transaction() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.decide = ignored -> Optional.empty();
            assertCode(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertTrue(fixture.actions.isEmpty());
            verify(fixture.source).getConnection();
        }
        try (var fixture = new Fixture()) {
            fixture.decide = request -> Optional.of(new ResumePermit(
                    new ResumeRequest(request.execution(), request.command(), "e".repeat(64)), EXPIRY));
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertTrue(fixture.actions.isEmpty());
            verify(fixture.source).getConnection();
        }
    }

    @Test
    void expired_permit_fails_cas_and_rolls_back_reservation() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.canAdvance = false;
            fixture.decide = request -> Optional.of(new ResumePermit(request, NOW.minusSeconds(1)));
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertEquals(List.of("lockRun", "lockSession", "lockSessionLease", "lockScope", "match", "reserveReceipt", "advance"),
                    fixture.actions);
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void either_failed_session_acquire_or_failed_receipt_completion_rolls_back_everything() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.sessionAvailable = false;
            assertCode(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertFalse(fixture.actions.contains("completeReceipt"));
            assertTrue(fixture.eventSequences.isEmpty());
            verify(fixture.connection).rollback();
        }
        try (var fixture = new Fixture()) {
            fixture.receiptAvailable = false;
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertEquals(List.of(4L, 5L), fixture.eventSequences);
            assertFalse(fixture.committedReceipt);
            verify(fixture.connection).rollback();
        }
        try (var fixture = new Fixture()) {
            fixture.corruptReceiptAfterWrite = true;
            assertCode(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertTrue(fixture.actions.contains("completeReceipt"));
            verify(fixture.connection).rollback();
            verify(fixture.connection).commit();
        }
    }

    @Test
    void expiry_between_acquiring_leases_and_committing_never_yields_launch() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.liveAtEnd = false;
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertTrue(fixture.actions.contains("completeReceipt"));
            verify(fixture.connection).rollback();
            verify(fixture.connection).commit();
        }
    }

    @Test
    void lost_commit_race_returns_ack_without_a_second_run_or_session_write() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.conflictingInsert = true;
            var result = fixture.service.takeOver(OWNER, COMMAND, WORKER);
            assertEquals(5L, result.acknowledgement().revision());
            assertTrue(result.takeLaunch().isEmpty());
            assertFalse(fixture.actions.contains("advance"));
            assertFalse(fixture.actions.contains("acquire"));
            assertTrue(fixture.eventSequences.isEmpty());
        }
    }

    @Test
    void serializable_lost_commit_race_reads_receipt_after_rollback_and_never_retries_write() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.serializeOnInsert = true;
            fixture.raceCommitted = true;
            var replay = fixture.service.takeOver(OWNER, COMMAND, WORKER);
            assertEquals(5L, replay.acknowledgement().revision());
            assertTrue(replay.takeLaunch().isEmpty());
            assertEquals(1, fixture.actions.stream().filter("reserveReceipt"::equals).count());
            assertFalse(fixture.actions.contains("advance"));
            verify(fixture.connection).rollback();
            verify(fixture.connection, times(2)).commit();
            verify(fixture.connection, times(2)).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        }
        try (var fixture = new Fixture()) {
            fixture.serializeOnInsert = true;
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () -> fixture.service.takeOver(OWNER, COMMAND, WORKER));
            assertEquals(1, fixture.actions.stream().filter("reserveReceipt"::equals).count());
            assertFalse(fixture.actions.contains("advance"));
            verify(fixture.connection).rollback();
        }
    }

    @Test
    void exact_preview_is_read_only_and_neither_authorizes_nor_writes() throws Exception {
        try (var fixture = new Fixture()) {
            var preview = fixture.service.previewAuthorization(OWNER, COMMAND).orElseThrow();
            assertEquals(new ResumeRequest(EXECUTION, COMMAND, MATCH.cutSha256()), preview);
            assertEquals(0, fixture.authorizationCalls);
            assertTrue(fixture.actions.isEmpty());
            assertTrue(fixture.eventSequences.isEmpty());
            verify(fixture.connection).setReadOnly(true);
            verify(fixture.connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            verify(fixture.connection).commit();
            verify(fixture.source).getConnection();
            verify(fixture.statement).executeUpdate("SET TRANSACTION READ ONLY");
        }
    }

    @Test
    void preview_of_an_exact_committed_replay_needs_no_new_authorization_cut() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.service.takeOver(OWNER, COMMAND, WORKER);
            fixture.actions.clear();
            var observation = fixture.observationMocks.constructed().getFirst();
            clearInvocations(observation, fixture.connection, fixture.source);

            assertTrue(fixture.service.previewAuthorization(OWNER, COMMAND).isEmpty());
            assertEquals(1, fixture.authorizationCalls);
            assertTrue(fixture.actions.isEmpty());
            verifyNoInteractions(observation);
            verify(fixture.connection).setReadOnly(true);
            verify(fixture.connection).commit();
            verify(fixture.source).getConnection();

            var changed = new ResumeCommand(RUN, COMMAND.commandId(), COMMAND.expectedRevision() + 1);
            assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> fixture.service.previewAuthorization(OWNER, changed));
            assertEquals(1, fixture.authorizationCalls);
            assertTrue(fixture.actions.isEmpty());
            verifyNoInteractions(observation);
        }
    }

    @Test
    void stale_or_ambient_preview_fails_without_authorization_or_writes() throws Exception {
        try (var fixture = new Fixture()) {
            var stale = new ResumeCommand(RUN, COMMAND.commandId(), COMMAND.expectedRevision() + 1);
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () -> fixture.service.previewAuthorization(OWNER, stale));
            TransactionSynchronizationManager.initSynchronization();
            try {
                assertThrows(IllegalStateException.class, () -> fixture.service.previewAuthorization(OWNER, COMMAND));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            assertEquals(0, fixture.authorizationCalls);
            assertTrue(fixture.actions.isEmpty());
            verify(fixture.connection, times(2)).setReadOnly(true);
            verify(fixture.source, times(2)).getConnection();
        }
    }

    @Test
    void authority_binding_changes_with_cut_command_or_resource() {
        URI primary = URI.create("https://agent.example.test");
        var first = new ResumeRequest(EXECUTION, COMMAND, MATCH.cutSha256());
        assertNotEquals(first.bindingSha256(primary), new ResumeRequest(EXECUTION, COMMAND, "e".repeat(64)).bindingSha256(primary));
        assertNotEquals(first.bindingSha256(primary), new ResumeRequest(EXECUTION, new ResumeCommand(RUN, "resume-2", 3),
                MATCH.cutSha256()).bindingSha256(primary));
        assertNotEquals(first.bindingSha256(primary), first.bindingSha256(URI.create("https://other.example.test")));
        assertThrows(IllegalArgumentException.class, () -> new ResumePermit(first, DEADLINE.plusSeconds(1)));
    }

    private static void assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(AgentLedgerException.class, action).code());
    }

    private static AgentCheckpointObservationService.ResumeMatch match(long sessionEpoch) {
        return new AgentCheckpointObservationService.ResumeMatch(EXECUTION, SESSION, INVOCATION, 3, 3, 2,
                sessionEpoch, 1, 1, "f".repeat(64));
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final Statement statement = mock(Statement.class);
        private final PostgresAdkSessionService.SessionLease sessionLease = mock(PostgresAdkSessionService.SessionLease.class);
        private final MockedConstruction<AgentCheckpointObservationService> observationMocks;
        private final MockedConstruction<JdbcTemplate> jdbcMocks;
        private final MockedConstruction<JdbcAdkSessionRepository> sessionRepositoryMocks;
        private final MockedConstruction<PostgresAdkSessionService> sessionServiceMocks;
        private final AgentCheckpointTakeoverService service;
        private final AgentCheckpointTakeoverService.ResumeAuthorization authority;
        private final List<String> actions = new ArrayList<>();
        private final List<Long> eventSequences = new ArrayList<>();
        private Optional<AgentCheckpointObservationService.ResumeMatch> actual = Optional.of(MATCH);
        private Function<ResumeRequest, Optional<ResumePermit>> decide = request -> Optional.of(new ResumePermit(request, EXPIRY));
        private int authorizationCalls;
        private boolean canAdvance = true;
        private boolean sessionAvailable = true;
        private boolean receiptAvailable = true;
        private boolean corruptReceiptAfterWrite;
        private boolean liveAtEnd = true;
        private boolean conflictingInsert;
        private boolean serializeOnInsert;
        private boolean raceCommitted;
        private boolean committedReceipt;
        private String requestHash;

        Fixture() throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            when(connection.getTransactionIsolation()).thenReturn(Connection.TRANSACTION_READ_COMMITTED);
            when(connection.createStatement()).thenReturn(statement);
            when(sessionLease.expiresAt()).thenReturn(EXPIRY);
            observationMocks = mockConstruction(AgentCheckpointObservationService.class, (mock, context) -> {
                assertEquals(List.of(source), context.arguments());
                when(mock.matchForTakeover(OWNER, QUERY)).thenAnswer(ignored -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    return Optional.of(MATCH);
                });
                when(mock.matchInTakeoverTransaction(OWNER, QUERY)).thenAnswer(ignored -> {
                    assertWritable();
                    actions.add("match");
                    return actual;
                });
            });
            jdbcMocks = mockConstruction(JdbcTemplate.class, withSettings().defaultAnswer(this::jdbc));
            sessionRepositoryMocks = mockConstruction(JdbcAdkSessionRepository.class, (mock, context) -> {
                assertEquals(OWNER, context.arguments().get(1));
                assertEquals("tianji", context.arguments().get(2));
                when(mock.lockRun(RUN)).thenAnswer(ignored -> {
                    assertWritable();
                    actions.add("lockRun");
                    return Optional.of(new JdbcAdkSessionRepository.RunFence(SESSION, "SUSPENDED", false, null, 2, null, DEADLINE));
                });
                when(mock.session(SESSION, true)).thenAnswer(ignored -> {
                    actions.add("lockSession");
                    return Optional.of(new JdbcAdkSessionRepository.SessionRow(SESSION, "{}", 1, 1, 1, NOW));
                });
                when(mock.lockLease(SESSION)).thenAnswer(ignored -> {
                    actions.add("lockSessionLease");
                    return new JdbcAdkSessionRepository.LeaseRow(11, "former-owner", NOW.minusSeconds(1), false);
                });
                when(mock.tryLockScopeForCheckpoint()).thenAnswer(ignored -> {
                    actions.add("lockScope");
                    return true;
                });
            });
            sessionServiceMocks = mockConstruction(PostgresAdkSessionService.class, (mock, context) -> {
                if (context.arguments().size() == 5) {
                    assertEquals(List.of(source, OWNER, "tianji", false, true), context.arguments());
                    when(mock.withResumeCheckpoint(source, sessionLease, MATCH)).thenReturn(mock);
                    return;
                }
                assertEquals(List.of(source, OWNER, "tianji"), context.arguments());
                when(mock.acquireCheckpointLeaseInTransaction(eq(SESSION), any(AgentRunLease.class), eq(11L), eq(EXPIRY)))
                        .thenAnswer(ignored -> {
                            assertWritable();
                            actions.add("acquire");
                            if (!sessionAvailable) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, "fixture");
                            return sessionLease;
                        });
            });
            try {
                authority = request -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionSynchronizationManager.hasResource(source));
                    assertEquals(new ResumeRequest(EXECUTION, COMMAND, MATCH.cutSha256()), request);
                    authorizationCalls++;
                    return decide.apply(request);
                };
                service = new AgentCheckpointTakeoverService(source, authority);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        private Object jdbc(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            if (args.length == 0 || !(args[0] instanceof String sql)) return RETURNS_DEFAULTS.answer(invocation);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            if ("execute".equals(invocation.getMethod().getName())) return null;
            if (sql.contains("pg_catalog.pg_is_in_recovery()")) {
                assertEquals(TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "on" : "off", args[2]);
                return true;
            }
            if (sql.contains("AS receipt_run_id")) {
                assertEquals(List.of(COMMAND.commandId(), OWNER.tenantId(), "USER", OWNER.issuerFingerprint(), OWNER.subjectId(), RUN),
                        Arrays.asList(args).subList(2, args.length));
                var row = mock(ResultSet.class);
                if (committedReceipt) {
                    when(row.getString("receipt_run_id")).thenReturn(RUN);
                    when(row.getString("request_hash")).thenReturn(requestHash);
                    when(row.getObject("ack_revision", Long.class)).thenReturn(5L);
                    when(row.getString("ack_run_id")).thenReturn(RUN);
                    when(row.getString("ack_status")).thenReturn(corruptReceiptAfterWrite ? "SUSPENDED" : "RUNNING");
                    when(row.getBoolean("ack_first_attempt")).thenReturn(true);
                }
                return List.of(((RowMapper<?>) args[1]).mapRow(row, 0));
            }
            assertWritable();
            if (sql.contains("INSERT INTO tianji_agent.agent_run_command_receipt")) {
                actions.add("reserveReceipt");
                assertTrue(sql.contains("'RESUME'"));
                requestHash = (String) args[6];
                assertTrue(requestHash.matches("[0-9a-f]{64}"));
                if (serializeOnInsert) {
                    committedReceipt = raceCommitted;
                    throw new CannotSerializeTransactionException("fixture serialization conflict");
                }
                if (conflictingInsert) {
                    committedReceipt = true;
                    return 0;
                }
                return 1;
            }
            if (sql.contains("UPDATE ONLY tianji_agent.agent_run AS target")) {
                actions.add("advance");
                assertTrue(sql.contains("target.status='SUSPENDED' AND target.wait_reason='LEASE_EXPIRED' AND target.attempt=1"));
                assertTrue(sql.contains("target.lease_epoch=? AND target.lease_owner IS NULL"));
                assertTrue(sql.contains("revision=target.revision+2"));
                assertTrue(sql.contains("last_event_sequence=target.last_event_sequence+2"));
                assertTrue(sql.contains("target.deadline_at>fence_clock.now AND ?>fence_clock.now"));
                assertFalse(sql.contains("attempt ="));
                if (!canAdvance) return List.of();
                assertEquals(Timestamp.from(EXPIRY), args[4]);
                assertEquals(Timestamp.from(EXPIRY), args[args.length - 1]);
                var row = mock(ResultSet.class);
                when(row.getLong("revision")).thenReturn(5L);
                when(row.getLong("last_event_sequence")).thenReturn(5L);
                when(row.getLong("lease_epoch")).thenReturn(3L);
                when(row.getTimestamp("lease_expires_at")).thenReturn(Timestamp.from(EXPIRY));
                return List.of(((RowMapper<?>) args[1]).mapRow(row, 0));
            }
            if (sql.contains("INSERT INTO tianji_agent.agent_event")) {
                eventSequences.add((Long) args[3]);
                actions.add(args[6] + "->" + args[7]);
                return 1;
            }
            if (sql.contains("UPDATE ONLY tianji_agent.agent_run_command_receipt")) {
                actions.add("completeReceipt");
                assertTrue(sql.contains("request_hash=? AND result IS NULL"));
                assertFalse(sql.contains("lease_owner"));
                assertFalse(sql.contains("invocation_id"));
                committedReceipt = receiptAvailable;
                return receiptAvailable ? 1 : 0;
            }
            if (sql.contains("pg_catalog.clock_timestamp()<LEAST")) return liveAtEnd;
            throw new AssertionError("Unexpected checkpoint takeover SQL");
        }

        private void assertWritable() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.hasResource(source));
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(Connection.TRANSACTION_READ_COMMITTED,
                    TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
        }

        @Override
        public void close() {
            sessionServiceMocks.close();
            sessionRepositoryMocks.close();
            jdbcMocks.close();
            observationMocks.close();
        }
    }
}
