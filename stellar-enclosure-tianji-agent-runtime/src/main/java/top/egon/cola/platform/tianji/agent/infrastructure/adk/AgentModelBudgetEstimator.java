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

    record Estimate(long inputTokens, int maxOutputTokens, BigDecimal maximumCost) {
        public Estimate {
            if (inputTokens < 0 || maxOutputTokens < 1) {
                throw new IllegalArgumentException("model budget estimate is invalid");
            }
        }
    }
}
