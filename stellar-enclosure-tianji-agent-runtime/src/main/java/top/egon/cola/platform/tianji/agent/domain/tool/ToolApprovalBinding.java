package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;

import java.time.Instant;
import java.util.Map;

/** Fixed v1 approval-workflow mapping for one tool policy. Provenance must come from the trusted ApprovalClient, never model/user JSON. */
public record ToolApprovalBinding(TrustedAgentActor consumer, String policyRef, String policyBindingSha256, String releaseVersion, String definitionKey, String definitionSha256, String taskDefinitionKey) {
    public ToolApprovalBinding {
        if (consumer == null || consumer.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(consumer.issuerFingerprint())
                || !ToolPolicy.key(policyRef) || policyBindingSha256 == null || !policyBindingSha256.matches("[0-9a-f]{64}") || releaseVersion == null || !releaseVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}") || definitionSha256 == null || !definitionSha256.matches("[0-9a-f]{64}")
                || taskDefinitionKey == null || !taskDefinitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Tool approval binding is invalid");
    }
    /** The real requester must authenticate this command (or present separately verified delegation); variables cannot establish its identity. */
    public StartProcessCommand sourceStart(ToolActionRequest action) {
        requirePolicy(action);
        return new StartProcessCommand(definitionKey, releaseVersion, action.businessKey(), action.intentId(), action.intentId(),
                Map.of("actionIntentId", action.intentId(), "inputSha256", action.requestSha256(), "businessVersion", action.businessVersion(), "toolSha256", action.tool().contentSha256()));
    }
    public VerifyApprovalQuery query(ToolActionRequest action, String approvalReceiptId) {
        requirePolicy(action);
        return new VerifyApprovalQuery(policyRef, policyBindingSha256, approvalReceiptId, ApprovalRequesterTypeEnum.valueOf(action.owner().subjectType().name()), action.owner().subjectId(), sourceStart(action));
    }
    /** Checks a freshly fetched server projection; does not consume approval, authorize the requester or dispatch a business call. */
    public void requireApproved(ToolActionRequest action, VerifiedApprovalDTO proof, Instant now) {
        if (proof == null || now == null) throw denied();
        var query = query(action, proof.approvalReceiptId()); var source = query.sourceStart();
        if (!policyRef.equals(proof.policyRef()) || !policyBindingSha256.equals(proof.policyBindingSha256()) || !query.fingerprint().equals(proof.requestSha256())
                || query.requesterType() != proof.requesterType() || !query.requesterSubjectId().equals(proof.requesterSubjectId()) || !source.commandId().equals(proof.sourceStartCommandId())
                || !releaseVersion.equals(proof.releaseVersion()) || !definitionKey.equals(proof.definitionKey()) || !definitionSha256.equals(proof.definitionSha256())
                || !taskDefinitionKey.equals(proof.taskDefinitionKey()) || !OrchestrationRequestDigests.start(source).equals(proof.sourceRequestSha256())
                || proof.occurredAt().isAfter(now) || proof.verifiedAt().isAfter(now) || !proof.expiresAt().isAfter(now) || !proof.approved()) throw denied();
    }
    private void requirePolicy(ToolActionRequest action) {
        if (action == null || action.owner().tenantId() != consumer.tenantId() || !action.owner().issuerFingerprint().equals(consumer.issuerFingerprint())
                || action.owner().subjectType() == TrustedAgentActor.SubjectType.SERVICE && !action.owner().equals(consumer)
                || action.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED || !policyRef.equals(action.policy().approvalPolicyRef())) throw denied();
    }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool approval is denied, unavailable or bound to another action"); }
}
