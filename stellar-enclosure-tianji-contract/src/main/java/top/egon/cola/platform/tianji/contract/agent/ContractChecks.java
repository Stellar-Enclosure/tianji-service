package top.egon.cola.platform.tianji.contract.agent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ContractChecks {
    private static final int MAX_JSON_DEPTH = 32;

    private ContractChecks() {
    }

    static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is required and must be at most " + maxLength + " characters");
        }
        return value.trim();
    }

    static String optional(String value, String name, int maxLength) {
        return value == null ? null : required(value, name, maxLength);
    }

    static String sessionId(String value) {
        String id = required(value, "sessionId", 128);
        for (int i = 0; i < id.length(); i++) {
            char character = id.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= id.length() || !Character.isLowSurrogate(id.charAt(++i)))) {
                throw new IllegalArgumentException("sessionId contains invalid characters");
            }
        }
        return id;
    }

    static Map<String, Object> jsonObject(Map<String, ?> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return copyObject(source, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static Map<String, Object> copyObject(Map<?, ?> source, int depth, Set<Object> ancestors) {
        enter(source, depth, ancestors);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings");
                }
                result.put(key, copyValue(entry.getValue(), depth + 1, ancestors));
            }
            return Collections.unmodifiableMap(result);
        } finally {
            ancestors.remove(source);
        }
    }

    private static Object copyValue(Object value, int depth, Set<Object> ancestors) {
        if (depth > MAX_JSON_DEPTH) {
            throw new IllegalArgumentException("JSON nesting exceeds the maximum depth");
        }
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return number;
        }
        if (value instanceof Float number && Float.isFinite(number)) {
            return number;
        }
        if (value instanceof Map<?, ?> map) {
            return copyObject(map, depth, ancestors);
        }
        if (value instanceof List<?> list) {
            enter(list, depth, ancestors);
            try {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    result.add(copyValue(item, depth + 1, ancestors));
                }
                return Collections.unmodifiableList(result);
            } finally {
                ancestors.remove(list);
            }
        }
        throw new IllegalArgumentException("value is not a JSON value");
    }

    private static void enter(Object value, int depth, Set<Object> ancestors) {
        if (depth > MAX_JSON_DEPTH || !ancestors.add(value)) {
            throw new IllegalArgumentException("JSON values must be acyclic and within the maximum depth");
        }
    }
}
