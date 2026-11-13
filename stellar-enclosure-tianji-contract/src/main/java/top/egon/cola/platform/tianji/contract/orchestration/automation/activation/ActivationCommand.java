package top.egon.cola.platform.tianji.contract.orchestration.automation.activation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ActivationCommand {

    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    /** Zero is the explicit precondition for the first activation. */
    @NotNull @Min(0) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private Long expectedActivationRevision;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
}
