package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** The closed, bounded schema subset used by automation releases. */
public record ProcessSchemaVO(
        @NotNull String type,
        Map<String, @Valid ProcessSchemaVO> properties,
        List<String> required,
        Boolean additionalProperties,
        @Valid ProcessSchemaVO items,
        Integer maxItems,
        List<String> enumValues,
        Integer maxLength,
        String pattern,
        String format,
        Long minimum,
        Long maximum) {

    private static final int MAX_SCHEMA_BYTES = 32 * 1024;
    private static final int MAX_VALUE_BYTES = 64 * 1024;
    private static final int MAX_DEPTH = 8;
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern DATE_TIME = Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z");
    private static final Pattern DECIMAL = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?");

    public ProcessSchemaVO {
        Set<String> allowed = keywords(type);
        Map<String, Object> wire = fields(type, properties, required, additionalProperties, items, maxItems,
                enumValues, maxLength, pattern, format, minimum, maximum);
        require(allowed.containsAll(wire.keySet()));
        if ("object".equals(type)) {
            require(properties != null && required != null && Boolean.FALSE.equals(additionalProperties));
            var frozen = new LinkedHashMap<String, ProcessSchemaVO>();
            for (var entry : properties.entrySet()) {
                require(entry.getKey() != null && entry.getValue() != null);
                frozen.put(entry.getKey(), entry.getValue());
            }
            properties = Collections.unmodifiableMap(frozen);
            required = copyStrings(required);
            require(properties.keySet().containsAll(required));
        }
        if ("array".equals(type)) {
            require(items != null && maxItems != null && maxItems >= 0 && maxItems <= 256);
        }
        if ("string".equals(type)) {
            if (enumValues != null) {
                require(!enumValues.isEmpty());
                enumValues = copyStrings(enumValues);
            }
            require(maxLength == null || maxLength >= 0);
            if (pattern != null) {
                PortablePattern.parse(pattern);
            }
            if (format != null && !"date".equals(format) && !"date-time".equals(format)) {
                scale(format);
            }
        }
        if ("integer".equals(type)) {
            require(minimum == null || inRange(minimum));
            require(maximum == null || inRange(maximum));
            require(minimum == null || maximum == null || minimum <= maximum);
        }
        if (items != null) {
            items.requireDepth(1);
        }
        if (properties != null) {
            for (ProcessSchemaVO property : properties.values()) {
                property.requireDepth(1);
            }
        }
        ProcessTypedJson.canonicalBytes(fields(type, properties, required, additionalProperties, items, maxItems,
                enumValues, maxLength, pattern, format, minimum, maximum), MAX_SCHEMA_BYTES);
    }

    /** Receives a JSON object already decoded by the host's strict Jackson parser. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ProcessSchemaVO fromWire(Map<String, Object> wire) {
        ProcessTypedJson.canonicalBytes(ProcessTypedJson.object(wire), MAX_SCHEMA_BYTES);
        return read(wire, 0);
    }

    private static ProcessSchemaVO read(Map<String, Object> wire, int depth) {
        require(wire != null && depth <= MAX_DEPTH);
        String type = value(wire, "type", String.class);
        require(keywords(type).containsAll(wire.keySet()) && wire.values().stream().noneMatch(Objects::isNull));
        var properties = new LinkedHashMap<String, ProcessSchemaVO>();
        if (wire.containsKey("properties")) {
            for (var entry : ProcessTypedJson.object(wire.get("properties")).entrySet()) {
                properties.put(entry.getKey(), read(ProcessTypedJson.object(entry.getValue()), depth + 1));
            }
        }
        ProcessSchemaVO items = wire.containsKey("items") ? read(ProcessTypedJson.object(wire.get("items")), depth + 1) : null;
        return new ProcessSchemaVO(type, wire.containsKey("properties") ? properties : null, strings(wire, "required"),
                value(wire, "additionalProperties", Boolean.class), items, intValue(wire, "maxItems"), strings(wire, "enum"),
                intValue(wire, "maxLength"), value(wire, "pattern", String.class), value(wire, "format", String.class),
                longValue(wire, "minimum"), longValue(wire, "maximum"));
    }

    @JsonValue
    public Map<String, Object> toWire() {
        return Collections.unmodifiableMap(fields(type, properties, required, additionalProperties, items, maxItems,
                enumValues, maxLength, pattern, format, minimum, maximum));
    }

    public void validateValue(Object value) {
        normalizeValue(value);
    }

    /** Returns the immutable, schema-normalized representation used for resolved digests. */
    public Object normalizeValue(Object value) {
        Object frozen = ProcessTypedJson.immutableValue(value, MAX_VALUE_BYTES);
        Object normalized = normalize(frozen, 0);
        ProcessTypedJson.canonicalBytes(normalized, MAX_VALUE_BYTES);
        return normalized;
    }

    private Object normalize(Object value, int depth) {
        require(depth <= MAX_DEPTH);
        return switch (type) {
            case "object" -> normalizeObject(ProcessTypedJson.object(value), depth);
            case "array" -> normalizeArray(value, depth);
            case "string" -> normalizeString(value);
            case "integer" -> {
                long integer = ProcessTypedJson.exactInteger(value);
                require(inRange(integer) && (minimum == null || integer >= minimum) && (maximum == null || integer <= maximum));
                yield integer;
            }
            case "boolean" -> {
                require(value instanceof Boolean);
                yield value;
            }
            case "null" -> {
                require(value == null);
                yield null;
            }
            default -> throw invalid();
        };
    }

    private Map<String, Object> normalizeObject(Map<String, Object> value, int depth) {
        require(value.keySet().containsAll(required) && properties.keySet().containsAll(value.keySet()));
        var result = new LinkedHashMap<String, Object>();
        value.forEach((key, item) -> result.put(key, properties.get(key).normalize(item, depth + 1)));
        return Collections.unmodifiableMap(result);
    }

    private List<Object> normalizeArray(Object value, int depth) {
        require(value instanceof List<?>);
        var list = (List<?>) value;
        require(list.size() <= maxItems);
        var result = new ArrayList<Object>(list.size());
        for (Object item : list) {
            result.add(items.normalize(item, depth + 1));
        }
        return Collections.unmodifiableList(result);
    }

    private String normalizeString(Object value) {
        require(value instanceof String);
        String text = (String) value;
        requireStringConstraints(text);
        if (format == null) {
            return text;
        }
        String normalized = normalizeFormat(text);
        if (!normalized.equals(text)) {
            requireStringConstraints(normalized);
        }
        return normalized;
    }

    private void requireStringConstraints(String text) {
        require(maxLength == null || text.codePointCount(0, text.length()) <= maxLength);
        require(enumValues == null || enumValues.contains(text));
        require(pattern == null || PortablePattern.parse(pattern).matches(text));
    }

    private String normalizeFormat(String text) {
        try {
            if ("date".equals(format)) {
                require(DATE.matcher(text).matches());
                LocalDate.parse(text);
                return text;
            }
            if ("date-time".equals(format)) {
                require(DATE_TIME.matcher(text).matches());
                LocalDateTime.parse(text.substring(0, text.length() - 1), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return text;
            }
            int scale = scale(format);
            int dot = text.indexOf('.');
            require(DECIMAL.matcher(text).matches() && (dot < 0 || text.length() - dot - 1 <= scale));
            return new BigDecimal(text).setScale(scale).toPlainString();
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
    }

    private void requireDepth(int depth) {
        require(depth <= MAX_DEPTH);
        if (items != null) {
            items.requireDepth(depth + 1);
        }
        if (properties != null) {
            for (ProcessSchemaVO property : properties.values()) {
                property.requireDepth(depth + 1);
            }
        }
    }

    private static Set<String> keywords(String type) {
        require(type != null);
        return switch (type) {
            case "object" -> Set.of("type", "properties", "required", "additionalProperties");
            case "array" -> Set.of("type", "items", "maxItems");
            case "string" -> Set.of("type", "enum", "maxLength", "pattern", "format");
            case "integer" -> Set.of("type", "minimum", "maximum");
            case "boolean", "null" -> Set.of("type");
            default -> throw invalid();
        };
    }

    private static Map<String, Object> fields(String type, Map<String, ProcessSchemaVO> properties, List<String> required,
                                              Boolean additionalProperties, ProcessSchemaVO items, Integer maxItems,
                                              List<String> enumValues, Integer maxLength, String pattern, String format,
                                              Long minimum, Long maximum) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("type", type);
        if (properties != null) {
            var nested = new LinkedHashMap<String, Object>();
            properties.forEach((name, schema) -> {
                require(schema != null);
                nested.put(name, schema.toWire());
            });
            fields.put("properties", Collections.unmodifiableMap(nested));
        }
        put(fields, "required", required);
        put(fields, "additionalProperties", additionalProperties);
        put(fields, "items", items == null ? null : items.toWire());
        put(fields, "maxItems", maxItems);
        put(fields, "enum", enumValues);
        put(fields, "maxLength", maxLength);
        put(fields, "pattern", pattern);
        put(fields, "format", format);
        put(fields, "minimum", minimum);
        put(fields, "maximum", maximum);
        return fields;
    }

    private static void put(Map<String, Object> fields, String key, Object value) {
        if (value != null) {
            fields.put(key, value);
        }
    }

    private static <T> T value(Map<String, Object> wire, String key, Class<T> type) {
        Object value = wire.get(key);
        require(value == null || type.isInstance(value));
        return type.cast(value);
    }

    private static List<String> strings(Map<String, Object> wire, String key) {
        if (!wire.containsKey(key)) {
            return null;
        }
        require(wire.get(key) instanceof List<?>);
        var result = new ArrayList<String>();
        for (Object item : (List<?>) wire.get(key)) {
            require(item instanceof String);
            result.add((String) item);
        }
        return result;
    }

    private static List<String> copyStrings(List<String> source) {
        var seen = new HashSet<String>();
        for (String value : source) {
            require(value != null && seen.add(value));
        }
        return List.copyOf(source);
    }

    private static Long longValue(Map<String, Object> wire, String key) {
        return wire.containsKey(key) ? ProcessTypedJson.exactInteger(wire.get(key)) : null;
    }

    private static Integer intValue(Map<String, Object> wire, String key) {
        Long value = longValue(wire, key);
        require(value == null || value >= 0 && value <= Integer.MAX_VALUE);
        return value == null ? null : value.intValue();
    }

    private static int scale(String format) {
        require(format.matches("decimal:(?:[0-9]|1[0-8])"));
        return Integer.parseInt(format.substring("decimal:".length()));
    }

    private static boolean inRange(long value) {
        return value >= -ProcessTypedJson.V2_MAX_WIRE_INTEGER && value <= ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation schema or value");
    }

    private record PortablePattern(boolean startAnchored, boolean endAnchored, List<PatternAtomVO> atoms) {

        private static PortablePattern parse(String expression) {
            require(expression.length() <= 128 && expression.chars().allMatch(character -> character >= 32 && character < 127));
            return new PatternReader(expression).read();
        }

        private boolean matches(String text) {
            int length = text.length();
            var positions = new BitSet(length + 1);
            positions.set(0, startAnchored ? 1 : length + 1);
            for (PatternAtomVO atom : atoms) {
                var next = new BitSet(length + 1);
                int run = 0;
                int mostRecentReachable = -1;
                for (int end = 0; end <= length; end++) {
                    run = end > 0 && atom.accepted.get(text.charAt(end - 1)) ? run + 1 : 0;
                    int latestStart = end - atom.minimum;
                    if (latestStart >= 0 && positions.get(latestStart)) {
                        mostRecentReachable = latestStart;
                    }
                    int earliestStart = end - Math.min(run, atom.maximum);
                    if (mostRecentReachable >= earliestStart) {
                        next.set(end);
                    }
                }
                positions = next;
            }
            return endAnchored ? positions.get(length) : !positions.isEmpty();
        }
    }

    private record PatternAtomVO(BitSet accepted, int minimum, int maximum) {
        private PatternAtomVO {
            accepted = (BitSet) accepted.clone();
        }
    }

    private static final class PatternReader {

        private static final String METACHARACTERS = "\\.^$|?*+()[]{}-/";
        private final String expression;
        private int offset;

        private PatternReader(String expression) {
            this.expression = expression;
        }

        private PortablePattern read() {
            boolean start = take('^');
            var atoms = new ArrayList<PatternAtomVO>();
            int width = 0;
            while (offset < expression.length() && expression.charAt(offset) != '$') {
                BitSet accepted = take('[') ? characterClass() : literalSet(readLiteral(false));
                int min = 1;
                int max = 1;
                if (take('?')) {
                    min = 0;
                } else if (take('{')) {
                    min = count();
                    max = take(',') ? count() : min;
                    require(min <= max && take('}'));
                }
                width += max;
                require(width <= 128);
                atoms.add(new PatternAtomVO(accepted, min, max));
            }
            boolean end = take('$');
            require(offset == expression.length());
            return new PortablePattern(start, end, List.copyOf(atoms));
        }

        private BitSet characterClass() {
            var accepted = new BitSet(128);
            while (offset < expression.length() && expression.charAt(offset) != ']') {
                char first = readLiteral(true);
                boolean range = offset + 1 < expression.length() && expression.charAt(offset) == '-'
                        && expression.charAt(offset + 1) != ']';
                if (range) {
                    offset++;
                    char last = readLiteral(true);
                    require(first <= last);
                    accepted.set(first, last + 1);
                } else {
                    accepted.set(first);
                }
            }
            require(take(']') && !accepted.isEmpty());
            return accepted;
        }

        private char readLiteral(boolean insideClass) {
            require(offset < expression.length());
            char current = expression.charAt(offset++);
            if (current == '\\') {
                require(offset < expression.length());
                char escaped = expression.charAt(offset++);
                require(METACHARACTERS.indexOf(escaped) >= 0);
                return escaped;
            }
            require(insideClass ? "[]^".indexOf(current) < 0 : ".^$|?*+()[]{}".indexOf(current) < 0);
            return current;
        }

        private int count() {
            int start = offset;
            int value = 0;
            while (offset < expression.length()) {
                char current = expression.charAt(offset);
                if (current < '0' || current > '9') {
                    break;
                }
                value = value * 10 + current - '0';
                require(value <= 128);
                offset++;
            }
            require(offset > start && (offset == start + 1 || expression.charAt(start) != '0'));
            return value;
        }

        private boolean take(char expected) {
            if (offset < expression.length() && expression.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private static BitSet literalSet(char literal) {
            var result = new BitSet(128);
            result.set(literal);
            return result;
        }
    }
}
