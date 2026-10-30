package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record AgentResultReceipt(String eventId, String eventFingerprint, AgentResultReceiptStateEnum state, Instant receivedAt) {
    public AgentResultReceipt {
        eventId = OrchestrationChecks.hash(eventId);
        eventFingerprint = OrchestrationChecks.hash(eventFingerprint);
        state = Objects.requireNonNull(state, "state");
        if (receivedAt == null || receivedAt.isBefore(Instant.EPOCH) || receivedAt.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
                || !receivedAt.equals(receivedAt.truncatedTo(ChronoUnit.MICROS))) throw OrchestrationChecks.invalid();
    }
}
