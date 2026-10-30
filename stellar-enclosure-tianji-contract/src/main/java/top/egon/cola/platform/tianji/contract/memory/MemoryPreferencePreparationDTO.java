package top.egon.cola.platform.tianji.contract.memory;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Current preparation carries no source body or preference values; its opaque command is revalidated at mutation time. */
public record MemoryPreferencePreparationDTO(ExtractMemoryPreferencesCommand command, MemoryPreferenceExtractionOutcomeEnum outcome,
                                             int candidateCount, List<String> preferenceKeys, MemorySensitivityEnum sensitivity) {
    public MemoryPreferencePreparationDTO {
        command = Objects.requireNonNull(command, "command"); outcome = Objects.requireNonNull(outcome, "outcome"); preferenceKeys = List.copyOf(preferenceKeys);
        if (candidateCount < 0 || candidateCount > 32 || preferenceKeys.size() != candidateCount || new HashSet<>(preferenceKeys).size() != preferenceKeys.size()
                || (outcome == MemoryPreferenceExtractionOutcomeEnum.ELIGIBLE) != (candidateCount > 0)
                || sensitivity != MemorySensitivityEnum.CONFIDENTIAL && sensitivity != MemorySensitivityEnum.RESTRICTED) throw MemoryChecks.invalid();
        for (var key : preferenceKeys) if (key == null || !key.matches("[a-z][a-z0-9_.-]{0,63}")) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryPreferencePreparation[redacted]"; }
}
