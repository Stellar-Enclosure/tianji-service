package top.egon.cola.platform.tianji.contract.orchestration;

public record RecoverProcessWorkerCommand(String instanceId, String jobId, String commandId,
                                          String expectedVersion, String reason) {
    public RecoverProcessWorkerCommand {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        jobId = OrchestrationChecks.id(jobId, 255);
        commandId = OrchestrationChecks.command(commandId);
        expectedVersion = OrchestrationChecks.hash(expectedVersion);
        reason = OrchestrationChecks.id(reason, 512);
    }

    @Override
    public String toString() {
        return "RecoverProcessWorkerCommand[redacted]";
    }
}
