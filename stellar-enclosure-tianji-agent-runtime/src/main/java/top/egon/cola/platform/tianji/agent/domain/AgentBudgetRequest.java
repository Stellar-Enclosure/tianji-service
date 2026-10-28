package top.egon.cola.platform.tianji.agent.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** A trusted upper-bound reservation made before a model or tool call. */
public record AgentBudgetRequest(String reservationId, Kind kind, String toolVersion, long inputTokens,
                                 long outputTokens, BigDecimal estimatedCost) {
    private static final BigDecimal MAX_COST_EXCLUSIVE = new BigDecimal("10000000000000000");
    public enum Kind { MODEL, TOOL }

    public AgentBudgetRequest {
        if (reservationId == null || reservationId.isBlank() || reservationId.length() > 128) {
            throw new IllegalArgumentException("reservationId is required and must be at most 128 characters");
        }
        reservationId = reservationId.trim();
        kind = Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.MODEL && toolVersion != null) || (kind == Kind.TOOL
                && (toolVersion == null || toolVersion.isBlank() || toolVersion.length() > 256))) {
            throw new IllegalArgumentException("budget tool version is invalid");
        }
        toolVersion = toolVersion == null ? null : toolVersion.trim();
        if (inputTokens < 0 || outputTokens < 0 || (kind == Kind.MODEL && outputTokens == 0)
                || (kind == Kind.TOOL && (inputTokens != 0 || outputTokens != 0))) {
            throw new IllegalArgumentException("budget token reservation is invalid");
        }
        checkCost(estimatedCost);
    }

    static void checkCost(BigDecimal cost) {
        if (cost != null && (cost.signum() < 0 || cost.scale() > 8 || cost.compareTo(MAX_COST_EXCLUSIVE) >= 0)) {
            throw new IllegalArgumentException("budget cost is invalid");
        }
    }
}
