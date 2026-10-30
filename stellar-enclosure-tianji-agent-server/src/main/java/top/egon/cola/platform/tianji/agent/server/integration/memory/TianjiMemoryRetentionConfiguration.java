package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryRetentionWork;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.retention", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiMemoryRetentionProperties.class)
public class TianjiMemoryRetentionConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryRetentionPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.memory.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.reconciliation.enabled", Boolean.class, false)) throw new IllegalStateException("Retention requires governed Memory and the existing maintenance scheduler");
        };
    }
    @Bean
    MemoryRetentionWork tianjiMemoryRetentionWork(DataSource source, PostgresMemoryService memory, OutboxMemoryIndexPublication publication, TianjiMemoryProperties storage, TianjiMemoryRetentionProperties properties) {
        return new MemoryRetentionWork(source, memory, publication, storage.origins().stream().map(origin -> new MemoryRetentionWork.Origin(origin.tenantId(), origin.issuerFingerprint())).toList(), properties.policyRefs(), Clock.systemUTC());
    }
    @Bean
    MemoryRetentionJob tianjiMemoryRetentionJob(MemoryRetentionWork work, @Qualifier("tianjiAgentReconciliationScheduler") TaskScheduler scheduler, TianjiMemoryRetentionProperties properties) {
        return new MemoryRetentionJob(work, scheduler, properties, Clock.systemUTC());
    }
}
