package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** The lease token is a transient capability; it and task input must not be logged. */
public record WorkerTaskDTO(String taskId, String activityOccurrenceId, WorkerScopeTypeEnum scopeType,
                            String processInstanceId, String caseInstanceId, long tenantId,
                            String topic, String schemaVersion, WorkerEffectSafetyEnum effectSafety,
                            Map<String, ProcessValueTypeEnum> inputSchema, Map<String, ProcessValueTypeEnum> outputSchema,
                            String businessKey, String correlationId,
                            String idempotencyKey, Instant acquiredAt, Instant leaseExpiresAt,
                            Map<String, Object> input, String leaseToken) {
    public WorkerTaskDTO {
        taskId = OrchestrationChecks.id(taskId, 255);
        activityOccurrenceId = OrchestrationChecks.id(activityOccurrenceId, 255);
        scopeType = Objects.requireNonNull(scopeType, "scopeType");
        if (scopeType == WorkerScopeTypeEnum.BPMN && caseInstanceId == null) {
            processInstanceId = OrchestrationChecks.id(processInstanceId, 255);
        } else if (scopeType == WorkerScopeTypeEnum.CMMN && processInstanceId == null) {
            caseInstanceId = OrchestrationChecks.id(caseInstanceId, 255);
        } else throw OrchestrationChecks.invalid();
        if (tenantId <= 0) throw OrchestrationChecks.invalid();
        var contract = new WorkerContractDTO(topic, schemaVersion, effectSafety, inputSchema, outputSchema);
        topic = contract.topic();
        schemaVersion = contract.schemaVersion();
        effectSafety = contract.effectSafety();
        inputSchema = contract.inputs();
        outputSchema = contract.outputs();
        businessKey = OrchestrationChecks.id(businessKey, 255);
        correlationId = OrchestrationChecks.command(correlationId);
        idempotencyKey = OrchestrationChecks.command(idempotencyKey);
        acquiredAt = Objects.requireNonNull(acquiredAt, "acquiredAt");
        leaseExpiresAt = Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        if (!leaseExpiresAt.isAfter(acquiredAt) || Duration.between(acquiredAt, leaseExpiresAt).compareTo(Duration.ofMinutes(15)) > 0) {
            throw OrchestrationChecks.invalid();
        }
        input = contract.validateInput(input);
        leaseToken = OrchestrationChecks.leaseToken(leaseToken);
    }

    public WorkerContractDTO contract() { return new WorkerContractDTO(topic, schemaVersion, effectSafety, inputSchema, outputSchema); }

    @Override public String toString() {
        return "WorkerTaskDTO[taskId=" + taskId + ", activityOccurrenceId=" + activityOccurrenceId
                + ", topic=" + topic + ", leaseExpiresAt=" + leaseExpiresAt + ", input=<redacted>, leaseToken=<redacted>]";
    }
}
