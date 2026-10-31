package top.egon.cola.platform.tianji.agent.domain.tool;

import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Current tool-requester authority, separate from the run's CLAIM/MODEL permission.
 * Implementations must bind every field to independently verified evidence and the exact registered
 * execution resource. A model argument, tool descriptor or original run grant is not a tool decision.
 * Call outside database transactions, without retaining a USER bearer; unknown authority is UNAVAILABLE.
 */
@FunctionalInterface
public interface ToolExecutionAuthorization {
    Decision authorize(Request request, Boundary boundary);
    enum Decision { ALLOW, DENY, UNAVAILABLE }
    enum Boundary { PREPARE, DISPATCH, RECONCILE }

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
