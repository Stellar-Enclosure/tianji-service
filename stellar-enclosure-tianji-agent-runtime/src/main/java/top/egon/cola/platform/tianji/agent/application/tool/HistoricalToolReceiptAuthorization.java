package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Independent current historical authority. Ordinary live-run grants do not supply this decision. */
@FunctionalInterface
public interface HistoricalToolReceiptAuthorization {
    /** Empty denotes a confirmed refusal. Implementations must finish within the supplied deadline. */
    Optional<Permit> authorize(Request request);

    record Request(TrustedAgentActor owner, String dispatchRunId, UUID intentId, TrustedAgentActor executor,
                   String requestSha256, long actionRevision, long invocationRevision, String priorState, Instant deadlineAt) {
        public Request {
            if (owner == null || "LEGACY".equals(owner.issuerFingerprint()) || intentId == null
                    || executor == null || executor.subjectType() != TrustedAgentActor.SubjectType.SERVICE
                    || "LEGACY".equals(executor.issuerFingerprint()) || executor.tenantId() != owner.tenantId()
                    || dispatchRunId == null || dispatchRunId.isBlank() || dispatchRunId.length() > 64
                    || !hash(requestSha256) || actionRevision < 1 || invocationRevision < 1 || priorState == null
                    || !Set.of("DISPATCHING", "UNKNOWN", "APPLIED").contains(priorState) || deadlineAt == null) throw invalid();
        }

        public String bindingSha256() {
            return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_START_RECEIPT_HISTORICAL_AUTHORIZATION_V1",
                    owner.identityFingerprint(), dispatchRunId, intentId.toString(), executor.identityFingerprint(), requestSha256,
                    Long.toString(actionRevision), Long.toString(invocationRevision), priorState, deadlineAt.toString()), Map.of());
        }

        @Override public String toString() { return "HistoricalToolReceiptRequest[redacted]"; }
    }

    record Permit(UUID checkId, String bindingSha256, long policyVersion, Instant checkedAt, Instant expiresAt) {
        public Permit {
            if (checkId == null || !hash(bindingSha256) || policyVersion < 1 || checkedAt == null
                    || expiresAt == null || !expiresAt.isAfter(checkedAt)) throw invalid();
        }

        @Override public String toString() { return "HistoricalToolReceiptPermit[redacted]"; }
    }

    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Historical tool receipt authorization binding is invalid");
    }
}
