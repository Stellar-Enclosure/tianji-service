package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseContractTest {
    private static final Instant SUBMITTED = Instant.parse("2026-11-01T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-11-01T11:00:00Z");
    private static final String VERSION_A = "a".repeat(64);
    private static final String VERSION_B = "b".repeat(64);
    private static final Set<String> VALID_TRANSITIONS = Set.of(
            "DISABLE:ENABLED:DISABLED",
            "ENABLE:DISABLED:ENABLED",
            "START:ENABLED:ACTIVE",
            "TERMINATE:ACTIVE:TERMINATED");

    @Test
    void exposes_exactly_three_case_operations_closed_enums_and_no_authority_or_engine_fields() throws Exception {
        assertEquals(CaseInstanceDTO.class,
                CaseClient.class.getMethod("start", StartCaseCommand.class).getReturnType());
        assertEquals(CaseInstanceDTO.class,
                CaseClient.class.getMethod("get", CaseInstanceQuery.class).getReturnType());
        assertEquals(CaseOperationResult.class,
                CaseClient.class.getMethod("actOnPlanItem", ActOnPlanItemCommand.class).getReturnType());
        assertEquals(3, CaseClient.class.getDeclaredMethods().length);
        assertEquals(Set.of("start", "get", "actOnPlanItem"), Arrays.stream(CaseClient.class.getDeclaredMethods())
                .map(method -> method.getName()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        assertEquals(0, CaseClient.class.getDeclaredFields().length);

        assertArrayEquals(new CasePlanItemActionEnum[] {
                CasePlanItemActionEnum.DISABLE,
                CasePlanItemActionEnum.ENABLE,
                CasePlanItemActionEnum.START,
                CasePlanItemActionEnum.TERMINATE
        }, CasePlanItemActionEnum.values());
        assertArrayEquals(new CaseInstanceStatusEnum[] {
                CaseInstanceStatusEnum.ACTIVE,
                CaseInstanceStatusEnum.COMPLETED,
                CaseInstanceStatusEnum.TERMINATED
        }, CaseInstanceStatusEnum.values());
        assertArrayEquals(new CasePlanItemStateEnum[] {
                CasePlanItemStateEnum.AVAILABLE,
                CasePlanItemStateEnum.ENABLED,
                CasePlanItemStateEnum.DISABLED,
                CasePlanItemStateEnum.ACTIVE,
                CasePlanItemStateEnum.COMPLETED,
                CasePlanItemStateEnum.TERMINATED
        }, CasePlanItemStateEnum.values());
        assertArrayEquals(new CasePlanItemKindEnum[] {
                CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemKindEnum.MILESTONE
        }, CasePlanItemKindEnum.values());

        assertComponents(StartCaseCommand.class,
                "definitionKey", "releaseVersion", "businessKey", "commandId", "correlationId");
        assertComponents(CaseInstanceQuery.class, "caseInstanceId");
        assertComponents(ActOnPlanItemCommand.class,
                "commandId", "caseInstanceId", "planItemInstanceId", "expectedVersion", "action");
        assertComponents(CasePlanItemDTO.class,
                "planItemInstanceId", "planItemDefinitionKey", "kind", "state", "version", "availableActions");
        assertComponents(CaseInstanceDTO.class,
                "caseInstanceId", "definitionKey", "releaseVersion", "businessKey", "correlationId",
                "status", "submittedAt", "finishedAt", "planItems");
        assertComponents(CaseOperationResult.class,
                "commandId", "caseInstanceId", "planItemInstanceId", "planItemDefinitionKey", "action",
                "releaseVersion", "definitionKey", "previousState", "previousVersion", "resultingState",
                "resultingVersion", "occurredAt");

        Set<String> forbidden = Set.of("tenantId", "actorId", "userId", "roleIds", "assignee", "variables",
                "inputs", "form", "engineCommand", "engineDefinitionId", "engineDeploymentId", "nativeRevision");
        for (Class<?> type : List.of(StartCaseCommand.class, CaseInstanceQuery.class, ActOnPlanItemCommand.class,
                CasePlanItemDTO.class, CaseInstanceDTO.class, CaseOperationResult.class)) {
            Set<String> actual = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            assertTrue(java.util.Collections.disjoint(actual, forbidden), type.getName());
        }
    }

    @Test
    void commands_and_query_accept_current_primitive_boundaries() {
        String definition = "d" + "x".repeat(127);
        String release = "r" + "x".repeat(127);
        String business = "b".repeat(255);
        String command = "c" + "x".repeat(127);
        String correlation = "t" + "x".repeat(127);
        String caseId = "i".repeat(255);
        String itemId = "p".repeat(255);

        var start = new StartCaseCommand(definition, release, business, command, correlation);
        var query = new CaseInstanceQuery(caseId);
        var action = new ActOnPlanItemCommand(command, caseId, itemId, VERSION_A, CasePlanItemActionEnum.START);

        assertEquals(definition, start.definitionKey());
        assertEquals(release, start.releaseVersion());
        assertEquals(business, start.businessKey());
        assertEquals(caseId, query.caseInstanceId());
        assertEquals(itemId, action.planItemInstanceId());
        assertEquals(VERSION_A, action.expectedVersion());
    }

    @Test
    void commands_and_query_reject_invalid_primitive_values() {
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("1bad", "release", "business", "command", "correlation"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("case", "-release", "business", "command", "correlation"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("case", "release", " business", "command", "correlation"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("case", "release", "b".repeat(256), "command", "correlation"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("case", "release", "business", "-command", "correlation"));
        assertThrows(IllegalArgumentException.class,
                () -> new StartCaseCommand("case", "release", "business", "command", "bad value"));
        assertThrows(IllegalArgumentException.class, () -> new CaseInstanceQuery(""));
        assertThrows(IllegalArgumentException.class, () -> new CaseInstanceQuery("i".repeat(256)));
        assertThrows(IllegalArgumentException.class,
                () -> new ActOnPlanItemCommand("command", "case", "item", "A".repeat(64), CasePlanItemActionEnum.START));
        assertThrows(NullPointerException.class,
                () -> new ActOnPlanItemCommand("command", "case", "item", VERSION_A, null));
    }

    @Test
    void plan_item_accepts_only_state_compatible_action_subsets_and_no_milestone_actions() {
        assertDoesNotThrow(() -> item("available", "available", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of()));
        assertDoesNotThrow(() -> item("enabled-empty", "enabledEmpty", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, List.of()));
        assertDoesNotThrow(() -> item("enabled-start", "enabledStart", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, List.of(CasePlanItemActionEnum.START)));
        assertDoesNotThrow(() -> item("enabled-disable", "enabledDisable", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, List.of(CasePlanItemActionEnum.DISABLE)));
        assertDoesNotThrow(() -> item("enabled-both", "enabledBoth", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, List.of(CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START)));
        assertDoesNotThrow(() -> item("disabled-empty", "disabledEmpty", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.DISABLED, List.of()));
        assertDoesNotThrow(() -> item("disabled-enable", "disabledEnable", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.DISABLED, List.of(CasePlanItemActionEnum.ENABLE)));
        assertDoesNotThrow(() -> item("active-primary", "activePrimary", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ACTIVE, List.of()));
        assertDoesNotThrow(() -> item("active-supplemental", "activeSupplemental", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ACTIVE, List.of(CasePlanItemActionEnum.TERMINATE)));
        assertDoesNotThrow(() -> item("completed", "completed", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.COMPLETED, List.of()));
        assertDoesNotThrow(() -> item("terminated", "terminated", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.TERMINATED, List.of()));
        assertDoesNotThrow(() -> item("milestone", "milestone", CasePlanItemKindEnum.MILESTONE,
                CasePlanItemStateEnum.ACTIVE, List.of()));

        assertThrows(IllegalArgumentException.class, () -> item("available-enable", "availableEnable",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.AVAILABLE,
                List.of(CasePlanItemActionEnum.ENABLE)));
        assertThrows(IllegalArgumentException.class, () -> item("disabled-start", "disabledStart",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.DISABLED,
                List.of(CasePlanItemActionEnum.START)));
        assertThrows(IllegalArgumentException.class, () -> item("enabled-terminate", "enabledTerminate",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ENABLED,
                List.of(CasePlanItemActionEnum.TERMINATE)));
        assertThrows(IllegalArgumentException.class, () -> item("active-enable", "activeEnable",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ACTIVE,
                List.of(CasePlanItemActionEnum.ENABLE)));
        assertThrows(IllegalArgumentException.class, () -> item("completed-start", "completedStart",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.COMPLETED,
                List.of(CasePlanItemActionEnum.START)));
        assertThrows(IllegalArgumentException.class, () -> item("terminated-start", "terminatedStart",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.TERMINATED,
                List.of(CasePlanItemActionEnum.START)));
        assertThrows(IllegalArgumentException.class, () -> item("milestone-action", "milestoneAction",
                CasePlanItemKindEnum.MILESTONE, CasePlanItemStateEnum.ENABLED,
                List.of(CasePlanItemActionEnum.START)));
    }

    @Test
    void plan_item_defensively_copies_actions_and_rejects_duplicates_nulls_and_invalid_bounds() {
        var mutable = new ArrayList<>(List.of(CasePlanItemActionEnum.DISABLE));
        var item = item("copy", "copy", CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ENABLED, mutable);
        mutable.add(CasePlanItemActionEnum.START);
        assertEquals(List.of(CasePlanItemActionEnum.DISABLE), item.availableActions());
        assertThrows(UnsupportedOperationException.class,
                () -> item.availableActions().add(CasePlanItemActionEnum.START));

        assertThrows(IllegalArgumentException.class, () -> item("duplicate", "duplicate",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ENABLED,
                List.of(CasePlanItemActionEnum.START, CasePlanItemActionEnum.START)));
        assertThrows(IllegalArgumentException.class, () -> item("too-many", "tooMany",
                CasePlanItemKindEnum.HUMAN_TASK, CasePlanItemStateEnum.ENABLED,
                List.of(CasePlanItemActionEnum.START, CasePlanItemActionEnum.DISABLE,
                        CasePlanItemActionEnum.START, CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START)));
        assertThrows(NullPointerException.class,
                () -> new CasePlanItemDTO("item", "item", CasePlanItemKindEnum.HUMAN_TASK,
                        CasePlanItemStateEnum.ENABLED, VERSION_A, null));
        var withNull = new ArrayList<CasePlanItemActionEnum>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> new CasePlanItemDTO("item", "item", CasePlanItemKindEnum.HUMAN_TASK,
                        CasePlanItemStateEnum.ENABLED, VERSION_A, withNull));
        assertThrows(IllegalArgumentException.class,
                () -> new CasePlanItemDTO("item", "item", CasePlanItemKindEnum.HUMAN_TASK,
                        CasePlanItemStateEnum.ENABLED, "not-a-hash", List.of()));
    }

    @Test
    void case_instance_copies_items_rejects_duplicates_and_keeps_the_v1_bound() {
        var first = item("first", "first", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, List.of(CasePlanItemActionEnum.START));
        var mutable = new ArrayList<>(List.of(first));
        var instance = instance(CaseInstanceStatusEnum.ACTIVE, null, mutable);
        mutable.clear();
        assertEquals(List.of(first), instance.planItems());
        assertThrows(UnsupportedOperationException.class, () -> instance.planItems().clear());

        var duplicateId = item("first", "second", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.ACTIVE, null, List.of(first, duplicateId)));
        var duplicateKey = item("second", "first", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.ACTIVE, null, List.of(first, duplicateKey)));

        List<CasePlanItemDTO> sixteen = items(16);
        assertDoesNotThrow(() -> instance(CaseInstanceStatusEnum.ACTIVE, null, sixteen));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.ACTIVE, null, items(17)));
    }

    @Test
    void case_instance_status_controls_only_finished_time_and_terminal_action_visibility() {
        assertDoesNotThrow(() -> instance(CaseInstanceStatusEnum.ACTIVE, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.ACTIVE, FINISHED, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.COMPLETED, null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.TERMINATED, null, List.of()));

        var available = item("available-history", "availableHistory", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.AVAILABLE, List.of());
        var activeWithoutActions = item("active-history", "activeHistory", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ACTIVE, List.of());
        assertDoesNotThrow(() -> instance(CaseInstanceStatusEnum.COMPLETED, FINISHED, List.of()));
        assertDoesNotThrow(() -> instance(CaseInstanceStatusEnum.COMPLETED, FINISHED, List.of(available)));
        assertDoesNotThrow(() -> instance(CaseInstanceStatusEnum.TERMINATED, FINISHED, List.of(activeWithoutActions)));
        assertDoesNotThrow(() -> new CaseInstanceDTO("case", "formalCase", "release", "business", "correlation",
                CaseInstanceStatusEnum.COMPLETED, SUBMITTED, SUBMITTED.minusSeconds(1), List.of()));

        var activeAction = item("active-action", "activeAction", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ACTIVE, List.of(CasePlanItemActionEnum.TERMINATE));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.COMPLETED, FINISHED, List.of(activeAction)));
        assertThrows(IllegalArgumentException.class,
                () -> instance(CaseInstanceStatusEnum.TERMINATED, FINISHED, List.of(activeAction)));
    }

    @Test
    void operation_result_accepts_exactly_the_four_historical_action_transitions() {
        assertDoesNotThrow(() -> result(CasePlanItemActionEnum.DISABLE,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.DISABLED, VERSION_A, VERSION_B));
        assertDoesNotThrow(() -> result(CasePlanItemActionEnum.ENABLE,
                CasePlanItemStateEnum.DISABLED, CasePlanItemStateEnum.ENABLED, VERSION_A, VERSION_B));
        assertDoesNotThrow(() -> result(CasePlanItemActionEnum.START,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE, VERSION_A, VERSION_B));
        assertDoesNotThrow(() -> result(CasePlanItemActionEnum.TERMINATE,
                CasePlanItemStateEnum.ACTIVE, CasePlanItemStateEnum.TERMINATED, VERSION_A, VERSION_B));
    }

    @Test
    void operation_result_rejects_every_other_action_state_pair() {
        int rejected = 0;
        for (CasePlanItemActionEnum action : CasePlanItemActionEnum.values()) {
            for (CasePlanItemStateEnum previous : CasePlanItemStateEnum.values()) {
                for (CasePlanItemStateEnum resulting : CasePlanItemStateEnum.values()) {
                    String transition = action + ":" + previous + ":" + resulting;
                    if (VALID_TRANSITIONS.contains(transition)) continue;
                    assertThrows(IllegalArgumentException.class,
                            () -> result(action, previous, resulting, VERSION_A, VERSION_B),
                            transition);
                    rejected++;
                }
            }
        }
        assertEquals(140, rejected);
    }

    @Test
    void operation_result_rejects_same_version_and_invalid_required_values() {
        assertThrows(IllegalArgumentException.class, () -> result(CasePlanItemActionEnum.START,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE, VERSION_A, VERSION_A));
        assertThrows(NullPointerException.class, () -> new CaseOperationResult(
                "command", "case", "item", "item", null, "release", "formalCase",
                CasePlanItemStateEnum.ENABLED, VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED));
        assertThrows(NullPointerException.class, () -> new CaseOperationResult(
                "command", "case", "item", "item", CasePlanItemActionEnum.START, "release", "formalCase",
                null, VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED));
        assertThrows(NullPointerException.class, () -> new CaseOperationResult(
                "command", "case", "item", "item", CasePlanItemActionEnum.START, "release", "formalCase",
                CasePlanItemStateEnum.ENABLED, VERSION_A, null, VERSION_B, FINISHED));
        assertThrows(NullPointerException.class, () -> new CaseOperationResult(
                "command", "case", "item", "item", CasePlanItemActionEnum.START, "release", "formalCase",
                CasePlanItemStateEnum.ENABLED, VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, null));
        assertThrows(IllegalArgumentException.class, () -> new CaseOperationResult(
                "bad command", "case", "item", "item", CasePlanItemActionEnum.START, "release", "formalCase",
                CasePlanItemStateEnum.ENABLED, VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED));
    }

    @Test
    void mutation_and_result_to_string_do_not_expose_sensitive_command_business_or_version_values() {
        var command = new ActOnPlanItemCommand("secret-command", "case-1", "item-1", VERSION_A,
                CasePlanItemActionEnum.START);
        assertFalse(command.toString().contains("secret-command"));
        assertFalse(command.toString().contains(VERSION_A));

        var start = new StartCaseCommand("formalCase", "release-1", "secret-business",
                "secret-start-command", "correlation-1");
        assertFalse(start.toString().contains("secret-business"));
        assertFalse(start.toString().contains("secret-start-command"));

        var result = result(CasePlanItemActionEnum.START,
                CasePlanItemStateEnum.ENABLED, CasePlanItemStateEnum.ACTIVE, VERSION_A, VERSION_B);
        assertFalse(result.toString().contains(VERSION_A));
        assertFalse(result.toString().contains(VERSION_B));
    }

    private static CasePlanItemDTO item(String id, String key, CasePlanItemKindEnum kind,
                                        CasePlanItemStateEnum state, List<CasePlanItemActionEnum> actions) {
        return new CasePlanItemDTO(id, key, kind, state, VERSION_A, actions);
    }

    private static CaseInstanceDTO instance(CaseInstanceStatusEnum status, Instant finished,
                                            List<CasePlanItemDTO> items) {
        return new CaseInstanceDTO("case-1", "formalCase", "release-1", "business-1", "correlation-1",
                status, SUBMITTED, finished, items);
    }

    private static CaseOperationResult result(CasePlanItemActionEnum action, CasePlanItemStateEnum previous,
                                              CasePlanItemStateEnum resulting, String previousVersion,
                                              String resultingVersion) {
        return new CaseOperationResult("command-1", "case-1", "item-1", "primary", action,
                "release-1", "formalCase", previous, previousVersion, resulting, resultingVersion, FINISHED);
    }

    private static void assertComponents(Class<?> type, String... expected) {
        assertTrue(type.isRecord(), type.getName());
        assertEquals(List.of(expected), Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName).toList());
    }

    private static List<CasePlanItemDTO> items(int count) {
        var values = new ArrayList<CasePlanItemDTO>();
        for (int i = 0; i < count; i++) {
            values.add(item("item-" + i, "item" + i, CasePlanItemKindEnum.HUMAN_TASK,
                    CasePlanItemStateEnum.AVAILABLE, List.of()));
        }
        return List.copyOf(values);
    }
}
