package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.JsonBaseModel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Restricts session state to JSON values that ADK's mapper can restore without changing them. */
final class AdkSessionStateCodec {
    private static final int MAX_BYTES = 1_048_576;
    private static final ObjectMapper MAPPER = JsonBaseModel.getMapper().copy();
    private static final TypeReference<Map<String, Object>> OBJECT = new TypeReference<>() { };

    String encode(Object value) {
        try {
            AdkSessionEventCodec.validateJsonValue(value, 0, false);
            String json = MAPPER.writeValueAsString(value);
            requireSize(json);
            if (!Objects.equals(value, MAPPER.readValue(json, Object.class))) throw invalid();
            return json;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    Map<String, Object> decodeMap(String json) {
        try {
            requireSize(json);
            Map<String, Object> value = MAPPER.readValue(json, OBJECT);
            if (value == null || value.values().stream().anyMatch(Objects::isNull)) throw invalid();
            AdkSessionEventCodec.validateJsonValue(value, 0, false);
            return value;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    Object decodeValue(String json) {
        try {
            requireSize(json);
            Object value = MAPPER.readValue(json, Object.class);
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
