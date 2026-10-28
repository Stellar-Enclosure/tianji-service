package top.egon.cola.platform.tianji.agent.domain;

import java.time.Instant;
import java.util.Objects;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

/** Queue candidate and a stable position for bounded, rotating discovery. */
public record GrantReadyAgentRun(TrustedAgentActor actor, GetAgentRunQuery query, Instant createdAt) {
    public GrantReadyAgentRun {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(query, "query"); Objects.requireNonNull(createdAt, "createdAt");
    }

    public Cursor cursor() { return new Cursor(createdAt, actor.tenantId(), query.runId()); }

    public record Cursor(Instant createdAt, long tenantId, String runId) {
        public Cursor {
            if (createdAt == null || tenantId <= 0 || runId == null || runId.isBlank() || runId.length() > 64) {
                throw new IllegalArgumentException("Agent dispatch cursor is invalid");
            }
        }
    }
}
