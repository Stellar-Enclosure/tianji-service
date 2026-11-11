package top.egon.cola.platform.tianji.agent.server.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueuedGrantRecoveryHandoffTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55432/tianji_it_queue_recovery_fixture";
    private static final String ROLE = "queue_fixture_owner";

    @Test
    void accepts_only_the_dedicated_loopback_queue_fixture() {
        assertEquals("tianji_it_queue_recovery_fixture", QueuedGrantRecoveryHandoff.databaseName(URL));
        for (String url : List.of(URL.replace("queue_recovery_", ""), URL.replace("127.0.0.1", "localhost"),
                URL.replace("55432", "65536"), URL + "?user=owner", URL + "/")) {
            assertThrows(IllegalArgumentException.class, () -> new QueuedGrantRecoveryHandoff(url, ROLE));
        }
        assertThrows(IllegalArgumentException.class, () -> new QueuedGrantRecoveryHandoff(URL, "owner'"));
    }

    @Test
    void round_trips_the_exact_bounded_handoff_without_disclosing_scope() throws IOException {
        var request = new QueuedGrantRecoveryHandoff(URL, ROLE);
        var output = new ByteArrayOutputStream();
        request.send(output);
        byte[] frame = output.toByteArray();
        assertEquals(request, receive(frame));
        assertFalse(request.toString().contains(URL));
        assertFalse(request.toString().contains(ROLE));
        byte[] wrongVersion = frame.clone();
        wrongVersion[0]++;
        for (byte[] invalid : List.of(wrongVersion, Arrays.copyOf(frame, frame.length - 1),
                Arrays.copyOf(frame, frame.length + 1), new byte[193])) {
            assertThrows(IllegalArgumentException.class, () -> receive(invalid));
        }
    }

    private static QueuedGrantRecoveryHandoff receive(byte[] frame) throws IOException {
        return QueuedGrantRecoveryHandoff.receive(new ByteArrayInputStream(frame));
    }
}
