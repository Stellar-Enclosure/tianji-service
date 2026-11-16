package top.egon.cola.platform.tianji.process.server.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTaskBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies atomic Tianshu SI Agent bindings prior to Flowable workflow activation and manages process reference locks.
 */
@Slf4j
@Component("tianshuAgentBindingVerifier")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TianshuAgentBindingVerifier {

    private static final String PREFIX = "egon.cola.component.tianshu.si.client.";
    private static final String PROP_STRICT = PREFIX + "strict-activation-check";

    private final ObjectProvider<DdcSiCatalogClient> clientProvider;
    private final Environment environment;
    private final Map<String, Set<String>> activeProcessBindings = new ConcurrentHashMap<>();

    /**
     * Validates all AGENT nodes inside a release source outside any DB transaction before activation succeeds.
     */
    public void verifyAndRegisterProcessBindings(long tenantId, String definitionKey, AutomationModelSourceVO source) {
        Objects.requireNonNull(definitionKey, "definitionKey");
        if (source == null || source.nodeBindings() == null || source.nodeBindings().isEmpty()) {
            return;
        }
        Map<String, AgentTaskBindingVO.TemplateRefVO> distinctRefs = extractAgentTemplateRefs(source.nodeBindings());
        if (distinctRefs.isEmpty()) {
            // Pure deterministic BPMN/DMN/HUMAN workflows never invoke Tianshu SI remote verification.
            return;
        }

        DdcSiCatalogClient client = clientProvider.getIfAvailable();
        boolean strict = environment.getProperty(PROP_STRICT, Boolean.class, Boolean.TRUE);
        if (client == null) {
            if (strict) {
                log.warn("Rejecting process activation definitionKey={} tenant={}: Tianshu SI client unavailable in strict mode",
                        definitionKey, tenantId);
                throw new ProcessConflictException();
            }
            log.warn("Skipping SI pre-activation check for definitionKey={} tenant={}: strictActivationCheck=false and client absent",
                    definitionKey, tenantId);
            return;
        }

        String bizCode = environment.getProperty(PREFIX + "biz-code", "ai");
        String namespaceCode = environment.getProperty(PREFIX + "namespace-code", "default");
        String envCode = environment.getProperty(PREFIX + "env-code", "dev");
        String appCode = environment.getProperty(PREFIX + "app-code");

        for (AgentTaskBindingVO.TemplateRefVO ref : distinctRefs.values()) {
            verifySingleAgentBinding(client, strict, tenantId, bizCode, namespaceCode, envCode, appCode, definitionKey, ref);
        }

        activeProcessBindings.put(processScopeKey(tenantId, definitionKey), Set.copyOf(distinctRefs.keySet()));
        log.info("Verified Tianshu SI agent bindings for TIANJI_PROCESS tenant={} definitionKey={} bindingsCount={}",
                tenantId, definitionKey, distinctRefs.size());
    }

    /**
     * Releases tracked process references on workflow deactivation; errors are logged and never block deactivation.
     */
    public void releaseProcessBindings(long tenantId, String definitionKey) {
        try {
            Set<String> removed = activeProcessBindings.remove(processScopeKey(tenantId, definitionKey));
            if (removed != null && !removed.isEmpty()) {
                log.info("Released TIANJI_PROCESS SI bindings tenant={} definitionKey={} releasedCount={}",
                        tenantId, definitionKey, removed.size());
            }
        } catch (RuntimeException failure) {
            log.warn("Tolerating SI reference release error during deactivation tenant={} definitionKey={}: {}",
                    tenantId, definitionKey, failure.getMessage());
        }
    }

    private void verifySingleAgentBinding(
            DdcSiCatalogClient client,
            boolean strict,
            long tenantId,
            String bizCode,
            String namespaceCode,
            String envCode,
            String appCode,
            String definitionKey,
            AgentTaskBindingVO.TemplateRefVO ref) {
        Integer pinnedVersionNo = Math.toIntExact(ref.version());
        final Optional<DdcSiResourceDescriptor> resolved;
        try {
            resolved = client.resolveResource(
                    tenantId, bizCode, namespaceCode, envCode, appCode, DdcSiKindEnum.AGENT, ref.key(), pinnedVersionNo);
        } catch (RuntimeException remoteFailure) {
            if (strict) {
                log.warn("Tianshu SI lookup failed in strict mode tenant={} definitionKey={} agentKey={} version={}: {}",
                        tenantId, definitionKey, ref.key(), ref.version(), remoteFailure.getMessage());
                throw new ProcessConflictException();
            }
            log.warn("Tianshu SI lookup failed in lenient mode tenant={} definitionKey={} agentKey={}: {}",
                    tenantId, definitionKey, ref.key(), remoteFailure.getMessage());
            return;
        }

        if (resolved.isEmpty()) {
            log.warn("Bound SI AGENT missing tenant={} definitionKey={} agentKey={} version={}",
                    tenantId, definitionKey, ref.key(), ref.version());
            throw new ProcessConflictException();
        }
        DdcSiResourceDescriptor descriptor = resolved.get();
        if (descriptor.getStatus() != DdcSiLifecycleStatusEnum.PUBLISHED) {
            log.warn("Bound SI AGENT is not PUBLISHED tenant={} definitionKey={} agentKey={} status={}",
                    tenantId, definitionKey, ref.key(), descriptor.getStatus());
            throw new ProcessConflictException();
        }
        if (descriptor.getDigestSha256() != null
                && !descriptor.getDigestSha256().isBlank()
                && !descriptor.getDigestSha256().equalsIgnoreCase(ref.contentSha256())) {
            log.warn("Bound SI AGENT digest mismatch tenant={} definitionKey={} agentKey={} expected={} actual={}",
                    tenantId, definitionKey, ref.key(), ref.contentSha256(), descriptor.getDigestSha256());
            throw new ProcessConflictException();
        }
    }

    private static Map<String, AgentTaskBindingVO.TemplateRefVO> extractAgentTemplateRefs(List<AutomationNodeBindingVO> bindings) {
        Map<String, AgentTaskBindingVO.TemplateRefVO> result = new LinkedHashMap<>();
        for (AutomationNodeBindingVO binding : bindings) {
            if (binding != null && "AGENT".equals(binding.kind()) && binding.agent() != null) {
                AgentTaskBindingVO.TemplateRefVO defaultRef = binding.agent().defaultTemplate();
                result.putIfAbsent(defaultRef.key() + ":" + defaultRef.version(), defaultRef);
                for (AgentTaskBindingVO.TemplateRefVO allowed : binding.agent().allowedTemplates()) {
                    result.putIfAbsent(allowed.key() + ":" + allowed.version(), allowed);
                }
            }
        }
        return result;
    }

    private static String processScopeKey(long tenantId, String definitionKey) {
        return tenantId + ":" + definitionKey;
    }
}
