package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

/** Only a bounded error code crosses the boundary; stack traces and arbitrary details do not. */
public record FailWorkerTaskCommand(String taskId, String activityOccurrenceId, String leaseToken,
                                    WorkerFailureKindEnum kind, String errorCode) {
    public FailWorkerTaskCommand {
        taskId = OrchestrationChecks.id(taskId, 255);
        activityOccurrenceId = OrchestrationChecks.id(activityOccurrenceId, 255);
        leaseToken = OrchestrationChecks.leaseToken(leaseToken);
        kind = Objects.requireNonNull(kind, "kind");
        errorCode = OrchestrationChecks.errorCode(errorCode);
    }

    @Override public String toString() {
        return "FailWorkerTaskCommand[taskId=" + taskId + ", activityOccurrenceId=" + activityOccurrenceId
                + ", leaseToken=<redacted>, kind=" + kind + ", errorCode=" + errorCode + "]";
    }
}
