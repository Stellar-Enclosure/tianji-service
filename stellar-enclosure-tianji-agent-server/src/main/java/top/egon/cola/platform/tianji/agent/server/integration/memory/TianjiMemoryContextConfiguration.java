package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryContextProvider;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryRunAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionGrantIssuer;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import java.time.Clock;
import java.time.Duration;

/** Explicit source/credential assembly. It migrates no schema and registers no permission or owner grant. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.context.memory", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({TianjiMemoryContextProperties.class, TianjiMemoryAccessProperties.class})
public class TianjiMemoryContextConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryContextPrerequisites(Environment environment) {
        return ignored -> {
            for (var capability : new String[]{"api", "storage", "runtime", "memory", "execution-grants", "execution-authorization", "context.snapshots"}) {
                if (!environment.getProperty("stellar.tianji.agent." + capability + ".enabled", Boolean.class, false)) throw new IllegalStateException("Memory context requires governed storage, API, execution grants and preparation snapshots");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(TianjiMemoryHttpAuthorization.class)
    TianjiMemoryHttpAuthorization tianjiMemorySourceHttpAuthority(TianjiAgentAccess access, TianjiMemoryAccessProperties teams) { return new TianjiMemoryHttpAuthorization(access, teams, Clock.systemUTC()); }

    @Bean(destroyMethod = "close")
    TianjiMemoryRunAuthorization tianjiMemoryRunAuthorization(AgentLedgerService ledger, MemoryReadGrantStore store, TianjiMemoryContextProperties properties, TianjiMemoryAccessProperties teams,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter, @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, Rbac3StarterProperties rbac, Environment environment, ObjectProvider<TianjiServiceExecutionBindings> services) {
        long timeout = environment.getProperty("stellar.tianji.agent.execution-authorization.timeout-ms", Long.class, 10_000L);
        int concurrency = environment.getProperty("stellar.tianji.agent.execution-authorization.concurrency", Integer.class, 4);
        return new TianjiMemoryRunAuthorization(ledger, store, properties.bindings(), teams, services(properties, environment, services), starter, authority, issuers, oauth, mapper, rbac, Duration.ofMillis(timeout), concurrency);
    }

    @Bean
    TianjiMemoryGrantIssuer tianjiMemoryGrantIssuer(AgentLedgerService ledger, MemoryReadGrantStore store, TianjiMemoryContextProperties properties, TianjiMemoryHttpAuthorization permissions,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter, @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, Environment environment, ObjectProvider<TianjiServiceExecutionBindings> services) {
        return new TianjiMemoryGrantIssuer(ledger, store, properties.bindings(), permissions, services(properties, environment, services), starter, authority, issuers, oauth, mapper);
    }

    @Bean(name = "tianjiMemoryAuthorization")
    @ConditionalOnMissingBean(name = "tianjiMemoryAuthorization")
    MemoryAuthorization tianjiMemorySourceAuthorization(TianjiMemoryHttpAuthorization http, TianjiMemoryRunAuthorization runs, Environment environment, ObjectProvider<TianjiMemoryPreferenceWriteAuthorization> candidates) {
        boolean enabled = environment.getProperty("stellar.tianji.agent.memory.extraction.background.enabled", Boolean.class, false);
        var writes = enabled ? candidates.getIfAvailable() : null;
        if (enabled && writes == null) throw new IllegalStateException("TIANJI_MEMORY_PREFERENCE_WRITE_AUTHORIZER_REQUIRED");
        return request -> {
            if (request.context().preferenceWrite() != null && writes != null) return writes.authorize(request);
            return request.context().runAuthorization() == null ? http.authorize(request) : runs.authorize(request);
        };
    }

    @Bean(destroyMethod = "close")
    MemoryContextProvider tianjiMemoryContextProvider(PostgresMemoryService memory, TianjiMemoryContextProperties properties, TianjiMemoryProperties storage) {
        return new MemoryContextProvider(memory, properties.bindings(), storage.policies(), properties.maxConcurrent(), Clock.systemUTC());
    }

    @Bean
    SmartInitializingSingleton tianjiMemoryContextReadiness(AgentLedgerService ledger, MemoryReadGrantStore store, TianjiMemoryProperties storage, TianjiMemoryContextProperties properties,
            TianjiMemoryRunAuthorization runs, TianjiMemoryGrantIssuer issuer, TianjiExecutionGrantAuthorizer execution, TianjiExecutionGrantIssuer submissions,
            ObjectProvider<TianjiServiceExecutionGrantIssuer> serviceIssuers, TrustedIssuerRegistry issuers) {
        return () -> {
            if (!store.usesLedger(ledger) || !runs.usesLedger(ledger) || !issuer.usesLedger(ledger) || !runs.usesBindings(properties.bindings()) || !issuer.usesBindings(properties.bindings())
                    || !execution.usesMemoryAuthorization(runs) || !submissions.usesMemoryIssuer(issuer)) throw new IllegalStateException("Memory sources require the matching issuer and actual execution gate");
            properties.bindings().forEach(binding -> {
                var origin = storage.origins().stream().filter(value -> value.tenantId() == binding.tenantId() && value.issuerFingerprint().equals(binding.issuerFingerprint())).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Memory context origin is not configured"));
                var trusted = issuers.findByIssuer(origin.issuer().toASCIIString()).orElseThrow(() -> new IllegalStateException("Memory context issuer is unavailable"));
                if (trusted.status() != TrustedIssuerRegistry.Status.ACTIVE || trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !Long.toString(binding.tenantId()).equals(trusted.tenantId())) throw new IllegalStateException("Memory context requires current tenant authority");
            });
            if (service(properties)) {
                var admission = serviceIssuers.getIfAvailable(); if (admission == null || !admission.usesMemoryIssuer(issuer)) throw new IllegalStateException("SERVICE Memory context requires its matching admission issuer");
                store.requireServiceStorage();
            }
        };
    }

    private static boolean service(TianjiMemoryContextProperties properties) { return properties.bindings().stream().anyMatch(binding -> binding.principalType() == TrustedAgentActor.SubjectType.SERVICE); }
    private static TianjiServiceExecutionBindings services(TianjiMemoryContextProperties properties, Environment environment, ObjectProvider<TianjiServiceExecutionBindings> candidates) {
        if (!service(properties)) return null; var bindings = candidates.getIfAvailable();
        if (!environment.getProperty("stellar.tianji.agent.service-execution.enabled", Boolean.class, false) || bindings == null) throw new IllegalStateException("SERVICE Memory context requires governed SERVICE execution bindings"); return bindings;
    }
}
