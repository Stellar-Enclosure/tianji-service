package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.ContextSnapshotFixtures.metadata;

class AgentContextJsonCodecTest {
    private final AgentContextJsonCodec codec = new AgentContextJsonCodec();

    @Test
    void metadata_survives_jsonb_style_spacing_and_key_order_without_changing_its_typed_fingerprint() throws Exception {
        var metadata = metadata();
        String encoded = codec.write(metadata);
        var mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        String database = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(encoded));
        assertEquals(metadata, codec.read(database, codec.fingerprint(metadata)));
        assertFalse(encoded.contains("private-input"));
    }

    @Test
    void changed_unknown_coerced_or_trailing_metadata_is_rejected_even_when_a_semantic_coercion_would_restore_the_hash() throws Exception {
        var metadata = metadata(); var hash = codec.fingerprint(metadata);
        String encoded = codec.write(metadata);
        assertThrows(AgentLedgerException.class, () -> codec.read(encoded.replace("call-one", "call-two"), hash));
        assertThrows(AgentLedgerException.class, () -> codec.read(encoded + " {}", hash));
        var mapper = JsonMapper.builder().build();
        var changed = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(encoded);
        changed.put("extra", "private-value"); assertThrows(AgentLedgerException.class, () -> codec.read(changed.toString(), hash));
        changed.remove("extra"); changed.put("estimatedInputTokens", "20");
        assertThrows(AgentLedgerException.class, () -> codec.read(changed.toString(), hash));
        changed.put("estimatedInputTokens", 20);
        ((com.fasterxml.jackson.databind.node.ObjectNode) changed.path("sources").get(0)).put("role", 0);
        assertThrows(AgentLedgerException.class, () -> codec.read(changed.toString(), hash));
    }
}
