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
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.orchestration.ApprovalRequesterTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Closed, bounded persisted tool records. Parse failures never echo private arguments or downstream output. */
final class ToolExecutionJsonCodec {
    static final int MAX_BYTES = 128 * 1024;
    static final int MAX_AUDIT_BYTES = 4 * 1024;
    static final int MAX_APPROVAL_BYTES = 16 * 1024;
    private static final Set<String> AUDIT_FIELDS = Set.of("schemaVersion", "localCheckId", "boundary", "decision", "subjectType", "grantId",
            "authVersion", "catalogVersion", "policyVersion", "checkedAt", "expiresAt");
    private static final Set<String> RUNTIME_OUTCOME_FIELDS = Set.of("status", "output", "businessReceiptId", "errorCode");
    private static final Set<String> OUTCOME_FIELDS = Set.of("status", "output", "businessReceiptId", "errorCode", "actualCost");
    private static final Set<String> PROOF_FIELDS = Set.of("schemaVersion", "source", "binding", "approval");
    private static final Set<String> SOURCE_FIELDS = Set.of("sourceId", "tenantId", "issuer", "resource");
    private static final Set<String> BINDING_FIELDS = Set.of("consumer", "policyRef", "policyBindingSha256", "releaseVersion", "definitionKey", "definitionSha256", "taskDefinitionKey");
    private static final Set<String> CONSUMER_FIELDS = Set.of("tenantId", "subjectType", "subjectId", "issuerFingerprint");
    private static final Set<String> APPROVAL_FIELDS = Set.of("policyRef", "policyBindingSha256", "requestSha256", "approvalReceiptId", "requesterType", "requesterSubjectId", "sourceStartCommandId",
            "scopeId", "taskId", "releaseVersion", "definitionKey", "definitionSha256", "taskDefinitionKey", "sourceRequestSha256", "approved", "occurredAt", "verifiedAt", "expiresAt");
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
    enum ActionProfile { LEGACY_V1, RUNTIME_V2 }

    String outcome(ActionProfile profile, ToolDescriptor descriptor, ToolExecutionOutcome value) {
        if (profile == ActionProfile.LEGACY_V1) return outcome(descriptor, value);
        try {
            if (profile != ActionProfile.RUNTIME_V2 || value.actualCost() != null) throw invalid();
            var validated = value.validated(descriptor);
            var object = mapper.createObjectNode().put("businessReceiptId", validated.businessReceiptId()).put("errorCode", validated.errorCode());
            object.set("output", mapper.valueToTree(validated.output()));
            object.put("status", validated.status().name());
            return write(object);
        } catch (RuntimeException malformed) { throw invalid(); }
    }

    ToolExecutionOutcome outcome(ActionProfile profile, String json, ToolDescriptor descriptor) {
        if (profile == ActionProfile.LEGACY_V1) return outcome(json, descriptor);
        try {
            if (profile != ActionProfile.RUNTIME_V2) throw invalid();
            var tree = fields(mapper.readTree(bytes(json)), RUNTIME_OUTCOME_FIELDS);
            if (!tree.get("status").isTextual() || !tree.get("output").isObject()) throw invalid();
            return new ToolExecutionOutcome(ToolExecutionOutcome.Status.valueOf(tree.get("status").textValue()),
                    mapper.convertValue(tree.get("output"), VALUES), auditText(tree.get("businessReceiptId")),
                    auditText(tree.get("errorCode")), null).validated(descriptor);
        } catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }

    String outcome(ToolDescriptor descriptor, ToolExecutionOutcome value) {
        try { return write(value.validated(descriptor)); }
        catch (RuntimeException malformed) { throw invalid(); }
    }
    ToolExecutionOutcome outcome(String json, ToolDescriptor descriptor) {
        try {
            byte[] content = bytes(json); var tree = fields(mapper.readTree(content), OUTCOME_FIELDS);
            if (!tree.get("status").isTextual() || !tree.get("output").isObject() || !tree.get("actualCost").isNull() && !tree.get("actualCost").isNumber()) throw invalid();
            auditText(tree.get("businessReceiptId")); auditText(tree.get("errorCode"));
            // Decode the original numeric token directly so the shape check cannot round a valid BigDecimal.
            return mapper.readValue(content, ToolExecutionOutcome.class).validated(descriptor);
        }
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

    record ApprovalSourceVO(String sourceId, long tenantId, String issuer, String resource) {
        ApprovalSourceVO {
            try {
                if (sourceId == null || !sourceId.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}") || tenantId < 1) throw invalid();
                secure(issuer, 2048); secure(resource, 512);
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        String namespaceSha256() {
            return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_APPROVAL_SOURCE_V1", sourceId, Long.toString(tenantId), issuer, resource), Map.of());
        }
        @Override public String toString() { return "ApprovalSourceVO[redacted]"; }
    }
    record ApprovalProofVO(int schemaVersion, ApprovalSourceVO source, ToolApprovalBinding binding, VerifiedApprovalDTO approval) {
        ApprovalProofVO {
            if (schemaVersion != 1 || source == null || binding == null || approval == null || !approval.approved()) throw invalid();
        }
        @Override public String toString() { return "ApprovalProofVO[redacted]"; }
    }
    String approval(ApprovalProofVO proof, ToolActionRequest action) {
        try {
            if (proof == null || action == null || proof.source().tenantId() != action.owner().tenantId()
                    || proof.source().tenantId() != proof.binding().consumer().tenantId()
                    || !sha256(proof.source().issuer()).equals(proof.binding().consumer().issuerFingerprint())) throw invalid();
            proof.binding().requireApproved(action, proof.approval(), proof.approval().verifiedAt());
            String json = write(proof); if (json.getBytes(StandardCharsets.UTF_8).length > MAX_APPROVAL_BYTES) throw invalid(); return json;
        } catch (RuntimeException malformed) { throw invalid(); }
    }
    ApprovalProofVO approval(String json, String expectedSha256, ToolActionRequest action) {
        try {
            if (expectedSha256 == null || !expectedSha256.matches("[0-9a-f]{64}")) throw invalid();
            byte[] content = bytes(json); if (content.length > MAX_APPROVAL_BYTES) throw invalid();
            var root = fields(mapper.readTree(content), PROOF_FIELDS);
            if (!Long.valueOf(1).equals(auditLong(root.get("schemaVersion")))) throw invalid();
            var source = fields(root.get("source"), SOURCE_FIELDS); var binding = fields(root.get("binding"), BINDING_FIELDS);
            var consumer = fields(binding.get("consumer"), CONSUMER_FIELDS); var approval = fields(root.get("approval"), APPROVAL_FIELDS);
            if (!approval.get("approved").isBoolean() || !approval.get("approved").booleanValue()) throw invalid();
            var value = new ApprovalProofVO(1,
                    new ApprovalSourceVO(auditText(source.get("sourceId")), auditLong(source.get("tenantId")), auditText(source.get("issuer")), auditText(source.get("resource"))),
                    new ToolApprovalBinding(new TrustedAgentActor(auditLong(consumer.get("tenantId")), TrustedAgentActor.SubjectType.valueOf(auditText(consumer.get("subjectType"))),
                            auditText(consumer.get("subjectId")), auditText(consumer.get("issuerFingerprint"))), auditText(binding.get("policyRef")), auditText(binding.get("policyBindingSha256")),
                            auditText(binding.get("releaseVersion")), auditText(binding.get("definitionKey")), auditText(binding.get("definitionSha256")), auditText(binding.get("taskDefinitionKey"))),
                    new VerifiedApprovalDTO(auditText(approval.get("policyRef")), auditText(approval.get("policyBindingSha256")), auditText(approval.get("requestSha256")), auditText(approval.get("approvalReceiptId")),
                            ApprovalRequesterTypeEnum.valueOf(auditText(approval.get("requesterType"))), auditText(approval.get("requesterSubjectId")), auditText(approval.get("sourceStartCommandId")),
                            auditText(approval.get("scopeId")), auditText(approval.get("taskId")), auditText(approval.get("releaseVersion")), auditText(approval.get("definitionKey")), auditText(approval.get("definitionSha256")),
                            auditText(approval.get("taskDefinitionKey")), auditText(approval.get("sourceRequestSha256")), true, auditInstant(approval.get("occurredAt")), auditInstant(approval.get("verifiedAt")), auditInstant(approval.get("expiresAt"))));
            if (!expectedSha256.equals(approvalSha256(value, action))) throw invalid(); return value;
        } catch (RuntimeException | java.io.IOException malformed) { throw invalid(); }
    }
    String approvalSha256(ApprovalProofVO proof, ToolActionRequest action) { return sha256(approval(proof, action)); }

    private static JsonNode fields(JsonNode value, Set<String> fields) {
        if (value == null || !value.isObject() || value.size() != fields.size()) throw invalid();
        for (var field : fields) if (!value.has(field)) throw invalid(); return value;
    }
    private static void secure(String value, int maximum) {
        if (value == null || value.length() > maximum) throw invalid(); var uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !uri.toASCIIString().equals(value) || !uri.normalize().toASCIIString().equals(value)) throw invalid();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw invalid(); }
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
