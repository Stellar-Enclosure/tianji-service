package top.egon.cola.platform.tianji.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerFailureDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessWorkerRecoveryClient;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;

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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessWorkerRecoveryHttpTest {
    private static final String TOKEN = "private-token";
    private static final String INSTANCE = "space /+";
    private static final String VERSION = "a".repeat(64);
    private static final String REASON = "Read-only evidence \u00a0 \uD83D\uDE80";
    private static final String OBSERVED = "2026-11-05T05:50:00.000001Z";
    private static final String DECIDED = "2026-11-05T05:50:01.000001Z";
    private static final String HTTPS = "https://process.example/context/";
    private static final String INVALID = "TIANJI_INVALID_RESPONSE";
    private static final String UNAVAILABLE = "TIANJI_DEPENDENCY_UNAVAILABLE";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int CAP = 2 * 1_048_576;
    private static final GetProcessInstanceQuery QUERY = new GetProcessInstanceQuery(INSTANCE);
    private static final RecoverProcessWorkerCommand COMMAND =
            new RecoverProcessWorkerCommand(INSTANCE, "job-1", "recover:1", VERSION, REASON);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ELIGIBLE = """
            {"jobId":"job-1","activityId":"worker","nativeDeadletter":true,
             "status":"RETRY_AVAILABLE","reason":"NONE","reportedFailure":"RETRYABLE","expectedVersion":"%s"}
            """.formatted(VERSION).strip();
    private static final String HISTORICAL = """
            {"jobId":"job-2","activityId":null,"nativeDeadletter":false,
             "status":"HISTORICAL_FAILURE","reason":"UNKNOWN_OUTCOME","reportedFailure":"UNKNOWN","expectedVersion":null}
            """.strip();
    private static final String OBSERVATION = observation(ELIGIBLE + "," + HISTORICAL);
    private static final String RESULT = """
            {"instanceId":"space /+","jobId":"job-1","occurrenceId":"job-1","commandId":"recover:1",
             "status":"RETRY_SCHEDULED","retries":1,"decidedAt":"%s"}
            """.formatted(DECIDED);
    private static final Map<Integer, String> SHARED_ERRORS = Map.of(
            400, "TIANJI_INVALID_COMMAND",
            401, "TIANJI_UNAUTHENTICATED",
            403, "TIANJI_PERMISSION_DENIED",
            404, "TIANJI_RESOURCE_NOT_FOUND",
            409, "TIANJI_TENANT_CONTEXT_CHANGED",
            500, "TIANJI_INTERNAL_ERROR",
            503, UNAVAILABLE);
    private static final Map<String, Integer> POST_ERRORS = Map.of(
            "TIANJI_IDEMPOTENCY_CONFLICT", 409,
            "TIANJI_PROCESS_VERSION_CONFLICT", 409,
            "TIANJI_PROCESS_WORKER_RECOVERY_BLOCKED", 409,
            "TIANJI_REQUEST_TOO_LARGE", 413);

    @Test
    void recovery_capability_is_available() {
        assertInstanceOf(ProcessWorkerRecoveryClient.class,
                new HttpProcessClient(URI.create(HTTPS), new FakeHttpClient(), () -> TOKEN, TIMEOUT));
    }

    @Test
    void observes_exact_path_and_maps_closed_rows() {
        var fake = new FakeHttpClient();
        fake.reply(200, "application/json; charset=UTF-8", OBSERVATION);
        var result = assertDoesNotThrow(() -> client(fake).workerFailures(QUERY));
        var request = assertSingleRequest(fake, "GET", "worker-failures");
        assertFalse(request.headers().firstValue("Content-Type").isPresent());
        assertEquals(0, body(request).length);
        assertEquals(INSTANCE, result.instanceId());
        assertEquals(Instant.parse(OBSERVED), result.observedAt());
        assertEquals(List.of("job-1", "job-2"), result.failures().stream().map(ProcessWorkerFailureDTO::jobId).toList());
        var eligible = result.failures().getFirst();
        assertEquals(ProcessWorkerFailureDTO.StatusEnum.RETRY_AVAILABLE, eligible.status());
        assertEquals(VERSION, eligible.expectedVersion());
        var historical = result.failures().getLast();
        assertEquals(ProcessWorkerFailureDTO.ReasonEnum.UNKNOWN_OUTCOME, historical.reason());
        assertNull(historical.activityId());
        assertNull(historical.expectedVersion());
    }

    @Test
    void posts_exact_command_once_and_explicit_replay_preserves_result() {
        var fake = new FakeHttpClient();
        fake.reply(200, "application/json", RESULT);
        fake.reply(200, "application/json", RESULT);
        var recovery = client(fake);
        var first = assertDoesNotThrow(() -> recovery.recoverWorker(COMMAND));
        var request = assertSingleRequest(fake, "POST", "worker-recoveries");
        assertEquals("application/json", request.headers().firstValue("Content-Type").orElseThrow());
        var json = JSON.readTree(body(request));
        assertEquals(Set.of("instanceId", "jobId", "commandId", "expectedVersion", "reason"), Set.copyOf(json.propertyNames()));
        assertEquals(INSTANCE, json.path("instanceId").asText());
        assertEquals(COMMAND.jobId(), json.path("jobId").asText());
        assertEquals(COMMAND.commandId(), json.path("commandId").asText());
        assertEquals(VERSION, json.path("expectedVersion").asText());
        assertEquals(REASON, json.path("reason").asText());
        assertEquals(new RecoverProcessWorkerResult(INSTANCE, "job-1", "job-1", "recover:1",
                RecoverProcessWorkerResult.StatusEnum.RETRY_SCHEDULED, 1, Instant.parse(DECIDED)), first);
        assertEquals(first, assertDoesNotThrow(() -> recovery.recoverWorker(COMMAND)));
        assertEquals(2, fake.requests.size());
        assertTrue(Arrays.equals(body(request), body(fake.requests.getLast())));
    }

    @Test
    void rejects_unsafe_transport_before_fetching_credentials() {
        for (String base : List.of("http://process.example/", "http://localhost.evil/",
                "https://process.example/context/../hidden/", "http://127.0.0.1/context/./nested/")) {
            var fake = new FakeHttpClient();
            var tokenCalls = new AtomicInteger();
            var recovery = client(base, fake, () -> {
                tokenCalls.incrementAndGet();
                return TOKEN;
            }, TIMEOUT);
            assertThrows(IllegalArgumentException.class, () -> recovery.workerFailures(QUERY), base);
            assertThrows(IllegalArgumentException.class, () -> recovery.recoverWorker(COMMAND), base);
            assertEquals(0, tokenCalls.get());
            assertTrue(fake.requests.isEmpty());
        }
        for (String base : List.of("http://localhost", "http://127.0.0.1", "http://[::1]", HTTPS)) {
            var fake = new FakeHttpClient();
            fake.reply(200, "application/json", observation(""));
            assertDoesNotThrow(() -> client(base, fake, () -> TOKEN, TIMEOUT).workerFailures(QUERY), base);
            assertEquals(1, fake.requests.size());
        }
        for (var redirect : List.of(HttpClient.Redirect.ALWAYS, HttpClient.Redirect.NORMAL)) {
            assertThrows(IllegalArgumentException.class,
                    () -> new HttpProcessClient(URI.create(HTTPS), new FakeHttpClient(redirect), () -> TOKEN, TIMEOUT));
        }
    }

    @Test
    void maps_only_the_documented_json_status_code_pairs() {
        SHARED_ERRORS.forEach((status, code) -> {
            assertReplyError(false, status, "application/json", error(code), code);
            assertReplyError(true, status, "application/json", error(code), code);
        });
        POST_ERRORS.forEach((code, status) -> {
            assertReplyError(true, status, "application/json", error(code), code);
            assertReplyError(false, status, "application/json", error(code), INVALID);
        });
        for (boolean post : List.of(false, true)) {
            for (int status : List.of(201, 204, 302, 307, 415, 429, 502)) {
                assertReplyError(post, status, "application/json", error(UNAVAILABLE), INVALID);
            }
            assertReplyError(post, 409, "application/json", error("TIANJI_PROCESS_CANCEL_BLOCKED"), INVALID);
            assertReplyError(post, 409, "application/json", error("TIANJI_INVALID_COMMAND"), INVALID);
            assertReplyError(post, 503, "application/problem+json", error(UNAVAILABLE), INVALID);
            assertReplyError(post, 503, null, error(UNAVAILABLE), INVALID);
            assertReplyError(post, 503, "text/html", REASON + TOKEN, INVALID);
            assertReplyError(post, 503, "application/json", "{\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\"}", INVALID);
            assertReplyError(post, 503, "application/json", error(UNAVAILABLE).replace("null", "\"foreign-id\""), INVALID);
            assertReplyError(post, 503, "application/json", error(UNAVAILABLE).replace("null", "null,\"extra\":0"), INVALID);
            assertReplyError(post, 503, "application/json", error(UNAVAILABLE).replace("null", "null,\"code\":\"TIANJI_DEPENDENCY_UNAVAILABLE\""), INVALID);
            assertReplyError(post, 503, "application/json", error(UNAVAILABLE) + "{}", INVALID);
        }
    }

    @Test
    void rejects_malformed_success_and_wrong_affinity() {
        for (String malformed : List.of("", "[]", "null", OBSERVATION + "{}", OBSERVATION.replace("\"instanceId\"", "\"unexpected\""),
                OBSERVATION.replace("\"instanceId\":", "\"instanceId\":\"foreign-id\",\"instanceId\":"),
                OBSERVATION.replace(INSTANCE, "foreign-id"), OBSERVATION.replace("\"observedAt\"", "\"unknownTime\""),
                OBSERVATION.replace("\"" + OBSERVED + "\"", "null"), OBSERVATION.replace(OBSERVED, "2026-11-05T05:50:00.000001+00:00"),
                OBSERVATION.replace(OBSERVED, "2026-11-05T05:50:00.000001z"), OBSERVATION.replace(OBSERVED, "2026-11-05T05:50:00.000001000Z"),
                observation(ELIGIBLE.replace("\"worker\"", "\"\\uD800\"")), observation(ELIGIBLE.replace("\"worker\"", "null")),
                observation(ELIGIBLE.replace("\"activityId\":\"worker\",", "")), observation(ELIGIBLE.replace("true", "\"true\"")),
                observation(ELIGIBLE.replace("RETRY_AVAILABLE", "UNKNOWN")), observation(ELIGIBLE.replace("RETRYABLE", "PERMANENT")),
                observation(ELIGIBLE.replace(VERSION, VERSION.toUpperCase(java.util.Locale.ROOT))),
                observation(ELIGIBLE.replace("\"" + VERSION + "\"", "null")), observation(HISTORICAL.replace("UNKNOWN_OUTCOME", "NONE")),
                observation(ELIGIBLE + "," + ELIGIBLE), observation(HISTORICAL + "," + ELIGIBLE),
                observation(String.join(",", Collections.nCopies(129, ELIGIBLE))))) {
            assertReplyError(false, 200, "application/json", malformed, INVALID);
        }
        for (String malformed : List.of("", "[]", RESULT + "[]", RESULT.replace(INSTANCE, "foreign-id"),
                RESULT.replace("recover:1", "foreign-id"), RESULT.replace("job-1", "foreign-id"),
                RESULT.replace("\"occurrenceId\":\"job-1\"", "\"occurrenceId\":\"foreign-id\""),
                RESULT.replace("RETRY_SCHEDULED", "COMPLETED"), RESULT.replace("\"retries\":1", "\"retries\":1.0"),
                RESULT.replace("\"retries\":1", "\"retries\":2"), RESULT.replace("\"retries\":1", "\"retries\":\"1\""),
                RESULT.replace("\"retries\":1", "\"retries\":null"), RESULT.replace("\"retries\":1,", ""),
                RESULT.replace("\"retries\":1", "\"retries\":1,\"extra\":0"),
                RESULT.replace("\"retries\":1", "\"retries\":1,\"retries\":1"),
                RESULT.replace(DECIDED, "2026-11-05T05:50:01.000001+00:00"))) {
            assertReplyError(true, 200, "application/json", malformed, INVALID);
        }
        assertReplyError(false, 200, "application/problem+json", OBSERVATION, INVALID);
        assertReplyError(true, 200, null, RESULT, INVALID);
        var malformedUtf8 = new FakeHttpClient();
        malformedUtf8.reply(200, jsonHeaders(), List.of(new byte[]{(byte) 0xc0, (byte) 0xaf}));
        assertFailure(assertThrows(ProcessClientException.class, () -> client(malformedUtf8).workerFailures(QUERY)), INVALID);
        assertEquals(1, malformedUtf8.requests.size());
    }

    @Test
    void bounds_body_and_does_not_resend_on_transport_failure() {
        var exact = new FakeHttpClient();
        byte[] prefix = bytes(observation(""));
        byte[] padding = new byte[CAP - prefix.length];
        Arrays.fill(padding, (byte) ' ');
        exact.reply(200, jsonHeaders(), List.of(prefix, padding));
        assertDoesNotThrow(() -> client(exact).workerFailures(QUERY));
        assertFalse(exact.lastSubscription.cancelled);
        assertEquals(1, exact.requests.size());

        var oversized = new FakeHttpClient();
        oversized.reply(200, jsonHeaders(), List.of(new byte[CAP], new byte[1]));
        assertFailure(assertThrows(ProcessClientException.class, () -> client(oversized).recoverWorker(COMMAND)),
                "TIANJI_RESPONSE_TOO_LARGE");
        assertTrue(oversized.lastSubscription.cancelled);
        assertEquals(1, oversized.requests.size());

        var synchronous = new FakeHttpClient();
        synchronous.syncFailure = new IllegalStateException(TOKEN + REASON);
        assertFailure(assertThrows(ProcessClientException.class, () -> client(synchronous).recoverWorker(COMMAND)), UNAVAILABLE);
        assertEquals(1, synchronous.requests.size());

        var exceptional = new FakeHttpClient();
        exceptional.asyncFailure = new IOException(TOKEN + REASON);
        assertFailure(assertThrows(ProcessClientException.class, () -> client(exceptional).workerFailures(QUERY)), UNAVAILABLE);
        assertEquals(1, exceptional.requests.size());

        var timed = new FakeHttpClient();
        timed.incomplete = true;
        var timeoutClient = client(HTTPS, timed, () -> TOKEN, Duration.ofMillis(5));
        assertFailure(assertThrows(ProcessClientException.class, () -> timeoutClient.recoverWorker(COMMAND)), UNAVAILABLE);
        assertEquals(1, timed.requests.size());
        assertNotNull(timed.incompleteFuture);
        assertTrue(timed.incompleteFuture.isCancelled());

        var interrupted = new FakeHttpClient();
        interrupted.incomplete = true;
        try {
            Thread.currentThread().interrupt();
            assertFailure(assertThrows(ProcessClientException.class, () -> client(interrupted).workerFailures(QUERY)), UNAVAILABLE);
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, interrupted.requests.size());
            assertNotNull(interrupted.incompleteFuture);
            assertTrue(interrupted.incompleteFuture.isCancelled());
        } finally {
            Thread.interrupted();
        }
    }

    private static void assertReplyError(boolean post, int status, String contentType, String body, String expected) {
        var fake = new FakeHttpClient();
        fake.reply(status, contentType, body);
        var recovery = client(fake);
        var failure = assertThrows(ProcessClientException.class, () -> {
            if (post) recovery.recoverWorker(COMMAND);
            else recovery.workerFailures(QUERY);
        });
        assertFailure(failure, expected);
        if (status == 200 && expected.equals(INVALID)) {
            assertTrue(failure.httpStatus() == 0 || failure.httpStatus() == 200);
        } else {
            assertEquals(status, failure.httpStatus());
        }
        assertEquals(1, fake.requests.size());
    }

    private static void assertFailure(ProcessClientException failure, String expected) {
        assertEquals(expected, failure.code());
        assertNull(failure.correlationId());
        assertNull(failure.retryAfter());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(TOKEN));
        assertFalse(failure.toString().contains(REASON));
        assertFalse(failure.toString().contains("foreign-id"));
    }

    private static HttpRequest assertSingleRequest(FakeHttpClient fake, String method, String suffix) {
        assertEquals(1, fake.requests.size());
        var request = fake.requests.getFirst();
        assertEquals("/context/api/tianji/v1/process-instances/space%20%2F%2B/" + suffix, request.uri().getRawPath());
        assertEquals(method, request.method());
        assertEquals(TIMEOUT, request.timeout().orElseThrow());
        assertEquals("Bearer " + TOKEN, request.headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/json", request.headers().firstValue("Accept").orElseThrow());
        assertEquals("no-store", request.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(request.headers().allValues("X-Egon-Tianji-Tenant").isEmpty());
        return request;
    }

    private static String observation(String rows) {
        return "{\"instanceId\":\"" + INSTANCE + "\",\"observedAt\":\"" + OBSERVED + "\",\"failures\":[" + rows + "]}";
    }

    private static String error(String code) {
        return "{\"code\":\"" + code + "\",\"correlationId\":null}";
    }

    private static ProcessWorkerRecoveryClient client(FakeHttpClient fake) {
        return client(HTTPS, fake, () -> TOKEN, TIMEOUT);
    }

    private static ProcessWorkerRecoveryClient client(String uri, FakeHttpClient fake,
                                                       java.util.function.Supplier<String> token, Duration timeout) {
        return assertInstanceOf(ProcessWorkerRecoveryClient.class, new HttpProcessClient(URI.create(uri), fake, token, timeout));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] body(HttpRequest request) {
        var capture = new BodyCapture();
        request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()).subscribe(capture);
        return capture.body.join();
    }

    private static Map<String, List<String>> jsonHeaders() {
        return Map.of("Content-Type", List.of("application/json"), "Cache-Control", List.of("no-store"));
    }

    private record Reply(int status, HttpHeaders headers, List<byte[]> chunks) { }

    private static final class FakeHttpClient extends HttpClient {
        private final ArrayDeque<Reply> replies = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();
        private final Redirect redirects;
        private RuntimeException syncFailure;
        private IOException asyncFailure;
        private boolean incomplete;
        private CompletableFuture<?> incompleteFuture;
        private TrackingSubscription lastSubscription;

        FakeHttpClient() {
            this(Redirect.NEVER);
        }

        FakeHttpClient(Redirect redirects) {
            this.redirects = redirects;
        }

        void reply(int status, String contentType, String payload) {
            reply(status, contentType == null ? Map.of() : Map.of("Content-Type", List.of(contentType)),
                    List.of(bytes(payload)));
        }

        void reply(int status, Map<String, List<String>> headers, List<byte[]> chunks) {
            replies.add(new Reply(status, HttpHeaders.of(headers, (name, value) -> true), chunks));
        }

        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return redirects; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLContext sslContext() {
            try { return SSLContext.getDefault(); }
            catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("TLS unavailable", failure); }
        }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("synchronous fake send is unsupported");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            if (syncFailure != null) throw syncFailure;
            if (asyncFailure != null) return CompletableFuture.failedFuture(asyncFailure);
            if (incomplete) {
                var future = new CompletableFuture<HttpResponse<T>>();
                incompleteFuture = future;
                return future;
            }
            Reply reply = replies.removeFirst();
            var subscriber = handler.apply(new ResponseInfo(reply));
            lastSubscription = new TrackingSubscription();
            subscriber.onSubscribe(lastSubscription);
            for (byte[] chunk : reply.chunks()) {
                subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
                if (lastSubscription.cancelled) break;
            }
            subscriber.onComplete();
            return subscriber.getBody().toCompletableFuture()
                    .thenApply(body -> new FakeResponse<>(reply.status(), request, reply.headers(), body));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                                                               HttpResponse.PushPromiseHandler<T> push) {
            return sendAsync(request, handler);
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
