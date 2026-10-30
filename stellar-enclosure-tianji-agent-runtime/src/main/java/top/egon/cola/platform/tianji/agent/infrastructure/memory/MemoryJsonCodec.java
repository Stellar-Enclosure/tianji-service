package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.memory.CorrectMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.ForgetMemoryCommand;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/** Canonical typed persistence and command digests. JSONB formatting never changes a content identity. */
final class MemoryJsonCodec {
    private static final int CONTENT_BYTES = 65_536;
    private static final int STORED_BYTES = 131_072;
    private static final int SOURCE_BYTES = 8192;
    private final JsonMapper mapper;

    MemoryJsonCodec() {
        mapper = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .addModule(new JavaTimeModule()).enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
        mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(STORED_BYTES).maxNumberLength(128).build());
    }

    String content(MemoryContentDTO content) { return write(content, CONTENT_BYTES); }
    String contentSha256(MemoryContentDTO content) { return sha256("TIANJI_MEMORY_CONTENT_V1:" + content(content)); }
    String modelContext(MemoryEntryDTO entry) { return write(Map.of("kind", entry.kind(), "confidence", entry.confidence(), "content", entry.content()), CONTENT_BYTES); }
    String source(MemorySourceDTO source) { return write(source, SOURCE_BYTES); }

    MemoryContentDTO readContent(String value, String expectedSha256) {
        try {
            var tree = tree(value, STORED_BYTES); keys(tree, Set.of("text", "facts"));
            if (!tree.get("text").isNull() && !tree.get("text").isTextual() || !tree.get("facts").isObject()) throw invalid();
            tree.get("facts").properties().forEach(item -> { if (!item.getValue().isTextual()) throw invalid(); });
            var content = mapper.treeToValue(tree, MemoryContentDTO.class);
            if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}") || !expectedSha256.equals(contentSha256(content))) throw invalid();
            return content;
        } catch (JsonProcessingException | IllegalArgumentException failure) { throw invalid(); }
    }

    MemorySourceDTO readSource(String value, String expectedEvidenceSha256) {
        try {
            var tree = tree(value, SOURCE_BYTES); keys(tree, Set.of("reference", "evidenceSha256", "sourceRunId", "sourceEventId"));
            for (String name : List.of("reference", "evidenceSha256")) if (!tree.get(name).isTextual()) throw invalid();
            for (String name : List.of("sourceRunId", "sourceEventId")) if (!tree.get(name).isNull() && !tree.get(name).isTextual()) throw invalid();
            var source = mapper.treeToValue(tree, MemorySourceDTO.class);
            if (!source.evidenceSha256().equals(expectedEvidenceSha256)) throw invalid(); return source;
        } catch (JsonProcessingException | IllegalArgumentException failure) { throw invalid(); }
    }

    String commandSha256(Object command) {
        String operation = switch (command) {
            case UpsertMemoryCommand ignored -> "UPSERT";
            case CorrectMemoryCommand ignored -> "CORRECT";
            case ForgetMemoryCommand ignored -> "FORGET";
            default -> throw new IllegalArgumentException("Memory command is invalid");
        };
        return sha256("TIANJI_MEMORY_COMMAND_V1:" + operation + ':' + write(command, STORED_BYTES));
    }

    Map<String, Object> vectorMetadata(String value) {
        try {
            var root = tree(value, SOURCE_BYTES);
            keys(root, Set.of("tenantId", "issuerFingerprint", "ownerType", "ownerId", "scopeId", "memoryId", "contentRevision", "modelRef",
                    "dimensions", "contentSha256", "kind", "sensitivity", "retentionPolicyRef", "effectiveAtMillis", "expiresAtMillis"));
            Map<String, Object> result = new LinkedHashMap<>();
            for (var field : root.properties()) {
                var item = field.getValue();
                if (field.getKey().equals("effectiveAtMillis") || field.getKey().equals("expiresAtMillis")) {
                    if (!item.isIntegralNumber() || !item.canConvertToLong()) throw invalid(); result.put(field.getKey(), item.longValue());
                } else { if (!item.isTextual()) throw invalid(); result.put(field.getKey(), item.textValue()); }
            }
            return Map.copyOf(result);
        } catch (JsonProcessingException failure) { throw invalid(); }
    }

    private JsonNode tree(String value, int maximum) throws JsonProcessingException {
        if (value == null || value.length() > maximum || value.getBytes(StandardCharsets.UTF_8).length > maximum) throw invalid();
        return mapper.readTree(value);
    }
    private static void keys(JsonNode tree, Set<String> expected) {
        if (tree == null || !tree.isObject()) throw invalid();
        var names = new HashSet<String>(); tree.fieldNames().forEachRemaining(names::add);
        if (!names.equals(expected)) throw invalid();
    }
    private String write(Object value, int maximum) {
        try {
            String json = mapper.writeValueAsString(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > maximum) throw invalid(); return json;
        } catch (JsonProcessingException failure) { throw invalid(); }
    }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("SHA-256 is unavailable", missing); }
    }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Stored Memory data is unavailable or changed"); }
}
