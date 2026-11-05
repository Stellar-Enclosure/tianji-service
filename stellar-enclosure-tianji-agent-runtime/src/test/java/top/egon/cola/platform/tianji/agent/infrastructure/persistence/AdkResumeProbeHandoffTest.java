package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AdkResumeProbeHandoffTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/tianji_it_resume_disposable";
    private static final AgentRunLease RUN = new AgentRunLease("b2db7090-fc4c-45a2-8f31-8f39aa8362cf",
            "original-run-owner", 1L, Instant.parse("2026-11-06T00:23:10.123456Z"));
    private static final String OWNER_HASH = "a".repeat(64);

    @Test
    void disposable_resume_handoff_protocol_exists() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.persistence.AdkResumeProbeHandoff"));
    }

    @Test
    void preserves_the_exact_test_run_and_independent_capture_and_resume_facts() throws IOException {
        for (boolean typed : new boolean[] {false, true}) {
            var capture = new AdkResumeProbeHandoff.Request(URL, 100_001, typed, RUN, null, null);
            var resume = new AdkResumeProbeHandoff.Request(URL, 100_001, typed, RUN, "invocation-1", OWNER_HASH);
            assertEquals(capture, receive(send(capture)));
            assertEquals(resume, receive(send(resume)));
            assertFalse(capture.resume());
            assertTrue(resume.resume());
            assertFalse(resume.toString().contains(URL));
            assertFalse(resume.toString().contains(OWNER_HASH));
            assertEquals("tianji_it_resume_disposable", AdkResumeProbeHandoff.databaseName(URL));
        }
    }

    @Test
    void rejects_non_disposable_or_ambiguous_handoffs_before_any_connection() {
        for (String invalid : new String[] {URL.replace("127.0.0.1", "localhost"), URL.replace("5432", "65536"),
                URL.replace("tianji_it_resume_disposable", "tianji_agent"), URL + "?user=unexpected", URL + "\n"}) {
            assertThrows(IllegalArgumentException.class, () -> AdkResumeProbeHandoff.databaseName(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> new AdkResumeProbeHandoff.Request(URL, 100_000, true, RUN, null, null));
        assertThrows(IllegalArgumentException.class, () -> new AdkResumeProbeHandoff.Request(URL, 100_001, false, RUN, "inv-1", null));
        assertThrows(IllegalArgumentException.class, () -> new AdkResumeProbeHandoff.Request(URL, 100_001, false, RUN,
                "inv-1\n", OWNER_HASH));
        var wrongEpoch = new AgentRunLease(RUN.runId(), RUN.owner(), 2, RUN.expiresAt());
        assertThrows(IllegalArgumentException.class, () -> new AdkResumeProbeHandoff.Request(URL, 100_001, false, wrongEpoch, null, null));
    }

    @Test
    void rejects_unknown_format_truncation_trailing_bytes_and_an_oversized_pipe() throws IOException {
        byte[] exact = send(new AdkResumeProbeHandoff.Request(URL, 100_001, false, RUN, null, null));
        byte[] unsupported = exact.clone();
        unsupported[3] = 2;
        for (byte[] bytes : new byte[][] {unsupported, Arrays.copyOf(exact, exact.length - 1),
                Arrays.copyOf(exact, exact.length + 1), new byte[1_025]}) {
            assertThrows(IllegalArgumentException.class, () -> receive(bytes));
        }
    }

    private static byte[] send(AdkResumeProbeHandoff.Request request) throws IOException {
        var pipe = new ByteArrayOutputStream();
        AdkResumeProbeHandoff.send(pipe, request);
        return pipe.toByteArray();
    }

    private static AdkResumeProbeHandoff.Request receive(byte[] bytes) throws IOException {
        return AdkResumeProbeHandoff.receive(new ByteArrayInputStream(bytes));
    }
}
