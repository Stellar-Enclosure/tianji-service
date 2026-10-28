package top.egon.cola.platform.tianji.contract.agent;

import java.util.Map;
import java.util.Objects;

public record AgentExecutionResult(String runId, AgentRunStatusEnum status, Map<String, Object> output,
                                   String artifactRef, String outputSchemaVersion, AgentUsageDTO usage,
                                   String errorCode, String sideEffectOutcome) {
    public AgentExecutionResult {
        runId = ContractChecks.required(runId, "runId", 128);
        status = Objects.requireNonNull(status, "status");
        output = ContractChecks.jsonObject(output);
        artifactRef = ContractChecks.optional(artifactRef, "artifactRef", 1024);
        outputSchemaVersion = ContractChecks.optional(outputSchemaVersion, "outputSchemaVersion", 64);
    }
}
