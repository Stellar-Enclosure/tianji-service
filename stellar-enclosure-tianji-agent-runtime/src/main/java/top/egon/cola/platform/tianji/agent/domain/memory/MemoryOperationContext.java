package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryOwnerTypeEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.time.Instant;
import java.util.Objects;

/** Internal caller context. Run evidence and a candidate-write capability are distinct and cannot borrow one another's authority. */
public record MemoryOperationContext(TrustedAgentActor actor, Instant deadlineAt, AgentRunAuthorization.Request runAuthorization,
                                     MemoryPreferenceWritePermit preferenceWrite) {
    public MemoryOperationContext(TrustedAgentActor actor, Instant deadlineAt, AgentRunAuthorization.Request runAuthorization) { this(actor, deadlineAt, runAuthorization, null); }
    public MemoryOperationContext {
        actor = Objects.requireNonNull(actor, "actor"); deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        if ("LEGACY".equals(actor.issuerFingerprint()) || runAuthorization != null
                && (!actor.equals(runAuthorization.actor()) || deadlineAt.isAfter(runAuthorization.deadlineAt()))) throw new IllegalArgumentException("Memory operation context is invalid");
        if (preferenceWrite != null) {
            var command = Objects.requireNonNull(preferenceWrite.command(), "command");
            if (runAuthorization != null || !actor.equals(preferenceWrite.actor()) || actor.subjectType() != TrustedAgentActor.SubjectType.USER
                    || preferenceWrite.expiresAt() == null || deadlineAt.isAfter(preferenceWrite.expiresAt())
                    || command.owner().type() != MemoryOwnerTypeEnum.USER || !command.owner().id().equals(actor.subjectId())
                    || command.kind() != MemoryKindEnum.PREFERENCE || command.confirmed() || command.expectedRevision() != 0 || command.source().sourceRunId() == null
                    || command.effectiveAt() != null || command.expiresAt() != null || command.confidence().compareTo(java.math.BigDecimal.ONE) >= 0
                    || command.sensitivity() != MemorySensitivityEnum.CONFIDENTIAL && command.sensitivity() != MemorySensitivityEnum.RESTRICTED) throw new IllegalArgumentException("Memory preference write context is invalid");
        }
    }
    @Override public String toString() { return "MemoryOperationContext[redacted]"; }
}
