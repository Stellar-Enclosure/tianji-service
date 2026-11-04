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
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskClient;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalClient;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;
import top.egon.cola.platform.tianji.contract.orchestration.VerifyApprovalQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.GetHumanTaskQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ListHumanTasksQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ClaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.UnclaimHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.TransferHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CaseClient;
import top.egon.cola.platform.tianji.contract.orchestration.StartCaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ActOnPlanItemCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;

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
import java.util.function.Function;

/** JDK HTTP transport for the engine-neutral orchestration contracts. Writes are sent once. */
public final class HttpProcessClient implements ProcessReleaseClient, ProcessClient, DecisionClient, WorkerClient,
        HumanTaskClient, ApprovalClient, CaseClient {
    private static final int MAX_REQUEST_BYTES = 9 * 1_048_576;
    private static final int MAX_RESPONSE_BYTES = 2 * 1_048_576;
    private static final java.util.Set<String> HUMAN_TASK_ERRORS = java.util.Set.of("TIANJI_UNAUTHENTICATED", "TIANJI_PERMISSION_DENIED", "TIANJI_RESOURCE_NOT_FOUND", "TIANJI_IDEMPOTENCY_CONFLICT",
            "TIANJI_CAPACITY_EXCEEDED", "TIANJI_DEPENDENCY_UNAVAILABLE", "TIANJI_TENANT_CONTEXT_CHANGED", "TIANJI_INVALID_COMMAND", "TIANJI_TASK_VERSION_CONFLICT", "TIANJI_TASK_NOT_AVAILABLE");
    private final URI baseUri;
    private final HttpClient http;
    private final Supplier<String> tokenSupplier;
    private final Duration timeout;
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final HumanTaskResponseCodec tasks = new HumanTaskResponseCodec();
    private final CaseResponseCodec cases = new CaseResponseCodec();

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
    public CaseInstanceDTO start(StartCaseCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", "cases/start", command, CaseInstanceDTO.class, null, cases::instance);
        if (!command.definitionKey().equals(result.definitionKey()) || !command.releaseVersion().equals(result.releaseVersion())
                || !command.businessKey().equals(result.businessKey()) || !command.correlationId().equals(result.correlationId())) throw invalidResponse();
        return result;
    }

    @Override
    public CaseInstanceDTO get(CaseInstanceQuery query) {
        Objects.requireNonNull(query, "query");
        var result = call("POST", "cases/get", query, CaseInstanceDTO.class, null, cases::instance);
        if (!query.caseInstanceId().equals(result.caseInstanceId())) throw invalidResponse();
        return result;
    }

    @Override
    public CaseOperationResult actOnPlanItem(ActOnPlanItemCommand command) {
        Objects.requireNonNull(command, "command");
        var result = call("POST", "cases/act-plan-item", command, CaseOperationResult.class, null, cases::operation);
        if (!command.commandId().equals(result.commandId()) || !command.caseInstanceId().equals(result.caseInstanceId())
                || !command.planItemInstanceId().equals(result.planItemInstanceId()) || command.action() != result.action()
                || !command.expectedVersion().equals(result.previousVersion())) throw invalidResponse();
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

    @Override
    public VerifiedApprovalDTO verifyApproval(VerifyApprovalQuery query) {
        Objects.requireNonNull(query, "query"); var result = call("POST", "human-tasks/approvals/verify", query, VerifiedApprovalDTO.class, null, tasks::approval);
        var source = query.sourceStart();
        if (!query.policyRef().equals(result.policyRef()) || !query.policyBindingSha256().equals(result.policyBindingSha256()) || !query.fingerprint().equals(result.requestSha256())
                || !query.approvalReceiptId().equals(result.approvalReceiptId()) || query.requesterType() != result.requesterType() || !query.requesterSubjectId().equals(result.requesterSubjectId())
                || !source.commandId().equals(result.sourceStartCommandId()) || !source.releaseVersion().equals(result.releaseVersion()) || !source.definitionKey().equals(result.definitionKey())
                || !OrchestrationRequestDigests.start(source).equals(result.sourceRequestSha256()) || !result.expiresAt().isAfter(java.time.Instant.now())) throw invalidResponse();
        return result;
    }

    @Override
    public HumanTaskDTO getHumanTask(GetHumanTaskQuery query) {
        Objects.requireNonNull(query, "query"); var task = call("POST", "human-tasks/get", query, HumanTaskDTO.class, null, tasks::task);
        if (!query.taskId().equals(task.taskId())) throw invalidResponse(); return task;
    }

    @Override
    public HumanTaskPageDTO humanTasks(ListHumanTasksQuery query) {
        return taskPage("query", query);
    }

    @Override
    public HumanTaskPageDTO humanTasksForAdministration(ListHumanTasksQuery query) {
        return taskPage("admin/query", query);
    }

    private HumanTaskPageDTO taskPage(String path, ListHumanTasksQuery query) {
        Objects.requireNonNull(query, "query"); var page = call("POST", "human-tasks/" + path, query, HumanTaskPageDTO.class, null, tasks::page);
        if (page.scopeType() != query.scopeType() || page.selection() != query.selection() || page.items().size() > query.limit()) throw invalidResponse();
        String previous = query.afterTaskId();
        for (var task : page.items()) {
            if (query.releaseVersion() != null && !query.releaseVersion().equals(task.releaseVersion()) || query.scopeId() != null && !query.scopeId().equals(task.scopeId())
                    || query.taskDefinitionKey() != null && !query.taskDefinitionKey().equals(task.taskDefinitionKey()) || previous != null && compareTaskIds(previous, task.taskId()) >= 0) throw invalidResponse();
            previous = task.taskId();
        }
        if (page.nextAfterTaskId() != null && (query.afterTaskId() != null && compareTaskIds(query.afterTaskId(), page.nextAfterTaskId()) >= 0
                || previous != null && compareTaskIds(previous, page.nextAfterTaskId()) > 0)) throw invalidResponse();
        return page;
    }

    @Override
    public HumanTaskActionReceiptDTO claimHumanTask(ClaimHumanTaskCommand command) {
        Objects.requireNonNull(command, "command"); return taskAction("claim", command, command.commandId(), command.taskId(), command.expectedVersion(), HumanTaskActionEnum.CLAIM);
    }
    @Override
    public HumanTaskActionReceiptDTO unclaimHumanTask(UnclaimHumanTaskCommand command) {
        Objects.requireNonNull(command, "command"); return taskAction("unclaim", command, command.commandId(), command.taskId(), command.expectedVersion(), HumanTaskActionEnum.UNCLAIM);
    }
    @Override
    public HumanTaskActionReceiptDTO transferHumanTask(TransferHumanTaskCommand command) {
        Objects.requireNonNull(command, "command"); var result = taskAction("transfer", command, command.commandId(), command.taskId(), command.expectedVersion(), HumanTaskActionEnum.TRANSFER);
        if (!command.targetLane().equals(result.lane())) throw invalidResponse(); return result;
    }
    @Override
    public HumanTaskActionReceiptDTO completeHumanTask(CompleteHumanTaskCommand command) {
        Objects.requireNonNull(command, "command"); return taskAction("complete", command, command.commandId(), command.taskId(), command.expectedVersion(), HumanTaskActionEnum.COMPLETE);
    }
    private HumanTaskActionReceiptDTO taskAction(String path, Object command, String id, String taskId, String version, HumanTaskActionEnum action) {
        var result = call("POST", "human-tasks/" + path, command, HumanTaskActionReceiptDTO.class, null, tasks::receipt);
        if (!id.equals(result.commandId()) || !taskId.equals(result.taskId()) || !version.equals(result.previousVersion()) || result.action() != action) throw invalidResponse(); return result;
    }
    private static int compareTaskIds(String left, String right) { return java.util.Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8)); }

    private <T> T call(String method, String path, Object body, Class<T> type) {
        return call(method, path, body, type, null);
    }

    private <T> T call(String method, String path, Object body, Class<T> type, String leaseToken) {
        return call(method, path, body, type, leaseToken, bytes -> mapper.readValue(bytes, type));
    }

    private <T> T call(String method, String path, Object body, Class<T> type, String leaseToken, Function<byte[], T> decoder) {
        boolean humanTask = path.startsWith("human-tasks/");
        boolean caseOperation = path.startsWith("cases/");
        if (humanTask && (!baseUri.normalize().equals(baseUri) || !"https".equalsIgnoreCase(baseUri.getScheme())
                && !("http".equalsIgnoreCase(baseUri.getScheme()) && java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(baseUri.getHost().toLowerCase(java.util.Locale.ROOT))))) {
            throw new IllegalArgumentException("Human task transport requires HTTPS or a loopback fixture");
        }
        if (caseOperation && (!baseUri.normalize().equals(baseUri) || !"https".equalsIgnoreCase(baseUri.getScheme())
                && !("http".equalsIgnoreCase(baseUri.getScheme()) && java.util.Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(baseUri.getHost().toLowerCase(java.util.Locale.ROOT))))) {
            throw new IllegalArgumentException("Case transport requires HTTPS or a loopback fixture");
        }
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
        if (caseOperation) builder.header("Cache-Control", "no-store");
        HttpRequest request = bytes == null ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
                : builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> call;
        try {
            call = http.sendAsync(request, info -> new BoundedBodySubscriber(caseOperation ? CaseResponseCodec.MAX_BYTES : MAX_RESPONSE_BYTES));
        } catch (RuntimeException failure) {
            if (caseOperation) throw new ProcessClientException(0, "TIANJI_DEPENDENCY_UNAVAILABLE", null, null);
            throw failure;
        }
        try {
            HttpResponse<byte[]> response = call.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw caseOperation ? caseError(response) : error(response, token, leaseToken, humanTask);
            }
            if ((humanTask || caseOperation) && response.statusCode() != 200) throw new ProcessClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            String mediaType = contentType.split(";", 2)[0].trim();
            if (!mediaType.equalsIgnoreCase("application/json") && !mediaType.toLowerCase(java.util.Locale.ROOT).endsWith("+json")) {
                throw new ProcessClientException(response.statusCode(), "TIANJI_INVALID_RESPONSE", null, null);
            }
            try {
                T result = decoder.apply(response.body());
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

    private ProcessClientException error(HttpResponse<byte[]> response, String token, String leaseToken, boolean humanTask) {
        String code = null;
        String correlation = null;
        try {
            Map<?, ?> body = mapper.readValue(response.body(), Map.class);
            if (body.get("code") instanceof String value && !containsCredential(value, token, leaseToken)) code = value;
            if (body.get("correlationId") instanceof String value && !containsCredential(value, token, leaseToken)) correlation = value;
        } catch (RuntimeException ignored) {
            // A non-JSON error body is not safe to expose to callers.
        }
        if (humanTask) { if (code == null || !HUMAN_TASK_ERRORS.contains(code)) code = null; correlation = null; }
        if (code == null) {
            code = switch (response.statusCode()) {
                case 400 -> humanTask ? "TIANJI_INVALID_COMMAND" : "TIANJI_DEPENDENCY_UNAVAILABLE";
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

    private ProcessClientException caseError(HttpResponse<byte[]> response) {
        String code = cases.errorCode(response.body(), response.statusCode());
        if (code == null) {
            code = switch (response.statusCode()) {
                case 400 -> "TIANJI_INVALID_COMMAND";
                case 401 -> "TIANJI_UNAUTHENTICATED";
                case 403 -> "TIANJI_PERMISSION_DENIED";
                case 404 -> "TIANJI_RESOURCE_NOT_FOUND";
                case 409 -> "TIANJI_IDEMPOTENCY_CONFLICT";
                case 413 -> "TIANJI_REQUEST_TOO_LARGE";
                case 429 -> "TIANJI_CAPACITY_EXCEEDED";
                default -> "TIANJI_DEPENDENCY_UNAVAILABLE";
            };
        }
        return new ProcessClientException(response.statusCode(), code, null,
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
