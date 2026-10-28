package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TianjiAgentReconciliationAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TianjiAgentReconciliationAutoConfiguration.class));

    @Test
    void disabled_by_default_and_enabled_configuration_requires_its_host_scheduler() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AgentRunReconciler.class);
        });
        runner.withPropertyValues("stellar.tianji.agent.reconciliation.enabled=true")
                .withBean(AgentLedgerService.class, () -> mock(AgentLedgerService.class))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabled_configuration_uses_and_cancels_the_explicit_host_scheduler() {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(scheduler.scheduleWithFixedDelay(any(Runnable.class), any(Duration.class))).thenAnswer(ignored -> future);
        runner.withPropertyValues("stellar.tianji.agent.reconciliation.enabled=true",
                        "stellar.tianji.agent.reconciliation.batch-size=12",
                        "stellar.tianji.agent.reconciliation.interval=3s")
                .withBean(AgentLedgerService.class, () -> mock(AgentLedgerService.class))
                .withBean("tianjiAgentReconciliationScheduler", TaskScheduler.class, () -> scheduler)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AgentRunReconciler.class);
                    assertThat(context.getBean(AgentRunReconciler.class).isRunning()).isTrue();
                    verify(scheduler).scheduleWithFixedDelay(any(Runnable.class), eq(Duration.ofSeconds(3)));
                });
        verify(future).cancel(false);
    }
}
