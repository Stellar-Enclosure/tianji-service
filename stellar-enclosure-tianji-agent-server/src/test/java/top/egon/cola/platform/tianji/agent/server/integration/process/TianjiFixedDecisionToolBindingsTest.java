package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
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

class TianjiFixedDecisionToolBindingsTest {
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
        assertEquals("0d08f52452a480e11615f13fa9eaa988012135d271f7219e08f8cfe2892e555c", backend.bindingSha256());
        var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(binding));
        var resolved = fixed.resolve(request(USER, descriptor)); assertNotNull(resolved);
        assertEquals(descriptor, resolved.descriptor()); assertEquals(binding, resolved.binding());
        assertEquals(backend.bindingSha256(), descriptor.policy().bindingSha256());
        var otherTool = descriptor("decision.other", 2, TOOL_INPUT, descriptor.policy());
        var otherMapping = new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "other-definition", 2, "d".repeat(64), otherTool.reference(), backend);
        assertNotNull(new TianjiFixedDecisionToolBindings(List.of(otherTool), List.of(otherMapping)));
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
                "expectedSourceBiz", "expectedSourceApp", "expectedEnvironment", "releaseVersion", "manifestSha256", "decisionKey", "decisionModelSha256", "inputSchema", "timeout"),
                Arrays.stream(TianjiFixedDecisionToolBindings.BackendVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
    }

    @Test void configured_collections_and_backend_inputs_are_immutable_bounded_snapshots() {
        var sourceInputs = new HashMap<>(INPUTS); var backend = backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, sourceInputs, Duration.ofSeconds(3));
        var descriptor = descriptor(backend); var configured = new ArrayList<>(List.of(mapping(USER, descriptor, backend))); var descriptors = new ArrayList<>(List.of(descriptor));
        var fixed = new TianjiFixedDecisionToolBindings(descriptors, configured);
        sourceInputs.clear(); descriptors.clear(); configured.clear();
        assertEquals(INPUTS, backend.inputSchema()); assertEquals(1, fixed.bindings().size());
        assertThrows(UnsupportedOperationException.class, () -> backend.inputSchema().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixed.bindings().clear());
        assertNotNull(fixed.resolve(request(USER, descriptor)));
        var many = new ArrayList<TianjiFixedDecisionToolBindings.BindingVO>();
        for (int version = 1; version <= 256; version++) many.add(new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, version, HASH, descriptor.reference(), backend));
        assertEquals(256, new TianjiFixedDecisionToolBindings(List.of(descriptor), many).bindings().size());
        many.add(new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 257, HASH, descriptor.reference(), backend));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), many));
        var registrations = new ArrayList<>(List.of(descriptor));
        for (int index = 1; index < 256; index++) registrations.add(descriptor("decision.extra_" + index, 1, TOOL_INPUT, descriptor.policy()));
        assertNotNull(new TianjiFixedDecisionToolBindings(registrations, List.of(mapping(USER, descriptor, backend))));
        registrations.add(descriptor("decision.extra_256", 1, TOOL_INPUT, descriptor.policy()));
        assertThrows(IllegalArgumentException.class, () -> new TianjiFixedDecisionToolBindings(registrations, List.of(mapping(USER, descriptor, backend))));
    }

    @Test void empty_duplicate_unregistered_and_drifted_configuration_is_refused() {
        var backend = backend(); var descriptor = descriptor(backend); var mapping = mapping(USER, descriptor, backend);
        var drifted = new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "d".repeat(64), descriptor.reference(), backend);
        var unregistered = new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef(descriptor.key(), descriptor.version(), "d".repeat(64)), backend);
        var another = descriptor("decision.other", 1, TOOL_INPUT, descriptor.policy());
        var changedDefinition = new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "d".repeat(64), another.reference(), backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedDecisionToolBindings(null, List.of(mapping)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), null),
                () -> new TianjiFixedDecisionToolBindings(List.of(), List.of(mapping)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of()),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor, descriptor), List.of(mapping)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(mapping, mapping)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(mapping, drifted)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(unregistered)),
                () -> new TianjiFixedDecisionToolBindings(List.of(descriptor, another), List.of(mapping, changedDefinition)))) assertThrows(IllegalArgumentException.class, operation);
    }

    @Test void resolution_requires_the_exact_origin_principal_definition_and_registered_descriptor() {
        var backend = backend(); var descriptor = descriptor(backend);
        var fixed = new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(mapping(USER, descriptor, backend)));
        var changed = new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "Changed display content", descriptor.input(), descriptor.output(), descriptor.policy());
        for (var request : List.of(
                request(TrustedAgentActor.verified(22, USER.subjectType(), "alice", "https://agent-idp.example/21"), descriptor),
                request(TrustedAgentActor.verified(21, USER.subjectType(), "alice", "https://other.example/21"), descriptor), request(SERVICE, descriptor),
                request(USER, "other-definition", 1, HASH, descriptor), request(USER, DEFINITION, 2, HASH, descriptor),
                request(USER, DEFINITION, 1, "d".repeat(64), descriptor), request(USER, changed))) assertUnavailable(() -> fixed.resolve(request));
        assertUnavailable(() -> fixed.resolve(null));
        var serviceMapping = mapping(SERVICE, descriptor, backend);
        var both = new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(mapping(USER, descriptor, backend), serviceMapping));
        assertEquals(serviceMapping, both.resolve(request(SERVICE, descriptor)).binding());
    }

    @Test void mapped_tools_are_read_only_with_exact_all_required_dmn_input_types_and_backend() {
        var backend = backend(); var descriptor = descriptor(backend);
        var optional = new ToolSchema(TOOL_INPUT.fields(), Set.of("amount"));
        var wrongType = new ToolSchema(Map.of("amount", ToolSchema.ValueType.STRING, "region", ToolSchema.ValueType.STRING, "eligible", ToolSchema.ValueType.BOOLEAN), INPUTS.keySet());
        var missing = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount"));
        for (var changed : List.of(
                descriptor("decision.optional", 1, optional, descriptor.policy()), descriptor("decision.type", 1, wrongType, descriptor.policy()),
                descriptor("decision.missing", 1, missing, descriptor.policy()),
                descriptor("decision.write", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "decision.evaluate", DecisionClient.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null)),
                descriptor("decision.resource", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.evaluate", "other.resource", backend.bindingSha256(), null)),
                descriptor("decision.backend", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.evaluate", DecisionClient.EXECUTE_RESOURCE_KEY, "d".repeat(64), null)))) {
            assertThrows(IllegalArgumentException.class, () -> new TianjiFixedDecisionToolBindings(List.of(changed), List.of(mapping(USER, changed, backend))));
        }
    }

    @Test void backend_validation_reuses_fixed_release_dmn_schema_and_duration_contracts() {
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
                () -> new TianjiFixedDecisionToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, " leading", "agent-client", "agent-app", "business", "agent", "test", "release-1", MANIFEST, "calculate_discount", MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> new TianjiFixedDecisionToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, "agent-process", "agent-client", "agent-app", "business", "agent", "\uD800", "release-1", MANIFEST, "calculate_discount", MODEL, INPUTS, Duration.ofSeconds(3)),
                () -> new TianjiFixedDecisionToolBindings.BackendVO(21, ISSUER, BASE, RESOURCE, "agent-process", "agent-client", "agent-app", "business", "agent", "test", "release-1", MANIFEST, "bad/key", MODEL, INPUTS, Duration.ofSeconds(3)))) assertThrows(IllegalArgumentException.class, operation);
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
                () -> new TianjiFixedDecisionToolBindings.BindingVO(0, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(22, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, "LEGACY", USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), null, DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), " leading", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "\uD800", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 0, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "bad", descriptor.reference(), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef("bad/key", 1, HASH), backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, null, backend),
                () -> new TianjiFixedDecisionToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), null),
                () -> new TianjiFixedDecisionToolBindings.ResolvedBindingVO(null, fixed),
                () -> new TianjiFixedDecisionToolBindings.ResolvedBindingVO(descriptor, null),
                () -> new TianjiFixedDecisionToolBindings.ResolvedBindingVO(descriptor("decision.other", 1, TOOL_INPUT, descriptor.policy()), fixed))) assertThrows(IllegalArgumentException.class, operation);
    }

    @Test void routing_metadata_diagnostics_are_redacted_and_do_not_claim_requester_permission() {
        var backend = backend(); var descriptor = descriptor(backend); var mapping = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedDecisionToolBindings(List.of(descriptor), List.of(mapping));
        var anotherUser = TrustedAgentActor.verified(21, USER.subjectType(), "bob", "https://agent-idp.example/21");
        var resolved = fixed.resolve(request(anotherUser, descriptor)); assertNotNull(resolved);
        assertEquals("TianjiFixedDecisionToolBindings.BackendVO[redacted]", backend.toString());
        assertEquals("TianjiFixedDecisionToolBindings.BindingVO[redacted]", mapping.toString());
        assertEquals("TianjiFixedDecisionToolBindings.ResolvedBindingVO[redacted]", resolved.toString());
    }

    private static TianjiFixedDecisionToolBindings.BackendVO backend() { return backend(21, ISSUER, BASE, RESOURCE, "release-1", MANIFEST, MODEL, INPUTS, Duration.ofSeconds(3)); }
    private static TianjiFixedDecisionToolBindings.BackendVO backend(long tenant, URI issuer, URI base, URI resource, String release, String manifest,
                                                                    String model, Map<String, ProcessValueTypeEnum> inputs, Duration timeout) {
        return new TianjiFixedDecisionToolBindings.BackendVO(tenant, issuer, base, resource, "agent-process", "agent-client", "agent-app",
                "business", "agent", "test", release, manifest, "calculate_discount", model, inputs, timeout);
    }
    private static ToolDescriptor descriptor(TianjiFixedDecisionToolBindings.BackendVO backend) {
        return descriptor("decision.calculate", 1, TOOL_INPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.evaluate", DecisionClient.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null));
    }
    private static ToolDescriptor descriptor(String key, long version, ToolSchema input, ToolPolicy policy) {
        return new ToolDescriptor(key, version, key.replace('.', '_'), "Fixed DMN evaluation", input,
                new ToolSchema(Map.of("discount", ToolSchema.ValueType.INTEGER), Set.of("discount")), policy);
    }
    private static TianjiFixedDecisionToolBindings.BindingVO mapping(TrustedAgentActor actor, ToolDescriptor descriptor, TianjiFixedDecisionToolBindings.BackendVO backend) {
        return new TianjiFixedDecisionToolBindings.BindingVO(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend);
    }
    private static ReadOnlyDecisionToolTransport.Request request(TrustedAgentActor actor, ToolDescriptor descriptor) { return request(actor, DEFINITION, 1, HASH, descriptor); }
    private static ReadOnlyDecisionToolTransport.Request request(TrustedAgentActor actor, String definition, long version, String hash, ToolDescriptor descriptor) {
        var run = new AgentRunAuthorization.Request(actor, RUN, definition, version, hash, "d".repeat(64), "e".repeat(64), "policy-v1", false, NOW.plusSeconds(300));
        return new ReadOnlyDecisionToolTransport.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(60)), descriptor,
                Map.of("amount", 20L, "region", "north", "eligible", true), UUID.fromString("20000000-0000-0000-0000-000000000001"), NOW.plusSeconds(30));
    }
    private static void assertUnavailable(Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation); assertNull(failure.getCause());
        assertEquals("TIANJI_FIXED_DECISION_TOOL_BINDING_UNAVAILABLE", failure.getMessage());
    }
}
