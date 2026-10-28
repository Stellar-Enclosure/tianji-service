package top.egon.cola.platform.tianji.agent.domain;

import java.math.BigDecimal;

public record AgentBudgetReservation(String reservationId, long leaseEpoch, AgentBudgetRequest.Kind kind,
                                     String toolVersion, long inputTokens, long outputTokens, BigDecimal estimatedCost,
                                     State state, Long actualInputTokens, Long actualOutputTokens,
                                     BigDecimal actualCost, boolean newlyCreated) {
    public enum State { RESERVED, SETTLED, UNKNOWN }
}
