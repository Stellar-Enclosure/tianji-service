package top.egon.cola.platform.tianji.agent.server.integration.process;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
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
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;

import javax.sql.DataSource;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Map;

/** Opt-in publication only; authenticated delivery and the Process Inbox have their own activation gates. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.terminal-events", name = "enabled", havingValue = "true")
public class TianjiAgentTerminalOutboxConfiguration {
    private static final String PREFIX = "stellar.tianji.agent.terminal-events.";

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper tianjiAgentOutboxObjectMapper() { return new ObjectMapper().findAndRegisterModules(); }

    @Bean
    OutboxAgentTerminalEventPublisher tianjiAgentTerminalEventPublisher(DataSource source, OutboxInfrastructure infrastructure,
                                                                     TransactionalOutbox outbox, Environment environment,
                                                                     ObjectProvider<OpenTelemetry> telemetry) {
        var observation = environment.getProperty(AgentResultSpanObservation.FLAG, Boolean.class, false)
                ? AgentResultSpanObservation.enabled(telemetry::getIfUnique, () -> null)
                : AgentResultSpanObservation.disabled();
        return new OutboxAgentTerminalEventPublisher(source, infrastructure, outbox, environment.getProperty(PREFIX + "producer-id"),
                Binder.get(environment).bind(PREFIX + "destinations", Bindable.mapOf(String.class, String.class)).orElse(Map.of()),
                observation);
    }

    @Bean
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
