package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationWorkerReportDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String taskId;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String activityOccurrenceId;
    @NotNull
    private OutcomeEnum outcome;
    @NotNull
    private Instant decidedAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid worker report time")
    public boolean isDecidedAtValid() {
        return WorkerEffectRecoveryVO.utcMicros(decidedAt);
    }

    public void requireCompletionOutcome() {
        if (outcome != OutcomeEnum.COMPLETED || !isDecidedAtValid()) {
            throw new IllegalArgumentException("Invalid worker completion receipt");
        }
    }

    public void requireFailureOutcome() {
        if (outcome == null || outcome == OutcomeEnum.COMPLETED || !isDecidedAtValid()) {
            throw new IllegalArgumentException("Invalid worker failure receipt");
        }
    }

    public enum OutcomeEnum {
        COMPLETED, UNKNOWN, PERMANENT, RETRY_SCHEDULED, NEEDS_MANUAL;

        @JsonValue
        public String wire() { return name(); }
    }
}
