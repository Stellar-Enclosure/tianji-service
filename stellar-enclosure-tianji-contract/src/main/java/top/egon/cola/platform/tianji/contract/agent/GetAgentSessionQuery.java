package top.egon.cola.platform.tianji.contract.agent;

public record GetAgentSessionQuery(String sessionId) {
    public GetAgentSessionQuery { sessionId = ContractChecks.sessionId(sessionId); }
}
