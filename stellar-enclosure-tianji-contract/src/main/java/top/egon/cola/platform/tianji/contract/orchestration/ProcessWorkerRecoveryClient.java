package top.egon.cola.platform.tianji.contract.orchestration;

/** Owner-scoped Worker recovery. Tenant and actor remain trusted transport facts. */
public interface ProcessWorkerRecoveryClient {
    String WORKER_FAILURES_PATH = "/api/tianji/v1/process-instances/{instanceId}/worker-failures";
    String WORKER_RECOVERIES_PATH = "/api/tianji/v1/process-instances/{instanceId}/worker-recoveries";
    String WORKER_FAILURES_RESOURCE_KEY = "tianji.api.process.instance.worker-failures";
    String WORKER_RECOVERIES_RESOURCE_KEY = "tianji.api.process.instance.worker-recoveries";
    String WORKER_RECOVER_SCOPE = "tianji:process:worker-recover";

    ProcessWorkerFailuresDTO workerFailures(GetProcessInstanceQuery query);
    RecoverProcessWorkerResult recoverWorker(RecoverProcessWorkerCommand command);
}
