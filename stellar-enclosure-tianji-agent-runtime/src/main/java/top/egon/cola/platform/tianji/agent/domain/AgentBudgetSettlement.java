package top.egon.cola.platform.tianji.agent.domain;

import java.math.BigDecimal;

/** Null counters or cost mean unknown; they never release their reserved allowance. */
public record AgentBudgetSettlement(Long inputTokens, Long outputTokens, BigDecimal actualCost) {
    public AgentBudgetSettlement {
        if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)) {
            throw new IllegalArgumentException("actual token counts must not be negative");
        }
        AgentBudgetRequest.checkCost(actualCost);
    }
}
