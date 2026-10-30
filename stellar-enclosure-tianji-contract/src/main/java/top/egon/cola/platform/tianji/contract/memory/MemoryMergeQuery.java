package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Current candidate comparison, never a mutation. Fact mode scans by exclusive ID; semantic mode has no cursor. */
public record MemoryMergeQuery(MemoryOwnerDTO owner, String scopeId, String memoryId, long expectedRevision,
                               boolean semantic, String afterMemoryId, int limit, Double minimumScore) {
    public MemoryMergeQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128);
        afterMemoryId = MemoryChecks.optionalIdentifier(afterMemoryId, 128);
        if (expectedRevision < 1 || limit < 1 || limit > 10 || semantic && (afterMemoryId != null || minimumScore == null)
                || !semantic && minimumScore != null || minimumScore != null && (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 1)) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryMergeQuery[redacted]"; }
}
