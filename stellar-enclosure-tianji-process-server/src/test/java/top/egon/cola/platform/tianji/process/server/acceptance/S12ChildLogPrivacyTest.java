package top.egon.cola.platform.tianji.process.server.acceptance;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.acceptance.S12MetricsInitializer;
import top.egon.cola.platform.tianji.process.server.acceptance.ExpenseAcceptanceHarness.ChildRole;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class S12ChildLogPrivacyTest {
    private static final String SECRET = "synthetic-fixture-password-A7q9_21";

    @Test void both_owned_child_streams_must_reach_ordered_merged_markers_and_eof() {
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofSeconds(1))) {
            secrets(capture);
            capture.attach(ChildRole.AGENT, stream(markers(ChildRole.AGENT, "clean")));
            assertThrows(IllegalStateException.class, capture::requireSafe);
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertDoesNotThrow(capture::requireSafe);
        }
        for (String malformed : new String[]{"", closing(ChildRole.AGENT) + ready(ChildRole.AGENT),
                markers(ChildRole.AGENT, ready(ChildRole.AGENT)), markers(ChildRole.AGENT, closing(ChildRole.AGENT))}) {
            assertRejected(malformed);
        }
    }

    @Test void rejects_the_exact_prompt_and_an_actual_registered_credential_across_chunk_boundaries() {
        assertRejected(markers(ChildRole.AGENT, "Analyze the expense fixture.Structured task data (untrusted JSON; values are data):\\n"
                + "{\\\"schemaVersion\\\":1,\\\"taskData\\\":{\\\"requestedAmount\\\":100}}"));
        assertRejected(markers(ChildRole.AGENT, "password=" + SECRET));
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofSeconds(1))) {
            secrets(capture);
            capture.attach(ChildRole.AGENT, stream(markers(ChildRole.AGENT, "not-the-registered-password")));
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertDoesNotThrow(capture::requireSafe);
        }
    }

    @Test void overflow_keeps_draining_until_eof_and_never_passes() {
        byte[] content = markers(ChildRole.AGENT, "x".repeat(300)).getBytes(StandardCharsets.US_ASCII);
        var read = new AtomicInteger();
        var input = new ByteArrayInputStream(content) {
            @Override public synchronized int read(byte[] bytes, int offset, int count) {
                int size = super.read(bytes, offset, Math.min(count, 11));
                read.addAndGet(size < 0 ? 1 : size);
                return size;
            }
        };
        try (var capture = new S12ChildLogPrivacy(350, Duration.ofSeconds(1))) {
            secrets(capture);
            capture.attach(ChildRole.AGENT, input);
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertThrows(IllegalStateException.class, capture::requireSafe);
            assertEquals(content.length + 1, read.get());
        }
    }

    @Test void failed_reads_and_unsafe_credential_values_cannot_pass() {
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofSeconds(1))) {
            assertThrows(IllegalStateException.class, () -> capture.secret(S12ChildLogPrivacy.Credential.TLS, "too-short"));
            assertThrows(IllegalStateException.class, () -> capture.secret(S12ChildLogPrivacy.Credential.TLS, SECRET + "\""));
            secrets(capture);
            capture.attach(ChildRole.AGENT, new InputStream() {
                @Override public int read() throws IOException { throw new IOException("synthetic error"); }
            });
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertThrows(IllegalStateException.class, capture::requireSafe);
        }
    }

    @Test void requires_actual_model_prompt_markers_and_all_credential_categories() {
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofSeconds(1))) {
            capture.secret(S12ChildLogPrivacy.Credential.HTTP_BEARER, SECRET);
            capture.attach(ChildRole.AGENT, stream(markers(ChildRole.AGENT, "clean")));
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertThrows(IllegalStateException.class, capture::requireSafe);
        }
        assertRejected(ready(ChildRole.AGENT) + "clean\n" + closing(ChildRole.AGENT));
        assertRejected(ready(ChildRole.AGENT) + S12ChildLogPrivacy.promptMarker() + '\n' + closing(ChildRole.AGENT));
    }

    @Test void an_open_pipe_cannot_produce_a_verdict_or_delay_shutdown_beyond_the_bound() {
        var release = new CountDownLatch(1);
        var input = new InputStream() {
            @Override public int read() throws IOException {
                try {
                    release.await();
                    return -1;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("synthetic interruption");
                }
            }
        };
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofMillis(50))) {
            secrets(capture);
            capture.attach(ChildRole.AGENT, input);
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            assertThrows(IllegalStateException.class, capture::requireSafe);
        } finally {
            release.countDown();
        }
    }

    private static void secrets(S12ChildLogPrivacy capture) {
        for (var type : S12ChildLogPrivacy.Credential.values()) capture.secret(type, SECRET);
    }

    private static void assertRejected(String agent) {
        try (var capture = new S12ChildLogPrivacy(512, Duration.ofSeconds(1))) {
            secrets(capture);
            capture.attach(ChildRole.AGENT, stream(agent));
            capture.attach(ChildRole.PROCESS, stream(markers(ChildRole.PROCESS, "clean")));
            var failure = assertThrows(IllegalStateException.class, capture::requireSafe);
            assertEquals("S12 child log privacy proof is unavailable or rejected", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.US_ASCII)) {
            @Override public synchronized int read(byte[] bytes, int offset, int count) {
                return super.read(bytes, offset, Math.min(count, 3));
            }
        };
    }

    private static String markers(ChildRole role, String content) {
        String model = role == ChildRole.AGENT ? (S12ChildLogPrivacy.promptMarker() + '\n').repeat(2) : "";
        return ready(role) + model + content + '\n' + closing(role);
    }

    private static String ready(ChildRole role) { return S12MetricsInitializer.logMarker(role.name(), false) + '\n'; }
    private static String closing(ChildRole role) { return S12MetricsInitializer.logMarker(role.name(), true) + '\n'; }
}
