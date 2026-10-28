package top.egon.cola.platform.tianji.process.server.execution;

import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Validates the primitive, bounded values admitted by a published model's input/output schema. */
public final class ProcessValues {
    private static final long MAX_INTEGER = 999_999_999_999_999L;

    private ProcessValues() { }

    public static Map<String, Object> inputs(Map<String, ValueType> schema, Map<String, Object> values) {
        return checked(schema, values, false);
    }

    public static Map<String, Object> outputs(Map<String, ValueType> schema, Map<String, Object> values) {
        return checked(schema, values, true);
    }

    private static Map<String, Object> checked(Map<String, ValueType> schema, Map<String, Object> values, boolean output) {
        if (values == null || !schema.keySet().equals(values.keySet())) throw invalid(output);
        Map<String, Object> checked = new LinkedHashMap<>();
        schema.forEach((key, type) -> {
            Object value = values.get(key);
            if (type == ValueType.STRING && value instanceof String text && safeText(text)) checked.put(key, text);
            else if (type == ValueType.BOOLEAN && value instanceof Boolean flag) checked.put(key, flag);
            else if (type == ValueType.INTEGER) checked.put(key, integer(value, output));
            else throw invalid(output);
        });
        return Map.copyOf(checked);
    }

    private static Long integer(Object value, boolean output) {
        try {
            long number;
            if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
                number = ((Number) value).longValue();
            } else if (value instanceof BigInteger integer) number = integer.longValueExact();
            else if (output && value instanceof BigDecimal decimal) number = decimal.longValueExact();
            else if (output && (value instanceof Float || value instanceof Double)) {
                double decimal = ((Number) value).doubleValue();
                if (!Double.isFinite(decimal) || decimal != Math.rint(decimal)
                        || decimal < -MAX_INTEGER || decimal > MAX_INTEGER) throw invalid(true);
                number = (long) decimal;
            } else throw invalid(output);
            if (number < -MAX_INTEGER || number > MAX_INTEGER) throw invalid(output);
            return number;
        } catch (ArithmeticException failure) {
            throw invalid(output);
        }
    }

    private static boolean safeText(String value) {
        if (value.length() > 1_024 || value.getBytes(StandardCharsets.UTF_8).length > 4_096) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\0' || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }

    private static RuntimeException invalid(boolean output) {
        return output ? new IllegalStateException("Process output does not match the release schema")
                : new IllegalArgumentException("Process input does not match the release schema");
    }
}
