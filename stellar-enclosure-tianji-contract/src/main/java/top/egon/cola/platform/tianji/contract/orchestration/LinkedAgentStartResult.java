package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;

/** Echoes the immutable link so a client can reject a mismatched response. */
public record LinkedAgentStartResult(LinkedAgentLookupQuery query, AgentRunDTO run) {
    public LinkedAgentStartResult {
        query = Objects.requireNonNull(query, "query");
        run = Objects.requireNonNull(run, "run");
    }
}
