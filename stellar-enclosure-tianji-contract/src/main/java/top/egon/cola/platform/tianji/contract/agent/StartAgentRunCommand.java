package top.egon.cola.platform.tianji.contract.agent;

import java.util.Objects;

/** The idempotency key identifies this start command within the authenticated actor's scope. */
public record StartAgentRunCommand(String definitionKey, long definitionVersion, String sessionId,
                                   String idempotencyKey, String businessKey, String correlationId,
                                   AgentInputDTO input, ExecutionPolicyOverrideDTO policyOverride) {
    public StartAgentRunCommand {
        definitionKey = ContractChecks.required(definitionKey, "definitionKey", 128);
        if (definitionVersion <= 0) {
            throw new IllegalArgumentException("definitionVersion must be positive");
        }
        sessionId = sessionId == null ? null : ContractChecks.sessionId(sessionId);
        idempotencyKey = ContractChecks.required(idempotencyKey, "idempotencyKey", 128);
        businessKey = ContractChecks.optional(businessKey, "businessKey", 256);
        correlationId = ContractChecks.optional(correlationId, "correlationId", 128);
        input = Objects.requireNonNull(input, "input");
    }
}
