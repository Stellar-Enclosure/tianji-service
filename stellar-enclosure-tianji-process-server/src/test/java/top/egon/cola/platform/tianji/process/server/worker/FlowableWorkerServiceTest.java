package top.egon.cola.platform.tianji.process.server.worker;

import org.flowable.job.service.impl.asyncexecutor.AsyncExecutor;
import org.flowable.job.service.impl.asyncexecutor.AsyncJobExecutorConfiguration;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlowableWorkerServiceTest {
    @Test
    void acquisition_requires_live_native_job_processing_and_expiry_reset() {
        var engine = mock(SpringProcessEngineConfiguration.class);
        var executor = mock(AsyncExecutor.class);
        var configuration = mock(AsyncJobExecutorConfiguration.class);
        when(engine.isAsyncExecutorActivate()).thenReturn(true);
        when(engine.getAsyncExecutor()).thenReturn(executor);
        when(executor.isActive()).thenReturn(true);
        when(engine.getAsyncExecutorConfiguration()).thenReturn(configuration);
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(configuration.isResetExpiredJobEnabled()).thenReturn(true);
        assertDoesNotThrow(() -> WorkerNativeAvailability.require(engine));
        when(configuration.isResetExpiredJobEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
        when(configuration.isResetExpiredJobEnabled()).thenReturn(true);
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
        when(configuration.isAsyncJobAcquisitionEnabled()).thenReturn(true);
        when(executor.isActive()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> WorkerNativeAvailability.require(engine));
    }
}
