package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class CreateProcessDraftCommand {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String title;
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String baseReleaseVersion;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
}
