package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryRetentionPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryContextProvider;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.context.TianjiContextAssemblyConfiguration;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryRunAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionGrantIssuer;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryContextConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private static final TrustedAgentActor ACTOR = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final MemoryRetentionPolicy RETENTION = new MemoryRetentionPolicy("retention", 1, Duration.ofDays(1), Duration.ofDays(30), 100,
            Set.of(MemorySensitivityEnum.CONFIDENTIAL), Set.of(MemorySensitivityEnum.CONFIDENTIAL), true);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final MemoryReadGrantStore store = mock(MemoryReadGrantStore.class);
    private final PostgresMemoryService memory = mock(PostgresMemoryService.class);
    private final TianjiExecutionGrantAuthorizer execution = mock(TianjiExecutionGrantAuthorizer.class);
    private final TianjiExecutionGrantIssuer submissions = mock(TianjiExecutionGrantIssuer.class);
    private final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);

    @Test void default_is_off_and_a_missing_prerequisite_stops_construction() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryContextConfiguration.class)
                .run(context -> assertTrue(context.getBeansOfType(MemoryContextProvider.class).isEmpty()));
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryContextConfiguration.class).withPropertyValues("stellar.tianji.agent.context.memory.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        verifyNoInteractions(store, memory, execution, submissions);
    }

    @Test void context_and_http_use_one_named_authority_and_the_actual_issuer_gate_and_provider() {
        var constructedRun = new AtomicReference<TianjiMemoryRunAuthorization>(); var constructedIssuer = new AtomicReference<TianjiMemoryGrantIssuer>();
        prepare(constructedRun, constructedIssuer);
        try (var runConstructors = mockConstruction(TianjiMemoryRunAuthorization.class, (value, ignored) -> {
            constructedRun.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true);
        }); var issueConstructors = mockConstruction(TianjiMemoryGrantIssuer.class, (value, ignored) -> {
            constructedIssuer.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true);
        })) {
            runner().withPropertyValues("stellar.tianji.agent.memory.http.enabled=true").run(context -> {
                assertNull(context.getStartupFailure());
                var current = context.getBean("tianjiMemoryAuthorization", MemoryAuthorization.class); assertTrue(context.getBean(DefaultContextAssembler.class).hasProviders());
                assertEquals(1, context.getBeansOfType(MemoryContextProvider.class).size()); assertEquals(1, runConstructors.constructed().size()); assertEquals(1, issueConstructors.constructed().size());
                verify(store, never()).requireServiceStorage(); verify(memory, never()).retrieveContext(any(), any(), anySet());
                var now = Instant.now(); when(context.getBean(TianjiAgentAccess.class).require(anyString())).thenReturn(new TianjiAgentAccess.Authorized(ACTOR, now.plusSeconds(300), ISSUER));
                var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
                assertEquals(MemoryAuthorization.Decision.ALLOW, current.authorize(new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(5), null), owner,
                        "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null)));
                verify(constructedRun.get(), never()).authorize(any());
                var run = new AgentRunAuthorization.Request(ACTOR, "run", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy", false, now.plusSeconds(60));
                var request = new MemoryAuthorization.Request(new MemoryOperationContext(ACTOR, now.plusSeconds(5), run), owner, "research", MemoryAuthorization.Operation.GET, "memory", 0, null, null);
                when(constructedRun.get().authorize(any())).thenReturn(MemoryAuthorization.Decision.ALLOW); assertEquals(MemoryAuthorization.Decision.ALLOW, current.authorize(request));
                verify(constructedRun.get()).authorize(request);
            });
            doReturn(false).when(execution).usesMemoryAuthorization(any());
            runner().run(context -> assertNotNull(context.getStartupFailure()));
        }
    }

    @Test void service_sources_require_governed_service_admission_and_explicit_v11_readiness() {
        var constructedRun = new AtomicReference<TianjiMemoryRunAuthorization>(); var constructedIssuer = new AtomicReference<TianjiMemoryGrantIssuer>(); prepare(constructedRun, constructedIssuer);
        var services = mock(TianjiServiceExecutionBindings.class); var serviceIssuer = mock(TianjiServiceExecutionGrantIssuer.class);
        when(serviceIssuer.usesMemoryIssuer(any())).thenAnswer(call -> call.getArgument(0) == constructedIssuer.get());
        try (var runConstructors = mockConstruction(TianjiMemoryRunAuthorization.class, (value, ignored) -> { constructedRun.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true); });
             var issueConstructors = mockConstruction(TianjiMemoryGrantIssuer.class, (value, ignored) -> { constructedIssuer.set(value); when(value.usesLedger(ledger)).thenReturn(true); when(value.usesBindings(anyList())).thenReturn(true); })) {
            runner().withPropertyValues("stellar.tianji.agent.context.memory.bindings[0].principal-type=SERVICE", "stellar.tianji.agent.context.memory.bindings[0].owner-type=APPLICATION")
                    .run(context -> assertNotNull(context.getStartupFailure()));
            runner().withPropertyValues("stellar.tianji.agent.context.memory.bindings[0].principal-type=SERVICE", "stellar.tianji.agent.context.memory.bindings[0].owner-type=APPLICATION", "stellar.tianji.agent.service-execution.enabled=true")
                    .withBean(TianjiServiceExecutionBindings.class, () -> services).withBean(TianjiServiceExecutionGrantIssuer.class, () -> serviceIssuer)
                    .run(context -> { assertNull(context.getStartupFailure()); verify(store).requireServiceStorage(); });
        }
    }

    private void prepare(AtomicReference<TianjiMemoryRunAuthorization> runs, AtomicReference<TianjiMemoryGrantIssuer> sourceIssuers) {
        when(store.usesLedger(ledger)).thenReturn(true); when(memory.policyReferences()).thenReturn(List.of(RETENTION.reference()));
        when(execution.usesMemoryAuthorization(any())).thenAnswer(call -> call.getArgument(0) == runs.get());
        when(submissions.usesMemoryIssuer(any())).thenAnswer(call -> call.getArgument(0) == sourceIssuers.get());
        when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER, URI.create("https://idp.example/jwks"),
                "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
    }
    private ApplicationContextRunner runner() {
        var profile = new VersionedMemoryEmbeddingModel.Profile("memory", 1, "embedding", "text-embedding-3-small", 2, 8191, 64, 16);
        var storage = new TianjiMemoryProperties(2, "0.8.0", null, 1, profile, List.of(RETENTION), List.of(new TianjiMemoryProperties.Origin(20, ISSUER)));
        return new ApplicationContextRunner().withUserConfiguration(TianjiMemoryContextConfiguration.class, TianjiMemoryHttpConfiguration.class, TianjiContextAssemblyConfiguration.class)
                .withBean(AgentLedgerService.class, () -> ledger).withBean(MemoryReadGrantStore.class, () -> store).withBean(PostgresMemoryService.class, () -> memory)
                .withBean(TianjiMemoryProperties.class, () -> storage).withBean(TianjiAgentAccess.class, () -> mock(TianjiAgentAccess.class))
                .withBean(TianjiExecutionGrantAuthorizer.class, () -> execution).withBean(TianjiExecutionGrantIssuer.class, () -> submissions)
                .withBean("idpStarterProperties", IdpStarterProperties.class, IdpStarterProperties::new)
                .withBean("identityAuthorityProperties", IdentityAuthorityProperties.class, IdentityAuthorityProperties::new)
                .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers)
                .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> mock(IdpServiceOAuth2Client.class))
                .withBean("jacksonObjectMapper", ObjectMapper.class, ObjectMapper::new).withBean(Rbac3StarterProperties.class, Rbac3StarterProperties::new)
                .withPropertyValues("stellar.tianji.agent.context.memory.enabled=true", "stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                        "stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true", "stellar.tianji.agent.context.snapshots.enabled=true",
                        "stellar.tianji.agent.context.memory.bindings[0].id=personal", "stellar.tianji.agent.context.memory.bindings[0].tenant-id=20",
                        "stellar.tianji.agent.context.memory.bindings[0].issuer-fingerprint=" + ACTOR.issuerFingerprint(), "stellar.tianji.agent.context.memory.bindings[0].principal-type=USER",
                        "stellar.tianji.agent.context.memory.bindings[0].definition-key=assistant", "stellar.tianji.agent.context.memory.bindings[0].definition-version=2", "stellar.tianji.agent.context.memory.bindings[0].definition-sha256=" + "a".repeat(64),
                        "stellar.tianji.agent.context.memory.bindings[0].owner-type=USER", "stellar.tianji.agent.context.memory.bindings[0].scope-id=research", "stellar.tianji.agent.context.memory.bindings[0].kind=PREFERENCE",
                        "stellar.tianji.agent.context.memory.bindings[0].semantic=true", "stellar.tianji.agent.context.memory.bindings[0].limit=2", "stellar.tianji.agent.context.memory.bindings[0].retention-policy-refs[0]=" + RETENTION.reference());
    }
}
