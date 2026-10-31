package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Closed, bounded persisted tool records. Parse failures never echo private arguments or downstream output. */
final class ToolExecutionJsonCodec {
    static final int MAX_BYTES = 128 * 1024;
    static final int MAX_AUDIT_BYTES = 4 * 1024;
    private static final Set<String> AUDIT_FIELDS = Set.of("schemaVersion", "localCheckId", "boundary", "decision", "subjectType", "grantId",
            "authVersion", "catalogVersion", "policyVersion", "checkedAt", "expiresAt");
    private static final TypeReference<Map<String, Object>> VALUES = new TypeReference<>() { };
    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(MAX_BYTES).maxNameLength(128).maxNumberLength(32).build())
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                    DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS, DeserializationFeature.USE_LONG_FOR_INTS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
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
    String audit(ToolExecutionAuthorization.AuditVO value) {
        try { String json = write(Objects.requireNonNull(value, "audit")); audit(json); return json; }
        catch (RuntimeException malformed) { throw invalid(); }
    }
    ToolExecutionAuthorization.AuditVO audit(String json) {
        try {
            byte[] content = bytes(json); if (content.length > MAX_AUDIT_BYTES) throw invalid();
            var value = mapper.readTree(content);
            if (value == null || !value.isObject() || value.size() != AUDIT_FIELDS.size()) throw invalid();
            for (var field : AUDIT_FIELDS) if (!value.has(field)) throw invalid();
            if (!Long.valueOf(1).equals(auditLong(value.get("schemaVersion")))) throw invalid();
            String checkId = auditText(value.get("localCheckId")); var id = UUID.fromString(checkId);
            if (!id.toString().equals(checkId)) throw invalid();
            return new ToolExecutionAuthorization.AuditVO(1, id,
                    ToolExecutionAuthorization.Boundary.valueOf(auditText(value.get("boundary"))),
                    ToolExecutionAuthorization.Decision.valueOf(auditText(value.get("decision"))),
                    TrustedAgentActor.SubjectType.valueOf(auditText(value.get("subjectType"))),
                    auditText(value.get("grantId")), auditLong(value.get("authVersion")), auditLong(value.get("catalogVersion")),
                    auditLong(value.get("policyVersion")), auditInstant(value.get("checkedAt")), auditInstant(value.get("expiresAt")));
        } catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }

    private static String auditText(JsonNode value) {
        if (value.isNull()) return null; if (!value.isTextual()) throw invalid(); return value.textValue();
    }
    private static Long auditLong(JsonNode value) {
        if (value.isNull()) return null; if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid(); return value.longValue();
    }
    private static Instant auditInstant(JsonNode value) {
        String text = auditText(value); if (text == null) return null;
        Instant instant = Instant.parse(text); if (!instant.toString().equals(text)) throw invalid(); return instant;
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
