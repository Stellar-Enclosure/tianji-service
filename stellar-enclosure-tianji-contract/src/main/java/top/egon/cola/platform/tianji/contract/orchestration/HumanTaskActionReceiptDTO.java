package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

/** Historical reference receipt. An approval reference is useful only after server-side verification of its exact decision and source. */
public record HumanTaskActionReceiptDTO(String commandId, String taskId, HumanTaskActionEnum action, HumanTaskScopeTypeEnum scopeType, String scopeId,
        String releaseVersion, String definitionKey, String taskDefinitionKey, HumanTaskKindEnum kind, String lane, String previousVersion, String resultingVersion,
        String approvalReceiptId, Boolean approved, Instant occurredAt) {
    public HumanTaskActionReceiptDTO {
        commandId = OrchestrationChecks.command(commandId); taskId = OrchestrationChecks.id(taskId, 255); action = Objects.requireNonNull(action, "action"); scopeType = Objects.requireNonNull(scopeType, "scopeType"); scopeId = OrchestrationChecks.id(scopeId, 255);
        releaseVersion = OrchestrationChecks.release(releaseVersion); definitionKey = OrchestrationChecks.definition(definitionKey); taskDefinitionKey = OrchestrationChecks.definition(taskDefinitionKey); kind = Objects.requireNonNull(kind, "kind");
        lane = OrchestrationChecks.definition(lane); previousVersion = OrchestrationChecks.hash(previousVersion); if (resultingVersion != null) resultingVersion = OrchestrationChecks.hash(resultingVersion); occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if ((action == HumanTaskActionEnum.COMPLETE) != (resultingVersion == null) || (action == HumanTaskActionEnum.COMPLETE && kind == HumanTaskKindEnum.APPROVAL) != (approvalReceiptId != null && approved != null)
                || (approvalReceiptId == null) != (approved == null)) throw OrchestrationChecks.invalid();
        if (approvalReceiptId != null) approvalReceiptId = OrchestrationChecks.id(approvalReceiptId, 255);
    }
    @Override public String toString() { return "HumanTaskActionReceipt[taskId=" + taskId + ", action=" + action + ", kind=" + kind + "]"; }
}
