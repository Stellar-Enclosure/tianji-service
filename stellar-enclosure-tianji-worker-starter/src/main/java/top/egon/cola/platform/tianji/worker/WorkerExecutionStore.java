package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import java.time.Duration;

/** Implementations must commit the business mutation and its receipt atomically before returning. */
public interface WorkerExecutionStore {
    long tenantId();
    Duration deadlineSafetyMargin();
    WorkerExecutionReceipt execute(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler);
}
