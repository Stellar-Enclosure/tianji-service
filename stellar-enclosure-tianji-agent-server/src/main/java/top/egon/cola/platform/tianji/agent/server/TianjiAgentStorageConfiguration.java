package top.egon.cola.platform.tianji.agent.server;

import javax.sql.DataSource;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;

/** Opts the standalone host into a pre-provisioned Agent database. */
@Configuration(proxyBeanMethods = false)
@Import(TianjiAgentStorageConfiguration.StorageDataSourceImport.class)
public class TianjiAgentStorageConfiguration {
    private static final String STORAGE = "stellar.tianji.agent.storage";

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = STORAGE, name = "enabled", havingValue = "true")
    @Import(DataSourceAutoConfiguration.class)
    static class StorageDataSourceImport { }

    @Bean
    @ConditionalOnProperty(prefix = STORAGE, name = "model-usage-enabled", havingValue = "true")
    static BeanFactoryPostProcessor tianjiAgentModelUsagePrerequisite(Environment environment) {
        return beanFactory -> {
            if (!environment.getProperty(STORAGE + ".enabled", Boolean.class, false)) {
                throw new IllegalStateException("TIANJI_AGENT_MODEL_USAGE_REQUIRES_STORAGE");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = STORAGE, name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(AgentLedgerService.class)
    AgentLedgerService tianjiAgentServerLedgerService(DataSource dataSource, ObjectProvider<AgentTerminalEventPublisher> publishers,
                                                      Environment environment) {
        AgentTerminalEventPublisher publisher = publishers.getIfAvailable();
        if (modelUsageEnabled(environment)) {
            return AgentLedgerService.withModelUsage(dataSource, publisher, reviewedRoutes(environment));
        }
        return publisher == null ? new AgentLedgerService(dataSource) : AgentLedgerService.withTerminalEvents(dataSource, publisher);
    }

    @Bean
    @Lazy(false)
    @ConditionalOnProperty(prefix = STORAGE, name = "enabled", havingValue = "true")
    SmartInitializingSingleton tianjiAgentStorageGuard(DataSource dataSource, AgentLedgerService ledger, Environment environment) {
        return () -> {
            if (!ledger.usesDataSource(dataSource)) throw new IllegalStateException("TIANJI_AGENT_STORAGE_DATA_SOURCE_MISMATCH");
            boolean enabled = modelUsageEnabled(environment);
            if (enabled ? !ledger.usesModelUsageRoutes(reviewedRoutes(environment)) : ledger.modelUsageEnabled()) {
                throw new IllegalStateException("TIANJI_AGENT_MODEL_USAGE_CONFIGURATION_MISMATCH");
            }
            new AgentStorageVerifier(dataSource).verify();
        };
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "stellar.tianji.agent",
            name = {"storage.enabled", "storage.model-usage-enabled", "api.enabled"}, havingValue = "true")
    AgentModelUsageDiagnostics tianjiAgentModelUsageDiagnostics(AgentLedgerService ledger, TianjiAgentAccess access) {
        return new AgentModelUsageDiagnostics(ledger, access);
    }

    private static boolean modelUsageEnabled(Environment environment) {
        return environment.getProperty(STORAGE + ".model-usage-enabled", Boolean.class, false);
    }

    private static List<String> reviewedRoutes(Environment environment) {
        return Binder.get(environment).bind(STORAGE + ".model-usage-cumulative-route-refs", Bindable.listOf(String.class))
                .orElse(List.of());
    }
}
