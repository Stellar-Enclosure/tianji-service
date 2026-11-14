package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.GetProcessStartReceiptQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskCompletionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EngineHumanTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.NodeRetryResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessAutomationClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.MAX_STANDARD_RESPONSE_BYTES;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.V2;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.array;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.decode;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exact;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exactInteger;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.nullable;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.require;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.string;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.utc;

/** Java client for typed process operation and observation under the caller's current credentials. */
public final class HttpProcessAutomationClient implements ProcessAutomationClient, AutoCloseable {
    private static final String INSTANCES = V2 + "process-instances";
    private static final int RELEASE_RESPONSE_LIMIT = 9 * 1024 * 1024;
    private static final int START_REQUEST_LIMIT = 384 * 1024;
    private static final int COMPLETION_REQUEST_LIMIT = 68 * 1024;
    private static final int RETRY_REQUEST_LIMIT = 8192;
    private static final Set<String> START_CONFLICTS = Set.of("TIANJI_PROCESS_NOT_ACTIVATED", "TIANJI_PROCESS_ACTIVATION_CONFLICT");
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern NODE_ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,254}");
    private static final Pattern DIGEST = Pattern.compile("[a-f0-9]{64}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,127}");
    private static final Pattern NODE_CURSOR = Pattern.compile("[A-Za-z0-9_-]{1,2048}");
    private static final JsonMapper RAW_OBJECT_MAPPER = JsonMapper.builder().build();
    static final Map<Integer, String> EXTENDED_ERRORS = Map.of(415, "TIANJI_UNSUPPORTED_MEDIA_TYPE",
            429, "TIANJI_CAPACITY_EXCEEDED", 500, "TIANJI_INTERNAL_ERROR");
    private final AutomationHttpTransport transport;

    public HttpProcessAutomationClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, null);
    }

    public HttpProcessAutomationClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout, Long tenantId) {
        transport = new AutomationHttpTransport(baseUri, http, tokens, timeout, tenantId);
    }

    /** Derives a recovery query for the original command. Retain it before sending the same unmodified command. */
    public static GetProcessStartReceiptQuery startReceiptQuery(AutomationStartCommand command) {
        return prepareStart(command).receiptQuery();
    }

    @Override public AutomationReleaseDTO getRelease(String releaseVersion) {
        AutomationHttpTransport.releaseVersion(releaseVersion);
        var response = read(V2 + "process-releases/" + AutomationHttpTransport.segment(releaseVersion), Map.of(), RELEASE_RESPONSE_LIMIT);
        return decode(response, AutomationReleaseDTO.class, HttpProcessAutomationClient::release,
                result -> releaseVersion.equals(result.getReleaseVersion()) && result.isReleaseProjectionConsistent());
    }

    @Override public AutomationInstanceDTO start(AutomationStartCommand command) {
        StartWireVO original = prepareStart(command);
        var response = transport.send("POST", INSTANCES, Map.of(), original.bytes(), START_REQUEST_LIMIT,
                200, MAX_STANDARD_RESPONSE_BYTES, START_CONFLICTS);
        return readInstance(response, result -> original.definitionKey().equals(result.getDefinitionKey())
                && original.businessKey().equals(result.getBusinessKey()) && original.correlationId().equals(result.getCorrelationId()));
    }

    @Override public AutomationInstanceDTO getInstance(String instanceId) {
        return readInstance(read(instancePath(instanceId), Map.of(), MAX_STANDARD_RESPONSE_BYTES),
                result -> instanceId.equals(result.getInstanceId()));
    }

    @Override public ProcessNodeExecutionPageDTO listNodes(String instanceId, String nodeId, String cursor, Integer limit) {
        var filters = new LinkedHashMap<String, String>();
        if (nodeId != null) {
            AutomationHttpTransport.require(NODE_ID.matcher(nodeId).matches());
            filters.put("nodeId", nodeId);
        }
        if (cursor != null) {
            AutomationHttpTransport.require(NODE_CURSOR.matcher(cursor).matches());
            filters.put("cursor", cursor);
        }
        if (limit != null) {
            AutomationHttpTransport.require(limit >= 1 && limit <= 100);
            filters.put("limit", limit.toString());
        }
        var response = read(instancePath(instanceId) + "/node-executions", filters, MAX_STANDARD_RESPONSE_BYTES);
        return decode(response, ProcessNodeExecutionPageDTO.class, HttpProcessAutomationClient::nodePage,
                result -> hasScopedNodes(result, instanceId, nodeId, limit == null ? 20 : limit));
    }

    @Override public EngineHumanTaskDTO getTask(String taskId) {
        var response = read(taskPath(taskId), Map.of(), MAX_STANDARD_RESPONSE_BYTES);
        return decode(response, EngineHumanTaskDTO.class, HttpProcessAutomationClient::humanTask,
                result -> taskId.equals(result.getTaskId()) && result.isTaskProjectionConsistent());
    }

    @Override public EngineHumanTaskCompletionDTO completeTask(String taskId, CompleteEngineHumanTaskCommand command) {
        AutomationHttpTransport.require(command != null && command.isScalarResultValid());
        byte[] body = encodeRequest(command);
        JsonNode request = requestTree(body, COMPLETION_REQUEST_LIMIT);
        exact(request, "commandId", "expectedVersion", "result");
        String commandId = matching(request.path("commandId"), ID);
        opaque(request.path("expectedVersion"), 128);
        resultObject(request.path("result"));
        request.path("result").forEach(value -> {
            if (value.isString()) string(value);
            else if (!value.isBoolean()) exactInteger(value, -ProcessTypedJson.V2_MAX_WIRE_INTEGER);
        });
        var response = transport.send("POST", taskPath(taskId) + "/completions", Map.of(), body, COMPLETION_REQUEST_LIMIT,
                200, MAX_STANDARD_RESPONSE_BYTES, Set.of(), EXTENDED_ERRORS);
        return decode(response, EngineHumanTaskCompletionDTO.class, HttpProcessAutomationClient::completedTask,
                result -> taskId.equals(result.getTaskId()) && commandId.equals(result.getCommandId()) && result.isCompletedAtValid());
    }

    @Override public NodeRetryResultDTO retryNode(String instanceId, NodeRetryCommand command) {
        AutomationHttpTransport.require(command != null);
        byte[] body = encodeRequest(new NodeRetryCommand(command.getCommandId(), command.getNodeExecutionRef(),
                command.getExpectedVersion(), command.getReason(), command.getBusinessReceiptRef()));
        JsonNode request = requestTree(body, RETRY_REQUEST_LIMIT);
        exact(request, "commandId", "nodeExecutionRef", "expectedVersion", "reason", "businessReceiptRef");
        String commandId = matching(request.path("commandId"), ID);
        String nodeRef = matching(request.path("nodeExecutionRef"), ID);
        opaque(request.path("expectedVersion"), 128);
        String reason = string(request.path("reason"));
        require(!reason.isBlank() && reason.length() <= 512);
        nullable(request, "businessReceiptRef", value -> opaque(value, 255));
        var response = transport.send("POST", instancePath(instanceId) + "/node-retries", Map.of(), body, RETRY_REQUEST_LIMIT,
                202, MAX_STANDARD_RESPONSE_BYTES, Set.of("TIANJI_NODE_RETRY_BLOCKED"), EXTENDED_ERRORS);
        return decode(response, NodeRetryResultDTO.class, HttpProcessAutomationClient::retriedNode,
                result -> instanceId.equals(result.getInstanceId()) && commandId.equals(result.getCommandId())
                        && !nodeRef.equals(result.getNodeExecutionRef()));
    }

    private AutomationHttpTransport.ResponseVO read(String path, Map<String, String> query, int limit) {
        return transport.send("GET", path, query, null, 0, 200, limit, Set.of(), EXTENDED_ERRORS);
    }

    private static AutomationInstanceDTO readInstance(AutomationHttpTransport.ResponseVO response, Predicate<AutomationInstanceDTO> scope) {
        return decode(response, AutomationInstanceDTO.class, HttpProcessAutomationClient::instance,
                result -> result.isInstanceProjectionConsistent() && scope.test(result));
    }

    private static StartWireVO prepareStart(AutomationStartCommand command) {
        AutomationHttpTransport.require(command != null && command.isPublicStartValid());
        byte[] body = encodeRequest(command);
        JsonNode root = requestTree(body, START_REQUEST_LIMIT);
        if (root.has("agentTaskOverrides")) {
            exact(root, "definitionKey", "businessKey", "commandId", "correlationId", "variables", "agentTaskOverrides");
            require(root.path("agentTaskOverrides").isObject());
        } else {
            exact(root, "definitionKey", "businessKey", "commandId", "correlationId", "variables");
        }
        String commandId = matching(root.path("commandId"), ID);
        require(!commandId.startsWith("evt-v2-"));
        var query = new GetProcessStartReceiptQuery(commandId, originalStartDigest(root));
        return new StartWireVO(body, query, string(root.path("definitionKey")), string(root.path("businessKey")),
                string(root.path("correlationId")));
    }

    @SuppressWarnings("unchecked")
    private static String originalStartDigest(JsonNode root) {
        try {
            // The strict reader has already validated the bytes. Untyped mapping preserves integer and string tokens.
            Map<String, Object> original = RAW_OBJECT_MAPPER.treeToValue(root, Map.class);
            return ProcessTypedJson.startRequestSha256(original);
        } catch (RuntimeException malformed) {
            throw invalidRequest();
        }
    }

    private static void release(JsonNode release) {
        exact(release, "releaseVersion", "sourceManifestSha256", "compiledManifestSha256", "compilerVersion", "artifact", "graph",
                "sourceDraftId", "sourceDraftRevision", "dependencyManifestSha256");
        opaque(release.path("compilerVersion"), 128);
        matching(release.path("sourceDraftId"), ID);
        exactInteger(release.path("sourceDraftRevision"), 1);
        matching(release.path("sourceManifestSha256"), DIGEST);
        matching(release.path("compiledManifestSha256"), DIGEST);
        matching(release.path("dependencyManifestSha256"), DIGEST);
        AutomationResponseCodec.graph(release.path("graph"));
        var artifact = release.path("artifact");
        exact(artifact, "profile", "models", "inputSchema", "outputSchema", "nodeBindings", "eventRoutes");
        array(artifact.path("models"), 64).forEach(model -> exact(model, "type", "definitionKey", "xml"));
        array(artifact.path("nodeBindings"), 128).forEach(HttpProcessAutomationClient::sourceBinding);
        array(artifact.path("eventRoutes"), 32).forEach(HttpProcessAutomationClient::sourceRoute);
    }

    private static void sourceBinding(JsonNode node) {
        for (String key : Set.of("service", "rule", "agent", "human", "control", "merge")) {
            if (node.has(key)) require(!node.path(key).isNull());
        }
        var policy = node.path("policy");
        exact(policy, "nodeTimeout", "attemptTimeout", "dispatchTimeout", "onFailure", "onAttemptTimeout", "onNodeTimeout",
                "retry", "cancel", "onUnknownEffect");
        for (String key : Set.of("nodeTimeout", "attemptTimeout", "dispatchTimeout")) duration(policy.path(key));
        for (String key : Set.of("onFailure", "onAttemptTimeout", "onNodeTimeout")) {
            var action = policy.path(key);
            if ("BRANCH".equals(string(action.path("action")))) {
                exact(action, "action", "branchTarget");
                matching(action.path("branchTarget"), ID);
            } else {
                exact(action, "action");
            }
        }
        var retry = policy.path("retry");
        exact(retry, "maxAttempts", "backoff", "retryableCodes");
        duration(retry.path("backoff"));
        if (node.has("human")) duration(node.path("human").path("dueAfter"));
        if (node.has("control") && node.path("control").has("completionCondition")) {
            require(!node.path("control").path("completionCondition").isNull());
        }
        if (node.has("agent") && node.path("agent").has("configurationRef")) {
            require(!node.path("agent").path("configurationRef").isNull());
        }
    }

    private static void sourceRoute(JsonNode route) {
        duration(route.path("ttl"));
        for (String key : Set.of("targetDefinitionKey", "targetNodeId", "schedule")) {
            if (route.has(key)) require(!route.path(key).isNull());
        }
        if (route.has("schedule")) {
            var schedule = route.path("schedule");
            utc(schedule.path("startAt"));
            duration(schedule.path("interval"));
            if (schedule.has("endAt")) utc(schedule.path("endAt"));
            if (schedule.has("maxOccurrences")) exactInteger(schedule.path("maxOccurrences"), 1);
        }
    }

    private static void duration(JsonNode node) {
        String value = string(node);
        require(value.length() <= 128);
        Duration.parse(value);
    }

    private static void instance(JsonNode node) {
        exact(node, "instanceId", "definitionKey", "releaseVersion", "activationRevision", "profile", "businessKey", "correlationId",
                "state", "version", "configurationSha256", "submittedAt", "finishedAt", "output", "availableActions", "graph");
        opaque(node.path("instanceId"), 255);
        matching(node.path("definitionKey"), ID);
        AutomationHttpTransport.releaseVersion(string(node.path("releaseVersion")));
        exactInteger(node.path("activationRevision"), 1);
        require("AUTOMATION_V2".equals(string(node.path("profile"))));
        opaque(node.path("businessKey"), 255);
        matching(node.path("correlationId"), ID);
        string(node.path("state"));
        opaque(node.path("version"), 128);
        matching(node.path("configurationSha256"), DIGEST);
        utc(node.path("submittedAt"));
        nullable(node, "finishedAt", AutomationResponseCodec::utc);
        nullable(node, "output", HttpProcessAutomationClient::resultObject);
        array(node.path("availableActions"), 1).forEach(AutomationResponseCodec::string);
        AutomationResponseCodec.graph(node.path("graph"));
    }

    private static void nodePage(JsonNode page) {
        exact(page, "instanceId", "releaseVersion", "observedAt", "items", "nextCursor");
        opaque(page.path("instanceId"), 255);
        AutomationHttpTransport.releaseVersion(string(page.path("releaseVersion")));
        utc(page.path("observedAt"));
        nullable(page, "nextCursor", value -> matching(value, NODE_CURSOR));
        array(page.path("items"), 100).forEach(HttpProcessAutomationClient::nodeExecution);
    }

    private static void nodeExecution(JsonNode node) {
        exact(node, "nodeId", "occurrenceId", "attempt", "nodeExecutionRef", "kind", "state", "inputSha256", "outputSchemaSha256",
                "startedAt", "finishedAt", "deadlineAt", "errorCode", "sideEffectOutcome", "agentRunId", "output", "eventReceiptIds",
                "version", "availableActions");
        matching(node.path("nodeId"), NODE_ID);
        matching(node.path("occurrenceId"), ID);
        matching(node.path("nodeExecutionRef"), ID);
        attempt(node.path("attempt"));
        string(node.path("kind"));
        string(node.path("state"));
        string(node.path("sideEffectOutcome"));
        matching(node.path("inputSha256"), DIGEST);
        matching(node.path("outputSchemaSha256"), DIGEST);
        utc(node.path("startedAt"));
        nullable(node, "finishedAt", AutomationResponseCodec::utc);
        utc(node.path("deadlineAt"));
        nullable(node, "errorCode", value -> matching(value, ERROR_CODE));
        nullable(node, "agentRunId", value -> opaque(value, 64));
        nullable(node, "output", HttpProcessAutomationClient::resultObject);
        array(node.path("eventReceiptIds"), Integer.MAX_VALUE).forEach(value -> matching(value, ID));
        opaque(node.path("version"), 128);
        array(node.path("availableActions"), 1).forEach(AutomationResponseCodec::string);
    }

    private static boolean hasScopedNodes(ProcessNodeExecutionPageDTO page, String instanceId, String nodeId, int limit) {
        if (!instanceId.equals(page.getInstanceId()) || !page.isObservedAtValid() || page.getItems().size() > limit) return false;
        var seen = new HashSet<String>();
        for (ProcessNodeExecutionDTO node : page.getItems()) {
            if (nodeId != null && !nodeId.equals(node.getNodeId()) || !node.isNodeProjectionConsistent()
                    || !seen.add(node.getNodeExecutionRef())) return false;
        }
        return true;
    }

    private static void humanTask(JsonNode node) {
        exact(node, "taskId", "instanceId", "nodeId", "state", "assignedToMe", "version", "resultSchema", "availableActions", "dueAt");
        opaque(node.path("taskId"), 255);
        opaque(node.path("instanceId"), 255);
        matching(node.path("nodeId"), NODE_ID);
        string(node.path("state"));
        require(node.path("assignedToMe").isBoolean());
        opaque(node.path("version"), 128);
        require(node.path("resultSchema").isObject());
        array(node.path("availableActions"), 4).forEach(AutomationResponseCodec::string);
        nullable(node, "dueAt", AutomationResponseCodec::utc);
    }

    private static void completedTask(JsonNode node) {
        exact(node, "taskId", "commandId", "state", "completedAt");
        opaque(node.path("taskId"), 255);
        matching(node.path("commandId"), ID);
        require("COMPLETED".equals(string(node.path("state"))));
        utc(node.path("completedAt"));
    }

    private static void retriedNode(JsonNode node) {
        exact(node, "commandId", "instanceId", "occurrenceId", "attempt", "nodeExecutionRef", "state");
        matching(node.path("commandId"), ID);
        opaque(node.path("instanceId"), 255);
        matching(node.path("occurrenceId"), ID);
        matching(node.path("nodeExecutionRef"), ID);
        require("SCHEDULED".equals(string(node.path("state"))));
        attempt(node.path("attempt"));
    }

    private static void attempt(JsonNode node) { require(exactInteger(node, 1) <= 10); }

    private static void resultObject(JsonNode node) { require(node.isObject()); }

    static byte[] encodeRequest(Object value) {
        try { return AutomationResponseCodec.encode(Objects.requireNonNull(value)); }
        catch (RuntimeException malformed) { throw invalidRequest(); }
    }

    static JsonNode requestTree(byte[] body, int limit) {
        if (body.length > limit) throw invalidRequest();
        try { return AutomationResponseCodec.tree(body); }
        catch (RuntimeException malformed) { throw invalidRequest(); }
    }

    static String matching(JsonNode value, Pattern pattern) {
        String text = string(value);
        require(pattern.matcher(text).matches());
        return text;
    }

    static String opaque(JsonNode value, int limit) {
        String text = string(value);
        require(!text.isBlank() && text.codePointCount(0, text.length()) <= limit && text.chars().noneMatch(Character::isISOControl));
        return text;
    }

    private static IllegalArgumentException invalidRequest() { return new IllegalArgumentException("Automation request is invalid"); }

    private static String instancePath(String id) { return INSTANCES + "/" + AutomationHttpTransport.opaqueSegment(id); }
    private static String taskPath(String id) { return V2 + "process-tasks/" + AutomationHttpTransport.opaqueSegment(id); }

    @Override public void close() { transport.close(); }

    private record StartWireVO(byte[] bytes, GetProcessStartReceiptQuery receiptQuery,
                               String definitionKey, String businessKey, String correlationId) { }
}
