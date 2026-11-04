package top.egon.cola.platform.tianji.contract.orchestration;

public record CancelProcessCommand(String instanceId, String commandId, String expectedVersion, String reason) {
    public CancelProcessCommand {
        instanceId = OrchestrationChecks.id(instanceId, 255);
        commandId = OrchestrationChecks.command(commandId);
        expectedVersion = OrchestrationChecks.hash(expectedVersion);
        reason = OrchestrationChecks.id(reason, 512);
    }

    @Override
    public String toString() {
        return "CancelProcessCommand[redacted]";
    }
}
