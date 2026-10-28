package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerContractDTO;

import java.util.Objects;

/** Operator-owned handler version and the exact topic contract it implements. */
public record WorkerHandlerDescriptor(WorkerContractDTO contract, String handlerVersion) {
    public WorkerHandlerDescriptor {
        contract = Objects.requireNonNull(contract, "contract");
        if (handlerVersion == null || !handlerVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new IllegalArgumentException("Worker handler descriptor is invalid");
        }
    }
}
