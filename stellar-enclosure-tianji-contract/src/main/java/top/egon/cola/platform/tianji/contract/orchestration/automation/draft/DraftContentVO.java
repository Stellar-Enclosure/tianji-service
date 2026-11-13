package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Preserves the original XML and JSON editor strings, including incomplete text and whitespace. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DraftContentVO(
        @JsonProperty(required = true) @Size(max = 64) List<@NotNull @Valid DraftModelSourceVO> models,
        @JsonProperty(required = true) String inputSchemaSource,
        @JsonProperty(required = true) String outputSchemaSource,
        @JsonProperty(required = true) String nodeBindingsSource,
        @JsonProperty(required = true) String eventRoutesSource) {

    public static final int MAX_CONTENT_BYTES = 4 * 1024 * 1024;

    public DraftContentVO {
        if (models != null) {
            if (models.size() > 64 || models.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Invalid automation draft content");
            }
            models = List.copyOf(models);
        }
        var wire = new LinkedHashMap<String, Object>();
        wire.put("models", models == null ? null : models.stream().map(model -> Map.of(
                "type", model.type(), "definitionKey", model.definitionKey(), "xml", model.xml())).toList());
        wire.put("inputSchemaSource", inputSchemaSource);
        wire.put("outputSchemaSource", outputSchemaSource);
        wire.put("nodeBindingsSource", nodeBindingsSource);
        wire.put("eventRoutesSource", eventRoutesSource);
        ProcessTypedJson.canonicalBytes(wire, MAX_CONTENT_BYTES);
    }

    @Override
    public String toString() {
        return "DraftContentVO";
    }
}
