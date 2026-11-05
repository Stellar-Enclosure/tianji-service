package top.egon.cola.platform.tianji.agent.server.api;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentCheckpointResumeWitnessTest {
    private static final String RUN_ID = "6b9a690c-c1c2-4bb0-ae68-0d7e335034d4";
    private static final String ACK = """
            {"runId":"%s","status":"RUNNING","attempt":1,"revision":8}
            """.formatted(RUN_ID);
    private static final Acknowledgement EXPECTED = new Acknowledgement(RUN_ID, AgentRunStatusEnum.RUNNING, 1, 8);

    @Test
    void accepts_only_the_original_exact_acknowledgement() {
        assertEquals(EXPECTED, decode(ACK));
        for (var body : List.of(ACK.replace("RUNNING", "COMPLETED"), ACK.replace(RUN_ID, "another"),
                ACK.replace("\"attempt\":1", "\"attempt\":2"), ACK.replace("\"revision\":8", "\"revision\":9"),
                ACK.replace("\"revision\":8", "\"revision\":\"8\""), ACK.replace("\"revision\":8", "\"revision\":8.0"),
                ACK.replace("\"revision\":8", "\"revision\":18446744073709551624"),
                ACK.replace("\"revision\":8", "\"revision\":7,\"revision\":8"),
                ACK.replace("\"revision\":8", "\"revision\":8,\"extra\":null"), ACK + "{}",
                ACK + " ".repeat(16_384))) {
            assertThrows(IllegalArgumentException.class, () -> decode(body));
        }
    }

    @Test
    void allows_only_the_exact_explicit_loopback_https_origin() {
        var origin = URI.create("https://127.0.0.1:8443");
        assertEquals(origin, AgentCheckpointResumeHttpPostgresTest.loopbackOrigin(origin.toString()));
        for (var bad : List.of("http://127.0.0.1:8443", "https://localhost:8443", "https://127.0.0.2:8443",
                "https://127.0.0.1", "https://127.0.0.1:65536", "https://127.0.0.1:08443",
                "https://user@127.0.0.1:8443", "https://127.0.0.1:8443/",
                "https://127.0.0.1:8443?target=other", "https://127.0.0.1:8443#redirect")) {
            assertThrows(IllegalArgumentException.class, () -> AgentCheckpointResumeHttpPostgresTest.loopbackOrigin(bad));
        }
    }

    private static Acknowledgement decode(String json) {
        return AgentCheckpointResumeHttpPostgresTest.readAcknowledgement(json.getBytes(StandardCharsets.UTF_8), EXPECTED);
    }
}
