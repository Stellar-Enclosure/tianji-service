package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** An immutable process occurrence and the deadline for submitting its Agent run. */
public record AgentProcessLink(String sourceId, WorkerScopeTypeEnum scopeType, String scopeInstanceId,
                               String activityOccurrenceId, String definitionContentSha256, Instant dispatchNotAfter) {
    public AgentProcessLink {
        sourceId = OrchestrationChecks.command(sourceId);
        scopeType = Objects.requireNonNull(scopeType, "scopeType");
        scopeInstanceId = OrchestrationChecks.id(scopeInstanceId, 255);
        activityOccurrenceId = OrchestrationChecks.id(activityOccurrenceId, 255);
        if (scopeInstanceId.getBytes(StandardCharsets.UTF_8).length > 512
                || activityOccurrenceId.getBytes(StandardCharsets.UTF_8).length > 512) throw OrchestrationChecks.invalid();
        definitionContentSha256 = OrchestrationChecks.hash(definitionContentSha256);
        dispatchNotAfter = Objects.requireNonNull(dispatchNotAfter, "dispatchNotAfter");
        if (dispatchNotAfter.isBefore(Instant.EPOCH) || dispatchNotAfter.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
                || !dispatchNotAfter.equals(dispatchNotAfter.truncatedTo(ChronoUnit.MICROS))) throw OrchestrationChecks.invalid();
    }
}
