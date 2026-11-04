package top.egon.cola.platform.tianji.contract.orchestration;

/** Owner-scoped process control; tenant and actor remain trusted transport facts. */
public interface ProcessControlClient {
    String READ_SCOPE = "tianji:process:read";
    String CANCEL_SCOPE = "tianji:process:cancel";
    String CONTROL_RESOURCE_KEY = "tianji.api.process.instance.control";
    String CANCEL_RESOURCE_KEY = "tianji.api.process.instance.cancel";
    String CONTROL_PATH = "/api/tianji/v1/process-instances/{instanceId}/control";
    String CANCEL_PATH = "/api/tianji/v1/process-instances/{instanceId}/cancellations";

    ProcessControlSnapshotDTO control(GetProcessInstanceQuery query);
    CancelProcessResult cancel(CancelProcessCommand command);
}
