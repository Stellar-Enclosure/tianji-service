package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
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

/** Requires an explicitly supplied fresh loopback database; no application server is started. */
@EnabledIfSystemProperty(named = "tianji.service.grant.postgres.test.url", matches = ".+")
@Timeout(120)
class AgentServiceExecutionGrantStorePostgresTest {
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final String ID = "8" + "1".repeat(62);
    private static final AtomicLong TENANTS = new AtomicLong(50_000);
    private static final AgentServiceExecutionGrant.Subject SUBJECT = new AgentServiceExecutionGrant.Subject(
            "process-worker", "process", "platform", "process", "test", URI.create("https://api.example/process"));
    private static DriverManagerDataSource source;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;
    private static AgentLinkedSubmissionService submissions;
    private static AgentServiceExecutionGrantStore store;

    @BeforeAll static void setup() {
        String url = System.getProperty("tianji.service.grant.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("service grant tests require a fresh loopback database");
        }
        source = new DriverManagerDataSource(url); jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("service grant test schema already exists");
        }
        var old = flyway("7"); assertEquals(7, old.migrate().migrationsExecuted); old.validate();
        Map<String, Integer> checksums = checksums();
        ledger = new AgentLedgerService(source); submissions = new AgentLinkedSubmissionService(source, ledger);
        var previous = start();
        var authorization = ledger.executionMaterial(previous.actor(), new GetAgentRunQuery(previous.run().runId())).authorization();
        assertThrows(AgentLedgerException.class, () -> new AgentStorageVerifier(source).verifyServiceGrants());
        var current = flyway(null); assertEquals(1, current.migrate().migrationsExecuted); current.validate();
        assertEquals(checksums, checksums());
        assertEquals(authorization, ledger.executionMaterial(previous.actor(), new GetAgentRunQuery(previous.run().runId())).authorization());
        store = new AgentServiceExecutionGrantStore(source, ledger); store.requireSchema();
    }

    @Test void reservations_fence_delayed_responses_and_bind_the_exact_service_tuple() {
        var value = start();
        var first = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
        var second = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
        assertEquals(first.epoch() + 1, second.epoch());
        Instant expiry = now().plusSeconds(30).plusNanos(123_456_789);
        var older = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:older", expiry);
        var newer = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:newer", expiry);
        assertFalse(store.complete(first, older)); assertTrue(store.complete(second, newer));
        assertFalse(store.complete(second, older));
        var stored = store.current(second.authorization(), RESOURCE, SUBJECT).orElseThrow();
        assertEquals(newer.ciphertext(), stored.ciphertext());
        assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), stored.expiresAt());
        var changed = new AgentServiceExecutionGrant.Subject("process-worker", "other-app", "platform", "process", "test",
                URI.create("https://api.example/process"));
        assertTrue(store.current(second.authorization(), RESOURCE, changed).isEmpty());
        assertThrows(IllegalStateException.class, () -> store.reserve(value.actor(), value.run().runId(), RESOURCE, changed));
        var otherIssuer = TrustedAgentActor.verified(value.actor().tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                "process-worker", "https://other.example/tenant");
        assertTrue(store.reserve(otherIssuer, value.run().runId(), RESOURCE, SUBJECT).isEmpty());
        assertTrue(store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).isEmpty());
        jdbc.update("UPDATE tianji_agent.agent_run_service_execution_grant SET expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                value.actor().tenantId(), value.run().runId());
        var replacement = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
        assertFalse(store.complete(replacement, new AgentServiceExecutionGrant.Ticket("8" + "2".repeat(62), "ENC:v1:key:nonce:other", expiry)));
        assertTrue(store.complete(replacement, newer));
    }

    @Test void concurrent_reservations_publish_only_the_latest_epoch() throws Exception {
        var value = start(); var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<AgentServiceExecutionGrant.Reservation>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> {
                gate.await(); return store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
            }));
            gate.countDown(); var reservations = new ArrayList<AgentServiceExecutionGrant.Reservation>();
            for (var future : futures) reservations.add(future.get(20, TimeUnit.SECONDS));
            var epochs = new HashSet<Long>(); reservations.forEach(item -> epochs.add(item.epoch()));
            assertEquals(8, epochs.size());
            var latest = reservations.stream().max(java.util.Comparator.comparingLong(AgentServiceExecutionGrant.Reservation::epoch)).orElseThrow();
            var ticket = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:winning", now().plusSeconds(30));
            assertTrue(store.complete(latest, ticket));
            for (var reservation : reservations) if (reservation != latest) assertFalse(store.complete(reservation, ticket));
            assertEquals(ticket.ciphertext(), store.current(latest.authorization(), RESOURCE, SUBJECT).orElseThrow().ciphertext());
        }
    }

    @Test void closing_the_process_fence_races_safely_with_ticket_publication() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 8; i++) {
                var value = start(); var reservation = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
                var ticket = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:race", now().plusSeconds(30));
                var gate = new CountDownLatch(1);
                var complete = executor.submit(() -> { gate.await(); return store.complete(reservation, ticket); });
                var close = executor.submit(() -> { gate.await(); return submissions.close(value.actor(), lookup(value.command())); });
                gate.countDown(); complete.get(10, TimeUnit.SECONDS); var closed = close.get(10, TimeUnit.SECONDS);
                assertTrue(closed.closed()); assertEquals(AgentRunStatusEnum.CANCELLED, closed.run().status());
                assertTrue(store.current(reservation.authorization(), RESOURCE, SUBJECT).isEmpty());
                assertFalse(store.complete(reservation, ticket));
                assertTrue(store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).isEmpty());
            }
        }
    }

    @Test void unlinked_cancelled_wrong_type_and_invalid_evidence_fail_closed() {
        var value = start(); var attempt = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
        var expired = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:expired", now().minusSeconds(1));
        assertFalse(store.complete(attempt, expired));
        var tooLong = new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:too-long", attempt.authorization().deadlineAt().plusNanos(1));
        assertThrows(IllegalArgumentException.class, () -> store.complete(attempt, tooLong));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE tianji_agent.agent_run_service_execution_grant SET grant_id=?,ciphertext=?,expires_at=clock_timestamp()+interval '30 seconds'
                WHERE tenant_id=? AND run_id=?
                """, "9" + "1".repeat(62), "ENC:v1:key:nonce:wrong", value.actor().tenantId(), value.run().runId()));
        var user = TrustedAgentActor.verified(value.actor().tenantId(), TrustedAgentActor.SubjectType.USER, "process-worker",
                "https://idp.example/tenants/" + value.actor().tenantId());
        assertThrows(IllegalArgumentException.class, () -> store.reserve(user, value.run().runId(), RESOURCE, SUBJECT));
        var unlinked = ledger.start(value.actor(), new StartAgentRunCommand("review", 1, null, "unlinked", "business", "trace",
                new AgentInputDTO("input", List.of(), Map.of()), null));
        assertTrue(store.reserve(value.actor(), unlinked.runId(), RESOURCE, SUBJECT).isEmpty());
        var other = new DriverManagerDataSource(source.getUrl());
        assertThrows(IllegalArgumentException.class, () -> new AgentServiceExecutionGrantStore(other, ledger));
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        assertThrows(IllegalStateException.class, () -> transaction.execute(status -> store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT)));
    }

    @Test void a_changed_process_fence_never_attaches_a_ticket_to_the_run() {
        var value = start();
        jdbc.update("UPDATE tianji_agent.agent_process_dispatch SET link_sha256=? WHERE tenant_id=? AND run_id=?",
                "f".repeat(64), value.actor().tenantId(), value.run().runId());
        assertThrows(AgentLedgerException.class, () -> store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run_service_execution_grant WHERE tenant_id=? AND run_id=?",
                Integer.class, value.actor().tenantId(), value.run().runId()));
    }

    @Test void service_queue_discovery_is_opt_in_and_skips_closed_or_expired_fences() {
        var first = start(); var second = start(); var unready = start();
        for (var value : List.of(first, second)) {
            var reservation = store.reserve(value.actor(), value.run().runId(), RESOURCE, SUBJECT).orElseThrow();
            assertTrue(store.complete(reservation, new AgentServiceExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:ready", now().plusSeconds(30))));
        }
        for (var value : List.of(first, second, unready)) {
            jdbc.update("UPDATE tianji_agent.agent_run SET created_at=? WHERE tenant_id=? AND run_id=?",
                    Timestamp.from(Instant.parse("2099-01-01T00:00:00Z")), value.actor().tenantId(), value.run().runId());
        }
        var cursor = new top.egon.cola.platform.tianji.agent.domain.GrantReadyAgentRun.Cursor(
                Instant.parse("2098-01-01T00:00:00Z"), 1, "0");
        assertTrue(ledger.grantReadyRuns(cursor, 2).isEmpty());
        var page = ledger.grantReadyRuns(cursor, 2, true);
        assertEquals(List.of(first.run().runId(), second.run().runId()), page.stream().map(item -> item.query().runId()).toList());
        assertTrue(ledger.grantReadyRuns(page.getLast().cursor(), 2, true).isEmpty());
        submissions.close(first.actor(), lookup(first.command()));
        assertEquals(List.of(second.run().runId()), ledger.grantReadyRuns(cursor, 2, true).stream().map(item -> item.query().runId()).toList());
        jdbc.update("UPDATE tianji_agent.agent_run_service_execution_grant SET expires_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                second.actor().tenantId(), second.run().runId());
        assertTrue(ledger.grantReadyRuns(cursor, 2, true).isEmpty());
    }

    @Test void schema_verifier_rejects_missing_or_weakened_evidence_constraints() {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        transaction.executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE tianji_agent.agent_run_service_execution_grant DROP CONSTRAINT agent_service_grant_id_format");
            assertThrows(AgentLedgerException.class, store::requireSchema);
            status.setRollbackOnly();
        });
        assertDoesNotThrow(store::requireSchema);
        transaction.executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE tianji_agent.agent_run_service_execution_grant DROP CONSTRAINT agent_service_grant_id_format");
            jdbc.execute("ALTER TABLE tianji_agent.agent_run_service_execution_grant ADD CONSTRAINT agent_service_grant_id_format "
                    + "CHECK (grant_id IS NULL OR grant_id ~ '^[89][0-9]{62}$')");
            assertThrows(AgentLedgerException.class, store::requireSchema);
            status.setRollbackOnly();
        });
        assertDoesNotThrow(store::requireSchema);
    }

    private static Fixture start() {
        long tenant = TENANTS.incrementAndGet();
        var actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.SERVICE, "process-worker",
                "https://idp.example/tenants/" + tenant);
        var definition = ledger.publish(actor, AgentJsonCodecTest.spec(1, Map.of("mode", "safe"), Set.of()));
        var input = new StartAgentRunCommand("review", 1, null, "request", "business", "trace",
                new AgentInputDTO("input", List.of(), Map.of()), null);
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-" + tenant, "activity-" + tenant,
                definition.contentSha256(), now().plusSeconds(60).truncatedTo(ChronoUnit.MICROS));
        var command = new LinkedAgentStartCommand(input, link);
        return new Fixture(actor, command, submissions.submit(actor, command));
    }

    private static LinkedAgentLookupQuery lookup(LinkedAgentStartCommand command) {
        return new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
    }
    private static Instant now() { return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant(); }
    private static Map<String, Integer> checksums() {
        return jdbc.query("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7')",
                rs -> { var result = new HashMap<String, Integer>(); while (rs.next()) result.put(rs.getString(1), rs.getInt(2)); return result; });
    }
    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent")
                .locations("classpath:db/tianji-agent").target("9").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }
    private record Fixture(TrustedAgentActor actor, LinkedAgentStartCommand command, AgentRunDTO run) { }
}
