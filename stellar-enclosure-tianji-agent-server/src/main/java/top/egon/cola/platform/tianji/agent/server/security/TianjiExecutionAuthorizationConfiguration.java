package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.execution-authorization", name = "enabled", havingValue = "true")
public class TianjiExecutionAuthorizationConfiguration {
    @Bean(destroyMethod = "close")
    TianjiExecutionGrantAuthorizer tianjiExecutionGrantAuthorizer(AgentLedgerService ledger, AgentExecutionGrantStore store,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, Rbac3StarterProperties rbac, Environment environment,
            ObjectProvider<TianjiServiceExecutionGrantAuthorizer> serviceAuthorizers, ObjectProvider<TianjiMemoryRunAuthorization> memoryAuthorizers, @Qualifier("tianjiToolGrantAuthorizer") ObjectProvider<TianjiToolGrantAuthorizer> toolAuthorizers) {
        if (!environment.getProperty("stellar.tianji.agent.execution-grants.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_EXECUTION_AUTHORIZATION_REQUIRES_GRANTS");
        }
        long timeoutMillis = environment.getProperty("stellar.tianji.agent.execution-authorization.timeout-ms", Long.class, 10_000L);
        int concurrency = environment.getProperty("stellar.tianji.agent.execution-authorization.concurrency", Integer.class, 4);
        TianjiServiceExecutionGrantAuthorizer service = null;
        if (environment.getProperty("stellar.tianji.agent.service-execution.enabled", Boolean.class, false)) {
            service = serviceAuthorizers.getIfAvailable();
            if (service == null) throw new IllegalStateException("TIANJI_SERVICE_EXECUTION_AUTHORIZER_REQUIRED");
        }
        TianjiMemoryRunAuthorization memory = null;
        if (environment.getProperty("stellar.tianji.agent.context.memory.enabled", Boolean.class, false)) {
            memory = memoryAuthorizers.getIfAvailable();
            if (memory == null) throw new IllegalStateException("TIANJI_MEMORY_EXECUTION_AUTHORIZER_REQUIRED");
        }
        boolean toolsEnabled = environment.getProperty("stellar.tianji.agent.tools.authority.enabled", Boolean.class, false);
        var tools = toolsEnabled && toolAuthorizers != null ? toolAuthorizers.getIfAvailable() : null;
        if (toolsEnabled && tools == null) throw new IllegalStateException("TIANJI_TOOL_AUTHORIZER_REQUIRED");
        return new TianjiExecutionGrantAuthorizer(ledger, store, starter, authority, issuers, oauth, mapper, rbac,
                Duration.ofMillis(timeoutMillis), concurrency, service, memory, tools);
    }
}
