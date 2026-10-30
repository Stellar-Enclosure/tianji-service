package top.egon.cola.platform.tianji.contract.memory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** A current unconfirmed candidate and bounded active targets. A fact cursor can pass scanned nonmatching rows even on an empty page. */
public record MemoryMergePageDTO(MemoryEntryDTO candidate, boolean semantic, List<MemoryMergeHitDTO> items, String nextAfterMemoryId) {
    public MemoryMergePageDTO {
        candidate = Objects.requireNonNull(candidate, "candidate"); items = List.copyOf(items); nextAfterMemoryId = MemoryChecks.optionalIdentifier(nextAfterMemoryId, 128);
        if (candidate.state() != MemoryStateEnum.CANDIDATE || candidate.confirmed() || items.size() > 10 || semantic && nextAfterMemoryId != null) throw MemoryChecks.invalid();
        var seen = new HashSet<String>(); String previous = null; Double previousScore = null;
        for (var item : items) {
            var entry = item.hit().entry(); var score = item.hit().score();
            if (!candidate.owner().equals(entry.owner()) || !candidate.scopeId().equals(entry.scopeId()) || candidate.kind() != entry.kind()
                    || candidate.memoryId().equals(entry.memoryId()) || !seen.add(entry.memoryId()) || semantic != (score != null)
                    || !semantic && item.relation() == MemoryMergeRelationEnum.RELATED_CONTENT
                    || semantic && previousScore != null && score > previousScore || !semantic && previous != null && compare(previous, entry.memoryId()) >= 0) throw MemoryChecks.invalid();
            previous = entry.memoryId(); previousScore = score;
        }
        if (nextAfterMemoryId != null && previous != null && compare(nextAfterMemoryId, previous) < 0) throw MemoryChecks.invalid();
    }
    private static int compare(String first, String second) { return Arrays.compareUnsigned(first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8)); }
    @Override public String toString() { return "MemoryMergePage[redacted]"; }
}
