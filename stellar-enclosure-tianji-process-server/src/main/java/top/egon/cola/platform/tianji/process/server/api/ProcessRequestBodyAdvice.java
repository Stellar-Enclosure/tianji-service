package top.egon.cola.platform.tianji.process.server.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.EnumFeature;
import top.egon.cola.platform.tianji.contract.orchestration.EvaluateDecisionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;
import top.egon.cola.platform.tianji.contract.orchestration.StartProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.CompleteEngineHumanTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.PublishProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ValidateProcessDraftQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerAcquireCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerCompletionCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerFailureCommand;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftContentCodec;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Map;

/** Applies the same bound to fixed-length and streamed JSON command bodies. */
@ControllerAdvice(basePackageClasses = ProcessController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class ProcessRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 9 * 1_048_576;
    static final int MAX_WORKER_BYTES = 256 * 1024;
    static final int MAX_AGENT_RESULT_BYTES = 64 * 1024;
    static final int MAX_PROCESS_CANCEL_BYTES = 4096;
    static final int MAX_PROCESS_WORKER_RECOVERY_BYTES = 8192;
    static final int MAX_DRAFT_SAVE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DRAFT_COMMAND_BYTES = 4 * 1024;
    private static final int MAX_ACTIVATION_COMMAND_BYTES = 4 * 1024;
    private static final int MAX_AUTOMATION_START_BYTES = 384 * 1024;
    private static final int MAX_ENGINE_TASK_COMPLETION_BYTES = 68 * 1024;
    private static final int MAX_AUTOMATION_WORKER_ACQUIRE_BYTES = 4 * 1024;
    private static final int MAX_AUTOMATION_WORKER_REPORT_BYTES = 68 * 1024;
    private static final Set<String> WORKER_ACQUIRE_FIELDS = Set.of("capabilityKey", "capabilityVersion");
    private static final Set<String> WORKER_COMPLETE_FIELDS = Set.of("taskId", "activityOccurrenceId", "nodeExecutionRef",
            "leaseToken", "commandId", "effectKey", "businessReceiptId", "output");
    private static final Set<String> WORKER_FAIL_FIELDS = Set.of("taskId", "activityOccurrenceId", "nodeExecutionRef",
            "leaseToken", "commandId", "kind", "errorCode", "businessReceiptId");
    private static final Set<String> ENGINE_TASK_COMPLETION_FIELDS = Set.of("commandId", "expectedVersion", "result");
    private static final Set<String> ACTIVATION_FIELDS = Set.of("releaseVersion", "expectedActivationRevision", "commandId");
    private static final JsonMapper STRICT_WORKER_JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final JsonMapper STRICT_AGENT_RESULT_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096)
                            .maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final JsonMapper STRICT_FLAT_PROCESS_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxStringLength(4096)
                            .maxNameLength(64).maxNumberLength(20).maxTokenCount(32).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final JsonMapper STRICT_AUTOMATION_START_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16)
                            .maxStringLength(MAX_AUTOMATION_START_BYTES).maxNameLength(MAX_AUTOMATION_START_BYTES)
                            .maxNumberLength(32).maxTokenCount(MAX_AUTOMATION_START_BYTES).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == PublishProcessReleaseCommand.class || type == StartProcessCommand.class || type == EvaluateDecisionCommand.class
                || workerCommand(type) || type == AgentTerminalEvent.class || type == CancelProcessCommand.class
                || type == RecoverProcessWorkerCommand.class || draftCommand(type) || type == ActivationCommand.class
                || type == AutomationStartCommand.class || type == CompleteEngineHumanTaskCommand.class || automationWorkerCommand(type);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        if (draftCommand(type) || type == ActivationCommand.class || type == AutomationStartCommand.class
                || type == CompleteEngineHumanTaskCommand.class || automationWorkerCommand(type)) {
            var contentType = input.getHeaders().getContentType();
            if (contentType != null && contentType.getCharset() != null
                    && !StandardCharsets.UTF_8.equals(contentType.getCharset())) {
                throw new IllegalArgumentException("Process automation request must use UTF-8");
            }
        }
        int maxBytes = type == SaveProcessDraftCommand.class ? MAX_DRAFT_SAVE_BYTES
                : draftCommand(type) ? MAX_DRAFT_COMMAND_BYTES
                : type == AutomationStartCommand.class ? MAX_AUTOMATION_START_BYTES
                : type == CompleteEngineHumanTaskCommand.class ? MAX_ENGINE_TASK_COMPLETION_BYTES
                : type == AutomationWorkerAcquireCommand.class ? MAX_AUTOMATION_WORKER_ACQUIRE_BYTES
                : automationWorkerCommand(type) ? MAX_AUTOMATION_WORKER_REPORT_BYTES
                : type == ActivationCommand.class ? MAX_ACTIVATION_COMMAND_BYTES
                : type == RecoverProcessWorkerCommand.class ? MAX_PROCESS_WORKER_RECOVERY_BYTES
                : type == CancelProcessCommand.class ? MAX_PROCESS_CANCEL_BYTES
                : type == AgentTerminalEvent.class ? MAX_AGENT_RESULT_BYTES : workerCommand(type) ? MAX_WORKER_BYTES : MAX_BYTES;
        if (input.getHeaders().getContentLength() > maxBytes) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new TooLarge();
        if (workerCommand(type)) validateWorkerBody(body, type);
        if (automationWorkerCommand(type)) validateAutomationWorkerBody(body, type);
        if (type == AgentTerminalEvent.class) validateAgentResult(body);
        if (type == CancelProcessCommand.class) validateProcessCancel(body);
        if (type == RecoverProcessWorkerCommand.class) validateProcessWorkerRecovery(body);
        if (type == CreateProcessDraftCommand.class) ProcessDraftContentCodec.validateCreateWire(body);
        if (type == SaveProcessDraftCommand.class) ProcessDraftContentCodec.validateSaveWire(body);
        if (type == DiscardProcessDraftCommand.class) ProcessDraftContentCodec.validateDiscardWire(body);
        if (type == PublishProcessDraftCommand.class || type == ValidateProcessDraftQuery.class) validatePublicationBody(body, type);
        if (type == ActivationCommand.class) validateActivationBody(body);
        if (type == AutomationStartCommand.class) validateAutomationStartBody(body);
        if (type == CompleteEngineHumanTaskCommand.class) validateEngineTaskCompletionBody(body);
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    private static boolean workerCommand(Type type) {
        return type == AcquireWorkerTaskCommand.class || type == CompleteWorkerTaskCommand.class || type == FailWorkerTaskCommand.class;
    }

    private static boolean automationWorkerCommand(Type type) {
        return type == AutomationWorkerAcquireCommand.class || type == AutomationWorkerCompletionCommand.class
                || type == AutomationWorkerFailureCommand.class;
    }

    private static void validateAutomationWorkerBody(byte[] body, Type type) {
        Set<String> expected = type == AutomationWorkerAcquireCommand.class ? WORKER_ACQUIRE_FIELDS
                : type == AutomationWorkerCompletionCommand.class ? WORKER_COMPLETE_FIELDS : WORKER_FAIL_FIELDS;
        try {
            var json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var root = STRICT_AUTOMATION_START_JSON.readTree(json);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(expected)) {
                throw invalidWorkerBody();
            }
            for (String name : expected) {
                var field = root.path(name);
                if ("capabilityVersion".equals(name)) {
                    if (!field.isIntegralNumber() || !field.canConvertToLong()
                            || field.longValue() < 1 || field.longValue() > ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
                        throw invalidWorkerBody();
                    }
                } else if ("output".equals(name)) {
                    if (!field.isObject()) throw invalidWorkerBody();
                    validateAutomationWorkerOutput(field);
                } else if (!(type == AutomationWorkerFailureCommand.class && "businessReceiptId".equals(name) && field.isNull())
                        && !field.isString()) {
                    throw invalidWorkerBody();
                }
            }
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidWorkerBody();
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateAutomationWorkerOutput(JsonNode output) {
        var values = (Map<String, Object>) STRICT_AUTOMATION_START_JSON.treeToValue(output, Map.class);
        ProcessTypedJson.validateTypedIntegerLeaves(values);
        ProcessTypedJson.canonicalBytes(values, 64 * 1024);
    }

    private static IllegalArgumentException invalidWorkerBody() {
        return new IllegalArgumentException("Invalid automation Worker request");
    }

    private static boolean draftCommand(Type type) {
        return type == CreateProcessDraftCommand.class || type == SaveProcessDraftCommand.class || type == DiscardProcessDraftCommand.class
                || type == PublishProcessDraftCommand.class || type == ValidateProcessDraftQuery.class;
    }

    private static void validatePublicationBody(byte[] body, Type type) {
        boolean publishing = type == PublishProcessDraftCommand.class;
        Set<String> fields = publishing
                ? Set.of("draftId", "expectedRevision", "expectedContentSha256", "releaseVersion", "commandId")
                : Set.of("draftId", "expectedRevision", "expectedContentSha256");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var root = STRICT_FLAT_PROCESS_JSON.readTree(json);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(fields)
                    || !root.get("expectedRevision").isIntegralNumber() || !root.get("expectedRevision").canConvertToLong()) {
                throw invalidPublicationBody();
            }
            for (String field : fields) {
                if (!"expectedRevision".equals(field) && !root.get(field).isString()) throw invalidPublicationBody();
            }
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidPublicationBody();
        }
    }

    private static IllegalArgumentException invalidPublicationBody() {
        return new IllegalArgumentException("Invalid process automation request");
    }

    private static void validateActivationBody(byte[] body) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var root = STRICT_FLAT_PROCESS_JSON.readTree(json);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(ACTIVATION_FIELDS)
                    || !root.get("releaseVersion").isString() || !root.get("commandId").isString()) {
                throw invalidActivationBody();
            }
            var revision = root.get("expectedActivationRevision");
            if (!revision.isIntegralNumber() || !revision.canConvertToLong()
                    || revision.longValue() < 0 || revision.longValue() > ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
                throw invalidActivationBody();
            }
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidActivationBody();
        }
    }

    private static IllegalArgumentException invalidActivationBody() {
        return new IllegalArgumentException("Invalid process activation request");
    }

    @SuppressWarnings("unchecked")
    private static void validateAutomationStartBody(byte[] body) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            Map<String, Object> wire = STRICT_AUTOMATION_START_JSON.readValue(json, Map.class);
            ProcessTypedJson.startRequestSha256(wire);
            if (((String) wire.get("commandId")).startsWith("evt-v2-")) throw new IllegalArgumentException();
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid process automation START request");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateEngineTaskCompletionBody(byte[] body) {
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var wire = STRICT_AUTOMATION_START_JSON.readValue(json, Map.class);
            if (wire == null || !wire.keySet().equals(ENGINE_TASK_COMPLETION_FIELDS)
                    || !(wire.get("commandId") instanceof String command) || !command.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                    || !(wire.get("expectedVersion") instanceof String version)
                    || version.length() > 128 || version.isBlank() || !version.matches("[^\\p{Cc}]{1,128}")
                    || !(wire.get("result") instanceof Map<?, ?> result)
                    || !new CompleteEngineHumanTaskCommand(command, version, (Map<String, Object>) result).isScalarResultValid()) {
                throw new IllegalArgumentException();
            }
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid automation task completion request");
        }
    }

    private static void validateWorkerBody(byte[] body, Type type) {
        Set<String> expected = type == AcquireWorkerTaskCommand.class ? Set.of("topic", "schemaVersion")
                : type == CompleteWorkerTaskCommand.class ? Set.of("taskId", "activityOccurrenceId", "leaseToken", "businessReceiptId", "output")
                : Set.of("taskId", "activityOccurrenceId", "leaseToken", "kind", "errorCode");
        try {
            var root = STRICT_WORKER_JSON.readTree(body);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(expected)) {
                throw new IllegalArgumentException("Worker request is invalid");
            }
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Worker request is invalid");
        }
    }

    private static void validateAgentResult(byte[] body) {
        Set<String> required = Set.of("schemaVersion", "producerId", "eventId", "tenantId", "ownerIssuerSha256", "ownerSubjectId",
                "idempotencyKey", "link", "definitionKey", "definitionVersion", "runId", "terminalRevision", "status", "finishedAt",
                "resultSha256", "sideEffectOutcome");
        Set<String> linkFields = Set.of("sourceId", "scopeType", "scopeInstanceId", "activityOccurrenceId", "definitionContentSha256", "dispatchNotAfter");
        try {
            var root = STRICT_AGENT_RESULT_JSON.readTree(body);
            if (root == null || !root.isObject()) throw invalidAgentResult();
            var fields = Set.copyOf(root.propertyNames());
            if (!fields.containsAll(required) || fields.stream().anyMatch(name -> !required.contains(name) && !"errorCode".equals(name))) throw invalidAgentResult();
            for (String name : Set.of("schemaVersion", "tenantId", "definitionVersion", "terminalRevision")) {
                if (!root.get(name).isIntegralNumber() || !root.get(name).canConvertToLong()) throw invalidAgentResult();
            }
            strings(root, Set.of("producerId", "eventId", "ownerIssuerSha256", "ownerSubjectId", "idempotencyKey", "definitionKey", "runId",
                    "status", "finishedAt", "resultSha256", "sideEffectOutcome"));
            if (root.get("errorCode") != null && !root.get("errorCode").isNull() && !root.get("errorCode").isString()) throw invalidAgentResult();
            var link = root.get("link");
            if (link == null || !link.isObject() || !Set.copyOf(link.propertyNames()).equals(linkFields)) throw invalidAgentResult();
            strings(link, linkFields);
            STRICT_AGENT_RESULT_JSON.readValue(body, AgentTerminalEvent.class);
        } catch (RuntimeException invalid) { throw invalidAgentResult(); }
    }

    private static void validateProcessCancel(byte[] body) {
        Set<String> fields = Set.of("instanceId", "commandId", "expectedVersion", "reason");
        try {
            var root = STRICT_FLAT_PROCESS_JSON.readTree(body);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(fields)) {
                throw invalidProcessCancel();
            }
            strings(root, fields);
            STRICT_FLAT_PROCESS_JSON.readValue(body, CancelProcessCommand.class);
        } catch (RuntimeException invalid) {
            throw invalidProcessCancel();
        }
    }

    private static void validateProcessWorkerRecovery(byte[] body) {
        Set<String> fields = Set.of("instanceId", "jobId", "commandId", "expectedVersion", "reason");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var root = STRICT_FLAT_PROCESS_JSON.readTree(json);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(fields)) {
                throw invalidProcessWorkerRecovery();
            }
            strings(root, fields);
            STRICT_FLAT_PROCESS_JSON.readValue(json, RecoverProcessWorkerCommand.class);
        } catch (CharacterCodingException | RuntimeException invalid) {
            throw invalidProcessWorkerRecovery();
        }
    }

    private static void strings(JsonNode node, Set<String> names) {
        for (String name : names) if (node.get(name) == null || !node.get(name).isString()) throw invalidAgentResult();
    }
    private static IllegalArgumentException invalidAgentResult() { return new IllegalArgumentException("Agent result event is invalid"); }
    private static IllegalArgumentException invalidProcessCancel() { return new IllegalArgumentException("Process cancellation request is invalid"); }

    private static IllegalArgumentException invalidProcessWorkerRecovery() {
        return new IllegalArgumentException("Process Worker recovery request is invalid");
    }

    static final class TooLarge extends RuntimeException { }
}
