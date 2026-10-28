package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.util.Objects;

/** Persisted run inputs and the policy fixed at submission. */
public record AgentExecutionSnapshot(AgentDefinitionSpecDTO definition, String definitionContentSha256,
                                     ExecutionPolicyDTO effectivePolicy, AgentInputDTO input, String businessKey) {
    public AgentExecutionSnapshot {
        definition = Objects.requireNonNull(definition, "definition");
        if (definitionContentSha256 == null || !definitionContentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("definition hash is invalid");
        }
        effectivePolicy = Objects.requireNonNull(effectivePolicy, "effectivePolicy");
        input = Objects.requireNonNull(input, "input");
    }
}
