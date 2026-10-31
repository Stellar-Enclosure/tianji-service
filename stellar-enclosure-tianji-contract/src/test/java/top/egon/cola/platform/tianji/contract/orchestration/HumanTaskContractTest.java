package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HumanTaskContractTest {
    private static final String BEFORE = "a".repeat(64), AFTER = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

    @Test void commands_fix_task_version_and_bounded_form_keys_without_accepting_identity_or_role_claims() {
        assertEquals(BEFORE, new ClaimHumanTaskCommand("claim-1", "task", BEFORE).expectedVersion());
        assertEquals("finance", new TransferHumanTaskCommand("transfer-1", "task", BEFORE, "finance").targetLane());
        assertThrows(IllegalArgumentException.class, () -> new ClaimHumanTaskCommand("claim-1", "task", "1"));
        assertThrows(IllegalArgumentException.class, () -> new UnclaimHumanTaskCommand("unclaim-1", "task", BEFORE.toUpperCase()));
        assertThrows(IllegalArgumentException.class, () -> new TransferHumanTaskCommand("transfer-1", "task", BEFORE, "${role}"));
        assertThrows(IllegalArgumentException.class, () -> new CompleteHumanTaskCommand("complete-1", "task", BEFORE, Map.of("form", Map.of("approved", true))));
        var values = new HashMap<String, Object>(); values.put("approved", true); values.put("comment", "private form");
        var command = new CompleteHumanTaskCommand("complete-1", "task", BEFORE, values); values.put("approved", false);
        assertEquals(true, command.form().get("approved")); assertThrows(UnsupportedOperationException.class, () -> command.form().clear()); assertFalse(command.toString().contains("private form"));
    }

    @Test void released_task_projection_has_a_finite_lane_form_and_assignment_shape() {
        var task = task(HumanTaskScopeTypeEnum.PROCESS, false, false);
        assertEquals("finance", task.lane()); assertThrows(UnsupportedOperationException.class, () -> task.availableLanes().clear()); assertThrows(UnsupportedOperationException.class, () -> task.formSchema().clear());
        assertThrows(IllegalArgumentException.class, () -> task(HumanTaskScopeTypeEnum.PROCESS, false, true));
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskDTO("task", HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "definition", "review", "Review", HumanTaskKindEnum.APPROVAL, "approved", BEFORE,
                "finance", List.of("finance", "finance"), false, false, false, Map.of("approved", ProcessValueTypeEnum.BOOLEAN), NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskDTO("task", HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "definition", "review", "Review", HumanTaskKindEnum.APPROVAL, "approved", BEFORE,
                "finance", List.of("finance"), false, false, false, Map.of("comment", ProcessValueTypeEnum.STRING), NOW, null));
    }

    @Test void bounded_selection_and_empty_continuation_do_not_grant_admin_or_cross_scope_visibility() {
        var query = new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, null, null, null, null, 50);
        assertEquals(50, query.limit()); assertThrows(IllegalArgumentException.class, () -> new ListHumanTasksQuery(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, null, null, null, null, 51));
        var empty = new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE, List.of(), "scanned"); assertEquals("scanned", empty.nextAfterTaskId());
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, List.of(task(HumanTaskScopeTypeEnum.CASE, false, false)), null));
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ASSIGNED, List.of(task(HumanTaskScopeTypeEnum.PROCESS, false, false)), null));
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.CANDIDATE, List.of(task(HumanTaskScopeTypeEnum.PROCESS, true, true)), null));
        var task = task(HumanTaskScopeTypeEnum.PROCESS, false, false);
        assertThrows(IllegalArgumentException.class, () -> new HumanTaskPageDTO(HumanTaskScopeTypeEnum.PROCESS, HumanTaskSelectionEnum.ALL, List.of(task, task), null));
    }

    @Test void approval_refs_are_paired_with_an_explicit_decision_and_never_inferred_from_an_action_or_a_claim() {
        var denied = receipt(HumanTaskActionEnum.COMPLETE, HumanTaskKindEnum.APPROVAL, null, "approval-ref", false); assertEquals(Boolean.FALSE, denied.approved());
        assertNull(receipt(HumanTaskActionEnum.COMPLETE, HumanTaskKindEnum.ACTION, null, null, null).approved());
        assertEquals(AFTER, receipt(HumanTaskActionEnum.CLAIM, HumanTaskKindEnum.APPROVAL, AFTER, null, null).resultingVersion());
        assertThrows(IllegalArgumentException.class, () -> receipt(HumanTaskActionEnum.COMPLETE, HumanTaskKindEnum.APPROVAL, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> receipt(HumanTaskActionEnum.COMPLETE, HumanTaskKindEnum.ACTION, null, "approval-ref", true));
        assertThrows(IllegalArgumentException.class, () -> receipt(HumanTaskActionEnum.CLAIM, HumanTaskKindEnum.APPROVAL, AFTER, "approval-ref", true));
        assertThrows(IllegalArgumentException.class, () -> receipt(HumanTaskActionEnum.COMPLETE, HumanTaskKindEnum.APPROVAL, AFTER, "approval-ref", true));
        assertThrows(IllegalArgumentException.class, () -> receipt(HumanTaskActionEnum.UNCLAIM, HumanTaskKindEnum.ACTION, null, null, null));
    }

    private static HumanTaskDTO task(HumanTaskScopeTypeEnum scope, boolean assigned, boolean mine) { return new HumanTaskDTO("task", scope, "instance", "release", "definition", "review", "Review", HumanTaskKindEnum.APPROVAL, "approved", BEFORE,
            "finance", List.of("finance", "manager"), assigned, mine, false, Map.of("approved", ProcessValueTypeEnum.BOOLEAN, "comment", ProcessValueTypeEnum.STRING), NOW, null); }
    private static HumanTaskActionReceiptDTO receipt(HumanTaskActionEnum action, HumanTaskKindEnum kind, String version, String reference, Boolean approved) { return new HumanTaskActionReceiptDTO("command-1", "task", action, HumanTaskScopeTypeEnum.PROCESS, "instance", "release", "definition", "review", kind, "finance",
            BEFORE, version, reference, approved, NOW); }
}
