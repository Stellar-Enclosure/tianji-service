package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

public record RecoverProcessWorkerResult(String instanceId, String jobId, String occurrenceId, String commandId,
                                         StatusEnum status, int retries, Instant decidedAt) {
    public RecoverProcessWorkerResult {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        jobId = OrchestrationChecks.id(jobId, 255);
        occurrenceId = OrchestrationChecks.id(occurrenceId, 255);
        commandId = OrchestrationChecks.command(commandId);
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
        if (!jobId.equals(occurrenceId) || status != StatusEnum.RETRY_SCHEDULED || retries != 1) {
            throw OrchestrationChecks.invalid();
        }
    }

    public enum StatusEnum {
        RETRY_SCHEDULED
    }
}
