package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Explicit request-time candidate extraction from one owned completed run; no text, tenant or approval claims are accepted. */
public record ExtractMemoryPreferencesCommand(MemoryOwnerDTO owner, String scopeId, String runId,
                                              String sourceEventId, String bindingId, String policyRef) {
    public ExtractMemoryPreferencesCommand {
        owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); runId = MemoryChecks.identifier(runId, 64);
        sourceEventId = MemoryChecks.identifier(sourceEventId, 128); bindingId = MemoryChecks.identifier(bindingId, 128);
        policyRef = MemoryChecks.identifier(policyRef, 256);
        if (owner.type() != MemoryOwnerTypeEnum.USER || !policyRef.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}")) throw MemoryChecks.invalid();
    }

    /** A source preference owns one durable write key, including when content deduplication returns another canonical entry. */
    public String itemCommandId(String preferenceKey) {
        return MemoryChecks.sourceCommandId(owner, scopeId, runId, sourceEventId, preferenceKey);
    }
    public PrepareMemoryPreferencesQuery sourceQuery() { return new PrepareMemoryPreferencesQuery(owner, scopeId, runId, sourceEventId); }
    @Override public String toString() { return "ExtractMemoryPreferencesCommand[redacted]"; }
}
