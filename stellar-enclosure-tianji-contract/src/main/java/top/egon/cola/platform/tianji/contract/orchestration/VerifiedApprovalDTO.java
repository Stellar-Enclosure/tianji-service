package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Short-lived provenance projection, including an authentic false decision. It neither consumes approval nor grants a business operation. */
public record VerifiedApprovalDTO(String policyRef, String policyBindingSha256, String requestSha256, String approvalReceiptId,
                                  ApprovalRequesterTypeEnum requesterType, String requesterSubjectId, String sourceStartCommandId,
                                  String scopeId, String taskId, String releaseVersion, String definitionKey, String definitionSha256,
                                  String taskDefinitionKey, String sourceRequestSha256, boolean approved, Instant occurredAt, Instant verifiedAt, Instant expiresAt) {
    public VerifiedApprovalDTO {
        policyRef = OrchestrationChecks.command(policyRef); policyBindingSha256 = OrchestrationChecks.hash(policyBindingSha256); requestSha256 = OrchestrationChecks.hash(requestSha256);
        approvalReceiptId = OrchestrationChecks.id(approvalReceiptId, 255); requesterType = Objects.requireNonNull(requesterType, "requesterType"); requesterSubjectId = OrchestrationChecks.id(requesterSubjectId, 256);
        sourceStartCommandId = OrchestrationChecks.command(sourceStartCommandId); scopeId = OrchestrationChecks.id(scopeId, 255); taskId = OrchestrationChecks.id(taskId, 255);
        releaseVersion = OrchestrationChecks.release(releaseVersion); definitionKey = OrchestrationChecks.definition(definitionKey); definitionSha256 = OrchestrationChecks.hash(definitionSha256);
        taskDefinitionKey = OrchestrationChecks.definition(taskDefinitionKey); sourceRequestSha256 = OrchestrationChecks.hash(sourceRequestSha256);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt"); verifiedAt = Objects.requireNonNull(verifiedAt, "verifiedAt"); expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (occurredAt.isAfter(verifiedAt) || !expiresAt.isAfter(verifiedAt) || Duration.between(verifiedAt, expiresAt).compareTo(Duration.ofSeconds(30)) > 0) throw OrchestrationChecks.invalid();
    }
    @Override public String toString() { return "VerifiedApprovalDTO[redacted]"; }
}
