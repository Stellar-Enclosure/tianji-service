package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryClient;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationOutcomeEnum;
import top.egon.cola.platform.tianji.contract.memory.GetMemoryQuery;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalQuery;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;

import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentSessionClient;
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
import java.util.function.Function;

/** JDK HTTP transport for the engine-neutral Agent contract. It does not initiate write retries. */
public final class HttpAgentClient implements AgentClient, AgentSessionClient, MemoryClient {
    private static final int MAX_JSON_BYTES = 2_097_152;
    private final URI baseUri;
    private final HttpClient http;
    private final Supplier<String> tokenSupplier;
    private final Duration requestTimeout;
    private final int maxReconnects;
    private final Duration reconnectDelay;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AgentContextResponseCodec contexts = new AgentContextResponseCodec();
    private final MemoryResponseCodec memories = new MemoryResponseCodec();

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
        Objects.requireNonNull(command, "command");
        return call("POST", path(command.sessionId() == null ? "" : "with-session"), command, AgentRunDTO.class);
    }

    @Override
    public AgentRunPageDTO list(ListAgentRunsQuery query) {
        Objects.requireNonNull(query, "query");
        String suffix = "?limit=" + query.limit() + (query.cursor() == null ? ""
                : "&cursor=" + URLEncoder.encode(query.cursor(), StandardCharsets.UTF_8));
        return call("GET", URI.create(path("").toString() + suffix), null, AgentRunPageDTO.class);
    }

    @Override
    public AgentRunDTO get(GetAgentRunQuery query) {
        return call("GET", path(segment(Objects.requireNonNull(query, "query").runId())), null, AgentRunDTO.class);
    }

    @Override
    public AgentContextSnapshotPageDTO contextSnapshots(AgentContextSnapshotsQuery query) {
        Objects.requireNonNull(query, "query");
        URI uri = URI.create(path(segment(query.runId()) + "/context-snapshots").toString()
                + "?afterModelCall=" + query.afterModelCall() + "&limit=" + query.limit());
        var page = call("GET", uri, null, AgentContextSnapshotPageDTO.class, null, contexts::read);
        if (page.items().size() > query.limit() || page.items().stream().anyMatch(item -> !item.runId().equals(query.runId())
                || item.modelCallNumber() <= query.afterModelCall())) throw new AgentClientException(200, "TIANJI_INVALID_RESPONSE", null, null);
        return page;
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

    @Override
    public AgentSessionDTO createSession(CreateAgentSessionCommand command) {
        Objects.requireNonNull(command, "command");
        return call("PUT", sessionPath("", command.sessionId()), null, AgentSessionDTO.class, command.sessionId());
    }

    @Override
    public AgentSessionDTO getSession(GetAgentSessionQuery query) {
        Objects.requireNonNull(query, "query");
        return call("GET", sessionPath("/detail", query.sessionId()), null, AgentSessionDTO.class, query.sessionId());
    }

    @Override
    public AgentSessionListDTO listSessions() {
        return call("GET", sessionPath("", null), null, AgentSessionListDTO.class);
    }

    @Override
    public void deleteSession(DeleteAgentSessionCommand command) {
        Objects.requireNonNull(command, "command");
        call("DELETE", sessionPath("", command.sessionId()), null, Void.class);
    }

    @Override
    public MemoryEntryDTO getMemory(GetMemoryQuery query) {
        Objects.requireNonNull(query, "query");
        var entry = call("POST", memoryPath("get"), query, MemoryEntryDTO.class, null, memories::entry);
        requireMemoryScope(query.owner(), query.scopeId(), entry.owner(), entry.scopeId());
        if (!query.memoryId().equals(entry.memoryId())) throw invalidMemoryResponse(); return entry;
    }

    @Override
    public MemoryRetrievalPageDTO retrieveMemory(MemoryRetrievalQuery query) {
        Objects.requireNonNull(query, "query");
        var page = call("POST", memoryPath("retrieve"), query, MemoryRetrievalPageDTO.class, null, memories::page, 8_388_608);
        requireMemoryScope(query.owner(), query.scopeId(), page.owner(), page.scopeId());
        if (page.items().size() > query.limit() || query.text() != null && page.nextAfterMemoryId() != null) throw invalidMemoryResponse();
        String previous = query.afterMemoryId();
        for (var hit : page.items()) {
            if (query.kind() != null && query.kind() != hit.entry().kind() || (query.text() == null) != (hit.score() == null)) throw invalidMemoryResponse();
            if (query.text() == null) {
                if (previous != null && java.util.Arrays.compareUnsigned(previous.getBytes(StandardCharsets.UTF_8), hit.entry().memoryId().getBytes(StandardCharsets.UTF_8)) >= 0) throw invalidMemoryResponse(); previous = hit.entry().memoryId();
            }
        }
        return page;
    }

    @Override
    public MemoryMutationResultDTO upsertMemory(UpsertMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", memoryPath("upsert"), command, MemoryMutationResultDTO.class, null, memories::mutation);
        requireMemoryReceipt(command.commandId(), command.owner(), command.scopeId(), result);
        if (result.outcome() != MemoryMutationOutcomeEnum.CREATED && result.outcome() != MemoryMutationOutcomeEnum.DEDUPLICATED
                || result.outcome() == MemoryMutationOutcomeEnum.CREATED && (!command.memoryId().equals(result.memoryId()) || result.revision() != 1)) throw invalidMemoryResponse();
        return result;
    }

    @Override
    public MemoryMutationResultDTO correctMemory(CorrectMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", memoryPath("correct"), command, MemoryMutationResultDTO.class, null, memories::mutation);
        requireMemoryReceipt(command.commandId(), command.owner(), command.scopeId(), result);
        if (!command.memoryId().equals(result.memoryId()) || result.outcome() != MemoryMutationOutcomeEnum.CORRECTED
                || result.revision() <= command.expectedRevision() || result.revision() - command.expectedRevision() != 1) throw invalidMemoryResponse();
        return result;
    }

    @Override
    public MemoryMutationResultDTO forgetMemory(ForgetMemoryCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", memoryPath("forget"), command, MemoryMutationResultDTO.class, null, memories::mutation);
        requireMemoryReceipt(command.commandId(), command.owner(), command.scopeId(), result);
        if (!command.memoryId().equals(result.memoryId()) || result.outcome() != MemoryMutationOutcomeEnum.FORGOTTEN
                || result.revision() <= command.expectedRevision() || result.revision() - command.expectedRevision() != 1) throw invalidMemoryResponse();
        return result;
    }

    private static void requireMemoryReceipt(String commandId, MemoryOwnerDTO owner, String scope, MemoryMutationResultDTO result) {
        requireMemoryScope(owner, scope, result.owner(), result.scopeId()); if (!commandId.equals(result.commandId())) throw invalidMemoryResponse();
    }
    private static void requireMemoryScope(MemoryOwnerDTO expectedOwner, String expectedScope, MemoryOwnerDTO owner, String scope) {
        if (!expectedOwner.equals(owner) || !expectedScope.equals(scope)) throw invalidMemoryResponse();
    }
    private static AgentClientException invalidMemoryResponse() { return new AgentClientException(200, "TIANJI_INVALID_RESPONSE", null, null); }
    private URI memoryPath(String operation) { return baseUri.resolve(MemoryClient.PATH.substring(1) + "/" + operation); }

    private <T> T call(String method, URI uri, Object body, Class<T> type) {
        return call(method, uri, body, type, null);
    }

    private <T> T call(String method, URI uri, Object body, Class<T> type, String expectedSessionId) {
        return call(method, uri, body, type, expectedSessionId, bytes -> mapper.readValue(bytes, type));
    }

    private <T> T call(String method, URI uri, Object body, Class<T> type, String expectedSessionId, Function<byte[], T> decoder) {
        return call(method, uri, body, type, expectedSessionId, decoder, MAX_JSON_BYTES);
    }

    private <T> T call(String method, URI uri, Object body, Class<T> type, String expectedSessionId, Function<byte[], T> decoder, int responseLimit) {
        byte[] bytes = body == null ? null : mapper.writeValueAsBytes(body);
        if (bytes != null && bytes.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Agent request exceeds its size limit");
        }
        HttpRequest.Builder builder = request(uri).timeout(requestTimeout).header("Accept", "application/json");
        HttpRequest request = body == null ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var call = http.sendAsync(request, info -> new BoundedBodySubscriber(responseLimit));
        try {
            HttpResponse<byte[]> response = call.get(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw error(response, response.body());
            }
            if (type == Void.class) {
                if (response.statusCode() != 204) {
                    throw new AgentClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
                }
                return null;
            }
            try {
                T value = decoder.apply(response.body());
                if (expectedSessionId != null && (!(value instanceof AgentSessionDTO session)
                        || !expectedSessionId.equals(session.sessionId()))) {
                    throw new AgentClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
                }
                return value;
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
            if (BoundedBodySubscriber.tooLarge(failure)) {
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

    private URI sessionPath(String suffix, String sessionId) {
        String query = sessionId == null ? "" : "?sessionId=" + URLEncoder.encode(sessionId, StandardCharsets.UTF_8);
        return baseUri.resolve("api/tianji/v1/agent-sessions" + suffix + query);
    }

    private static String segment(String value) {
        if (value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Agent run id is invalid");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
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
