package top.egon.cola.platform.tianji.contract.orchestration;

/** Read-only approval provenance for a separately registered authenticated service consumer. */
public interface ApprovalClient {
    String PATH = "/api/tianji/v1/human-tasks/approvals/verify";
    String VERIFY_SCOPE = "tianji:task:approval:verify";
    VerifiedApprovalDTO verifyApproval(VerifyApprovalQuery query);
}
