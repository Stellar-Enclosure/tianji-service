package top.egon.cola.platform.tianji.contract.agent;

/** The cursor is exclusive and belongs to the specified run. */
public record AgentEventsQuery(String runId, long afterSequence, int limit) {
    public AgentEventsQuery {
        runId = ContractChecks.required(runId, "runId", 128);
        if (afterSequence < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("event cursor or limit is invalid");
        }
    }
}
