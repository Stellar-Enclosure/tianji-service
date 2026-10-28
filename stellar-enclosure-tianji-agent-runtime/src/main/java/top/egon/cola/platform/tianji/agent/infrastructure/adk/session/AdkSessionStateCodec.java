package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Reads legacy JSON and, after coordinated rollout, writes explicitly typed JSON values. */
final class AdkSessionStateCodec {
    private static final int MAX_BYTES = 1_048_576;
    private static final ObjectMapper MAPPER = JsonBaseModel.getMapper().copy()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };
    private final AdkTypedValueCodec typed = new AdkTypedValueCodec();
    private final int writeVersion;

    AdkSessionStateCodec(boolean typedWrites) {
        this.writeVersion = typedWrites ? 2 : 1;
    }

    int writeVersion() { return writeVersion; }

    String encode(Object value) {
        try {
            AdkSessionEventCodec.validateJsonValue(value, 0, false);
            String json = MAPPER.writeValueAsString(writeVersion == 2 ? typed.encode(value) : value);
            requireSize(json);
            Object restored = writeVersion == 2 ? typed.decode(MAPPER.readTree(json)) : MAPPER.readValue(json, Object.class);
            if (!Objects.equals(value, restored)) throw invalid();
            return json;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    Map<String, Object> decodeMap(String json, int version) {
        try {
            requireSize(json);
            Object decoded = switch (version) {
                case 1 -> MAPPER.readValue(json, OBJECT);
                case 2 -> typed.decode(MAPPER.readTree(json));
                default -> throw invalid();
            };
            if (!(decoded instanceof Map<?, ?> map)) throw invalid();
            @SuppressWarnings("unchecked") Map<String, Object> value = (Map<String, Object>) map;
            if (value == null || value.values().stream().anyMatch(Objects::isNull)) throw invalid();
            AdkSessionEventCodec.validateJsonValue(value, 0, false);
            return value;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    Object decodeValue(String json, int version) {
        try {
            requireSize(json);
            Object value = switch (version) {
                case 1 -> MAPPER.readValue(json, Object.class);
                case 2 -> typed.decode(MAPPER.readTree(json));
                default -> throw invalid();
            };
            if (value == null) throw invalid();
            AdkSessionEventCodec.validateJsonValue(value, 0, false);
            return value;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    private static void requireSize(String json) {
        if (json == null || json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("ADK session state is invalid or cannot be stored safely");
    }
}
