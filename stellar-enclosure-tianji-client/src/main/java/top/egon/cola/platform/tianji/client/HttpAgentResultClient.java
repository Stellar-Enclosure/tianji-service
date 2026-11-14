package top.egon.cola.platform.tianji.client;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultClient;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** One bounded SERVICE-authenticated delivery attempt; Outbox owns retry, and HTTP 202 means durable reception. */
public final class HttpAgentResultClient implements AgentResultClient, AutoCloseable {
    @FunctionalInterface
    public interface TokenProvider {
        String acquire(long tenantId);
        default void rejected(long tenantId) { }
    }

    private static final int MAX_BYTES = 16_384;
    private static final Set<String> RECEIPT_FIELDS = Set.of("eventId", "eventFingerprint", "state", "receivedAt");
    private static final JsonMapper TERMINAL_JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(
                    StreamReadConstraints.builder().maxNestingDepth(12).maxStringLength(MAX_BYTES).maxNameLength(MAX_BYTES)
                            .maxNumberLength(20).maxTokenCount(MAX_BYTES).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Map<Integer, Set<String>> ERROR_CODES = Map.of(
            400, Set.of("TIANJI_INVALID_COMMAND"), 401, Set.of("TIANJI_UNAUTHENTICATED"), 403, Set.of("TIANJI_PERMISSION_DENIED"),
            404, Set.of("TIANJI_RESOURCE_NOT_FOUND"), 409, Set.of("TIANJI_IDEMPOTENCY_CONFLICT", "TIANJI_TENANT_CONTEXT_CHANGED"),
            413, Set.of("TIANJI_REQUEST_TOO_LARGE"), 429, Set.of("TIANJI_CAPACITY_EXCEEDED"), 503, Set.of("TIANJI_DEPENDENCY_UNAVAILABLE"));
    private final URI endpoint;
    private final HttpClient http;
    private final TokenProvider tokens;
    private final BoundedServiceCalls calls;
    private final Duration timeout;
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(8).maxStringLength(4096).maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    public HttpAgentResultClient(URI baseUri, HttpClient http, TokenProvider tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, 4);
    }

    public HttpAgentResultClient(URI baseUri, HttpClient http, TokenProvider tokens, Duration timeout, int maxTokenCalls) {
        Objects.requireNonNull(baseUri, "baseUri");
        String host = baseUri.getHost();
        boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
        if (!baseUri.isAbsolute() || host == null || baseUri.getUserInfo() != null || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !baseUri.normalize().equals(baseUri) || !("https".equalsIgnoreCase(baseUri.getScheme())
                || "http".equalsIgnoreCase(baseUri.getScheme()) && loopback)) throw new IllegalArgumentException("Agent result service URI is invalid");
        String value = baseUri.toASCIIString();
        this.endpoint = URI.create(value.endsWith("/") ? value : value + "/").resolve(AgentResultTraceLinkCarrier.PATH.substring(1));
        this.http = Objects.requireNonNull(http, "http");
        if (http.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("Agent result client must not follow redirects");
        this.tokens = Objects.requireNonNull(tokens, "tokens");
        this.calls = new BoundedServiceCalls(timeout, maxTokenCalls, "agent-result-token-", HttpAgentResultClient::unavailable);
        this.timeout = timeout;
    }

    @Override public AgentResultReceipt receive(AgentTerminalEvent event) {
        return receive(event, null);
    }

    @Override public AgentResultReceipt receiveV2(@NotNull @Valid AgentTerminalEventV2 event) {
        Objects.requireNonNull(event, "event");
        long started = calls.begin();
        checkV2Transport();
        return sendV2(event, event.canonicalBytes(), null, started);
    }

    /** Validates the committed fact against its wire value and sends the original byte snapshot. */
    public AgentResultReceipt receiveV2(@NotNull @Valid AgentTerminalEventV2 event, @NotNull byte[] original, String traceLink) {
        Objects.requireNonNull(event, "event");
        long started = calls.begin();
        checkV2Transport();
        if (original == null || original.length == 0 || original.length > MAX_BYTES) throw invalidV2Event();
        byte[] snapshot = original.clone();
        requireOriginalV2Event(event, snapshot);
        return sendV2(event, snapshot, traceLink, started);
    }

    private void checkV2Transport() {
        if (http.authenticator().isPresent()) {
            throw new IllegalArgumentException("Agent result v2 requires explicit bearer authentication");
        }
    }

    private AgentResultReceipt sendV2(AgentTerminalEventV2 event, byte[] snapshot, String traceLink, long started) {
        long tenant = event.tenantId();
        String token = calls.token(() -> tokens.acquire(tenant), started);
        var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofNanos(calls.remaining(started)))
                .header("Authorization", "Bearer " + token).header("X-Egon-Tianji-Tenant", Long.toString(tenant))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("Cache-Control", "no-store");
        addTraceLink(builder, traceLink);
        return exchange(builder.POST(new AutomationHttpTransport.SingleSendBody(snapshot)).build(), started, response -> {
            if (!endpoint.equals(response.uri())) throw invalidResponse();
            int status = response.statusCode();
            if (status == 401) calls.invoke(() -> { tokens.rejected(tenant); return Boolean.TRUE; }, started);
            if (status != 202) {
                if (status >= 200 && status < 300) throw invalidResponse();
                throw errorV2(response);
            }
            return receiptV2(response, event);
        }, HttpAgentResultClient::subscribeV2);
    }

    private static void requireOriginalV2Event(AgentTerminalEventV2 event, byte[] snapshot) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(snapshot)).toString();
            if (text.charAt(0) == '\uFEFF') throw invalidV2Event();
            var decoded = TERMINAL_JSON.readTree(text);
            if (!(decoded instanceof ObjectNode root) || !(root.path("link") instanceof ObjectNode link)
                    || !event.finishedAt().equals(AutomationResponseCodec.utc(root.path("finishedAt")))
                    || !event.link().dispatchNotAfter().equals(AutomationResponseCodec.utc(link.path("dispatchNotAfter")))) {
                throw invalidV2Event();
            }
            // Alternate legal UTC spellings represent the same instant. All other JSON fields must match
            // the frozen canonical tree exactly, independently of object member order or escaping.
            root.put("finishedAt", event.finishedAt().toString());
            link.put("dispatchNotAfter", event.link().dispatchNotAfter().toString());
            if (!root.equals(TERMINAL_JSON.readTree(event.canonicalBytes()))) throw invalidV2Event();
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidV2Event();
        }
    }

    private static AgentResultReceipt receiptV2(HttpResponse<byte[]> response, AgentTerminalEventV2 event) {
        try {
            var media = response.headers().allValues("Content-Type");
            var cache = response.headers().allValues("Cache-Control");
            if (media.size() != 1 || !"application/json".equalsIgnoreCase(media.getFirst().split(";", 2)[0].trim())
                    || cache.size() != 1 || !"no-store".equalsIgnoreCase(cache.getFirst().trim())) throw invalidResponse();
            JsonNode root = TERMINAL_JSON.readTree(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(response.body())).toString());
            if (root == null || !root.isObject() || !RECEIPT_FIELDS.equals(Set.copyOf(root.propertyNames()))) throw invalidResponse();
            String eventId = AutomationResponseCodec.string(root.path("eventId"));
            String fingerprint = AutomationResponseCodec.string(root.path("eventFingerprint"));
            var state = AgentResultReceiptStateEnum.valueOf(AutomationResponseCodec.string(root.path("state")));
            var receipt = new AgentResultReceipt(eventId, fingerprint, state, AutomationResponseCodec.utc(root.path("receivedAt")));
            if (!event.eventId().equals(eventId) || !event.fingerprint().equals(fingerprint)) throw invalidResponse();
            return receipt;
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidResponse();
        }
    }

    private static IllegalArgumentException invalidV2Event() {
        return new IllegalArgumentException("Agent result v2 payload does not match the original terminal event");
    }

    /** The optional diagnostic link never participates in authorization or event identity. */
    public AgentResultReceipt receive(AgentTerminalEvent event, String traceLink) {
        Objects.requireNonNull(event, "event");
        long started = calls.begin();
        long tenant = event.tenantId();
        String token = calls.token(() -> tokens.acquire(tenant), started);
        byte[] body;
        try { body = mapper.writeValueAsBytes(event); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Agent result event cannot be encoded"); }
        if (body.length > MAX_BYTES) throw new IllegalArgumentException("Agent result event exceeds its size limit");
        var builder = HttpRequest.newBuilder(endpoint).timeout(Duration.ofNanos(calls.remaining(started)))
                .header("Authorization", "Bearer " + token).header("X-Egon-Tianji-Tenant", Long.toString(tenant))
                .header("Content-Type", "application/json").header("Accept", "application/json");
        addTraceLink(builder, traceLink);
        var request = builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return exchange(request, started, response -> {
            if (response.statusCode() == 401) calls.invoke(() -> { tokens.rejected(tenant); return Boolean.TRUE; }, started);
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.statusCode() != 202) throw invalidResponse();
            if (response.statusCode() != 202) throw error(response, token);
            String mediaType = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!mediaType.equals("application/json") && !(mediaType.startsWith("application/") && mediaType.endsWith("+json"))) throw invalidResponse();
            AgentResultReceipt receipt;
            try {
                var root = mapper.readTree(response.body());
                if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(RECEIPT_FIELDS)
                        || !root.get("state").isString() || !root.get("receivedAt").isString()) throw invalidResponse();
                receipt = mapper.readValue(response.body(), AgentResultReceipt.class);
                if (receipt == null || !event.eventId().equals(receipt.eventId()) || !event.fingerprint().equals(receipt.eventFingerprint())) throw invalidResponse();
            } catch (RuntimeException invalid) { throw invalidResponse(); }
            return receipt;
        }, ignored -> new BoundedBodySubscriber(MAX_BYTES));
    }

    private AgentResultReceipt exchange(HttpRequest request, long started,
                                        java.util.function.Function<HttpResponse<byte[]>, AgentResultReceipt> decode,
                                        HttpResponse.BodyHandler<byte[]> subscribe) {
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pending;
        try { pending = calls.track(http.sendAsync(request, subscribe)); }
        catch (RuntimeException failure) { throw unavailable(); }
        try {
            var receipt = decode.apply(pending.get(calls.remaining(started), TimeUnit.NANOSECONDS));
            calls.remaining(started);
            return receipt;
        } catch (ProcessClientException failed) {
            pending.cancel(true); throw failed;
        } catch (InterruptedException interrupted) {
            pending.cancel(true); Thread.currentThread().interrupt(); throw unavailable();
        } catch (TimeoutException timedOut) {
            pending.cancel(true); throw unavailable();
        } catch (ExecutionException failure) {
            throw new ProcessClientException(0, BoundedBodySubscriber.tooLarge(failure) ? "TIANJI_RESPONSE_TOO_LARGE" : "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        } catch (CancellationException cancelled) { throw unavailable(); }
        finally { calls.untrack(pending); }
    }

    private void addTraceLink(HttpRequest.Builder builder, String traceLink) {
        if ("https".equalsIgnoreCase(endpoint.getScheme()) && AgentResultTraceLinkCarrier.PATH.equals(endpoint.getRawPath())
                && AgentResultTraceLinkCarrier.isValid(traceLink)) {
            builder.header(AgentResultTraceLinkCarrier.HEADER, traceLink);
        }
    }

    private static ProcessClientException errorV2(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        String code = switch (status) {
            case 400 -> "TIANJI_INVALID_COMMAND";
            case 401 -> "TIANJI_UNAUTHENTICATED";
            case 403 -> "TIANJI_PERMISSION_DENIED";
            case 404 -> "TIANJI_RESOURCE_NOT_FOUND";
            case 409 -> "TIANJI_IDEMPOTENCY_CONFLICT";
            case 413 -> "TIANJI_REQUEST_TOO_LARGE";
            case 415 -> "TIANJI_UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "TIANJI_CAPACITY_EXCEEDED";
            case 500 -> "TIANJI_INTERNAL_ERROR";
            default -> status == 408 || status >= 500 && status < 600
                    ? "TIANJI_DEPENDENCY_UNAVAILABLE" : "TIANJI_INVALID_RESPONSE";
        };
        return new ProcessClientException(status, code, null,
                response.headers().firstValue("Retry-After").map(HttpAgentResultClient::retryAfter).orElse(null));
    }

    private static HttpResponse.BodySubscriber<byte[]> subscribeV2(HttpResponse.ResponseInfo response) {
        if (response.statusCode() == 202) return new BoundedBodySubscriber(MAX_BYTES);
        // Only receipt bodies are buffered. Error disposition uses the status and a bounded Retry-After hint.
        return HttpResponse.BodySubscribers.replacing(new byte[0]);
    }

    private ProcessClientException error(HttpResponse<byte[]> response, String token) {
        String code = null;
        try {
            var root = mapper.readTree(response.body());
            String candidate = root != null && root.isObject() && root.get("code") != null && root.get("code").isString() ? root.get("code").stringValue() : null;
            if (candidate != null && !candidate.contains(token) && ERROR_CODES.getOrDefault(response.statusCode(), Set.of()).contains(candidate)) code = candidate;
        } catch (RuntimeException invalid) { code = null; }
        if (code == null) code = switch (response.statusCode()) {
            case 400 -> "TIANJI_INVALID_COMMAND"; case 401 -> "TIANJI_UNAUTHENTICATED"; case 403 -> "TIANJI_PERMISSION_DENIED";
            case 404 -> "TIANJI_RESOURCE_NOT_FOUND"; case 409 -> "TIANJI_IDEMPOTENCY_CONFLICT"; case 413 -> "TIANJI_REQUEST_TOO_LARGE";
            case 429 -> "TIANJI_CAPACITY_EXCEEDED"; default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
        };
        return new ProcessClientException(response.statusCode(), code, null, response.headers().firstValue("Retry-After").map(HttpAgentResultClient::retryAfter).orElse(null));
    }

    private static Duration retryAfter(String value) {
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(Math.min(seconds, 86_400));
        } catch (NumberFormatException notSeconds) {
            try {
                Duration delay = Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                return delay.isNegative() ? Duration.ZERO : delay.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : delay;
            } catch (java.time.format.DateTimeParseException invalid) { return null; }
        }
    }
    public Duration timeout() { return timeout; }
    private static ProcessClientException invalidResponse() { return new ProcessClientException(0, "TIANJI_INVALID_RESPONSE", null, null); }
    private static ProcessClientException unavailable() { return new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null); }
    @Override public void close() { calls.close(); }
}
