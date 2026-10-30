package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

public record MemoryMergeHitDTO(MemoryHitDTO hit, MemoryMergeRelationEnum relation) {
    public MemoryMergeHitDTO { hit = Objects.requireNonNull(hit, "hit"); relation = Objects.requireNonNull(relation, "relation"); }
    @Override public String toString() { return "MemoryMergeHit[redacted]"; }
}
