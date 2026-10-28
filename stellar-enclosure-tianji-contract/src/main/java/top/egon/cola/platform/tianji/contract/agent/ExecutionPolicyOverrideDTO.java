package top.egon.cola.platform.tianji.contract.agent;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/** Overrides are requests to tighten policy; the service must intersect them with the effective policy. */
public record ExecutionPolicyOverrideDTO(Duration totalTimeout, Duration modelIdleTimeout, Duration toolTimeout,
                                         Integer maxModelCalls, Integer maxToolCalls, Long maxInputTokens,
                                         Long maxOutputTokens, BigDecimal maxEstimatedCost, Integer maxParallelism,
                                         Set<String> allowedToolVersions) {
    public ExecutionPolicyOverrideDTO {
        PolicyChecks.optionalDuration(totalTimeout, "totalTimeout");
        PolicyChecks.optionalDuration(modelIdleTimeout, "modelIdleTimeout");
        PolicyChecks.optionalDuration(toolTimeout, "toolTimeout");
        if ((maxModelCalls != null && maxModelCalls < 1) || (maxToolCalls != null && maxToolCalls < 0)
                || (maxInputTokens != null && maxInputTokens < 1) || (maxOutputTokens != null && maxOutputTokens < 1)
                || (maxEstimatedCost != null && maxEstimatedCost.signum() < 0)
                || (maxParallelism != null && maxParallelism < 1)) {
            throw new IllegalArgumentException("execution policy override limits are invalid");
        }
        allowedToolVersions = allowedToolVersions == null ? null : Set.copyOf(allowedToolVersions);
    }
}
