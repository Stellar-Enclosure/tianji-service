package top.egon.cola.platform.tianji.contract.orchestration;

public record CaseInstanceQuery(String caseInstanceId) {
    public CaseInstanceQuery {
        caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
    }
}
