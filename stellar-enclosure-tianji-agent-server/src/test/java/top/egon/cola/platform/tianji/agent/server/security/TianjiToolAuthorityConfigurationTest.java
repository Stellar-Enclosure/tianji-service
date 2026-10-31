package top.egon.cola.platform.tianji.agent.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionStorageVerifier;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiToolAuthorityConfigurationTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.authority.";
    private static final URI ISSUER = URI.create("https://idp.example/21");
    private static final URI RESOURCE = URI.create("https://agent.example/api");
    private static final String HASH = "c".repeat(64);
    private static final ToolDescriptor TOOL = new ToolDescriptor("process.read", 1, "process_read", "Read process",
            new ToolSchema(Map.of("query", ToolSchema.ValueType.STRING), Set.of("query")), new ToolSchema(Map.of("found", ToolSchema.ValueType.BOOLEAN), Set.of("found")),
            new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.read", "process", "a".repeat(64), null));
    private final TianjiToolAuthorityConfiguration configuration = new TianjiToolAuthorityConfiguration();

    @Test void disabled_authority_creates_no_components_and_touches_no_source() {
        var source = mock(DataSource.class);
        new ApplicationContextRunner().withUserConfiguration(TianjiToolAuthorityConfiguration.class).withBean(DataSource.class, () -> source).run(context -> {
            assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(ToolCapabilityStore.class).isEmpty());
            assertTrue(context.getBeansOfType(ToolActionIntentStore.class).isEmpty()); assertTrue(context.getBeansOfType(TianjiToolGrantIssuer.class).isEmpty());
            assertTrue(context.getBeansOfType(TianjiToolGrantAuthorizer.class).isEmpty()); assertTrue(context.getBeansOfType(TianjiToolGrantBindings.class).isEmpty());
        });
        verifyNoInteractions(source);
    }

    @Test void each_prerequisite_is_required_before_storage_is_touched() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var pdp = mock(TianjiToolGrantAuthorizer.class);
        for (var missing : List.of("api", "storage", "execution-grants", "execution-authorization")) {
            var environment = prerequisites(); environment.setProperty("stellar.tianji.agent." + missing + ".enabled", "false");
            assertThrows(IllegalStateException.class, () -> configuration.toolCapabilityStore(source, ledger, environment));
            assertThrows(IllegalStateException.class, () -> configuration.toolActionIntentStore(source, ledger, pdp, environment));
        }
        verifyNoInteractions(source, ledger, pdp);
    }

    @Test void both_real_stores_independently_verify_the_exact_ledger_source() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var pdp = mock(TianjiToolGrantAuthorizer.class);
        when(ledger.usesDataSource(source)).thenReturn(true); var checkedSources = new ArrayList<Object>();
        try (var verifiers = mockConstruction(ToolExecutionStorageVerifier.class, (value, context) -> checkedSources.add(context.arguments().getFirst()))) {
            var capabilities = configuration.toolCapabilityStore(source, ledger, prerequisites());
            var intents = configuration.toolActionIntentStore(source, ledger, pdp, prerequisites());
            assertNotNull(capabilities); assertNotNull(intents);
            assertTrue(capabilities.usesDataSource(source)); assertTrue(capabilities.usesLedger(ledger));
            assertTrue(intents.usesDataSource(source)); assertTrue(intents.usesLedger(ledger));
            assertEquals(List.of(source, source), checkedSources); assertEquals(2, verifiers.constructed().size());
            verifiers.constructed().forEach(value -> verify(value).verify());
        }
        verifyNoInteractions(source, pdp);
    }

    @Test void source_mismatch_and_either_readiness_failure_abort_creation() {
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var pdp = mock(TianjiToolGrantAuthorizer.class);
        assertThrows(IllegalArgumentException.class, () -> configuration.toolCapabilityStore(source, ledger, prerequisites()));
        assertThrows(IllegalArgumentException.class, () -> configuration.toolActionIntentStore(source, ledger, pdp, prerequisites()));
        when(ledger.usesDataSource(source)).thenReturn(true);
        try (var ignored = mockConstruction(ToolExecutionStorageVerifier.class, (value, context) -> doThrow(new IllegalStateException("catalog unavailable")).when(value).verify())) {
            assertThrows(IllegalStateException.class, () -> configuration.toolCapabilityStore(source, ledger, prerequisites()));
            assertThrows(IllegalStateException.class, () -> configuration.toolActionIntentStore(source, ledger, pdp, prerequisites()));
        }
        verifyNoInteractions(source, pdp);
    }

    @Test void user_mapping_does_not_resolve_service_providers_and_service_mapping_requires_real_static_metadata() {
        var starter = starter(); var services = provider((TianjiServiceExecutionBindings) null);
        var serviceProperties = provider((TianjiServiceExecutionProperties) null); var linkedProperties = provider((TianjiLinkedDispatchProperties) null);
        var user = configuration.tianjiToolGrantBindings(properties(false), starter, services, serviceProperties, linkedProperties, prerequisites());
        assertNotNull(user); assertFalse(user.hasServiceBindings()); verifyNoInteractions(services, serviceProperties, linkedProperties);
        assertThrows(IllegalStateException.class, () -> configuration.tianjiToolGrantBindings(properties(true), starter, services, serviceProperties, linkedProperties, prerequisites()));
        var environment = prerequisites().withProperty("stellar.tianji.agent.service-execution.enabled", "true");
        assertThrows(IllegalStateException.class, () -> configuration.tianjiToolGrantBindings(properties(true), starter, services, serviceProperties, linkedProperties, environment));
        var actual = mock(TianjiServiceExecutionBindings.class); var metadata = serviceProperties(); var linked = linkedProperties(HASH);
        var bound = configuration.tianjiToolGrantBindings(properties(true), starter, provider(actual), provider(metadata), provider(linked), environment);
        assertNotNull(bound); assertTrue(bound.usesServices(actual)); assertTrue(bound.hasServiceBindings());
        assertThrows(IllegalStateException.class, () -> configuration.tianjiToolGrantBindings(properties(true), starter, provider(actual), provider(metadata), provider(linkedProperties("d".repeat(64))), environment));
        var wrongSource = new TianjiServiceExecutionProperties(true, List.of(new TianjiServiceExecutionProperties.Source(ISSUER, 21, "other-client", URI.create("https://process.example/api"))));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiToolGrantBindings(properties(true), starter, provider(actual), provider(wrongSource), provider(linked), environment));
        var otherOrigin = new TianjiServiceExecutionProperties(true, List.of(new TianjiServiceExecutionProperties.Source(URI.create("https://other.example/21"), 21, "worker", URI.create("https://process.example/api"))));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiToolGrantBindings(properties(true), starter, provider(actual), provider(otherOrigin), provider(linked), environment));
        verifyNoInteractions(actual);
    }

    @Test void chain_proof_resolves_roots_only_in_the_callback_and_requires_exact_instances() {
        var bindings = new TianjiToolGrantBindings(List.of(TOOL), List.of(mapping(false)), RESOURCE, null);
        var issuer = mock(TianjiToolGrantIssuer.class); var pdp = mock(TianjiToolGrantAuthorizer.class);
        var userRoot = mock(TianjiExecutionGrantIssuer.class); var runRoot = mock(TianjiExecutionGrantAuthorizer.class);
        var users = provider(userRoot); var services = provider((TianjiServiceExecutionGrantIssuer) null); var runs = provider(runRoot);
        var source = mock(DataSource.class); var ledger = mock(AgentLedgerService.class); var capabilities = mock(ToolCapabilityStore.class); var intents = mock(ToolActionIntentStore.class);
        when(ledger.usesDataSource(source)).thenReturn(true); when(capabilities.usesDataSource(source)).thenReturn(true); when(capabilities.usesLedger(ledger)).thenReturn(true);
        when(intents.usesDataSource(source)).thenReturn(true); when(intents.usesLedger(ledger)).thenReturn(true);
        var proof = configuration.tianjiToolAuthorityChainVerifier(bindings, issuer, pdp, users, services, runs, source, ledger, capabilities, intents);
        assertNotNull(proof); verifyNoInteractions(users, services, runs, userRoot, runRoot);
        assertThrows(IllegalStateException.class, proof::afterSingletonsInstantiated);
        when(userRoot.usesToolIssuer(issuer)).thenReturn(true); assertThrows(IllegalStateException.class, proof::afterSingletonsInstantiated);
        when(runRoot.usesToolAuthorization(pdp)).thenReturn(true); assertDoesNotThrow(proof::afterSingletonsInstantiated); verifyNoInteractions(services);
        var absent = configuration.tianjiToolAuthorityChainVerifier(bindings, issuer, pdp, provider((TianjiExecutionGrantIssuer) null), services, runs, source, ledger, capabilities, intents);
        assertNotNull(absent); assertThrows(IllegalStateException.class, absent::afterSingletonsInstantiated);
        var serviceBindings = new TianjiToolGrantBindings(List.of(TOOL), List.of(mapping(true)), RESOURCE, mock(TianjiServiceExecutionBindings.class));
        var serviceRoot = mock(TianjiServiceExecutionGrantIssuer.class);
        var serviceProof = configuration.tianjiToolAuthorityChainVerifier(serviceBindings, issuer, pdp, users, provider(serviceRoot), runs, source, ledger, capabilities, intents);
        assertNotNull(serviceProof); assertThrows(IllegalStateException.class, serviceProof::afterSingletonsInstantiated);
        when(serviceRoot.usesToolIssuer(issuer)).thenReturn(true); assertDoesNotThrow(serviceProof::afterSingletonsInstantiated);
        when(intents.usesLedger(ledger)).thenReturn(false); assertThrows(IllegalStateException.class, serviceProof::afterSingletonsInstantiated);
        verifyNoInteractions(source);
    }

    @Test void actual_user_factories_share_one_authority_in_a_queue_only_context() {
        var fixture = new Fixture();
        try (var oldStorage = mockConstruction(AgentStorageVerifier.class); var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class)) {
            fixture.runner(false).run(context -> {
                assertNull(context.getStartupFailure());
                assertFalse(context.getEnvironment().getProperty("stellar.tianji.agent.runtime.enabled", Boolean.class, false));
                assertEquals(1, context.getBeansOfType(TianjiToolGrantIssuer.class).size());
                var issuer = context.getBean("tianjiToolGrantIssuer", TianjiToolGrantIssuer.class); var pdp = context.getBean("tianjiToolGrantAuthorizer", TianjiToolGrantAuthorizer.class);
                var bindings = context.getBean("tianjiToolGrantBindings", TianjiToolGrantBindings.class); var store = context.getBean("toolCapabilityStore", ToolCapabilityStore.class);
                assertTrue(context.getBean(TianjiExecutionGrantIssuer.class).usesToolIssuer(issuer));
                assertTrue(context.getBean(TianjiExecutionGrantAuthorizer.class).usesToolAuthorization(pdp));
                assertTrue(issuer.usesLedger(fixture.ledger)); assertTrue(issuer.usesBindings(bindings)); assertTrue(issuer.usesIssuerRegistry(fixture.issuers)); assertTrue(issuer.usesResource(RESOURCE));
                assertTrue(pdp.usesLedger(fixture.ledger)); assertTrue(pdp.usesBindings(bindings)); assertTrue(pdp.usesIssuerRegistry(fixture.issuers)); assertTrue(pdp.usesResource(RESOURCE));
                assertTrue(store.usesLedger(fixture.ledger)); assertNotNull(context.getBean("toolActionIntentStore", ToolActionIntentStore.class));
                assertEquals(2, toolStorage.constructed().size()); toolStorage.constructed().forEach(value -> verify(value).verify());
                assertEquals(1, oldStorage.constructed().size());
            });
        }
        verifyNoInteractions(fixture.source); fixture.noOwnerCalls();
    }

    @Test void actual_service_factories_preserve_the_existing_source_and_connect_both_roots() {
        var fixture = new Fixture();
        try (var ignored = mockConstruction(AgentStorageVerifier.class); var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class)) {
            fixture.runner(true).run(context -> {
                assertNull(context.getStartupFailure());
                assertEquals(1, context.getBeansOfType(TianjiToolGrantIssuer.class).size());
                var issuer = context.getBean("tianjiToolGrantIssuer", TianjiToolGrantIssuer.class); var pdp = context.getBean("tianjiToolGrantAuthorizer", TianjiToolGrantAuthorizer.class);
                var service = context.getBean(TianjiServiceExecutionBindings.class); var bindings = context.getBean("tianjiToolGrantBindings", TianjiToolGrantBindings.class);
                assertTrue(bindings.usesServices(service)); assertTrue(issuer.usesServices(service));
                assertTrue(context.getBean(TianjiExecutionGrantIssuer.class).usesToolIssuer(issuer)); assertTrue(context.getBean(TianjiServiceExecutionGrantIssuer.class).usesToolIssuer(issuer));
                assertTrue(context.getBean(TianjiExecutionGrantAuthorizer.class).usesToolAuthorization(pdp));
                assertEquals(2, toolStorage.constructed().size());
            });
        }
        verifyNoInteractions(fixture.source); fixture.noOwnerCalls();
    }

    @Test void user_only_tools_coexist_with_enabled_existing_service_execution() {
        var fixture = new Fixture();
        try (var ignored = mockConstruction(AgentStorageVerifier.class); var storage = mockConstruction(ToolExecutionStorageVerifier.class)) {
            fixture.runner(false).withUserConfiguration(TianjiServiceExecutionConfiguration.class, LinkedMetadata.class).withPropertyValues(serviceValues()).run(context -> {
                assertNull(context.getStartupFailure());
                var issuer = context.getBean("tianjiToolGrantIssuer", TianjiToolGrantIssuer.class);
                assertFalse(context.getBean("tianjiToolGrantBindings", TianjiToolGrantBindings.class).hasServiceBindings());
                assertTrue(context.getBean(TianjiServiceExecutionGrantIssuer.class).usesToolIssuer(issuer));
                assertTrue(context.getBean(TianjiExecutionGrantAuthorizer.class).supportsService()); assertEquals(2, storage.constructed().size());
            });
        }
        verifyNoInteractions(fixture.source); fixture.noOwnerCalls();
    }

    @Test void eager_proof_rejects_missing_or_disconnected_roots_even_with_lazy_beans() {
        var fixture = new Fixture();
        try (var ignored = mockConstruction(ToolExecutionStorageVerifier.class)) {
            fixture.dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiToolAuthorityConfiguration.class, LazyBeans.class))
                    .withPropertyValues(values(false)).run(context -> assertNotNull(context.getStartupFailure()));
            var user = mock(TianjiExecutionGrantIssuer.class); var runs = mock(TianjiExecutionGrantAuthorizer.class);
            fixture.dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiToolAuthorityConfiguration.class, LazyBeans.class))
                    .withPropertyValues(values(false)).withBean(TianjiExecutionGrantIssuer.class, () -> user).withBean(TianjiExecutionGrantAuthorizer.class, () -> runs)
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
        verifyNoInteractions(fixture.source); fixture.noOwnerCalls();
    }

    @Test void eager_chain_initializes_both_store_readiness_checks_under_global_lazy() {
        var fixture = new Fixture(); var user = mock(TianjiExecutionGrantIssuer.class); var runs = mock(TianjiExecutionGrantAuthorizer.class);
        when(user.usesToolIssuer(any())).thenReturn(true); when(runs.usesToolAuthorization(any())).thenReturn(true);
        try (var issuer = mockConstruction(TianjiToolGrantIssuer.class); var storage = mockConstruction(ToolExecutionStorageVerifier.class)) {
            fixture.dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiToolAuthorityConfiguration.class, LazyBeans.class))
                    .withPropertyValues(values(false)).withBean(TianjiExecutionGrantIssuer.class, () -> user).withBean(TianjiExecutionGrantAuthorizer.class, () -> runs)
                    .run(context -> {
                        assertNull(context.getStartupFailure()); assertEquals(1, issuer.constructed().size());
                        assertEquals(2, storage.constructed().size()); storage.constructed().forEach(value -> verify(value).verify());
                    });
        }
        verifyNoInteractions(fixture.source); fixture.noOwnerCalls();
    }

    @Configuration(proxyBeanMethods = false)
    static class LazyBeans {
        @Bean static BeanFactoryPostProcessor lazyDefinitions() { return factory -> {
            for (var name : factory.getBeanDefinitionNames()) if (!name.equals("tianjiToolAuthorityChainVerifier")) factory.getBeanDefinition(name).setLazyInit(true);
        }; }
    }
    @EnableConfigurationProperties(TianjiLinkedDispatchProperties.class)
    static class LinkedMetadata { }

    private static final class Fixture {
        final DataSource source = mock(DataSource.class);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class);
        final IdentityAuthorityProperties authority = new IdentityAuthorityProperties();
        final Rbac3StarterProperties rbac = new Rbac3StarterProperties();
        Fixture() {
            when(ledger.usesDataSource(source)).thenReturn(true);
            when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,
                    URI.create("https://idp.example/jwks"), "tenant:21", TrustedIssuerRegistry.RealmType.TENANT, "21", TrustedIssuerRegistry.Status.ACTIVE, 1)));
            when(oauth.authorizationKey(any())).thenAnswer(invocation -> {
                IdpServiceTokenRequest request = invocation.getArgument(0);
                return new ServiceAuthorizationKey(ISSUER.toASCIIString(), request.registrationId(), "agent", 1, request.appId(), request.audience(), request.context(), request.tenantId(), request.scopes());
            });
            authority.setBaseUri(URI.create("https://idp.example")); authority.setTimeout(Duration.ofSeconds(1));
            var binding = new IdentityAuthorityProperties.Binding(); binding.setIssuer(ISSUER); binding.setSourceResource(RESOURCE); binding.setRegistrationId("agent");
            binding.setAudience(URI.create("https://idp.example/api")); binding.setContext(ServiceTokenContext.TENANT); binding.setTenantId("21");
            binding.setScopes(Set.of(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE, IdentityAuthorityScopes.SERVICE_EXECUTION_GRANT_ISSUE)); authority.setBindings(Map.of("agent", binding));
            rbac.setSystemCode("tianji"); rbac.getAuthorization().setEndpoint("https://rbac.example"); rbac.getAuthorization().getServiceToken().setResourceUri("https://rbac.example/api");
        }
        ApplicationContextRunner dependencies(ApplicationContextRunner runner) {
            return runner.withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                    .withBean("idpStarterProperties", IdpStarterProperties.class, TianjiToolAuthorityConfigurationTest::starter)
                    .withBean("identityAuthorityProperties", IdentityAuthorityProperties.class, () -> authority)
                    .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers).withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> oauth)
                    .withBean("jacksonObjectMapper", ObjectMapper.class, ObjectMapper::new).withBean(Rbac3StarterProperties.class, () -> rbac);
        }
        ApplicationContextRunner runner(boolean service) {
            var runner = dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiToolAuthorityConfiguration.class,
                    TianjiExecutionGrantConfiguration.class, TianjiExecutionAuthorizationConfiguration.class)).withPropertyValues(values(service));
            return service ? runner.withUserConfiguration(TianjiServiceExecutionConfiguration.class, LinkedMetadata.class).withPropertyValues(serviceValues()) : runner;
        }
        void noOwnerCalls() { verify(oauth, never()).authorize(any()); verify(oauth, never()).authorizeBounded(any(), anyString(), any()); }
    }

    private static MockEnvironment prerequisites() {
        var environment = new MockEnvironment(); for (var feature : List.of("api", "storage", "execution-grants", "execution-authorization")) environment.setProperty("stellar.tianji.agent." + feature + ".enabled", "true"); return environment;
    }
    private static IdpStarterProperties starter() { var properties = new IdpStarterProperties(); properties.setResourceUri(RESOURCE); properties.getServiceClient().setAppId("agent-app"); return properties; }
    private static TianjiToolAuthorityProperties properties(boolean service) { return new TianjiToolAuthorityProperties(true, Duration.ofMillis(750), 2, List.of(TOOL), List.of(mapping(service))); }
    private static TianjiToolGrantBindings.BindingVO mapping(boolean service) { return new TianjiToolGrantBindings.BindingVO(21, TrustedIssuerRegistry.issuerHash(ISSUER), service ? TrustedAgentActor.SubjectType.SERVICE : TrustedAgentActor.SubjectType.USER,
            "review", 1, HASH, TOOL.reference(), "TIANJI_TOOL_PROCESS_READ", "process.read"); }
    private static TianjiServiceExecutionProperties serviceProperties() { return new TianjiServiceExecutionProperties(true, List.of(new TianjiServiceExecutionProperties.Source(ISSUER, 21, "worker", URI.create("https://process.example/api")))); }
    private static TianjiLinkedDispatchProperties linkedProperties(String hash) { return new TianjiLinkedDispatchProperties(true, true, List.of(new TianjiLinkedDispatchProperties.Binding(ISSUER, 21, "worker", "process-app", "platform", "process", "test", "process-east",
            Set.of(WorkerScopeTypeEnum.BPMN), List.of(new TianjiLinkedDispatchProperties.Definition("review", 1, hash))))); }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) { ObjectProvider<T> provider = mock(ObjectProvider.class); when(provider.getIfAvailable()).thenReturn(value); return provider; }
    private static String[] values(boolean service) {
        return new String[]{"stellar.tianji.agent.api.enabled=true", "stellar.tianji.agent.storage.enabled=true", "stellar.tianji.agent.execution-grants.enabled=true", "stellar.tianji.agent.execution-authorization.enabled=true",
                PREFIX + "enabled=true", PREFIX + "timeout=750ms", PREFIX + "concurrency=2", PREFIX + "descriptors[0].key=process.read", PREFIX + "descriptors[0].version=1", PREFIX + "descriptors[0].name=process_read", PREFIX + "descriptors[0].description=Read process",
                PREFIX + "descriptors[0].input.fields.query=STRING", PREFIX + "descriptors[0].input.required[0]=query", PREFIX + "descriptors[0].output.fields.found=BOOLEAN", PREFIX + "descriptors[0].output.required[0]=found",
                PREFIX + "descriptors[0].policy.risk=READ_ONLY", PREFIX + "descriptors[0].policy.action-key=process.read", PREFIX + "descriptors[0].policy.execution-resource-key=process", PREFIX + "descriptors[0].policy.binding-sha256=" + "a".repeat(64),
                PREFIX + "bindings[0].tenant-id=21", PREFIX + "bindings[0].issuer-fingerprint=" + TrustedIssuerRegistry.issuerHash(ISSUER), PREFIX + "bindings[0].principal-type=" + (service ? "SERVICE" : "USER"), PREFIX + "bindings[0].definition-key=review",
                PREFIX + "bindings[0].definition-version=1", PREFIX + "bindings[0].definition-sha256=" + HASH, PREFIX + "bindings[0].tool.key=process.read", PREFIX + "bindings[0].tool.version=1", PREFIX + "bindings[0].tool.content-sha256=" + TOOL.reference().contentSha256(),
                PREFIX + "bindings[0].purpose=TIANJI_TOOL_PROCESS_READ", PREFIX + "bindings[0].authorization-resource-key=process.read"};
    }
    private static String[] serviceValues() {
        return new String[]{"stellar.tianji.agent.service-execution.enabled=true", "stellar.tianji.agent.service-execution.sources[0].issuer=" + ISSUER,
                "stellar.tianji.agent.service-execution.sources[0].tenant-id=21", "stellar.tianji.agent.service-execution.sources[0].client-id=worker", "stellar.tianji.agent.service-execution.sources[0].resource-uri=https://process.example/api",
                "stellar.tianji.agent.linked.enabled=true", "stellar.tianji.agent.linked.submission-enabled=true", "stellar.tianji.agent.linked.bindings[0].issuer=" + ISSUER, "stellar.tianji.agent.linked.bindings[0].tenant-id=21", "stellar.tianji.agent.linked.bindings[0].client-id=worker",
                "stellar.tianji.agent.linked.bindings[0].app-id=process-app", "stellar.tianji.agent.linked.bindings[0].business-code=platform", "stellar.tianji.agent.linked.bindings[0].application-code=process", "stellar.tianji.agent.linked.bindings[0].environment=test",
                "stellar.tianji.agent.linked.bindings[0].source-id=process-east", "stellar.tianji.agent.linked.bindings[0].scope-types[0]=BPMN", "stellar.tianji.agent.linked.bindings[0].definitions[0].key=review", "stellar.tianji.agent.linked.bindings[0].definitions[0].version=1", "stellar.tianji.agent.linked.bindings[0].definitions[0].content-sha256=" + HASH};
    }
}
