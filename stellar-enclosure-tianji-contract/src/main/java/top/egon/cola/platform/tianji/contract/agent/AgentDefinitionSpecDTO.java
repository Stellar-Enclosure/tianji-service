package top.egon.cola.platform.tianji.contract.agent;

import java.util.Map;
import java.util.Objects;

/** Immutable publication input. The service computes the content hash. */
public record AgentDefinitionSpecDTO(String definitionKey, long version, String executionMode, String codeRef,
                                     String inputSchemaVersion, String outputSchemaVersion,
                                     DefinitionDependenciesDTO dependencies, ExecutionPolicyDTO policy,
                                     Map<String, Object> configuration) {
    public AgentDefinitionSpecDTO {
        definitionKey = ContractChecks.required(definitionKey, "definitionKey", 128);
        if (version <= 0) {
            throw new IllegalArgumentException("definition version must be positive");
        }
        executionMode = ContractChecks.required(executionMode, "executionMode", 64);
        codeRef = ContractChecks.optional(codeRef, "codeRef", 256);
        inputSchemaVersion = ContractChecks.required(inputSchemaVersion, "inputSchemaVersion", 64);
        outputSchemaVersion = ContractChecks.required(outputSchemaVersion, "outputSchemaVersion", 64);
        dependencies = Objects.requireNonNull(dependencies, "dependencies");
        policy = Objects.requireNonNull(policy, "policy");
        configuration = ContractChecks.jsonObject(configuration);
    }
}
