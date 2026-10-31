package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

public record ListHumanTasksQuery(HumanTaskScopeTypeEnum scopeType, HumanTaskSelectionEnum selection, String releaseVersion,
                                 String scopeId, String taskDefinitionKey, String afterTaskId, int limit) {
    public ListHumanTasksQuery {
        scopeType = Objects.requireNonNull(scopeType, "scopeType"); selection = Objects.requireNonNull(selection, "selection");
        if (releaseVersion != null) releaseVersion = OrchestrationChecks.release(releaseVersion);
        if (scopeId != null) scopeId = OrchestrationChecks.id(scopeId, 255);
        if (taskDefinitionKey != null) taskDefinitionKey = OrchestrationChecks.definition(taskDefinitionKey);
        if (afterTaskId != null) afterTaskId = OrchestrationChecks.id(afterTaskId, 255);
        if (limit < 1 || limit > 50) throw OrchestrationChecks.invalid();
    }
    @Override public String toString() { return "ListHumanTasksQuery[redacted]"; }
}
