package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;

/** Session metadata; native ADK events and state remain internal. */
public record AgentSessionDTO(String sessionId, Instant updatedAt) {
    public AgentSessionDTO {
        sessionId = ContractChecks.sessionId(sessionId);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
