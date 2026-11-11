package top.egon.cola.platform.tianji.agent.server.security;

import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.ExecutionAuthorizationContract;
import top.egon.cola.platform.tianquan.jianshen.starter.client.HttpExecutionAuthorizationClient;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiGrantDispatchPostgresTest.fixtureDefinition;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiGrantDispatchPostgresTest.fixtureEstimator;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiGrantDispatchPostgresTest.fixtureFlow;
import static top.egon.cola.platform.tianji.agent.server.security.TianjiGrantDispatchPostgresTest.fixtureModel;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

@EnabledIfSystemProperty(named = "tianji.postgres.queue.recovery", matches = "true")
@Timeout(90)
class TianjiQueuedGrantRecoveryPostgresTest {
    private static final String CHILD_SWITCH = "tianji.postgres.queue.recovery.producer";
    private static final long CHILD_SECONDS = 16;
    private static final long TENANT = 90_201;
    private static final URI ISSUER = URI.create("https://queue-fixture.example/tenant/" + TENANT);
    private static final URI RESOURCE = URI.create("https://queue-fixture.example/tianji");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(
            TENANT, TrustedAgentActor.SubjectType.USER, "queue-fixture-owner", ISSUER.toASCIIString());
    private static final String ORIGINAL = "queue-before-first-claim";
    private static final String ORIGINAL_GRANT = "9" + "4".repeat(62);
    private static final String DENIED_GRANT = "9" + "5".repeat(62);
    private static final String EXPIRING_GRANT = "9" + "6".repeat(62);

    @Test
    void a_fresh_product_poller_recovers_one_committed_grant_ready_queue_after_producer_exit() throws Exception {
        String url = System.getProperty("tianji.postgres.queue.recovery.url");
        String databaseName = QueuedGrantRecoveryHandoff.databaseName(url);
        var setup = dataSource(url, null);
        var setupJdbc = jdbc(setup);
        String role = setupJdbc.queryForObject("SELECT current_user", String.class);
        var request = new QueuedGrantRecoveryHandoff(url, role);
        assertIdentity(setupJdbc, databaseName, role);
        assertEquals(0, setupJdbc.queryForObject("""
                SELECT count(*) FROM pg_catalog.pg_namespace WHERE nspname='tianji_agent'
                """, Integer.class));
        var flyway = flyway(setup);
        assertEquals(9, flyway.migrate().migrationsExecuted);
        flyway.validate();

        var observer = new AgentLedgerService(setup);
        var producer = startProducer(request);
        GetAgentRunQuery query;
        AgentRunDTO queued;
        AgentExecutionGrant.Ticket grant;
        try {
            assertNotEquals(ProcessHandle.current().pid(), producer.pid());
            query = awaitQueuedCut(setupJdbc, producer);
            queued = observer.get(OWNER, query);
            grant = assertQueuedCut(observer, setupJdbc, query, queued);
            assertTrue(producer.isAlive(), "producer must be alive at the durable pre-claim cut");
        } finally {
            reap(producer);
        }
        assertNotEquals(0, producer.exitValue());

        var replacementSource = dataSource(url, role);
        var replacementJdbc = jdbc(replacementSource);
        assertIdentity(replacementJdbc, databaseName, role);
        var replacement = new AgentLedgerService(replacementSource);
        assertEquals(grant, assertQueuedCut(replacement, replacementJdbc, query, queued));
        var grants = new AgentExecutionGrantStore(replacementSource, replacement);
        var missing = new GetAgentRunQuery(replacement.start(OWNER, command("queue-missing-grant")).runId());
        var denied = new GetAgentRunQuery(replacement.start(OWNER, command("queue-current-denial")).runId());
        sealGrant(replacement, grants, denied, DENIED_GRANT, 45);
        var expired = new GetAgentRunQuery(replacement.start(OWNER, command("queue-expired-grant")).runId());
        var expiry = sealGrant(replacement, grants, expired, EXPIRING_GRANT, 3).expiresAt();
        await().atMost(Duration.ofSeconds(5)).until(() -> !replacement.databaseNow().isBefore(expiry));
        var eligible = replacement.grantReadyRuns(null, 8);
        assertEquals(2, eligible.size());
        assertTrue(eligible.stream().allMatch(candidate -> OWNER.equals(candidate.actor())));
        assertEquals(Set.of(query, denied), eligible.stream().map(candidate -> candidate.query()).collect(Collectors.toSet()));

        verifyFreshPoller(replacement, grants, query, missing, expired, denied, queued);
        assertTrue(Boolean.TRUE.equals(replacementJdbc.queryForObject("""
                SELECT issue_epoch=1 AND grant_id=? AND ciphertext=? AND expires_at=?
                FROM tianji_agent.agent_run_execution_grant WHERE tenant_id=? AND run_id=?
                """, Boolean.class, grant.grantId(), grant.ciphertext(), java.sql.Timestamp.from(grant.expiresAt()),
                OWNER.tenantId(), query.runId())));
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0 || !Boolean.getBoolean(CHILD_SWITCH)) {
            throw new IllegalArgumentException("Disposable queue producer is disabled");
        }
        var request = QueuedGrantRecoveryHandoff.receive(System.in);
        var source = dataSource(request.url(), request.setupRole());
        var jdbc = jdbc(source);
        assertIdentity(jdbc, QueuedGrantRecoveryHandoff.databaseName(request.url()), request.setupRole());
        flyway(source).validate();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run", Integer.class));

        var ledger = new AgentLedgerService(source);
        ledger.publish(OWNER, fixtureDefinition());
        var run = ledger.start(OWNER, command(ORIGINAL));
        assertEquals(QUEUED, run.status());
        var store = new AgentExecutionGrantStore(source, ledger);
        sealGrant(ledger, store, new GetAgentRunQuery(run.runId()), ORIGINAL_GRANT, 45);
        new CountDownLatch(1).await(CHILD_SECONDS * 2, TimeUnit.SECONDS);
        fail("queue producer was not force-stopped at the pre-claim cut");
    }

    private static AgentExecutionGrant.Ticket assertQueuedCut(AgentLedgerService ledger, JdbcTemplate jdbc,
            GetAgentRunQuery query, AgentRunDTO queued) {
        assertEquals(queued, ledger.get(OWNER, query));
        assertEquals(QUEUED, queued.status());
        assertEquals(0, queued.attempt());
        assertEquals(0, queued.revision());
        assertEquals(1, queued.lastEventSequence());
        var events = ledger.events(OWNER, new AgentEventsQuery(query.runId(), 0, 2));
        assertEquals(1, events.size());
        assertEquals("RUN_QUEUED", events.getFirst().type());
        assertTrue(ledger.getResult(OWNER, query).isEmpty());
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT r.lease_epoch=0 AND r.lease_owner IS NULL AND r.lease_expires_at IS NULL
                  AND r.model_calls_reserved=0 AND r.tool_calls_reserved=0 AND r.result IS NULL
                  AND g.issue_epoch=1 AND g.grant_id=? AND g.expires_at>clock_timestamp()
                  AND NOT EXISTS (SELECT 1 FROM tianji_agent.agent_budget_reservation b
                                  WHERE b.tenant_id=r.tenant_id AND b.run_id=r.run_id)
                FROM tianji_agent.agent_run r JOIN tianji_agent.agent_run_execution_grant g USING (tenant_id,run_id)
                WHERE r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=? AND r.run_id=?
                """, Boolean.class, ORIGINAL_GRANT, OWNER.tenantId(), OWNER.subjectType().name(),
                OWNER.issuerFingerprint(), OWNER.subjectId(), query.runId())));
        var store = new AgentExecutionGrantStore(jdbc.getDataSource(), ledger);
        return store.current(ledger.executionMaterial(OWNER, query).authorization(), RESOURCE).orElseThrow();
    }

    private static void verifyFreshPoller(AgentLedgerService ledger, AgentExecutionGrantStore store,
            GetAgentRunQuery original, GetAgentRunQuery missing, GetAgentRunQuery expired,
            GetAgentRunQuery denied, AgentRunDTO queued) {
        var registry = mock(TrustedIssuerRegistry.class);
        var trust = new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create(ISSUER + "/jwks"), "tenant:" + TENANT,
                TrustedIssuerRegistry.RealmType.TENANT, Long.toString(TENANT), TrustedIssuerRegistry.Status.ACTIVE, 1);
        when(registry.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trust));
        var decisions = mock(HttpExecutionAuthorizationClient.class);
        var originalDecisions = new AtomicInteger();
        var deniedDecisions = new AtomicInteger();
        when(decisions.decide(any(), any())).thenAnswer(call -> {
            ExecutionAuthorizationContract.Request request = call.getArgument(0);
            HttpExecutionAuthorizationClient.Expectation expected = call.getArgument(1);
            assertEquals(OWNER.subjectId(), expected.subject());
            assertEquals(ISSUER, expected.issuer());
            assertEquals(Long.toString(TENANT), expected.tenantId());
            if (denied.runId().equals(expected.intentId())) {
                assertEquals(DENIED_GRANT, request.grantId());
                deniedDecisions.incrementAndGet();
                throw new HttpExecutionAuthorizationClient.ExecutionAuthorizationDeniedException();
            }
            assertEquals(original.runId(), expected.intentId());
            assertEquals(ORIGINAL_GRANT, request.grantId());
            originalDecisions.incrementAndGet();
            return new ExecutionAuthorizationContract.Decision(request.grantId(), expected.issuer().toString(),
                    expected.tenantId(), expected.subject(), expected.resource().toString(), expected.purpose(),
                    expected.intentId(), expected.bindingSha256(), expected.sessionRequired(), expected.deadlineAt(),
                    ledger.databaseNow().plusSeconds(10), 1, 1);
        });
        var streams = new AtomicInteger();
        var host = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2));
        var control = Executors.newScheduledThreadPool(2);
        try (var validators = Validation.buildDefaultValidatorFactory();
             var authorizer = new TianjiExecutionGrantAuthorizer(ledger, store, RESOURCE, "tianji", registry,
                     Map.of(OWNER.issuerFingerprint(), new TianjiExecutionGrantAuthorizer.BoundClient(ISSUER,
                             Long.toString(TENANT), decisions)), Duration.ofSeconds(5), 2, Clock.systemUTC())) {
            var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
            var worker = new AdkAgentRunExecutor(ledger, fixtureFlow(), validator,
                    Map.of("fixtureModel", fixtureModel(streams)), Map.of(), control, fixtureEstimator(), null, false, authorizer);
            var dispatcher = new AgentRunDispatcher(host, worker, ledger);
            var scheduler = mock(TaskScheduler.class);
            var tick = new AtomicReference<Runnable>();
            doAnswer(call -> {
                tick.set(call.getArgument(0));
                return mock(ScheduledFuture.class);
            }).when(scheduler).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            try (var poller = new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler,
                    8, 2, Duration.ofSeconds(1), Duration.ofSeconds(5))) {
                poller.start();
                tick.get().run();
                await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
                    assertEquals(COMPLETED, ledger.get(OWNER, original).status());
                    assertEquals(SUSPENDED, ledger.get(OWNER, denied).status());
                });
                tick.get().run();
                tick.get().run();
            }
            var recovered = ledger.get(OWNER, original);
            assertEquals(1, recovered.attempt());
            assertEquals(queued.runId(), recovered.runId());
            assertEquals(queued.createdAt(), recovered.createdAt());
            assertEquals(queued.definitionVersion(), recovered.definitionVersion());
            assertEquals("done", ledger.getResult(OWNER, original).orElseThrow().output().get("text"));
            var events = ledger.events(OWNER, new AgentEventsQuery(original.runId(), 0, 20));
            assertEquals(1L, events.stream().filter(event -> "RUN_QUEUED".equals(event.type())).count());
            assertEquals(1L, events.stream().filter(event -> "RUN_STATUS_CHANGED".equals(event.type())
                    && "QUEUED".equals(event.payload().get("from")) && "RUNNING".equals(event.payload().get("status"))).count());
            assertEquals(1, streams.get());
            assertTrue(originalDecisions.get() >= 2, "current authority must be reached for claim and model");
            assertEquals(1, deniedDecisions.get());
            for (var excluded : List.of(missing, expired)) {
                assertEquals(QUEUED, ledger.get(OWNER, excluded).status());
                assertEquals(0, ledger.get(OWNER, excluded).attempt());
                assertTrue(ledger.getResult(OWNER, excluded).isEmpty());
            }
            assertEquals("AUTHORIZATION_DENIED", ledger.get(OWNER, denied).waitReason());
            assertEquals(0, ledger.get(OWNER, denied).attempt());
            assertTrue(ledger.getResult(OWNER, denied).isEmpty());
        } finally {
            host.shutdownNow();
            control.shutdownNow();
        }
    }

    private static StartAgentRunCommand command(String idempotencyKey) {
        return new StartAgentRunCommand("model", 1, null, idempotencyKey, null, null,
                new AgentInputDTO("input", List.of(), Map.of()), null);
    }

    private static AgentExecutionGrant.Ticket sealGrant(AgentLedgerService ledger, AgentExecutionGrantStore store,
            GetAgentRunQuery query, String grantId, long liveSeconds) {
        var reservation = store.reserve(OWNER, query.runId(), RESOURCE).orElseThrow();
        var ticket = new AgentExecutionGrant.Ticket(grantId, "ENC:v1:key:nonce:queue-fixture",
                ledger.databaseNow().plusSeconds(liveSeconds));
        assertTrue(store.complete(reservation, ticket));
        return ticket;
    }

    private static GetAgentRunQuery awaitQueuedCut(JdbcTemplate jdbc, Process producer) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CHILD_SECONDS);
        while (System.nanoTime() - deadline < 0 && producer.isAlive()) {
            var matches = jdbc.queryForList("""
                    SELECT r.run_id FROM tianji_agent.agent_run r
                    JOIN tianji_agent.agent_run_execution_grant g USING (tenant_id,run_id)
                    WHERE r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=? AND r.idempotency_key=?
                      AND r.status='QUEUED' AND g.grant_id=? AND g.ciphertext IS NOT NULL AND g.expires_at>clock_timestamp()
                    LIMIT 2
                    """, String.class, OWNER.tenantId(), OWNER.subjectType().name(), OWNER.issuerFingerprint(),
                    OWNER.subjectId(), ORIGINAL, ORIGINAL_GRANT);
            if (!matches.isEmpty()) {
                assertEquals(1, matches.size());
                return new GetAgentRunQuery(matches.getFirst());
            }
            Thread.sleep(20);
        }
        return fail("producer did not reach the committed grant-ready pre-claim cut");
    }

    private static Process startProducer(QueuedGrantRecoveryHandoff request) throws IOException {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        if (classpath == null || classpath.isBlank()) throw new IllegalStateException("Queue producer classpath is missing");
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        var builder = new ProcessBuilder(java.toString(), "-D" + CHILD_SWITCH + "=true",
                "-Dotel.traces.exporter=none", "-Dotel.metrics.exporter=none", "-Dotel.logs.exporter=none",
                "-cp", classpath, TianjiQueuedGrantRecoveryPostgresTest.class.getName())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().put("ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS", "false");
        var child = builder.start();
        try {
            try (var pipe = child.getOutputStream()) {
                request.send(pipe);
            }
            return child;
        } catch (IOException | RuntimeException failure) {
            try { reap(child); }
            catch (RuntimeException | AssertionError cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private static void reap(Process child) {
        if (child.isAlive()) child.destroyForcibly();
        boolean interrupted = Thread.interrupted();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(CHILD_SECONDS);
        try {
            while (child.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) fail("queue producer was not reaped");
                try { assertTrue(child.waitFor(remaining, TimeUnit.NANOSECONDS), "queue producer was not reaped"); }
                catch (InterruptedException waitInterrupted) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void assertIdentity(JdbcTemplate jdbc, String databaseName, String role) {
        assertTrue(Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT current_database()=? AND current_user=? AND inet_server_addr()=inet '127.0.0.1'
                  AND NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'
                  AND current_setting('session_replication_role')='origin' AND current_setting('transaction_read_only')='off'
                """, Boolean.class, databaseName, role)));
    }

    private static JdbcTemplate jdbc(DriverManagerDataSource source) {
        var jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(4);
        return jdbc;
    }

    private static DriverManagerDataSource dataSource(String url, String role) {
        var source = new DriverManagerDataSource(url);
        if (role != null) source.setUsername(role);
        var limits = new Properties();
        limits.setProperty("connectTimeout", "3");
        limits.setProperty("loginTimeout", "3");
        limits.setProperty("socketTimeout", "7");
        limits.setProperty("options", "-c statement_timeout=5000 -c lock_timeout=2000");
        source.setConnectionProperties(limits);
        return source;
    }

    private static Flyway flyway(DriverManagerDataSource source) {
        return Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).lockRetryCount(0).load();
    }
}
