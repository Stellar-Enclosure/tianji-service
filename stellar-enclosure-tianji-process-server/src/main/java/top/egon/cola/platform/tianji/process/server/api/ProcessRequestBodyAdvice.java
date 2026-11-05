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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Applies the same bound to fixed-length and streamed JSON command bodies. */
@ControllerAdvice(basePackageClasses = ProcessController.class)
@ConditionalOnProperty(prefix = "stellar.tianji.process.api", name = "enabled", havingValue = "true")
public final class ProcessRequestBodyAdvice extends RequestBodyAdviceAdapter {
    static final int MAX_BYTES = 9 * 1_048_576;
    static final int MAX_WORKER_BYTES = 256 * 1024;
    static final int MAX_AGENT_RESULT_BYTES = 64 * 1024;
    static final int MAX_PROCESS_CANCEL_BYTES = 4096;
    static final int MAX_PROCESS_WORKER_RECOVERY_BYTES = 8192;
    private static final JsonMapper STRICT_WORKER_JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final JsonMapper STRICT_AGENT_RESULT_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096)
                            .maxNameLength(64).maxNumberLength(20).maxTokenCount(256).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private static final JsonMapper STRICT_PROCESS_CANCEL_JSON = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(2).maxStringLength(4096)
                            .maxNameLength(64).maxNumberLength(20).maxTokenCount(32).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();

    @Override
    public boolean supports(MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return type == PublishProcessReleaseCommand.class || type == StartProcessCommand.class || type == EvaluateDecisionCommand.class
                || workerCommand(type) || type == AgentTerminalEvent.class || type == CancelProcessCommand.class
                || type == RecoverProcessWorkerCommand.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type type,
                                           Class<? extends HttpMessageConverter<?>> converter) throws IOException {
        int maxBytes = type == RecoverProcessWorkerCommand.class ? MAX_PROCESS_WORKER_RECOVERY_BYTES
                : type == CancelProcessCommand.class ? MAX_PROCESS_CANCEL_BYTES
                : type == AgentTerminalEvent.class ? MAX_AGENT_RESULT_BYTES : workerCommand(type) ? MAX_WORKER_BYTES : MAX_BYTES;
        if (input.getHeaders().getContentLength() > maxBytes) throw new TooLarge();
        byte[] body = input.getBody().readNBytes(maxBytes + 1);
        if (body.length > maxBytes) throw new TooLarge();
        if (workerCommand(type)) validateWorkerBody(body, type);
        if (type == AgentTerminalEvent.class) validateAgentResult(body);
        if (type == CancelProcessCommand.class) validateProcessCancel(body);
        if (type == RecoverProcessWorkerCommand.class) validateProcessWorkerRecovery(body);
        return new HttpInputMessage() {
            @Override public InputStream getBody() { return new ByteArrayInputStream(body); }
            @Override public HttpHeaders getHeaders() { return input.getHeaders(); }
        };
    }

    private static boolean workerCommand(Type type) {
        return type == AcquireWorkerTaskCommand.class || type == CompleteWorkerTaskCommand.class || type == FailWorkerTaskCommand.class;
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
            var root = STRICT_PROCESS_CANCEL_JSON.readTree(body);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(fields)) {
                throw invalidProcessCancel();
            }
            strings(root, fields);
            STRICT_PROCESS_CANCEL_JSON.readValue(body, CancelProcessCommand.class);
        } catch (RuntimeException invalid) {
            throw invalidProcessCancel();
        }
    }

    private static void validateProcessWorkerRecovery(byte[] body) {
        Set<String> fields = Set.of("instanceId", "jobId", "commandId", "expectedVersion", "reason");
        try {
            String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            var root = STRICT_PROCESS_CANCEL_JSON.readTree(json);
            if (root == null || !root.isObject() || !Set.copyOf(root.propertyNames()).equals(fields)) {
                throw invalidProcessWorkerRecovery();
            }
            strings(root, fields);
            STRICT_PROCESS_CANCEL_JSON.readValue(json, RecoverProcessWorkerCommand.class);
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
