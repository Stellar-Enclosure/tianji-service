package top.egon.cola.platform.tianji.process.server.release.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_REQUEST_TOO_LARGE;

@Slf4j
@Validated
@Component("processDraftContentCodec")
public class ProcessDraftContentCodec {
    private static final int MAX_SAVE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> CONTENT_FIELDS = Set.of("models", "inputSchemaSource", "outputSchemaSource",
            "nodeBindingsSource", "eventRoutesSource");
    private static final Set<String> MODEL_FIELDS = Set.of("type", "definitionKey", "xml");
    private static final TypeReference<List<AutomationNodeBindingVO>> BINDINGS = new TypeReference<>() { };
    private static final TypeReference<List<EventRouteVO>> ROUTES = new TypeReference<>() { };
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(32).maxNameLength(512).maxStringLength(MAX_SAVE_BYTES)
                    .maxNumberLength(25).maxTokenCount(200_000).build()).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    public static void validateCreateWire(byte[] body) {
        JsonNode root = tree(body);
        if (!root.isObject()) throw invalid();
        Set<String> fields = Set.copyOf(root.propertyNames());
        if (!Set.of("commandId", "title").equals(fields) && !Set.of("commandId", "title", "baseReleaseVersion").equals(fields)) {
            throw invalid();
        }
        validTitle(root.path("title"));
        commandId(root.path("commandId"));
        if (root.hasNonNull("baseReleaseVersion")
                && !string(root.path("baseReleaseVersion")).matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid();
    }

    public static void validateSaveWire(byte[] body) {
        JsonNode root = tree(body);
        exact(root, Set.of("commandId", "expectedRevision", "title", "content"));
        commandId(root.path("commandId"));
        validTitle(root.path("title"));
        revision(root.path("expectedRevision"));
        shape(root.path("content"));
    }

    public static void validateDiscardWire(byte[] body) {
        JsonNode root = tree(body);
        exact(root, Set.of("commandId", "expectedRevision"));
        commandId(root.path("commandId"));
        revision(root.path("expectedRevision"));
    }

    static DraftContentVO decodeStored(String json) {
        try {
            var wire = shape(JSON.readTree(json));
            return JSON.treeToValue(wire, DraftContentVO.class);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("Stored draft content is invalid");
        }
    }

    public byte[] canonicalContent(@NotNull @Valid DraftContentVO content) {
        if (content == null) throw invalid();
        var map = new LinkedHashMap<String, Object>();
        var models = content.models();
        if (models != null) {
            for (var model : models) {
                if (!validEditorKey(model.definitionKey())) throw invalid();
            }
        }
        map.put("models", models == null ? null : models.stream().map(
                model -> Map.of("type", model.type(), "definitionKey", model.definitionKey(), "xml", model.xml())).toList());
        map.put("inputSchemaSource", content.inputSchemaSource());
        map.put("outputSchemaSource", content.outputSchemaSource());
        map.put("nodeBindingsSource", content.nodeBindingsSource());
        map.put("eventRoutesSource", content.eventRoutesSource());
        return boundedCanonical(map);
    }

    /** The editor boundary never calls this; only validation/publication parses the saved source. */
    public AutomationModelSourceVO decodeForPublish(@NotNull @Valid DraftContentVO content) {
        canonicalContent(content);
        try {
            var models = content.models().stream().map(value -> new AutomationModelSourceVO.ModelSourceVO(
                    value.type(), value.definitionKey(), value.xml())).toList();
            return new AutomationModelSourceVO("AUTOMATION_V2", models,
                    JSON.readValue(content.inputSchemaSource(), ProcessSchemaVO.class),
                    JSON.readValue(content.outputSchemaSource(), ProcessSchemaVO.class),
                    JSON.readValue(content.nodeBindingsSource(), BINDINGS),
                    JSON.readValue(content.eventRoutesSource(), ROUTES));
        } catch (RuntimeException malformed) {
            throw invalid();
        }
    }

    private static JsonNode shape(JsonNode content) {
        exact(content, CONTENT_FIELDS);
        var wire = new LinkedHashMap<String, Object>();
        for (String field : Set.of("inputSchemaSource", "outputSchemaSource", "nodeBindingsSource", "eventRoutesSource")) {
            wire.put(field, nullableString(content.path(field)));
        }
        JsonNode models = content.path("models");
        if (models.isNull()) {
            wire.put("models", null);
        } else {
            if (!models.isArray() || models.size() > 64) throw invalid();
            var entries = new ArrayList<Map<String, String>>();
            for (JsonNode asset : models) {
                exact(asset, MODEL_FIELDS);
                String type = string(asset.path("type"));
                String definitionKey = string(asset.path("definitionKey"));
                String xml = string(asset.path("xml"));
                if (!Set.of("BPMN", "DMN").contains(type) || !validEditorKey(definitionKey)) throw invalid();
                entries.add(Map.of("type", type, "definitionKey", definitionKey, "xml", xml));
            }
            wire.put("models", entries);
        }
        boundedCanonical(wire);
        return content;
    }

    private static byte[] boundedCanonical(Map<String, Object> content) {
        // The envelope was bounded and each leaf UTF-8 checked. Canonical serialization may be larger than its decoded source.
        byte[] bytes = ProcessTypedJson.canonicalBytes(content, 32 * 1024 * 1024);
        if (bytes.length > DraftContentVO.MAX_CONTENT_BYTES) {
            throw new AutomationOperationException(TIANJI_REQUEST_TOO_LARGE);
        }
        return bytes;
    }

    static JsonNode tree(byte[] body) {
        if (body == null || body.length == 0 || body.length > MAX_SAVE_BYTES) throw invalid();
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            if (text.charAt(0) == '\uFEFF') throw invalid();
            var root = JSON.readTree(text);
            if (root == null || !root.isObject()) throw invalid();
            return root;
        } catch (CharacterCodingException | RuntimeException malformed) {
            throw invalid();
        }
    }

    static void exact(JsonNode root, Set<String> keys) {
        if (root == null || !root.isObject() || !keys.equals(Set.copyOf(root.propertyNames()))) throw invalid();
    }

    static String string(JsonNode node) {
        if (!node.isString()) throw invalid();
        String text = node.stringValue();
        if (text.indexOf('\0') >= 0 || !StandardCharsets.UTF_8.newEncoder().canEncode(text)) throw invalid();
        return text;
    }

    static String nullableString(JsonNode node) {
        return node.isNull() ? null : string(node);
    }

    static long revision(JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() < 1 || node.longValue() > ProcessTypedJson.V2_MAX_WIRE_INTEGER) throw invalid();
        return node.longValue();
    }

    static String contentHash(byte[] bytes) {
        return ProcessTypedJson.sha256("AUTOMATION_DRAFT_CONTENT_V2", bytes);
    }

    private static void commandId(JsonNode node) {
        if (!string(node).matches(ProcessDraftService.COMMAND_ID)) throw invalid();
    }

    private static void validTitle(JsonNode node) {
        String title = string(node);
        if (title.isBlank() || !title.matches("[^\\p{Cc}]{1,128}")) throw invalid();
    }

    private static boolean validEditorKey(String value) {
        return value.codePointCount(0, value.length()) <= 128;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process draft command");
    }
}
