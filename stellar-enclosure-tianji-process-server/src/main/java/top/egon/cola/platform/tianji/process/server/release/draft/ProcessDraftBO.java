package top.egon.cola.platform.tianji.process.server.release.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO.StateEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessDraftBO {
    @NotNull @Valid @ToString.Exclude private ProcessActor owner;
    @NotNull @Pattern(regexp = "pd[A-Za-z0-9_.:-]{0,126}") private String draftId;
    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}") private String definitionKey;
    @NotNull @Pattern(regexp = "[^\\p{Cc}]{1,128}") @ToString.Exclude private String title;
    private String baseReleaseVersion;
    private String baseSourceManifestSha256;
    @NotNull private StateEnum state;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) private long revision;
    @NotNull @Valid @ToString.Exclude private DraftContentVO content;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") private String contentSha256;
    private String publishedReleaseVersion;
    private Long publishedRevision;
    private String publishCommandId;
    private Instant publishedAt;
    private String publishedSourceSha256;
    private String publishedCompiledSha256;
    private String publishedDependencySha256;
    private String publishedCompilerVersion;
    @NotNull private String lastCommandId;
    @NotNull private Instant createdAt;
    @NotNull private Instant updatedAt;
}
