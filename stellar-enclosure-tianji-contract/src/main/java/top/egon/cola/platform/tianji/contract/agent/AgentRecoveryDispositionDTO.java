package top.egon.cola.platform.tianji.contract.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Current owner-local recovery evidence, independent of any permission to act on the run. */
public record AgentRecoveryDispositionDTO(String runId, Instant observedAt, Lifecycle lifecycle, Reason reason,
                                         AgentEffect agentEffect, ToolEvidenceDTO toolEvidence, boolean evidenceConflict) {
    private static final Instant MAX_OBSERVATION = Instant.parse("9999-12-31T23:59:59.999999Z");

    public AgentRecoveryDispositionDTO {
        runId = ContractChecks.contextIdentifier(runId, "runId", 64);
        if (observedAt == null || observedAt.isBefore(Instant.EPOCH) || observedAt.isAfter(MAX_OBSERVATION)
                || !observedAt.equals(observedAt.truncatedTo(ChronoUnit.MICROS))
                || reason == null || lifecycle != lifecycleFor(reason) || agentEffect == null || toolEvidence == null
                || lifecycle != Lifecycle.TERMINAL && agentEffect != AgentEffect.UNVERIFIED
                || evidenceConflict != (agentEffect == AgentEffect.RECORDED_NONE
                        && Boolean.TRUE.equals(toolEvidence.hasUnresolvedWrite()))) throw invalid();
    }

    public enum Lifecycle { QUEUED, ACTIVE, STALLED, TERMINAL, UNVERIFIED }

    public enum Reason {
        QUEUE_DISCOVERY, CURRENT_LEASE, CANCEL_REQUESTED, DEADLINE_DUE, LEASE_MISSING, LEASE_EXPIRED,
        AUTHORIZATION_DENIED, AUTHORIZATION_UNAVAILABLE, FENCE_UNVERIFIED, SUSPENSION_UNVERIFIED,
        LEGACY_WAIT_UNVERIFIED, TERMINAL_RECORDED, SOURCE_UNAVAILABLE
    }

    public enum AgentEffect { RECORDED_NONE, RECORDED_CONFIRMED, UNKNOWN, UNVERIFIED }

    public enum ToolCoverage { COMPLETE, TRUNCATED, UNAVAILABLE }

    public record ToolEvidenceDTO(ToolCoverage coverage, Boolean hasDispatching, Boolean hasUnknownResult,
                                  Boolean hasUnresolvedWrite, Boolean hasLocalReceipt) {
        public ToolEvidenceDTO {
            if (coverage == null || !validFlag(coverage, hasDispatching) || !validFlag(coverage, hasUnknownResult)
                    || !validFlag(coverage, hasUnresolvedWrite) || !validFlag(coverage, hasLocalReceipt)
                    || Boolean.TRUE.equals(hasUnresolvedWrite)
                    && !Boolean.TRUE.equals(hasDispatching) && !Boolean.TRUE.equals(hasUnknownResult)) throw invalid();
        }
    }

    private static Lifecycle lifecycleFor(Reason reason) {
        return switch (reason) {
            case QUEUE_DISCOVERY -> Lifecycle.QUEUED;
            case CURRENT_LEASE, CANCEL_REQUESTED -> Lifecycle.ACTIVE;
            case DEADLINE_DUE, LEASE_MISSING, LEASE_EXPIRED, AUTHORIZATION_DENIED, AUTHORIZATION_UNAVAILABLE -> Lifecycle.STALLED;
            case TERMINAL_RECORDED -> Lifecycle.TERMINAL;
            case FENCE_UNVERIFIED, SUSPENSION_UNVERIFIED, LEGACY_WAIT_UNVERIFIED, SOURCE_UNAVAILABLE -> Lifecycle.UNVERIFIED;
        };
    }

    private static boolean validFlag(ToolCoverage coverage, Boolean flag) {
        return switch (coverage) {
            case COMPLETE -> Objects.nonNull(flag);
            case TRUNCATED -> flag == null || Boolean.TRUE.equals(flag);
            case UNAVAILABLE -> flag == null;
        };
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Agent recovery evidence is invalid");
    }
}
