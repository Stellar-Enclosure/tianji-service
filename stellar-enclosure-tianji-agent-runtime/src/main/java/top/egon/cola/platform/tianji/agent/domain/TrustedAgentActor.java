package top.egon.cola.platform.tianji.agent.domain;

import java.util.Objects;

/** Internal identity created only after the caller has been authenticated and tenant-bound. */
public record TrustedAgentActor(long tenantId, SubjectType subjectType, String subjectId) {
    public enum SubjectType { USER, SERVICE }

    public TrustedAgentActor {
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        subjectType = Objects.requireNonNull(subjectType, "subjectType");
        if (subjectId == null || subjectId.isBlank() || subjectId.length() > 256) {
            throw new IllegalArgumentException("subjectId is required and must be at most 256 characters");
        }
        subjectId = subjectId.trim();
    }
}
