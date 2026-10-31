package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiToolGrantBindingsTest {
    private static final URI ISSUER = URI.create("https://idp.example/21"), RESOURCE = URI.create("https://agent.example/api");
    private static final String HASH = "a".repeat(64), RUN_ID = "10000000-0000-0000-0000-000000000001", DEFINITION = "审批分析";
    private static final Instant CREATED = Instant.parse("2026-10-31T00:00:00Z"), DEADLINE = CREATED.plusSeconds(300);
    private static final TrustedAgentActor USER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString());
    private static final TrustedAgentActor SERVICE = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", ISSUER.toASCIIString());
    private static final ToolDescriptor READ = descriptor("process.read", ToolPolicy.Risk.READ_ONLY), WRITE = descriptor("process.start", ToolPolicy.Risk.IDEMPOTENT_WRITE);

    @Test void fixed_visible_tools_resolve_once_in_registry_order_to_independent_immutable_grants() {
        var configured = List.of(mapping(USER, WRITE), mapping(USER, READ));
        var bindings = new TianjiToolGrantBindings(List.of(WRITE, READ), configured, RESOURCE, null);
        var material = material(USER); var results = bindings.resolve(material);
        assertEquals(2, results.size()); assertEquals(READ, results.getFirst().descriptor()); assertEquals(WRITE, results.getLast().descriptor());
        for (var result : results) {
            assertEquals(material.authorization(), result.binding().run()); assertEquals(result.descriptor().reference(), result.binding().tool());
            assertEquals(result.descriptor().policy().bindingSha256(), result.binding().backendBindingSha256());
            assertEquals(RESOURCE.toASCIIString(), result.binding().resource().toASCIIString()); assertNull(result.binding().serviceSubject());
            assertTrue(result.binding().purpose().startsWith("TIANJI_TOOL_")); assertEquals("tianji.api.agent.start", result.binding().authorizationResourceKey());
            assertEquals("TianjiToolGrantBindings.ResolvedToolResult[redacted]", result.toString());
        }
        assertThrows(UnsupportedOperationException.class, () -> results.add(results.getFirst()));
    }

    @Test void input_and_output_collections_are_bounded_immutable_and_preserve_exact_resource_identity() {
        var registrations = new ArrayList<>(List.of(READ)); var configured = new ArrayList<>(List.of(mapping(USER, READ)));
        var bindings = new TianjiToolGrantBindings(registrations, configured, RESOURCE, null);
        registrations.clear(); configured.clear();
        assertEquals(List.of(mapping(USER, READ)), bindings.bindings());
        assertThrows(UnsupportedOperationException.class, () -> bindings.bindings().clear());
        assertFalse(bindings.hasServiceBindings()); assertTrue(bindings.usesServices(null));
        assertTrue(bindings.usesIssuerRegistry(mock(TrustedIssuerRegistry.class)));
        assertTrue(bindings.usesResource(RESOURCE)); assertFalse(bindings.usesResource(URI.create("https://AGENT.example/api"))); assertFalse(bindings.usesResource(null));
        assertEquals("TianjiToolGrantBindings.BindingVO[redacted]", bindings.bindings().getFirst().toString());
        var many = new ArrayList<TianjiToolGrantBindings.BindingVO>();
        for (int index = 1; index <= 257; index++) many.add(new TianjiToolGrantBindings.BindingVO(index, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings(List.of(READ), many, RESOURCE, null));
        var tooManyRegistrations = new ArrayList<ToolDescriptor>();
        for (int index = 0; index <= 256; index++) tooManyRegistrations.add(descriptor("extra_" + index, ToolPolicy.Risk.READ_ONLY));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings(tooManyRegistrations, List.of(mapping(USER, tooManyRegistrations.getFirst())), RESOURCE, null));
    }

    @Test void invalid_empty_duplicate_and_unregistered_configuration_is_rejected_before_resolution() {
        var fixed = mapping(USER, READ);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiToolGrantBindings(List.of(), List.of(fixed), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ), List.of(), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ, READ), List.of(fixed), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ), List.of(fixed, fixed), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ), List.of(mapping(USER, WRITE)), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ), List.of(new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH,
                        new DefinitionResourceRef(READ.key(), READ.version(), "b".repeat(64)), fixed.purpose(), fixed.authorizationResourceKey())), RESOURCE, null),
                () -> new TianjiToolGrantBindings(List.of(READ), List.of(mapping(SERVICE, READ)), RESOURCE, null))) {
            assertThrows(IllegalArgumentException.class, operation);
        }
        var drifted = new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "b".repeat(64), READ.reference(), "TIANJI_TOOL_OTHER", fixed.authorizationResourceKey());
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings(List.of(READ), List.of(fixed, drifted), RESOURCE, null));
        for (var uri : List.of("http://agent.example/api", "https://agent.example/api?query=1", "https://agent.example/api#fragment", "https://name:password@agent.example/api", "https://agent.example/a/../api")) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings(List.of(READ), List.of(fixed), URI.create(uri), null));
        }
    }

    @Test void binding_values_require_exact_nonlegacy_namespace_definition_tool_and_purpose_formats() {
        for (Executable operation : List.<Executable>of(
                () -> new TianjiToolGrantBindings.BindingVO(0, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, "LEGACY", USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), null, DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), " leading", 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "\uD800", 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 0, HASH, READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "bad", READ.reference(), "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, null, "TIANJI_TOOL_READ", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), AgentRunAuthorization.PURPOSE, "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_", "tianji.api.agent.start"),
                () -> new TianjiToolGrantBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, READ.reference(), "TIANJI_TOOL_READ", "invalid/path"))) {
            assertThrows(IllegalArgumentException.class, operation);
        }
    }

    @Test void active_tools_need_exact_mapping_but_hidden_or_zero_budget_versions_do_not() {
        var bindings = new TianjiToolGrantBindings(List.of(READ), List.of(mapping(USER, READ)), RESOURCE, null);
        assertThrows(IllegalStateException.class, () -> bindings.resolve(material(USER)));
        var missing = new DefinitionResourceRef("unregistered", 1, "c".repeat(64));
        var defaults = policy(Set.of(READ.versionKey(), "unregistered@1"), 4);
        var narrowed = material(USER, List.of(READ.reference(), missing), defaults, policy(Set.of(READ.versionKey()), 4), null);
        var selected = bindings.resolve(narrowed); assertEquals(1, selected.size()); assertEquals(READ, selected.getFirst().descriptor());
        var zero = material(USER, List.of(READ.reference(), missing), defaults, policy(defaults.allowedToolVersions(), 0), null);
        assertTrue(bindings.resolve(zero).isEmpty());
        assertTrue(bindings.resolve(material(USER, List.of(), policy(Set.of(), 4), policy(Set.of(), 4), null)).isEmpty());
        assertThrows(IllegalStateException.class, () -> bindings.resolve(material(USER, List.of(READ.reference(), missing), defaults, defaults, null)));
        var otherActor = TrustedAgentActor.verified(22, USER.subjectType(), USER.subjectId(), ISSUER.toASCIIString());
        assertThrows(IllegalStateException.class, () -> bindings.resolve(material(otherActor, List.of(READ.reference()), policy(Set.of(READ.versionKey()), 4), policy(Set.of(READ.versionKey()), 4), null)));
    }

    @Test void mismatched_run_authorization_and_snapshot_identity_never_resolve_a_mapping() {
        var material = material(USER); var bindings = new TianjiToolGrantBindings(List.of(READ, WRITE), List.of(mapping(USER, READ), mapping(USER, WRITE)), RESOURCE, null);
        var run = material.run(); var snapshot = material.snapshot(); var authorization = material.authorization();
        var changedRun = new AgentRunDTO("10000000-0000-0000-0000-000000000002", run.definitionKey(), run.definitionVersion(), run.sessionId(), run.status(), null, false, 0, 0, 0, DEADLINE, CREATED, null);
        var changedSession = new AgentRunDTO(run.runId(), run.definitionKey(), run.definitionVersion(), "session-1", run.status(), null, false, 0, 0, 0, DEADLINE, CREATED, null);
        var changedDeadline = new AgentRunDTO(run.runId(), run.definitionKey(), run.definitionVersion(), run.sessionId(), run.status(), null, false, 0, 0, 0, DEADLINE.minusSeconds(1), CREATED, null);
        var definition = snapshot.definition();
        var changedDefinition = new AgentDefinitionSpecDTO("other", definition.version(), definition.executionMode(), definition.codeRef(), definition.inputSchemaVersion(), definition.outputSchemaVersion(), definition.dependencies(), definition.policy(), definition.configuration());
        var changedPolicy = new AgentDefinitionSpecDTO(definition.definitionKey(), definition.version(), definition.executionMode(), definition.codeRef(), definition.inputSchemaVersion(), definition.outputSchemaVersion(),
                new DefinitionDependenciesDTO(List.of(), List.of(), definition.dependencies().tools(), "model-route", "other-policy"), definition.policy(), definition.configuration());
        for (var invalid : List.of(new AgentLedgerService.ExecutionMaterial(changedRun, snapshot, authorization), new AgentLedgerService.ExecutionMaterial(changedSession, snapshot, authorization),
                new AgentLedgerService.ExecutionMaterial(changedDeadline, snapshot, authorization), new AgentLedgerService.ExecutionMaterial(run, new AgentExecutionSnapshot(changedDefinition, HASH, snapshot.effectivePolicy(), snapshot.input(), null), authorization),
                new AgentLedgerService.ExecutionMaterial(run, new AgentExecutionSnapshot(definition, "b".repeat(64), snapshot.effectivePolicy(), snapshot.input(), null), authorization),
                new AgentLedgerService.ExecutionMaterial(run, new AgentExecutionSnapshot(changedPolicy, HASH, snapshot.effectivePolicy(), snapshot.input(), null), authorization))) {
            assertThrows(IllegalStateException.class, () -> bindings.resolve(invalid));
        }
        var legacy = new TrustedAgentActor(21, USER.subjectType(), USER.subjectId());
        assertThrows(IllegalStateException.class, () -> bindings.resolve(material(legacy)));
    }

    @Test void service_sources_are_obtained_from_the_existing_binding_without_fabricating_requester_identity() {
        var services = mock(TianjiServiceExecutionBindings.class); var issuers = mock(TrustedIssuerRegistry.class); var otherIssuers = mock(TrustedIssuerRegistry.class);
        var material = material(SERVICE, List.of(READ.reference()), policy(Set.of(READ.versionKey()), 4), policy(Set.of(READ.versionKey()), 4),
                new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", HASH, DEADLINE));
        var subject = new AgentServiceExecutionGrant.Subject("worker", "process-app", "platform", "process", "test", URI.create("https://process.example/api"));
        when(services.require(material.authorization(), material.snapshot().processLink())).thenReturn(subject);
        when(services.usesIssuerRegistry(issuers)).thenReturn(true);
        var bindings = new TianjiToolGrantBindings(List.of(READ), List.of(mapping(SERVICE, READ)), RESOURCE, services);
        var resolved = bindings.resolve(material); assertEquals(1, resolved.size()); assertEquals(subject, resolved.getFirst().binding().serviceSubject());
        assertEquals(SERVICE, resolved.getFirst().binding().run().actor()); assertTrue(bindings.hasServiceBindings());
        assertTrue(bindings.usesServices(services)); assertFalse(bindings.usesServices(mock(TianjiServiceExecutionBindings.class)));
        assertTrue(bindings.usesIssuerRegistry(issuers)); assertFalse(bindings.usesIssuerRegistry(otherIssuers));
        verify(services).require(material.authorization(), material.snapshot().processLink());
        when(services.require(material.authorization(), material.snapshot().processLink())).thenThrow(new IllegalStateException("private source context"));
        var failure = assertThrows(IllegalStateException.class, () -> bindings.resolve(material));
        assertEquals("TIANJI_TOOL_GRANT_BINDING_UNAVAILABLE", failure.getMessage()); assertNull(failure.getCause());
    }

    @Test void resolved_value_cannot_pair_another_descriptor_with_a_pinned_grant() {
        var material = material(USER); var binding = new ToolExecutionGrant.BindingVO(material.authorization(), WRITE.reference(), WRITE.policy().bindingSha256(), "TIANJI_TOOL_PROCESS_START", RESOURCE, "tianji.api.agent.start", null);
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings.ResolvedToolResult(READ, binding));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings.ResolvedToolResult(null, binding));
        assertThrows(IllegalArgumentException.class, () -> new TianjiToolGrantBindings.ResolvedToolResult(WRITE, null));
    }

    private static TianjiToolGrantBindings.BindingVO mapping(TrustedAgentActor actor, ToolDescriptor descriptor) {
        return new TianjiToolGrantBindings.BindingVO(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), DEFINITION, 1, HASH, descriptor.reference(),
                descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY ? "TIANJI_TOOL_PROCESS_READ" : "TIANJI_TOOL_PROCESS_START", "tianji.api.agent.start");
    }
    private static AgentLedgerService.ExecutionMaterial material(TrustedAgentActor actor) {
        var policy = policy(Set.of(READ.versionKey(), WRITE.versionKey()), 4);
        return material(actor, List.of(READ.reference(), WRITE.reference()), policy, policy, null);
    }
    private static AgentLedgerService.ExecutionMaterial material(TrustedAgentActor actor, List<DefinitionResourceRef> tools, ExecutionPolicyDTO declared, ExecutionPolicyDTO effective, AgentProcessLink link) {
        var definition = new AgentDefinitionSpecDTO(DEFINITION, 1, "CODE_FIRST", "trusted-test", "text-v1", "text-v1",
                new DefinitionDependenciesDTO(List.of(), List.of(), tools, "model-route", "policy-v1"), declared, Map.of());
        var input = new AgentInputDTO("controlled request", List.of(), Map.of()); var snapshot = new AgentExecutionSnapshot(definition, HASH, effective, input, null, link);
        var run = new AgentRunDTO(RUN_ID, DEFINITION, 1, null, AgentRunStatusEnum.QUEUED, null, false, 0, 0, 0, DEADLINE, CREATED, null);
        var authorization = new AgentRunAuthorization.Request(actor, RUN_ID, DEFINITION, 1, HASH, "b".repeat(64), "c".repeat(64), "policy-v1", false, DEADLINE);
        return new AgentLedgerService.ExecutionMaterial(run, snapshot, authorization);
    }
    private static ExecutionPolicyDTO policy(Set<String> allowed, int toolCalls) { return new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(10), 5, toolCalls, 10_000, 10_000, null, null, 4, allowed, "retry-v1", null); }
    private static ToolDescriptor descriptor(String key, ToolPolicy.Risk risk) {
        return new ToolDescriptor(key, 1, key.replace('.', '_'), "Fixed operation", new ToolSchema(Map.of(), Set.of()), new ToolSchema(Map.of(), Set.of()),
                new ToolPolicy(risk, "business.operation", "tianji.api.process.operation", "d".repeat(64), null));
    }
}
