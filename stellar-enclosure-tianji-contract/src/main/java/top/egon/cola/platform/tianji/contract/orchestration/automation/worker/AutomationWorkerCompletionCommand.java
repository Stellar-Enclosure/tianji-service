package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationWorkerCompletionCommand {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String taskId;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String activityOccurrenceId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") @ToString.Exclude
    private String leaseToken;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String effectKey;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") @ToString.Exclude
    private String businessReceiptId;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> output;

    @JsonIgnore
    @AssertTrue(message = "Invalid worker completion values")
    public boolean isCompletionValid() {
        try {
            leaseTokenSha256();
            AutomationWorkerBusinessResultDTO.requireBusinessReceiptId(businessReceiptId);
            AutomationWorkerBusinessResultDTO.canonicalData(output);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    public String leaseTokenSha256() {
        return AutomationWorkerTaskDTO.leaseTokenSha256(leaseToken);
    }
}
