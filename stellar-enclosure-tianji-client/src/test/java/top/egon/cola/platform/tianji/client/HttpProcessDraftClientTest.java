package top.egon.cola.platform.tianji.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpProcessDraftClientTest {
    static final String TOKEN = "test-process-secret";
    static final String NOW = "2026-11-12T08:35:00.123456Z";
    static final String HASH = "a".repeat(64);
    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE = "/api/tianji/v2/";
    private static final String DRAFT_PATH = BASE + "process-drafts/pd-101";
    private static final DraftContentVO CONTENT = new DraftContentVO(List.of(new DraftModelSourceVO("BPMN", "", " <未完成\r\n\t")),
            " {\"unfinished\":", null, " \n [] ", null);
    private Fixture fixture;
    private HttpClient http;
    private HttpProcessDraftClient client;

    @BeforeEach
    void start() throws IOException {
        fixture = new Fixture();
        http = HttpClient.newHttpClient();
        client = new HttpProcessDraftClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(5), 11L);
    }

    @AfterEach
    void stop() {
        client.close();
        http.close();
        fixture.close();
    }

    @Test
    void calls_all_draft_routes_and_preserves_the_original_editor_text() {
        fixture.handler = request -> switch (request.method + " " + request.path) {
            case "POST /api/tianji/v2/process-definitions/order/drafts" -> Reply.json(summary(1, "EDITING"));
            case "GET /api/tianji/v2/process-drafts/pd-101" -> Reply.json(detail(CONTENT, "null"));
            case "PUT /api/tianji/v2/process-drafts/pd-101" -> Reply.json(mutation("EDITING", "save-1"));
            case "GET /api/tianji/v2/process-drafts" -> Reply.json(page(summary(1, "EDITING"), "null"));
            case "POST /api/tianji/v2/process-drafts/pd-101/discard-commands" -> Reply.json(mutation("DISCARDED", "discard-1"));
            case "POST /api/tianji/v2/process-model-validations" -> Reply.json(validation());
            case "POST /api/tianji/v2/process-releases" -> Reply.json(publication());
            default -> new Reply(404, "application/json", "{}");
        };
        var create = new CreateProcessDraftCommand(" Order ", null, "create-1");
        var save = new SaveProcessDraftCommand("save-1", 2, " Order ", CONTENT);
        var discard = new DiscardProcessDraftCommand("discard-1", 2);
        var validate = new ValidateProcessDraftQuery("pd-101", 2, HASH);
        var publish = new PublishProcessDraftCommand("pd-101", 2, HASH, "release-1", "publish-1");
        assertEquals(1L, client.create("order", create).getRevision());
        assertEquals(CONTENT, client.get("pd-101", "save:1").getContent());
        assertEquals(DraftMutationResultDTO.StateEnum.EDITING, client.save("pd-101", save).getState());
        assertEquals(DraftSummaryDTO.StateEnum.EDITING, client.list("order", "EDITING", 10, "cursor+/?= 甲", null).getItems().getFirst().getState());
        assertEquals(DraftMutationResultDTO.StateEnum.DISCARDED, client.discard("pd-101", discard).getState());
        assertFalse(client.validate(validate).isValid());
        assertEquals("release-1", client.publish(publish).releaseVersion());

        assertRequest(fixture.take(), "POST", BASE + "process-definitions/order/drafts", CreateProcessDraftCommand.class, create);
        Seen read = fixture.take();
        assertRequest(read, "GET", DRAFT_PATH, null, null);
        assertEquals("commandId=save%3A1", read.query);
        assertRequest(fixture.take(), "PUT", DRAFT_PATH, SaveProcessDraftCommand.class, save);
        Seen list = fixture.take();
        assertRequest(list, "GET", BASE + "process-drafts", null, null);
        assertTrue(list.query.contains("cursor=cursor%2B%2F%3F%3D%20%E7%94%B2"));
        assertRequest(fixture.take(), "POST", DRAFT_PATH + "/discard-commands", DiscardProcessDraftCommand.class, discard);
        assertRequest(fixture.take(), "POST", BASE + "process-model-validations", ValidateProcessDraftQuery.class, validate);
        assertRequest(fixture.take(), "POST", BASE + "process-releases", PublishProcessDraftCommand.class, publish);
        assertNull(fixture.poll());
    }

    @Test
    void saves_and_reads_back_a_large_legal_draft() {
        String text = "界".repeat(1_350_000);
        DraftContentVO large = new DraftContentVO(null, text, null, null, null);
        fixture.handler = request -> "PUT".equals(request.method) ? Reply.json(mutation("EDITING", "save-1")) : Reply.json(detail(large, "null"));
        assertEquals(3L, client.save("pd-101", new SaveProcessDraftCommand("save-1", 2, " Order ", large)).getRevision());
        assertEquals(text, client.get("pd-101", null).getContent().inputSchemaSource());
        assertTrue(fixture.take().body.length > 4_000_000);
        assertEquals(0, fixture.take().body.length);
        assertThrows(IllegalArgumentException.class,
                () -> new DraftContentVO(null, text + "界".repeat(60_000), null, null, null));
    }

    @Test
    void response_loss_after_save_preserves_one_write_and_the_original_receipt() {
        fixture.handler = request -> "PUT".equals(request.method) ? null : Reply.json(detail(CONTENT, """
                {"commandId":"save-1","operation":"SAVE","requestSha256":"%s","resultRevision":2,
                 "resultContentSha256":"%s","releaseVersion":null,"completedAt":"%s"}
                """.formatted(HASH, HASH, NOW)));
        var command = new SaveProcessDraftCommand("save-1", 1, " Order ", CONTENT);
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(ProcessClientException.class,
                () -> client.save("pd-101", command)).code());
        assertRequest(fixture.take(), "PUT", DRAFT_PATH, SaveProcessDraftCommand.class, command);
        assertNull(fixture.poll());
        var draft = client.get("pd-101", "save-1");
        assertEquals(3L, draft.getRevision());
        assertEquals(2L, draft.getCommandReceipt().getResultRevision());
        assertEquals("commandId=save-1", fixture.take().query);
        assertNull(fixture.poll());
    }

    @Test
    void checks_closed_shapes_echoes_and_http_errors_without_leaking_bearer() {
        var command = new SaveProcessDraftCommand("save-1", 2, " Order ", CONTENT);
        fixture.handler = request -> new Reply(409, "application/json", """
                {"code":"TIANJI_DRAFT_VERSION_CONFLICT","correlationId":"corr-101"}
                """);
        ProcessClientException conflict = assertThrows(ProcessClientException.class, () -> client.save("pd-101", command));
        assertEquals(409, conflict.httpStatus());
        assertEquals("TIANJI_DRAFT_VERSION_CONFLICT", conflict.code());
        assertEquals("corr-101", conflict.correlationId());
        fixture.take();
        assertNull(fixture.poll());

        var invalid = List.of(
                mutation("EDITING", "save-1").replace("\"revision\":3", "\"revision\":3e0"),
                mutation("EDITING", "save-1").replace("\"revision\":3", "\"revision\":1000000000000000"),
                mutation("EDITING", "save-1").replace("\"revision\":3", "\"revision\":3,\"revision\":3"),
                mutation("EDITING", "save-1").replace("\"revision\":3", "\"revision\":3,\"unknown\":1"),
                mutation("EDITING", "save-1").replace("pd-101", "pd-102"),
                mutation("EDITING", "save-2"), mutation("UNKNOWN", "save-1"),
                mutation("EDITING", "save-1").replace(NOW, "2026-11-12T08:35:00+00:00"),
                mutation("EDITING", "save-1") + "{}");
        for (String body : invalid) {
            fixture.handler = request -> Reply.json(body);
            assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.save("pd-101", command)).code());
        }
        fixture.handler = request -> new Reply(201, "application/json", mutation("EDITING", "save-1"));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.save("pd-101", command)).code());
        fixture.handler = request -> new Reply(200, "text/html", mutation("EDITING", "save-1"));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.save("pd-101", command)).code());
        fixture.handler = request -> new Reply(403, "application/json", "{\"code\":\"TIANJI_PERMISSION_DENIED\",\"correlationId\":\"" + TOKEN + "\"}");
        ProcessClientException denied = assertThrows(ProcessClientException.class, () -> client.get("pd-101", null));
        assertEquals("TIANJI_PERMISSION_DENIED", denied.code());
        assertNull(denied.correlationId());
        assertNull(denied.getCause());
        assertFalse(denied.toString().contains(TOKEN));
    }

    @Test
    void creation_lookup_bypasses_default_editing_and_forbids_mixed_filters() {
        fixture.handler = request -> Reply.json(page(summary(3, "DISCARDED"), "null"));
        assertEquals(DraftSummaryDTO.StateEnum.DISCARDED, client.list(null, null, null, null, "create-1").getItems().getFirst().getState());
        Seen lookup = fixture.take();
        assertEquals("GET", lookup.method);
        assertEquals("creationCommandId=create-1", lookup.query);
        assertThrows(IllegalArgumentException.class, () -> client.list("order", null, null, null, "create-1"));
        assertThrows(IllegalArgumentException.class, () -> client.get("pd-101/../../secrets", null));
        assertNull(fixture.poll());
    }

    @Test
    void shared_transport_preserves_a_maximum_length_native_identity_as_one_encoded_segment() {
        String id = "n".repeat(252) + "/甲?";
        String prefix = BASE + "process-instances/";
        fixture.handler = request -> Reply.json("{}");
        try (var transport = new AutomationHttpTransport(fixture.base, http, () -> TOKEN, Duration.ofSeconds(3), null)) {
            String path = prefix + AutomationHttpTransport.opaqueSegment(id);
            assertEquals(prefix + "n".repeat(252) + "%2F%E7%94%B2%3F", path);
            assertEquals(200, transport.send("GET", path, Map.of(), null, 0, 200, 4096, Set.of()).status());
            assertEquals(path, fixture.take().path());
            assertThrows(IllegalArgumentException.class, () -> AutomationHttpTransport.opaqueSegment(id + "n"));
            assertThrows(IllegalArgumentException.class, () -> AutomationHttpTransport.opaqueSegment(".."));
            assertThrows(IllegalArgumentException.class, () ->
                    transport.send("GET", prefix + "%2E%2E", Map.of(), null, 0, 200, 4096, Set.of()));
            assertNull(fixture.poll());
        }
    }

    @Test
    void bounds_response_buffers_and_requires_no_store_on_success() {
        fixture.handler = request -> Reply.json(" ".repeat(AutomationHttpTransport.MAX_DRAFT_RESPONSE_BYTES + 1));
        var oversized = assertThrows(ProcessClientException.class, () -> client.get("pd-101", null));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", oversized.code());
        assertEquals("GET", fixture.take().method());

        fixture.handler = request -> new Reply(200, "application/json", detail(CONTENT, "null"), null);
        var unprotected = assertThrows(ProcessClientException.class, () -> client.get("pd-101", null));
        assertEquals(200, unprotected.httpStatus());
        assertEquals("TIANJI_INVALID_RESPONSE", unprotected.code());
        assertEquals("GET", fixture.take().method());
        assertNull(fixture.poll());
    }

    @Test
    void shared_request_publisher_sends_its_snapshot_only_on_first_subscription() throws Exception {
        byte[] bytes = "original".getBytes(StandardCharsets.UTF_8);
        var publisher = new AutomationHttpTransport.SingleSendBody(bytes);
        bytes[0] = 0;
        var first = new RecordingSubscriber();
        publisher.subscribe(first);
        assertArrayEquals("original".getBytes(StandardCharsets.UTF_8), first.completed.get(1, TimeUnit.SECONDS));
        var second = new RecordingSubscriber();
        publisher.subscribe(second);
        assertThrows(java.util.concurrent.ExecutionException.class, () -> second.completed.get(1, TimeUnit.SECONDS));
        assertEquals(0, second.received.size());
    }

    private static final class RecordingSubscriber implements Flow.Subscriber<ByteBuffer> {
        final CompletableFuture<byte[]> completed = new CompletableFuture<>();
        final ByteArrayOutputStream received = new ByteArrayOutputStream();
        private boolean subscribed;

        @Override public void onSubscribe(Flow.Subscription subscription) {
            subscribed = true;
            subscription.request(Long.MAX_VALUE);
        }

        @Override public void onNext(ByteBuffer buffer) {
            byte[] chunk = new byte[buffer.remaining()];
            buffer.get(chunk);
            received.writeBytes(chunk);
        }

        @Override public void onError(Throwable failure) {
            assertTrue(subscribed);
            completed.completeExceptionally(failure);
        }

        @Override public void onComplete() { completed.complete(received.toByteArray()); }
    }

    static void assertRequest(Seen seen, String method, String path, Class<?> type, Object value) {
        assertEquals(method, seen.method);
        assertEquals(path, seen.path);
        assertEquals("Bearer " + TOKEN, seen.authorization);
        assertEquals("application/json", seen.accept);
        assertEquals("11", seen.tenant);
        if (type == null) {
            assertEquals(0, seen.body.length);
        } else {
            assertEquals("application/json", seen.contentType);
            assertEquals(value, JSON.readValue(seen.body, type));
        }
    }

    private static String summary(int revision, String state) {
        return """
                {"draftId":"pd-101","definitionKey":"order","title":" Order ","baseReleaseVersion":null,
                 "state":"%s","revision":%d,"contentSha256":"%s","publishedReleaseVersion":null,
                 "createdAt":"%s","updatedAt":"%s","availableActions":["VALIDATE"]}
                """.formatted(state, revision, HASH, NOW, NOW).strip();
    }

    private static String detail(DraftContentVO content, String receipt) {
        String summary = summary(3, "EDITING");
        return summary.substring(0, summary.length() - 1) + """
                ,"baseSourceManifestSha256":null,"content":%s,"lastCommandId":"later",
                "publication":null,"commandReceipt":%s}
                """.formatted(JSON.writeValueAsString(content), receipt);
    }

    private static String page(String item, String cursor) {
        return """
                {"observedAt":"%s","items":[%s],"nextCursor":%s}
                """.formatted(NOW, item, cursor);
    }

    private static String mutation(String state, String commandId) {
        return """
                {"draftId":"pd-101","state":"%s","revision":3,"contentSha256":"%s",
                 "updatedAt":"%s","commandId":"%s"}
                """.formatted(state, HASH, NOW, commandId);
    }

    private static String validation() {
        return """
                {"draftId":"pd-101","revision":2,"contentSha256":"%s","valid":false,"compilerVersion":"1",
                 "sourceManifestSha256":null,"graph":null,"issues":[{"path":"models","code":"INVALID_MODEL","message":"Invalid model","nodeId":null}]}
                """.formatted(HASH);
    }

    private static String publication() {
        return """
                {"draftId":"pd-101","publishedRevision":2,"draftRevision":3,"releaseVersion":"release-1",
                 "sourceManifestSha256":"%s","compiledManifestSha256":"%s","dependencyManifestSha256":"%s",
                 "compilerVersion":"1","publishedAt":"%s","commandId":"publish-1"}
                """.formatted(HASH, HASH, HASH, NOW);
    }

    record Seen(String method, String path, String query, String authorization, String tenant, String accept,
                String contentType, byte[] body) { }

    record Reply(int status, String contentType, String body, String cacheControl) {
        Reply(int status, String contentType, String body) { this(status, contentType, body, "no-store"); }
        static Reply json(String body) { return new Reply(200, "application/json", body); }
    }

    static final class Fixture implements AutoCloseable {
        final URI base;
        private final HttpServer server;
        private final ExecutorService executor;
        private final BlockingQueue<Seen> requests = new LinkedBlockingQueue<>();
        volatile Function<Seen, Reply> handler = request -> new Reply(404, "application/json", "{}");

        Fixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            server.createContext("/", exchange -> {
                try (exchange) {
                    var headers = exchange.getRequestHeaders();
                    var uri = exchange.getRequestURI();
                    var seen = new Seen(exchange.getRequestMethod(), uri.getRawPath(), uri.getRawQuery(), headers.getFirst("Authorization"),
                            headers.getFirst("X-Egon-Tianji-Tenant"), headers.getFirst("Accept"), headers.getFirst("Content-Type"),
                            exchange.getRequestBody().readAllBytes());
                    requests.add(seen);
                    Reply reply = handler.apply(seen);
                    if (reply != null) {
                        exchange.getResponseHeaders().set("Content-Type", reply.contentType);
                        if (reply.cacheControl != null) exchange.getResponseHeaders().set("Cache-Control", reply.cacheControl);
                        byte[] body = reply.body.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(reply.status, body.length);
                        exchange.getResponseBody().write(body);
                    }
                } catch (IOException ignored) {
                    // Client cancellation is expected in bounded-response tests.
                }
            });
            server.start();
            base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        }

        Seen take() { return requests.remove(); }

        Seen poll() {
            try { return requests.poll(120, TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }

        @Override public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
