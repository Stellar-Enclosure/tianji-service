package top.egon.cola.platform.tianji.contract.orchestration;

/** A service identity submits, resolves and closes its own linked Agent occurrence. */
public interface LinkedAgentClient {
    LinkedAgentStartResult submit(LinkedAgentStartCommand command);
    LinkedAgentLookupResult resolve(LinkedAgentLookupQuery query);
    LinkedAgentCloseResult close(LinkedAgentLookupQuery query);
}
