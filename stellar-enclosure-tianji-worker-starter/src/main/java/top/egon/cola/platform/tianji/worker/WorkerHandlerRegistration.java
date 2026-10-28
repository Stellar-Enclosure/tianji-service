package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;

import java.util.Objects;

/** Local handler declaration; it does not grant any Process Server permission. */
public record WorkerHandlerRegistration(WorkerHandlerDescriptor descriptor, TransactionalWorkerHandler handler) {
    public WorkerHandlerRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
        if (descriptor.contract().effectSafety() != WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT) {
            throw new IllegalArgumentException("Worker handler safety mode is not supported");
        }
    }
}
