package top.egon.cola.platform.tianji.contract.memory;

import java.util.Objects;

/** The state can already be terminal; only CANCELLED confirms this matching cancellation receipt. */
public record MemoryPreferenceIntentCancellationDTO(String commandId, MemoryPreferenceIntentStatusDTO status) {
    public MemoryPreferenceIntentCancellationDTO {
        commandId = MemoryChecks.identifier(commandId, 128); status = Objects.requireNonNull(status, "status");
        if (status.intent() == null || !status.intent().state().terminal()) throw MemoryChecks.invalid();
    }
    @Override public String toString() { return "MemoryPreferenceIntentCancellation[redacted]"; }
}
