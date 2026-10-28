package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessReleaseQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskResult;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** JDK HTTP transport for the engine-neutral orchestration contracts. Writes are sent once. */
public final class HttpProcessClient implements ProcessReleaseClient, ProcessClient, DecisionClient, WorkerClient {
    private static final int MAX_REQUEST_BYTES = 9 * 1_048_576;
    private static final int MAX_RESPONSE_BYTES = 2 * 1_048_576;
    private final URI baseUri;
    private final HttpClient http;
    private final Supplier<String> tokenSupplier;
    private final Duration timeout;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public HttpProcessClient(URI baseUri, HttpClient http, Supplier<String> tokenSupplier, Duration timeout) {
        Objects.requireNonNull(baseUri, "baseUri");
        if (!baseUri.isAbsolute() || baseUri.getHost() == null || baseUri.getUserInfo() != null
                || baseUri.getQuery() != null || baseUri.getFragment() != null
                || !("http".equalsIgnoreCase(baseUri.getScheme()) || "https".equalsIgnoreCase(baseUri.getScheme()))) {
            throw new IllegalArgumentException("Process service base URI is invalid");
        }
        String value = baseUri.toString();
        this.baseUri = URI.create(value.endsWith("/") ? value : value + "/");
        this.http = Objects.requireNonNull(http, "http");
        if (http.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("Process HTTP client must not follow redirects");
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier");
        if (timeout == null || timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Process client timeout is invalid");
        }
        this.timeout = timeout;
    }

    @Override
    public ProcessReleaseDTO publish(PublishProcessReleaseCommand command) {
        Objects.requireNonNull(command, "command");
        ProcessReleaseDTO result = call("POST", "process-releases", command, ProcessReleaseDTO.class);
        if (!command.releaseVersion().equals(result.releaseVersion()) || command.models().size() != result.models().size()) throw invalidResponse();
        for (var requested : command.models()) {
            var published = result.models().stream().filter(model -> model.type() == requested.type()
                    && model.definitionKey().equals(requested.definitionKey())).findFirst().orElseThrow(HttpProcessClient::invalidResponse);
            if (!published.contentSha256().equals(hash(requested.xml().getBytes(StandardCharsets.UTF_8)))
                    || (requested.type() == ProcessModelTypeEnum.BPMN || !requested.inputs().isEmpty())
                    && !published.inputs().equals(requested.inputs())) throw invalidResponse();
        }
        return result;
    }

    @Override
    public ProcessReleaseDTO get(GetProcessReleaseQuery query) {
        Objects.requireNonNull(query, "query");
        ProcessReleaseDTO result = call("GET", "process-releases/" + segment(query.releaseVersion()), null, ProcessReleaseDTO.class);
        if (!query.releaseVersion().equals(result.releaseVersion())) throw invalidResponse();
        return result;
    }

    @Override
    public ProcessInstanceDTO start(StartProcessCommand command) {
        Objects.requireNonNull(command, "command");
        ProcessInstanceDTO result = call("POST", "process-instances", command, ProcessInstanceDTO.class);
        if (!command.releaseVersion().equals(result.releaseVersion()) || !command.definitionKey().equals(result.definitionKey())
                || !command.businessKey().equals(result.businessKey()) || !command.correlationId().equals(result.correlationId())) throw invalidResponse();
        return result;
    }

    @Override
    public ProcessInstanceDTO get(GetProcessInstanceQuery query) {
        Objects.requireNonNull(query, "query");
        ProcessInstanceDTO result = call("GET", "process-instances/" + segment(query.instanceId()), null, ProcessInstanceDTO.class);
        if (!query.instanceId().equals(result.instanceId())) throw invalidResponse();
        return result;
    }

    @Override
    public DecisionResultDTO evaluate(EvaluateDecisionCommand command) {
        Objects.requireNonNull(command, "command");
        DecisionResultDTO result = call("POST", "decisions/evaluate", command, DecisionResultDTO.class);
        if (!command.releaseVersion().equals(result.releaseVersion()) || !command.decisionKey().equals(result.decisionKey())
                || !command.correlationId().equals(result.correlationId())) throw invalidResponse();
        return result;
    }

    @Override
    public AcquireWorkerTaskResult acquire(AcquireWorkerTaskCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", "worker-tasks/acquire", command, AcquireWorkerTaskResult.class);
        if (result.task() != null && (!command.topic().equals(result.task().topic())
                || !command.schemaVersion().equals(result.task().schemaVersion()))) throw invalidResponse();
        return result;
    }

    @Override
    public WorkerReceiptDTO complete(CompleteWorkerTaskCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", "worker-tasks/complete", command, WorkerReceiptDTO.class, command.leaseToken());
        if (!command.taskId().equals(result.taskId()) || !command.activityOccurrenceId().equals(result.activityOccurrenceId())
                || result.outcome() != WorkerReceiptOutcomeEnum.COMPLETED) throw invalidResponse();
        return result;
    }

    @Override
    public WorkerReceiptDTO fail(FailWorkerTaskCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", "worker-tasks/fail", command, WorkerReceiptDTO.class, command.leaseToken());
        if (!command.taskId().equals(result.taskId()) || !command.activityOccurrenceId().equals(result.activityOccurrenceId())
                || command.kind() == WorkerFailureKindEnum.UNKNOWN && result.outcome() != WorkerReceiptOutcomeEnum.NEEDS_MANUAL
                || command.kind() == WorkerFailureKindEnum.RETRYABLE && result.outcome() != WorkerReceiptOutcomeEnum.RETRY_SCHEDULED
                    && result.outcome() != WorkerReceiptOutcomeEnum.NEEDS_MANUAL
                || command.kind() == WorkerFailureKindEnum.BUSINESS_REJECTED && result.outcome() != WorkerReceiptOutcomeEnum.BUSINESS_REJECTED
                    && result.outcome() != WorkerReceiptOutcomeEnum.NEEDS_MANUAL) throw invalidResponse();
        return result;
    }

    private <T> T call(String method, String path, Object body, Class<T> type) {
        return call(method, path, body, type, null);
    }

    private <T> T call(String method, String path, Object body, Class<T> type, String leaseToken) {
        byte[] bytes;
        try { bytes = body == null ? null : mapper.writeValueAsBytes(body); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Process request cannot be encoded"); }
        if (bytes != null && bytes.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Process request exceeds its size limit");
        String token;
        try { token = tokenSupplier.get(); }
        catch (RuntimeException failure) { throw new ProcessClientException(0, "TIANJI_UNAUTHENTICATED", null, null); }
        if (token == null || !token.matches("[A-Za-z0-9._~+/-]+=*")) throw new IllegalArgumentException("a valid access token is required");
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve("api/tianji/v1/" + path)).timeout(timeout)
                .header("Authorization", "Bearer " + token).header("Accept", "application/json");
        HttpRequest request = bytes == null ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        var call = http.sendAsync(request, info -> new BoundedBodySubscriber(MAX_RESPONSE_BYTES));
        try {
            HttpResponse<byte[]> response = call.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw error(response, token, leaseToken);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String mediaType = contentType.split(";", 2)[0].trim();
            if (!mediaType.equalsIgnoreCase("application/json") && !mediaType.toLowerCase(java.util.Locale.ROOT).endsWith("+json")) {
                throw new ProcessClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
            }
            try {
                T result = mapper.readValue(response.body(), type);
                if (result == null) throw invalidResponse();
                return result;
            } catch (RuntimeException invalid) {
                throw new ProcessClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
            }
        } catch (InterruptedException interrupted) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        } catch (TimeoutException timeout) {
            call.cancel(true);
            throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        } catch (ExecutionException failure) {
            throw new ProcessClientException(0, BoundedBodySubscriber.tooLarge(failure)
                    ? "TIANJI_RESPONSE_TOO_LARGE" : "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
        }
    }

    private ProcessClientException error(HttpResponse<byte[]> response, String token, String leaseToken) {
        String code = null;
        String correlation = null;
        try {
            Map<?, ?> body = mapper.readValue(response.body(), Map.class);
            if (body.get("code") instanceof String value && !containsCredential(value, token, leaseToken)) code = value;
            if (body.get("correlationId") instanceof String value && !containsCredential(value, token, leaseToken)) correlation = value;
        } catch (RuntimeException ignored) {
            // A non-JSON error body is not safe to expose to callers.
        }
        if (code == null) {
            code = switch (response.statusCode()) {
                case 401 -> "TIANJI_UNAUTHENTICATED";
                case 403 -> "TIANJI_PERMISSION_DENIED";
                case 404 -> "TIANJI_RESOURCE_NOT_FOUND";
                case 409 -> "TIANJI_IDEMPOTENCY_CONFLICT";
                case 429 -> "TIANJI_CAPACITY_EXCEEDED";
                default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
            };
        }
        return new ProcessClientException(response.statusCode(), code, correlation,
                response.headers().firstValue("Retry-After").map(HttpProcessClient::retryAfter).orElse(null));
    }

    private static boolean containsCredential(String value, String token, String leaseToken) {
        return value.contains(token) || leaseToken != null && value.contains(leaseToken);
    }

    private static String segment(String value) {
        if (value.equals(".") || value.equals("..")) throw new IllegalArgumentException("Process resource ID is invalid");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String hash(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }

    private static Duration retryAfter(String value) {
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(Math.min(seconds, 86_400));
        } catch (NumberFormatException notSeconds) {
            try {
                Duration delay = Duration.between(Instant.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                if (delay.isNegative()) return Duration.ZERO;
                return delay.compareTo(Duration.ofDays(1)) > 0 ? Duration.ofDays(1) : delay;
            } catch (DateTimeParseException invalid) {
                return null;
            }
        }
    }

    private static ProcessClientException invalidResponse() {
        return new ProcessClientException(0, "TIANJI_INVALID_RESPONSE", null, null);
    }
}
