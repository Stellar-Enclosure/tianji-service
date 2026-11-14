package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
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
    default WorkerExecutionReceipt executeAutomation(@Valid AutomationWorkerTaskDTO task,
                                                      @Valid AutomationWorkerHandlerRegistration registration) {
        throw new IllegalStateException("Typed worker storage unavailable");
    }
}
