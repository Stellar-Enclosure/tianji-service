package top.egon.cola.platform.tianji.agent.domain.tool;

/** Immutable operator policy metadata; constructing it is neither a current permission decision nor an approval proof. */
public record ToolPolicy(Risk risk, String actionKey, String executionResourceKey, String bindingSha256, String approvalPolicyRef) {
    public enum Risk { READ_ONLY, IDEMPOTENT_WRITE, APPROVAL_REQUIRED }
    public ToolPolicy {
        if (risk == null || !key(actionKey) || !key(executionResourceKey) || bindingSha256 == null || !bindingSha256.matches("[0-9a-f]{64}")
                || (risk == Risk.APPROVAL_REQUIRED) != (approvalPolicyRef != null) || approvalPolicyRef != null && !key(approvalPolicyRef)) throw new IllegalArgumentException("Tool policy is invalid");
    }
    static boolean key(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}"); }
}
