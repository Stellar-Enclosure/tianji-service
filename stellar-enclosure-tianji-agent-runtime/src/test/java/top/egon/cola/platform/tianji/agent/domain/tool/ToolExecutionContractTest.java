package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    @Test void legacy_sam_is_called_once_and_returns_an_explicitly_unrecorded_check() {
        var request = request(TrustedAgentActor.SubjectType.USER); var calls = new AtomicInteger();
        ToolExecutionAuthorization authority = (input, boundary) -> {
            assertSame(request, input); assertEquals(ToolExecutionAuthorization.Boundary.PREPARE, boundary);
            calls.incrementAndGet(); return ToolExecutionAuthorization.Decision.ALLOW;
        };
        var result = authority.check(request, ToolExecutionAuthorization.Boundary.PREPARE);
        assertNotNull(result); assertEquals(1, calls.get()); assertSame(request, result.request());
        assertEquals(ToolExecutionAuthorization.Decision.ALLOW, result.decision()); assertNull(result.audit());
        assertEquals("ToolExecutionAuthorization.CheckResult[redacted]", result.toString());
    }

    @Test void typed_user_and_service_audits_cover_the_window_without_granting_authority_by_construction() {
        for (var type : TrustedAgentActor.SubjectType.values()) {
            var request = request(type); var audit = audit(type, ToolExecutionAuthorization.Boundary.DISPATCH, ToolExecutionAuthorization.Decision.ALLOW, request.deadlineAt());
            var result = new ToolExecutionAuthorization.CheckResult(request, audit.boundary(), audit.decision(), audit);
            assertEquals(audit, result.audit()); assertEquals(request.deadlineAt(), audit.expiresAt());
            assertEquals("ToolExecutionAuthorization.AuditVO[redacted]", audit.toString());
            if (type == TrustedAgentActor.SubjectType.USER) { assertEquals(0L, audit.authVersion()); assertNull(audit.catalogVersion()); }
            else { assertNull(audit.authVersion()); assertEquals(1L, audit.catalogVersion()); }
            assertEquals(0L, audit.policyVersion());
            assertDoesNotThrow(() -> new ToolExecutionAuthorization.CheckResult(request, audit.boundary(), audit.decision(),
                    audit(type, audit.boundary(), audit.decision(), request.run().deadlineAt())));
        }
        for (var decision : java.util.List.of(ToolExecutionAuthorization.Decision.DENY, ToolExecutionAuthorization.Decision.UNAVAILABLE)) {
            var audit = audit(TrustedAgentActor.SubjectType.USER, ToolExecutionAuthorization.Boundary.RECONCILE, decision, null);
            assertNull(audit.grantId()); assertNull(audit.expiresAt());
            assertDoesNotThrow(() -> new ToolExecutionAuthorization.CheckResult(request(TrustedAgentActor.SubjectType.USER), audit.boundary(), decision, audit));
        }
    }

    @Test void audit_schema_identity_namespace_and_upstream_version_slots_fail_closed() {
        var id = UUID.fromString("87654321-4321-4321-4321-cba987654321");
        var boundary = ToolExecutionAuthorization.Boundary.PREPARE; var allow = ToolExecutionAuthorization.Decision.ALLOW;
        var user = TrustedAgentActor.SubjectType.USER; var service = TrustedAgentActor.SubjectType.SERVICE;
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(2, id, boundary, allow, user, "9" + "1".repeat(62), 0L, null, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, null, boundary, allow, user, "9" + "1".repeat(62), 0L, null, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, user, "8" + "1".repeat(62), 0L, null, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, user, "9" + "1".repeat(62), -1L, null, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, user, "9" + "1".repeat(62), 0L, 1L, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, service, "8" + "1".repeat(62), null, 0L, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, service, "8" + "1".repeat(62), 0L, 1L, 0L, NOW, NOW.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, allow, service, "8" + "1".repeat(62), null, 1L, -1L, NOW, NOW.plusSeconds(20)));
        for (var decision : java.util.List.of(ToolExecutionAuthorization.Decision.DENY, ToolExecutionAuthorization.Decision.UNAVAILABLE)) {
            assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, decision, user, "9" + "1".repeat(62), null, null, null, NOW, null));
            assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, decision, user, null, 0L, null, null, NOW, null));
            assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, id, boundary, decision, user, null, null, null, null, NOW, NOW.plusSeconds(20)));
        }
    }

    @Test void recorded_checks_reject_mismatched_subject_boundary_decision_or_incomplete_expiry_coverage() {
        var request = request(TrustedAgentActor.SubjectType.USER); var boundary = ToolExecutionAuthorization.Boundary.DISPATCH; var allow = ToolExecutionAuthorization.Decision.ALLOW;
        var audit = audit(TrustedAgentActor.SubjectType.USER, boundary, allow, NOW.plusSeconds(20));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(null, boundary, allow, audit));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request, ToolExecutionAuthorization.Boundary.PREPARE, allow, audit));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request, boundary, ToolExecutionAuthorization.Decision.DENY, audit));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request(TrustedAgentActor.SubjectType.SERVICE), boundary, allow, audit));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request, boundary, allow, audit(TrustedAgentActor.SubjectType.USER, boundary, allow, request.deadlineAt().minusNanos(1))));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request, boundary, allow, audit(TrustedAgentActor.SubjectType.USER, boundary, allow, request.run().deadlineAt().plusNanos(1))));
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.AuditVO(1, audit.localCheckId(), boundary, allow, audit.subjectType(), audit.grantId(), 0L, null, 0L, NOW, NOW));
        var late = new ToolExecutionAuthorization.AuditVO(1, audit.localCheckId(), boundary, allow, audit.subjectType(), audit.grantId(), 0L, null, 0L, request.deadlineAt(), request.run().deadlineAt());
        assertThrows(IllegalArgumentException.class, () -> new ToolExecutionAuthorization.CheckResult(request, boundary, allow, late));
    }

    private static ToolExecutionAuthorization.Request request(TrustedAgentActor.SubjectType type) {
        var actor = TrustedAgentActor.verified(20, type, "requester", "https://idp.example/20");
        var run = new AgentRunAuthorization.Request(actor, "run", "agent", 1, SHA, SHA, SHA, "policy", false, NOW.plusSeconds(60));
        return new ToolExecutionAuthorization.Request(run, new AgentRunLease("run", "worker", 3, NOW.plusSeconds(30)), descriptor(ToolPolicy.Risk.IDEMPOTENT_WRITE).reference(), SHA, SHA, null, NOW.plusSeconds(20));
    }
    private static ToolExecutionAuthorization.AuditVO audit(TrustedAgentActor.SubjectType type, ToolExecutionAuthorization.Boundary boundary, ToolExecutionAuthorization.Decision decision, Instant expires) {
        boolean allow = decision == ToolExecutionAuthorization.Decision.ALLOW; boolean user = type == TrustedAgentActor.SubjectType.USER;
        return new ToolExecutionAuthorization.AuditVO(1, UUID.fromString("87654321-4321-4321-4321-cba987654321"), boundary, decision, type,
                allow ? (user ? "9" : "8") + "1".repeat(62) : null, allow && user ? 0L : null, allow && !user ? 1L : null, allow ? 0L : null, NOW, expires);
    }
    private static ToolDescriptor descriptor(ToolPolicy.Risk risk) { return new ToolDescriptor("operation", 1, "operation", "Fixed operation", EMPTY, OUTPUT,
            new ToolPolicy(risk, "operation", "resource.operation", SHA, risk == ToolPolicy.Risk.APPROVAL_REQUIRED ? "approval.v1" : null)); }
}
