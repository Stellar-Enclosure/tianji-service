package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Metadata has its own strict codec and cannot change the existing run/definition fingerprint encoding. */
final class AgentContextJsonCodec {
    static final int MAX_METADATA_BYTES = 262_144;
    static final int MAX_DATABASE_BYTES = 294_912;
    private final JsonMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule())
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    String write(AgentContextMetadataDTO metadata) {
        if (metadata == null) throw invalid();
        try {
            String value = mapper.writeValueAsString(metadata);
            if (value.getBytes(StandardCharsets.UTF_8).length > MAX_METADATA_BYTES) throw new AgentLedgerException(
                    AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Context provenance exceeds its metadata limit");
            return value;
        } catch (JsonProcessingException failure) { throw invalid(); }
    }

    AgentContextMetadataDTO read(String value, String expectedHash) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length > MAX_DATABASE_BYTES) throw invalid();
        try {
            var tree = mapper.readTree(value);
            requireShape(tree);
            var metadata = mapper.treeToValue(tree, AgentContextMetadataDTO.class);
            if (!hash(write(metadata)).equals(expectedHash)) throw invalid();
            return metadata;
        } catch (RuntimeException | JsonProcessingException failure) { throw invalid(); }
    }

    String fingerprint(AgentContextMetadataDTO metadata) { return hash(write(metadata)); }

    private static void requireShape(JsonNode tree) {
        strings(tree, "projectionVersion", "ownerSha256", "runId", "reservationId", "agentName", "nativeInvocationId", "nativeSessionId", "definitionContentSha256", "requestSha256", "executionSnapshotSha256", "nativeRequestSha256",
                "sourcePolicySha256", "modelAdapterSha256", "trimmingPolicySha256", "historyEventIdsSha256");
        integers(tree, "schemaVersion", "leaseEpoch", "estimatedInputTokens", "effectiveOutputLimit", "availableHistoryEventCount");
        var window = tree.path("window");
        strings(window, "modelName", "modelRouteRef", "policyVersionRef"); integers(window, "windowTokens", "safetyMarginTokens");
        if (!tree.path("sources").isArray() || !tree.path("trims").isArray()) throw invalid();
        if (!tree.path("lastHistoryEventId").isNull() && !tree.path("lastHistoryEventId").isTextual()) throw invalid();
        for (var source : tree.path("sources")) {
            strings(source, "kind", "role", "sourceId", "sourceVersion", "provenanceId", "sourceSha256", "modelTextSha256");
            integers(source, "utf8Bytes");
            if (!source.path("observedAt").isNull() && !source.path("observedAt").isTextual()) throw invalid();
        }
        for (var trim : tree.path("trims")) {
            strings(trim, "kind", "sourceId", "sourceVersion", "reason", "beforeSha256", "afterSha256"); integers(trim, "droppedItems");
        }
    }
    private static void strings(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid();
        for (var field : fields) if (!object.path(field).isTextual()) throw invalid();
    }
    private static void integers(JsonNode object, String... fields) {
        if (!object.isObject()) throw invalid();
        for (var field : fields) if (!object.path(field).isIntegralNumber()) throw invalid();
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
    private static AgentLedgerException invalid() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Context provenance metadata is invalid");
    }
}
