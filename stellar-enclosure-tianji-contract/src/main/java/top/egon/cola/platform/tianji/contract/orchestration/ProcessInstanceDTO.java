package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Instant;
import java.util.Objects;

public record ProcessInstanceDTO(String instanceId, String definitionKey, String releaseVersion, String businessKey,
                                 String correlationId, ProcessInstanceStatusEnum status, Instant submittedAt, Instant finishedAt) {
    public ProcessInstanceDTO {
        instanceId = OrchestrationChecks.id(instanceId, 255);
        definitionKey = OrchestrationChecks.definition(definitionKey);
        releaseVersion = OrchestrationChecks.release(releaseVersion);
        businessKey = OrchestrationChecks.id(businessKey, 255);
        correlationId = OrchestrationChecks.command(correlationId);
        status = Objects.requireNonNull(status, "status");
        submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        if ((status == ProcessInstanceStatusEnum.COMPLETED || status == ProcessInstanceStatusEnum.CANCELLED) != (finishedAt != null)) {
            throw OrchestrationChecks.invalid();
        }
    }
}
