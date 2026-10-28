package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;

import java.time.Instant;
import java.util.Map;

/** Business-facing task data deliberately excludes the lease capability. */
public record WorkerExecutionContext(String sourceId, long tenantId, String taskId, String activityOccurrenceId, WorkerScopeTypeEnum scopeType,
                                     String processInstanceId, String caseInstanceId, String topic, String schemaVersion,
                                     WorkerEffectSafetyEnum effectSafety, String businessKey, String correlationId,
                                     String idempotencyKey, Instant deadline, Map<String, Object> input) {
    public WorkerExecutionContext { input = Map.copyOf(input); }

    static WorkerExecutionContext from(WorkerTaskDTO task, String sourceId, Instant deadline) {
        return new WorkerExecutionContext(sourceId, task.tenantId(), task.taskId(), task.activityOccurrenceId(), task.scopeType(),
                task.processInstanceId(), task.caseInstanceId(), task.topic(), task.schemaVersion(), task.effectSafety(),
                task.businessKey(), task.correlationId(), task.idempotencyKey(), deadline, task.input());
    }

    @Override public String toString() {
        return "WorkerExecutionContext[taskId=" + taskId + ", activityOccurrenceId=" + activityOccurrenceId
                + ", topic=" + topic + ", deadline=" + deadline + ", input=<redacted>]";
    }
}
