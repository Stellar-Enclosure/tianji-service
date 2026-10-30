package top.egon.cola.platform.tianji.contract.memory;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryContractTest {
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final MemoryContentDTO CONTENT = new MemoryContentDTO("private preference", Map.of("language", "中文"));
    private static final MemorySourceDTO SOURCE = new MemorySourceDTO("explicit-reference", "a".repeat(64), null, null);
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void content_is_an_immutable_nfc_copy_without_folding_case_or_meaningful_whitespace() {
        var facts = new HashMap<>(Map.of("z", "Café", "a", "Value"));
        var content = new MemoryContentDTO("  Cafe\u0301\n", facts); facts.put("a", "changed");
        assertEquals("  Café\n", content.text()); assertEquals("Value", content.facts().get("a"));
        assertEquals(List.of("a", "z"), List.copyOf(content.facts().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> content.facts().put("extra", "value"));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO(null, Map.of("Café", "one", "Cafe\u0301", "two")));
        assertFalse(content.toString().contains("Café"));
    }

    @Test
    void invalid_unicode_nul_empty_content_and_aggregate_byte_overflow_are_refused_without_truncation() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO("broken\ud800", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO("NUL\0content", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO("  ", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO(" ".repeat(20_000), Map.of("field", "value")));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO("中".repeat(6000), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new MemoryContentDTO("x".repeat(16_000), Map.of("field", "y".repeat(1000))));
        assertThrows(IllegalArgumentException.class, () -> new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, " alice"));
    }

    @Test
    void creation_and_revision_checked_correction_have_distinct_contracts_and_expiry_is_ordered() {
        assertEquals(0, upsert(0, NOW.plusSeconds(60)).expectedRevision());
        assertThrows(IllegalArgumentException.class, () -> upsert(1, NOW.plusSeconds(60)));
        assertThrows(IllegalArgumentException.class, () -> upsert(0, NOW));
        assertThrows(IllegalArgumentException.class, () -> new CorrectMemoryCommand("command", OWNER, "research", "memory", 0, CONTENT, SOURCE, BigDecimal.ONE, "USER_CORRECTION"));
        assertThrows(IllegalArgumentException.class, () -> new ForgetMemoryCommand("command", OWNER, "research", "memory", 0, "USER_FORGET"));
        assertThrows(IllegalArgumentException.class, () -> new ForgetMemoryCommand("command", OWNER, "research", "memory", 1, "free form sensitive reason"));
        assertThrows(IllegalArgumentException.class, () -> new MemorySourceDTO("reference", "a".repeat(64), "run", null));
    }

    @Test
    void forgotten_or_expired_projections_cannot_carry_bodies_and_active_facts_require_confirmation() {
        var forgotten = entry(OWNER, "memory", MemoryStateEnum.FORGOTTEN, null, null, true);
        assertNull(forgotten.content()); assertNull(forgotten.source());
        assertThrows(IllegalArgumentException.class, () -> entry(OWNER, "memory", MemoryStateEnum.FORGOTTEN, CONTENT, SOURCE, true));
        assertThrows(IllegalArgumentException.class, () -> entry(OWNER, "memory", MemoryStateEnum.EXPIRED, CONTENT, SOURCE, true));
        assertThrows(IllegalArgumentException.class, () -> entry(OWNER, "memory", MemoryStateEnum.ACTIVE, CONTENT, SOURCE, false));
        assertThrows(IllegalArgumentException.class, () -> entry(OWNER, "memory", MemoryStateEnum.CANDIDATE, CONTENT, SOURCE, true));
        assertFalse(entry(OWNER, "memory", MemoryStateEnum.ACTIVE, CONTENT, SOURCE, true).toString().contains("private preference"));
        var receipt = new MemoryMutationResultDTO("command", OWNER, "research", "memory", 2, MemoryMutationOutcomeEnum.FORGOTTEN, MemoryStateEnum.FORGOTTEN, NOW);
        assertEquals(2, receipt.revision()); assertFalse(receipt.toString().contains("private preference"));
    }

    @Test
    void retrieval_refuses_cursor_search_aliases_candidates_and_cross_owner_pages() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalQuery(OWNER, "research", null, "query", "cursor", 5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalQuery(OWNER, "research", null, " ", null, 5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalQuery(OWNER, "research", null, null, null, 51));
        var active = new MemoryHitDTO(entry(OWNER, "memory", MemoryStateEnum.ACTIVE, CONTENT, SOURCE, true), 0.8);
        assertThrows(IllegalArgumentException.class, () -> new MemoryHitDTO(active.entry(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new MemoryHitDTO(entry(OWNER, "candidate", MemoryStateEnum.CANDIDATE, CONTENT, SOURCE, false), 0.8));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalPageDTO(new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "bob"), "research", List.of(active), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalPageDTO(OWNER, "research", List.of(active, active), null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryRetrievalPageDTO(OWNER, "research", List.of(active), "wrong-cursor"));
    }

    private static UpsertMemoryCommand upsert(long expected, Instant expires) {
        return new UpsertMemoryCommand("command", OWNER, "research", "memory", MemoryKindEnum.PREFERENCE, expected, CONTENT, SOURCE,
                new BigDecimal("0.950000"), true, MemorySensitivityEnum.CONFIDENTIAL, "retention-v1", NOW, expires);
    }
    private static MemoryEntryDTO entry(MemoryOwnerDTO owner, String id, MemoryStateEnum state, MemoryContentDTO content, MemorySourceDTO source, boolean confirmed) {
        return new MemoryEntryDTO(owner, "research", id, MemoryKindEnum.PREFERENCE, 1, null, state, content, "b".repeat(64), source,
                new BigDecimal("0.95"), confirmed, MemorySensitivityEnum.CONFIDENTIAL, "retention-v1", NOW, null, NOW, NOW);
    }
}
