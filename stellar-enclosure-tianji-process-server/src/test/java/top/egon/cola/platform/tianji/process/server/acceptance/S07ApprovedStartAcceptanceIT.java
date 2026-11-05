package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

/** Opt-in operator fixture using real IAM, a native approval and isolated PostgreSQL stores. */
class S07ApprovedStartAcceptanceIT {
    @Test
    void requires_real_requester_source_and_reviewer_approval_before_one_governed_start() throws Exception {
        try (var driver = Driver.open(Suite.S07_APPROVED_START)) {
            driver.verifyApprovedStart();
        }
    }
}
