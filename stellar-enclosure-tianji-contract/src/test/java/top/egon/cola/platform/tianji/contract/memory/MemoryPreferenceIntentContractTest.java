package top.egon.cola.platform.tianji.contract.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPreferenceIntentContractTest {
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String ID = "00000000-0000-0000-0000-000000000001", POLICY = "preferences@1#" + "a".repeat(64);

    @Test void status_and_cancellation_are_owned_user_intentions_without_tenant_or_source_body_fields() {
        var query = new GetMemoryPreferenceIntentQuery(OWNER, "research", "run"); var command = command("cancel-1", "research", "run", 3);
        assertEquals(query, command.sourceQuery()); assertFalse(query.toString().contains("alice")); assertFalse(command.toString().contains("alice"));
        assertThrows(IllegalArgumentException.class, () -> new GetMemoryPreferenceIntentQuery(new MemoryOwnerDTO(MemoryOwnerTypeEnum.TEAM, "team"), "research", "run"));
        assertThrows(IllegalArgumentException.class, () -> command("cancel-1", "research", "run", 0));
        assertThrows(IllegalArgumentException.class, () -> new GetMemoryPreferenceIntentQuery(OWNER, " research", "run"));
        assertThrows(IllegalArgumentException.class, () -> new GetMemoryPreferenceIntentQuery(OWNER, "research", "a".repeat(65)));
    }

    @Test void the_cancellation_receipt_hash_freezes_every_field_and_is_length_framed() {
        var command = command("cancel-1", "research", "run", 3);
        assertEquals("01bbd0f312b7e427c186350bc651b2c98658f06357162b1f5eaeb9261c1b0fcb", command.requestSha256());
        assertNotEquals(command.requestSha256(), command("cancel-1", "research", "run", 4).requestSha256());
        assertNotEquals(command.requestSha256(), command("cancel-2", "research", "run", 3).requestSha256());
        assertNotEquals(command("cancel-1", "a", "bc", 3).requestSha256(), command("cancel-1", "ab", "c", 3).requestSha256());
        var other = new CancelMemoryPreferenceIntentCommand("cancel-1", new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "other"), "research", "run", 3);
        assertNotEquals(command.requestSha256(), other.requestSha256());
    }

    @Test void not_requested_is_distinct_from_processed_or_cancelled_and_no_success_can_claim_live_pending_work() {
        var absent = new MemoryPreferenceIntentStatusDTO(OWNER, "research", "run", null); assertNull(absent.intent());
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntentCancellationDTO("cancel-1", absent));
        var pending = new MemoryPreferenceIntentStatusDTO(OWNER, "research", "run", intent(MemoryPreferenceIntentStateEnum.READY, 0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntentCancellationDTO("cancel-1", pending));
        var applied = new MemoryPreferenceIntentStatusDTO(OWNER, "research", "run", intent(MemoryPreferenceIntentStateEnum.APPLIED, 2, 2, NOW));
        assertEquals(MemoryPreferenceIntentStateEnum.APPLIED, new MemoryPreferenceIntentCancellationDTO("cancel-1", applied).status().intent().state());
        var cancelled = new MemoryPreferenceIntentStatusDTO(OWNER, "research", "run", intent(MemoryPreferenceIntentStateEnum.CANCELLED, 1, 2, NOW));
        assertEquals(MemoryPreferenceIntentStateEnum.CANCELLED, new MemoryPreferenceIntentCancellationDTO("cancel-1", cancelled).status().intent().state());
    }

    @Test void invalid_progress_timestamps_identity_or_terminal_shapes_are_rejected() {
        assertThrows(IllegalArgumentException.class, () -> intent(MemoryPreferenceIntentStateEnum.APPLIED, 0, 0, NOW));
        assertThrows(IllegalArgumentException.class, () -> intent(MemoryPreferenceIntentStateEnum.APPLIED, 1, 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> intent(MemoryPreferenceIntentStateEnum.CLAIMED, 0, 2, NOW));
        assertThrows(IllegalArgumentException.class, () -> intent(MemoryPreferenceIntentStateEnum.CANCELLED, 0, 2, null));
        assertThrows(IllegalArgumentException.class, () -> intent(MemoryPreferenceIntentStateEnum.READY, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntentDTO("1-1-1-1-1", "source", POLICY, MemoryPreferenceIntentStateEnum.READY, 3, 0, null, 0, null, NOW, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new MemoryPreferenceIntentDTO(ID, "source", POLICY, MemoryPreferenceIntentStateEnum.CANCELLED, 3, 0, null, 0, "USER_CANCELLED", NOW, NOW, NOW.plusSeconds(1)));
    }

    private static CancelMemoryPreferenceIntentCommand command(String id, String scope, String run, long revision) { return new CancelMemoryPreferenceIntentCommand(id, OWNER, scope, run, revision); }
    private static MemoryPreferenceIntentDTO intent(MemoryPreferenceIntentStateEnum state, int index, Integer total, Instant closed) { return new MemoryPreferenceIntentDTO(ID, "source", POLICY, state, 3, index, total, 1, null, NOW.minusSeconds(1), NOW, closed); }
}
