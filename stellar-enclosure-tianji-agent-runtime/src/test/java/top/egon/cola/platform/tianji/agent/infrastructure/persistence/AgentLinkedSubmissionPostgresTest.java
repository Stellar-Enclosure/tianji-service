package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import static org.junit.jupiter.api.Assertions.*;

/** All migrations and race tests use a fresh, explicitly supplied loopback database. */
@EnabledIfSystemProperty(named = "tianji.linked.postgres.test.url", matches = ".+")
@Timeout(120)
class AgentLinkedSubmissionPostgresTest {
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;
    private static AgentLinkedSubmissionService submissions;
    private static TrustedAgentActor service;
    private static String definitionHash;
    private static final AtomicInteger IDS = new AtomicInteger();

    @BeforeAll static void setup() {
        String url = System.getProperty("tianji.linked.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("linked submission tests require a fresh loopback database");
        }
        source = new DriverManagerDataSource(url); jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("linked submission test schema already exists");
        }
        var old = flyway("6"); assertEquals(6, old.migrate().migrationsExecuted); old.validate();
        Map<String, Integer> oldChecksums = checksums();
        ledger = new AgentLedgerService(source);
        service = TrustedAgentActor.verified(700, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://idp.example/700");
        var definition = ledger.publish(service, AgentJsonCodecTest.spec(1, Map.of("mode", "safe"), Set.of()));
        definitionHash = definition.contentSha256();
        var user = TrustedAgentActor.verified(700, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/700");
        var oldRun = ledger.start(user, start("old-run", "old-trace", "old-input"));
        String oldHash = ledger.executionMaterial(user, new GetAgentRunQuery(oldRun.runId())).authorization().snapshotSha256();
        assertThrows(AgentLedgerException.class, () -> new AgentStorageVerifier(source).verifyLinked());
        var current = flyway("7"); assertEquals(1, current.migrate().migrationsExecuted); current.validate();
        assertEquals(oldChecksums, checksums());
        assertNull(ledger.snapshot(user, new GetAgentRunQuery(oldRun.runId())).processLink());
        assertEquals(oldHash, ledger.executionMaterial(user, new GetAgentRunQuery(oldRun.runId())).authorization().snapshotSha256());
        submissions = new AgentLinkedSubmissionService(source, ledger);
    }

    @Test void submission_replays_and_close_cancels_the_owned_run() {
        var command = command("submit", "occurrence-submit", now().plusSeconds(30), "input");
        var query = lookup(command);
        var run = submissions.submit(service, command);
        assertEquals(run.runId(), submissions.submit(service, command).runId());
        assertEquals(command.link(), ledger.snapshot(service, new GetAgentRunQuery(run.runId())).processLink());
        assertEquals(run.runId(), submissions.resolve(service, query).orElseThrow().run().runId());
        var closed = submissions.close(service, query);
        assertTrue(closed.closed()); assertEquals(AgentRunStatusEnum.CANCELLED, closed.run().status());
        assertTrue(submissions.close(service, query).closed());
        assertTrue(submissions.resolve(service, query).orElseThrow().closed());
        assertCode(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, () -> submissions.submit(service, command));
    }

    @Test void terminal_publication_shares_the_ledger_transaction_and_never_contains_result_output() {
        jdbc.execute("CREATE TABLE tianji_agent.test_terminal_publication (event_id varchar(64) PRIMARY KEY, payload jsonb NOT NULL)");
        var fail = new AtomicBoolean(true);
        var codec = new AgentJsonCodec();
        AgentTerminalEventPublisher publisher = new AgentTerminalEventPublisher() {
            @Override public String producerId() { return "agent-east"; }
            @Override public boolean usesDataSource(DataSource candidate) { return candidate == source; }
            @Override public void publish(AgentTerminalEvent event) {
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.hasResource(source));
                jdbc.update("INSERT INTO tianji_agent.test_terminal_publication VALUES (?,CAST(? AS jsonb))", event.eventId(),
                        codec.write(event, AgentJsonCodec.MAX_EVENT_BYTES));
                if (fail.get()) throw new IllegalStateException("controlled terminal publication failure");
            }
        };
        var publishingLedger = AgentLedgerService.withTerminalEvents(source, publisher);
        var linked = new AgentLinkedSubmissionService(source, publishingLedger);
        var cancelCommand = command("terminal-cancel", "occurrence-terminal-cancel", now().plusSeconds(60), "private input");
        var queued = linked.submit(service, cancelCommand);
        assertThrows(IllegalStateException.class, () -> linked.close(service, lookup(cancelCommand)));
        assertEquals(AgentRunStatusEnum.QUEUED, publishingLedger.get(service, new GetAgentRunQuery(queued.runId())).status());
        assertFalse(linked.resolve(service, lookup(cancelCommand)).orElseThrow().closed());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.test_terminal_publication", Integer.class));
        assertEquals(1, publishingLedger.events(service, new top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery(queued.runId(), 0, 20)).size());
        fail.set(false);
        assertEquals(AgentRunStatusEnum.CANCELLED, linked.close(service, lookup(cancelCommand)).run().status());
        linked.close(service, lookup(cancelCommand));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.test_terminal_publication", Integer.class));
        int number = 0;
        for (AgentRunStatusEnum terminal : List.of(AgentRunStatusEnum.COMPLETED, AgentRunStatusEnum.FAILED,
                AgentRunStatusEnum.CANCELLED, AgentRunStatusEnum.TIMED_OUT)) {
            String suffix = Integer.toString(number++);
            var command = command("terminal-finish-" + suffix, "occurrence-terminal-finish-" + suffix, now().plusSeconds(60), "private input");
            var run = linked.submit(service, command);
            var claimed = publishingLedger.claim(service, new GetAgentRunQuery(run.runId()), run.revision(), "terminal-worker", Duration.ofSeconds(30));
            var current = claimed.run();
            if (terminal == AgentRunStatusEnum.CANCELLED) {
                current = publishingLedger.get(service, new GetAgentRunQuery(run.runId()));
                publishingLedger.cancel(service, new top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand(current.runId(),
                        "request-cancel-" + suffix, current.revision(), "controlled cancellation"));
                current = publishingLedger.get(service, new GetAgentRunQuery(run.runId()));
            }
            var result = new AgentExecutionResult(run.runId(), terminal, Map.of("answer", "private result body"), null, "1", null,
                    terminal == AgentRunStatusEnum.FAILED ? "TIANJI_DEPENDENCY_UNAVAILABLE" : null, "UNKNOWN");
            var finished = publishingLedger.finish(service, claimed.lease(), current.revision(), result);
            String eventId = AgentTerminalEvent.eventIdFor("agent-east", service.tenantId(), service.issuerFingerprint(), service.subjectId(), run.runId(), finished.revision());
            String payload = jdbc.queryForObject("SELECT payload::text FROM tianji_agent.test_terminal_publication WHERE event_id=?", String.class, eventId);
            assertFalse(payload.contains("private result body"));
            assertFalse(payload.contains("private input"));
            String storedResult = jdbc.queryForObject("SELECT result::text FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?",
                    String.class, service.tenantId(), run.runId());
            assertTrue(payload.contains(codec.resultHash(storedResult)));
            var reference = publishingLedger.terminalReference(service, new GetAgentRunQuery(run.runId()), Duration.ofSeconds(5)).orElseThrow();
            assertEquals(eventId, reference.eventId());
            assertEquals(codec.resultHash(storedResult), reference.resultSha256());
            assertEquals(command.link(), reference.link());
        }
        var deadlineCommand = command("terminal-deadline", "occurrence-terminal-deadline", now().plusSeconds(60), "input");
        var deadlineRun = linked.submit(service, deadlineCommand);
        jdbc.update("UPDATE tianji_agent.agent_run SET deadline_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                service.tenantId(), deadlineRun.runId());
        assertEquals(AgentRunStatusEnum.TIMED_OUT, publishingLedger.expireDeadline(service, new GetAgentRunQuery(deadlineRun.runId()), deadlineRun.revision()).status());
        assertEquals(6, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.test_terminal_publication", Integer.class));
    }

    @Test void a_closed_unknown_submission_is_a_durable_tombstone() {
        var command = command("unknown", "occurrence-unknown", now().plusSeconds(30), "input");
        var query = lookup(command);
        assertTrue(submissions.resolve(service, query).isEmpty());
        var closed = submissions.close(service, query);
        assertTrue(closed.closed()); assertNull(closed.run());
        assertTrue(submissions.resolve(service, query).orElseThrow().closed());
        assertCode(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, () -> submissions.submit(service, command));
        assertEquals(0, runCount(service, command.start().idempotencyKey()));
    }

    @Test void scope_input_definition_and_actor_mismatches_are_rejected() {
        var command = command("scope", "occurrence-scope", now().plusSeconds(30), "input");
        var run = submissions.submit(service, command);
        var alteredInput = new LinkedAgentStartCommand(start("scope", "trace-scope", "other"), command.link());
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> submissions.submit(service, alteredInput));
        var alteredLink = link("different-occurrence", command.link().dispatchNotAfter(), definitionHash);
        assertTrue(submissions.resolve(service, new LinkedAgentLookupQuery("scope", alteredLink)).isEmpty());
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                () -> submissions.submit(service, new LinkedAgentStartCommand(command.start(), alteredLink)));
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                () -> submissions.submit(service, new LinkedAgentStartCommand(start("different-key", "trace-scope", "input"), command.link())));
        var wrongDefinition = command("wrong-definition", "wrong-definition", now().plusSeconds(30), "input", "0".repeat(64));
        assertCode(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT, () -> submissions.submit(service, wrongDefinition));
        assertTrue(submissions.resolve(service, lookup(wrongDefinition)).isEmpty());
        var user = TrustedAgentActor.verified(700, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/700");
        var legacy = new TrustedAgentActor(700, TrustedAgentActor.SubjectType.SERVICE, "process-worker");
        assertThrows(IllegalArgumentException.class, () -> submissions.submit(user, command));
        assertThrows(IllegalArgumentException.class, () -> submissions.close(legacy, lookup(command)));
        var otherIssuer = TrustedAgentActor.verified(700, TrustedAgentActor.SubjectType.SERVICE, "process-worker", "https://other.example/700");
        assertTrue(submissions.resolve(otherIssuer, lookup(command)).isEmpty());
        assertCode(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, () -> submissions.close(otherIssuer, lookup(command)));
        assertEquals(run.runId(), submissions.resolve(service, lookup(command)).orElseThrow().run().runId());
    }

    @Test void expired_submissions_do_not_create_a_fence_or_run() {
        var command = command("expired", "occurrence-expired", now().minusSeconds(1), "input");
        assertCode(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED, () -> submissions.submit(service, command));
        assertTrue(submissions.resolve(service, lookup(command)).isEmpty());
        assertNull(submissions.close(service, lookup(command)).run());
        assertEquals(0, runCount(service, command.start().idempotencyKey()));
        var tooLate = command("too-late", "occurrence-too-late", now().plus(Duration.ofMinutes(16)), "input");
        assertCode(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, () -> submissions.submit(service, tooLate));
        assertTrue(submissions.resolve(service, lookup(tooLate)).isEmpty());
    }

    @Test void close_requests_cancellation_for_a_running_run_and_preserves_a_terminal_result() {
        var runningCommand = command("running", "occurrence-running", now().plusSeconds(30), "input");
        var running = submissions.submit(service, runningCommand);
        ledger.claim(service, new GetAgentRunQuery(running.runId()), running.revision(), "worker-running", Duration.ofSeconds(30));
        var closed = submissions.close(service, lookup(runningCommand));
        assertEquals(AgentRunStatusEnum.RUNNING, closed.run().status());
        assertTrue(closed.run().cancelRequested());
        assertTrue(submissions.close(service, lookup(runningCommand)).run().cancelRequested());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_event WHERE tenant_id=? AND run_id=? AND event_type='CANCEL_REQUESTED'",
                Integer.class, service.tenantId(), running.runId()));

        var completedCommand = command("completed", "occurrence-completed", now().plusSeconds(30), "input");
        var queued = submissions.submit(service, completedCommand);
        var claimed = ledger.claim(service, new GetAgentRunQuery(queued.runId()), queued.revision(), "worker-completed", Duration.ofSeconds(30));
        var result = new top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult(queued.runId(), AgentRunStatusEnum.COMPLETED,
                Map.of("text", "done"), null, "1", null, null, "NONE");
        ledger.finish(service, claimed.lease(), claimed.run().revision(), result);
        var closedCompleted = submissions.close(service, lookup(completedCommand));
        assertEquals(AgentRunStatusEnum.COMPLETED, closedCompleted.run().status());
        assertEquals(result, ledger.getResult(service, new GetAgentRunQuery(queued.runId())).orElseThrow());
    }

    @Test void concurrent_duplicates_and_close_races_leave_at_most_one_cancelled_run() throws Exception {
        var command = command("concurrent", "occurrence-concurrent", now().plusSeconds(30), "input");
        var startGate = new CountDownLatch(1); List<Future<AgentRunDTO>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 20; index++) futures.add(executor.submit(() -> { startGate.await(); return submissions.submit(service, command); }));
            startGate.countDown();
            String runId = futures.getFirst().get(20, TimeUnit.SECONDS).runId();
            for (var future : futures) assertEquals(runId, future.get(20, TimeUnit.SECONDS).runId());
        }
        assertEquals(1, runCount(service, command.start().idempotencyKey()));
        for (int index = 0; index < 10; index++) {
            String id = "race-" + IDS.incrementAndGet();
            var raced = command(id, "occurrence-" + id, now().plusSeconds(30), "input");
            var gate = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var submitted = executor.submit(() -> { gate.await(); return submissions.submit(service, raced); });
                var closed = executor.submit(() -> { gate.await(); return submissions.close(service, lookup(raced)); });
                gate.countDown();
                try { submitted.get(20, TimeUnit.SECONDS); }
                catch (ExecutionException failure) {
                    var error = assertInstanceOf(AgentLedgerException.class, failure.getCause());
                    assertEquals(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED, error.code());
                }
                var outcome = closed.get(20, TimeUnit.SECONDS);
                assertTrue(outcome.closed());
                if (outcome.run() != null) assertEquals(AgentRunStatusEnum.CANCELLED, outcome.run().status());
                assertTrue(submissions.resolve(service, lookup(raced)).orElseThrow().closed());
                assertTrue(runCount(service, id) <= 1);
            }
        }
    }

    @Test void close_serializes_with_running_event_updates() throws Exception {
        for (int index = 0; index < 5; index++) {
            String id = "running-race-" + IDS.incrementAndGet();
            var command = command(id, "occurrence-" + id, now().plusSeconds(30), "input");
            var queued = submissions.submit(service, command);
            var claimed = ledger.claim(service, new GetAgentRunQuery(queued.runId()), queued.revision(), "worker", Duration.ofSeconds(30));
            var gate = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var event = executor.submit(() -> { gate.await(); return ledger.appendEvent(service, claimed.lease(), claimed.run().revision(),
                        "TEXT_DELTA", "writer", Map.of("text", "chunk")); });
                var closed = executor.submit(() -> { gate.await(); return submissions.close(service, lookup(command)); });
                gate.countDown();
                try { event.get(20, TimeUnit.SECONDS); }
                catch (ExecutionException failure) {
                    assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                            assertInstanceOf(AgentLedgerException.class, failure.getCause()).code());
                }
                assertTrue(closed.get(20, TimeUnit.SECONDS).run().cancelRequested());
                assertTrue(submissions.resolve(service, lookup(command)).orElseThrow().closed());
            }
        }
    }

    @Test void closing_after_deadline_waits_for_an_in_flight_commit_and_cancels_it() throws Exception {
        var barrier = new CommitBarrierDataSource(source);
        var guardedLedger = new AgentLedgerService(barrier);
        var guarded = new AgentLinkedSubmissionService(barrier, guardedLedger);
        var command = command("commit-race", "occurrence-commit-race", now().plusSeconds(3), "input");
        barrier.armed.set(true);
        var started = new CompletableFuture<AgentRunDTO>();
        Thread submitThread = Thread.ofPlatform().name("linked-submit-commit").start(() -> {
            try { started.complete(guarded.submit(service, command)); }
            catch (Throwable failure) { started.completeExceptionally(failure); }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            assertTrue(barrier.entered.await(10, TimeUnit.SECONDS));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(8)).until(() -> now().isAfter(command.link().dispatchNotAfter()));
            var closed = executor.submit(() -> guarded.close(service, lookup(command)));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> jdbc.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'
                      AND query LIKE '%INSERT INTO tianji_agent.agent_process_dispatch%'
                    """, Integer.class) > 0);
            assertFalse(closed.isDone());
            barrier.release.countDown();
            var run = started.get(10, TimeUnit.SECONDS); var result = closed.get(10, TimeUnit.SECONDS);
            assertEquals(run.runId(), result.run().runId());
            assertEquals(AgentRunStatusEnum.CANCELLED, result.run().status());
            assertTrue(guarded.resolve(service, lookup(command)).orElseThrow().closed());
        } finally { barrier.release.countDown(); submitThread.join(10_000); }
        assertFalse(submitThread.isAlive());
    }

    @Test void schema_drift_and_nested_write_transactions_fail_closed() {
        var command = command("nested", "occurrence-nested", now().plusSeconds(30), "input");
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(new DataSourceTransactionManager(source))
                .execute(status -> submissions.submit(service, command)));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(new DataSourceTransactionManager(source))
                .execute(status -> submissions.resolve(service, lookup(command))));
        assertTrue(submissions.resolve(service, lookup(command)).isEmpty());
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        for (String change : List.of("UPDATE tianji_agent.flyway_schema_history SET checksum=0 WHERE version='7'",
                "ALTER TABLE tianji_agent.agent_process_dispatch DROP CONSTRAINT agent_process_dispatch_pkey")) {
            transaction.executeWithoutResult(status -> {
                jdbc.execute(change);
                assertThrows(AgentLedgerException.class, () -> new AgentStorageVerifier(source).verifyLinked());
                status.setRollbackOnly();
            });
        }
    }

    @Test void a_limited_runtime_role_can_submit_and_close_only_with_required_fence_privileges() {
        String user = System.getProperty("tianji.linked.postgres.test.runtime-user", "");
        if (!user.matches("tianji_it_linked_[0-9a-f]{12}")) throw new IllegalArgumentException("temporary linked runtime role required");
        jdbc.execute("GRANT USAGE ON SCHEMA tianji_agent TO \"" + user + "\"");
        jdbc.execute("GRANT SELECT ON ALL TABLES IN SCHEMA tianji_agent TO \"" + user + "\"");
        jdbc.execute("GRANT INSERT,UPDATE ON tianji_agent.agent_process_dispatch, tianji_agent.agent_run, "
                + "tianji_agent.agent_run_command_receipt TO \"" + user + "\"");
        jdbc.execute("GRANT INSERT ON tianji_agent.agent_event TO \"" + user + "\"");
        jdbc.execute("GRANT UPDATE ON tianji_agent.agent_budget_reservation TO \"" + user + "\"");
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_schema_privilege(?, 'tianji_agent', 'CREATE')", Boolean.class, user));
        var limited = new DriverManagerDataSource(source.getUrl()); limited.setUsername(user);
        var verifier = new AgentStorageVerifier(limited); verifier.verifyLinked();
        jdbc.execute("REVOKE UPDATE ON tianji_agent.agent_process_dispatch FROM \"" + user + "\"");
        assertThrows(AgentLedgerException.class, verifier::verifyLinked);
        jdbc.execute("GRANT UPDATE ON tianji_agent.agent_process_dispatch TO \"" + user + "\"");
        var limitedLedger = new AgentLedgerService(limited);
        var limitedSubmissions = new AgentLinkedSubmissionService(limited, limitedLedger);
        var command = command("limited-role", "occurrence-limited-role", now().plusSeconds(30), "input");
        var run = limitedSubmissions.submit(service, command);
        assertEquals(AgentRunStatusEnum.CANCELLED, limitedSubmissions.close(service, lookup(command)).run().status());
        assertEquals(run.runId(), limitedSubmissions.resolve(service, lookup(command)).orElseThrow().run().runId());
    }

    private static LinkedAgentStartCommand command(String key, String occurrence, Instant notAfter, String input) {
        return command(key, occurrence, notAfter, input, definitionHash);
    }
    private static LinkedAgentStartCommand command(String key, String occurrence, Instant notAfter, String input, String hash) {
        return new LinkedAgentStartCommand(start(key, "trace-" + key, input), link(occurrence, notAfter, hash));
    }
    private static StartAgentRunCommand start(String key, String trace, String input) {
        return new StartAgentRunCommand("review", 1, null, key, "business", trace, new AgentInputDTO(input, List.of(), Map.of()), null);
    }
    private static AgentProcessLink link(String occurrence, Instant notAfter, String hash) {
        return new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", occurrence, hash, notAfter.truncatedTo(ChronoUnit.MICROS));
    }
    private static LinkedAgentLookupQuery lookup(LinkedAgentStartCommand command) {
        return new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
    }
    private static Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant(); }
    private static int runCount(TrustedAgentActor actor, String key) {
        return jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND idempotency_key=?",
                Integer.class, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), key);
    }
    private static Map<String, Integer> checksums() {
        return jdbc.query("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IN ('1','2','3','4','5','6')",
                rs -> { Map<String, Integer> values = new java.util.HashMap<>(); while (rs.next()) values.put(rs.getString(1), rs.getInt(2)); return values; });
    }
    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }
    private static void assertCode(AgentErrorCodeEnum expected, Runnable work) {
        assertEquals(expected, assertThrows(AgentLedgerException.class, work::run).code());
    }

    private static final class CommitBarrierDataSource extends AbstractDataSource {
        private final DataSource delegate;
        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private CommitBarrierDataSource(DataSource delegate) { this.delegate = delegate; }
        @Override public Connection getConnection() throws SQLException { return wrap(delegate.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException { return wrap(delegate.getConnection(user, password)); }
        private Connection wrap(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("commit") && Thread.currentThread().getName().equals("linked-submit-commit") && armed.compareAndSet(true, false)) {
                    entered.countDown();
                    if (!release.await(10, TimeUnit.SECONDS)) throw new SQLException("test commit barrier timed out");
                }
                try { return method.invoke(connection, args); }
                catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }
    }
}
