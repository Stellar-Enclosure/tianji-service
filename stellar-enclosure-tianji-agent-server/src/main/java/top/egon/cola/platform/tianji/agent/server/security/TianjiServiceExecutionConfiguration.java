package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentServiceExecutionGrantStore;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.service-execution", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiServiceExecutionProperties.class)
public class TianjiServiceExecutionConfiguration {
    @Bean
    TianjiServiceExecutionBindings tianjiServiceExecutionBindings(TianjiServiceExecutionProperties properties,
            TianjiLinkedDispatchProperties linked, TrustedIssuerRegistry issuers, Environment environment) {
        requirePrerequisites(environment);
        return new TianjiServiceExecutionBindings(properties, linked, issuers);
    }

    @Bean
    AgentServiceExecutionGrantStore agentServiceExecutionGrantStore(DataSource source, AgentLedgerService ledger, Environment environment) {
        requirePrerequisites(environment);
        var store = new AgentServiceExecutionGrantStore(source, ledger);
        store.requireSchema();
        return store;
    }

    @Bean
    TianjiServiceExecutionGrantIssuer tianjiServiceExecutionGrantIssuer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth, @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        return new TianjiServiceExecutionGrantIssuer(ledger, store, bindings, starter, authority, issuers, oauth, mapper);
    }

    @Bean
    TianjiServiceExecutionGrantAuthorizer tianjiServiceExecutionGrantAuthorizer(AgentLedgerService ledger, AgentServiceExecutionGrantStore store,
            TianjiServiceExecutionBindings bindings, @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth, @Qualifier("jacksonObjectMapper") ObjectMapper mapper,
            Rbac3StarterProperties rbac) {
        return new TianjiServiceExecutionGrantAuthorizer(ledger, store, bindings, starter, authority, issuers, oauth, mapper, rbac);
    }

    private static void requirePrerequisites(Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.linked.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.execution-grants.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.execution-authorization.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_SERVICE_EXECUTION_PREREQUISITES_REQUIRED");
        }
    }
}
