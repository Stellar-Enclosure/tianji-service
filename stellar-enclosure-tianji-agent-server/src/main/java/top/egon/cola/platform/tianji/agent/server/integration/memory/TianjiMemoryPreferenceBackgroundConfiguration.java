package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceGrantIssuer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.HashSet;

/** Explicit default-off assembly; schema, IAM purpose and grants must already be provisioned by their owners. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.extraction.background", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({TianjiMemoryPreferenceProperties.class, TianjiMemoryPreferenceBackgroundProperties.class, TianjiMemoryAccessProperties.class})
public class TianjiMemoryPreferenceBackgroundConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryPreferenceBackgroundPrerequisites(Environment environment) {
        return ignored -> {
            for (var capability : new String[]{"api", "storage", "runtime", "memory", "memory.http", "execution-grants", "execution-authorization", "reconciliation"}) {
                if (!environment.getProperty("stellar.tianji.agent." + capability + ".enabled", Boolean.class, false)) throw new IllegalStateException("Background preferences require governed storage, API, Memory HTTP, execution authority and maintenance");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    MemoryPreferenceIntentStore tianjiMemoryPreferenceIntentStore(DataSource source, AgentLedgerService ledger) { return new MemoryPreferenceIntentStore(source, ledger); }

    @Bean
    @ConditionalOnMissingBean(TianjiMemoryHttpAuthorization.class)
    TianjiMemoryHttpAuthorization tianjiMemoryPreferenceSourceHttpAuthority(TianjiAgentAccess access, TianjiMemoryAccessProperties teams) { return new TianjiMemoryHttpAuthorization(access, teams, Clock.systemUTC()); }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    TianjiMemoryPreferenceWriteAuthorization tianjiMemoryPreferenceWriteAuthorization(AgentLedgerService ledger, MemoryPreferenceIntentStore store,
            TianjiMemoryPreferenceProperties sources, TianjiMemoryPreferenceBackgroundProperties properties, TianjiMemoryProperties storage,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter, @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper, Rbac3StarterProperties rbac) {
        validateSources(sources, properties, storage, issuers);
        return new TianjiMemoryPreferenceWriteAuthorization(ledger, store, sources.bindings(), sources.policies(), starter, authority, issuers, oauth, mapper, rbac, properties.authorizationTimeout(), properties.authorizationConcurrency());
    }

    @Bean
    @ConditionalOnMissingBean
    TianjiMemoryPreferenceGrantIssuer tianjiMemoryPreferenceGrantIssuer(AgentLedgerService ledger, MemoryPreferenceIntentStore store,
            TianjiMemoryPreferenceProperties sources, TianjiMemoryPreferenceBackgroundProperties properties, TianjiMemoryProperties storage,
            TianjiAgentAccess access, TianjiMemoryHttpAuthorization permissions,
            @Qualifier("idpStarterProperties") IdpStarterProperties starter, @Qualifier("identityAuthorityProperties") IdentityAuthorityProperties authority,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, @Qualifier("idpServiceOAuth2Client") IdpServiceOAuth2Client oauth,
            @Qualifier("jacksonObjectMapper") ObjectMapper mapper) {
        validateSources(sources, properties, storage, issuers);
        return new TianjiMemoryPreferenceGrantIssuer(ledger, store, sources.bindings(), access, permissions, starter, authority, issuers, oauth, mapper, properties.grantLifetime(), properties.maximumAttempts());
    }

    @Bean(name = "tianjiMemoryAuthorization")
    @ConditionalOnMissingBean(name = "tianjiMemoryAuthorization")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.context.memory", name = "enabled", havingValue = "false", matchIfMissing = true)
    MemoryAuthorization tianjiMemoryPreferenceAuthorization(TianjiMemoryHttpAuthorization http, TianjiMemoryPreferenceWriteAuthorization writes) {
        return request -> request.context().preferenceWrite() == null ? http.authorize(request) : writes.authorize(request);
    }

    @Bean
    MemoryPreferenceWork tianjiMemoryPreferenceWork(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, PostgresMemoryService memory,
            TianjiMemoryPreferenceWriteAuthorization authority, TianjiMemoryPreferenceProperties sources, TianjiMemoryPreferenceBackgroundProperties properties) {
        return new MemoryPreferenceWork(source, ledger, store, memory, authority, sources.bindings().stream().map(binding -> new MemoryPreferenceWork.Origin(binding.tenantId(), binding.issuerFingerprint())).distinct().toList(),
                properties.leaseDuration(), properties.retryDelay(), Clock.systemUTC());
    }

    @Bean
    MemoryPreferenceJob tianjiMemoryPreferenceJob(MemoryPreferenceWork work, @Qualifier("tianjiAgentReconciliationScheduler") TaskScheduler scheduler, TianjiMemoryPreferenceBackgroundProperties properties) {
        return new MemoryPreferenceJob(work, scheduler, properties, Clock.systemUTC());
    }

    @Bean
    SmartInitializingSingleton tianjiMemoryPreferenceBackgroundReadiness(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, PostgresMemoryService memory,
            TianjiMemoryPreferenceWriteAuthorization writes, TianjiMemoryPreferenceGrantIssuer issuer, TianjiExecutionGrantIssuer submissions,
            TianjiMemoryPreferenceProperties sources, TianjiMemoryPreferenceBackgroundProperties properties, TianjiMemoryProperties storage, TrustedIssuerRegistry issuers) {
        return () -> {
            validateSources(sources, properties, storage, issuers);
            if (!ledger.usesDataSource(source) || !store.usesDataSource(source) || !store.usesLedger(ledger) || !memory.usesDataSource(source) || !writes.usesLedger(ledger) || !issuer.usesLedger(ledger)
                    || !writes.usesBindings(sources.bindings(), sources.policies()) || !issuer.usesBindings(sources.bindings()) || !submissions.usesPreferenceIssuer(issuer)) throw new IllegalStateException("Background preferences require the matching source issuer, write authority and DataSource");
            store.verifyStorage();
        };
    }

    private static void validateSources(TianjiMemoryPreferenceProperties sources, TianjiMemoryPreferenceBackgroundProperties properties, TianjiMemoryProperties storage, TrustedIssuerRegistry issuers) {
        var retention = storage.policies().stream().map(policy -> policy.reference()).collect(java.util.stream.Collectors.toSet()); var configured = new HashSet<String>();
        for (var policy : sources.policies()) {
            if (!retention.contains(policy.retentionPolicyRef()) || Math.min(policy.maximumCandidates(), policy.preferenceValues().size()) > properties.maximumAttempts()) throw new IllegalStateException("Background preference retention or progress budget cannot cover the approved policy");
            configured.add(policy.reference());
        }
        sources.bindings().forEach(binding -> {
            if (!configured.contains(binding.policyRef())) throw new IllegalStateException("Background preference policy is not configured");
            TianjiMemoryConfiguration.requireOrigin(binding.tenantId(), binding.issuerFingerprint(), storage, issuers);
        });
    }
}
