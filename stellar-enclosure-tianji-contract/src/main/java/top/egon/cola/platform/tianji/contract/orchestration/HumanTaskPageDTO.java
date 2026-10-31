package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** An empty page can carry a continuation through inaccessible rows; it does not imply an empty task scope. */
public record HumanTaskPageDTO(HumanTaskScopeTypeEnum scopeType, HumanTaskSelectionEnum selection, List<HumanTaskDTO> items, String nextAfterTaskId) {
    public HumanTaskPageDTO {
        scopeType = Objects.requireNonNull(scopeType, "scopeType"); selection = Objects.requireNonNull(selection, "selection"); items = List.copyOf(items);
        if (items.size() > 50) throw OrchestrationChecks.invalid(); if (nextAfterTaskId != null) nextAfterTaskId = OrchestrationChecks.id(nextAfterTaskId, 255);
        var ids = new HashSet<String>();
        for (var task : items) if (task.scopeType() != scopeType || !ids.add(task.taskId()) || selection == HumanTaskSelectionEnum.ASSIGNED && !task.assignedToMe()
                || selection == HumanTaskSelectionEnum.CANDIDATE && task.assigned()) throw OrchestrationChecks.invalid();
    }
}
