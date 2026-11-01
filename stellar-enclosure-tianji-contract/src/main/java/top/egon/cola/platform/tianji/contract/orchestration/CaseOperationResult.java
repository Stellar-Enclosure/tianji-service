package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

public record CaseOperationResult(String commandId, String caseInstanceId, String planItemInstanceId,
                                  String planItemDefinitionKey, CasePlanItemActionEnum action,
                                  String releaseVersion, String definitionKey,
                                  CasePlanItemStateEnum previousState, String previousVersion,
                                  CasePlanItemStateEnum resultingState, String resultingVersion,
                                  Instant occurredAt) {
    public CaseOperationResult {
        commandId = OrchestrationChecks.command(commandId);
        caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
        planItemInstanceId = OrchestrationChecks.id(planItemInstanceId, 255);
        planItemDefinitionKey = OrchestrationChecks.definition(planItemDefinitionKey);
        action = Objects.requireNonNull(action, "action");
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        definitionKey = OrchestrationChecks.definition(definitionKey);
        previousState = Objects.requireNonNull(previousState, "previousState");
        previousVersion = OrchestrationChecks.hash(previousVersion);
        resultingState = Objects.requireNonNull(resultingState, "resultingState");
        resultingVersion = OrchestrationChecks.hash(resultingVersion);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        boolean validTransition = switch (action) {
            case DISABLE -> previousState == CasePlanItemStateEnum.ENABLED
                    && resultingState == CasePlanItemStateEnum.DISABLED;
            case ENABLE -> previousState == CasePlanItemStateEnum.DISABLED
                    && resultingState == CasePlanItemStateEnum.ENABLED;
            case START -> previousState == CasePlanItemStateEnum.ENABLED
                    && resultingState == CasePlanItemStateEnum.ACTIVE;
            case TERMINATE -> previousState == CasePlanItemStateEnum.ACTIVE
                    && resultingState == CasePlanItemStateEnum.TERMINATED;
        };
        if (!validTransition || previousVersion.equals(resultingVersion)) throw OrchestrationChecks.invalid();
    }
    @Override public String toString() {
        return "CaseOperationResult[caseInstanceId=" + caseInstanceId + ", planItemInstanceId="
                + planItemInstanceId + ", action=" + action + "]";
    }
}
