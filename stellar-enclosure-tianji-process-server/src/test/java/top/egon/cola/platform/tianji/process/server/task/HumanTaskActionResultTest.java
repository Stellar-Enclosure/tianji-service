package top.egon.cola.platform.tianji.process.server.task;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService.Definition;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTask;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskKind;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.HumanTaskField;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HumanTaskActionResultTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");
    private static final ProcessActor REQUESTER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "applicant", "https://idp.example/20");
    private static final String HASH = "a".repeat(64), AFTER = "b".repeat(64);
    private static final TaskOrigin ORIGIN = new TaskOrigin(REQUESTER, "start", HASH, "release", "correlation", "instance", NOW);
    private static final Definition DEFINITION = new Definition(ValidatedProcessModel.Type.BPMN, "workflow", "definition", "deployment", 1, HASH, Map.of());

    @Test void form_names_map_only_to_released_targets_and_all_fields_are_typed_before_native_completion() {
        var policy = policy(HumanTaskKind.APPROVAL);
        assertEquals(Map.of("reviewApproved", false, "reviewComment", "private form"), HumanTaskActionResult.formTargets(policy, Map.of("approved", false, "comment", "private form")));
        assertThrows(IllegalArgumentException.class, () -> HumanTaskActionResult.formTargets(policy, Map.of("reviewApproved", true, "reviewComment", "private form")));
        assertThrows(IllegalArgumentException.class, () -> HumanTaskActionResult.formTargets(policy, Map.of("approved", "true", "comment", "private form")));
        assertThrows(IllegalArgumentException.class, () -> HumanTaskActionResult.formTargets(policy, Map.of("approved", true)));
    }

    @Test void approval_decisions_are_explicit_and_bind_the_same_source_without_copying_the_form_into_the_reference() {
        var context = context("complete", Set.of("50")); var before = snapshot(HASH, "finance", context.userKey(), policy(HumanTaskKind.APPROVAL));
        var rejected = HumanTaskActionResult.applied(context, HumanTaskActionEnum.COMPLETE, "complete-1", HASH, before, null, null, Map.of("approved", false, "comment", "private form"), NOW);
        assertEquals(Boolean.FALSE, rejected.receipt().approved()); assertTrue(rejected.receipt().approvalReceiptId().matches("approval-[0-9a-f]{64}")); assertEquals("50", rejected.authorizedRoleId());
        assertEquals(ORIGIN, rejected.origin()); assertEquals(HASH, rejected.receipt().previousVersion()); assertNull(rejected.receipt().resultingVersion()); assertFalse(rejected.toString().contains("private form"));
        var replayShape = HumanTaskActionResult.applied(context, HumanTaskActionEnum.COMPLETE, "complete-1", HASH, before, null, null, Map.of("approved", false, "comment", "private form"), NOW.plusSeconds(1));
        assertEquals(rejected.receipt().approvalReceiptId(), replayShape.receipt().approvalReceiptId());
        var changed = HumanTaskActionResult.applied(context, HumanTaskActionEnum.COMPLETE, "complete-2", HASH, before, null, null, Map.of("approved", true, "comment", "private form"), NOW);
        assertNotEquals(rejected.receipt().approvalReceiptId(), changed.receipt().approvalReceiptId()); assertEquals(Boolean.TRUE, changed.receipt().approved());
    }

    @Test void claim_release_and_transfer_require_consistent_native_after_state_and_a_new_version() {
        var policy = policy(HumanTaskKind.APPROVAL); var claim = context("claim", Set.of("50")); var unassigned = snapshot(HASH, "finance", null, policy); var claimed = snapshot(AFTER, "finance", claim.userKey(), policy);
        assertEquals(AFTER, HumanTaskActionResult.applied(claim, HumanTaskActionEnum.CLAIM, "claim-1", HASH, unassigned, claimed, null, Map.of(), NOW).receipt().resultingVersion());
        assertThrows(IllegalArgumentException.class, () -> HumanTaskActionResult.applied(claim, HumanTaskActionEnum.CLAIM, "claim-1", HASH, unassigned, snapshot(HASH, "finance", claim.userKey(), policy), null, Map.of(), NOW));
        var unclaim = context("unclaim", Set.of()); var released = snapshot("c".repeat(64), "finance", null, policy);
        assertNull(HumanTaskActionResult.applied(unclaim, HumanTaskActionEnum.UNCLAIM, "unclaim-1", HASH, claimed, released, null, Map.of(), NOW).authorizedRoleId());
        var transfer = context("transfer", Set.of()); var manager = snapshot("c".repeat(64), "manager", null, policy);
        assertEquals("manager", HumanTaskActionResult.applied(transfer, HumanTaskActionEnum.TRANSFER, "transfer-1", HASH, claimed, manager, "manager", Map.of(), NOW).receipt().lane());
        assertThrows(IllegalArgumentException.class, () -> HumanTaskActionResult.applied(transfer, HumanTaskActionEnum.TRANSFER, "transfer-1", HASH, claimed, released, "manager", Map.of(), NOW));
    }

    @Test void ordinary_actions_cannot_mint_approval_and_an_applicant_cannot_bypass_separation_through_result_construction() {
        var context = context("complete", Set.of("50")); var action = snapshot(HASH, "finance", context.userKey(), policy(HumanTaskKind.ACTION));
        var result = HumanTaskActionResult.applied(context, HumanTaskActionEnum.COMPLETE, "complete-1", HASH, action, null, null, Map.of("approved", true, "comment", "form"), NOW);
        assertNull(result.receipt().approvalReceiptId()); assertNull(result.receipt().approved());
        var applicant = new HumanTaskContext(REQUESTER, "tianji.api.process.task.complete", Set.of("50"), NOW.plusSeconds(10), 2, 3);
        assertThrows(ProcessResourceNotFoundException.class, () -> HumanTaskActionResult.applied(applicant, HumanTaskActionEnum.COMPLETE, "complete-1", HASH,
                snapshot(HASH, "finance", applicant.userKey(), policy(HumanTaskKind.APPROVAL)), null, null, Map.of("approved", true, "comment", "form"), NOW));
    }

    private static HumanTaskContext context(String operation, Set<String> roles) { return new HumanTaskContext(REVIEWER, "tianji.api.process.task." + operation, roles, NOW.plusSeconds(30), 2, 3); }
    private static HumanTask policy(HumanTaskKind kind) { return new HumanTask(kind, Map.of("finance", Set.of("50"), "manager", Set.of("60")), "finance", null, kind == HumanTaskKind.APPROVAL ? "reviewApproved" : null,
            List.of(new HumanTaskField("approved", "reviewApproved", ValueType.BOOLEAN), new HumanTaskField("comment", "reviewComment", ValueType.STRING))); }
    private static HumanTaskSnapshot snapshot(String version, String lane, String assignee, HumanTask policy) {
        var snapshot = mock(HumanTaskSnapshot.class); when(snapshot.taskId()).thenReturn("task"); when(snapshot.taskDefinitionKey()).thenReturn("review"); when(snapshot.version()).thenReturn(version); when(snapshot.lane()).thenReturn(lane);
        when(snapshot.origin()).thenReturn(ORIGIN); when(snapshot.definition()).thenReturn(DEFINITION); when(snapshot.policy()).thenReturn(policy); when(snapshot.facts()).thenReturn(new HumanTaskAccessRules.Facts(REQUESTER, lane, assignee, false, false)); return snapshot;
    }
}
