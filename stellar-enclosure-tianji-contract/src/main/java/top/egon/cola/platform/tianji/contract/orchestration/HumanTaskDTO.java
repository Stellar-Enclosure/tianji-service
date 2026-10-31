package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Released task references and form schema; opaque version metadata grants no task or approval authority. */
public record HumanTaskDTO(String taskId, HumanTaskScopeTypeEnum scopeType, String scopeId, String releaseVersion, String definitionKey,
        String taskDefinitionKey, String name, HumanTaskKindEnum kind, String approvalField, String version, String lane, List<String> availableLanes,
        boolean assigned, boolean assignedToMe, boolean suspended, Map<String, ProcessValueTypeEnum> formSchema, Instant createdAt, Instant dueAt) {
    public HumanTaskDTO {
        taskId = OrchestrationChecks.id(taskId, 255); scopeType = Objects.requireNonNull(scopeType, "scopeType"); scopeId = OrchestrationChecks.id(scopeId, 255);
        releaseVersion = OrchestrationChecks.release(releaseVersion); definitionKey = OrchestrationChecks.definition(definitionKey); taskDefinitionKey = OrchestrationChecks.definition(taskDefinitionKey);
        name = OrchestrationChecks.id(name, 512); kind = Objects.requireNonNull(kind, "kind"); version = OrchestrationChecks.hash(version); lane = OrchestrationChecks.definition(lane);
        availableLanes = List.copyOf(availableLanes); formSchema = OrchestrationChecks.schema(formSchema); createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (availableLanes.isEmpty() || availableLanes.size() > 8 || new HashSet<>(availableLanes).size() != availableLanes.size() || !availableLanes.contains(lane) || assignedToMe && !assigned
                || kind == HumanTaskKindEnum.APPROVAL && (approvalField == null || formSchema.get(approvalField) != ProcessValueTypeEnum.BOOLEAN)
                || kind == HumanTaskKindEnum.ACTION && approvalField != null) throw OrchestrationChecks.invalid();
        availableLanes.forEach(OrchestrationChecks::definition);
    }
    @Override public String toString() { return "HumanTask[taskId=" + taskId + ", scopeId=" + scopeId + ", kind=" + kind + "]"; }
}
