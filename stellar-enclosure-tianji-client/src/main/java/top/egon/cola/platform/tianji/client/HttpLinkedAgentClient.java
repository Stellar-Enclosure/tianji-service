package top.egon.cola.platform.tianji.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentCloseResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupResult;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartResult;

/** Bounded service transport; each operation is sent once at the application level. */
public final class HttpLinkedAgentClient implements LinkedAgentClient, AutoCloseable {
    public enum Operation { SUBMIT, RESOLVE, CLOSE }
    private static final int MAX_SUBMIT_BYTES = 2_097_152;
    private static final int MAX_QUERY_BYTES = 16_384;
    private static final int MAX_RESPONSE_BYTES = 65_536;
    private static final Set<String> ERROR_CODES = Arrays.stream(AgentErrorCodeEnum.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private final URI baseUri;
    private final HttpClient http;
    private final Function<Operation, String> tokens;
    private final Duration timeout;
    private final BoundedServiceCalls calls;
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    public HttpLinkedAgentClient(URI baseUri, HttpClient http, Function<Operation, String> tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, 4);
    }

    public HttpLinkedAgentClient(URI baseUri, HttpClient http, Function<Operation, String> tokens, Duration timeout, int maxTokenCalls) {
        Objects.requireNonNull(baseUri, "baseUri");
        String host = baseUri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                || "::1".equals(host) || "[::1]".equals(host);
        if (!baseUri.isAbsolute() || host == null || baseUri.getUserInfo() != null || baseUri.getQuery() != null
                || baseUri.getFragment() != null || !baseUri.normalize().equals(baseUri)
                || !("https".equalsIgnoreCase(baseUri.getScheme()) || "http".equalsIgnoreCase(baseUri.getScheme()) && loopback)) {
            throw new IllegalArgumentException("Linked Agent service URI is invalid");
        }
        String value = baseUri.toASCIIString();
        this.baseUri = URI.create(value.endsWith("/") ? value : value + "/");
        this.http = Objects.requireNonNull(http, "http");
        if (http.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("Linked Agent client must not follow redirects");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(60)) > 0
                || maxTokenCalls < 1 || maxTokenCalls > 8) throw new IllegalArgumentException("Linked Agent client settings are invalid");
        this.timeout = timeout;
        this.calls = new BoundedServiceCalls(timeout, maxTokenCalls, "linked-agent-token-", HttpLinkedAgentClient::unavailable);
    }

    @Override
    public LinkedAgentStartResult submit(LinkedAgentStartCommand command) {
        Objects.requireNonNull(command, "command");
        LinkedAgentLookupQuery expected = new LinkedAgentLookupQuery(command.start().idempotencyKey(), command.link());
        var result = call(Operation.SUBMIT, "submit", command, LinkedAgentStartResult.class, 202, MAX_SUBMIT_BYTES);
        if (!expected.equals(result.query()) || !validRun(result.run())
                || !command.start().definitionKey().equals(result.run().definitionKey())
                || command.start().definitionVersion() != result.run().definitionVersion()
                || !Objects.equals(command.start().sessionId(), result.run().sessionId())) throw invalidResponse();
        return result;
    }

    @Override
    public LinkedAgentLookupResult resolve(LinkedAgentLookupQuery query) {
        Objects.requireNonNull(query, "query");
        var result = call(Operation.RESOLVE, "resolve", query, LinkedAgentLookupResult.class, 200, MAX_QUERY_BYTES);
        if (!query.equals(result.query()) || result.found() && result.submission().run() != null
                && !validRun(result.submission().run())) throw invalidResponse();
        return result;
    }

    @Override
    public LinkedAgentCloseResult close(LinkedAgentLookupQuery query) {
        Objects.requireNonNull(query, "query");
        var result = call(Operation.CLOSE, "close", query, LinkedAgentCloseResult.class, 200, MAX_QUERY_BYTES);
        if (!query.equals(result.query()) || result.submission().run() != null && !validRun(result.submission().run())) throw invalidResponse();
        return result;
    }

    private <T> T call(Operation operation, String path, Object body, Class<T> type, int expectedStatus, int maxRequestBytes) {
        long started = calls.begin();
        String token = calls.token(() -> tokens.apply(operation), started);
        byte[] bytes;
        try { bytes = mapper.writeValueAsBytes(body); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Linked Agent request is invalid"); }
        if (bytes.length > maxRequestBytes) throw new IllegalArgumentException("Linked Agent request exceeds its size limit");
        long remaining = calls.remaining(started);
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("api/tianji/v1/agent-process-dispatches/" + path))
                .timeout(Duration.ofNanos(remaining)).header("Authorization", "Bearer " + token)
                .header("Accept", "application/json").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending;
        try { pending = calls.track(http.sendAsync(request, ignored -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES))); }
        catch (RuntimeException failure) { throw unavailable(); }
        try {
            HttpResponse<byte[]> response = pending.get(calls.remaining(started), TimeUnit.NANOSECONDS);
            if (response.statusCode() != expectedStatus) throw error(response.statusCode(), response.body(), token);
            try {
                if (type == LinkedAgentLookupResult.class) {
                    var root = mapper.readTree(response.body());
                    if (root == null || !root.isObject() || root.get("found") == null || !root.get("found").isBoolean()) throw invalidResponse();
                }
                return Objects.requireNonNull(mapper.readValue(response.body(), type));
            }
            catch (RuntimeException invalid) { throw invalidResponse(); }
        } catch (AgentClientException expiredOrInvalid) {
            pending.cancel(true); throw expiredOrInvalid;
        } catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw unavailable();
        } catch (TimeoutException timedOut) {
            pending.cancel(true); throw unavailable();
        } catch (ExecutionException failure) {
            if (BoundedBodySubscriber.tooLarge(failure)) throw new AgentClientException(0, "TIANJI_RESPONSE_TOO_LARGE", null, null);
            throw unavailable();
        } catch (CancellationException cancelled) { throw unavailable(); }
        finally { calls.untrack(pending); }
    }

    private AgentClientException error(int status, byte[] body, String token) {
        String code = null;
        try {
            Map<?, ?> payload = mapper.readValue(body, Map.class);
            if (payload.get("code") instanceof String candidate && ERROR_CODES.contains(candidate)
                    && !candidate.contains(token) && matchesStatus(candidate, status)) code = candidate;
        } catch (RuntimeException ignored) { }
        if (code == null) code = switch (status) {
            case 401 -> "TIANJI_UNAUTHENTICATED";
            case 403 -> "TIANJI_PERMISSION_DENIED";
            case 404 -> "TIANJI_RESOURCE_NOT_FOUND";
            case 429 -> "TIANJI_CAPACITY_EXCEEDED";
            default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
        };
        return new AgentClientException(status, code, null, null);
    }

    private static boolean matchesStatus(String code, int status) {
        return switch (status) {
            case 400 -> code.equals("TIANJI_INVALID_COMMAND");
            case 401 -> code.equals("TIANJI_UNAUTHENTICATED");
            case 403 -> code.equals("TIANJI_PERMISSION_DENIED");
            case 404 -> code.equals("TIANJI_RESOURCE_NOT_FOUND");
            case 409 -> Set.of("TIANJI_DISPATCH_EXPIRED", "TIANJI_DISPATCH_CLOSED", "TIANJI_IDEMPOTENCY_CONFLICT",
                    "TIANJI_DEFINITION_VERSION_CONFLICT", "TIANJI_REVISION_CONFLICT", "TIANJI_TENANT_CONTEXT_CHANGED").contains(code);
            case 413 -> code.equals("TIANJI_REQUEST_TOO_LARGE");
            case 429 -> code.equals("TIANJI_CAPACITY_EXCEEDED");
            case 503 -> code.equals("TIANJI_DEPENDENCY_UNAVAILABLE");
            default -> false;
        };
    }

    private static boolean validRun(AgentRunDTO run) {
        if (run == null || run.deadlineAt() == null || run.deadlineAt().isBefore(run.createdAt()) || run.lastEventSequence() < 1) return false;
        boolean terminal = switch (run.status()) {
            case COMPLETED, FAILED, CANCELLED, TIMED_OUT -> true;
            default -> false;
        };
        if (terminal != (run.finishedAt() != null)) return false;
        try { return UUID.fromString(run.runId()).toString().equals(run.runId()); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private static AgentClientException invalidResponse() { return new AgentClientException(0, "TIANJI_INVALID_RESPONSE", null, null); }
    private static AgentClientException unavailable() { return new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null); }

    public Duration timeout() { return timeout; }
    @Override public void close() { calls.close(); }
}
