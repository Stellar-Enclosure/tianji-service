package top.egon.cola.platform.tianji.contract.orchestration;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CasePlanItemDTO(String planItemInstanceId, String planItemDefinitionKey,
                              CasePlanItemKindEnum kind, CasePlanItemStateEnum state, String version,
                              List<CasePlanItemActionEnum> availableActions) {
    public CasePlanItemDTO {
        planItemInstanceId = OrchestrationChecks.id(planItemInstanceId, 255);
        planItemDefinitionKey = OrchestrationChecks.definition(planItemDefinitionKey);
        kind = Objects.requireNonNull(kind, "kind");
        state = Objects.requireNonNull(state, "state");
        version = OrchestrationChecks.hash(version);
        availableActions = List.copyOf(availableActions);
        Set<CasePlanItemActionEnum> allowed = switch (state) {
            case AVAILABLE, COMPLETED, TERMINATED -> Set.of();
            case ENABLED -> Set.of(CasePlanItemActionEnum.DISABLE, CasePlanItemActionEnum.START);
            case DISABLED -> Set.of(CasePlanItemActionEnum.ENABLE);
            case ACTIVE -> Set.of(CasePlanItemActionEnum.TERMINATE);
        };
        if (availableActions.size() > 4 || new HashSet<>(availableActions).size() != availableActions.size()
                || !allowed.containsAll(availableActions)
                || kind == CasePlanItemKindEnum.MILESTONE && !availableActions.isEmpty()) {
            throw OrchestrationChecks.invalid();
        }
    }
}
