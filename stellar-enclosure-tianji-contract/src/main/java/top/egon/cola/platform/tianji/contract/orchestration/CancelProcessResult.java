package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

public record CancelProcessResult(String instanceId, String commandId, boolean accepted,
                                  ProcessInstanceStatusEnum status, String resultingVersion,
                                  Instant finishedAt, Instant decidedAt, boolean linkedAgentClosureScheduled) {
    public CancelProcessResult {
        instanceId = OrchestrationChecks.id(instanceId, 255);
        commandId = OrchestrationChecks.command(commandId);
        status = Objects.requireNonNull(status, "status");
        resultingVersion = OrchestrationChecks.hash(resultingVersion);
        finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        if (status != ProcessInstanceStatusEnum.COMPLETED && status != ProcessInstanceStatusEnum.CANCELLED
                || accepted && status != ProcessInstanceStatusEnum.CANCELLED
                || !accepted && linkedAgentClosureScheduled) throw OrchestrationChecks.invalid();
    }

    @Override
    public String toString() {
        return "CancelProcessResult[redacted]";
    }
}
