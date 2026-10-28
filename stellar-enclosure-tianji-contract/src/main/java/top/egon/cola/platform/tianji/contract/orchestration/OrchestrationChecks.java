package top.egon.cola.platform.tianji.contract.orchestration;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class OrchestrationChecks {
    private static final long MAX_INTEGER = 999_999_999_999_999L;
    private static final Set<String> RESERVED = Set.of("true", "false", "null", "empty", "and", "or", "not", "div", "mod",
            "eq", "ne", "lt", "gt", "le", "ge", "instanceof", "execution", "task", "authenticatedUserId");

    private OrchestrationChecks() { }

    static String release(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
        return value;
    }

    static String definition(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw invalid();
        return value;
    }

    static String command(String value) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
        return value;
    }

    static String id(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())
                || !safeText(value, false)) throw invalid();
        return value;
    }

    static String xml(String value) {
        if (value == null || value.isEmpty() || value.length() > 512 * 1024 || !safeText(value, true)
                || value.getBytes(StandardCharsets.UTF_8).length > 512 * 1024) throw invalid();
        return value;
    }

    static String hash(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid();
        return value;
    }

    static Map<String, ProcessValueTypeEnum> schema(Map<String, ProcessValueTypeEnum> input) {
        if (input == null || input.size() > 16) throw invalid();
        Map<String, ProcessValueTypeEnum> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            variable(key);
            if (value == null) throw invalid();
            result.put(key, value);
        });
        return Map.copyOf(result);
    }

    static Map<String, Object> values(Map<String, Object> input) {
        if (input == null || input.size() > 16) throw invalid();
        Map<String, Object> result = new LinkedHashMap<>();
        input.forEach((key, value) -> {
            variable(key);
            if (value instanceof String text && text.length() <= 1_024 && safeText(text, true)
                    && text.getBytes(StandardCharsets.UTF_8).length <= 4_096) result.put(key, text);
            else if (value instanceof Boolean flag) result.put(key, flag);
            else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                result.put(key, integer(((Number) value).longValue()));
            } else if (value instanceof BigInteger number) {
                try { result.put(key, integer(number.longValueExact())); }
                catch (ArithmeticException failure) { throw invalid(); }
            } else throw invalid();
        });
        return Map.copyOf(result);
    }

    private static long integer(long value) {
        if (value < -MAX_INTEGER || value > MAX_INTEGER) throw invalid();
        return value;
    }

    private static void variable(String value) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") || RESERVED.contains(value)) throw invalid();
    }

    private static boolean safeText(String value, boolean allowControls) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == 0 || !allowControls && (character < 32 || character == 127)
                    || Character.isLowSurrogate(character) || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }

    static IllegalArgumentException invalid() { return new IllegalArgumentException("Orchestration contract value is invalid"); }
}
