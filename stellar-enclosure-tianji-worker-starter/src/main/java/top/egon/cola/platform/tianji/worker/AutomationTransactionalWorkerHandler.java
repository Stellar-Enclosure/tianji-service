package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcOperations;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerBusinessResultDTO;

@FunctionalInterface
public interface AutomationTransactionalWorkerHandler {
    @Valid AutomationWorkerBusinessResultDTO execute(@Valid AutomationWorkerExecutionContextVO context, JdbcOperations jdbc);
}
