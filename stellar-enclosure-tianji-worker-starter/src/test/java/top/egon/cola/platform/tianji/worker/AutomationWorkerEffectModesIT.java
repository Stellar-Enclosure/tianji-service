package top.egon.cola.platform.tianji.worker;

import org.junit.jupiter.api.BeforeAll;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.worker.WorkerEffectReceiptIT.EMPTY;
import static top.egon.cola.platform.tianji.worker.WorkerEffectReceiptIT.descriptor;
import static top.egon.cola.platform.tianji.worker.WorkerEffectReceiptIT.result;
import static top.egon.cola.platform.tianji.worker.WorkerEffectReceiptIT.task;

@Timeout(45)
class AutomationWorkerEffectModesIT {
    private static WorkerEffectReceiptIT.FixtureVO fixture;

    @BeforeAll
    static void provision() {
        fixture = WorkerEffectReceiptIT.provision("tianji.worker.nontransactional.postgres.test.url");
    }

    @Test
    void ordinary_attempts_cannot_redispatch_and_two_frozen_recovery_attempts_advance_generation_once() throws Exception {
        var descriptor = descriptor(WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "customer-system");
        var attempts = new AtomicInteger();
        var sent = new ConcurrentLinkedQueue<Long>();
        var registration = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY, null, (context, effectKey) -> {
            assertEquals("external-effect", effectKey);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            attempts.incrementAndGet();
            sent.add(context.dispatchGeneration());
            throw new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, "RESPONSE_LOST");
        });
        var proofObserved = new AtomicReference<Long>();
        var store = fixture.store(query -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            proofObserved.set(query.getDispatchGeneration());
            var now = fixture.now();
            return new WorkerEffectEvidenceVO(WorkerEffectEvidenceVO.Status.NOT_APPLIED_FINAL, query.getEffectKey(),
                    query.getEffectFingerprint(), query.getTargetId(), query.getDispatchGeneration(), "customer-authority",
                    now, now.plusSeconds(15), "a".repeat(64), null, null, null);
        });
        var original = task(fixture, descriptor, "external-job-1", "external-effect", 4, null);
        assertUnknown(() -> store.executeAutomation(original, registration));
        assertEquals(1, attempts.get());
        var ordinary = task(fixture, descriptor, "external-job-2", "external-effect", 5, null);
        assertUnknown(() -> store.executeAutomation(ordinary, registration));
        assertEquals(1, attempts.get());
        assertEquals(1L, generation());

        var now = fixture.now();
        var recovery = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.RETRY_NOT_APPLIED, 1, now,
                now.plusSeconds(15), "customer-authority", "b".repeat(64), null, null);
        var first = task(fixture, descriptor, "external-job-3", "external-effect", 6, recovery);
        var second = task(fixture, descriptor, "external-job-3", "external-effect", 7, recovery);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                assertUnknown(() -> store.executeAutomation(first, registration));
                return true;
            });
            var b = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                assertUnknown(() -> store.executeAutomation(second, registration));
                return true;
            });
            assertTrue(a.get(12, TimeUnit.SECONDS));
            assertTrue(b.get(12, TimeUnit.SECONDS));
        }
        assertEquals(2L, generation());
        assertEquals(2, attempts.get());
        assertUnknown(() -> store.executeAutomation(task(fixture, descriptor, "external-job-3", "external-effect", 8, recovery), registration));
        assertEquals(2L, generation());
        assertEquals(2, attempts.get());
        assertEquals(java.util.List.of(1L, 2L), java.util.List.copyOf(sent));
        assertEquals(2L, proofObserved.get());
    }

    @Test
    void applied_recovery_replays_authoritative_output_without_dispatch() {
        var descriptor = descriptor(WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, "customer-system");
        var attempts = new AtomicInteger();
        var registration = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY, null, (context, key) -> {
            attempts.incrementAndGet();
            throw new WorkerHandlerFailureException(WorkerFailureKindEnum.UNKNOWN, "RESPONSE_LOST");
        });
        var first = task(fixture, descriptor, "applied-first", "applied-recovery", 11, null);
        var hash = AutomationWorkerBusinessResultDTO.resultSha256(first.getEffectFingerprint(), "applied-business", Map.of());
        var store = fixture.store(query -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            var now = fixture.now();
            return new WorkerEffectEvidenceVO(WorkerEffectEvidenceVO.Status.APPLIED, query.getEffectKey(),
                    query.getEffectFingerprint(), query.getTargetId(), query.getDispatchGeneration(), "customer-authority",
                    now, now.plusSeconds(15), "a".repeat(64), "applied-business", Map.of(), hash);
        });
        assertUnknown(() -> store.executeAutomation(first, registration));
        var now = fixture.now();
        var recovery = new WorkerEffectRecoveryVO(WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED, 1, now, now.plusSeconds(15),
                "customer-authority", "b".repeat(64), "applied-business", hash);
        var result = store.executeAutomation(task(fixture, descriptor, "applied-recovery-job", "applied-recovery", 12, recovery), registration);
        assertTrue(result.replayed());
        assertEquals(hash, result.resultSha256());
        assertEquals(1, attempts.get());
        assertTrue(fixture.owner().queryForObject("""
                SELECT dispatch_generation=1 AND state='COMPLETED'
                    AND last_reconciliation->>'status'='APPLIED' AND (last_reconciliation->>'dispatchGeneration')::bigint=1
                FROM business.tianji_worker_effect_receipt WHERE effect_key='applied-recovery'
                """, Boolean.class));
    }

    @Test
    void read_only_handler_runs_outside_sql_transaction_with_a_stable_receipt() {
        var descriptor = descriptor(WorkerEffectSafetyEnum.READ_ONLY, "trusted-reader");
        var registration = new AutomationWorkerHandlerRegistration(descriptor, EMPTY, EMPTY, null, (context, effectKey) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("read-effect", context.effectKey());
            return result("query-receipt");
        });
        var receipt = fixture.store().executeAutomation(task(fixture, descriptor, "read-job", "read-effect", 9, null), registration);
        assertEquals("query-receipt", receipt.businessReceiptId());
    }

    private static long generation() {
        return fixture.owner().queryForObject("""
                SELECT dispatch_generation FROM business.tianji_worker_effect_receipt WHERE effect_key='external-effect'
                """, Long.class);
    }

    private static void assertUnknown(org.junit.jupiter.api.function.Executable action) {
        assertEquals(WorkerFailureKindEnum.UNKNOWN,
                assertThrows(WorkerHandlerFailureException.class, action).kind());
    }
}
