package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;

public record AgentRunDTO(String runId, String definitionKey, long definitionVersion, String sessionId,
                          AgentRunStatusEnum status, String waitReason, boolean cancelRequested,
                          int attempt, long revision, long lastEventSequence, Instant deadlineAt,
                          Instant createdAt, Instant finishedAt) {
    public AgentRunDTO {
        runId = ContractChecks.required(runId, "runId", 128);
        definitionKey = ContractChecks.required(definitionKey, "definitionKey", 128);
        sessionId = ContractChecks.optional(sessionId, "sessionId", 128);
        status = Objects.requireNonNull(status, "status");
        if (definitionVersion <= 0 || attempt < 0 || revision < 0 || lastEventSequence < 0) {
            throw new IllegalArgumentException("run version, attempt, revision or sequence is invalid");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
}
