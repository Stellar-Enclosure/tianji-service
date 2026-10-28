package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;

/** An uncertain response may be retried with the same token, receipt and output. */
public record CompleteWorkerTaskCommand(String taskId, String activityOccurrenceId, String leaseToken, String businessReceiptId,
                                        Map<String, Object> output) {
    public CompleteWorkerTaskCommand {
        taskId = OrchestrationChecks.id(taskId, 255);
        activityOccurrenceId = OrchestrationChecks.id(activityOccurrenceId, 255);
        leaseToken = OrchestrationChecks.leaseToken(leaseToken);
        businessReceiptId = OrchestrationChecks.id(businessReceiptId, 255);
        output = OrchestrationChecks.values(output);
    }

    @Override public String toString() {
        return "CompleteWorkerTaskCommand[taskId=" + taskId + ", activityOccurrenceId=" + activityOccurrenceId
                + ", leaseToken=<redacted>, businessReceiptId="
                + businessReceiptId + ", output=<redacted>]";
    }
}
