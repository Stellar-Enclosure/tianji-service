package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;

import java.time.Instant;
import java.util.Objects;

public record WorkerExecutionReceipt(String handlerVersion, Instant committedAt, WorkerBusinessResultDTO result, boolean replayed) {
    public WorkerExecutionReceipt {
        if (handlerVersion == null || !handlerVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw new IllegalArgumentException("Worker receipt is invalid");
        committedAt = Objects.requireNonNull(committedAt, "committedAt");
        result = Objects.requireNonNull(result, "result");
    }
}
