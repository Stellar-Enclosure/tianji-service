package top.egon.cola.platform.tianji.contract.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Unconfirmed entries remain separate from current confirmed evidence. */
public record MemoryCandidatePageDTO(MemoryOwnerDTO owner, String scopeId, List<MemoryEntryDTO> items, String nextAfterMemoryId) {
    public MemoryCandidatePageDTO {
        owner = Objects.requireNonNull(owner, "owner"); scopeId = MemoryChecks.identifier(scopeId, 128); items = List.copyOf(items); nextAfterMemoryId = MemoryChecks.optionalIdentifier(nextAfterMemoryId, 128);
        if (items.size() > 50 || nextAfterMemoryId != null && (items.isEmpty() || !nextAfterMemoryId.equals(items.getLast().memoryId()))) throw MemoryChecks.invalid();
        var ids = new HashSet<String>();
        for (var item : items) if (!owner.equals(item.owner()) || !scopeId.equals(item.scopeId()) || item.state() != MemoryStateEnum.CANDIDATE || item.confirmed() || !ids.add(item.memoryId())) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryCandidatePage[redacted]"; }
}
