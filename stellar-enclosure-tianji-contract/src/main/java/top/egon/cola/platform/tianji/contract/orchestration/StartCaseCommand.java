package top.egon.cola.platform.tianji.contract.orchestration;

/** commandId is idempotent within the verified tenant and actor. */
public record StartCaseCommand(String definitionKey, String releaseVersion, String businessKey,
                               String commandId, String correlationId) {
    public StartCaseCommand {
        definitionKey = OrchestrationChecks.definition(definitionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        businessKey = OrchestrationChecks.id(businessKey, 255);
        commandId = OrchestrationChecks.command(commandId);
        correlationId = OrchestrationChecks.command(correlationId);
    }
    @Override public String toString() { return "StartCaseCommand[redacted]"; }
}
