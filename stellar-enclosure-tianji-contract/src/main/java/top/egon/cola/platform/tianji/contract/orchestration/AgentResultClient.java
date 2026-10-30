package top.egon.cola.platform.tianji.contract.orchestration;

/** Authenticated delivery to a Process Inbox; receiving a receipt does not imply APPLIED. */
public interface AgentResultClient {
    AgentResultReceipt receive(AgentTerminalEvent event);
}
