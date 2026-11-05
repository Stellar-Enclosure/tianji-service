package top.egon.cola.platform.tianji.process.server.error;

/** Current evidence does not permit scheduling this Worker recovery. */
public final class ProcessWorkerRecoveryBlockedException extends RuntimeException {
    public ProcessWorkerRecoveryBlockedException() {
        super("Process Worker recovery is blocked");
    }
}
