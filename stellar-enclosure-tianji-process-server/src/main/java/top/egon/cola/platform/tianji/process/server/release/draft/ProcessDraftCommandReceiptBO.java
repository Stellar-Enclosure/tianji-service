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
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftCommandReceiptDTO.OperationEnum;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessDraftCommandReceiptBO {
    @NotNull @Valid @ToString.Exclude private ProcessActor actor;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") private String commandId;
    @NotNull private OperationEnum operation;
    @NotNull @Pattern(regexp = "pd[A-Za-z0-9_.:-]{0,126}") private String draftId;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") private String requestSha256;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) private long resultRevision;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") private String resultContentSha256;
    private String releaseVersion;
    @NotNull private Instant completedAt;
}
