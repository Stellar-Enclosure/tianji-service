package top.egon.cola.platform.tianji.contract.agent;

/** Small pages keep bounded provenance metadata within the existing Agent transport response limit. */
public record AgentContextSnapshotsQuery(String runId, int afterModelCall, int limit) {
    public AgentContextSnapshotsQuery {
        runId = ContractChecks.contextIdentifier(runId, "runId", 64);
        if (afterModelCall < 0 || limit < 1 || limit > 5) throw new IllegalArgumentException("context snapshot page is invalid");
    }
}
