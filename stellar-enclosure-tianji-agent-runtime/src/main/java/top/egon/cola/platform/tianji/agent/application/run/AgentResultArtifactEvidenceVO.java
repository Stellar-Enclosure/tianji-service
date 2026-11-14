package top.egon.cola.platform.tianji.agent.application.run;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/** Short-lived, private metadata authorization for a single artifact verification query. */
@JsonIgnoreType
public record AgentResultArtifactEvidenceVO(@NotNull DecisionEnum decision,
                                            @Pattern(regexp = AgentResultArtifactQuery.REGISTERED_ID) String authorityRef,
                                            @Pattern(regexp = AgentResultArtifactQuery.SHA256) String querySha256,
                                            Instant checkedAt, Instant validUntil) {
    public enum DecisionEnum { ALLOW, DENY, UNAVAILABLE }

    public AgentResultArtifactEvidenceVO {
        if (decision == null) throw invalid();
        if (decision == DecisionEnum.ALLOW) {
            if (!AgentResultArtifactQuery.matches(authorityRef, AgentResultArtifactQuery.REGISTERED_ID)
                    || !AgentResultArtifactQuery.matches(querySha256, AgentResultArtifactQuery.SHA256)
                    || !AgentResultArtifactQuery.utcMicros(checkedAt) || !AgentResultArtifactQuery.utcMicros(validUntil)
                    || !checkedAt.isBefore(validUntil)) throw invalid();
        } else if (authorityRef != null || querySha256 != null || checkedAt != null || validUntil != null) {
            throw invalid();
        }
    }

    /** Rechecks this proof against the original query and the caller's current verification clock. */
    public boolean permits(AgentResultArtifactQuery query, Instant now) {
        if (decision != DecisionEnum.ALLOW || query == null || now == null) return false;
        var expected = query.snapshot();
        return expected.hasValidShape() && permits(expected.querySha256(), expected.getDeadlineAt(), now);
    }

    boolean permits(String expectedSha256, Instant deadlineAt, Instant now) {
        return decision == DecisionEnum.ALLOW && !checkedAt.isAfter(now) && now.isBefore(validUntil)
                && !validUntil.isAfter(deadlineAt) && querySha256.equals(expectedSha256);
    }

    @Override
    public String toString() {
        return "AgentResultArtifactEvidenceVO[decision=" + decision + "]";
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid Agent result artifact verification evidence");
    }
}
