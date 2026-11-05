package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

/** Opt-in operator fixture using real IAM and pre-provisioned isolated PostgreSQL stores. */
class S07GovernedStartAcceptanceIT {
    @Test
    void starts_once_through_actual_agent_tool_and_preserves_service_owner() throws Exception {
        try (var driver = Driver.open(Suite.S07_GOVERNED_START)) {
            driver.verifyGovernedStart();
        }
    }
}
