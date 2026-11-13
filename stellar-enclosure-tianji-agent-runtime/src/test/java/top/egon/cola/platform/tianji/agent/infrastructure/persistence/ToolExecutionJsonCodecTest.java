package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolApprovalBinding;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.VerifiedApprovalDTO;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionJsonCodecTest {
    private static final JsonMapper AUDIT_JSON = new JsonMapper();
    private static final JsonMapper APPROVAL_JSON = JsonMapper.builder().addModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private static final Instant APPROVAL_AT = Instant.parse("2020-01-01T00:00:00.123456789Z");
    private static final String APPROVAL_ISSUER = "https://idp.example/21";
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

    @Test void outcome_profile_selects_only_its_closed_shape_and_preserves_legacy_bytes() throws Exception {
        var result = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("reference", "process-1"), "process-1", null, null);
        String legacy = "{\"actualCost\":null,\"businessReceiptId\":\"process-1\",\"errorCode\":null,\"output\":{\"reference\":\"process-1\"},\"status\":\"SUCCEEDED\"}";
        String runtime = legacy.replace("\"actualCost\":null,", "");
        var old = ToolExecutionJsonCodec.ActionProfile.LEGACY_V1;
        var current = ToolExecutionJsonCodec.ActionProfile.RUNTIME_V2;
        assertEquals(legacy, codec.outcome(old, descriptor, result));
        assertEquals(result, codec.outcome(old, legacy, descriptor));
        assertEquals(result, codec.outcome(current, runtime, descriptor));
        assertEquals(runtime, codec.outcome(current, descriptor, result));
        assertSanitized(() -> codec.outcome(old, runtime, descriptor));
        assertSanitized(() -> codec.outcome(current, legacy, descriptor));
        assertSanitized(() -> codec.outcome(null, runtime, descriptor));
        assertSanitized(() -> codec.outcome(current, descriptor, new ToolExecutionOutcome(
                result.status(), result.output(), result.businessReceiptId(), null, BigDecimal.ZERO)));
        for (var key : List.of("status", "output", "businessReceiptId", "errorCode")) {
            var value = (ObjectNode) AUDIT_JSON.readTree(runtime); value.remove(key);
            assertSanitized(() -> codec.outcome(current, value.toString(), descriptor));
        }
        for (var invalid : List.of(runtime + "{}", runtime.replace("\"errorCode\":null", "\"errorCode\":false"),
                runtime.replace("\"errorCode\":null", "\"errorCode\":null,\"errorCode\":null"),
                runtime.replace("\"errorCode\":null", "\"errorCode\":null,\"private\":true"),
                runtime.replace("\"businessReceiptId\":\"process-1\"", "\"businessReceiptId\":null"),
                runtime.replace("\"SUCCEEDED\"", "\"FAILED_KNOWN\""))) {
            assertSanitized(() -> codec.outcome(current, invalid, descriptor));
        }
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

    @Test void historical_user_and_service_approval_proofs_round_trip_as_exact_closed_objects() throws Exception {
        for (var type : TrustedAgentActor.SubjectType.values()) {
            var action = approvedAction(type); var proof = approvalProof(action);
            String json = codec.approval(proof, action); assertNotNull(json);
            String sha = codec.approvalSha256(proof, action); assertNotNull(sha); assertTrue(sha.matches("[0-9a-f]{64}"));
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))), sha);
            var decoded = codec.approval(json, sha, action); assertNotNull(decoded); assertEquals(proof, decoded);
            var tree = (ObjectNode) APPROVAL_JSON.readTree(json);
            for (var path : List.of("", "source", "binding", "binding.consumer", "approval")) {
                var object = proofObject(tree, path); assertEquals(proofFields(path), fieldNames(object));
            }
            for (var field : List.of("occurredAt", "verifiedAt", "expiresAt")) assertEquals(
                    field.equals("expiresAt") ? APPROVAL_AT.plusSeconds(10).toString() : APPROVAL_AT.toString(), tree.get("approval").get(field).textValue());
            assertTrue(tree.get("approval").get("approved").isBoolean()); assertTrue(tree.get("approval").get("approved").booleanValue());
            assertEquals("ApprovalProofVO[redacted]", proof.toString()); assertEquals("ApprovalSourceVO[redacted]", proof.source().toString());
        }
    }

    @Test void canonical_approval_sha_ignores_original_whitespace_and_field_order() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); var proof = approvalProof(action);
        String sha = codec.approvalSha256(proof, action); assertNotNull(sha);
        String reordered = APPROVAL_JSON.writerWithDefaultPrettyPrinter().writeValueAsString(approvalTree(action));
        var decoded = codec.approval(" \n" + reordered + "\n ", sha, action); assertNotNull(decoded); assertEquals(proof, decoded);
        assertEquals(sha, codec.approvalSha256(decoded, action));
        assertSanitized(() -> codec.approval(reordered, "f".repeat(64), action));
        assertSanitized(() -> codec.approval(reordered, sha.toUpperCase(java.util.Locale.ROOT), action));
        assertSanitized(() -> codec.approval(reordered, null, action));
    }

    @Test void approval_source_namespace_has_its_own_stable_encoding_and_exact_https_identity() {
        var source = approvalProof(approvedAction(TrustedAgentActor.SubjectType.USER)).source();
        assertEquals("0247b2bc66813db582ed628936ce0e38c8ba32d4839ff3a13ee19a4eb4a617df", source.namespaceSha256());
        for (var changed : List.of(new ToolExecutionJsonCodec.ApprovalSourceVO("other-source", 21, source.issuer(), source.resource()),
                new ToolExecutionJsonCodec.ApprovalSourceVO(source.sourceId(), 22, source.issuer(), source.resource()),
                new ToolExecutionJsonCodec.ApprovalSourceVO(source.sourceId(), 21, "https://IDP.example/21", source.resource()),
                new ToolExecutionJsonCodec.ApprovalSourceVO(source.sourceId(), 21, source.issuer(), "https://process.example/api/22"))) {
            assertNotEquals(source.namespaceSha256(), changed.namespaceSha256());
        }
        for (var uri : List.of("http://private.example/", "https://private.example/a/../b", "https://secret@private.example/",
                "https://private.example/?token=private", "https://private.example/#private", "https://private.example/资料", "https://private.example/" + "x".repeat(2048))) {
            assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalSourceVO("approval-source", 21, uri, source.resource()));
            assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalSourceVO("approval-source", 21, source.issuer(), uri));
        }
        for (var id : List.of("", " private", "private source", "x".repeat(129), "\uD800")) assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalSourceVO(id, 21, source.issuer(), source.resource()));
        assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalSourceVO(source.sourceId(), 0, source.issuer(), source.resource()));
        String prefix = "https://process.example/";
        assertNotNull(new ToolExecutionJsonCodec.ApprovalSourceVO("a".repeat(128), Long.MAX_VALUE, prefix + "x".repeat(2048 - prefix.length()), prefix + "x".repeat(512 - prefix.length())));
        assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalSourceVO(source.sourceId(), 21, source.issuer(), prefix + "x".repeat(513 - prefix.length())));
    }

    @Test void every_approval_field_at_every_object_level_is_required_and_unknown_duplicate_or_trailing_content_is_rejected() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); String sha = validProofSha(action);
        for (var path : List.of("", "source", "binding", "binding.consumer", "approval")) {
            for (var field : proofFields(path)) {
                var tree = approvalTree(action); proofObject(tree, path).remove(field);
                assertSanitized(() -> codec.approval(tree.toString(), sha, action));
            }
            var unknown = approvalTree(action); proofObject(unknown, path).put("privateUnknown", "private arguments");
            assertSanitized(() -> codec.approval(unknown.toString(), sha, action));
            var tree = approvalTree(action); var object = proofObject(tree, path); String key = object.fieldNames().next();
            String original = object.toString(), duplicate = original.substring(0, original.length() - 1) + ",\"" + key + "\":" + object.get(key) + '}';
            String json = tree.toString(); assertTrue(json.contains(original)); String malformed = json.replace(original, duplicate);
            assertSanitized(() -> codec.approval(malformed, sha, action));
        }
        for (var json : List.of("null", "[]", "true", "{}", approvalTree(action) + " {}")) assertSanitized(() -> codec.approval(json, sha, action));
    }

    @Test void approval_json_scalar_types_are_exact_and_consumed_proof_requires_boolean_true() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); String sha = validProofSha(action);
        for (var path : List.of("", "source", "binding.consumer")) {
            String field = path.isEmpty() ? "schemaVersion" : "tenantId";
            for (var value : Arrays.<Object>asList("1", 1.0d, true, null, new BigInteger("9223372036854775808"), 0L, -1L)) {
                String json = approvalField(action, path, field, value); assertSanitized(() -> codec.approval(json, sha, action));
            }
        }
        for (var path : List.of("source", "binding", "binding.consumer", "approval")) {
            var object = proofObject(approvalTree(action), path);
            for (var field : proofFields(path)) if (object.get(field).isTextual()) {
                String json = approvalField(action, path, field, 1); assertSanitized(() -> codec.approval(json, sha, action));
            }
        }
        for (var value : Arrays.<Object>asList(false, "true", 1, null)) {
            String json = approvalField(action, "approval", "approved", value); assertSanitized(() -> codec.approval(json, sha, action));
        }
        for (var value : List.of("user", "SERVICE ", "private arguments")) {
            String json = approvalField(action, "approval", "requesterType", value); assertSanitized(() -> codec.approval(json, sha, action));
        }
    }

    @Test void approval_instants_are_canonical_utc_and_the_reader_uses_historical_verified_at() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); String sha = validProofSha(action);
        for (var field : List.of("occurredAt", "verifiedAt", "expiresAt")) {
            for (var value : Arrays.<Object>asList(0L, APPROVAL_AT.toString().replace("Z", "+00:00"), APPROVAL_AT.toString().replace("Z", "z"),
                    "2020-01-01T00:00:00.000Z", "2020-01-01 00:00:00Z", null)) {
                String json = approvalField(action, "approval", field, value); assertSanitized(() -> codec.approval(json, sha, action));
            }
        }
        var proof = approvalProof(action); assertTrue(proof.approval().expiresAt().isBefore(Instant.parse("2021-01-01T00:00:00Z")));
        String json = codec.approval(proof, action); assertNotNull(json); assertEquals(proof, codec.approval(json, sha, action));
    }

    @Test void source_consumer_and_action_drift_is_rejected_without_leaking_supplied_action_or_private_causes() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); var proof = approvalProof(action);
        var tenant = new ToolExecutionJsonCodec.ApprovalSourceVO(proof.source().sourceId(), 22, proof.source().issuer(), proof.source().resource());
        var issuer = new ToolExecutionJsonCodec.ApprovalSourceVO(proof.source().sourceId(), 21, "https://other.example/21", proof.source().resource());
        assertSanitized(() -> codec.approval(new ToolExecutionJsonCodec.ApprovalProofVO(1, tenant, proof.binding(), proof.approval()), action));
        assertSanitized(() -> codec.approval(new ToolExecutionJsonCodec.ApprovalProofVO(1, issuer, proof.binding(), proof.approval()), action));
        var changed = new ToolActionRequest(action.owner(), approvalDescriptor(), action.businessKey(), "changed-private-version", action.arguments());
        assertSanitized(() -> codec.approval(proof, changed));
        assertSanitized(() -> codec.approvalSha256(proof, changed));
        for (var field : List.of("policyBindingSha256", "requestSha256", "definitionSha256", "sourceRequestSha256")) {
            var tree = approvalTree(action); proofObject(tree, "approval").put(field, "f".repeat(64));
            var value = APPROVAL_JSON.treeToValue(tree.get("approval"), VerifiedApprovalDTO.class);
            assertSanitized(() -> codec.approval(new ToolExecutionJsonCodec.ApprovalProofVO(1, proof.source(), proof.binding(), value), action));
        }
        var falseTree = approvalTree(action); proofObject(falseTree, "approval").put("approved", false);
        var falseDecision = APPROVAL_JSON.treeToValue(falseTree.get("approval"), VerifiedApprovalDTO.class);
        assertFalse(falseDecision.approved());
        assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalProofVO(1, proof.source(), proof.binding(), falseDecision));
        assertSanitized(() -> new ToolExecutionJsonCodec.ApprovalProofVO(2, proof.source(), proof.binding(), proof.approval()));
        assertSanitized(() -> codec.approval((ToolExecutionJsonCodec.ApprovalProofVO) null, action));
        assertSanitized(() -> codec.approval(proof, null)); assertSanitized(() -> codec.approvalSha256(null, action));
    }

    @Test void approval_content_obeys_the_sixteen_kib_utf8_boundary_and_rejects_invalid_unicode() throws Exception {
        var action = approvedAction(TrustedAgentActor.SubjectType.USER); String json = approvalTree(action).toString(), sha = validProofSha(action);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length > json.length());
        String atLimit = json + " ".repeat(ToolExecutionJsonCodec.MAX_APPROVAL_BYTES - json.getBytes(StandardCharsets.UTF_8).length);
        var decoded = codec.approval(atLimit, sha, action); assertNotNull(decoded); assertEquals(approvalProof(action), decoded);
        assertSanitized(() -> codec.approval(atLimit + ' ', sha, action));
        assertSanitized(() -> codec.approval(json.replace("私密审批", "\uD800"), sha, action));
        assertSanitized(() -> codec.approval(json.replace("私密审批", "\\uD800"), sha, action));
        assertSanitized(() -> codec.approval(null, sha, action));
    }

    @Test void persisted_outcomes_require_all_five_keys_even_when_nullable_and_reject_scalar_coercion() throws Exception {
        var value = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", null);
        String json = codec.outcome(descriptor, value); assertNotNull(json);
        for (var field : List.of("status", "output", "businessReceiptId", "errorCode", "actualCost")) {
            var object = (ObjectNode) AUDIT_JSON.readTree(json); object.remove(field); assertSanitized(() -> codec.outcome(object.toString(), descriptor));
        }
        for (var entry : List.<Map.Entry<String, Object>>of(Map.entry("status", 1), Map.entry("status", true), Map.entry("output", "private output"),
                Map.entry("businessReceiptId", 1), Map.entry("errorCode", 1), Map.entry("actualCost", "0"), Map.entry("actualCost", true))) {
            String malformed = auditField(json, entry.getKey(), entry.getValue()); assertSanitized(() -> codec.outcome(malformed, descriptor));
        }
        assertSanitized(() -> codec.outcome(json.substring(0, json.length() - 1) + ",\"privateExtra\":true}", descriptor));
        assertSanitized(() -> codec.outcome(json.substring(0, json.length() - 1) + ",\"actualCost\":0}", descriptor));
        assertSanitized(() -> codec.outcome(json + " {}", descriptor));
        var fractional = new ToolExecutionOutcome(ToolExecutionOutcome.Status.UNKNOWN, Map.of(), null, "OUTCOME_UNKNOWN", new BigDecimal("0.12345678"));
        assertEquals(fractional, codec.outcome(codec.outcome(descriptor, fractional), descriptor));
        assertEquals(value, codec.outcome(json, descriptor));
    }

    private static ToolDescriptor approvalDescriptor() {
        return new ToolDescriptor("process.approved_start", 1, "approved_start", "Approved fixed operation",
                new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")), new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
                new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start.invoice", "tianji.api.process.instance.start", "a".repeat(64), "approval.v1"));
    }
    private static ToolActionRequest approvedAction(TrustedAgentActor.SubjectType type) {
        var owner = TrustedAgentActor.verified(21, type, type == TrustedAgentActor.SubjectType.USER ? "alice" : "approval-consumer", APPROVAL_ISSUER);
        return new ToolActionRequest(owner, approvalDescriptor(), "private-business-1", "business-v1", Map.of("amount", 20L));
    }
    private static ToolExecutionJsonCodec.ApprovalProofVO approvalProof(ToolActionRequest action) {
        var consumer = TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.SERVICE, "approval-consumer", APPROVAL_ISSUER);
        var binding = new ToolApprovalBinding(consumer, "approval.v1", "b".repeat(64), "approval-release", "approval_process", "c".repeat(64), "review_task");
        var query = binding.query(action, "私密审批-receipt-1"); var source = query.sourceStart();
        var approved = new VerifiedApprovalDTO(binding.policyRef(), binding.policyBindingSha256(), query.fingerprint(), query.approvalReceiptId(), query.requesterType(), query.requesterSubjectId(),
                source.commandId(), "private-scope", "private-task", binding.releaseVersion(), binding.definitionKey(), binding.definitionSha256(), binding.taskDefinitionKey(),
                OrchestrationRequestDigests.start(source), true, APPROVAL_AT, APPROVAL_AT, APPROVAL_AT.plusSeconds(10));
        return new ToolExecutionJsonCodec.ApprovalProofVO(1, new ToolExecutionJsonCodec.ApprovalSourceVO("ps-approval-primary", 21, APPROVAL_ISSUER, "https://process.example/api/21"), binding, approved);
    }
    private static ObjectNode approvalTree(ToolActionRequest action) {
        return APPROVAL_JSON.valueToTree(approvalProof(action));
    }
    private String validProofSha(ToolActionRequest action) { String sha = codec.approvalSha256(approvalProof(action), action); assertNotNull(sha); return sha; }
    private static String approvalField(ToolActionRequest action, String path, String field, Object value) {
        var tree = approvalTree(action); proofObject(tree, path).set(field, APPROVAL_JSON.valueToTree(value)); return tree.toString();
    }
    private static ObjectNode proofObject(ObjectNode root, String path) {
        var value = root; if (!path.isEmpty()) for (var part : path.split("\\.")) value = (ObjectNode) value.get(part); return value;
    }
    private static Set<String> fieldNames(ObjectNode value) { var fields = new java.util.HashSet<String>(); value.fieldNames().forEachRemaining(fields::add); return fields; }
    private static Set<String> proofFields(String path) {
        return switch (path) {
            case "" -> Set.of("schemaVersion", "source", "binding", "approval");
            case "source" -> Set.of("sourceId", "tenantId", "issuer", "resource");
            case "binding" -> Set.of("consumer", "policyRef", "policyBindingSha256", "releaseVersion", "definitionKey", "definitionSha256", "taskDefinitionKey");
            case "binding.consumer" -> Set.of("tenantId", "subjectType", "subjectId", "issuerFingerprint");
            case "approval" -> Set.of("policyRef", "policyBindingSha256", "requestSha256", "approvalReceiptId", "requesterType", "requesterSubjectId", "sourceStartCommandId", "scopeId", "taskId", "releaseVersion",
                    "definitionKey", "definitionSha256", "taskDefinitionKey", "sourceRequestSha256", "approved", "occurredAt", "verifiedAt", "expiresAt");
            default -> throw new AssertionError("Unexpected proof fixture path");
        };
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
