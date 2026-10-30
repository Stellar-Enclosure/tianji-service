package top.egon.cola.platform.tianji.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidateQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryCandidatePageDTO;
import top.egon.cola.platform.tianji.contract.memory.ExtractMemoryPreferencesCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferenceExtractionItemDTO;
import top.egon.cola.platform.tianji.contract.memory.PrepareMemoryPreferencesQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryPreferencePreparationDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryStateEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryHitDTO;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import java.math.BigDecimal;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionListDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.CreateAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.DeleteAgentSessionCommand;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextWindowDTO;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpAgentClientTest {
    private static final String TOKEN = "test-token-value";
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AtomicReference<Handler> handler = new AtomicReference<>();
    private HttpServer server;
    private ExecutorService executor;
    private URI baseUri;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                handler.get().handle(exchange);
            } catch (IOException ignored) {
                // A timeout or cancellation can close the client side first.
            }
        });
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void start_query_result_and_cancel_follow_the_contract_without_exposing_engine_types() {
        AgentRunDTO run = run("run-1");
        AgentExecutionResult result = new AgentExecutionResult("run-1", AgentRunStatusEnum.COMPLETED,
                Map.of("answer", "ok"), null, "1", null, null, null);
        CancelAgentRunResult cancelled = new CancelAgentRunResult("run-1", true,
                AgentRunStatusEnum.RUNNING, 3, NOW);
        BlockingQueue<SeenRequest> seen = new LinkedBlockingQueue<>();
        handler.set(exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            seen.add(new SeenRequest(exchange.getRequestMethod(), path,
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestBody().readAllBytes()));
            Object body = path.endsWith("/result") ? result : path.endsWith("/cancel") ? cancelled : run;
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(body));
        });
        HttpAgentClient client = client(Duration.ofSeconds(3), 0);
        StartAgentRunCommand start = new StartAgentRunCommand("research", 1, null, "request-1", null,
                "trace-1", new AgentInputDTO("question", List.of(), Map.of()), null);
        CancelAgentRunCommand cancel = new CancelAgentRunCommand("run-1", "cancel-1", 2, "user request");

        assertEquals(run, client.start(start));
        assertEquals(run, client.get(new GetAgentRunQuery("run-1")));
        assertEquals(result, client.getResult(new GetAgentRunQuery("run-1")));
        assertEquals(cancelled, client.cancel(cancel));
        List<SeenRequest> requests = new ArrayList<>();
        seen.drainTo(requests);
        assertEquals(List.of("POST", "GET", "GET", "POST"), requests.stream().map(SeenRequest::method).toList());
        assertEquals(List.of("/api/tianji/v1/agent-runs", "/api/tianji/v1/agent-runs/run-1",
                "/api/tianji/v1/agent-runs/run-1/result", "/api/tianji/v1/agent-runs/run-1/cancel"),
                requests.stream().map(SeenRequest::path).toList());
        assertTrue(requests.stream().allMatch(request -> ("Bearer " + TOKEN).equals(request.authorization())));
        assertEquals("application/json", requests.getFirst().contentType());
        assertEquals(start, mapper.readValue(requests.getFirst().body(), StartAgentRunCommand.class));
        assertEquals(cancel, mapper.readValue(requests.getLast().body(), CancelAgentRunCommand.class));
    }

    @Test
    void list_encodes_the_cursor_and_returns_the_page() throws Exception {
        var expected = new AgentRunPageDTO(List.of(run("run-1")), "next-cursor");
        BlockingQueue<String> queries = new LinkedBlockingQueue<>();
        handler.set(exchange -> {
            queries.add(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(expected));
        });
        assertEquals(expected, client(Duration.ofSeconds(3), 0).list(new ListAgentRunsQuery("a+/=", 10)));
        assertEquals("limit=10&cursor=a%2B%2F%3D", queries.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void session_operations_encode_identifiers_and_do_not_retry_delete() throws Exception {
        String id = "a/会 +😀";
        AgentSessionDTO session = new AgentSessionDTO(id, NOW);
        AgentSessionListDTO list = new AgentSessionListDTO(List.of(session));
        BlockingQueue<String> seen = new LinkedBlockingQueue<>();
        AtomicInteger deletes = new AtomicInteger();
        handler.set(exchange -> {
            seen.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath() + "?"
                    + exchange.getRequestURI().getRawQuery());
            if (exchange.getRequestMethod().equals("DELETE")) {
                deletes.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
            } else {
                Object body = exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().getRawQuery() == null
                        ? list : session;
                respond(exchange, 200, "application/json", mapper.writeValueAsBytes(body));
            }
        });
        HttpAgentClient client = client(Duration.ofSeconds(3), 3);
        assertEquals(session, client.createSession(new CreateAgentSessionCommand(id)));
        assertEquals(session, client.getSession(new GetAgentSessionQuery(id)));
        assertEquals(list, client.listSessions());
        client.deleteSession(new DeleteAgentSessionCommand(id));
        String encoded = "sessionId=a%2F%E4%BC%9A+%2B%F0%9F%98%80";
        assertEquals("PUT /api/tianji/v1/agent-sessions?" + encoded, seen.poll(1, TimeUnit.SECONDS));
        assertEquals("GET /api/tianji/v1/agent-sessions/detail?" + encoded, seen.poll(1, TimeUnit.SECONDS));
        assertEquals("GET /api/tianji/v1/agent-sessions?null", seen.poll(1, TimeUnit.SECONDS));
        assertEquals("DELETE /api/tianji/v1/agent-sessions?" + encoded, seen.poll(1, TimeUnit.SECONDS));
        assertEquals(1, deletes.get());
        handler.set(exchange -> {
            deletes.incrementAndGet();
            respond(exchange, 503, "application/json", "{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8));
        });
        assertThrows(AgentClientException.class, () -> client.deleteSession(new DeleteAgentSessionCommand(id)));
        assertEquals(2, deletes.get());
    }

    @Test
    void session_backed_start_uses_the_combined_authorization_endpoint() throws Exception {
        BlockingQueue<String> paths = new LinkedBlockingQueue<>();
        handler.set(exchange -> {
            paths.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());
            respond(exchange, 202, "application/json", mapper.writeValueAsBytes(run("run-1")));
        });
        StartAgentRunCommand command = new StartAgentRunCommand("research", 1, "session-1", "request-1", null,
                null, new AgentInputDTO("question", List.of(), Map.of()), null);
        assertEquals("run-1", client(Duration.ofSeconds(3), 0).start(command).runId());
        assertEquals("POST /api/tianji/v1/agent-runs/with-session", paths.poll(1, TimeUnit.SECONDS));
    }

    @Test
    void session_metadata_must_match_the_requested_identifier() {
        handler.set(exchange -> respond(exchange, 200, "application/json",
                mapper.writeValueAsBytes(new AgentSessionDTO("other", NOW))));
        HttpAgentClient client = client(Duration.ofSeconds(3), 0);
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class,
                () -> client.createSession(new CreateAgentSessionCommand("expected"))).code());
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class,
                () -> client.getSession(new GetAgentSessionQuery("expected"))).code());
    }

    @Test
    void write_failure_is_not_retried_and_error_body_is_not_exposed() {
        AtomicInteger calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("Retry-After", "9");
            respond(exchange, 409, "application/json", ("{\"code\":\"TIANJI_REVISION_CONFLICT\","
                    + "\"correlationId\":\"trace-123\",\"message\":\"secret " + TOKEN + "\"}")
                    .getBytes(StandardCharsets.UTF_8));
        });
        AgentClientException error = assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 3)
                .cancel(new CancelAgentRunCommand("run-1", "cancel-1", 2, "cancel")));
        assertEquals(1, calls.get());
        assertEquals(409, error.httpStatus());
        assertEquals("TIANJI_REVISION_CONFLICT", error.code());
        assertEquals("trace-123", error.correlationId());
        assertEquals(Duration.ofSeconds(9), error.retryAfter());
        assertFalse(error.toString().contains(TOKEN));
        assertNull(error.getCause());
    }

    @Test
    void credentials_echoed_in_error_fields_are_not_returned() {
        handler.set(exchange -> respond(exchange, 409, "application/json",
                ("{\"code\":\"" + TOKEN + "\",\"correlationId\":\"prefix-" + TOKEN + "\"}")
                        .getBytes(StandardCharsets.UTF_8)));
        AgentClientException error = assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0)
                .cancel(new CancelAgentRunCommand("run-1", "cancel-1", 2, "cancel")));
        assertFalse(error.getMessage().contains(TOKEN));
        assertFalse(error.code().contains(TOKEN));
        assertNull(error.correlationId());
    }

    @Test
    void oversized_response_and_whole_body_timeout_are_bounded() {
        handler.set(exchange -> respond(exchange, 200, "application/json", new byte[2_097_153]));
        AgentClientException tooLarge = assertThrows(AgentClientException.class,
                () -> client(Duration.ofSeconds(3), 0).get(new GetAgentRunQuery("run-1")));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", tooLarge.code());

        CountDownLatch release = new CountDownLatch(1);
        handler.set(exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{');
            exchange.getResponseBody().flush();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        });
        try {
            AgentClientException timeout = assertTimeoutPreemptively(Duration.ofSeconds(3),
                    () -> assertThrows(AgentClientException.class,
                            () -> client(Duration.ofMillis(250), 0).get(new GetAgentRunQuery("run-1"))));
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", timeout.code());
        } finally {
            release.countDown();
        }
    }

    @Test
    void sse_reconnects_from_last_delivered_sequence_and_completes_with_exact_demand() throws Exception {
        BlockingQueue<String> cursors = new LinkedBlockingQueue<>();
        AtomicInteger connections = new AtomicInteger();
        handler.set(exchange -> {
            int connection = connections.incrementAndGet();
            cursors.add(exchange.getRequestURI().getRawQuery() + "|"
                    + exchange.getRequestHeaders().getFirst("Last-Event-ID"));
            String content = connection == 1 ? frame(1) : frame(1) + frame(2) + "event: stream.complete\n\n";
            respond(exchange, 200, "text/event-stream; charset=utf-8", content.getBytes(StandardCharsets.UTF_8));
        });
        RecordingSubscriber subscriber = new RecordingSubscriber(2);
        client(Duration.ofSeconds(3), 2).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(subscriber.terminal.await(5, TimeUnit.SECONDS));
        assertNull(subscriber.error.get());
        assertTrue(subscriber.completed);
        assertEquals(List.of(1L, 2L), subscriber.events.stream().map(AgentEventDTO::sequence).toList());
        assertEquals("afterSequence=0&limit=100|null", cursors.poll(1, TimeUnit.SECONDS));
        assertEquals("afterSequence=1&limit=100|1", cursors.poll(1, TimeUnit.SECONDS));
        assertEquals(2, connections.get());
    }

    @Test
    void sse_accepts_a_maximum_sized_persisted_payload_plus_event_metadata() throws Exception {
        var event = new AgentEventDTO("event-1", "run-1", 1, "TEXT_DELTA", null, null, null, NOW,
                "trace-1", "1", Map.of("text", "x".repeat(1_048_400)));
        String frame = "id: 1\nevent: agent.event\ndata: " + mapper.writeValueAsString(event)
                + "\n\nevent: stream.complete\n\n";
        assertTrue(frame.getBytes(StandardCharsets.UTF_8).length > 1_048_576);
        handler.set(exchange -> respond(exchange, 200, "text/event-stream", frame.getBytes(StandardCharsets.UTF_8)));
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(subscriber.terminal.await(5, TimeUnit.SECONDS));
        assertNull(subscriber.error.get());
        assertEquals(event, subscriber.events.getFirst());
        assertTrue(subscriber.completed);
    }

    @Test
    void planned_stream_rotation_reconnects_without_spending_failure_retries() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        handler.set(exchange -> {
            int count = connections.incrementAndGet();
            if (count == 1) {
                try { Thread.sleep(Duration.ofMillis(1100)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            String body = count == 1 ? "event: stream.reconnect\n\n" : frame(1) + "event: stream.complete\n\n";
            respond(exchange, 200, "text/event-stream", body.getBytes(StandardCharsets.UTF_8));
        });
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(subscriber.terminal.await(5, TimeUnit.SECONDS));
        assertTrue(subscriber.completed);
        assertNull(subscriber.error.get());
        assertEquals(2, connections.get());
    }

    @Test
    void repeated_immediate_rotation_is_bounded_as_a_disconnect() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        handler.set(exchange -> {
            connections.incrementAndGet();
            respond(exchange, 200, "text/event-stream", "event: stream.reconnect\n\n".getBytes(StandardCharsets.UTF_8));
        });
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 1).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(subscriber.terminal.await(5, TimeUnit.SECONDS));
        assertEquals("TIANJI_EVENT_STREAM_DISCONNECTED", ((AgentClientException) subscriber.error.get()).code());
        assertEquals(2, connections.get());
    }

    @Test
    void sse_honors_demand_and_closing_subscription_does_not_cancel_run() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        handler.set(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 200, "text/event-stream", (frame(1) + frame(2) + frame(3)
                    + "event: stream.complete\n\n").getBytes(StandardCharsets.UTF_8));
        });
        RecordingSubscriber subscriber = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertEquals(1, subscriber.received.poll(3, TimeUnit.SECONDS));
        assertNull(subscriber.received.poll(150, TimeUnit.MILLISECONDS));
        subscriber.subscription.request(1);
        assertEquals(2, subscriber.received.poll(3, TimeUnit.SECONDS));
        assertFalse(subscriber.completed);
        subscriber.subscription.cancel();
        assertFalse(subscriber.terminal.await(150, TimeUnit.MILLISECONDS));
        assertEquals(1, requests.get());
    }

    @Test
    void cancelling_does_not_wait_for_a_blocked_subscriber_callback() throws Exception {
        handler.set(exchange -> respond(exchange, 200, "text/event-stream", frame(1).getBytes(StandardCharsets.UTF_8)));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        Flow.Subscriber<AgentEventDTO> subscriber = new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription value) { subscription.set(value); value.request(1); }
            @Override public void onNext(AgentEventDTO value) {
                entered.countDown();
                while (release.getCount() != 0) {
                    try { release.await(); } catch (InterruptedException ignored) { }
                }
            }
            @Override public void onError(Throwable failure) { }
            @Override public void onComplete() { }
        };
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        try {
            CompletableFuture.runAsync(() -> subscription.get().cancel()).get(1, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    void invalid_demand_from_on_next_signals_error_after_the_callback_returns() throws Exception {
        handler.set(exchange -> respond(exchange, 200, "text/event-stream", frame(1).getBytes(StandardCharsets.UTF_8)));
        AtomicBoolean inCallback = new AtomicBoolean();
        AtomicBoolean concurrentSignal = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch terminal = new CountDownLatch(1);
        Flow.Subscriber<AgentEventDTO> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
            @Override public void onNext(AgentEventDTO value) {
                inCallback.set(true);
                subscription.request(0);
                inCallback.set(false);
            }
            @Override public void onError(Throwable value) {
                concurrentSignal.set(inCallback.get());
                failure.set(value);
                terminal.countDown();
            }
            @Override public void onComplete() { terminal.countDown(); }
        };
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
        assertTrue(terminal.await(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, failure.get());
        assertFalse(concurrentSignal.get());
    }

    @Test
    void sse_rejects_invalid_frames_gaps_utf8_and_expired_cursor() throws Exception {
        List<byte[]> invalid = List.of(
                "event: agent.event\ndata: not-json\n\n".getBytes(StandardCharsets.UTF_8),
                frame(2).getBytes(StandardCharsets.UTF_8),
                frame(1).replace("id: 1", "id: 99").getBytes(StandardCharsets.UTF_8),
                new byte[]{'d', 'a', 't', 'a', ':', ' ', (byte) 0xc3, (byte) 0x28, '\n', '\n'},
                ("data: " + "x".repeat(2_097_153) + "\n\n").getBytes(StandardCharsets.UTF_8));
        for (byte[] body : invalid) {
            handler.set(exchange -> respond(exchange, 200, "text/event-stream", body));
            RecordingSubscriber subscriber = new RecordingSubscriber(1);
            client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(subscriber);
            assertTrue(subscriber.terminal.await(5, TimeUnit.SECONDS));
            assertEquals("TIANJI_INVALID_EVENT_STREAM", ((AgentClientException) subscriber.error.get()).code());
            assertFalse(subscriber.completed);
        }
        handler.set(exchange -> respond(exchange, 410, "application/json", new byte[0]));
        RecordingSubscriber expired = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 99, 100)).subscribe(expired);
        assertTrue(expired.terminal.await(5, TimeUnit.SECONDS));
        assertEquals("TIANJI_EVENT_CURSOR_EXPIRED", ((AgentClientException) expired.error.get()).code());

        handler.set(exchange -> respond(exchange, 409, "application/json",
                "{\"code\":\"TIANJI_SESSION_BUSY\",\"correlationId\":\"trace-123\"}"
                        .getBytes(StandardCharsets.UTF_8)));
        RecordingSubscriber rejected = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(rejected);
        assertTrue(rejected.terminal.await(5, TimeUnit.SECONDS));
        AgentClientException error = (AgentClientException) rejected.error.get();
        assertEquals("TIANJI_SESSION_BUSY", error.code());
        assertEquals("trace-123", error.correlationId());

        handler.set(exchange -> respond(exchange, 409, "application/json", new byte[2_097_153]));
        RecordingSubscriber tooLarge = new RecordingSubscriber(1);
        client(Duration.ofSeconds(3), 0).events(new AgentEventsQuery("run-1", 0, 100)).subscribe(tooLarge);
        assertTrue(tooLarge.terminal.await(5, TimeUnit.SECONDS));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", ((AgentClientException) tooLarge.error.get()).code());
    }

    @Test
    void rejects_unsafe_transport_configuration_and_invalid_token_without_leaking_supplier_error() {
        assertThrows(IllegalArgumentException.class, () -> new HttpAgentClient(baseUri,
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build(), () -> TOKEN,
                Duration.ofSeconds(1), 0, Duration.ZERO));
        HttpAgentClient broken = new HttpAgentClient(baseUri, HttpClient.newHttpClient(),
                () -> { throw new IllegalStateException(TOKEN); }, Duration.ofSeconds(1), 0, Duration.ZERO);
        AgentClientException error = assertThrows(AgentClientException.class,
                () -> broken.get(new GetAgentRunQuery("run-1")));
        assertEquals("TIANJI_UNAUTHENTICATED", error.code());
        assertFalse(error.toString().contains(TOKEN));
        assertNull(error.getCause());
        assertThrows(IllegalArgumentException.class,
                () -> client(Duration.ofSeconds(1), 0).get(new GetAgentRunQuery("..")));
    }

    @Test
    void context_metadata_uses_an_exclusive_bounded_query_and_preserves_the_prepared_snapshot_contract() {
        var page = new AgentContextSnapshotPageDTO(List.of(contextSnapshot("run-1", 7)), 7);
        handler.set(exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals("/api/tianji/v1/agent-runs/run-1/context-snapshots", exchange.getRequestURI().getPath());
            assertEquals("afterModelCall=5&limit=1", exchange.getRequestURI().getRawQuery());
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(page));
        });
        assertEquals(page, client(Duration.ofSeconds(3), 0).contextSnapshots(new AgentContextSnapshotsQuery("run-1", 5, 1)));
    }

    @Test
    void context_metadata_rejects_wrong_run_stale_or_oversized_pages_and_scalar_enum_coercion_without_echoing_the_body() {
        var page = new AgentContextSnapshotPageDTO(List.of(contextSnapshot("run-1", 7)), null);
        var encoded = mapper.writeValueAsString(page);
        var response = new AtomicReference<>(encoded);
        handler.set(exchange -> respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8)));
        List<String> invalid = new ArrayList<>();
        invalid.add(encoded.replace("run-1", "other-run"));
        invalid.add(mapper.writeValueAsString(new AgentContextSnapshotPageDTO(List.of(contextSnapshot("run-1", 7), contextSnapshot("run-1", 8)), null)));
        invalid.add(encoded.replace("\"role\":\"SYSTEM\"", "\"role\":0"));
        invalid.add(encoded.replace("\"modelName\":\"model\"", "\"modelName\":123"));
        invalid.add(encoded.replace("\"estimatedInputTokens\":20", "\"estimatedInputTokens\":\"20\""));
        invalid.add(encoded.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
        invalid.add(encoded.substring(0, encoded.length() - 1) + ",\"privateBody\":\"private-detail\"}");
        for (var body : invalid) {
            response.set(body);
            var failure = assertThrows(AgentClientException.class,
                    () -> client(Duration.ofSeconds(3), 0).contextSnapshots(new AgentContextSnapshotsQuery("run-1", 5, 1)));
            assertEquals("TIANJI_INVALID_RESPONSE", failure.code()); assertFalse(failure.toString().contains("private-detail"));
        }
        response.set(encoded);
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class,
                () -> client(Duration.ofSeconds(3), 0).contextSnapshots(new AgentContextSnapshotsQuery("run-1", 7, 1))).code());
    }

    @Test
    void memory_operations_use_private_post_bodies_and_exact_owner_scoped_receipts() throws Exception {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var entry = memoryEntry("memory", owner);
        var get = new GetMemoryQuery(owner, "research", "memory"); var query = new MemoryRetrievalQuery(owner, "research", null, "private preference", null, 5);
        var upsert = new UpsertMemoryCommand("create", owner, "research", "memory", entry.kind(), 0, entry.content(), entry.source(), BigDecimal.ONE, true,
                entry.sensitivity(), entry.retentionPolicyRef(), null, null);
        var correct = new CorrectMemoryCommand("correct", owner, "research", "memory", 1, entry.content(), entry.source(), BigDecimal.ONE, "USER_CONFIRMED");
        var forget = new ForgetMemoryCommand("forget", owner, "research", "memory", 2, "USER_FORGET");
        var seen = new LinkedBlockingQueue<String>();
        handler.set(exchange -> {
            String operation = exchange.getRequestURI().getPath().substring("/api/tianji/v1/memories/".length());
            assertEquals("POST", exchange.getRequestMethod()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals("Bearer " + TOKEN, exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = exchange.getRequestBody().readAllBytes(); seen.add(operation);
            Object result = switch (operation) {
                case "get" -> { assertEquals(get, mapper.readValue(body, GetMemoryQuery.class)); yield entry; }
                case "retrieve" -> { assertEquals(query, mapper.readValue(body, MemoryRetrievalQuery.class)); yield new MemoryRetrievalPageDTO(owner, "research", List.of(new MemoryHitDTO(entry, 0.9)), null); }
                case "upsert" -> { assertEquals(upsert, mapper.readValue(body, UpsertMemoryCommand.class)); yield memoryReceipt("create", owner, "canonical-memory", 4, MemoryMutationOutcomeEnum.DEDUPLICATED); }
                case "correct" -> { assertEquals(correct, mapper.readValue(body, CorrectMemoryCommand.class)); yield memoryReceipt("correct", owner, "memory", 2, MemoryMutationOutcomeEnum.CORRECTED); }
                case "forget" -> { assertEquals(forget, mapper.readValue(body, ForgetMemoryCommand.class)); yield memoryReceipt("forget", owner, "memory", 3, MemoryMutationOutcomeEnum.FORGOTTEN); }
                default -> throw new AssertionError(operation);
            };
            respond(exchange, 200, "application/json", mapper.writeValueAsBytes(result));
        });
        var client = client(Duration.ofSeconds(3), 3); assertEquals(entry, client.getMemory(get));
        assertEquals(1, client.retrieveMemory(query).items().size()); assertEquals("canonical-memory", client.upsertMemory(upsert).memoryId());
        assertEquals(2, client.correctMemory(correct).revision()); assertEquals(MemoryStateEnum.FORGOTTEN, client.forgetMemory(forget).stateAtMutation());
        assertEquals(List.of("get", "retrieve", "upsert", "correct", "forget"), List.copyOf(seen));
    }

    @Test
    void memory_response_decoder_rejects_wrong_scope_unknown_fields_missing_flags_and_type_coercion() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); String encoded = mapper.writeValueAsString(memoryEntry("memory", owner));
        var response = new AtomicReference<>(encoded); handler.set(exchange -> respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8)));
        for (String invalid : List.of(encoded.replace("alice", "bob"), encoded.replace("research", "foreign"), encoded.replace("\"memoryId\":\"memory\"", "\"memoryId\":\"other\""),
                encoded.replace("\"revision\":1,", ""), encoded.replace("\"confirmed\":true,", ""), encoded.replace("\"confirmed\":true", "\"confirmed\":\"true\""),
                encoded.replace("\"revision\":1", "\"revision\":1.1"), encoded.replace("\"kind\":\"PREFERENCE\"", "\"kind\":0"),
                encoded.replace("\"effectiveAt\":\"" + NOW + "\"", "\"effectiveAt\":123"), encoded.replace("\"format\":\"short\"", "\"format\":123"),
                encoded.replace("\"state\":\"ACTIVE\"", "\"state\":\"FORGOTTEN\""), encoded.substring(0, encoded.length() - 1) + ",\"tenantId\":20}", encoded + " {}")) {
            response.set(invalid); var failure = assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0).getMemory(new GetMemoryQuery(owner, "research", "memory")));
            assertEquals("TIANJI_INVALID_RESPONSE", failure.code()); assertFalse(failure.toString().contains("private preference"));
        }
        response.set(encoded); assertEquals("memory", client(Duration.ofSeconds(3), 0).getMemory(new GetMemoryQuery(owner, "research", "memory")).memoryId());
    }

    @Test
    void memory_mutation_replies_are_bound_to_command_operation_revision_and_scope_without_retries() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var entry = memoryEntry("memory", owner);
        var command = new CorrectMemoryCommand("correct", owner, "research", "memory", 1, entry.content(), entry.source(), BigDecimal.ONE, "USER_CONFIRMED");
        var response = new AtomicReference<>(memoryReceipt("correct", owner, "memory", 2, MemoryMutationOutcomeEnum.CORRECTED));
        var calls = new AtomicInteger(); handler.set(exchange -> { calls.incrementAndGet(); respond(exchange, 200, "application/json", mapper.writeValueAsBytes(response.get())); });
        for (var invalid : List.of(memoryReceipt("foreign", owner, "memory", 2, MemoryMutationOutcomeEnum.CORRECTED), memoryReceipt("correct", owner, "other", 2, MemoryMutationOutcomeEnum.CORRECTED),
                memoryReceipt("correct", owner, "memory", 3, MemoryMutationOutcomeEnum.CORRECTED), memoryReceipt("correct", owner, "memory", 2, MemoryMutationOutcomeEnum.FORGOTTEN))) {
            response.set(invalid); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 3).correctMemory(command)).code());
        }
        assertEquals(4, calls.get());
        handler.set(exchange -> { calls.incrementAndGet(); respond(exchange, 503, "application/json", "{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8)); });
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 3).correctMemory(command)).code()); assertEquals(5, calls.get());
    }

    @Test
    void memory_fact_pagination_matches_postgres_utf8_c_order_and_the_original_query_bounds() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var entry = memoryEntry("😀", owner);
        var response = new AtomicReference<>(new MemoryRetrievalPageDTO(owner, "research", List.of(new MemoryHitDTO(entry, null)), "😀"));
        handler.set(exchange -> respond(exchange, 200, "application/json", mapper.writeValueAsBytes(response.get())));
        var query = new MemoryRetrievalQuery(owner, "research", null, null, "\uE000", 1);
        assertEquals("😀", client(Duration.ofSeconds(3), 0).retrieveMemory(query).nextAfterMemoryId());
        response.set(new MemoryRetrievalPageDTO(owner, "research", List.of(new MemoryHitDTO(entry, null), new MemoryHitDTO(memoryEntry("other", owner), null)), null));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0).retrieveMemory(query)).code());
        response.set(new MemoryRetrievalPageDTO(owner, "research", List.of(new MemoryHitDTO(entry, 0.8)), null));
        assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0).retrieveMemory(query)).code());
    }

    @Test
    void candidate_review_uses_a_private_bounded_query_and_rejects_active_or_foreign_pages() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var active = memoryEntry("memory", owner);
        var candidate = new MemoryEntryDTO(owner, active.scopeId(), active.memoryId(), active.kind(), active.revision(), active.supersedesRevision(), MemoryStateEnum.CANDIDATE,
                active.content(), active.contentSha256(), active.source(), active.confidence(), false, active.sensitivity(), active.retentionPolicyRef(), active.effectiveAt(), active.expiresAt(), active.createdAt(), active.updatedAt());
        var page = new MemoryCandidatePageDTO(owner, "research", List.of(candidate), "memory"); var encoded = mapper.writeValueAsString(page); var response = new AtomicReference<>(encoded);
        var query = new MemoryCandidateQuery(owner, "research", MemoryKindEnum.PREFERENCE, "before", 1);
        handler.set(exchange -> {
            assertEquals("POST", exchange.getRequestMethod()); assertEquals("/api/tianji/v1/memories/candidates", exchange.getRequestURI().getPath()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals(query, mapper.readValue(exchange.getRequestBody().readAllBytes(), MemoryCandidateQuery.class));
            respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(page, client(Duration.ofSeconds(3), 0).memoryCandidates(query));
        for (var invalid : List.of(encoded.replace("alice", "bob"), encoded.replace("research", "foreign"), encoded.replace("\"CANDIDATE\"", "\"ACTIVE\"").replace("\"confirmed\":false", "\"confirmed\":true"),
                encoded.replace("\"confirmed\":false,", ""), encoded.replace("\"revision\":1", "\"revision\":1.5"), encoded.replace("PREFERENCE", "FACT"), encoded.substring(0, encoded.length() - 1) + ",\"unknown\":true}")) {
            response.set(invalid); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0).memoryCandidates(query)).code());
        }
    }

    @Test
    void explicit_preference_extraction_binds_all_source_fields_and_item_receipts_without_automatic_replay() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
        var command = new ExtractMemoryPreferencesCommand(owner, "research", "run", "terminal", "source", "preferences@1#" + "a".repeat(64));
        var item = new MemoryMutationResultDTO(command.itemCommandId("language"), owner, "research", "memory", 1, MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE, NOW);
        var result = new MemoryPreferenceExtractionResultDTO(owner, "research", "run", "terminal", "source", command.policyRef(), MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of(new MemoryPreferenceExtractionItemDTO("language", item)));
        var encoded = mapper.writeValueAsString(result); var response = new AtomicReference<>(encoded); var calls = new AtomicInteger();
        handler.set(exchange -> {
            calls.incrementAndGet(); assertEquals("POST", exchange.getRequestMethod()); assertEquals("/api/tianji/v1/memories/extract-preferences", exchange.getRequestURI().getPath()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals(command, mapper.readValue(exchange.getRequestBody().readAllBytes(), ExtractMemoryPreferencesCommand.class));
            respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(result, client(Duration.ofSeconds(3), 2).extractMemoryPreferences(command));
        for (var invalid : List.of(encoded.replace("alice", "bob"), encoded.replace("terminal", "other"), encoded.replace("\"source\"", "\"foreign\""),
                encoded.replace(command.itemCommandId("language"), "foreign-command"), encoded.replace("\"CANDIDATE\"", "\"ACTIVE\""),
                encoded.replace("\"revision\":1", "\"revision\":1.5"), encoded.substring(0, encoded.length() - 1) + ",\"unknown\":true}")) {
            response.set(invalid); int before = calls.get();
            assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 2).extractMemoryPreferences(command)).code()); assertEquals(before + 1, calls.get());
        }
        calls.set(0); handler.set(exchange -> { calls.incrementAndGet(); respond(exchange, 503, "application/json", "{}".getBytes(StandardCharsets.UTF_8)); });
        assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 2).extractMemoryPreferences(command)); assertEquals(1, calls.get());
    }

    @Test
    void preference_preparation_selects_only_server_owned_configuration_and_checks_the_original_source() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var query = new PrepareMemoryPreferencesQuery(owner, "research", "run", "terminal");
        var command = new ExtractMemoryPreferencesCommand(owner, "research", "run", "terminal", "source", "preferences@1#" + "a".repeat(64));
        var prepared = new MemoryPreferencePreparationDTO(command, MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, 1, List.of("language"), MemorySensitivityEnum.CONFIDENTIAL);
        var encoded = mapper.writeValueAsString(prepared); var response = new AtomicReference<>(encoded);
        handler.set(exchange -> {
            assertEquals("POST", exchange.getRequestMethod()); assertEquals("/api/tianji/v1/memories/prepare-preferences", exchange.getRequestURI().getPath()); assertNull(exchange.getRequestURI().getRawQuery());
            assertEquals(query, mapper.readValue(exchange.getRequestBody().readAllBytes(), PrepareMemoryPreferencesQuery.class));
            respond(exchange, 200, "application/json", response.get().getBytes(StandardCharsets.UTF_8));
        });
        assertEquals(prepared, client(Duration.ofSeconds(3), 0).prepareMemoryPreferences(query));
        for (var invalid : List.of(encoded.replace("alice", "other"), encoded.replace("terminal", "other"), encoded.replace("\"candidateCount\":1", "\"candidateCount\":\"1\""),
                encoded.replace("\"language\"", "123"), encoded.replace("CONFIDENTIAL", "PUBLIC"), encoded.substring(0, encoded.length() - 1) + ",\"text\":\"unrequested-body\"}")) {
            response.set(invalid); assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(AgentClientException.class, () -> client(Duration.ofSeconds(3), 0).prepareMemoryPreferences(query)).code());
        }
    }

    private static MemoryEntryDTO memoryEntry(String id, MemoryOwnerDTO owner) {
        return new MemoryEntryDTO(owner, "research", id, MemoryKindEnum.PREFERENCE, 1, null, MemoryStateEnum.ACTIVE, new MemoryContentDTO("private preference", Map.of("format", "short")),
                "a".repeat(64), new MemorySourceDTO("explicit", "b".repeat(64), null, null), BigDecimal.ONE, true, MemorySensitivityEnum.CONFIDENTIAL,
                "retention@1#" + "c".repeat(64), NOW, NOW.plusSeconds(300), NOW, NOW);
    }
    private static MemoryMutationResultDTO memoryReceipt(String command, MemoryOwnerDTO owner, String id, long revision, MemoryMutationOutcomeEnum outcome) {
        return new MemoryMutationResultDTO(command, owner, "research", id, revision, outcome, outcome == MemoryMutationOutcomeEnum.FORGOTTEN ? MemoryStateEnum.FORGOTTEN : MemoryStateEnum.ACTIVE, NOW);
    }

    private static AgentContextSnapshotDTO contextSnapshot(String run, int number) {
        String hash = "a".repeat(64); String call = "call-" + number;
        var metadata = new AgentContextMetadataDTO(1, AgentContextMetadataDTO.PROJECTION_V1, hash, run, call, 1, "writer", "invocation", "session", 0, hash, null,
                hash, hash, hash, hash, hash, hash, hash, new AgentContextWindowDTO("model", "route", "policy", 100, 5), 20, 10,
                List.of(new AgentContextSourceDTO(AgentContextSourceKindEnum.DEFINITION, AgentContextRoleEnum.SYSTEM, "definition", "1", "published-version", hash, hash, 1, null)), List.of());
        return new AgentContextSnapshotDTO(run, call, number, 1, 1, metadata, NOW);
    }

    private HttpAgentClient client(Duration timeout, int reconnects) {
        return new HttpAgentClient(baseUri, HttpClient.newHttpClient(), () -> TOKEN,
                timeout, reconnects, Duration.ofMillis(10));
    }

    private AgentRunDTO run(String id) {
        return new AgentRunDTO(id, "research", 1, null, AgentRunStatusEnum.RUNNING, null,
                false, 1, 2, 0, null, NOW, null);
    }

    private String frame(long sequence) {
        AgentEventDTO event = new AgentEventDTO("event-" + sequence, "run-1", sequence,
                "RUNNING", null, null, null, NOW, "trace-1", "1", Map.of());
        return "id: " + sequence + "\nevent: agent.event\ndata: " + mapper.writeValueAsString(event) + "\n\n";
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    @FunctionalInterface
    private interface Handler { void handle(HttpExchange exchange) throws IOException; }

    private record SeenRequest(String method, String path, String authorization, String contentType, byte[] body) { }

    private static final class RecordingSubscriber implements Flow.Subscriber<AgentEventDTO> {
        private final long initialDemand;
        private final List<AgentEventDTO> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final BlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        private final CountDownLatch terminal = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private volatile Flow.Subscription subscription;
        private volatile boolean completed;

        private RecordingSubscriber(long initialDemand) { this.initialDemand = initialDemand; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(initialDemand); }
        @Override public void onNext(AgentEventDTO event) { events.add(event); received.add((int) event.sequence()); }
        @Override public void onError(Throwable failure) { error.set(failure); terminal.countDown(); }
        @Override public void onComplete() { completed = true; terminal.countDown(); }
    }
}
