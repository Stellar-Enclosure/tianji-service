package top.egon.cola.platform.tianji.contract.memory;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Reference-only intent progress. APPLIED counts processed commands, including content-deduplicated receipts. */
public record MemoryPreferenceIntentDTO(String intentId, String bindingId, String policyRef, MemoryPreferenceIntentStateEnum state, long revision,
        int nextIndex, Integer totalCount, int attemptCount, String reasonCode, Instant createdAt, Instant updatedAt, Instant closedAt) {
    public MemoryPreferenceIntentDTO {
        intentId = MemoryChecks.identifier(intentId, 36); bindingId = MemoryChecks.identifier(bindingId, 128); policyRef = MemoryChecks.identifier(policyRef, 256);
        state = Objects.requireNonNull(state, "state"); createdAt = Objects.requireNonNull(createdAt, "createdAt"); updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        try { if (intentId.length() != 36 || !UUID.fromString(intentId).toString().equals(intentId)) throw MemoryChecks.invalid(); }
        catch (IllegalArgumentException malformed) { throw MemoryChecks.invalid(); }
        if (!policyRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}") || revision < 1 || nextIndex < 0 || nextIndex > 32
                || totalCount == null && nextIndex != 0 || totalCount != null && (totalCount < nextIndex || totalCount > 32) || attemptCount < 0 || attemptCount > 100
                || updatedAt.isBefore(createdAt) || state.terminal() != (closedAt != null) || closedAt != null && (closedAt.isBefore(createdAt) || closedAt.isAfter(updatedAt))
                || state == MemoryPreferenceIntentStateEnum.APPLIED && (totalCount == null || totalCount < 1 || nextIndex != totalCount)) throw MemoryChecks.invalid();
        if (reasonCode != null) reasonCode = MemoryChecks.reason(reasonCode);
    }
    @Override public String toString() { return "MemoryPreferenceIntent[redacted]"; }
}
