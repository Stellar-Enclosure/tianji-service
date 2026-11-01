package top.egon.cola.platform.tianji.agent.server.integration.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyMcpToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TianjiFixedReadOnlyMcpToolBindingsTest {
    private static final URI ISSUER = URI.create("https://mcp-idp.example/21"), ENDPOINT = URI.create("https://mcp.example/mcp"), RESOURCE = URI.create("https://mcp.example/api");
    private static final String HASH = "c".repeat(64), DOCUMENT = "b".repeat(64), DEFINITION = "只读助手", RUN = "10000000-0000-0000-0000-000000000001";
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final TrustedAgentActor USER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://agent-idp.example/21");
    private static final TrustedAgentActor SERVICE = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://agent-idp.example/21");
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount"));
    private static final ToolSchema OUTPUT = new ToolSchema(Map.of("eligible", ToolSchema.ValueType.BOOLEAN), Set.of("eligible"));

    @Test void the_backend_fingerprint_pins_all_fields_and_fixed_protocol_limits_before_descriptor_identity() {
        var backend = backend();
        var scopes = Map.<String, Object>of("catalog:read", true, "price:read", true);
        String inputHash = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_SCHEMA_V1"), Map.of("amount.type", "INTEGER", "amount.required", true));
        String outputHash = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_SCHEMA_V1"), Map.of("eligible.type", "BOOLEAN", "eligible.required", true));
        String expected = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_MCP_READ_ONLY_BACKEND_V1", "21", ISSUER.toASCIIString(), ENDPOINT.toASCIIString(), RESOURCE.toASCIIString(),
                "agent-mcp", "agent-client", "agent-app", "business", "agent", "test", "yuheng", "1.0", "catalog.read", DOCUMENT,
                inputHash, outputHash, "true", "PT3S", "YUHENG_STABLE_JSON_POST", "2025-11-25", "1048576", "131072", "8", "4096", "256", "512", "128", "128"), scopes);
        assertEquals(expected, backend.bindingSha256());
        var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(binding));
        assertEquals(binding, fixed.resolve(request(USER, descriptor)).binding());
        var another = descriptor("mcp.other", 2, INPUT, OUTPUT, descriptor.policy());
        assertEquals(backend.bindingSha256(), mapping(USER, another, backend).backend().bindingSha256());
        assertEquals(Set.of("tenantId", "issuer", "endpoint", "resourceUri", "registrationId", "expectedClientId", "expectedAppId", "expectedSourceBiz", "expectedSourceApp",
                "expectedEnvironment", "businessScopes", "expectedServerName", "expectedServerVersion", "remoteToolName", "remoteToolDocumentSha256", "inputSchema", "outputSchema", "nonBillable", "timeout"),
                Arrays.stream(TianjiFixedReadOnlyMcpToolBindings.BackendVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
        assertEquals("YUHENG_STABLE_JSON_POST", TianjiFixedReadOnlyMcpToolBindings.TRANSPORT_MODE);
        assertEquals("2025-11-25", TianjiFixedReadOnlyMcpToolBindings.PROTOCOL_VERSION);
        assertEquals(1_048_576, TianjiFixedReadOnlyMcpToolBindings.MAX_RESPONSE_BYTES);
        assertEquals(131_072, TianjiFixedReadOnlyMcpToolBindings.MAX_REMOTE_TOOL_DOCUMENT_BYTES);
    }

    @Test void each_variable_backend_dimension_and_both_schema_required_sets_change_the_fingerprint() {
        var changes = Map.<String, Object>ofEntries(Map.entry("tenantId", 22L), Map.entry("issuer", URI.create("https://other-idp.example/21")),
                Map.entry("endpoint", URI.create("https://other-mcp.example/mcp")), Map.entry("resourceUri", URI.create("https://other-mcp.example/api")),
                Map.entry("registrationId", "other-registration"), Map.entry("expectedClientId", "other-client"), Map.entry("expectedAppId", "other-app"),
                Map.entry("expectedSourceBiz", "other-business"), Map.entry("expectedSourceApp", "other-source"), Map.entry("expectedEnvironment", "other-environment"),
                Map.entry("businessScopes", Set.of("catalog:read")), Map.entry("expectedServerName", "other-server"), Map.entry("expectedServerVersion", "2.0"),
                Map.entry("remoteToolName", "catalog.other"), Map.entry("remoteToolDocumentSha256", "d".repeat(64)),
                Map.entry("inputSchema", new ToolSchema(INPUT.fields(), Set.of())), Map.entry("outputSchema", new ToolSchema(OUTPUT.fields(), Set.of())), Map.entry("timeout", Duration.ofSeconds(4)));
        String original = backend().bindingSha256();
        changes.forEach((field, value) -> assertNotEquals(original, changed(field, value).bindingSha256(), field));
        assertNotEquals(original, changed("inputSchema", new ToolSchema(Map.of("amount", ToolSchema.ValueType.STRING), Set.of("amount"))).bindingSha256());
        assertNotEquals(original, changed("outputSchema", new ToolSchema(Map.of("eligible", ToolSchema.ValueType.STRING), Set.of("eligible"))).bindingSha256());
        assertEquals(original, changed("businessScopes", new java.util.LinkedHashSet<>(List.of("price:read", "catalog:read"))).bindingSha256());
    }

    @Test void host_collections_scopes_and_local_schemas_are_bounded_immutable_snapshots() {
        var scopes = new HashSet<>(Set.of("catalog:read", "price:read")); var backend = changed("businessScopes", scopes);
        var descriptor = descriptor(backend); var mappings = new ArrayList<>(List.of(mapping(USER, descriptor, backend))); var descriptors = new ArrayList<>(List.of(descriptor));
        var fixed = new TianjiFixedReadOnlyMcpToolBindings(descriptors, mappings); scopes.clear(); mappings.clear(); descriptors.clear();
        assertEquals(Set.of("catalog:read", "price:read"), backend.businessScopes());
        assertThrows(UnsupportedOperationException.class, () -> backend.businessScopes().clear());
        assertThrows(UnsupportedOperationException.class, () -> fixed.bindings().clear());
        assertNotNull(fixed.resolve(request(USER, descriptor)));
        var many = new ArrayList<TianjiFixedReadOnlyMcpToolBindings.BindingVO>();
        for (int version = 1; version <= 256; version++) many.add(new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, version, HASH, descriptor.reference(), backend));
        assertEquals(256, new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), many).bindings().size());
        many.add(new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 257, HASH, descriptor.reference(), backend));
        assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), many));
        var registrations = new ArrayList<ToolDescriptor>();
        for (int index = 0; index < 256; index++) registrations.add(descriptor("mcp.extra_" + index, 1, INPUT, OUTPUT, descriptor.policy()));
        assertNotNull(new TianjiFixedReadOnlyMcpToolBindings(registrations, List.of(mapping(USER, registrations.getFirst(), backend))));
        registrations.add(descriptor("mcp.extra_256", 1, INPUT, OUTPUT, descriptor.policy()));
        assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings(registrations, List.of(mapping(USER, registrations.getFirst(), backend))));
    }

    @Test void null_empty_duplicate_unregistered_and_definition_drifted_configuration_fails_closed() {
        var backend = backend(); var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        var other = descriptor("mcp.other", 1, INPUT, OUTPUT, descriptor.policy());
        var drift = new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "d".repeat(64), other.reference(), backend);
        var missing = new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef(descriptor.key(), 1, "d".repeat(64)), backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedReadOnlyMcpToolBindings(null, List.of(binding)), () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), null),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(), List.of(binding)), () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of()),
                () -> new TianjiFixedReadOnlyMcpToolBindings(Arrays.asList((ToolDescriptor) null), List.of(binding)),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), Arrays.asList((TianjiFixedReadOnlyMcpToolBindings.BindingVO) null)),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor, descriptor), List.of(binding)),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(binding, binding)),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(missing)),
                () -> new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor, other), List.of(binding, drift)))) assertInvalid(operation);
    }

    @Test void resolution_rechecks_the_entire_owner_definition_tool_and_descriptor_namespace() {
        var backend = backend(); var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        var fixed = new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(binding));
        var changed = new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "Changed display content", INPUT, OUTPUT, descriptor.policy());
        for (var request : List.of(request(TrustedAgentActor.verified(22, USER.subjectType(), "alice", "https://agent-idp.example/21"), descriptor),
                request(TrustedAgentActor.verified(21, USER.subjectType(), "alice", "https://other.example/21"), descriptor), request(SERVICE, descriptor),
                request(USER, "other-definition", 1, HASH, descriptor), request(USER, DEFINITION, 2, HASH, descriptor), request(USER, DEFINITION, 1, "d".repeat(64), descriptor), request(USER, changed))) assertUnavailable(() -> fixed.resolve(request));
        assertUnavailable(() -> fixed.resolve(null));
        var serviceBinding = mapping(SERVICE, descriptor, backend);
        var both = new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(binding, serviceBinding));
        assertEquals(serviceBinding, both.resolve(request(SERVICE, descriptor)).binding());
        assertEquals(binding, both.resolve(request(TrustedAgentActor.verified(21, USER.subjectType(), "bob", "https://agent-idp.example/21"), descriptor)).binding());
        assertNotEquals(USER.issuerFingerprint(), TrustedAgentActor.verified(21, USER.subjectType(), "alice", ISSUER.toASCIIString()).issuerFingerprint());
    }

    @Test void resolved_values_require_read_only_mcp_resource_full_tool_reference_and_exact_input_and_output_schemas() {
        var backend = backend(); var descriptor = descriptor(backend);
        var optionalInput = new ToolSchema(INPUT.fields(), Set.of()); var optionalOutput = new ToolSchema(OUTPUT.fields(), Set.of());
        for (var changed : List.of(descriptor("mcp.optional_input", 1, optionalInput, OUTPUT, descriptor.policy()), descriptor("mcp.optional_output", 1, INPUT, optionalOutput, descriptor.policy()),
                descriptor("mcp.input_type", 1, new ToolSchema(Map.of("amount", ToolSchema.ValueType.STRING), Set.of("amount")), OUTPUT, descriptor.policy()),
                descriptor("mcp.output_type", 1, INPUT, new ToolSchema(Map.of("eligible", ToolSchema.ValueType.STRING), Set.of("eligible")), descriptor.policy()),
                descriptor("mcp.write", 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "mcp.read", ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null)),
                descriptor("mcp.resource", 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "mcp.read", "other.resource", backend.bindingSha256(), null)),
                descriptor("mcp.backend", 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "mcp.read", ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY, "d".repeat(64), null)))) {
            assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings(List.of(changed), List.of(mapping(USER, changed, backend))));
        }
        assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO(null, mapping(USER, descriptor, backend)));
        assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO(descriptor, null));
        assertInvalid(() -> new TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO(descriptor("mcp.other", 1, INPUT, OUTPUT, descriptor.policy()), mapping(USER, descriptor, backend)));
    }

    @Test void backend_requires_explicit_nonbillable_true_canonical_identity_scopes_and_bounded_duration() {
        for (var field : List.of("registrationId", "expectedClientId", "expectedAppId", "expectedSourceBiz", "expectedSourceApp", "expectedEnvironment", "expectedServerName", "expectedServerVersion")) {
            for (var value : Arrays.asList(null, "", " leading", "trailing ", "control\n", "\uD800", "x".repeat(129))) assertInvalid(() -> changed(field, value));
            assertNotNull(changed(field, "x".repeat(128)));
        }
        for (Executable operation : List.<Executable>of(() -> changed("tenantId", 0L), () -> changed("nonBillable", false), () -> changed("nonBillable", null),
                () -> changed("inputSchema", null), () -> changed("outputSchema", null), () -> changed("timeout", null), () -> changed("timeout", Duration.ofMillis(99)),
                () -> changed("timeout", Duration.ofSeconds(30).plusNanos(1)), () -> changed("remoteToolDocumentSha256", "A".repeat(64)),
                () -> changed("remoteToolDocumentSha256", null), () -> changed("remoteToolDocumentSha256", "a".repeat(63)),
                () -> changed("businessScopes", null), () -> changed("businessScopes", Set.of()))) assertInvalid(operation);
        for (var name : List.of("bad/name", " bad", "bad name", "", "x".repeat(129), "工具", "\uD800")) assertInvalid(() -> changed("remoteToolName", name));
        assertInvalid(() -> changed("remoteToolName", null)); assertNotNull(changed("remoteToolName", "0-A_z.9"));
        for (var scope : List.of("openid", "profile", "email", "phone", "address", "offline_access", "bad scope", " bad", "x".repeat(129))) assertInvalid(() -> changed("businessScopes", Set.of(scope)));
        var nullScope = new HashSet<String>(); nullScope.add(null); assertInvalid(() -> changed("businessScopes", nullScope));
        var scopes = new HashSet<String>(); for (int index = 0; index < 64; index++) scopes.add("scope:" + index);
        assertNotNull(changed("businessScopes", scopes)); scopes.add("scope:64"); assertInvalid(() -> changed("businessScopes", scopes));
        assertNotNull(changed("timeout", Duration.ofMillis(100))); assertNotNull(changed("timeout", Duration.ofSeconds(30)));
    }

    @Test void backend_uri_identity_is_exact_ascii_normalized_and_only_the_endpoint_allows_loopback_http() {
        for (var field : List.of("issuer", "endpoint", "resourceUri")) {
            assertInvalid(() -> changed(field, null));
            for (var value : List.of("http://mcp.example/", "https://mcp.example/a/../b", "https://user:secret@mcp.example/", "https://mcp.example/?x=1",
                    "https://mcp.example/#fragment", "https://mcp.example/资料", "https:/missing-host", "https://mcp.example/" + "x".repeat(2048))) assertInvalid(() -> changed(field, URI.create(value)));
        }
        for (var endpoint : List.of("http://localhost/mcp", "http://127.0.0.1/mcp", "http://[::1]/mcp")) {
            assertNotNull(changed("endpoint", URI.create(endpoint))); assertInvalid(() -> changed("issuer", URI.create(endpoint))); assertInvalid(() -> changed("resourceUri", URI.create(endpoint)));
        }
        String prefix = "https://mcp.example/";
        for (var field : List.of("issuer", "endpoint", "resourceUri")) {
            int maximum = field.equals("resourceUri") ? 512 : 2048; URI exact = URI.create(prefix + "x".repeat(maximum - prefix.length()));
            assertNotNull(changed(field, exact)); assertInvalid(() -> changed(field, URI.create(exact + "x")));
        }
        var original = backend(); var changed = changed("endpoint", URI.create("https://MCP.example/mcp"));
        assertNotEquals(original, changed); assertNotEquals(original.hashCode(), changed.hashCode()); assertNotEquals(original.bindingSha256(), changed.bindingSha256());
        assertEquals(original, backend()); assertEquals(original.hashCode(), backend().hashCode());
    }

    @Test void binding_guards_and_redacted_diagnostics_do_not_turn_host_metadata_into_permission() {
        var backend = backend(); var descriptor = descriptor(backend); var binding = mapping(USER, descriptor, backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(0, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(22, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, "LEGACY", USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), null, DEFINITION, 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), " leading", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "\uD800", 1, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 0, HASH, descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "bad", descriptor.reference(), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, new DefinitionResourceRef("bad/key", 1, HASH), backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, null, backend),
                () -> new TianjiFixedReadOnlyMcpToolBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), null))) assertInvalid(operation);
        var resolved = new TianjiFixedReadOnlyMcpToolBindings(List.of(descriptor), List.of(binding)).resolve(request(USER, descriptor));
        assertEquals("TianjiFixedReadOnlyMcpToolBindings.BackendVO[redacted]", backend.toString());
        assertEquals("TianjiFixedReadOnlyMcpToolBindings.BindingVO[redacted]", binding.toString());
        assertEquals("TianjiFixedReadOnlyMcpToolBindings.ResolvedBindingVO[redacted]", resolved.toString());
    }

    @Test void remote_document_hash_sorts_every_object_preserves_array_order_and_pins_the_complete_tool_document() {
        var first = new LinkedHashMap<String, Object>(); first.put("name", "catalog.read"); first.put("inputSchema", Map.of("type", "object", "properties", Map.of("amount", Map.of("type", "integer"))));
        first.put("annotations", Map.of("readOnlyHint", "true")); first.put("description", "Read catalog");
        var reversed = new LinkedHashMap<String, Object>(); reversed.put("description", "Read catalog"); reversed.put("annotations", Map.of("readOnlyHint", "true")); reversed.put("inputSchema", first.get("inputSchema")); reversed.put("name", "catalog.read");
        assertEquals(documentHash(first), documentHash(reversed));
        assertEquals(OrchestrationRequestDigests.hash(List.of("TIANJI_MCP_REMOTE_TOOL_V1", "{\"a\":true,\"b\":null}"), Map.of()), documentHash(nullableDocument()));
        for (var field : List.of("name", "description", "title", "annotations", "_meta", "inputSchema", "outputSchema")) {
            var changed = new LinkedHashMap<>(first); changed.put(field, "changed"); assertNotEquals(documentHash(first), documentHash(changed), field);
        }
        assertNotEquals(documentHash(Map.of("array", List.of("a", "b"))), documentHash(Map.of("array", List.of("b", "a"))));
        assertNotEquals(documentHash(Map.of("value", "true")), documentHash(Map.of("value", true)));
        var mutable = new ArrayList<Object>(List.of("original")); var document = new HashMap<String, Object>(); document.put("array", mutable);
        String before = documentHash(document); mutable.set(0, "changed"); assertNotEquals(before, documentHash(document));
    }

    @Test void document_numbers_use_one_bounded_decimal_form_across_jdk_numeric_types() {
        String one = documentHash(Map.of("number", 1));
        for (var value : List.of((byte) 1, (short) 1, 1L, BigInteger.ONE, new BigDecimal("1.000"), new BigDecimal("10E-1"), 1F, 1D)) assertEquals(one, documentHash(Map.of("number", value)));
        assertEquals(documentHash(Map.of("number", 0)), documentHash(Map.of("number", -0.0D)));
        assertEquals(documentHash(Map.of("number", 0)), documentHash(Map.of("number", new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE))));
        assertEquals(documentHash(Map.of("number", 0)), documentHash(Map.of("number", new BigDecimal(BigInteger.ZERO, Integer.MAX_VALUE))));
        assertEquals(documentHash(Map.of("number", new BigDecimal("0.1"))), documentHash(Map.of("number", 0.1D)));
        assertEquals(documentHash(Map.of("number", new BigDecimal("0.1"))), documentHash(Map.of("number", 0.1F)));
        assertNotNull(documentHash(Map.of("number", new BigDecimal("1E+128")))); assertNotNull(documentHash(Map.of("number", new BigDecimal("1E-128"))));
        for (var value : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Float.NaN, Float.POSITIVE_INFINITY,
                new BigDecimal("9".repeat(129)), new BigDecimal("1E+129"), new BigDecimal("1E-129"), new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE), new BigDecimal(BigInteger.ONE, Integer.MAX_VALUE),
                BigInteger.ONE.shiftLeft(513), new BigDecimal(BigInteger.TEN.pow(1000), 1000))) assertDocumentInvalid(Map.of("number", value));
    }

    @Test void remote_documents_reject_non_json_values_without_invoking_arbitrary_to_string() {
        var hostile = new Object() { @Override public String toString() { fail("arbitrary object diagnostic must not be called"); return "secret"; } };
        var number = new Number() {
            @Override public int intValue() { fail("unknown number must not be converted"); return 0; }
            @Override public long longValue() { fail("unknown number must not be converted"); return 0; }
            @Override public float floatValue() { fail("unknown number must not be converted"); return 0; }
            @Override public double doubleValue() { fail("unknown number must not be converted"); return 0; }
            @Override public String toString() { fail("unknown number diagnostic must not be called"); return "secret"; }
        };
        for (var value : List.of(hostile, number, Set.of("value"), new int[]{1}, URI.create("https://secret.example/"))) assertDocumentInvalid(Map.of("value", value));
        assertDocumentInvalid(null);
        var nullKey = new HashMap<String, Object>(); nullKey.put(null, true); assertDocumentInvalid(nullKey);
        assertDocumentInvalid(nonStringKeyDocument());
    }

    @Test void document_containers_depth_nodes_and_cycles_are_rejected_before_unbounded_traversal() {
        assertNotNull(documentHash(Map.of())); assertNotNull(documentHash(nestedDocument(8))); assertDocumentInvalid(nestedDocument(9));
        var object = new LinkedHashMap<String, Object>(); for (int index = 0; index < 256; index++) object.put("value" + index, true);
        assertNotNull(documentHash(object)); object.put("extra", true); assertDocumentInvalid(object);
        assertNotNull(documentHash(Map.of("array", java.util.Collections.nCopies(256, true)))); assertDocumentInvalid(Map.of("array", java.util.Collections.nCopies(257, true)));
        var repeated = java.util.Collections.nCopies(256, true); var tooManyNodes = new ArrayList<Object>();
        for (int index = 0; index < 16; index++) tooManyNodes.add(repeated);
        assertDocumentInvalid(Map.of("array", tooManyNodes));
        var exactNodes = new ArrayList<Object>(); for (int index = 0; index < 15; index++) exactNodes.add(repeated);
        exactNodes.addAll(java.util.Collections.nCopies(238, true));
        assertNotNull(documentHash(Map.of("array", exactNodes))); exactNodes.add(true); assertDocumentInvalid(Map.of("array", exactNodes));
        var cycle = new HashMap<String, Object>(); cycle.put("self", cycle); assertDocumentInvalid(cycle);
        var listCycle = new ArrayList<Object>(); listCycle.add(listCycle); assertDocumentInvalid(Map.of("array", listCycle));
        Object dag = "leaf"; for (int index = 0; index < 7; index++) dag = java.util.Collections.nCopies(256, dag);
        assertDocumentInvalid(Map.of("array", dag));
    }

    @Test void document_text_and_serialized_bytes_are_bounded_with_valid_unicode_and_no_plain_decimal_expansion() {
        assertDocumentInvalid(Map.of("value", "\uD800")); assertDocumentInvalid(Map.of("\uDC00", true));
        assertDocumentInvalid(Map.of("value", "a".repeat(131_073)));
        assertDocumentInvalid(Map.of("value", "字".repeat(43_691)));
        assertDocumentInvalid(Map.of("a", "x".repeat(65_536), "b", "y".repeat(65_536)));
        assertDocumentInvalid(Map.of("value", "\n".repeat(65_536)));
        String prefix = "{\"value\":\""; String suffix = "\"}";
        assertNotNull(documentHash(Map.of("value", "x".repeat(131_072 - prefix.length() - suffix.length()))));
        assertDocumentInvalid(Map.of("value", "x".repeat(131_073 - prefix.length() - suffix.length())));
        assertNotNull(documentHash(Map.of("number", new BigDecimal("1E+128"), "emoji", "😀")));
    }

    private static TianjiFixedReadOnlyMcpToolBindings.BackendVO backend() { return backend(Map.of()); }
    private static TianjiFixedReadOnlyMcpToolBindings.BackendVO changed(String field, Object value) { var changes = new HashMap<String, Object>(); changes.put(field, value); return backend(changes); }
    @SuppressWarnings("unchecked")
    private static TianjiFixedReadOnlyMcpToolBindings.BackendVO backend(Map<String, Object> values) {
        return new TianjiFixedReadOnlyMcpToolBindings.BackendVO((long) values.getOrDefault("tenantId", 21L), (URI) values.getOrDefault("issuer", ISSUER),
                (URI) values.getOrDefault("endpoint", ENDPOINT), (URI) values.getOrDefault("resourceUri", RESOURCE), (String) values.getOrDefault("registrationId", "agent-mcp"),
                (String) values.getOrDefault("expectedClientId", "agent-client"), (String) values.getOrDefault("expectedAppId", "agent-app"), (String) values.getOrDefault("expectedSourceBiz", "business"),
                (String) values.getOrDefault("expectedSourceApp", "agent"), (String) values.getOrDefault("expectedEnvironment", "test"), (Set<String>) values.getOrDefault("businessScopes", Set.of("catalog:read", "price:read")),
                (String) values.getOrDefault("expectedServerName", "yuheng"), (String) values.getOrDefault("expectedServerVersion", "1.0"), (String) values.getOrDefault("remoteToolName", "catalog.read"),
                (String) values.getOrDefault("remoteToolDocumentSha256", DOCUMENT), (ToolSchema) values.getOrDefault("inputSchema", INPUT), (ToolSchema) values.getOrDefault("outputSchema", OUTPUT),
                (Boolean) values.getOrDefault("nonBillable", true), (Duration) values.getOrDefault("timeout", Duration.ofSeconds(3)));
    }
    private static ToolDescriptor descriptor(TianjiFixedReadOnlyMcpToolBindings.BackendVO backend) {
        return descriptor("mcp.catalog", 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "mcp.read", ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY, backend.bindingSha256(), null));
    }
    private static ToolDescriptor descriptor(String key, long version, ToolSchema input, ToolSchema output, ToolPolicy policy) { return new ToolDescriptor(key, version, key.replace('.', '_'), "Fixed read-only MCP call", input, output, policy); }
    private static TianjiFixedReadOnlyMcpToolBindings.BindingVO mapping(TrustedAgentActor actor, ToolDescriptor descriptor, TianjiFixedReadOnlyMcpToolBindings.BackendVO backend) {
        return new TianjiFixedReadOnlyMcpToolBindings.BindingVO(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), DEFINITION, 1, HASH, descriptor.reference(), backend);
    }
    private static ReadOnlyMcpToolTransport.Request request(TrustedAgentActor actor, ToolDescriptor descriptor) { return request(actor, DEFINITION, 1, HASH, descriptor); }
    private static ReadOnlyMcpToolTransport.Request request(TrustedAgentActor actor, String definition, long version, String hash, ToolDescriptor descriptor) {
        var run = new AgentRunAuthorization.Request(actor, RUN, definition, version, hash, "d".repeat(64), "e".repeat(64), "policy-v1", false, NOW.plusSeconds(300));
        return new ReadOnlyMcpToolTransport.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.plusSeconds(60)), descriptor, Map.of("amount", 20L), UUID.fromString("20000000-0000-0000-0000-000000000001"), NOW.plusSeconds(30));
    }
    private static String documentHash(Map<String, Object> document) { return TianjiFixedReadOnlyMcpToolBindings.remoteToolDocumentSha256(document); }
    private static Map<String, Object> nullableDocument() { var document = new LinkedHashMap<String, Object>(); document.put("b", null); document.put("a", true); return document; }
    private static Map<String, Object> nestedDocument(int depth) { Object value = true; for (int index = 2; index < depth; index++) value = List.of(value); return Map.of("value", value); }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static Map<String, Object> nonStringKeyDocument() { return (Map) Map.of(1, true); }
    private static void assertDocumentInvalid(Map<String, Object> document) { assertInvalid(() -> documentHash(document)); }
    private static void assertInvalid(Executable operation) { var failure = assertThrows(IllegalArgumentException.class, operation); assertNull(failure.getCause()); assertEquals("TIANJI_FIXED_READ_ONLY_MCP_TOOL_BINDING_INVALID", failure.getMessage()); }
    private static void assertUnavailable(Executable operation) { var failure = assertThrows(IllegalStateException.class, operation); assertNull(failure.getCause()); assertEquals("TIANJI_FIXED_READ_ONLY_MCP_TOOL_BINDING_UNAVAILABLE", failure.getMessage()); }
}
