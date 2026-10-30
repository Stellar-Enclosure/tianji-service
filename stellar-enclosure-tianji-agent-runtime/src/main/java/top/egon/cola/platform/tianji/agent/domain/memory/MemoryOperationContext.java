package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;

import java.time.Instant;
import java.util.Objects;

/** Internal caller context. An optional current-run binding is data for source authorization, never an implicit Memory grant. */
public record MemoryOperationContext(TrustedAgentActor actor, Instant deadlineAt, AgentRunAuthorization.Request runAuthorization) {
    public MemoryOperationContext {
        actor = Objects.requireNonNull(actor, "actor"); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        if ("LEGACY".equals(actor.issuerFingerprint()) || runAuthorization != null
                && (!actor.equals(runAuthorization.actor()) || deadlineAt.isAfter(runAuthorization.deadlineAt()))) throw new IllegalArgumentException("Memory operation context is invalid");
    }
    @Override public String toString() { return "MemoryOperationContext[redacted]"; }
}
