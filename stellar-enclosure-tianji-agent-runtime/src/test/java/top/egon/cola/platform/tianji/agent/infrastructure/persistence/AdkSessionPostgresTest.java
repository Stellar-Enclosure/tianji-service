package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionNotFoundException;
import com.google.adk.sessions.State;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.application.session.AgentSessionManager;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.SessionLease;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DeleteAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.CANCELLED;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.COMPLETED;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AdkSessionPostgresTest {
    private static AgentLedgerService ledger;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        ledger = new AgentLedgerService(PostgresTestDatabase.dataSource());
        jdbc = new JdbcTemplate(PostgresTestDatabase.dataSource());
    }

    @Test
    void sessions_and_scoped_state_are_isolated_by_tenant_principal_and_application() {
        long tenant = PostgresTestDatabase.nextTenant();
        TrustedAgentActor alice = new TrustedAgentActor(tenant, USER, "alice");
        TrustedAgentActor bob = new TrustedAgentActor(tenant, USER, "bob");
        TrustedAgentActor sameService = new TrustedAgentActor(tenant, SERVICE, "alice");
        TrustedAgentActor otherTenant = new TrustedAgentActor(tenant + 1_000_000, USER, "alice");
        PostgresAdkSessionService owner = service(alice, true);
        Session created = owner.createSession("tianji", owner.adkUserId(),
                Map.of("app:shared", "team", "user:color", "blue", "local", 1, "temp:ephemeral", "ignored"),
                "only-alice").blockingGet();
        assertEquals("team", created.state().get("app:shared"));
        assertEquals("blue", created.state().get("user:color"));
        assertFalse(created.state().containsKey("temp:ephemeral"));

        PostgresAdkSessionService otherUser = service(bob, false);
        Session bobSession = otherUser.createSession("tianji", otherUser.adkUserId(), Map.of(), "bob-session").blockingGet();
        assertEquals("team", bobSession.state().get("app:shared"));
        assertFalse(bobSession.state().containsKey("user:color"));
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> otherUser.createSession("tianji", otherUser.adkUserId(), Map.of("app:shared", "change"),
                        "denied").blockingGet()).code());
        assertNull(otherUser.getSession("tianji", otherUser.adkUserId(), "denied", Optional.empty()).blockingGet());
        assertNull(otherUser.getSession("tianji", otherUser.adkUserId(), "only-alice", Optional.empty()).blockingGet());
        assertThrows(SessionNotFoundException.class,
                () -> otherUser.deleteSession("tianji", otherUser.adkUserId(), "only-alice").blockingAwait());
        assertNull(service(sameService, false).getSession("tianji", "SERVICE:alice", "only-alice", Optional.empty()).blockingGet());
        PostgresAdkSessionService foreign = service(otherTenant, false);
        Session foreignSession = foreign.createSession("tianji", foreign.adkUserId(), Map.of(), "other").blockingGet();
        assertFalse(foreignSession.state().containsKey("app:shared"));
        assertNull(foreign.getSession("tianji", foreign.adkUserId(), "only-alice", Optional.empty()).blockingGet());

        PostgresAdkSessionService otherApp = new PostgresAdkSessionService(PostgresTestDatabase.dataSource(), alice, "other-app");
        assertFalse(otherApp.createSession("other-app", otherApp.adkUserId(), Map.of(), "app-session")
                .blockingGet().state().containsKey("user:color"));
        assertThrows(IllegalArgumentException.class,
                () -> owner.getSession("tianji", otherUser.adkUserId(), "only-alice", Optional.empty()).blockingGet());
        owner.deleteSession("tianji", owner.adkUserId(), "only-alice").blockingAwait();
        Session later = owner.createSession("tianji", owner.adkUserId(), Map.of(), "later").blockingGet();
        assertEquals("blue", later.state().get("user:color"));
        assertEquals("team", later.state().get("app:shared"));
    }

    @Test
    void a_run_requires_an_owned_session_and_active_runs_block_deletion() {
        TrustedAgentActor owner = actor("owner");
        TrustedAgentActor other = new TrustedAgentActor(owner.tenantId(), USER, "other");
        ledger.publish(owner, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        AgentLedgerException missing = assertThrows(AgentLedgerException.class, () -> start(owner, "missing", "one"));
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, missing.code());
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "owned").blockingGet();
        AgentLedgerException crossOwner = assertThrows(AgentLedgerException.class, () -> start(other, "owned", "two"));
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, crossOwner.code());
        AgentRunDTO queued = start(owner, "owned", "three");
        assertEquals(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, assertThrows(AgentLedgerException.class,
                () -> sessions.deleteSession("tianji", sessions.adkUserId(), "owned").blockingAwait()).code());
        ledger.cancel(owner, new CancelAgentRunCommand(queued.runId(), "cancel", queued.revision(), "test"));
        sessions.deleteSession("tianji", sessions.adkUserId(), "owned").blockingAwait();
        assertNull(sessions.getSession("tianji", sessions.adkUserId(), "owned", Optional.empty()).blockingGet());
    }

    @Test
    void management_create_is_idempotent_and_never_replaces_existing_history() throws Exception {
        TrustedAgentActor owner = actor("manager");
        TrustedAgentActor other = new TrustedAgentActor(owner.tenantId(), USER, "another");
        AgentSessionManager manager = new AgentSessionManager(ledger, PostgresTestDatabase.dataSource());
        String id = "report/会话 +😀";
        assertEquals(id, manager.create(owner, new CreateAgentSessionCommand(id)).sessionId());
        AgentRunLease run = claim(owner, id, "management-run");
        PostgresAdkSessionService sessions = service(owner, false);
        SessionLease lease = sessions.acquireSessionLease(id, run, Duration.ofSeconds(30));
        PostgresAdkSessionService bound = sessions.withLease(lease);
        Session current = bound.getSession("tianji", bound.adkUserId(), id, Optional.empty()).blockingGet();
        bound.appendEvent(current, event("keep", 1_000, Map.of("local", "preserved"))).blockingGet();
        try (var pool = Executors.newFixedThreadPool(8)) {
            CountDownLatch gate = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                gate.await();
                return manager.create(owner, new CreateAgentSessionCommand(id)).sessionId();
            }));
            gate.countDown();
            for (Future<String> future : futures) assertEquals(id, future.get(10, TimeUnit.SECONDS));
        }
        assertEquals(id, manager.get(owner, new GetAgentSessionQuery(id)).sessionId());
        assertEquals(List.of(id), manager.list(owner).items().stream().map(item -> item.sessionId()).toList());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> manager.get(other, new GetAgentSessionQuery(id))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> manager.delete(other, new DeleteAgentSessionCommand(id))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, assertThrows(AgentLedgerException.class,
                () -> manager.delete(owner, new DeleteAgentSessionCommand(id))).code());
        Session unchanged = sessions.getSession("tianji", sessions.adkUserId(), id, Optional.empty()).blockingGet();
        assertEquals(1, unchanged.immutableEvents().size());
        assertEquals("preserved", unchanged.state().get("local"));
        AgentRunDTO running = ledger.get(owner, new GetAgentRunQuery(run.runId()));
        ledger.finish(owner, run, running.revision(), result(run, COMPLETED));
        assertTrue(sessions.releaseSessionLease(lease));
        manager.delete(owner, new DeleteAgentSessionCommand(id));
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> manager.get(owner, new GetAgentSessionQuery(id))).code());
    }

    @Test
    void appends_are_idempotent_and_old_session_and_run_leases_are_fenced() {
        TrustedAgentActor owner = actor("lease-owner");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s1").blockingGet();
        AgentRunLease run = claim(owner, "s1", "run-one");
        PostgresAdkSessionService otherApp = new PostgresAdkSessionService(PostgresTestDatabase.dataSource(), owner, "other-app");
        otherApp.createSession("other-app", otherApp.adkUserId(), Map.of(), "s1").blockingGet();
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> otherApp.acquireSessionLease("s1", run, Duration.ofSeconds(30))).code());
        SessionLease firstLease = sessions.acquireSessionLease("s1", run, Duration.ofSeconds(30));
        TrustedAgentActor serviceActor = new TrustedAgentActor(owner.tenantId(), SERVICE, owner.subjectId());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> service(serviceActor, false).withLease(firstLease)).code());
        PostgresAdkSessionService bound = sessions.withLease(firstLease);
        Session current = bound.getSession("tianji", bound.adkUserId(), "s1", Optional.empty()).blockingGet();
        Session stale = bound.getSession("tianji", bound.adkUserId(), "s1", Optional.empty()).blockingGet();
        Session lostReply = bound.getSession("tianji", bound.adkUserId(), "s1", Optional.empty()).blockingGet();
        Event first = event("event-1", 3_000, Map.of("user:preference", "old", "local", 1));
        bound.appendEvent(current, first).blockingGet();
        bound.appendEvent(current, first).blockingGet();
        bound.appendEvent(lostReply, first).blockingGet();
        assertEquals(1, lostReply.immutableEvents().size());
        assertEquals(1, lostReply.state().get("local"));
        assertEquals(1, bound.listEvents("tianji", bound.adkUserId(), "s1").blockingGet().events().size());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                assertThrows(AgentLedgerException.class,
                        () -> bound.appendEvent(current, event("event-1", 3_000, Map.of("local", 2))).blockingGet()).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                assertThrows(AgentLedgerException.class, () -> bound.appendEvent(current,
                        event("event-1", 3_000, Map.of("user:preference", "old", "local", 1.0))).blockingGet()).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                assertThrows(AgentLedgerException.class,
                        () -> bound.appendEvent(stale, event("stale", 4_000, Map.of())).blockingGet()).code());

        jdbc.update("""
                UPDATE tianji_agent.agent_session_lease SET lease_expires_at=clock_timestamp()-interval '1 second'
                WHERE tenant_id=? AND subject_type='USER' AND subject_id=? AND app_name='tianji' AND session_id='s1'
                """, owner.tenantId(), owner.subjectId());
        SessionLease nextLease = sessions.acquireSessionLease("s1", run, Duration.ofSeconds(30));
        assertFalse(sessions.releaseSessionLease(firstLease));
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                assertThrows(AgentLedgerException.class,
                        () -> bound.getSession("tianji", bound.adkUserId(), "s1", Optional.empty()).blockingGet()).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                assertThrows(AgentLedgerException.class,
                        () -> bound.appendEvent(current, event("old-owner", 5_000, Map.of())).blockingGet()).code());
        PostgresAdkSessionService resumed = sessions.withLease(nextLease);
        Session reloaded = resumed.getSession("tianji", resumed.adkUserId(), "s1", Optional.empty()).blockingGet();
        resumed.appendEvent(reloaded, event("event-2", 1_000, Map.of("user:preference", State.REMOVED))).blockingGet();
        resumed.appendEvent(reloaded, event("event-3", 2_000, Map.of("local", 3))).blockingGet();
        Session recovered = service(owner, false).getSession("tianji", sessions.adkUserId(), "s1", Optional.empty()).blockingGet();
        assertEquals(List.of("event-1", "event-2", "event-3"), recovered.immutableEvents().stream().map(Event::id).toList());
        assertFalse(recovered.state().containsKey("user:preference"));
        assertEquals(3, recovered.state().get("local"));
        var filter = GetSessionConfig.builder().numRecentEvents(2).afterTimestamp(Instant.ofEpochMilli(2_000)).build();
        Session filtered = resumed.getSession("tianji", resumed.adkUserId(), "s1", Optional.of(filter)).blockingGet();
        assertEquals(List.of("event-3"), filtered.immutableEvents().stream().map(Event::id).toList());

        AgentRunDTO live = ledger.get(owner, new GetAgentRunQuery(run.runId()));
        ledger.cancel(owner, new CancelAgentRunCommand(run.runId(), "stop", live.revision(), "test"));
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                assertThrows(AgentLedgerException.class,
                        () -> resumed.appendEvent(reloaded, event("after-cancel", 6_000, Map.of())).blockingGet()).code());
        AgentRunDTO cancelling = ledger.get(owner, new GetAgentRunQuery(run.runId()));
        ledger.finish(owner, run, cancelling.revision(), result(run, CANCELLED));
        assertTrue(sessions.releaseSessionLease(nextLease));
        sessions.deleteSession("tianji", sessions.adkUserId(), "s1").blockingAwait();
    }

    @Test
    void scoped_state_and_event_roll_back_together_on_invalid_delta() {
        TrustedAgentActor owner = actor("rollback");
        PostgresAdkSessionService sessions = service(owner, true);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of("app:shared", "before"), "s2").blockingGet();
        AgentRunLease run = claim(owner, "s2", "run");
        PostgresAdkSessionService bound = sessions.withLease(sessions.acquireSessionLease("s2", run, Duration.ofSeconds(30)));
        Session current = bound.getSession("tianji", bound.adkUserId(), "s2", Optional.empty()).blockingGet();
        assertThrows(IllegalArgumentException.class,
                () -> bound.appendEvent(current, event("bad", 1_000, Map.of("app:shared", "after", "user:", "bad"))).blockingGet());
        Session reloaded = sessions.getSession("tianji", sessions.adkUserId(), "s2", Optional.empty()).blockingGet();
        assertEquals("before", reloaded.state().get("app:shared"));
        assertTrue(reloaded.immutableEvents().isEmpty());
    }

    @Test
    void an_expired_run_cannot_append_even_when_the_session_lease_is_still_live() {
        TrustedAgentActor owner = actor("expired-run");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s-expired").blockingGet();
        AgentRunLease run = claim(owner, "s-expired", "run");
        SessionLease sessionLease = sessions.acquireSessionLease("s-expired", run, Duration.ofSeconds(30));
        PostgresAdkSessionService bound = sessions.withLease(sessionLease);
        Session current = bound.getSession("tianji", bound.adkUserId(), "s-expired", Optional.empty()).blockingGet();
        jdbc.update("UPDATE tianji_agent.agent_run SET lease_expires_at=clock_timestamp()-interval '1 second' "
                + "WHERE tenant_id=? AND run_id=?", owner.tenantId(), run.runId());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> bound.appendEvent(current, event("late", 1_000, Map.of())).blockingGet()).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> sessions.renewSessionLease(sessionLease, Duration.ofSeconds(30))).code());
        assertTrue(sessions.getSession("tianji", sessions.adkUserId(), "s-expired", Optional.empty())
                .blockingGet().immutableEvents().isEmpty());
    }

    @Test
    void lease_expiring_while_a_state_write_waits_rolls_back_the_entire_event() throws Exception {
        TrustedAgentActor owner = actor("lease-wait");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s-wait").blockingGet();
        AgentRunLease run = claim(owner, "s-wait", "run");
        SessionLease sessionLease = sessions.acquireSessionLease("s-wait", run, Duration.ofSeconds(3));
        PostgresAdkSessionService bound = sessions.withLease(sessionLease);
        Session current = bound.getSession("tianji", bound.adkUserId(), "s-wait", Optional.empty()).blockingGet();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate holder = new TransactionTemplate(new DataSourceTransactionManager(PostgresTestDatabase.dataSource()));
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> held = pool.submit(() -> holder.executeWithoutResult(status -> {
                jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> { },
                        "tianji:adk-session-scope:" + owner.tenantId() + ":tianji");
                locked.countDown();
                try { assertTrue(release.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<Object> append = pool.submit(() -> {
                try { bound.appendEvent(current, event("waiting", 1_000, Map.of("user:key", "value"))).blockingGet(); return Boolean.TRUE; }
                catch (AgentLedgerException failure) { return failure.code(); }
            });
            try {
                await().atMost(Duration.ofSeconds(8)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype='advisory' AND NOT granted)", Boolean.class)));
                await().atMost(Duration.ofSeconds(5)).until(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT clock_timestamp() > ?", Boolean.class, Timestamp.from(sessionLease.expiresAt()))));
            } finally {
                release.countDown();
            }
            held.get(5, TimeUnit.SECONDS);
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, append.get(5, TimeUnit.SECONDS));
        }
        Session after = sessions.getSession("tianji", sessions.adkUserId(), "s-wait", Optional.empty()).blockingGet();
        assertTrue(after.immutableEvents().isEmpty());
        assertFalse(after.state().containsKey("user:key"));
    }

    @Test
    void malformed_persisted_event_identity_is_rejected() {
        TrustedAgentActor owner = actor("corrupt");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s-corrupt").blockingGet();
        AgentRunLease run = claim(owner, "s-corrupt", "run");
        PostgresAdkSessionService bound = sessions.withLease(sessions.acquireSessionLease("s-corrupt", run, Duration.ofSeconds(30)));
        Session current = bound.getSession("tianji", bound.adkUserId(), "s-corrupt", Optional.empty()).blockingGet();
        bound.appendEvent(current, event("stored", 1_000, Map.of())).blockingGet();
        jdbc.update("UPDATE tianji_agent.agent_session_event SET native_event_id='different' "
                + "WHERE tenant_id=? AND subject_type='USER' AND subject_id=? AND app_name='tianji' AND session_id='s-corrupt'",
                owner.tenantId(), owner.subjectId());
        assertThrows(IllegalStateException.class,
                () -> sessions.getSession("tianji", sessions.adkUserId(), "s-corrupt", Optional.empty()).blockingGet());
    }

    @Test
    void concurrent_runs_cannot_hold_the_same_session_lease() throws Exception {
        TrustedAgentActor owner = actor("concurrent");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s3").blockingGet();
        AgentRunLease first = claim(owner, "s3", "first");
        AgentRunLease second = claim(owner, "s3", "second");
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = new ArrayList<>();
            for (AgentRunLease run : List.of(first, second)) futures.add(pool.submit(() -> {
                gate.await();
                try { return sessions.acquireSessionLease("s3", run, Duration.ofSeconds(30)); }
                catch (AgentLedgerException failure) { return failure.code(); }
            }));
            gate.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(SessionLease.class::isInstance).count());
            assertEquals(1, results.stream().filter(AgentErrorCodeEnum.TIANJI_SESSION_BUSY::equals).count());
            SessionLease winner = (SessionLease) results.stream().filter(SessionLease.class::isInstance).findFirst().orElseThrow();
            assertTrue(sessions.releaseSessionLease(winner));
        }
    }

    @Test
    void concurrent_start_and_delete_never_leave_an_active_run_with_a_missing_session() throws Exception {
        TrustedAgentActor owner = actor("start-delete");
        PostgresAdkSessionService sessions = service(owner, false);
        ledger.publish(owner, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                String id = "race-" + iteration;
                sessions.createSession("tianji", sessions.adkUserId(), Map.of(), id).blockingGet();
                CountDownLatch gate = new CountDownLatch(1);
                Future<Object> start = pool.submit(() -> {
                    gate.await();
                    try { return start(owner, id, id); }
                    catch (AgentLedgerException failure) { return failure.code(); }
                });
                Future<Object> delete = pool.submit(() -> {
                    gate.await();
                    try { sessions.deleteSession("tianji", sessions.adkUserId(), id).blockingAwait(); return Boolean.TRUE; }
                    catch (AgentLedgerException failure) { return failure.code(); }
                });
                gate.countDown();
                Object startResult = start.get(10, TimeUnit.SECONDS);
                Object deleteResult = delete.get(10, TimeUnit.SECONDS);
                if (startResult instanceof AgentRunDTO) {
                    assertEquals(AgentErrorCodeEnum.TIANJI_SESSION_BUSY, deleteResult);
                    assertNotNull(sessions.getSession("tianji", sessions.adkUserId(), id, Optional.empty()).blockingGet());
                } else {
                    assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, startResult);
                    assertEquals(Boolean.TRUE, deleteResult);
                    assertNull(sessions.getSession("tianji", sessions.adkUserId(), id, Optional.empty()).blockingGet());
                }
            }
        }
    }

    @Test
    void concurrent_sessions_preserve_both_user_scoped_state_updates() throws Exception {
        TrustedAgentActor owner = actor("scope-race");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "scope-one").blockingGet();
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "scope-two").blockingGet();
        AgentRunLease firstRun = claim(owner, "scope-one", "one");
        AgentRunLease secondRun = claim(owner, "scope-two", "two");
        PostgresAdkSessionService first = sessions.withLease(sessions.acquireSessionLease("scope-one", firstRun, Duration.ofSeconds(30)));
        PostgresAdkSessionService second = sessions.withLease(sessions.acquireSessionLease("scope-two", secondRun, Duration.ofSeconds(30)));
        Session firstSession = first.getSession("tianji", first.adkUserId(), "scope-one", Optional.empty()).blockingGet();
        Session secondSession = second.getSession("tianji", second.adkUserId(), "scope-two", Optional.empty()).blockingGet();
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> left = pool.submit(() -> {
                gate.await();
                first.appendEvent(firstSession, event("left", 1_000, Map.of("user:left", "L"))).blockingGet();
                return null;
            });
            Future<?> right = pool.submit(() -> {
                gate.await();
                second.appendEvent(secondSession, event("right", 2_000, Map.of("user:right", "R"))).blockingGet();
                return null;
            });
            gate.countDown();
            left.get(10, TimeUnit.SECONDS);
            right.get(10, TimeUnit.SECONDS);
        }
        Session reloaded = sessions.getSession("tianji", sessions.adkUserId(), "scope-one", Optional.empty()).blockingGet();
        assertEquals("L", reloaded.state().get("user:left"));
        assertEquals("R", reloaded.state().get("user:right"));
    }

    @Test
    void concurrent_cancellation_and_append_are_ordered_by_the_run_fence() throws Exception {
        TrustedAgentActor owner = actor("cancel-append");
        PostgresAdkSessionService sessions = service(owner, false);
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 20; iteration++) {
                String id = "cancel-race-" + iteration;
                sessions.createSession("tianji", sessions.adkUserId(), Map.of(), id).blockingGet();
                AgentRunLease run = claim(owner, id, id);
                AgentRunDTO running = ledger.get(owner, new GetAgentRunQuery(run.runId()));
                PostgresAdkSessionService bound = sessions.withLease(sessions.acquireSessionLease(id, run, Duration.ofSeconds(30)));
                Session snapshot = bound.getSession("tianji", bound.adkUserId(), id, Optional.empty()).blockingGet();
                CountDownLatch gate = new CountDownLatch(1);
                Future<Object> append = pool.submit(() -> {
                    gate.await();
                    try { bound.appendEvent(snapshot, event("event", 1_000, Map.of("value", 1))).blockingGet(); return Boolean.TRUE; }
                    catch (AgentLedgerException failure) { return failure.code(); }
                });
                Future<CancelAgentRunResult> cancel = pool.submit(() -> {
                    gate.await();
                    return ledger.cancel(owner, new CancelAgentRunCommand(run.runId(), "cancel-" + id,
                            running.revision(), "test"));
                });
                gate.countDown();
                Object appended = append.get(10, TimeUnit.SECONDS);
                assertTrue(cancel.get(10, TimeUnit.SECONDS).accepted());
                Session stored = sessions.getSession("tianji", sessions.adkUserId(), id, Optional.empty()).blockingGet();
                if (Boolean.TRUE.equals(appended)) {
                    assertEquals(1, stored.immutableEvents().size());
                    assertEquals(Boolean.TRUE, jdbc.queryForObject("""
                            SELECT s.updated_at <= r.cancel_requested_at
                            FROM tianji_agent.agent_session s JOIN tianji_agent.agent_run r
                              ON r.tenant_id=s.tenant_id AND r.subject_type=s.subject_type AND r.subject_id=s.subject_id
                             AND r.session_id=s.session_id
                            WHERE s.tenant_id=? AND s.subject_type='USER' AND s.subject_id=?
                              AND s.app_name='tianji' AND s.session_id=? AND r.run_id=?
                            """, Boolean.class, owner.tenantId(), owner.subjectId(), id, run.runId()));
                } else {
                    assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, appended);
                    assertTrue(stored.immutableEvents().isEmpty());
                }
            }
        }
    }

    @Test
    void native_runner_restores_a_tool_exchange_and_prior_turn_for_the_next_run() {
        TrustedAgentActor owner = actor("native-runner");
        PostgresAdkSessionService firstStore = service(owner, false);
        firstStore.createSession("tianji", firstStore.adkUserId(), Map.of(), "conversation").blockingGet();
        AgentRunLease firstRun = claim(owner, "conversation", "first-turn");
        SessionLease firstLease = firstStore.acquireSessionLease("conversation", firstRun, Duration.ofSeconds(30));
        ScriptModel firstModel = new ScriptModel(true, "first answer");
        runNative(firstStore.withLease(firstLease), firstModel, "first question");
        assertEquals(2, firstModel.requests.size());
        AgentRunDTO firstStatus = ledger.get(owner, new GetAgentRunQuery(firstRun.runId()));
        ledger.finish(owner, firstRun, firstStatus.revision(), result(firstRun, COMPLETED));
        assertTrue(firstStore.releaseSessionLease(firstLease));

        PostgresAdkSessionService secondStore = service(owner, false);
        AgentRunLease secondRun = claim(owner, "conversation", "second-turn");
        SessionLease secondLease = secondStore.acquireSessionLease("conversation", secondRun, Duration.ofSeconds(30));
        ScriptModel secondModel = new ScriptModel(false, "second answer");
        runNative(secondStore.withLease(secondLease), secondModel, "second question");
        assertEquals(1, secondModel.requests.size());
        List<Part> history = secondModel.requests.getFirst().contents().stream()
                .flatMap(content -> content.parts().orElse(List.of()).stream()).toList();
        List<String> text = history.stream().flatMap(part -> part.text().stream()).toList();
        assertTrue(text.containsAll(List.of("first question", "first answer", "second question")));
        assertTrue(history.stream().anyMatch(part -> part.functionCall().flatMap(FunctionCall::id).orElse("").equals("call-1")));
        assertTrue(history.stream().anyMatch(part -> part.functionResponse().flatMap(response -> response.id()).orElse("").equals("call-1")));
        AgentRunDTO secondStatus = ledger.get(owner, new GetAgentRunQuery(secondRun.runId()));
        ledger.finish(owner, secondRun, secondStatus.revision(), result(secondRun, COMPLETED));
        assertTrue(secondStore.releaseSessionLease(secondLease));
    }

    @Test
    void history_is_bounded_and_recent_queries_remain_available() {
        TrustedAgentActor owner = actor("bounded");
        PostgresAdkSessionService sessions = service(owner, false);
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s4").blockingGet();
        AgentRunLease run = claim(owner, "s4", "run");
        PostgresAdkSessionService bound = sessions.withLease(sessions.acquireSessionLease("s4", run, Duration.ofSeconds(30)));
        Session current = bound.getSession("tianji", bound.adkUserId(), "s4", Optional.empty()).blockingGet();
        bound.appendEvent(current, event("event-1", 1_000, Map.of())).blockingGet();
        jdbc.update("""
                INSERT INTO tianji_agent.agent_session_event
                    (tenant_id, subject_type, subject_id, app_name, session_id, sequence, native_event_id, event_timestamp_ms, payload)
                SELECT tenant_id, subject_type, subject_id, app_name, session_id, n,
                       'event-' || n, event_timestamp_ms, jsonb_set(payload::jsonb, '{event,id}', to_jsonb('event-' || n), false)::text
                FROM tianji_agent.agent_session_event CROSS JOIN generate_series(2, 1001) AS n
                WHERE tenant_id=? AND subject_type='USER' AND subject_id=? AND app_name='tianji' AND session_id='s4' AND sequence=1
                """, owner.tenantId(), owner.subjectId());
        jdbc.update("""
                UPDATE tianji_agent.agent_session SET revision=1001, last_event_sequence=1001
                WHERE tenant_id=? AND subject_type='USER' AND subject_id=? AND app_name='tianji' AND session_id='s4'
                """, owner.tenantId(), owner.subjectId());
        assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> sessions.getSession("tianji", sessions.adkUserId(), "s4", Optional.empty()).blockingGet()).code());
        var config = GetSessionConfig.builder().numRecentEvents(10).build();
        Session recent = sessions.getSession("tianji", sessions.adkUserId(), "s4", Optional.of(config)).blockingGet();
        assertEquals(10, recent.immutableEvents().size());
        assertEquals("event-1001", recent.immutableEvents().getLast().id());
    }

    private static TrustedAgentActor actor(String subject) {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, subject);
    }

    private static PostgresAdkSessionService service(TrustedAgentActor actor, boolean appWrites) {
        return new PostgresAdkSessionService(PostgresTestDatabase.dataSource(), actor, "tianji", appWrites);
    }

    private static AgentRunDTO start(TrustedAgentActor actor, String session, String key) {
        return ledger.start(actor, new StartAgentRunCommand("review", 1, session, key, null, null,
                new AgentInputDTO("question", List.of(), Map.of()), null));
    }

    private static AgentRunLease claim(TrustedAgentActor actor, String session, String key) {
        ledger.publish(actor, AgentJsonCodecTest.spec(1, Map.of(), Set.of()));
        AgentRunDTO queued = start(actor, session, key);
        return ledger.claim(actor, new GetAgentRunQuery(queued.runId()), queued.revision(), "worker-" + key,
                Duration.ofSeconds(30)).lease();
    }

    private static Event event(String id, long timestamp, Map<String, Object> delta) {
        return Event.builder().id(id).invocationId("invocation").author("agent").timestamp(timestamp)
                .content(Content.fromParts(Part.fromText(id))).actions(EventActions.builder().stateDelta(delta).build()).build();
    }

    private static void runNative(PostgresAdkSessionService sessions, ScriptModel model, String text) {
        LlmAgent agent = LlmAgent.builder().name("assistant").model(model).tools(new LookupTool()).build();
        Runner runner = Runner.builder().agent(agent).appName("tianji").sessionService(sessions).build();
        try {
            runner.runAsync(sessions.adkUserId(), "conversation", Content.fromParts(Part.fromText(text)),
                    RunConfig.builder().setAutoCreateSession(false).setMaxLlmCalls(5).build()).blockingLast();
        } finally {
            runner.close().blockingAwait();
        }
    }

    private static final class LookupTool extends BaseTool {
        private LookupTool() { super("lookup", "Find a test record"); }
        @Override public Optional<FunctionDeclaration> declaration() {
            return Optional.of(FunctionDeclaration.builder().name("lookup").description("Find a test record").build());
        }
        @Override public Single<Map<String, Object>> runAsync(Map<String, Object> arguments, ToolContext context) {
            return Single.just(Map.of("found", "record"));
        }
    }

    private static final class ScriptModel extends BaseLlm {
        private final boolean callTool;
        private final String answer;
        private final List<LlmRequest> requests = new ArrayList<>();

        private ScriptModel(boolean callTool, String answer) {
            super("fixture");
            this.callTool = callTool;
            this.answer = answer;
        }

        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            requests.add(request);
            Part part = callTool && requests.size() == 1
                    ? Part.builder().functionCall(FunctionCall.builder().id("call-1").name("lookup")
                            .args(Map.of("query", "record")).build()).build()
                    : Part.fromText(answer);
            return Flowable.just(LlmResponse.builder().content(Content.builder().role("model").parts(part).build())
                    .turnComplete(!(callTool && requests.size() == 1)).partial(false).build());
        }

        @Override public BaseLlmConnection connect(LlmRequest request) { throw new UnsupportedOperationException(); }
    }

    private static AgentExecutionResult result(AgentRunLease lease, top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum status) {
        return new AgentExecutionResult(lease.runId(), status, Map.of(), null, null, null, null, "NONE");
    }
}
