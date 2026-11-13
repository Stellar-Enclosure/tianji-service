package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.GraphVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class DraftValidationResultDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String draftId;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
    @JsonProperty(required = true)
    private boolean valid;
    @NotBlank @Size(max = 128)
    private String compilerVersion;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[0-9a-f]{64}")
    private String sourceManifestSha256;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid
    private GraphVO graph;
    @NotNull @Size(max = 128)
    private List<@NotNull @Valid ValidationIssueVO> issues;

    @JsonIgnore
    @AssertTrue(message = "Validation outcome does not match its projection")
    public boolean isValidationOutcomeConsistent() {
        if (issues == null) {
            return false;
        }
        return valid ? sourceManifestSha256 != null && graph != null && issues.isEmpty()
                : sourceManifestSha256 == null && graph == null;
    }

    public record ValidationIssueVO(@NotNull String path, @NotBlank String code, @NotBlank String message,
                                    @JsonProperty(required = true) @JsonInclude(JsonInclude.Include.ALWAYS) String nodeId) {
        public ValidationIssueVO {
            if (path == null || code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("Invalid automation draft validation issue");
            }
        }
    }
}
