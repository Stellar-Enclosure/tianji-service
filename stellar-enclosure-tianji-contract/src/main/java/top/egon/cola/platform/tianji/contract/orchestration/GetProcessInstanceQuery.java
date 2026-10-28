package top.egon.cola.platform.tianji.contract.orchestration;

public record GetProcessInstanceQuery(String instanceId) {
    public GetProcessInstanceQuery {
        instanceId = OrchestrationChecks.id(instanceId, 255);
        if (instanceId.equals(".") || instanceId.equals("..")) throw OrchestrationChecks.invalid();
    }
}
