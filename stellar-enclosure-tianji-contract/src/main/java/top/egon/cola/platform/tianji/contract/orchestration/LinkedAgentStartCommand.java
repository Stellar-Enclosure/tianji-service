package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.Objects;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

/** A service-only submission; its owner is derived from authenticated identity, not this payload. */
public record LinkedAgentStartCommand(StartAgentRunCommand start, AgentProcessLink link) {
    public LinkedAgentStartCommand {
        start = Objects.requireNonNull(start, "start");
        link = Objects.requireNonNull(link, "link");
        OrchestrationChecks.command(start.idempotencyKey());
        OrchestrationChecks.command(start.correlationId());
    }

    @Override public String toString() { return "LinkedAgentStartCommand[redacted]"; }
}
