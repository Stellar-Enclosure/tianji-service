package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

public record AcquireWorkerTaskResult(WorkerAcquireStatusEnum status, WorkerTaskDTO task) {
    public AcquireWorkerTaskResult {
        status = Objects.requireNonNull(status, "status");
        if ((status == WorkerAcquireStatusEnum.ACQUIRED) != (task != null)) throw OrchestrationChecks.invalid();
    }
}
