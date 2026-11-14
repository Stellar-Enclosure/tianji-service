package top.egon.cola.platform.tianji.process.server.release;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Instant;

import static top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionQuery.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationActivationBO {
    @Min(1) private long tenantId;
    @NotNull @Pattern(regexp = DEFINITION_KEY) private String definitionKey;
    @Min(1) private long revision;
    private Long previousRevision;
    @NotNull @Pattern(regexp = RELEASE_VERSION) private String releaseVersion;
    @NotNull private ProcessActor actor;
    @NotNull @Pattern(regexp = COMMAND_ID) private String commandId;
    @NotNull @Pattern(regexp = SHA256) private String requestSha256;
    @NotNull @Pattern(regexp = SHA256) private String sourceManifestSha256;
    @NotNull @Pattern(regexp = SHA256) private String compiledManifestSha256;
    @NotNull @Pattern(regexp = SHA256) private String dependencyManifestSha256;
    @NotNull @Valid private AutomationAdmissionProofVO proof;
    @NotNull private Instant activatedAt;

    public Long getExpectedActivationRevision() {
        return previousRevision == null ? 0L : previousRevision;
    }
}
