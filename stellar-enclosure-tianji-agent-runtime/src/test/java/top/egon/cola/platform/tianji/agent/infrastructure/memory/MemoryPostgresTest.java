package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import jakarta.validation.Validation;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
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
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceCommands;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryContextBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidateQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryMergeRelationEnum;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

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

    private static final String S04_DEFINITION = "s04-session-memory";
    private static final String S04_SESSION = "s04-shared-session";
    private static final String S04_SCOPE = "s04-reviewed-scope";
    private static final String S04_SIBLING_SCOPE = "s04-other-scope";
    private static final String S04_MEMORY = "s04-same-memory-id";

    @Test
    void v10_fact_receipt_index_visibility_forget_concurrency_grants_and_catalog_fences() throws Exception {
        MemoryAuthorization authority = request -> request.owner().type() == MemoryOwnerTypeEnum.TEAM ? MemoryAuthorization.Decision.DENY : MemoryAuthorization.Decision.ALLOW;
        try (var fixture = openV10Fixture(ignored -> authority,
                event -> { if (event.tenantId() != 20) throw new IllegalArgumentException("untrusted test origin"); })) {
            var jdbc = fixture.jdbc();
            var memory = fixture.memory();
            var work = fixture.work();
            var events = fixture.events();
            var ledger = fixture.ledger();
            var source = fixture.source();
            var version = fixture.vectorVersion();
            assertThrows(AgentLedgerException.class, () -> new MemoryStorageVerifier(source, 3, version, fixture.publication().binding()).verify());
            lifecycle(jdbc, memory, work, events);
            contextSelection(memory, work, events);
            candidates(memory);
            mergeSuggestions(jdbc, memory, work, events, fixture.delegate());
            preferenceAdmission(ledger, memory, authority, jdbc);
            rollback(jdbc, memory, fixture.failPublication());
            concurrency(memory);
            lateEmbedding(jdbc, memory, work, events, fixture.delegate());
            retention(source, jdbc, memory, work, fixture.publication(), events, fixture.failPublication());
            grants(ledger, source);
            serviceGrantCorrection(ledger, source, jdbc); memory.verifyStorage(2, version);
            preferenceIntents(ledger, source, memory, jdbc); memory.verifyStorage(2, version);
            jdbc.execute("ALTER TABLE tianji_agent.agent_memory_entry DROP CONSTRAINT memory_entry_confirmation");
            assertThrows(AgentLedgerException.class, () -> memory.verifyStorage(2, version));
            assertThrows(AgentLedgerException.class, () -> memory.get(context(), new GetMemoryQuery(OWNER, "research", "main")));
        }
    }

    private static MemoryFixture openV10Fixture(Function<AgentLedgerService, MemoryAuthorization> authority,
                                                 Consumer<MemoryIndexPublication.Event> buildAuthority) {
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
        var index = new PgVectorMemoryIndex(source, binding, model, 2, Clock.systemUTC());
        try {
            var memory = new PostgresMemoryService(ledger, source, authority.apply(ledger), List.of(POLICY), publication, Clock.systemUTC(), index);
            memory.verifyStorage(2, version);
            var work = new MemoryIndexWork(source, memory, index, buildAuthority, Clock.systemUTC());
            return new MemoryFixture(source, jdbc, ledger, memory, index, work, publication, events, failPublication, delegate, version);
        } catch (RuntimeException | Error failure) {
            index.close();
            throw failure;
        }
    }

    private record MemoryFixture(DriverManagerDataSource source, JdbcTemplate jdbc, AgentLedgerService ledger,
                                 PostgresMemoryService memory, PgVectorMemoryIndex index, MemoryIndexWork work,
                                 MemoryIndexPublication publication, Map<String, MemoryIndexPublication.Event> events,
                                 AtomicBoolean failPublication, EmbeddingModel delegate, String vectorVersion) implements AutoCloseable {
        @Override public void close() { index.close(); }
    }

    @Test
    @EnabledIfSystemProperty(named = "tianji.memory.postgres.session-context.enabled", matches = "true")
    void replacement_executor_revalidates_owned_durable_memory_history_and_blocks_forgotten_sources() {
        var secondTenant = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/21");
        var owners = Set.of(ALICE, secondTenant);
        var memoryReads = new ConcurrentHashMap<String, List<MemoryAuthorization.Operation>>();
        try (var fixture = openV10Fixture(ledger -> s04Authority(ledger, owners, memoryReads), event -> {
            if (!OWNER.equals(event.owner()) || !Set.of(S04_SCOPE, S04_SIBLING_SCOPE).contains(event.scopeId())
                    || owners.stream().noneMatch(actor -> actor.tenantId() == event.tenantId()
                    && actor.issuerFingerprint().equals(event.ownerIssuer()))) throw new IllegalArgumentException("untrusted S04 fixture origin");
        });
             var validators = Validation.buildDefaultValidatorFactory();
             var control = Executors.newScheduledThreadPool(2)) {
            var ledger = fixture.ledger();
            var factory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of()));
            var validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
            var runPolicy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(8), Duration.ofSeconds(5),
                    2, 0, 2000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
            var definition = new AgentDefinitionSpecDTO(S04_DEFINITION, 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "s04-fixture-route", "s04-fixture-policy"),
                    runPolicy, Map.of("chatModelBeanName", "s04Model", "modelName", "s04-fixture-model",
                    "rootAgentName", "assistant", "agents", List.of(Map.of("name", "assistant", "instruction", "Summarize fixture evidence."))));
            var bindings = owners.stream().map(actor -> {
                var published = ledger.publish(actor, definition);
                return new MemoryContextBinding("s04-binding-" + actor.tenantId(), actor.tenantId(), actor.issuerFingerprint(),
                        actor.subjectType(), S04_DEFINITION, 1, published.contentSha256(), MemoryOwnerTypeEnum.USER,
                        null, S04_SCOPE, MemoryKindEnum.PREFERENCE, true, 3, Set.of(POLICY.reference()));
            }).toList();

            var primaryCommand = s04Fact(S04_SCOPE, "s04-owner-memory-marker");
            var original = fixture.memory().upsert(s04Context(ALICE), primaryCommand);
            var originalBuild = buildScoped(fixture.events(), ALICE, S04_SCOPE);
            fixture.work().apply(originalBuild, Instant.now().plusSeconds(10));
            fixture.memory().upsert(s04Context(secondTenant), s04Fact(S04_SCOPE, "s04-foreign-tenant-marker"));
            fixture.work().apply(buildScoped(fixture.events(), secondTenant, S04_SCOPE), Instant.now().plusSeconds(10));
            fixture.memory().upsert(s04Context(ALICE), s04Fact(S04_SIBLING_SCOPE, "s04-sibling-scope-marker"));
            fixture.work().apply(buildScoped(fixture.events(), ALICE, S04_SIBLING_SCOPE), Instant.now().plusSeconds(10));

            for (var actor : owners) {
                var sessions = new PostgresAdkSessionService(fixture.source(), actor, "tianji");
                sessions.createSession("tianji", sessions.adkUserId(), Map.of(), S04_SESSION).blockingGet();
            }
            AgentRunAuthorization runAuthority = (request, boundary) -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                if (!owners.contains(request.actor()) || !request.sessionRequired()
                        || !S04_DEFINITION.equals(request.definitionKey())) return AgentRunAuthorization.Decision.DENY;
                var current = ledger.executionMaterial(request.actor(), new GetAgentRunQuery(request.runId()));
                var expected = boundary == AgentRunAuthorization.Boundary.CLAIM ? AgentRunStatusEnum.QUEUED : AgentRunStatusEnum.RUNNING;
                return current.authorization().equals(request) && current.run().status() == expected
                        ? AgentRunAuthorization.Decision.ALLOW : AgentRunAuthorization.Decision.DENY;
            };

            var originalModel = new S04Model("s04-first-owned-answer");
            AgentContextSourceDTO originalSource;
            try (var provider = new MemoryContextProvider(fixture.memory(), bindings, List.of(POLICY), 1, Clock.systemUTC())) {
                var firstExecutor = s04Executor(fixture, factory, validator, control, provider, runAuthority, originalModel);
                var first = s04Run(ledger, firstExecutor, ALICE, S04_SESSION, "s04-first", "s04-first-owned-question", AgentRunStatusEnum.COMPLETED);
                originalSource = s04MemorySnapshot(ledger, ALICE, first);
                assertPrompt(originalModel, List.of("s04-first-owned-question", "s04-owner-memory-marker"),
                        List.of("s04-foreign-tenant-marker", "s04-sibling-scope-marker"));
            }

            // Replace the executor and its Memory provider, leaving the durable Session and V9 metadata intact.
            var replacementModel = new S04Model("s04-second-owned-answer");
            try (var provider = new MemoryContextProvider(fixture.memory(), bindings, List.of(POLICY), 1, Clock.systemUTC())) {
                var replacement = s04Executor(fixture, factory, validator, control, provider, runAuthority, replacementModel);
                var second = s04Run(ledger, replacement, ALICE, S04_SESSION, "s04-second", "s04-second-owned-question", AgentRunStatusEnum.COMPLETED);
                var replacementSource = s04MemorySnapshot(ledger, ALICE, second);
                assertEquals(originalSource.provenanceId(), replacementSource.provenanceId());
                assertEquals(originalSource.sourceSha256(), replacementSource.sourceSha256());
                assertPrompt(replacementModel, List.of("s04-first-owned-question", "s04-first-owned-answer",
                                "s04-second-owned-question", "s04-owner-memory-marker"),
                        List.of("s04-foreign-tenant-marker", "s04-sibling-scope-marker"));
            }

            var foreignModel = new S04Model("s04-foreign-answer");
            try (var provider = new MemoryContextProvider(fixture.memory(), bindings, List.of(POLICY), 1, Clock.systemUTC())) {
                var foreign = s04Executor(fixture, factory, validator, control, provider, runAuthority, foreignModel);
                s04Run(ledger, foreign, secondTenant, S04_SESSION, "s04-first", "s04-foreign-question", AgentRunStatusEnum.COMPLETED);
                assertPrompt(foreignModel, List.of("s04-foreign-tenant-marker", "s04-foreign-question"),
                        List.of("s04-first-owned-question", "s04-first-owned-answer", "s04-owner-memory-marker", "s04-sibling-scope-marker"));
            }

            fixture.memory().forget(s04Context(ALICE), new ForgetMemoryCommand(
                    "s04-forget", OWNER, S04_SCOPE, S04_MEMORY, original.revision(), "USER_FORGET"));
            int eventsAfterForget = fixture.events().size();
            // Replay both the original command and its already delivered BUILD event after the tombstone.
            assertEquals(original, fixture.memory().upsert(s04Context(ALICE), primaryCommand));
            assertEquals(eventsAfterForget, fixture.events().size());
            fixture.work().apply(originalBuild, Instant.now().plusSeconds(10));
            assertEquals(MemoryStateEnum.FORGOTTEN,
                    fixture.memory().get(s04Context(ALICE), new GetMemoryQuery(OWNER, S04_SCOPE, S04_MEMORY)).state());
            assertTrue(fixture.memory().retrieve(s04Context(ALICE), s04Search(S04_SCOPE)).items().isEmpty());
            assertEquals(0, fixture.jdbc().queryForObject(
                    "SELECT count(*) FROM tianji_agent.agent_memory_vector_v1 WHERE id=?", Integer.class, originalBuild.documentId()));

            var blockedModel = new S04Model("s04-must-not-be-produced");
            try (var provider = new MemoryContextProvider(fixture.memory(), bindings, List.of(POLICY), 1, Clock.systemUTC())) {
                var blocked = s04Executor(fixture, factory, validator, control, provider, runAuthority, blockedModel);
                var failed = s04Run(ledger, blocked, ALICE, S04_SESSION, "s04-third", "s04-third-owned-question", AgentRunStatusEnum.FAILED);
                var result = ledger.getResult(ALICE, new GetAgentRunQuery(failed.runId())).orElseThrow();
                assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(), result.errorCode());
                assertTrue(result.output().isEmpty());
                assertNull(blockedModel.prompt.get());
                assertEquals(List.of(MemoryAuthorization.Operation.RETRIEVE), memoryReads.get(failed.runId()));
                assertEquals(0, fixture.jdbc().queryForObject("""
                        SELECT count(*) FROM tianji_agent.agent_budget_reservation
                        WHERE tenant_id=? AND run_id=? AND kind='MODEL'
                        """, Integer.class, ALICE.tenantId(), failed.runId()));
            }
            assertEquals("s04-foreign-tenant-marker",
                    fixture.memory().retrieve(s04Context(secondTenant), s04Search(S04_SCOPE)).items().getFirst().entry().content().text());
            assertEquals("s04-sibling-scope-marker",
                    fixture.memory().retrieve(s04Context(ALICE), s04Search(S04_SIBLING_SCOPE)).items().getFirst().entry().content().text());

            // A clean Session remains runnable by the same owner; only stale history was denied.
            var sessions = new PostgresAdkSessionService(fixture.source(), ALICE, "tianji");
            sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "s04-fresh-session").blockingGet();
            var cleanModel = new S04Model("s04-clean-answer");
            try (var provider = new MemoryContextProvider(fixture.memory(), bindings, List.of(POLICY), 1, Clock.systemUTC())) {
                var clean = s04Executor(fixture, factory, validator, control, provider, runAuthority, cleanModel);
                s04Run(ledger, clean, ALICE, "s04-fresh-session", "s04-fresh", "s04-clean-question", AgentRunStatusEnum.COMPLETED);
                assertPrompt(cleanModel, List.of("s04-clean-question"),
                        List.of("s04-owner-memory-marker", "s04-first-owned-answer", "s04-foreign-tenant-marker", "s04-sibling-scope-marker"));
            }
        }
    }

    /** Local, exact test authority. Production IAM and grant issuance remain separate gates. */
    private static MemoryAuthorization s04Authority(AgentLedgerService ledger, Set<TrustedAgentActor> owners,
                                                     Map<String, List<MemoryAuthorization.Operation>> reads) {
        return request -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var actor = request.context().actor();
            if (!owners.contains(actor) || !OWNER.equals(request.owner()) || !Set.of(S04_SCOPE, S04_SIBLING_SCOPE).contains(request.scopeId())) {
                return MemoryAuthorization.Decision.DENY;
            }
            var run = request.context().runAuthorization();
            if (run == null) return MemoryAuthorization.Decision.ALLOW;
            if (!actor.equals(run.actor()) || !S04_SCOPE.equals(request.scopeId()) || !S04_DEFINITION.equals(run.definitionKey())
                    || !run.sessionRequired() || !Set.of(MemoryAuthorization.Operation.RETRIEVE, MemoryAuthorization.Operation.SEARCH).contains(request.operation())) {
                return MemoryAuthorization.Decision.DENY;
            }
            var current = ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId()));
            if (!run.equals(current.authorization()) || current.run().status() != AgentRunStatusEnum.RUNNING) {
                return MemoryAuthorization.Decision.DENY;
            }
            reads.computeIfAbsent(run.runId(), ignored -> new CopyOnWriteArrayList<>()).add(request.operation());
            return MemoryAuthorization.Decision.ALLOW;
        };
    }

    private static UpsertMemoryCommand s04Fact(String scope, String text) {
        return new UpsertMemoryCommand("s04-fact", OWNER, scope, S04_MEMORY, MemoryKindEnum.PREFERENCE, 0,
                new MemoryContentDTO(text, Map.of()), PROVENANCE, BigDecimal.ONE, true,
                MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null);
    }

    private static MemoryRetrievalQuery s04Search(String scope) {
        return new MemoryRetrievalQuery(OWNER, scope, MemoryKindEnum.PREFERENCE, "fixture", null, 3);
    }

    private static MemoryOperationContext s04Context(TrustedAgentActor actor) {
        return new MemoryOperationContext(actor, Instant.now().plusSeconds(15), null);
    }

    private static MemoryIndexPublication.Event buildScoped(Map<String, MemoryIndexPublication.Event> events,
                                                              TrustedAgentActor actor, String scope) {
        return events.values().stream().filter(event -> event.action() == MemoryIndexPublication.Action.BUILD
                && event.tenantId() == actor.tenantId() && actor.issuerFingerprint().equals(event.ownerIssuer())
                && OWNER.equals(event.owner()) && scope.equals(event.scopeId()) && S04_MEMORY.equals(event.memoryId())
                && event.revision() == 1).findFirst().orElseThrow();
    }

    private static AgentRunDTO s04Run(AgentLedgerService ledger, AdkAgentRunExecutor executor, TrustedAgentActor actor,
                                      String session, String command, String input, AgentRunStatusEnum expected) {
        var run = ledger.start(actor, new StartAgentRunCommand(S04_DEFINITION, 1, session, command, null, null,
                new AgentInputDTO(input, List.of(), Map.of()), null));
        var query = new GetAgentRunQuery(run.runId());
        assertEquals(expected, executor.executeQueued(actor, query).status());
        var persisted = ledger.get(actor, query);
        assertEquals(expected, persisted.status());
        return persisted;
    }

    private static AdkAgentRunExecutor s04Executor(MemoryFixture fixture, AgentFlowFactory factory, AgentFlowConfigValidator validator,
                                                    ScheduledExecutorService control, MemoryContextProvider provider,
                                                    AgentRunAuthorization authority, S04Model model) {
        var estimator = new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
                return new Estimate(20, 20, new BigDecimal("0.10"));
            }
            @Override public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
                return new ContextWindow(request.model().orElseThrow(), snapshot.definition().dependencies().modelRouteRef(),
                        snapshot.definition().dependencies().policyVersionRef(), 2000, 10);
            }
            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
                return new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05"));
            }
        };
        return new AdkAgentRunExecutor(fixture.ledger(), factory, validator, Map.of("s04Model", model), Map.of(),
                control, estimator, fixture.source(), true, authority, new DefaultContextAssembler(List.of(provider)), true);
    }

    private static AgentContextSourceDTO s04MemorySnapshot(AgentLedgerService ledger, TrustedAgentActor actor, AgentRunDTO run) {
        var snapshots = ledger.contextSnapshots(actor, new AgentContextSnapshotsQuery(run.runId(), 0, 2)).items();
        assertEquals(1, snapshots.size());
        var metadata = snapshots.getFirst().metadata();
        assertEquals(S04_SESSION, metadata.nativeSessionId());
        assertEquals(actor.identityFingerprint(), metadata.ownerSha256());
        var sources = metadata.sources().stream().filter(source -> source.kind() == AgentContextSourceKindEnum.MEMORY).toList();
        assertEquals(1, sources.size());
        return sources.getFirst();
    }

    private static void assertPrompt(S04Model model, List<String> expected, List<String> excluded) {
        var prompt = model.prompt.get();
        assertNotNull(prompt);
        for (var marker : expected) assertTrue(prompt.stream().anyMatch(text -> text.contains(marker)), "Expected fixture marker was missing");
        for (var marker : excluded) assertTrue(prompt.stream().noneMatch(text -> text.contains(marker)), "Unexpected fixture marker crossed scope");
    }

    private static final class S04Model implements ChatModel {
        private final String output;
        private final AtomicReference<List<String>> prompt = new AtomicReference<>();

        private S04Model(String output) { this.output = output; }

        @Override public ChatOptions getOptions() { return ChatOptions.builder().model("s04-fixture-model").build(); }

        @Override public ChatResponse call(Prompt ignored) {
            throw new AssertionError("S04 fixture requires streaming");
        }

        @Override public Flux<ChatResponse> stream(Prompt request) {
            return Flux.defer(() -> {
                var messages = request.getInstructions().stream().map(message -> message.getText()).filter(Objects::nonNull).toList();
                assertTrue(prompt.compareAndSet(null, messages), "Unexpected repeat model subscription");
                var response = new ChatResponse(List.of(new Generation(new AssistantMessage(output),
                        ChatGenerationMetadata.builder().finishReason("stop").build())),
                        ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build());
                return Flux.just(response);
            });
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

    private static void preferenceAdmission(AgentLedgerService ledger, PostgresMemoryService memory, MemoryAuthorization authority, JdbcTemplate jdbc) {
        var execution = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(10), Duration.ofSeconds(5), 2, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        var definition = ledger.publish(ALICE, new AgentDefinitionSpecDTO("preference-source", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), execution, Map.of()));
        var run = ledger.start(ALICE, new StartAgentRunCommand("preference-source", 1, null, "preference-source", null, null, new AgentInputDTO("memory.preference.language=zh-CN", null, null), null));
        var claimed = ledger.claim(ALICE, new GetAgentRunQuery(run.runId()), run.revision(), "preference-worker", Duration.ofSeconds(30));
        var finished = ledger.finish(ALICE, claimed.lease(), claimed.run().revision(), new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of("text", "model text must not be a source"), null, "1", null, null, null));
        var event = ledger.events(ALICE, new AgentEventsQuery(run.runId(), finished.lastEventSequence() - 1, 1)).getFirst();
        var policy = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN", "en-US")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), 3);
        var binding = new MemoryPreferenceSourceBinding("personal-preferences", 20, ALICE.issuerFingerprint(), "preference-source", 1, definition.contentSha256(), "extraction", policy.reference());
        var admission = new MemoryPreferenceAdmission(ledger, memory, authority, List.of(binding), List.of(policy), Clock.systemUTC());
        var command = new ExtractMemoryPreferencesCommand(OWNER, "extraction", run.runId(), event.eventId(), binding.id(), policy.reference());
        var first = admission.extract(context(), command); assertEquals(1, first.items().size()); assertEquals(MemoryStateEnum.CANDIDATE, first.items().getFirst().receipt().stateAtMutation());
        assertEquals(first, admission.extract(context(), command));
        String id = first.items().getFirst().receipt().memoryId();
        assertTrue(memory.retrieve(context(), new MemoryRetrievalQuery(OWNER, "extraction", null, null, null, 5)).items().isEmpty());
        memory.forget(context(), new ForgetMemoryCommand("forget-extracted", OWNER, "extraction", id, 1, "USER_REJECTED_CANDIDATE"));
        assertEquals(first, admission.extract(context(), command));
        assertEquals(first, admission.extract(context(), new ExtractMemoryPreferencesCommand(OWNER, "extraction", run.runId(), event.eventId(), binding.id(), policy.reference())));
        assertEquals(MemoryStateEnum.FORGOTTEN, memory.get(context(), new GetMemoryQuery(OWNER, "extraction", id)).state());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_revision WHERE memory_id=? AND (content_json IS NOT NULL OR source_json IS NOT NULL)", Integer.class, id));
        assertTrue(memory.candidates(context(), new MemoryCandidateQuery(OWNER, "extraction", null, null, 5)).items().isEmpty());
        memory.upsert(context(), new UpsertMemoryCommand("canonical-before-extract", OWNER, "extraction-alias", "canonical-preference", MemoryKindEnum.PREFERENCE, 0,
                new MemoryContentDTO(null, Map.of("language", "zh-CN")), PROVENANCE, new BigDecimal("0.5"), false, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null));
        var aliasBinding = new MemoryPreferenceSourceBinding("alias-source", 20, ALICE.issuerFingerprint(), "preference-source", 1, definition.contentSha256(), "extraction-alias", policy.reference());
        var aliasAdmission = new MemoryPreferenceAdmission(ledger, memory, authority, List.of(aliasBinding), List.of(policy), Clock.systemUTC());
        var aliasCommand = new ExtractMemoryPreferencesCommand(OWNER, "extraction-alias", run.runId(), event.eventId(), aliasBinding.id(), policy.reference());
        var aliasReceipt = aliasAdmission.extract(context(), aliasCommand); assertEquals("canonical-preference", aliasReceipt.items().getFirst().receipt().memoryId());
        assertEquals(MemoryMutationOutcomeEnum.DEDUPLICATED, aliasReceipt.items().getFirst().receipt().outcome());
        memory.forget(context(), new ForgetMemoryCommand("forget-canonical-preference", OWNER, "extraction-alias", "canonical-preference", 1, "USER_REJECTED_CANDIDATE"));
        assertEquals(aliasReceipt, aliasAdmission.extract(context(), aliasCommand));
        var changedPolicy = new MemoryExtractionPolicy("preferences", 2, policy.preferenceValues(), new BigDecimal("0.4"), policy.sensitivity(), POLICY.reference(), 3);
        var changedBinding = new MemoryPreferenceSourceBinding("new-alias-source", 20, ALICE.issuerFingerprint(), "preference-source", 1, definition.contentSha256(), "extraction-alias", changedPolicy.reference());
        var changedAdmission = new MemoryPreferenceAdmission(ledger, memory, authority, List.of(changedBinding), List.of(changedPolicy), Clock.systemUTC());
        assertThrows(AgentLedgerException.class, () -> changedAdmission.extract(context(), new ExtractMemoryPreferencesCommand(OWNER, "extraction-alias", run.runId(), event.eventId(), changedBinding.id(), changedPolicy.reference())));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.agent_memory_entry WHERE scope_id='extraction-alias'", Integer.class));
        assertTrue(memory.candidates(context(), new MemoryCandidateQuery(OWNER, "extraction-alias", null, null, 5)).items().isEmpty());
    }

    private static void mergeSuggestions(JdbcTemplate jdbc, PostgresMemoryService memory, MemoryIndexWork work,
                                         Map<String, MemoryIndexPublication.Event> events, EmbeddingModel delegate) throws Exception {
        memory.upsert(context(), new UpsertMemoryCommand("merge-candidate-create", OWNER, "merge-review", "merge-candidate", MemoryKindEnum.PREFERENCE, 0,
                new MemoryContentDTO(null, Map.of("language", "zh-CN")), PROVENANCE, new BigDecimal("0.5"), false, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null));
        for (var id : List.of("merge-a", "merge-b", "merge-c")) {
            var facts = id.equals("merge-a") ? Map.of("language", "zh-CN", "format", "short") : id.equals("merge-b") ? Map.of("language", "en-US") : Map.of("timezone", "UTC");
            memory.upsert(context(), new UpsertMemoryCommand("create-" + id, OWNER, "merge-review", id, MemoryKindEnum.PREFERENCE, 0, new MemoryContentDTO(null, facts), PROVENANCE,
                    BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null));
        }
        var query = new MemoryMergeQuery(OWNER, "merge-review", "merge-candidate", 1, false, null, 1, null);
        int before = jdbc.queryForObject("SELECT count(*) FROM tianji_agent.memory_test_outbox", Integer.class);
        var first = memory.mergeSuggestions(context(), query); assertEquals("merge-a", first.items().getFirst().hit().entry().memoryId()); assertEquals("merge-a", first.nextAfterMemoryId());
        var second = memory.mergeSuggestions(context(), new MemoryMergeQuery(OWNER, "merge-review", "merge-candidate", 1, false, first.nextAfterMemoryId(), 1, null));
        assertEquals("merge-b", second.items().getFirst().hit().entry().memoryId()); assertEquals(MemoryMergeRelationEnum.DIFFERING_FIELDS, second.items().getFirst().relation()); assertNull(second.nextAfterMemoryId());
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM tianji_agent.memory_test_outbox", Integer.class));
        assertEquals(MemoryStateEnum.CANDIDATE, memory.get(context(), new GetMemoryQuery(OWNER, "merge-review", "merge-candidate")).state());
        for (var id : List.of("merge-a", "merge-b", "merge-c")) work.apply(build(events, id, 1), Instant.now().plusSeconds(10));
        var semantic = new MemoryMergeQuery(OWNER, "merge-review", "merge-candidate", 1, true, null, 5, 0.0);
        assertEquals(3, memory.mergeSuggestions(context(), semantic).items().size());
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return response(); })
                .when(delegate).call(any(EmbeddingRequest.class));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var future = executor.submit(() -> memory.mergeSuggestions(context(), semantic)); assertTrue(entered.await(5, TimeUnit.SECONDS));
                memory.forget(context(), new ForgetMemoryCommand("forget-merge-source", OWNER, "merge-review", "merge-candidate", 1, "USER_REJECTED_CANDIDATE")); release.countDown();
                var failure = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
                assertEquals(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, assertInstanceOf(AgentLedgerException.class, failure.getCause()).code());
            } finally { release.countDown(); }
        } finally {
            doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return response(); }).when(delegate).call(any(EmbeddingRequest.class));
        }
        assertThrows(AgentLedgerException.class, () -> memory.mergeSuggestions(context(), query));
    }

    private static void candidates(PostgresMemoryService memory) {
        for (var id : List.of("future", "now")) memory.upsert(context(), new UpsertMemoryCommand("candidate-create-" + id, OWNER, "candidate-review", id, MemoryKindEnum.FACT, 0,
                new MemoryContentDTO("candidate " + id, Map.of()), PROVENANCE, new BigDecimal("0.5"), false, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), id.equals("future") ? Instant.now().plusSeconds(60) : null, null));
        var query = new MemoryCandidateQuery(OWNER, "candidate-review", null, null, 1); var first = memory.candidates(context(), query);
        assertEquals("future", first.items().getFirst().memoryId()); assertEquals("future", first.nextAfterMemoryId());
        var next = memory.candidates(context(), new MemoryCandidateQuery(OWNER, "candidate-review", null, first.nextAfterMemoryId(), 1)); assertEquals("now", next.items().getFirst().memoryId()); assertNull(next.nextAfterMemoryId());
        assertTrue(memory.retrieve(context(), new MemoryRetrievalQuery(OWNER, "candidate-review", null, null, null, 5)).items().isEmpty());
        memory.correct(context(), new CorrectMemoryCommand("confirm-candidate", OWNER, "candidate-review", "now", 1, new MemoryContentDTO("approved fact", Map.of()), PROVENANCE, BigDecimal.ONE, "USER_CONFIRMED"));
        assertEquals(List.of("future"), memory.candidates(context(), new MemoryCandidateQuery(OWNER, "candidate-review", null, null, 5)).items().stream().map(item -> item.memoryId()).toList());
        assertEquals("now", memory.retrieve(context(), new MemoryRetrievalQuery(OWNER, "candidate-review", null, null, null, 5)).items().getFirst().entry().memoryId());
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

    private static void preferenceIntents(AgentLedgerService ledger, DriverManagerDataSource source, PostgresMemoryService memory, JdbcTemplate jdbc) throws Exception {
        var history = jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
        var migration = Flyway.configure().dataSource(source).schemas("tianji_agent").defaultSchema("tianji_agent").locations("classpath:db/tianji-agent").target("12")
                .placeholders(Map.of("tianjiMemoryDimensions", "2")).cleanDisabled(true).load();
        assertEquals(1, migration.migrate().migrationsExecuted); migration.validate();
        assertEquals(history, jdbc.queryForList("SELECT version,checksum FROM tianji_agent.flyway_schema_history WHERE version<>'12' AND version IS NOT NULL ORDER BY installed_rank"));
        var store = new MemoryPreferenceIntentStore(source, ledger); store.verifyStorage();
        var run = ledger.start(ALICE, new StartAgentRunCommand("preference-source", 1, null, "background-preferences", null, null,
                new AgentInputDTO("memory.preference.language=zh-CN\nmemory.preference.timezone=UTC", null, null), null));
        var query = new GetAgentRunQuery(run.runId()); var material = ledger.executionMaterial(ALICE, query);
        var policy = new MemoryExtractionPolicy("background", 1, Map.of("language", Set.of("zh-CN"), "timezone", Set.of("UTC")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), 3);
        var binding = new MemoryPreferenceSourceBinding("background", 20, ALICE.issuerFingerprint(), material.run().definitionKey(), material.run().definitionVersion(), material.snapshot().definitionContentSha256(), "background", policy.reference());
        var resource = URI.create("https://agent.example/api"); var deadline = Instant.now().plusSeconds(300).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var first = store.reserve(material.authorization(), binding, resource, deadline, 5).orElseThrow(); var second = store.reserve(material.authorization(), binding, resource, deadline.plusSeconds(10), 5).orElseThrow();
        var ticket = new AgentExecutionGrant.Ticket("9" + "2".repeat(62), "ENC:v1:test:A:A", deadline.minusSeconds(30));
        assertFalse(store.recordIssueFailure(first, "OWNER_DENIED", true));
        assertTrue(store.recordIssueFailure(second, "ISSUE_UNAVAILABLE", false));
        assertEquals("ISSUE_UNAVAILABLE", store.view(ALICE, run.runId(), "background").orElseThrow().reasonCode());
        assertFalse(store.complete(first, ticket)); assertTrue(store.complete(second, ticket)); assertEquals(second.deadlineAt(), first.deadlineAt());
        var claimedRun = ledger.claim(ALICE, query, run.revision(), "background-source", Duration.ofSeconds(30));
        var finished = ledger.finish(ALICE, claimedRun.lease(), claimedRun.run().revision(), new AgentExecutionResult(run.runId(), AgentRunStatusEnum.COMPLETED, Map.of(), null, "1", null, null, null));
        var event = ledger.events(ALICE, new AgentEventsQuery(run.runId(), finished.lastEventSequence() - 1, 1)).getFirst();
        assertTrue(store.candidates(20, ALICE.issuerFingerprint(), 10).contains(first.intentId()));
        var lease = store.claim(20, ALICE.issuerFingerprint(), first.intentId(), "worker-a", Duration.ofSeconds(30)).orElseThrow();
        var extracted = new MemoryPreferenceExtraction().evaluate(new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, material.snapshot().input().text()), policy);
        var commands = MemoryPreferenceCommands.create(ALICE, new ExtractMemoryPreferencesCommand(OWNER, "background", run.runId(), event.eventId(), binding.id(), policy.reference()), policy, extracted);
        var commandHash = PostgresMemoryService.preferenceCommandListSha256(commands);
        lease = store.sealCommands(lease, commands.size(), commandHash);
        var originalReceipt = memory.upsert(preferenceContext(source, store, lease, commands.getFirst()), commands.getFirst());
        assertEquals(originalReceipt, memory.upsert(preferenceContext(source, store, lease, commands.getFirst()), commands.getFirst()));
        lease = store.acknowledge(lease).orElseThrow(); assertEquals(1, lease.nextIndex());
        memory.upsert(preferenceContext(source, store, lease, commands.get(1)), commands.get(1)); assertTrue(store.acknowledge(lease).isEmpty());
        assertEquals(MemoryPreferenceIntent.State.APPLIED, store.view(ALICE, run.runId(), "background").orElseThrow().state());
        assertNull(jdbc.queryForObject("SELECT ciphertext FROM tianji_agent.agent_memory_preference_intent WHERE intent_id=?", String.class, first.intentId()));

        var cancelBinding = new MemoryPreferenceSourceBinding("cancel-background", 20, ALICE.issuerFingerprint(), binding.definitionKey(), binding.definitionVersion(), binding.definitionSha256(), "background-cancel", policy.reference());
        var cancelReservation = store.reserve(material.authorization(), cancelBinding, resource, deadline, 5).orElseThrow(); assertTrue(store.complete(cancelReservation, ticket));
        var old = store.claim(20, ALICE.issuerFingerprint(), cancelReservation.intentId(), "old-worker", Duration.ofSeconds(30)).orElseThrow();
        jdbc.update("UPDATE tianji_agent.agent_memory_preference_intent SET lease_expires_at=clock_timestamp()-interval '1 second' WHERE intent_id=?", cancelReservation.intentId());
        var current = store.claim(20, ALICE.issuerFingerprint(), cancelReservation.intentId(), "new-worker", Duration.ofSeconds(30)).orElseThrow(); assertTrue(current.epoch() > old.epoch());
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        assertThrows(AgentLedgerException.class, () -> transaction.executeWithoutResult(status -> store.verifyFence(old)));
        assertThrows(AgentLedgerException.class, () -> store.cancel(ALICE, cancelReservation.intentId(), current.revision(), "expired-cancel", "c".repeat(64), Instant.EPOCH));
        assertDoesNotThrow(() -> transaction.executeWithoutResult(status -> store.verifyFence(current)));
        var cancelled = store.cancel(ALICE, cancelReservation.intentId(), current.revision(), "cancel-background", "c".repeat(64), Instant.now().plusSeconds(20));
        assertEquals(MemoryPreferenceIntent.State.CANCELLED, cancelled.state()); assertEquals(cancelled, store.cancel(ALICE, cancelReservation.intentId(), current.revision(), "cancel-background", "c".repeat(64)));
        assertThrows(AgentLedgerException.class, () -> transaction.executeWithoutResult(status -> store.verifyFence(current)));
        assertFalse(store.complete(cancelReservation, ticket));
        jdbc.execute("ALTER TABLE tianji_agent.agent_memory_preference_intent DROP CONSTRAINT preference_intent_actor_type");
        assertThrows(AgentLedgerException.class, store::verifyStorage);
    }

    private static MemoryOperationContext preferenceContext(DriverManagerDataSource source, MemoryPreferenceIntentStore store, MemoryPreferenceIntent.Lease lease, UpsertMemoryCommand command) {
        var permit = new MemoryPreferenceWritePermit() {
            @Override public TrustedAgentActor actor() { return ALICE; }
            @Override public UpsertMemoryCommand command() { return command; }
            @Override public Instant expiresAt() { return lease.expiresAt(); }
            @Override public boolean usesDataSource(javax.sql.DataSource candidate) { return candidate == source; }
            @Override public void verifyTransaction() { store.verifyFence(lease); }
        };
        var deadline = Instant.now().plusSeconds(5); if (lease.expiresAt().isBefore(deadline)) deadline = lease.expiresAt(); return new MemoryOperationContext(ALICE, deadline, null, permit);
    }

    private static UpsertMemoryCommand upsert(String command, String id, String text) { return new UpsertMemoryCommand(command, OWNER, "research", id, MemoryKindEnum.PREFERENCE, 0,
            new MemoryContentDTO(text, Map.of()), PROVENANCE, BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL, POLICY.reference(), null, null); }
    private static MemoryOperationContext context() { return new MemoryOperationContext(ALICE, Instant.now().plusSeconds(30), null); }
    private static MemoryRetrievalQuery search() { return new MemoryRetrievalQuery(OWNER, "research", MemoryKindEnum.PREFERENCE, "preference", null, 5); }
    private static MemoryIndexPublication.Event build(Map<String, MemoryIndexPublication.Event> events, String id, long revision) { return events.values().stream().filter(event -> event.action() == MemoryIndexPublication.Action.BUILD && event.memoryId().equals(id) && event.revision() == revision).findFirst().orElseThrow(); }
    private static EmbeddingResponse response() { var metadata = new EmbeddingResponseMetadata(); metadata.setModel("text-embedding-3-small"); return new EmbeddingResponse(List.of(new Embedding(new float[]{0.25f, 0.75f}, 0)), metadata); }
}
