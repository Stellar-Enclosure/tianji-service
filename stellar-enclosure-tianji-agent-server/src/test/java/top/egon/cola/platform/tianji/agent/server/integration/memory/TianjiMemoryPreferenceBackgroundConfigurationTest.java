package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceCommands;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceWritePermit;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryRunAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Finite assembly with controlled schema/client markers and a manual scheduler; no server, database or owner call is started. */
class TianjiMemoryPreferenceBackgroundConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final String HASH = "a".repeat(64);
    private static final MemoryRetentionPolicy RETENTION = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
    private static final MemoryExtractionPolicy POLICY = new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN"), "timezone", Set.of("UTC")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION.reference(), 2);
    private static final MemoryPreferenceSourceBinding BINDING = new MemoryPreferenceSourceBinding("source", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", POLICY.reference());
    private final DataSource source = mock(DataSource.class);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryPreferenceIntentStore store = mock(MemoryPreferenceIntentStore.class);
    private final PostgresMemoryService memory = mock(PostgresMemoryService.class);
    private final TianjiAgentAccess access = mock(TianjiAgentAccess.class);
    private final TianjiExecutionGrantIssuer submissions = mock(TianjiExecutionGrantIssuer.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final AtomicReference<TianjiMemoryPreferenceWriteAuthorization> writes = new AtomicReference<>();
    private final AtomicReference<TianjiMemoryPreferenceGrantIssuer> issued = new AtomicReference<>();

    @BeforeEach void setup() {
        when(ledger.usesDataSource(source)).thenReturn(true); when(store.usesDataSource(source)).thenReturn(true); when(store.usesLedger(ledger)).thenReturn(true); when(memory.usesDataSource(source)).thenReturn(true);
        when(memory.policyReferences()).thenReturn(List.of(RETENTION.reference())); when(submissions.usesPreferenceIssuer(any())).thenAnswer(call -> call.getArgument(0) == issued.get());
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"), "tenant:20",
                TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenReturn(mock(ScheduledFuture.class));
    }

    @Test void default_is_off_and_each_governed_prerequisite_is_required_before_any_source_or_storage_work() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceBackgroundConfiguration.class)
                .run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(MemoryPreferenceJob.class).isEmpty()); });
        for (var capability : List.of("api", "storage", "runtime", "memory", "memory.http", "execution-grants", "execution-authorization", "reconciliation")) {
            runner(sources()).withPropertyValues("stellar.tianji.agent." + capability + ".enabled=false").run(context -> assertNotNull(context.getStartupFailure()));
        }
        verify(store, never()).verifyStorage(); verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class)); verify(ledger, never()).executionMaterial(any(), any());
    }

    @Test void enabled_assembly_verifies_storage_uses_the_existing_scheduler_and_routes_http_and_private_writes_without_fallback() {
        try (var writeConstructors = writeConstructors(); var issueConstructors = issueConstructors()) {
            runner(sources()).run(context -> {
                assertNull(context.getStartupFailure()); assertEquals(1, writeConstructors.constructed().size()); assertEquals(1, issueConstructors.constructed().size());
                assertEquals(1, context.getBeansOfType(TianjiMemoryHttpAuthorization.class).size()); verify(store).verifyStorage();
                assertTrue(context.getBean(MemoryPreferenceJob.class).isRunning()); verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(5)));
                verify(ledger, never()).executionMaterial(any(), any()); verify(store, never()).candidates(anyLong(), anyString(), anyInt()); verify(issued.get(), never()).ensure(any(), any());
                var current = context.getBean("tianjiMemoryAuthorization", MemoryAuthorization.class); var now = Instant.now(); var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
                when(access.require(anyString())).thenReturn(new TianjiAgentAccess.Authorized(ACTOR, now.plusSeconds(300), ISSUER));
                var read = new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(10), null), owner, "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null);
                assertEquals(MemoryAuthorization.Decision.ALLOW, current.authorize(read)); clearInvocations(access);
                var candidate = writeRequest(); when(writes.get().authorize(candidate)).thenReturn(MemoryAuthorization.Decision.DENY);
                assertEquals(MemoryAuthorization.Decision.DENY, current.authorize(candidate)); verify(writes.get()).authorize(candidate); verifyNoInteractions(access);
                var run = new AgentRunAuthorization.Request(ACTOR, "run", "assistant", 1, HASH, HASH, HASH, "policy", false, now.plusSeconds(60));
                var backgroundRead = new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(10), run), owner, "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null);
                assertEquals(MemoryAuthorization.Decision.UNAVAILABLE, current.authorize(backgroundRead)); verifyNoInteractions(access);
            });
            verify(writes.get()).close();
        }
    }

    @Test void missing_actual_issuer_wrong_datasource_unregistered_retention_or_insufficient_progress_budget_fail_closed() {
        try (var writeConstructors = writeConstructors(); var issueConstructors = issueConstructors()) {
            doReturn(false).when(submissions).usesPreferenceIssuer(any()); runner(sources()).run(context -> assertNotNull(context.getStartupFailure())); verify(store, never()).verifyStorage();
            doAnswer(call -> call.getArgument(0) == issued.get()).when(submissions).usesPreferenceIssuer(any());
            runner(sources()).withPropertyValues("stellar.tianji.agent.memory.extraction.background.maximum-attempts=1").run(context -> assertNotNull(context.getStartupFailure()));
            var changed = new MemoryExtractionPolicy("other", 1, POLICY.preferenceValues(), POLICY.candidateConfidence(), POLICY.sensitivity(), "unknown@1#" + HASH, 2);
            var binding = new MemoryPreferenceSourceBinding("other", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", changed.reference());
            runner(new TianjiMemoryPreferenceProperties(List.of(changed), List.of(binding))).run(context -> assertNotNull(context.getStartupFailure()));
            doReturn(false).when(store).usesDataSource(source); runner(sources()).run(context -> assertNotNull(context.getStartupFailure())); verify(store, never()).verifyStorage();
        }
    }

    @Test void read_context_and_background_write_share_one_named_router_and_keep_distinct_current_authorizers() {
        var reads = new AtomicReference<TianjiMemoryRunAuthorization>(); var readIssuer = new AtomicReference<TianjiMemoryGrantIssuer>(); var readStore = mock(MemoryReadGrantStore.class); var execution = mock(TianjiExecutionGrantAuthorizer.class);
        when(readStore.usesLedger(ledger)).thenReturn(true); when(execution.usesMemoryAuthorization(any())).thenAnswer(call -> call.getArgument(0) == reads.get());
        when(submissions.usesMemoryIssuer(any())).thenAnswer(call -> call.getArgument(0) == readIssuer.get());
        try (var writeConstructors = writeConstructors(); var issueConstructors = issueConstructors();
             var readConstructors = mockConstruction(TianjiMemoryRunAuthorization.class, (value, ignored) -> { reads.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true); });
             var readIssueConstructors = mockConstruction(TianjiMemoryGrantIssuer.class, (value, ignored) -> { readIssuer.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true); })) {
            runner(sources()).withUserConfiguration(TianjiMemoryContextConfiguration.class).withBean(MemoryReadGrantStore.class, () -> readStore).withBean(TianjiExecutionGrantAuthorizer.class, () -> execution)
                    .withPropertyValues("stellar.tianji.agent.context.memory.enabled=true", "stellar.tianji.agent.context.snapshots.enabled=true",
                            "stellar.tianji.agent.context.memory.bindings[0].id=read", "stellar.tianji.agent.context.memory.bindings[0].tenant-id=20", "stellar.tianji.agent.context.memory.bindings[0].issuer-fingerprint=" + ACTOR.issuerFingerprint(),
                            "stellar.tianji.agent.context.memory.bindings[0].principal-type=USER", "stellar.tianji.agent.context.memory.bindings[0].definition-key=assistant", "stellar.tianji.agent.context.memory.bindings[0].definition-version=1", "stellar.tianji.agent.context.memory.bindings[0].definition-sha256=" + HASH,
                            "stellar.tianji.agent.context.memory.bindings[0].owner-type=USER", "stellar.tianji.agent.context.memory.bindings[0].scope-id=research", "stellar.tianji.agent.context.memory.bindings[0].limit=2", "stellar.tianji.agent.context.memory.bindings[0].retention-policy-refs[0]=" + RETENTION.reference())
                    .run(context -> {
                        assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(TianjiMemoryHttpAuthorization.class).size()); var current = context.getBean("tianjiMemoryAuthorization", MemoryAuthorization.class);
                        var request = writeRequest(); when(writes.get().authorize(request)).thenReturn(MemoryAuthorization.Decision.DENY); assertEquals(MemoryAuthorization.Decision.DENY, current.authorize(request)); verify(reads.get(), never()).authorize(any());
                        var now = Instant.now(); var run = new AgentRunAuthorization.Request(ACTOR, "run", "assistant", 1, HASH, HASH, HASH, "policy", false, now.plusSeconds(60));
                        var read = new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(10), run), request.owner(), "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null);
                        when(reads.get().authorize(read)).thenReturn(MemoryAuthorization.Decision.ALLOW); assertEquals(MemoryAuthorization.Decision.ALLOW, current.authorize(read)); verify(reads.get()).authorize(read);
                    });
        }
    }

    @Test void schema_failure_or_revoked_origin_prevents_task_start_and_a_missing_writer_cannot_borrow_http_authority() {
        try (var writeConstructors = writeConstructors(); var issueConstructors = issueConstructors()) {
            doThrow(new IllegalStateException("Schema unavailable")).when(store).verifyStorage(); runner(sources()).run(context -> assertNotNull(context.getStartupFailure()));
            verify(scheduler, never()).scheduleWithFixedDelay(any(Runnable.class), any(Duration.class));
            clearInvocations(store); when(issuers.findByIssuer(anyString())).thenReturn(Optional.empty()); runner(sources()).run(context -> assertNotNull(context.getStartupFailure())); verify(store, never()).verifyStorage();
        }
        @SuppressWarnings("unchecked") org.springframework.beans.factory.ObjectProvider<TianjiMemoryPreferenceWriteAuthorization> candidates = mock(org.springframework.beans.factory.ObjectProvider.class);
        var environment = new org.springframework.mock.env.MockEnvironment().withProperty("stellar.tianji.agent.memory.extraction.background.enabled", "true");
        var failure = assertThrows(IllegalStateException.class, () -> new TianjiMemoryContextConfiguration().tianjiMemorySourceAuthorization(null, null, environment, candidates));
        assertEquals("TIANJI_MEMORY_PREFERENCE_WRITE_AUTHORIZER_REQUIRED", failure.getMessage());
    }

    @Test void the_default_progress_budget_covers_a_full_batch_and_only_counts_keys_the_policy_can_emit() {
        try (var writeConstructors = writeConstructors(); var issueConstructors = issueConstructors()) {
            var values = java.util.stream.IntStream.range(0, 32).boxed().collect(java.util.stream.Collectors.toMap(index -> "key" + index, index -> Set.of("yes")));
            var full = new MemoryExtractionPolicy("full", 1, values, POLICY.candidateConfidence(), POLICY.sensitivity(), RETENTION.reference(), 32);
            var fullBinding = new MemoryPreferenceSourceBinding("full", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", full.reference());
            var fullSources = new TianjiMemoryPreferenceProperties(List.of(full), List.of(fullBinding));
            runner(fullSources).run(context -> { assertNull(context.getStartupFailure()); assertEquals(32, context.getBean(TianjiMemoryPreferenceBackgroundProperties.class).maximumAttempts()); });
            runner(fullSources).withPropertyValues("stellar.tianji.agent.memory.extraction.background.maximum-attempts=31").run(context -> assertNotNull(context.getStartupFailure()));
            var single = new MemoryExtractionPolicy("single", 1, Map.of("language", Set.of("zh-CN")), POLICY.candidateConfidence(), POLICY.sensitivity(), RETENTION.reference(), 32);
            var singleBinding = new MemoryPreferenceSourceBinding("single", 20, ACTOR.issuerFingerprint(), "assistant", 1, HASH, "research", single.reference());
            runner(new TianjiMemoryPreferenceProperties(List.of(single), List.of(singleBinding))).withPropertyValues("stellar.tianji.agent.memory.extraction.background.maximum-attempts=1")
                    .run(context -> assertNull(context.getStartupFailure()));
        }
    }

    private org.mockito.MockedConstruction<TianjiMemoryPreferenceWriteAuthorization> writeConstructors() {
        return mockConstruction(TianjiMemoryPreferenceWriteAuthorization.class, (value, ignored) -> { writes.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList(), anyList())).thenReturn(true); });
    }
    private org.mockito.MockedConstruction<TianjiMemoryPreferenceGrantIssuer> issueConstructors() {
        return mockConstruction(TianjiMemoryPreferenceGrantIssuer.class, (value, ignored) -> { issued.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true); });
    }
    private static TianjiMemoryPreferenceProperties sources() { return new TianjiMemoryPreferenceProperties(List.of(POLICY), List.of(BINDING)); }
    private ApplicationContextRunner runner(TianjiMemoryPreferenceProperties sources) {
        var profile = new VersionedMemoryEmbeddingModel.Profile("memory", 1, "embedding", "text-embedding-3-small", 2, 8191, 64, 16);
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, profile, List.of(RETENTION), List.of(new TianjiMemoryProperties.Origin(20, ISSUER)));
        return new ApplicationContextRunner().withUserConfiguration(TianjiMemoryPreferenceBackgroundConfiguration.class, TianjiMemoryHttpConfiguration.class)
                .withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger).withBean(MemoryPreferenceIntentStore.class, () -> store).withBean(PostgresMemoryService.class, () -> memory)
                .withBean(TianjiMemoryProperties.class, () -> storage).withBean(TianjiAgentAccess.class, () -> access).withBean(TianjiExecutionGrantIssuer.class, () -> submissions)
                .withBean("idpStarterProperties", IdpStarterProperties.class, IdpStarterProperties::new).withBean("identityAuthorityProperties", IdentityAuthorityProperties.class, IdentityAuthorityProperties::new)
                .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers).withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> mock(IdpServiceOAuth2Client.class))
                .withBean("jacksonObjectMapper", ObjectMapper.class, ObjectMapper::new).withBean(Rbac3StarterProperties.class, Rbac3StarterProperties::new)
                .withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> scheduler)
                .withPropertyValues("stellar.tianji.agent.memory.extraction.background.enabled=true", "stellar.tianji.agent.memory.http.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                        "stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true", "stellar.tianji.agent.reconciliation.enabled=true")
                .withPropertyValues(sourceProperties(sources));
    }
    private static String[] sourceProperties(TianjiMemoryPreferenceProperties sources) {
        var values = new java.util.ArrayList<String>(); String prefix = "stellar.tianji.agent.memory.extraction.";
        for (int index = 0; index < sources.policies().size(); index++) {
            var policy = sources.policies().get(index); String item = prefix + "policies[" + index + "].";
            values.addAll(List.of(item + "key=" + policy.key(), item + "version=" + policy.version(), item + "candidate-confidence=" + policy.candidateConfidence().toPlainString(), item + "sensitivity=" + policy.sensitivity(),
                    item + "retention-policy-ref=" + policy.retentionPolicyRef(), item + "maximum-candidates=" + policy.maximumCandidates()));
            policy.preferenceValues().forEach((key, entries) -> { int number = 0; for (var value : entries.stream().sorted().toList()) values.add(item + "preference-values." + key + '[' + number++ + "]=" + value); });
        }
        for (int index = 0; index < sources.bindings().size(); index++) {
            var binding = sources.bindings().get(index); String item = prefix + "bindings[" + index + "].";
            values.addAll(List.of(item + "id=" + binding.id(), item + "tenant-id=" + binding.tenantId(), item + "issuer-fingerprint=" + binding.issuerFingerprint(), item + "definition-key=" + binding.definitionKey(),
                    item + "definition-version=" + binding.definitionVersion(), item + "definition-sha256=" + binding.definitionSha256(), item + "scope-id=" + binding.scopeId(), item + "policy-ref=" + binding.policyRef()));
        }
        return values.toArray(String[]::new);
    }
    private static MemoryAuthorization.Request writeRequest() {
        var now = Instant.now(); var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var extracted = new MemoryPreferenceExtraction().evaluate(new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, "memory.preference.language=zh-CN"), POLICY);
        var command = MemoryPreferenceCommands.create(ACTOR, new ExtractMemoryPreferencesCommand(owner, "research", "run", "terminal", "source", POLICY.reference()), POLICY, extracted).getFirst();
        var permit = mock(MemoryPreferenceWritePermit.class); when(permit.actor()).thenReturn(ACTOR); when(permit.command()).thenReturn(command); when(permit.expiresAt()).thenReturn(now.plusSeconds(20));
        return new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(10), null, permit), owner, "research", MemoryAuthorization.Operation.UPSERT, command.memoryId(), 0, "run", "terminal");
    }
}
