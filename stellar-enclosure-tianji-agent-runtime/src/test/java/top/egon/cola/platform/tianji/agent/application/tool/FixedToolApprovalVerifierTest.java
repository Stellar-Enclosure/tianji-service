package top.egon.cola.platform.tianji.agent.application.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.*;

class FixedToolApprovalVerifierTest {
    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final String ISSUER = "https://idp.example/21", RESOURCE = "https://process.example/api/21", HASH = "a".repeat(64);
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", ISSUER);
    private static final TrustedAgentActor CONSUMER = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "approval-consumer", ISSUER);
    private static final ToolDescriptor TOOL = tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, "instanceId", "approval-policy");
    private static final ToolActionRequest ACTION = new ToolActionRequest(OWNER, TOOL, "invoice-1", "business-v1", Map.of("amount", 7));
    private static final AgentRunAuthorization.Request RUN = run(OWNER, "run-one");
    private static final AgentRunLease LEASE = new AgentRunLease(RUN.runId(), "worker", 7, NOW.minusSeconds(1));
    private static final ToolApprovalBinding BINDING = binding(CONSUMER, "approval-policy", "release-1");
    private static final VerifiedApprovalDTO PROOF = proof(BINDING, ACTION, true);

    @Test void request_contains_only_the_fixed_action_and_keeps_parameter_and_business_hashes_separate() {
        var request = request("receipt-not-a-uuid");
        assertEquals(6, Request.class.getRecordComponents().length);
        assertEquals(TOOL.parametersSha256(ACTION.arguments()), request.parametersSha256());
        assertNotEquals(ACTION.requestSha256(), request.parametersSha256());
        assertEquals(LEASE, request.lease(), "An expired Java hint is not a current database lease decision");
        assertEquals("receipt-not-a-uuid", request.approvalReceiptId()); assertEquals(Map.of("amount", 7L), request.action().arguments());
    }

    @Test void nulls_and_wrong_run_lease_or_deadline_are_rejected_at_the_request_boundary() {
        Object[] values = {RUN, LEASE, TOOL, ACTION, "receipt-1", NOW.plusSeconds(5)};
        for (int index = 0; index < values.length; index++) { var changed = values.clone(); changed[index] = null; assertThrows(IllegalArgumentException.class, () -> construct(changed)); }
        assertThrows(IllegalArgumentException.class, () -> new Request(RUN, new AgentRunLease("other-run", "worker", 7, NOW.plusSeconds(1)), TOOL, ACTION, "receipt-1", NOW));
        assertThrows(IllegalArgumentException.class, () -> new Request(RUN, LEASE, TOOL, ACTION, "receipt-1", RUN.deadlineAt().plusNanos(1)));
    }

    @Test void the_full_owner_action_descriptor_policy_and_start_profile_must_match() {
        var stranger = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "other", ISSUER);
        assertThrows(IllegalArgumentException.class, () -> new Request(run(stranger, RUN.runId()), LEASE, TOOL, ACTION, "receipt-1", NOW));
        for (var candidate : List.of(tool(ToolPolicy.Risk.READ_ONLY, ProcessClient.START_RESOURCE_KEY, "instanceId", null),
                tool(ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, "instanceId", null),
                tool(ToolPolicy.Risk.APPROVAL_REQUIRED, "other.write", "instanceId", "approval-policy"),
                tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, "other", "approval-policy"),
                tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, "instanceId", "another-policy"))) {
            assertThrows(IllegalArgumentException.class, () -> new Request(RUN, LEASE, candidate, ACTION, "receipt-1", NOW));
        }
        var otherIssuer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, "alice", "https://other-idp.example/21");
        assertThrows(IllegalArgumentException.class, () -> new Request(run(otherIssuer, RUN.runId()), LEASE, TOOL, ACTION, "receipt-1", NOW));
    }

    @Test void receipts_are_canonical_bounded_text_without_a_uuid_requirement() {
        assertNotNull(request("receipt-" + "x".repeat(247)));
        for (String value : List.of("", " ", " receipt-1", "receipt-1 ", "line\nbreak", "x".repeat(256), "\ud800")) {
            assertThrows(IllegalArgumentException.class, () -> request(value));
        }
    }

    @Test void observation_preserves_the_same_source_namespace_vector_and_the_shorter_live_boundary() {
        var observation = observation(CONSUMER, BINDING, PROOF, NOW.plusSeconds(10));
        assertEquals(7, ApprovedObservation.class.getRecordComponents().length); assertEquals(21, observation.tenantId());
        assertEquals("84b58abb8fb1a273a71a2cec394cce11cc9b8015b2ac47d7168109c908b4d605", observation.sourceNamespaceSha256());
        assertEquals(NOW.plusSeconds(10), observation.expiresAt());
        assertEquals(PROOF.expiresAt(), observation(CONSUMER, BINDING, PROOF, NOW.plusSeconds(25)).expiresAt());
        assertSame(PROOF, observation.approval()); assertSame(BINDING, observation.binding());
    }

    @Test void namespace_excludes_consumer_policy_workflow_and_tool_but_includes_the_actual_source() {
        var original = observation(CONSUMER, BINDING, PROOF, NOW.plusSeconds(10));
        var otherConsumer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "second-consumer", ISSUER);
        var otherBinding = binding(otherConsumer, "another-policy", "release-2");
        var otherTool = tool(ToolPolicy.Risk.APPROVAL_REQUIRED, ProcessClient.START_RESOURCE_KEY, "instanceId", "another-policy");
        var otherAction = new ToolActionRequest(OWNER, otherTool, "invoice-1", "business-v1", Map.of("amount", 7));
        var changed = observation(otherConsumer, otherBinding, proof(otherBinding, otherAction, true), NOW.plusSeconds(10));
        assertEquals(original.sourceNamespaceSha256(), changed.sourceNamespaceSha256());
        assertNotEquals(original.sourceNamespaceSha256(), new ApprovedObservation("other-ps", ISSUER, RESOURCE, BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)).sourceNamespaceSha256());
        assertNotEquals(original.sourceNamespaceSha256(), new ApprovedObservation("ps-authority", ISSUER, RESOURCE + "/other", BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)).sourceNamespaceSha256());
        assertEquals(List.of("sourceId", "sourceIssuer", "sourceResource", "binding", "approval", "consumer", "consumerExpiresAt"),
                Arrays.stream(ApprovedObservation.class.getRecordComponents()).map(component -> component.getName()).toList());
    }

    @Test void observation_source_consumer_workflow_and_approved_structure_are_closed() {
        Object[] values = {"ps-authority", ISSUER, RESOURCE, BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)};
        for (int index = 0; index < values.length; index++) { var changed = values.clone(); changed[index] = null; assertThrows(IllegalArgumentException.class, () -> constructObservation(changed)); }
        for (String id : List.of("", " bad", "1invalid", "x".repeat(129), "line\nbreak")) assertThrows(IllegalArgumentException.class,
                () -> new ApprovedObservation(id, ISSUER, RESOURCE, BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)));
        for (String uri : List.of("http://idp.example/21", "https://name@idp.example/21", ISSUER + "?query=private", ISSUER + "#fragment", "https://idp.example/a/../21", "https://idp.example/私有")) {
            assertThrows(IllegalArgumentException.class, () -> new ApprovedObservation("ps-authority", uri, RESOURCE, BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)));
            assertThrows(IllegalArgumentException.class, () -> new ApprovedObservation("ps-authority", ISSUER, uri, BINDING, PROOF, CONSUMER, NOW.plusSeconds(10)));
        }
        assertThrows(IllegalArgumentException.class, () -> observation(OWNER, BINDING, PROOF, NOW.plusSeconds(10)));
        var other = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "other-consumer", ISSUER);
        assertThrows(IllegalArgumentException.class, () -> observation(other, BINDING, PROOF, NOW.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> observation(CONSUMER, BINDING, proof(BINDING, ACTION, false), NOW.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> observation(CONSUMER, BINDING, PROOF, NOW));
        assertThrows(IllegalArgumentException.class, () -> observation(CONSUMER, binding(CONSUMER, "approval-policy", "other-release"), PROOF, NOW.plusSeconds(10)));
    }

    @Test void values_and_failures_expose_only_safe_diagnostics() {
        assertEquals("ToolApprovalVerificationRequest[redacted]", request("private-receipt").toString());
        assertEquals("ApprovedToolObservation[redacted]", observation(CONSUMER, BINDING, PROOF, NOW.plusSeconds(10)).toString());
        assertEquals(6, FailureKind.values().length);
        for (var kind : FailureKind.values()) {
            var failure = new Failure(kind); assertEquals(kind, failure.kind()); assertEquals(kind.name(), failure.getMessage()); assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private"));
        }
        assertEquals(1, Callback.class.getDeclaredMethods().length);
        assertArrayEquals(new Class<?>[]{ApprovedObservation.class}, Callback.class.getDeclaredMethods()[0].getParameterTypes());
    }

    private static Request construct(Object[] values) { return new Request((AgentRunAuthorization.Request) values[0], (AgentRunLease) values[1], (ToolDescriptor) values[2], (ToolActionRequest) values[3], (String) values[4], (Instant) values[5]); }
    private static ApprovedObservation constructObservation(Object[] values) { return new ApprovedObservation((String) values[0], (String) values[1], (String) values[2], (ToolApprovalBinding) values[3], (VerifiedApprovalDTO) values[4], (TrustedAgentActor) values[5], (Instant) values[6]); }
    private static Request request(String receipt) { return new Request(RUN, LEASE, TOOL, ACTION, receipt, NOW.plusSeconds(5)); }
    private static ApprovedObservation observation(TrustedAgentActor consumer, ToolApprovalBinding binding, VerifiedApprovalDTO proof, Instant expires) { return new ApprovedObservation("ps-authority", ISSUER, RESOURCE, binding, proof, consumer, expires); }
    private static AgentRunAuthorization.Request run(TrustedAgentActor actor, String id) { return new AgentRunAuthorization.Request(actor, id, "assistant", 1, HASH, HASH, HASH, "policy-v1", false, NOW.plusSeconds(30)); }
    private static ToolDescriptor tool(ToolPolicy.Risk risk, String resource, String output, String approval) { return new ToolDescriptor("process.approved", 1, "approved_start", "Approval-required fixed START", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of(output, ToolSchema.ValueType.STRING), Set.of(output)), new ToolPolicy(risk, "expense.start", resource, "f".repeat(64), approval)); }
    private static ToolApprovalBinding binding(TrustedAgentActor consumer, String policy, String release) { return new ToolApprovalBinding(consumer, policy, "b".repeat(64), release, "approval-workflow", "c".repeat(64), "approve"); }
    private static VerifiedApprovalDTO proof(ToolApprovalBinding binding, ToolActionRequest action, boolean approved) {
        var query = binding.query(action, "receipt-1"); var start = query.sourceStart();
        return new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(),
                start.commandId(), "approval-instance", "approval-task", binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(),
                OrchestrationRequestDigests.start(start), approved, NOW.minusSeconds(1), NOW, NOW.plusSeconds(20));
    }
}
