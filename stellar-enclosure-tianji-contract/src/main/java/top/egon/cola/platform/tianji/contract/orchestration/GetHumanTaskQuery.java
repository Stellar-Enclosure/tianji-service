package top.egon.cola.platform.tianji.contract.orchestration;

public record GetHumanTaskQuery(String taskId) {
    public GetHumanTaskQuery { taskId = OrchestrationChecks.id(taskId, 255); }
}
