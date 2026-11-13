package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class DraftCommandReceiptDTO {

    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull
    private OperationEnum operation;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String requestSha256;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long resultRevision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String resultContentSha256;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @NotNull
    private Instant completedAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation draft receipt")
    public boolean isReceiptConsistent() {
        return operation != null && (operation == OperationEnum.PUBLISH) == (releaseVersion != null)
                && DraftSummaryDTO.utcMicros(completedAt);
    }

    public enum OperationEnum {
        CREATE, SAVE, DISCARD, PUBLISH;

        @JsonValue
        public String wire() {
            return name();
        }
    }
}
