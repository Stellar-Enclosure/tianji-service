package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** The finite policy key identifies its source-bound write; no candidate value or body is retained in a receipt. */
public record MemoryPreferenceExtractionItemDTO(String preferenceKey, MemoryMutationResultDTO receipt) {
    public MemoryPreferenceExtractionItemDTO {
        if (preferenceKey == null || !preferenceKey.matches("[a-z][a-z0-9_.-]{0,63}")) throw MemoryChecks.invalid();
        receipt = Objects.requireNonNull(receipt, "receipt");
    }
    @Override public String toString() { return "MemoryPreferenceExtractionItem[redacted]"; }
}
