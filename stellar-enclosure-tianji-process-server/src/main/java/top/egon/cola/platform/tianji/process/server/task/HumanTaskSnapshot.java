package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Copies bounded native state into a versioned released-policy snapshot. It is neither a lock nor authorization. */
public final class HumanTaskSnapshot {
    private final TaskOrigin origin;
    private final Definition definition;
    private final HumanTask policy;
    private final String taskId;
    private final String taskDefinitionKey;
    private final String name;
    private final String assignee;
    private final String lane;
    private final boolean suspended;
    private final boolean delegated;
    private final Instant createdAt;
    private final Instant dueAt;
    private final String version;

    public HumanTaskSnapshot(Task task, List<IdentityLink> links, TaskOrigin origin, Definition definition, ValidatedProcessModel model) {
        this.origin = Objects.requireNonNull(origin, "origin"); this.definition = Objects.requireNonNull(definition, "definition"); Objects.requireNonNull(model, "model");
        Objects.requireNonNull(task, "task");
        if (!(task instanceof TaskEntity nativeTask) || nativeTask.getRevision() < 1 || definition.type() != Type.BPMN || model.type() != Type.BPMN
                || !definition.definitionKey().equals(model.definitionKey()) || !definition.modelSha256().equals(modelHash(model.xml()))
                || "LEGACY".equals(origin.requester().issuerFingerprint()) || !origin.instanceId().equals(task.getProcessInstanceId()) || !definition.engineDefinitionId().equals(task.getProcessDefinitionId())
                || !Long.toString(origin.requester().tenantId()).equals(task.getTenantId()) || task.getScopeType() != null || task.getScopeId() != null || task.getParentTaskId() != null
                || task.getCreateTime() == null || links == null || links.size() > 64) throw unavailable();
        taskId = identifier(task.getId(), 255); taskDefinitionKey = identifier(task.getTaskDefinitionKey(), 128);
        policy = model.humanTasks().get(taskDefinitionKey); if (policy == null) throw unavailable();
        String title = task.getName(); name = title == null || title.isBlank() ? taskDefinitionKey : identifier(title.strip(), 512);
        assignee = task.getAssignee(); if (assignee != null) identifier(assignee, 255);
        suspended = task.isSuspended(); delegated = task.getDelegationState() != null || task.getOwner() != null;
        createdAt = task.getCreateTime().toInstant(); dueAt = task.getDueDate() == null ? null : task.getDueDate().toInstant();
        var groups = new HashSet<String>(); var identities = new ArrayList<String>();
        for (var link : links) {
            if (link == null || !taskId.equals(link.getTaskId()) || link.getType() == null || link.getType().isBlank()) throw unavailable();
            if ("candidate".equals(link.getType())) {
                String group = link.getGroupId(); if (link.getUserId() != null || group == null || !group.matches("role:[1-9][0-9]{0,18}") || !groups.add(group.substring(5))) throw unavailable();
            }
            identities.add(ProcessRequestDigest.hash(List.of(link.getType(), text(link.getUserId()), text(link.getGroupId())), Map.of()));
        }
        var selected = policy.lanes().entrySet().stream().filter(entry -> entry.getValue().equals(groups)).map(Map.Entry::getKey).toList();
        if (selected.size() != 1) throw unavailable(); lane = selected.getFirst();
        var fields = new ArrayList<>(List.of("TIANJI_HUMAN_TASK_SNAPSHOT_V1", taskId, taskDefinitionKey, Long.toString(origin.requester().tenantId()), origin.requester().issuerFingerprint(),
                origin.requester().subjectType().name(), origin.requester().subjectId(), origin.commandId(), origin.requestHash(), origin.instanceId(), origin.releaseKey(), definition.definitionKey(),
                definition.engineDefinitionId(), definition.engineDeploymentId(), Integer.toString(definition.engineVersion()), definition.modelSha256(), policy.kind().name(), lane,
                Integer.toString(nativeTask.getRevision()), text(assignee), text(task.getOwner()), text(task.getState()), text(task.getExecutionId()), task.getDelegationState() == null ? "" : task.getDelegationState().name(), Boolean.toString(suspended),
                task.getClaimTime() == null ? "" : task.getClaimTime().toInstant().toString(), text(task.getClaimedBy()), task.getInProgressStartTime() == null ? "" : task.getInProgressStartTime().toInstant().toString(), text(task.getInProgressStartedBy()),
                createdAt.toString(), dueAt == null ? "" : dueAt.toString(), Integer.toString(task.getPriority()), name, text(task.getFormKey()), Integer.toString(identities.size())));
        fields.addAll(identities.stream().sorted().toList()); version = ProcessRequestDigest.hash(fields, Map.of());
    }

    public String version() { return version; }
    public String taskId() { return taskId; }
    public String taskDefinitionKey() { return taskDefinitionKey; }
    public TaskOrigin origin() { return origin; }
    public Definition definition() { return definition; }
    public HumanTask policy() { return policy; }
    public String lane() { return lane; }
    public HumanTaskAccessRules.Facts facts() { return new HumanTaskAccessRules.Facts(origin.requester(), lane, assignee, suspended, delegated); }
    public HumanTaskDTO projection(HumanTaskContext context) {
        if (!HumanTaskAccessRules.readable(context, facts(), policy)) throw new top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException();
        var schema = new LinkedHashMap<String, ProcessValueTypeEnum>(); String approvalField = null;
        for (var field : policy.fields()) {
            schema.put(field.name(), ProcessValueTypeEnum.valueOf(field.type().name()));
            if (field.target().equals(policy.approvalVariable())) approvalField = field.name();
        }
        return new HumanTaskDTO(taskId, HumanTaskScopeTypeEnum.PROCESS, origin.instanceId(), origin.releaseKey(), definition.definitionKey(), taskDefinitionKey, name,
                HumanTaskKindEnum.valueOf(policy.kind().name()), approvalField, version, lane, policy.lanes().keySet().stream().sorted().toList(), assignee != null,
                context.userKey().equals(assignee), suspended, schema, createdAt, dueAt);
    }
    @Override public String toString() { return "HumanTaskSnapshot[redacted]"; }
    private static String text(String value) { return value == null ? "" : identifier(value, 512); }
    private static String modelHash(byte[] bytes) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); } catch (java.security.NoSuchAlgorithmException failure) { throw unavailable(); } }
    private static String identifier(String value, int max) { if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)
            || value.chars().anyMatch(Character::isISOControl)) throw unavailable(); return value; }
    private static IllegalStateException unavailable() { return new IllegalStateException("Native human task does not match its released source"); }
}
