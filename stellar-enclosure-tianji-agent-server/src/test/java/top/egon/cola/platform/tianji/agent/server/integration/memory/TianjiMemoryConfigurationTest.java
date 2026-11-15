package top.egon.cola.platform.tianji.agent.server.integration.memory;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.component.agentflow.api.memory.MemoryStore;
import top.egon.cola.component.agentflow.memory.jdbc.JdbcMemoryStore;
import top.egon.cola.component.outbox.api.TransactionalOutbox;
import top.egon.cola.component.outbox.autoconfigure.OutboxInfrastructure;
import top.egon.cola.component.outbox.autoconfigure.TransactionalOutboxProperties;
import top.egon.cola.component.outbox.delivery.DeliveryHandlerRegistry;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryOperationContext;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.LegacyMemoryFactConverterImpl;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.VersionedMemoryEmbeddingModel;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryStorageVerifier;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiMemoryConfigurationTest {
    private static final URI ISSUER = URI.create("https://idp.example/20");
    private final DataSource source = mock(DataSource.class);
    private final EmbeddingModel delegate = mock(EmbeddingModel.class);
    private final TransactionalOutbox outbox = mock(TransactionalOutbox.class);

    @Test
    void memory_is_absent_by_default_and_incomplete_activation_fails_closed() {
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(PostgresMemoryService.class).isEmpty());
        });
        new ApplicationContextRunner().withUserConfiguration(TianjiMemoryConfiguration.class).withPropertyValues("stellar.tianji.agent.memory.enabled=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        configured(issuers()).run(context -> assertNotNull(context.getStartupFailure()));
        verifyNoInteractions(source, delegate, outbox);
    }

    @Test
    void explicit_authority_wires_the_durable_components_without_inference_or_a_data_read() {
        var issuers = issuers(); var authority = mock(MemoryAuthorization.class);
        try (var storage = mockConstruction(MemoryStorageVerifier.class);
             var validator = Validation.buildDefaultValidatorFactory()) {
            exactStore(configured(issuers), validator.getValidator())
                    .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> authority).run(context -> {
                assertNull(context.getStartupFailure()); assertEquals(1, storage.constructed().size());
                assertNotNull(context.getBean(PostgresMemoryService.class)); assertNotNull(context.getBean(MemoryIndexDeliveryHandler.class));
                var converter = assertInstanceOf(LegacyMemoryFactConverterImpl.class, context.getBean("legacyMemoryFactConverter"));
                assertEquals("legacyMemoryFactConverter", converter.getClass().getAnnotation(Component.class).value());
                assertNotSame(context.getBean("jdbcMemoryStore"), context.getBean("tianjiHostMemoryStore"));
                verifyNoInteractions(source, delegate, outbox, authority);
                when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());
                var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
                assertThrows(AgentLedgerException.class, () -> context.getBean(PostgresMemoryService.class).get(new MemoryOperationContext(actor, Instant.now().plusSeconds(5), null),
                        new GetMemoryQuery(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "memory")));
                verifyNoInteractions(source, delegate, outbox, authority);
            });
        }
    }

    @Test
    void explicitly_enabled_memory_requires_an_exact_component_database_and_schema() {
        var authority = mock(MemoryAuthorization.class);
        var configuration = configured(issuers()).withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> authority);
        try (var verification = mockConstruction(MemoryStorageVerifier.class);
             var validator = Validation.buildDefaultValidatorFactory()) {
            configuration.run(context -> assertNotNull(context.getStartupFailure()));
            configuration.withBean("jdbcMemoryStore", MemoryStore.class, () ->
                    new JdbcMemoryStore(mock(DataSource.class), "egon_agent_memory", "COMPONENT", java.time.Clock.systemUTC(),
                            validator.getValidator())).run(context -> assertNotNull(context.getStartupFailure()));
            configuration.withBean("jdbcMemoryStore", MemoryStore.class, () ->
                    new JdbcMemoryStore(source, "foreign_memory", "COMPONENT", java.time.Clock.systemUTC(),
                            validator.getValidator())).run(context -> assertNotNull(context.getStartupFailure()));
            assertTrue(verification.constructed().isEmpty());
        }
    }

    @Test
    void default_core_starts_without_optional_vector_classes_and_explicit_memory_fails_closed() {
        var runner = new ApplicationContextRunner().withUserConfiguration(TianjiMemoryConfiguration.class,
                        top.egon.cola.platform.tianji.agent.server.context.TianjiContextAssemblyConfiguration.class)
                .withClassLoader(new FilteredClassLoader("org.springframework.ai.vectorstore.pgvector"));
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.getBeansOfType(PostgresMemoryService.class).isEmpty());
            assertNotNull(context.getBean(top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler.class));
        });
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            exactStore(configured(issuers()), validator.getValidator())
                    .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> request -> MemoryAuthorization.Decision.ALLOW)
                    .withClassLoader(new FilteredClassLoader("org.springframework.ai.vectorstore.pgvector"))
                    .run(context -> {
                        var failure = context.getStartupFailure();
                        assertNotNull(failure);
                        assertEquals("Memory requires the configured optional pgvector storage",
                                NestedExceptionUtils.getMostSpecificCause(failure).getMessage());
                    });
        }
    }

    @Test
    void mismatched_outbox_type_schema_validation_and_inactive_origin_do_not_reach_storage_verification() {
        var authority = mock(MemoryAuthorization.class);
        try (var storage = mockConstruction(MemoryStorageVerifier.class);
             var validator = Validation.buildDefaultValidatorFactory()) {
            var properties = outboxProperties();
            properties.getStorage().setValidateSchema(false);
            exactStore(configured(issuers(), properties), validator.getValidator())
                    .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> authority)
                    .run(context -> assertNotNull(context.getStartupFailure()));
            var wrongType = outboxProperties();
            wrongType.getStorage().setType(TransactionalOutboxProperties.Storage.StorageType.MYBATIS_PLUS);
            exactStore(configured(issuers(), wrongType), validator.getValidator())
                    .withPropertyValues("egon.cola.component.transactional-outbox.storage.type=MYBATIS_PLUS")
                    .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> authority)
                    .run(context -> assertNotNull(context.getStartupFailure()));
            var inactive = issuers();
            when(inactive.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());
            exactStore(configured(inactive), validator.getValidator())
                    .withBean("tianjiMemoryAuthorization", MemoryAuthorization.class, () -> authority)
                    .run(context -> assertNotNull(context.getStartupFailure()));
            assertTrue(storage.constructed().isEmpty());
        }
        verifyNoInteractions(source, delegate, outbox, authority);
    }

    private ApplicationContextRunner exactStore(ApplicationContextRunner runner, Validator validator) {
        return runner.withBean("jdbcMemoryStore", MemoryStore.class, () ->
                validatedStore(new JdbcMemoryStore(source, "egon_agent_memory", "COMPONENT", java.time.Clock.systemUTC(), validator)));
    }

    private static MemoryStore validatedStore(JdbcMemoryStore store) {
        var proxy = new ProxyFactory(store);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new org.springframework.validation.beanvalidation.MethodValidationInterceptor());
        return (MemoryStore) proxy.getProxy();
    }

    private ApplicationContextRunner configured(TrustedIssuerRegistry issuers) {
        return configured(issuers, outboxProperties());
    }
    private ApplicationContextRunner configured(TrustedIssuerRegistry issuers, TransactionalOutboxProperties outboxProperties) {
        var profile = new VersionedMemoryEmbeddingModel.Profile("personal", 1, "embeddingBean", "text-embedding-3-small", 2, 8191, 64, 16);
        var model = new VersionedMemoryEmbeddingModel(profile, delegate, () -> EmbeddingOptions.builder().model("text-embedding-3-small").dimensions(2).build());
        return new ApplicationContextRunner().withUserConfiguration(TianjiMemoryConfiguration.class, Registry.class)
                .withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> new AgentLedgerService(source))
                .withBean(VersionedMemoryEmbeddingModel.class, () -> model).withBean(TransactionalOutbox.class, () -> outbox)
                .withBean(OutboxInfrastructure.class, () -> new OutboxInfrastructure(source, "dataSource", new DataSourceTransactionManager(source), "transactionManager"))
                .withBean(TransactionalOutboxProperties.class, () -> outboxProperties).withBean(TrustedIssuerRegistry.class, () -> issuers)
                .withPropertyValues("stellar.tianji.agent.memory.enabled=true", "stellar.tianji.agent.storage.enabled=true",
                        "egon.cola.component.agent-flow.enabled=true", "egon.cola.component.agent-flow.memory.mode=JDBC",
                        "egon.cola.component.agent-flow.memory.data-source-ref=dataSource", "egon.cola.component.agent-flow.memory.schema=egon_agent_memory",
                        "egon.cola.component.transactional-outbox.enabled=true", "egon.cola.component.transactional-outbox.storage.type=JDBC", "spring.flyway.enabled=false", "spring.sql.init.mode=never",
                        "stellar.tianji.agent.memory.base-vector-dimensions=2", "stellar.tianji.agent.memory.expected-vector-version=0.8.0",
                        "stellar.tianji.agent.memory.model.key=personal", "stellar.tianji.agent.memory.model.version=1", "stellar.tianji.agent.memory.model.model-bean-name=embeddingBean",
                        "stellar.tianji.agent.memory.model.model-name=text-embedding-3-small", "stellar.tianji.agent.memory.model.dimensions=2", "stellar.tianji.agent.memory.model.window-tokens=8191",
                        "stellar.tianji.agent.memory.model.safety-margin-tokens=64", "stellar.tianji.agent.memory.model.request-overhead-tokens=16",
                        "stellar.tianji.agent.memory.policies[0].key=personal", "stellar.tianji.agent.memory.policies[0].version=1", "stellar.tianji.agent.memory.policies[0].default-lifetime=P1D",
                        "stellar.tianji.agent.memory.policies[0].maximum-lifetime=P30D", "stellar.tianji.agent.memory.policies[0].maximum-active-entries=100",
                        "stellar.tianji.agent.memory.policies[0].embedding-allowed=CONFIDENTIAL", "stellar.tianji.agent.memory.policies[0].model-context-allowed=CONFIDENTIAL",
                        "stellar.tianji.agent.memory.policies[0].semantic-query-allowed=true", "stellar.tianji.agent.memory.origins[0].tenant-id=20", "stellar.tianji.agent.memory.origins[0].issuer=" + ISSUER);
    }
    private static TransactionalOutboxProperties outboxProperties() {
        var properties = new TransactionalOutboxProperties(); properties.getStorage().setType(TransactionalOutboxProperties.Storage.StorageType.JDBC);
        properties.getStorage().getJdbc().setSchema("tianji_agent"); properties.getStorage().setValidateSchema(true); return properties;
    }
    private static TrustedIssuerRegistry issuers() {
        var registry = mock(TrustedIssuerRegistry.class); when(registry.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                URI.create("https://idp.example/jwks"), "tenant:20", TrustedIssuerRegistry.RealmType.TENANT, "20", TrustedIssuerRegistry.Status.ACTIVE, 1))); return registry;
    }
    static class Registry { @Bean DeliveryHandlerRegistry handlers(MemoryIndexDeliveryHandler handler) { return new DeliveryHandlerRegistry(List.of(handler)); } }
}
