package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

@AutoConfiguration
@ConditionalOnProperty(prefix = "stellar.tianji.agent.reconciliation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentReconciliationProperties.class)
public class TianjiAgentReconciliationAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentRunReconciler.class)
    AgentRunReconciler tianjiAgentRunReconciler(AgentLedgerService ledger,
            @Qualifier("tianjiAgentReconciliationScheduler") TaskScheduler scheduler,
            AgentReconciliationProperties properties) {
        return new AgentRunReconciler(ledger, scheduler, properties);
    }
}
