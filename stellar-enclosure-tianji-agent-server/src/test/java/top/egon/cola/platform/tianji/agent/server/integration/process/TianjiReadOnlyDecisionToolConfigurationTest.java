package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentRuntimeAutoConfiguration;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkReadOnlyDecisionToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentExecutionResourcesConfiguration;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolAuthorityProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantBindings;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiReadOnlyDecisionToolConfigurationTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.read-only-decision.";
    private static final URI OWNER_ISSUER = URI.create("https://requester.example/20");
    private static final URI RESOURCE = URI.create("https://agent.example/api");
    private static final String DEFINITION_SHA = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final TianjiFixedDecisionToolBindings.BackendVO BACKEND = new TianjiFixedDecisionToolBindings.BackendVO(20,
            URI.create("https://executor.example/20"), URI.create("https://process.example"), URI.create("https://process.example/api"),
            "process-client", "process-service", "process-app", "platform", "process", "test", "release-1", "c".repeat(64),
            "fixed-decision", "d".repeat(64), Map.of("amount", ProcessValueTypeEnum.INTEGER), Duration.ofSeconds(2));
    private static final ToolDescriptor TOOL = tool("fixed.decision", 1, ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, BACKEND.bindingSha256());
    private static final ToolDescriptor QUERY = tool("other.query", 1, ToolPolicy.Risk.READ_ONLY, "memory.query", "e".repeat(64));
    private static final ToolDescriptor WRITE = tool("other.write", 1, ToolPolicy.Risk.IDEMPOTENT_WRITE, "memory.write", "f".repeat(64));
    private static final ToolDescriptor UNUSED = tool("fixed.decision", 2, ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, BACKEND.bindingSha256());
    private static final List<ToolDescriptor> DESCRIPTORS = List.of(TOOL, QUERY, WRITE, UNUSED);
    private final TianjiReadOnlyDecisionToolConfiguration configuration = new TianjiReadOnlyDecisionToolConfiguration();

    @Test
    void disabled_configuration_has_no_required_dependencies_or_side_effects() {
        new ApplicationContextRunner().withUserConfiguration(TianjiReadOnlyDecisionToolConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure());
            for (String name : names()) assertFalse(context.containsBean(name), name);
        });
    }

    @Test
    void every_prerequisite_is_required_before_constructing_storage_or_http_resources() {
        var fixture = new Fixture();
        for (String feature : prerequisites()) {
            MockEnvironment environment = environment(); environment.setProperty("stellar.tianji.agent." + feature + ".enabled", "false");
            assertThrows(IllegalStateException.class, () -> configuration.toolInvocationStore(fixture.source, fixture.ledger, fixture.capabilities, fixture.authorization, environment));
            assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolHttpClient(environment));
        }
        fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
    }

    @Test
    void both_modes_are_rejected_before_any_owned_factory_constructs_verifies_or_resolves_its_roots() {
        var chain = new Chain(); var environment = environment(); environment.setProperty("stellar.tianji.agent.tools.governed-process.enabled", "true");
        var telemetry = provider(mock(OpenTelemetry.class));
        try (var stores = mockConstruction(ToolInvocationStore.class); var recorders = mockConstruction(ToolInvocationRecorder.class);
             var transports = mockConstruction(TianjiFixedDecisionToolTransport.class); var verifiers = mockConstruction(ToolExecutionStorageVerifier.class)) {
            assertAll(
                    () -> assertModeConflict(() -> configuration.toolInvocationStore(chain.source, chain.ledger, chain.capabilities, chain.authorization, environment)),
                    () -> assertModeConflict(() -> configuration.toolInvocationRecorder(chain.source, chain.ledger, chain.preparations, chain.capabilities, chain.authorization, environment)),
                    () -> assertModeConflict(() -> configuration.tianjiFixedDecisionToolBindings(chain.authority, properties(), chain.grants, environment)),
                    () -> assertModeConflict(() -> configuration.tianjiFixedDecisionToolHttpClient(environment).shutdownNow()),
                    () -> assertModeConflict(() -> configuration.tianjiFixedDecisionToolTransport(chain.bindings, chain.oauth, chain.authentication, chain.converter, chain.issuers, chain.http, properties(), environment)),
                    () -> assertModeConflict(() -> configuration.tianjiAdkReadOnlyDecisionTools(chain.recorder, chain.transport, chain.authority, chain.bindings, chain.grants, telemetry, environment)),
                    () -> assertModeConflict(() -> configuration.tianjiReadOnlyToolRuntimeChainVerifier(environment, chain.source, chain.ledger, chain.capabilities, chain.authorization,
                            chain.grants, chain.preparations, chain.recorder, chain.bindings, chain.http, chain.transport, chain.oauth, chain.authentication, chain.converter, chain.issuers,
                            chain.bridges, chain.executors, chain.runAuthorization).afterSingletonsInstantiated()));
            assertTrue(stores.constructed().isEmpty()); assertTrue(recorders.constructed().isEmpty()); assertTrue(transports.constructed().isEmpty()); assertTrue(verifiers.constructed().isEmpty());
        }
        verifyNoInteractions(telemetry, chain.bridges, chain.executors, chain.source); chain.noExternalCalls();
    }

    @Test
    void runtime_mode_conflict_precedes_each_missing_prerequisite_in_the_owned_guard() {
        for (String prerequisite : prerequisites()) {
            var environment = environment(); environment.setProperty("stellar.tianji.agent.tools.governed-process.enabled", "true");
            environment.setProperty("stellar.tianji.agent." + prerequisite + ".enabled", "false");
            assertModeConflict(() -> configuration.tianjiFixedDecisionToolHttpClient(environment).shutdownNow());
        }
    }

    @Test
    void preparation_and_recorder_each_verify_their_own_storage_once_and_keep_exact_roots() {
        var fixture = new Fixture();
        try (var verification = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var store = configuration.toolInvocationStore(fixture.source, fixture.ledger, fixture.capabilities, fixture.authorization, environment());
            var recorder = configuration.toolInvocationRecorder(fixture.source, fixture.ledger, store, fixture.capabilities, fixture.authorization, environment());
            assertTrue(store.usesDataSource(fixture.source)); assertTrue(store.usesLedger(fixture.ledger));
            assertTrue(store.usesCapabilities(fixture.capabilities)); assertTrue(store.usesAuthorization(fixture.authorization));
            assertTrue(recorder.usesDataSource(fixture.source)); assertTrue(recorder.usesLedger(fixture.ledger)); assertTrue(recorder.usesPreparations(store));
            assertTrue(recorder.usesCapabilities(fixture.capabilities)); assertTrue(recorder.usesAuthorization(fixture.authorization));
            assertEquals(2, verification.constructed().size()); verification.constructed().forEach(value -> verify(value, times(1)).verify());
        }
        fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
    }

    @Test
    void fixed_dmn_mapping_closes_only_the_actual_authority_subset_and_preserves_other_registrations() {
        var fixture = new Fixture();
        var fixed = configuration.tianjiFixedDecisionToolBindings(fixture.authority, properties(), fixture.grants, environment());
        assertEquals(List.of(profile()), fixed.bindings()); assertEquals(4, fixture.authority.descriptors().size());
        var base = mapping(TOOL); var owner = base.issuerFingerprint();
        List<TianjiToolGrantBindings.BindingVO> mismatches = List.of(
                new TianjiToolGrantBindings.BindingVO(21, owner, base.principalType(), base.definitionKey(), 1, DEFINITION_SHA, TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, "9".repeat(64), base.principalType(), base.definitionKey(), 1, DEFINITION_SHA, TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, owner, TrustedAgentActor.SubjectType.SERVICE, base.definitionKey(), 1, DEFINITION_SHA, TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, owner, base.principalType(), "another", 1, DEFINITION_SHA, TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, owner, base.principalType(), base.definitionKey(), 2, DEFINITION_SHA, TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, owner, base.principalType(), base.definitionKey(), 1, "8".repeat(64), TOOL.reference(), base.purpose(), base.authorizationResourceKey()),
                new TianjiToolGrantBindings.BindingVO(20, owner, base.principalType(), base.definitionKey(), 1, DEFINITION_SHA, UNUSED.reference(), base.purpose(), base.authorizationResourceKey()));
        for (var changed : mismatches) {
            var grants = new TianjiToolGrantBindings(DESCRIPTORS, List.of(changed), RESOURCE,
                    changed.principalType() == TrustedAgentActor.SubjectType.SERVICE ? mock(TianjiServiceExecutionBindings.class) : null);
            assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolBindings(fixture.authority, properties(), grants, environment()));
        }
        var missing = new TianjiToolGrantBindings(DESCRIPTORS, List.of(mapping(QUERY)), RESOURCE, null);
        assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolBindings(fixture.authority, properties(), missing, environment()));
        var extra = new TianjiToolGrantBindings(DESCRIPTORS, List.of(base, mismatches.get(3)), RESOURCE, null);
        assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolBindings(fixture.authority, properties(), extra, environment()));
        var duplicate = mock(TianjiToolGrantBindings.class); when(duplicate.bindings()).thenReturn(List.of(base, base));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolBindings(fixture.authority, properties(), duplicate, environment()));
        var duplicateProfiles = new TianjiReadOnlyDecisionToolProperties(true, 2, List.of(profile(), profile()));
        assertThrows(IllegalStateException.class, () -> configuration.tianjiFixedDecisionToolBindings(fixture.authority, duplicateProfiles, fixture.grants, environment()));
        fixture.noExternalCalls();
    }

    @Test
    void bridge_resolves_telemetry_by_type_before_construction_and_retains_the_original_host_resolver() {
        var fixture = new Fixture(); var recorder = mock(ToolInvocationRecorder.class); var transport = mock(TianjiFixedDecisionToolTransport.class);
        var bindings = new TianjiFixedDecisionToolBindings(DESCRIPTORS, List.of(profile()));
        ObjectProvider<OpenTelemetry> telemetry = provider(mock(OpenTelemetry.class));
        var captured = new AtomicReference<List<?>>();
        try (var constructors = mockConstruction(AdkReadOnlyDecisionToolBridge.class, (bridge, invocation) -> {
            verify(telemetry).getIfAvailable(); captured.set(invocation.arguments());
        })) {
            var bridge = configuration.tianjiAdkReadOnlyDecisionTools(recorder, transport, fixture.authority, bindings, fixture.grants, telemetry, environment());
            assertSame(constructors.constructed().getFirst(), bridge);
            assertEquals(List.of(TOOL), captured.get().get(2));
            @SuppressWarnings("unchecked")
            var resolver = (Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>>) captured.get().get(3);
            var material = material(Set.of(TOOL.versionKey(), QUERY.versionKey()), List.of(TOOL.reference(), QUERY.reference()));
            assertEquals(fixture.grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver.apply(material));
            assertEquals(2, resolver.apply(material).size(), "the source resolver must not silently filter another visible tool");
        }
        fixture.noExternalCalls(); verifyNoInteractions(recorder, transport);
    }

    @Test
    void an_absent_optional_telemetry_bean_does_not_install_or_replace_a_global_instance() {
        var fixture = new Fixture(); var recorder = mock(ToolInvocationRecorder.class); var transport = mock(TianjiFixedDecisionToolTransport.class);
        var telemetry = provider((OpenTelemetry) null);
        var bridge = configuration.tianjiAdkReadOnlyDecisionTools(recorder, transport, fixture.authority,
                new TianjiFixedDecisionToolBindings(DESCRIPTORS, List.of(profile())), fixture.grants, telemetry, environment());
        assertTrue(bridge.usesRecorder(recorder)); assertTrue(bridge.usesTransport(transport)); verify(telemetry).getIfAvailable();
        fixture.noExternalCalls();
    }

    @Test
    void actual_runtime_consumes_the_same_chain_even_with_boot_global_lazy_and_without_discovery() {
        for (boolean lazy : List.of(false, true)) {
            var fixture = new Fixture();
            try (var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class); var oldStorage = mockConstruction(AgentStorageVerifier.class)) {
                fixture.runner(lazy, true).run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeanFactory().containsSingleton("tianjiAdkReadOnlyDecisionTools"));
                    assertTrue(context.getBeanFactory().containsSingleton("tianjiAdkAgentRunExecutor"));
                    assertSame(fixture.authority, context.getBean(TianjiToolAuthorityProperties.class));
                    var bridge = context.getBean("tianjiAdkReadOnlyDecisionTools", AdkReadOnlyDecisionToolBridge.class);
                    var executor = context.getBean("tianjiAdkAgentRunExecutor", AdkAgentRunExecutor.class);
                    var recorder = context.getBean("toolInvocationRecorder", ToolInvocationRecorder.class);
                    var transport = context.getBean("tianjiFixedDecisionToolTransport", TianjiFixedDecisionToolTransport.class);
                    assertTrue(executor.usesLedger(fixture.ledger)); assertTrue(executor.usesReadOnlyDecisionTools(bridge));
                    assertTrue(executor.usesAuthorization(fixture.runAuthorization));
                    assertTrue(bridge.usesRecorder(recorder)); assertTrue(bridge.usesTransport(transport));
                    assertTrue(transport.usesBindings(context.getBean("tianjiFixedDecisionToolBindings", TianjiFixedDecisionToolBindings.class)));
                    assertTrue(transport.usesOAuth(fixture.oauth)); assertTrue(transport.usesAuthentication(fixture.authentication));
                    assertTrue(transport.usesConverter(fixture.converter)); assertTrue(transport.usesIssuerRegistry(fixture.issuers));
                    assertEquals(2, toolStorage.constructed().size()); toolStorage.constructed().forEach(value -> verify(value, times(1)).verify());
                    assertEquals(1, oldStorage.constructed().size());
                    assertFalse(context.containsBean("tianjiAgentDispatchScheduler"));
                    assertFalse(context.getEnvironment().getProperty("stellar.tianji.agent.dispatch.enabled", Boolean.class, false));
                });
            }
            fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
        }
    }

    @Test
    void each_storage_readiness_failure_blocks_global_lazy_startup_without_remote_calls() {
        for (int failingIndex : List.of(0, 1)) {
            var fixture = new Fixture();
            try (var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class, (verifier, invocation) -> {
                if (invocation.getCount() - 1 == failingIndex) doThrow(new IllegalStateException("private-storage-sentinel")).when(verifier).verify();
            }); var oldStorage = mockConstruction(AgentStorageVerifier.class)) {
                fixture.runner(true, true).run(context -> {
                    assertNotNull(context.getStartupFailure()); assertEquals(failingIndex + 1, toolStorage.constructed().size());
                    assertFalse(context.getStartupFailure().toString().contains("private-storage-sentinel"));
                });
            }
            fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
        }
    }

    @Test
    void missing_runtime_consumer_or_identity_dependency_is_not_hidden_by_global_lazy() {
        var fixture = new Fixture();
        try (var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class); var oldStorage = mockConstruction(AgentStorageVerifier.class)) {
            fixture.runner(true, false).run(context -> assertNotNull(context.getStartupFailure()));
            fixture.dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiReadOnlyDecisionToolConfiguration.class), false)
                    .withPropertyValues(values()).withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
        fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
    }

    @Test
    void eager_verifier_requires_every_selected_root_and_actual_executor_consumption() {
        List<Consumer<Chain>> breaks = List.of(
                chain -> when(chain.ledger.usesDataSource(chain.source)).thenReturn(false),
                chain -> when(chain.capabilities.usesDataSource(chain.source)).thenReturn(false),
                chain -> when(chain.capabilities.usesLedger(chain.ledger)).thenReturn(false),
                chain -> when(chain.preparations.usesDataSource(chain.source)).thenReturn(false),
                chain -> when(chain.preparations.usesLedger(chain.ledger)).thenReturn(false),
                chain -> when(chain.preparations.usesCapabilities(chain.capabilities)).thenReturn(false),
                chain -> when(chain.preparations.usesAuthorization(chain.authorization)).thenReturn(false),
                chain -> when(chain.recorder.usesDataSource(chain.source)).thenReturn(false),
                chain -> when(chain.recorder.usesLedger(chain.ledger)).thenReturn(false),
                chain -> when(chain.recorder.usesPreparations(chain.preparations)).thenReturn(false),
                chain -> when(chain.recorder.usesCapabilities(chain.capabilities)).thenReturn(false),
                chain -> when(chain.recorder.usesAuthorization(chain.authorization)).thenReturn(false),
                chain -> when(chain.authorization.usesLedger(chain.ledger)).thenReturn(false),
                chain -> when(chain.authorization.usesBindings(chain.grants)).thenReturn(false),
                chain -> when(chain.authorization.usesIssuerRegistry(chain.issuers)).thenReturn(false),
                chain -> when(chain.transport.usesBindings(chain.bindings)).thenReturn(false),
                chain -> when(chain.transport.usesOAuth(chain.oauth)).thenReturn(false),
                chain -> when(chain.transport.usesAuthentication(chain.authentication)).thenReturn(false),
                chain -> when(chain.transport.usesConverter(chain.converter)).thenReturn(false),
                chain -> when(chain.transport.usesIssuerRegistry(chain.issuers)).thenReturn(false),
                chain -> when(chain.transport.usesHttpClient(chain.http)).thenReturn(false),
                chain -> when(chain.http.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS),
                chain -> when(chain.bridge.usesRecorder(chain.recorder)).thenReturn(false),
                chain -> when(chain.bridge.usesTransport(chain.transport)).thenReturn(false),
                chain -> when(chain.executor.usesLedger(chain.ledger)).thenReturn(false),
                chain -> when(chain.executor.usesReadOnlyDecisionTools(chain.bridge)).thenReturn(false));
        var checks = new ArrayList<org.junit.jupiter.api.function.Executable>();
        checks.add(() -> {
            var healthy = new Chain(); var proof = healthy.proof(configuration);
            verifyNoInteractions(healthy.bridges, healthy.executors); assertDoesNotThrow(proof::afterSingletonsInstantiated);
            verify(healthy.bridges).getObject(); verify(healthy.executors).getObject();
        });
        for (var mutation : breaks) checks.add(() -> {
            var broken = new Chain(); mutation.accept(broken);
            assertThrows(IllegalStateException.class, broken.proof(configuration)::afterSingletonsInstantiated);
            broken.noExternalCalls();
        });
        assertAll(checks);
    }

    @Test
    void actual_runtime_must_consume_the_run_authorizer_that_consumes_this_tool_authority() {
        assertAll(
                () -> {
                    var chain = new Chain(); when(chain.executor.usesAuthorization(chain.runAuthorization)).thenReturn(false);
                    assertThrows(IllegalStateException.class, chain.proof(configuration)::afterSingletonsInstantiated);
                },
                () -> {
                    var chain = new Chain(); when(chain.runAuthorization.usesToolAuthorization(chain.authorization)).thenReturn(false);
                    assertThrows(IllegalStateException.class, chain.proof(configuration)::afterSingletonsInstantiated);
                },
                () -> {
                    var fixture = new Fixture();
                    try (var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class); var oldStorage = mockConstruction(AgentStorageVerifier.class)) {
                        fixture.runner(true, true).withBean("foreignRunAuthorization", AgentRunAuthorization.class,
                                () -> mock(AgentRunAuthorization.class), definition -> definition.setPrimary(true))
                                .run(context -> assertNotNull(context.getStartupFailure(), "a valid unused run authorizer must not prove the worker chain"));
                    }
                    fixture.noExternalCalls();
                });
    }

    @Test
    void owned_http_and_transport_are_closed_while_shared_clients_are_untouched() throws Exception {
        var fixture = new Fixture(); var http = new AtomicReference<HttpClient>(); var transport = new AtomicReference<TianjiFixedDecisionToolTransport>();
        try (var toolStorage = mockConstruction(ToolExecutionStorageVerifier.class); var oldStorage = mockConstruction(AgentStorageVerifier.class)) {
            fixture.runner(false, true).run(context -> {
                assertNull(context.getStartupFailure());
                http.set(context.getBean("tianjiFixedDecisionToolHttpClient", HttpClient.class));
                transport.set(context.getBean("tianjiFixedDecisionToolTransport", TianjiFixedDecisionToolTransport.class));
                assertEquals(HttpClient.Redirect.NEVER, http.get().followRedirects()); assertEquals(Duration.ofSeconds(3), http.get().connectTimeout().orElseThrow());
            });
        }
        assertNotNull(http.get()); assertTrue(http.get().awaitTermination(Duration.ofSeconds(2)));
        var material = material(Set.of(TOOL.versionKey()), List.of(TOOL.reference()));
        var request = new ReadOnlyDecisionToolTransport.Request(material.authorization(), new AgentRunLease(material.run().runId(), "worker", 1, NOW.plusSeconds(30)),
                TOOL, Map.of("amount", 7L), UUID.randomUUID(), NOW.plusSeconds(20));
        assertEquals(ReadOnlyDecisionToolTransport.FailureKind.CANCELLED, assertThrows(ReadOnlyDecisionToolTransport.Failure.class,
                () -> transport.get().withVerifiedCall(request, (executor, operation) -> { throw new AssertionError("closed transport dispatched"); })).kind());
        fixture.noExternalCalls(); verifyNoInteractions(fixture.source);
    }

    @Test
    void configuration_has_exact_seven_named_factories_with_explicit_lifecycle_and_lazy_boundaries() throws Exception {
        var methods = Arrays.stream(TianjiReadOnlyDecisionToolConfiguration.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Bean.class)).toList();
        assertEquals(7, methods.size());
        assertEquals(Set.copyOf(names()), methods.stream().map(method -> {
            Bean bean = method.getAnnotation(Bean.class); return bean.value().length == 0 ? method.getName() : bean.value()[0];
        }).collect(java.util.stream.Collectors.toSet()));
        var bridge = methods.stream().filter(method -> method.getName().equals("tianjiAdkReadOnlyDecisionTools")).findFirst().orElseThrow();
        assertTrue(bridge.getAnnotation(Lazy.class).value());
        var telemetry = Arrays.stream(bridge.getParameters()).filter(parameter -> parameter.getParameterizedType().getTypeName().contains("OpenTelemetry")).findFirst().orElseThrow();
        assertNull(telemetry.getAnnotation(Qualifier.class));
        var verifier = methods.stream().filter(method -> method.getName().equals("tianjiReadOnlyToolRuntimeChainVerifier")).findFirst().orElseThrow();
        assertFalse(verifier.getAnnotation(Lazy.class).value());
        assertEquals("shutdownNow", TianjiReadOnlyDecisionToolConfiguration.class.getDeclaredMethod("tianjiFixedDecisionToolHttpClient", org.springframework.core.env.Environment.class).getAnnotation(Bean.class).destroyMethod());
    }

    private static void assertModeConflict(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation); assertEquals("TIANJI_TOOL_RUNTIME_MODE_CONFLICT", failure.getMessage()); assertNull(failure.getCause());
    }

    private static class Fixture {
        final DataSource source = mock(DataSource.class); final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final ToolCapabilityStore capabilities = mock(ToolCapabilityStore.class); final TianjiToolGrantAuthorizer authorization = mock(TianjiToolGrantAuthorizer.class);
        final TianjiExecutionGrantAuthorizer runAuthorization = mock(TianjiExecutionGrantAuthorizer.class);
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class); final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final TenantIssuerAuthenticationManagerComponent authentication = mock(TenantIssuerAuthenticationManagerComponent.class); final VerifiedPrincipalConverter converter = mock(VerifiedPrincipalConverter.class);
        final TianjiToolAuthorityProperties authority = new TianjiToolAuthorityProperties(true, Duration.ofSeconds(1), 2, DESCRIPTORS, List.of(mapping(TOOL), mapping(QUERY), mapping(WRITE)));
        final TianjiToolGrantBindings grants = new TianjiToolGrantBindings(DESCRIPTORS, authority.bindings(), RESOURCE, null);
        Fixture() {
            when(ledger.usesDataSource(source)).thenReturn(true); when(capabilities.usesDataSource(source)).thenReturn(true); when(capabilities.usesLedger(ledger)).thenReturn(true);
            when(authorization.usesLedger(ledger)).thenReturn(true); when(authorization.usesBindings(grants)).thenReturn(true); when(authorization.usesIssuerRegistry(issuers)).thenReturn(true);
            when(runAuthorization.usesToolAuthorization(authorization)).thenReturn(true);
        }
        ApplicationContextRunner dependencies(ApplicationContextRunner runner, boolean identity) {
            var configured = runner.withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                    .withBean("toolCapabilityStore", ToolCapabilityStore.class, () -> capabilities).withBean("tianjiToolGrantAuthorizer", TianjiToolGrantAuthorizer.class, () -> authorization)
                    .withBean("tianjiExecutionGrantAuthorizer", TianjiExecutionGrantAuthorizer.class, () -> runAuthorization)
                    .withBean("tianjiToolGrantBindings", TianjiToolGrantBindings.class, () -> grants)
                    .withInitializer(context -> context.getBeanFactory().registerSingleton("fixtureToolAuthorityProperties", authority))
                    .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> oauth)
                    .withBean("tenantIssuerAuthenticationManagerComponent", TenantIssuerAuthenticationManagerComponent.class, () -> authentication)
                    .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers);
            return identity ? configured.withBean("verifiedPrincipalConverter", VerifiedPrincipalConverter.class, () -> converter) : configured;
        }
        ApplicationContextRunner runner(boolean lazy, boolean runtime) {
            var runner = dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiReadOnlyDecisionToolConfiguration.class), true).withPropertyValues(values());
            if (runtime) runner = runner.withConfiguration(AutoConfigurations.of(TianjiAgentRuntimeAutoConfiguration.class))
                    .withUserConfiguration(TianjiAgentExecutionResourcesConfiguration.class)
                    .withBean(AgentFlowFactory.class, () -> mock(AgentFlowFactory.class)).withBean(AgentFlowConfigValidator.class, () -> mock(AgentFlowConfigValidator.class))
                    .withBean("agentFlowChatModels", Map.class, () -> Map.of("fixtureModel", mock(ChatModel.class)))
                    .withBean(AgentModelBudgetEstimator.class, () -> mock(AgentModelBudgetEstimator.class));
            return lazy ? runner.withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor())) : runner;
        }
        void noExternalCalls() { verify(oauth, never()).authorize(any()); verify(oauth, never()).authorizeBounded(any(), anyString(), any()); verifyNoInteractions(authentication, converter); }
    }

    private static final class Chain extends Fixture {
        final ToolInvocationStore preparations = mock(ToolInvocationStore.class); final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
        final TianjiFixedDecisionToolBindings bindings = new TianjiFixedDecisionToolBindings(DESCRIPTORS, List.of(profile()));
        final HttpClient http = mock(HttpClient.class); final TianjiFixedDecisionToolTransport transport = mock(TianjiFixedDecisionToolTransport.class);
        final AdkReadOnlyDecisionToolBridge bridge = mock(AdkReadOnlyDecisionToolBridge.class); final AdkAgentRunExecutor executor = mock(AdkAgentRunExecutor.class);
        final ObjectProvider<AdkReadOnlyDecisionToolBridge> bridges = provider(bridge); final ObjectProvider<AdkAgentRunExecutor> executors = provider(executor);
        Chain() {
            when(preparations.usesDataSource(source)).thenReturn(true); when(preparations.usesLedger(ledger)).thenReturn(true);
            when(preparations.usesCapabilities(capabilities)).thenReturn(true); when(preparations.usesAuthorization(authorization)).thenReturn(true);
            when(recorder.usesDataSource(source)).thenReturn(true); when(recorder.usesLedger(ledger)).thenReturn(true); when(recorder.usesPreparations(preparations)).thenReturn(true);
            when(recorder.usesCapabilities(capabilities)).thenReturn(true); when(recorder.usesAuthorization(authorization)).thenReturn(true);
            when(transport.usesBindings(bindings)).thenReturn(true); when(transport.usesOAuth(oauth)).thenReturn(true); when(transport.usesAuthentication(authentication)).thenReturn(true);
            when(transport.usesConverter(converter)).thenReturn(true); when(transport.usesIssuerRegistry(issuers)).thenReturn(true); when(transport.usesHttpClient(http)).thenReturn(true);
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(bridge.usesRecorder(recorder)).thenReturn(true); when(bridge.usesTransport(transport)).thenReturn(true);
            when(executor.usesLedger(ledger)).thenReturn(true); when(executor.usesReadOnlyDecisionTools(bridge)).thenReturn(true);
            when(executor.usesAuthorization(runAuthorization)).thenReturn(true);
        }
        org.springframework.beans.factory.SmartInitializingSingleton proof(TianjiReadOnlyDecisionToolConfiguration configuration) {
            return configuration.tianjiReadOnlyToolRuntimeChainVerifier(environment(), source, ledger, capabilities, authorization, grants, preparations, recorder,
                    bindings, http, transport, oauth, authentication, converter, issuers, bridges, executors, runAuthorization);
        }
    }

    private static ToolDescriptor tool(String key, long version, ToolPolicy.Risk risk, String resource, String hash) {
        return new ToolDescriptor(key, version, key.replace('.', '_') + "_" + version, "Fixed tool metadata",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of("value", ToolSchema.ValueType.STRING), Set.of("value")), new ToolPolicy(risk, "decision.evaluate", resource, hash, null));
    }
    private static TianjiToolGrantBindings.BindingVO mapping(ToolDescriptor descriptor) {
        return new TianjiToolGrantBindings.BindingVO(20, TrustedIssuerRegistry.issuerHash(OWNER_ISSUER), TrustedAgentActor.SubjectType.USER,
                "assistant", 1, DEFINITION_SHA, descriptor.reference(), "TIANJI_TOOL_FIXED_DECISION", descriptor.policy().executionResourceKey());
    }
    private static TianjiFixedDecisionToolBindings.BindingVO profile() {
        return new TianjiFixedDecisionToolBindings.BindingVO(20, TrustedIssuerRegistry.issuerHash(OWNER_ISSUER), TrustedAgentActor.SubjectType.USER,
                "assistant", 1, DEFINITION_SHA, TOOL.reference(), BACKEND);
    }
    private static TianjiReadOnlyDecisionToolProperties properties() { return new TianjiReadOnlyDecisionToolProperties(true, 2, List.of(profile())); }
    private static List<String> prerequisites() { return List.of("api", "storage", "execution-grants", "execution-authorization", "runtime", "tools.authority"); }
    private static MockEnvironment environment() { var value = new MockEnvironment(); prerequisites().forEach(feature -> value.setProperty("stellar.tianji.agent." + feature + ".enabled", "true")); return value; }
    private static String[] values() {
        var values = new ArrayList<String>(); prerequisites().forEach(feature -> values.add("stellar.tianji.agent." + feature + ".enabled=true"));
        values.addAll(List.of(PREFIX + "enabled=true", PREFIX + "concurrency=2",
                PREFIX + "bindings[0].tenant-id=20", PREFIX + "bindings[0].owner-issuer-fingerprint=" + TrustedIssuerRegistry.issuerHash(OWNER_ISSUER),
                PREFIX + "bindings[0].principal-type=USER", PREFIX + "bindings[0].definition-key=assistant", PREFIX + "bindings[0].definition-version=1",
                PREFIX + "bindings[0].definition-content-sha256=" + DEFINITION_SHA, PREFIX + "bindings[0].tool.key=" + TOOL.key(),
                PREFIX + "bindings[0].tool.version=1", PREFIX + "bindings[0].tool.content-sha256=" + TOOL.reference().contentSha256(),
                PREFIX + "bindings[0].backend.tenant-id=20", PREFIX + "bindings[0].backend.process-issuer=" + BACKEND.processIssuer(),
                PREFIX + "bindings[0].backend.process-base-uri=" + BACKEND.processBaseUri(), PREFIX + "bindings[0].backend.process-resource-uri=" + BACKEND.processResourceUri(),
                PREFIX + "bindings[0].backend.registration-id=" + BACKEND.registrationId(), PREFIX + "bindings[0].backend.expected-client-id=" + BACKEND.expectedClientId(),
                PREFIX + "bindings[0].backend.expected-app-id=" + BACKEND.expectedAppId(), PREFIX + "bindings[0].backend.expected-source-biz=" + BACKEND.expectedSourceBiz(),
                PREFIX + "bindings[0].backend.expected-source-app=" + BACKEND.expectedSourceApp(), PREFIX + "bindings[0].backend.expected-environment=" + BACKEND.expectedEnvironment(),
                PREFIX + "bindings[0].backend.release-version=" + BACKEND.releaseVersion(), PREFIX + "bindings[0].backend.manifest-sha256=" + BACKEND.manifestSha256(),
                PREFIX + "bindings[0].backend.decision-key=" + BACKEND.decisionKey(), PREFIX + "bindings[0].backend.decision-model-sha256=" + BACKEND.decisionModelSha256(),
                PREFIX + "bindings[0].backend.input-schema.amount=INTEGER", PREFIX + "bindings[0].backend.timeout=2s"));
        return values.toArray(String[]::new);
    }
    private static List<String> names() { return List.of("toolInvocationStore", "toolInvocationRecorder", "tianjiFixedDecisionToolBindings", "tianjiFixedDecisionToolHttpClient", "tianjiFixedDecisionToolTransport", "tianjiAdkReadOnlyDecisionTools", "tianjiReadOnlyToolRuntimeChainVerifier"); }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) { var provider = (ObjectProvider<T>) mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); when(provider.getIfAvailable()).thenReturn(value); return provider; }
    private static AgentLedgerService.ExecutionMaterial material(Set<String> selected, List<DefinitionResourceRef> declared) {
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", OWNER_ISSUER.toASCIIString());
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 3, 2, 1000, 100,
                BigDecimal.ONE, "USD", 1, selected, "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), declared, "route", "policy"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, DEFINITION_SHA, policy, new AgentInputDTO("fixed task", null, null), null);
        var run = new AgentRunDTO("00000000-0000-0000-0000-000000000001", "assistant", 1, null, AgentRunStatusEnum.RUNNING, null, false, 1, 1, 0, NOW.plusSeconds(30), NOW, null);
        var authorization = new AgentRunAuthorization.Request(actor, run.runId(), "assistant", 1, DEFINITION_SHA, "7".repeat(64), "6".repeat(64), "policy", false, run.deadlineAt());
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }
}
