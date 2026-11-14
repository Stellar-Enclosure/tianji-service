package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.V2;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.request;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.response;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.JSON;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.TOKEN;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.assertRequest;

class HttpProcessEventClientTest {
    private static final String RECEIVE = V2 + "process-events";
    private static final String RECEIPT = V2 + "process-event-receipts/{receiptId}";
    private HttpProcessDraftClientTest.Fixture fixture;
    private HttpClient http;
    private HttpProcessEventClient client;

    @BeforeEach void start() throws IOException {
        fixture = new HttpProcessDraftClientTest.Fixture();
        http = HttpClient.newHttpClient();
        client = new HttpProcessEventClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(5), 11L);
    }

    @AfterEach void stop() {
        client.close();
        http.close();
        fixture.close();
    }

    @Test void event_acceptance_and_query_preserve_original_admission_and_use_separate_routes() {
        var event = event();
        fixture.handler = request -> "POST".equals(request.method()) ? response(RECEIVE, "post", 202) : response(RECEIPT, "get", 200);
        var accepted = client.receive(event);
        assertEquals("RECEIVED", accepted.getState());
        assertEquals("evt-101", accepted.getEventId());
        assertEquals("auto-2", accepted.getReleaseVersion());
        assertEquals(2L, accepted.getActivationRevision());
        assertEquals(accepted, client.getReceipt("er-101"));
        var post = fixture.take();
        assertRequest(post, "POST", RECEIVE, ProcessEventCommand.class, event);
        var body = JSON.readTree(post.body());
        assertFalse(body.has("releaseVersion"));
        assertFalse(body.has("mode"));
        assertEquals(true, body.path("waitToken").isNull());
        assertRequest(fixture.take(), "GET", V2 + "process-event-receipts/er-101", null, null);
        assertNull(fixture.poll());
    }

    @Test void lost_event_response_does_not_retry_and_foreign_or_malformed_receipts_fail_closed() {
        fixture.handler = request -> null;
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(ProcessClientException.class, () -> client.receive(event())).code());
        assertRequest(fixture.take(), "POST", RECEIVE, ProcessEventCommand.class, event());
        assertNull(fixture.poll());
        var good = response(RECEIVE, "post", 202).body();
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(202, "application/json", good.replace("evt-101", "evt-other"));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.receive(event())).code());
        fixture.take();
        fixture.handler = request -> HttpProcessDraftClientTest.Reply.json(good.replace("er-101", "er-foreign"));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.getReceipt("er-101")).code());
        fixture.take();
        fixture.handler = request -> HttpProcessDraftClientTest.Reply.json(good);
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.receive(event())).code());
        fixture.take();
        assertThrows(IllegalArgumentException.class, () -> client.receive(event().setWaitToken("unsafe!")));
        assertThrows(IllegalArgumentException.class, () -> client.receive(event().setPayload(Map.of("value", 1.5))));
        assertNull(fixture.poll());
    }

    @Test void preserves_current_source_permission_and_route_conflict_errors() {
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(409, "application/json", """
                {"code":"TIANJI_EVENT_ROUTE_VERSION_CONFLICT","correlationId":null}
                """);
        assertEquals("TIANJI_EVENT_ROUTE_VERSION_CONFLICT", assertThrows(ProcessClientException.class, () -> client.receive(event())).code());
        fixture.take();
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(403, "application/json", "{\"code\":\"TIANJI_PERMISSION_DENIED\"}");
        assertEquals("TIANJI_PERMISSION_DENIED", assertThrows(ProcessClientException.class, () -> client.getReceipt("er-101")).code());
        fixture.take();
        assertNull(fixture.poll());
    }

    @Test void replay_preserves_the_original_admission_and_can_observe_a_terminal_result() {
        var admitted = JSON.readTree(response(RECEIVE, "post", 202).body());
        var applied = (tools.jackson.databind.node.ObjectNode) admitted;
        applied.put("state", "APPLIED");
        applied.put("instanceId", "pi-original");
        applied.put("decidedAt", "2026-11-12T08:41:00Z");
        applied.put("decisionCode", "APPLIED");
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(202, "application/json", JSON.writeValueAsString(applied));
        var replay = client.receive(event());
        assertRequest(fixture.take(), "POST", RECEIVE, ProcessEventCommand.class, event());
        assertEquals("er-101", replay.getReceiptId());
        assertEquals("APPLIED", replay.getState());
        assertEquals("auto-2", replay.getReleaseVersion());
        assertEquals(2L, replay.getActivationRevision());
        assertEquals(Instant.parse("2026-11-12T08:40:00Z"), replay.getAdmittedAt());
        assertEquals("pi-original", replay.getInstanceId());

        applied.put("decisionCode", "LATE");
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.receive(event())).code());
        fixture.take();
        assertNull(fixture.poll());
    }

    private static ProcessEventCommand event() { return request(RECEIVE, "post", ProcessEventCommand.class); }
}
