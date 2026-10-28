package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.EventCompaction;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.sessions.State;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import com.google.genai.types.ToolCall;
import com.google.genai.types.ToolResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.math.BigInteger;

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
    void typed_events_restore_generic_values_in_every_supported_native_slot() {
        AdkSessionEventCodec typed = new AdkSessionEventCodec(true);
        Map<String, Object> numbers = numbers();
        EventActions actions = EventActions.builder()
                .stateDelta(Map.of("local", numbers, "removed", State.REMOVED))
                .agentState(numbers).setModelResponse(numbers)
                .compaction(EventCompaction.builder().startTimestamp(1).endTimestamp(2)
                        .compactedContent(Content.fromParts(Part.builder().functionCall(FunctionCall.builder()
                                .id("compacted").args(numbers).build()).build())).build())
                .requestedToolConfirmations(Map.of("confirmation", ToolConfirmation.builder()
                                .hint("confirm").confirmed(false).payload(numbers).build(),
                        "empty", ToolConfirmation.builder().hint("confirm").confirmed(false).build())).build();
        Part call = Part.builder().functionCall(FunctionCall.builder().id("f").args(numbers).build()).build();
        Part response = Part.builder().functionResponse(FunctionResponse.builder().id("f").response(numbers).build()).build();
        Part toolCall = Part.builder().toolCall(ToolCall.builder().id("t").args(numbers).build()).build();
        Part toolResponse = Part.builder().toolResponse(ToolResponse.builder().id("t").response(numbers).build())
                .partMetadata(numbers).build();
        Event original = event(Content.builder().parts(call, response, toolCall, toolResponse).build(), actions);
        original.setOutput(numbers);

        String stored = typed.encode(original);
        Event restored = typed.decode(stored);
        assertEquals(numbers, restored.actions().stateDelta().get("local"));
        assertSame(State.REMOVED, restored.actions().stateDelta().get("removed"));
        assertEquals(numbers, restored.actions().agentState().orElseThrow());
        assertEquals(numbers, restored.actions().setModelResponse().orElseThrow());
        assertEquals(numbers, restored.actions().requestedToolConfirmations().get("confirmation").payload());
        assertNull(restored.actions().requestedToolConfirmations().get("empty").payload());
        assertEquals(numbers, restored.actions().compaction().orElseThrow().compactedContent().parts().orElseThrow()
                .getFirst().functionCall().orElseThrow().args().orElseThrow());
        assertEquals(numbers, restored.output().orElseThrow());
        List<Part> parts = restored.content().orElseThrow().parts().orElseThrow();
        assertEquals(numbers, parts.get(0).functionCall().orElseThrow().args().orElseThrow());
        assertEquals(numbers, parts.get(1).functionResponse().orElseThrow().response().orElseThrow());
        assertEquals(numbers, parts.get(2).toolCall().orElseThrow().args().orElseThrow());
        assertEquals(numbers, parts.get(3).toolResponse().orElseThrow().response().orElseThrow());
        assertEquals(numbers, parts.get(3).partMetadata().orElseThrow());
        assertTrue(typed.sameEvent(stored, original));
    }

    @Test
    void reads_legacy_events_and_rejects_invalid_typed_data() throws Exception {
        Event original = event(null, EventActions.builder().stateDelta(Map.of("count", 1)).build());
        String legacy = codec.encode(original);
        AdkSessionEventCodec typed = new AdkSessionEventCodec(true);
        assertEquals(1, typed.decode(legacy).actions().stateDelta().get("count"));
        assertTrue(typed.sameEvent(legacy, typed.decode(typed.encode(original))));
        assertFalse(typed.sameEvent(legacy, event(null, EventActions.builder().stateDelta(Map.of("count", 1L)).build())));

        ObjectNode invalid = (ObjectNode) JsonBaseModel.getMapper().readTree(typed.encode(original));
        ((com.fasterxml.jackson.databind.node.ArrayNode) invalid.get("typedValues")).set(0,
                JsonBaseModel.getMapper().getNodeFactory().textNode("unrecognized"));
        assertThrows(IllegalArgumentException.class, () -> typed.decode(invalid.toString()));
        ObjectNode mismatch = (ObjectNode) JsonBaseModel.getMapper().readTree(typed.encode(original));
        var count = (com.fasterxml.jackson.databind.node.ArrayNode) mismatch.path("typedValues").get(1)
                .path("stateDelta").get(1).path("count");
        count.set(1, JsonBaseModel.getMapper().getNodeFactory().textNode("2"));
        assertThrows(IllegalArgumentException.class, () -> typed.decode(mismatch.toString()));
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

    private static Map<String, Object> numbers() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("byte", (byte) 1);
        values.put("short", (short) 2);
        values.put("integer", 3);
        values.put("long", 4L);
        values.put("float", -0.0f);
        values.put("double", -0.0d);
        values.put("bigInteger", new BigInteger("123456789012345678901234567890"));
        values.put("bigDecimal", new BigDecimal("12345678901234567890.00100"));
        List<Object> list = new ArrayList<>();
        list.add(null);
        list.add(Map.of("map", List.of("long", "1")));
        values.put("nested", list);
        return values;
    }
}
