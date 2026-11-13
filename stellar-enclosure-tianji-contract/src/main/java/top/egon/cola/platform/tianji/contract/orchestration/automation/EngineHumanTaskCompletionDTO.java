package top.egon.cola.platform.tianji.contract.orchestration.automation;

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
public class EngineHumanTaskCompletionDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String taskId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull
    private StateEnum state;
    @NotNull
    private Instant completedAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid human task completion time")
    public boolean isCompletedAtValid() {
        return ProcessLifecycleEventV2.utcMicros(completedAt);
    }

    public enum StateEnum {
        COMPLETED;

        @JsonValue
        public String wire() { return name(); }
    }
}
