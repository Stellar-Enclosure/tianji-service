package top.egon.cola.platform.tianji.agent.domain;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import static org.junit.jupiter.api.Assertions.*;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

class AgentRunTransitionsTest {
    @Test
    void lifecycle_allows_expected_paths_and_never_reopens_a_terminal_run() {
        assertTrue(AgentRunTransitions.allows(QUEUED, RUNNING));
        assertTrue(AgentRunTransitions.allows(RUNNING, WAITING));
        assertTrue(AgentRunTransitions.allows(WAITING, QUEUED));
        assertTrue(AgentRunTransitions.allows(RUNNING, COMPLETED));
        assertFalse(AgentRunTransitions.allows(QUEUED, COMPLETED));
        for (AgentRunStatusEnum terminal : new AgentRunStatusEnum[]{COMPLETED, FAILED, CANCELLED, TIMED_OUT}) {
            assertTrue(AgentRunTransitions.terminal(terminal));
            for (AgentRunStatusEnum target : AgentRunStatusEnum.values()) {
                assertFalse(AgentRunTransitions.allows(terminal, target));
            }
        }
    }
}
