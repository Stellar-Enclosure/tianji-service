package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.RunDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

/** Operator-only evidence for actual Fake CONFIG settlement in the two original linked Agent runs. */
class S12ModelUsageAcceptanceIT {
    @Test
    void settles_one_priced_fake_model_call_per_original_bridge_run() throws Exception {
        try (var driver = Driver.open(Suite.S12_MODEL_USAGE)) {
            RunDTO previous = null;
            for (var tenant : driver.tenants()) {
                var run = driver.start(tenant, Scenario.NORMAL, "s12-model-usage", 100);
                try {
                    driver.startWorker(run, false);
                    driver.assertNormalEnd(run);
                    driver.verifyModelUsage(run);
                    if (previous != null) driver.assertDisjoint(previous, run);
                    previous = run;
                } finally {
                    driver.stopWorker(run);
                }
            }
        }
        System.out.println("S12_MODEL_USAGE_SUBSET tenants=2 pricedCalls=2 source=FAKE_CONFIG");
    }
}
