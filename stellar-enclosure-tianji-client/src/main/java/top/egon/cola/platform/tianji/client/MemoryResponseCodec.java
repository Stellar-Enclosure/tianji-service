package top.egon.cola.platform.tianji.client;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryMutationResultDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryRetrievalPageDTO;

/** Memory responses have a closed typed decoder without changing existing Agent/Session wire compatibility. */
final class MemoryResponseCodec {
    private final JsonMapper mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS).build();

    MemoryEntryDTO entry(byte[] bytes) { var node = mapper.readTree(bytes); entry(node); return mapper.treeToValue(node, MemoryEntryDTO.class); }
    MemoryMutationResultDTO mutation(byte[] bytes) {
        var node = mapper.readTree(bytes); owner(node.path("owner"));
        strings(node, "commandId", "scopeId", "memoryId", "outcome", "stateAtMutation", "occurredAt"); integers(node, "revision");
        return mapper.treeToValue(node, MemoryMutationResultDTO.class);
    }
    MemoryRetrievalPageDTO page(byte[] bytes) {
        var node = mapper.readTree(bytes); owner(node.path("owner")); strings(node, "scopeId"); optional(node.path("nextAfterMemoryId"), true);
        if (!node.path("items").isArray() || node.path("items").size() > 50) throw invalid();
        for (var hit : node.path("items")) {
            if (!hit.isObject()) throw invalid(); entry(hit.path("entry"));
            var score = hit.path("score"); if (!score.isMissingNode() && !score.isNull() && !score.isNumber()) throw invalid();
        }
        return mapper.treeToValue(node, MemoryRetrievalPageDTO.class);
    }
    private static void entry(JsonNode node) {
        owner(node.path("owner")); strings(node, "scopeId", "memoryId", "kind", "state", "contentSha256", "sensitivity", "retentionPolicyRef", "effectiveAt", "createdAt", "updatedAt");
        integers(node, "revision"); optional(node.path("supersedesRevision"), false); optional(node.path("expiresAt"), true);
        if (!node.path("confirmed").isBoolean() || !node.path("confidence").isNumber()) throw invalid();
        var content = node.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            if (!content.isObject() || !content.path("facts").isObject()) throw invalid(); optional(content.path("text"), true);
            for (var value : content.path("facts")) if (!value.isString()) throw invalid();
        }
        var source = node.path("source");
        if (!source.isMissingNode() && !source.isNull()) {
            strings(source, "reference", "evidenceSha256"); optional(source.path("sourceRunId"), true); optional(source.path("sourceEventId"), true);
        }
    }
    private static void owner(JsonNode owner) { strings(owner, "type", "id"); }
    private static void strings(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid(); for (var name : fields) if (!object.path(name).isString()) throw invalid();
    }
    private static void integers(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid(); for (var name : fields) if (!object.path(name).isIntegralNumber() || !object.path(name).canConvertToLong()) throw invalid();
    }
    private static void optional(JsonNode value, boolean string) {
        if (!value.isMissingNode() && !value.isNull() && !(string ? value.isString() : value.isIntegralNumber() && value.canConvertToLong())) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory response is invalid"); }
}
