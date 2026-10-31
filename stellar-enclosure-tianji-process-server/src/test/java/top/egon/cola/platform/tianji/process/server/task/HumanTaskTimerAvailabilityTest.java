package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HumanTaskTimerAvailabilityTest {
    @Test void timer_and_executable_job_acquisition_and_expired_lock_recovery_must_all_be_active() {
        var engine = mock(SpringProcessEngineConfiguration.class); var jobs = mock(AsyncJobExecutorConfiguration.class); var executor = mock(AsyncExecutor.class);
        assertThrows(IllegalStateException.class, () -> HumanTaskTimerAvailability.require(engine));
        when(engine.isAsyncExecutorActivate()).thenReturn(true); when(engine.getAsyncExecutorConfiguration()).thenReturn(jobs);
        when(jobs.isTimerJobAcquisitionEnabled()).thenReturn(true); when(jobs.isAsyncJobAcquisitionEnabled()).thenReturn(true); when(jobs.isResetExpiredJobEnabled()).thenReturn(true);
        when(engine.getAsyncExecutor()).thenReturn(executor); assertThrows(IllegalStateException.class, () -> HumanTaskTimerAvailability.require(engine));
        when(executor.isActive()).thenReturn(true); assertDoesNotThrow(() -> HumanTaskTimerAvailability.require(engine));
        when(jobs.isTimerJobAcquisitionEnabled()).thenReturn(false); assertThrows(IllegalStateException.class, () -> HumanTaskTimerAvailability.require(engine)); when(jobs.isTimerJobAcquisitionEnabled()).thenReturn(true);
        when(jobs.isAsyncJobAcquisitionEnabled()).thenReturn(false); assertThrows(IllegalStateException.class, () -> HumanTaskTimerAvailability.require(engine)); when(jobs.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(jobs.isResetExpiredJobEnabled()).thenReturn(false); assertThrows(IllegalStateException.class, () -> HumanTaskTimerAvailability.require(engine));
    }
}
