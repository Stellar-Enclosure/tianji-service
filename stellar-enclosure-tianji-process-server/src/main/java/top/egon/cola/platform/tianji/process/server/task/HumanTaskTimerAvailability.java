package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.spring.SpringProcessEngineConfiguration;

/** New timeout models require Flowable's own live timer acquisition and expired-lock recovery. */
public final class HumanTaskTimerAvailability {
    private HumanTaskTimerAvailability() { }
    public static void require(SpringProcessEngineConfiguration process) {
        var configuration = process.getAsyncExecutorConfiguration();
        if (!process.isAsyncExecutorActivate() || configuration == null || !configuration.isTimerJobAcquisitionEnabled() || !configuration.isAsyncJobAcquisitionEnabled() || !configuration.isResetExpiredJobEnabled()
                || process.getAsyncExecutor() == null || !process.getAsyncExecutor().isActive()) {
            throw new IllegalStateException("Human task timeouts require native timer/job acquisition and expired-lock recovery");
        }
    }
}
