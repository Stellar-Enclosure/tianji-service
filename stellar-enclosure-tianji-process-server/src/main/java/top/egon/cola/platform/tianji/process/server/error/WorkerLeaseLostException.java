package top.egon.cola.platform.tianji.process.server.error;

/** The worker can no longer safely act on this lease. */
public final class WorkerLeaseLostException extends RuntimeException {
    public WorkerLeaseLostException() { super("Worker lease is no longer valid"); }
}
