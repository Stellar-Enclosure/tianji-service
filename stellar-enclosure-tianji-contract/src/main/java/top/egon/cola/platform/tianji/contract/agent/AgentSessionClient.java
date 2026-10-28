package top.egon.cola.platform.tianji.contract.agent;

/** Engine-neutral management of the caller's own Agent sessions. */
public interface AgentSessionClient {
    AgentSessionDTO createSession(CreateAgentSessionCommand command);
    AgentSessionDTO getSession(GetAgentSessionQuery query);
    AgentSessionListDTO listSessions();
    void deleteSession(DeleteAgentSessionCommand command);
}
