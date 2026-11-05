package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;

/** Separate operator opt-in for the metrics and literal child-log privacy subset. */
class S12ChildLogPrivacyAcceptanceIT {
    @Test
    void collects_the_same_metrics_with_private_child_log_evidence() throws Exception {
        try (var logs = new S12ChildLogPrivacy()) {
            S12MetricsAcceptanceIT.verifyMetrics(logs);
        }
    }
}
