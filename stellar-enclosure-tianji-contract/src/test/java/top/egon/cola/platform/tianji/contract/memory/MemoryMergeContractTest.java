package top.egon.cola.platform.tianji.contract.memory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryMergeContractTest {
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");

    @Test void comparison_modes_cannot_mix_fact_cursors_scores_or_confirmed_candidate_sources() {
        var source = entry("candidate", false); var active = entry("active", true);
        assertDoesNotThrow(() -> new MemoryMergeQuery(OWNER, "review", "candidate", 1, false, "after", 10, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergeQuery(OWNER, "review", "candidate", 1, true, "after", 10, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergeQuery(OWNER, "review", "candidate", 1, false, null, 11, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergeQuery(OWNER, "review", "candidate", 1, true, null, 10, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergeQuery(OWNER, "review", "candidate", 1, false, null, 10, 0.5));
        assertDoesNotThrow(() -> new MemoryMergePageDTO(source, false, List.of(), "scanned-nonmatch"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergePageDTO(active, false, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergePageDTO(source, true, List.of(), "cursor"));
        var factual = new MemoryMergeHitDTO(new MemoryHitDTO(active, null), MemoryMergeRelationEnum.SHARED_VALUES);
        var page = new MemoryMergePageDTO(source, false, List.of(factual), "after-active");
        assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergePageDTO(source, false, List.of(factual, factual), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergePageDTO(source, true, List.of(factual), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryMergePageDTO(source, false, List.of(new MemoryMergeHitDTO(new MemoryHitDTO(active, null), MemoryMergeRelationEnum.RELATED_CONTENT)), null));
    }
    private static MemoryEntryDTO entry(String id, boolean confirmed) {
        var now = Instant.parse("2026-10-31T00:00:00Z");
        return new MemoryEntryDTO(OWNER, "review", id, MemoryKindEnum.PREFERENCE, 1, null, confirmed ? MemoryStateEnum.ACTIVE : MemoryStateEnum.CANDIDATE,
                new MemoryContentDTO(null, Map.of("language", "zh-CN")), "a".repeat(64), new MemorySourceDTO("declared", "b".repeat(64), null, null), new BigDecimal("0.5"), confirmed,
                MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "c".repeat(64), now, now.plusSeconds(60), now, now);
    }
}
