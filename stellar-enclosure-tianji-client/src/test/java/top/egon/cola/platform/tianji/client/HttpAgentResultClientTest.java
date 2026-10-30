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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class HttpAgentResultClientTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
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
    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body);
    }
    private static void assertCode(String expected, Runnable action) {
        assertEquals(expected, assertThrows(ProcessClientException.class, action::run).code());
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
