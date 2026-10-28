package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only the explicitly supplied disposable loopback database. */
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentExecutionGrantStorePostgresTest {
    private static final URI RESOURCE = URI.create("https://api.example/tianji");
    private static final String ID = "9" + "1".repeat(62);
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;
    private static AgentExecutionGrantStore store;

    @BeforeAll static void setup() {
        dataSource = PostgresTestDatabase.dataSource(); jdbc = new JdbcTemplate(dataSource);
        ledger = new AgentLedgerService(dataSource); store = new AgentExecutionGrantStore(dataSource, ledger);
    }

    @Test void newer_attempt_fences_delayed_responses_and_preserves_exact_owner() {
        var actor = actor("alice"); var run = start(actor);
        var first = store.reserve(actor, run.runId(), RESOURCE).orElseThrow();
        var second = store.reserve(actor, run.runId(), RESOURCE).orElseThrow();
        assertEquals(first.epoch() + 1, second.epoch()); assertEquals(first.bindingSha256(), second.bindingSha256());
        Instant expiry = ledger.databaseNow().plusSeconds(30).plusNanos(123_456_789);
        var older = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:older", expiry);
        var latest = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:latest", expiry);
        assertFalse(store.complete(first, older)); assertTrue(store.complete(second, latest));
        var authorization = ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId())).authorization();
        var stored = store.current(authorization, RESOURCE).orElseThrow();
        assertEquals(latest.ciphertext(), stored.ciphertext()); assertEquals(expiry.truncatedTo(ChronoUnit.MICROS), stored.expiresAt());
        assertFalse(stored.toString().contains("latest"));
        var otherIssuer = TrustedAgentActor.verified(actor.tenantId(), TrustedAgentActor.SubjectType.USER, "alice", "https://other.example/tenant");
        var otherSubject = TrustedAgentActor.verified(actor.tenantId(), TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/tenants/21");
        assertTrue(store.reserve(otherIssuer, run.runId(), RESOURCE).isEmpty());
        assertTrue(store.reserve(otherSubject, run.runId(), RESOURCE).isEmpty());
        assertTrue(store.current(authorization, URI.create("https://api.example/other")).isEmpty());
        assertThrows(IllegalStateException.class, () -> store.reserve(actor, run.runId(), URI.create("https://api.example/other")));
        var replacement = store.reserve(actor, run.runId(), RESOURCE).orElseThrow();
        assertFalse(store.complete(replacement, new AgentExecutionGrant.Ticket("9" + "2".repeat(62), "ENC:v1:key:nonce:other", expiry)));
        assertEquals(latest.ciphertext(), store.current(authorization, RESOURCE).orElseThrow().ciphertext());
    }

    @Test void concurrent_attempts_have_unique_epochs_and_only_the_latest_can_publish() throws Exception {
        var actor = actor("alice"); var run = start(actor);
        var gate = new CountDownLatch(1); List<Future<AgentExecutionGrant.Reservation>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(12)) {
            for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> { gate.await(); return store.reserve(actor, run.runId(), RESOURCE).orElseThrow(); }));
            gate.countDown(); List<AgentExecutionGrant.Reservation> values = new ArrayList<>();
            for (var future : futures) values.add(future.get(20, TimeUnit.SECONDS));
            Set<Long> epochs = new HashSet<>(); values.forEach(value -> epochs.add(value.epoch()));
            assertEquals(12, epochs.size()); assertEquals(12L, epochs.stream().mapToLong(Long::longValue).max().orElseThrow());
            var latest = values.stream().max(java.util.Comparator.comparingLong(AgentExecutionGrant.Reservation::epoch)).orElseThrow();
            var ticket = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:winning", ledger.databaseNow().plusSeconds(30));
            assertTrue(store.complete(latest, ticket));
            for (var stale : values) if (stale != latest) assertFalse(store.complete(stale, ticket));
            assertEquals(ticket.ciphertext(), store.current(latest.authorization(), RESOURCE).orElseThrow().ciphertext());
        }
    }

    @Test void cancellation_and_claim_prevent_late_publication_and_use() {
        var cancelledActor = actor("cancelled"); var cancelled = start(cancelledActor);
        var reservation = store.reserve(cancelledActor, cancelled.runId(), RESOURCE).orElseThrow();
        ledger.cancel(cancelledActor, new CancelAgentRunCommand(cancelled.runId(), "cancel", cancelled.revision(), "stop"));
        var ticket = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:sealed", ledger.databaseNow().plusSeconds(30));
        assertFalse(store.complete(reservation, ticket)); assertTrue(store.current(reservation.authorization(), RESOURCE).isEmpty());
        assertTrue(store.reserve(cancelledActor, cancelled.runId(), RESOURCE).isEmpty());
        var runningActor = actor("running"); var running = start(runningActor);
        var pending = store.reserve(runningActor, running.runId(), RESOURCE).orElseThrow(); assertTrue(store.complete(pending, ticket));
        var later = store.reserve(runningActor, running.runId(), RESOURCE).orElseThrow();
        var claimed = ledger.claim(runningActor, new GetAgentRunQuery(running.runId()), running.revision(), "worker", Duration.ofSeconds(30));
        assertEquals(AgentRunStatusEnum.RUNNING, claimed.run().status());
        assertFalse(store.complete(later, ticket)); assertTrue(store.current(pending.authorization(), RESOURCE).isPresent());
        ledger.cancel(runningActor, new CancelAgentRunCommand(running.runId(), "cancel-running", claimed.run().revision(), "stop"));
        assertTrue(store.current(pending.authorization(), RESOURCE).isEmpty());
    }

    @Test void cancellation_races_never_leave_a_usable_grant() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (int iteration = 0; iteration < 8; iteration++) {
                var actor = actor("race"); var run = start(actor); var reservation = store.reserve(actor, run.runId(), RESOURCE).orElseThrow();
                var ticket = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:race", ledger.databaseNow().plusSeconds(30));
                var gate = new CountDownLatch(1);
                var complete = executor.submit(() -> { gate.await(); return store.complete(reservation, ticket); });
                var cancel = executor.submit(() -> { gate.await(); return ledger.cancel(actor,
                        new CancelAgentRunCommand(run.runId(), "cancel-race", run.revision(), "stop")); });
                gate.countDown(); complete.get(10, TimeUnit.SECONDS); cancel.get(10, TimeUnit.SECONDS);
                assertEquals(AgentRunStatusEnum.CANCELLED, ledger.get(actor, new GetAgentRunQuery(run.runId())).status());
                assertTrue(store.current(reservation.authorization(), RESOURCE).isEmpty());
            }
        }
    }

    @Test void expiry_and_database_constraints_fail_closed() {
        var actor = actor("expiry"); var run = start(actor); var reservation = store.reserve(actor, run.runId(), RESOURCE).orElseThrow();
        var expired = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:expired", ledger.databaseNow().minusSeconds(1));
        assertFalse(store.complete(reservation, expired));
        var tooLong = new AgentExecutionGrant.Ticket(ID, "ENC:v1:key:nonce:toolong", reservation.authorization().deadlineAt().plusNanos(1));
        assertThrows(IllegalArgumentException.class, () -> store.complete(reservation, tooLong));
        assertTrue(store.current(reservation.authorization(), RESOURCE).isEmpty());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE tianji_agent.agent_run_execution_grant SET grant_id = ?, ciphertext = NULL, expires_at = NULL
                WHERE tenant_id = ? AND run_id = ?
                """, ID, actor.tenantId(), run.runId()));
    }

    @Test void store_requires_the_ledgers_exact_transaction_bound_data_source() {
        assertDoesNotThrow(store::requireSchema);
        var other = new DriverManagerDataSource(dataSource.getUrl());
        assertThrows(IllegalArgumentException.class, () -> new AgentExecutionGrantStore(other, ledger));
    }

    private static TrustedAgentActor actor(String subject) {
        long tenant = PostgresTestDatabase.nextTenant();
        return TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER, subject, "https://idp.example/tenants/" + tenant);
    }

    private static top.egon.cola.platform.tianji.contract.agent.AgentRunDTO start(TrustedAgentActor actor) {
        ledger.publish(actor, AgentJsonCodecTest.spec(1, Map.of("topic", "grant"), Set.of()));
        return ledger.start(actor, new StartAgentRunCommand("review", 1, null, "request", "business", "correlation",
                new AgentInputDTO("input", List.of(), Map.of()), null));
    }
}
