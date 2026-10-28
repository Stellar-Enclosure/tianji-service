package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.util.Objects;

/** A persisted owner and run selected by the internal dispatcher. */
public record PendingAgentRun(TrustedAgentActor actor, GetAgentRunQuery query) {
    public PendingAgentRun {
        actor = Objects.requireNonNull(actor, "actor");
        query = Objects.requireNonNull(query, "query");
    }
}
