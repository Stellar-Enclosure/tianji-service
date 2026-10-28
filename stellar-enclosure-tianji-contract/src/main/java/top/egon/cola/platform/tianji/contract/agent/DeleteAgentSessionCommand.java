package top.egon.cola.platform.tianji.contract.agent;

public record DeleteAgentSessionCommand(String sessionId) {
    public DeleteAgentSessionCommand { sessionId = ContractChecks.sessionId(sessionId); }
}
