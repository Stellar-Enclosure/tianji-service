package top.egon.cola.platform.tianji.agent.server.security;

import java.time.Clock;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLinkedSubmissionService;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.linked", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiLinkedDispatchProperties.class)
public class TianjiLinkedDispatchConfiguration {
    @Bean("tianjiLinkedDispatchBindings")
    TianjiLinkedDispatchBindings tianjiLinkedDispatchBindings(TianjiLinkedDispatchProperties properties,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_LINKED_REQUIRES_AGENT_API");
        }
        return new TianjiLinkedDispatchBindings(properties, issuers, Clock.systemUTC());
    }

    @Bean("agentLinkedSubmissionService")
    AgentLinkedSubmissionService agentLinkedSubmissionService(DataSource source, AgentLedgerService ledger) {
        return new AgentLinkedSubmissionService(source, ledger);
    }
}
