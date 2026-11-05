package top.egon.cola.platform.tianji.contract.agent;

/** Reads Agent runs in an exact correlation window under the transport's current Agent owner. */
public interface AgentRunCorrelationClient {
    AgentRunPageDTO listByCorrelation(ListAgentRunsByCorrelationQuery query);
}
