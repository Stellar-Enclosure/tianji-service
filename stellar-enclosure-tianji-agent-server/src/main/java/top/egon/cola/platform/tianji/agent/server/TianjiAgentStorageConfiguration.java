package top.egon.cola.platform.tianji.agent.server;

import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;

/** Opts the standalone host into a pre-provisioned Agent database. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.storage", name = "enabled", havingValue = "true")
@Import(DataSourceAutoConfiguration.class)
public class TianjiAgentStorageConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentLedgerService.class)
    AgentLedgerService tianjiAgentServerLedgerService(DataSource dataSource, ObjectProvider<AgentTerminalEventPublisher> publishers) {
        AgentTerminalEventPublisher publisher = publishers.getIfAvailable();
        return publisher == null ? new AgentLedgerService(dataSource) : AgentLedgerService.withTerminalEvents(dataSource, publisher);
    }

    @Bean
    @Lazy(false)
    SmartInitializingSingleton tianjiAgentStorageGuard(DataSource dataSource, AgentLedgerService ledger) {
        return () -> {
            if (!ledger.usesDataSource(dataSource)) throw new IllegalStateException("TIANJI_AGENT_STORAGE_DATA_SOURCE_MISMATCH");
            new AgentStorageVerifier(dataSource).verify();
        };
    }
}
