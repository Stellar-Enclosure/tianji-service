package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentExecutionGrantStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.execution-grants", name = "enabled", havingValue = "true")
public class TianjiExecutionGrantConfiguration {
    @Bean
    AgentExecutionGrantStore tianjiExecutionGrantStore(DataSource dataSource, AgentLedgerService ledger, Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_EXECUTION_GRANT_REQUIRES_AGENT_API");
        }
        var store = new AgentExecutionGrantStore(dataSource, ledger);
        new AgentStorageVerifier(dataSource).verify();
        return store;
    }

    @Bean
    TianjiExecutionGrantIssuer tianjiExecutionGrantIssuer(AgentLedgerService ledger, AgentExecutionGrantStore store,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, Environment environment, ObjectProvider<TianjiMemoryGrantIssuer> memoryIssuers, ObjectProvider<TianjiMemoryPreferenceGrantIssuer> preferenceIssuers, @Qualifier("tianjiToolGrantIssuer") ObjectProvider<TianjiToolGrantIssuer> toolIssuers) {
        var memory = environment.getProperty("stellar.tianji.agent.context.memory.enabled", Boolean.class, false) ? memoryIssuers.getIfAvailable() : null;
        if (environment.getProperty("stellar.tianji.agent.context.memory.enabled", Boolean.class, false) && memory == null) throw new IllegalStateException("TIANJI_MEMORY_GRANT_ISSUER_REQUIRED");
        var preferences = environment.getProperty("stellar.tianji.agent.memory.extraction.background.enabled", Boolean.class, false) ? preferenceIssuers.getIfAvailable() : null;
        if (environment.getProperty("stellar.tianji.agent.memory.extraction.background.enabled", Boolean.class, false) && preferences == null) throw new IllegalStateException("TIANJI_MEMORY_PREFERENCE_GRANT_ISSUER_REQUIRED");
        boolean toolsEnabled = environment.getProperty("stellar.tianji.agent.tools.authority.enabled", Boolean.class, false);
        var tools = toolsEnabled && toolIssuers != null ? toolIssuers.getIfAvailable() : null;
        if (toolsEnabled && tools == null) throw new IllegalStateException("TIANJI_TOOL_GRANT_ISSUER_REQUIRED");
        return new TianjiExecutionGrantIssuer(ledger, store, starter, authority, issuers, oauth, mapper, memory, preferences, tools);
    }
}
