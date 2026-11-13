package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Metadata for a host-authorized immutable result artifact. */
public record AgentResultArtifactRefVO(
        @NotNull String resourceKey,
        @NotNull String contentSha256,
        @NotNull String mediaType,
        @JsonProperty(required = true) @Min(0) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long sizeBytes) {

    public AgentResultArtifactRefVO {
        if (!ProcessTypedJson.isId(resourceKey) || !ProcessTypedJson.isSha256(contentSha256)
                || mediaType == null || !mediaType.matches("[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}")
                || sizeBytes < 0 || sizeBytes > ProcessTypedJson.V2_MAX_WIRE_INTEGER) {
            throw new IllegalArgumentException("Invalid Agent result artifact reference");
        }
    }

    public Map<String, Object> toWire() {
        return Map.of("resourceKey", resourceKey, "contentSha256", contentSha256, "mediaType", mediaType, "sizeBytes", sizeBytes);
    }
}
