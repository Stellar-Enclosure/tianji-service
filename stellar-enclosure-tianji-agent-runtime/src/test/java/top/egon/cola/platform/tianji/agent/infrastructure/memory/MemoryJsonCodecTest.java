package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.memory.MemoryContentDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySourceDTO;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryJsonCodecTest {
    private final MemoryJsonCodec codec = new MemoryJsonCodec();

    @Test
    void typed_round_trip_digest_ignores_jsonb_order_and_formatting_but_rejects_changed_content() {
        var content = new MemoryContentDTO("Café", Map.of("language", "中文", "case", "Keep CASE"));
        String hash = codec.contentSha256(content);
        String stored = "{\"facts\": {\"language\":\"中文\",\"case\":\"Keep CASE\"}, \"text\":\"Cafe\\u0301\"}";
        assertEquals(content, codec.readContent(stored, hash));
        assertEquals(content, codec.readContent(codec.content(content), hash));
        assertThrows(AgentLedgerException.class, () -> codec.readContent(stored.replace("Keep CASE", "different"), hash));
        assertFalse(hash.equals(MemoryJsonCodec.sha256(codec.content(content))));
    }

    @Test
    void duplicate_unknown_scalar_coerced_missing_and_trailing_json_are_not_accepted() {
        var content = new MemoryContentDTO("private content", Map.of()); String hash = codec.contentSha256(content);
        for (String json : List.of("{\"text\":\"private content\",\"facts\":{},\"text\":\"changed\"}",
                "{\"text\":\"private content\",\"facts\":{},\"trusted\":true}",
                "{\"text\":123,\"facts\":{}}", "{\"text\":\"private content\",\"facts\":{\"field\":123}}",
                "{\"text\":\"private content\"}", "{\"text\":\"private content\",\"facts\":{}} {}")) {
            var failure = assertThrows(AgentLedgerException.class, () -> codec.readContent(json, hash));
            assertFalse(failure.toString().contains("private content")); assertNull(failure.getCause());
        }
    }

    @Test
    void source_evidence_affinity_and_closed_shape_cannot_be_replaced_by_caller_authority_flags() {
        var source = new MemorySourceDTO("private-reference", "a".repeat(64), "run", "event");
        assertEquals(source, codec.readSource(codec.source(source), source.evidenceSha256()));
        assertThrows(AgentLedgerException.class, () -> codec.readSource(codec.source(source), "b".repeat(64)));
        assertThrows(AgentLedgerException.class, () -> codec.readSource(codec.source(source).replace("\"reference\"", "\"verified\":true,\"reference\""), source.evidenceSha256()));
    }

    @Test
    void idempotency_digest_covers_confirmation_retention_and_provenance_without_derived_database_time() {
        var original = command(true, "retention-v1", "source-one");
        assertEquals(codec.commandSha256(original), codec.commandSha256(command(true, "retention-v1", "source-one")));
        assertNotEquals(codec.commandSha256(original), codec.commandSha256(command(false, "retention-v1", "source-one")));
        assertNotEquals(codec.commandSha256(original), codec.commandSha256(command(true, "retention-v2", "source-one")));
        assertNotEquals(codec.commandSha256(original), codec.commandSha256(command(true, "retention-v1", "source-two")));
    }

    private static UpsertMemoryCommand command(boolean confirmed, String retention, String reference) {
        return new UpsertMemoryCommand("command", new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice"), "research", "memory", MemoryKindEnum.PREFERENCE,
                0, new MemoryContentDTO("private preference", Map.of()), new MemorySourceDTO(reference, "a".repeat(64), null, null),
                new BigDecimal("0.9"), confirmed, MemorySensitivityEnum.CONFIDENTIAL, retention, Instant.parse("2026-10-30T00:00:00Z"), null);
    }
}
