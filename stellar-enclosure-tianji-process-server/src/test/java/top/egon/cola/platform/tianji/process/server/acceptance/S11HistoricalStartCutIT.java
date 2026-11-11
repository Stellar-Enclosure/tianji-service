package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

/** Separate opt-in producer for a real committed START with a discarded successful caller response. */
class S11HistoricalStartCutIT {
    @Test
    void retains_the_original_start_when_the_agent_loses_its_committed_success_response() throws Exception {
        try (var driver = Driver.open(Suite.S11_HISTORICAL_START_CUT)) {
            driver.verifyHistoricalStartCut();
        }
    }
}
