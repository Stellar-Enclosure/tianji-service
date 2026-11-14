package top.egon.cola.platform.tianji.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class HttpLinkedAgentClientTest {
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private URI baseUri;

    @BeforeEach void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/", exchange -> { try (exchange) { handler.get().handle(exchange); } catch (IOException ignored) { } });
        server.start(); baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }
    @AfterEach void stopServer() { server.stop(0); executor.shutdownNow(); }

    @Test void submit_runtime_sends_one_frozen_v2_command_and_accepts_an_opaque_terminal_run() throws Exception {
        var command = runtimeCommand();
        var seen = new LinkedBlockingQueue<String>();
        var payload = new AtomicReference<byte[]>();
        handler.set(exchange -> {
            payload.set(exchange.getRequestBody().readAllBytes());
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath() + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            respondRuntime(exchange, 202, runtimeResponse(command.getIdempotencyKey(), command.getNodeExecutionRef(), "COMPLETED"));
        });
        var acquired = new LinkedBlockingQueue<HttpLinkedAgentClient.Operation>();
        try (var http = HttpClient.newHttpClient(); var client = new HttpLinkedAgentClient(baseUri, http, operation -> {
            acquired.add(operation);
            return "runtime-bearer";
        }, Duration.ofSeconds(3))) {
            var result = client.submitRuntime(command);
            assertEquals("host:run/not-uuid", result.getRunId());
            assertEquals("COMPLETED", result.getState());
            assertEquals(command.getNodeExecutionRef(), result.getNodeExecutionRef());
        }
        assertEquals("POST /api/tianji/v2/agent-process-dispatches/submit Bearer runtime-bearer", seen.poll(1, TimeUnit.SECONDS));
        assertTrue(seen.isEmpty());
        assertEquals(HttpLinkedAgentClient.Operation.SUBMIT, acquired.poll());
        assertTrue(acquired.isEmpty());
        assertEquals(command, mapper.readValue(payload.get(), LinkedAgentRuntimeCommand.class));
        assertEquals(command.getAttemptDeadlineAt().toString(), mapper.readTree(payload.get()).path("attemptDeadlineAt").asString());
    }

    @Test void submit_runtime_rejects_wrong_acknowledgements_without_fallback_or_resend() throws Exception {
        var command = runtimeCommand();
        var seen = new LinkedBlockingQueue<String>();
        var response = new AtomicReference<byte[]>();
        var status = new AtomicInteger(202);
        handler.set(exchange -> {
            exchange.getRequestBody().readAllBytes();
            seen.add(exchange.getRequestURI().getRawPath());
            if (response.get() != null) respondRuntime(exchange, status.get(), response.get());
        });
        try (var client = client(Duration.ofSeconds(3))) {
            response.set(runtimeResponse("other", command.getNodeExecutionRef(), "QUEUED"));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submitRuntime(command));
            response.set(runtimeResponse(command.getIdempotencyKey(), "node-other", "QUEUED"));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submitRuntime(command));
            response.set(runtimeResponse(command.getIdempotencyKey(), command.getNodeExecutionRef(), "UNKNOWN"));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submitRuntime(command));
            response.set(runtimeResponse(command.getIdempotencyKey(), command.getNodeExecutionRef(), "QUEUED"));
            status.set(200);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submitRuntime(command));
            status.set(500);
            response.set("""
                    {"code":"TIANJI_INTERNAL_ERROR","message":"private runtime error","correlationId":"trace-runtime","decisionId":null,"retryAfter":"PT2S"}
                    """.getBytes(StandardCharsets.UTF_8));
            var internal = assertThrows(AgentClientException.class, () -> client.submitRuntime(command));
            assertEquals(500, internal.httpStatus());
            assertEquals("TIANJI_INTERNAL_ERROR", internal.code());
            assertEquals("trace-runtime", internal.correlationId());
            assertEquals(Duration.ofSeconds(2), internal.retryAfter());
            assertFalse(internal.getMessage().contains("private runtime error"));
            response.set(null);
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.submitRuntime(command));
        }
        for (int i = 0; i < 6; i++) assertEquals("/api/tianji/v2/agent-process-dispatches/submit", seen.poll(1, TimeUnit.SECONDS));
        assertNull(seen.poll(150, TimeUnit.MILLISECONDS));
    }

    @Test void sends_each_operation_once_with_its_own_token_and_checks_the_echoed_link() throws Exception {
        var command = command(); var query = query(command); var run = run("review", 1);
        var seen = new LinkedBlockingQueue<String>();
        handler.set(exchange -> {
            String action = exchange.getRequestURI().getPath().substring(exchange.getRequestURI().getPath().lastIndexOf('/') + 1);
            seen.add(exchange.getRequestMethod() + " " + action + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (action.equals("submit")) assertEquals(command, mapper.readValue(body, LinkedAgentStartCommand.class));
            else assertEquals(query, mapper.readValue(body, LinkedAgentLookupQuery.class));
            Object result = switch (action) {
                case "submit" -> new LinkedAgentStartResult(query, run);
                case "resolve" -> new LinkedAgentLookupResult(query, false, null);
                case "close" -> new LinkedAgentCloseResult(query, new LinkedAgentSubmissionDTO(true, null));
                default -> throw new AssertionError(action);
            };
            respond(exchange, action.equals("submit") ? 202 : 200, mapper.writeValueAsBytes(result));
        });
        var inherited = new InheritableThreadLocal<String>(); inherited.set("private-request-context");
        var captured = new AtomicReference<String>();
        try (var client = new HttpLinkedAgentClient(baseUri, HttpClient.newHttpClient(), operation -> {
            captured.set(inherited.get()); return "token-" + operation.name();
        }, Duration.ofSeconds(3))) {
            assertEquals(run, client.submit(command).run());
            assertFalse(client.resolve(query).found());
            assertTrue(client.close(query).submission().closed());
        } finally { inherited.remove(); }
        assertNull(captured.get());
        assertEquals("POST submit Bearer token-SUBMIT", seen.poll(1, TimeUnit.SECONDS));
        assertEquals("POST resolve Bearer token-RESOLVE", seen.poll(1, TimeUnit.SECONDS));
        assertEquals("POST close Bearer token-CLOSE", seen.poll(1, TimeUnit.SECONDS));
        assertTrue(seen.isEmpty());
    }

    @Test void rejects_mismatched_missing_duplicate_or_oversized_responses() {
        var command = command(); var query = query(command);
        var different = new LinkedAgentLookupQuery("other", query.link());
        var response = new AtomicReference<byte[]>(); var status = new AtomicInteger(200);
        handler.set(exchange -> { exchange.getRequestBody().readAllBytes(); respond(exchange, status.get(), response.get()); });
        try (var client = client(Duration.ofSeconds(3))) {
            response.set(mapper.writeValueAsBytes(new LinkedAgentLookupResult(different, false, null)));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.resolve(query));
            response.set("{\"query\":{},\"submission\":null}".getBytes(StandardCharsets.UTF_8));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.resolve(query));
            String valid = mapper.writeValueAsString(new LinkedAgentLookupResult(query, false, null));
            response.set(valid.replace("\"found\":false", "\"found\":false,\"found\":true").getBytes(StandardCharsets.UTF_8));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.resolve(query));
            response.set((valid + " {}").getBytes(StandardCharsets.UTF_8));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.resolve(query));
            response.set("x".repeat(65_537).getBytes(StandardCharsets.UTF_8));
            assertCode("TIANJI_RESPONSE_TOO_LARGE", () -> client.resolve(query));
            response.set(mapper.writeValueAsBytes(new LinkedAgentStartResult(query, run("other", 1)))); status.set(202);
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submit(command));
            var missingDeadline = new AgentRunDTO("00000000-0000-0000-0000-000000000001", "review", 1, null,
                    AgentRunStatusEnum.QUEUED, null, false, 0, 0, 1, null, NOW, null);
            response.set(mapper.writeValueAsBytes(new LinkedAgentStartResult(query, missingDeadline)));
            assertCode("TIANJI_INVALID_RESPONSE", () -> client.submit(command));
            var roundedDeadline = new AgentRunDTO("00000000-0000-0000-0000-000000000001", "review", 1, null,
                    AgentRunStatusEnum.QUEUED, null, false, 0, 0, 1, NOW, NOW, null);
            response.set(mapper.writeValueAsBytes(new LinkedAgentStartResult(query, roundedDeadline)));
            assertEquals(roundedDeadline, client.submit(command).run());
            response.set(mapper.writeValueAsBytes(new LinkedAgentCloseResult(query, new LinkedAgentSubmissionDTO(true, null)))); status.set(404);
            var notFound = assertThrows(AgentClientException.class, () -> client.resolve(query));
            assertEquals(404, notFound.httpStatus()); assertEquals("TIANJI_RESOURCE_NOT_FOUND", notFound.code());
            response.set("{\"code\":\"TIANJI_DISPATCH_CLOSED\"}".getBytes(StandardCharsets.UTF_8)); status.set(500);
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.submit(command));
            status.set(409);
            assertCode("TIANJI_DISPATCH_CLOSED", () -> client.submit(command));
        }
    }

    @Test void does_not_repeat_writes_or_expose_the_raw_error_body() {
        var calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            respond(exchange, 503, "{\"code\":\"private-token-value\",\"message\":\"private-fixture\"}".getBytes(StandardCharsets.UTF_8));
        });
        try (var client = new HttpLinkedAgentClient(baseUri, HttpClient.newHttpClient(), operation -> "private-token-value", Duration.ofSeconds(3))) {
            var failure = assertThrows(AgentClientException.class, () -> client.submit(command()));
            assertEquals(1, calls.get()); assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", failure.code());
            assertFalse(failure.toString().contains("private-token-value")); assertFalse(failure.toString().contains("private-fixture"));
        }
    }

    @Test void token_acquisition_is_bounded_and_never_sends_after_timeout() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> { requests.incrementAndGet(); respond(exchange, 500, new byte[0]); });
        try (var client = new HttpLinkedAgentClient(baseUri, HttpClient.newHttpClient(), operation -> {
            entered.countDown();
            while (true) {
                try { if (release.await(100, TimeUnit.MILLISECONDS)) return "late-token"; }
                catch (InterruptedException ignored) { }
            }
        }, Duration.ofMillis(150), 1)) {
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.resolve(query(command())));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.resolve(query(command())));
            assertEquals(0, requests.get());
        } finally { release.countDown(); }
    }

    @Test void http_timeout_cancels_the_single_request_and_insecure_destinations_are_rejected() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> {
            requests.incrementAndGet(); entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, new byte[0]);
        });
        try (var client = client(Duration.ofMillis(600))) {
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.close(query(command())));
            assertTrue(entered.await(1, TimeUnit.SECONDS)); assertEquals(1, requests.get());
        } finally { release.countDown(); }
        assertThrows(IllegalArgumentException.class, () -> new HttpLinkedAgentClient(URI.create("http://example.com/"),
                HttpClient.newHttpClient(), operation -> "token", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new HttpLinkedAgentClient(baseUri,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), operation -> "token", Duration.ofSeconds(1)));
    }

    @Test void closing_the_client_cancels_an_in_flight_transport_without_retrying_it() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var requests = new AtomicInteger();
        handler.set(exchange -> {
            requests.incrementAndGet(); entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, new byte[0]);
        });
        var client = client(Duration.ofSeconds(10));
        try (var caller = Executors.newSingleThreadExecutor()) {
            var future = caller.submit(() -> client.close(query(command())));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            client.close();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertInstanceOf(AgentClientException.class, failure.getCause()).code());
            assertEquals(1, requests.get());
            assertCode("TIANJI_DEPENDENCY_UNAVAILABLE", () -> client.resolve(query(command())));
        } finally { release.countDown(); client.close(); }
    }

    private HttpLinkedAgentClient client(Duration timeout) {
        return new HttpLinkedAgentClient(baseUri, HttpClient.newHttpClient(), operation -> "test-token", timeout);
    }
    private static LinkedAgentStartCommand command() {
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "process-1", "occurrence-1", "a".repeat(64), NOW.plusSeconds(30));
        return new LinkedAgentStartCommand(new StartAgentRunCommand("review", 1, null, "intent-1", "order-1", "trace-1",
                new AgentInputDTO("private-fixture", List.of(), Map.of()), null), link);
    }
    private static LinkedAgentLookupQuery query(LinkedAgentStartCommand command) {
        return new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
    }
    private static AgentRunDTO run(String definition, long version) {
        return new AgentRunDTO("00000000-0000-0000-0000-000000000001", definition, version, null, AgentRunStatusEnum.QUEUED,
                null, false, 0, 0, 1, NOW.plusSeconds(60), NOW, null);
    }
    private static LinkedAgentRuntimeCommand runtimeCommand() {
        var deadline = NOW.plusSeconds(60);
        var link = new AgentProcessLink("ps-main", WorkerScopeTypeEnum.BPMN, "instance-1", "job-1", "a".repeat(64), deadline.minusSeconds(1));
        var limits = new TechnicalLimitsVO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 3, 1, 100, 20);
        return new LinkedAgentRuntimeCommand("1-runtime", link, "node-1", "review", 1L, "a".repeat(64), "b".repeat(64),
                "c".repeat(64), "d".repeat(64), Map.of("amount", "1.00"), Map.of(), limits, deadline, "correlation-1");
    }

    private static byte[] runtimeResponse(String key, String nodeExecutionRef, String state) {
        return """
                {"runId":"host:run/not-uuid","nodeExecutionRef":"%s","state":"%s","revision":2,"idempotencyKey":"%s"}
                """.formatted(nodeExecutionRef, state, key).getBytes(StandardCharsets.UTF_8);
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length); exchange.getResponseBody().write(body);
    }

    private static void respondRuntime(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        respond(exchange, status, body);
    }
    private static void assertCode(String expected, Runnable action) {
        assertEquals(expected, assertThrows(AgentClientException.class, action::run).code());
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
