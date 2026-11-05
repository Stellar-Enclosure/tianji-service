package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;

/** Operator-only S12 protected reads for the original Process USER and linked Agent SERVICE. */
class S12OwnerObservationAcceptanceIT {
    @Test
    void observes_each_tenants_owned_process_and_bridge_agent_run() throws Exception {
        S12MetricsAcceptanceIT.verifyOwnerObservations();
    }
}
