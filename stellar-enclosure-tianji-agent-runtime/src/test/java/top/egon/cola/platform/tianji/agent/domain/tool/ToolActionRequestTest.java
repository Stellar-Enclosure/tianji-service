package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolActionRequestTest {
    private static final TrustedAgentActor OWNER = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "requester", "https://idp.example/20");
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "comment", ToolSchema.ValueType.STRING), Set.of("amount", "comment"));
    private static final ToolSchema OUTPUT = new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference"));

    @Test void repeated_calls_and_numeric_representations_share_one_business_intent_and_request_digest() {
        var values = new HashMap<String, Object>(Map.of("amount", 20, "comment", "private action")); var request = new ToolActionRequest(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", values); values.clear();
        var duplicate = new ToolActionRequest(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", Map.of("comment", "private action", "amount", BigInteger.valueOf(20)));
        assertEquals(UUID.fromString(request.intentId()).toString(), request.intentId()); assertEquals(request.intentId(), duplicate.intentId()); assertEquals(request.requestSha256(), duplicate.requestSha256());
        assertEquals(20L, request.arguments().get("amount")); assertThrows(UnsupportedOperationException.class, () -> request.arguments().clear()); assertFalse(request.toString().contains("private action"));
    }

    @Test void a_changed_payload_tool_version_or_binding_changes_the_request_but_never_mints_a_second_intent_for_the_same_object_version() {
        var original = request(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", 20);
        for (var changed : java.util.List.of(request(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", 21), request(OWNER, tool(2, "a".repeat(64)), "invoice-1", "7", 20), request(OWNER, tool(1, "b".repeat(64)), "invoice-1", "7", 20))) {
            assertEquals(original.intentId(), changed.intentId()); assertNotEquals(original.requestSha256(), changed.requestSha256());
        }
        assertNotEquals(original.intentId(), request(OWNER, tool(1, "a".repeat(64)), "invoice-1", "8", 20).intentId());
        assertNotEquals(original.intentId(), request(OWNER, tool(1, "a".repeat(64)), "invoice-2", "7", 20).intentId());
    }

    @Test void the_complete_requester_namespace_and_kind_separate_business_intents() {
        var original = request(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", 20);
        for (var actor : java.util.List.of(TrustedAgentActor.verified(21, TrustedAgentActor.SubjectType.USER, OWNER.subjectId(), "https://idp.example/21"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, OWNER.subjectId(), "https://other.example/20"),
                TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, OWNER.subjectId(), "https://idp.example/20"))) {
            assertNotEquals(original.intentId(), request(actor, tool(1, "a".repeat(64)), "invoice-1", "7", 20).intentId());
        }
    }

    @Test void missing_business_versions_legacy_owners_read_only_tools_and_model_identity_extras_cannot_be_prepared_as_writes() {
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, tool(1, "a".repeat(64)), "invoice-1", " ", 20));
        assertThrows(IllegalArgumentException.class, () -> request(new TrustedAgentActor(20, TrustedAgentActor.SubjectType.USER, "requester"), tool(1, "a".repeat(64)), "invoice-1", "7", 20));
        var read = new ToolDescriptor("process.get", 1, "get_process", "Read", INPUT, OUTPUT, new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.get", "tianji.api.process.instance.get", "a".repeat(64), null));
        assertThrows(IllegalArgumentException.class, () -> request(OWNER, read, "invoice-1", "7", 20));
        assertThrows(IllegalArgumentException.class, () -> new ToolActionRequest(OWNER, tool(1, "a".repeat(64)), "invoice-1", "7", Map.of("amount", 20, "comment", "private", "approved", true)));
    }
    private static ToolActionRequest request(TrustedAgentActor actor, ToolDescriptor tool, String key, String version, long amount) { return new ToolActionRequest(actor, tool, key, version, Map.of("amount", amount, "comment", "private action")); }
    private static ToolDescriptor tool(long version, String binding) { return new ToolDescriptor("process.start", version, "start_process", "Fixed process action", INPUT, OUTPUT,
            new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start.invoice", "tianji.api.process.instance.start", binding, "approval.v1")); }
}
