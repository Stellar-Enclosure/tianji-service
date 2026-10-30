package top.egon.cola.platform.tianji.agent.server.context;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import top.egon.cola.component.rag.api.RagRetrievalService;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextAuthority;
import top.egon.cola.platform.tianji.agent.infrastructure.rag.RagContextProvider;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RagContextConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RagContextConfiguration.class, TianjiContextAssemblyConfiguration.class);

    @Test
    void source_is_absent_by_default_and_requires_durable_governance_and_an_explicit_business_authority() {
        runner.run(context -> {
            assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(RagContextProvider.class).isEmpty());
            assertFalse(context.getBean(DefaultContextAssembler.class).hasProviders());
        });
        runner.withPropertyValues("stellar.tianji.agent.context.rag.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
        configured().withBean(RagRetrievalService.class, () -> mock(RagRetrievalService.class))
                .withBean(TrustedIssuerRegistry.class, TianshuContextProviderTest::issuers)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void explicit_binding_wires_one_assembler_without_source_access_and_rechecks_current_issuer() {
        var retrieval = mock(RagRetrievalService.class); when(retrieval.retrieve(any())).thenReturn(List.of());
        var authority = mock(RagContextAuthority.class); when(authority.policyFingerprint()).thenReturn("d".repeat(64));
        var request = request(); var actor = request.authorization().actor();
        when(authority.authorize(any(), any())).thenReturn(new RagContextAuthority.Scope(actor.identityFingerprint(), "collection", "embedding-v1", "acl-1"));
        var issuers = TianshuContextProviderTest.issuers();
        configured().withBean(RagRetrievalService.class, () -> retrieval).withBean("tianjiRagSourceAuthority", RagContextAuthority.class, () -> authority)
                .withBean(TrustedIssuerRegistry.class, () -> issuers).run(context -> {
                    assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(DefaultContextAssembler.class).size());
                    assertTrue(context.getBean(DefaultContextAssembler.class).hasProviders());
                    verifyNoInteractions(retrieval); verify(authority, never()).authorize(any(), any());
                    var provider = context.getBean(RagContextProvider.class);
                    assertTrue(provider.provide(request).isEmpty()); verify(retrieval, times(1)).retrieve(any());
                    when(issuers.findByIssuer(TianshuContextProviderTest.ISSUER.toASCIIString())).thenReturn(Optional.empty());
                    assertThrows(AgentLedgerException.class, () -> provider.provide(request));
                    verify(retrieval, times(1)).retrieve(any());
                });
    }

    @Test
    void shared_assembly_retains_published_then_retrieval_order_and_honors_a_host_assembler() {
        var published = mock(TianshuContextProvider.class); var retrieval = mock(RagContextProvider.class);
        when(published.provide(any())).thenReturn(List.of()); when(retrieval.provide(any())).thenReturn(List.of());
        var assembly = new ApplicationContextRunner().withUserConfiguration(TianjiContextAssemblyConfiguration.class)
                .withBean(TianshuContextProvider.class, () -> published).withBean(RagContextProvider.class, () -> retrieval);
        assembly.run(context -> {
            assertNull(context.getStartupFailure()); assertEquals(1, context.getBeansOfType(DefaultContextAssembler.class).size());
            context.getBean(DefaultContextAssembler.class).resolve(request());
            var order = inOrder(published, retrieval); order.verify(published).provide(any()); order.verify(retrieval).provide(any());
        });
        var supplied = new DefaultContextAssembler();
        assembly.withBean(DefaultContextAssembler.class, () -> supplied).run(context -> {
            assertNull(context.getStartupFailure()); assertSame(supplied, context.getBean(DefaultContextAssembler.class));
        });
    }

    private ApplicationContextRunner configured() {
        return runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                "stellar.tianji.agent.context.snapshots.enabled=true", "stellar.tianji.agent.context.rag.enabled=true",
                "stellar.tianji.agent.context.rag.bindings[0].id=kb-source", "stellar.tianji.agent.context.rag.bindings[0].tenant-id=20",
                "stellar.tianji.agent.context.rag.bindings[0].issuer=" + TianshuContextProviderTest.ISSUER,
                "stellar.tianji.agent.context.rag.bindings[0].definition-key=assistant", "stellar.tianji.agent.context.rag.bindings[0].definition-version=2",
                "stellar.tianji.agent.context.rag.bindings[0].collection-id=collection", "stellar.tianji.agent.context.rag.bindings[0].logical-model-name=embedding-v1");
    }
    private static ContextProvider.Request request() {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("private task", null, null), null);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", TianshuContextProviderTest.ISSUER.toASCIIString());
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, Instant.now().plusSeconds(60));
        return new ContextProvider.Request(authorization, snapshot, 1, Duration.ofSeconds(2));
    }
}
