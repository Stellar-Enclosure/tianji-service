package top.egon.cola.platform.tianji.process.server.task;

import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.execution.ProcessValues;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Canonical task result construction, shared by native orchestration and durable reference storage. It commits no receipt itself. */
public final class HumanTaskActionResult {
    private HumanTaskActionResult() { }

    public static Map<String, Object> formTargets(HumanTask policy, Map<String, Object> supplied) {
        var checked = form(policy, supplied); var result = new LinkedHashMap<String, Object>();
        for (var field : policy.fields()) result.put(field.target(), checked.get(field.name())); return Map.copyOf(result);
    }

    public static Entry applied(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String requestHash,
            HumanTaskSnapshot before, HumanTaskSnapshot after, String targetLane, Map<String, Object> suppliedForm, Instant occurredAt) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(action, "action"); Objects.requireNonNull(before, "before"); Objects.requireNonNull(occurredAt, "occurredAt");
        if (requestHash == null || !requestHash.matches("[0-9a-f]{64}")) throw invalid();
        var policy = before.policy(); var facts = before.facts();
        switch (action) {
            case CLAIM -> HumanTaskAccessRules.claim(context, facts, policy);
            case UNCLAIM -> HumanTaskAccessRules.unclaim(context, facts);
            case TRANSFER -> HumanTaskAccessRules.transfer(context, facts, policy, targetLane);
            case COMPLETE -> HumanTaskAccessRules.complete(context, facts, policy);
        }
        Map<String, Object> checked = action == HumanTaskActionEnum.COMPLETE ? form(policy, suppliedForm) : Map.of();
        if (suppliedForm == null || action != HumanTaskActionEnum.COMPLETE && !suppliedForm.isEmpty()) throw invalid();
        if (action == HumanTaskActionEnum.COMPLETE) { if (after != null) throw invalid(); }
        else if (after == null || !before.taskId().equals(after.taskId()) || !before.origin().equals(after.origin()) || !before.definition().equals(after.definition())
                || !policy.equals(after.policy()) || before.version().equals(after.version()) || !Objects.equals(after.facts().assignee(), action == HumanTaskActionEnum.CLAIM ? context.userKey() : null)
                || !after.lane().equals(action == HumanTaskActionEnum.TRANSFER ? targetLane : before.lane())) throw invalid();
        boolean approval = action == HumanTaskActionEnum.COMPLETE && policy.kind() == HumanTaskKind.APPROVAL;
        Boolean approved = null; String reference = null;
        if (approval) {
            var field = policy.fields().stream().filter(value -> value.target().equals(policy.approvalVariable()) && value.type() == ValueType.BOOLEAN).findFirst().orElseThrow(HumanTaskActionResult::invalid);
            approved = (Boolean) checked.get(field.name());
            reference = "approval-" + ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_APPROVAL_REF_V1", Long.toString(context.actor().tenantId()), context.actor().issuerFingerprint(), context.actor().subjectId(),
                    commandId, before.taskId(), before.version(), before.origin().requestHash()), Map.of());
        }
        String lane = after == null ? before.lane() : after.lane();
        var receipt = new HumanTaskActionReceiptDTO(commandId, before.taskId(), action, HumanTaskScopeTypeEnum.PROCESS, before.origin().instanceId(), before.origin().releaseKey(),
                before.definition().definitionKey(), before.taskDefinitionKey(), HumanTaskKindEnum.valueOf(policy.kind().name()), lane, before.version(), after == null ? null : after.version(), reference, approved, occurredAt);
        String role = action == HumanTaskActionEnum.CLAIM || action == HumanTaskActionEnum.COMPLETE ? policy.lanes().get(before.lane()).stream().filter(context.roleIds()::contains).sorted().findFirst().orElseThrow(HumanTaskActionResult::invalid) : null;
        return new Entry(context.actor(), requestHash, receipt, before.origin(), before.definition().modelSha256(), before.definition().engineDefinitionId(), role,
                context.authVersion(), context.policyVersion(), facts.assignee(), after == null ? null : after.facts().assignee());
    }

    private static Map<String, Object> form(HumanTask policy, Map<String, Object> supplied) {
        Objects.requireNonNull(policy, "policy"); var schema = new LinkedHashMap<String, ValueType>(); for (var field : policy.fields()) schema.put(field.name(), field.type());
        return ProcessValues.inputs(schema, supplied);
    }
    public record Entry(ProcessActor actor, String requestHash, HumanTaskActionReceiptDTO receipt, TaskOrigin origin, String modelSha256, String engineDefinitionId,
                        String authorizedRoleId, long authVersion, long policyVersion, String previousAssignee, String resultingAssignee) {
        public Entry {
            Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(receipt, "receipt"); Objects.requireNonNull(origin, "origin");
            if (actor.subjectType() != ProcessActor.SubjectType.USER || "LEGACY".equals(actor.issuerFingerprint()) || actor.tenantId() != origin.requester().tenantId() || !actor.issuerFingerprint().equals(origin.requester().issuerFingerprint())
                    || requestHash == null || !requestHash.matches("[0-9a-f]{64}") || modelSha256 == null || !modelSha256.matches("[0-9a-f]{64}") || engineDefinitionId == null || engineDefinitionId.isBlank()
                    || !origin.instanceId().equals(receipt.scopeId()) || !origin.releaseKey().equals(receipt.releaseVersion()) || authVersion < 0 || policyVersion < 0) throw invalid();
            boolean memberAction = receipt.action() == HumanTaskActionEnum.CLAIM || receipt.action() == HumanTaskActionEnum.COMPLETE;
            if (receipt.scopeType() != HumanTaskScopeTypeEnum.PROCESS || memberAction != (authorizedRoleId != null)
                    || authorizedRoleId != null && (!authorizedRoleId.matches("[1-9][0-9]{0,18}") || Long.parseLong(authorizedRoleId) < 1)
                    || previousAssignee != null && !previousAssignee.matches("user-[0-9a-f]{64}") || resultingAssignee != null && !resultingAssignee.matches("user-[0-9a-f]{64}")
                    || receipt.action() == HumanTaskActionEnum.CLAIM && (previousAssignee != null || resultingAssignee == null)
                    || receipt.action() != HumanTaskActionEnum.CLAIM && resultingAssignee != null
                    || (receipt.action() == HumanTaskActionEnum.UNCLAIM || receipt.action() == HumanTaskActionEnum.COMPLETE) && previousAssignee == null
                    || receipt.previousVersion().equals(receipt.resultingVersion()) || origin.createdAt() == null || receipt.occurredAt().isBefore(origin.createdAt())
                    || origin.requestHash() == null || !origin.requestHash().matches("[0-9a-f]{64}")
                    || receipt.kind() == HumanTaskKindEnum.APPROVAL && receipt.action() == HumanTaskActionEnum.COMPLETE
                       && origin.requester().subjectType() == ProcessActor.SubjectType.USER && actor.subjectId().equals(origin.requester().subjectId())) throw invalid();
        }
        @Override public String toString() { return "HumanTaskActionEntry[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Human task action result is inconsistent"); }
}
