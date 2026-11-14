package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerReportDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationWorkerContractTest {

    private static final String A = "a".repeat(64);
    private static final String B = "b".repeat(64);
    private static final String RESULT_SHA = "ec154e3466b26702aa4e3741d80228875666d30eb5bc5e9fbb625cd315cd9360";
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8";
    private static final String TOKEN_SHA = "630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd";
    private static final Instant NOW = Instant.parse("2026-11-11T11:00:00Z");

    @Test
    void empty_output_has_independent_business_and_node_digest_domains() {
        assertEquals(RESULT_SHA, AutomationWorkerBusinessResultDTO.resultSha256(A, "br-01", Map.of()));
        assertEquals("c121fe796332920cf12c62c47011ec380a51bae42923fe6098138f4882ca76a4",
                AutomationWorkerBusinessResultDTO.nodeOutputSha256(B, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> AutomationWorkerBusinessResultDTO.resultSha256(A, "", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> AutomationWorkerBusinessResultDTO.resultSha256(A, "br-01", null));
        assertThrows(IllegalArgumentException.class, () -> AutomationWorkerBusinessResultDTO.nodeOutputSha256(B, Map.of("amount", 1.0)));
        assertThrows(IllegalArgumentException.class, () -> AutomationWorkerBusinessResultDTO.nodeOutputSha256(B,
                Map.of("n", ProcessTypedJson.V2_MAX_WIRE_INTEGER + 1)));
    }

    @Test
    void task_and_report_share_a_hash_of_the_decoded_lease_bytes() {
        var task = task(null);
        var completion = new AutomationWorkerCompletionCommand().setLeaseToken(TOKEN)
                .setBusinessReceiptId("private-receipt").setOutput(Map.of("private-data", "yes"));
        var failure = new AutomationWorkerFailureCommand().setLeaseToken(TOKEN).setBusinessReceiptId("private-receipt");
        assertEquals(TOKEN_SHA, task.leaseTokenSha256());
        assertEquals(TOKEN_SHA, completion.leaseTokenSha256());
        assertEquals(TOKEN_SHA, failure.leaseTokenSha256());
        assertTrue(completion.isCompletionValid());
        assertTrue(failure.isFailureValid());
        assertNotEquals("ea866a757e4c38babfa8127cbe9a409d3e1f93a00ff1488ff735fcf917afffd0", task.leaseTokenSha256());
        for (String unsafe : new String[]{TOKEN + "=", TOKEN.substring(0, 42) + "9", ""}) {
            assertThrows(IllegalArgumentException.class, () -> AutomationWorkerTaskDTO.leaseTokenSha256(unsafe));
        }
        assertFalse(task.toString().contains(TOKEN));
        assertFalse(completion.toString().contains(TOKEN));
        assertFalse(completion.toString().contains("private-"));
        assertFalse(failure.toString().contains(TOKEN));
        assertFalse(failure.toString().contains("private-"));
    }

    @Test
    void task_digest_covers_full_contract_and_host_scope() {
        var ordinary = task(null);
        assertEquals("7dfac2b5ab8db6dafc1ae506280b69593553845c67ba35ac70a21f42ca52f8f7",
                ordinary.contractSha256(11L, "worker-main"));
        var recovery = task(replayApplied());
        String expected = "b34af1dcf7756e32fef0bc90b414d0d3782406df07ebedfa0f27b5c37721fe61";
        assertEquals(expected, recovery.contractSha256(11L, "worker-main"));
        recovery.setTaskContractSha256(expected);
        assertDoesNotThrow(() -> recovery.verifyContractHash(11L, "worker-main"));
        assertThrows(IllegalArgumentException.class, () -> recovery.verifyContractHash(12L, "worker-main"));
        assertThrows(IllegalArgumentException.class, () -> recovery.verifyContractHash(11L, "worker-other"));
        assertThrows(IllegalArgumentException.class, () -> recovery.setLeaseToken("A".repeat(43)).verifyContractHash(11L, "worker-main"));
        assertThrows(IllegalArgumentException.class, () -> recovery.setLeaseToken(TOKEN).setEffectRecovery(null).verifyContractHash(11L, "worker-main"));
        assertDoesNotThrow(() -> ordinary.setTargetId("1customer").contractSha256(Long.MAX_VALUE, "1worker"));
    }

    @Test
    void recovery_proof_and_effect_mode_have_distinct_boundaries() {
        var retry = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED, 3L, NOW, NOW.plusSeconds(25),
                "authority-1", A, null, null);
        assertTrue(task(retry).isTaskContractConsistent());
        assertFalse(task(retry).setEffectMode(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT).setTargetId(null).isTaskContractConsistent());
        assertFalse(task(retry).setEffectMode(WorkerEffectSafetyEnum.READ_ONLY).isTaskContractConsistent());
        assertTrue(task(replayApplied()).setEffectMode(WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT).setTargetId(null).isTaskContractConsistent());
        assertFalse(task(null).setTargetId(null).isTaskContractConsistent());
        assertThrows(IllegalArgumentException.class, () -> new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED,
                3L, NOW, NOW.plusSeconds(25), "authority-1", A, "br-01", RESULT_SHA));
        assertThrows(IllegalArgumentException.class, () -> new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED,
                3L, NOW, NOW.plusSeconds(25), "authority-1", A, "br-01", null));
        assertThrows(IllegalArgumentException.class, () -> new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED,
                3L, NOW, NOW, "authority-1", A, null, null));
    }

    @Test
    void wire_unions_preserve_explicit_nulls_and_operation_specific_outcomes() throws Exception {
        var acquire = new AutomationWorkerAcquireDTO().setStatus(AutomationWorkerAcquireDTO.StatusEnum.EMPTY);
        assertTrue(acquire.isAcquisitionConsistent());
        assertFalse(acquire.setStatus(AutomationWorkerAcquireDTO.StatusEnum.ACQUIRED).isAcquisitionConsistent());
        assertTrue(acquire.setTask(task(null)).isAcquisitionConsistent());
        assertFalse(acquire.setStatus(AutomationWorkerAcquireDTO.StatusEnum.EMPTY).isAcquisitionConsistent());
        assertEquals(JsonInclude.Include.ALWAYS, AutomationWorkerAcquireDTO.class.getDeclaredField("task").getAnnotation(JsonInclude.class).value());
        assertEquals(JsonInclude.Include.ALWAYS, AutomationWorkerTaskDTO.class.getDeclaredField("effectRecovery").getAnnotation(JsonInclude.class).value());
        var report = new AutomationWorkerReportDTO().setDecidedAt(NOW).setOutcome(AutomationWorkerReportDTO.OutcomeEnum.COMPLETED);
        assertDoesNotThrow(report::requireCompletionOutcome);
        assertThrows(IllegalArgumentException.class, report::requireFailureOutcome);
        for (var outcome : AutomationWorkerReportDTO.OutcomeEnum.values()) {
            if (outcome != AutomationWorkerReportDTO.OutcomeEnum.COMPLETED) {
                report.setOutcome(outcome);
                assertDoesNotThrow(report::requireFailureOutcome);
                assertThrows(IllegalArgumentException.class, report::requireCompletionOutcome);
            }
        }
        assertEquals("PERMANENT", AutomationWorkerFailureCommand.KindEnum.PERMANENT.wire());
        assertEquals("BUSINESS_REJECTED", WorkerFailureKindEnum.BUSINESS_REJECTED.name());
        var command = new AutomationWorkerAcquireCommand().setCapabilityKey("customer.lookup")
                .setCapabilityVersion(ProcessTypedJson.V2_MAX_WIRE_INTEGER);
        assertEquals(999_999_999_999_999L, command.getCapabilityVersion());
        assertEquals(command.getCapabilityVersion(), AutomationWorkerAcquireCommand.class.getDeclaredField("capabilityVersion")
                .getAnnotation(Max.class).value());
        assertFalse(task(null).setCapabilityVersion(9_007_199_254_740_993L).isTaskContractConsistent());
    }

    private static AutomationWorkerTaskDTO task(WorkerEffectRecoveryVO recovery) {
        return AutomationWorkerTaskDTO.builder().taskId("job-101").activityOccurrenceId("job-101").nodeExecutionRef("nx-101")
                .leaseToken(TOKEN).capabilityKey("customer.lookup").capabilityVersion(1).handlerVersion("customer-read-v1")
                .effectMode(WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT).targetId("customer-system").inputSchemaSha256(A)
                .outputSchemaSha256(B).effectFingerprint(A).input(Map.of()).inputSha256(A).effectKey("effect-101")
                .attemptDeadlineAt(NOW.plusSeconds(60)).leaseExpiresAt(NOW.plusSeconds(30)).effectRecovery(recovery).build();
    }

    @Test
    void capability_key_uses_the_logical_worker_topic_alphabet() throws NoSuchFieldException {
        String pattern = AutomationWorkerAcquireCommand.class.getDeclaredField("capabilityKey")
                .getAnnotation(Pattern.class).regexp();
        assertTrue("_customer.lookup-2".matches(pattern));
        assertFalse("customer:lookup".matches(pattern));
        assertTrue(("_" + "k".repeat(127)).matches(pattern));
        assertFalse(("_" + "k".repeat(128)).matches(pattern));
        assertDoesNotThrow(() -> task(null).setCapabilityKey("_customer.lookup-2").contractSha256(20, "process-east"));
        assertThrows(IllegalArgumentException.class,
                () -> task(null).setCapabilityKey("customer:lookup").contractSha256(20, "process-east"));
        assertDoesNotThrow(() -> new AutomationNodeBindingVO.CapabilityRefVO(
                "_customer.lookup-2", 1, A, B, WorkerEffectSafetyEnum.READ_ONLY));
        assertThrows(IllegalArgumentException.class, () -> new AutomationNodeBindingVO.CapabilityRefVO(
                "customer:lookup", 1, A, B, WorkerEffectSafetyEnum.READ_ONLY));
    }

    private static WorkerEffectRecoveryVO replayApplied() {
        return new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, 3L, NOW, NOW.plusSeconds(25),
                "authority-1", A, "br-01", RESULT_SHA);
    }
}
