package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
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
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DraftMutationResultDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String draftId;
    @NotNull
    private StateEnum state;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
    @NotNull
    private Instant updatedAt;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;

    @JsonIgnore
    @AssertTrue(message = "Draft mutation timestamp must have microsecond precision")
    public boolean isUpdateTimeValid() {
        return DraftSummaryDTO.utcMicros(updatedAt);
    }

    public enum StateEnum {
        EDITING, DISCARDED;

        @JsonValue
        public String wire() {
            return name();
        }
    }
}
