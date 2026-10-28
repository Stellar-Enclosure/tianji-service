package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Keeps the not-yet-implemented runtime closed until its required services are available. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime", name = "enabled", havingValue = "true")
public class TianjiAgentRuntimeAutoConfiguration {

    @Bean
    Object tianjiAgentRuntimeUnavailable() {
        throw new IllegalStateException("Tianji Agent Runtime is not implemented: persistence and trusted identity integration are required");
    }
}
