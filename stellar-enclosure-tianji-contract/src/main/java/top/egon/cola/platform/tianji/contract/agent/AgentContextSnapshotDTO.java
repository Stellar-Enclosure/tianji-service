package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.util.Objects;

public record AgentContextSnapshotDTO(String runId, String reservationId, int modelCallNumber, int attempt,
                                       long leaseEpoch, AgentContextMetadataDTO metadata, Instant capturedAt) {
    public AgentContextSnapshotDTO {
        runId = ContractChecks.contextIdentifier(runId, "runId", 64);
        reservationId = ContractChecks.contextIdentifier(reservationId, "reservationId", 128);
        metadata = Objects.requireNonNull(metadata, "metadata"); capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
        if (modelCallNumber < 1 || attempt < 1 || leaseEpoch < 1 || !runId.equals(metadata.runId())
                || !reservationId.equals(metadata.reservationId()) || leaseEpoch != metadata.leaseEpoch()) throw new IllegalArgumentException("context snapshot identity is invalid");
    }
}
