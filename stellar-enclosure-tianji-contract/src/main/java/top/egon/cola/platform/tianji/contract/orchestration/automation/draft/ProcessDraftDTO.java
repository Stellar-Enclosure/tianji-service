package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO.StateEnum;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessDraftDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String draftId;
    @NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}")
    private String definitionKey;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String title;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String baseReleaseVersion;
    @NotNull
    private StateEnum state;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String contentSha256;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String publishedReleaseVersion;
    @NotNull
    private Instant createdAt;
    @NotNull
    private Instant updatedAt;
    @NotNull @Size(max = 4)
    private List<@NotNull String> availableActions;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[0-9a-f]{64}")
    private String baseSourceManifestSha256;
    @NotNull @Valid @ToString.Exclude
    private DraftContentVO content;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String lastCommandId;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid
    private DraftPublicationVO publication;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid
    private DraftCommandReceiptDTO commandReceipt;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation draft detail")
    public boolean isDraftProjectionConsistent() {
        return DraftSummaryDTO.validSummary(state, publishedReleaseVersion, createdAt, updatedAt, availableActions)
                && (baseReleaseVersion == null) == (baseSourceManifestSha256 == null)
                && (state == StateEnum.PUBLISHED) == (publication != null)
                && (publication == null || publicationMatchesCurrentDraft())
                && (commandReceipt == null || commandReceipt.getResultRevision() <= revision);
    }

    private boolean publicationMatchesCurrentDraft() {
        return publication.draftRevision() == revision && Objects.equals(publication.draftId(), draftId)
                && Objects.equals(publication.releaseVersion(), publishedReleaseVersion)
                && Objects.equals(publication.commandId(), lastCommandId) && Objects.equals(publication.publishedAt(), updatedAt);
    }
}
