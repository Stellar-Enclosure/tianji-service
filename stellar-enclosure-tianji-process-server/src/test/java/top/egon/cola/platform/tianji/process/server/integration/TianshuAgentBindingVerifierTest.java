package top.egon.cola.platform.tianji.process.server.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodePolicyVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationConverter;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationService;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionAuthority;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseTrustVerifier.TrustAuthority;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Verifies pre-activation verification of atomic Tianshu SI Agent bindings inside Flowable releases.
 */
class TianshuAgentBindingVerifierTest {

    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final ProcessSchemaVO EMPTY_OBJECT =
            new ProcessSchemaVO("object", Map.of(), List.of(), false, null, null, null, null, null, null, null, null);
    private static final NodePolicyVO DEFAULT_POLICY = new NodePolicyVO(
            Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(30),
            new NodePolicyVO.PolicyActionVO("END", null),
            new NodePolicyVO.PolicyActionVO("END", null),
            new NodePolicyVO.PolicyActionVO("END", null),
            new NodePolicyVO.RetryPolicyVO(1, Duration.ZERO, List.of()),
            "BEST_EFFORT_STOP", "WAIT_RECOVERY");

    @Test
    void shouldRejectActivationWhenBoundAgentIsMissingOrDraftOrArchivedOrDigestMismatched() {
        DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
        MockEnvironment env = new MockEnvironment()
                .withProperty("egon.cola.component.tianshu.si.client.biz-code", "ai")
                .withProperty("egon.cola.component.tianshu.si.client.namespace-code", "core")
                .withProperty("egon.cola.component.tianshu.si.client.env-code", "prod")
                .withProperty("egon.cola.component.tianshu.si.client.strict-activation-check", "true");

        TianshuAgentBindingVerifier verifier = new TianshuAgentBindingVerifier(providerOf(client), env);
        AutomationModelSourceVO source = agentFlowSource("orderPlanner", 2, SHA_A);

        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "orderPlanner", 2))
                .thenReturn(Optional.of(DdcSiResourceDescriptor.builder()
                        .tenantId(1001L)
                        .bizCode("ai")
                        .namespaceCode("core")
                        .envCode("prod")
                        .kind(DdcSiKindEnum.AGENT)
                        .resourceKey("orderPlanner")
                        .displayName("Order Planner")
                        .status(DdcSiLifecycleStatusEnum.DRAFT)
                        .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                        .activeVersionNo(2)
                        .digestSha256(SHA_A)
                        .build()));

        assertThatThrownBy(() -> verifier.verifyAndRegisterProcessBindings(1001L, "orderFlow", source))
                .isInstanceOf(ProcessConflictException.class);

        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "orderPlanner", 2))
                .thenReturn(Optional.of(DdcSiResourceDescriptor.builder()
                        .tenantId(1001L)
                        .bizCode("ai")
                        .namespaceCode("core")
                        .envCode("prod")
                        .kind(DdcSiKindEnum.AGENT)
                        .resourceKey("orderPlanner")
                        .displayName("Order Planner")
                        .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                        .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                        .activeVersionNo(2)
                        .digestSha256(SHA_B)
                        .build()));

        assertThatThrownBy(() -> verifier.verifyAndRegisterProcessBindings(1001L, "orderFlow", source))
                .isInstanceOf(ProcessConflictException.class);
    }

    @Test
    void shouldAllowPublishedAgentAndSkipRemoteCallsForPureNonAgentWorkflows() {
        DdcSiCatalogClient client = mock(DdcSiCatalogClient.class);
        MockEnvironment env = new MockEnvironment()
                .withProperty("egon.cola.component.tianshu.si.client.biz-code", "ai")
                .withProperty("egon.cola.component.tianshu.si.client.namespace-code", "core")
                .withProperty("egon.cola.component.tianshu.si.client.env-code", "prod")
                .withProperty("egon.cola.component.tianshu.si.client.strict-activation-check", "true");

        TianshuAgentBindingVerifier verifier = new TianshuAgentBindingVerifier(providerOf(client), env);

        AutomationModelSourceVO humanOnly = humanOnlySource();
        assertThatCode(() -> verifier.verifyAndRegisterProcessBindings(1001L, "humanFlow", humanOnly))
                .doesNotThrowAnyException();
        verifyNoInteractions(client);

        when(client.resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "orderPlanner", 2))
                .thenReturn(Optional.of(DdcSiResourceDescriptor.builder()
                        .tenantId(1001L)
                        .bizCode("ai")
                        .namespaceCode("core")
                        .envCode("prod")
                        .kind(DdcSiKindEnum.AGENT)
                        .resourceKey("orderPlanner")
                        .displayName("Order Planner")
                        .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                        .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                        .activeVersionNo(2)
                        .digestSha256(SHA_A)
                        .build()));

        AutomationModelSourceVO agentSource = agentFlowSource("orderPlanner", 2, SHA_A);
        assertThatCode(() -> verifier.verifyAndRegisterProcessBindings(1001L, "orderFlow", agentSource))
                .doesNotThrowAnyException();
        verify(client).resolveResource(1001L, "ai", "core", "prod", null, DdcSiKindEnum.AGENT, "orderPlanner", 2);

        assertThatCode(() -> verifier.releaseProcessBindings(1001L, "orderFlow"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRespectStrictVsLenientClientAvailabilityPolicy() {
        MockEnvironment strictEnv = new MockEnvironment()
                .withProperty("egon.cola.component.tianshu.si.client.strict-activation-check", "true");
        TianshuAgentBindingVerifier strictVerifier = new TianshuAgentBindingVerifier(providerOf(null), strictEnv);
        AutomationModelSourceVO agentSource = agentFlowSource("orderPlanner", 2, SHA_A);

        assertThatThrownBy(() -> strictVerifier.verifyAndRegisterProcessBindings(1001L, "orderFlow", agentSource))
                .isInstanceOf(ProcessConflictException.class);

        MockEnvironment devEnv = new MockEnvironment()
                .withProperty("egon.cola.component.tianshu.si.client.strict-activation-check", "false");
        TianshuAgentBindingVerifier lenientVerifier = new TianshuAgentBindingVerifier(providerOf(null), devEnv);

        assertThatCode(() -> lenientVerifier.verifyAndRegisterProcessBindings(1001L, "orderFlow", agentSource))
                .doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveLegacyNineArgConstructorAndWireTenArgDeactivationHook() {
        TianshuAgentBindingVerifier verifierMock = mock(TianshuAgentBindingVerifier.class);
        AutomationActivationService legacyService = new AutomationActivationService(
                mock(AutomationActivationStore.class),
                mock(AutomationReleaseStore.class),
                mock(ProcessReleaseService.class),
                mock(AutomationActivationConverter.class),
                mock(ProcessApiSchemaVerifier.class),
                mock(TransactionTemplate.class),
                new MockEnvironment(),
                (ObjectProvider<AutomationAdmissionAuthority>) mock(ObjectProvider.class),
                (ObjectProvider<TrustAuthority>) mock(ObjectProvider.class));
        assertThatCode(() -> legacyService.onDeactivated(1001L, "orderFlow")).doesNotThrowAnyException();

        ObjectProvider<TianshuAgentBindingVerifier> verifierProvider = mock(ObjectProvider.class);
        when(verifierProvider.getIfAvailable()).thenReturn(verifierMock);
        AutomationActivationService wiredService = new AutomationActivationService(
                mock(AutomationActivationStore.class),
                mock(AutomationReleaseStore.class),
                mock(ProcessReleaseService.class),
                mock(AutomationActivationConverter.class),
                mock(ProcessApiSchemaVerifier.class),
                mock(TransactionTemplate.class),
                new MockEnvironment(),
                (ObjectProvider<AutomationAdmissionAuthority>) mock(ObjectProvider.class),
                (ObjectProvider<TrustAuthority>) mock(ObjectProvider.class),
                verifierProvider);

        wiredService.onDeactivated(1001L, "orderFlow");
        verify(verifierMock).releaseProcessBindings(1001L, "orderFlow");
    }

    private static AutomationModelSourceVO agentFlowSource(String agentKey, long version, String sha256) {
        AgentTaskBindingVO.TemplateRefVO ref = new AgentTaskBindingVO.TemplateRefVO(agentKey, version, sha256);
        AgentTaskBindingVO binding = new AgentTaskBindingVO(ref, List.of(ref), EMPTY_OBJECT, Map.of(), List.of(), null);
        AutomationNodeBindingVO node = new AutomationNodeBindingVO(
                "agentStep", "AGENT", EMPTY_OBJECT, EMPTY_OBJECT, List.of(), List.of(), DEFAULT_POLICY,
                null, null, binding, null, null, null);
        return new AutomationModelSourceVO(
                "AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "orderFlow", "<definitions/>")),
                EMPTY_OBJECT,
                EMPTY_OBJECT,
                List.of(node),
                List.of());
    }

    private static AutomationModelSourceVO humanOnlySource() {
        AutomationNodeBindingVO node = new AutomationNodeBindingVO(
                "reviewStep", "HUMAN", EMPTY_OBJECT, EMPTY_OBJECT, List.of(), List.of(), DEFAULT_POLICY,
                null, null, null, new AutomationNodeBindingVO.HumanBindingVO("reviewerRole", EMPTY_OBJECT, Duration.ofMinutes(15)),
                null, null);
        return new AutomationModelSourceVO(
                "AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "humanFlow", "<definitions/>")),
                EMPTY_OBJECT,
                EMPTY_OBJECT,
                List.of(node),
                List.of());
    }

    private static ObjectProvider<DdcSiCatalogClient> providerOf(DdcSiCatalogClient client) {
        return new ObjectProvider<>() {
            @Override
            public DdcSiCatalogClient getObject(Object... args) {
                if (client == null) {
                    throw new IllegalStateException("No client bean");
                }
                return client;
            }

            @Override
            public DdcSiCatalogClient getIfAvailable() {
                return client;
            }

            @Override
            public DdcSiCatalogClient getIfUnique() {
                return client;
            }

            @Override
            public DdcSiCatalogClient getObject() {
                return getObject(new Object[0]);
            }
        };
    }
}
