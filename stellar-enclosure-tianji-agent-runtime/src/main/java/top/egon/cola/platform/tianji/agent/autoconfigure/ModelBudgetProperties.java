package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/** Operator-reviewed formatting allowances and accounting tariffs are versioned data, not live provider pricing. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.model-budgets")
public record ModelBudgetProperties(List<Profile> profiles) {
    public ModelBudgetProperties {
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        if (profiles.size() > 256) throw invalid();
    }

    public record Profile(String routeKey, long routeVersion, String policyKey, long policyVersion, String modelBeanName, String modelName,
                           long windowTokens, long safetyMarginTokens, int maxOutputTokens, int messageOverheadTokens, int requestOverheadTokens,
                           int toolOverheadTokens, BigDecimal inputRatePerMillion, BigDecimal outputRatePerMillion, String currency,
                           int maxTrimSteps, int retainRecentUserTurns, boolean trimEvidence, boolean trimHistory) {
        public Profile {
            key(routeKey); key(policyKey); key(modelBeanName);
            if (routeVersion < 1 || policyVersion < 1 || modelName == null || modelName.isBlank() || !modelName.equals(modelName.trim())
                    || modelName.length() > 128 || windowTokens < 2 || safetyMarginTokens < 1 || safetyMarginTokens >= windowTokens
                    || maxOutputTokens < 1 || maxOutputTokens > windowTokens - safetyMarginTokens || inputRatePerMillion == null || outputRatePerMillion == null
                    || !rate(inputRatePerMillion) || !rate(outputRatePerMillion)
                    || currency == null || !currency.matches("[A-Z]{3}")) throw invalid();
            messageOverheadTokens = messageOverheadTokens == 0 ? 8 : messageOverheadTokens;
            requestOverheadTokens = requestOverheadTokens == 0 ? 16 : requestOverheadTokens;
            toolOverheadTokens = toolOverheadTokens == 0 ? 32 : toolOverheadTokens;
            maxTrimSteps = maxTrimSteps == 0 ? 32 : maxTrimSteps;
            retainRecentUserTurns = retainRecentUserTurns == 0 ? 1 : retainRecentUserTurns;
            if (messageOverheadTokens < 1 || messageOverheadTokens > 1024 || requestOverheadTokens < 1 || requestOverheadTokens > 4096
                    || toolOverheadTokens < 1 || toolOverheadTokens > 4096 || maxTrimSteps < 1 || maxTrimSteps > 64
                    || retainRecentUserTurns < 1 || retainRecentUserTurns > 32) throw invalid();
        }
    }
    private static boolean rate(BigDecimal value) { return value.signum() >= 0 && value.scale() >= -6 && value.scale() <= 8 && value.precision() <= 18; }
    private static void key(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}")) throw invalid(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Versioned model budget profiles are invalid"); }
}
