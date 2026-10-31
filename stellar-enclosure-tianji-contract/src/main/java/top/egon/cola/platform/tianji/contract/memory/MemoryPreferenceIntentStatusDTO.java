package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** A null intent means that no extraction was requested for this owned source/scope; it is not an empty successful extraction. */
public record MemoryPreferenceIntentStatusDTO(MemoryOwnerDTO owner, String scopeId, String runId, MemoryPreferenceIntentDTO intent) {
    public MemoryPreferenceIntentStatusDTO {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); runId = MemoryChecks.identifier(runId, 64);
        if (owner.type() != MemoryOwnerTypeEnum.USER) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryPreferenceIntentStatus[redacted]"; }
}
