package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;

public interface WorkerExecutionStore {
    WorkerExecutionReceipt execute(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler);
}
