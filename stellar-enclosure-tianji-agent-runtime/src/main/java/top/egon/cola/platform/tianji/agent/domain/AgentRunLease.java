package top.egon.cola.platform.tianji.agent.domain;

import java.time.Instant;
import java.util.Objects;

/** Fencing token for one claimed Agent run; PostgreSQL determines the expiry. */
public record AgentRunLease(String runId, String owner, long epoch, Instant expiresAt) {
    public AgentRunLease {
        if (runId == null || runId.isBlank() || runId.length() > 128
                || owner == null || owner.isBlank() || owner.length() > 128 || epoch <= 0) {
            throw new IllegalArgumentException("Agent run lease is invalid");
        }
        runId = runId.trim();
        owner = owner.trim();
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
