package top.egon.cola.platform.tianji.agent.application.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyDecisionToolTransportTest {
    private static final Instant NOW = Instant.parse("2026-10-31T10:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", NEXT = "10000000-0000-0000-0000-000000000002", HASH = "a".repeat(64);
    private static final UUID INVOCATION = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final ToolDescriptor TOOL = new ToolDescriptor("invoice.decision", 1, "invoice_decision", "Fixed decision",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "note", ToolSchema.ValueType.STRING), Set.of("amount")),
            new ToolSchema(Map.of("approved", ToolSchema.ValueType.BOOLEAN), Set.of("approved")),
            new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "invoice.review", DecisionClient.EXECUTE_RESOURCE_KEY, HASH, null));

    @Test void request_values_normalize_and_copy_arguments_and_reuse_the_original_parameter_hash_domain() {
        var values = new HashMap<String, Object>(); values.put("amount", 20); values.put("note", "private input");
        var request = request(OWNER, RUN, TOOL, values, INVOCATION);
        values.put("amount", 21);
        assertEquals(Map.of("amount", 20L, "note", "private input"), request.arguments());
        assertThrows(UnsupportedOperationException.class, () -> request.arguments().put("amount", 22L));
        String expected = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_INVOCATION_PARAMETERS_V1", TOOL.key(), "1", TOOL.reference().contentSha256()), request.arguments());
        assertEquals(expected, request.parametersSha256());
        assertEquals(expected, TOOL.parametersSha256(Map.of("amount", 20, "note", "private input")));
        assertEquals("DecisionToolRequest[redacted]", request.toString());
    }

    @Test void downstream_identity_is_stable_for_one_invocation_and_separates_run_and_owner_namespaces() {
        var first = request(OWNER, RUN, TOOL, Map.of("amount", 20), INVOCATION);
        assertNotNull(first.downstreamRequestId());
        assertEquals(UUID.fromString(first.downstreamRequestId()).toString(), first.downstreamRequestId());
        assertEquals(first.downstreamRequestId(), request(OWNER, RUN, TOOL, Map.of("amount", 20L), INVOCATION).downstreamRequestId());
        assertNotEquals(first.downstreamRequestId(), request(OWNER, NEXT, TOOL, Map.of("amount", 20L), INVOCATION).downstreamRequestId());
        var other = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20");
        assertNotEquals(first.downstreamRequestId(), request(other, RUN, TOOL, Map.of("amount", 20L), INVOCATION).downstreamRequestId());
        assertNotEquals(first.downstreamRequestId(), request(OWNER, RUN, TOOL, Map.of("amount", 20L), UUID.randomUUID()).downstreamRequestId());
        assertEquals(RUN, first.correlationId());
        // A changed body cannot mint a second downstream key for the same immutable invocation.
        assertEquals(first.downstreamRequestId(), request(OWNER, RUN, TOOL, Map.of("amount", 21L), INVOCATION).downstreamRequestId());
    }

    @Test void readonly_nonlegacy_source_matching_lease_and_fixed_schema_are_required_at_the_handoff() {
        var write = new ToolDescriptor(TOOL.key(), TOOL.version(), TOOL.name(), TOOL.description(), TOOL.input(), TOOL.output(),
                new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, TOOL.policy().actionKey(), TOOL.policy().executionResourceKey(), HASH, null));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, write, Map.of("amount", 20L), INVOCATION));
        var legacy = new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "alice");
        assertThrows(IllegalArgumentException.class, () -> request(legacy, RUN, TOOL, Map.of("amount", 20L), INVOCATION));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, TOOL, Map.of(), INVOCATION));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, TOOL, Map.of("amount", "20"), INVOCATION));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, TOOL, Map.of("amount", 20L, "endpoint", "https://private.example"), INVOCATION));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.Request(run(OWNER, RUN), lease(NEXT), TOOL, Map.of("amount", 20L), INVOCATION, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 20L), INVOCATION, NOW.plusSeconds(61)));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.Request(null, lease(RUN), TOOL, Map.of("amount", 20L), INVOCATION, NOW));
    }

    @Test void immutable_request_time_is_not_a_current_authorization_or_a_java_lease_expiry_check() {
        var value = new ReadOnlyDecisionToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 20L), INVOCATION, NOW.minusSeconds(1));
        assertNotNull(value); assertEquals(NOW.minusSeconds(1), value.deadlineAt());
        // Current time and actual live database fencing belong to the trusted consumers.
        assertEquals(NOW.minusSeconds(2), value.lease().expiresAt());
    }

    @Test void executor_metadata_requires_a_nonlegacy_service_but_does_not_authenticate_it() {
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "agent-service", "https://process-idp.example/20");
        var value = new ReadOnlyDecisionToolTransport.VerifiedExecutor(service, NOW.plusSeconds(30));
        assertEquals(service, value.actor()); assertEquals("DecisionToolExecutor[redacted]", value.toString());
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.VerifiedExecutor(OWNER, NOW.plusSeconds(30)));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.VerifiedExecutor(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.SERVICE, "legacy"), NOW));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.VerifiedExecutor(service, null));
    }

    @Test void matched_and_no_match_observations_have_bounded_immutable_primitive_groups_and_redacted_diagnostics() {
        var output = new HashMap<String, Object>(); output.put("amount", BigInteger.valueOf(20));
        var value = observation(ReadOnlyDecisionToolTransport.Status.MATCHED, output, "rule_1");
        output.put("amount", 21L); assertEquals(Map.of("amount", 20L), value.output());
        assertThrows(UnsupportedOperationException.class, () -> value.output().put("amount", 22L));
        assertEquals("DecisionToolObservation[redacted]", value.toString());
        assertNotNull(observation(ReadOnlyDecisionToolTransport.Status.NO_MATCH, Map.of(), null));
        assertThrows(IllegalArgumentException.class, () -> observation(ReadOnlyDecisionToolTransport.Status.NO_MATCH, Map.of("approved", true), null));
        assertThrows(IllegalArgumentException.class, () -> observation(ReadOnlyDecisionToolTransport.Status.MATCHED, Map.of("approved", true), null));
        assertThrows(IllegalArgumentException.class, () -> observation(ReadOnlyDecisionToolTransport.Status.MATCHED, Map.of("nested", Map.of("private", "body")), "rule_1"));
        assertThrows(IllegalArgumentException.class, () -> observation(ReadOnlyDecisionToolTransport.Status.MATCHED, Map.of("text", "x".repeat(1025)), "rule_1"));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyDecisionToolTransport.DecisionObservation(ReadOnlyDecisionToolTransport.Status.NO_MATCH, "not-an-id", NOW, NOW, Map.of(), null));
    }

    @Test void observation_preserves_remote_and_local_instants_without_inventing_cross_clock_order() {
        var value = new ReadOnlyDecisionToolTransport.DecisionObservation(ReadOnlyDecisionToolTransport.Status.MATCHED, INVOCATION.toString(),
                NOW.plusSeconds(2), NOW, Map.of("approved", true), "row:1");
        assertEquals(NOW.plusSeconds(2), value.submittedAt()); assertEquals(NOW, value.receivedAt());
    }

    @Test void failure_only_exposes_its_fixed_category_and_cannot_accept_a_private_cause() {
        for (var kind : ReadOnlyDecisionToolTransport.FailureKind.values()) {
            var failure = new ReadOnlyDecisionToolTransport.Failure(kind);
            assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, () -> failure.initCause(new IllegalArgumentException("private bearer")));
        }
    }

    private static ReadOnlyDecisionToolTransport.DecisionObservation observation(ReadOnlyDecisionToolTransport.Status status, Map<String, Object> output, String rule) {
        return new ReadOnlyDecisionToolTransport.DecisionObservation(status, INVOCATION.toString(), NOW, NOW.plusSeconds(1), output, rule);
    }
    private static ReadOnlyDecisionToolTransport.Request request(TrustedAgentActor actor, String runId, ToolDescriptor descriptor, Map<String, Object> values, UUID id) {
        return new ReadOnlyDecisionToolTransport.Request(run(actor, runId), lease(runId), descriptor, values, id, NOW.plusSeconds(20));
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor, String runId) {
        return new AgentRunAuthorization.Request(actor, runId, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60));
    }
    private static AgentRunLease lease(String runId) { return new AgentRunLease(runId, "tool-host", 1, NOW.minusSeconds(2)); }
}
