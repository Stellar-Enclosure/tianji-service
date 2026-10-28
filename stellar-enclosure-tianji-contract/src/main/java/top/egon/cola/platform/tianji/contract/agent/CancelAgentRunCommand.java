package top.egon.cola.platform.tianji.contract.agent;

public record CancelAgentRunCommand(String runId, String commandId, long expectedRevision, String reason) {
    public CancelAgentRunCommand {
        runId = ContractChecks.required(runId, "runId", 128);
        commandId = ContractChecks.required(commandId, "commandId", 128);
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision must not be negative");
        }
        reason = ContractChecks.required(reason, "reason", 512);
    }
}
