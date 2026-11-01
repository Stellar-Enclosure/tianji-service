package top.egon.cola.platform.tianji.agent.application.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReadOnlyMcpToolTransportTest {
    private static final Instant NOW = Instant.parse("2026-11-01T10:00:00Z");
    private static final String RUN = "10000000-0000-0000-0000-000000000001", NEXT = "10000000-0000-0000-0000-000000000002", HASH = "a".repeat(64);
    private static final UUID ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private static final ToolDescriptor TOOL = tool(ToolPolicy.Risk.READ_ONLY, ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY);

    @Test void request_normalizes_and_copies_closed_arguments_and_uses_the_original_parameter_digest() {
        var values = new HashMap<String, Object>(); values.put("amount", 20); values.put("note", "private-input");
        var request = request(OWNER, RUN, TOOL, values, ID); values.put("amount", 21);
        assertEquals(Map.of("amount", 20L, "note", "private-input"), request.arguments());
        assertEquals(TOOL.parametersSha256(Map.of("amount", 20L, "note", "private-input")), request.parametersSha256());
        assertThrows(UnsupportedOperationException.class, () -> request.arguments().clear());
        assertEquals("McpToolRequest[redacted]", request.toString());
    }

    @Test void request_requires_readonly_mcp_resource_nonlegacy_owner_run_lease_and_valid_arguments() {
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY), Map.of("amount", 1), ID));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, tool(ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY), Map.of("amount", 1), ID));
        assertThrows(IllegalArgumentException.class, () -> request(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "alice"), RUN, TOOL, Map.of("amount", 1), ID));
        for (var invalid : java.util.List.of(Map.<String,Object>of(), Map.<String,Object>of("amount", "1"), Map.<String,Object>of("amount", 1, "tenant", 21))) {
            assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, TOOL, invalid, ID));
        }
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.Request(run(OWNER, RUN), lease(NEXT), TOOL, Map.of("amount", 1), ID, NOW));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 1), ID, NOW.plusSeconds(61)));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.Request(null, lease(RUN), TOOL, Map.of("amount", 1), ID, NOW));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.Request(run(OWNER, RUN), null, TOOL, Map.of("amount", 1), ID, NOW));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, RUN, TOOL, Map.of("amount", 1), null));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 1), ID, null));
    }

    @Test void downstream_uuid_separates_protocol_owner_issuer_type_tenant_run_invocation_and_full_tool_version() {
        var first = request(OWNER, RUN, TOOL, Map.of("amount", 1), ID);
        var value = first.downstreamRequestId(); assertEquals(UUID.fromString(value).toString(), value);
        assertEquals(value, request(OWNER, RUN, TOOL, Map.of("amount", 2L), ID).downstreamRequestId());
        assertEquals(value, request(OWNER, RUN, TOOL, Map.of("amount", 1L), ID).downstreamRequestId());
        assertNotEquals(value, request(OWNER, NEXT, TOOL, Map.of("amount", 1), ID).downstreamRequestId());
        assertNotEquals(value, request(OWNER, RUN, TOOL, Map.of("amount", 1), UUID.randomUUID()).downstreamRequestId());
        for (var actor : java.util.List.of(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "alice", "https://idp.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://other-idp.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "bob", "https://idp.example/20"))) {
            assertNotEquals(value, request(actor, RUN, TOOL, Map.of("amount", 1), ID).downstreamRequestId());
        }
        var next = new ToolDescriptor(TOOL.key(), 2, TOOL.name(), TOOL.description(), TOOL.input(), TOOL.output(), TOOL.policy());
        assertNotEquals(value, request(OWNER, RUN, next, Map.of("amount", 1), ID).downstreamRequestId());
        var changed = new ToolDescriptor(TOOL.key(), 1, TOOL.name(), "Changed declaration", TOOL.input(), TOOL.output(), TOOL.policy());
        assertNotEquals(value, request(OWNER, RUN, changed, Map.of("amount", 1), ID).downstreamRequestId());
        var dmn = new ReadOnlyDecisionToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 1), ID, NOW.plusSeconds(20));
        assertNotEquals(value, dmn.downstreamRequestId()); assertEquals(RUN, first.correlationId());
    }

    @Test void metadata_does_not_check_current_clock_or_create_live_database_or_service_authority() {
        var expired = new ReadOnlyMcpToolTransport.Request(run(OWNER, RUN), lease(RUN), TOOL, Map.of("amount", 1), ID, NOW.minusSeconds(1));
        assertEquals(NOW.minusSeconds(1), expired.deadlineAt()); assertEquals(NOW.minusSeconds(2), expired.lease().expiresAt());
        var service = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "machine", "https://business-idp.example/20");
        assertEquals(service, new ReadOnlyDecisionToolTransport.VerifiedExecutor(service, NOW).actor());
    }

    @Test void success_observation_copies_and_normalizes_bounded_primitive_values_without_dmn_fields() {
        var output = new HashMap<String, Object>(); output.put("amount", BigInteger.valueOf(20));
        var value = new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.SUCCESS, output); output.clear();
        assertEquals(Map.of("amount", 20L), value.output()); assertThrows(UnsupportedOperationException.class, () -> value.output().clear());
        assertEquals("McpToolObservation[redacted]", value.toString());
        for (var invalid : java.util.List.of(Map.<String,Object>of("nested", Map.of("private", "body")), Map.<String,Object>of("text", "x".repeat(1025)),
                Map.<String,Object>of("amount", 1.0), Map.<String,Object>of("amount", Long.MAX_VALUE), Map.<String,Object>of("text", "\uD800"))) {
            assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.SUCCESS, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.CallObservation(null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.CallObservation(ReadOnlyMcpToolTransport.Status.SUCCESS, null));
    }

    @Test void rejected_and_unknown_observations_never_carry_private_output() {
        for (var status : java.util.List.of(ReadOnlyMcpToolTransport.Status.TOOL_ERROR, ReadOnlyMcpToolTransport.Status.UNKNOWN)) {
            assertNotNull(new ReadOnlyMcpToolTransport.CallObservation(status, Map.of()));
            assertThrows(IllegalArgumentException.class, () -> new ReadOnlyMcpToolTransport.CallObservation(status, Map.of("private", "body")));
        }
    }

    @Test void failure_exposes_only_fixed_category_with_no_attachable_private_cause() {
        for (var kind : ReadOnlyMcpToolTransport.FailureKind.values()) {
            var failure = new ReadOnlyMcpToolTransport.Failure(kind);
            assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause());
            assertThrows(IllegalStateException.class, () -> failure.initCause(new IllegalArgumentException("private-token")));
        }
    }

    private static ToolDescriptor tool(ToolPolicy.Risk risk, String resource) {
        return new ToolDescriptor("invoice.mcp", 1, "invoice_mcp", "Fixed MCP",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "note", ToolSchema.ValueType.STRING), Set.of("amount")),
                new ToolSchema(Map.of("approved", ToolSchema.ValueType.BOOLEAN), Set.of("approved")),
                new ToolPolicy(risk, "invoice.lookup", resource, HASH, null));
    }
    private static ReadOnlyMcpToolTransport.Request request(TrustedAgentActor actor, String runId, ToolDescriptor tool, Map<String,Object> args, UUID id) {
        return new ReadOnlyMcpToolTransport.Request(run(actor, runId), lease(runId), tool, args, id, NOW.plusSeconds(20));
    }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor, String id) {
        return new AgentRunAuthorization.Request(actor, id, "assistant", 1, HASH, HASH, HASH, "policy-1", false, NOW.plusSeconds(60));
    }
    private static AgentRunLease lease(String id) { return new AgentRunLease(id, "host", 1, NOW.minusSeconds(2)); }
}
