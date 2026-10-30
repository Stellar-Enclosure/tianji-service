package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.http", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiMemoryAccessProperties.class)
public class TianjiMemoryHttpConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryHttpPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.memory.enabled", Boolean.class, false)) throw new IllegalStateException("Memory HTTP requires the verified API and governed Memory storage");
        };
    }

    @Bean(name = "tianjiMemoryAuthorization")
    @ConditionalOnMissingBean(name = "tianjiMemoryAuthorization")
    MemoryAuthorization tianjiMemoryHttpAuthorization(TianjiAgentAccess access, TianjiMemoryAccessProperties properties) {
        return new TianjiMemoryHttpAuthorization(access, properties, Clock.systemUTC());
    }
}
