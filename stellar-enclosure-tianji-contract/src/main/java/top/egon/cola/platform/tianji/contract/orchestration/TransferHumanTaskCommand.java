package top.egon.cola.platform.tianji.contract.orchestration;

/** An administrator selects a release-approved queue; this is not an arbitrary user/role assignment API. */
public record TransferHumanTaskCommand(String commandId, String taskId, String expectedVersion, String targetLane) {
    public TransferHumanTaskCommand {
        commandId = OrchestrationChecks.command(commandId); taskId = OrchestrationChecks.id(taskId, 255); expectedVersion = OrchestrationChecks.hash(expectedVersion);
        targetLane = OrchestrationChecks.definition(targetLane);
    }
    @Override public String toString() { return "TransferHumanTaskCommand[redacted]"; }
}
