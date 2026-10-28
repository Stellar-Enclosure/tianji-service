package top.egon.cola.platform.tianji.process.server.worker;

import org.flowable.spring.SpringProcessEngineConfiguration;

/** Worker leases require both native async completion and expired-lock recovery. */
public final class WorkerNativeAvailability {
    private WorkerNativeAvailability() { }

    public static void require(SpringProcessEngineConfiguration process) {
        var configuration = process.getAsyncExecutorConfiguration();
        if (!process.isAsyncExecutorActivate() || process.getAsyncExecutor() == null || !process.getAsyncExecutor().isActive()
                || configuration == null || !configuration.isAsyncJobAcquisitionEnabled() || !configuration.isResetExpiredJobEnabled()) {
            throw new IllegalStateException("Worker acquisition requires the native job executor and expired-lock reset");
        }
    }
}
