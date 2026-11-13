package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Typed worker commands scoped by the authenticated service client. */
public interface AutomationWorkerClient {
    AutomationWorkerAcquireDTO acquire(@NotNull @Valid AutomationWorkerAcquireCommand command);

    AutomationWorkerReportDTO complete(@NotNull @Valid AutomationWorkerCompletionCommand command);

    AutomationWorkerReportDTO fail(@NotNull @Valid AutomationWorkerFailureCommand command);
}
