package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdkSessionEventCodecTest {
    private final AdkSessionEventCodec codec = new AdkSessionEventCodec();

    @Test
    void preserves_native_tool_calls_responses_and_scoped_removals_without_persisting_temp_state() {
        Object temporary = new Object();
        EventActions actions = EventActions.builder().stateDelta(Map.of(
                "app:deleted", State.REMOVED,
                "user:literal", "__ADK_SENTINEL_REMOVED__",
                "local", Map.of("items", List.of("one", 2)),
                "temp:transient", temporary)).build();
        Part call = Part.builder().functionCall(FunctionCall.builder().id("call-1").name("lookup")
                .args(Map.of("query", "record", "limit", 2)).build()).build();
        Part response = Part.builder().functionResponse(FunctionResponse.builder().id("call-1").name("lookup")
                .response(Map.of("result", List.of("a", "b"))).build()).build();
        Event event = event(Content.builder().role("model").parts(call, response).build(), actions);

        String stored = codec.encode(event);
        assertFalse(stored.contains("temp:transient"));
        assertSame(temporary, event.actions().stateDelta().get("temp:transient"));
        Event restored = codec.decode(stored);
        assertSame(State.REMOVED, restored.actions().stateDelta().get("app:deleted"));
        assertEquals("__ADK_SENTINEL_REMOVED__", restored.actions().stateDelta().get("user:literal"));
        assertFalse(restored.actions().stateDelta().containsKey("temp:transient"));
        assertEquals(Map.of("items", List.of("one", 2)), restored.actions().stateDelta().get("local"));
        List<Part> parts = restored.content().orElseThrow().parts().orElseThrow();
        assertEquals("call-1", parts.get(0).functionCall().orElseThrow().id().orElseThrow());
        assertEquals(Map.of("query", "record", "limit", 2), parts.get(0).functionCall().orElseThrow().args().orElseThrow());
        assertEquals("call-1", parts.get(1).functionResponse().orElseThrow().id().orElseThrow());
        assertEquals(Map.of("result", List.of("a", "b")), parts.get(1).functionResponse().orElseThrow().response().orElseThrow());
    }

    @Test
    void rejects_unknown_versions_corrupt_removal_markers_and_unknown_native_fields() throws Exception {
        EventActions actions = EventActions.builder().stateDelta(Map.of("value", State.REMOVED)).build();
        String stored = codec.encode(event(Content.fromParts(Part.fromText("hello")), actions));
        ObjectNode original = (ObjectNode) JsonBaseModel.getMapper().readTree(stored);
        ObjectNode unknownVersion = original.deepCopy();
        unknownVersion.put("codecVersion", 2);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknownVersion.toString()));
        ObjectNode wrongAdk = original.deepCopy();
        wrongAdk.put("adkVersion", "2.0.0");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongAdk.toString()));
        ObjectNode wrongKey = original.deepCopy();
        wrongKey.withArray("removedStateKeys").add("missing");
        assertThrows(IllegalArgumentException.class, () -> codec.decode(wrongKey.toString()));
        ObjectNode unknownField = original.deepCopy();
        ((ObjectNode) unknownField.path("event")).put("unrecognizedField", true);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(unknownField.toString()));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(stored.replaceFirst("\\{", "{\"codecVersion\":1,")));
    }

    @Test
    void serializes_equivalent_maps_in_the_same_order_for_event_idempotency() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("z", 2);
        Event left = event(null, EventActions.builder().stateDelta(first).build());
        Event right = event(null, EventActions.builder().stateDelta(second).build());
        left.setOutput(first);
        right.setOutput(second);
        assertEquals(codec.encode(left), codec.encode(right));
    }

    @Test
    void rejects_partial_oversized_non_json_and_authentication_material_without_exposing_it() {
        Event partial = event(Content.fromParts(Part.fromText("chunk")), EventActions.builder().build());
        partial.setPartial(true);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(partial));
        Event huge = event(Content.fromParts(Part.fromText("x".repeat(1_048_576))), EventActions.builder().build());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(huge));
        EventActions unsafe = EventActions.builder().stateDelta(Map.of("opaque", new Object())).build();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(event(null, unsafe)));
        EventActions changedNumberType = EventActions.builder().stateDelta(Map.of("count", 1L)).build();
        assertThrows(IllegalArgumentException.class, () -> codec.encode(event(null, changedNumberType)));
        EventActions credentialRequest = EventActions.builder().build();
        credentialRequest.setRequestedAuthConfigs(Map.of("tool", Map.of("credential", "not-a-real-secret")));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> codec.encode(event(null, credentialRequest)));
        assertFalse(failure.toString().contains("not-a-real-secret"));
        assertNull(failure.getCause());
    }

    private static Event event(Content content, EventActions actions) {
        return Event.builder().id("event-1").invocationId("invocation-1").author("agent")
                .timestamp(1_700_000_000_000L).content(content).actions(actions).build();
    }
}
