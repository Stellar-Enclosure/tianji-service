package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** JDK HTTP transport for the engine-neutral Agent contract. Writes are never retried automatically. */
public final class HttpAgentClient implements AgentClient {
    private static final int MAX_JSON_BYTES = 2_097_152;
    private final URI baseUri;
    private final HttpClient http;
    private final Supplier<String> tokenSupplier;
    private final Duration requestTimeout;
    private final int maxReconnects;
    private final Duration reconnectDelay;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public HttpAgentClient(URI baseUri, HttpClient http, Supplier<String> tokenSupplier,
                           Duration requestTimeout, int maxReconnects, Duration reconnectDelay) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (!baseUri.isAbsolute() || baseUri.getHost() == null || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("Agent service base URI is invalid");
        }
        String value = baseUri.toString();
        this.baseUri = URI.create(value.endsWith("/") ? value : value + "/");
        this.http = Objects.requireNonNull(http, "http");
        if (http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Agent HTTP client must not follow redirects");
        }
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()
                || requestTimeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0
                || reconnectDelay == null || reconnectDelay.isNegative()
                || reconnectDelay.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0
                || maxReconnects < 0 || maxReconnects > 20) {
            throw new IllegalArgumentException("Agent client timeout or reconnect policy is invalid");
        }
        this.requestTimeout = requestTimeout;
        this.maxReconnects = maxReconnects;
        this.reconnectDelay = reconnectDelay;
    }

    @Override
    public AgentRunDTO start(StartAgentRunCommand command) {
        return call("POST", path(""), Objects.requireNonNull(command, "command"), AgentRunDTO.class);
    }

    @Override
    public AgentRunDTO get(GetAgentRunQuery query) {
        return call("GET", path(segment(Objects.requireNonNull(query, "query").runId())), null, AgentRunDTO.class);
    }

    @Override
    public AgentExecutionResult getResult(GetAgentRunQuery query) {
        return call("GET", path(segment(Objects.requireNonNull(query, "query").runId()) + "/result"),
                null, AgentExecutionResult.class);
    }

    @Override
    public CancelAgentRunResult cancel(CancelAgentRunCommand command) {
        Objects.requireNonNull(command, "command");
        return call("POST", path(segment(command.runId()) + "/cancel"), command, CancelAgentRunResult.class);
    }

    @Override
    public Flow.Publisher<AgentEventDTO> events(AgentEventsQuery query) {
        Objects.requireNonNull(query, "query");
        return new AgentEventPublisher(http, mapper, query, maxReconnects, reconnectDelay,
                requestTimeout, cursor -> eventRequest(query, cursor), this::error);
    }

    private <T> T call(String method, URI uri, Object body, Class<T> type) {
        byte[] bytes = body == null ? null : mapper.writeValueAsBytes(body);
        if (bytes != null && bytes.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Agent request exceeds its size limit");
        }
        HttpRequest.Builder builder = request(uri).timeout(requestTimeout).header("Accept", "application/json");
        HttpRequest request = body == null ? builder.GET().build()
                : builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var call = http.sendAsync(request, info -> new BoundedBodySubscriber(MAX_JSON_BYTES));
        try {
            HttpResponse<byte[]> response = call.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw error(response, response.body());
            }
            try {
                return mapper.readValue(response.body(), type);
            } catch (RuntimeException invalid) {
                throw new AgentClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
            }
        } catch (InterruptedException interrupted) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        } catch (TimeoutException timeout) {
            call.cancel(true);
            throw new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        } catch (ExecutionException failure) {
            if (tooLarge(failure)) {
                throw new AgentClientException(0, "TIANJI_RESPONSE_TOO_LARGE", null, null);
            }
            throw new AgentClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        }
    }

    private HttpRequest eventRequest(AgentEventsQuery query, long cursor) {
        URI uri = path(segment(query.runId()) + "/events?afterSequence=" + cursor + "&limit=" + query.limit());
        HttpRequest.Builder builder = request(uri).header("Accept", "text/event-stream");
        if (cursor > 0) builder.header("Last-Event-ID", Long.toString(cursor));
        return builder.GET().build();
    }

    private HttpRequest.Builder request(URI uri) {
        String token;
        try { token = tokenSupplier.get(); }
        catch (RuntimeException failure) {
            throw new AgentClientException(0, "TIANJI_UNAUTHENTICATED", null, null);
        }
        if (token == null || !token.matches("[A-Za-z0-9._~+/-]+=*")) {
            throw new IllegalArgumentException("a valid access token is required");
        }
        return HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token);
    }

    private URI path(String suffix) {
        return baseUri.resolve("api/tianji/v1/agent-runs" + (suffix.isEmpty() ? "" : "/" + suffix));
    }

    private static String segment(String value) {
        if (value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Agent run id is invalid");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static boolean tooLarge(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++, current = current.getCause()) {
            if (current instanceof BoundedBodySubscriber.TooLarge) return true;
        }
        return false;
    }

    private AgentClientException error(HttpResponse<?> response, byte[] body) {
        String code = null;
        String correlation = null;
        try {
            Map<?, ?> payload = mapper.readValue(body, Map.class);
            if (payload.get("code") instanceof String value) code = value;
            if (payload.get("correlationId") instanceof String value) correlation = value;
        } catch (RuntimeException ignored) {
            // A non-JSON error body is not safe to expose to callers.
        }
        String authorization = response.request().headers().firstValue("Authorization").orElse("");
        String credential = authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (!credential.isEmpty()) {
            if (code != null && code.contains(credential)) code = null;
            if (correlation != null && correlation.contains(credential)) correlation = null;
        }
        if (code == null) {
            code = switch (response.statusCode()) {
                case 401 -> "TIANJI_UNAUTHENTICATED";
                case 403 -> "TIANJI_PERMISSION_DENIED";
                case 404 -> "TIANJI_RESOURCE_NOT_FOUND";
                case 410 -> "TIANJI_EVENT_CURSOR_EXPIRED";
                case 429 -> "TIANJI_CAPACITY_EXCEEDED";
                default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
            };
        }
        return new AgentClientException(response.statusCode(), code, correlation,
                response.headers().firstValue("Retry-After").map(HttpAgentClient::retryAfter).orElse(null));
    }

    private static Duration retryAfter(String value) {
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(Math.min(seconds, 86_400));
        } catch (NumberFormatException notSeconds) {
            try {
                Duration duration = Duration.between(java.time.Instant.now(),
                        ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                if (duration.isNegative()) return Duration.ZERO;
                return duration.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : duration;
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }
}
