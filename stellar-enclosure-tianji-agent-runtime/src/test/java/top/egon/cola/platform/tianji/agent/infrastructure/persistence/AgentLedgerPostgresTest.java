package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.*;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

/** Runs only against an explicitly supplied loopback test database with the required name prefix. */
@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentLedgerPostgresTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
    private static final AtomicLong TENANT = new AtomicLong(10_000);
    private static DriverManagerDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AgentLedgerService ledger;

    @BeforeAll
    static void migrateIsolatedDatabase() {
        String url = System.getProperty("tianji.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("integration tests require an explicit loopback tianji_it_ database");
        }
        dataSource = new DriverManagerDataSource();
        dataSource.setUrl(url);
        jdbc = new JdbcTemplate(dataSource);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname = 'tianji_agent'", Integer.class) != 0) {
            throw new IllegalStateException("integration tests require a fresh database without a Tianji schema");
        }
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("tianji_agent")
                .defaultSchema("tianji_agent").locations("classpath:db/tianji-agent")
                .cleanDisabled(true).load();
        flyway.migrate();
        flyway.validate();
        ledger = new AgentLedgerService(dataSource, CLOCK);
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
    void cas_winner_emits_one_event_and_terminal_result_cannot_be_rewritten() throws Exception {
        TrustedAgentActor owner = actor(USER, "alice");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO queued = ledger.start(owner, command("key", "question", "trace"));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        CountDownLatch gate = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> attempts = List.of(
                    pool.submit(() -> transitionOrError(gate, owner, query, RUNNING)),
                    pool.submit(() -> transitionOrError(gate, owner, query, CANCELLED)));
            gate.countDown();
            List<Object> outcomes = List.of(attempts.get(0).get(20, TimeUnit.SECONDS), attempts.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(1, outcomes.stream().filter(AgentRunDTO.class::isInstance).count());
            assertEquals(1, outcomes.stream().filter(AgentLedgerException.class::isInstance).count());
        }
        AgentRunDTO winner = ledger.get(owner, query);
        assertEquals(1, winner.revision());
        assertEquals(2, winner.lastEventSequence());
        assertEquals(2, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 10)).size());
        if (winner.status() == RUNNING) {
            AgentExecutionResult result = result(query.runId(), COMPLETED);
            AgentRunDTO completed = ledger.transition(owner, query, 1, RUNNING, COMPLETED, null, result);
            assertEquals(2, completed.revision());
            assertEquals(result, ledger.getResult(owner, query).orElseThrow());
            assertEquals(3, ledger.events(owner, new AgentEventsQuery(query.runId(), 0, 10)).size());
            assertEquals(1, ledger.events(owner, new AgentEventsQuery(query.runId(), 2, 10)).size());
            assertThrows(AgentLedgerException.class,
                    () -> ledger.transition(owner, query, 2, COMPLETED, RUNNING, null, null));
        } else {
            assertEquals(CANCELLED, winner.status());
            assertEquals(CANCELLED, ledger.getResult(owner, query).orElseThrow().status());
        }
    }

    @Test
    void completion_stores_result_and_pages_events_in_order() {
        TrustedAgentActor owner = actor(USER, "alice");
        ledger.publish(owner, spec(Map.of()));
        AgentRunDTO queued = ledger.start(owner, command("key", "question", "trace"));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        AgentRunDTO running = ledger.transition(owner, query, 0, QUEUED, RUNNING, null, null);
        assertEquals(1, running.attempt());
        AgentExecutionResult result = result(query.runId(), COMPLETED);
        AgentRunDTO completed = ledger.transition(owner, query, 1, RUNNING, COMPLETED, null, result);
        assertEquals(2, completed.revision());
        assertEquals(3, completed.lastEventSequence());
        assertEquals(result, ledger.getResult(owner, query).orElseThrow());
        assertEquals(List.of(2L, 3L), ledger.events(owner, new AgentEventsQuery(query.runId(), 1, 10))
                .stream().map(event -> event.sequence()).toList());
        assertEquals("trace", ledger.events(owner, new AgentEventsQuery(query.runId(), 2, 10)).getFirst().correlationId());
        assertThrows(AgentLedgerException.class,
                () -> ledger.transition(owner, query, 2, COMPLETED, RUNNING, null, null));
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
                    () -> ledger.transition(owner, new GetAgentRunQuery(queued.runId()), 0, QUEUED, RUNNING, null, null));
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

    private static Object transitionOrError(CountDownLatch gate, TrustedAgentActor owner,
                                            GetAgentRunQuery query, AgentRunStatusEnum target) throws InterruptedException {
        gate.await();
        try {
            AgentExecutionResult result = target == CANCELLED ? result(query.runId(), CANCELLED) : null;
            return ledger.transition(owner, query, 0, QUEUED, target, null, result);
        } catch (AgentLedgerException failure) {
            return failure;
        }
    }

    private static AgentExecutionResult result(String runId, AgentRunStatusEnum status) {
        return new AgentExecutionResult(runId, status, Map.of("answer", "done"), null, "1", null, null, "NONE");
    }

    private static TrustedAgentActor actor(TrustedAgentActor.SubjectType type, String subject) {
        return new TrustedAgentActor(TENANT.incrementAndGet(), type, subject);
    }

    private static AgentDefinitionSpecDTO spec(Map<String, Object> configuration) {
        return AgentJsonCodecTest.spec(1, configuration, Set.of("read@1", "search@1"));
    }

    private static StartAgentRunCommand command(String key, String text, String correlation) {
        return new StartAgentRunCommand("review", 1, null, key, "business", correlation,
                new AgentInputDTO(text, List.of(), Map.of()), null);
    }
}
