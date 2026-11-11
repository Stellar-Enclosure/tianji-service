package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.process.server.acceptance.S12WorkerReceiptCorrelationEvidence.ProcessRow;
import top.egon.cola.platform.tianji.process.server.acceptance.S12WorkerReceiptCorrelationEvidence.Scope;
import top.egon.cola.platform.tianji.process.server.acceptance.S12WorkerReceiptCorrelationEvidence.WorkerRow;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class S12WorkerReceiptCorrelationEvidenceTest {
    private static final Instant OBSERVED = Instant.parse("2026-11-11T08:00:00Z");
    private static final String RECEIPT = "receipt-a";
    private static final String TASK = "native-job";
    private static final String OCCURRENCE = "native-job";
    private static final Scope SCOPE = new Scope(20, "expense-process-20", "instance-a", "1".repeat(64), "worker-a");
    private static final CompleteWorkerTaskCommand COMMAND = new CompleteWorkerTaskCommand(TASK, OCCURRENCE,
            "A".repeat(43), RECEIPT, Map.of());
    private static final String KEY = FlowableWorkerService.workerOccurrenceKey(SCOPE.tenant(), TASK);
    private static final String W_DIGEST = ProcessRequestDigest.hash(List.of("worker-business-result-v1", RECEIPT), Map.of());
    private static final String P_DIGEST = ProcessRequestDigest.hash(List.of("worker-complete-v1", TASK, OCCURRENCE, RECEIPT), Map.of());

    @Test
    void same_owned_business_receipt_and_native_completion_are_correlated_without_clock_ordering() {
        var row = worker(20, W_DIGEST, OBSERVED);
        assertDoesNotThrow(() -> S12WorkerReceiptCorrelationEvidence.verify(SCOPE, COMMAND, RECEIPT, row, row,
                process("worker-a", P_DIGEST), 1));
    }

    @Test
    void another_tenants_worker_row_and_another_process_worker_are_rejected() {
        var foreign = worker(21, W_DIGEST, OBSERVED);
        assertEquals("Worker business receipt must match the original owned occurrence",
                assertThrows(AssertionError.class, () -> S12WorkerReceiptCorrelationEvidence.verify(
                        SCOPE, COMMAND, RECEIPT, foreign, foreign, process("worker-a", P_DIGEST), 1)).getMessage());
        var owned = worker(20, W_DIGEST, OBSERVED);
        assertEquals("Process receipt must match the original owned Worker occurrence",
                assertThrows(AssertionError.class, () -> S12WorkerReceiptCorrelationEvidence.verify(
                        SCOPE, COMMAND, RECEIPT, owned, owned, process("worker-b", P_DIGEST), 1)).getMessage());
    }

    @Test
    void rewritten_commit_and_wrong_digest_domain_are_rejected() {
        var owned = worker(20, W_DIGEST, OBSERVED);
        var rewritten = worker(20, W_DIGEST, OBSERVED.plusSeconds(1));
        assertEquals("Worker business receipt must remain unchanged across Process acknowledgement replay",
                assertThrows(AssertionError.class, () -> S12WorkerReceiptCorrelationEvidence.verify(
                        SCOPE, COMMAND, RECEIPT, owned, rewritten, process("worker-a", P_DIGEST), 1)).getMessage());
        assertEquals("Process completion digest must match its original domain",
                assertThrows(AssertionError.class, () -> S12WorkerReceiptCorrelationEvidence.verify(
                        SCOPE, COMMAND, RECEIPT, owned, owned, process("worker-a", W_DIGEST), 1)).getMessage());
        var wrongWorkerDigest = worker(20, P_DIGEST, OBSERVED);
        assertEquals("Worker business receipt digest must match its original domain",
                assertThrows(AssertionError.class, () -> S12WorkerReceiptCorrelationEvidence.verify(
                        SCOPE, COMMAND, RECEIPT, wrongWorkerDigest, wrongWorkerDigest, process("worker-a", P_DIGEST), 1)).getMessage());
    }

    private static WorkerRow worker(long tenant, String resultDigest, Instant committedAt) {
        return new WorkerRow(SCOPE.source(), tenant, KEY, TASK, OCCURRENCE, "reserveBusiness", "1",
                "TRANSACTIONAL_RECEIPT", "expense-fixture-v1", "2".repeat(64), resultDigest, RECEIPT, true, committedAt);
    }

    private static ProcessRow process(String subject, String digest) {
        return new ProcessRow(20, SCOPE.workerIssuer(), subject, SCOPE.instance(), TASK, OCCURRENCE, KEY,
                "reserveBusiness", "1", "COMPLETED", RECEIPT, digest, OBSERVED.minusSeconds(1));
    }
}
