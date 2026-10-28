package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

/** Replaying an accepted command returns the original receipt. */
public record WorkerReceiptDTO(String taskId, String activityOccurrenceId, WorkerReceiptOutcomeEnum outcome, Instant decidedAt) {
    public WorkerReceiptDTO {
        taskId = OrchestrationChecks.id(taskId, 255);
        activityOccurrenceId = OrchestrationChecks.id(activityOccurrenceId, 255);
        outcome = Objects.requireNonNull(outcome, "outcome");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt");
    }
}
