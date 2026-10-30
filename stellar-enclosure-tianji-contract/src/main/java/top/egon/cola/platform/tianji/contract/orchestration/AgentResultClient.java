package top.egon.cola.platform.tianji.contract.orchestration;

/** Authenticated delivery to a Process Inbox; receiving a receipt does not imply APPLIED. */
public interface AgentResultClient {
    String RECEIVE_SCOPE = "tianji:process:agent-result:receive";
    AgentResultReceipt receive(AgentTerminalEvent event);
}
