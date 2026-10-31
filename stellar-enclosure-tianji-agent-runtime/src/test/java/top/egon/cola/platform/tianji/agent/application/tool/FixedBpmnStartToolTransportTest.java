package top.egon.cola.platform.tianji.agent.application.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedBpmnStartToolTransportTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", NEXT = "10000000-0000-0000-0000-000000000002", HASH = "a".repeat(64);
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final ToolDescriptor TOOL = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE, "Start order");

    @Test void request_reuses_the_immutable_stable_action_without_minting_a_run_or_native_call_based_intent() {
        var input = new HashMap<String, Object>(); input.put("amount", 20); input.put("note", "private input");
        var action = action(OWNER, TOOL, input); var first = request(OWNER, RUN, TOOL, action, INVOCATION);
        input.clear(); assertSame(action, first.action()); assertEquals(Map.of("amount", 20L, "note", "private input"), first.action().arguments());
        assertThrows(UnsupportedOperationException.class, () -> first.action().arguments().clear());
        var another = request(OWNER, NEXT, TOOL, action, UUID.randomUUID());
        assertEquals(first.action().intentId(), another.action().intentId()); assertEquals(UUID.fromString(action.intentId()).toString(), action.intentId());
        assertEquals("business-v1", first.action().businessVersion());
        assertEquals("BpmnStartToolRequest[redacted]", first.toString());
        assertEquals(List.of("run", "lease", "descriptor", "action", "invocationId", "deadlineAt"),
                Arrays.stream(FixedBpmnStartToolTransport.Request.class.getRecordComponents()).map(component -> component.getName()).toList());
    }

    @Test void handoff_requires_the_exact_nonlegacy_owner_tool_policy_lease_and_deadline() {
        var action = action(OWNER, TOOL, Map.of("amount", 20));
        var bob = TrustedAgentActor.verified(20, OWNER.subjectType(), "bob", "https://idp.example/20");
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
        var legacy = new TrustedAgentActor(20, OWNER.subjectType(), "alice");
        var readonly = descriptor(ToolPolicy.Risk.READ_ONLY, "Read order");
        assertInvalid(() -> request(bob, RUN, TOOL, action, INVOCATION));
        assertInvalid(() -> request(service, RUN, TOOL, action, INVOCATION));
        assertInvalid(() -> request(legacy, RUN, TOOL, action, INVOCATION));
        assertInvalid(() -> request(OWNER, RUN, readonly, action, INVOCATION));
        assertInvalid(() -> request(OWNER, RUN, descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED, "Start order"), action, INVOCATION));
        assertInvalid(() -> request(OWNER, RUN, descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE, "Changed descriptor"), action, INVOCATION));
        assertInvalid(() -> new FixedBpmnStartToolTransport.Request(run(OWNER, RUN), lease(NEXT), TOOL, action, INVOCATION, NOW));
        assertInvalid(() -> new FixedBpmnStartToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, action, INVOCATION, NOW.plusSeconds(61)));
        assertInvalid(() -> new FixedBpmnStartToolTransport.Request(null, lease(RUN), TOOL, action, INVOCATION, NOW));
        assertInvalid(() -> new FixedBpmnStartToolTransport.Request(run(OWNER, RUN), null, TOOL, action, INVOCATION, NOW));
        assertInvalid(() -> request(OWNER, RUN, TOOL, null, INVOCATION));
        assertInvalid(() -> request(OWNER, RUN, TOOL, action, null));
        assertInvalid(() -> new FixedBpmnStartToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, action, INVOCATION, null));
    }

    @Test void malformed_or_drifted_action_metadata_cannot_cross_the_handoff() {
        var actual = action(OWNER, TOOL, Map.of("amount", 20));
        for (var variant : List.of("arguments", "unnormalized", "intent", "hash", "action", "business", "policy")) {
            var drift = spy(actual);
            switch (variant) {
                case "arguments" -> doReturn(Map.of("amount", "20")).when(drift).arguments();
                case "unnormalized" -> doReturn(Map.of("amount", 20)).when(drift).arguments();
                case "intent" -> doReturn("30000000-0000-0000-0000-000000000001").when(drift).intentId();
                case "hash" -> doReturn("b".repeat(64)).when(drift).requestSha256();
                case "action" -> doReturn("other.action").when(drift).actionKey();
                case "business" -> doReturn(" business-v1").when(drift).businessVersion();
                case "policy" -> doReturn(new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "other.action", ProcessClient.START_RESOURCE_KEY, HASH, null)).when(drift).policy();
            }
            assertInvalid(() -> request(OWNER, RUN, TOOL, drift, INVOCATION));
        }
    }

    @Test void approved_risk_and_service_owner_are_metadata_only_and_current_time_is_not_guessed() {
        var tool = descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED, "Start order with approval");
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "worker", "https://idp.example/20");
        var action = action(service, tool, Map.of("amount", 20));
        var value = new FixedBpmnStartToolTransport.Request(run(service, RUN), lease(RUN), tool, action, INVOCATION, NOW.minusSeconds(1));
        assertNotNull(value); assertEquals(NOW.minusSeconds(1), value.deadlineAt()); assertEquals(NOW.minusSeconds(2), value.lease().expiresAt());
        // A constructed action/request supplies no current grant, approval consumption or live database lease proof.
        var executor = new ReadOnlyDecisionToolTransport.VerifiedExecutor(service, NOW.plusSeconds(30));
        FixedBpmnStartToolTransport.Callback<String> callback = (observed, operation) -> observed.actor().subjectId();
        assertEquals(service.subjectId(), callback.apply(executor, () -> { throw new AssertionError("Operation must remain unconsumed"); }));
    }

    @Test void observation_has_only_a_closed_host_reference_and_bounded_instance_id_output() {
        String reference = "tianji:ps-start:v1:" + HASH;
        var value = new FixedBpmnStartToolTransport.StartObservation(reference, "process-instance-1");
        assertEquals(reference, value.hostReceiptReference()); assertEquals("process-instance-1", value.instanceId());
        assertEquals(Map.of("instanceId", "process-instance-1"), value.output());
        assertThrows(UnsupportedOperationException.class, () -> value.output().clear());
        assertEquals("BpmnStartToolObservation[redacted]", value.toString());
        assertEquals(List.of("hostReceiptReference", "instanceId"), Arrays.stream(FixedBpmnStartToolTransport.StartObservation.class.getRecordComponents()).map(component -> component.getName()).toList());
        assertNotNull(new FixedBpmnStartToolTransport.StartObservation(reference, "x".repeat(255)));
        for (var malformed : List.of("", "instance-1", "tianji:ps-start:v2:" + HASH, "tianji:ps-start:v1:" + HASH.toUpperCase(), reference + "x", " " + reference)) {
            assertInvalid(() -> new FixedBpmnStartToolTransport.StartObservation(malformed, "instance-1"));
        }
        for (var malformed : List.of("", " ", " instance-1", "x".repeat(256), "private\ninstance", "\uD800", ".", "..")) {
            assertInvalid(() -> new FixedBpmnStartToolTransport.StartObservation(reference, malformed));
        }
        assertInvalid(() -> new FixedBpmnStartToolTransport.StartObservation(null, "instance-1"));
        assertInvalid(() -> new FixedBpmnStartToolTransport.StartObservation(reference, null));
    }

    @Test void failures_expose_exactly_nine_safe_categories_without_an_attachable_private_cause() {
        assertEquals(Set.of("CREDENTIAL_UNAVAILABLE", "RELEASE_UNAVAILABLE", "RELEASE_CHANGED", "OPERATION_NOT_SENT", "OPERATION_DENIED",
                "OPERATION_RESULT_UNKNOWN", "OPERATION_INVALID_RESPONSE", "CANCELLED", "DEADLINE_EXCEEDED"),
                Arrays.stream(FixedBpmnStartToolTransport.FailureKind.values()).map(Enum::name).collect(java.util.stream.Collectors.toSet()));
        for (var kind : FixedBpmnStartToolTransport.FailureKind.values()) {
            var failure = new FixedBpmnStartToolTransport.Failure(kind);
            assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, () -> failure.initCause(new IllegalArgumentException("private bearer")));
        }
    }

    private static ToolDescriptor descriptor(ToolPolicy.Risk risk, String description) {
        return new ToolDescriptor("order.start", 1, "start_order", description,
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "note", ToolSchema.ValueType.STRING), Set.of("amount")),
                new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(risk, "order.start", ProcessClient.START_RESOURCE_KEY, HASH, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval.order" : null));
    }
    private static ToolActionRequest action(TrustedAgentActor owner, ToolDescriptor descriptor, Map<String, Object> arguments) {
        return new ToolActionRequest(owner, descriptor, "private-order-1", "business-v1", arguments);
    }
    private static FixedBpmnStartToolTransport.Request request(TrustedAgentActor owner, String run, ToolDescriptor descriptor, ToolActionRequest action, UUID invocation) {
        return new FixedBpmnStartToolTransport.Request(run(owner, run), lease(run), descriptor, action, invocation, NOW.plusSeconds(20));
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor, String run) {
        return new AgentRunAuthorization.Request(actor, run, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60));
    }
    private static AgentRunLease lease(String run) { return new AgentRunLease(run, "worker", 1, NOW.minusSeconds(2)); }
    private static void assertInvalid(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(IllegalArgumentException.class, operation); assertNull(failure.getCause());
        assertEquals("BPMN start tool transport value is invalid", failure.getMessage());
    }
}
