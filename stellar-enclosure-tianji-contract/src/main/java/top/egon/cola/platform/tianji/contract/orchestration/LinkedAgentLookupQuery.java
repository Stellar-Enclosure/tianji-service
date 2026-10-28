package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

/** Identifies one linked submission within the authenticated service's scope. */
public record LinkedAgentLookupQuery(String idempotencyKey, AgentProcessLink link) {
    public LinkedAgentLookupQuery {
        idempotencyKey = OrchestrationChecks.command(idempotencyKey);
        link = Objects.requireNonNull(link, "link");
    }
}
