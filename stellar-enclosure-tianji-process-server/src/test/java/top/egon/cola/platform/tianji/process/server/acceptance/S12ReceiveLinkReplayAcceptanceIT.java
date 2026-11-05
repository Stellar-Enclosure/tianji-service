package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.process.server.acceptance.BusinessProcessAgentAcceptanceIT.Driver;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceFaultRelay.FaultPhase;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildRole;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ExpenseAcceptanceManifestDTO;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Scenario;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.Suite;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Operator-only S12 replay subset; runs on a separate private manifest, stores and collector. */
class S12ReceiveLinkReplayAcceptanceIT {
    @Test
    void receives_two_exact_links_when_a_committed_response_is_lost_and_the_agent_restarts() throws Exception {
        String enqueue;
        String lostLink;
        String replayLink;
        Instant replacementStartedAt;
        var earliest = Instant.now();
        ExpenseAcceptanceManifestDTO manifest;
        try (var driver = Driver.open(Suite.RECEIVE_LINK_REPLAY)) {
            manifest = driver.receiveLinkManifest();
            var run = driver.start(driver.tenants().getFirst(), Scenario.RECOVERY, "s12-receive-link-replay", 200);
            WorkerRecoveryIT.earlyResultAndAgentRestart(driver, run);
            enqueue = driver.committedReceiveLinkCarrier(run);
            lostLink = driver.relay().committedResultLink(FaultPhase.DROP_COMMITTED_RESULT_202);
            replayLink = driver.relay().committedResultLink(FaultPhase.OBSERVE_REPLACEMENT_RESEND);
            replacementStartedAt = driver.child(ChildRole.AGENT).startedAt();
        }
        Path collector = Path.of(manifest.privateRoot()).resolve(S12ReceiveLinkCollectorEvidence.TRACE_FILE);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        byte[] bytes;
        boolean linked;
        do {
            bytes = S12ReceiveLinkCollectorEvidence.readPrivate(collector);
            linked = S12ReceiveLinkCollectorEvidence.replayLinked(bytes, enqueue, lostLink, replayLink, earliest, replacementStartedAt);
            if (linked) break;
            Thread.sleep(Duration.ofMillis(100));
        } while (System.nanoTime() - deadline < 0);
        assertTrue(linked, "The two committed relay observations must link through the original and restarted delivery to one durable enqueue");
        String collectorDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        System.out.printf("S12_RECEIVE_LINK_REPLAY_SUBSET collectorSha256=%s agentArchive=%s processArchive=%s agentFixtures=%s processFixtures=%s receives=2%n",
                collectorDigest, manifest.agent().execJarSha256(), manifest.process().execJarSha256(),
                manifest.agent().testClassesSha256(), manifest.process().testClassesSha256());
    }
}
