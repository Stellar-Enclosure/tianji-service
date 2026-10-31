package top.egon.cola.platform.tianji.agent.domain.tool;

import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Bounded v1 tool object schema. Identity, targets and approval authority belong to the trusted execution context, not these values. */
public record ToolSchema(Map<String, ValueType> fields, Set<String> required) {
    private static final long MAX_INTEGER = 999_999_999_999_999L;
    public enum ValueType { STRING, INTEGER, BOOLEAN }

    public ToolSchema {
        fields = Map.copyOf(fields); required = Set.copyOf(required);
        if (fields.size() > 16 || !fields.keySet().containsAll(required)) throw invalid();
        for (var name : fields.keySet()) if (!name.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) throw invalid();
    }

    public Map<String, Object> values(Map<String, Object> supplied) {
        if (supplied == null || !fields.keySet().containsAll(supplied.keySet()) || !supplied.keySet().containsAll(required)) throw invalid();
        var result = new LinkedHashMap<String, Object>();
        for (var entry : supplied.entrySet()) {
            Object value = entry.getValue();
            switch (fields.get(entry.getKey())) {
                case STRING -> { if (!(value instanceof String text) || text.length() > 1024 || text.indexOf(0) >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(text) || text.getBytes(StandardCharsets.UTF_8).length > 4096) throw invalid(); result.put(entry.getKey(), text); }
                case BOOLEAN -> { if (!(value instanceof Boolean)) throw invalid(); result.put(entry.getKey(), value); }
                case INTEGER -> {
                    long number;
                    if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) number = ((Number) value).longValue();
                    else if (value instanceof BigInteger integer) { try { number = integer.longValueExact(); } catch (ArithmeticException tooLarge) { throw invalid(); } }
                    else throw invalid();
                    if (number < -MAX_INTEGER || number > MAX_INTEGER) throw invalid(); result.put(entry.getKey(), number);
                }
            }
        }
        return Map.copyOf(result);
    }

    void writeTo(DataOutputStream output) throws IOException {
        output.writeInt(fields.size());
        for (var entry : new TreeMap<>(fields).entrySet()) { text(output, entry.getKey()); text(output, entry.getValue().name()); output.writeBoolean(required.contains(entry.getKey())); }
    }
    static void text(DataOutputStream output, String value) throws IOException { byte[] bytes = value.getBytes(StandardCharsets.UTF_8); output.writeInt(bytes.length); output.write(bytes); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool value does not match its fixed schema"); }
}
