package top.egon.cola.platform.tianji.worker;

import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;

import java.util.Objects;

/** A bounded failure classification, without a raw business exception or stack trace in the remote receipt. */
public final class WorkerHandlerFailureException extends RuntimeException {
    private final WorkerFailureKindEnum kind;
    private final String code;

    public WorkerHandlerFailureException(WorkerFailureKindEnum kind, String code) {
        super("Worker handler reported a failure");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}")) throw new IllegalArgumentException("Worker failure code is invalid");
        this.code = code;
    }

    public WorkerFailureKindEnum kind() { return kind; }
    public String code() { return code; }
}
