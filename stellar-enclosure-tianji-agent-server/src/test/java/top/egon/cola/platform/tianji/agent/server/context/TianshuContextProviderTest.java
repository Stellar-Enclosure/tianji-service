package top.egon.cola.platform.tianji.agent.server.context;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.rpc.consumer.reference.RpcServiceTargetVO;
import top.egon.cola.component.rpc.context.identity.RpcProcessIdentity;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.component.tianshu.model.ai.DdcAiClientScope;
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
    static final RpcServiceTargetVO TARGET = new RpcServiceTargetVO(
            "si-context", 1L, "platform", "tianshu-si", 19080, Set.of(TianshuContextConfiguration.METHOD));
    static final RpcProcessIdentity PROCESS = new RpcProcessIdentity("tianji-agent-server", "test", "localhost", 10L, "agent-test");
    static final TianshuContextConfiguration.CatalogAuthorityVO AUTHORITY =
            new TianshuContextConfiguration.CatalogAuthorityVO(TARGET, PROCESS, "20");
    private static final String PROMPT_SPEC = "{\"promptText\":\"Review\"}";
    private static final String SKILL_SPEC = "{\"files\":[{\"path\":\"\",\"nodeType\":\"DIRECTORY\",\"content\":null},"
            + "{\"path\":\"SKILL.md\",\"nodeType\":\"FILE\",\"content\":\"Review\"}]}";
    static final String PROMPT_HASH = sha256(PROMPT_SPEC);
    static final String SKILL_HASH = sha256(SKILL_SPEC);
    private final DdcSiCatalogClient catalog = mock(DdcSiCatalogClient.class);
    private final TrustedIssuerRegistry issuers = issuers();

    @Test
    void contextTransportAcceptsTheFixedSiCatalogInsteadOfLegacyAiClientHandles() throws Exception {
        assertNotNull(TianshuContextProvider.Ports.class.getDeclaredConstructor(
                DdcAiClientScope.class, DdcSiCatalogClient.class, Duration.class, int.class,
                TianshuContextConfiguration.CatalogAuthorityVO.class));
    }

    @Test
    void explicit_identity_to_internal_tenant_mapping_and_pinned_selectors_are_preserved_on_revalidation() {
        var provider = provider();
        var request = request(20, ISSUER, true, true);
        var result = provider.provide(request);
        assertEquals(List.of(ContextProvider.Kind.PROMPT, ContextProvider.Kind.SKILL), result.stream().map(ContextProvider.Fragment::kind).toList());
        assertEquals("Review", result.getFirst().text());
        assertTrue(result.getLast().text().contains("SKILL.md"));
        assertTrue(result.getFirst().provenanceId().startsWith("tianshu-si:v1:"));
        provider.revalidate(request, result);
        verify(catalog, times(2)).resolveExecutionResource(101L, "xingyuan", "prompts", "test", "prompt-owner", "tianji", DdcSiKindEnum.PROMPT, "review", 2L);
        verify(catalog, times(2)).resolveExecutionResource(101L, "xingyuan", "skills", "test", null, "tianji", DdcSiKindEnum.SKILL, "review-skill", 3L);
        assertEquals(PROMPT_HASH, result.getFirst().sourceSha256());
        assertEquals(NOW, result.getFirst().observedAt());
    }

    @Test
    void wrong_actor_tenant_or_issuer_is_rejected_before_remote_reads() {
        var provider = provider();
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> provider.provide(request(21, ISSUER, true, false))).code());
        assertThrows(AgentLedgerException.class, () -> provider.provide(request(20, URI.create("https://other-idp.example/20"), true, false)));
        verifyNoInteractions(catalog);
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
        verify(catalog, times(2)).resolveExecutionResource(101L, "xingyuan", "prompts", "test", "prompt-owner", "tianji", DdcSiKindEnum.PROMPT, "review", 2L);
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenThrow(Status.PERMISSION_DENIED
                .withDescription("private-denial-detail").asRuntimeException());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> provider.revalidateHistory(verified, List.of(source))).code());
    }

    @Test
    void response_identity_version_hash_and_actual_text_are_all_checked() {
        var provider = provider();
        List<Consumer<DdcSiResourceDescriptor>> changes = List.of(value -> value.setResourceId(11L),
                value -> value.setResourceKey("other"), value -> value.setTenantId(102L), value -> value.setEnvCode("foreign"),
                value -> value.setOwnerAppCode("foreign"), value -> value.setNamespaceCode("other"),
                value -> value.setResolvedVersionNo(3L), value -> value.setDigestSha256("a".repeat(64)),
                value -> value.setSpecJson("{}"), value -> value.setResolvedVersionId(null),
                value -> value.setDependencyManifest(null), value -> value.setStatus(DdcSiLifecycleStatusEnum.ARCHIVED));
        for (var change : changes) {
            var response = prompt(11L);
            change.accept(response);
            when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                    eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenReturn(Optional.of(response));
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                    () -> provider.provide(request(20, ISSUER, true, false))).code());
        }
    }

    @Test
    void legacyAiDigestCannotBeSubstitutedForThePublishedSiBodyDigest() {
        var originalAiDigest = "20456df72878d3281630add2a2dc2d19baf9cda3f8b9efc8860e5ba00baa7a68";
        var original = request(20, ISSUER, true, false, originalAiDigest);

        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                assertThrows(AgentLedgerException.class, () -> provider().provide(original)).code());
        verify(catalog).resolveExecutionResource(101L, "xingyuan", "prompts", "test", "prompt-owner", "tianji",
                DdcSiKindEnum.PROMPT, "review", 2L);
    }

    @Test
    void revocation_or_an_immutable_version_identity_change_prevents_cached_reuse_and_unknown_errors_are_sanitized() {
        var provider = provider();
        var request = request(20, ISSUER, true, false);
        var previous = provider.provide(request);
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenReturn(Optional.of(prompt(12L)));
        assertThrows(AgentLedgerException.class, () -> provider.revalidate(request, previous));
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenThrow(Status.PERMISSION_DENIED
                .withDescription("private-denial-detail").asRuntimeException());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> provider.revalidate(request, previous)).code());
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenThrow(new IllegalStateException("private-transport-detail"));
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
        verifyNoInteractions(catalog);
    }

    @Test
    void direct_provider_calls_cannot_hold_a_database_transaction_during_remote_source_access() {
        var provider = provider();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> provider.provide(request(20, ISSUER, true, false)));
            assertThrows(IllegalStateException.class, () -> provider.revalidate(request(20, ISSUER, true, false), List.of()));
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(catalog);
    }

    @Test
    void concurrent_reads_have_no_queue_and_release_capacity_after_completion() throws Exception {
        var provider = provider();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenAnswer(call -> {
                    started.countDown();
                    if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException();
                    return Optional.of(prompt(11L));
                });
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
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.PROMPT), anyString(), any())).thenReturn(Optional.of(prompt(11L)));
        when(catalog.resolveExecutionResource(anyLong(), anyString(), anyString(), anyString(), any(), anyString(),
                eq(DdcSiKindEnum.SKILL), anyString(), any())).thenReturn(Optional.of(
                snapshot(DdcSiKindEnum.SKILL, "skills", null, "review-skill", 20L, 21L, 3L, SKILL_SPEC)));
        return new TianshuContextProvider(properties(), Map.of("library", new TianshuContextProvider.Ports(
                SCOPE, catalog, Duration.ofSeconds(1), 1, AUTHORITY)), issuers, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static TianshuContextProperties properties() {
        var connection = new TianshuContextProperties.Connection("library", 101, "xingyuan", "test", "tianji",
                20, TARGET.bindingId(), TARGET.version(), Duration.ofSeconds(1), 1);
        return new TianshuContextProperties(true, List.of(connection),
                List.of(new TianshuContextProperties.Binding(20, ISSUER, "library", ContextProvider.Kind.PROMPT,
                                "fixed", "prompts", "prompt-owner", "review", 10),
                        new TianshuContextProperties.Binding(20, ISSUER, "library", ContextProvider.Kind.SKILL,
                                "fixed-skill", "skills", null, "review-skill", 20)));
    }

    static TrustedIssuerRegistry issuers() {
        var registry = mock(TrustedIssuerRegistry.class);
        when(registry.findByIssuer(ISSUER.toString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1)));
        return registry;
    }
    static DdcSiResourceDescriptor prompt(long versionId) {
        return snapshot(DdcSiKindEnum.PROMPT, "prompts", "prompt-owner", "review", 10L, versionId, 2L, PROMPT_SPEC);
    }

    private static DdcSiResourceDescriptor snapshot(DdcSiKindEnum kind, String namespace, String owner, String key,
            long id, long versionId, long version, String spec) {
        String digest = sha256(spec);
        var root = new DdcSiDependencyManifestVO.VersionIdentityVO("101", Long.toString(id), Long.toString(versionId),
                Long.toString(version), kind, "test", digest);
        return new DdcSiResourceDescriptor().setTenantId(101L).setBizCode("xingyuan").setEnvCode("test")
                .setNamespaceCode(namespace).setOwnerAppCode(owner).setKind(kind).setResourceKey(key).setResourceId(id)
                .setResolvedVersionId(versionId).setResolvedVersionNo(version).setActiveVersionNo(9)
                .setDigestSha256(digest).setSpecJson(spec).setStatus(DdcSiLifecycleStatusEnum.PUBLISHED)
                .setSourceType(DdcSiSourceTypeEnum.CONSOLE)
                .setDependencyManifest(DdcSiDependencyManifestVO.create(root, null, List.of(), List.of()));
    }

    private static String sha256(String source) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }

    static ContextProvider.Request request(long tenant, URI issuer, boolean prompt, boolean skill) {
        return request(tenant, issuer, prompt, skill, PROMPT_HASH);
    }

    private static ContextProvider.Request request(long tenant, URI issuer, boolean prompt, boolean skill, String promptDigest) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(prompt ? List.of(new DefinitionResourceRef("fixed", 2, promptDigest)) : List.of(),
                        skill ? List.of(new DefinitionResourceRef("fixed-skill", 3, SKILL_HASH)) : List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("private-input", null, null), null);
        var actor = TrustedAgentActor.verified(tenant, TrustedAgentActor.SubjectType.USER, "alice", issuer.toString());
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "policy-v1", false, NOW.plusSeconds(60));
        return new ContextProvider.Request(authorization, snapshot, 1, Duration.ofSeconds(10));
    }
}
