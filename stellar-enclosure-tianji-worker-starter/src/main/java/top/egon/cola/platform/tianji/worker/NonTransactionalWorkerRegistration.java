package top.egon.cola.platform.tianji.worker;

import java.util.Objects;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;

/** A read-only or externally idempotent operation with an operator-owned stable target. */
public record NonTransactionalWorkerRegistration(WorkerHandlerDescriptor descriptor, String targetId,
                                                  NonTransactionalWorkerHandler handler) {
    public NonTransactionalWorkerRegistration {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        handler = Objects.requireNonNull(handler, "handler");
        if (descriptor.contract().effectSafety() == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT || targetId == null
                || !targetId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("Worker handler safety mode is not supported");
        }
    }
}
