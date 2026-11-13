package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationReleaseDTO {

    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String sourceManifestSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String compiledManifestSha256;
    @NotBlank @Size(max = 128)
    private String compilerVersion;
    @NotNull @Valid @ToString.Exclude
    private AutomationModelSourceVO artifact;
    @NotNull @Valid
    private GraphVO graph;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String sourceDraftId;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long sourceDraftRevision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String dependencyManifestSha256;

    @JsonIgnore
    @AssertTrue(message = "Release graph does not match its source definition")
    public boolean isReleaseProjectionConsistent() {
        return artifact != null && graph != null && artifact.models().getFirst().definitionKey().equals(graph.definitionKey());
    }
}
