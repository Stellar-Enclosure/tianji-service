package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;

public record CancelAgentRunResult(String runId, boolean accepted, AgentRunStatusEnum status,
                                   long revision, Instant requestedAt) {
    public CancelAgentRunResult {
        runId = ContractChecks.required(runId, "runId", 128);
        status = Objects.requireNonNull(status, "status");
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
