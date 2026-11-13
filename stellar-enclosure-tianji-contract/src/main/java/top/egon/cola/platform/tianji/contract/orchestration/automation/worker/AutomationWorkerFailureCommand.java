package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationWorkerFailureCommand {

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
    @NotNull
    private KindEnum kind;
    @NotNull @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private String errorCode;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[^\\p{Cc}]{1,255}") @ToString.Exclude
    private String businessReceiptId;

    @JsonIgnore
    @AssertTrue(message = "Invalid worker failure values")
    public boolean isFailureValid() {
        try {
            leaseTokenSha256();
            if (businessReceiptId != null) {
                AutomationWorkerBusinessResultDTO.requireBusinessReceiptId(businessReceiptId);
            }
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    public String leaseTokenSha256() {
        return AutomationWorkerTaskDTO.leaseTokenSha256(leaseToken);
    }

    public enum KindEnum {
        RETRYABLE, PERMANENT, UNKNOWN;

        @JsonValue
        public String wire() { return name(); }
    }
}
