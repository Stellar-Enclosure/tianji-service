package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;

import java.util.Objects;

public record ClaimedAgentRun(AgentRunDTO run, AgentRunLease lease) {
    public ClaimedAgentRun {
        run = Objects.requireNonNull(run, "run");
        lease = Objects.requireNonNull(lease, "lease");
        if (!run.runId().equals(lease.runId())) {
            throw new IllegalArgumentException("run and lease do not match");
        }
    }
}
