package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.CaseInstanceDTO;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationErrorDTO;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Closed Case-only decoder; existing Process/Decision/Worker compatibility is preserved. */
final class CaseResponseCodec {
    static final int MAX_BYTES = 128 * 1_024;
    private static final Map<String, Integer> ERRORS = Map.of(
            "TIANJI_INVALID_COMMAND", 400,
            "TIANJI_UNAUTHENTICATED", 401,
            "TIANJI_PERMISSION_DENIED", 403,
            "TIANJI_RESOURCE_NOT_FOUND", 404,
            "TIANJI_IDEMPOTENCY_CONFLICT", 409,
            "TIANJI_TENANT_CONTEXT_CHANGED", 409,
            "TIANJI_REQUEST_TOO_LARGE", 413,
            "TIANJI_CAPACITY_EXCEEDED", 429,
            "TIANJI_DEPENDENCY_UNAVAILABLE", 503);
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    CaseInstanceDTO instance(byte[] body) {
        var node = tree(body);
        strings(node, "caseInstanceId", "definitionKey", "releaseVersion", "businessKey", "correlationId", "status", "submittedAt");
        var finished = node.path("finishedAt");
        if (finished.isMissingNode() || !finished.isNull() && !finished.isString()) throw invalid();
        instant(node.path("submittedAt"));
        if (!finished.isNull()) instant(finished);
        var items = node.path("planItems");
        if (!items.isArray() || items.size() > 16) throw invalid();
        items.forEach(CaseResponseCodec::planItem);
        return mapper.treeToValue(node, CaseInstanceDTO.class);
    }

    CaseOperationResult operation(byte[] body) {
        var node = tree(body);
        strings(node, "commandId", "caseInstanceId", "planItemInstanceId", "planItemDefinitionKey", "action",
                "releaseVersion", "definitionKey", "previousState", "previousVersion", "resultingState",
                "resultingVersion", "occurredAt");
        instant(node.path("occurredAt"));
        return mapper.treeToValue(node, CaseOperationResult.class);
    }

    String errorCode(byte[] body, int status) {
        try {
            var node = tree(body);
            strings(node, "code");
            var correlation = node.path("correlationId");
            if (!correlation.isMissingNode() && !correlation.isNull() && !correlation.isString()) throw invalid();
            var error = mapper.treeToValue(node, OrchestrationErrorDTO.class);
            return ERRORS.getOrDefault(error.code(), -1) == status ? error.code() : null;
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

    private static void planItem(JsonNode node) {
        strings(node, "planItemInstanceId", "planItemDefinitionKey", "kind", "state", "version");
        var actions = node.path("availableActions");
        if (!actions.isArray() || actions.size() > 4) throw invalid();
        for (var action : actions) if (!action.isString()) throw invalid();
    }

    private static void strings(JsonNode node, String... names) {
        if (!node.isObject()) throw invalid();
        for (var name : names) if (!node.path(name).isString()) throw invalid();
    }

    private static void instant(JsonNode value) {
        try { Instant.parse(value.asText()); }
        catch (DateTimeParseException invalidTimestamp) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Case response is invalid");
    }
}
