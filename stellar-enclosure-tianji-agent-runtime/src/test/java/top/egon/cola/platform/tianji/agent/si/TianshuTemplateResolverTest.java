package top.egon.cola.platform.tianji.agent.si;

import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies runtime resolution of immutable atomic Agent bundles from Tianshu SI descriptors.
 */
class TianshuTemplateResolverTest {

    private static final String SHA = "c".repeat(64);

    @Test
    void shouldAssembleImmutableExecutionBundleAndMaskSecrets() {
        DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
        TianshuTemplateResolver resolver = new TianshuTemplateResolver(client);

        DdcSiResourceDescriptor agent = DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-planner")
                .displayName("Order Planner")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(3)
                .digestSha256(SHA)
                .specJson("""
                        {
                          "modelKey": "gpt-6-luna",
                          "runtime": {"maxSteps": 6, "timeoutSeconds": 45},
                          "systemInstruction": "Always verify order state first."
                        }
                        """)
                .references(List.of(
                        DdcSiReferenceDescriptor.builder()
                                .targetType(DdcSiReferenceTargetTypeEnum.PROMPT)
                                .targetKey("order-system-prompt")
                                .pinnedVersionNo(2)
                                .required(true)
                                .build(),
                        DdcSiReferenceDescriptor.builder()
                                .targetType(DdcSiReferenceTargetTypeEnum.SKILL)
                                .targetKey("refund-policy-skill")
                                .pinnedVersionNo(null)
                                .required(true)
                                .build(),
                        DdcSiReferenceDescriptor.builder()
                                .targetType(DdcSiReferenceTargetTypeEnum.MCP)
                                .targetKey("erp-mcp")
                                .pinnedVersionNo(1)
                                .required(true)
                                .build(),
                        DdcSiReferenceDescriptor.builder()
                                .targetType(DdcSiReferenceTargetTypeEnum.SKILL)
                                .targetKey("optional-audit-skill")
                                .pinnedVersionNo(1)
                                .required(false)
                                .build()))
                .build();

        when(client.resolveResource(1001L, "ai", "core", "prod", "order-svc", DdcSiKindEnum.AGENT, "order-planner", null))
                .thenReturn(Optional.of(agent));
        when(client.resolveResource(1001L, "ai", "core", "prod", "order-svc", DdcSiKindEnum.PROMPT, "order-system-prompt", 2))
                .thenReturn(Optional.of(published(DdcSiKindEnum.PROMPT, "order-system-prompt", 2, "{\"promptText\":\"You check ERP safely.\"}")));
        when(client.resolveResource(1001L, "ai", "core", "prod", "order-svc", DdcSiKindEnum.SKILL, "refund-policy-skill", null))
                .thenReturn(Optional.of(published(DdcSiKindEnum.SKILL, "refund-policy-skill", 4, "{\"skillMarkdown\":\"# Refund steps\"}")));
        when(client.resolveResource(1001L, "ai", "core", "prod", "order-svc", DdcSiKindEnum.MCP, "erp-mcp", 1))
                .thenReturn(Optional.of(published(DdcSiKindEnum.MCP, "erp-mcp", 1,
                        "{\"transport\":\"STREAMABLE_HTTP\",\"endpoint\":\"https://mcp.internal/erp\",\"credentialRef\":\"vault://erp-secret\",\"headers\":{\"X-Token\":\"should-never-leak-raw\"}}")));
        when(client.resolveResource(1001L, "ai", "core", "prod", "order-svc", DdcSiKindEnum.SKILL, "optional-audit-skill", 1))
                .thenReturn(Optional.empty());

        TianshuTemplateResolver.ResolvedAgentExecutionBundle bundle = resolver.resolveBundle(
                1001L, "ai", "core", "prod", "order-svc", "order-planner", null);

        assertThat(bundle.agentKey()).isEqualTo("order-planner");
        assertThat(bundle.versionNo()).isEqualTo(3);
        assertThat(bundle.modelKey()).isEqualTo("gpt-6-luna");
        assertThat(bundle.maxSteps()).isEqualTo(6);
        assertThat(bundle.timeoutSeconds()).isEqualTo(45);
        assertThat(bundle.prompts()).hasSize(1);
        assertThat(bundle.prompts().getFirst().versionNo()).isEqualTo(2);
        assertThat(bundle.skills()).hasSize(1);
        assertThat(bundle.skills().getFirst().versionNo()).isEqualTo(4);
        assertThat(bundle.mcpServers()).hasSize(1);
        assertThat(bundle.mcpServers().getFirst().credentialRef()).isEqualTo("vault://erp-secret");
        assertThat(bundle.toString()).doesNotContain("should-never-leak-raw");
    }

    @Test
    void shouldRejectDraftPrimaryAgentOrMissingRequiredDependency() {
        DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
        TianshuTemplateResolver resolver = new TianshuTemplateResolver(client);

        DdcSiResourceDescriptor draftAgent = DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-planner")
                .displayName("Draft Agent")
                .status(DdcSiLifecycleStatusEnum.DRAFT)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(1)
                .digestSha256(SHA)
                .build();

        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "order-planner", 1))
                .thenReturn(Optional.of(draftAgent));

        assertThatThrownBy(() -> resolver.resolveBundle(1001L, "ai", "core", "prod", null, "order-planner", 1))
                .isInstanceOf(IllegalStateException.class);

        DdcSiResourceDescriptor activeWithBrokenDep = DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("order-planner")
                .displayName("Active Agent")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(2)
                .digestSha256(SHA)
                .specJson("{\"modelKey\":\"gpt-6-luna\"}")
                .references(List.of(DdcSiReferenceDescriptor.builder()
                        .targetType(DdcSiReferenceTargetTypeEnum.PROMPT)
                        .targetKey("missing-prompt")
                        .required(true)
                        .build()))
                .build();

        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "order-planner", 2))
                .thenReturn(Optional.of(activeWithBrokenDep));
        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.PROMPT, "missing-prompt", null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveBundle(1001L, "ai", "core", "prod", null, "order-planner", 2))
                .isInstanceOf(IllegalStateException.class);
    }

    private static DdcSiResourceDescriptor published(DdcSiKindEnum kind, String key, int versionNo, String specJson) {
        return DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("ai")
                .namespaceCode("core")
                .envCode("prod")
                .kind(kind)
                .resourceKey(key)
                .displayName(key)
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(versionNo)
                .digestSha256(SHA)
                .specJson(specJson)
                .build();
    }
}
