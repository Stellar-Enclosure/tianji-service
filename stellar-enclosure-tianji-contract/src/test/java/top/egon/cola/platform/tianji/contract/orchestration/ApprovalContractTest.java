package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalContractTest {
    private static final String SHA = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test void the_published_v1_start_fingerprint_preserves_the_independent_utf8_boolean_long_vector() {
        var command = new StartProcessCommand("workflow", "release", "invoice-1", "start", "correlation", Map.of("amount", 20, "approved", false, "note", "审阅"));
        assertEquals("8d7cfd57d61d948aa9e68641caa29c8c3e443b16c903b129018e668aa84afe98", OrchestrationRequestDigests.start(command));
        assertEquals(OrchestrationRequestDigests.start(command), OrchestrationRequestDigests.hash(List.of("release", "workflow", "invoice-1", "correlation"), Map.of("note", "审阅", "approved", false, "amount", 20L)));
        assertThrows(IllegalArgumentException.class, () -> OrchestrationRequestDigests.hash(List.of("field"), Map.of("number", 20)));
    }

    @Test void provenance_query_fixes_the_requester_policy_reference_and_complete_start_input_without_exposing_arguments_in_diagnostics() {
        var command = new StartProcessCommand("approval", "release", "business", "start", "correlation", Map.of("inputSha256", SHA, "objectVersion", "7", "note", "private input"));
        var query = new VerifyApprovalQuery("approval:v1", SHA, "approval-ref", ApprovalRequesterTypeEnum.USER, "requester", command);
        assertFalse(query.toString().contains("private input")); assertTrue(query.fingerprint().matches("[0-9a-f]{64}"));
        var differentCommand = new StartProcessCommand(command.definitionKey(), command.releaseVersion(), command.businessKey(), "different-start", command.correlationId(), command.variables());
        assertEquals(OrchestrationRequestDigests.start(command), OrchestrationRequestDigests.start(differentCommand));
        assertNotEquals(query.fingerprint(), new VerifyApprovalQuery(query.policyRef(), SHA, query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), differentCommand).fingerprint());
        assertNotEquals(query.fingerprint(), new VerifyApprovalQuery(query.policyRef(), SHA, query.approvalReceiptId(), ApprovalRequesterTypeEnum.SERVICE, query.requesterSubjectId(), command).fingerprint());
        var changedVersion = new StartProcessCommand(command.definitionKey(), command.releaseVersion(), command.businessKey(), command.commandId(), command.correlationId(), Map.of("inputSha256", SHA, "objectVersion", "8", "note", "private input"));
        assertNotEquals(query.fingerprint(), new VerifyApprovalQuery(query.policyRef(), SHA, query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(), changedVersion).fingerprint());
    }

    @Test void an_authentic_false_decision_remains_explicit_and_the_projection_has_a_short_live_window() {
        var rejected = result(false, NOW.minusSeconds(1), NOW, NOW.plusSeconds(30)); assertFalse(rejected.approved());
        assertTrue(result(true, NOW.minusSeconds(1), NOW, NOW.plusSeconds(10)).approved()); assertFalse(rejected.toString().contains("requester"));
        assertThrows(IllegalArgumentException.class, () -> result(true, NOW.plusSeconds(1), NOW, NOW.plusSeconds(10)));
        assertThrows(IllegalArgumentException.class, () -> result(true, NOW, NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> result(true, NOW, NOW, NOW.plusSeconds(31)));
    }
    private static VerifiedApprovalDTO result(boolean approved, Instant occurred, Instant verified, Instant expires) { return new VerifiedApprovalDTO("approval.v1", SHA, SHA, "approval-ref", ApprovalRequesterTypeEnum.USER,
            "requester", "start", "instance", "task", "release", "approval", SHA, "review", SHA, approved, occurred, verified, expires); }
}
