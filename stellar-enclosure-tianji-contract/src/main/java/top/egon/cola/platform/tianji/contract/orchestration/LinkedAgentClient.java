package top.egon.cola.platform.tianji.contract.orchestration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeDTO;

/** A service identity submits, resolves and closes its own linked Agent occurrence. */
public interface LinkedAgentClient {
    String SUBMIT_SCOPE = "tianji:agent:linked:submit";
    String RESOLVE_SCOPE = "tianji:agent:linked:resolve";
    String CLOSE_SCOPE = "tianji:agent:linked:close";

    LinkedAgentStartResult submit(LinkedAgentStartCommand command);

    default LinkedAgentRuntimeDTO submitRuntime(@NotNull @Valid LinkedAgentRuntimeCommand command) {
        throw new UnsupportedOperationException("Linked Agent Runtime v2 is not supported by this client");
    }

    LinkedAgentLookupResult resolve(LinkedAgentLookupQuery query);
    LinkedAgentCloseResult close(LinkedAgentLookupQuery query);
}
