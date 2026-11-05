package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.FirstInputCheckpointBO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkCheckpointCaptureTest {
    private static final String SESSION = "session";
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Content INPUT = Content.fromParts(Part.fromText("fixture input"));

    @Test
    void accepts_only_exact_first_durable_user_input() {
        assertTrue(PostgresAdkSessionService.matchesFirstInput(userEvent(INPUT), INPUT));
        assertFalse(PostgresAdkSessionService.matchesFirstInput(userEvent(Content.fromParts(Part.fromText("other"))), INPUT),
                "mismatched input must not become checkpoint evidence");
        var actions = userEvent(INPUT);
        actions.setActions(EventActions.builder().stateDelta(Map.of("temp:marker", "must not capture")).build());
        assertFalse(PostgresAdkSessionService.matchesFirstInput(actions, INPUT));
        var branch = userEvent(INPUT);
        branch.branch("other");
        assertFalse(PostgresAdkSessionService.matchesFirstInput(branch, INPUT));
        var partial = userEvent(INPUT);
        partial.setPartial(true);
        assertFalse(PostgresAdkSessionService.matchesFirstInput(partial, INPUT));
        var model = userEvent(INPUT);
        model.setAuthor("writer");
        assertFalse(PostgresAdkSessionService.matchesFirstInput(model, INPUT));
        for (boolean typed : List.of(false, true)) {
            var codec = new AdkSessionEventCodec(typed);
            assertTrue(PostgresAdkSessionService.matchesFirstInput(codec.decode(codec.encode(userEvent(INPUT))), INPUT));
        }
    }

    @Test
    void stores_checkpoint_between_session_update_and_the_final_fences() throws Exception {
        for (boolean typed : List.of(false, true)) {
            try (var fixture = new Fixture(typed, true)) {
                var event = userEvent(INPUT);
                assertEquals(event.id(), fixture.bound.appendEvent(fixture.session, event).blockingGet().id());
                var order = inOrder(fixture.repository, fixture.connection);
                order.verify(fixture.repository).tryLockScopeForCheckpoint();
                order.verify(fixture.repository).insertEvent(eq(SESSION), eq(1L), eq(event.id()), eq(event.timestamp()), anyString());
                order.verify(fixture.repository).updateSession(eq(SESSION), eq(0L), eq(1L), anyString(), eq(typed ? 2 : 1));
                order.verify(fixture.repository).insertFirstInputCheckpoint(eq(fixture.checkpoint), eq(fixture.run), eq(7L),
                        eq(fixture.before), eq(fixture.after), argThat(saved -> saved.id().equals(event.id())));
                order.verify(fixture.repository).liveFences(eq(SESSION), eq(fixture.run), anyString(), eq(7L));
                order.verify(fixture.connection).commit();
                assertEquals(1, fixture.session.immutableEvents().size());
            }
        }
    }

    @Test
    void failed_checkpoint_rolls_back_before_the_native_session_advances() throws Exception {
        try (var fixture = new Fixture(false, true)) {
            var failure = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent checkpoint capture is unavailable");
            doThrow(failure).when(fixture.repository).insertFirstInputCheckpoint(any(), any(), anyLong(), any(), any(), any());
            assertSame(failure, assertThrows(AgentLedgerException.class,
                    () -> fixture.bound.appendEvent(fixture.session, userEvent(INPUT)).blockingGet()));
            verify(fixture.connection).rollback();
            verify(fixture.connection, never()).commit();
            assertTrue(fixture.session.immutableEvents().isEmpty());
        }
    }

    @Test
    void disabled_and_busy_scope_paths_keep_the_ordinary_session_append() throws Exception {
        for (boolean enabled : List.of(false, true)) {
            try (var fixture = new Fixture(false, enabled)) {
                when(fixture.repository.tryLockScopeForCheckpoint()).thenReturn(false);
                fixture.bound.appendEvent(fixture.session, userEvent(INPUT)).blockingGet();
                verify(fixture.repository, times(enabled ? 1 : 0)).tryLockScopeForCheckpoint();
                verify(fixture.repository, never()).insertFirstInputCheckpoint(any(), any(), anyLong(), any(), any(), any());
                verify(fixture.connection).commit();
                assertEquals(1, fixture.session.immutableEvents().size());
            }
        }
    }

    @Test
    void resolves_the_locked_adk_engine_marker() throws Exception {
        var method = AdkAgentRunExecutor.class.getDeclaredMethod("checkpointEngineVersion");
        method.setAccessible(true);
        assertEquals("1.13.0", method.invoke(null));
    }

    @Test
    void takeover_session_lease_uses_the_bound_connection_and_exact_prior_epoch() throws Exception {
        var actor = new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.USER, "fixture", "a".repeat(64));
        var source = mock(DataSource.class);
        var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        var expires = NOW.plusSeconds(30);
        var run = new AgentRunLease("run", "takeover-worker", 3, expires);
        try (var repositories = mockConstruction(JdbcAdkSessionRepository.class)) {
            var store = new PostgresAdkSessionService(source, actor, "tianji");
            var repository = repositories.constructed().getFirst();
            assertThrows(IllegalStateException.class,
                    () -> store.acquireCheckpointLeaseInTransaction(SESSION, run, 7, expires));
            verifyNoInteractions(repository);
            when(repository.lockRun(run.runId())).thenReturn(Optional.of(new JdbcAdkSessionRepository.RunFence(
                    SESSION, "RUNNING", false, run.owner(), run.epoch(), expires, NOW.plusSeconds(60))));
            when(repository.databaseNow()).thenReturn(NOW);
            when(repository.acquireCheckpoint(eq(SESSION), anyString(), eq(7L), eq(expires))).thenAnswer(call ->
                    Optional.of(new JdbcAdkSessionRepository.LeaseRow(8, call.getArgument(1), expires, true)));
            when(repository.liveFences(eq(SESSION), eq(run), anyString(), eq(8L))).thenReturn(true);

            var write = new TransactionTemplate(new DataSourceTransactionManager(source));
            assertNotNull(write.execute(status -> store.acquireCheckpointLeaseInTransaction(SESSION, run, 7, expires)));
            verify(source).getConnection();
            verify(repository).acquireCheckpoint(eq(SESSION), anyString(), eq(7L), eq(expires));
            verify(repository).liveFences(eq(SESSION), eq(run), anyString(), eq(8L));
            verify(connection).commit();
        }
    }

    private static Event userEvent(Content input) {
        return Event.builder().id("fixture-event").invocationId("fixture-invocation").author("user")
                .timestamp(NOW.toEpochMilli()).content(input).build();
    }

    private static final class Fixture implements AutoCloseable {
        private final TrustedAgentActor actor = new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.USER, "fixture", "a".repeat(64));
        private final AgentRunLease run = new AgentRunLease("run", "worker", 2, NOW.plusSeconds(30));
        private final Connection connection = mock(Connection.class);
        private final org.mockito.MockedConstruction<JdbcAdkSessionRepository> repositories;
        private final JdbcAdkSessionRepository repository;
        private final PostgresAdkSessionService bound;
        private final FirstInputCheckpointBO checkpoint;
        private final JdbcAdkSessionRepository.SessionRow before;
        private final JdbcAdkSessionRepository.SessionRow after;
        private final Session session;

        Fixture(boolean typed, boolean enabled) throws Exception {
            var source = mock(DataSource.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            var codec = new AdkSessionStateCodec(typed);
            String state = codec.encode(Map.of());
            before = new JdbcAdkSessionRepository.SessionRow(SESSION, state, codec.writeVersion(), 0, 0, NOW);
            after = new JdbcAdkSessionRepository.SessionRow(SESSION, state, codec.writeVersion(), 1, 1, NOW);
            repositories = mockConstruction(JdbcAdkSessionRepository.class);
            try {
                var store = new PostgresAdkSessionService(source, actor, "tianji", false, typed);
                repository = repositories.constructed().getFirst();
                var owner = new AtomicReference<String>();
                when(repository.acquire(eq(SESSION), anyString(), anyLong())).thenAnswer(invocation -> {
                    owner.set(invocation.getArgument(1));
                    return Optional.of(lease(owner.get()));
                });
                when(repository.lockRun(run.runId())).thenReturn(Optional.of(new JdbcAdkSessionRepository.RunFence(
                        SESSION, "RUNNING", false, run.owner(), run.epoch(), run.expiresAt(), NOW.plusSeconds(60))));
                when(repository.databaseNow()).thenReturn(NOW);
                when(repository.liveFences(eq(SESSION), eq(run), anyString(), eq(7L))).thenReturn(true);
                when(repository.lockLease(SESSION)).thenAnswer(invocation -> lease(owner.get()));
                when(repository.session(SESSION, true)).thenReturn(Optional.of(before));
                when(repository.eventBounds(eq(SESSION), anyInt(), isNull())).thenReturn(new JdbcAdkSessionRepository.EventBounds(0, 0, 0, 0));
                when(repository.events(eq(SESSION), anyInt(), isNull())).thenReturn(List.of());
                when(repository.scopeBounds()).thenReturn(new JdbcAdkSessionRepository.ScopeBounds(0, 0, 0, 0));
                when(repository.scopeState()).thenReturn(Map.of());
                when(repository.event(eq(SESSION), anyString(), anyString())).thenReturn(Optional.empty());
                when(repository.tryLockScopeForCheckpoint()).thenReturn(true);
                when(repository.updateSession(eq(SESSION), eq(0L), eq(1L), anyString(), anyInt())).thenReturn(after);
                var sessionLease = store.acquireSessionLease(SESSION, run, Duration.ofSeconds(30));
                var request = new AgentRunAuthorization.Request(actor, run.runId(), "definition", 1, "b".repeat(64),
                        "c".repeat(64), "d".repeat(64), "policy", true, NOW.plusSeconds(60));
                var claimed = new AgentRunDTO(run.runId(), "definition", 1, SESSION, AgentRunStatusEnum.RUNNING,
                        null, false, 1, 2, 2, NOW.plusSeconds(60), NOW, null);
                checkpoint = new FirstInputCheckpointBO(request, claimed, INPUT, "1.13.0");
                bound = enabled ? store.withLease(sessionLease, checkpoint) : store.withLease(sessionLease);
                session = bound.getSession("tianji", bound.adkUserId(), SESSION, Optional.empty()).blockingGet();
                assertNotNull(session);
                clearInvocations(repository, connection);
            } catch (Throwable failure) {
                repositories.close();
                throw failure;
            }
        }

        private static JdbcAdkSessionRepository.LeaseRow lease(String owner) {
            return new JdbcAdkSessionRepository.LeaseRow(7, owner, NOW.plusSeconds(30), true);
        }

        @Override
        public void close() {
            repositories.close();
        }
    }
}
