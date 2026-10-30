package top.egon.cola.platform.tianji.agent.server.integration.memory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryPreferenceAdmission;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.extraction.http", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiMemoryPreferenceProperties.class)
public class TianjiMemoryPreferenceConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryPreferencePrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.api.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.memory.enabled", Boolean.class, false)
                    || !environment.getProperty("stellar.tianji.agent.memory.http.enabled", Boolean.class, false)) throw new IllegalStateException("Memory preference admission requires the verified API and Memory HTTP/storage");
        };
    }

    @Bean
    @ConditionalOnMissingBean
    MemoryPreferenceAdmission tianjiMemoryPreferenceAdmission(DataSource source, AgentLedgerService ledger, PostgresMemoryService memory,
            @Qualifier("tianjiMemoryAuthorization") MemoryAuthorization authority, TianjiMemoryPreferenceProperties properties,
            TianjiMemoryProperties memoryProperties, TrustedIssuerRegistry issuers) {
        if (!ledger.usesDataSource(source) || !memory.usesDataSource(source)) throw new IllegalStateException("Memory preference source and fact storage must share the verified ledger DataSource");
        properties.bindings().forEach(binding -> TianjiMemoryConfiguration.requireOrigin(binding.tenantId(), binding.issuerFingerprint(), memoryProperties, issuers));
        MemoryAuthorization guarded = request -> {
            TianjiMemoryConfiguration.requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), memoryProperties, issuers);
            var decision = authority.authorize(request);
            TianjiMemoryConfiguration.requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), memoryProperties, issuers);
            return decision;
        };
        return new MemoryPreferenceAdmission(ledger, memory, guarded, properties.bindings(), properties.policies(), Clock.systemUTC());
    }
}
