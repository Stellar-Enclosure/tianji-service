package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.ByteArrayOutputStream;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCaseClientTest {
    private static final String TOKEN = "case-test-token";
    private static final Instant SUBMITTED = Instant.parse("2026-11-01T10:00:00.123456789Z");
    private static final Instant FINISHED = Instant.parse("2026-11-01T11:00:00.987654321Z");
    private static final String VERSION_A = "a".repeat(64);
    private static final String VERSION_B = "b".repeat(64);
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void cmmn_release_correlation_requires_both_input_schemas_to_be_empty() throws Exception {
        String xml = "<synthetic-cmmn/>";
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes(xml)));
        var type = ProcessModelTypeEnum.valueOf("CMMN");
        Map<String, ProcessValueTypeEnum> empty = Map.of();
        var inputs = Map.of("amount", ProcessValueTypeEnum.INTEGER);
        for (var schemas : List.of(List.of(empty, empty), List.of(empty, inputs), List.of(inputs, inputs))) {
            var requested = new PublishProcessReleaseCommand("release-1", List.of(
                    new ProcessModelResource(type, "governedCase", xml, schemas.get(0))));
            var returned = new ProcessReleaseDTO("release-1", VERSION_A, SUBMITTED, List.of(
                    new ProcessModelDTO(type, "governedCase", hash, schemas.get(1))));
            var transport = new FakeHttpClient(); transport.reply(200, "application/json", json(returned));
            var client = client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1));
            if (schemas.get(0).isEmpty() && schemas.get(1).isEmpty()) assertEquals(returned, assertDoesNotThrow(() -> client.publish(requested)));
            else assertEquals("TIANJI_INVALID_RESPONSE", assertThrows(ProcessClientException.class, () -> client.publish(requested)).code());
            assertEquals(1, transport.requests.size());
        }
    }

    @Test
    void case_calls_use_private_post_routes_preserve_prefix_headers_and_exact_command_bodies() {
        FakeHttpClient transport = new FakeHttpClient();
        StartCaseCommand start = new StartCaseCommand("formalCase", "release-1", "business-1",
                "start-command", "correlation-1");
        CaseInstanceQuery query = new CaseInstanceQuery("case-1");
        ActOnPlanItemCommand action = new ActOnPlanItemCommand("action-command", "case-1", "primary-1",
                VERSION_A, CasePlanItemActionEnum.START);
        CaseInstanceDTO active = activeCase();
        CaseInstanceDTO completed = completedCase();
        CaseOperationResult operation = operation();
        transport.reply(200, "application/json", json(active));
        transport.reply(200, "application/vnd.tianji.case+json", json(completed));
        transport.reply(200, "application/json; charset=UTF-8", json(operation));
        HttpProcessClient client = client(URI.create("https://process.example/prefix/"), transport,
                () -> TOKEN, Duration.ofSeconds(1));

        assertEquals(active, assertDoesNotThrow(() -> client.start(start)));
        assertEquals(completed, assertDoesNotThrow(() -> client.get(query)));
        assertEquals(operation, assertDoesNotThrow(() -> client.actOnPlanItem(action)));

        assertEquals(3, transport.requests.size());
        assertRequest(transport.requests.get(0), "/prefix/api/tianji/v1/cases/start", start, StartCaseCommand.class);
        assertRequest(transport.requests.get(1), "/prefix/api/tianji/v1/cases/get", query, CaseInstanceQuery.class);
        assertRequest(transport.requests.get(2), "/prefix/api/tianji/v1/cases/act-plan-item", action, ActOnPlanItemCommand.class);
        assertTrue(transport.requests.stream().allMatch(request -> request.uri().getRawQuery() == null));
        assertTrue(transport.requests.stream().allMatch(request -> "POST".equals(request.method())));
        assertTrue(transport.requests.stream().allMatch(request -> ("Bearer " + TOKEN)
                .equals(request.headers().firstValue("Authorization").orElse(null))));
        assertTrue(transport.requests.stream().allMatch(request -> "application/json"
                .equals(request.headers().firstValue("Accept").orElse(null))));
        assertTrue(transport.requests.stream().allMatch(request -> "application/json"
                .equals(request.headers().firstValue("Content-Type").orElse(null))));
        assertTrue(transport.requests.stream().allMatch(request -> "no-store"
                .equals(request.headers().firstValue("Cache-Control").orElse(null))));
        assertTrue(transport.requests.stream().noneMatch(request -> request.headers().map().keySet().stream()
                .anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("tenant")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("actor")
                        || name.toLowerCase(java.util.Locale.ROOT).contains("role"))));
    }

    @Test
    void strict_case_binding_rejects_mismatches_status_media_and_accepts_terminal_start_replay() {
        StartCaseCommand start = new StartCaseCommand("formalCase", "release-1", "business-1",
                "start-command", "correlation-1");

        FakeHttpClient terminalTransport = new FakeHttpClient();
        terminalTransport.reply(200, "application/json", json(completedCase()));
        assertEquals(CaseInstanceStatusEnum.COMPLETED,
                assertDoesNotThrow(() -> client(URI.create("https://process.example/"), terminalTransport,
                        () -> TOKEN, Duration.ofSeconds(1)).start(start)).status());

        assertInvalidResponse(start, activeCaseWith("wrong", "release-1", "business-1", "correlation-1"), 200, "application/json");
        assertInvalidResponse(start, activeCaseWith("formalCase", "wrong", "business-1", "correlation-1"), 200, "application/json");
        assertInvalidResponse(start, activeCaseWith("formalCase", "release-1", "wrong", "correlation-1"), 200, "application/json");
        assertInvalidResponse(start, activeCaseWith("formalCase", "release-1", "business-1", "wrong"), 200, "application/json");
        assertInvalidResponse(start, activeCase(), 201, "application/json");
        assertInvalidResponse(start, activeCase(), 200, "text/plain");

        FakeHttpClient getMismatch = new FakeHttpClient();
        getMismatch.reply(200, "application/json", json(activeCaseWithId("other-case")));
        ProcessClientException getFailure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), getMismatch, () -> TOKEN, Duration.ofSeconds(1))
                        .get(new CaseInstanceQuery("case-1")));
        assertEquals("TIANJI_INVALID_RESPONSE", getFailure.code());

        for (CaseOperationResult mismatch : List.of(
                new CaseOperationResult("other-command", "case-1", "primary-1", "primaryTask",
                        CasePlanItemActionEnum.START, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                        VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED),
                new CaseOperationResult("action-command", "other-case", "primary-1", "primaryTask",
                        CasePlanItemActionEnum.START, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                        VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED),
                new CaseOperationResult("action-command", "case-1", "other-item", "primaryTask",
                        CasePlanItemActionEnum.START, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                        VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED),
                new CaseOperationResult("action-command", "case-1", "primary-1", "primaryTask",
                        CasePlanItemActionEnum.DISABLE, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                        VERSION_A, CasePlanItemStateEnum.DISABLED, VERSION_B, FINISHED),
                new CaseOperationResult("action-command", "case-1", "primary-1", "primaryTask",
                        CasePlanItemActionEnum.START, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                        "c".repeat(64), CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED))) {
            FakeHttpClient transport = new FakeHttpClient();
            transport.reply(200, "application/json", json(mismatch));
            ProcessClientException failure = assertThrows(ProcessClientException.class,
                    () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                            .actOnPlanItem(actionCommand()));
            assertEquals("TIANJI_INVALID_RESPONSE", failure.code());
        }
    }

    @Test
    void case_transport_guard_runs_before_credentials_while_legacy_process_http_remains_unchanged() {
        AtomicInteger unsafeTokens = new AtomicInteger();
        FakeHttpClient unsafeTransport = new FakeHttpClient();
        HttpProcessClient unsafe = client(URI.create("http://process.example/prefix/"), unsafeTransport,
                () -> { unsafeTokens.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> unsafe.start(startCommand()));
        assertEquals(0, unsafeTokens.get());
        assertTrue(unsafeTransport.requests.isEmpty());

        AtomicInteger nonNormalizedTokens = new AtomicInteger();
        FakeHttpClient nonNormalizedTransport = new FakeHttpClient();
        HttpProcessClient nonNormalized = client(URI.create("https://process.example/a/../prefix/"), nonNormalizedTransport,
                () -> { nonNormalizedTokens.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> nonNormalized.start(startCommand()));
        assertEquals(0, nonNormalizedTokens.get());
        assertTrue(nonNormalizedTransport.requests.isEmpty());

        FakeHttpClient legacyTransport = new FakeHttpClient();
        ProcessInstanceDTO process = new ProcessInstanceDTO("process-1", "legacy", "release-1", "business-1",
                "correlation-1", ProcessInstanceStatusEnum.ACTIVE, SUBMITTED, null);
        legacyTransport.reply(200, "application/json", json(process));
        HttpProcessClient legacy = client(URI.create("http://process.example/prefix/"), legacyTransport,
                () -> TOKEN, Duration.ofSeconds(1));
        assertEquals(process, legacy.start(new StartProcessCommand("legacy", "release-1", "business-1",
                "legacy-command", "correlation-1", Map.of())));

        AtomicInteger humanTokens = new AtomicInteger();
        HttpProcessClient unsafeHuman = client(URI.create("http://process.example/"), new FakeHttpClient(),
                () -> { humanTokens.incrementAndGet(); return TOKEN; }, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> unsafeHuman.getHumanTask(new GetHumanTaskQuery("task-1")));
        assertEquals(0, humanTokens.get());
    }

    @Test
    void case_errors_are_one_send_code_bounded_correlation_free_and_sync_dispatch_failures_are_masked() {
        FakeHttpClient known = new FakeHttpClient();
        known.reply(409, "application/json", bytes("{\"code\":\"TIANJI_TENANT_CONTEXT_CHANGED\",\"correlationId\":\"private\"}"));
        ProcessClientException knownFailure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), known, () -> TOKEN, Duration.ofSeconds(1))
                        .actOnPlanItem(actionCommand()));
        assertEquals(409, knownFailure.httpStatus());
        assertEquals("TIANJI_TENANT_CONTEXT_CHANGED", knownFailure.code());
        assertNull(knownFailure.correlationId());
        assertEquals(1, known.requests.size());
        assertFalse(knownFailure.toString().contains("private"));

        FakeHttpClient unknown = new FakeHttpClient();
        unknown.reply(409, "application/json", bytes("{\"code\":\"PRIVATE_UNKNOWN\",\"correlationId\":\"private\"}"));
        ProcessClientException fallback = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), unknown, () -> TOKEN, Duration.ofSeconds(1))
                        .actOnPlanItem(actionCommand()));
        assertEquals("TIANJI_IDEMPOTENCY_CONFLICT", fallback.code());
        assertNull(fallback.correlationId());
        assertEquals(1, unknown.requests.size());

        FakeHttpClient retryTransport = new FakeHttpClient();
        retryTransport.replies.add(new Reply(429,
                HttpHeaders.of(Map.of("Content-Type", List.of("application/json"),
                        "Retry-After", List.of("172800")), (name, value) -> true),
                List.of(bytes("{\"code\":\"TIANJI_CAPACITY_EXCEEDED\"}"))));
        ProcessClientException retry = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), retryTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .get(new CaseInstanceQuery("case-1")));
        assertEquals(Duration.ofDays(1), retry.retryAfter());
        assertEquals(1, retryTransport.requests.size());

        FakeHttpClient invalidRetryTransport = new FakeHttpClient();
        invalidRetryTransport.replies.add(new Reply(503,
                HttpHeaders.of(Map.of("Content-Type", List.of("application/json"),
                        "Retry-After", List.of("private-invalid-date")), (name, value) -> true),
                List.of(bytes("{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}"))));
        ProcessClientException invalidRetry = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), invalidRetryTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .get(new CaseInstanceQuery("case-1")));
        assertNull(invalidRetry.retryAfter());
        assertFalse(invalidRetry.toString().contains("private-invalid-date"));
        assertEquals(1, invalidRetryTransport.requests.size());

        FakeHttpClient syncFailure = new FakeHttpClient();
        syncFailure.syncFailure = new IllegalStateException("private dispatch detail");
        ProcessClientException masked = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), syncFailure, () -> TOKEN, Duration.ofSeconds(1))
                        .start(startCommand()));
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", masked.code());
        assertNull(masked.getCause());
        assertFalse(masked.toString().contains("private dispatch detail"));

        FakeHttpClient legacyFailure = new FakeHttpClient();
        IllegalStateException original = new IllegalStateException("legacy behavior");
        legacyFailure.syncFailure = original;
        IllegalStateException propagated = assertThrows(IllegalStateException.class,
                () -> client(URI.create("https://process.example/"), legacyFailure, () -> TOKEN, Duration.ofSeconds(1))
                        .evaluate(new EvaluateDecisionCommand("decision", "release-1", "request-1", "correlation-1", Map.of())));
        assertEquals(original, propagated);
    }

    @Test
    void case_timeout_cancels_the_request_future_and_oversize_body_cancels_the_real_subscriber() {
        FakeHttpClient timeoutTransport = new FakeHttpClient();
        timeoutTransport.incomplete = true;
        ProcessClientException timeout;
        try {
            timeout = assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> assertThrows(ProcessClientException.class,
                            () -> client(URI.create("https://process.example/"), timeoutTransport, () -> TOKEN, Duration.ofNanos(1))
                                    .get(new CaseInstanceQuery("case-1"))));
        } finally {
            if (timeoutTransport.incompleteFuture != null && !timeoutTransport.incompleteFuture.isDone()) {
                timeoutTransport.incompleteFuture.cancel(true);
            }
        }
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", timeout.code());
        assertNotNull(timeoutTransport.incompleteFuture);
        assertTrue(timeoutTransport.incompleteFuture.isCancelled());
        assertEquals(1, timeoutTransport.requests.size());

        FakeHttpClient oversizeTransport = new FakeHttpClient();
        oversizeTransport.reply(200, "application/json", new byte[128 * 1024 + 1]);
        ProcessClientException oversize = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), oversizeTransport, () -> TOKEN, Duration.ofSeconds(1))
                        .get(new CaseInstanceQuery("case-1")));
        assertEquals("TIANJI_RESPONSE_TOO_LARGE", oversize.code());
        assertNotNull(oversizeTransport.lastSubscription);
        assertTrue(oversizeTransport.lastSubscription.cancelled);
        assertEquals(1, oversizeTransport.requests.size());
    }

    @Test
    void controlled_legacy_process_decision_and_https_human_task_calls_keep_existing_semantics() {
        FakeHttpClient transport = new FakeHttpClient();
        ProcessInstanceDTO process = new ProcessInstanceDTO("process-1", "legacy", "release-1", "business-1",
                "correlation-1", ProcessInstanceStatusEnum.ACTIVE, SUBMITTED, null);
        DecisionResultDTO decision = new DecisionResultDTO("12345678-1234-1234-1234-123456789abc", "decision",
                "release-1", "correlation-1", SUBMITTED, List.of(), List.of());
        HumanTaskDTO task = new HumanTaskDTO("task-1", HumanTaskScopeTypeEnum.PROCESS, "process-1", "release-1",
                "legacy", "review", "Review", HumanTaskKindEnum.ACTION, null, VERSION_A, "operator",
                List.of("operator"), false, false, false, Map.of(), SUBMITTED, null);
        transport.reply(200, "application/json", json(process));
        transport.reply(200, "application/json", json(decision));
        transport.reply(200, "application/json", json(task));
        HttpProcessClient client = client(URI.create("https://process.example/"), transport,
                () -> TOKEN, Duration.ofSeconds(1));

        assertEquals(process, client.start(new StartProcessCommand("legacy", "release-1", "business-1",
                "legacy-command", "correlation-1", Map.of())));
        assertEquals(decision, client.evaluate(new EvaluateDecisionCommand("decision", "release-1", "request-1",
                "correlation-1", Map.of())));
        assertEquals(task, client.getHumanTask(new GetHumanTaskQuery("task-1")));
        assertEquals(List.of("/api/tianji/v1/process-instances", "/api/tianji/v1/decisions/evaluate",
                "/api/tianji/v1/human-tasks/get"), transport.requests.stream().map(request -> request.uri().getPath()).toList());
    }

    private void assertInvalidResponse(StartCaseCommand command, CaseInstanceDTO response, int status, String contentType) {
        FakeHttpClient transport = new FakeHttpClient();
        transport.reply(status, contentType, json(response));
        ProcessClientException failure = assertThrows(ProcessClientException.class,
                () -> client(URI.create("https://process.example/"), transport, () -> TOKEN, Duration.ofSeconds(1))
                        .start(command));
        assertEquals("TIANJI_INVALID_RESPONSE", failure.code());
        assertEquals(1, transport.requests.size());
    }

    private <T> void assertRequest(HttpRequest request, String path, T expected, Class<T> type) {
        assertEquals(path, request.uri().getRawPath());
        assertEquals(expected, mapper.readValue(body(request), type));
    }

    private static HttpProcessClient client(URI base, HttpClient transport,
                                            java.util.function.Supplier<String> token, Duration timeout) {
        return new HttpProcessClient(base, transport, token, timeout);
    }

    private byte[] json(Object value) {
        return mapper.writeValueAsBytes(value);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static StartCaseCommand startCommand() {
        return new StartCaseCommand("formalCase", "release-1", "business-1", "start-command", "correlation-1");
    }

    private static ActOnPlanItemCommand actionCommand() {
        return new ActOnPlanItemCommand("action-command", "case-1", "primary-1", VERSION_A,
                CasePlanItemActionEnum.START);
    }

    private static CaseInstanceDTO activeCase() {
        return activeCaseWith("formalCase", "release-1", "business-1", "correlation-1");
    }

    private static CaseInstanceDTO activeCaseWith(String definition, String release, String business, String correlation) {
        return new CaseInstanceDTO("case-1", definition, release, business, correlation, CaseInstanceStatusEnum.ACTIVE,
                SUBMITTED, null, List.of(new CasePlanItemDTO("primary-1", "primaryTask", CasePlanItemKindEnum.HUMAN_TASK,
                CasePlanItemStateEnum.ENABLED, VERSION_A,
                List.of(CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START))));
    }

    private static CaseInstanceDTO activeCaseWithId(String id) {
        CaseInstanceDTO value = activeCase();
        return new CaseInstanceDTO(id, value.definitionKey(), value.releaseVersion(), value.businessKey(),
                value.correlationId(), value.status(), value.submittedAt(), value.finishedAt(), value.planItems());
    }

    private static CaseInstanceDTO completedCase() {
        return new CaseInstanceDTO("case-1", "formalCase", "release-1", "business-1", "correlation-1",
                CaseInstanceStatusEnum.COMPLETED, SUBMITTED, FINISHED, List.of());
    }

    private static CaseOperationResult operation() {
        return new CaseOperationResult("action-command", "case-1", "primary-1", "primaryTask",
                CasePlanItemActionEnum.START, "release-1", "formalCase", CasePlanItemStateEnum.ENABLED,
                VERSION_A, CasePlanItemStateEnum.ACTIVE, VERSION_B, FINISHED);
    }

    private static byte[] body(HttpRequest request) {
        BodyCapture capture = new BodyCapture();
        request.bodyPublisher().orElseThrow().subscribe(capture);
        return capture.body.join();
    }

    private static SSLContext defaultSslContext() {
        try {
            return SSLContext.getDefault();
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("default TLS context is unavailable", failure);
        }
    }

    private record Reply(int status, HttpHeaders headers, List<byte[]> chunks) {
        static Reply of(int status, String contentType, byte[] body) {
            return new Reply(status, HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true),
                    List.of(body));
        }
    }

    private static final class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Reply> replies = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();
        private RuntimeException syncFailure;
        private boolean incomplete;
        private CompletableFuture<?> incompleteFuture;
        private TrackingSubscription lastSubscription;

        void reply(int status, String contentType, byte[] body) {
            replies.add(Reply.of(status, contentType, body));
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() { return defaultSslContext(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            throw new UnsupportedOperationException("synchronous fake send is unsupported");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> responseBodyHandler) {
            requests.add(request);
            if (syncFailure != null) throw syncFailure;
            if (incomplete) {
                CompletableFuture<HttpResponse<T>> future = new CompletableFuture<>();
                incompleteFuture = future;
                return future;
            }
            Reply reply = replies.removeFirst();
            HttpResponse.BodySubscriber<T> subscriber = responseBodyHandler.apply(new ResponseInfo(reply));
            TrackingSubscription subscription = new TrackingSubscription();
            lastSubscription = subscription;
            subscriber.onSubscribe(subscription);
            for (byte[] chunk : reply.chunks()) subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture().handle((value, failure) -> {
                if (failure != null) throw new CompletionException(failure);
                return new FakeResponse<>(reply.status(), request, reply.headers(), value);
            });
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                                 HttpResponse.BodyHandler<T> responseBodyHandler,
                                                                 HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }
    }

    private record ResponseInfo(Reply reply) implements HttpResponse.ResponseInfo {
        @Override public int statusCode() { return reply.status(); }
        @Override public HttpHeaders headers() { return reply.headers(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private record FakeResponse<T>(int statusCode, HttpRequest request, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public Optional<SSLSession> sslSession() { return Optional.empty(); }
        @Override public URI uri() { return request.uri(); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private static final class TrackingSubscription implements Flow.Subscription {
        private boolean cancelled;
        @Override public void request(long count) { }
        @Override public void cancel() { cancelled = true; }
    }

    private static final class BodyCapture implements Flow.Subscriber<ByteBuffer> {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        @Override public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
        @Override public void onNext(ByteBuffer buffer) {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            output.writeBytes(bytes);
        }
        @Override public void onError(Throwable failure) { body.completeExceptionally(failure); }
        @Override public void onComplete() { body.complete(output.toByteArray()); }
    }
}
