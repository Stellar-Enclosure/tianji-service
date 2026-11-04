package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessControlContractTest {
    private static final String HASH = "a".repeat(64);
    private static final Instant SUBMITTED_AT = Instant.parse("2026-11-05T00:00:00.123456Z");
    private static final Instant FINISHED_AT = Instant.parse("2026-11-05T00:05:00.123456Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-11-05T00:06:00.123456Z");

    @Test
    void process_control_records_have_the_exact_components_and_types() {
        assertRecord(ProcessActiveWaitDTO.class,
                List.of("activityKey", "kind", "occurrences"),
                List.of(String.class, ProcessActiveWaitKindEnum.class, int.class));
        assertRecord(ProcessControlSnapshotDTO.class,
                List.of("process", "activeWaits", "allowedActions", "cancellationBlockReason", "version", "observedAt"),
                List.of(ProcessInstanceDTO.class, List.class, Set.class, ProcessControlBlockReasonEnum.class, String.class, Instant.class));
        assertRecord(CancelProcessCommand.class,
                List.of("instanceId", "commandId", "expectedVersion", "reason"),
                List.of(String.class, String.class, String.class, String.class));
        assertRecord(CancelProcessResult.class,
                List.of("instanceId", "commandId", "accepted", "status", "resultingVersion", "finishedAt", "decidedAt", "linkedAgentClosureScheduled"),
                List.of(String.class, String.class, boolean.class, ProcessInstanceStatusEnum.class, String.class, Instant.class, Instant.class, boolean.class));
    }

    @Test
    void process_control_enums_are_closed_to_the_approved_wire_names() {
        assertEquals(List.of("HUMAN_TASK", "EXTERNAL_WORKER", "AGENT_RESULT_WAIT", "CASE_COMPLETION_WAIT", "TIMER"),
                Arrays.stream(ProcessActiveWaitKindEnum.values()).map(Enum::name).toList());
        assertEquals(List.of("CANCEL"), Arrays.stream(ProcessControlActionEnum.values()).map(Enum::name).toList());
        assertEquals(List.of("NONE", "PROCESS_TERMINAL", "MODEL_CONTAINS_CASE_SOURCE"),
                Arrays.stream(ProcessControlBlockReasonEnum.values()).map(Enum::name).toList());
    }

    @Test
    void process_control_client_is_a_separate_exact_capability() throws NoSuchMethodException {
        assertTrue(ProcessControlClient.class.isInterface());
        assertFalse(ProcessClient.class.isAssignableFrom(ProcessControlClient.class));
        assertEquals(0, ProcessControlClient.class.getInterfaces().length);
        assertEquals("tianji:process:read", ProcessControlClient.READ_SCOPE);
        assertEquals("tianji:process:cancel", ProcessControlClient.CANCEL_SCOPE);
        assertEquals("tianji.api.process.instance.control", ProcessControlClient.CONTROL_RESOURCE_KEY);
        assertEquals("tianji.api.process.instance.cancel", ProcessControlClient.CANCEL_RESOURCE_KEY);
        assertEquals("/api/tianji/v1/process-instances/{instanceId}/control", ProcessControlClient.CONTROL_PATH);
        assertEquals("/api/tianji/v1/process-instances/{instanceId}/cancellations", ProcessControlClient.CANCEL_PATH);
        assertEquals(ProcessControlSnapshotDTO.class,
                ProcessControlClient.class.getDeclaredMethod("control", GetProcessInstanceQuery.class).getReturnType());
        assertEquals(CancelProcessResult.class,
                ProcessControlClient.class.getDeclaredMethod("cancel", CancelProcessCommand.class).getReturnType());
        assertEquals(2, ProcessControlClient.class.getDeclaredMethods().length);
    }

    @Test
    void existing_process_contract_remains_unchanged() {
        assertEquals("tianji:process:start", ProcessClient.START_SCOPE);
        assertEquals("tianji.api.process.instance.start", ProcessClient.START_RESOURCE_KEY);
        assertEquals(List.of(
                        "get(GetProcessInstanceQuery):ProcessInstanceDTO",
                        "start(StartProcessCommand):ProcessInstanceDTO"
                ), Arrays.stream(ProcessClient.class.getDeclaredMethods()).map(ProcessControlContractTest::signature).sorted().toList());
        assertRecord(ProcessInstanceDTO.class,
                List.of("instanceId", "definitionKey", "releaseVersion", "businessKey", "correlationId", "status", "submittedAt", "finishedAt"),
                List.of(String.class, String.class, String.class, String.class, String.class,
                        ProcessInstanceStatusEnum.class, Instant.class, Instant.class));
    }

    @Test
    void active_wait_accepts_only_valid_definition_keys_kinds_and_boundary_counts() {
        var one = new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1);
        var maximum = new ProcessActiveWaitDTO("deadline", ProcessActiveWaitKindEnum.TIMER, 256);
        assertEquals("review", one.activityKey());
        assertEquals(ProcessActiveWaitKindEnum.HUMAN_TASK, one.kind());
        assertEquals(1, one.occurrences());
        assertEquals(256, maximum.occurrences());
    }

    @Test
    void active_wait_rejects_null_invalid_and_out_of_range_values() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO(null, ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessActiveWaitDTO("review", null, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO(" review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO("bad" + (char) 0, ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO("bad" + (char) 0xd800, ProcessActiveWaitKindEnum.HUMAN_TASK, 1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 257))
        );
    }

    @Test
    void eligible_snapshot_defensively_copies_canonical_waits_and_actions() {
        var waits = new ArrayList<>(List.of(
                new ProcessActiveWaitDTO("agentWait", ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT, 1),
                new ProcessActiveWaitDTO("approve", ProcessActiveWaitKindEnum.HUMAN_TASK, 2),
                new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1)
        ));
        var actions = new LinkedHashSet<>(Set.of(ProcessControlActionEnum.CANCEL));
        var snapshot = new ProcessControlSnapshotDTO(activeProcess(), waits, actions,
                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT);

        assertNotSame(waits, snapshot.activeWaits());
        assertNotSame(actions, snapshot.allowedActions());
        waits.clear();
        actions.clear();
        assertEquals(3, snapshot.activeWaits().size());
        assertEquals(Set.of(ProcessControlActionEnum.CANCEL), snapshot.allowedActions());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.activeWaits().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.allowedActions().clear());
    }

    @Test
    void snapshot_rejects_null_objects_collections_and_collection_members() {
        var waitsWithNull = new ArrayList<ProcessActiveWaitDTO>();
        waitsWithNull.add(null);
        var actionsWithNull = new HashSet<ProcessControlActionEnum>();
        actionsWithNull.add(null);

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(null, List.of(), Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), null, Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), null,
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), waitsWithNull, Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), actionsWithNull,
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(ProcessControlActionEnum.CANCEL),
                                null, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.NONE, null, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.NONE, HASH, null))
        );
    }

    @Test
    void snapshot_requires_unique_waits_in_kind_name_then_activity_key_order() {
        var approve = new ProcessActiveWaitDTO("approve", ProcessActiveWaitKindEnum.HUMAN_TASK, 1);
        var review = new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1);
        var agent = new ProcessActiveWaitDTO("agentWait", ProcessActiveWaitKindEnum.AGENT_RESULT_WAIT, 1);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> eligibleSnapshot(List.of(review, approve))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> eligibleSnapshot(List.of(approve, agent))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> eligibleSnapshot(List.of(approve, approve)))
        );
    }

    @Test
    void snapshot_enforces_both_wait_list_and_aggregate_occurrence_caps() {
        var tooManyWaits = IntStream.range(0, 257)
                .mapToObj(index -> new ProcessActiveWaitDTO("task" + String.format("%03d", index),
                        ProcessActiveWaitKindEnum.HUMAN_TASK, 1))
                .toList();
        var tooManyOccurrences = List.of(
                new ProcessActiveWaitDTO("approve", ProcessActiveWaitKindEnum.HUMAN_TASK, 128),
                new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 129)
        );

        assertThrows(IllegalArgumentException.class, () -> eligibleSnapshot(tooManyWaits));
        assertThrows(IllegalArgumentException.class, () -> eligibleSnapshot(tooManyOccurrences));
        assertDoesNotThrow(() -> eligibleSnapshot(List.of(
                new ProcessActiveWaitDTO("approve", ProcessActiveWaitKindEnum.HUMAN_TASK, 128),
                new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 128)
        )));
    }

    @Test
    void snapshot_accepts_each_approved_lifecycle_action_and_block_combination() {
        assertAll(
                () -> assertDoesNotThrow(() -> eligibleSnapshot(List.of())),
                () -> assertDoesNotThrow(() -> new ProcessControlSnapshotDTO(suspendedProcess(), List.of(),
                        Set.of(ProcessControlActionEnum.CANCEL), ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertDoesNotThrow(() -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(),
                        ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE, HASH, DECIDED_AT)),
                () -> assertDoesNotThrow(() -> new ProcessControlSnapshotDTO(suspendedProcess(), List.of(), Set.of(),
                        ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE, HASH, DECIDED_AT)),
                () -> assertDoesNotThrow(() -> terminalSnapshot(ProcessInstanceStatusEnum.COMPLETED)),
                () -> assertDoesNotThrow(() -> terminalSnapshot(ProcessInstanceStatusEnum.CANCELLED))
        );
    }

    @Test
    void snapshot_rejects_cross_field_lifecycle_action_and_block_conflicts() {
        var wait = new ProcessActiveWaitDTO("review", ProcessActiveWaitKindEnum.HUMAN_TASK, 1);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(),
                                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(ProcessControlActionEnum.CANCEL),
                                ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(activeProcess(), List.of(), Set.of(),
                                ProcessControlBlockReasonEnum.PROCESS_TERMINAL, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(terminalProcess(ProcessInstanceStatusEnum.COMPLETED), List.of(wait), Set.of(),
                                ProcessControlBlockReasonEnum.PROCESS_TERMINAL, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(terminalProcess(ProcessInstanceStatusEnum.CANCELLED), List.of(),
                                Set.of(ProcessControlActionEnum.CANCEL), ProcessControlBlockReasonEnum.PROCESS_TERMINAL, HASH, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessControlSnapshotDTO(terminalProcess(ProcessInstanceStatusEnum.CANCELLED), List.of(), Set.of(),
                                ProcessControlBlockReasonEnum.MODEL_CONTAINS_CASE_SOURCE, HASH, DECIDED_AT))
        );
    }

    @Test
    void cancellation_command_accepts_exact_fields_and_safe_utf16_boundary_text() {
        String maximumReason = "😀".repeat(256);
        var command = new CancelProcessCommand("process-1", "cancel:command-1", HASH, maximumReason);
        assertEquals("process-1", command.instanceId());
        assertEquals("cancel:command-1", command.commandId());
        assertEquals(HASH, command.expectedVersion());
        assertEquals(512, command.reason().length());
    }

    @Test
    void cancellation_command_rejects_null_and_invalid_identifiers_or_version() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand(null, "command-1", HASH, "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", null, HASH, "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", null, "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand(" process-1", "command-1", HASH, "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "bad command", HASH, "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", "A".repeat(64), "reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", "a".repeat(63), "reason"))
        );
    }

    @Test
    void cancellation_command_rejects_blank_trimmed_control_malformed_and_oversized_reason() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "   ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, " reason")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "reason ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "bad" + (char) 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "bad" + (char) 0xd800)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "bad" + (char) 0xdc00)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessCommand("process-1", "command-1", HASH, "r".repeat(513)))
        );
    }

    @Test
    void cancellation_command_string_representation_redacts_the_audit_reason() {
        String reason = "private cancellation reason 9f3c";
        var command = new CancelProcessCommand("process-1", "command-1", HASH, reason);
        assertEquals("CancelProcessCommand[redacted]", command.toString());
        assertFalse(command.toString().contains(reason));
    }

    @Test
    void cancellation_result_accepts_applied_and_terminal_noop_outcomes() {
        var applied = new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                HASH, FINISHED_AT, DECIDED_AT, true);
        var completedNoop = new CancelProcessResult("process-1", "command-2", false, ProcessInstanceStatusEnum.COMPLETED,
                HASH, FINISHED_AT, DECIDED_AT, false);
        var cancelledNoop = new CancelProcessResult("process-1", "command-3", false, ProcessInstanceStatusEnum.CANCELLED,
                HASH, FINISHED_AT, DECIDED_AT, false);

        assertTrue(applied.accepted());
        assertTrue(applied.linkedAgentClosureScheduled());
        assertFalse(completedNoop.accepted());
        assertFalse(cancelledNoop.linkedAgentClosureScheduled());
    }

    @Test
    void cancellation_result_rejects_null_invalid_and_cross_field_outcomes() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult(null, "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", null, true, ProcessInstanceStatusEnum.CANCELLED,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, null,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                                null, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                                HASH, null, DECIDED_AT, false)),
                () -> assertThrows(NullPointerException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                                HASH, FINISHED_AT, null, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.COMPLETED,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", false, ProcessInstanceStatusEnum.CANCELLED,
                                HASH, FINISHED_AT, DECIDED_AT, true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", false, ProcessInstanceStatusEnum.ACTIVE,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", false, ProcessInstanceStatusEnum.SUSPENDED,
                                HASH, FINISHED_AT, DECIDED_AT, false)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                                "A".repeat(64), FINISHED_AT, DECIDED_AT, false))
        );
    }

    @Test
    void cancellation_result_preserves_both_clock_orders_and_nanosecond_precision() {
        Instant nanos = Instant.parse("2026-11-05T00:06:00.123456789Z");
        var nativeBeforeDecision = new CancelProcessResult("process-1", "command-1", true, ProcessInstanceStatusEnum.CANCELLED,
                HASH, FINISHED_AT, nanos, false);
        var nativeAfterDecision = new CancelProcessResult("process-1", "command-2", true, ProcessInstanceStatusEnum.CANCELLED,
                HASH, nanos, FINISHED_AT, false);

        assertEquals(FINISHED_AT, nativeBeforeDecision.finishedAt());
        assertEquals(nanos, nativeBeforeDecision.decidedAt());
        assertEquals(nanos, nativeAfterDecision.finishedAt());
        assertEquals(FINISHED_AT, nativeAfterDecision.decidedAt());
        assertEquals(123_456_789, nativeAfterDecision.finishedAt().getNano());
    }

    private static ProcessControlSnapshotDTO eligibleSnapshot(List<ProcessActiveWaitDTO> waits) {
        return new ProcessControlSnapshotDTO(activeProcess(), waits, Set.of(ProcessControlActionEnum.CANCEL),
                ProcessControlBlockReasonEnum.NONE, HASH, DECIDED_AT);
    }

    private static ProcessControlSnapshotDTO terminalSnapshot(ProcessInstanceStatusEnum status) {
        return new ProcessControlSnapshotDTO(terminalProcess(status), List.of(), Set.of(),
                ProcessControlBlockReasonEnum.PROCESS_TERMINAL, HASH, DECIDED_AT);
    }

    private static ProcessInstanceDTO activeProcess() {
        return process(ProcessInstanceStatusEnum.ACTIVE, null);
    }

    private static ProcessInstanceDTO suspendedProcess() {
        return process(ProcessInstanceStatusEnum.SUSPENDED, null);
    }

    private static ProcessInstanceDTO terminalProcess(ProcessInstanceStatusEnum status) {
        return process(status, FINISHED_AT);
    }

    private static ProcessInstanceDTO process(ProcessInstanceStatusEnum status, Instant finishedAt) {
        return new ProcessInstanceDTO("process-1", "invoiceReview", "release-7", "invoice-4711", "corr-4711",
                status, SUBMITTED_AT, finishedAt);
    }

    private static void assertRecord(Class<?> type, List<String> names, List<Class<?>> types) {
        assertTrue(type.isRecord());
        assertEquals(names, Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList());
        assertEquals(types, Arrays.stream(type.getRecordComponents()).map(component -> component.getType()).toList());
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(","));
        return method.getName() + "(" + parameters + "):" + method.getReturnType().getSimpleName();
    }
}
