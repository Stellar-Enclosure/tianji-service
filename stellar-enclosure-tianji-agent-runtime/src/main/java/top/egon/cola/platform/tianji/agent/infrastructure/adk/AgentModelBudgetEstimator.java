package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;

import java.math.BigDecimal;

/** Host-supplied provider adapter for conservative token and cost bounds and verified usage. */
public interface AgentModelBudgetEstimator {
    Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request);
    AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response);

    /**
     * Resolves the selected model's immutable route and policy versions. The estimate must cover
     * the complete provider request, including model-adapter additions, not just user text.
     * Existing adapters fail closed until they supply a context window.
     */
    default ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
        throw new UnsupportedOperationException("model context window is unavailable");
    }

    /**
     * Stable SHA-256 of the selected route's adapter mapping, model defaults and tokenizer/pricing policy.
     * Excludes credentials, transport headers and per-call request data; snapshots require this capability.
     */
    default String modelAdapterFingerprint(AgentExecutionSnapshot snapshot, LlmRequest request) {
        throw new UnsupportedOperationException("versioned model adapter identity is unavailable");
    }

    record Estimate(long inputTokens, int maxOutputTokens, BigDecimal maximumCost) {
        public Estimate {
            if (inputTokens < 0 || maxOutputTokens < 1) {
                throw new IllegalArgumentException("model budget estimate is invalid");
            }
        }
    }

    record ContextWindow(String modelName, String modelRouteRef, String policyVersionRef,
                         long windowTokens, long safetyMarginTokens) {
        public ContextWindow {
            if (!valid(modelName, 512) || !valid(modelRouteRef, 256) || !valid(policyVersionRef, 256)
                    || windowTokens < 2 || safetyMarginTokens < 1 || safetyMarginTokens >= windowTokens) {
                throw new IllegalArgumentException("model context window is invalid");
            }
        }

        private static boolean valid(String value, int maximum) {
            return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                    && value.chars().noneMatch(Character::isISOControl);
        }
    }
}
