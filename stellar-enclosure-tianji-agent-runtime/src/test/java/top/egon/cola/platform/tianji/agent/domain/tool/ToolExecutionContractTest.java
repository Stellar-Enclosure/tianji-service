package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionContractTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String SHA = "a".repeat(64);
    private static final ToolSchema EMPTY = new ToolSchema(Map.of(), Set.of()), OUTPUT = new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference"));
    @Test void independent_tool_authority_binds_the_original_run_lease_descriptor_arguments_and_deadline() {
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "requester", "https://idp.example/20");
        var run = new AgentRunAuthorization.Request(actor, "run", "agent", 1, SHA, SHA, SHA, "policy", false, NOW.plusSeconds(60));
        var lease = new AgentRunLease("run", "worker", 3, NOW.plusSeconds(30)); var tool = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE).reference();
        var request = new ToolExecutionAuthorization.Request(run, lease, tool, SHA, SHA, "12345678-1234-1234-1234-123456789abc", NOW.plusSeconds(20));
        assertEquals(3, request.lease().epoch()); assertFalse(request.toString().contains("requester"));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.Request(run, new AgentRunLease("other", "worker", 3, NOW.plusSeconds(30)), tool, SHA, SHA, null, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.Request(run, lease, tool, SHA, SHA, null, NOW.plusSeconds(61)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.Request(run, lease, tool, SHA, SHA, "model-call-id", NOW.plusSeconds(20)));
    }
    @Test void a_write_success_requires_a_real_receipt_and_typed_output_while_read_success_cannot_claim_a_business_effect() {
        var write = descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE); var read = descriptor(ToolPolicy.Risk.READ_ONLY);
        assertEquals("receipt", new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private reference"), "receipt", null, BigDecimal.ZERO).validated(write).businessReceiptId());
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private reference"), null, null, BigDecimal.ZERO).validated(write));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private reference"), "receipt", null, BigDecimal.ZERO).validated(read));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", 42), null, null, BigDecimal.ZERO).validated(read));
        assertFalse(new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private reference"), null, null, BigDecimal.ZERO).toString().contains("private reference"));
    }
    @Test void unknown_rejection_and_approval_wait_are_distinct_and_never_invent_receipts_or_retry_authority() {
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        assertNull(unknown.businessReceiptId()); assertNull(unknown.actualCost());
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), "invented-receipt", "OUTCOME_UNKNOWN", null));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.REJECTED, Map.of(), null, null, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionOutcome(ToolExecutionOutcome.Status.FAILED_KNOWN, Map.of("private", "payload"), null, "FAILED", BigDecimal.ZERO));
        var wait = new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> wait.validated(descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE)));
        assertEquals(wait, wait.validated(descriptor(ToolPolicy.Risk.APPROVAL_REQUIRED)));
    }
    private static ToolDescriptor descriptor(ToolPolicy.Risk risk) { return new ToolDescriptor("operation", 1, "operation", "Fixed operation", EMPTY, OUTPUT,
            new ToolPolicy(risk, "operation", "resource.operation", SHA, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval.v1" : null)); }
}
