package top.egon.cola.platform.tianji.agent.server.integration.process;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentRuntimeAutoConfiguration;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge.StartActionContextResolver;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkToolTelemetryPrivacy;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolExecutionStorageVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.agent.server.TianjiAgentExecutionResourcesConfiguration;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServiceExecutionBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolAuthorityProperties;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantAuthorizer;
import top.egon.cola.platform.tianji.agent.server.security.TianjiToolGrantBindings;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
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

/** Exercises the real assembly and worker graph without database, identity or model network calls. */
class TianjiGovernedProcessToolConfigurationTest {
    private static final String PREFIX = "stellar.tianji.agent.tools.governed-process.";
    private static final String HOST = "tianjiToolStartActionContextResolver";
    private static final URI OWNER = URI.create("https://requester.example/20"), RESOURCE = URI.create("https://agent.example/api");
    private static final String SHA = "b".repeat(64), ISSUER = TrustedIssuerRegistry.issuerHash(OWNER);
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final TianjiFixedDecisionToolBindings.BackendVO DMN = new TianjiFixedDecisionToolBindings.BackendVO(20,
            URI.create("https://executor.example/20"), URI.create("https://process.example"), URI.create("https://process.example/api"),
            "process-client", "process-service", "process-app", "platform", "process", "test", "release-1", "c".repeat(64),
            "fixed-decision", "d".repeat(64), Map.of("amount", ProcessValueTypeEnum.INTEGER), Duration.ofSeconds(2));
    private static final TianjiFixedBpmnStartToolBindings.BackendVO BPMN = new TianjiFixedBpmnStartToolBindings.BackendVO(20,
            DMN.processIssuer(), DMN.processBaseUri(), DMN.processResourceUri(), DMN.registrationId(), DMN.expectedClientId(), DMN.expectedAppId(),
            DMN.expectedSourceBiz(), DMN.expectedSourceApp(), DMN.expectedEnvironment(), DMN.releaseVersion(), DMN.manifestSha256(),
            "fixed-process", "e".repeat(64), DMN.inputSchema(), DMN.timeout());
    private static final ToolDescriptor READ = tool("fixed.decision", 1, ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, DMN.bindingSha256());
    private static final ToolDescriptor START = tool("fixed.start", 1, ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, BPMN.bindingSha256());
    private static final ToolDescriptor QUERY = tool("other.query", 1, ToolPolicy.Risk.READ_ONLY, "memory.query", "f".repeat(64));
    private static final ToolDescriptor UNUSED_READ = tool("fixed.decision", 2, ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, DMN.bindingSha256());
    private static final ToolDescriptor UNUSED_START = tool("fixed.start", 2, ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, BPMN.bindingSha256());
    private static final List<ToolDescriptor> DESCRIPTORS = List.of(READ, START, QUERY, UNUSED_READ, UNUSED_START);
    private final TianjiGovernedProcessToolConfiguration configuration = new TianjiGovernedProcessToolConfiguration();

    @Test void disabled_configuration_requires_no_dependencies_or_host_resolver() {
        new ApplicationContextRunner().withUserConfiguration(TianjiGovernedProcessToolConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure()); names().forEach(name -> assertFalse(context.containsBean(name), name));
        });
    }

    @Test void all_six_prerequisites_guard_every_owned_factory_before_construction_or_resolution() {
        for (String feature : prerequisites()) {
            var chain = new Chain(); var env = environment(); env.setProperty("stellar.tianji.agent." + feature + ".enabled", "false");
            for (var operation : chain.operations(configuration, env)) assertThrows(IllegalStateException.class, operation, feature);
            verifyNoInteractions(chain.source, chain.bridges, chain.executors, chain.resolvers); chain.noExternalCalls();
        }
    }

    @Test void mutual_mode_conflict_precedes_prerequisites_and_all_owned_factory_work() {
        var chain = new Chain(); var env = environment(); env.setProperty("stellar.tianji.agent.tools.read-only-decision.enabled", "true");
        for (String feature : prerequisites()) env.setProperty("stellar.tianji.agent." + feature + ".enabled", "false");
        try (var verifiers = mockConstruction(ToolExecutionStorageVerifier.class);
             var stores = mockConstruction(ToolInvocationStore.class); var recorders = mockConstruction(ToolInvocationRecorder.class);
             var accesses = mockConstruction(ToolResultAccessStore.class)) {
            for (var operation : chain.operations(configuration, env)) {
                var failure = assertThrows(IllegalStateException.class, operation);
                assertEquals("TIANJI_TOOL_RUNTIME_MODE_CONFLICT", failure.getMessage()); assertNull(failure.getCause());
            }
            assertTrue(verifiers.constructed().isEmpty()); assertTrue(stores.constructed().isEmpty());
            assertTrue(recorders.constructed().isEmpty()); assertTrue(accesses.constructed().isEmpty());
        }
        verifyNoInteractions(chain.source, chain.bridges, chain.executors, chain.resolvers); chain.noExternalCalls();
    }

    @Test void actual_preparation_and_invocation_verify_v13_and_actual_access_store_verifies_v14_once() {
        var f = new Fixture();
        try (var verification = mockConstruction(ToolExecutionStorageVerifier.class)) {
            var prep = configuration.governedProcessToolInvocationStore(f.source, f.ledger, f.capabilities, f.authorization, environment());
            var recorder = configuration.governedProcessToolInvocationRecorder(f.source, f.ledger, prep, f.capabilities, f.authorization, environment());
            var access = configuration.governedProcessToolResultAccessStore(f.source, f.ledger, f.capabilities, f.authorization, environment());
            var facade = configuration.governedProcessToolResultAccessRecorder(access, f.ledger, environment());
            assertTrue(prep.usesDataSource(f.source)); assertTrue(prep.usesLedger(f.ledger)); assertTrue(prep.usesCapabilities(f.capabilities)); assertTrue(prep.usesAuthorization(f.authorization));
            assertTrue(recorder.usesDataSource(f.source)); assertTrue(recorder.usesLedger(f.ledger)); assertTrue(recorder.usesPreparations(prep));
            assertTrue(recorder.usesCapabilities(f.capabilities)); assertTrue(recorder.usesAuthorization(f.authorization));
            assertTrue(access.usesDataSource(f.source)); assertTrue(access.usesLedger(f.ledger)); assertTrue(access.usesCapabilities(f.capabilities)); assertTrue(access.usesAuthorization(f.authorization));
            assertTrue(facade.usesStore(access)); assertTrue(facade.usesLedger(f.ledger)); assertEquals(3, verification.constructed().size());
            verify(verification.constructed().get(0)).verify(); verify(verification.constructed().get(1)).verify(); verify(verification.constructed().get(2)).verifyResultAccess();
            verify(verification.constructed().get(0), never()).verifyResultAccess(); verify(verification.constructed().get(2), never()).verify();
        }
        f.noExternalCalls(); verifyNoInteractions(f.source);
    }

    @Test void both_fixed_profile_sets_close_only_the_actual_supported_authority_slots() {
        var f = new Fixture();
        assertEquals(List.of(decisionProfile()), configuration.tianjiGovernedProcessDecisionBindings(f.authority, properties(), f.grants, environment()).bindings());
        assertEquals(List.of(startProfile()), configuration.tianjiGovernedProcessStartBindings(f.authority, properties(), f.grants, environment()).bindings());
        assertEquals(5, f.authority.descriptors().size()); f.noExternalCalls();
    }

    @Test void disabled_authority_or_disabled_profile_properties_do_not_construct_a_supported_profile_set() {
        var f = new Fixture();
        var disabledAuthority = new TianjiToolAuthorityProperties(false, Duration.ofSeconds(1), 2, DESCRIPTORS, f.authority.bindings());
        var disabledProperties = new TianjiGovernedProcessToolProperties(false, 2, List.of(decisionProfile()), List.of(startProfile()));
        for (var descriptor : List.of(READ, START)) {
            assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, disabledAuthority, properties(), f.grants));
            assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, f.authority, disabledProperties, f.grants));
        }
        f.noExternalCalls();
    }

    @Test void every_owner_definition_and_tool_tuple_component_is_part_of_each_profile_closure() {
        var f = new Fixture();
        for (var descriptor : List.of(READ, START)) {
            var base = mapping(descriptor);
            var changed = List.of(
                    new TianjiToolGrantBindings.BindingVO(21, ISSUER, base.principalType(), "assistant", 1, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    new TianjiToolGrantBindings.BindingVO(20, "9".repeat(64), base.principalType(), "assistant", 1, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    new TianjiToolGrantBindings.BindingVO(20, ISSUER, TrustedAgentActor.SubjectType.SERVICE, "assistant", 1, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    new TianjiToolGrantBindings.BindingVO(20, ISSUER, base.principalType(), "other", 1, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    new TianjiToolGrantBindings.BindingVO(20, ISSUER, base.principalType(), "assistant", 2, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    new TianjiToolGrantBindings.BindingVO(20, ISSUER, base.principalType(), "assistant", 1, "8".repeat(64), descriptor.reference(), base.purpose(), base.authorizationResourceKey()),
                    mapping(descriptor == READ ? UNUSED_READ : UNUSED_START));
            for (var value : changed) {
                var grants = new TianjiToolGrantBindings(DESCRIPTORS, List.of(value), RESOURCE,
                        value.principalType() == TrustedAgentActor.SubjectType.SERVICE ? mock(TianjiServiceExecutionBindings.class) : null);
                assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, f.authority, properties(), grants));
            }
        }
        f.noExternalCalls();
    }

    @Test void missing_extra_duplicate_and_approval_required_profiles_fail_closed() {
        var f = new Fixture();
        for (var descriptor : List.of(READ, START)) {
            var missing = new TianjiToolGrantBindings(DESCRIPTORS, List.of(mapping(QUERY)), RESOURCE, null);
            assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, f.authority, properties(), missing));
            var base = mapping(descriptor);
            var extra = new TianjiToolGrantBindings.BindingVO(20, ISSUER, base.principalType(), "another", 1, SHA, descriptor.reference(), base.purpose(), base.authorizationResourceKey());
            var expanded = new TianjiToolGrantBindings(DESCRIPTORS, List.of(base, extra), RESOURCE, null);
            assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, f.authority, properties(), expanded));
            var duplicate = mock(TianjiToolGrantBindings.class); when(duplicate.bindings()).thenReturn(List.of(base, base));
            assertThrows(IllegalStateException.class, () -> profileFactory(descriptor, f.authority, properties(), duplicate));
        }
        var duplicateProfiles = new TianjiGovernedProcessToolProperties(true, 2, List.of(decisionProfile(), decisionProfile()), List.of(startProfile(), startProfile()));
        assertThrows(IllegalStateException.class, () -> profileFactory(READ, f.authority, duplicateProfiles, f.grants));
        assertThrows(IllegalStateException.class, () -> profileFactory(START, f.authority, duplicateProfiles, f.grants));
        var approved = new ToolDescriptor("approved.start", 1, "approved_start", "Approval tool", START.input(), START.output(),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.approved", ProcessClient.START_RESOURCE_KEY, BPMN.bindingSha256(), "approval-v1"));
        var descriptors = List.of(READ, approved); var aaProfile = new TianjiFixedBpmnStartToolBindings.BindingVO(20, ISSUER, TrustedAgentActor.SubjectType.USER, "assistant", 1, SHA, approved.reference(), BPMN);
        var authority = new TianjiToolAuthorityProperties(true, Duration.ofSeconds(1), 2, descriptors, List.of(mapping(READ), mapping(approved)));
        var grants = new TianjiToolGrantBindings(descriptors, authority.bindings(), RESOURCE, null);
        assertNotNull(new TianjiFixedBpmnStartToolBindings(descriptors, List.of(aaProfile)), "the library allows AA, the assembly must reject it");
        assertThrows(IllegalStateException.class, () -> profileFactory(START, authority,
                new TianjiGovernedProcessToolProperties(true, 2, List.of(decisionProfile()), List.of(aaProfile)), grants));
    }

    @Test void bridge_uses_the_profile_union_and_original_unfiltered_resolver_after_host_and_type_telemetry_resolution() {
        var f = new Fixture(); var recorder = mock(ToolInvocationRecorder.class); var access = mock(ToolResultAccessRecorder.class);
        var decision = mock(TianjiFixedDecisionToolTransport.class); var start = mock(TianjiFixedBpmnStartToolTransport.class);
        var resolvers = provider(f.resolver); var telemetry = provider(mock(OpenTelemetry.class)); var captured = new AtomicReference<List<?>>();
        try (var construction = mockConstruction(AdkGovernedProcessToolBridge.class, (bridge, invocation) -> {
            var order = inOrder(resolvers, telemetry); order.verify(resolvers).getObject(); order.verify(telemetry).getIfAvailable(); captured.set(invocation.arguments());
        })) {
            var bridge = configuration.tianjiAdkGovernedProcessTools(f.ledger, recorder, decision, start, access, f.authority,
                    f.decisions, f.starts, f.grants, resolvers, telemetry, environment());
            assertSame(construction.constructed().getFirst(), bridge); var args = captured.get();
            assertSame(f.ledger, args.get(0)); assertSame(recorder, args.get(1)); assertSame(decision, args.get(2)); assertSame(start, args.get(3)); assertSame(access, args.get(4));
            assertEquals(Set.of(READ, START), Set.copyOf((List<?>) args.get(5))); assertSame(f.resolver, args.get(7));
            @SuppressWarnings("unchecked") var resolver = (Function<AgentLedgerService.ExecutionMaterial, List<ToolExecutionGrant.BindingVO>>) args.get(6);
            var material = material(List.of(READ, START, QUERY));
            assertEquals(f.grants.resolve(material).stream().map(TianjiToolGrantBindings.ResolvedToolResult::binding).toList(), resolver.apply(material));
            assertEquals(3, resolver.apply(material).size(), "the authority resolver must not silently filter unsupported visible tools");
        }
        verifyNoInteractions(recorder, access, decision, start); f.noExternalCalls();
    }

    @Test void absent_optional_telemetry_does_not_supply_a_default_host_resolver_or_mutate_global_telemetry() {
        var f = new Fixture(); var recorder = mock(ToolInvocationRecorder.class); var access = mock(ToolResultAccessRecorder.class);
        when(recorder.usesLedger(f.ledger)).thenReturn(true); when(access.usesLedger(f.ledger)).thenReturn(true);
        var telemetry = provider((OpenTelemetry) null);
        AdkToolTelemetryPrivacy.requireSafe();
        try (var global = mockStatic(GlobalOpenTelemetry.class)) {
            var bridge = configuration.tianjiAdkGovernedProcessTools(f.ledger, recorder, mock(TianjiFixedDecisionToolTransport.class),
                    mock(TianjiFixedBpmnStartToolTransport.class), access, f.authority, f.decisions, f.starts, f.grants, provider(f.resolver), telemetry, environment());
            assertTrue(bridge.usesActionContextResolver(f.resolver)); verify(telemetry).getIfAvailable(); global.verifyNoInteractions();
        }
        f.noExternalCalls();
    }

    @Test void actual_runtime_consumes_the_same_verified_union_graph_with_and_without_global_lazy() {
        for (boolean lazy : List.of(false, true)) {
            var f = new Fixture();
            try (var storage = mockConstruction(ToolExecutionStorageVerifier.class); var old = mockConstruction(AgentStorageVerifier.class)) {
                f.runner(lazy, true, "stable").run(context -> {
                    assertNull(context.getStartupFailure());
                    assertTrue(context.getBeanFactory().containsSingleton("tianjiAdkGovernedProcessTools"));
                    assertTrue(context.getBeanFactory().containsSingleton("tianjiAdkAgentRunExecutor"));
                    var bridge = context.getBean("tianjiAdkGovernedProcessTools", AdkGovernedProcessToolBridge.class);
                    var worker = context.getBean("tianjiAdkAgentRunExecutor", AdkAgentRunExecutor.class);
                    assertTrue(worker.usesGovernedProcessTools(bridge)); assertTrue(worker.usesAuthorization(f.runAuthorization)); assertTrue(worker.usesLedger(f.ledger));
                    assertTrue(bridge.usesActionContextResolver(f.resolver)); assertTrue(bridge.usesLedger(f.ledger));
                    assertTrue(bridge.usesRecorder(context.getBean("governedProcessToolInvocationRecorder", ToolInvocationRecorder.class)));
                    assertTrue(bridge.usesDecisionTransport(context.getBean("tianjiGovernedProcessDecisionTransport", TianjiFixedDecisionToolTransport.class)));
                    assertTrue(bridge.usesStartTransport(context.getBean("tianjiGovernedProcessStartTransport", TianjiFixedBpmnStartToolTransport.class)));
                    var access = context.getBean("governedProcessToolResultAccessRecorder", ToolResultAccessRecorder.class);
                    assertTrue(bridge.usesResultAccessRecorder(access)); assertTrue(access.usesStore(context.getBean("governedProcessToolResultAccessStore", ToolResultAccessStore.class)));
                    assertFalse(context.getEnvironment().getProperty("stellar.tianji.agent.discovery.enabled", Boolean.class, false));
                    assertEquals(3, storage.constructed().size());
                    assertEquals(2, storage.constructed().stream().filter(value -> mockingDetails(value).getInvocations().stream().anyMatch(call -> call.getMethod().getName().equals("verify"))).count());
                    assertEquals(1, storage.constructed().stream().filter(value -> mockingDetails(value).getInvocations().stream().anyMatch(call -> call.getMethod().getName().equals("verifyResultAccess"))).count());
                });
            }
            f.noExternalCalls(); verifyNoInteractions(f.source, f.resolver);
        }
    }

    @Test void failure_of_either_v13_verification_or_v14_verification_blocks_global_lazy_startup() {
        for (int index = 0; index < 3; index++) {
            var f = new Fixture(); int failing = index;
            try (var storage = mockConstruction(ToolExecutionStorageVerifier.class, (value, invocation) -> {
                if (invocation.getCount() - 1 == failing) {
                    doThrow(new IllegalStateException("private-readiness-sentinel")).when(value).verify();
                    doThrow(new IllegalStateException("private-readiness-sentinel")).when(value).verifyResultAccess();
                }
            }); var old = mockConstruction(AgentStorageVerifier.class)) {
                f.runner(true, true, "stable").run(context -> {
                    assertNotNull(context.getStartupFailure()); assertEquals(failing + 1, storage.constructed().size());
                    assertFalse(context.getStartupFailure().toString().contains("private-readiness-sentinel"));
                });
            }
            f.noExternalCalls(); verifyNoInteractions(f.source);
        }
    }

    @Test void missing_ambiguous_and_prototype_host_resolvers_cannot_be_hidden_by_global_lazy() {
        for (String mode : List.of("missing", "ambiguous", "prototype")) {
            var f = new Fixture();
            try (var storage = mockConstruction(ToolExecutionStorageVerifier.class); var old = mockConstruction(AgentStorageVerifier.class)) {
                f.runner(true, true, mode).run(context -> assertNotNull(context.getStartupFailure(), mode));
            }
            f.noExternalCalls();
        }
    }

    @Test void missing_actual_worker_or_identity_dependency_cannot_pass_through_an_unused_bridge() {
        var f = new Fixture();
        try (var storage = mockConstruction(ToolExecutionStorageVerifier.class); var old = mockConstruction(AgentStorageVerifier.class)) {
            f.runner(true, false, "stable").run(context -> assertNotNull(context.getStartupFailure()));
            f.dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiGovernedProcessToolConfiguration.class), false, "stable")
                    .withPropertyValues(values()).withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
        f.noExternalCalls();
    }

    @Test void an_unused_correct_run_authorizer_does_not_prove_the_actual_worker_authorization_boundary() {
        var f = new Fixture();
        try (var storage = mockConstruction(ToolExecutionStorageVerifier.class); var old = mockConstruction(AgentStorageVerifier.class)) {
            f.runner(true, true, "stable").withBean("foreignRunAuthorization", AgentRunAuthorization.class,
                    () -> mock(AgentRunAuthorization.class), definition -> definition.setPrimary(true))
                    .run(context -> assertNotNull(context.getStartupFailure()));
        }
        f.noExternalCalls();
    }

    @Test void eager_verifier_rejects_every_detached_store_pdp_port_facade_resolver_and_actual_worker_root() {
        List<Consumer<Chain>> breaks = List.of(
                c -> when(c.ledger.usesDataSource(c.source)).thenReturn(false),
                c -> when(c.capabilities.usesDataSource(c.source)).thenReturn(false), c -> when(c.capabilities.usesLedger(c.ledger)).thenReturn(false),
                c -> when(c.prep.usesDataSource(c.source)).thenReturn(false), c -> when(c.prep.usesLedger(c.ledger)).thenReturn(false),
                c -> when(c.prep.usesCapabilities(c.capabilities)).thenReturn(false), c -> when(c.prep.usesAuthorization(c.authorization)).thenReturn(false),
                c -> when(c.recorder.usesDataSource(c.source)).thenReturn(false), c -> when(c.recorder.usesLedger(c.ledger)).thenReturn(false),
                c -> when(c.recorder.usesPreparations(c.prep)).thenReturn(false), c -> when(c.recorder.usesCapabilities(c.capabilities)).thenReturn(false), c -> when(c.recorder.usesAuthorization(c.authorization)).thenReturn(false),
                c -> when(c.accesses.usesDataSource(c.source)).thenReturn(false), c -> when(c.accesses.usesLedger(c.ledger)).thenReturn(false),
                c -> when(c.accesses.usesCapabilities(c.capabilities)).thenReturn(false), c -> when(c.accesses.usesAuthorization(c.authorization)).thenReturn(false),
                c -> when(c.access.usesStore(c.accesses)).thenReturn(false), c -> when(c.access.usesLedger(c.ledger)).thenReturn(false),
                c -> when(c.authorization.usesLedger(c.ledger)).thenReturn(false), c -> when(c.authorization.usesBindings(c.grants)).thenReturn(false), c -> when(c.authorization.usesIssuerRegistry(c.issuers)).thenReturn(false),
                c -> when(c.runAuthorization.usesToolAuthorization(c.authorization)).thenReturn(false),
                c -> when(c.decision.usesBindings(c.decisions)).thenReturn(false), c -> when(c.decision.usesOAuth(c.oauth)).thenReturn(false), c -> when(c.decision.usesAuthentication(c.authentication)).thenReturn(false),
                c -> when(c.decision.usesConverter(c.converter)).thenReturn(false), c -> when(c.decision.usesIssuerRegistry(c.issuers)).thenReturn(false), c -> when(c.decision.usesHttpClient(c.http)).thenReturn(false),
                c -> when(c.start.usesBindings(c.starts)).thenReturn(false), c -> when(c.start.usesOAuth(c.oauth)).thenReturn(false), c -> when(c.start.usesAuthentication(c.authentication)).thenReturn(false),
                c -> when(c.start.usesConverter(c.converter)).thenReturn(false), c -> when(c.start.usesIssuerRegistry(c.issuers)).thenReturn(false), c -> when(c.start.usesHttpClient(c.http)).thenReturn(false),
                c -> when(c.http.followRedirects()).thenReturn(HttpClient.Redirect.ALWAYS),
                c -> when(c.bridge.usesLedger(c.ledger)).thenReturn(false), c -> when(c.bridge.usesRecorder(c.recorder)).thenReturn(false),
                c -> when(c.bridge.usesDecisionTransport(c.decision)).thenReturn(false), c -> when(c.bridge.usesStartTransport(c.start)).thenReturn(false), c -> when(c.bridge.usesResultAccessRecorder(c.access)).thenReturn(false),
                c -> when(c.bridge.usesActionContextResolver(c.resolver)).thenReturn(false),
                c -> when(c.worker.usesLedger(c.ledger)).thenReturn(false), c -> when(c.worker.usesAuthorization(c.runAuthorization)).thenReturn(false), c -> when(c.worker.usesGovernedProcessTools(c.bridge)).thenReturn(false));
        var healthy = new Chain(); var proof = healthy.proof(configuration, environment());
        verifyNoInteractions(healthy.bridges, healthy.executors, healthy.resolvers); assertDoesNotThrow(proof::afterSingletonsInstantiated);
        verify(healthy.bridges).getObject(); verify(healthy.executors).getObject(); verify(healthy.resolvers).getObject();
        var checks = new ArrayList<Executable>();
        for (var mutation : breaks) checks.add(() -> { var broken = new Chain(); mutation.accept(broken); assertThrows(IllegalStateException.class, broken.proof(configuration, environment())::afterSingletonsInstantiated); broken.noExternalCalls(); });
        assertAll(checks);
    }

    @Test void the_resolver_checked_by_readiness_must_be_the_same_object_already_consumed_by_the_bridge() {
        var chain = new Chain(); var foreign = mock(StartActionContextResolver.class);
        when(chain.resolvers.getObject()).thenReturn(foreign);
        assertThrows(IllegalStateException.class, chain.proof(configuration, environment())::afterSingletonsInstantiated);
        verifyNoInteractions(foreign, chain.resolver); chain.noExternalCalls();
    }

    @Test void context_shutdown_closes_the_owned_shared_http_and_both_private_transport_pools_only() throws Exception {
        var f = new Fixture(); var http = new AtomicReference<HttpClient>();
        var decision = new AtomicReference<TianjiFixedDecisionToolTransport>(); var start = new AtomicReference<TianjiFixedBpmnStartToolTransport>();
        try (var storage = mockConstruction(ToolExecutionStorageVerifier.class); var old = mockConstruction(AgentStorageVerifier.class)) {
            f.runner(false, true, "stable").run(context -> {
                assertNull(context.getStartupFailure()); http.set(context.getBean("tianjiGovernedProcessHttpClient", HttpClient.class));
                decision.set(context.getBean("tianjiGovernedProcessDecisionTransport", TianjiFixedDecisionToolTransport.class));
                start.set(context.getBean("tianjiGovernedProcessStartTransport", TianjiFixedBpmnStartToolTransport.class));
                assertEquals(HttpClient.Redirect.NEVER, http.get().followRedirects()); assertEquals(Duration.ofSeconds(3), http.get().connectTimeout().orElseThrow());
                assertTrue(decision.get().usesHttpClient(http.get())); assertTrue(start.get().usesHttpClient(http.get()));
            });
        }
        assertTrue(http.get().awaitTermination(Duration.ofSeconds(2)));
        var material = material(List.of(READ, START)); var lease = new AgentRunLease(material.run().runId(), "worker", 1, NOW.plusSeconds(30));
        var read = new ReadOnlyDecisionToolTransport.Request(material.authorization(), lease, READ, Map.of("amount", 7L), UUID.randomUUID(), NOW.plusSeconds(20));
        assertEquals(ReadOnlyDecisionToolTransport.FailureKind.CANCELLED, assertThrows(ReadOnlyDecisionToolTransport.Failure.class,
                () -> decision.get().withVerifiedCall(read, (executor, operation) -> fail("closed decision transport dispatched"))).kind());
        var action = new ToolActionRequest(material.authorization().actor(), START, "invoice-1", "version-1", Map.of("amount", 7L));
        var write = new FixedBpmnStartToolTransport.Request(material.authorization(), lease, START, action, UUID.randomUUID(), NOW.plusSeconds(20));
        assertEquals(FixedBpmnStartToolTransport.FailureKind.CANCELLED, assertThrows(FixedBpmnStartToolTransport.Failure.class,
                () -> start.get().withVerifiedCall(write, (executor, operation) -> fail("closed START transport dispatched"))).kind());
        f.noExternalCalls(); verifyNoInteractions(f.source, f.issuers);
    }

    @Test void eleven_distinct_named_factories_keep_explicit_ownership_qualifiers_and_lazy_boundaries() {
        var methods = Arrays.stream(TianjiGovernedProcessToolConfiguration.class.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Bean.class)).toList();
        assertEquals(11, methods.size()); assertEquals(Set.copyOf(names()), methods.stream().map(method -> {
            var bean = method.getAnnotation(Bean.class); return bean.value().length == 0 ? method.getName() : bean.value()[0];
        }).collect(java.util.stream.Collectors.toSet()));
        var bridge = methods.stream().filter(method -> method.getName().equals("tianjiAdkGovernedProcessTools")).findFirst().orElseThrow(); assertTrue(bridge.getAnnotation(Lazy.class).value());
        var telemetry = Arrays.stream(bridge.getParameters()).filter(value -> value.getParameterizedType().getTypeName().contains("OpenTelemetry")).findFirst().orElseThrow(); assertNull(telemetry.getAnnotation(Qualifier.class));
        var host = Arrays.stream(bridge.getParameters()).filter(value -> value.getParameterizedType().getTypeName().contains("StartActionContextResolver")).findFirst().orElseThrow(); assertEquals(HOST, host.getAnnotation(Qualifier.class).value());
        var verifier = methods.stream().filter(method -> method.getName().equals("tianjiGovernedProcessToolRuntimeChainVerifier")).findFirst().orElseThrow(); assertFalse(verifier.getAnnotation(Lazy.class).value());
        for (var method : methods) if (method.getReturnType() == HttpClient.class) assertEquals("shutdownNow", method.getAnnotation(Bean.class).destroyMethod());
        else if (method.getReturnType() == TianjiFixedDecisionToolTransport.class || method.getReturnType() == TianjiFixedBpmnStartToolTransport.class) assertEquals("close", method.getAnnotation(Bean.class).destroyMethod());
        assertFalse(methods.stream().anyMatch(method -> method.getReturnType() == StartActionContextResolver.class));
    }

    private Object profileFactory(ToolDescriptor descriptor, TianjiToolAuthorityProperties authority, TianjiGovernedProcessToolProperties props, TianjiToolGrantBindings grants) {
        return descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY ? configuration.tianjiGovernedProcessDecisionBindings(authority, props, grants, environment())
                : configuration.tianjiGovernedProcessStartBindings(authority, props, grants, environment());
    }

    private static class Fixture {
        final DataSource source = mock(DataSource.class); final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final ToolCapabilityStore capabilities = mock(ToolCapabilityStore.class); final TianjiToolGrantAuthorizer authorization = mock(TianjiToolGrantAuthorizer.class);
        final TianjiExecutionGrantAuthorizer runAuthorization = mock(TianjiExecutionGrantAuthorizer.class);
        final IdpServiceOAuth2Client oauth = mock(IdpServiceOAuth2Client.class); final TrustedIssuerRegistry issuers = mock(TrustedIssuerRegistry.class);
        final TenantIssuerAuthenticationManagerComponent authentication = mock(TenantIssuerAuthenticationManagerComponent.class); final VerifiedPrincipalConverter converter = mock(VerifiedPrincipalConverter.class);
        final StartActionContextResolver resolver = mock(StartActionContextResolver.class);
        final TianjiToolAuthorityProperties authority = new TianjiToolAuthorityProperties(true, Duration.ofSeconds(1), 2, DESCRIPTORS, List.of(mapping(READ), mapping(START), mapping(QUERY)));
        final TianjiToolGrantBindings grants = new TianjiToolGrantBindings(DESCRIPTORS, authority.bindings(), RESOURCE, null);
        final TianjiFixedDecisionToolBindings decisions = new TianjiFixedDecisionToolBindings(DESCRIPTORS, List.of(decisionProfile()));
        final TianjiFixedBpmnStartToolBindings starts = new TianjiFixedBpmnStartToolBindings(DESCRIPTORS, List.of(startProfile()));
        Fixture() {
            when(ledger.usesDataSource(source)).thenReturn(true); when(capabilities.usesDataSource(source)).thenReturn(true); when(capabilities.usesLedger(ledger)).thenReturn(true);
            when(authorization.usesLedger(ledger)).thenReturn(true); when(authorization.usesBindings(grants)).thenReturn(true); when(authorization.usesIssuerRegistry(issuers)).thenReturn(true);
            when(runAuthorization.usesToolAuthorization(authorization)).thenReturn(true);
        }
        ApplicationContextRunner dependencies(ApplicationContextRunner runner, boolean identity, String resolverMode) {
            var configured = runner.withBean(DataSource.class, () -> source).withBean(AgentLedgerService.class, () -> ledger)
                    .withBean("toolCapabilityStore", ToolCapabilityStore.class, () -> capabilities).withBean("tianjiToolGrantAuthorizer", TianjiToolGrantAuthorizer.class, () -> authorization)
                    .withBean("tianjiExecutionGrantAuthorizer", TianjiExecutionGrantAuthorizer.class, () -> runAuthorization).withBean("tianjiToolGrantBindings", TianjiToolGrantBindings.class, () -> grants)
                    .withInitializer(context -> context.getBeanFactory().registerSingleton("fixtureToolAuthorityProperties", authority))
                    .withBean("idpServiceOAuth2Client", IdpServiceOAuth2Client.class, () -> oauth).withBean("tenantIssuerAuthenticationManagerComponent", TenantIssuerAuthenticationManagerComponent.class, () -> authentication)
                    .withBean("trustedIssuerRegistry", TrustedIssuerRegistry.class, () -> issuers);
            if (identity) configured = configured.withBean("verifiedPrincipalConverter", VerifiedPrincipalConverter.class, () -> converter);
            if (resolverMode.equals("stable")) configured = configured.withBean(HOST, StartActionContextResolver.class, () -> resolver);
            else if (resolverMode.equals("prototype")) configured = configured.withBean(HOST, StartActionContextResolver.class, () -> mock(StartActionContextResolver.class), definition -> definition.setScope("prototype"));
            else if (resolverMode.equals("ambiguous")) for (String name : List.of("firstHostResolver", "secondHostResolver")) configured = configured.withBean(name, StartActionContextResolver.class,
                    () -> mock(StartActionContextResolver.class), definition -> ((AbstractBeanDefinition) definition).addQualifier(new AutowireCandidateQualifier(Qualifier.class, HOST)));
            return configured;
        }
        ApplicationContextRunner runner(boolean lazy, boolean runtime, String resolverMode) {
            var runner = dependencies(new ApplicationContextRunner().withUserConfiguration(TianjiGovernedProcessToolConfiguration.class), true, resolverMode).withPropertyValues(values());
            if (runtime) runner = runner.withConfiguration(AutoConfigurations.of(TianjiAgentRuntimeAutoConfiguration.class)).withUserConfiguration(TianjiAgentExecutionResourcesConfiguration.class)
                    .withBean(AgentFlowFactory.class, () -> mock(AgentFlowFactory.class)).withBean(AgentFlowConfigValidator.class, () -> mock(AgentFlowConfigValidator.class))
                    .withBean("agentFlowChatModels", Map.class, () -> Map.of("fixtureModel", mock(ChatModel.class))).withBean(AgentModelBudgetEstimator.class, () -> mock(AgentModelBudgetEstimator.class));
            return lazy ? runner.withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor())) : runner;
        }
        void noExternalCalls() { verify(oauth, never()).authorize(any()); verify(oauth, never()).authorizeBounded(any(), anyString(), any()); verifyNoInteractions(authentication, converter); }
    }

    private static final class Chain extends Fixture {
        final ToolInvocationStore prep = mock(ToolInvocationStore.class); final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
        final ToolResultAccessStore accesses = mock(ToolResultAccessStore.class); final ToolResultAccessRecorder access = mock(ToolResultAccessRecorder.class);
        final HttpClient http = mock(HttpClient.class); final TianjiFixedDecisionToolTransport decision = mock(TianjiFixedDecisionToolTransport.class); final TianjiFixedBpmnStartToolTransport start = mock(TianjiFixedBpmnStartToolTransport.class);
        final AdkGovernedProcessToolBridge bridge = mock(AdkGovernedProcessToolBridge.class); final AdkAgentRunExecutor worker = mock(AdkAgentRunExecutor.class);
        final ObjectProvider<AdkGovernedProcessToolBridge> bridges = provider(bridge); final ObjectProvider<AdkAgentRunExecutor> executors = provider(worker); final ObjectProvider<StartActionContextResolver> resolvers = provider(resolver);
        Chain() {
            when(prep.usesDataSource(source)).thenReturn(true); when(prep.usesLedger(ledger)).thenReturn(true); when(prep.usesCapabilities(capabilities)).thenReturn(true); when(prep.usesAuthorization(authorization)).thenReturn(true);
            when(recorder.usesDataSource(source)).thenReturn(true); when(recorder.usesLedger(ledger)).thenReturn(true); when(recorder.usesPreparations(prep)).thenReturn(true); when(recorder.usesCapabilities(capabilities)).thenReturn(true); when(recorder.usesAuthorization(authorization)).thenReturn(true);
            when(accesses.usesDataSource(source)).thenReturn(true); when(accesses.usesLedger(ledger)).thenReturn(true); when(accesses.usesCapabilities(capabilities)).thenReturn(true); when(accesses.usesAuthorization(authorization)).thenReturn(true);
            when(access.usesStore(accesses)).thenReturn(true); when(access.usesLedger(ledger)).thenReturn(true);
            when(decision.usesBindings(decisions)).thenReturn(true); when(decision.usesOAuth(oauth)).thenReturn(true); when(decision.usesAuthentication(authentication)).thenReturn(true); when(decision.usesConverter(converter)).thenReturn(true); when(decision.usesIssuerRegistry(issuers)).thenReturn(true); when(decision.usesHttpClient(http)).thenReturn(true);
            when(start.usesBindings(starts)).thenReturn(true); when(start.usesOAuth(oauth)).thenReturn(true); when(start.usesAuthentication(authentication)).thenReturn(true); when(start.usesConverter(converter)).thenReturn(true); when(start.usesIssuerRegistry(issuers)).thenReturn(true); when(start.usesHttpClient(http)).thenReturn(true);
            when(http.followRedirects()).thenReturn(HttpClient.Redirect.NEVER);
            when(bridge.usesLedger(ledger)).thenReturn(true); when(bridge.usesRecorder(recorder)).thenReturn(true); when(bridge.usesDecisionTransport(decision)).thenReturn(true); when(bridge.usesStartTransport(start)).thenReturn(true); when(bridge.usesResultAccessRecorder(access)).thenReturn(true); when(bridge.usesActionContextResolver(resolver)).thenReturn(true);
            when(worker.usesLedger(ledger)).thenReturn(true); when(worker.usesAuthorization(runAuthorization)).thenReturn(true); when(worker.usesGovernedProcessTools(bridge)).thenReturn(true);
        }
        SmartInitializingSingleton proof(TianjiGovernedProcessToolConfiguration cfg, MockEnvironment env) {
            return cfg.tianjiGovernedProcessToolRuntimeChainVerifier(env, source, ledger, capabilities, authorization, grants, prep, recorder, accesses, access,
                    decisions, starts, http, decision, start, oauth, authentication, converter, issuers, bridges, executors, resolvers, runAuthorization);
        }
        List<Executable> operations(TianjiGovernedProcessToolConfiguration cfg, MockEnvironment env) {
            return List.of(() -> cfg.governedProcessToolInvocationStore(source, ledger, capabilities, authorization, env),
                    () -> cfg.governedProcessToolInvocationRecorder(source, ledger, prep, capabilities, authorization, env),
                    () -> cfg.governedProcessToolResultAccessStore(source, ledger, capabilities, authorization, env),
                    () -> cfg.governedProcessToolResultAccessRecorder(accesses, ledger, env),
                    () -> cfg.tianjiGovernedProcessDecisionBindings(authority, properties(), grants, env), () -> cfg.tianjiGovernedProcessStartBindings(authority, properties(), grants, env),
                    () -> cfg.tianjiGovernedProcessHttpClient(env).shutdownNow(),
                    () -> cfg.tianjiGovernedProcessDecisionTransport(decisions, oauth, authentication, converter, issuers, http, properties(), env).close(),
                    () -> cfg.tianjiGovernedProcessStartTransport(starts, oauth, authentication, converter, issuers, http, properties(), env).close(),
                    () -> cfg.tianjiAdkGovernedProcessTools(ledger, recorder, decision, start, access, authority, decisions, starts, grants, resolvers, provider((OpenTelemetry) null), env),
                    () -> proof(cfg, env).afterSingletonsInstantiated());
        }
    }

    private static ToolDescriptor tool(String key, long version, ToolPolicy.Risk risk, String resource, String hash) {
        String field = resource.equals(ProcessClient.START_RESOURCE_KEY) ? "instanceId" : "value";
        return new ToolDescriptor(key, version, key.replace('.', '_') + '_' + version, "Fixed tool metadata", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of(field, ToolSchema.ValueType.STRING), Set.of(field)), new ToolPolicy(risk, key + ".action", resource, hash, null));
    }
    private static TianjiToolGrantBindings.BindingVO mapping(ToolDescriptor tool) { return new TianjiToolGrantBindings.BindingVO(20, ISSUER, TrustedAgentActor.SubjectType.USER, "assistant", 1, SHA, tool.reference(), "TIANJI_TOOL_FIXED_PROCESS", tool.policy().executionResourceKey()); }
    private static TianjiFixedDecisionToolBindings.BindingVO decisionProfile() { return new TianjiFixedDecisionToolBindings.BindingVO(20, ISSUER, TrustedAgentActor.SubjectType.USER, "assistant", 1, SHA, READ.reference(), DMN); }
    private static TianjiFixedBpmnStartToolBindings.BindingVO startProfile() { return new TianjiFixedBpmnStartToolBindings.BindingVO(20, ISSUER, TrustedAgentActor.SubjectType.USER, "assistant", 1, SHA, START.reference(), BPMN); }
    private static TianjiGovernedProcessToolProperties properties() { return new TianjiGovernedProcessToolProperties(true, 2, List.of(decisionProfile()), List.of(startProfile())); }
    private static List<String> prerequisites() { return List.of("api", "storage", "execution-grants", "execution-authorization", "runtime", "tools.authority"); }
    private static MockEnvironment environment() { var env = new MockEnvironment(); prerequisites().forEach(feature -> env.setProperty("stellar.tianji.agent." + feature + ".enabled", "true")); env.setProperty(PREFIX + "enabled", "true"); return env; }
    @SuppressWarnings("unchecked") private static <T> ObjectProvider<T> provider(T value) { var provider = (ObjectProvider<T>) mock(ObjectProvider.class); when(provider.getObject()).thenReturn(value); when(provider.getIfAvailable()).thenReturn(value); return provider; }
    private static List<String> names() { return List.of("governedProcessToolInvocationStore", "governedProcessToolInvocationRecorder", "governedProcessToolResultAccessStore", "governedProcessToolResultAccessRecorder", "tianjiGovernedProcessDecisionBindings", "tianjiGovernedProcessStartBindings", "tianjiGovernedProcessHttpClient", "tianjiGovernedProcessDecisionTransport", "tianjiGovernedProcessStartTransport", "tianjiAdkGovernedProcessTools", "tianjiGovernedProcessToolRuntimeChainVerifier"); }
    private static String[] values() {
        var values = new ArrayList<String>(); prerequisites().forEach(feature -> values.add("stellar.tianji.agent." + feature + ".enabled=true"));
        values.add(PREFIX + "enabled=true"); values.add(PREFIX + "concurrency=2");
        for (boolean start : List.of(false, true)) {
            String prefix = PREFIX + (start ? "start-bindings[0]." : "decision-bindings[0]."); var tool = start ? START : READ;
            values.addAll(List.of(prefix + "tenant-id=20", prefix + "owner-issuer-fingerprint=" + ISSUER, prefix + "principal-type=USER", prefix + "definition-key=assistant", prefix + "definition-version=1", prefix + "definition-content-sha256=" + SHA,
                    prefix + "tool.key=" + tool.key(), prefix + "tool.version=1", prefix + "tool.content-sha256=" + tool.reference().contentSha256(),
                    prefix + "backend.tenant-id=20", prefix + "backend.process-issuer=" + DMN.processIssuer(), prefix + "backend.process-base-uri=" + DMN.processBaseUri(), prefix + "backend.process-resource-uri=" + DMN.processResourceUri(),
                    prefix + "backend.registration-id=" + DMN.registrationId(), prefix + "backend.expected-client-id=" + DMN.expectedClientId(), prefix + "backend.expected-app-id=" + DMN.expectedAppId(),
                    prefix + "backend.expected-source-biz=" + DMN.expectedSourceBiz(), prefix + "backend.expected-source-app=" + DMN.expectedSourceApp(), prefix + "backend.expected-environment=" + DMN.expectedEnvironment(),
                    prefix + "backend.release-version=" + DMN.releaseVersion(), prefix + "backend.manifest-sha256=" + DMN.manifestSha256(), prefix + "backend.input-schema.amount=INTEGER", prefix + "backend.timeout=2s"));
            values.add(prefix + "backend." + (start ? "process-definition-key=" + BPMN.processDefinitionKey() : "decision-key=" + DMN.decisionKey()));
            values.add(prefix + "backend." + (start ? "process-model-sha256=" + BPMN.processModelSha256() : "decision-model-sha256=" + DMN.decisionModelSha256()));
        }
        return values.toArray(String[]::new);
    }
    private static AgentLedgerService.ExecutionMaterial material(List<ToolDescriptor> tools) {
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", OWNER.toASCIIString());
        var selected = tools.stream().map(ToolDescriptor::versionKey).collect(java.util.stream.Collectors.toSet());
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 3, 3, 1000, 100, BigDecimal.ONE, "USD", 1, selected, "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), tools.stream().map(ToolDescriptor::reference).toList(), "route", "policy"), policy, Map.of());
        var snapshot = new AgentExecutionSnapshot(definition, SHA, policy, new AgentInputDTO("fixed task", null, null), "invoice-1");
        var run = new AgentRunDTO("10000000-0000-0000-0000-000000000001", "assistant", 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 0, NOW.plusSeconds(30), NOW, null);
        var authorization = new AgentRunAuthorization.Request(actor, run.runId(), "assistant", 1, SHA, "c".repeat(64), "d".repeat(64), "policy", false, run.deadlineAt());
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }
}
