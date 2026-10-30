package top.egon.cola.platform.tianji.contract.orchestration;

/** A service identity submits, resolves and closes its own linked Agent occurrence. */
public interface LinkedAgentClient {
    String SUBMIT_SCOPE = "tianji:agent:linked:submit";
    String RESOLVE_SCOPE = "tianji:agent:linked:resolve";
    String CLOSE_SCOPE = "tianji:agent:linked:close";

    LinkedAgentStartResult submit(LinkedAgentStartCommand command);
    LinkedAgentLookupResult resolve(LinkedAgentLookupQuery query);
    LinkedAgentCloseResult close(LinkedAgentLookupQuery query);
}
