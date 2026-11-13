package top.egon.cola.platform.tianji.contract.orchestration.automation.activation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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

/** Result of the specified activation command. Current activation is obtained from the separate query. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationActivationDTO {

    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}")
    private String definitionKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private Long previousRevision;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String commandId;
    @NotNull @Min(0) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private Long expectedActivationRevision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String sourceManifestSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String compiledManifestSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String dependencyManifestSha256;
    @NotNull
    private Instant activatedAt;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation activation receipt")
    public boolean isActivationConsistent() {
        return expectedActivationRevision != null && expectedActivationRevision >= 0
                && expectedActivationRevision < ProcessTypedJson.V2_MAX_WIRE_INTEGER
                && revision == expectedActivationRevision + 1
                && (revision == 1 ? previousRevision == null : expectedActivationRevision.equals(previousRevision))
                && utcMicros(activatedAt);
    }

    static boolean utcMicros(Instant value) {
        return value != null && value.getNano() % 1_000 == 0;
    }
}
