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
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;
import top.egon.cola.component.agentflow.api.memory.MemoryStore;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import org.springframework.ai.embedding.EmbeddingModel;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.component.outbox.serialization.OutboxMessageFingerprint;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryIndexWork;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.MemoryScopeMigrationService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.LegacyMemoryStoreAdapter;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.LegacyMemoryFactConverter;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.LegacyMemoryFactConverterImpl;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PgVectorMemoryIndex;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryReadGrantStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.Map;

/** Opt-in assembly around the current source authority and existing JDBC Outbox, with no schema or provider auto-creation. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.memory", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TianjiMemoryProperties.class)
@Import(LegacyMemoryFactConverterImpl.class)
public class TianjiMemoryConfiguration {
    @Bean
    static BeanFactoryPostProcessor memoryPrerequisites(Environment environment) {
        return factory -> {
            if (!ClassUtils.isPresent("org.springframework.ai.vectorstore.pgvector.PgVectorStore", factory.getBeanClassLoader())) {
                throw new IllegalStateException("Memory requires the configured optional pgvector storage");
            }
            if (!environment.getProperty("egon.cola.component.agent-flow.enabled", Boolean.class, false)
                    || !"JDBC".equalsIgnoreCase(environment.getProperty("egon.cola.component.agent-flow.memory.mode", ""))
                    || !environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
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

    @Bean("legacyMemoryStoreAdapter")
    LegacyMemoryStoreAdapter legacyMemoryStoreAdapter(DataSource source, ListableBeanFactory beans, Environment environment,
            @Qualifier("jdbcMemoryStore") ObjectProvider<MemoryStore> candidates) {
        String schema = environment.getProperty("egon.cola.component.agent-flow.memory.schema");
        String sourceRef = environment.getProperty("egon.cola.component.agent-flow.memory.data-source-ref");
        if (schema == null || sourceRef == null || beans.getBean(sourceRef, DataSource.class) != source
                || !(candidates.getIfAvailable() instanceof JdbcMemoryStore store) || !store.usesStorage(source, schema)) throw invalid();
        return new LegacyMemoryStoreAdapter(source, schema, store, Clock.systemUTC());
    }

    @Bean({"outboxMemoryIndexPublication", "tianjiMemoryPublication"})
    OutboxMemoryIndexPublication tianjiMemoryPublication(DataSource source, OutboxInfrastructure infrastructure, TransactionalOutbox outbox, MemoryIndexPublication.Binding binding,
            @Qualifier("legacyMemoryStoreAdapter") LegacyMemoryStoreAdapter adapter) {
        return new OutboxMemoryIndexPublication(source, infrastructure, outbox, binding, adapter);
    }

    @Bean({"postgresMemoryService", "tianjiMemoryService"})
    PostgresMemoryService tianjiMemoryService(DataSource source, AgentLedgerService ledger, OutboxMemoryIndexPublication publication, PgVectorMemoryIndex index,
            TianjiMemoryProperties properties, @Qualifier("tianjiMemoryAuthorization") MemoryAuthorization authority, TrustedIssuerRegistry issuers,
            @Qualifier("legacyMemoryStoreAdapter") LegacyMemoryStoreAdapter adapter) {
        MemoryAuthorization guarded = request -> {
            requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), properties, issuers);
            var decision = authority.authorize(request);
            requireOrigin(request.context().actor().tenantId(), request.context().actor().issuerFingerprint(), properties, issuers); return decision;
        };
        return new PostgresMemoryService(ledger, source, guarded, properties.policies(), publication, Clock.systemUTC(), index, adapter);
    }

    @Bean("tianjiHostMemoryStore")
    MemoryStore tianjiHostMemoryStore(PostgresMemoryService memory,
            @Qualifier("legacyMemoryStoreAdapter") LegacyMemoryStoreAdapter adapter,
            @Qualifier("legacyMemoryFactConverter") LegacyMemoryFactConverter converter) {
        return adapter.hostStore(memory, converter);
    }

    @Bean
    MemoryReadGrantStore tianjiMemoryReadGrantStore(DataSource source, AgentLedgerService ledger) { return new MemoryReadGrantStore(source, ledger); }

    @Bean({"memoryIndexWork", "tianjiMemoryIndexWork"})
    MemoryIndexWork tianjiMemoryIndexWork(DataSource source, PostgresMemoryService memory, PgVectorMemoryIndex index, TianjiMemoryProperties properties, TrustedIssuerRegistry issuers) {
        return new MemoryIndexWork(source, memory, index, event -> requireOrigin(event.tenantId(), event.ownerIssuer(), properties, issuers), Clock.systemUTC());
    }

    @Bean({"memoryIndexDeliveryHandler", "tianjiMemoryDeliveryHandler"})
    MemoryIndexDeliveryHandler tianjiMemoryDeliveryHandler(ObjectProvider<MemoryIndexWork> work) { return new MemoryIndexDeliveryHandler(work::getObject, Clock.systemUTC()); }

    @Bean
    SmartInitializingSingleton tianjiMemoryReadiness(DataSource source, PostgresMemoryService memory, PgVectorMemoryIndex index, OutboxMemoryIndexPublication publication,
            TianjiMemoryProperties properties, TransactionalOutboxProperties outbox, DeliveryHandlerRegistry handlers, TrustedIssuerRegistry issuers,
            @Qualifier("legacyMemoryStoreAdapter") LegacyMemoryStoreAdapter adapter) {
        return () -> {
            if (!memory.usesDataSource(source) || !memory.usesHostAuthority(adapter) || !index.usesDataSource(source) || !publication.usesDataSource(source)
                    || !publication.binding().equals(index.binding()) || !outbox.getStorage().isValidateSchema()
                    || outbox.getStorage().getType() != TransactionalOutboxProperties.Storage.StorageType.JDBC
                    || !"tianji_agent".equals(outbox.getStorage().getJdbc().getSchema())) throw invalid();
            var handler = handlers.required(OutboxMemoryIndexPublication.CHANNEL);
            if (!(handler instanceof MemoryIndexDeliveryHandler)) throw invalid(); handler.validateDestination(OutboxMemoryIndexPublication.DESTINATION);
            properties.origins().forEach(origin -> requireOrigin(origin.tenantId(), origin.issuerFingerprint(), properties, issuers));
            memory.verifyStorage(properties.baseVectorDimensions(), properties.expectedVectorVersion());
        };
    }

    @Bean(name = {"memoryScopeMigrationService", "tianjiMemoryScopeMigrationService"})
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.memory.migration", name = "enabled", havingValue = "true")
    MemoryScopeMigrationService memoryScopeMigrationService(DataSource source,
            @Qualifier("jdbcMemoryStore") MemoryStore configured, @Qualifier("memoryIndexWork") MemoryIndexWork indexes,
            @Qualifier("tianjiMemoryMigrationAuthority") MemoryScopeMigrationService.MigrationAuthority authority,
            OutboxInfrastructure infrastructure, TransactionalOutboxProperties outbox, Environment environment) {
        String schema = environment.getRequiredProperty("egon.cola.component.agent-flow.memory.schema");
        String role = environment.getRequiredProperty("stellar.tianji.agent.memory.migration.role");
        if (!(configured instanceof JdbcMemoryStore store) || !store.usesStorage(source, schema)
                || infrastructure.dataSource() != source || outbox.getStorage().getType() != TransactionalOutboxProperties.Storage.StorageType.JDBC
                || !outbox.getStorage().isValidateSchema() || !"tianji_agent".equals(outbox.getStorage().getJdbc().getSchema())) throw invalid();
        var protocol = new MemoryScopeMigrationService.OutboxProtocol() {
            @Override public MemoryIndexPublication.Event decode(String payload) { return MemoryIndexEventJson.decode(payload); }
            @Override public String messageId(MemoryIndexPublication.Event event) { return OutboxMemoryIndexPublication.transportMessageId(event); }
            @Override public String fingerprint(String payload, Map<String, String> headers) {
                return OutboxMessageFingerprint.sha256(OutboxMemoryIndexPublication.CHANNEL, OutboxMemoryIndexPublication.DESTINATION,
                        payload, "application/json", "1", headers);
            }
        };
        return new MemoryScopeMigrationService(source, schema, store, indexes, role, authority, protocol, Clock.systemUTC());
    }

    static void requireOrigin(long tenantId, String fingerprint, TianjiMemoryProperties properties, TrustedIssuerRegistry issuers) {
        var binding = properties.origins().stream().filter(origin -> origin.tenantId() == tenantId && origin.issuerFingerprint().equals(fingerprint)).findFirst().orElseThrow(TianjiMemoryConfiguration::unavailable);
        var issuer = issuers.findByIssuer(binding.issuer().toASCIIString()).orElseThrow(TianjiMemoryConfiguration::unavailable);
        if (issuer.status() != TrustedIssuerRegistry.Status.ACTIVE || issuer.realmType() != TrustedIssuerRegistry.RealmType.TENANT || !Long.toString(tenantId).equals(issuer.tenantId())) throw unavailable();
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Memory requires matching governed storage, current authority and pre-provisioned JDBC Outbox"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Current Memory origin authority is unavailable"); }
}
