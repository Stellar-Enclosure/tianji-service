package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Host-owned model metadata, bound only while native templates are explicitly enabled. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.native-templates", ignoreUnknownFields = false)
public record NativeAgentTemplateProperties(boolean enabled, String modelBeanName,
                                            String modelName, String modelRouteRef) {
    public NativeAgentTemplateProperties {
        if (!enabled || !safe(modelBeanName, 64) || !safe(modelName, 128) || !safe(modelRouteRef, 256)
                || !modelRouteRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}@[1-9][0-9]{0,18}#[0-9a-f]{64}")) {
            throw new IllegalArgumentException("TIANJI_NATIVE_TEMPLATE_CONFIGURATION_INVALID");
        }
    }

    private static boolean safe(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim())) return false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) || Character.isLowSurrogate(current)) return false;
            if (Character.isHighSurrogate(current)
                    && (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index)))) return false;
        }
        return true;
    }
}
