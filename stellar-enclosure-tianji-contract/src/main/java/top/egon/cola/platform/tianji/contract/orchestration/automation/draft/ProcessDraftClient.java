package top.egon.cola.platform.tianji.contract.orchestration.automation.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Owner-scoped draft operations. Authentication and transport are supplied by the SDK. */
public interface ProcessDraftClient {

    DraftSummaryDTO create(@NotNull @Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}") String definitionKey,
                           @NotNull @Valid CreateProcessDraftCommand command);

    ProcessDraftDTO get(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String draftId,
                        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") String commandId);

    DraftMutationResultDTO save(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String draftId,
                                @NotNull @Valid SaveProcessDraftCommand command);

    /** A creation command lookup may not be combined with a definition, state, or cursor filter. */
    ProcessDraftPageDTO list(@Pattern(regexp = "[A-Za-z_][A-Za-z0-9_.-]{0,127}") String definitionKey,
                             @Pattern(regexp = "EDITING|PUBLISHED|DISCARDED") String state, @Min(1) @Max(100) Integer limit,
                             @Size(min = 1, max = 2048) String cursor,
                             @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") String creationCommandId);

    DraftMutationResultDTO discard(@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String draftId,
                                   @NotNull @Valid DiscardProcessDraftCommand command);

    DraftValidationResultDTO validate(@NotNull @Valid ValidateProcessDraftQuery query);

    DraftPublicationVO publish(@NotNull @Valid PublishProcessDraftCommand command);
}
