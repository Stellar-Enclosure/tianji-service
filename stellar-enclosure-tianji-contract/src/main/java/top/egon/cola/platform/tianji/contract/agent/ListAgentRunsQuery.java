package top.egon.cola.platform.tianji.contract.agent;

/** Opaque cursor is exclusive; tenant and owner are derived from the authenticated caller. */
public record ListAgentRunsQuery(String cursor, int limit) {
    public ListAgentRunsQuery {
        cursor = ContractChecks.optional(cursor, "cursor", 256);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("run page limit is invalid");
    }
}
