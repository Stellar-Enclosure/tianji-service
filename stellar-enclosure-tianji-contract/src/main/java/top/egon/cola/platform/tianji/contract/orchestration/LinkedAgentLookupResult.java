package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

/** Distinguishes an absent fence from transport or endpoint failure. */
public record LinkedAgentLookupResult(LinkedAgentLookupQuery query, boolean found, LinkedAgentSubmissionDTO submission) {
    public LinkedAgentLookupResult {
        query = Objects.requireNonNull(query, "query");
        if (found != (submission != null)) throw OrchestrationChecks.invalid();
    }
}
