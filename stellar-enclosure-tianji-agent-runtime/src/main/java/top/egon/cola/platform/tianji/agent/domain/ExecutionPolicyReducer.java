package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/** Applies only restrictions requested by a caller; approval and retry policy remain server-owned. */
public final class ExecutionPolicyReducer {
    private ExecutionPolicyReducer() {
    }

    public static ExecutionPolicyDTO reduce(ExecutionPolicyDTO policy, ExecutionPolicyOverrideDTO override) {
        if (override == null) {
            return policy;
        }
        Set<String> allowedTools = override.allowedToolVersions() == null
                ? policy.allowedToolVersions() : override.allowedToolVersions();
        if (!policy.allowedToolVersions().containsAll(allowedTools)) {
            throw invalid();
        }
        BigDecimal cost = override.maxEstimatedCost() == null ? policy.maxEstimatedCost() : override.maxEstimatedCost();
        if ((cost != null && policy.currency() == null)
                || (policy.maxEstimatedCost() != null && cost.compareTo(policy.maxEstimatedCost()) > 0)) {
            throw invalid();
        }
        return new ExecutionPolicyDTO(
                duration(policy.totalTimeout(), override.totalTimeout()),
                duration(policy.modelIdleTimeout(), override.modelIdleTimeout()),
                duration(policy.toolTimeout(), override.toolTimeout()),
                integer(policy.maxModelCalls(), override.maxModelCalls()),
                integer(policy.maxToolCalls(), override.maxToolCalls()),
                number(policy.maxInputTokens(), override.maxInputTokens()),
                number(policy.maxOutputTokens(), override.maxOutputTokens()),
                cost, policy.currency(), integer(policy.maxParallelism(), override.maxParallelism()),
                allowedTools, policy.retryPolicyRef(), policy.approvalPolicyRef());
    }

    private static Duration duration(Duration current, Duration requested) {
        if (requested != null && requested.compareTo(current) > 0) {
            throw invalid();
        }
        return requested == null ? current : requested;
    }

    private static int integer(int current, Integer requested) {
        if (requested != null && requested > current) {
            throw invalid();
        }
        return requested == null ? current : requested;
    }

    private static long number(long current, Long requested) {
        if (requested != null && requested > current) {
            throw invalid();
        }
        return requested == null ? current : requested;
    }

    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND,
                "execution policy override cannot expand the allowed limits");
    }
}
