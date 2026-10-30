package top.egon.cola.platform.tianji.agent.application.memory;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerDTO;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MemoryIndexPublicationTest {
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final String ISSUER = "a".repeat(64);
    private static final String MODEL = "memory-model@1#" + "b".repeat(64);

    @Test
    void deterministic_vector_identity_is_bound_to_every_owner_scope_revision_and_model_dimension_reference() {
        UUID id = MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 1, MODEL);
        assertEquals(id, MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 1, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(21, ISSUER, OWNER, "research", "memory", 1, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(20, "c".repeat(64), OWNER, "research", "memory", 1, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(20, ISSUER, new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "alice"), "research", "memory", 1, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "other", "memory", 1, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 2, MODEL));
        assertNotEquals(id, MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 1, MODEL.replace("@1#", "@2#")));
        assertNotEquals(MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "a:b", "c", 1, MODEL),
                MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "a", "b:c", 1, MODEL));
    }

    @Test
    void event_dedup_identity_is_stable_but_body_reference_or_timestamp_drift_changes_the_fingerprint() {
        var time = Instant.parse("2026-10-30T00:00:00Z");
        var id = MemoryIndexPublication.vectorDocumentId(20, ISSUER, OWNER, "research", "memory", 1, MODEL);
        var event = new MemoryIndexPublication.Event(MemoryIndexPublication.Action.BUILD, 20, ISSUER, OWNER, "research", "memory", 1, "d".repeat(64), MODEL, id, time);
        var changed = new MemoryIndexPublication.Event(event.action(), 20, ISSUER, OWNER, "research", "memory", 1, "e".repeat(64), MODEL, id, time.plusSeconds(1));
        assertEquals(event.eventId(), changed.eventId()); assertNotEquals(event.fingerprint(), changed.fingerprint());
        assertThrows(IllegalArgumentException.class, () -> new MemoryIndexPublication.Event(event.action(), 20, ISSUER, OWNER, "research", "memory", 1, "d".repeat(64), MODEL, UUID.randomUUID(), time));
        assertThrows(IllegalArgumentException.class, () -> new MemoryIndexPublication.Binding(MODEL, 2, "agent_run"));
    }
}
