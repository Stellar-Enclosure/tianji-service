package top.egon.cola.platform.tianji.contract.agent;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

public record ExecutionPolicyDTO(Duration totalTimeout, Duration modelIdleTimeout, Duration toolTimeout,
                                 int maxModelCalls, int maxToolCalls, long maxInputTokens, long maxOutputTokens,
                                 BigDecimal maxEstimatedCost, String currency, int maxParallelism,
                                 Set<String> allowedToolVersions, String retryPolicyRef, String approvalPolicyRef) {
    public ExecutionPolicyDTO {
        PolicyChecks.duration(totalTimeout, "totalTimeout");
        PolicyChecks.duration(modelIdleTimeout, "modelIdleTimeout");
        PolicyChecks.duration(toolTimeout, "toolTimeout");
        if (maxModelCalls < 1 || maxToolCalls < 0 || maxInputTokens < 1 || maxOutputTokens < 1
                || maxParallelism < 1 || (maxEstimatedCost != null && maxEstimatedCost.signum() < 0)) {
            throw new IllegalArgumentException("execution policy limits are invalid");
        }
        currency = maxEstimatedCost == null ? ContractChecks.optional(currency, "currency", 3)
                : ContractChecks.required(currency, "currency", 3);
        allowedToolVersions = allowedToolVersions == null ? Set.of() : Set.copyOf(allowedToolVersions);
    }
}
