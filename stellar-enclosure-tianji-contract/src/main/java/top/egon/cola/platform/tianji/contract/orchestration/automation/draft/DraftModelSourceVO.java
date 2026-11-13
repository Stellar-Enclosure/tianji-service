package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.util.Map;

/** An editor fragment. Model parsing occurs only when the host validates or publishes a draft. */
public record DraftModelSourceVO(
        @JsonProperty(required = true) @NotNull String type,
        @JsonProperty(required = true) @NotNull String definitionKey,
        @JsonProperty(required = true) @NotNull String xml) {

    public DraftModelSourceVO {
        if ((!"BPMN".equals(type) && !"DMN".equals(type)) || definitionKey == null || xml == null) {
            throw new IllegalArgumentException("Invalid automation draft model");
        }
        ProcessTypedJson.canonicalBytes(Map.of("type", type, "definitionKey", definitionKey, "xml", xml),
                DraftContentVO.MAX_CONTENT_BYTES);
    }

    @Override
    public String toString() {
        return "DraftModelSourceVO[type=" + type + "]";
    }
}
