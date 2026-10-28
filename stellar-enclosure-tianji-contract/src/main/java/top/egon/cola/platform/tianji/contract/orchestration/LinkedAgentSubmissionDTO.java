package top.egon.cola.platform.tianji.contract.orchestration;

import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;

/** A closed submission can have no run when cancellation won before submission. */
public record LinkedAgentSubmissionDTO(boolean closed, AgentRunDTO run) {
    public LinkedAgentSubmissionDTO {
        if (!closed && run == null) throw OrchestrationChecks.invalid();
    }
}
