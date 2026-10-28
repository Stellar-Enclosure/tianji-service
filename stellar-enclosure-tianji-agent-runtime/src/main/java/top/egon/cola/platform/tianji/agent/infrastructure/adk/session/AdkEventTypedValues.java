package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.adk.events.Event;
import com.google.adk.events.EventCompaction;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.sessions.State;
import com.google.genai.types.Part;
import com.google.genai.types.Content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Restores only ADK's generic value slots; all other native event fields stay with ADK. */
final class AdkEventTypedValues {
    private static final String REMOVED_VALUE = "__ADK_SENTINEL_REMOVED__";
    private final AdkTypedValueCodec codec = new AdkTypedValueCodec();

    JsonNode encode(Event event) {
        return codec.encode(values(event));
    }

    void restore(Event event, JsonNode encoded, Set<String> removed) {
        Map<String, Object> values = map(codec.decode(encoded));
        Map<String, Object> shape = values(event);
        if (!values.keySet().equals(shape.keySet())) throw invalid();
        Map<String, Object> delta = map(values.get("stateDelta"));
        if (!delta.keySet().equals(map(shape.get("stateDelta")).keySet())) throw invalid();
        removed.forEach(key -> {
            if (!REMOVED_VALUE.equals(delta.get(key))) throw invalid();
            delta.put(key, State.REMOVED);
        });
        event.actions().setStateDelta(new ConcurrentHashMap<>(delta));
        if (values.containsKey("agentState")) event.actions().setAgentState(map(values.get("agentState")));
        if (values.containsKey("modelResponse")) event.actions().setSetModelResponse(values.get("modelResponse"));
        if (values.containsKey("output")) event.setOutput(values.get("output"));

        Map<String, Object> confirmations = map(values.get("confirmations"));
        if (!confirmations.keySet().equals(event.actions().requestedToolConfirmations().keySet())) throw invalid();
        Map<String, ToolConfirmation> restored = new HashMap<>();
        event.actions().requestedToolConfirmations().forEach((key, confirmation) ->
                restored.put(key, confirmation.toBuilder().payload(confirmations.get(key)).build()));
        event.actions().setRequestedToolConfirmations(restored);

        if (event.content().isPresent()) event.setContent(restoreContent(event.content().orElseThrow(), values.get("parts")));
        else if (!list(values.get("parts")).isEmpty()) throw invalid();
        event.actions().compaction().ifPresent(compaction -> event.actions().setCompaction(EventCompaction.builder()
                .startTimestamp(compaction.startTimestamp()).endTimestamp(compaction.endTimestamp())
                .compactedContent(restoreContent(compaction.compactedContent(), values.get("compactionParts"))).build()));
    }

    private static Map<String, Object> values(Event event) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> delta = new HashMap<>();
        event.actions().stateDelta().forEach((key, value) -> delta.put(key, value == State.REMOVED ? REMOVED_VALUE : value));
        result.put("stateDelta", delta);
        event.actions().agentState().ifPresent(value -> result.put("agentState", value));
        event.actions().setModelResponse().ifPresent(value -> result.put("modelResponse", value));
        event.output().ifPresent(value -> result.put("output", value));
        Map<String, Object> confirmations = new HashMap<>();
        event.actions().requestedToolConfirmations().forEach((key, value) -> confirmations.put(key, value.payload()));
        result.put("confirmations", confirmations);
        result.put("parts", event.content().flatMap(content -> content.parts()).orElse(List.of())
                .stream().map(AdkEventTypedValues::partValues).toList());
        event.actions().compaction().ifPresent(compaction -> result.put("compactionParts", compaction.compactedContent()
                .parts().orElse(List.of()).stream().map(AdkEventTypedValues::partValues).toList()));
        return result;
    }

    private static Content restoreContent(Content content, Object encoded) {
        List<?> parts = list(encoded);
        List<Part> originals = content.parts().orElse(List.of());
        if (parts.size() != originals.size()) throw invalid();
        if (parts.isEmpty()) return content;
        List<Part> result = new ArrayList<>(parts.size());
        for (int i = 0; i < parts.size(); i++) result.add(restorePart(originals.get(i), map(parts.get(i))));
        return content.toBuilder().parts(result).build();
    }

    private static Map<String, Object> partValues(Part part) {
        Map<String, Object> values = new HashMap<>();
        part.functionCall().flatMap(call -> call.args()).ifPresent(value -> values.put("functionCall", value));
        part.functionResponse().flatMap(response -> response.response()).ifPresent(value -> values.put("functionResponse", value));
        part.toolCall().flatMap(call -> call.args()).ifPresent(value -> values.put("toolCall", value));
        part.toolResponse().flatMap(response -> response.response()).ifPresent(value -> values.put("toolResponse", value));
        part.partMetadata().ifPresent(value -> values.put("metadata", value));
        return values;
    }

    private static Part restorePart(Part part, Map<String, Object> values) {
        if (!values.keySet().equals(partValues(part).keySet())) throw invalid();
        Part.Builder result = part.toBuilder();
        if (values.containsKey("functionCall")) result.functionCall(part.functionCall().orElseThrow().toBuilder()
                .args(map(values.get("functionCall"))).build());
        if (values.containsKey("functionResponse")) result.functionResponse(part.functionResponse().orElseThrow().toBuilder()
                .response(map(values.get("functionResponse"))).build());
        if (values.containsKey("toolCall")) result.toolCall(part.toolCall().orElseThrow().toBuilder()
                .args(map(values.get("toolCall"))).build());
        if (values.containsKey("toolResponse")) result.toolResponse(part.toolResponse().orElseThrow().toBuilder()
                .response(map(values.get("toolResponse"))).build());
        if (values.containsKey("metadata")) result.partMetadata(map(values.get("metadata")));
        return result.build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?>)) throw invalid();
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?> list)) throw invalid();
        return list;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("ADK event typed values are invalid");
    }
}
