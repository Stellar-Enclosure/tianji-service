package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessNodeExecutionPageDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String instanceId;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @NotNull
    private Instant observedAt;
    @NotNull @Size(max = 100)
    private List<@NotNull @Valid ProcessNodeExecutionDTO> items;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9_-]+")
    private String nextCursor;

    @JsonIgnore
    @AssertTrue(message = "Invalid node observation time")
    public boolean isObservedAtValid() {
        return ProcessLifecycleEventV2.utcMicros(observedAt);
    }
}
