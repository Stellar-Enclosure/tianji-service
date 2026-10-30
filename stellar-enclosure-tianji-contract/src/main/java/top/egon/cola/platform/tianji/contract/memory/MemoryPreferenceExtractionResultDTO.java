package top.egon.cola.platform.tianji.contract.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Reference-only item receipts remain safe to replay after their candidate content has been forgotten. */
public record MemoryPreferenceExtractionResultDTO(MemoryOwnerDTO owner, String scopeId, String runId,
                                                  String sourceEventId, String bindingId, String policyRef,
                                                  MemoryPreferenceExtractionOutcomeEnum outcome, List<MemoryPreferenceExtractionItemDTO> items) {
    public MemoryPreferenceExtractionResultDTO {
        var request = new ExtractMemoryPreferencesCommand(owner, scopeId, runId, sourceEventId, bindingId, policyRef);
        outcome = Objects.requireNonNull(outcome, "outcome"); items = List.copyOf(items);
        if (items.size() > 32 || (outcome == MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE) == items.isEmpty()) throw MemoryChecks.invalid();
        var ids = new HashSet<String>();
        for (var reference : items) {
            var item = reference.receipt();
            if (!owner.equals(item.owner()) || !scopeId.equals(item.scopeId()) || !request.itemCommandId(reference.preferenceKey()).equals(item.commandId()) || !ids.add(reference.preferenceKey())
                    || item.outcome() != MemoryMutationOutcomeEnum.CREATED && item.outcome() != MemoryMutationOutcomeEnum.DEDUPLICATED
                    || item.outcome() == MemoryMutationOutcomeEnum.CREATED && (item.revision() != 1 || item.stateAtMutation() != MemoryStateEnum.CANDIDATE)) throw MemoryChecks.invalid();
        }
    }
    @Override public String toString() { return "MemoryPreferenceExtractionResult[redacted]"; }
}
