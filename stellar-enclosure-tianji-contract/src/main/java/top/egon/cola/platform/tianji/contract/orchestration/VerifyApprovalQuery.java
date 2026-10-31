package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.List;
import java.util.Objects;

/** Expected provenance filters. Only the separately authenticated consumer and durable source can establish authority. */
public record VerifyApprovalQuery(String policyRef, String policyBindingSha256, String approvalReceiptId,
                                  ApprovalRequesterTypeEnum requesterType, String requesterSubjectId, StartProcessCommand sourceStart) {
    public VerifyApprovalQuery {
        policyRef = OrchestrationChecks.command(policyRef); policyBindingSha256 = OrchestrationChecks.hash(policyBindingSha256);
        approvalReceiptId = OrchestrationChecks.id(approvalReceiptId, 255); requesterType = Objects.requireNonNull(requesterType, "requesterType");
        requesterSubjectId = OrchestrationChecks.id(requesterSubjectId, 256); sourceStart = Objects.requireNonNull(sourceStart, "sourceStart");
    }
    public String fingerprint() {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_APPROVAL_VERIFY_REQUEST_V1", policyRef, policyBindingSha256, approvalReceiptId, requesterType.name(),
                requesterSubjectId, sourceStart.commandId(), sourceStart.releaseVersion(), sourceStart.definitionKey(), sourceStart.businessKey(), sourceStart.correlationId()), sourceStart.variables());
    }
    @Override public String toString() { return "VerifyApprovalQuery[redacted]"; }
}
