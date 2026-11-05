package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff.Phase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AgentCheckpointForkHandoffTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/tianji_it_checkpoint_disposable";
    private static final String SETUP = "checkpoint_fixture_setup";
    private static final String RUNTIME = "tianji_it_checkpoint_runtime";
    private static final String VECTOR = "0.8.3";
    private static final String RUN = "b2db7090-fc4c-45a2-8f31-8f39aa8362cf";
    private static final String DIGEST = "a".repeat(64);

    @Test
    void disposable_checkpoint_fork_handoff_protocol_exists() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentCheckpointForkHandoff"));
    }

    @Test
    void preserves_two_codec_variants_and_binds_reconciliation_to_the_capture_digest() throws IOException {
        for (boolean typed : new boolean[] {false, true}) {
            var capture = request(typed, null);
            var reconcile = request(typed, DIGEST);
            byte[] captureFrame = send(capture);
            byte[] reconcileFrame = send(reconcile);
            assertEquals(0, captureFrame[phaseOffset(captureFrame)]);
            assertEquals(1, reconcileFrame[phaseOffset(reconcileFrame)]);
            assertEquals(capture, receive(captureFrame));
            assertEquals(reconcile, receive(reconcileFrame));
            assertEquals(Phase.CAPTURE, capture.phase());
            assertEquals(Phase.RECONCILE, reconcile.phase());
            assertEquals(typed ? 90_102 : 90_101, AgentCheckpointForkHandoff.tenant(typed));
            for (String protectedValue : new String[] {URL, SETUP, RUNTIME, RUN, DIGEST}) {
                assertFalse(reconcile.toString().contains(protectedValue));
            }
        }
    }

    @Test
    void round_trips_native_resume_as_a_distinct_digest_bound_phase() throws IOException {
        for (boolean typed : new boolean[] {false, true}) {
            var resume = new AgentCheckpointForkHandoff.Request(URL, SETUP, RUNTIME, VECTOR, typed, RUN,
                    Phase.NATIVE_RESUME, DIGEST);
            byte[] frame = send(resume);
            var decoded = receive(frame);
            assertEquals(Phase.NATIVE_RESUME, decoded.phase());
            assertEquals(2, frame[phaseOffset(frame)]);
            assertEquals(resume, decoded);
            assertFalse(decoded.toString().contains(DIGEST));
        }
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, RUNTIME, VECTOR, true, RUN, Phase.NATIVE_RESUME, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, RUNTIME, VECTOR, true, RUN, Phase.CAPTURE, DIGEST));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, RUNTIME, VECTOR, true, RUN, null, DIGEST));
    }

    @Test
    void rejects_unscoped_or_ambiguous_requests_before_a_connection() {
        for (String url : new String[] {URL.replace("127.0.0.1", "localhost"), URL.replace("5432", "65536"),
                URL.replace("tianji_it_checkpoint_disposable", "tianji_agent"), URL + "?user=other", URL + "\n"}) {
            assertThrows(IllegalArgumentException.class, () -> AgentCheckpointForkHandoff.databaseName(url));
        }
        assertEquals("tianji_it_checkpoint_disposable", AgentCheckpointForkHandoff.databaseName(URL));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, RUNTIME, RUNTIME, VECTOR, true, RUN, Phase.CAPTURE, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, "postgres", VECTOR, true, RUN, Phase.CAPTURE, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, RUNTIME, VECTOR + "beta", true, RUN, Phase.CAPTURE, null));
        assertThrows(IllegalArgumentException.class, () -> new AgentCheckpointForkHandoff.Request(
                URL, SETUP, RUNTIME, VECTOR, true, RUN + " ", Phase.CAPTURE, null));
        assertThrows(IllegalArgumentException.class, () -> request(true, "unknown"));
    }

    @Test
    void rejects_unknown_format_noncanonical_flags_truncation_trailing_and_oversized_frames() throws IOException {
        byte[] exact = send(request(false, DIGEST));
        byte[] wrongFormat = exact.clone();
        wrongFormat[3] = 2;
        int phaseOffset = phaseOffset(exact);
        byte[] wrongCodec = exact.clone();
        wrongCodec[phaseOffset - RUN.length() - 3] = 2;
        byte[] wrongPhase = exact.clone();
        wrongPhase[phaseOffset] = 3;
        for (byte[] rejected : new byte[][] {wrongFormat, wrongCodec, wrongPhase, Arrays.copyOf(exact, exact.length - 1),
                Arrays.copyOf(exact, exact.length + 1), new byte[513]}) {
            assertThrows(IllegalArgumentException.class, () -> receive(rejected));
        }
    }

    private static int phaseOffset(byte[] exact) throws IOException {
        try (var frame = new DataInputStream(new ByteArrayInputStream(exact))) {
            frame.readInt();
            for (int i = 0; i < 4; i++) frame.readUTF();
            frame.readBoolean();
            frame.readUTF();
            return exact.length - frame.available();
        }
    }

    private static AgentCheckpointForkHandoff.Request request(boolean typed, String digest) {
        return new AgentCheckpointForkHandoff.Request(URL, SETUP, RUNTIME, VECTOR, typed, RUN,
                digest == null ? Phase.CAPTURE : Phase.RECONCILE, digest);
    }

    private static byte[] send(AgentCheckpointForkHandoff.Request request) throws IOException {
        var pipe = new ByteArrayOutputStream();
        AgentCheckpointForkHandoff.send(pipe, request);
        return pipe.toByteArray();
    }

    private static AgentCheckpointForkHandoff.Request receive(byte[] bytes) throws IOException {
        return AgentCheckpointForkHandoff.receive(new ByteArrayInputStream(bytes));
    }
}
