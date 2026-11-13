package top.egon.cola.platform.tianji.contract.orchestration.automation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Canonical bytes for decoded v2 values. Raw token validation belongs to the transport parser. */
public final class ProcessTypedJson {

    public static final long V2_MAX_WIRE_INTEGER = 999_999_999_999_999L;

    private static final int MAX_GENERIC_BYTES = 32 * 1024 * 1024;
    private static final int MAX_GENERIC_DEPTH = 32;
    private static final int MAX_TYPED_DEPTH = 8;
    private static final int MAX_TYPED_ARRAY = 256;
    private static final int MAX_START_VARIABLE_BYTES = 64 * 1024;
    private static final int MAX_START_OVERRIDE_BYTES = 256 * 1024;
    private static final String START_DOMAIN = "AUTOMATION_START_REQUEST_V2";
    private static final String START_OVERRIDES = "agentTaskOverrides";
    private static final Set<String> START_REQUIRED = Set.of("definitionKey", "businessKey", "commandId", "correlationId", "variables");
    private static final Set<String> TEMPLATE_FIELDS = Set.of("key", "version", "contentSha256");
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ProcessTypedJson() { }

    public static byte[] canonicalBytes(Object value) {
        return canonicalBytes(value, MAX_GENERIC_BYTES);
    }

    public static byte[] canonicalBytes(Object value, int maxBytes) {
        return encode(value, maxBytes, MAX_GENERIC_DEPTH, false).bytes();
    }

    /** Validates wire-safe integral leaves without reducing the generic host identity range. */
    public static void validateTypedIntegerLeaves(Object value) {
        encode(value, MAX_GENERIC_BYTES, MAX_TYPED_DEPTH, true);
    }

    public static String sha256(String domain, byte[]... segments) {
        if (domain == null || domain.isEmpty() || segments == null) {
            throw invalid();
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
        update(digest, utf8(domain));
        for (byte[] segment : segments) {
            if (segment == null) {
                throw invalid();
            }
            update(digest, segment);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Hashes the exact intent before release selection or schema-based normalization.
     * The public host rejects reserved event command IDs before invoking this shared algorithm.
     */
    public static String startRequestSha256(Map<String, Object> originalBody) {
        Map<String, Object> body = object(originalBody);
        if (!body.keySet().containsAll(START_REQUIRED)
                || body.keySet().stream().anyMatch(key -> !START_REQUIRED.contains(key) && !START_OVERRIDES.equals(key))) {
            throw invalid();
        }
        String definitionKey = requireId(body.get("definitionKey"));
        String businessKey = requireBusinessKey(body.get("businessKey"));
        requireId(body.get("commandId"));
        String correlationId = requireId(body.get("correlationId"));
        var variables = object(body.get("variables"));
        var overrides = body.containsKey(START_OVERRIDES) ? object(body.get(START_OVERRIDES)) : Map.<String, Object>of();
        validateOverrides(overrides);
        return sha256(START_DOMAIN, utf8(definitionKey), utf8(businessKey), utf8(correlationId),
                encode(variables, MAX_START_VARIABLE_BYTES, MAX_TYPED_DEPTH, true).bytes(),
                encode(overrides, MAX_START_OVERRIDE_BYTES, MAX_TYPED_DEPTH + 2, true).bytes());
    }

    private static void validateOverrides(Map<String, Object> overrides) {
        if (overrides.size() > 128) {
            throw invalid();
        }
        for (var entry : overrides.entrySet()) {
            requireId(entry.getKey());
            Map<String, Object> value = object(entry.getValue());
            if (value.isEmpty() || value.keySet().stream().anyMatch(key -> !"templateRef".equals(key) && !"parameters".equals(key))) {
                throw invalid();
            }
            if (value.containsKey("templateRef")) {
                var template = object(value.get("templateRef"));
                if (!TEMPLATE_FIELDS.equals(template.keySet()) || !(template.get("contentSha256") instanceof String hash)
                        || !isSha256(hash)) {
                    throw invalid();
                }
                requireId(template.get("key"));
                long version = exactInteger(template.get("version"));
                if (version < 1 || version > V2_MAX_WIRE_INTEGER) {
                    throw invalid();
                }
            }
            if (value.containsKey("parameters")) {
                encode(object(value.get("parameters")), MAX_START_OVERRIDE_BYTES, MAX_TYPED_DEPTH, true);
            }
        }
    }

    private static String requireBusinessKey(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.length() > 255 || !text.equals(text.strip())
                || text.chars().anyMatch(Character::isISOControl)) {
            throw invalid();
        }
        return text;
    }

    private static String requireId(Object value) {
        if (!(value instanceof String text) || !isId(text)) {
            throw invalid();
        }
        return text;
    }

    static boolean isId(String value) {
        return value != null && ID.matcher(value).matches();
    }

    static boolean isSha256(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    static Map<String, Object> immutableObject(Map<String, Object> value, int maxBytes) {
        return object(immutableValue(value, maxBytes));
    }

    static Object immutableValue(Object value, int maxBytes) {
        Object frozen = freeze(value, 0, new CopyBudget(maxBytes));
        encode(frozen, maxBytes, MAX_TYPED_DEPTH, true);
        return frozen;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source) || source.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw invalid();
        }
        return (Map<String, Object>) source;
    }

    static long exactInteger(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue();
        }
        if (value instanceof BigInteger integer) {
            try {
                return integer.longValueExact();
            } catch (ArithmeticException overflow) {
                throw invalid();
            }
        }
        throw invalid();
    }

    static int utf8Length(String value, int maximumBytes) {
        if (value == null || value.length() > maximumBytes) {
            throw invalid();
        }
        int bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == 0 || Character.isLowSurrogate(character)) {
                throw invalid();
            }
            if (Character.isHighSurrogate(character)) {
                if (++index == value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw invalid();
                }
                bytes += 4;
            } else {
                bytes += character < 0x80 ? 1 : character < 0x800 ? 2 : 3;
            }
            if (bytes > maximumBytes) {
                throw invalid();
            }
        }
        return bytes;
    }

    private static byte[] utf8(String value) {
        utf8Length(value, MAX_GENERIC_BYTES);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        int length = bytes.length;
        digest.update((byte) (length >>> 24));
        digest.update((byte) (length >>> 16));
        digest.update((byte) (length >>> 8));
        digest.update((byte) length);
        digest.update(bytes);
    }

    private static Object freeze(Object value, int depth, CopyBudget budget) {
        if (depth > MAX_TYPED_DEPTH) {
            throw invalid();
        }
        budget.take(1);
        if (value instanceof String text) {
            budget.take(utf8Length(text, budget.remaining));
            return text;
        }
        if (value instanceof Map<?, ?> source) {
            var copy = new LinkedHashMap<String, Object>();
            for (var entry : source.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw invalid();
                }
                budget.take(1 + utf8Length(key, budget.remaining));
                copy.put(key, freeze(entry.getValue(), depth + 1, budget));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            if (list.size() > MAX_TYPED_ARRAY) {
                throw invalid();
            }
            var copy = new ArrayList<Object>(list.size());
            for (Object item : list) {
                if (copy.size() == MAX_TYPED_ARRAY) {
                    throw invalid();
                }
                copy.add(freeze(item, depth + 1, budget));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof Boolean) {
            return value;
        }
        long integer = exactInteger(value);
        if (integer < -V2_MAX_WIRE_INTEGER || integer > V2_MAX_WIRE_INTEGER) {
            throw invalid();
        }
        return integer;
    }

    private static JsonByteWriter encode(Object value, int maxBytes, int maxDepth, boolean typed) {
        if (maxBytes < 1 || maxBytes > MAX_GENERIC_BYTES) {
            throw invalid();
        }
        var writer = new JsonByteWriter(maxBytes, maxDepth, typed);
        writer.write(value, 0);
        return writer;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid automation typed JSON");
    }

    private static final class CopyBudget {
        private int remaining;

        private CopyBudget(int remaining) {
            this.remaining = remaining;
        }

        private void take(int count) {
            if (count > remaining) {
                throw invalid();
            }
            remaining -= count;
        }
    }

    private static final class JsonByteWriter {

        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final int maxBytes;
        private final int maxDepth;
        private final boolean typed;

        private JsonByteWriter(int maxBytes, int maxDepth, boolean typed) {
            this.maxBytes = maxBytes;
            this.maxDepth = maxDepth;
            this.typed = typed;
        }

        private void write(Object value, int depth) {
            if (depth > maxDepth) {
                throw invalid();
            }
            switch (value) {
                case null -> ascii("null");
                case Boolean flag -> ascii(flag ? "true" : "false");
                case String text -> string(text);
                case Map<?, ?> ignored -> writeObject(object(value), depth);
                case List<?> array -> writeArray(array, depth);
                case Number number -> {
                    long integer = exactInteger(number);
                    if (typed && (integer < -V2_MAX_WIRE_INTEGER || integer > V2_MAX_WIRE_INTEGER)) {
                        throw invalid();
                    }
                    ascii(Long.toString(integer));
                }
                default -> throw invalid();
            }
        }

        private void writeObject(Map<String, Object> object, int depth) {
            if (object.size() > remaining() / 4) {
                throw invalid();
            }
            var keys = new ArrayList<>(object.keySet());
            keys.sort(String::compareTo);
            append('{');
            for (int index = 0; index < keys.size(); index++) {
                if (index > 0) {
                    append(',');
                }
                String key = keys.get(index);
                string(key);
                append(':');
                write(object.get(key), depth + 1);
            }
            append('}');
        }

        private void writeArray(List<?> array, int depth) {
            if (array.size() > remaining() || typed && array.size() > MAX_TYPED_ARRAY) {
                throw invalid();
            }
            append('[');
            boolean first = true;
            for (Object item : array) {
                if (!first) {
                    append(',');
                }
                write(item, depth + 1);
                first = false;
            }
            append(']');
        }

        private void string(String text) {
            utf8Length(text, remaining());
            append('"');
            for (int index = 0; index < text.length(); ) {
                int character = text.codePointAt(index);
                index += Character.charCount(character);
                switch (character) {
                    case '"' -> ascii("\\\"");
                    case '\\' -> ascii("\\\\");
                    case '\b' -> ascii("\\b");
                    case '\f' -> ascii("\\f");
                    case '\n' -> ascii("\\n");
                    case '\r' -> ascii("\\r");
                    case '\t' -> ascii("\\t");
                    default -> {
                        if (character < 0x20) {
                            ascii("\\u00");
                            append(HEX[character >>> 4]);
                            append(HEX[character & 15]);
                        } else {
                            utf8CodePoint(character);
                        }
                    }
                }
            }
            append('"');
        }

        private void utf8CodePoint(int value) {
            if (value < 0x80) {
                append(value);
            } else if (value < 0x800) {
                append(0xC0 | value >>> 6);
                append(0x80 | value & 0x3F);
            } else if (value < 0x10000) {
                append(0xE0 | value >>> 12);
                append(0x80 | value >>> 6 & 0x3F);
                append(0x80 | value & 0x3F);
            } else {
                append(0xF0 | value >>> 18);
                append(0x80 | value >>> 12 & 0x3F);
                append(0x80 | value >>> 6 & 0x3F);
                append(0x80 | value & 0x3F);
            }
        }

        private void ascii(String value) {
            for (int index = 0; index < value.length(); index++) {
                append(value.charAt(index));
            }
        }

        private void append(int value) {
            if (remaining() == 0) {
                throw invalid();
            }
            output.write(value);
        }

        private int remaining() {
            return maxBytes - output.size();
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
