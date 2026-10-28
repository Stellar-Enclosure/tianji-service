package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.sessions.State;
import com.google.genai.types.Part;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Versioned ADK event storage; temporary state and credential requests are never persisted. */
final class AdkSessionEventCodec {
    private static final int MAX_BYTES = 1_048_576;
    private static final String ADK_VERSION = "1.13.0";
    private static final String REMOVED_VALUE = "__ADK_SENTINEL_REMOVED__";
    private static final ObjectMapper MAPPER = JsonBaseModel.getMapper().copy()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    private final int writeVersion;
    private final AdkEventTypedValues typedValues = new AdkEventTypedValues();

    AdkSessionEventCodec() { this(false); }

    AdkSessionEventCodec(boolean typedWrites) {
        this.writeVersion = typedWrites ? 2 : 1;
    }

    String encode(Event event) {
        try {
            validateEvent(event);
            Map<String, Object> delta = new HashMap<>(event.actions().stateDelta());
            delta.keySet().removeIf(key -> key.startsWith(State.TEMP_PREFIX));
            delta.values().forEach(value -> validateJsonValue(value, 0, true));
            event.output().ifPresent(value -> validateJsonValue(value, 0, false));
            event.actions().agentState().ifPresent(value -> validateJsonValue(value, 0, false));
            event.actions().setModelResponse().ifPresent(value -> validateJsonValue(value, 0, false));
            event.actions().requestedToolConfirmations().values()
                    .forEach(confirmation -> validateJsonValue(confirmation.payload(), 0, false));
            event.content().flatMap(content -> content.parts()).ifPresent(parts -> parts.forEach(AdkSessionEventCodec::validatePart));
            event.actions().compaction().flatMap(compaction -> compaction.compactedContent().parts())
                    .ifPresent(parts -> parts.forEach(AdkSessionEventCodec::validatePart));
            Event durableEvent = event.toBuilder().actions(event.actions().toBuilder().stateDelta(delta).build()).build();
            JsonNode raw = MAPPER.readTree(durableEvent.toJson());
            if (!(raw instanceof ObjectNode nativeEvent)
                    || !(nativeEvent.path("actions").path("stateDelta") instanceof ObjectNode state)
                    || writeVersion == 1 && !state.equals(MAPPER.valueToTree(delta))) throw invalid();
            Set<String> removed = new TreeSet<>();
            delta.forEach((key, value) -> {
                if (value == State.REMOVED) removed.add(key);
            });
            ObjectNode envelope = MAPPER.createObjectNode();
            envelope.put("codecVersion", writeVersion);
            envelope.put("adkVersion", ADK_VERSION);
            envelope.set("event", nativeEvent);
            ArrayNode keys = envelope.putArray("removedStateKeys");
            removed.forEach(keys::add);
            if (writeVersion == 2) envelope.set("typedValues", typedValues.encode(durableEvent));
            String stored = MAPPER.writeValueAsString(canonical(envelope));
            requireSize(stored);
            Event restored = decode(stored);
            if (!nativeEvent.equals(MAPPER.readTree(restored.toJson())) || !sameGenericValues(durableEvent, restored)) {
                throw invalid();
            }
            return stored;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    Event decode(String stored) {
        try {
            requireSize(stored);
            JsonNode envelope = MAPPER.readTree(stored);
            if (!(envelope instanceof ObjectNode root) || !root.path("codecVersion").isInt()
                    || (root.path("codecVersion").intValue() != 1 && root.path("codecVersion").intValue() != 2)
                    || !ADK_VERSION.equals(root.path("adkVersion").textValue())
                    || !(root.path("event") instanceof ObjectNode nativeEvent)
                    || !(root.path("removedStateKeys") instanceof ArrayNode removed)) throw invalid();
            int version = root.path("codecVersion").intValue();
            if (root.size() != (version == 1 ? 4 : 5) || version == 2 && !root.has("typedValues")) throw invalid();
            JsonNode state = nativeEvent.path("actions").path("stateDelta");
            if (!(state instanceof ObjectNode stateObject)) throw invalid();
            var fields = stateObject.fieldNames();
            while (fields.hasNext()) {
                if (fields.next().startsWith(State.TEMP_PREFIX)) throw invalid();
            }
            Set<String> seen = new HashSet<>();
            for (JsonNode node : removed) {
                if (!node.isTextual() || !seen.add(node.textValue()) || node.textValue().startsWith(State.TEMP_PREFIX)
                        || !REMOVED_VALUE.equals(state.path(node.textValue()).textValue())) throw invalid();
            }
            Event event = MAPPER.treeToValue(nativeEvent, Event.class);
            validateEvent(event);
            if (version == 2) typedValues.restore(event, root.get("typedValues"), seen);
            else for (String key : seen) event.actions().stateDelta().put(key, State.REMOVED);
            if (!nativeEvent.equals(MAPPER.readTree(event.toJson()))) throw invalid();
            return event;
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    boolean sameEvent(String stored, Event candidate) {
        try {
            Event existing = decode(stored);
            return MAPPER.readTree(existing.toJson()).equals(MAPPER.readTree(candidate.toJson()))
                    && sameGenericValues(existing, candidate);
        } catch (JsonProcessingException | RuntimeException failure) {
            throw invalid();
        }
    }

    private static void validateEvent(Event event) {
        if (event == null || event.id() == null || event.id().isBlank() || event.id().length() > 128
                || event.partial().orElse(false) || event.actions() == null
                || !event.actions().requestedAuthConfigs().isEmpty()) throw invalid();
    }

    private static boolean sameGenericValues(Event original, Event restored) {
        if (!original.actions().stateDelta().equals(restored.actions().stateDelta())
                || !original.actions().agentState().equals(restored.actions().agentState())
                || !original.actions().setModelResponse().equals(restored.actions().setModelResponse())
                || !original.actions().requestedToolConfirmations().equals(restored.actions().requestedToolConfirmations())
                || !original.output().equals(restored.output())) return false;
        List<Part> before = new java.util.ArrayList<>(original.content().flatMap(content -> content.parts()).orElse(List.of()));
        original.actions().compaction().flatMap(compaction -> compaction.compactedContent().parts()).ifPresent(before::addAll);
        List<Part> after = new java.util.ArrayList<>(restored.content().flatMap(content -> content.parts()).orElse(List.of()));
        restored.actions().compaction().flatMap(compaction -> compaction.compactedContent().parts()).ifPresent(after::addAll);
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            if (!Objects.equals(genericPartValues(before.get(i)), genericPartValues(after.get(i)))) return false;
        }
        return true;
    }

    private static void validatePart(Part part) {
        part.text().ifPresent(value -> validateJsonValue(value, 0, false));
        part.inlineData().flatMap(blob -> blob.data()).ifPresent(AdkSessionEventCodec::validateBytes);
        part.thoughtSignature().ifPresent(AdkSessionEventCodec::validateBytes);
        part.functionCall().flatMap(call -> call.args()).ifPresent(value -> validateJsonValue(value, 0, false));
        part.functionResponse().flatMap(response -> response.response()).ifPresent(value -> validateJsonValue(value, 0, false));
        part.toolCall().flatMap(call -> call.args()).ifPresent(value -> validateJsonValue(value, 0, false));
        part.toolResponse().flatMap(response -> response.response()).ifPresent(value -> validateJsonValue(value, 0, false));
        part.partMetadata().ifPresent(value -> validateJsonValue(value, 0, false));
    }

    private static List<Object> genericPartValues(Part part) {
        return List.of(part.functionCall().flatMap(call -> call.args()),
                part.functionResponse().flatMap(response -> response.response()),
                part.toolCall().flatMap(call -> call.args()), part.toolResponse().flatMap(response -> response.response()),
                part.partMetadata());
    }

    private static JsonNode canonical(JsonNode value) {
        if (value instanceof ObjectNode object) {
            ObjectNode result = MAPPER.createObjectNode();
            Set<String> names = new TreeSet<>();
            object.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(object.get(name))));
            return result;
        }
        if (value instanceof ArrayNode array) {
            ArrayNode result = MAPPER.createArrayNode();
            array.forEach(item -> result.add(canonical(item)));
            return result;
        }
        return value;
    }

    static void validateJsonValue(Object value, int depth, boolean allowRemoved) {
        if (depth > 64) throw invalid();
        if (value == null || value instanceof Boolean) return;
        if (value instanceof String text) {
            if (text.length() > MAX_BYTES) throw invalid();
            for (int i = 0; i < text.length(); i++) {
                char character = text.charAt(i);
                if (character == '\0' || Character.isLowSurrogate(character)
                        || Character.isHighSurrogate(character)
                        && (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(++i)))) throw invalid();
            }
            return;
        }
        if (value == State.REMOVED && allowRemoved) return;
        if (value instanceof Number number) {
            if ((number instanceof Double || number instanceof Float) && !Double.isFinite(number.doubleValue())) throw invalid();
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
                    || number instanceof Float || number instanceof Double || number instanceof java.math.BigDecimal
                    || number instanceof java.math.BigInteger) return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) throw invalid();
                validateJsonValue(key, depth + 1, false);
                validateJsonValue(entry.getValue(), depth + 1, false);
            }
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> validateJsonValue(item, depth + 1, false));
            return;
        }
        throw invalid();
    }

    private static void requireSize(String value) {
        if (value == null || value.length() > MAX_BYTES || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw invalid();
        }
    }

    private static void validateBytes(byte[] value) {
        if (value.length > MAX_BYTES) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("ADK session event is invalid or cannot be stored safely");
    }
}
