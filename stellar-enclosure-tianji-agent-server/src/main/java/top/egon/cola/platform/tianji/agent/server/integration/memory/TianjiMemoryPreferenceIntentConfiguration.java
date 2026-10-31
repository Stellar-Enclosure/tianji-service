package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.time.Clock;

/** Management remains independently opt-in so a user can stop an old intent while automatic extraction is disabled. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.extraction.intents.http", name = "enabled", havingValue = "true")
public class TianjiMemoryPreferenceIntentConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryPreferenceIntentPrerequisites(Environment environment) {
        return ignored -> {
            for (var capability : new String[]{"api", "storage", "memory", "memory.http"}) if (!environment.getProperty("stellar.tianji.agent." + capability + ".enabled", Boolean.class, false)) throw new IllegalStateException("Preference intent HTTP requires verified API and Memory storage/HTTP");
        };
    }
    @Bean
    @ConditionalOnMissingBean
    MemoryPreferenceIntentStore tianjiMemoryPreferenceManagementStore(DataSource source, AgentLedgerService ledger) { return new MemoryPreferenceIntentStore(source, ledger); }

    @Bean
    MemoryPreferenceIntentAccess tianjiMemoryPreferenceIntentAccess(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, TianjiAgentAccess access, TianjiMemoryProperties storage, TrustedIssuerRegistry issuers) {
        return new MemoryPreferenceIntentAccess(source, ledger, store, access, storage, issuers, Clock.systemUTC());
    }
    @Bean
    SmartInitializingSingleton tianjiMemoryPreferenceIntentReadiness(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, TianjiMemoryProperties storage, TrustedIssuerRegistry issuers) {
        return () -> {
            if (!ledger.usesDataSource(source) || !store.usesDataSource(source) || !store.usesLedger(ledger)) throw new IllegalStateException("Preference intent storage must match its owned source");
            storage.origins().forEach(origin -> TianjiMemoryConfiguration.requireOrigin(origin.tenantId(), origin.issuerFingerprint(), storage, issuers)); store.verifyStorage();
        };
    }
}
