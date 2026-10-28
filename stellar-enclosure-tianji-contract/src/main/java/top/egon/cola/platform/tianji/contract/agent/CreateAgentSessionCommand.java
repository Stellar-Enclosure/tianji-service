package top.egon.cola.platform.tianji.contract.agent;

/** The caller supplies a stable ID; repeating a create never replaces existing session data. */
public record CreateAgentSessionCommand(String sessionId) {
    public CreateAgentSessionCommand { sessionId = ContractChecks.sessionId(sessionId); }
}
