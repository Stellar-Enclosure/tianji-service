package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessControlSnapshotDTO;

import java.time.Instant;
import java.util.Map;

/** Closed decoder for the optional Process control capability; older response families keep their contracts. */
final class ProcessControlResponseCodec {
    static final int MAX_BYTES = 2 * 1_048_576;
    private static final Map<String, Integer> CONTROL_ERRORS = Map.of(
            "TIANJI_INVALID_COMMAND", 400,
            "TIANJI_UNAUTHENTICATED", 401,
            "TIANJI_PERMISSION_DENIED", 403,
            "TIANJI_RESOURCE_NOT_FOUND", 404,
            "TIANJI_TENANT_CONTEXT_CHANGED", 409,
            "TIANJI_INTERNAL_ERROR", 500,
            "TIANJI_DEPENDENCY_UNAVAILABLE", 503);
    private static final Map<String, Integer> CANCELLATION_ERRORS = Map.ofEntries(
            Map.entry("TIANJI_INVALID_COMMAND", 400),
            Map.entry("TIANJI_UNAUTHENTICATED", 401),
            Map.entry("TIANJI_PERMISSION_DENIED", 403),
            Map.entry("TIANJI_RESOURCE_NOT_FOUND", 404),
            Map.entry("TIANJI_IDEMPOTENCY_CONFLICT", 409),
            Map.entry("TIANJI_TENANT_CONTEXT_CHANGED", 409),
            Map.entry("TIANJI_PROCESS_VERSION_CONFLICT", 409),
            Map.entry("TIANJI_PROCESS_CANCEL_BLOCKED", 409),
            Map.entry("TIANJI_REQUEST_TOO_LARGE", 413),
            Map.entry("TIANJI_INTERNAL_ERROR", 500),
            Map.entry("TIANJI_DEPENDENCY_UNAVAILABLE", 503));
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(1024)
                            .maxNameLength(128).maxNumberLength(32).maxTokenCount(4096).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    ProcessControlSnapshotDTO snapshot(byte[] body) {
        try {
            var node = tree(body);
            strings(node, "cancellationBlockReason", "version", "observedAt");
            instant(node.path("observedAt"));
            var process = node.path("process");
            strings(process, "instanceId", "definitionKey", "releaseVersion", "businessKey", "correlationId",
                    "status", "submittedAt");
            instant(process.path("submittedAt"));
            var finished = process.path("finishedAt");
            if (finished.isMissingNode() || !finished.isNull() && !finished.isString()) throw invalid();
            if (!finished.isNull()) instant(finished);
            var waits = node.path("activeWaits");
            if (!waits.isArray() || waits.size() > 256) throw invalid();
            for (var wait : waits) {
                strings(wait, "activityKey", "kind");
                if (!wait.path("occurrences").isNumber()) throw invalid();
            }
            var actions = node.path("allowedActions");
            if (!actions.isArray() || actions.size() > 1) throw invalid();
            for (var action : actions) if (!action.isString()) throw invalid();
            return mapper.treeToValue(node, ProcessControlSnapshotDTO.class);
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    CancelProcessResult cancellation(byte[] body) {
        try {
            var node = tree(body);
            strings(node, "instanceId", "commandId", "status", "resultingVersion", "finishedAt", "decidedAt");
            if (!node.path("accepted").isBoolean() || !node.path("linkedAgentClosureScheduled").isBoolean()) throw invalid();
            instant(node.path("finishedAt"));
            instant(node.path("decidedAt"));
            return mapper.treeToValue(node, CancelProcessResult.class);
        } catch (RuntimeException invalid) {
            throw invalid();
        }
    }

    String controlErrorCode(byte[] body, int status) {
        return errorCode(body, status, CONTROL_ERRORS);
    }

    String cancellationErrorCode(byte[] body, int status) {
        return errorCode(body, status, CANCELLATION_ERRORS);
    }

    private String errorCode(byte[] body, int status, Map<String, Integer> expected) {
        try {
            var node = tree(body);
            strings(node, "code");
            var correlation = node.path("correlationId");
            if (!correlation.isMissingNode() && !correlation.isNull() && !correlation.isString()) return null;
            var error = mapper.treeToValue(node, OrchestrationErrorDTO.class);
            return expected.getOrDefault(error.code(), -1) == status ? error.code() : null;
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private JsonNode tree(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_BYTES) throw invalid();
        var node = mapper.readTree(body);
        if (node == null || !node.isObject()) throw invalid();
        return node;
    }

    private static void strings(JsonNode node, String... names) {
        if (!node.isObject()) throw invalid();
        for (var name : names) if (!node.path(name).isString()) throw invalid();
    }

    private static void instant(JsonNode node) {
        Instant.parse(node.asText());
    }

    private static ProcessClientException invalid() {
        return new ProcessClientException(0, "TIANJI_INVALID_RESPONSE", null, null);
    }
}
