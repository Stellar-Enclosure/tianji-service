package top.egon.cola.platform.tianji.contract.memory;

import java.time.Instant;
import java.util.Objects;

/** Immutable command receipt contains references only, so replay after forgetting cannot resurrect a stored result body. */
public record MemoryMutationResultDTO(String commandId, MemoryOwnerDTO owner, String scopeId, String memoryId, long revision,
                                      MemoryMutationOutcomeEnum outcome, MemoryStateEnum stateAtMutation, Instant occurredAt) {
    public MemoryMutationResultDTO {
        commandId = MemoryChecks.identifier(commandId, 128); owner = Objects.requireNonNull(owner, "owner");
        scopeId = MemoryChecks.identifier(scopeId, 128); memoryId = MemoryChecks.identifier(memoryId, 128);
        outcome = Objects.requireNonNull(outcome, "outcome"); stateAtMutation = Objects.requireNonNull(stateAtMutation, "stateAtMutation"); occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (revision < 1 || outcome == MemoryMutationOutcomeEnum.FORGOTTEN && stateAtMutation != MemoryStateEnum.FORGOTTEN
                || outcome == MemoryMutationOutcomeEnum.CORRECTED && stateAtMutation != MemoryStateEnum.ACTIVE
                || outcome != MemoryMutationOutcomeEnum.FORGOTTEN && stateAtMutation != MemoryStateEnum.ACTIVE && stateAtMutation != MemoryStateEnum.CANDIDATE) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryMutationResult[redacted]"; }
}
