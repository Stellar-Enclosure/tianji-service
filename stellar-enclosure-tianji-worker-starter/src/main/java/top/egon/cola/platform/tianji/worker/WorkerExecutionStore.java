package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import java.time.Duration;

/** Transactional handlers commit their business mutation and receipt atomically; other modes require durable intent and replay. */
public interface WorkerExecutionStore {
    long tenantId();
    Duration deadlineSafetyMargin();
    WorkerExecutionReceipt execute(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler);

    /** A store must opt in only after checking its durable non-transactional intent schema. */
    default void requireNonTransactional() { throw new UnsupportedOperationException("Non-transactional workers are not supported"); }

    default WorkerExecutionReceipt executeNonTransactional(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor,
                                                            String targetId, NonTransactionalWorkerHandler handler) {
        throw new UnsupportedOperationException("Non-transactional workers are not supported");
    }
}
