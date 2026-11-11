package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

/** Operator-only proof of the original USER's Tool events and existing governed START receipt. */
class S12ToolEvidenceAcceptanceIT {
    @Test
    void reads_owned_started_and_finished_tool_events_bound_to_the_native_start_receipt() throws Exception {
        try (var driver = Driver.open(Suite.S12_TOOL_EVIDENCE)) {
            driver.verifyGovernedStart();
        }
        System.out.println("S12_TOOL_EVIDENCE_SUBSET tenants=2 toolPairs=2 foreignStreamDenials=2");
    }
}
