package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Candidate review is a bounded fact query and never performs semantic retrieval. */
public record MemoryCandidateQuery(MemoryOwnerDTO owner, String scopeId, MemoryKindEnum kind, String afterMemoryId, int limit) {
    public MemoryCandidateQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); afterMemoryId = MemoryChecks.optionalIdentifier(afterMemoryId, 128);
        if (limit < 1 || limit > 50) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryCandidateQuery[redacted]"; }
}
