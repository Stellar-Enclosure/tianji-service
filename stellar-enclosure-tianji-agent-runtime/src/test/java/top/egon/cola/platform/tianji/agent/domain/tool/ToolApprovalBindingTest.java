package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolApprovalBindingTest {
    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private final ToolApprovalBinding binding = new ToolApprovalBinding(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "consumer", "https://idp.example/20"), "approval.v1", SHA, "approval-release", "approval", SHA, "review");
    @Test void a_fixed_approval_command_contains_only_server_action_fingerprints_and_the_business_version() {
        var action = action(20, "7"); var source = binding.sourceStart(action);
        assertEquals(action.intentId(), source.commandId()); assertEquals(action.intentId(), source.correlationId()); assertEquals(action.businessKey(), source.businessKey());
        assertEquals(Map.of("actionIntentId", action.intentId(), "inputSha256", action.requestSha256(), "businessVersion", "7", "toolSha256", action.tool().contentSha256()), source.variables());
        assertFalse(source.variables().containsKey("approved")); assertFalse(source.variables().containsKey("tenantId")); assertFalse(source.variables().containsValue("private business argument"));
    }
    @Test void only_the_same_requester_input_object_version_and_pinned_model_can_use_a_fresh_true_projection() {
        var original = action(20, "7"); var proof = proof(original, true, "review");
        assertDoesNotThrow(() -> binding.requireApproved(original, proof, NOW));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action(21, "7"), proof, NOW));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action(20, "8"), proof, NOW));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(original, proof(original, true, "otherTask"), NOW));
        assertThrows(AgentLedgerException.class, () -> new ToolApprovalBinding(binding.consumer(), binding.policyRef(), SHA, binding.releaseVersion(), binding.definitionKey(), "b".repeat(64), "review").requireApproved(original, proof, NOW));
        var foreign = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "consumer", "https://idp.example/21");
        assertThrows(AgentLedgerException.class, () -> new ToolApprovalBinding(foreign, binding.policyRef(), SHA, binding.releaseVersion(), binding.definitionKey(), SHA, "review").sourceStart(original));
        var otherService = new ToolActionRequest(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "other-service", "https://idp.example/20"),
                descriptor(), "invoice-1", "7", Map.of("amount", 20L, "comment", "private business argument"));
        assertThrows(AgentLedgerException.class, () -> binding.sourceStart(otherService));
    }
    @Test void a_false_expired_or_future_projection_cannot_turn_a_reference_into_an_execution_approval() {
        var action = action(20, "7"); var proof = proof(action, true, "review");
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action, proof(action, false, "review"), NOW));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action, proof, NOW.plusSeconds(20)));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action, proof, NOW.minusSeconds(1)));
        assertThrows(AgentLedgerException.class, () -> binding.requireApproved(action, null, NOW));
    }
    private VerifiedApprovalDTO proof(ToolActionRequest action, boolean approved, String task) {
        var query = binding.query(action, "approval-ref"); var source = query.sourceStart();
        return new VerifiedApprovalDTO(binding.policyRef(), SHA, query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), source.commandId(),
                "instance", "task", source.releaseVersion(), source.definitionKey(), SHA, task, OrchestrationRequestDigests.start(source), approved, NOW.minusSeconds(1), NOW, NOW.plusSeconds(20));
    }
    private static ToolActionRequest action(long amount, String version) {
        return new ToolActionRequest(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "requester", "https://idp.example/20"), descriptor(), "invoice-1", version, Map.of("amount", amount, "comment", "private business argument"));
    }
    private static ToolDescriptor descriptor() { return new ToolDescriptor("process.start", 1, "start_process", "Fixed operation", new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "comment", ToolSchema.ValueType.STRING), Set.of("amount", "comment")),
            new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")), new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start.invoice", "tianji.api.process.instance.start", SHA, "approval.v1")); }
}
