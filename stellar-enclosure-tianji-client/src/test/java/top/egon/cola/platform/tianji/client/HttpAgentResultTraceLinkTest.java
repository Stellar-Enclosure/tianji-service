package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class HttpAgentResultTraceLinkTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String CARRIER = "00-" + "a".repeat(32) + "-" + "b".repeat(16) + "-01";
    private static final URI HTTPS = URI.create("https://process.example/");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AgentTerminalEvent event = event();
    private final AgentResultReceipt receipt = new AgentResultReceipt(
            event.eventId(), event.fingerprint(), AgentResultReceiptStateEnum.RECEIVED, NOW);

    @Test
    void adds_only_one_diagnostic_header_without_changing_the_request_or_event_fingerprint() throws Exception {
        var http = new FakeHttpClient(JSON.writeValueAsBytes(receipt));
        try (var client = client(HTTPS, http)) {
            assertEquals(receipt, client.receive(event));
            assertEquals(receipt, client.receive(event, CARRIER));
        }
        assertEquals(2, http.requests.size());
        var original = http.requests.getFirst();
        var linked = http.requests.getLast();
        var path = URI.create("https://process.example/api/tianji/v1/agent-result-inbox");
        assertEquals(path, original.uri());
        assertEquals(path, linked.uri());
        assertEquals("POST", original.method());
        assertEquals("POST", linked.method());
        assertTrue(original.timeout().orElseThrow().compareTo(Duration.ofSeconds(2)) <= 0);
        assertTrue(linked.timeout().orElseThrow().compareTo(Duration.ofSeconds(2)) <= 0);
        assertEquals(4, original.headers().map().size());
        original.headers().map().forEach((name, values) -> assertEquals(values, linked.headers().allValues(name)));
        assertEquals(List.of("Bearer bounded-token"), original.headers().allValues("Authorization"));
        assertEquals(List.of("20"), original.headers().allValues("X-Egon-Tianji-Tenant"));
        assertEquals(List.of("application/json"), original.headers().allValues("Accept"));
        assertEquals(List.of("application/json"), original.headers().allValues("Content-Type"));
        assertTrue(original.headers().allValues(AgentResultTraceLinkCarrier.HEADER).isEmpty());
        assertEquals(List.of(CARRIER), linked.headers().allValues(AgentResultTraceLinkCarrier.HEADER));
        assertEquals(original.headers().map().size() + 1, linked.headers().map().size());
        assertTrue(linked.headers().allValues("traceparent").isEmpty());
        var oldBody = body(original);
        var linkedBody = body(linked);
        assertArrayEquals(JSON.writeValueAsBytes(event), oldBody);
        assertArrayEquals(oldBody, linkedBody);
        assertEquals(event.fingerprint(), JSON.readValue(linkedBody, AgentTerminalEvent.class).fingerprint());
    }

    @Test
    void omits_invalid_cleartext_and_prefixed_links_without_replaying_the_post() {
        var https = new FakeHttpClient(JSON.writeValueAsBytes(receipt));
        var rejected = List.of("", " " + CARRIER, CARRIER + " ", CARRIER + "," + CARRIER, "x".repeat(500),
                CARRIER.replace("00-", "01-"), CARRIER.substring(0, 54) + "0",
                CARRIER.replace("a".repeat(32), "0".repeat(32)), CARRIER.replace("b".repeat(16), "0".repeat(16)),
                CARRIER.replace("a".repeat(32), "A".repeat(32)));
        try (var client = client(HTTPS, https)) {
            for (String value : rejected) {
                assertFalse(AgentResultTraceLinkCarrier.isValid(value));
                assertEquals(receipt, client.receive(event, value));
            }
        }
        assertTrue(AgentResultTraceLinkCarrier.isValid(CARRIER));
        assertEquals(rejected.size(), https.requests.size());
        assertTrue(https.requests.stream().allMatch(request -> request.headers().allValues(AgentResultTraceLinkCarrier.HEADER).isEmpty()));

        var loopback = new FakeHttpClient(JSON.writeValueAsBytes(receipt));
        try (var client = client(URI.create("http://127.0.0.1/"), loopback)) {
            assertEquals(receipt, client.receive(event, CARRIER));
        }
        assertEquals(1, loopback.requests.size());
        assertTrue(loopback.requests.getFirst().headers().allValues(AgentResultTraceLinkCarrier.HEADER).isEmpty());

        var prefixed = new FakeHttpClient(JSON.writeValueAsBytes(receipt));
        try (var client = client(URI.create("https://process.example/internal/"), prefixed)) {
            assertEquals(receipt, client.receive(event, CARRIER));
        }
        assertEquals(1, prefixed.requests.size());
        var request = prefixed.requests.getFirst();
        assertEquals("/internal/api/tianji/v1/agent-result-inbox", request.uri().getRawPath());
        assertTrue(request.headers().allValues(AgentResultTraceLinkCarrier.HEADER).isEmpty());
    }

    private static HttpAgentResultClient client(URI base, HttpClient http) {
        return new HttpAgentResultClient(base, http, tenant -> {
            assertEquals(20, tenant);
            return "bounded-token";
        }, Duration.ofSeconds(2));
    }

    private static byte[] body(HttpRequest request) throws Exception {
        var output = new ByteArrayOutputStream();
        var result = new CompletableFuture<byte[]>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            @Override public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
            @Override public void onComplete() { result.complete(output.toByteArray()); }
        });
        return result.get(1, TimeUnit.SECONDS);
    }

    private static AgentTerminalEvent event() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance", "occurrence", "a".repeat(64), NOW.plusSeconds(60));
        return new AgentTerminalEvent(1, "agent-east", AgentTerminalEvent.eventIdFor("agent-east", 20, "b".repeat(64), "process-worker", "run-one", 3),
                20, "b".repeat(64), "process-worker", "intent", link, "research", 2, "run-one", 3, AgentRunStatusEnum.COMPLETED,
                NOW, "c".repeat(64), null, "UNKNOWN");
    }

    private static final class FakeHttpClient extends HttpClient {
        private static final HttpHeaders HEADERS = HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json")), (name, value) -> true);
        private final List<HttpRequest> requests = new ArrayList<>();
        private final byte[] response;

        FakeHttpClient(byte[] response) { this.response = response; }

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
}
