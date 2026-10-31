package top.egon.cola.platform.tianji.process.server.identity;

import java.time.Instant;
import java.util.Objects;

/** Internal current-service facts after the exact approval-verification guard. Construction is not authentication. */
public record ApprovalVerificationContext(ProcessActor actor, Instant deadlineAt) {
    public ApprovalVerificationContext {
        actor = Objects.requireNonNull(actor, "actor"); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        if (actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) throw new IllegalArgumentException("Approval consumer context is invalid");
    }
    @Override public String toString() { return "ApprovalVerificationContext[redacted]"; }
}
