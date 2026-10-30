package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.google.genai.types.Content;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Canonical SDK JSON digests preserve numeric spelling and never serialize native tool implementations. */
final class AdkSemanticJsonDigest {
    private static final int MAX_BYTES = 16_777_216;
    private static final JsonFactory JSON = JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(64).maxStringLength(MAX_BYTES).maxNumberLength(MAX_BYTES).build()).build();

    static Value json(String json, boolean omitTransport) {
        var output = new Digester();
        try (var generator = JSON.createGenerator(output)) { write(generator, parse(json, omitTransport)); }
        catch (IOException failure) { throw invalid(); }
        return output.value();
    }

    static Value contents(List<Content> contents) {
        var output = new Digester();
        try (var generator = JSON.createGenerator(output)) {
            generator.writeStartArray();
            for (var content : contents) write(generator, parse(content.toJson(), false));
            generator.writeEndArray();
        } catch (IOException failure) { throw invalid(); }
        return output.value();
    }

    static Value text(String value) {
        var digest = new Digester();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); digest.write(bytes, 0, bytes.length);
        return digest.value();
    }

    static String fingerprint(String domain, List<String> fields) {
        var digest = new Digester();
        try (var output = new DataOutputStream(digest)) {
            for (var field : java.util.stream.Stream.concat(java.util.stream.Stream.of(domain), fields.stream()).toList()) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8); output.writeLong(bytes.length); output.write(bytes);
            }
        } catch (IOException failure) { throw invalid(); }
        return digest.value().sha256();
    }

    private static Object parse(String value, boolean omitTransport) throws IOException {
        if (value == null || value.length() > MAX_BYTES || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw tooLarge();
        try (var parser = JSON.createParser(value)) {
            if (parser.nextToken() == null) throw invalid();
            var result = read(parser, 0, new int[1], omitTransport);
            if (parser.nextToken() != null) throw invalid();
            return result;
        }
    }

    private static Object read(JsonParser parser, int depth, int[] nodes, boolean omitTransport) throws IOException {
        if (depth > 64 || ++nodes[0] > 200_000) throw tooLarge();
        JsonToken token = parser.currentToken();
        if (token == null) throw invalid();
        if (token == JsonToken.START_OBJECT) {
            Map<String, Object> object = new TreeMap<>((a, b) -> Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8)));
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw invalid();
                String key = parser.currentName(); parser.nextToken();
                if (omitTransport && depth == 0 && ("httpOptions".equals(key) || "http_options".equals(key))) parser.skipChildren();
                else object.put(key, read(parser, depth + 1, nodes, omitTransport));
            }
            return object;
        }
        if (token == JsonToken.START_ARRAY) {
            List<Object> values = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) values.add(read(parser, depth + 1, nodes, omitTransport));
            return values;
        }
        return switch (token) {
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new Numeric(parser.getText());
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            case VALUE_NULL -> null;
            default -> throw invalid();
        };
    }

    private static void write(JsonGenerator output, Object value) throws IOException {
        if (value == null) output.writeNull();
        else if (value instanceof String text) output.writeString(text);
        else if (value instanceof Boolean bool) output.writeBoolean(bool);
        else if (value instanceof Numeric number) output.writeNumber(number.text());
        else if (value instanceof List<?> list) {
            output.writeStartArray(); for (var item : list) write(output, item); output.writeEndArray();
        } else if (value instanceof Map<?, ?> map) {
            output.writeStartObject();
            for (var entry : map.entrySet()) { output.writeFieldName((String) entry.getKey()); write(output, entry.getValue()); }
            output.writeEndObject();
        } else throw invalid();
    }

    record Value(String sha256, long utf8Bytes) { }
    private record Numeric(String text) { }
    private static final class Digester extends OutputStream {
        private final MessageDigest digest;
        private long bytes;
        Digester() {
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
        }
        @Override public void write(int value) { limit(1); digest.update((byte) value); }
        @Override public void write(byte[] value, int offset, int length) { limit(length); digest.update(value, offset, length); }
        private void limit(int amount) { bytes += amount; if (bytes > MAX_BYTES) throw tooLarge(); }
        Value value() { return new Value(HexFormat.of().formatHex(digest.digest()), bytes); }
    }
    private static AgentLedgerException invalid() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Native model context projection is invalid"); }
    private static AgentLedgerException tooLarge() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Native model context projection exceeds its limit"); }
}
