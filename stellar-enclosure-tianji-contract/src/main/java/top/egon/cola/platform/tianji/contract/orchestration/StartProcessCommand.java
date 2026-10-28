package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;

/** commandId is idempotent within the verified tenant and actor. */
public record StartProcessCommand(String definitionKey, String releaseVersion, String businessKey,
                                  String commandId, String correlationId, Map<String, Object> variables) {
    public StartProcessCommand {
        definitionKey = OrchestrationChecks.definition(definitionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        businessKey = OrchestrationChecks.id(businessKey, 255);
        commandId = OrchestrationChecks.command(commandId);
        correlationId = OrchestrationChecks.command(correlationId);
        variables = OrchestrationChecks.values(variables);
    }
}
