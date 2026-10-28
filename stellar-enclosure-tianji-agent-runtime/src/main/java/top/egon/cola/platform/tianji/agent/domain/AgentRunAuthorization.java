package top.egon.cola.platform.tianji.agent.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Verifies a bounded execution grant and current authorization at each requested boundary.
 * Implementations must bind the decision to every field, a configured exact resource URI and
 * {@link #PURPOSE}, and must recheck current permissions (including session access when required).
 * They must not rely on a submission-time permission snapshot or retain a USER bearer token.
 * Calls must be bounded: return DENY for a confirmed refusal and UNAVAILABLE when authority
 * cannot be reached or verified; unexpected exceptions are also treated as unavailable.
 */
@FunctionalInterface
public interface AgentRunAuthorization {
    String PURPOSE = "TIANJI_AGENT_RUN_EXECUTION";

    Decision authorize(Request request, Boundary boundary);

    enum Decision { ALLOW, DENY, UNAVAILABLE }
    enum Boundary { CLAIM, MODEL }

    /** Only identifiers and content fingerprints cross the authorization boundary. */
    record Request(TrustedAgentActor actor, String runId, String definitionKey, long definitionVersion,
                   String definitionSha256, String requestSha256, String snapshotSha256,
                   String policyVersionRef, boolean sessionRequired, Instant deadlineAt) {
        public Request {
            actor = Objects.requireNonNull(actor, "actor");
            if (runId == null || runId.isBlank() || runId.length() > 128
                    || definitionKey == null || definitionKey.isBlank() || definitionKey.length() > 128
                    || definitionVersion < 1 || !hash(definitionSha256) || !hash(requestSha256)
                    || !hash(snapshotSha256) || policyVersionRef == null || policyVersionRef.isBlank()
                    || policyVersionRef.length() > 256 || deadlineAt == null) {
                throw new IllegalArgumentException("Agent authorization request is invalid");
            }
        }

        private static boolean hash(String value) {
            return value != null && value.matches("[0-9a-f]{64}");
        }

        @Override public String toString() { return "AgentRunAuthorization.Request[redacted]"; }
    }
}
