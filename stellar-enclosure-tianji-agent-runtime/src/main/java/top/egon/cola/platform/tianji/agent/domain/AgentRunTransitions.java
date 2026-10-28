package top.egon.cola.platform.tianji.agent.domain;

import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

/** Management lifecycle transitions; ADK continues to own its execution state. */
public final class AgentRunTransitions {
    private static final Map<AgentRunStatusEnum, Set<AgentRunStatusEnum>> ALLOWED = transitions();
    private static final Set<AgentRunStatusEnum> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED, TIMED_OUT);

    private AgentRunTransitions() {
    }

    public static boolean allows(AgentRunStatusEnum from, AgentRunStatusEnum to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean terminal(AgentRunStatusEnum status) {
        return TERMINAL.contains(status);
    }

    private static Map<AgentRunStatusEnum, Set<AgentRunStatusEnum>> transitions() {
        Map<AgentRunStatusEnum, Set<AgentRunStatusEnum>> result = new EnumMap<>(AgentRunStatusEnum.class);
        result.put(CREATED, EnumSet.of(QUEUED, CANCELLED));
        result.put(QUEUED, EnumSet.of(RUNNING, SUSPENDED, CANCELLED, TIMED_OUT));
        result.put(RUNNING, EnumSet.of(WAITING, COMPLETED, FAILED, SUSPENDED, CANCELLED, TIMED_OUT));
        result.put(WAITING, EnumSet.of(QUEUED, CANCELLED, TIMED_OUT));
        result.put(SUSPENDED, EnumSet.of(QUEUED, FAILED, CANCELLED, TIMED_OUT));
        return Map.copyOf(result);
    }
}
