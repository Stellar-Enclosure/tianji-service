package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

public record ActOnPlanItemCommand(String commandId, String caseInstanceId, String planItemInstanceId,
                                   String expectedVersion, CasePlanItemActionEnum action) {
    public ActOnPlanItemCommand {
        commandId = OrchestrationChecks.command(commandId);
        caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
        planItemInstanceId = OrchestrationChecks.id(planItemInstanceId, 255);
        expectedVersion = OrchestrationChecks.hash(expectedVersion);
        action = Objects.requireNonNull(action, "action");
    }
    @Override public String toString() { return "ActOnPlanItemCommand[redacted]"; }
}
