package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.RunDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator-only S12 metrics subset against the isolated, pinned OTLP file Collector. */
class S12MetricsAcceptanceIT {
    @Test
    void collects_two_initial_claims_and_two_committed_inbox_decisions_with_fixed_tags() throws Exception {
        verifyMetrics(null);
    }

    static void verifyMetrics(S12ChildLogPrivacy logs) throws Exception {
        var earliest = Instant.now();
        ExpenseAcceptanceManifestDTO manifest;
        try (var driver = Driver.open(Suite.S12_METRICS, logs)) {
            manifest = driver.metricsManifest();
            RunDTO prior = null;
            for (var tenant : driver.tenants()) {
                var run = driver.start(tenant, Scenario.NORMAL, "s12-metrics", 100);
                try {
                    driver.startWorker(run, false);
                    driver.assertNormalEnd(run);
                    if (prior != null) driver.assertDisjoint(prior, run);
                    prior = run;
                } finally {
                    driver.stopWorker(run);
                }
            }
        }

        if (logs != null) logs.requireSafe();
        var file = Path.of(manifest.privateRoot()).resolve(S12MetricsCollectorEvidence.METRICS_FILE);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        byte[] exported;
        boolean verified;
        do {
            exported = S12MetricsCollectorEvidence.readAvailable(file);
            verified = S12MetricsCollectorEvidence.verified(exported, earliest, Instant.now());
            if (verified) break;
            Thread.sleep(Duration.ofMillis(100));
        } while (System.nanoTime() - deadline < 0);
        assertTrue(verified, "Both exact committed Timer aggregates must arrive in the isolated Collector");
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(exported));
        System.out.printf("S12_METRICS_SUBSET collectorSha256=%s agentArchive=%s processArchive=%s agentFixtures=%s processFixtures=%s paths=2%n",
                digest, manifest.agent().execJarSha256(), manifest.process().execJarSha256(),
                manifest.agent().testClassesSha256(), manifest.process().testClassesSha256());
        if (logs != null) System.out.println("S12_CHILD_LOG_PRIVACY_SUBSET roles=2 promptObservations=2");
    }
}
