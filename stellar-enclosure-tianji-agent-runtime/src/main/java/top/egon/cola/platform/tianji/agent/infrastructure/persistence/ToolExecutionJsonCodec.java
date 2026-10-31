package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Closed, bounded persisted tool records. Parse failures never echo private arguments or downstream output. */
final class ToolExecutionJsonCodec {
    static final int MAX_BYTES = 128 * 1024;
    private static final TypeReference<Map<String, Object>> VALUES = new TypeReference<>() { };
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(MAX_BYTES).maxNameLength(128).maxNumberLength(32).build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.USE_LONG_FOR_INTS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();

    String descriptor(ToolDescriptor value) {
        try { return write(Objects.requireNonNull(value, "descriptor")); }
        catch (RuntimeException malformed) { throw invalid(); }
    }
    ToolDescriptor descriptor(String json, DefinitionResourceRef reference) {
        try {
            var value = mapper.readValue(bytes(json), ToolDescriptor.class);
            if (value == null || !value.reference().equals(reference)) throw invalid(); return value;
        } catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }
    String arguments(ToolDescriptor descriptor, Map<String, Object> values) {
        try { return write(descriptor.input().values(values)); }
        catch (RuntimeException malformed) { throw invalid(); }
    }
    Map<String, Object> arguments(String json, ToolDescriptor descriptor) {
        try { return descriptor.input().values(mapper.readValue(bytes(json), VALUES)); }
        catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }
    String outcome(ToolDescriptor descriptor, ToolExecutionOutcome value) {
        try { return write(value.validated(descriptor)); }
        catch (RuntimeException malformed) { throw invalid(); }
    }
    ToolExecutionOutcome outcome(String json, ToolDescriptor descriptor) {
        try { var value = mapper.readValue(bytes(json), ToolExecutionOutcome.class); if (value == null) throw invalid(); return value.validated(descriptor); }
        catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }
    private String write(Object value) {
        try { String json = mapper.writeValueAsString(value); bytes(json); return json; }
        catch (RuntimeException | java.io.IOException failure) { throw invalid(); }
    }
    private static byte[] bytes(String json) {
        if (json == null || !StandardCharsets.UTF_8.newEncoder().canEncode(json)) throw invalid(); byte[] bytes = json.getBytes(StandardCharsets.UTF_8); if (bytes.length > MAX_BYTES) throw invalid(); return bytes;
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Stored tool execution content is invalid or changed"); }
}
