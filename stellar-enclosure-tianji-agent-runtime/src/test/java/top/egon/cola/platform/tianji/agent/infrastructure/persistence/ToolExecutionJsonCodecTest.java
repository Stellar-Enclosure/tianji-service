package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionJsonCodecTest {
    private static final JsonMapper AUDIT_JSON = new JsonMapper();
    private static final UUID AUDIT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Instant AUDIT_AT = Instant.parse("2026-10-31T00:00:00.123456789Z");
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();
    private final ToolDescriptor descriptor = new ToolDescriptor("process.start", 1, "start_process", "Fixed operation",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "comment", ToolSchema.ValueType.STRING), Set.of("amount", "comment")),
            new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference")),
            new ToolPolicy(ToolPolicy.Risk.IDEMPOTENT_WRITE, "process.start.invoice", "tianji.api.process.instance.start", "a".repeat(64), null));

    @Test void registered_descriptor_and_primitive_arguments_round_trip_without_numeric_or_schema_drift() {
        assertEquals(descriptor, codec.descriptor(codec.descriptor(descriptor), descriptor.reference()));
        var values = Map.<String, Object>of("amount", 20, "comment", "private arguments");
        assertEquals(Map.of("amount", 20L, "comment", "private arguments"), codec.arguments(codec.arguments(descriptor, values), descriptor));
    }
    @Test void changed_or_unknown_descriptor_metadata_never_matches_the_original_pinned_reference() {
        String json = codec.descriptor(descriptor);
        assertSanitized(() -> codec.descriptor(json.replace("Fixed operation", "Changed operation"), descriptor.reference()));
        assertSanitized(() -> codec.descriptor(json.substring(0, json.length() - 1) + ",\"hiddenCallback\":true}", descriptor.reference()));
        assertSanitized(() -> codec.descriptor(json + " {}", descriptor.reference()));
    }
    @Test void malformed_private_arguments_fail_closed_without_echoing_the_payload() {
        for (var json : java.util.List.of("{\"amount\":\"20\",\"comment\":\"private arguments\"}", "{\"amount\":20.0,\"comment\":\"private arguments\"}", "{\"amount\":20,\"amount\":21,\"comment\":\"private arguments\"}",
                "{\"amount\":20,\"comment\":{\"nested\":\"private arguments\"}}", "{\"amount\":20,\"comment\":\"private arguments\",\"tenantId\":21}",
                "{\"amount\":20,\"comment\":\"\uD800\"}", "{\"amount\":20,\"comment\":\"\\uD800\"}", "null", " ".repeat(ToolExecutionJsonCodec.MAX_BYTES + 1))) {
            assertSanitized(() -> codec.arguments(json, descriptor));
        }
    }
    @Test void successful_receipt_and_unknown_result_round_trip_with_distinct_effect_semantics() {
        var success = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "process-1"), "process-1", null, BigDecimal.ZERO);
        assertEquals(success, codec.outcome(codec.outcome(descriptor, success), descriptor));
        var unknown = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        assertEquals(unknown, codec.outcome(codec.outcome(descriptor, unknown), descriptor));
        String json = codec.outcome(descriptor, success);
        assertSanitized(() -> codec.outcome(json.replace("\"businessReceiptId\":\"process-1\"", "\"businessReceiptId\":null"), descriptor));
    }

    @Test void null_or_invalid_private_content_is_rejected_consistently_at_every_writer_boundary() {
        var missingReceipt = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "private result"), null, null, null);
        var wrongOutput = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", 20L), "process-1", null, null);
        for (var operation : java.util.List.<org.junit.jupiter.api.function.Executable>of(
                () -> codec.descriptor((ToolDescriptor) null),
                () -> codec.arguments(descriptor, null),
                () -> codec.arguments(null, Map.of()),
                () -> codec.arguments(descriptor, Map.of("amount", "private arguments", "comment", "private arguments")),
                () -> codec.arguments(descriptor, Map.of("amount", 20L, "comment", "\uD800")),
                () -> codec.outcome(descriptor, null),
                () -> codec.outcome(null, missingReceipt),
                () -> codec.outcome(descriptor, missingReceipt),
                () -> codec.outcome(descriptor, wrongOutput))) {
            assertSanitized(operation);
        }
    }

    @Test void user_and_service_allow_audits_round_trip_with_exact_version_slots_and_utc_instants() throws Exception {
        for (var subject : TrustedAgentActor.SubjectType.values()) {
            var value = audit(subject, ToolExecutionAuthorization.Decision.ALLOW);
            String json = codec.audit(value); assertNotNull(json);
            var decoded = codec.audit(json); assertNotNull(decoded); assertEquals(value, decoded);
            var tree = AUDIT_JSON.readTree(json);
            assertEquals(11, tree.size()); assertEquals(AUDIT_AT.toString(), tree.get("checkedAt").textValue());
            assertEquals(AUDIT_AT.plusSeconds(10).toString(), tree.get("expiresAt").textValue());
            assertTrue(tree.get(subject == TrustedAgentActor.SubjectType.USER ? "catalogVersion" : "authVersion").isNull());
        }
    }

    @Test void deny_and_unavailable_audits_keep_all_upstream_fields_explicitly_null() throws Exception {
        for (var subject : TrustedAgentActor.SubjectType.values()) {
            for (var decision : List.of(ToolExecutionAuthorization.Decision.DENY, ToolExecutionAuthorization.Decision.UNAVAILABLE)) {
                var value = audit(subject, decision); String json = codec.audit(value); assertNotNull(json);
                var decoded = codec.audit(json); assertNotNull(decoded); assertEquals(value, decoded);
                var tree = AUDIT_JSON.readTree(json); assertEquals(11, tree.size());
                for (var field : List.of("grantId", "authVersion", "catalogVersion", "policyVersion", "expiresAt")) assertTrue(tree.has(field) && tree.get(field).isNull());
            }
        }
    }

    @Test void audit_long_versions_and_the_four_kib_boundary_are_preserved_without_coercion() {
        var maximum = new ToolExecutionAuthorization.AuditVO(1, AUDIT_ID, ToolExecutionAuthorization.Boundary.DISPATCH,
                ToolExecutionAuthorization.Decision.ALLOW, TrustedAgentActor.SubjectType.USER, "9" + "1".repeat(62),
                Long.MAX_VALUE, null, Long.MAX_VALUE, AUDIT_AT, AUDIT_AT.plusSeconds(10));
        String encoded = codec.audit(maximum); assertNotNull(encoded);
        var decoded = codec.audit(encoded); assertNotNull(decoded); assertEquals(maximum, decoded);
        String json = validAuditJson();
        String atLimit = json + " ".repeat(ToolExecutionJsonCodec.MAX_AUDIT_BYTES - json.getBytes(StandardCharsets.UTF_8).length);
        var bounded = codec.audit(atLimit); assertNotNull(bounded);
        assertEquals(audit(TrustedAgentActor.SubjectType.USER, ToolExecutionAuthorization.Decision.ALLOW), bounded);
        assertSanitized(() -> codec.audit(atLimit + " "));
    }

    @Test void every_audit_key_is_required_and_duplicate_unknown_or_trailing_content_is_closed() throws Exception {
        String json = validAuditJson();
        for (var field : List.of("schemaVersion", "localCheckId", "boundary", "decision", "subjectType", "grantId",
                "authVersion", "catalogVersion", "policyVersion", "checkedAt", "expiresAt")) {
            var object = (ObjectNode) AUDIT_JSON.readTree(json); object.remove(field);
            assertSanitized(() -> codec.audit(object.toString()));
        }
        for (var malformed : List.of("null", "[]", "true", "{}", json + " {}", json.substring(0, json.length() - 1) + ",\"reasonCode\":\"private arguments\"}",
                json.substring(0, json.length() - 1) + ",\"grantId\":\"private arguments\"}")) assertSanitized(() -> codec.audit(malformed));
    }

    @Test void audit_scalar_types_and_user_service_or_denied_groups_are_not_coerced() throws Exception {
        String json = validAuditJson();
        for (var field : List.of("schemaVersion", "authVersion", "policyVersion")) {
            for (var value : Arrays.<Object>asList("1", 1.0d, true, new BigInteger("9223372036854775808"), null, -1L)) {
                String changed = auditField(json, field, value); assertSanitized(() -> codec.audit(changed));
            }
        }
        for (var changed : List.of(auditField(json, "schemaVersion", 2), auditField(json, "grantId", 9L),
                auditField(json, "grantId", "8" + "1".repeat(62)), auditField(json, "catalogVersion", 1L),
                auditField(json, "decision", "DENY"), auditField(json, "decision", "UNAVAILABLE"),
                auditField(json, "checkedAt", null), auditField(json, "expiresAt", null),
                auditField(json, "expiresAt", AUDIT_AT.toString()))) assertSanitized(() -> codec.audit(changed));
        var service = (ObjectNode) AUDIT_JSON.readTree(json);
        service.put("subjectType", "SERVICE").put("grantId", "8" + "1".repeat(62)).putNull("authVersion").put("catalogVersion", 1);
        for (var value : Arrays.<Object>asList(0L, -1L, "1", 1.0d, null)) {
            String changed = auditField(service.toString(), "catalogVersion", value); assertSanitized(() -> codec.audit(changed));
        }
    }

    @Test void audit_identifiers_enums_and_instants_use_canonical_spelling_only() throws Exception {
        String json = validAuditJson();
        for (var value : List.of(AUDIT_ID.toString().toUpperCase(java.util.Locale.ROOT), "1-1-1-1-1", " " + AUDIT_ID)) {
            String changed = auditField(json, "localCheckId", value); assertSanitized(() -> codec.audit(changed));
        }
        for (var field : List.of("boundary", "decision", "subjectType")) {
            for (var value : List.of("prepare", "allow", "user", "PREPARE ", "private arguments")) {
                String changed = auditField(json, field, value); assertSanitized(() -> codec.audit(changed));
            }
        }
        for (var field : List.of("checkedAt", "expiresAt")) {
            for (var value : Arrays.<Object>asList(0L, AUDIT_AT.toString().replace("Z", "+00:00"),
                    AUDIT_AT.toString().replace("T", "t"), AUDIT_AT.toString().replace("Z", "z"),
                    "2026-10-31T00:00:00.000Z", "2026-10-31T00:00:00", "private arguments")) {
                String changed = auditField(json, field, value); assertSanitized(() -> codec.audit(changed));
            }
        }
    }

    @Test void audit_null_invalid_unicode_and_oversized_private_content_have_one_sanitized_failure() {
        assertSanitized(() -> codec.audit((ToolExecutionAuthorization.AuditVO) null));
        assertSanitized(() -> codec.audit((String) null));
        assertSanitized(() -> codec.audit(validAuditJson().replace("PREPARE", "\uD800")));
        assertSanitized(() -> codec.audit(validAuditJson().replace("PREPARE", "\\uD800")));
        assertSanitized(() -> codec.audit(" ".repeat(ToolExecutionJsonCodec.MAX_AUDIT_BYTES + 1)));
    }

    private static ToolExecutionAuthorization.AuditVO audit(TrustedAgentActor.SubjectType subject, ToolExecutionAuthorization.Decision decision) {
        boolean allow = decision == ToolExecutionAuthorization.Decision.ALLOW;
        return new ToolExecutionAuthorization.AuditVO(1, AUDIT_ID, ToolExecutionAuthorization.Boundary.PREPARE, decision, subject,
                allow ? (subject == TrustedAgentActor.SubjectType.USER ? "9" : "8") + "1".repeat(62) : null,
                allow && subject == TrustedAgentActor.SubjectType.USER ? 0L : null, allow && subject == TrustedAgentActor.SubjectType.SERVICE ? 1L : null,
                allow ? 3L : null, AUDIT_AT, allow ? AUDIT_AT.plusSeconds(10) : null);
    }

    private static String validAuditJson() {
        return """
                {"schemaVersion":1,"localCheckId":"123e4567-e89b-12d3-a456-426614174000","boundary":"PREPARE","decision":"ALLOW","subjectType":"USER","grantId":"%s","authVersion":0,"catalogVersion":null,"policyVersion":3,"checkedAt":"2026-10-31T00:00:00.123456789Z","expiresAt":"2026-10-31T00:00:10.123456789Z"}
                """.formatted("9" + "1".repeat(62)).strip();
    }

    private static String auditField(String json, String field, Object value) throws Exception {
        var object = (ObjectNode) AUDIT_JSON.readTree(json); object.set(field, AUDIT_JSON.valueToTree(value)); return object.toString();
    }

    private static void assertSanitized(org.junit.jupiter.api.function.Executable operation) {
        var failure = assertThrows(IllegalStateException.class, operation);
        assertEquals("Stored tool execution content is invalid or changed", failure.getMessage());
        assertNull(failure.getCause());
    }
}
