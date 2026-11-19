package top.egon.cola.platform.tianji.agent.si;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO;
import top.egon.cola.component.tianshu.model.si.DdcSiExecutionBundleVO.MaterialVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses real immutable manifest/material fixtures; custom malformed ports never establish execution authority. */
class TianshuTemplateResolverTest {
    private static final String SHA = "c".repeat(64);
    private static final String OWNER = "agent-owner";
    private static final String CALLER = "order-svc";
    private static final String ROOT_BODY = "{\"modelKey\":\"fixture-model\",\"systemInstruction\":\"private-instruction\",\"runtime\":{\"maxSteps\":6,\"timeoutSeconds\":45}}";
    private final DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
    private final TianshuTemplateResolver resolver = new TianshuTemplateResolver(client);

    @ParameterizedTest
    @CsvSource(value = {"NULL|true", "4|true", "5|false"}, delimiter = '|', nullValues = "NULL")
    void rejectsAnAuthorityResponseWithoutTheRequestedPinnedVersionAndDigest(Integer publishedVersion, boolean hasDigest) {
        DdcSiResourceDescriptor descriptor = DdcSiResourceDescriptor.builder().tenantId(1001L).bizCode("ai")
                .namespaceCode("core").envCode("prod").kind(DdcSiKindEnum.AGENT).resourceKey("order-planner")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED).sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(publishedVersion).digestSha256(hasDigest ? SHA : null)
                .specJson("{\"modelKey\":\"fixture-model\"}").build();
        when(client.resolveResource(1001L, "ai", "core", "prod", CALLER, DdcSiKindEnum.AGENT, "order-planner", 5))
                .thenReturn(Optional.of(descriptor));
        assertThatThrownBy(() -> resolver.resolveBundle(1001L, "ai", "core", "prod", CALLER, "order-planner", 5))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(client);
    }

    @Test
    void assemblesTheCompleteRecursiveGraphWithIndependentOwnerAndCaller() {
        var response = fixture(5L);
        supplied(response);
        var bundle = resolve(5L);
        assertThat(bundle.agentKey()).isEqualTo("order-planner");
        assertThat(bundle.versionNo()).isEqualTo(5L);
        assertThat(bundle.modelKey()).isEqualTo("fixture-model");
        assertThat(bundle.maxSteps()).isEqualTo(6);
        assertThat(bundle.timeoutSeconds()).isEqualTo(45);
        assertThat(bundle.bizCode()).isEqualTo("ai");
        assertThat(bundle.namespaceCode()).isEqualTo("core");
        assertThat(bundle.envCode()).isEqualTo("prod");
        assertThat(bundle.ownerAppCode()).isEqualTo(OWNER);
        assertThat(bundle.callerAppCode()).isEqualTo(CALLER);
        assertThat(bundle.dependencyManifest()).isEqualTo(response.manifest());
        assertThat(bundle.resources()).hasSize(5);
        assertThat(bundle.resources().stream().map(TianshuTemplateResolver.ResolvedResourceVO::key))
                .containsExactly("order-planner", "nested-agent", "order-system-prompt", "refund-skill", "erp-mcp");
        assertThat(bundle.prompts()).hasSize(1);
        assertThat(bundle.prompts().getFirst().content()).isEqualTo("private-prompt");
        assertThat(bundle.skills().getFirst().markdown()).isEqualTo("# Private refund steps");
        assertThat(bundle.mcpServers().getFirst().credentialRef()).isEqualTo("vault://erp-secret");
        assertThat(bundle.resources().get(2).ownerAppCode()).isEqualTo("shared-library-owner");
        assertThat(bundle.resources().get(3).ownerAppCode()).isNull();
        verify(client).resolveExecutionBundle(1001L, "ai", "core", "prod", OWNER, CALLER,
                DdcSiKindEnum.AGENT, "order-planner", 5L);
        verifyNoMoreInteractions(client);
    }

    @Test
    void lineageOnlySourceDoesNotBecomeAnExecutionDependencyOrRequireAnExtraRead() {
        var response = fixture(5L);
        var lineage = identity(100, 99, 2, DdcSiKindEnum.AGENT, "{\"modelKey\":\"old\"}", "test");
        var manifest = DdcSiDependencyManifestVO.create(response.manifest().root(), lineage,
                response.manifest().nodes(), response.manifest().edges());
        supplied(new DdcSiExecutionBundleVO(response.root(), manifest, response.nodes()));
        var bundle = resolve(5L);
        assertThat(bundle.dependencyManifest().source()).isEqualTo(lineage);
        assertThat(bundle.resources()).hasSize(5).noneMatch(resource -> resource.identity().versionId().equals("99"));
        verify(client, times(1)).resolveExecutionBundle(anyLong(), anyString(), anyString(), anyString(),
                any(), anyString(), any(), anyString(), any());
        verifyNoMoreInteractions(client);
    }

    @Test
    void longVersionIdentityNeverNarrowsToLegacyIntegerOrDefaultsToOne() {
        long exact = (long) Integer.MAX_VALUE + 1;
        supplied(fixture(exact));
        assertThat(resolve(exact).versionNo()).isEqualTo(exact);
    }

    @Test
    void returnedGraphAndSecretSafeStringRepresentationsAreImmutable() {
        supplied(fixture(5L));
        var bundle = resolve(5L);
        assertThatThrownBy(() -> bundle.resources().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> bundle.prompts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> bundle.mcpServers().getFirst().headerNames().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        String diagnostics = bundle + " " + bundle.resources() + bundle.prompts() + bundle.skills() + bundle.mcpServers();
        assertThat(diagnostics).doesNotContain("private-instruction", "private-prompt", "Private refund", "vault://",
                "https://mcp.internal", "non-secret-value");
    }

    @Test
    void emptyAuthorityReplyOrUnavailableAndDeniedNeverFallBackToMetadataOrCachedMaterial() {
        when(client.resolveExecutionBundle(anyLong(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyString(), any()))
                .thenReturn(Optional.empty()).thenThrow(new SecurityException("synthetic-denied"))
                .thenThrow(new IllegalStateException("synthetic-authority-unavailable"));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
        assertThatThrownBy(() -> resolve(5L)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> resolve(5L)).hasMessage("synthetic-authority-unavailable");
        verify(client, times(3)).resolveExecutionBundle(anyLong(), anyString(), anyString(), anyString(), any(), anyString(), any(), anyString(), any());
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant", "biz", "namespace", "env", "owner", "key", "version", "root-kind"})
    void rejectsAnyRootTupleMismatchEvenFromACustomCatalogPort(String field) {
        var valid = fixture(5L);
        MaterialVO root = material(valid.root(), field);
        var manifest = valid.manifest();
        if (field.equals("version") || field.equals("root-kind")) {
            manifest = DdcSiDependencyManifestVO.create(root.identity(), null, valid.manifest().nodes(), valid.manifest().edges());
        }
        supplied(unsafe(root, manifest, valid.nodes()));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "extra", "wrong-body", "wrong-owner-scope", "node-identity"})
    void requiresExactlyEveryFrozenNodeAndItsUntamperedHistoricalBody(String defect) {
        var response = fixture(5L);
        var nodes = new ArrayList<>(response.nodes());
        switch (defect) {
            case "missing" -> nodes.removeLast();
            case "duplicate" -> nodes.add(nodes.getFirst());
            case "extra" -> nodes.add(new MaterialVO(identity(999, 999, 1, DdcSiKindEnum.PROMPT,
                    "{\"promptText\":\"extra\"}", "prod"), "ai", "core", null, "extra", "{\"promptText\":\"extra\"}"));
            case "wrong-body" -> nodes.set(0, altered(nodes.getFirst(), old -> old + " "));
            case "wrong-owner-scope" -> nodes.set(0, material(nodes.getFirst(), "namespace"));
            case "node-identity" -> nodes.set(0, material(nodes.getFirst(), "tenant"));
            default -> throw new AssertionError();
        }
        supplied(unsafe(response.root(), response.manifest(), nodes));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    @Test
    void rejectsRootBodyAndClosureDigestTamperingRatherThanTrustingAWellNamedField() {
        var valid = fixture(5L);
        supplied(unsafe(altered(valid.root(), body -> body + " "), valid.manifest(), valid.nodes()));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
        var fake = mock(DdcSiDependencyManifestVO.class);
        when(fake.schemaVersion()).thenReturn("1"); when(fake.root()).thenReturn(valid.manifest().root());
        when(fake.nodes()).thenReturn(valid.manifest().nodes()); when(fake.edges()).thenReturn(valid.manifest().edges());
        when(fake.closureDigest()).thenReturn("0".repeat(64));
        supplied(unsafe(valid.root(), fake, valid.nodes()));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    @Test
    void rejectsDisconnectedOrCyclicRootTopologyEvenWhenCustomPortSkipsTheManifestConstructor() {
        var valid = fixture(5L);
        var fake = mock(DdcSiDependencyManifestVO.class);
        when(fake.schemaVersion()).thenReturn("1"); when(fake.root()).thenReturn(valid.manifest().root());
        when(fake.nodes()).thenReturn(valid.manifest().nodes()); when(fake.edges()).thenReturn(List.of());
        when(fake.closureDigest()).thenReturn(valid.manifest().closureDigest());
        supplied(unsafe(valid.root(), fake, valid.nodes()));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    @Test
    void bodyMustBeAnObjectAndRuntimeIntegersMustNotBeCoercedOrTruncated() {
        for (String body : List.of("[]", "{\"modelKey\":\"fixture\",\"modelKey\":\"duplicate\"}",
                "{\"modelKey\":\"fixture\",\"runtime\":{\"maxSteps\":1.5}}")) {
            MaterialVO root = unsafeMaterial(identity(100, 101, 5, DdcSiKindEnum.AGENT, body, "prod"),
                    "ai", "core", OWNER, "order-planner", body);
            var manifest = DdcSiDependencyManifestVO.create(root.identity(), null, List.of(), List.of());
            supplied(unsafe(root, manifest, List.of()));
            assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
        }
    }

    @Test
    void invalidSelectorsAndAmbientPersistenceTransactionsFailBeforeAnyRemoteCall() {
        assertThatThrownBy(() -> resolve(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> resolver.resolveBundle(1001, "ai", "core", "prod", OWNER, "", "order-planner", 5L))
                .isInstanceOf(IllegalArgumentException.class);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_RESOLUTION_TRANSACTION_FORBIDDEN");
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(client);
    }

    @Test
    void namespaceOwnedRootRemainsDistinctFromTheVerifiedCallerAndOtherNodeOwners() {
        var response = fixture(5L);
        MaterialVO old = response.root();
        MaterialVO root = new MaterialVO(old.identity(), old.bizCode(), old.namespaceCode(), null,
                old.resourceKey(), old.specJson());
        supplied(new DdcSiExecutionBundleVO(root, response.manifest(), response.nodes()));
        var result = resolver.resolveBundle(1001L, "ai", "core", "prod", null, CALLER, "order-planner", 5L);
        assertThat(result.ownerAppCode()).isNull();
        assertThat(result.callerAppCode()).isEqualTo(CALLER);
        verify(client).resolveExecutionBundle(1001L, "ai", "core", "prod", null, CALLER,
                DdcSiKindEnum.AGENT, "order-planner", 5L);
    }

    @Test
    void boundedCustomPortRejectsNodeCountAndUtf8BodyOverflowBeforeAnyExecutionProjection() {
        var valid = fixture(5L);
        supplied(unsafe(valid.root(), valid.manifest(), java.util.Collections.nCopies(257, valid.nodes().getFirst())));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
        String hugeBody = "{\"modelKey\":\"" + "界".repeat(400_000) + "\"}";
        var root = unsafeMaterial(identity(100, 101, 5, DdcSiKindEnum.AGENT, hugeBody, "prod"),
                "ai", "core", OWNER, "order-planner", hugeBody);
        var manifest = DdcSiDependencyManifestVO.create(root.identity(), null, List.of(), List.of());
        supplied(unsafe(root, manifest, List.of()));
        assertThatThrownBy(() -> resolve(5L)).hasMessage("TIANJI_SI_EXECUTION_BUNDLE_UNAVAILABLE");
    }

    private TianshuTemplateResolver.ResolvedAgentExecutionBundle resolve(long version) {
        return resolver.resolveBundle(1001L, "ai", "core", "prod", OWNER, CALLER, "order-planner", version);
    }

    private void supplied(DdcSiExecutionBundleVO response) {
        doReturn(Optional.of(response)).when(client).resolveExecutionBundle(anyLong(), anyString(), anyString(),
                anyString(), any(), anyString(), any(), anyString(), any());
    }

    private static DdcSiExecutionBundleVO fixture(long version) {
        MaterialVO root = new MaterialVO(identity(100, 101, version, DdcSiKindEnum.AGENT, ROOT_BODY, "prod"),
                "ai", "core", OWNER, "order-planner", ROOT_BODY);
        List<MaterialVO> nodes = List.of(
                node(200, 102, 3, DdcSiKindEnum.AGENT, "nested-agent", OWNER, "{\"modelKey\":\"nested\"}"),
                node(300, 103, 2, DdcSiKindEnum.PROMPT, "order-system-prompt", "shared-library-owner", "{\"promptText\":\"private-prompt\"}"),
                node(400, 104, 4, DdcSiKindEnum.SKILL, "refund-skill", null, "{\"skillMarkdown\":\"# Private refund steps\"}"),
                node(500, 105, 1, DdcSiKindEnum.MCP, "erp-mcp", OWNER,
                        "{\"transport\":\"STREAMABLE_HTTP\",\"endpoint\":\"https://mcp.internal/erp\",\"credentialRef\":\"vault://erp-secret\",\"headers\":{\"X-App\":\"non-secret-value\"}}"));
        var manifest = DdcSiDependencyManifestVO.create(root.identity(), null, nodes.stream().map(MaterialVO::identity).toList(),
                List.of(new EdgeVO("101", "102"), new EdgeVO("101", "105"), new EdgeVO("102", "103"),
                        new EdgeVO("102", "104"), new EdgeVO("105", "103")));
        return new DdcSiExecutionBundleVO(root, manifest, nodes);
    }

    private static MaterialVO node(long resource, long versionId, long no, DdcSiKindEnum kind, String key, String owner, String body) {
        return new MaterialVO(identity(resource, versionId, no, kind, body, "prod"), "ai", "core", owner, key, body);
    }

    private static VersionIdentityVO identity(long resource, long versionId, long no, DdcSiKindEnum kind, String body, String env) {
        return new VersionIdentityVO("1001", Long.toString(resource), Long.toString(versionId), Long.toString(no), kind, env, sha(body));
    }

    private static MaterialVO altered(MaterialVO old, UnaryOperator<String> body) {
        return unsafeMaterial(old.identity(), old.bizCode(), old.namespaceCode(), old.ownerAppCode(), old.resourceKey(), body.apply(old.specJson()));
    }

    private static MaterialVO material(MaterialVO original, String field) {
        var old = original.identity();
        VersionIdentityVO identity = switch (field) {
            case "tenant" -> new VersionIdentityVO("2002", old.resourceId(), old.versionId(), old.versionNo(), old.kind(), old.envCode(), old.digestSha256());
            case "env" -> new VersionIdentityVO(old.tenantId(), old.resourceId(), old.versionId(), old.versionNo(), old.kind(), "test", old.digestSha256());
            case "version" -> new VersionIdentityVO(old.tenantId(), old.resourceId(), old.versionId(), "4", old.kind(), old.envCode(), old.digestSha256());
            case "root-kind" -> new VersionIdentityVO(old.tenantId(), old.resourceId(), old.versionId(), old.versionNo(), DdcSiKindEnum.PROMPT, old.envCode(), old.digestSha256());
            default -> old;
        };
        return new MaterialVO(identity, field.equals("biz") ? "other" : original.bizCode(),
                field.equals("namespace") ? "other" : original.namespaceCode(),
                field.equals("owner") ? "unauthorized-owner" : original.ownerAppCode(),
                field.equals("key") ? "other-key" : original.resourceKey(), original.specJson());
    }

    private static MaterialVO unsafeMaterial(VersionIdentityVO identity, String biz, String namespace,
            String owner, String key, String body) {
        var material = mock(MaterialVO.class);
        when(material.identity()).thenReturn(identity); when(material.bizCode()).thenReturn(biz);
        when(material.namespaceCode()).thenReturn(namespace); when(material.ownerAppCode()).thenReturn(owner);
        when(material.resourceKey()).thenReturn(key); when(material.specJson()).thenReturn(body);
        return material;
    }

    /** A deliberately malformed custom port response bypasses DTO validation to exercise the consumer boundary itself. */
    private static DdcSiExecutionBundleVO unsafe(MaterialVO root, DdcSiDependencyManifestVO manifest, List<MaterialVO> nodes) {
        var response = mock(DdcSiExecutionBundleVO.class);
        when(response.root()).thenReturn(root); when(response.manifest()).thenReturn(manifest); when(response.nodes()).thenReturn(nodes);
        return response;
    }

    private static String sha(String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new AssertionError(impossible);
        }
    }
}
