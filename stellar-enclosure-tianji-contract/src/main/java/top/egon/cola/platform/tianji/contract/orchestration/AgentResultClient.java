package top.egon.cola.platform.tianji.contract.orchestration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;

/** Authenticated delivery to a Process Inbox; receiving a receipt does not imply APPLIED. */
public interface AgentResultClient {
    String RECEIVE_SCOPE = "tianji:process:agent-result:receive";
    AgentResultReceipt receive(AgentTerminalEvent event);

    default AgentResultReceipt receiveV2(@NotNull @Valid AgentTerminalEventV2 event) {
        throw new UnsupportedOperationException("Agent Runtime v2 results are not supported by this client");
    }
}
