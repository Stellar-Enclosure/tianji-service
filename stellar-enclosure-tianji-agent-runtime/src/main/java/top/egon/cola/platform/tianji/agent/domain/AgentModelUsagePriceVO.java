package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Immutable internal tariff for one exact model route. */
public record AgentModelUsagePriceVO(String routeRef, String modelName, String currency,
                                    BigDecimal inputRatePerMillion, BigDecimal outputRatePerMillion) {
    private static final String DOMAIN = "TIANJI_MODEL_USAGE_PRICE_V1";
    private static final String ALGORITHM = "PER_MILLION_CEILING_8";
    private static final int INTEGER_DIGITS = 24;
    private static final int FRACTION_DIGITS = 8;

    public AgentModelUsagePriceVO {
        if (!validText(routeRef, 256) || !validText(modelName, 512) || currency == null || !currency.matches("[A-Z]{3}")) {
            throw invalid();
        }
        inputRatePerMillion = normalize(inputRatePerMillion);
        outputRatePerMillion = normalize(outputRatePerMillion);
    }

    public String priceVersionRef() {
        return OrchestrationRequestDigests.hash(List.of(DOMAIN, routeRef, modelName, currency,
                inputRatePerMillion.toPlainString(), outputRatePerMillion.toPlainString(), ALGORITHM), Map.of());
    }

    private static BigDecimal normalize(BigDecimal rate) {
        if (rate == null || rate.signum() < 0) throw invalid();
        if (rate.signum() == 0) return BigDecimal.ZERO;
        if ((long) rate.precision() - rate.scale() > INTEGER_DIGITS) throw invalid();
        BigDecimal normalized = rate.stripTrailingZeros();
        if (normalized.scale() > FRACTION_DIGITS) throw invalid();
        return normalized;
    }

    private static boolean validText(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Model usage price is invalid");
    }
}
