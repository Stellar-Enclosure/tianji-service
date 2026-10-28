package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.JsonBaseModel;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Explicit value tags preserve numeric types without enabling polymorphic deserialization. */
final class AdkTypedValueCodec {
    private static final ObjectMapper MAPPER = JsonBaseModel.getMapper();
    private static final int MAX_NUMBER_CHARS = 4096;

    JsonNode encode(Object value) {
        AdkSessionEventCodec.validateJsonValue(value, 0, false);
        return encode(value, 0);
    }

    Object decode(JsonNode value) {
        Object decoded = decode(value, 0);
        AdkSessionEventCodec.validateJsonValue(decoded, 0, false);
        return decoded;
    }

    private JsonNode encode(Object value, int depth) {
        if (depth > 64) throw invalid();
        ArrayNode encoded = MAPPER.createArrayNode();
        if (value == null) return encoded.add("null");
        if (value instanceof String text) return encoded.add("string").add(text);
        if (value instanceof Boolean flag) return encoded.add("boolean").add(flag);
        if (value instanceof Number number) {
            String kind = switch (number) {
                case Byte ignored -> "byte";
                case Short ignored -> "short";
                case Integer ignored -> "integer";
                case Long ignored -> "long";
                case Float ignored -> "float";
                case Double ignored -> "double";
                case BigInteger ignored -> "bigInteger";
                case BigDecimal ignored -> "bigDecimal";
                default -> throw invalid();
            };
            String text = number.toString();
            if (text.length() > MAX_NUMBER_CHARS) throw invalid();
            return encoded.add(kind).add(text);
        }
        if (value instanceof Map<?, ?> map) {
            ObjectNode entries = encoded.add("map").addObject();
            map.forEach((key, item) -> entries.set((String) key, encode(item, depth + 1)));
            return encoded;
        }
        if (value instanceof List<?> list) {
            ArrayNode entries = encoded.add("list").addArray();
            list.forEach(item -> entries.add(encode(item, depth + 1)));
            return encoded;
        }
        throw invalid();
    }

    private Object decode(JsonNode value, int depth) {
        if (depth > 64 || !(value instanceof ArrayNode pair) || pair.isEmpty()
                || !pair.get(0).isTextual()) throw invalid();
        String kind = pair.get(0).textValue();
        if ("null".equals(kind)) {
            if (pair.size() != 1) throw invalid();
            return null;
        }
        if (pair.size() != 2) throw invalid();
        JsonNode payload = pair.get(1);
        if ("string".equals(kind)) {
            if (!payload.isTextual()) throw invalid();
            return payload.textValue();
        }
        if ("boolean".equals(kind)) {
            if (!payload.isBoolean()) throw invalid();
            return payload.booleanValue();
        }
        if ("map".equals(kind)) {
            if (!(payload instanceof ObjectNode object)) throw invalid();
            Map<String, Object> result = new HashMap<>();
            object.fields().forEachRemaining(entry -> result.put(entry.getKey(), decode(entry.getValue(), depth + 1)));
            return result;
        }
        if ("list".equals(kind)) {
            if (!(payload instanceof ArrayNode array)) throw invalid();
            List<Object> result = new ArrayList<>(array.size());
            array.forEach(item -> result.add(decode(item, depth + 1)));
            return result;
        }
        if (!payload.isTextual() || payload.textValue().length() > MAX_NUMBER_CHARS) throw invalid();
        String text = payload.textValue();
        Number number = switch (kind) {
            case "byte" -> Byte.valueOf(text);
            case "short" -> Short.valueOf(text);
            case "integer" -> Integer.valueOf(text);
            case "long" -> Long.valueOf(text);
            case "float" -> Float.valueOf(text);
            case "double" -> Double.valueOf(text);
            case "bigInteger" -> new BigInteger(text);
            case "bigDecimal" -> new BigDecimal(text);
            default -> throw invalid();
        };
        if (!number.toString().equals(text)
                || (number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) throw invalid();
        return number;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("ADK session value is invalid or cannot be stored safely");
    }
}
