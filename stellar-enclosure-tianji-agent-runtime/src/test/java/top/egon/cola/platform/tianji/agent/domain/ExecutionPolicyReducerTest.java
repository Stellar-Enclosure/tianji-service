package top.egon.cola.platform.tianji.agent.domain;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionPolicyReducerTest {

    private final ExecutionPolicyDTO policy = new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30),
            Duration.ofSeconds(20), 10, 5, 1000, 500, new BigDecimal("8.00"), "USD", 4,
            Set.of("search@1", "read@1"), "retry-v1", "approval-v1");

    @Test
    void accepts_only_narrower_limits_and_preserves_server_policies() {
        ExecutionPolicyOverrideDTO override = new ExecutionPolicyOverrideDTO(Duration.ofMinutes(1),
                Duration.ofSeconds(10), Duration.ofSeconds(5), 3, 0, 500L, 200L,
                new BigDecimal("2.00"), 1, Set.of("read@1"));
        ExecutionPolicyDTO effective = ExecutionPolicyReducer.reduce(policy, override);
        assertEquals(Duration.ofMinutes(1), effective.totalTimeout());
        assertEquals(0, effective.maxToolCalls());
        assertEquals(Set.of("read@1"), effective.allowedToolVersions());
        assertEquals("approval-v1", effective.approvalPolicyRef());
        assertEquals("retry-v1", effective.retryPolicyRef());
    }

    @Test
    void rejects_expanded_limits_and_tool_allowlists() {
        ExecutionPolicyOverrideDTO expandedTools = new ExecutionPolicyOverrideDTO(null, null, null,
                null, null, null, null, null, null, Set.of("write@1"));
        AgentLedgerException tools = assertThrows(AgentLedgerException.class,
                () -> ExecutionPolicyReducer.reduce(policy, expandedTools));
        assertEquals(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, tools.code());
        assertThrows(AgentLedgerException.class, () -> ExecutionPolicyReducer.reduce(policy,
                new ExecutionPolicyOverrideDTO(null, null, null, 11, null, null, null, null, null, null)));
        assertThrows(AgentLedgerException.class, () -> ExecutionPolicyReducer.reduce(policy,
                new ExecutionPolicyOverrideDTO(null, null, null, null, null, null, null,
                        new BigDecimal("8.01"), null, null)));
        assertThrows(AgentLedgerException.class, () -> ExecutionPolicyReducer.reduce(policy,
                new ExecutionPolicyOverrideDTO(Duration.ofMinutes(6), null, null,
                        null, null, null, null, null, null, null)));
    }
}
