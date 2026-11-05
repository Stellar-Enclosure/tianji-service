package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.math.BigDecimal;
import java.util.Optional;

/** Host-supplied provider adapter for conservative token and cost bounds and verified usage. */
public interface AgentModelBudgetEstimator {
    Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request);
    AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response);

    /** An exact tariff is optional and cannot be inferred from a usage or budget estimate. */
    default Optional<AgentModelUsagePriceVO> modelUsagePrice(AgentExecutionSnapshot snapshot, LlmRequest request) {
        return Optional.empty();
    }

    default AgentModelBudgetEstimator bindNative(AgentExecutionSnapshot snapshot, NativeModelBinding binding) {
        throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                "Native model budget binding is unavailable");
    }

    record NativeModelBinding(String codeRef, String modelBeanName, String modelName, ChatModel model) {
        public NativeModelBinding {
            if (!valid(codeRef, 256) || !valid(modelBeanName, 64) || !valid(modelName, 128) || model == null) {
                throw new IllegalArgumentException("Native model budget binding is invalid");
            }
        }

        @Override public String toString() {
            return "NativeModelBinding[codeRef=" + codeRef + ", modelBeanName=" + modelBeanName
                    + ", modelName=" + modelName + ", model=<redacted>]";
        }

        private static boolean valid(String value, int maximum) {
            return value != null && !value.isBlank() && value.length() <= maximum && value.equals(value.trim())
                    && value.chars().noneMatch(Character::isISOControl);
        }
    }

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

    /** Optional request trimming uses immutable data rules selected by the same published policy reference. */
    default ContextPolicy contextPolicy(AgentExecutionSnapshot snapshot, LlmRequest request) {
        throw new UnsupportedOperationException("versioned context selection policy is unavailable");
    }

    record ContextPolicy(String policyVersionRef, int maxTrimSteps, int retainRecentUserTurns,
                          boolean trimEvidence, boolean trimHistory) {
        public ContextPolicy {
            if (!ContextWindow.valid(policyVersionRef, 256) || maxTrimSteps < 1 || maxTrimSteps > 64
                    || retainRecentUserTurns < 1 || retainRecentUserTurns > 32) throw new IllegalArgumentException("context selection policy is invalid");
        }
        public String fingerprint() {
            return AdkSemanticJsonDigest.fingerprint("TIANJI_CONTEXT_SELECTION_V1", java.util.List.of(policyVersionRef,
                    Integer.toString(maxTrimSteps), Integer.toString(retainRecentUserTurns), Boolean.toString(trimEvidence), Boolean.toString(trimHistory)));
        }
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
