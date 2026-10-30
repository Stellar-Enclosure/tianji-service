package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Requires an explicitly supplied empty loopback database and preinstalled vector extension; it starts no process. */
@EnabledIfSystemProperty(named = "tianji.memory.postgres.test.url", matches = ".+")
@Timeout(120)
class MemoryPostgresTest {
    private static final TrustedAgentActor ALICE = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemorySourceDTO PROVENANCE = new MemorySourceDTO("explicit-user-entry", "a".repeat(64), null, null);
    private static final MemoryRetentionPolicy POLICY = new MemoryRetentionPolicy("personal", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);

    @Test
    void v10_fact_receipt_index_visibility_forget_concurrency_grants_and_catalog_fences() throws Exception {
        String url = System.getProperty("tianji.memory.postgres.test.url");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) throw new IllegalArgumentException("Memory tests require an empty loopback tianji_it_ database");
        String version = System.getProperty("tianji.memory.postgres.vector.version");
        if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("Memory tests require the explicitly pinned preinstalled vector patch");
        var source = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(source);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_agent'", Integer.class));
        assertEquals(version, jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='vector'", String.class));
        var old = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").target("9").cleanDisabled(true).load();
        assertEquals(9, old.migrate().migrationsExecuted); old.validate();
        var before = jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var migration = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent").locations("classpath:db/tianji-agent")
                .target("10").placeholders(Map.of("tianjiMemoryDimensions", "2")).cleanDisabled(true).load();
        assertEquals(1, migration.migrate().migrationsExecuted); migration.validate();
        assertEquals(before, jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7','8','9') ORDER BY installed_rank"));
        jdbc.execute("CREATE TABLE tianji_agent.memory_test_outbox (event_id varchar(128) PRIMARY KEY,fingerprint char(64) NOT NULL)");
        var ledger = new AgentLedgerService(source); var events = new ConcurrentHashMap<String, MemoryIndexPublication.Event>(); var failPublication = new AtomicBoolean();
        var delegate = mock(EmbeddingModel.class);
        when(delegate.call(any(EmbeddingRequest.class))).thenAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return response(); });
        var model = new VersionedMemoryEmbeddingModel(new VersionedMemoryEmbeddingModel.Profile("personal", 1, "embeddingBean", "text-embedding-3-small", 2, 8191, 64, 16), delegate,
                () -> EmbeddingOptions.builder().model("text-embedding-3-small").dimensions(2).build());
        var binding = new MemoryIndexPublication.Binding(model.descriptor().modelRef(), 2, "agent_memory_vector_v1");
        MemoryIndexPublication publication = new MemoryIndexPublication() {
            @Override public boolean usesDataSource(javax.sql.DataSource selected) { return selected == source; }
            @Override public Binding binding() { return binding; }
            @Override public void publish(Event event) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); assertTrue(TransactionSynchronizationManager.hasResource(source));
                jdbc.update("INSERT INTO tianji_agent.memory_test_outbox VALUES (?,?)", event.eventId(), event.fingerprint());
                if (failPublication.get()) throw new IllegalStateException("controlled publication failure");
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { events.put(event.eventId(), event); } });
            }
        };
        MemoryAuthorization authority = request -> request.owner().type() == MemoryOwnerTypeEnum.TEAM ? MemoryAuthorization.Decision.DENY : MemoryAuthorization.Decision.ALLOW;
        try (var index = new PgVectorMemoryIndex(source, binding, model, 2, Clock.systemUTC())) {
            var memory = new PostgresMemoryService(ledger, source, authority, List.of(POLICY), publication, Clock.systemUTC(), index);
            memory.verifyStorage(2, version);
            assertThrows(AgentLedgerException.class, () -> new MemoryStorageVerifier(source, 3, version, binding).verify());
            var work = new MemoryIndexWork(source, memory, index, event -> { if (event.tenantId() != 20) throw new IllegalArgumentException("untrusted test origin"); }, Clock.systemUTC());
            lifecycle(jdbc, memory, work, events);
            contextSelection(memory, work, events);
            rollback(jdbc, memory, failPublication);
            concurrency(memory);
            lateEmbedding(jdbc, memory, work, events, delegate);
            retention(source, jdbc, memory, work, publication, events, failPublication);
            grants(ledger, source);
            serviceGrantCorrection(ledger, source, jdbc); memory.verifyStorage(2, version);
            jdbc.execute("ALTER TABLE tianji_agent.agent_memory_entry DROP CONSTRAINT memory_entry_confirmation");
            assertThrows(AgentLedgerException.class, () -> memory.verifyStorage(2, version));
            assertThrows(AgentLedgerException.class, () -> memory.get(context(), new GetMemoryQuery(OWNER, "research", "main")));
        }
    }

    private static void lifecycle(JdbcTemplate jdbc, PostgresMemoryService memory, MemoryIndexWork work, Map<String, MemoryIndexPublication.Event> events) {
        var created = memory.upsert(context(), upsert("create-main", "main", "first preference")); assertEquals(MemoryMutationOutcomeEnum.CREATED, created.outcome());
        assertEquals(created, memory.upsert(context(), upsert("create-main", "main", "first preference")));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> memory.retrieve(context(), search())).code());
        work.apply(build(events, "main", 1), Instant.now().plusSeconds(10));
        assertEquals("first preference", memory.retrieve(context(), search()).items().getFirst().entry().content().text());
        memory.correct(context(), new CorrectMemoryCommand("correct-main", OWNER, "research", "main", 1, new MemoryContentDTO("corrected preference", Map.of()), PROVENANCE, BigDecimal.ONE, "USER_CORRECTION"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1_active", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1", Integer.class));
        work.apply(build(events, "main", 2), Instant.now().plusSeconds(10));
        assertEquals("corrected preference", memory.retrieve(context(), search()).items().getFirst().entry().content().text());
        memory.forget(context(), new ForgetMemoryCommand("forget-main", OWNER, "research", "main", 2, "USER_FORGET"));
        assertEquals(MemoryStateEnum.FORGOTTEN, memory.get(context(), new GetMemoryQuery(OWNER, "research", "main")).state());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE memory_id='main' AND (content_json IS NOT NULL OR source_json IS NOT NULL)", Integer.class));
        assertTrue(memory.retrieve(context(), search()).items().isEmpty()); assertEquals(created, memory.upsert(context(), upsert("create-main", "main", "first preference")));
        var deletion = events.values().stream().filter(event -> event.action() == MemoryIndexPublication.Action.DELETE_MEMORY && event.memoryId().equals("main")).findFirst().orElseThrow();
        work.apply(deletion, Instant.now().plusSeconds(10)); assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1", Integer.class));
        var other = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/21");
        assertThrows(AgentLedgerException.class, () -> memory.get(new MemoryOperationContext(other, Instant.now().plusSeconds(10), null), new GetMemoryQuery(OWNER, "research", "main")));
        assertThrows(AgentLedgerException.class, () -> memory.get(context(), new GetMemoryQuery(new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "unconfirmed-team"), "research", "main")));
    }

    private static void rollback(JdbcTemplate jdbc, PostgresMemoryService memory, AtomicBoolean failure) {
        memory.upsert(context(), upsert("create-rollback", "rollback", "rollback original"));
        int before = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.memory_test_outbox", Integer.class);
        failure.set(true);
        try { assertThrows(IllegalStateException.class, () -> memory.correct(context(), new CorrectMemoryCommand("failed-correction", OWNER, "research", "rollback", 1,
                new MemoryContentDTO("never committed", Map.of()), PROVENANCE, BigDecimal.ONE, "USER_CORRECTION"))); }
        finally { failure.set(false); }
        var read = memory.get(context(), new GetMemoryQuery(OWNER, "research", "rollback")); assertEquals(1, read.revision()); assertEquals("rollback original", read.content().text());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE memory_id='rollback'", Integer.class));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.memory_test_outbox", Integer.class));
    }

    private static void concurrency(PostgresMemoryService memory) throws Exception {
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 8; i++) { int number = i; results.add(threads.submit(() -> memory.upsert(context(), upsert("parallel-" + number, "parallel-" + number, "one deduplicated preference")).memoryId())); }
            var ids = new java.util.HashSet<String>(); for (var result : results) ids.add(result.get(10, TimeUnit.SECONDS)); assertEquals(1, ids.size());
            String id = ids.iterator().next(); var updates = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 8; i++) { int number = i; updates.add(threads.submit(() -> {
                try { memory.correct(context(), new CorrectMemoryCommand("parallel-correct-" + number, OWNER, "research", id, 1, new MemoryContentDTO("winner " + number, Map.of()), PROVENANCE, BigDecimal.ONE, "USER_CORRECTION")); return true; }
                catch (AgentLedgerException conflict) { assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, conflict.code()); return false; }
            })); }
            int winners = 0; for (var update : updates) if (update.get(10, TimeUnit.SECONDS)) winners++; assertEquals(1, winners);
        }
    }

    private static void lateEmbedding(JdbcTemplate jdbc, PostgresMemoryService memory, MemoryIndexWork work, Map<String, MemoryIndexPublication.Event> events, EmbeddingModel model) throws Exception {
        memory.upsert(context(), upsert("late-create", "late", "late embedding preference"));
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(model.call(any(EmbeddingRequest.class))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); started.countDown();
            while (release.getCount() > 0) { try { release.await(20, TimeUnit.MILLISECONDS); } catch (InterruptedException ignored) { } }
            return response();
        });
        try (var caller = Executors.newVirtualThreadPerTaskExecutor()) {
            var delivery = caller.submit(() -> assertThrows(AgentLedgerException.class, () -> work.apply(build(events, "late", 1), Instant.now().plusMillis(150))));
            assertTrue(started.await(2, TimeUnit.SECONDS)); delivery.get(3, TimeUnit.SECONDS);
            memory.forget(context(), new ForgetMemoryCommand("late-forget", OWNER, "research", "late", 1, "USER_FORGET"));
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_index_manifest WHERE memory_id='late' AND index_state<>'DELETED'", Integer.class) != 0) Thread.sleep(20);
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_index_manifest WHERE memory_id='late' AND index_state<>'DELETED'", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE metadata->>'memoryId'='late'", Integer.class));
        } finally { release.countDown(); }
    }

    private static void grants(AgentLedgerService ledger, DriverManagerDataSource source) {
        var policy = new ExecutionPolicyDTO(Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofSeconds(5), 4, 0, 1000, 1000, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        ledger.publish(ALICE, new AgentDefinitionSpecDTO("memory-grant-test", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of()));
        var started = ledger.start(ALICE, new StartAgentRunCommand("memory-grant-test", 1, null, "grant-run", null, null, new AgentInputDTO("input", null, null), null));
        var request = ledger.executionMaterial(ALICE, new GetAgentRunQuery(started.runId())).authorization();
        var store = new MemoryReadGrantStore(source, ledger); var scope = new MemoryReadGrant.Scope(OWNER, "research", "e".repeat(64), null); var resource = URI.create("https://agent.example");
        var first = store.reserve(request, resource, scope).orElseThrow(); var second = store.reserve(request, resource, scope).orElseThrow();
        var ticket = new AgentExecutionGrant.Ticket("9" + "1".repeat(62), "ENC:v1:test:A:A", Instant.now().plusSeconds(30));
        assertFalse(store.complete(first, ticket)); assertTrue(store.complete(second, ticket)); assertEquals(ticket.grantId(), store.current(request, resource, scope).orElseThrow().grantId());
        assertTrue(store.current(request, resource, new MemoryReadGrant.Scope(OWNER, "other", "f".repeat(64), null)).isEmpty());
        ledger.cancel(ALICE, new CancelAgentRunCommand(started.runId(), "cancel-grant", started.revision(), "controlled cancellation"));
        assertTrue(store.current(request, resource, scope).isEmpty()); assertFalse(store.complete(second, ticket));
    }

    private static void retention(DriverManagerDataSource source, JdbcTemplate jdbc, PostgresMemoryService memory, MemoryIndexWork indexWork, MemoryIndexPublication publication,
                                  Map<String, MemoryIndexPublication.Event> events, AtomicBoolean failPublication) throws Exception {
        var expires = Instant.now().plusSeconds(2);
        memory.upsert(context(), new UpsertMemoryCommand("retention-create", OWNER, "retention", "retained-private", MemoryKindEnum.FACT, 0, new MemoryContentDTO("expired private fact", Map.of()), PROVENANCE,
                BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), Instant.now().minusSeconds(1), expires));
        indexWork.apply(build(events, "retained-private", 1), Instant.now().plusSeconds(10));
        while (Instant.now().isBefore(expires)) Thread.sleep(10);
        assertNull(memory.get(context(), new GetMemoryQuery(OWNER, "retention", "retained-private")).content());
        var origin = new MemoryRetentionWork.Origin(ALICE.tenantId(), ALICE.issuerFingerprint());
        var retention = new MemoryRetentionWork(source, memory, publication, List.of(origin), Set.of(POLICY.reference()), Clock.systemUTC());
        failPublication.set(true);
        try { assertThrows(IllegalStateException.class, () -> retention.apply(origin, 10, Instant.now().plusSeconds(5))); }
        finally { failPublication.set(false); }
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT state FROM tianji_agent.agent_memory_entry WHERE memory_id='retained-private'", String.class));
        assertEquals(1, retention.apply(origin, 10, Instant.now().plusSeconds(5)).expired());
        var expired = memory.get(context(), new GetMemoryQuery(OWNER, "retention", "retained-private")); assertEquals(MemoryStateEnum.EXPIRED, expired.state()); assertEquals(2, expired.revision());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE memory_id='retained-private' AND (content_json IS NOT NULL OR source_json IS NOT NULL)", Integer.class));
        var deletion = events.values().stream().filter(event -> event.action() == MemoryIndexPublication.Action.DELETE_MEMORY && event.memoryId().equals("retained-private")).findFirst().orElseThrow();
        indexWork.apply(deletion, Instant.now().plusSeconds(10));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE metadata->>'memoryId'='retained-private'", Integer.class));
        assertEquals(0, retention.apply(origin, 10, Instant.now().plusSeconds(5)).selected());
    }

    private static void contextSelection(PostgresMemoryService memory, MemoryIndexWork work, Map<String, MemoryIndexPublication.Event> events) {
        for (var sensitivity : List.of(MemorySensitivityEnum.INTERNAL, MemorySensitivityEnum.CONFIDENTIAL)) {
            String id = sensitivity == MemorySensitivityEnum.INTERNAL ? "a-context-excluded" : "b-context-approved";
            memory.upsert(context(), new UpsertMemoryCommand("create-" + id, OWNER, "context-policy", id, MemoryKindEnum.FACT, 0, new MemoryContentDTO(id, Map.of()), PROVENANCE,
                    BigDecimal.ONE, true, sensitivity, POLICY.reference(), null, null));
            if (sensitivity == MemorySensitivityEnum.CONFIDENTIAL) work.apply(build(events, id, 1), Instant.now().plusSeconds(10));
        }
        var query = new MemoryRetrievalQuery(OWNER, "context-policy", null, null, null, 1);
        assertEquals("a-context-excluded", memory.retrieve(context(), query).items().getFirst().entry().memoryId());
        assertEquals("b-context-approved", memory.retrieveContext(context(), query, Set.of(POLICY.reference())).items().getFirst().entry().memoryId());
        var semantic = new MemoryRetrievalQuery(OWNER, "context-policy", null, "query", null, 1);
        assertEquals("b-context-approved", memory.retrieveContext(context(), semantic, Set.of(POLICY.reference())).items().getFirst().entry().memoryId());
    }

    private static void serviceGrantCorrection(AgentLedgerService ledger, DriverManagerDataSource source, JdbcTemplate jdbc) {
        var store = new MemoryReadGrantStore(source, ledger); assertThrows(AgentLedgerException.class, store::requireServiceStorage);
        var before = jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var correction = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent").locations("classpath:db/tianji-agent")
                .target("11").placeholders(Map.of("tianjiMemoryDimensions", "2")).cleanDisabled(true).load();
        assertEquals(1, correction.migrate().migrationsExecuted); correction.validate();
        assertEquals(before, jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version<>'11' AND version IS NOT NULL ORDER BY installed_rank"));
        store.requireServiceStorage();
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "memory-client", "https://idp.example/20");
        var run = ledger.start(actor, new StartAgentRunCommand("memory-grant-test", 1, null, "service-memory-grant", null, null, new AgentInputDTO("input", null, null), null));
        var request = ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId())).authorization(); var resource = URI.create("https://agent.example");
        var scope = new MemoryReadGrant.Scope(new MemoryOwnerDTO(MemoryOwnerTypeEnum.APPLICATION, "memory-client"), "research", "f".repeat(64),
                new AgentServiceExecutionGrant.Subject("memory-client", "application", "business", "source", "test", resource));
        var reserved = store.reserve(request, resource, scope).orElseThrow(); var ticket = new MemoryReadGrant.Ticket("8" + "1".repeat(62), "ENC:v1:test:A:A", Instant.now().plusSeconds(30));
        assertTrue(store.completeEvidence(reserved, ticket)); assertEquals(ticket.grantId(), store.currentEvidence(request, resource, scope).orElseThrow().grantId());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_agent.agent_memory_run_grant SET grant_id=? WHERE tenant_id=20 AND run_id=?", "9" + "1".repeat(62), run.runId()));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_agent.agent_memory_run_grant SET grant_id=? WHERE actor_type='USER'", "8" + "1".repeat(62)));
        assertEquals(ticket.grantId(), store.currentEvidence(request, resource, scope).orElseThrow().grantId());
    }

    private static UpsertMemoryCommand upsert(String command, String id, String text) { return new UpsertMemoryCommand(command, OWNER, "research", id, MemoryKindEnum.PREFERENCE, 0,
            new MemoryContentDTO(text, Map.of()), PROVENANCE, BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null); }
    private static MemoryOperationContext context() { return new MemoryOperationContext(ALICE, Instant.now().plusSeconds(30), null); }
    private static MemoryRetrievalQuery search() { return new MemoryRetrievalQuery(OWNER, "research", MemoryKindEnum.PREFERENCE, "preference", null, 5); }
    private static MemoryIndexPublication.Event build(Map<String, MemoryIndexPublication.Event> events, String id, long revision) { return events.values().stream().filter(event -> event.action() == MemoryIndexPublication.Action.BUILD && event.memoryId().equals(id) && event.revision() == revision).findFirst().orElseThrow(); }
    private static EmbeddingResponse response() { var metadata = new EmbeddingResponseMetadata(); metadata.setModel("text-embedding-3-small"); return new EmbeddingResponse(List.of(new Embedding(new float[]{0.25f, 0.75f}, 0)), metadata); }
}
