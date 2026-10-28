package top.egon.cola.platform.tianji.contract.agent;

import java.util.Objects;

public record AgentDefinitionDTO(String definitionKey, long version, String contentSha256,
                                 String executionMode, String codeRef, String inputSchemaVersion,
                                 String outputSchemaVersion, DefinitionDependenciesDTO dependencies) {
    public AgentDefinitionDTO {
        definitionKey = ContractChecks.required(definitionKey, "definitionKey", 128);
        if (version <= 0 || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("definition version or SHA-256 is invalid");
        }
        executionMode = ContractChecks.required(executionMode, "executionMode", 64);
        codeRef = ContractChecks.optional(codeRef, "codeRef", 256);
        inputSchemaVersion = ContractChecks.required(inputSchemaVersion, "inputSchemaVersion", 64);
        outputSchemaVersion = ContractChecks.required(outputSchemaVersion, "outputSchemaVersion", 64);
        dependencies = Objects.requireNonNull(dependencies, "dependencies");
    }
}
