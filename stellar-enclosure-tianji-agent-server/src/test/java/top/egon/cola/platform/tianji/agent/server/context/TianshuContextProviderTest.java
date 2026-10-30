package top.egon.cola.platform.tianji.agent.server.context;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.rpc.tianshu.autoconfigure.DdcRpcProperties;
import top.egon.cola.component.tianshu.api.client.DdcPromptClient;
import top.egon.cola.component.tianshu.api.client.DdcSkillClient;
import top.egon.cola.component.tianshu.error.DdcException;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeDTO;
import top.egon.cola.component.tianshu.model.ai.DdcAiNodeTypeEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiResourceQuery;
import top.egon.cola.component.tianshu.model.ai.DdcPromptResult;
import top.egon.cola.component.tianshu.model.ai.DdcSkillResult;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianshuContextProviderTest {
    static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    static final URI ISSUER = URI.create("https://idp.example/20");
    static final DdcAiClientScope SCOPE = new DdcAiClientScope(101, "xingyuan", "test", "tianji");
    static final String PROMPT_HASH = "20456df72878d3281630add2a2dc2d19baf9cda3f8b9efc8860e5ba00baa7a68";
    static final String SKILL_HASH = "5d572227769bb0863b83e66e4e732513e41995b78700e9688cad60300794de62";
    private final DdcPromptClient prompt = mock(DdcPromptClient.class);
    private final DdcSkillClient skill = mock(DdcSkillClient.class);
    private final TrustedIssuerRegistry issuers = issuers();

    @Test
    void canonical_prompt_and_complete_skill_tree_match_independent_v1_wire_vectors() {
        assertEquals(PROMPT_HASH, TianshuContextProvider.contentHash("Review", List.of()));
        assertEquals(SKILL_HASH, TianshuContextProvider.contentHash(null, nodes()));
    }

    @Test
    void explicit_identity_to_internal_tenant_mapping_and_pinned_selectors_are_preserved_on_revalidation() {
        var provider = provider();
        var request = request(20, ISSUER, true, true);
        var result = provider.provide(request);
        assertEquals(List.of(ContextProvider.Kind.PROMPT, ContextProvider.Kind.SKILL), result.stream().map(ContextProvider.Fragment::kind).toList());
        assertEquals("Review", result.getFirst().text());
        assertTrue(result.getLast().text().contains("SKILL.md"));
        assertTrue(result.getFirst().provenanceId().contains("3:101"));
        provider.revalidate(request, result);
        verify(prompt, times(2)).get(new DdcAiResourceQuery("prompts", "review", 2L, 10L));
        verify(skill, times(2)).get(new DdcAiResourceQuery("skills", "review-skill", 3L, 20L));
        assertEquals(PROMPT_HASH, result.getFirst().sourceSha256());
        assertEquals(NOW, result.getFirst().observedAt());
    }

    @Test
    void wrong_origin_or_unproven_transport_scope_is_rejected_before_remote_reads() {
        var provider = provider();
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> provider.provide(request(21, ISSUER, true, false))).code());
        assertThrows(AgentLedgerException.class, () -> provider.provide(request(20, URI.create("https://other-idp.example/20"), true, false)));
        verify(prompt, never()).get(any()); verify(skill, never()).get(any());
        when(prompt.scope()).thenReturn(new DdcAiClientScope(202, "xingyuan", "test", "tianji"));
        assertThrows(AgentLedgerException.class, () -> provider.provide(request(20, ISSUER, true, false)));
        verify(prompt, never()).get(any());
    }

    @Test
    void historical_prompt_metadata_is_reloaded_under_current_scope_and_revocation_blocks_reuse() throws Exception {
        var provider = provider(); var original = request(20, ISSUER, true, false); var fragment = provider.provide(original).getFirst();
        byte[] text = fragment.modelText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var source = new AgentContextSourceDTO(AgentContextSourceKindEnum.PROMPT, AgentContextRoleEnum.SYSTEM, fragment.sourceId(), fragment.sourceVersion(), fragment.provenanceId(), fragment.sourceSha256(),
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text)), text.length, NOW);
        var authorization = original.authorization();
        var current = new AgentRunAuthorization.Request(authorization.actor(), "new-run", authorization.definitionKey(), authorization.definitionVersion(), authorization.definitionSha256(),
                authorization.requestSha256(), authorization.snapshotSha256(), authorization.policyVersionRef(), true, authorization.deadlineAt());
        var verified = new ContextProvider.Request(current, original.snapshot(), original.leaseEpoch(), original.timeout(), true);
        assertDoesNotThrow(() -> provider.revalidateHistory(verified, List.of(source)));
        verify(prompt, times(2)).get(new DdcAiResourceQuery("prompts", "review", 2L, 10L));
        doThrow(new DdcException(56307, "TIANSHU_AI_SCOPE_FORBIDDEN", "private-denial-detail")).when(prompt).get(any());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> provider.revalidateHistory(verified, List.of(source))).code());
    }

    @Test
    void response_identity_version_hash_and_actual_text_are_all_checked() {
        var provider = provider();
        List<Consumer<DdcPromptResult>> changes = List.of(value -> value.setResourceId(11L), value -> value.setResourceKey("other"),
                value -> value.setVersionNo(3L), value -> value.setContentSha256("a".repeat(64)), value -> value.setTemplate("tampered"),
                value -> value.setVersionId(null));
        for (var change : changes) {
            var response = prompt(); change.accept(response); when(prompt.get(any())).thenReturn(response);
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                    () -> provider.provide(request(20, ISSUER, true, false))).code());
        }
    }

    @Test
    void revocation_or_an_immutable_version_identity_change_prevents_cached_reuse_and_unknown_errors_are_sanitized() {
        var provider = provider();
        var request = request(20, ISSUER, true, false);
        var previous = provider.provide(request);
        when(prompt.get(any())).thenReturn(prompt().setVersionId(12L));
        assertThrows(AgentLedgerException.class, () -> provider.revalidate(request, previous));
        doThrow(new DdcException(56307, "TIANSHU_AI_SCOPE_FORBIDDEN", "private-denial-detail")).when(prompt).get(any());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> provider.revalidate(request, previous)).code());
        doThrow(new IllegalStateException("private-transport-detail")).when(prompt).get(any());
        var failure = assertThrows(AgentLedgerException.class, () -> provider.revalidate(request, previous));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
        assertFalse(failure.toString().contains("private-transport-detail")); assertNull(failure.getCause());
    }

    @Test
    void missing_dependency_selectors_do_not_create_requests_and_short_deadlines_do_not_start_a_larger_rpc() {
        var provider = provider();
        assertTrue(provider.provide(request(21, ISSUER, false, false)).isEmpty());
        var original = request(20, ISSUER, true, false);
        var shortRequest = new ContextProvider.Request(original.authorization(), original.snapshot(), 1, Duration.ofMillis(200));
        assertThrows(AgentLedgerException.class, () -> provider.provide(shortRequest));
        verify(prompt, never()).get(any());
    }

    @Test
    void direct_provider_calls_cannot_hold_a_database_transaction_during_remote_source_access() {
        var provider = provider();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> provider.provide(request(20, ISSUER, true, false)));
            assertThrows(IllegalStateException.class, () -> provider.revalidate(request(20, ISSUER, true, false), List.of()));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verify(prompt, never()).get(any()); verify(skill, never()).get(any());
    }

    @Test
    void concurrent_reads_have_no_queue_and_release_capacity_after_completion() throws Exception {
        var provider = provider();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(prompt.get(any())).thenAnswer(call -> { started.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException(); return prompt(); });
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> provider.provide(request(20, ISSUER, true, false)));
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertEquals(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, assertThrows(AgentLedgerException.class,
                        () -> provider.provide(request(20, ISSUER, true, false))).code());
            } finally { release.countDown(); }
            assertEquals(1, first.get(2, TimeUnit.SECONDS).size());
            assertEquals(1, provider.provide(request(20, ISSUER, true, false)).size());
        }
    }

    private TianshuContextProvider provider() {
        when(prompt.scope()).thenReturn(SCOPE); when(skill.scope()).thenReturn(SCOPE);
        when(prompt.get(any())).thenReturn(prompt());
        when(skill.get(any())).thenReturn(new DdcSkillResult().setResourceId(20L).setResourceKey("review-skill").setVersionId(21L)
                .setVersionNo(3L).setContentSha256(SKILL_HASH).setFiles(nodes()));
        return new TianshuContextProvider(properties(), Map.of("library", new TianshuContextProvider.Ports(SCOPE, prompt, skill, Duration.ofSeconds(1), 1)),
                issuers, Clock.fixed(NOW, ZoneOffset.UTC));
    }
    static TianshuContextProperties properties() {
        var rpc = new DdcRpcProperties(); rpc.setTarget("localhost:19080"); rpc.setDefaultTimeout(Duration.ofSeconds(1)); rpc.setConnectTimeout(Duration.ofMillis(100));
        rpc.getTls().setEnabled(true); rpc.getTls().setDevelopmentPlaintext(false);
        return new TianshuContextProperties(List.of(new TianshuContextProperties.Connection("library", 101, "xingyuan", "test", "tianji", rpc, 1)),
                List.of(new TianshuContextProperties.Binding(20, ISSUER, "library", ContextProvider.Kind.PROMPT, "fixed", "prompts", "review", 10),
                        new TianshuContextProperties.Binding(20, ISSUER, "library", ContextProvider.Kind.SKILL, "fixed-skill", "skills", "review-skill", 20)));
    }
    static TrustedIssuerRegistry issuers() {
        var registry = mock(TrustedIssuerRegistry.class);
        when(registry.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        return registry;
    }
    private static DdcPromptResult prompt() {
        return new DdcPromptResult().setResourceId(10L).setResourceKey("review").setVersionId(11L).setVersionNo(2L).setContentSha256(PROMPT_HASH).setTemplate("Review");
    }
    private static List<DdcAiNodeDTO> nodes() {
        return List.of(DdcAiNodeDTO.builder().path("").nodeType(DdcAiNodeTypeEnum.DIRECTORY).build(),
                DdcAiNodeDTO.builder().path("SKILL.md").nodeType(DdcAiNodeTypeEnum.FILE).content("Review").build());
    }
    static ContextProvider.Request request(long tenant, URI issuer, boolean prompt, boolean skill) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(prompt ? List.of(new DefinitionResourceRef("fixed", 2, PROMPT_HASH)) : List.of(),
                        skill ? List.of(new DefinitionResourceRef("fixed-skill", 3, SKILL_HASH)) : List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("private-input", null, null), null);
        var actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER, "alice", issuer.toString());
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "policy-v1", false, NOW.plusSeconds(60));
        return new ContextProvider.Request(authorization, snapshot, 1, Duration.ofSeconds(10));
    }
}
