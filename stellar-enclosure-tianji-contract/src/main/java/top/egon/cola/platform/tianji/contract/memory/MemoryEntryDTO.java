package top.egon.cola.platform.tianji.contract.memory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A scoped revision projection. Forgotten/expired entries disclose no content or caller-declared source reference. */
public record MemoryEntryDTO(MemoryOwnerDTO owner, String scopeId, String memoryId, MemoryKindEnum kind, long revision, Long supersedesRevision,
                              MemoryStateEnum state, MemoryContentDTO content, String contentSha256, MemorySourceDTO source, BigDecimal confidence,
                              boolean confirmed, MemorySensitivityEnum sensitivity, String retentionPolicyRef, Instant effectiveAt, Instant expiresAt,
                              Instant createdAt, Instant updatedAt) {
    public MemoryEntryDTO {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128);
        kind = Objects.requireNonNull(kind, "kind"); state = Objects.requireNonNull(state, "state");
        contentSha256 = MemoryChecks.hash(contentSha256); confidence = MemoryChecks.confidence(confidence);
        sensitivity = Objects.requireNonNull(sensitivity, "sensitivity"); retentionPolicyRef = MemoryChecks.identifier(retentionPolicyRef, 256);
        effectiveAt = Objects.requireNonNull(effectiveAt, "effectiveAt"); createdAt = Objects.requireNonNull(createdAt, "createdAt"); updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 1 || supersedesRevision != null && (supersedesRevision < 1 || supersedesRevision >= revision)
                || updatedAt.isBefore(createdAt) || expiresAt != null && !expiresAt.isAfter(effectiveAt)
                || state == MemoryStateEnum.ACTIVE && !confirmed || state == MemoryStateEnum.CANDIDATE && confirmed) throw MemoryChecks.invalid();
        boolean redacted = state == MemoryStateEnum.FORGOTTEN || state == MemoryStateEnum.EXPIRED;
        if (redacted && (content != null || source != null) || !redacted && (content == null || source == null)) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryEntry[redacted]"; }
}
