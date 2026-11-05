package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.google.adk.events.Event;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointObservationService.ResumeMatch;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Launch;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeRequest;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkCheckpointResumeFirstReadTest {
    private static final TrustedAgentActor OWNER = new TrustedAgentActor(40001, TrustedAgentActor.SubjectType.USER, "fixture", "a".repeat(64));
    private static final String APP = "tianji";
    private static final String SESSION = "session";
    private static final String INVOCATION = "fixture-invocation";
    private static final Instant NOW = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant DEADLINE = NOW.plusSeconds(60);
    private static final AgentRunLease RUN = new AgentRunLease("run", "takeover-worker", 3, NOW.plusSeconds(30));
    private static final AgentRunAuthorization.Request REQUEST = new AgentRunAuthorization.Request(
            OWNER, RUN.runId(), "definition", 1, "b".repeat(64), "c".repeat(64), "d".repeat(64), "policy", true, DEADLINE);

    @Test
    void resume_first_read_returns_the_exact_locked_view_for_both_codecs() throws Exception {
        for (boolean typed : List.of(false, true)) {
            try (var fixture = new Fixture(typed)) {
                assertEquals("ea875a4f5dbb1f057a40a22d93e71591cbcc3978a7773cf76dd49d91e50ab6d7",
                        fixture.cut.digest("fixture-event", 1, NOW.toEpochMilli(), "{}", "raw"));
                Session view = fixture.read();
                assertEquals(SESSION, view.id());
                assertEquals(INVOCATION, view.immutableEvents().getFirst().invocationId());
                assertEquals(userEvent("fixture input").content(), view.immutableEvents().getFirst().content());
                assertEquals(1, view.immutableEvents().size());
                assertTrue(view.state().isEmpty());

                var order = inOrder(fixture.repository, fixture.connection);
                order.verify(fixture.repository).boundCheckpointFirstRead();
                order.verify(fixture.repository).lockRun(RUN.runId());
                order.verify(fixture.repository).checkpointRunMatches(SESSION, RUN, fixture.cut);
                order.verify(fixture.repository).session(SESSION, true);
                order.verify(fixture.repository).lockLease(SESSION);
                order.verify(fixture.repository).tryLockScopeForCheckpoint();
                order.verify(fixture.repository).scopeBounds();
                order.verify(fixture.repository).eventBounds(SESSION, 2, null);
                order.verify(fixture.repository).events(SESSION, 2, null);
                order.verify(fixture.repository).liveFences(eq(SESSION), eq(RUN), anyString(), eq(8L));
                order.verify(fixture.connection).commit();
                verify(fixture.source).getConnection();
                verify(fixture.repository, never()).scopeState();
                verify(fixture.repository, never()).insertEvent(anyString(), anyLong(), anyString(), anyLong(), anyString());
            }
        }
    }

    @Test
    void resume_first_read_rejects_changed_raw_event_under_the_same_ids() throws Exception {
        for (boolean typed : List.of(false, true)) {
            try (var fixture = new Fixture(typed)) {
                fixture.persistedEvent(userEvent("changed input"));
                assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, fixture::read);
                verify(fixture.connection).rollback();
                verify(fixture.connection, never()).commit();
            }
        }
    }

    @Test
    void resume_first_read_denies_partial_reads_and_appends_before_verification() throws Exception {
        try (var fixture = new Fixture(false)) {
            var config = GetSessionConfig.builder().numRecentEvents(1).build();
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () ->
                    fixture.bound.getSession(APP, fixture.bound.adkUserId(), SESSION, Optional.of(config)).blockingGet());
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, () ->
                    fixture.bound.listEvents(APP, fixture.bound.adkUserId(), SESSION).blockingGet());
            var forged = Session.builder(SESSION).appName(APP).userId(fixture.bound.adkUserId()).build();
            var partial = userEvent("fixture input");
            partial.setPartial(true);
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    () -> fixture.bound.appendEvent(forged, partial).blockingGet());
            assertThrows(IllegalArgumentException.class, () -> fixture.bound.withLease(fixture.lease));
            assertCode(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                    () -> fixture.bound.renewSessionLease(fixture.lease, java.time.Duration.ofSeconds(30)));
            verifyNoInteractions(fixture.source, fixture.repository);

            var ambient = new TransactionTemplate(new DataSourceTransactionManager(fixture.source));
            ambient.executeWithoutResult(status -> assertThrows(IllegalStateException.class, fixture::read));
            verify(fixture.source).getConnection();
            verifyNoInteractions(fixture.repository);

            Session verified = fixture.read();
            assertSame(partial, fixture.bound.appendEvent(verified, partial).blockingGet());
        }
    }

    @Test
    void resume_first_read_remains_guarded_if_commit_fails_and_rechecks_on_retry() throws Exception {
        try (var fixture = new Fixture(true)) {
            doThrow(new SQLException("simulated commit failure")).doNothing().when(fixture.connection).commit();
            assertThrows(TransactionException.class, fixture::read);
            var partial = userEvent("fixture input");
            partial.setPartial(true);
            var forged = Session.builder(SESSION).appName(APP).userId(fixture.bound.adkUserId()).build();
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    () -> fixture.bound.appendEvent(forged, partial).blockingGet());

            assertEquals(1, fixture.read().immutableEvents().size());
            verify(fixture.repository, times(2)).tryLockScopeForCheckpoint();
            verify(fixture.connection, times(2)).commit();
        }
    }

    @Test
    void resume_first_read_rejects_scope_history_codec_and_lease_drift() throws Exception {
        try (var fixture = new Fixture(false)) {
            when(fixture.repository.scopeBounds()).thenReturn(new JdbcAdkSessionRepository.ScopeBounds(0, 0, 1, 8));
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, fixture::read);
            verify(fixture.repository, never()).events(SESSION, 2, null);
        }
        try (var fixture = new Fixture(false)) {
            when(fixture.repository.eventBounds(SESSION, 2, null)).thenReturn(new JdbcAdkSessionRepository.EventBounds(2, 100, 1, 2));
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, fixture::read);
            verify(fixture.repository, never()).events(SESSION, 2, null);
        }
        try (var fixture = new Fixture(false)) {
            String typedEmptyState = new AdkSessionStateCodec(true).encode(Map.of());
            when(fixture.repository.session(SESSION, true)).thenReturn(Optional.of(
                    new JdbcAdkSessionRepository.SessionRow(SESSION, typedEmptyState, 2, 1, 1, NOW)));
            assertCode(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, fixture::read);
        }
        try (var fixture = new Fixture(false)) {
            doReturn(false).when(fixture.repository).checkpointRunMatches(SESSION, RUN, fixture.cut);
            assertCode(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, fixture::read);
            verify(fixture.repository, never()).tryLockScopeForCheckpoint();
        }
        try (var fixture = new Fixture(false)) {
            when(fixture.repository.lockLease(SESSION)).thenReturn(
                    new JdbcAdkSessionRepository.LeaseRow(8, fixture.sessionOwner.get(), RUN.expiresAt().plusSeconds(1), true));
            assertCode(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, fixture::read);
            verify(fixture.repository, never()).tryLockScopeForCheckpoint();
        }
    }

    @Test
    void resume_binding_rejects_a_different_primary_and_actor_before_a_read() throws Exception {
        try (var fixture = new Fixture(false)) {
            var otherSource = mock(DataSource.class);
            var wrongPrimary = new PostgresAdkSessionService(otherSource, OWNER, APP);
            assertThrows(IllegalArgumentException.class, () -> fixture.launch.bindSession(wrongPrimary));
            var otherActor = new TrustedAgentActor(OWNER.tenantId(), OWNER.subjectType(), "other", OWNER.issuerFingerprint());
            var wrongActor = new PostgresAdkSessionService(fixture.source, otherActor, APP);
            assertCode(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, () -> fixture.launch.bindSession(wrongActor));
            verifyNoInteractions(otherSource, fixture.source, fixture.repository);
        }
    }

    private static Event userEvent(String text) {
        return Event.builder().id("fixture-event").invocationId(INVOCATION).author("user")
                .timestamp(NOW.toEpochMilli()).content(Content.fromParts(Part.fromText(text))).build();
    }

    private static void assertCode(AgentErrorCodeEnum code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(AgentLedgerException.class, action).code());
    }

    private static final class Fixture implements AutoCloseable {
        private final DataSource source = mock(DataSource.class);
        private final Connection connection = mock(Connection.class);
        private final AtomicReference<String> sessionOwner = new AtomicReference<>();
        private final MockedConstruction<JdbcAdkSessionRepository> repositories;
        private final JdbcAdkSessionRepository repository;
        private final AdkSessionEventCodec eventCodec;
        private final ResumeMatch cut;
        private final PostgresAdkSessionService.SessionLease lease;
        private final Launch launch;
        private final PostgresAdkSessionService bound;

        Fixture(boolean typed) throws Exception {
            when(source.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            eventCodec = new AdkSessionEventCodec(typed);
            var stateCodec = new AdkSessionStateCodec(typed);
            String state = stateCodec.encode(Map.of());
            int codecVersion = stateCodec.writeVersion();
            Event input = userEvent("fixture input");
            var unsigned = new ResumeMatch(REQUEST, SESSION, INVOCATION, 3, 3, 2, 7, 1, 1, "");
            cut = new ResumeMatch(REQUEST, SESSION, INVOCATION, 3, 3, 2, 7, 1, 1,
                    unsigned.digest(input.id(), codecVersion, input.timestamp(), state, eventCodec.encode(input)));

            repositories = mockConstruction(JdbcAdkSessionRepository.class);
            try {
                var store = new PostgresAdkSessionService(source, OWNER, APP, false, typed);
                repository = repositories.constructed().getFirst();
                when(repository.lockRun(RUN.runId())).thenReturn(Optional.of(new JdbcAdkSessionRepository.RunFence(
                        SESSION, "RUNNING", false, RUN.owner(), RUN.epoch(), RUN.expiresAt(), DEADLINE)));
                when(repository.databaseNow()).thenReturn(NOW);
                when(repository.acquireCheckpoint(eq(SESSION), anyString(), eq(7L), eq(RUN.expiresAt()))).thenAnswer(call -> {
                    sessionOwner.set(call.getArgument(1));
                    return Optional.of(new JdbcAdkSessionRepository.LeaseRow(8, sessionOwner.get(), RUN.expiresAt(), true));
                });
                when(repository.liveFences(eq(SESSION), eq(RUN), anyString(), eq(8L))).thenReturn(true);
                when(repository.lockLease(SESSION)).thenAnswer(call ->
                        new JdbcAdkSessionRepository.LeaseRow(8, sessionOwner.get(), RUN.expiresAt(), true));
                when(repository.checkpointRunMatches(SESSION, RUN, cut)).thenAnswer(call -> {
                    assertTrue(TransactionSynchronizationManager.hasResource(source));
                    assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    return true;
                });
                when(repository.session(SESSION, true)).thenReturn(Optional.of(
                        new JdbcAdkSessionRepository.SessionRow(SESSION, state, codecVersion, 1, 1, NOW)));
                when(repository.tryLockScopeForCheckpoint()).thenReturn(true);
                when(repository.scopeBounds()).thenReturn(new JdbcAdkSessionRepository.ScopeBounds(0, 0, 0, 0));
                persistedEvent(input);

                var takeover = new TransactionTemplate(new DataSourceTransactionManager(source));
                lease = takeover.execute(status -> store.acquireCheckpointLeaseInTransaction(SESSION, RUN, 7, RUN.expiresAt()));
                launch = new Launch(source, RUN, lease, cut, new ResumeRequest(REQUEST,
                        new ResumeCommand(RUN.runId(), "resume-1", cut.revision()), cut.cutSha256()));
                bound = launch.bindSession(store);
                clearInvocations(source, repository, connection);
            } catch (Throwable failure) {
                repositories.close();
                throw failure;
            }
        }

        void persistedEvent(Event event) {
            String stored = eventCodec.encode(event);
            when(repository.eventBounds(SESSION, 2, null)).thenReturn(new JdbcAdkSessionRepository.EventBounds(
                    1, stored.getBytes(StandardCharsets.UTF_8).length, 1, 1));
            when(repository.events(SESSION, 2, null)).thenReturn(List.of(
                    new JdbcAdkSessionRepository.EventRow(1, event.id(), event.timestamp(), stored, true)));
        }

        Session read() {
            return bound.getSession(APP, bound.adkUserId(), SESSION, Optional.empty()).blockingGet();
        }

        @Override public void close() { repositories.close(); }
    }
}
