package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Operations on immutable releases, admitted instances, node executions, and technical human tasks. */
public interface ProcessAutomationClient {
    AutomationReleaseDTO getRelease(@NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}") String releaseVersion);

    AutomationInstanceDTO start(@NotNull @Valid AutomationStartCommand command);

    AutomationInstanceDTO getInstance(@NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") String instanceId);

    ProcessNodeExecutionPageDTO listNodes(@NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") String instanceId,
                                          @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,254}") String nodeId,
                                          @Pattern(regexp = "[A-Za-z0-9_-]+") String cursor, @Min(1) @Max(100) Integer limit);

    EngineHumanTaskDTO getTask(@NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") String taskId);

    EngineHumanTaskCompletionDTO completeTask(@NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") String taskId,
                                               @NotNull @Valid CompleteEngineHumanTaskCommand command);

    NodeRetryResultDTO retryNode(@NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}") String instanceId,
                                  @NotNull @Valid NodeRetryCommand command);
}
