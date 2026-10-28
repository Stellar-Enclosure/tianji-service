package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;

public record LinkedAgentCloseResult(LinkedAgentLookupQuery query, LinkedAgentSubmissionDTO submission) {
    public LinkedAgentCloseResult {
        query = Objects.requireNonNull(query, "query");
        submission = Objects.requireNonNull(submission, "submission");
        if (!submission.closed()) throw OrchestrationChecks.invalid();
    }
}
