package top.egon.cola.platform.tianji.contract.memory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryCandidateContractTest {
    @Test void candidate_pages_are_immutable_owner_scoped_and_cannot_contain_confirmed_evidence() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"); var candidate = entry(owner, false);
        var values = new ArrayList<>(List.of(candidate)); var page = new MemoryCandidatePageDTO(owner, "review", values, "candidate"); values.clear();
        assertEquals(1, page.items().size()); assertThrows(UnsupportedOperationException.class, () -> page.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidatePageDTO(owner, "review", List.of(entry(owner, true)), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidatePageDTO(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "bob"), "review", List.of(candidate), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidatePageDTO(owner, "review", List.of(candidate, candidate), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidatePageDTO(owner, "review", List.of(candidate), "wrong-cursor"));
        assertFalse(page.toString().contains("private candidate"));
    }
    @Test void candidate_query_is_exclusive_and_bounded_without_accepting_tenant_or_search_options() {
        var owner = new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "team");
        assertEquals("after", new MemoryCandidateQuery(owner, "review", null, "after", 50).afterMemoryId());
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidateQuery(owner, "review", null, null, 51));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCandidateQuery(owner, "review", null, "bad\n", 1));
    }
    private static MemoryEntryDTO entry(MemoryOwnerDTO owner, boolean confirmed) {
        var now = Instant.parse("2026-10-31T00:00:00Z");
        return new MemoryEntryDTO(owner, "review", "candidate", MemoryKindEnum.FACT, 1, null, confirmed ? MemoryStateEnum.ACTIVE : MemoryStateEnum.CANDIDATE,
                new MemoryContentDTO("private candidate", Map.of()), "a".repeat(64), new MemorySourceDTO("caller claim", "b".repeat(64), null, null), BigDecimal.ONE, confirmed,
                MemorySensitivityEnum.CONFIDENTIAL, "retention@1#" + "c".repeat(64), now.plusSeconds(10), now.plusSeconds(60), now, now);
    }
}
