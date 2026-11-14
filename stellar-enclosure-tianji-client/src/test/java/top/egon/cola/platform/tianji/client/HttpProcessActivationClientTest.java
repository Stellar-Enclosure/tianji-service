package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.HASH;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.NOW;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.TOKEN;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.assertRequest;

class HttpProcessActivationClientTest {
    private static final String PATH = "/api/tianji/v2/process-definitions/_order/activations";
    private static final String FIRST = "2026-11-11T08:35:00Z";
    private static final ActivationCommand COMMAND = new ActivationCommand("release-1", 0L, "act:1");
    private HttpProcessDraftClientTest.Fixture fixture;
    private HttpClient http;
    private HttpProcessActivationClient client;

    @BeforeEach
    void start() throws IOException {
        fixture = new HttpProcessDraftClientTest.Fixture();
        http = HttpClient.newHttpClient();
        client = new HttpProcessActivationClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(5), 11L);
    }

    @AfterEach
    void stop() {
        client.close();
        http.close();
        fixture.close();
    }

    @Test
    void recovers_immutable_command_receipts_independently_from_current_activation() {
        fixture.handler = request -> "POST".equals(request.method()) ? json(activation("_order", "act:1"))
                : json(page(current(), activation("_order", "act:1"), "null"));
        var applied = client.activate("_order", COMMAND);
        assertEquals("_order", applied.getDefinitionKey());
        assertEquals(Instant.parse(FIRST), applied.getActivatedAt());
        var page = client.list("_order", "act:1", 20, null);
        assertEquals(2L, page.getCurrent().revision());
        assertEquals("release-2", page.getCurrent().releaseVersion());
        assertEquals(applied, page.getItems().getFirst());
        assertRequest(fixture.take(), "POST", PATH, ActivationCommand.class, COMMAND);
        var lookup = fixture.take();
        assertRequest(lookup, "GET", PATH, null, null);
        assertTrue(List.of(lookup.query().split("&")).containsAll(List.of("commandId=act%3A1", "limit=20")));
        assertNull(fixture.poll());
    }

    @Test
    void lost_write_response_does_not_retry_and_absent_lookup_never_mutates_or_guesses() {
        fixture.handler = request -> "POST".equals(request.method()) ? null : json(page("null", "", "null"));
        ProcessClientException failure = assertThrows(ProcessClientException.class, () -> client.activate("_order", COMMAND));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
        assertRequest(fixture.take(), "POST", PATH, ActivationCommand.class, COMMAND);
        assertNull(fixture.poll());
        var empty = client.list("_order", "act:1", null, null);
        assertTrue(empty.getItems().isEmpty());
        assertNull(empty.getCurrent());
        var lookup = fixture.take();
        assertRequest(lookup, "GET", PATH, null, null);
        assertEquals("commandId=act%3A1", lookup.query());
        assertNull(fixture.poll());
    }

    @Test
    void rejects_mixed_history_resources_bad_nullable_fields_and_wrong_command_echoes() {
        var invalid = List.of(page(current(), activation("other", "act:1"), "null"),
                page(current(), activation("_order", "act-wrong"), "null"),
                page("null", activation("_order", "act:1"), "null"),
                page(current(), activation("_order", "act:1"), "\"unexpectedCursor\""),
                page(current(), activation("_order", "act:1"), "null").replace(",\"nextCursor\":null", ""),
                page(current(), activation("_order", "act:1"), "null").replace("\"revision\":1", "\"revision\":1.0"));
        for (String reply : invalid) {
            fixture.handler = request -> json(reply);
            assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                    () -> client.list("_order", "act:1", null, null)).code());
        }
        fixture.handler = request -> json(activation("_order", "act:2"));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class,
                () -> client.activate("_order", COMMAND)).code());
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(409, "application/json", """
                {"code":"TIANJI_PROCESS_ACTIVATION_CONFLICT","correlationId":null}
                """);
        var conflict = assertThrows(ProcessClientException.class, () -> client.activate("_order", COMMAND));
        assertEquals(409, conflict.httpStatus());
        assertEquals("TIANJI_PROCESS_ACTIVATION_CONFLICT", conflict.code());
        for (int index = 0; index < invalid.size() + 2; index++) fixture.take();
        assertThrows(IllegalArgumentException.class, () -> client.list("_order", "act:1", null, "otherCursor"));
        assertNull(fixture.poll());
    }

    @Test
    void rejects_redirecting_clients_and_keeps_optional_tenant_assertion_absent() {
        try (HttpClient redirecting = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HttpProcessActivationClient(fixture.base, redirecting, () -> TOKEN, Duration.ofSeconds(5)));
        }
        fixture.handler = request -> new HttpProcessDraftClientTest.Reply(401, "application/json", "{\"code\":\"TIANJI_UNAUTHENTICATED\"}");
        try (var unasserted = new HttpProcessActivationClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(5))) {
            var failure = assertThrows(ProcessClientException.class, () -> unasserted.list("_order", null, null, null));
            assertEquals(401, failure.httpStatus());
            assertEquals("TIANJI_UNAUTHENTICATED", failure.code());
        }
        assertNull(fixture.take().tenant());
        assertNull(fixture.poll());
    }

    private static HttpProcessDraftClientTest.Reply json(String body) { return HttpProcessDraftClientTest.Reply.json(body); }

    private static String page(String current, String items, String cursor) {
        return """
                {"definitionKey":"_order","observedAt":"%s","current":%s,"items":[%s],"nextCursor":%s}
                """.formatted(NOW, current, items, cursor);
    }

    private static String current() {
        return """
                {"revision":2,"releaseVersion":"release-2","activatedAt":"%s"}
                """.formatted(NOW);
    }

    private static String activation(String key, String commandId) {
        return """
                {"definitionKey":"%s","revision":1,"releaseVersion":"release-1","previousRevision":null,
                 "commandId":"%s","expectedActivationRevision":0,"sourceManifestSha256":"%s",
                 "compiledManifestSha256":"%s","dependencyManifestSha256":"%s","activatedAt":"%s"}
                """.formatted(key, commandId, HASH, HASH, HASH, FIRST);
    }
}
