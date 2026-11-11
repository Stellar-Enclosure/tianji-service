package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.RunDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Operator-only receipt correlation over the existing isolated Worker acknowledgement recovery fixture. */
class S12WorkerReceiptAcceptanceIT {
    @Test
    void both_owned_worker_receipts_survive_the_committed_process_acknowledgement_replay() throws Exception {
        try (var driver = Driver.openWorkerReceiptEvidence()) {
            RunDTO first = null;
            int checked = 0;
            for (var tenant : driver.tenants()) {
                var run = driver.start(tenant, Scenario.RECOVERY, "s12-worker-receipt", 200);
                WorkerRecoveryIT.forwardedAcknowledgement(driver, run);
                driver.verifyWorkerReceiptCorrelation(run);
                if (first == null) first = run;
                else driver.assertDisjoint(first, run);
                checked++;
            }
            assertEquals(2, checked);
        }
        System.out.println("S12_WORKER_RECEIPT_SUBSET paths=2");
    }
}
