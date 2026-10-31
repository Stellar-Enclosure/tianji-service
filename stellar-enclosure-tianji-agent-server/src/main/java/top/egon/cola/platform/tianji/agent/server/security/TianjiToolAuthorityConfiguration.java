package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Opt-in verified tool authority and proof that the existing submission and run chains consume it. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.tools.authority", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiToolAuthorityProperties.class)
public class TianjiToolAuthorityConfiguration {
    @Bean("toolCapabilityStore")
    ToolCapabilityStore toolCapabilityStore(DataSource source, AgentLedgerService ledger, Environment environment) {
        requirePrerequisites(environment);
        var store = new ToolCapabilityStore(source, ledger); store.verifyStorage(); return store;
    }

    @Bean("tianjiToolGrantBindings")
    TianjiToolGrantBindings tianjiToolGrantBindings(TianjiToolAuthorityProperties properties,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter, ObjectProvider<TianjiServiceExecutionBindings> services,
            ObjectProvider<TianjiServiceExecutionProperties> serviceProperties, ObjectProvider<TianjiLinkedDispatchProperties> linkedProperties,
            Environment environment) {
        requirePrerequisites(environment);
        TianjiServiceExecutionBindings selected = null;
        if (properties.bindings().stream().anyMatch(binding -> binding.principalType() == TrustedAgentActor.SubjectType.SERVICE)) {
            if (!environment.getProperty("stellar.tianji.agent.service-execution.enabled", Boolean.class, false)) throw unavailable();
            selected = services.getIfAvailable(); var sources = serviceProperties.getIfAvailable(); var linked = linkedProperties.getIfAvailable();
            if (selected == null || sources == null || linked == null || !sources.enabled() || !linked.enabled()) throw unavailable();
            for (var binding : properties.bindings()) {
                if (binding.principalType() == TrustedAgentActor.SubjectType.SERVICE && sources.sources().stream().noneMatch(source ->
                        source.tenantId() == binding.tenantId() && TrustedIssuerRegistry.issuerHash(source.issuer()).equals(binding.issuerFingerprint())
                        && linked.bindings().stream().anyMatch(link -> link.tenantId() == source.tenantId()
                        && link.issuer().toASCIIString().equals(source.issuer().toASCIIString()) && link.clientId().equals(source.clientId())
                        && link.definitions().stream().anyMatch(definition -> definition.key().equals(binding.definitionKey())
                        && definition.version() == binding.definitionVersion() && definition.contentSha256().equals(binding.definitionSha256()))))) throw unavailable();
            }
        }
        return new TianjiToolGrantBindings(properties.descriptors(), properties.bindings(), starter.getResourceUri(), selected);
    }

    @Bean("tianjiToolGrantIssuer")
    TianjiToolGrantIssuer tianjiToolGrantIssuer(AgentLedgerService ledger, @Qualifier("toolCapabilityStore") ToolCapabilityStore store,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings bindings, @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority, @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth, @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        return new TianjiToolGrantIssuer(ledger, store, bindings, starter, authority, issuers, oauth, mapper);
    }

    @Bean(name = "tianjiToolGrantAuthorizer", destroyMethod = "close")
    TianjiToolGrantAuthorizer tianjiToolGrantAuthorizer(AgentLedgerService ledger, @Qualifier("toolCapabilityStore") ToolCapabilityStore store,
            @Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings bindings, @Qualifier("idpStarterProperties") IdpStarterProperties starter,
            @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority, @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers,
            @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth, @Qualifier("jacksonObjectMapper") ObjectMapper mapper,
            Rbac3StarterProperties rbac, TianjiToolAuthorityProperties properties) {
        return new TianjiToolGrantAuthorizer(ledger, store, bindings, starter, authority, issuers, oauth, mapper, rbac, properties.timeout(), properties.concurrency());
    }

    @Bean("toolActionIntentStore")
    ToolActionIntentStore toolActionIntentStore(DataSource source, AgentLedgerService ledger,
            @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorizer, Environment environment) {
        requirePrerequisites(environment);
        var store = new ToolActionIntentStore(source, ledger, authorizer); store.verifyStorage(); return store;
    }

    @Bean("tianjiToolAuthorityChainVerifier")
    @Lazy(false)
    SmartInitializingSingleton tianjiToolAuthorityChainVerifier(@Qualifier("tianjiToolGrantBindings") TianjiToolGrantBindings bindings,
            @Qualifier("tianjiToolGrantIssuer") TianjiToolGrantIssuer issuer, @Qualifier("tianjiToolGrantAuthorizer") TianjiToolGrantAuthorizer authorizer,
            ObjectProvider<TianjiExecutionGrantIssuer> users, ObjectProvider<TianjiServiceExecutionGrantIssuer> services,
            ObjectProvider<TianjiExecutionGrantAuthorizer> runs, DataSource source, AgentLedgerService ledger,
            @Qualifier("toolCapabilityStore") ToolCapabilityStore capabilities, @Qualifier("toolActionIntentStore") ToolActionIntentStore intents) {
        return () -> {
            if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)
                    || !intents.usesDataSource(source) || !intents.usesLedger(ledger)) throw unavailable();
            var user = users.getIfAvailable(); var run = runs.getIfAvailable();
            if (user == null || run == null || !user.usesToolIssuer(issuer) || !run.usesToolAuthorization(authorizer)) throw unavailable();
            if (bindings.hasServiceBindings()) {
                var service = services.getIfAvailable(); if (service == null || !service.usesToolIssuer(issuer)) throw unavailable();
            }
        };
    }

    private static void requirePrerequisites(Environment environment) {
        for (var feature : new String[]{"api", "storage", "execution-grants", "execution-authorization"}) {
            if (!environment.getProperty("stellar.tianji.agent." + feature + ".enabled", Boolean.class, false)) {
                throw new IllegalStateException("TIANJI_TOOL_AUTHORITY_PREREQUISITES_REQUIRED");
            }
        }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("TIANJI_TOOL_AUTHORITY_CHAIN_UNAVAILABLE"); }
}
