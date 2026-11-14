package top.egon.cola.platform.tianji.process.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.AbstractEngineConfiguration;
import org.flowable.common.engine.impl.EngineConfigurator;
import org.flowable.cmmn.engine.impl.parser.CmmnParserImpl;
import org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory;
import org.flowable.common.spring.SpringEngineConfiguration;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.platform.tianji.process.server.decision.PinnedDecisionActivityBehaviorFactory;
import top.egon.cola.platform.tianji.process.server.execution.AutomationBootstrapBehavior;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;
import top.egon.cola.platform.tianji.process.server.execution.ProcessInstanceBindingStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;
import top.egon.cola.platform.tianji.process.server.task.PinnedCaseActivityBehaviorFactory;

import javax.sql.DataSource;
import java.util.Collections;

/** Keeps Flowable and the JDBC Outbox on the same pre-provisioned local transaction. */
@Configuration(proxyBeanMethods = false)
public class ProcessStorageConfiguration {
    private static final String OUTBOX = "egon.cola.component.transactional-outbox.";

    @Bean
    static BeanFactoryPostProcessor processStoragePropertiesGuard(Environment environment) {
        return ignored -> {
            String schema = environment.getProperty("flowable.database-schema", "");
            String outboxSchema = environment.getProperty(OUTBOX + "storage.jdbc.schema", "egon_outbox");
            if (!"false".equalsIgnoreCase(environment.getProperty("flowable.database-schema-update", ""))
                    || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))
                    || environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                    || !environment.getProperty(OUTBOX + "enabled", Boolean.class, true)
                    || !"JDBC".equalsIgnoreCase(environment.getProperty(OUTBOX + "storage.type", ""))
                    || !environment.getProperty(OUTBOX + "storage.validate-schema", Boolean.class, true)
                    || !schema.matches("[a-z_][a-z0-9_]{0,62}") || !schema.equals(outboxSchema)) {
                throw new IllegalStateException("Process storage requires a pre-provisioned shared Flowable and JDBC Outbox schema");
            }
        };
    }

    @Bean
    SmartInitializingSingleton workerWebBoundaryGuard(Environment environment, ApplicationContext context) {
        return () -> {
            if (environment.getProperty("stellar.tianji.process.worker-recovery.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false)
                    || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Process Worker recovery requires the enabled servlet Process Worker API");
            }
            if (environment.getProperty("stellar.tianji.process.cancellation.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false) || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Process cancellation requires an enabled servlet Process API");
            }
            boolean cases = environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false);
            if (cases && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false) || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Case API requires an enabled servlet Process API");
            }
            if (environment.getProperty("stellar.tianji.process.cases.new-starts-enabled", Boolean.class, false)
                    && (!cases || !environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false))) {
                throw new IllegalStateException("New Case starts require the Case and human task APIs");
            }
            if (environment.getProperty("stellar.tianji.process.approval-verification.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false) || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Approval verification requires an enabled servlet Process API");
            }
            if (environment.getProperty("stellar.tianji.process.human-tasks.publish-enabled", Boolean.class, false)
                    && !environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Governed task publication requires the human task API");
            }
            if (environment.getProperty("stellar.tianji.process.human-tasks.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Human task API requires an enabled servlet Process API");
            }
            if (environment.getProperty("stellar.tianji.process.worker.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Worker API requires an enabled servlet Process API");
            }
            if (environment.getProperty("stellar.tianji.process.agent-results.enabled", Boolean.class, false)
                    && (!environment.getProperty("stellar.tianji.process.api.enabled", Boolean.class, false)
                    || !(context instanceof WebApplicationContext))) {
                throw new IllegalStateException("Agent result Inbox requires an enabled servlet Process API");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper outboxObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processStorageGuard(
            OutboxInfrastructure outbox, TransactionalOutboxProperties properties) {
        return config -> {
            verify(config, config, outbox, properties);
            if (config.getActivityBehaviorFactory() != null) {
                throw new IllegalStateException("Process Server requires its pinned decision task behavior");
            }
            config.setActivityBehaviorFactory(new PinnedDecisionActivityBehaviorFactory());
        };
    }

    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processStorageGuard(OutboxInfrastructure outbox, TransactionalOutboxProperties properties,
            Environment environment, ObjectProvider<FlowableCaseService> services,
            ObjectProvider<top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry> registries) {
        return config -> {
            verify(config, config, outbox, properties);
            if (config.getActivityBehaviorFactory() != null) {
                throw new IllegalStateException("Process Server requires its pinned decision task behavior");
            }
            config.setActivityBehaviorFactory(environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false)
                    ? new PinnedDecisionActivityBehaviorFactory(services::getObject, registries::getObject)
                    : new PinnedDecisionActivityBehaviorFactory());
        };
    }

    @Bean("processInstanceBindingStore")
    ProcessInstanceBindingStore processInstanceBindingStore(OutboxInfrastructure outbox, TransactionalOutboxProperties properties) {
        return new ProcessInstanceBindingStore(new JdbcTemplate(outbox.dataSource()), properties.getStorage().getJdbc().getSchema());
    }

    @Bean("automationBootstrapBehavior")
    AutomationBootstrapBehavior automationBootstrapBehavior(OutboxInfrastructure outbox, TransactionalOutboxProperties properties,
            @Qualifier("processInstanceBindingStore") ProcessInstanceBindingStore bindings) {
        var releaseStore = new AutomationReleaseStore(new JdbcTemplate(outbox.dataSource()), properties.getStorage().getJdbc().getSchema());
        return new AutomationBootstrapBehavior(bindings, releaseStore);
    }

    @Bean("processStorageGuard")
    @Order(Ordered.LOWEST_PRECEDENCE)
    EngineConfigurationConfigurer<SpringProcessEngineConfiguration> processStorageGuardWithAutomation(
            OutboxInfrastructure outbox, TransactionalOutboxProperties properties, Environment environment,
            ObjectProvider<FlowableCaseService> services,
            ObjectProvider<top.egon.cola.platform.tianji.process.server.bridge.CaseSourceRegistry> registries,
            @Qualifier("automationBootstrapBehavior") ObjectProvider<AutomationBootstrapBehavior> bootstrap) {
        var pinnedStorage = processStorageGuard(outbox, properties, environment, services, registries);
        return config -> {
            pinnedStorage.configure(config);
            AutomationBootstrapBehavior.register(config, bootstrap::getObject);
        };
    }

    EngineConfigurationConfigurer<SpringCmmnEngineConfiguration> caseStorageGuard(
            OutboxInfrastructure outbox, TransactionalOutboxProperties properties) {
        return config -> verify(config, config, outbox, properties);
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    EngineConfigurationConfigurer<SpringCmmnEngineConfiguration> caseStorageGuard(
            OutboxInfrastructure outbox, TransactionalOutboxProperties properties, Environment environment,
            ObjectProvider<FlowableCaseService> services) {
        return config -> {
            verify(config, config, outbox, properties);
            if (!environment.getProperty("stellar.tianji.process.cases.enabled", Boolean.class, false)) return;
            config.addConfigurator(new EngineConfigurator() {
                @Override public void beforeInit(AbstractEngineConfiguration engine) { }
                @Override public int getPriority() { return Integer.MAX_VALUE; }
                @Override public void configure(AbstractEngineConfiguration engine) {
                    if (engine != config || config.getExpressionManager() == null || config.getClassDelegateFactory() == null
                            || config.getActivityBehaviorFactory() == null || config.getActivityBehaviorFactory().getClass() != DefaultCmmnActivityBehaviorFactory.class
                            || !(config.getCmmnParser() instanceof CmmnParserImpl parser) || parser.getClass() != CmmnParserImpl.class
                            || parser.getActivityBehaviorFactory() != config.getActivityBehaviorFactory()) {
                        throw new IllegalStateException("Formal Case requires its initialized native parser and default behavior factory");
                    }
                    var factory = new PinnedCaseActivityBehaviorFactory(services::getObject);
                    factory.setExpressionManager(config.getExpressionManager()); factory.setClassDelegateFactory(config.getClassDelegateFactory());
                    config.setActivityBehaviorFactory(factory); parser.setActivityBehaviorFactory(factory);
                    if (config.getActivityBehaviorFactory() != factory || parser.getActivityBehaviorFactory() != factory) {
                        throw new IllegalStateException("Formal Case behavior factory installation did not bind both native pointers");
                    }
                    PinnedCaseActivityBehaviorFactory.requireConfigured(config);
                }
            });
        };
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    EngineConfigurationConfigurer<SpringDmnEngineConfiguration> decisionStorageGuard(
            OutboxInfrastructure outbox, TransactionalOutboxProperties properties) {
        return config -> verify(config, config, outbox, properties);
    }

    private static void verify(AbstractEngineConfiguration engine, SpringEngineConfiguration spring,
                               OutboxInfrastructure outbox, TransactionalOutboxProperties properties) {
        DataSource source = outbox.dataSource();
        DataSource engineSource = engine.getDataSource();
        if (engineSource instanceof TransactionAwareDataSourceProxy proxy) {
            engineSource = proxy.getTargetDataSource();
        }
        String schema = properties.getStorage().getJdbc().getSchema();
        if (source != engineSource || spring.getTransactionManager() != outbox.transactionManager()
                || !(outbox.transactionManager() instanceof DataSourceTransactionManager manager)
                || manager.getDataSource() != source || !"false".equals(engine.getDatabaseSchemaUpdate())
                || !schema.equals(engine.getDatabaseSchema())) {
            throw new IllegalStateException("Flowable and Outbox must share one JDBC DataSource and transaction manager");
        }
        String currentSchema = new JdbcTemplate(source).queryForObject("SELECT current_schema()", String.class);
        if (!schema.equals(currentSchema)) {
            throw new IllegalStateException("Process DataSource search path does not select the configured schema");
        }
        // Imported models must not obtain arbitrary Spring beans through Flowable expressions.
        engine.setBeans(Collections.emptyMap());
    }
}
