package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Read-only preparation selects an approved immutable binding without accepting configuration or input-body claims. */
public record PrepareMemoryPreferencesQuery(MemoryOwnerDTO owner, String scopeId, String runId, String sourceEventId) {
    public PrepareMemoryPreferencesQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128);
        runId = MemoryChecks.identifier(runId, 64); sourceEventId = MemoryChecks.identifier(sourceEventId, 128);
        if (owner.type() != MemoryOwnerTypeEnum.USER) throw MemoryChecks.invalid();
    }
    public String admissionId() { return MemoryChecks.sourceCommandId(owner, scopeId, runId, sourceEventId, "admission"); }
    @Override public String toString() { return "PrepareMemoryPreferencesQuery[redacted]"; }
}
