package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;

/** Metadata decoding is strict without changing the older Agent operations' response contract. */
final class AgentContextResponseCodec {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    AgentContextSnapshotPageDTO read(byte[] bytes) {
        var page = mapper.readTree(bytes);
        if (!page.isObject() || !page.path("items").isArray()) throw invalid();
        optional(page.path("nextAfterModelCall"), false);
        for (var snapshot : page.path("items")) {
            strings(snapshot, "runId", "reservationId", "capturedAt");
            integers(snapshot, "modelCallNumber", "attempt", "leaseEpoch");
            var metadata = snapshot.path("metadata");
            strings(metadata, "projectionVersion", "ownerSha256", "runId", "reservationId", "agentName", "nativeInvocationId", "nativeSessionId",
                    "definitionContentSha256", "requestSha256", "executionSnapshotSha256", "nativeRequestSha256", "sourcePolicySha256", "modelAdapterSha256", "trimmingPolicySha256", "historyEventIdsSha256");
            integers(metadata, "schemaVersion", "leaseEpoch", "estimatedInputTokens", "effectiveOutputLimit", "availableHistoryEventCount");
            optional(metadata.path("lastHistoryEventId"), true);
            strings(metadata.path("window"), "modelName", "modelRouteRef", "policyVersionRef");
            integers(metadata.path("window"), "windowTokens", "safetyMarginTokens");
            if (!metadata.path("sources").isArray() || !metadata.path("trims").isArray()) throw invalid();
            for (var source : metadata.path("sources")) {
                strings(source, "kind", "role", "sourceId", "sourceVersion", "provenanceId", "sourceSha256", "modelTextSha256");
                integers(source, "utf8Bytes"); optional(source.path("observedAt"), true);
            }
            for (var trim : metadata.path("trims")) {
                strings(trim, "kind", "sourceId", "sourceVersion", "reason", "beforeSha256", "afterSha256"); integers(trim, "droppedItems");
            }
        }
        return mapper.treeToValue(page, AgentContextSnapshotPageDTO.class);
    }
    private static void strings(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid();
        for (var field : fields) if (!object.path(field).isString()) throw invalid();
    }
    private static void integers(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid();
        for (var field : fields) if (!object.path(field).isIntegralNumber()) throw invalid();
    }
    private static void optional(JsonNode value, boolean string) {
        if (!value.isMissingNode() && !value.isNull() && !(string ? value.isString() : value.isIntegralNumber())) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent context metadata response is invalid"); }
}
