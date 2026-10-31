package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Private owned-source status query; it contains no tenant, permission or source-body claim. */
public record GetMemoryPreferenceIntentQuery(MemoryOwnerDTO owner, String scopeId, String runId) {
    public GetMemoryPreferenceIntentQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); runId = MemoryChecks.identifier(runId, 64);
        if (owner.type() != MemoryOwnerTypeEnum.USER) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "GetMemoryPreferenceIntentQuery[redacted]"; }
}
