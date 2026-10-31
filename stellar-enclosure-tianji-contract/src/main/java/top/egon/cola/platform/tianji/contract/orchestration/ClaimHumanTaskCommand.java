package top.egon.cola.platform.tianji.contract.orchestration;

public record ClaimHumanTaskCommand(String commandId, String taskId, String expectedVersion) {
    public ClaimHumanTaskCommand {
        commandId = OrchestrationChecks.command(commandId); taskId = OrchestrationChecks.id(taskId, 255); expectedVersion = OrchestrationChecks.hash(expectedVersion);
    }
    @Override public String toString() { return "ClaimHumanTaskCommand[redacted]"; }
}
