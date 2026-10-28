package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.util.Map;
import java.util.Objects;

/** Explicit publication metadata and non-secret substitutions for one legacy flow. */
public record AgentFlowImportOptions(String sourceFlowId, String definitionKey, long version,
                                     String inputSchemaVersion, String outputSchemaVersion,
                                     DefinitionDependenciesDTO dependencies, ExecutionPolicyDTO policy,
                                     Map<String, String> substitutions) {
    public AgentFlowImportOptions {
        if (sourceFlowId == null || sourceFlowId.isBlank()) {
            throw new IllegalArgumentException("sourceFlowId is required");
        }
        sourceFlowId = sourceFlowId.trim();
        dependencies = Objects.requireNonNull(dependencies, "dependencies");
        policy = Objects.requireNonNull(policy, "policy");
        substitutions = substitutions == null ? Map.of() : Map.copyOf(substitutions);
    }
}
