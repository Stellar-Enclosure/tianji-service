package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;

@FunctionalInterface
public interface AutomationNonTransactionalWorkerHandler {
    @Valid AutomationWorkerBusinessResultDTO execute(@Valid AutomationWorkerExecutionContextVO context, String effectKey);
}
