package top.egon.cola.platform.tianji.contract.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record MemoryRetrievalPageDTO(MemoryOwnerDTO owner, String scopeId, List<MemoryHitDTO> items, String nextAfterMemoryId) {
    public MemoryRetrievalPageDTO {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); items = List.copyOf(items);
        nextAfterMemoryId = MemoryChecks.optionalIdentifier(nextAfterMemoryId, 128);
        if (items.size() > 50 || nextAfterMemoryId != null && (items.isEmpty() || !nextAfterMemoryId.equals(items.getLast().entry().memoryId()))) throw MemoryChecks.invalid();
        var ids = new HashSet<String>();
        for (var item : items) if (!owner.equals(item.entry().owner()) || !scopeId.equals(item.entry().scopeId()) || !ids.add(item.entry().memoryId())) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryRetrievalPage[redacted]"; }
}
