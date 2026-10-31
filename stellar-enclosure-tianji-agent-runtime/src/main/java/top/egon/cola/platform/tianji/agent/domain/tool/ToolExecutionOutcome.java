package top.egon.cola.platform.tianji.agent.domain.tool;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/** Typed transport result. An unknown write carries no invented receipt or automatic-retry permission. */
public record ToolExecutionOutcome(Status status, Map<String, Object> output, String businessReceiptId, String errorCode, BigDecimal actualCost) {
    public enum Status { SUCCEEDED, REJECTED, FAILED_KNOWN, UNKNOWN, AWAITING_APPROVAL }
    public ToolExecutionOutcome {
        status = Objects.requireNonNull(status, "status"); output = Map.copyOf(output);
        if (output.size() > 16 || businessReceiptId != null && (!reference(businessReceiptId) || status != Status.SUCCEEDED)
                || errorCode != null && !errorCode.matches("[A-Z][A-Z0-9_]{0,63}") || status == Status.SUCCEEDED && errorCode != null
                || (status == Status.REJECTED || status == Status.FAILED_KNOWN || status == Status.UNKNOWN) && errorCode == null
                || status != Status.SUCCEEDED && !output.isEmpty() || status == Status.AWAITING_APPROVAL && errorCode != null
                || actualCost != null && (actualCost.signum() < 0 || actualCost.scale() > 8 || actualCost.compareTo(new BigDecimal("10000000000000000")) >= 0)) throw invalid();
    }
    public ToolExecutionOutcome validated(ToolDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (status == Status.SUCCEEDED) {
            if (descriptor.policy().risk() != ToolPolicy.Risk.READ_ONLY && businessReceiptId == null) throw invalid();
            if (descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY && businessReceiptId != null) throw invalid();
            return new ToolExecutionOutcome(status, descriptor.output().values(output), businessReceiptId, errorCode, actualCost);
        }
        if (status == Status.AWAITING_APPROVAL && descriptor.policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED) throw invalid();
        return this;
    }
    @Override public String toString() { return "ToolExecutionOutcome[redacted]"; }
    private static boolean reference(String value) { return !value.isBlank() && value.equals(value.trim()) && value.length() <= 255 && StandardCharsets.UTF_8.newEncoder().canEncode(value) && value.chars().noneMatch(Character::isISOControl); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool execution outcome is inconsistent"); }
}
