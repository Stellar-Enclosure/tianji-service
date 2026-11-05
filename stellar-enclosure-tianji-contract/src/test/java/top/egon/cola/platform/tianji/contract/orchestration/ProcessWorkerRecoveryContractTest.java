package top.egon.cola.platform.tianji.contract.orchestration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReasonEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.ReportedFailureEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum;

import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.HISTORICAL_FAILURE;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.MANUAL_REVIEW;
import static top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO.StatusEnum.RETRY_AVAILABLE;
import static top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED;

class ProcessWorkerRecoveryContractTest {
    private static final String INSTANCE_ID = "process-1";
    private static final String JOB_ID = "job-1";
    private static final String COMMAND_ID = "recover:command-1";
    private static final String VERSION = "0123456789abcdef".repeat(4);
    private static final Instant OBSERVED_AT = Instant.parse("2026-11-05T05:50:00.000001Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-11-05T05:50:01.000001Z");

    @Test
    void recovery_capability_and_records_have_exact_shape() {
        var capability = contractType("ProcessWorkerRecoveryClient");
        var command = contractType("RecoverProcessWorkerCommand");
        var row = contractType("ProcessWorkerFailureDTO");
        var rows = contractType("ProcessWorkerFailuresDTO");
        var result = contractType("RecoverProcessWorkerResult");
        var rowStatus = contractType("ProcessWorkerFailureDTO$StatusEnum");
        var reason = contractType("ProcessWorkerFailureDTO$ReasonEnum");
        var reported = contractType("ProcessWorkerFailureDTO$ReportedFailureEnum");
        var resultStatus = contractType("RecoverProcessWorkerResult$StatusEnum");

        assertTrue(capability.isInterface());
        assertEquals(0, capability.getInterfaces().length);
        assertFalse(ProcessControlClient.class.isAssignableFrom(capability));
        assertFalse(ProcessClient.class.isAssignableFrom(capability));
        assertEquals(2, capability.getDeclaredMethods().length);
        var queryMethod = assertDoesNotThrow(() ->
                capability.getDeclaredMethod("workerFailures", GetProcessInstanceQuery.class));
        var commandMethod = assertDoesNotThrow(() -> capability.getDeclaredMethod("recoverWorker", command));
        assertEquals(rows, queryMethod.getReturnType());
        assertEquals(result, commandMethod.getReturnType());
        assertTrue(Modifier.isAbstract(queryMethod.getModifiers()));
        assertTrue(Modifier.isAbstract(commandMethod.getModifiers()));
        assertEquals(5, capability.getDeclaredFields().length);
        assertConstant(capability, "WORKER_FAILURES_PATH", "/api/tianji/v1/process-instances/{instanceId}/worker-failures");
        assertConstant(capability, "WORKER_RECOVERIES_PATH", "/api/tianji/v1/process-instances/{instanceId}/worker-recoveries");
        assertConstant(capability, "WORKER_FAILURES_RESOURCE_KEY", "tianji.api.process.instance.worker-failures");
        assertConstant(capability, "WORKER_RECOVERIES_RESOURCE_KEY", "tianji.api.process.instance.worker-recoveries");
        assertConstant(capability, "WORKER_RECOVER_SCOPE", "tianji:process:worker-recover");
        assertRecord(command, List.of("instanceId", "jobId", "commandId", "expectedVersion", "reason"),
                List.of(String.class, String.class, String.class, String.class, String.class));
        assertRecord(row,
                List.of("jobId", "activityId", "nativeDeadletter", "status", "reason", "reportedFailure", "expectedVersion"),
                List.of(String.class, String.class, boolean.class, rowStatus, reason, reported, String.class));
        assertRecord(rows, List.of("instanceId", "observedAt", "failures"), List.of(String.class, Instant.class, List.class));
        assertRecord(result, List.of("instanceId", "jobId", "occurrenceId", "commandId", "status", "retries", "decidedAt"),
                List.of(String.class, String.class, String.class, String.class, resultStatus, int.class, Instant.class));

        assertEnum(rowStatus, List.of("RETRY_AVAILABLE", "MANUAL_REVIEW", "HISTORICAL_FAILURE"));
        assertEnum(reason, List.of("NONE", "NO_NATIVE_DEADLETTER", "UNKNOWN_OUTCOME", "BUSINESS_REJECTED",
                "RESTORE_LIMIT_REACHED", "UNSUPPORTED_NATIVE_JOB", "PROCESS_INACTIVE", "EVIDENCE_CONFLICT",
                "ACTIVE_LEASE", "BINDING_UNVERIFIED", "HISTORY_INCOMPLETE", "UNSUPPORTED_EFFECT"));
        assertEnum(reported, List.of("NONE", "RETRYABLE", "BUSINESS_REJECTED", "UNKNOWN", "UNVERIFIED"));
        assertEnum(resultStatus, List.of("RETRY_SCHEDULED"));
    }

    private static void assertConstant(Class<?> type, String name, String value) {
        assertEquals(value, assertDoesNotThrow(() -> type.getDeclaredField(name).get(null)));
    }

    private static Class<?> contractType(String name) {
        return assertDoesNotThrow(() -> Class.forName(ProcessWorkerRecoveryContractTest.class.getPackageName() + "." + name));
    }

    private static void assertRecord(Class<?> type, List<String> names, List<Class<?>> types) {
        assertTrue(type.isRecord());
        var components = Arrays.stream(type.getRecordComponents()).toList();
        assertEquals(names, components.stream().map(RecordComponent::getName).toList());
        assertEquals(types, components.stream().map(RecordComponent::getType).toList());
    }

    private static void assertEnum(Class<?> type, List<String> values) {
        assertTrue(type.isEnum());
        assertEquals(values, Arrays.stream(type.getEnumConstants()).map(value -> ((Enum<?>) value).name()).toList());
    }

    @Test
    void command_rejects_invalid_fields_and_preserves_exact_safe_reason() {
        String exactReason = "核对\u0085 Read-Only  e\u0301 😀";
        var command = assertDoesNotThrow(() ->
                new RecoverProcessWorkerCommand(INSTANCE_ID, JOB_ID, COMMAND_ID, VERSION, exactReason));
        assertNotNull(command);
        assertEquals(INSTANCE_ID, command.instanceId());
        assertEquals(JOB_ID, command.jobId());
        assertEquals(COMMAND_ID, command.commandId());
        assertEquals(VERSION, command.expectedVersion());
        assertEquals(exactReason, command.reason());
        assertEquals("RecoverProcessWorkerCommand[redacted]", command.toString());

        String maximumId = "中" + "😀".repeat(127);
        String maximumCommandId = "A" + "b_.:-".repeat(25) + "12";
        String maximumReason = "😀".repeat(256);
        var maximum = assertDoesNotThrow(() ->
                new RecoverProcessWorkerCommand(maximumId, maximumId, maximumCommandId, VERSION, maximumReason));
        assertNotNull(maximum);
        assertEquals(maximumId, maximum.instanceId());
        assertEquals(maximumId, maximum.jobId());
        assertEquals(maximumCommandId, maximum.commandId());
        assertEquals(maximumReason, maximum.reason());

        assertAll(
                () -> assertAll("instanceId", Arrays.asList(null, "", ".", "..", " process", maximumId + "x")
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new RecoverProcessWorkerCommand(invalid, JOB_ID, COMMAND_ID, VERSION, exactReason)))),
                () -> assertAll("jobId", Arrays.asList(null, " ", "job ", "j" + (char) 127 + "ob", maximumId + "x")
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new RecoverProcessWorkerCommand(INSTANCE_ID, invalid, COMMAND_ID, VERSION, exactReason)))),
                () -> assertAll("commandId", Arrays.asList(null, "", "-command", "bad command", "恢复", maximumCommandId + "x")
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new RecoverProcessWorkerCommand(INSTANCE_ID, JOB_ID, invalid, VERSION, exactReason)))),
                () -> assertAll("expectedVersion", Arrays.asList(null, "A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65))
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new RecoverProcessWorkerCommand(INSTANCE_ID, JOB_ID, COMMAND_ID, invalid, exactReason)))),
                () -> assertAll("reason", Arrays.asList(null, "", "   ", " reason", "reason ",
                                "bad" + (char) 0 + "reason", "bad\nreason", "bad" + (char) 127 + "reason",
                                "bad" + (char) 0xd800 + "reason", "bad" + (char) 0xdc00 + "reason", maximumReason + "x")
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new RecoverProcessWorkerCommand(INSTANCE_ID, JOB_ID, COMMAND_ID, VERSION, invalid)))),
                () -> {
                    String privateReason = " private recovery reason";
                    var failure = assertThrows(IllegalArgumentException.class,
                            () -> new RecoverProcessWorkerCommand(INSTANCE_ID, JOB_ID, COMMAND_ID, VERSION, privateReason));
                    assertFalse(String.valueOf(failure.getMessage()).contains(privateReason.strip()));
                }
        );
    }

    @Test
    void failure_row_enforces_closed_dispositions_and_safe_activity() {
        String maximumActivityId = "😀".repeat(64);
        String maximumJobId = "中" + "😀".repeat(127);
        var eligible = assertDoesNotThrow(() -> new ProcessWorkerFailureDTO(maximumJobId, maximumActivityId,
                true, RETRY_AVAILABLE, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION));
        assertNotNull(eligible);
        assertEquals(maximumJobId, eligible.jobId());
        assertEquals(maximumActivityId, eligible.activityId());
        assertTrue(eligible.nativeDeadletter());
        assertEquals(RETRY_AVAILABLE, eligible.status());
        assertEquals(ReasonEnum.NONE, eligible.reason());
        assertEquals(ReportedFailureEnum.RETRYABLE, eligible.reportedFailure());
        assertEquals(VERSION, eligible.expectedVersion());

        assertAll(
                () -> assertAll("status reason membership", Arrays.stream(ReasonEnum.values())
                        .<Executable>map(reason -> () -> assertDispositionReason(reason))),
                () -> assertAll("activityId", List.of("", " ", " act", "act ", "a\nb", "a" + (char) 127 + "b",
                                "a" + (char) 0xd800, "a" + (char) 0xdc00, maximumActivityId + "x")
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new ProcessWorkerFailureDTO(JOB_ID, invalid, true, MANUAL_REVIEW,
                                        ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.UNVERIFIED, null)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(null, "act", true,
                        RETRY_AVAILABLE, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(maximumJobId + "x", "act", true,
                        RETRY_AVAILABLE, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertThrows(NullPointerException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, "act", true,
                        null, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertThrows(NullPointerException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, "act", true,
                        RETRY_AVAILABLE, null, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertThrows(NullPointerException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, "act", true,
                        RETRY_AVAILABLE, ReasonEnum.NONE, null, VERSION)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, null, true,
                        RETRY_AVAILABLE, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, "act", false,
                        RETRY_AVAILABLE, ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, VERSION)),
                () -> assertAll("retry version", Arrays.asList(null, "A".repeat(64), "a".repeat(63))
                        .stream().<Executable>map(invalid -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new ProcessWorkerFailureDTO(JOB_ID, "act", true, RETRY_AVAILABLE,
                                        ReasonEnum.NONE, ReportedFailureEnum.RETRYABLE, invalid)))),
                () -> assertAll("retry failure classification", Arrays.stream(ReportedFailureEnum.values())
                        .filter(reported -> reported != ReportedFailureEnum.RETRYABLE)
                        .<Executable>map(reported -> () -> assertThrows(IllegalArgumentException.class,
                                () -> new ProcessWorkerFailureDTO(JOB_ID, "act", true, RETRY_AVAILABLE,
                                        ReasonEnum.NONE, reported, VERSION)))),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, null, false,
                        MANUAL_REVIEW, ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.UNVERIFIED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, null, true,
                        MANUAL_REVIEW, ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.UNVERIFIED, VERSION)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, null, true,
                        HISTORICAL_FAILURE, ReasonEnum.NO_NATIVE_DEADLETTER, ReportedFailureEnum.UNVERIFIED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(JOB_ID, null, false,
                        HISTORICAL_FAILURE, ReasonEnum.NO_NATIVE_DEADLETTER, ReportedFailureEnum.UNVERIFIED, VERSION)),
                () -> assertAll("reported failure affinity", List.of(MANUAL_REVIEW, HISTORICAL_FAILURE).stream()
                        .<Executable>map(status -> () -> assertAll(status.name(),
                                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(
                                        JOB_ID, null, status == MANUAL_REVIEW, status, ReasonEnum.UNKNOWN_OUTCOME,
                                        ReportedFailureEnum.BUSINESS_REJECTED, null)),
                                () -> assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(
                                        JOB_ID, null, status == MANUAL_REVIEW, status, ReasonEnum.BUSINESS_REJECTED,
                                        ReportedFailureEnum.UNKNOWN, null)))))
        );
    }

    @Test
    void observation_enforces_unsigned_utf8_order_uniqueness_cap_and_copy() {
        var ascii = historicalFailure("z");
        var bmp = historicalFailure("\uE000");
        var supplementary = historicalFailure("\uD800\uDC00");
        var input = new ArrayList<>(List.of(ascii, bmp, supplementary));
        var observation = assertDoesNotThrow(() -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, input));
        assertNotNull(observation);
        assertEquals(INSTANCE_ID, observation.instanceId());
        assertEquals(OBSERVED_AT, observation.observedAt());

        input.clear();
        var tooMany = IntStream.range(100, 229).mapToObj(index -> historicalFailure("job-" + index)).toList();
        var duplicateJob = assertDoesNotThrow(() -> new ProcessWorkerFailureDTO(bmp.jobId(), "act", true,
                MANUAL_REVIEW, ReasonEnum.EVIDENCE_CONFLICT, ReportedFailureEnum.UNVERIFIED, null));
        assertNotNull(duplicateJob);
        var maximum = assertDoesNotThrow(() -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, tooMany.subList(0, 128)));
        assertNotNull(maximum);
        var empty = assertDoesNotThrow(() -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, List.of()));
        assertNotNull(empty);

        assertAll(
                () -> assertEquals(List.of(ascii, bmp, supplementary), observation.failures()),
                () -> assertThrows(UnsupportedOperationException.class, () -> observation.failures().clear()),
                () -> assertEquals(128, maximum.failures().size()),
                () -> assertTrue(empty.failures().isEmpty()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, List.of(supplementary, bmp))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, List.of(bmp, ascii))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, List.of(bmp, duplicateJob))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, tooMany)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, OBSERVED_AT, Arrays.asList(ascii, null))),
                () -> assertThrows(NullPointerException.class,
                        () -> new ProcessWorkerFailuresDTO(INSTANCE_ID, null, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO(null, OBSERVED_AT, List.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ProcessWorkerFailuresDTO("..", OBSERVED_AT, List.of()))
        );
    }

    @Test
    void result_enforces_exact_accepted_scheduling_identity() {
        var result = assertDoesNotThrow(() ->
                new RecoverProcessWorkerResult(INSTANCE_ID, JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT));
        assertNotNull(result);
        assertEquals(INSTANCE_ID, result.instanceId());
        assertEquals(JOB_ID, result.jobId());
        assertEquals(JOB_ID, result.occurrenceId());
        assertEquals(COMMAND_ID, result.commandId());
        assertEquals(RETRY_SCHEDULED, result.status());
        assertEquals(1, result.retries());
        assertEquals(DECIDED_AT, result.decidedAt());

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, "Job-1", COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 0, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 2, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, COMMAND_ID, null, 1, DECIDED_AT)),
                () -> assertThrows(NullPointerException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 1, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        null, JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        ".", JOB_ID, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, null, JOB_ID, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, null, COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, " job", " job", COMMAND_ID, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, null, RETRY_SCHEDULED, 1, DECIDED_AT)),
                () -> assertThrows(IllegalArgumentException.class, () -> new RecoverProcessWorkerResult(
                        INSTANCE_ID, JOB_ID, JOB_ID, "-command", RETRY_SCHEDULED, 1, DECIDED_AT))
        );
    }

    private static ProcessWorkerFailureDTO historicalFailure(String jobId) {
        var row = assertDoesNotThrow(() -> new ProcessWorkerFailureDTO(jobId, null, false, HISTORICAL_FAILURE,
                ReasonEnum.NO_NATIVE_DEADLETTER, ReportedFailureEnum.UNVERIFIED, null));
        assertNotNull(row);
        return row;
    }

    private static void assertDispositionReason(ReasonEnum reason) {
        Set<StatusEnum> allowedStatuses = switch (reason) {
            case NONE -> Set.of(RETRY_AVAILABLE);
            case NO_NATIVE_DEADLETTER -> Set.of(HISTORICAL_FAILURE);
            case UNKNOWN_OUTCOME, BUSINESS_REJECTED, HISTORY_INCOMPLETE -> Set.of(MANUAL_REVIEW, HISTORICAL_FAILURE);
            case RESTORE_LIMIT_REACHED, UNSUPPORTED_NATIVE_JOB, PROCESS_INACTIVE, EVIDENCE_CONFLICT,
                 ACTIVE_LEASE, BINDING_UNVERIFIED, UNSUPPORTED_EFFECT -> Set.of(MANUAL_REVIEW);
        };
        assertAll(reason.name(), Arrays.stream(StatusEnum.values()).<Executable>map(status -> () -> {
            ReportedFailureEnum reported = switch (reason) {
                case UNKNOWN_OUTCOME -> ReportedFailureEnum.UNKNOWN;
                case BUSINESS_REJECTED -> ReportedFailureEnum.BUSINESS_REJECTED;
                default -> status == RETRY_AVAILABLE ? ReportedFailureEnum.RETRYABLE : ReportedFailureEnum.UNVERIFIED;
            };
            boolean nativeDeadletter = status != HISTORICAL_FAILURE;
            String activityId = status == RETRY_AVAILABLE ? "act" : null;
            String expectedVersion = status == RETRY_AVAILABLE ? VERSION : null;
            if (allowedStatuses.contains(status)) {
                var row = assertDoesNotThrow(() -> new ProcessWorkerFailureDTO(
                        JOB_ID, activityId, nativeDeadletter, status, reason, reported, expectedVersion));
                assertNotNull(row);
                assertEquals(status, row.status());
                assertEquals(reason, row.reason());
                assertEquals(reported, row.reportedFailure());
                if (status != RETRY_AVAILABLE) {
                    assertNull(row.activityId());
                    assertNull(row.expectedVersion());
                }
            } else {
                assertThrows(IllegalArgumentException.class, () -> new ProcessWorkerFailureDTO(
                        JOB_ID, activityId, nativeDeadletter, status, reason, reported, expectedVersion));
            }
        }));
    }
}
