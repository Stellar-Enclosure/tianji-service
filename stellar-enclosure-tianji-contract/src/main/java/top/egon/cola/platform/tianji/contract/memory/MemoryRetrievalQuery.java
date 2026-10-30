package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Null text lists current facts by exclusive memory-ID cursor; non-null text requests bounded semantic retrieval without a cursor. */
public record MemoryRetrievalQuery(MemoryOwnerDTO owner, String scopeId, MemoryKindEnum kind, String text, String afterMemoryId, int limit) {
    public MemoryRetrievalQuery {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128);
        afterMemoryId = MemoryChecks.optionalIdentifier(afterMemoryId, 128);
        if (limit < 1 || limit > 50 || text != null && (text.isBlank() || text.length() > 2000 || afterMemoryId != null)) throw MemoryChecks.invalid();
        if (text != null) text = MemoryChecks.text(text, 8000);
    }
    @Override public String toString() { return "MemoryRetrievalQuery[redacted]"; }
}
