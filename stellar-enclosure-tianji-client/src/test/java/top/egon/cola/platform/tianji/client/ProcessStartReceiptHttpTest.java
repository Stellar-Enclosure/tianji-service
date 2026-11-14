package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessStartReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClientTest.startGolden;
import static top.egon.cola.platform.tianji.client.HttpProcessDraftClientTest.JSON;

class ProcessStartReceiptHttpTest {
    private static final String TOKEN = "fixture-private-bearer";
    private static final String BASE = "https://process.example/context/";
    private static final String INVALID = "TIANJI_INVALID_RESPONSE";
    private static final String HASH = "a".repeat(64);
    private static final String RECORDED = "2026-11-06T02:03:04.000001Z";
    private static final GetProcessStartReceiptQuery QUERY = new GetProcessStartReceiptQuery("intent:one", HASH);
    private static final String RECEIPT = """
            {"instanceId":"instance-1","status":"APPLIED","recordedAt":"%s"}
            """.formatted(RECORDED);
    private static final Map<String, List<String>> HEADERS = Map.of(
            "Content-Type", List.of("application/json; charset=UTF-8"), "Cache-Control", List.of("no-store"));

    @Test
    void sends_only_the_exact_owned_get_and_decodes_the_applied_receipt() {
        var wire = new FakeHttpClient(200, HEADERS, RECEIPT.getBytes(StandardCharsets.UTF_8));
        var credentialReads = new AtomicInteger();
        var client = client(wire, BASE, () -> { credentialReads.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));

        assertEquals(new ProcessStartReceiptDTO("instance-1", ProcessStartReceiptDTO.StatusEnum.APPLIED, Instant.parse(RECORDED)),
                client.getStartReceipt(QUERY));
        assertEquals(1, credentialReads.get());
        assertEquals(1, wire.sent);
        assertEquals("GET", wire.request.method());
        assertEquals(URI.create("https://process.example/context" + ProcessStartReceiptClient.START_RECEIPT_PATH
                + "?commandId=intent%3Aone&requestSha256=" + HASH), wire.request.uri());
        assertEquals(Duration.ofSeconds(1), wire.request.timeout().orElseThrow());
        assertEquals("Bearer " + TOKEN, wire.request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", wire.request.headers().firstValue("Accept").orElseThrow());
        assertEquals("no-store", wire.request.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(wire.request.headers().firstValue("Content-Type").isEmpty());
        assertTrue(wire.request.headers().firstValue("X-Egon-Tianji-Tenant").isEmpty());
        assertEquals(0, wire.request.bodyPublisher().orElseThrow().contentLength());

        var fraction = reply(200, HEADERS, RECEIPT.replace(RECORDED, "2026-11-06T02:03:04.1Z"));
        assertEquals(Instant.parse("2026-11-06T02:03:04.1Z"), client(fraction).getStartReceipt(QUERY).recordedAt());
    }

    @Test
    void rejects_wrong_status_media_or_success_cache_control_without_fallback() {
        for (int status : List.of(201, 202, 204, 302, 307)) {
            assertFailure(reply(status, HEADERS, RECEIPT), status, INVALID);
        }
        assertFailure(reply(200, Map.of("Cache-Control", List.of("no-store")), RECEIPT), 200, INVALID);
        assertFailure(reply(200, Map.of("Content-Type", List.of("application/problem+json"),
                "Cache-Control", List.of("no-store")), RECEIPT), 200, INVALID);
        assertFailure(reply(200, Map.of("Content-Type", List.of("application/json", "text/plain"),
                "Cache-Control", List.of("no-store")), RECEIPT), 200, INVALID);
        assertFailure(reply(200, Map.of("Content-Type", List.of("application/json")), RECEIPT), 200, INVALID);
        assertFailure(reply(200, Map.of("Content-Type", List.of("application/json"),
                "Cache-Control", List.of("public", "no-store")), RECEIPT), 200, INVALID);
    }

    @Test
    void rejects_extra_duplicate_or_invalid_receipt_fields() {
        for (String invalid : List.of(
                "",
                "null",
                "[]",
                RECEIPT + "{}",
                RECEIPT.replace("\"status\"", "\"phase\""),
                RECEIPT.replace("\"status\":\"APPLIED\"", "\"status\":\"PENDING\""),
                RECEIPT.replace("\"status\":\"APPLIED\"", "\"status\":0"),
                RECEIPT.replace("\"status\":\"APPLIED\"", "\"status\":\"APPLIED\",\"status\":\"APPLIED\""),
                RECEIPT.replace("\"instance-1\"", "\"instance-1\",\"rawOwner\":\"foreign-owner\""),
                RECEIPT.replace("\"instance-1\"", "\"\\uD800\""),
                RECEIPT.replace("\"instance-1\"", "17"),
                RECEIPT.replace(RECORDED, "2026-11-06T02:03:04+00:00"),
                RECEIPT.replace(RECORDED, "2026-11-06T02:03:04.0000010Z"),
                RECEIPT.replace(RECORDED, "2026-11-06T24:00:00Z"),
                RECEIPT.replace(RECORDED, "2026-11-06T23:59:60Z"),
                RECEIPT.replace(RECORDED, "0000-01-01T00:00:00Z"))) {
            assertFailure(reply(200, HEADERS, invalid), 200, INVALID);
        }
        assertFailure(new FakeHttpClient(200, HEADERS, new byte[]{(byte) 0xc0, (byte) 0xaf}), 200, INVALID);
    }

    @Test
    void preserves_owned_404_as_a_sanitized_inconclusive_failure() {
        Map.of(400, "TIANJI_INVALID_COMMAND", 401, "TIANJI_UNAUTHENTICATED",
                403, "TIANJI_PERMISSION_DENIED", 404, "TIANJI_RESOURCE_NOT_FOUND",
                409, "TIANJI_TENANT_CONTEXT_CHANGED", 503, "TIANJI_DEPENDENCY_UNAVAILABLE").forEach((status, code) ->
                assertFailure(reply(status, HEADERS, error(code)), status, code));
        assertFailure(reply(404, HEADERS, error("TIANJI_PERMISSION_DENIED")), 404, INVALID);
        assertFailure(reply(404, HEADERS, error("TIANJI_RESOURCE_NOT_FOUND").replace("null", "\"foreign-owner\"")), 404, INVALID);
        assertFailure(reply(404, HEADERS, error("TIANJI_RESOURCE_NOT_FOUND").replace("null", "null,\"extra\":true")), 404, INVALID);
        assertFailure(reply(404, Map.of("Content-Type", List.of("application/json")),
                error("TIANJI_RESOURCE_NOT_FOUND")), 404, INVALID);
        assertFailure(reply(404, Map.of("Content-Type", List.of("text/html")), TOKEN), 404, INVALID);
    }

    @Test
    void bounds_the_response_and_cancels_a_stalled_call_without_resending() {
        var oversized = new FakeHttpClient(200, HEADERS, new byte[4097]);
        assertFailure(oversized, 0, "TIANJI_RESPONSE_TOO_LARGE");
        assertTrue(oversized.subscription.cancelled);

        var timeout = new FakeHttpClient(200, HEADERS, new byte[0]);
        timeout.stalled = new CompletableFuture<>();
        var failure = assertThrows(ProcessClientException.class,
                () -> client(timeout, BASE, () -> TOKEN, Duration.ofMillis(5)).getStartReceipt(QUERY));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
        assertTrue(timeout.stalled.isCancelled());
        assertEquals(1, timeout.sent);
    }

    @Test
    void rejects_unsafe_bases_and_redirecting_transport_before_requesting_a_credential() {
        for (String base : List.of("http://process.example/", "http://localhost.evil/",
                "https://process.example/context/../wrong/")) {
            var wire = reply(200, HEADERS, RECEIPT);
            var credentialReads = new AtomicInteger();
            var client = client(wire, base, () -> { credentialReads.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));
            assertThrows(IllegalArgumentException.class, () -> client.getStartReceipt(QUERY));
            assertEquals(0, credentialReads.get());
            assertEquals(0, wire.sent);
        }
        var redirect = reply(200, HEADERS, RECEIPT);
        redirect.redirect = HttpClient.Redirect.ALWAYS;
        assertThrows(IllegalArgumentException.class, () -> client(redirect));
        assertEquals(0, redirect.sent);
    }

    @Test
    void recovers_the_exact_v2_start_digest_through_the_unchanged_v1_receipt_query() throws IOException {
        String exact = "7cfa4c0086eaadc9837ce74cbf54d7420a290d50fdad3d30db4ed485ff411323";
        long max = ProcessTypedJson.V2_MAX_WIRE_INTEGER;
        var command = startGolden("1.00", max);
        var query = HttpProcessAutomationClient.startReceiptQuery(command);
        assertEquals(new GetProcessStartReceiptQuery("start-101", exact), query);
        assertEquals("8758777d98df5e731e4ac50e896692e0cec31a15d4b00236c0d388399c82bac2",
                HttpProcessAutomationClient.startReceiptQuery(startGolden("1.0", max)).requestSha256());
        assertThrows(IllegalArgumentException.class, () -> HttpProcessAutomationClient.startReceiptQuery(
                command.setVariables(Map.of("overflow", 9_007_199_254_740_993L))));

        try (var fixture = new HttpProcessDraftClientTest.Fixture(); var http = HttpClient.newHttpClient();
             var automation = new HttpProcessAutomationClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(3))) {
            fixture.handler = request -> "POST".equals(request.method()) ? null : HttpProcessDraftClientTest.Reply.json(RECEIPT);
            var original = startGolden("1.00", max);
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(ProcessClientException.class, () -> automation.start(original)).code());
            var post = fixture.take();
            assertEquals("/api/tianji/v2/process-instances", post.path());
            var actualWire = JSON.readTree(post.body());
            assertEquals("1.00", actualWire.path("variables").path("amount").asString());
            assertEquals("十", actualWire.path("variables").path("10").asString());
            assertEquals(max, actualWire.path("agentTaskOverrides").path("agentNode").path("templateRef").path("version").asLong());
            @SuppressWarnings("unchecked")
            Map<String, Object> originalWire = JSON.readValue(post.body(), Map.class);
            assertEquals(exact, ProcessTypedJson.startRequestSha256(originalWire));
            assertNull(fixture.poll());

            var receiptClient = new HttpProcessClient(fixture.base, http, () -> TOKEN, Duration.ofSeconds(3));
            assertEquals("instance-1", receiptClient.getStartReceipt(query).instanceId());
            var lookup = fixture.take();
            assertEquals("GET", lookup.method());
            assertEquals(ProcessStartReceiptClient.START_RECEIPT_PATH, lookup.path());
            assertEquals("commandId=start-101&requestSha256=" + exact, lookup.query());
            fixture.handler = request -> new HttpProcessDraftClientTest.Reply(404, "application/json", error("TIANJI_RESOURCE_NOT_FOUND"));
            assertEquals(404, assertThrows(ProcessClientException.class, () -> receiptClient.getStartReceipt(query)).httpStatus());
            assertEquals("GET", fixture.take().method());
            assertNull(fixture.poll());
        }
    }

    private static ProcessStartReceiptClient client(FakeHttpClient wire) {
        return client(wire, BASE, () -> TOKEN, Duration.ofSeconds(1));
    }

    private static ProcessStartReceiptClient client(FakeHttpClient wire, String base, Supplier<String> credential,
                                                    Duration timeout) {
        return new HttpProcessClient(URI.create(base), wire, credential, timeout);
    }

    private static FakeHttpClient reply(int status, Map<String, List<String>> headers, String body) {
        return new FakeHttpClient(status, headers, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String error(String code) {
        return "{\"code\":\"" + code + "\",\"correlationId\":null}";
    }

    private static void assertFailure(FakeHttpClient wire, int status, String expected) {
        var failure = assertThrows(ProcessClientException.class, () -> client(wire).getStartReceipt(QUERY));
        assertEquals(status, failure.httpStatus());
        assertEquals(expected, failure.code());
        assertNull(failure.getCause());
        assertNull(failure.correlationId());
        assertNull(failure.retryAfter());
        assertFalse(failure.toString().contains(TOKEN));
        assertFalse(failure.toString().contains("foreign-owner"));
        assertEquals(1, wire.sent);
        assertEquals("GET", wire.request.method());
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ReplyInfo info;
        private final byte[] body;
        private final TrackingSubscription subscription = new TrackingSubscription();
        private Redirect redirect = Redirect.NEVER;
        private CompletableFuture<?> stalled;
        private HttpRequest request;
        private int sent;

        private FakeHttpClient(int status, Map<String, List<String>> headers, byte[] body) {
            this.info = new ReplyInfo(status, HttpHeaders.of(headers, (name, value) -> true));
            this.body = body;
        }

        @Override public <T> HttpResponse<T> send(HttpRequest value, HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("Unexpected synchronous HTTP request");
        }

        @Override @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest value, HttpResponse.BodyHandler<T> handler) {
            request = value;
            sent++;
            if (stalled != null) return (CompletableFuture<HttpResponse<T>>) stalled;
            var subscriber = handler.apply(info);
            subscriber.onSubscribe(subscription);
            subscriber.onNext(List.of(ByteBuffer.wrap(body)));
            if (!subscription.cancelled) subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(reply -> new Reply<>(info, value, reply));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest value, HttpResponse.BodyHandler<T> handler,
                                                               HttpResponse.PushPromiseHandler<T> push) {
            throw new AssertionError("Unexpected HTTP push");
        }

        @Override public Redirect followRedirects() { return redirect; }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new AssertionError("Unexpected TLS context lookup"); }
        @Override public SSLParameters sslParameters() { throw new AssertionError("Unexpected TLS parameters lookup"); }
    }

    private record ReplyInfo(int statusCode, HttpHeaders headers) implements HttpResponse.ResponseInfo {
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private record Reply<T>(ReplyInfo info, HttpRequest request, T body) implements HttpResponse<T> {
        @Override public int statusCode() { return info.statusCode(); }
        @Override public HttpHeaders headers() { return info.headers(); }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return info.version(); }
    }

    private static final class TrackingSubscription implements Flow.Subscription {
        private boolean cancelled;
        @Override public void request(long count) { }
        @Override public void cancel() { cancelled = true; }
    }
}
