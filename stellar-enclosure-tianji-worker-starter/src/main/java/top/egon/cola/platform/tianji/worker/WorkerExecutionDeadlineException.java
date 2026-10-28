package top.egon.cola.platform.tianji.worker;

public final class WorkerExecutionDeadlineException extends IllegalStateException {
    public WorkerExecutionDeadlineException() { super("Worker execution deadline has elapsed"); }
}
