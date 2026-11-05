package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class S12MetricsCollectorEvidenceTest {
    private static final Instant START = Instant.parse("2026-11-06T00:00:00Z");
    private static final Instant END = START.plusSeconds(3);

    @Test
    void requires_exactly_one_completed_collector_record_from_each_role() {
        String agent = line("agent", "tianji.agent.run.initial.claim.delay", "");
        String process = line("process", "tianji.process.agent.result.inbox.decision.delay",
                "\"attributes\":[{\"key\":\"outcome\",\"value\":{\"stringValue\":\"applied\"}}],");
        assertFalse(verify(""));
        assertFalse(verify(agent));
        assertFalse(verify(agent + process.stripTrailing()));
        assertFalse(verify(agent + process + "partial-third-record"));
        assertTrue(verify(agent + process));
        assertTrue(verify(process + agent));
        assertThrows(IllegalStateException.class, () -> verify(agent + agent + process));
    }

    @Test
    void rejects_sensitive_fields_incomplete_counts_stale_samples_and_duplicate_keys() {
        String agent = line("agent", "tianji.agent.run.initial.claim.delay", "");
        String process = line("process", "tianji.process.agent.result.inbox.decision.delay",
                "\"attributes\":[{\"key\":\"outcome\",\"value\":{\"stringValue\":\"applied\"}}],");
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("applied", "dead")));
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("\"outcome\"", "\"runId\"")));
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("\"count\":\"2\"", "\"count\":\"1\"")));
        assertThrows(IllegalStateException.class,
                () -> verify(agent + process.replace("\"aggregationTemporality\":2", "\"aggregationTemporality\":4294967298")));
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("\"sum\":0.5", "\"sum\":0.5,\"sum\":0.5")));
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("1793923202000000000", "1793923204000000000")));
        assertThrows(IllegalStateException.class, () -> verify(agent + process.replace("\"unit\":\"s\"", "\"unit\":\"s\",\"secret\":\"x\"")));
        assertThrows(IllegalStateException.class, () -> verify("{}\n" + process.stripTrailing()));
        assertThrows(IllegalStateException.class, () -> verify("x".repeat(8_193)));
    }

    @Test
    void allows_only_real_absence_and_rejects_dangling_links(@TempDir Path directory) throws Exception {
        directory = directory.toRealPath();
        Path file = directory.resolve(S12MetricsCollectorEvidence.METRICS_FILE);
        assertArrayEquals(new byte[0], S12MetricsCollectorEvidence.readAvailable(file));
        Files.createSymbolicLink(file, directory.resolve("missing"));
        assertThrows(java.io.IOException.class, () -> S12MetricsCollectorEvidence.readAvailable(file));
        Files.delete(file);
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        assertArrayEquals(new byte[0], S12MetricsCollectorEvidence.readAvailable(file));
    }

    private static boolean verify(String body) {
        return S12MetricsCollectorEvidence.verified(body.getBytes(StandardCharsets.UTF_8), START, END);
    }

    private static String line(String role, String meter, String attributes) {
        return """
                {"resourceMetrics":[{"resource":{"attributes":[{"key":"service.name","value":{"stringValue":"tianji-s12-%s"}}]},"scopeMetrics":[{"scope":{"name":"top.egon.cola.tianji.s12.metrics"},"metrics":[{"name":"%s","unit":"s","histogram":{"aggregationTemporality":2,"dataPoints":[{%s"startTimeUnixNano":"1793923201000000000","timeUnixNano":"1793923202000000000","count":"2","sum":0.5,"bucketCounts":["2"]}]}}]}]}]}
                """.formatted(role, meter, attributes);
    }
}
