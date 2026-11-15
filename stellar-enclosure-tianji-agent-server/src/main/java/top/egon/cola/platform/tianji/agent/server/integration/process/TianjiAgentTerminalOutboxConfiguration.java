package top.egon.cola.platform.tianji.agent.server.integration.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;

import javax.sql.DataSource;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;

/** Opt-in publication only; authenticated delivery and the Process Inbox have their own activation gates. */
@Configuration(proxyBeanMethods = false)
public class TianjiAgentTerminalOutboxConfiguration {
    private static final String PREFIX = "stellar.tianji.agent.terminal-events.";
    private static final String ENABLED = PREFIX + "enabled";

    @Bean("tianjiRuntimeV2TerminalBindingGuard")
    @ConditionalOnProperty(name = "stellar.tianji.agent.runtime-v2.enabled", havingValue = "true")
    SmartInitializingSingleton tianjiRuntimeV2TerminalBindingGuard(Environment environment,
            @Qualifier("tianjiAgentTerminalEventPublisher") ObjectProvider<OutboxAgentTerminalEventPublisher> publishers,
            ObjectProvider<AgentLedgerService> ledgers) {
        return () -> {
            var publisher = publishers.getIfAvailable();
            var ledger = ledgers.getIfAvailable();
            if (!environment.getProperty(ENABLED, Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.result-delivery.enabled", Boolean.class, false)
                    || publisher == null || ledger == null || !ledger.hasTerminalPublisher(publisher)) {
                throw new IllegalStateException("Agent Runtime v2 admission requires the matching terminal Outbox and current delivery route");
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = ENABLED, havingValue = "true")
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper tianjiAgentOutboxObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }

    @Bean("tianjiAgentTerminalEventPublisher")
    @ConditionalOnProperty(name = ENABLED, havingValue = "true")
    OutboxAgentTerminalEventPublisher tianjiAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure,
                                                                     TransactionalOutbox outbox, Environment environment,
                                                                     ObjectProvider<OpenTelemetry> telemetry,
                                                                     @Qualifier(TianjiAgentResultArtifactConfiguration.VERIFIER)
                                                                     ObjectProvider<AgentResultArtifactVerifier> artifacts,
                                                                     @Qualifier("tianjiAgentResultDeliveryHandler")
                                                                     ObjectProvider<AgentResultDeliveryHandler> delivery) {
        var observation = environment.getProperty(AgentResultSpanObservation.FLAG, Boolean.class, false)
                ? AgentResultSpanObservation.enabled(telemetry::getIfUnique, () -> null)
                : AgentResultSpanObservation.disabled();
        return new OutboxAgentTerminalEventPublisher(source, infrastructure, outbox, environment.getProperty(PREFIX + "producer-id"),
                Binder.get(environment).bind(PREFIX + "destinations", Bindable.mapOf(String.class, String.class)).orElse(Map.of()),
                observation, artifacts.getIfAvailable(), route -> {
                    var handler = delivery.getIfAvailable();
                    return handler != null && handler.hasRuntimeRoute(route);
                });
    }

    @Bean("tianjiAgentTerminalOutboxGuard")
    @ConditionalOnProperty(name = ENABLED, havingValue = "true")
    SmartInitializingSingleton tianjiAgentTerminalOutboxGuard(DataSource source, AgentLedgerService ledger,
                                                              OutboxAgentTerminalEventPublisher publisher,
                                                              TransactionalOutboxProperties properties, DeliveryHandlerRegistry handlers,
                                                              Environment environment) {
        return () -> {
            if (!environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                    || !environment.getProperty("egon.cola.component.transactional-outbox.enabled", Boolean.class, false)
                    || !"JDBC".equalsIgnoreCase(environment.getProperty("egon.cola.component.transactional-outbox.storage.type", ""))
                    || !properties.getStorage().isValidateSchema() || !"tianji_agent".equals(properties.getStorage().getJdbc().getSchema())
                    || environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                    || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))
                    || !ledger.usesDataSource(source) || !ledger.hasTerminalPublisher(publisher)) {
                throw new IllegalStateException("Agent terminal publication requires the matching ledger and pre-provisioned JDBC Outbox");
            }
            new AgentStorageVerifier(source).verifyLinked();
            var handler = handlers.required(OutboxAgentTerminalEventPublisher.CHANNEL);
            Binder.get(environment).bind(PREFIX + "destinations", Bindable.mapOf(String.class, String.class)).orElse(Map.of())
                    .forEach((sourceId, destination) -> {
                        handler.validateDestination(destination);
                        if (handler instanceof AgentResultDeliveryHandler results) results.validateSourceDestination(sourceId, destination);
                    });
        };
    }
}
