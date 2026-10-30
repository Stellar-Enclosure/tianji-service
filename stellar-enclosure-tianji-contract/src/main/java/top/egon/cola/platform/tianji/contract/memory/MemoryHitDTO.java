package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** Retrieval scores are transient ranking data, not fact confidence or source authority. */
public record MemoryHitDTO(MemoryEntryDTO entry, Double score) {
    public MemoryHitDTO {
        entry = Objects.requireNonNull(entry, "entry");
        if (entry.state() != MemoryStateEnum.ACTIVE || score != null && (!Double.isFinite(score) || score < 0 || score > 1)) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryHit[redacted]"; }
}
