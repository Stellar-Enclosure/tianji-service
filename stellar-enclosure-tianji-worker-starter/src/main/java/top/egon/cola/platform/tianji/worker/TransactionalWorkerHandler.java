package top.egon.cola.platform.tianji.worker;

import org.springframework.jdbc.core.JdbcOperations;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;

/** The handler's business writes must use the supplied local transaction and must not make external side effects. */
@FunctionalInterface
public interface TransactionalWorkerHandler {
    WorkerBusinessResultDTO execute(WorkerExecutionContext context, JdbcOperations jdbc);
}
