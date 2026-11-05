package top.egon.cola.platform.tianji.contract.orchestration;

/** Exact Process instance and optional navigation cursor. Owner is derived by the server. */
public record GetProcessAgentOutcomesQuery(String instanceId, String cursor) {
    public GetProcessAgentOutcomesQuery {
        instanceId = new GetProcessInstanceQuery(instanceId).instanceId();
        if (cursor != null && !cursor.matches("v1\\.[0-9a-f]{64}")) {
            throw OrchestrationChecks.invalid();
        }
    }
}
