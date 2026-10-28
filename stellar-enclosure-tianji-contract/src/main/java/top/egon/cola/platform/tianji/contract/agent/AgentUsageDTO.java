package top.egon.cola.platform.tianji.contract.agent;

import java.math.BigDecimal;
import java.util.Objects;

/** Missing counters and cost remain null rather than being represented as zero. */
public record AgentUsageDTO(AgentUsageKindEnum kind, Long inputTokens, Long outputTokens, Long totalTokens,
                            BigDecimal cost, String currency, String priceVersionRef) {
    public AgentUsageDTO {
        kind = Objects.requireNonNull(kind, "kind");
        if ((inputTokens != null && inputTokens < 0) || (outputTokens != null && outputTokens < 0)
                || (totalTokens != null && totalTokens < 0) || (cost != null && cost.signum() < 0)) {
            throw new IllegalArgumentException("usage must not be negative");
        }
        currency = cost == null ? ContractChecks.optional(currency, "currency", 3)
                : ContractChecks.required(currency, "currency", 3);
        priceVersionRef = ContractChecks.optional(priceVersionRef, "priceVersionRef", 256);
    }
}
