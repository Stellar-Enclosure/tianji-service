package top.egon.cola.platform.tianji.contract.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Creates or content-deduplicates an explicit intention. Existing facts are changed only through revision-checked correction. */
public record UpsertMemoryCommand(String commandId, MemoryOwnerDTO owner, String scopeId, String memoryId, MemoryKindEnum kind,
                                  long expectedRevision, MemoryContentDTO content, MemorySourceDTO source, BigDecimal confidence,
                                  boolean confirmed, MemorySensitivityEnum sensitivity, String retentionPolicyRef, Instant effectiveAt, Instant expiresAt) {
    public UpsertMemoryCommand {
        commandId = MemoryChecks.identifier(commandId, 128); owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128); kind = Objects.requireNonNull(kind, "kind");
        if (expectedRevision != 0) throw MemoryChecks.invalid();
        content = Objects.requireNonNull(content, "content"); source = Objects.requireNonNull(source, "source"); confidence = MemoryChecks.confidence(confidence);
        sensitivity = Objects.requireNonNull(sensitivity, "sensitivity"); retentionPolicyRef = MemoryChecks.identifier(retentionPolicyRef, 256);
        if (effectiveAt != null && expiresAt != null && !expiresAt.isAfter(effectiveAt)) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "UpsertMemoryCommand[redacted]"; }
}
