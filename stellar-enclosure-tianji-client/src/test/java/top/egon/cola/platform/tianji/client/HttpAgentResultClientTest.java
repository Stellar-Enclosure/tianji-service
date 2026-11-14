package top.egon.cola.platform.tianji.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetSocketAddress;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class HttpAgentResultClientTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String CARRIER = "00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01";
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AgentTerminalEvent event = event();
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private URI base;

    @BeforeEach void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> { try (exchange) { handler.get().handle(exchange); } catch (IOException ignored) { } });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }
    @AfterEach void stopStub() { server.stop(0); executor.shutdownNow(); }

    @Test
    void v2_preserves_raw_terminal_fact_bytes_and_sends_typed_form_on_existing_inbox() {
        var fact = v2();
        byte[] raw = rawV2(fact);
        var seen = new java.util.concurrent.LinkedBlockingQueue<byte[]>();
        var receipt = v2Receipt(fact);
        handler.set(exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals(AgentResultTraceLinkCarrier.PATH, exchange.getRequestURI().getRawPath());
            assertEquals("20", exchange.getRequestHeaders().getFirst("X-Egon-Tianji-Tenant"));
            assertEquals("Bearer result-token", exchange.getRequestHeaders().getFirst("Authorization"));
            assertNull(exchange.getRequestHeaders().getFirst(AgentResultTraceLinkCarrier.HEADER));
            seen.add(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            respond(exchange, 202, mapper.writeValueAsBytes(receipt));
        });
        try (var client = client(Duration.ofSeconds(3))) {
            assertEquals(receipt, client.receiveV2(fact, raw, CARRIER));
            assertArrayEquals(raw, seen.remove());
            assertEquals(receipt, client.receiveV2(fact));
            assertArrayEquals(fact.canonicalBytes(), seen.remove());
            byte[] padded = (" ".repeat(16_384 - raw.length) + new String(raw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
            assertEquals(receipt, client.receiveV2(fact, padded, null));
            assertArrayEquals(padded, seen.remove());
            assertTrue(seen.isEmpty());
        }
    }

    @Test
    void v2_rejects_nonexact_or_malformed_originals_before_token_and_transport() {
        var fact = v2();
        String canonical = new String(fact.canonicalBytes(), StandardCharsets.UTF_8);
        var tokenCalls = new AtomicInteger();
        var requests = new AtomicInteger();
        handler.set(exchange -> { requests.incrementAndGet(); respond(exchange, 500, new byte[0]); });
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> {
            tokenCalls.incrementAndGet();
            return "result-token";
        }, Duration.ofSeconds(3))) {
            for (String invalid : List.of(
                    canonical.replace("\"schemaVersion\":2", "\"schemaVersion\":1"),
                    canonical.replace("\"schemaVersion\":2", "\"schemaVersion\":2.0"),
                    canonical.replace("\"schemaVersion\":2", "\"schemaVersion\":\"2\""),
                    canonical.replace("\"schemaVersion\":2", "\"schemaVersion\":2,\"schemaVersion\":2"),
                    canonical.replace("\"artifactRef\":null,", ""),
                    canonical.replace("\"tenantId\":20", "\"tenantId\":21"),
                    canonical.replace("\"ownerSubjectId\":\"process-worker\"", "\"ownerSubjectId\":\"other-worker\""),
                    canonical.replace("\"sourceId\":\"process-east\"", "\"sourceId\":\"another-process\""),
                    canonical.replace("\"configurationSha256\":\"" + "c".repeat(64), "\"configurationSha256\":\"" + "f".repeat(64)),
                    canonical.replace("\"finishedAt\":\"2026-10-30T00:00:00Z\"", "\"finishedAt\":1793318400"),
                    canonical.substring(0, canonical.length() - 1) + ",\"unexpected\":true}",
                    canonical + " {}", " ".repeat(16_385), "")) {
                assertThrows(IllegalArgumentException.class,
                        () -> client.receiveV2(fact, invalid.getBytes(StandardCharsets.UTF_8), CARRIER), invalid);
            }
            byte[] invalidUtf8 = {(byte) 0xC0, (byte) 0xAF};
            assertThrows(IllegalArgumentException.class, () -> client.receiveV2(fact, invalidUtf8, null));
        }
        assertEquals(0, tokenCalls.get());
        assertEquals(0, requests.get());
    }

    @Test
    void v2_trace_is_diagnostic_only_on_the_exact_https_destination_and_body_cannot_resubscribe() throws Exception {
        var fact = v2();
        byte[] raw = rawV2(fact);
        var receipt = v2Receipt(fact);
        for (URI origin : List.of(URI.create("https://process.example/"), URI.create("https://process.example/internal/"),
                URI.create("http://127.0.0.1/"))) {
            var http = new CapturingHttpClient(mapper.writeValueAsBytes(receipt));
            try (var client = new HttpAgentResultClient(origin, http, tenant -> "result-token", Duration.ofSeconds(3))) {
                assertEquals(receipt, client.receiveV2(fact, raw, CARRIER));
                assertEquals(receipt, client.receiveV2(fact, raw, CARRIER + "," + CARRIER));
            }
            assertEquals(2, http.requests.size());
            var first = http.requests.getFirst();
            var second = http.requests.getLast();
            boolean traceExpected = origin.equals(URI.create("https://process.example/"));
            assertEquals(traceExpected ? List.of(CARRIER) : List.of(),
                    first.headers().allValues(AgentResultTraceLinkCarrier.HEADER));
            assertTrue(second.headers().allValues(AgentResultTraceLinkCarrier.HEADER).isEmpty());
            assertEquals("POST", first.method());
            assertEquals(origin.resolve(AgentResultTraceLinkCarrier.PATH.substring(1)), first.uri());
            assertArrayEquals(raw, collect(first).get(2, TimeUnit.SECONDS));
            assertArrayEquals(raw, collect(second).get(2, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> collect(first).get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void v2_requires_exact_accepted_receipt_and_preserves_transport_retry_signals_without_replay() {
        var fact = v2();
        var receipt = v2Receipt(fact);
        String valid = mapper.writeValueAsString(receipt);
        var requests = new AtomicInteger();
        var rejected = new AtomicInteger();
        var status = new AtomicInteger(202);
        var payload = new AtomicReference<>(valid);
        handler.set(exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Retry-After", "7");
            if (status.get() != 0) respond(exchange, status.get(), payload.get().getBytes(StandardCharsets.UTF_8));
        });
        var tokens = new HttpAgentResultClient.TokenProvider() {
            @Override public String acquire(long tenantId) { return "private-result-token"; }
            @Override public void rejected(long tenantId) { assertEquals(20, tenantId); rejected.incrementAndGet(); }
        };
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tokens, Duration.ofSeconds(3))) {
            for (String invalid : List.of(valid.replace(fact.eventId(), "d".repeat(64)),
                    valid.replace(fact.fingerprint(), "e".repeat(64)),
                    valid.replace("RECEIVED", "UNRECOGNIZED"),
                    valid.replace("2026-10-30T00:00:00Z", "2026-10-30T02:00:00+02:00"),
                    valid.replace("\"state\":\"RECEIVED\"", "\"state\":\"RECEIVED\",\"state\":\"APPLIED\""),
                    valid.substring(0, valid.length() - 1) + ",\"unexpected\":true}")) {
                payload.set(invalid);
                assertCode("TIANJI_INVALID_RESPONSE", () -> client.receiveV2(fact));
            }
            payload.set(" ".repeat(16_385));
            assertCode("TIANJI_RESPONSE_TOO_LARGE", () -> client.receiveV2(fact));
            payload.set(valid);
            status.set(200);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.receiveV2(fact));
            status.set(302);
            var redirected = assertThrows(ProcessClientException.class, () -> client.receiveV2(fact));
            assertEquals(302, redirected.httpStatus());
            assertEquals("TIANJI_INVALID_RESPONSE", redirected.code());
            payload.set("{\"code\":\"private-result-token\",\"detail\":\"private-payload\"}");
            for (int failureStatus : List.of(401, 403, 404, 408, 429, 500, 503, 0)) {
                status.set(failureStatus);
                var failure = assertThrows(ProcessClientException.class, () -> client.receiveV2(fact));
                assertEquals(failureStatus, failure.httpStatus());
                if (failureStatus == 0 || failureStatus == 408 || failureStatus == 503) {
                    assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
                } else if (failureStatus == 500) {
                    assertEquals("TIANJI_INTERNAL_ERROR", failure.code());
                }
                if (failureStatus != 0) assertEquals(Duration.ofSeconds(7), failure.retryAfter());
                assertFalse(failure.toString().contains("private-result-token"));
                assertFalse(failure.toString().contains("private-payload"));
            }
            status.set(503);
            payload.set(" ".repeat(32_768));
            var overloaded = assertThrows(ProcessClientException.class, () -> client.receiveV2(fact));
            assertEquals(503, overloaded.httpStatus());
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", overloaded.code());
            assertEquals(Duration.ofSeconds(7), overloaded.retryAfter());
        }
        assertEquals(1, rejected.get());
        assertEquals(18, requests.get());
    }

    @Test
    void sends_one_bound_tenant_reference_and_keeps_received_distinct_from_applied() {
        var calls = new AtomicInteger();
        var state = new AtomicReference<>(AgentResultReceiptStateEnum.RECEIVED);
        handler.set(exchange -> {
            calls.incrementAndGet();
            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("/api/tianji/v1/agent-result-inbox", exchange.getRequestURI().getPath());
            assertEquals("Bearer result-token-20", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("20", exchange.getRequestHeaders().getFirst("X-Egon-Tianji-Tenant"));
            assertEquals(event, mapper.readValue(exchange.getRequestBody().readAllBytes(), AgentTerminalEvent.class));
            respond(exchange, 202, mapper.writeValueAsBytes(receipt(state.get())));
        });
        var inherited = new InheritableThreadLocal<String>(); inherited.set("private-request-context");
        var captured = new AtomicReference<String>();
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> {
            assertEquals(20, tenant); captured.set(inherited.get()); return "result-token-" + tenant;
        }, Duration.ofSeconds(3))) {
            assertEquals(AgentResultReceiptStateEnum.RECEIVED, client.receive(event).state());
            state.set(AgentResultReceiptStateEnum.APPLIED);
            assertEquals(AgentResultReceiptStateEnum.APPLIED, client.receive(event).state());
        } finally { inherited.remove(); }
        assertNull(captured.get());
        assertEquals(2, calls.get());
    }

    @Test
    void rejects_mismatched_duplicate_coerced_and_oversized_receipts() {
        var payload = new AtomicReference<byte[]>();
        var status = new AtomicInteger(202);
        handler.set(exchange -> { exchange.getRequestBody().readAllBytes(); respond(exchange, status.get(), payload.get()); });
        try (var client = client(Duration.ofSeconds(3))) {
            String valid = mapper.writeValueAsString(receipt(AgentResultReceiptStateEnum.RECEIVED));
            for (String bad : List.of(
                    valid.replace(event.eventId(), "d".repeat(64)), valid.replace(event.fingerprint(), "e".repeat(64)),
                    valid.replace("\"state\":\"RECEIVED\"", "\"state\":0"),
                    valid.replace("\"state\":\"RECEIVED\"", "\"state\":\"RECEIVED\",\"state\":\"APPLIED\""),
                    valid.replace("\"receivedAt\":\"2026-10-30T00:00:00Z\"", "\"receivedAt\":123"),
                    valid.substring(0, valid.length() - 1) + ",\"unexpected\":true}", valid + " {}")) {
                payload.set(bad.getBytes(StandardCharsets.UTF_8));
                assertCode("TIANJI_INVALID_RESPONSE", () -> client.receive(event));
            }
            payload.set(" ".repeat(16_385).getBytes(StandardCharsets.UTF_8));
            assertCode("TIANJI_RESPONSE_TOO_LARGE", () -> client.receive(event));
            payload.set(valid.getBytes(StandardCharsets.UTF_8)); status.set(200);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.receive(event));
        }
    }

    @Test
    void unavailable_delivery_does_not_retry_or_expose_private_remote_data() {
        var calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Retry-After", "5");
            respond(exchange, 503, "{\"code\":\"private-result-token\",\"message\":\"private-payload\"}".getBytes(StandardCharsets.UTF_8));
        });
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> "private-result-token", Duration.ofSeconds(3))) {
            var failure = assertThrows(ProcessClientException.class, () -> client.receive(event));
            assertEquals(503, failure.httpStatus());
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
            assertEquals(Duration.ofSeconds(5), failure.retryAfter());
            assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private-result-token"));
            assertFalse(failure.toString().contains("private-payload"));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void rejected_credentials_are_invalidated_without_repeating_the_post() {
        var calls = new AtomicInteger(); var rejected = new AtomicInteger();
        handler.set(exchange -> { calls.incrementAndGet(); exchange.getRequestBody().readAllBytes(); respond(exchange, 401, "{}".getBytes(StandardCharsets.UTF_8)); });
        var tokens = new HttpAgentResultClient.TokenProvider() {
            @Override public String acquire(long tenantId) { return "result-token"; }
            @Override public void rejected(long tenantId) { assertEquals(20, tenantId); rejected.incrementAndGet(); }
        };
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tokens, Duration.ofSeconds(3))) {
            assertCode("TIANJI_UNAUTHENTICATED", () -> client.receive(event));
            assertEquals(1, rejected.get()); assertEquals(1, calls.get());
        }
    }

    @Test
    void uninterruptible_token_calls_are_bounded_without_queueing_or_sending_after_timeout() throws Exception {
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> { requests.incrementAndGet(); respond(exchange, 500, new byte[0]); });
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> {
            entered.countDown();
            while (true) {
                try { if (release.await(50, TimeUnit.MILLISECONDS)) return "late-token"; }
                catch (InterruptedException ignored) { }
            }
        }, Duration.ofMillis(150), 2)) {
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.receive(event));
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.receive(event));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.receive(event));
            assertEquals(0, requests.get());
        } finally { release.countDown(); }
    }

    @Test
    void close_cancels_in_flight_delivery_and_rejects_insecure_destinations() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes(); entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            respond(exchange, 202, mapper.writeValueAsBytes(receipt(AgentResultReceiptStateEnum.RECEIVED)));
        });
        var client = client(Duration.ofSeconds(10));
        try (var caller = Executors.newSingleThreadExecutor()) {
            var future = caller.submit(() -> client.receive(event));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            client.close();
            var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertInstanceOf(ProcessClientException.class, error.getCause()).code());
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.receive(event));
            assertEquals(1, requests.get());
        } finally { release.countDown(); client.close(); }
        assertThrows(IllegalArgumentException.class, () -> new HttpAgentResultClient(URI.create("http://example.com/"), HttpClient.newHttpClient(), tenant -> "token", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new HttpAgentResultClient(base, HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), tenant -> "token", Duration.ofSeconds(1)));
    }

    @Test
    void token_acquisition_and_http_wait_share_one_total_deadline() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> {
            requests.incrementAndGet(); exchange.getRequestBody().readAllBytes(); entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            respond(exchange, 202, mapper.writeValueAsBytes(receipt(AgentResultReceiptStateEnum.RECEIVED)));
        });
        try (var client = new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> {
            try { Thread.sleep(600); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted"); }
            return "result-token";
        }, Duration.ofSeconds(1))) {
            long started = System.nanoTime();
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.receive(event));
            assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofMillis(1400)) < 0);
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertEquals(1, requests.get());
        } finally { release.countDown(); }
    }

    private HttpAgentResultClient client(Duration timeout) { return new HttpAgentResultClient(base, HttpClient.newHttpClient(), tenant -> "result-token", timeout); }
    private AgentResultReceipt receipt(AgentResultReceiptStateEnum state) { return new AgentResultReceipt(event.eventId(), event.fingerprint(), state, NOW); }
    private static AgentTerminalEvent event() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "run-one", 3),
                20, "b".repeat(64), "process-worker", "intent", link, "review", 1, "run-one", 3, AgentRunStatusEnum.COMPLETED, NOW, "c".repeat(64), null, "UNKNOWN");
    }
    private static AgentTerminalEventV2 v2() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        var output = Map.<String, Object>of("summary", "Résumé", "notes", "n".repeat(5_000),
                "items", java.util.stream.IntStream.range(0, 128).boxed().toList());
        var outcome = SideEffectOutcomeEnum.NONE;
        String id = AgentTerminalEventV2.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "opaque-run-v2", 3);
        return new AgentTerminalEventV2(2, "agent-east", id, 20, "b".repeat(64), "process-worker", "intent", link,
                "review", 1, "opaque-run-v2", 3, "node-one", "c".repeat(64), "d".repeat(64), "e".repeat(64),
                AgentRunStatusEnum.COMPLETED, NOW, AgentTerminalEventV2.resultSha256(AgentRunStatusEnum.COMPLETED, output, null, null, outcome),
                output, null, null, outcome);
    }

    private static byte[] rawV2(AgentTerminalEventV2 event) {
        return (" \n" + new String(event.canonicalBytes(), StandardCharsets.UTF_8)
                .replace("Résumé", "R\\u00e9sum\\u00e9").replace("\"schemaVersion\":2", "\"schemaVersion\" : 2") + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static AgentResultReceipt v2Receipt(AgentTerminalEventV2 event) {
        return new AgentResultReceipt(event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);
    }

    private static CompletableFuture<byte[]> collect(HttpRequest request) {
        var output = new ByteArrayOutputStream();
        var body = new CompletableFuture<byte[]>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
            @Override public void onComplete() { body.complete(output.toByteArray()); }
        });
        return body;
    }

    private static final class CapturingHttpClient extends HttpClient {
        private static final HttpHeaders HEADERS = HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json"), "Cache-Control", List.of("no-store")), (name, value) -> true);
        private final List<HttpRequest> requests = new ArrayList<>();
        private final byte[] response;

        private CapturingHttpClient(byte[] response) { this.response = response; }
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { throw new AssertionError("no TLS socket"); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }
        @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("synchronous send is outside the contract");
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            var subscriber = handler.apply(new HttpResponse.ResponseInfo() {
                @Override public int statusCode() { return 202; }
                @Override public HttpHeaders headers() { return HEADERS; }
                @Override public Version version() { return Version.HTTP_1_1; }
            });
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override public void request(long count) { }
                @Override public void cancel() { }
            });
            subscriber.onNext(List.of(ByteBuffer.wrap(response)));
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().thenApply(body -> new ReceiptResponse<>(request, body));
        }
        @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> push) {
            return sendAsync(request, handler);
        }
        private record ReceiptResponse<T>(HttpRequest request, T body) implements HttpResponse<T> {
            @Override public int statusCode() { return 202; }
            @Override public HttpHeaders headers() { return HEADERS; }
            @Override public URI uri() { return request.uri(); }
            @Override public Version version() { return Version.HTTP_1_1; }
            @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
            @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        }
    }
    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body);
    }
    private static void assertCode(String expected, Runnable action) {
        assertEquals(expected, assertThrows(ProcessClientException.class, action::run).code());
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
