package top.egon.cola.platform.tianji.contract.agent;

import java.time.Duration;
import java.util.Objects;

/** Only safe, client-visible details belong in this response. */
public record AgentErrorDTO(AgentErrorCodeEnum code, String message, String correlationId,
                            String decisionId, Duration retryAfter) {
    public AgentErrorDTO {
        code = Objects.requireNonNull(code, "code");
        message = ContractChecks.required(message, "message", 1024);
        correlationId = ContractChecks.optional(correlationId, "correlationId", 128);
        decisionId = ContractChecks.optional(decisionId, "decisionId", 128);
        if (retryAfter != null && retryAfter.isNegative()) {
            throw new IllegalArgumentException("retryAfter must not be negative");
        }
    }
}
