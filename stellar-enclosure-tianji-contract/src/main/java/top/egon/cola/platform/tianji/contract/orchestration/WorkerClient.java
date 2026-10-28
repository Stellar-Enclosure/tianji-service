package top.egon.cola.platform.tianji.contract.orchestration;

/** Worker operations use the authenticated service identity and never select a tenant from a command. */
public interface WorkerClient {
    AcquireWorkerTaskResult acquire(AcquireWorkerTaskCommand command);
    WorkerReceiptDTO complete(CompleteWorkerTaskCommand command);
    WorkerReceiptDTO fail(FailWorkerTaskCommand command);
}
