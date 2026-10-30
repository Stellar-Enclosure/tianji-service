package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.merge-suggestions.http", name = "enabled", havingValue = "true")
public class TianjiMemoryMergeConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryMergePrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.memory.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.memory.http.enabled", Boolean.class, false)) throw new IllegalStateException("Memory comparison requires the verified API and Memory HTTP/storage");
        };
    }
}
