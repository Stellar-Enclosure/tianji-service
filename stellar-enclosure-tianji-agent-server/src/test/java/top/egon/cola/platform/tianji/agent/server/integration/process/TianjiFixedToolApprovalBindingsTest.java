package top.egon.cola.platform.tianji.agent.server.integration.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TianjiFixedToolApprovalBindingsTest {
    private static final URI ISSUER = URI.create("https://idp.example/21"), BASE = URI.create("https://process.example/"), RESOURCE = URI.create("https://process.example/api");
    private static final String BUSINESS = "b".repeat(64), POLICY = "c".repeat(64), MODEL = "d".repeat(64), DEFINITION_SHA = "e".repeat(64), DEFINITION = "审批助手";
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001";
    private static final TrustedAgentActor USER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER.toASCIIString()),
            CONSUMER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "approval-client", ISSUER.toASCIIString());
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            OUTPUT = new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId"));

    @Test void approval_routing_is_separate_from_the_business_start_backend_and_has_no_fake_backend_digest() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef());
        var mapping = mapping(USER, descriptor, approval, backend);
        var resolved = new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(mapping)).resolve(request(USER, descriptor));
        assertEquals(descriptor, resolved.descriptor()); assertEquals(mapping, resolved.binding());
        assertEquals(BUSINESS, resolved.descriptor().policy().bindingSha256()); assertNotEquals(BUSINESS, approval.policyBindingSha256());
        assertEquals(Set.of("sourceId", "tenantId", "processIssuer", "processBaseUri", "processResourceUri", "registrationId", "expectedClientId", "expectedAppId",
                        "expectedSourceBiz", "expectedSourceApp", "expectedEnvironment", "timeout"),
                Arrays.stream(TianjiFixedToolApprovalBindings.BackendVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
        assertEquals(Set.of("tenantId", "ownerIssuerFingerprint", "principalType", "definitionKey", "definitionVersion", "definitionContentSha256", "tool", "approval", "backend"),
                Arrays.stream(TianjiFixedToolApprovalBindings.BindingVO.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet()));
    }

    @Test void configuration_is_an_immutable_bounded_snapshot() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef());
        var descriptors = new ArrayList<>(List.of(descriptor)); var configured = new ArrayList<>(List.of(mapping(USER, descriptor, approval, backend)));
        var fixed = new TianjiFixedToolApprovalBindings(descriptors, configured); descriptors.clear(); configured.clear();
        assertEquals(1, fixed.bindings().size()); assertThrows(UnsupportedOperationException.class, () -> fixed.bindings().clear());
        assertNotNull(fixed.resolve(request(USER, descriptor)));
        var many = new ArrayList<TianjiFixedToolApprovalBindings.BindingVO>();
        for (int version = 1; version <= 256; version++) many.add(new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, version, DEFINITION_SHA, descriptor.reference(), approval, backend));
        assertEquals(256, new TianjiFixedToolApprovalBindings(List.of(descriptor), many).bindings().size());
        many.add(new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 257, DEFINITION_SHA, descriptor.reference(), approval, backend));
        assertInvalid(() -> new TianjiFixedToolApprovalBindings(List.of(descriptor), many));
        var tools = new ArrayList<>(List.of(descriptor));
        for (int index = 1; index < 256; index++) tools.add(descriptor("process.extra_" + index, 1, INPUT, OUTPUT, descriptor.policy()));
        assertEquals(1, new TianjiFixedToolApprovalBindings(tools, List.of(mapping(USER, descriptor, approval, backend))).bindings().size());
        tools.add(descriptor("process.extra_256", 1, INPUT, OUTPUT, descriptor.policy()));
        assertInvalid(() -> new TianjiFixedToolApprovalBindings(tools, List.of(mapping(USER, descriptor, approval, backend))));
    }

    @Test void empty_duplicate_unregistered_and_same_origin_definition_hash_drift_is_rejected() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef()); var mapping = mapping(USER, descriptor, approval, backend);
        var unregistered = new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA,
                new DefinitionResourceRef(descriptor.key(), descriptor.version(), "a".repeat(64)), approval, backend);
        var another = descriptor("process.other", 1, INPUT, OUTPUT, descriptor.policy());
        var changedDefinition = new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "a".repeat(64), another.reference(), approval, backend);
        var displayDrift = new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "Changed", INPUT, OUTPUT, descriptor.policy());
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedToolApprovalBindings(null, List.of(mapping)), () -> new TianjiFixedToolApprovalBindings(List.of(descriptor), null),
                () -> new TianjiFixedToolApprovalBindings(List.of(), List.of(mapping)), () -> new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of()),
                () -> new TianjiFixedToolApprovalBindings(Arrays.asList(descriptor, null), List.of(mapping)), () -> new TianjiFixedToolApprovalBindings(List.of(descriptor), Arrays.asList(mapping, null)),
                () -> new TianjiFixedToolApprovalBindings(List.of(descriptor, descriptor), List.of(mapping)),
                () -> new TianjiFixedToolApprovalBindings(List.of(descriptor, displayDrift), List.of(mapping)),
                () -> new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(mapping, mapping)),
                () -> new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(unregistered)),
                () -> new TianjiFixedToolApprovalBindings(List.of(descriptor, another), List.of(mapping, changedDefinition)))) assertInvalid(operation);
    }

    @Test void resolution_pins_full_requester_origin_agent_definition_and_tool_content() {
        var descriptor = descriptor("approval.start"); var fixed = fixed(USER, descriptor, approval(CONSUMER, "approval.start"), backend());
        var changed = new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "Changed tool display", INPUT, OUTPUT, descriptor.policy());
        for (var request : List.of(
                request(TrustedAgentActor.verified(22, USER.subjectType(), USER.subjectId(), ISSUER.toASCIIString()), descriptor),
                request(TrustedAgentActor.verified(21, USER.subjectType(), USER.subjectId(), "https://other-idp.example/21"), descriptor),
                request(CONSUMER, descriptor), request(USER, "other-definition", 1, DEFINITION_SHA, descriptor),
                request(USER, DEFINITION, 2, DEFINITION_SHA, descriptor), request(USER, DEFINITION, 1, "a".repeat(64), descriptor), request(USER, changed))) assertUnavailable(() -> fixed.resolve(request));
        assertUnavailable(() -> fixed.resolve(null));
        assertNotNull(fixed.resolve(request(USER, descriptor)));
    }

    @Test void user_requester_is_separate_from_consumer_but_service_requester_must_be_the_consumer_itself() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef());
        var fixed = new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(mapping(USER, descriptor, approval, backend), mapping(CONSUMER, descriptor, approval, backend)));
        var otherUser = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "bob", ISSUER.toASCIIString());
        var resolved = fixed.resolve(request(otherUser, descriptor)); var query = resolved.binding().approval().query(request(otherUser, descriptor).action(), "approval-reference");
        assertEquals(ApprovalRequesterTypeEnum.USER, query.requesterType()); assertEquals("bob", query.requesterSubjectId());
        assertEquals(CONSUMER, resolved.binding().approval().consumer()); assertNotEquals(otherUser, CONSUMER);
        assertNotNull(fixed.resolve(request(CONSUMER, descriptor)));
        var otherService = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "another-service", ISSUER.toASCIIString());
        assertUnavailable(() -> fixed.resolve(request(otherService, descriptor)));
    }

    @Test void only_aa_start_with_the_exact_required_string_instance_id_and_matching_approval_policy_is_mapped() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var ordinary = descriptor(approval.policyRef());
        for (var policy : List.of(
                new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.start", ProcessClient.START_RESOURCE_KEY, BUSINESS, null),
                new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start", ProcessClient.START_RESOURCE_KEY, BUSINESS, null),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", "other.resource", BUSINESS, approval.policyRef()),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", ProcessClient.START_RESOURCE_KEY, BUSINESS, "other.approval"))) {
            var changed = descriptor(ordinary.key(), 1, INPUT, OUTPUT, policy);
            assertInvalid(() -> fixed(USER, changed, approval, backend));
        }
        for (var output : List.of(new ToolSchema(Map.of(), Set.of()), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of()),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.INTEGER), Set.of("instanceId")), new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING, "secret", ToolSchema.ValueType.STRING), Set.of("instanceId", "secret")))) {
            var changed = descriptor(ordinary.key(), 1, INPUT, output, ordinary.policy()); assertInvalid(() -> fixed(USER, changed, approval, backend));
        }
        var independent = descriptor(ordinary.key(), 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", ProcessClient.START_RESOURCE_KEY, "f".repeat(64), approval.policyRef()));
        assertNotNull(fixed(USER, independent, approval, backend).resolve(request(USER, independent)));
    }

    @Test void backend_fields_are_finite_safe_identifiers_and_bounded_timeout_values() {
        for (var source : Arrays.asList(null, "", " leading", "1bad", "a".repeat(129), "\uD800", "source\n")) assertInvalid(() -> backend(source, 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)));
        assertInvalid(() -> backend("process-source", 0, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)));
        for (int index = 0; index < 6; index++) {
            for (var invalid : Arrays.asList(null, "", " leading", "x".repeat(129), "bad\n", "\uD800")) {
                var fields = credentials(); fields[index] = invalid;
                assertInvalid(() -> backend("process-source", 21, ISSUER, BASE, RESOURCE, fields, Duration.ofSeconds(3)));
            }
        }
        for (var duration : Arrays.asList(null, Duration.ZERO, Duration.ofMillis(99), Duration.ofSeconds(30).plusNanos(1))) assertInvalid(() -> backend("process-source", 21, ISSUER, BASE, RESOURCE, credentials(), duration));
        assertNotNull(backend("process-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofMillis(100)));
        assertNotNull(backend("process-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(30)));
    }

    @Test void source_uris_are_exact_normalized_ascii_https_while_only_the_base_allows_loopback_http() {
        for (var value : List.of("https://user:secret@process.example/", "https://process.example/a/../b", "https://process.example/?query=1", "https://process.example/#fragment", "https://process.example/资料")) {
            URI uri = URI.create(value);
            assertInvalid(() -> backend("process-source", 21, uri, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)));
            assertInvalid(() -> backend("process-source", 21, ISSUER, uri, RESOURCE, credentials(), Duration.ofSeconds(3)));
            assertInvalid(() -> backend("process-source", 21, ISSUER, BASE, uri, credentials(), Duration.ofSeconds(3)));
        }
        for (var uri : List.of(URI.create("http://process.example/"), URI.create("http://localhost/"))) {
            assertInvalid(() -> backend("process-source", 21, uri, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)));
            assertInvalid(() -> backend("process-source", 21, ISSUER, BASE, uri, credentials(), Duration.ofSeconds(3)));
        }
        assertInvalid(() -> backend("process-source", 21, ISSUER, URI.create("http://process.example/"), RESOURCE, credentials(), Duration.ofSeconds(3)));
        for (var loopback : List.of("http://localhost/", "http://127.0.0.1:8080/", "http://[::1]/")) assertNotNull(backend("process-source", 21, ISSUER, URI.create(loopback), RESOURCE, credentials(), Duration.ofSeconds(3)));
        String prefix = "https://process.example/";
        URI maximum = URI.create(prefix + "x".repeat(2048 - prefix.length())), maximumResource = URI.create(prefix + "x".repeat(512 - prefix.length()));
        assertNotNull(backend("process-source", 21, maximum, maximum, maximumResource, credentials(), Duration.ofSeconds(3)));
        assertInvalid(() -> backend("process-source", 21, URI.create(maximum + "x"), BASE, RESOURCE, credentials(), Duration.ofSeconds(3)));
        assertInvalid(() -> backend("process-source", 21, ISSUER, URI.create(maximum + "x"), RESOURCE, credentials(), Duration.ofSeconds(3)));
        assertInvalid(() -> backend("process-source", 21, ISSUER, BASE, URI.create(maximumResource + "x"), credentials(), Duration.ofSeconds(3)));
    }

    @Test void exact_uri_bytes_participate_in_backend_equality_and_every_backend_field_is_preserved() {
        var original = backend(); assertEquals(original, backend()); assertEquals(original.hashCode(), backend().hashCode());
        for (var changed : List.of(
                backend("other-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)),
                backend("process-source", 22, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)),
                backend("process-source", 21, URI.create("https://IDP.example/21"), BASE, RESOURCE, credentials(), Duration.ofSeconds(3)),
                backend("process-source", 21, ISSUER, URI.create("https://PROCESS.example/"), RESOURCE, credentials(), Duration.ofSeconds(3)),
                backend("process-source", 21, ISSUER, BASE, URI.create("https://PROCESS.example/api"), credentials(), Duration.ofSeconds(3)),
                backend("process-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(4)))) {
            assertNotEquals(original, changed); assertNotEquals(original.hashCode(), changed.hashCode());
        }
        for (int index = 0; index < 6; index++) {
            var changed = credentials(); changed[index] = "other-field";
            assertNotEquals(original, backend("process-source", 21, ISSUER, BASE, RESOURCE, changed, Duration.ofSeconds(3)));
        }
    }

    @Test void a_binding_cannot_relabel_the_consumer_tenant_issuer_client_or_agent_metadata() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef());
        var foreignConsumer = TrustedAgentActor.verified(22, TrustedAgentActor.SubjectType.SERVICE, CONSUMER.subjectId(), ISSUER.toASCIIString());
        var otherConsumer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other-client", ISSUER.toASCIIString());
        var otherIssuer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, CONSUMER.subjectId(), "https://other-idp.example/21");
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedToolApprovalBindings.BindingVO(0, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(22, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, "LEGACY", USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, "a".repeat(64), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), null, DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), " leading", 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), "\uD800", 1, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 0, DEFINITION_SHA, descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, "bad", descriptor.reference(), approval, backend),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, new DefinitionResourceRef("bad/key", 1, MODEL), approval, backend),
                () -> mapping(USER, descriptor, approval(foreignConsumer, approval.policyRef()), backend),
                () -> mapping(USER, descriptor, approval(otherConsumer, approval.policyRef()), backend),
                () -> mapping(USER, descriptor, approval(otherIssuer, approval.policyRef()), backend),
                () -> mapping(USER, descriptor, null, backend), () -> mapping(USER, descriptor, approval, null),
                () -> new TianjiFixedToolApprovalBindings.BindingVO(21, USER.issuerFingerprint(), USER.subjectType(), DEFINITION, 1, DEFINITION_SHA, null, approval, backend))) assertInvalid(operation);
    }

    @Test void resolved_values_require_the_exact_registered_tool_and_approval_policy() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef()); var mapping = mapping(USER, descriptor, approval, backend);
        for (Executable operation : List.<Executable>of(
                () -> new TianjiFixedToolApprovalBindings.ResolvedBindingVO(null, mapping), () -> new TianjiFixedToolApprovalBindings.ResolvedBindingVO(descriptor, null),
                () -> new TianjiFixedToolApprovalBindings.ResolvedBindingVO(descriptor("process.other", 1, INPUT, OUTPUT, descriptor.policy()), mapping),
                () -> new TianjiFixedToolApprovalBindings.ResolvedBindingVO(new ToolDescriptor(descriptor.key(), descriptor.version(), descriptor.name(), "changed", INPUT, OUTPUT, descriptor.policy()), mapping))) assertInvalid(operation);
    }

    @Test void source_namespace_is_shared_across_consumer_policy_tool_and_transport_aliases() {
        var firstApproval = approval(CONSUMER, "approval.start"); var firstDescriptor = descriptor(firstApproval.policyRef());
        var otherConsumer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other-client", ISSUER.toASCIIString());
        var secondApproval = approval(otherConsumer, "other.approval"); var secondDescriptor = descriptor("process.other", 2, INPUT, OUTPUT,
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.other", ProcessClient.START_RESOURCE_KEY, "f".repeat(64), secondApproval.policyRef()));
        var aliasCredentials = credentials(); aliasCredentials[0] = "other-registration"; aliasCredentials[1] = otherConsumer.subjectId();
        var alias = backend("process-source", 21, ISSUER, URI.create("https://process-alias.example/"), RESOURCE, aliasCredentials, Duration.ofSeconds(4));
        var first = observation(fixed(USER, firstDescriptor, firstApproval, backend()).resolve(request(USER, firstDescriptor)), request(USER, firstDescriptor));
        var second = observation(fixed(USER, secondDescriptor, secondApproval, alias).resolve(request(USER, secondDescriptor)), request(USER, secondDescriptor));
        assertEquals(first.sourceNamespaceSha256(), second.sourceNamespaceSha256());
        assertNotEquals(first.binding(), second.binding()); assertNotEquals(first.consumer(), second.consumer());
        var renamed = backend("operator-changed-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3));
        var renamedObservation = observation(fixed(USER, firstDescriptor, firstApproval, renamed).resolve(request(USER, firstDescriptor)), request(USER, firstDescriptor));
        assertNotEquals(first.sourceNamespaceSha256(), renamedObservation.sourceNamespaceSha256());
    }

    @Test void all_routing_metadata_is_redacted_and_does_not_claim_current_authority() {
        var backend = backend(); var approval = approval(CONSUMER, "approval.start"); var descriptor = descriptor(approval.policyRef()); var mapping = mapping(USER, descriptor, approval, backend);
        var resolved = fixed(USER, descriptor, approval, backend).resolve(request(USER, descriptor));
        assertEquals("TianjiFixedToolApprovalBindings.BackendVO[redacted]", backend.toString());
        assertEquals("TianjiFixedToolApprovalBindings.BindingVO[redacted]", mapping.toString());
        assertEquals("TianjiFixedToolApprovalBindings.ResolvedBindingVO[redacted]", resolved.toString());
    }

    private static String[] credentials() { return new String[]{"approval-registration", "approval-client", "agent-app", "business", "agent", "test"}; }
    private static TianjiFixedToolApprovalBindings.BackendVO backend() { return backend("process-source", 21, ISSUER, BASE, RESOURCE, credentials(), Duration.ofSeconds(3)); }
    private static TianjiFixedToolApprovalBindings.BackendVO backend(String source, long tenant, URI issuer, URI base, URI resource, String[] credentials, Duration timeout) {
        return new TianjiFixedToolApprovalBindings.BackendVO(source, tenant, issuer, base, resource, credentials[0], credentials[1], credentials[2], credentials[3], credentials[4], credentials[5], timeout);
    }
    private static ToolApprovalBinding approval(TrustedAgentActor consumer, String policy) { return new ToolApprovalBinding(consumer, policy, POLICY, "approval-release-1", "approveStart", MODEL, "review"); }
    private static ToolDescriptor descriptor(String policy) { return descriptor("process.approvalStart", 1, INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", ProcessClient.START_RESOURCE_KEY, BUSINESS, policy)); }
    private static ToolDescriptor descriptor(String key, long version, ToolSchema input, ToolSchema output, ToolPolicy policy) { return new ToolDescriptor(key, version, key.replace('.', '_'), "Fixed approval START", input, output, policy); }
    private static TianjiFixedToolApprovalBindings.BindingVO mapping(TrustedAgentActor actor, ToolDescriptor descriptor, ToolApprovalBinding approval, TianjiFixedToolApprovalBindings.BackendVO backend) {
        return new TianjiFixedToolApprovalBindings.BindingVO(actor.tenantId(), actor.issuerFingerprint(), actor.subjectType(), DEFINITION, 1, DEFINITION_SHA, descriptor.reference(), approval, backend);
    }
    private static TianjiFixedToolApprovalBindings fixed(TrustedAgentActor actor, ToolDescriptor descriptor, ToolApprovalBinding approval, TianjiFixedToolApprovalBindings.BackendVO backend) {
        return new TianjiFixedToolApprovalBindings(List.of(descriptor), List.of(mapping(actor, descriptor, approval, backend)));
    }
    private static FixedToolApprovalVerifier.Request request(TrustedAgentActor actor, ToolDescriptor descriptor) { return request(actor, DEFINITION, 1, DEFINITION_SHA, descriptor); }
    private static FixedToolApprovalVerifier.Request request(TrustedAgentActor actor, String definition, long version, String hash, ToolDescriptor descriptor) {
        var run = new AgentRunAuthorization.Request(actor, RUN, definition, version, hash, "f".repeat(64), "1".repeat(64), "policy-v1", false, NOW.plusSeconds(300));
        return new FixedToolApprovalVerifier.Request(run, new AgentRunLease(RUN, "worker", 1, NOW.minusSeconds(1)), descriptor,
                new ToolActionRequest(actor, descriptor, "order-1", "version-1", Map.of("amount", 20L)), "approval-reference", NOW.plusSeconds(30));
    }
    private static FixedToolApprovalVerifier.ApprovedObservation observation(TianjiFixedToolApprovalBindings.ResolvedBindingVO resolved, FixedToolApprovalVerifier.Request request) {
        var binding = resolved.binding().approval(); var query = binding.query(request.action(), request.approvalReceiptId()); var source = query.sourceStart(); var backend = resolved.binding().backend();
        var proof = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), source.commandId(),
                "approval-instance", "approval-task", binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(), OrchestrationRequestDigests.start(source), true, NOW.minusSeconds(1), NOW, NOW.plusSeconds(20));
        return new FixedToolApprovalVerifier.ApprovedObservation(backend.sourceId(), backend.processIssuer().toASCIIString(), backend.processResourceUri().toASCIIString(), binding, proof, binding.consumer(), NOW.plusSeconds(30));
    }
    private static void assertInvalid(Executable operation) {
        var failure = assertThrows(IllegalArgumentException.class, operation); assertNull(failure.getCause()); assertEquals("Fixed tool approval binding is invalid", failure.getMessage());
    }
    private static void assertUnavailable(Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation); assertNull(failure.getCause()); assertEquals("Fixed tool approval binding is unavailable", failure.getMessage());
    }
}
