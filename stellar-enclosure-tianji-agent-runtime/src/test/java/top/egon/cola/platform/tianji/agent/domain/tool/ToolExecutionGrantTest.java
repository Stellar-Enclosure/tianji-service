package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionGrantTest {
    private static final TrustedAgentActor USER = actor(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final TrustedAgentActor SERVICE = actor(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", PURPOSE = "TIANJI_TOOL_PROCESS_START_V1";
    private static final Instant DEADLINE = Instant.parse("2026-10-31T02:00:00.123456Z");
    private static final URI RESOURCE = URI.create("https://agent.example/api");
    private static final DefinitionResourceRef TOOL = new DefinitionResourceRef("process.start", 1, "d".repeat(64));

    @Test void capability_slot_and_binding_are_defined_without_reusing_the_run_grant_namespace() {
        var binding = user();
        assertNotNull(binding.capabilityId()); assertNotNull(binding.bindingSha256());
        assertEquals(binding.capabilityId(), UUID.fromString(binding.capabilityId()).toString());
        assertTrue(binding.bindingSha256().matches("[0-9a-f]{64}"));
        // Independent Python struct/UTF-8 vectors pin the version-one byte representation.
        assertEquals("8cbbd6c3-6400-b6b9-7d32-8a3c35de6a54", binding.capabilityId());
        assertEquals("ec5be3e0168140db26c380412b379e6492ba0cc0f66f089b888684ab04aaefa2", binding.bindingSha256());
        assertNotEquals(AgentExecutionGrant.fingerprint(binding.run(), RESOURCE), binding.bindingSha256());
        assertFalse(ToolExecutionGrant.SESSION_REQUIRED);
    }

    @Test void same_slot_metadata_drift_changes_the_binding_instead_of_creating_a_second_capability() {
        var original = user();
        for (var changed : List.of(
                binding(run(USER), new DefinitionResourceRef(TOOL.key(), TOOL.version(), "f".repeat(64)), "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null),
                binding(run(USER), TOOL, "f".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null),
                binding(run(USER), TOOL, "e".repeat(64), "TIANJI_TOOL_OTHER_V1", RESOURCE, "tianji.api.agent.start", null),
                binding(run(USER), TOOL, "e".repeat(64), PURPOSE, URI.create("https://other.example/api"), "tianji.api.agent.start", null),
                binding(run(USER), TOOL, "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.other", null))) {
            assertEquals(original.capabilityId(), changed.capabilityId());
            assertNotEquals(original.bindingSha256(), changed.bindingSha256());
        }
    }

    @Test void owner_namespace_run_and_tool_version_each_have_an_independent_slot() {
        var original = user();
        for (var changed : List.of(
                user(run(actor(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20"))),
                user(run(actor(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20"))),
                user(run(actor(20, TrustedAgentActor.SubjectType.USER, "alice", "https://other.example/20"))),
                user(request(USER, "10000000-0000-0000-0000-000000000002", "assistant", 1, "a", "b", "c", "policy-1", true, DEADLINE)),
                binding(run(USER), new DefinitionResourceRef("process.read", 1, TOOL.contentSha256()), "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null),
                binding(run(USER), new DefinitionResourceRef(TOOL.key(), 2, TOOL.contentSha256()), "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null),
                service(subject("app", "business", "application", "test", "https://worker.example/api")))) {
            assertNotEquals(original.capabilityId(), changed.capabilityId());
            assertNotEquals(original.bindingSha256(), changed.bindingSha256());
        }
    }

    @Test void binding_value_equality_preserves_the_exact_audience_and_service_source_encoding() {
        var original = user();
        for (var audience : List.of(URI.create("https://AGENT.example/api"), URI.create("HTTPS://agent.example/api"))) {
            assertEquals(RESOURCE, audience);
            var changed = binding(run(USER), TOOL, "e".repeat(64), PURPOSE, audience, "tianji.api.agent.start", null);
            assertNotEquals(original.bindingSha256(), changed.bindingSha256());
            assertNotEquals(original, changed);
            assertNotEquals(new ToolExecutionGrant.ReservationVO(original, 1), new ToolExecutionGrant.ReservationVO(changed, 1));
        }
        var source = subject("app", "business", "application", "test", "https://worker.example/api");
        var changed = subject("app", "business", "application", "test", "https://WORKER.example/api");
        assertEquals(source, changed);
        assertNotEquals(service(source).bindingSha256(), service(changed).bindingSha256());
        assertNotEquals(service(source), service(changed));
        assertEquals(original, user()); assertEquals(original.hashCode(), user().hashCode());
    }

    @Test void every_immutable_run_dimension_is_bound_including_its_original_session_requirement() {
        var original = user();
        for (var changed : List.of(
                request(USER, RUN, "other", 1, "a", "b", "c", "policy-1", true, DEADLINE),
                request(USER, RUN, "assistant", 2, "a", "b", "c", "policy-1", true, DEADLINE),
                request(USER, RUN, "assistant", 1, "f", "b", "c", "policy-1", true, DEADLINE),
                request(USER, RUN, "assistant", 1, "a", "f", "c", "policy-1", true, DEADLINE),
                request(USER, RUN, "assistant", 1, "a", "b", "f", "policy-1", true, DEADLINE),
                request(USER, RUN, "assistant", 1, "a", "b", "c", "policy-2", true, DEADLINE),
                request(USER, RUN, "assistant", 1, "a", "b", "c", "policy-1", false, DEADLINE),
                request(USER, RUN, "assistant", 1, "a", "b", "c", "policy-1", true, DEADLINE.plusNanos(1)))) {
            assertEquals(original.capabilityId(), user(changed).capabilityId());
            assertNotEquals(original.bindingSha256(), user(changed).bindingSha256());
        }
    }

    @Test void original_service_source_is_bound_separately_from_the_agent_audience() {
        var original = service(subject("app", "business", "application", "test", "https://worker.example/api"));
        for (var changed : List.of(
                subject("other", "business", "application", "test", "https://worker.example/api"),
                subject("app", "other", "application", "test", "https://worker.example/api"),
                subject("app", "business", "other", "test", "https://worker.example/api"),
                subject("app", "business", "application", "other", "https://worker.example/api"),
                subject("app", "business", "application", "test", "https://other.example/api"))) {
            assertEquals(original.capabilityId(), service(changed).capabilityId());
            assertNotEquals(original.bindingSha256(), service(changed).bindingSha256());
        }
        assertNotEquals(original.resource(), original.serviceSubject().resourceUri());
        assertThrows(IllegalArgumentException.class, () -> user(run(SERVICE)));
        assertThrows(IllegalArgumentException.class, () -> binding(run(USER), TOOL, "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", original.serviceSubject()));
        assertThrows(IllegalArgumentException.class, () -> binding(run(SERVICE), TOOL, "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start",
                new AgentServiceExecutionGrant.Subject("other", "app", "business", "application", "test", URI.create("https://worker.example/api"))));
    }

    @Test void legacy_noncanonical_or_unsafe_sources_and_non_tool_purposes_cannot_form_a_binding() {
        assertThrows(IllegalArgumentException.class, () -> user(run(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "alice"))));
        assertThrows(IllegalArgumentException.class, () -> user(request(USER, "1-1-1-1-1", "assistant", 1, "a", "b", "c", "policy-1", true, DEADLINE)));
        for (var purpose : List.of(AgentRunAuthorization.PURPOSE, "TIANJI_MEMORY_READ", "TIANJI_TOOL_", "tianji_tool_start", "TIANJI_TOOL_" + "A".repeat(85))) {
            assertThrows(IllegalArgumentException.class, () -> binding(run(USER), TOOL, "e".repeat(64), purpose, RESOURCE, "tianji.api.agent.start", null));
        }
        for (var uri : List.of("http://agent.example/api", "https://name:password@agent.example/api", "https://agent.example/api?q=1", "https://agent.example/api#fragment", "https://agent.example/a/../api", "/api")) {
            assertThrows(IllegalArgumentException.class, () -> binding(run(USER), TOOL, "e".repeat(64), PURPOSE, URI.create(uri), "tianji.api.agent.start", null));
        }
        assertThrows(IllegalArgumentException.class, () -> binding(run(USER), TOOL, "bad", PURPOSE, RESOURCE, "tianji.api.agent.start", null));
        assertThrows(IllegalArgumentException.class, () -> binding(run(USER), TOOL, "e".repeat(64), PURPOSE, RESOURCE, "bad/key", null));
        assertThrows(IllegalArgumentException.class, () -> binding(run(USER), new DefinitionResourceRef("1tool", 1, "d".repeat(64)), "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null));
    }

    @Test void sealed_evidence_is_typed_epoch_fenced_and_bounded_by_the_original_run_deadline() {
        var userReservation = new ToolExecutionGrant.ReservationVO(user(), 1);
        var serviceReservation = new ToolExecutionGrant.ReservationVO(service(subject("app", "business", "application", "test", "https://worker.example/api")), 2);
        var userEvidence = new ToolExecutionGrant.EvidenceVO(userReservation, "9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE);
        var serviceEvidence = new ToolExecutionGrant.EvidenceVO(serviceReservation, "8" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE.minusNanos(1));
        assertEquals(DEADLINE, userEvidence.expiresAt()); assertEquals(2, serviceEvidence.reservation().issueEpoch());
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.ReservationVO(user(), 0));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.EvidenceVO(userReservation, "8" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.EvidenceVO(serviceReservation, "9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.EvidenceVO(userReservation, "9" + "1".repeat(62), "not sealed", DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.EvidenceVO(userReservation, "9" + "1".repeat(62), "ENC:v1:key:nonce:" + "a".repeat(32_769), DEADLINE));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionGrant.EvidenceVO(userReservation, "9" + "1".repeat(62), "ENC:v1:key:nonce:sealed", DEADLINE.plusNanos(1)));
        assertEquals("ToolExecutionGrant.BindingVO[redacted]", user().toString());
        assertEquals("ToolExecutionGrant.ReservationVO[redacted]", userReservation.toString());
        assertEquals("ToolExecutionGrant.EvidenceVO[redacted]", userEvidence.toString());
    }

    private static ToolExecutionGrant.BindingVO user() { return user(run(USER)); }
    private static ToolExecutionGrant.BindingVO user(AgentRunAuthorization.Request run) { return binding(run, TOOL, "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", null); }
    private static ToolExecutionGrant.BindingVO service(AgentServiceExecutionGrant.Subject subject) { return binding(run(SERVICE), TOOL, "e".repeat(64), PURPOSE, RESOURCE, "tianji.api.agent.start", subject); }
    private static ToolExecutionGrant.BindingVO binding(AgentRunAuthorization.Request run, DefinitionResourceRef tool, String backend, String purpose, URI resource, String key, AgentServiceExecutionGrant.Subject subject) {
        return new ToolExecutionGrant.BindingVO(run, tool, backend, purpose, resource, key, subject);
    }
    private static AgentServiceExecutionGrant.Subject subject(String app, String business, String application, String environment, String resource) {
        return new AgentServiceExecutionGrant.Subject("worker", app, business, application, environment, URI.create(resource));
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor) { return request(actor, RUN, "assistant", 1, "a", "b", "c", "policy-1", true, DEADLINE); }
    private static AgentRunAuthorization.Request request(TrustedAgentActor actor, String id, String definition, long version, String definitionHash, String requestHash, String snapshotHash, String policy, boolean session, Instant deadline) {
        return new AgentRunAuthorization.Request(actor, id, definition, version, definitionHash.repeat(64), requestHash.repeat(64), snapshotHash.repeat(64), policy, session, deadline);
    }
    private static TrustedAgentActor actor(long tenant, TrustedAgentActor.SubjectType type, String id, String issuer) { return TrustedAgentActor.verified(tenant, type, id, issuer); }
}
