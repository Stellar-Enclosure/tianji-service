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
import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator-only S12a E subset; separate private manifest, stores and collector. */
class S12ReceiveLinkAcceptanceIT {
    @Test
    void receives_both_owned_terminal_results_with_exact_collector_links() throws Exception {
        var carriers = new ArrayList<String>();
        var start = Instant.now();
        ExpenseAcceptanceManifestDTO manifest;
        try (var driver = Driver.open(Suite.RECEIVE_LINK)) {
            manifest = driver.receiveLinkManifest();
            RunDTO prior = null;
            for (var tenant : driver.tenants()) {
                var run = driver.start(tenant, Scenario.NORMAL, "s12-receive-link", 100);
                try {
                    driver.startWorker(run, false);
                    driver.assertNormalEnd(run);
                    if (prior != null) driver.assertDisjoint(prior, run);
                    carriers.add(driver.committedReceiveLinkCarrier(run));
                    prior = run;
                } finally {
                    driver.stopWorker(run);
                }
            }
        }
        Path collector = Path.of(manifest.privateRoot()).resolve(S12ReceiveLinkCollectorEvidence.TRACE_FILE);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        byte[] bytes;
        boolean linked;
        do {
            bytes = S12ReceiveLinkCollectorEvidence.readPrivate(collector);
            linked = S12ReceiveLinkCollectorEvidence.linked(bytes, carriers, start);
            if (linked) break;
            Thread.sleep(Duration.ofMillis(100));
        } while (System.nanoTime() - deadline < 0);
        assertTrue(linked, "Both exact persisted enqueues must link through delivery to received spans in the private collector");
        String collectorDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        System.out.printf("S12_RECEIVE_LINK_SUBSET collectorSha256=%s agentArchive=%s processArchive=%s agentFixtures=%s processFixtures=%s paths=2%n",
                collectorDigest, manifest.agent().execJarSha256(), manifest.process().execJarSha256(),
                manifest.agent().testClassesSha256(), manifest.process().testClassesSha256());
    }
}
