package top.egon.cola.platform.tianji.contract.memory;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPreferenceContractTest {
    private static final MemoryOwnerDTO OWNER = new MemoryOwnerDTO(MemoryOwnerTypeEnum.USER, "alice");
    private static final String POLICY = "preferences@1#" + "a".repeat(64);

    @Test void source_item_receipts_are_scoped_and_can_never_claim_automatic_confirmation() {
        var command = command(); var created = item(command.itemCommandId("language"), MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE);
        var result = result(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of(created)); assertEquals(1, result.items().size());
        assertEquals(command.itemCommandId("language"), command().itemCommandId("language")); assertNotEquals(command.itemCommandId("language"), command.itemCommandId("timezone"));
        var changedPolicy = new ExtractMemoryPreferencesCommand(OWNER, "research", "run", "terminal", "new-binding", "preferences@2#" + "b".repeat(64));
        assertEquals(command.itemCommandId("language"), changedPolicy.itemCommandId("language"));
        assertThrows(IllegalArgumentException.class, () -> command.itemCommandId("unknown-control\n"));
        assertThrows(IllegalArgumentException.class, () -> result(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> result(MemoryPreferenceExtractionOutcomeEnum.NO_DECLARATION, List.of(created)));
        assertThrows(IllegalArgumentException.class, () -> result(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of(item("foreign", MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.CANDIDATE))));
        assertThrows(IllegalArgumentException.class, () -> result(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of(item(command.itemCommandId("language"), MemoryMutationOutcomeEnum.CREATED, MemoryStateEnum.ACTIVE))));
        assertDoesNotThrow(() -> result(MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE, List.of(item(command.itemCommandId("language"), MemoryMutationOutcomeEnum.DEDUPLICATED, MemoryStateEnum.ACTIVE))));
        assertDoesNotThrow(() -> result(MemoryPreferenceExtractionOutcomeEnum.NO_DECLARATION, List.of()));
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
        assertThrows(IllegalArgumentException.class, () -> new ExtractMemoryPreferencesCommand(new MemoryOwnerDTO(MemoryOwnerTypeEnum.APPLICATION, "worker"), "research", "run", "terminal", "source", POLICY));
    }
    private static ExtractMemoryPreferencesCommand command() { return new ExtractMemoryPreferencesCommand(OWNER, "research", "run", "terminal", "source", POLICY); }
    private static MemoryPreferenceExtractionItemDTO item(String commandId, MemoryMutationOutcomeEnum outcome, MemoryStateEnum state) {
        return new MemoryPreferenceExtractionItemDTO("language", new MemoryMutationResultDTO(commandId, OWNER, "research", "memory", 1, outcome, state, Instant.parse("2026-10-31T00:00:00Z")));
    }
    private static MemoryPreferenceExtractionResultDTO result(MemoryPreferenceExtractionOutcomeEnum outcome, List<MemoryPreferenceExtractionItemDTO> items) { return new MemoryPreferenceExtractionResultDTO(OWNER, "research", "run", "terminal", "source", POLICY, outcome, items); }
}
