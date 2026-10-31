package top.egon.cola.platform.tianji.agent.server.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;

/** Finite operator-owned tool authority settings; disabled by default. */
@ConfigurationProperties(prefix = "stellar.tianji.agent.tools.authority", ignoreUnknownFields = false)
public record TianjiToolAuthorityProperties(boolean enabled, Duration timeout, int concurrency,
        List<ToolDescriptor> descriptors, List<TianjiToolGrantBindings.BindingVO> bindings) {
    public TianjiToolAuthorityProperties {
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        concurrency = concurrency == 0 ? 4 : concurrency;
        descriptors = descriptors == null ? List.of() : List.copyOf(descriptors);
        bindings = bindings == null ? List.of() : List.copyOf(bindings);
        if (timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(10)) > 0
                || concurrency < 1 || concurrency > 8 || descriptors.size() > 256 || bindings.size() > 256
                || enabled && (descriptors.isEmpty() || bindings.isEmpty())) {
            throw new IllegalArgumentException("TIANJI_TOOL_AUTHORITY_CONFIGURATION_INVALID");
        }
    }
}
