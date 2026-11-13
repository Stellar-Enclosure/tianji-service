package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class NodeRetryCommand {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String expectedVersion;
    @NotBlank @Size(max = 512) @ToString.Exclude
    private String reason;
    @Pattern(regexp = "[^\\p{Cc}]{1,255}") @ToString.Exclude
    private String businessReceiptRef;
}
