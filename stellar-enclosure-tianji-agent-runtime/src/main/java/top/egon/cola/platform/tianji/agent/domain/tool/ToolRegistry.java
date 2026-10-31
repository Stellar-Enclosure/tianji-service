package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable version selection only. Runtime authority, budgets, intent receipts and transport remain separate execution gates. */
public final class ToolRegistry {
    private final Map<String, ToolDescriptor> tools;
    public ToolRegistry(Collection<ToolDescriptor> registrations) {
        Objects.requireNonNull(registrations, "registrations"); if (registrations.size() > 256) throw invalid(); var copy = new HashMap<String, ToolDescriptor>();
        for (var descriptor : registrations) if (descriptor == null || copy.putIfAbsent(descriptor.versionKey(), descriptor) != null) throw invalid(); tools = Map.copyOf(copy);
    }

    public Map<String, ToolDescriptor> visible(AgentExecutionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot"); var definition = snapshot.definition(); var effective = snapshot.effectivePolicy();
        if (!definition.policy().allowedToolVersions().containsAll(effective.allowedToolVersions()) || definition.dependencies().tools().size() > 32
                || effective.maxToolCalls() > definition.policy().maxToolCalls() || !Objects.equals(effective.approvalPolicyRef(), definition.policy().approvalPolicyRef())) throw invalid();
        var declared = new HashMap<String, DefinitionResourceRef>();
        for (var reference : definition.dependencies().tools()) if (declared.putIfAbsent(ToolDescriptor.versionKey(reference.key(), reference.version()), reference) != null) throw invalid();
        if (!declared.keySet().containsAll(effective.allowedToolVersions())) throw invalid();
        if (effective.maxToolCalls() == 0) return Map.of();
        var selected = new TreeMap<String, ToolDescriptor>();
        for (var version : effective.allowedToolVersions()) {
            var descriptor = tools.get(version); if (descriptor == null || !descriptor.reference().equals(declared.get(version))) throw invalid();
            if (descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !descriptor.policy().approvalPolicyRef().equals(effective.approvalPolicyRef())
                    || selected.putIfAbsent(descriptor.name(), descriptor) != null) throw invalid();
        }
        return Collections.unmodifiableMap(selected);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Fixed tool dependency or policy is unavailable or changed"); }
}
