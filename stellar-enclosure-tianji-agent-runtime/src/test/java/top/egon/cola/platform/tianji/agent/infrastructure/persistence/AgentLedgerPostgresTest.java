package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.*;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

/** Runs only against an explicitly supplied loopback test database with the required name prefix. */
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentLedgerPostgresTest {
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;

    @BeforeAll
    static void migrateIsolatedDatabase() {
        dataSource = PostgresTestDatabase.dataSource();
        jdbc = new JdbcTemplate(dataSource);
        ledger = new AgentLedgerService(dataSource);
    }

    @Test
    void definition_versions_are_immutable_and_scoped_by_tenant() {
        TrustedAgentActor owner = actor(USER, "alice");
        AgentDefinitionSpecDTO spec = spec(Map.of("first", 1, "second", 2));
        AgentDefinitionDTO created = ledger.publish(owner, spec);
        AgentDefinitionDTO repeated = ledger.publish(owner, spec(Map.of("second", 2, "first", 1)));
        assertEquals(created.contentSha256(), repeated.contentSha256());
        AgentLedgerException conflict = assertThrows(AgentLedgerException.class,
                () -> ledger.publish(owner, spec(Map.of("first", 3))));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT, conflict.code());
        TrustedAgentActor otherTenant = new TrustedAgentActor(owner.tenantId() + 1_000_000, USER, "alice");
        AgentLedgerException missing = assertThrows(AgentLedgerException.class,
                () -> ledger.start(otherTenant, command("other", "question", "trace")));
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, missing.code());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_definition_version WHERE tenant_id = ?",
                Integer.class, owner.tenantId()));
    }

    @Test
    void hundred_concurrent_starts_produce_one_run_and_one_first_event() throws Exception {
        TrustedAgentActor owner = actor(USER, "alice");
        AgentDefinitionDTO definition = ledger.publish(owner, spec(Map.of("topic", "review")));
        StartAgentRunCommand command = command("request-1", "question", "trace-a");
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(24)) {
            for (int i = 0; i < 100; i++) {
                results.add(pool.submit(() -> {
                    gate.await();
                    return ledger.start(owner, command).runId();
                }));
            }
            gate.countDown();
            Set<String> ids = new HashSet<>();
            for (Future<String> future : results) {
                ids.add(future.get(40, TimeUnit.SECONDS));
            }
            assertEquals(1, ids.size());
            String runId = ids.iterator().next();
            assertEquals(runId, ledger.start(owner, command("request-1", "question", "trace-b")).runId());
            AgentLedgerException conflict = assertThrows(AgentLedgerException.class,
                    () -> ledger.start(owner, command("request-1", "different", "trace-a")));
            assertEquals(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, conflict.code());
            AgentRunDTO run = ledger.get(owner, new GetAgentRunQuery(runId));
            assertEquals(QUEUED, run.status());
            assertEquals(0, run.revision());
            assertEquals(1, run.lastEventSequence());
            assertEquals(List.of("RUN_QUEUED"), ledger.events(owner, new AgentEventsQuery(runId, 0, 10))
                    .stream().map(event -> event.type()).toList());
            assertEquals("trace-a", ledger.events(owner, new AgentEventsQuery(runId, 0, 10)).getFirst().correlationId());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE tenant_id = ?",
                    Integer.class, owner.tenantId()));
            String snapshot = jdbc.queryForObject("SELECT execution_snapshot::text FROM tianji_agent.agent_run WHERE tenant_id = ?",
                    String.class, owner.tenantId());
            assertNotNull(snapshot);
            assertTrue(snapshot.contains(definition.contentSha256()));
        }
    }

    @Test
    void tenant_and_subject_scopes_prevent_cross_owner_reads() {
        TrustedAgentActor owner = actor(USER, "same-id");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO run = ledger.start(owner, command("key", "question", "trace"));
        TrustedAgentActor otherSubject = new TrustedAgentActor(owner.tenantId(), USER, "other");
        TrustedAgentActor service = new TrustedAgentActor(owner.tenantId(), SERVICE, "same-id");
        TrustedAgentActor otherTenant = new TrustedAgentActor(owner.tenantId() + 1_000_000, USER, "same-id");
        for (TrustedAgentActor stranger : List.of(otherSubject, service, otherTenant)) {
            AgentLedgerException missing = assertThrows(AgentLedgerException.class,
                    () -> ledger.get(stranger, new GetAgentRunQuery(run.runId())));
            assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, missing.code());
            assertThrows(AgentLedgerException.class,
                    () -> ledger.events(stranger, new AgentEventsQuery(run.runId(), 0, 10)));
            assertThrows(AgentLedgerException.class,
                    () -> ledger.getResult(stranger, new GetAgentRunQuery(run.runId())));
        }
        AgentRunDTO sameKeyOtherSubject = ledger.start(service, command("key", "question", "trace"));
        assertNotEquals(run.runId(), sameKeyOtherSubject.runId());
    }

    @Test
    void same_subject_from_distinct_issuers_has_distinct_runs_receipts_and_leases() {
        long tenant = PostgresTestDatabase.nextTenant();
        var first = TrustedAgentActor.verified(tenant, USER, "alice", "https://idp-a.example/tenant");
        var second = TrustedAgentActor.verified(tenant, USER, "alice", "https://idp-b.example/tenant");
        var legacy = new TrustedAgentActor(tenant, USER, "alice");
        ledger.publish(first, spec(Map.of()));
        var command = command("same-key", "question", "trace");
        var firstRun = ledger.start(first, command);
        var secondRun = ledger.start(second, command);
        var legacyRun = ledger.start(legacy, command);
        assertNotEquals(firstRun.runId(), secondRun.runId());
        assertNotEquals(firstRun.runId(), legacyRun.runId());
        assertEquals(firstRun.runId(), ledger.start(first, command).runId());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> ledger.get(second, new GetAgentRunQuery(firstRun.runId()))).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, assertThrows(AgentLedgerException.class,
                () -> ledger.cancel(second, new CancelAgentRunCommand(firstRun.runId(), "foreign", 0, "test"))).code());
        assertEquals(List.of(firstRun.runId()), ledger.list(first, new ListAgentRunsQuery(null, 10)).items().stream().map(AgentRunDTO::runId).toList());
        assertEquals(List.of(secondRun.runId()), ledger.list(second, new ListAgentRunsQuery(null, 10)).items().stream().map(AgentRunDTO::runId).toList());
        var pending = ledger.pendingRuns(1000).stream().filter(item -> item.query().runId().equals(firstRun.runId())).findFirst().orElseThrow();
        assertEquals(first, pending.actor());
        var claimed = ledger.claim(first, new GetAgentRunQuery(firstRun.runId()), 0, "issuer-worker", Duration.ofSeconds(30));
        assertThrows(AgentLedgerException.class, () -> ledger.heartbeat(second, claimed.lease(), Duration.ofSeconds(30)));
        ledger.cancel(second, new CancelAgentRunCommand(secondRun.runId(), "same-cancel", 0, "test"));
        ledger.cancel(legacy, new CancelAgentRunCommand(legacyRun.runId(), "same-cancel", 0, "test"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run_command_receipt "
                + "WHERE tenant_id=? AND command_id='same-cancel'", Integer.class, tenant));
        var overdue = ledger.start(first, command("overdue", "question", "trace"));
        jdbc.update("UPDATE tianji_agent.agent_run SET deadline_at=clock_timestamp()-interval '1 second' WHERE tenant_id=? AND run_id=?",
                tenant, overdue.runId());
        assertTrue(ledger.reconcileDue(1000) > 0);
        assertEquals(TIMED_OUT, ledger.get(first, new GetAgentRunQuery(overdue.runId())).status());
    }

    @Test
    void owner_scoped_keyset_pages_are_stable_when_timestamps_tie() {
        TrustedAgentActor owner = actor(USER, "list-owner");
        TrustedAgentActor otherSubject = new TrustedAgentActor(owner.tenantId(), USER, "other-owner");
        TrustedAgentActor otherType = new TrustedAgentActor(owner.tenantId(), SERVICE, "list-owner");
        TrustedAgentActor otherTenant = new TrustedAgentActor(owner.tenantId() + 1_000_000, USER, "list-owner");
        ledger.publish(owner, spec(Map.of()));
        ledger.publish(otherTenant, spec(Map.of()));
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 7; i++) expected.add(ledger.start(owner, command("page-" + i, "question", null)).runId());
        String otherRun = ledger.start(otherSubject, command("page-other", "question", null)).runId();
        String serviceRun = ledger.start(otherType, command("page-service", "question", null)).runId();
        String tenantRun = ledger.start(otherTenant, command("page-tenant", "question", null)).runId();
        jdbc.update("UPDATE tianji_agent.agent_run SET created_at=? WHERE tenant_id=? AND subject_type=? AND subject_id=?",
                java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), owner.tenantId(), "USER", "list-owner");

        List<String> actual = new ArrayList<>();
        String cursor = null;
        do {
            var page = ledger.list(owner, new ListAgentRunsQuery(cursor, 3));
            assertTrue(page.items().size() <= 3);
            actual.addAll(page.items().stream().map(AgentRunDTO::runId).toList());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertEquals(expected.size(), actual.size());
        assertEquals(expected, new HashSet<>(actual));
        assertEquals(actual.stream().sorted(java.util.Comparator.reverseOrder()).toList(), actual);
        assertFalse(actual.contains(otherRun));
        assertFalse(actual.contains(serviceRun));
        assertFalse(actual.contains(tenantRun));
        assertEquals(List.of(otherRun), ledger.list(otherSubject, new ListAgentRunsQuery(null, 10))
                .items().stream().map(AgentRunDTO::runId).toList());
        assertThrows(IllegalArgumentException.class, () -> ledger.list(owner, new ListAgentRunsQuery("invalid!", 3)));
        String extreme = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("1|+1000000000-12-31T23:59:59.999999999Z|" + actual.getFirst())
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ledger.list(owner, new ListAgentRunsQuery(extreme, 3)));
    }

    @Test
    void one_claim_wins_the_cas_and_terminal_result_cannot_be_rewritten() throws Exception {
        TrustedAgentActor owner = actor(USER, "alice");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO queued = ledger.start(owner, command("key", "question", "trace"));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> attempts = List.of(
                    pool.submit(() -> claimOrError(gate, owner, query, "worker-a")),
                    pool.submit(() -> claimOrError(gate, owner, query, "worker-b")));
            gate.countDown();
            List<Object> outcomes = List.of(attempts.get(0).get(20, TimeUnit.SECONDS), attempts.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(ClaimedAgentRun.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(AgentLedgerException.class::isInstance).count());
            ClaimedAgentRun claimed = (ClaimedAgentRun) outcomes.stream()
                    .filter(ClaimedAgentRun.class::isInstance).findFirst().orElseThrow();
            AgentExecutionResult result = result(query.runId(), COMPLETED);
            assertEquals(COMPLETED, ledger.finish(owner, claimed.lease(), 1, result).status());
        }
        AgentRunDTO winner = ledger.get(owner, query);
        assertEquals(2, winner.revision());
        assertEquals(3, winner.lastEventSequence());
        assertEquals(result(query.runId(), COMPLETED), ledger.getResult(owner, query).orElseThrow());
        assertEquals(3, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 10)).size());
    }

    @Test
    void completion_stores_result_and_pages_events_in_order() {
        TrustedAgentActor owner = actor(USER, "alice");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO queued = ledger.start(owner, command("key", "question", "trace"));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        ClaimedAgentRun claimed = ledger.claim(owner, query, 0, "worker", Duration.ofSeconds(10));
        AgentRunDTO running = claimed.run();
        assertEquals(1, running.attempt());
        AgentExecutionResult result = result(query.runId(), COMPLETED);
        AgentRunDTO completed = ledger.finish(owner, claimed.lease(), 1, result);
        assertEquals(2, completed.revision());
        assertEquals(3, completed.lastEventSequence());
        assertEquals(result, ledger.getResult(owner, query).orElseThrow());
        assertEquals(List.of(2L, 3L), ledger.events(owner, new AgentEventsQuery(query.runId(), 1, 10))
                .stream().map(event -> event.sequence()).toList());
        assertEquals("trace", ledger.events(owner, new AgentEventsQuery(query.runId(), 2, 10)).getFirst().correlationId());
        assertThrows(AgentLedgerException.class, () -> ledger.finish(owner, claimed.lease(), 2, result));
    }

    @Test
    void snapshot_is_bound_to_definition_and_event_append_uses_revision_cas() {
        TrustedAgentActor owner = actor(USER, "alice");
        AgentDefinitionDTO definition = ledger.publish(owner, spec(Map.of("topic", "original")));
        AgentRunDTO queued = ledger.start(owner, command("key", "question", "trace"));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        assertEquals(definition.contentSha256(), ledger.snapshot(owner, query).definitionContentSha256());
        assertEquals("question", ledger.snapshot(owner, query).input().text());
        AgentRunLease fake = new AgentRunLease(query.runId(), "fake", 1, Instant.now().plusSeconds(10));
        assertThrows(AgentLedgerException.class,
                () -> ledger.appendEvent(owner, fake, 0, "TEXT_DELTA", "planner", Map.of("text", "a")));
        ClaimedAgentRun claimed = ledger.claim(owner, query, 0, "worker", Duration.ofSeconds(10));
        AgentRunDTO appended = ledger.appendEvent(owner, claimed.lease(), 1, "TEXT_DELTA", "planner", Map.of("text", "a"));
        assertEquals(2, appended.revision());
        assertEquals(3, appended.lastEventSequence());
        assertThrows(AgentLedgerException.class,
                () -> ledger.appendEvent(owner, claimed.lease(), 1, "TEXT_DELTA", "planner", Map.of("text", "duplicate")));
        assertEquals(3, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 10)).size());

        jdbc.update("""
                UPDATE tianji_agent.agent_run
                SET execution_snapshot = jsonb_set(execution_snapshot, '{definition,configuration,topic}', '"changed"'::jsonb)
                WHERE tenant_id = ? AND run_id = ?
                """, owner.tenantId(), query.runId());
        AgentLedgerException invalid = assertThrows(AgentLedgerException.class, () -> ledger.snapshot(owner, query));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, invalid.code());
    }

    @Test
    void event_failure_rolls_back_both_start_and_status_update() {
        TrustedAgentActor owner = actor(USER, "alice");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO queued = ledger.start(owner, command("existing", "question", "trace"));
        jdbc.execute("ALTER TABLE tianji_agent.agent_event ADD CONSTRAINT it_forced_event_failure CHECK (tenant_id <> "
                + owner.tenantId() + ") NOT VALID");
        try {
            assertThrows(DataIntegrityViolationException.class,
                    () -> ledger.start(owner, command("failed-start", "question", "trace")));
            assertThrows(DataIntegrityViolationException.class,
                    () -> ledger.claim(owner, new GetAgentRunQuery(queued.runId()), 0, "worker", Duration.ofSeconds(10)));
        } finally {
            jdbc.execute("ALTER TABLE tianji_agent.agent_event DROP CONSTRAINT it_forced_event_failure");
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_run WHERE tenant_id = ?",
                Integer.class, owner.tenantId()));
        AgentRunDTO unchanged = ledger.get(owner, new GetAgentRunQuery(queued.runId()));
        assertEquals(QUEUED, unchanged.status());
        assertEquals(0, unchanged.revision());
        assertEquals(1, unchanged.lastEventSequence());
        assertEquals(1, ledger.events(owner, new AgentEventsQuery(queued.runId(), 0, 10)).size());
    }

    private static Object claimOrError(CountDownLatch gate, TrustedAgentActor owner,
                                       GetAgentRunQuery query, String worker) throws InterruptedException {
        gate.await();
        try {
            return ledger.claim(owner, query, 0, worker, Duration.ofSeconds(10));
        } catch (AgentLedgerException failure) {
            return failure;
        }
    }

    private static AgentExecutionResult result(String runId, AgentRunStatusEnum status) {
        return new AgentExecutionResult(runId, status, Map.of("answer", "done"), null, "1", null, null, "NONE");
    }

    private static TrustedAgentActor actor(TrustedAgentActor.SubjectType type, String subject) {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), type, subject);
    }

    private static AgentDefinitionSpecDTO spec(Map<String, Object> configuration) {
        return AgentJsonCodecTest.spec(1, configuration, Set.of("read@1", "search@1"));
    }

    private static StartAgentRunCommand command(String key, String text, String correlation) {
        return new StartAgentRunCommand("review", 1, null, key, "business", correlation,
                new AgentInputDTO(text, List.of(), Map.of()), null);
    }
}
