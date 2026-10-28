package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Map;

/** requestId is idempotent within the verified tenant and actor. */
public record EvaluateDecisionCommand(String decisionKey, String releaseVersion, String requestId,
                                      String correlationId, Map<String, Object> inputs) {
    public EvaluateDecisionCommand {
        decisionKey = OrchestrationChecks.definition(decisionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        requestId = OrchestrationChecks.command(requestId);
        correlationId = OrchestrationChecks.command(correlationId);
        inputs = OrchestrationChecks.values(inputs);
    }
}
