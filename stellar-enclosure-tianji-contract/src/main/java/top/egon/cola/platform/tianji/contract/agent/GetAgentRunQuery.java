package top.egon.cola.platform.tianji.contract.agent;

public record GetAgentRunQuery(String runId) {
    public GetAgentRunQuery {
        runId = ContractChecks.required(runId, "runId", 128);
    }
}
