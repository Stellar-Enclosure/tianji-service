package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ListableBeanFactory;
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
import org.springframework.ai.embedding.EmbeddingModel;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryIndexWork;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PgVectorMemoryIndex;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.time.Clock;

/** Opt-in assembly around the current source authority and existing JDBC Outbox, with no schema or provider auto-creation. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiMemoryProperties.class)
public class TianjiMemoryConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryPrerequisites(Environment environment) {
        return ignored -> {
            if (!environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                    || !environment.getProperty("egon.cola.component.transactional-outbox.enabled", Boolean.class, false)
                    || !"JDBC".equalsIgnoreCase(environment.getProperty("egon.cola.component.transactional-outbox.storage.type", ""))
                    || environment.getProperty("spring.flyway.enabled", Boolean.class, true)
                    || !"never".equalsIgnoreCase(environment.getProperty("spring.sql.init.mode", ""))) throw invalid();
        };
    }

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper tianjiMemoryOutboxMapper() { return new ObjectMapper().findAndRegisterModules(); }

    @Bean
    @ConditionalOnMissingBean(VersionedMemoryEmbeddingModel.class)
    VersionedMemoryEmbeddingModel tianjiMemoryEmbedding(TianjiMemoryProperties properties, ListableBeanFactory beans) {
        if (properties.model().modelBeanName().equals("tianjiMemoryEmbedding")) throw invalid();
        var selected = beans.getBean(properties.model().modelBeanName(), EmbeddingModel.class);
        return VersionedMemoryEmbeddingModel.forOpenAi(properties.model(), selected);
    }

    @Bean
    MemoryIndexPublication.Binding tianjiMemoryIndexBinding(TianjiMemoryProperties properties, VersionedMemoryEmbeddingModel model) {
        var descriptor = model.descriptor();
        if (!properties.model().modelName().equals(descriptor.modelName()) || properties.model().dimensions() != descriptor.dimensions()
                || !descriptor.modelRef().startsWith(properties.model().key() + '@' + properties.model().version() + '#')) throw invalid();
        return new MemoryIndexPublication.Binding(descriptor.modelRef(), descriptor.dimensions(), properties.vectorTable());
    }

    @Bean(destroyMethod = "close")
    PgVectorMemoryIndex tianjiMemoryVectorIndex(DataSource source, MemoryIndexPublication.Binding binding, VersionedMemoryEmbeddingModel model, TianjiMemoryProperties properties) {
        return new PgVectorMemoryIndex(source, binding, model, properties.maxConcurrent(), Clock.systemUTC());
    }

    @Bean
    OutboxMemoryIndexPublication tianjiMemoryPublication(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox, MemoryIndexPublication.Binding binding) {
        return new OutboxMemoryIndexPublication(source, infrastructure, outbox, binding);
    }

    @Bean
    PostgresMemoryService tianjiMemoryService(DataSource source, AgentLedgerService ledger, OutboxMemoryIndexPublication publication, PgVectorMemoryIndex index,
            TianjiMemoryProperties properties, @Qualifier("tianjiMemoryAuthorization") MemoryAuthorization authority, TrustedIssuerRegistry issuers) {
        MemoryAuthorization guarded = request -> {
            requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), properties, issuers);
            var decision = authority.authorize(request);
            requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), properties, issuers); return decision;
        };
        return new PostgresMemoryService(ledger, source, guarded, properties.policies(), publication, Clock.systemUTC(), index);
    }

    @Bean
    MemoryReadGrantStore tianjiMemoryReadGrantStore(DataSource source, AgentLedgerService ledger) { return new MemoryReadGrantStore(source, ledger); }

    @Bean
    MemoryIndexWork tianjiMemoryIndexWork(DataSource source, PostgresMemoryService memory, PgVectorMemoryIndex index, TianjiMemoryProperties properties, TrustedIssuerRegistry issuers) {
        return new MemoryIndexWork(source, memory, index, event -> requireOrigin(event.tenantId(), event.ownerIssuer(), properties, issuers), Clock.systemUTC());
    }

    @Bean
    MemoryIndexDeliveryHandler tianjiMemoryDeliveryHandler(ObjectProvider<MemoryIndexWork> work) { return new MemoryIndexDeliveryHandler(work::getObject, Clock.systemUTC()); }

    @Bean
    SmartInitializingSingleton tianjiMemoryReadiness(DataSource source, PostgresMemoryService memory, PgVectorMemoryIndex index, OutboxMemoryIndexPublication publication,
            TianjiMemoryProperties properties, TransactionalOutboxProperties outbox, DeliveryHandlerRegistry handlers, TrustedIssuerRegistry issuers) {
        return () -> {
            if (!memory.usesDataSource(source) || !index.usesDataSource(source) || !publication.usesDataSource(source)
                    || !publication.binding().equals(index.binding()) || !outbox.getStorage().isValidateSchema()
                    || outbox.getStorage().getType() != TransactionalOutboxProperties.Storage.StorageType.JDBC
                    || !"tianji_agent".equals(outbox.getStorage().getJdbc().getSchema())) throw invalid();
            var handler = handlers.required(OutboxMemoryIndexPublication.CHANNEL);
            if (!(handler instanceof MemoryIndexDeliveryHandler)) throw invalid(); handler.validateDestination(OutboxMemoryIndexPublication.DESTINATION);
            properties.origins().forEach(origin -> requireOrigin(origin.tenantId(), origin.issuerFingerprint(), properties, issuers));
            memory.verifyStorage(properties.baseVectorDimensions(), properties.expectedVectorVersion());
        };
    }

    private static void requireOrigin(long tenantId, String fingerprint, TianjiMemoryProperties properties, TrustedIssuerRegistry issuers) {
        var binding = properties.origins().stream().filter(origin -> origin.tenantId() == tenantId && origin.issuerFingerprint().equals(fingerprint)).findFirst().orElseThrow(TianjiMemoryConfiguration::unavailable);
        var issuer = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(TianjiMemoryConfiguration::unavailable);
        if (issuer.status() != TrustedIssuerRegistry.Status.ACTIVE || issuer.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !Long.toString(tenantId).equals(issuer.tenantId())) throw unavailable();
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Memory requires matching governed storage, current authority and pre-provisioned JDBC Outbox"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory origin authority is unavailable"); }
}
