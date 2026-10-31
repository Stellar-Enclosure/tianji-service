package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Current tool-requester authority, separate from the run's CLAIM/MODEL permission.
 * Implementations must bind every field to independently verified evidence and the exact registered
 * execution resource. A model argument, tool descriptor or original run grant is not a tool decision.
 * Call outside database transactions, without retaining a USER bearer; unknown authority is UNAVAILABLE.
 * ALLOW requires a verified independent tool ticket whose validity covers Request.deadlineAt;
 * insufficient evidence or a shorter validity window is UNAVAILABLE. Calls must themselves be bounded.
 * The lease expiry is the original claim hint and may have been renewed by a heartbeat without
 * changing the Java value. The owning store rechecks actual live expiry, owner and epoch under its database lock.
 */
@FunctionalInterface
public interface ToolExecutionAuthorization {
    Decision authorize(Request request, Boundary boundary);
    /** Compatibility path only. A future dispatcher must refuse ALLOW without a recorded audit from its trusted implementation. */
    default CheckResult check(Request request, Boundary boundary) {
        if (request == null || boundary == null) throw invalid();
        return new CheckResult(request, boundary, authorize(request, boundary), null);
    }
    enum Decision { ALLOW, DENY, UNAVAILABLE }
    enum Boundary { PREPARE, DISPATCH, RECONCILE }

    /** Immutable observation of a port call, not authentication or permission to dispatch by construction. */
    record CheckResult(Request request, Boundary boundary, Decision decision, AuditVO audit) {
        public CheckResult {
            if (request == null || boundary == null || decision == null) throw invalid();
            if (audit != null && (audit.boundary() != boundary || audit.decision() != decision || audit.subjectType() != request.run().actor().subjectType()
                    || decision == Decision.ALLOW && (!audit.checkedAt().isBefore(request.deadlineAt()) || audit.expiresAt().isBefore(request.deadlineAt())
                    || audit.expiresAt().isAfter(request.run().deadlineAt())))) throw invalid();
        }
        @Override public String toString() { return "ToolExecutionAuthorization.CheckResult[redacted]"; }
    }

    /** Local check identity/time plus the actual upstream version slots. It does not claim an upstream decision ID. */
    record AuditVO(int schemaVersion, UUID localCheckId, Boundary boundary, Decision decision, TrustedAgentActor.SubjectType subjectType,
                   String grantId, Long authVersion, Long catalogVersion, Long policyVersion, Instant checkedAt, Instant expiresAt) {
        public AuditVO {
            if (schemaVersion != 1 || localCheckId == null || boundary == null || decision == null || subjectType == null || checkedAt == null) throw invalid();
            if (decision != Decision.ALLOW) {
                if (grantId != null || authVersion != null || catalogVersion != null || policyVersion != null || expiresAt != null) throw invalid();
            } else {
                if (policyVersion == null || policyVersion < 0 || expiresAt == null || !expiresAt.isAfter(checkedAt)) throw invalid();
                if (subjectType == TrustedAgentActor.SubjectType.USER) {
                    if (grantId == null || !grantId.matches("9[0-9]{62}") || authVersion == null || authVersion < 0 || catalogVersion != null) throw invalid();
                } else if (grantId == null || !grantId.matches("8[0-9]{62}") || authVersion != null || catalogVersion == null || catalogVersion < 1) throw invalid();
            }
        }
        @Override public String toString() { return "ToolExecutionAuthorization.AuditVO[redacted]"; }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool authorization audit does not match its check"); }

    record Request(AgentRunAuthorization.Request run, AgentRunLease lease, DefinitionResourceRef tool, String bindingSha256,
                   String parametersSha256, String actionIntentId, Instant deadlineAt) {
        public Request {
            run = Objects.requireNonNull(run, "run"); lease = Objects.requireNonNull(lease, "lease"); tool = Objects.requireNonNull(tool, "tool"); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
            if ("LEGACY".equals(run.actor().issuerFingerprint()) || !run.runId().equals(lease.runId()) || !ToolPolicy.key(tool.key()) || !hash(bindingSha256) || !hash(parametersSha256)
                    || deadlineAt.isAfter(run.deadlineAt())) throw invalid();
            if (actionIntentId != null) {
                try { if (!UUID.fromString(actionIntentId).toString().equals(actionIntentId)) throw invalid(); }
                catch (IllegalArgumentException malformed) { throw invalid(); }
            }
        }
        private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
        private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool authorization request is invalid"); }
        @Override public String toString() { return "ToolExecutionAuthorization.Request[redacted]"; }
    }
}
