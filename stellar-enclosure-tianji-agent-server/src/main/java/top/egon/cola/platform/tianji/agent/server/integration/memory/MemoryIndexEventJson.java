package top.egon.cola.platform.tianji.agent.server.integration.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import top.egon.cola.platform.tianji.agent.application.memory.MemoryIndexPublication;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Private bounded protocol for reference-only local Outbox work, independent of the application's general mapper. */
final class MemoryIndexEventJson {
    private static final int MAX_BYTES = 16_384;
    private static final Set<String> FIELDS = Set.of("action", "tenantId", "ownerIssuer", "owner", "scopeId", "memoryId", "revision", "contentSha256", "modelRef", "documentId", "occurredAt");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    static String encode(MemoryIndexPublication.Event event) {
        try { String value = JSON.writeValueAsString(event); bounded(value); return value; }
        catch (JsonProcessingException failure) { throw invalid(); }
    }
    static MemoryIndexPublication.Event decode(String value) {
        bounded(value);
        try {
            var root = JSON.readTree(value); keys(root, FIELDS); keys(root.get("owner"), Set.of("type", "id"));
            for (var field : List.of("action", "ownerIssuer", "scopeId", "memoryId", "contentSha256", "occurredAt")) if (!root.get(field).isTextual()) throw invalid();
            for (var field : List.of("modelRef", "documentId")) if (!root.get(field).isNull() && !root.get(field).isTextual()) throw invalid();
            for (var field : List.of("tenantId", "revision")) if (!root.get(field).isIntegralNumber() || !root.get(field).canConvertToLong()) throw invalid();
            if (!root.get("owner").get("type").isTextual() || !root.get("owner").get("id").isTextual()) throw invalid();
            return JSON.treeToValue(root, MemoryIndexPublication.Event.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) { throw invalid(); }
    }
    private static void bounded(String value) {
        if (value == null || value.length() > MAX_BYTES || !StandardCharsets.UTF_8.newEncoder().canEncode(value) || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw invalid();
    }
    private static void keys(JsonNode value, Set<String> expected) {
        if (value == null || !value.isObject()) throw invalid(); var fields = new HashSet<String>(); value.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(expected)) throw invalid();
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory index protocol is invalid"); }
}
