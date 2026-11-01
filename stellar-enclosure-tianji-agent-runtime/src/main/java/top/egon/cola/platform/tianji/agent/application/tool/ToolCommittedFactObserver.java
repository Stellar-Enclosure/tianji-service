package top.egon.cola.platform.tianji.agent.application.tool;

import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;

/** Observes an already committed status without receiving private result data or execution authority. */
@FunctionalInterface
public interface ToolCommittedFactObserver {
    void onCommitted(ToolExecutionOutcome.Status status);
}
