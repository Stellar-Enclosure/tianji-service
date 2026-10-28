package top.egon.cola.platform.tianji.contract.agent;

import java.util.concurrent.Flow;

/** Engine-neutral Agent operations; implementations obtain identity from a trusted context. */
public interface AgentClient {
    AgentRunDTO start(StartAgentRunCommand command);
    default AgentRunPageDTO list(ListAgentRunsQuery query) {
        throw new UnsupportedOperationException("Agent run listing is not supported by this client");
    }
    AgentRunDTO get(GetAgentRunQuery query);
    AgentExecutionResult getResult(GetAgentRunQuery query);
    CancelAgentRunResult cancel(CancelAgentRunCommand command);
    Flow.Publisher<AgentEventDTO> events(AgentEventsQuery query);
}
