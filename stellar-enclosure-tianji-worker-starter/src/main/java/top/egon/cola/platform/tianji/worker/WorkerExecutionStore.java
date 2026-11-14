package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerTaskDTO;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Transactional handlers commit their business mutation and receipt atomically; other modes require durable intent and replay. */
public interface WorkerExecutionStore {
    long tenantId();
    Duration deadlineSafetyMargin();
    WorkerExecutionReceipt execute(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler);

    /** A store must opt in only after checking its durable non-transactional intent schema. */
    default void requireNonTransactional() { throw new UnsupportedOperationException("Non-transactional workers are not supported"); }

    default WorkerExecutionReceipt executeNonTransactional(WorkerTaskDTO task, WorkerHandlerDescriptor descriptor,
                                                            String targetId, NonTransactionalWorkerHandler handler) {
        throw new UnsupportedOperationException("Non-transactional workers are not supported");
    }
    default WorkerExecutionReceipt executeAutomation(@Valid AutomationWorkerTaskDTO task,
                                                      @Valid AutomationWorkerHandlerRegistration registration) {
        throw new IllegalStateException("Typed worker storage unavailable");
    }

    /** Trusted local storage may enable typed leasing after verifying its durable effect storage. */
    default Optional<AutomationWorkerReadinessVO> automationReadiness() {
        return Optional.empty();
    }

    record AutomationWorkerReadinessVO(long tenantId, String sourceId) {
        public AutomationWorkerReadinessVO {
            if (tenantId <= 0 || sourceId == null || !sourceId.matches(AutomationWorkerHandlerDescriptor.REGISTERED_ID)) {
                throw new IllegalArgumentException("Worker automation scope is invalid");
            }
        }
    }

    /** Snapshots an untrusted transport DTO and verifies it against the fixed local scope and registered handler. */
    static AutomationWorkerTaskDTO verifiedAutomationTask(@Valid AutomationWorkerTaskDTO task,
            @Valid AutomationWorkerHandlerRegistration registration, AutomationWorkerReadinessVO scope) {
        var input = WorkerDigests.immutableTypedObject(task.getInput());
        var normalized = registration.normalizeInput(input);
        if (!Arrays.equals(ProcessTypedJson.canonicalBytes(input), ProcessTypedJson.canonicalBytes(normalized))) {
            throw new IllegalArgumentException("Worker automation input is not normalized");
        }
        var snapshot = new AutomationWorkerTaskDTO(task.getTaskId(), task.getActivityOccurrenceId(), task.getNodeExecutionRef(),
                task.getLeaseToken(), task.getCapabilityKey(), task.getCapabilityVersion(), normalized, task.getInputSha256(),
                task.getEffectKey(), task.getAttemptDeadlineAt(), task.getLeaseExpiresAt(), task.getHandlerVersion(),
                task.getEffectMode(), task.getTargetId(), task.getInputSchemaSha256(), task.getOutputSchemaSha256(),
                task.getEffectFingerprint(), task.getEffectRecovery(), task.getTaskContractSha256());
        snapshot.verifyContractHash(scope.tenantId(), scope.sourceId());
        var descriptor = registration.descriptor();
        if (!descriptor.capabilityKey().equals(snapshot.getCapabilityKey())
                || descriptor.capabilityVersion() != snapshot.getCapabilityVersion()
                || !descriptor.inputSchemaSha256().equals(snapshot.getInputSchemaSha256())
                || !descriptor.outputSchemaSha256().equals(snapshot.getOutputSchemaSha256())
                || !descriptor.handlerVersion().equals(snapshot.getHandlerVersion())
                || descriptor.effectMode() != snapshot.getEffectMode()
                || !Objects.equals(descriptor.targetId(), snapshot.getTargetId())
                || !WorkerDigests.taskInputSha256(normalized).equals(snapshot.getInputSha256())
                || !WorkerDigests.automationEffectFingerprint(descriptor, normalized).equals(snapshot.getEffectFingerprint())) {
            throw new IllegalArgumentException("Worker automation task does not match its registered handler");
        }
        return snapshot;
    }
}
