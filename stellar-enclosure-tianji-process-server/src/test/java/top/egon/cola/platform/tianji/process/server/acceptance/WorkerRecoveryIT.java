package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.client.ProcessClientException;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.DispatchDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.RunDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultWindowDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildRole;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpensePathResultDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.TenantBindingDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessKind;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.WitnessState;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.DEFER_RESULT_UNTIL_TIMER_OR_RESTART;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_RESULT_202;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.DROP_COMMITTED_WORKER_COMPLETE_200;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.HOLD_LINKED_SUCCESS;
import static top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase.OBSERVE_REPLACEMENT_RESEND;

class WorkerRecoveryIT {
    private static final long RECOVERY_AMOUNT = 200;

    @Test
    void recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants() throws Exception {
        try (var driver = Driver.open(Suite.RECOVERY)) {
            var results = new ArrayList<ExpensePathResultDTO>();
            var prior = new java.util.HashMap<String, RunDTO>();
            try {
                for (TenantBindingDTO tenant : driver.tenants()) {
                    var lost = driver.start(tenant, Scenario.RECOVERY, "lost-complete", RECOVERY_AMOUNT);
                    String reacquired = lostAcknowledgement(driver, lost);
                    var baseline = driver.result(lost, Map.of());
                    var forwarded = driver.start(tenant, Scenario.RECOVERY, "forwarded-complete", RECOVERY_AMOUNT);
                    forwardedAcknowledgement(driver, forwarded);
                    driver.result(forwarded, Map.of());
                    var early = driver.start(tenant, Scenario.RECOVERY, "early-agent-restart", RECOVERY_AMOUNT);
                    var delivery = earlyResultAndAgentRestart(driver, early);
                    driver.result(early, Map.of());
                    var wait = driver.start(tenant, Scenario.RECOVERY, "retained-process-wait", RECOVERY_AMOUNT);
                    String retained = processRestart(driver, wait);
                    driver.result(wait, Map.of());
                    for (RunDTO run : java.util.List.of(lost, forwarded, early, wait)) {
                        RunDTO firstTenant = prior.putIfAbsent(run.window(), run);
                        if (firstTenant != null) driver.assertDisjoint(firstTenant, run);
                    }

                    assertNotEquals(lost.instance().instanceId(), early.instance().instanceId());
                    assertNotEquals(lost.instance().instanceId(), wait.instance().instanceId());
                    assertNotEquals(early.instance().instanceId(), wait.instance().instanceId());
                    var witnesses = new java.util.EnumMap<WitnessKind, WitnessDTO>(baseline.witnesses());
                    witnesses.put(WitnessKind.WORKER_REACQUIRED, proved(reacquired));
                    witnesses.put(WitnessKind.EARLY_INBOX_PENDING, proved(delivery.early()));
                    witnesses.put(WitnessKind.OUTBOX_RETRY_CONFIRMED, proved(delivery.resent()));
                    witnesses.put(WitnessKind.PROCESS_WAIT_RETAINED, proved(retained));
                    results.add(new ExpensePathResultDTO(baseline.tenantId(), baseline.scenario(), baseline.operationId(),
                            baseline.businessKey(), baseline.correlationId(), baseline.passed(), witnesses));
                }
                assertEquals(2, results.size());
                assertEquals(results.get(0).operationId(), results.get(1).operationId());
                assertEquals(results.get(0).businessKey(), results.get(1).businessKey());
                assertNotEquals(results.get(0).witnesses().get(WitnessKind.PROCESS_INSTANCE),
                        results.get(1).witnesses().get(WitnessKind.PROCESS_INSTANCE));
                assertNotEquals(results.get(0).witnesses().get(WitnessKind.ORIGINAL_RECEIPT),
                        results.get(1).witnesses().get(WitnessKind.ORIGINAL_RECEIPT));
                driver.publish(results);
            } catch (RuntimeException | AssertionError failure) {
                try {
                    driver.publishFailure(results);
                } catch (RuntimeException | AssertionError evidenceFailure) {
                    failure.addSuppressed(evidenceFailure);
                }
                throw failure;
            }
        }
    }

    private static String lostAcknowledgement(Driver driver, RunDTO run) {
        var relay = driver.relay();
        String job = driver.await(run, () -> driver.workerJob(run));
        arm(driver, run, DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT, job, job, null, null);
        driver.startWorker(run, true);
        try {
            var dropped = relay.awaitObserved(DROP_WORKER_COMPLETE_AFTER_BUSINESS_COMMIT, driver.relayBudget());
            assertEquals(job, dropped.occurrenceId());
            var oldComplete = driver.await(run, () -> driver.droppedComplete(run));
            assertEquals(job, oldComplete.taskId());
            String originalTokenHash = WorkerLeaseStore.tokenHash(oldComplete.leaseToken());
            var original = driver.await(run, () -> driver.leaseRows(run, "reserveExpense").stream()
                    .filter(lease -> job.equals(lease.jobId()) && originalTokenHash.equals(lease.tokenSha256()))
                    .findFirst());
            assertEquals(original.occurrenceId(), oldComplete.activityOccurrenceId());
            var afterCommit = driver.effects(run);
            assertEquals(1, afterCommit.originalCount());
            assertNotNull(afterCommit.originalReceiptId());
            var fresh = driver.await(run, () -> driver.lease(run).filter(lease -> job.equals(lease.jobId())
                    && lease.leasedAt().isAfter(original.leasedAt())));
            assertEquals(original.occurrenceId(), fresh.occurrenceId());
            assertEquals(original.idempotencyKey(), fresh.idempotencyKey());
            assertTrue(driver.leaseRows(run, "reserveExpense").stream().anyMatch(row ->
                    originalTokenHash.equals(row.tokenSha256()) && "EXPIRED".equals(row.state())),
                    "The original lease expired before reacquisition");
            assertFalse(fresh.leasedAt().isBefore(original.expiresAt()), "The fresh lease follows native expiry");
            assertTrue(!original.tokenSha256().equals(fresh.tokenSha256()), "The native reacquisition issued a fresh token");
            var rejected = assertThrows(ProcessClientException.class, () -> driver.workerClient(run).complete(oldComplete));
            assertEquals(409, rejected.httpStatus());
            assertEquals("TIANJI_WORKER_LEASE_LOST", rejected.code());
            driver.assertNormalEnd(run);
            var settled = driver.effects(run);
            assertEquals(afterCommit.originalReceiptId(), settled.originalReceiptId());
            assertEquals(1, settled.originalCount());
            return join(job, fresh.occurrenceId(), original.leasedAt().toEpochMilli(), fresh.leasedAt().toEpochMilli());
        } finally {
            driver.stopWorker(run);
        }
    }

    static void forwardedAcknowledgement(Driver driver, RunDTO run) {
        var relay = driver.relay();
        String job = driver.await(run, () -> driver.workerJob(run));
        arm(driver, run, DROP_COMMITTED_WORKER_COMPLETE_200, job, job, null, null);
        driver.startWorker(run, false);
        try {
            var replay = relay.awaitObserved(DROP_COMMITTED_WORKER_COMPLETE_200, driver.relayBudget());
            var original = driver.droppedComplete(run).orElseThrow();
            assertEquals(job, original.taskId());
            assertEquals(original.activityOccurrenceId(), replay.occurrenceId());
            driver.assertNormalEnd(run);
            var leases = driver.leaseRows(run, "reserveExpense");
            assertEquals(1, leases.size(), "Committed ACK replay keeps the original lease");
            var completed = leases.getFirst();
            assertEquals(job, completed.jobId());
            assertEquals(original.activityOccurrenceId(), completed.occurrenceId());
            assertEquals(WorkerLeaseStore.tokenHash(original.leaseToken()), completed.tokenSha256());
            assertEquals("COMPLETED", completed.state());
            var effects = driver.effects(run);
            assertEquals(original.businessReceiptId(), effects.originalReceiptId());
            assertEquals(1, effects.originalCount());
        } finally {
            driver.stopWorker(run);
        }
    }

    static DeliveryWitnessDTO earlyResultAndAgentRestart(Driver driver, RunDTO run) {
        var relay = driver.relay();
        arm(driver, run, HOLD_LINKED_SUCCESS, null, null, null, null);
        arm(driver, run, DROP_COMMITTED_RESULT_202, null, null, null, null);
        driver.startWorker(run, false);
        try {
            var held = relay.awaitObserved(HOLD_LINKED_SUCCESS, driver.linkedHoldBudget());
            var dropped = relay.awaitObserved(DROP_COMMITTED_RESULT_202, driver.linkedHoldBudget());
            assertEquals(held.occurrenceId(), dropped.occurrenceId());
            var pending = driver.await(run, () -> driver.inbox(run).filter(inbox -> "PENDING".equals(inbox.state())));
            assertEquals(dropped.eventId(), pending.eventId());
            assertEquals(dropped.fingerprint(), pending.fingerprint());
            var beforeAck = driver.await(run, () -> driver.dispatch(run));
            assertEquals(held.occurrenceId(), beforeAck.occurrenceId());
            assertNotEquals("ACKNOWLEDGED", beforeAck.state());
            // Fence the replacement observation before opening the first response gate. A prior
            // generation's resend cannot satisfy the relay's replacement readiness predicate.
            arm(driver, run, OBSERVE_REPLACEMENT_RESEND, held.occurrenceId(), null, pending.eventId(), pending.fingerprint());
            relay.release(HOLD_LINKED_SUCCESS);
            var acknowledged = driver.await(run, () -> driver.dispatch(run).filter(WorkerRecoveryIT::acknowledged));
            var durable = driver.await(run, () -> driver.outbox(run, pending.eventId())
                    .filter(outbox -> "RETRY_WAIT".equals(outbox.status()) && outbox.attempts() > 0));
            assertEquals(pending.eventId(), durable.eventId());
            var before = driver.child(ChildRole.AGENT);
            var after = driver.replaceChild(ChildRole.AGENT);
            assertNotEquals(before.pid(), after.pid());
            assertTrue(after.startedAt().isAfter(before.startedAt()));
            var resend = relay.awaitObserved(OBSERVE_REPLACEMENT_RESEND, driver.relayBudget());
            assertEquals(pending.eventId(), resend.eventId());
            assertEquals(pending.fingerprint(), resend.fingerprint());
            driver.assertNormalEnd(run);
            var applied = driver.await(run, () -> driver.inbox(run).filter(inbox -> "APPLIED".equals(inbox.state())));
            assertEquals(pending.eventId(), applied.eventId());
            assertEquals(pending.fingerprint(), applied.fingerprint());
            var succeeded = driver.await(run, () -> driver.outbox(run, pending.eventId())
                    .filter(outbox -> "SUCCEEDED".equals(outbox.status())));
            assertTrue(succeeded.attempts() >= durable.attempts());
            return new DeliveryWitnessDTO(join(run.instance().instanceId(), held.occurrenceId(),
                    acknowledged.runId(), pending.eventId()),
                    join(pending.eventId(), pending.fingerprint(), before.pid(), after.pid()));
        } finally {
            driver.stopWorker(run);
        }
    }

    private static String processRestart(Driver driver, RunDTO run) {
        var relay = driver.relay();
        arm(driver, run, DEFER_RESULT_UNTIL_TIMER_OR_RESTART, null, null, null, null);
        driver.startWorker(run, false);
        try {
            var deferred = relay.awaitObserved(DEFER_RESULT_UNTIL_TIMER_OR_RESTART, driver.relayBudget());
            var dispatch = driver.await(run, () -> driver.dispatch(run).filter(WorkerRecoveryIT::acknowledged));
            assertEquals(deferred.occurrenceId(), dispatch.occurrenceId());
            var wait = driver.await(run, () -> driver.nativeWait(run));
            assertNotNull(wait.deadline());
            var before = driver.child(ChildRole.PROCESS);
            var after = driver.replaceChild(ChildRole.PROCESS);
            assertNotEquals(before.pid(), after.pid());
            assertTrue(after.startedAt().isAfter(before.startedAt()));
            assertEquals(wait, driver.await(run, () -> driver.nativeWait(run)));
            relay.release(DEFER_RESULT_UNTIL_TIMER_OR_RESTART);
            driver.assertNormalEnd(run);
            var applied = driver.await(run, () -> driver.inbox(run).filter(inbox -> "APPLIED".equals(inbox.state())));
            assertEquals(deferred.eventId(), applied.eventId());
            assertEquals(deferred.fingerprint(), applied.fingerprint());
            return join(run.instance().instanceId(), dispatch.runId(), wait.subscriptionId(), wait.timerId(),
                    wait.deadline().toEpochMilli(), before.pid(), after.pid());
        } finally {
            driver.stopWorker(run);
        }
    }

    private static void arm(Driver driver, RunDTO run, FaultPhase phase, String occurrence, String job,
                            String eventId, String fingerprint) {
        Duration limit = phase == HOLD_LINKED_SUCCESS ? driver.linkedHoldBudget() : driver.relayBudget();
        driver.relay().arm(new FaultWindowDTO(phase, run.tenant().tenantId(), driver.sourceId(run),
                run.instance().instanceId(), occurrence, job, eventId, fingerprint, limit));
    }

    private static boolean acknowledged(DispatchDTO dispatch) {
        return "ACKNOWLEDGED".equals(dispatch.state()) && dispatch.runId() != null;
    }

    private static WitnessDTO proved(String reference) {
        return new WitnessDTO(WitnessState.PROVED, reference);
    }

    private static String join(Object... values) {
        return String.join(":", java.util.Arrays.stream(values).map(Object::toString).toList());
    }

    private record DeliveryWitnessDTO(String early, String resent) { }
}
