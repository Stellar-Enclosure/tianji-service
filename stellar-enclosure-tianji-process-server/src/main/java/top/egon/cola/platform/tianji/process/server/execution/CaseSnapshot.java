package top.egon.cola.platform.tianji.process.server.execution;

import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Copies native facts into a released-policy projection. A version is neither authorization nor a lock. */
public final class CaseSnapshot {
    private static final List<String> ELEMENTS = List.of("primary", "supplemental", "milestone");
    private final CaseStartProvenanceStore.Entry origin;
    private final ValidatedCaseModel model;
    private final CaseNativeAccess.Observation observation;
    private final CaseInstanceStatusEnum status;
    private final List<CaseNativeAccess.PlanItem> items;

    public CaseSnapshot(CaseStartProvenanceStore.Entry origin, ValidatedCaseModel model, CaseNativeAccess.Observation observation) {
        this.origin = Objects.requireNonNull(origin, "origin"); this.model = Objects.requireNonNull(model, "model"); this.observation = Objects.requireNonNull(observation, "observation");
        var seed = origin.seed();
        if (!seed.caseInstanceId().equals(observation.caseId()) || !seed.caseEngineDefinitionId().equals(observation.definitionId())
                || !Long.toString(seed.owner().tenantId()).equals(observation.tenantId()) || !seed.businessKey().equals(observation.businessKey())
                || !seed.caseDefinitionKey().equals(model.definitionKey()) || !seed.caseModelSha256().equals(hash(model.xml()))) throw unavailable();
        status = switch (observation.state()) { case "active" -> CaseInstanceStatusEnum.ACTIVE; case "completed" -> CaseInstanceStatusEnum.COMPLETED; case "terminated" -> CaseInstanceStatusEnum.TERMINATED; default -> throw unavailable(); };
        if ((status == CaseInstanceStatusEnum.ACTIVE) != (observation.finishedAt() == null) || observation.items() == null || observation.items().size() > 3) throw unavailable();
        var ids = new HashSet<String>(); var elements = new HashSet<String>();
        for (var item : observation.items()) {
            if (item == null || !ids.add(item.id()) || !elements.add(item.elementId()) || !ELEMENTS.contains(item.elementId())
                    || !identifier(item.id(), 255) || item.createdAt() == null || !item.historical() && item.revision() < 1 || item.taskRevision() < 0
                    || item.assignee() != null && !identifier(item.assignee(), 255)) throw unavailable();
            var policy = model.planItems().get(item.elementId());
            if (policy == null || !policy.definitionId().equals(item.definitionId())) throw unavailable();
            kind(item); state(item);
            if ("completed".equals(item.state()) && ("milestone".equals(item.kind()) ? item.occurredAt() == null || item.endedAt() == null : item.completedAt() == null)
                    || "terminated".equals(item.state()) && item.terminatedAt() == null) throw unavailable();
        }
        items = observation.items().stream().sorted(Comparator.comparingInt(item -> ELEMENTS.indexOf(item.elementId()))).toList();
    }

    public CaseInstanceDTO projection(CaseContext context) {
        requireRead(context, origin, model); var seed = origin.seed(); var projected = new ArrayList<CasePlanItemDTO>();
        for (var item : items) projected.add(new CasePlanItemDTO(item.id(), item.definitionId(), kind(item), state(item), version(item), availableActions(context, item)));
        return new CaseInstanceDTO(seed.caseInstanceId(), seed.caseDefinitionKey(), seed.releaseKey(), seed.businessKey(), seed.correlationId(), status, origin.createdAt(), observation.finishedAt(), projected);
    }
    public String version(CaseNativeAccess.PlanItem item) {
        var seed = origin.seed();
        return ProcessRequestDigest.hash(List.of(item.historical() ? "TIANJI_CASE_PLAN_ITEM_HISTORY_V1" : "TIANJI_CASE_PLAN_ITEM_RUNTIME_V1",
                Long.toString(seed.owner().tenantId()), seed.owner().issuerFingerprint(), seed.caseInstanceId(), seed.releaseKey(), seed.caseEngineDefinitionId(), seed.caseModelSha256(),
                item.id(), item.elementId(), item.definitionId(), item.kind(), item.state(), item.historical() ? "" : Integer.toString(item.revision()),
                time(item.createdAt()), time(item.availableAt()), time(item.enabledAt()), time(item.disabledAt()), time(item.startedAt()), time(item.completedAt()), time(item.occurredAt()), time(item.terminatedAt()), time(item.endedAt()),
                item.assignee() == null ? "" : item.assignee(), Integer.toString(item.taskRevision())), Map.of());
    }
    CaseNativeAccess.PlanItem item(String id) { return items.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow(ProcessResourceNotFoundException::new); }
    List<CasePlanItemActionEnum> availableActions(CaseContext context, CaseNativeAccess.PlanItem item) {
        var seed = origin.seed(); var policy = model.planItems().get(item.elementId());
        if (status != CaseInstanceStatusEnum.ACTIVE || item.historical() || kind(item) != CasePlanItemKindEnum.HUMAN_TASK
                || context.actor().subjectType() != ProcessActor.SubjectType.USER || context.actor().tenantId() != seed.owner().tenantId()
                || !context.actor().issuerFingerprint().equals(seed.owner().issuerFingerprint()) || policy.roleIds().stream().noneMatch(context.roleIds()::contains)) return List.of();
        var result = new ArrayList<CasePlanItemActionEnum>();
        for (var action : CasePlanItemActionEnum.values()) {
            if (!policy.actions().contains(ValidatedCaseModel.Action.valueOf(action.name()))) continue;
            boolean valid = switch (action) {
                case DISABLE -> "enabled".equals(item.state());
                case ENABLE -> "disabled".equals(item.state());
                case START -> "enabled".equals(item.state());
                case TERMINATE -> "supplemental".equals(item.elementId()) && "active".equals(item.state()) && (item.assignee() == null || userKey(context.actor()).equals(item.assignee()));
            };
            if (valid) result.add(action);
        }
        return List.copyOf(result);
    }
    String authorizedRole(CaseContext context, CaseNativeAccess.PlanItem item) {
        return model.planItems().get(item.elementId()).roleIds().stream().filter(context.roleIds()::contains).min(Comparator.comparingLong(Long::parseLong)).orElseThrow(ProcessResourceNotFoundException::new);
    }
    static void requireRead(CaseContext context, CaseStartProvenanceStore.Entry origin, ValidatedCaseModel model) {
        var actor = Objects.requireNonNull(context, "context").actor(); var owner = origin.seed().owner();
        if (actor.tenantId() != owner.tenantId() || !actor.issuerFingerprint().equals(owner.issuerFingerprint())
                || !actor.equals(owner) && (actor.subjectType() != ProcessActor.SubjectType.USER || model.operatorRoleIds().stream().noneMatch(context.roleIds()::contains))) throw new ProcessResourceNotFoundException();
    }
    private CasePlanItemKindEnum kind(CaseNativeAccess.PlanItem item) {
        if (org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK.equals(item.kind()) && model.humanTasks().containsKey(item.definitionId())) return CasePlanItemKindEnum.HUMAN_TASK;
        if ("milestone".equals(item.kind()) && "milestone".equals(item.elementId()) && "completionMilestone".equals(item.definitionId())) return CasePlanItemKindEnum.MILESTONE;
        throw unavailable();
    }
    private static CasePlanItemStateEnum state(CaseNativeAccess.PlanItem item) {
        return switch (item.state()) { case "available" -> CasePlanItemStateEnum.AVAILABLE; case "enabled" -> CasePlanItemStateEnum.ENABLED; case "disabled" -> CasePlanItemStateEnum.DISABLED; case "active" -> CasePlanItemStateEnum.ACTIVE; case "completed" -> CasePlanItemStateEnum.COMPLETED; case "terminated" -> CasePlanItemStateEnum.TERMINATED; default -> throw unavailable(); };
    }
    static String userKey(ProcessActor actor) { return "user-" + ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_TASK_USER_V1", Long.toString(actor.tenantId()), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId()), Map.of()); }
    private static String time(Instant value) { return value == null ? "" : value.toString(); }
    private static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (NoSuchAlgorithmException failure) { throw unavailable(); } }
    private static boolean identifier(String value, int maximum) { return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim()) && StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.chars().noneMatch(Character::isISOControl); }
    @Override public String toString() { return "CaseSnapshot[redacted]"; }
    private static IllegalStateException unavailable() { return new IllegalStateException("Native Case does not match its immutable released source"); }
}
