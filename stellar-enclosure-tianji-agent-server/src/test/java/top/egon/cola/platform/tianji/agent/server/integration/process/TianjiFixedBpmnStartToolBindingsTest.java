package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TianjiFixedBpmnStartToolBindingsTest {
    private static final URI ISSUER = URI.create("https://process-idp.example/21"), BASE = URI.create("https://process.example/"),
            RESOURCE = URI.create("https://process.example/api");
    private static final String HASH = "c".repeat(64), MANIFEST = "a".repeat(64), MODEL = "b".repeat(64), DEFINITION = "审批助手",
            RUN = "10000000-0000-0000-0000-000000000001";
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final TrustedAgentActor USER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://agent-idp.example/21");
    private static final TrustedAgentActor SERVICE = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://agent-idp.example/21");
    private static final Map<String, ProcessValueTypeEnum> INPUTS = Map.of("amount", ProcessValueTypeEnum.INTEGER, "region", ProcessValueTypeEnum.STRING, "eligible", ProcessValueTypeEnum.BOOLEAN);
    private static final ToolSchema TOOL_INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "region", ToolSchema.ValueType.STRING, "eligible", ToolSchema.ValueType.BOOLEAN), INPUTS.keySet());

    @Test void the_backend_digest_precedes_tool_and_definition_fingerprints_without_a_cycle() {
        var backend = backend();
        assertEquals("b995e5b843f81e88db2576c11957bd038658223911d1742b2aba6758a3c615ec", backend.bindingSha256());
        var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(binding));
        var resolved = fixed.resolve(request(USER, descriptor)); assertNotNull(resolved);
        assertEquals(descriptor, resolved.descriptor()); assertEquals(binding, resolved.binding());
        assertEquals(backend.bindingSha256(), descriptor.policy().bindingSha256());
        var otherTool = descriptor("process.other", 2, TOOL_INPUT, descriptor.policy());
        var otherMapping = new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "other-definition", 2, "d".repeat(64), otherTool.reference(), backend);
        assertNotNull(new TianjiFixedBpmnStartToolBindings(List.of(otherTool), List.of(otherMapping)));
        assertEquals(backend.bindingSha256(), otherMapping.backend().bindingSha256());
    }

    @Test void stable_backend_content_is_canonical_and_contains_no_runtime_or_credential_versions() {
        var first = backend(); var ordered = new LinkedHashMap<String, ProcessValueTypeEnum>();
        ordered.put("region", ProcessValueTypeEnum.STRING); ordered.put("eligible", ProcessValueTypeEnum.BOOLEAN); ordered.put("amount", ProcessValueTypeEnum.INTEGER);
        assertEquals(first.bindingSha256(), backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, ordered, Duration.ofSeconds(3)).bindingSha256());
        for (var changed : List.of(
                backend(21, ISSUER, BASE, RESOURCE, "release-2", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)),
                backend(21, ISSUER, BASE, RESOURCE, "release-1", "d".repeat(64), MODEL, INPUTS, Duration.ofSeconds(3)),
                backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, "d".repeat(64), INPUTS, Duration.ofSeconds(3)),
                backend(21, ISSUER, URI.create("https://other.example/"), RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)),
                backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(4)),
                backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, Map.of("amount", ProcessValueTypeEnum.STRING), Duration.ofSeconds(3)))) {
            assertNotEquals(first.bindingSha256(), changed.bindingSha256());
        }
        assertEquals(Set.of("tenantId", "processIssuer", "processBaseUri", "processResourceUri", "registrationId", "expectedClientId", "expectedAppId",
                "expectedSourceBiz", "expectedSourceApp", "expectedEnvironment", "releaseVersion", "manifestSha256", "processDefinitionKey", "processModelSha256", "inputSchema", "timeout"),
                Arrays.stream(TianjiFixedBpmnStartToolBindings.BackendVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
    }

    @Test void configured_collections_and_backend_inputs_are_immutable_bounded_snapshots() {
        var sourceInputs = new HashMap<>(INPUTS); var backend = backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, sourceInputs, Duration.ofSeconds(3));
        var descriptor = descriptor(backend); var configured = new ArrayList<>(List.of(mapping(USER, descriptor, backend))); var descriptors = new ArrayList<>(List.of(descriptor));
        var fixed = new TianjiFixedBpmnStartToolBindings(descriptors, configured);
        sourceInputs.clear(); descriptors.clear(); configured.clear();
        assertEquals(INPUTS, backend.inputSchema()); assertEquals(1, fixed.bindings().size());
        assertThrows(UnsupportedOperationException.class, () -> backend.inputSchema().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixed.bindings().clear());
        assertNotNull(fixed.resolve(request(USER, descriptor)));
        var many = new ArrayList<TianjiFixedBpmnStartToolBindings.BindingVO>();
        for (int version = 1; version <= 256; version++) many.add(new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, version, HASH, descriptor.reference(), backend));
        assertEquals(256, new TianjiFixedBpmnStartToolBindings(List.of(descriptor), many).bindings().size());
        many.add(new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 257, HASH, descriptor.reference(), backend));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), many));
        var registrations = new ArrayList<>(List.of(descriptor));
        for (int index = 1; index < 256; index++) registrations.add(descriptor("process.extra_" + index, 1, TOOL_INPUT, descriptor.policy()));
        assertNotNull(new TianjiFixedBpmnStartToolBindings(registrations, List.of(mapping(USER, descriptor, backend))));
        registrations.add(descriptor("process.extra_256", 1, TOOL_INPUT, descriptor.policy()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedBpmnStartToolBindings(registrations, List.of(mapping(USER, descriptor, backend))));
    }

    @Test void empty_duplicate_unregistered_and_drifted_configuration_is_refused() {
        var backend = backend(); var descriptor = descriptor(backend); var mapping = mapping(USER, descriptor, backend);
        var drifted = new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "d".repeat(64), descriptor.reference(), backend);
        var unregistered = new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef(descriptor.key(), descriptor.version(), "d".repeat(64)), backend);
        var another = descriptor("process.other", 1, TOOL_INPUT, descriptor.policy());
        var changedDefinition = new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "d".repeat(64), another.reference(), backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedBpmnStartToolBindings(null, List.of(mapping)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), null),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(), List.of(mapping)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of()),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor, descriptor), List.of(mapping)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(mapping, mapping)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(mapping, drifted)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(unregistered)),
                () -> new TianjiFixedBpmnStartToolBindings(List.of(descriptor, another), List.of(mapping, changedDefinition)))) assertThrows(IllegalArgumentException.class, operation);
    }

    @Test void resolution_requires_the_exact_origin_principal_definition_and_registered_descriptor() {
        var backend = backend(); var descriptor = descriptor(backend);
        var fixed = new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(mapping(USER, descriptor, backend)));
        var changed = new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "Changed display content", descriptor.input(), descriptor.output(), descriptor.policy());
        for (var request : List.of(
                request(TrustedAgentActor.verified(22, USER.subjectType(), "alice", "https://agent-idp.example/21"), descriptor),
                request(TrustedAgentActor.verified(21, USER.subjectType(), "alice", "https://other.example/21"), descriptor), request(SERVICE, descriptor),
                request(USER, "other-definition", 1, HASH, descriptor), request(USER, DEFINITION, 2, HASH, descriptor),
                request(USER, DEFINITION, 1, "d".repeat(64), descriptor), request(USER, changed))) assertUnavailable(() -> fixed.resolve(request));
        assertUnavailable(() -> fixed.resolve(null));
        var serviceMapping = mapping(SERVICE, descriptor, backend);
        var both = new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(mapping(USER, descriptor, backend), serviceMapping));
        assertEquals(serviceMapping, both.resolve(request(SERVICE, descriptor)).binding());
    }

    @Test void mapped_tools_require_write_risk_with_exact_all_required_bpmn_input_types_and_backend() {
        var backend = backend(); var descriptor = descriptor(backend);
        var optional = new ToolSchema(TOOL_INPUT.fields(), Set.of("amount"));
        var wrongType = new ToolSchema(Map.of("amount", ToolSchema.ValueType.STRING, "region", ToolSchema.ValueType.STRING, "eligible", ToolSchema.ValueType.BOOLEAN), INPUTS.keySet());
        var missing = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount"));
        for (var changed : List.of(
                descriptor("process.optional", 1, optional, descriptor.policy()), descriptor("process.type", 1, wrongType, descriptor.policy()),
                descriptor("process.missing", 1, missing, descriptor.policy()),
                descriptor("process.readonly", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.start", ProcessClient.START_RESOURCE_KEY, backend.bindingSha256(), null)),
                descriptor("process.resource", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", "other.resource", backend.bindingSha256(), null)),
                descriptor("process.backend", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", ProcessClient.START_RESOURCE_KEY, "d".repeat(64), null)))) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiFixedBpmnStartToolBindings(List.of(changed), List.of(mapping(USER, changed, backend))));
        }
    }

    @Test void only_a_required_string_instance_id_output_is_registered_for_either_write_risk() {
        var backend = backend(); var ordinary = descriptor(backend);
        for (var output : List.of(new ToolSchema(Map.of(), Set.of()),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of()),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.INTEGER), Set.of("instanceId")),
                new ToolSchema(Map.of("receipt", ToolSchema.ValueType.STRING), Set.of("receipt")),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING, "secret", ToolSchema.ValueType.STRING), Set.of("instanceId", "secret")))) {
            var changed = new ToolDescriptor(ordinary.key(), ordinary.version(), ordinary.name(), ordinary.description(), ordinary.input(), output, ordinary.policy());
            assertThrows(IllegalArgumentException.class, () -> new TianjiFixedBpmnStartToolBindings(List.of(changed), List.of(mapping(USER, changed, backend))));
        }
        var approval = descriptor("process.approval", 1, TOOL_INPUT,
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", ProcessClient.START_RESOURCE_KEY, backend.bindingSha256(), "approval.start"));
        var configured = new TianjiFixedBpmnStartToolBindings(List.of(ordinary, approval), List.of(mapping(USER, ordinary, backend), mapping(USER, approval, backend)));
        var resolved = configured.resolve(request(USER, approval)); assertNotNull(resolved); assertEquals(approval, resolved.descriptor());
        // This metadata match does not prove that the future caller consumed an approval or committed dispatch.
        assertEquals(ToolPolicy.Risk.APPROVAL_REQUIRED, resolved.descriptor().policy().risk());
    }

    @Test void backend_validation_reuses_fixed_release_bpmn_schema_and_duration_contracts() {
        var invalidSchema = new HashMap<String, ProcessValueTypeEnum>(); invalidSchema.put("amount", null);
        var tooMany = new HashMap<String, ProcessValueTypeEnum>(); for (int index = 0; index < 17; index++) tooMany.put("value_" + index, ProcessValueTypeEnum.STRING);
        for (Executable operation : List.<Executable>of(
                () -> backend(0, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, " bad", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "bad:version", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", "bad", MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, "bad", INPUTS, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, null, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, invalidSchema, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, tooMany, Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, Map.of("execution", ProcessValueTypeEnum.STRING), Duration.ofSeconds(3)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, null),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofMillis(99)),
                () -> backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(30).plusNanos(1)),
                () -> new TianjiFixedBpmnStartToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, " leading", "agent-client", "agent-app", "business", "agent", "test", "release-1", MANIFEST, "start_order", MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> new TianjiFixedBpmnStartToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, "agent-process", "agent-client", "agent-app", "business", "agent", "\uD800", "release-1", MANIFEST, "start_order", MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> new TianjiFixedBpmnStartToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, "agent-process", "agent-client", "agent-app", "business", "agent", "test", "release-1", MANIFEST, "bad/key", MODEL, INPUTS, Duration.ofSeconds(3)))) assertThrows(IllegalArgumentException.class, operation);
        assertNotNull(backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, Map.of(), Duration.ofMillis(100)));
        assertNotNull(backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(30)));
    }

    @Test void backend_uris_keep_exact_ascii_identity_and_fit_the_existing_oauth_resource_limit() {
        for (var value : List.of("http://process.example/", "https://process.example/a/../b", "https://user:secret@process.example/",
                "https://process.example/?value=1", "https://process.example/#fragment", "https://process.example/资料", "https://process.example/" + "x".repeat(2048))) {
            URI uri = URI.create(value);
            assertThrows(IllegalArgumentException.class, () -> backend(21, uri, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)));
            assertThrows(IllegalArgumentException.class, () -> backend(21, ISSUER, uri, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)));
            assertThrows(IllegalArgumentException.class, () -> backend(21, ISSUER, BASE, uri, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)));
        }
        String prefix = "https://process.example/"; URI exact = URI.create(prefix + "x".repeat(512 - prefix.length()));
        assertNotNull(backend(21, ISSUER, BASE, exact, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)));
        assertThrows(IllegalArgumentException.class, () -> backend(21, ISSUER, BASE, URI.create(exact + "x"), "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)));
        var original = backend(); var changed = backend(21, ISSUER, URI.create("https://PROCESS.example/"), RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3));
        assertNotEquals(original, changed); assertNotEquals(original.hashCode(), changed.hashCode()); assertNotEquals(original.bindingSha256(), changed.bindingSha256());
    }

    @Test void binding_and_resolved_values_cannot_relabel_an_owner_definition_or_backend() {
        var backend = backend(); var descriptor = descriptor(backend); var fixed = mapping(USER, descriptor, backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(0, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(22, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, "LEGACY", USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), null, DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), " leading", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "\uD800", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 0, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "bad", descriptor.reference(), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef("bad/key", 1, HASH), backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, null, backend),
                () -> new TianjiFixedBpmnStartToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), null),
                () -> new TianjiFixedBpmnStartToolBindings.ResolvedBindingVO(null, fixed),
                () -> new TianjiFixedBpmnStartToolBindings.ResolvedBindingVO(descriptor, null),
                () -> new TianjiFixedBpmnStartToolBindings.ResolvedBindingVO(descriptor("process.other", 1, TOOL_INPUT, descriptor.policy()), fixed))) assertThrows(IllegalArgumentException.class, operation);
    }

    @Test void routing_metadata_diagnostics_are_redacted_and_do_not_claim_requester_permission() {
        var backend = backend(); var descriptor = descriptor(backend); var mapping = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedBpmnStartToolBindings(List.of(descriptor), List.of(mapping));
        var anotherUser = TrustedAgentActor.verified(21, USER.subjectType(), "bob", "https://agent-idp.example/21");
        var resolved = fixed.resolve(request(anotherUser, descriptor)); assertNotNull(resolved);
        assertEquals("TianjiFixedBpmnStartToolBindings.BackendVO[redacted]", backend.toString());
        assertEquals("TianjiFixedBpmnStartToolBindings.BindingVO[redacted]", mapping.toString());
        assertEquals("TianjiFixedBpmnStartToolBindings.ResolvedBindingVO[redacted]", resolved.toString());
    }

    private static TianjiFixedBpmnStartToolBindings.BackendVO backend() { return backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)); }
    private static TianjiFixedBpmnStartToolBindings.BackendVO backend(long tenant, URI issuer, URI base, URI resource, String release, String manifest,
                                                                    String model, Map<String, ProcessValueTypeEnum> inputs, Duration timeout) {
        return new TianjiFixedBpmnStartToolBindings.BackendVO(tenant, issuer, base, resource, "agent-process", "agent-client", "agent-app",
                "business", "agent", "test", release, manifest, "start_order", model, inputs, timeout);
    }
    private static ToolDescriptor descriptor(TianjiFixedBpmnStartToolBindings.BackendVO backend) {
        return descriptor("process.start", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", ProcessClient.START_RESOURCE_KEY, backend.bindingSha256(), null));
    }
    private static ToolDescriptor descriptor(String key, long version, ToolSchema input, ToolPolicy policy) {
        return new ToolDescriptor(key, version, key.replace('.', '_'), "Fixed BPMN start", input,
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")), policy);
    }
    private static TianjiFixedBpmnStartToolBindings.BindingVO mapping(TrustedAgentActor actor, ToolDescriptor descriptor, TianjiFixedBpmnStartToolBindings.BackendVO backend) {
        return new TianjiFixedBpmnStartToolBindings.BindingVO(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend);
    }
    private static FixedBpmnStartToolTransport.Request request(TrustedAgentActor actor, ToolDescriptor descriptor) { return request(actor, DEFINITION, 1, HASH, descriptor); }
    private static FixedBpmnStartToolTransport.Request request(TrustedAgentActor actor, String definition, long version, String hash, ToolDescriptor descriptor) {
        var run = new AgentRunAuthorization.Request(actor, RUN, definition, version, hash, "d".repeat(64), "e".repeat(64), "policy-v1", false, NOW.plusSeconds(300));
        return new FixedBpmnStartToolTransport.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(60)), descriptor,
                new ToolActionRequest(actor, descriptor, "order-1", "version-1", Map.of("amount", 20L, "region", "north", "eligible", true)), UUID.fromString("20000000-0000-0000-0000-000000000001"), NOW.plusSeconds(30));
    }
    private static void assertUnavailable(Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation); assertNull(failure.getCause());
        assertEquals("TIANJI_FIXED_BPMN_START_TOOL_BINDING_UNAVAILABLE", failure.getMessage());
    }
}
