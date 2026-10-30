package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryExtractionPolicy;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;

import java.util.List;

/** Explicit source approvals and finite extraction policies; an empty configuration cannot enable admission. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.memory.extraction")
public record TianjiMemoryPreferenceProperties(List<MemoryExtractionPolicy> policies, List<MemoryPreferenceSourceBinding> bindings) {
    public TianjiMemoryPreferenceProperties {
        policies = policies == null ? List.of() : List.copyOf(policies); bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (policies.isEmpty() || policies.size() > 32 || bindings.isEmpty() || bindings.size() > 1000) throw new IllegalArgumentException("Memory preference policies and source bindings are required");
    }
}
