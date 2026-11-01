package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record CaseInstanceDTO(String caseInstanceId, String definitionKey, String releaseVersion,
                              String businessKey, String correlationId, CaseInstanceStatusEnum status,
                              Instant submittedAt, Instant finishedAt, List<CasePlanItemDTO> planItems) {
    public CaseInstanceDTO {
        caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
        definitionKey = OrchestrationChecks.definition(definitionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        businessKey = OrchestrationChecks.id(businessKey, 255);
        correlationId = OrchestrationChecks.command(correlationId);
        status = Objects.requireNonNull(status, "status");
        submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        planItems = List.copyOf(planItems);
        var instanceIds = new HashSet<String>();
        var definitionKeys = new HashSet<String>();
        if (planItems.size() > 16 || (status == CaseInstanceStatusEnum.ACTIVE) != (finishedAt == null)) {
            throw OrchestrationChecks.invalid();
        }
        for (var item : planItems) {
            if (!instanceIds.add(item.planItemInstanceId()) || !definitionKeys.add(item.planItemDefinitionKey())
                    || status != CaseInstanceStatusEnum.ACTIVE && !item.availableActions().isEmpty()) {
                throw OrchestrationChecks.invalid();
            }
        }
    }
}
