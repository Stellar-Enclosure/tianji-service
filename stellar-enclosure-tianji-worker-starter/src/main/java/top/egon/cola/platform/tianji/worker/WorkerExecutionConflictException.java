package top.egon.cola.platform.tianji.worker;

public final class WorkerExecutionConflictException extends IllegalStateException {
    public WorkerExecutionConflictException() { super("Worker execution conflicts with an existing receipt"); }
}
