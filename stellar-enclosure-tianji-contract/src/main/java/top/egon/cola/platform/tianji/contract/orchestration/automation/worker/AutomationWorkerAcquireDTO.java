package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
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
public class AutomationWorkerAcquireDTO {

    @NotNull
    private StatusEnum status;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid
    private AutomationWorkerTaskDTO task;

    @JsonIgnore
    @AssertTrue(message = "Invalid worker acquisition result")
    public boolean isAcquisitionConsistent() {
        return status != null && (status == StatusEnum.ACQUIRED) == (task != null);
    }

    public enum StatusEnum {
        ACQUIRED, EMPTY;

        @JsonValue
        public String wire() { return name(); }
    }
}
