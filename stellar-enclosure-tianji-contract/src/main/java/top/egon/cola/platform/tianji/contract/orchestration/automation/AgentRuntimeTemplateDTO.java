package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.RuntimeProfileEnum;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AgentRuntimeTemplateDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String templateKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long templateVersion;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
    @NotNull
    private RuntimeProfileEnum runtimeProfile;
    @NotNull
    private Instant publishedAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid runtime template publication time")
    public boolean isPublishedAtValid() {
        return AgentTerminalEventV2.utcMicros(publishedAt);
    }
}
