package top.egon.cola.platform.tianji.agent.server;

import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

@Timeout(20)
class TianjiAgentExecutionResourcesConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TianjiAgentExecutionResourcesConfiguration.class);

    @Test void does_not_create_execution_resources_by_default() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean("tianjiAgentDispatchExecutor")
                    .doesNotHaveBean("tianjiAgentControlExecutor")
                    .doesNotHaveBean("tianjiAgentDispatchScheduler")
                    .doesNotHaveBean("tianjiAgentReconciliationScheduler");
        });
    }

    @Test void creates_bounded_separate_resources_that_do_not_inherit_request_context() {
        var dispatchRef = new AtomicReference<ThreadPoolExecutor>();
        var controlRef = new AtomicReference<ScheduledThreadPoolExecutor>();
        var schedulerRef = new AtomicReference<ThreadPoolTaskScheduler>();
        var inherited = new InheritableThreadLocal<String>();
        inherited.set("request-context");
        try {
            runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.dispatch.enabled=true",
                    "stellar.tianji.agent.reconciliation.enabled=true").run(context -> {
                assertThat(context).hasNotFailed();
                var dispatch = context.getBean("tianjiAgentDispatchExecutor", ThreadPoolExecutor.class);
                var control = context.getBean("tianjiAgentControlExecutor", ScheduledThreadPoolExecutor.class);
                var discovery = context.getBean("tianjiAgentDispatchScheduler", ThreadPoolTaskScheduler.class);
                var reconcile = context.getBean("tianjiAgentReconciliationScheduler", ThreadPoolTaskScheduler.class);
                dispatchRef.set(dispatch); controlRef.set(control); schedulerRef.set(discovery);
                assertThat(dispatch.getMaximumPoolSize()).isEqualTo(4);
                assertThat(dispatch.getQueue().remainingCapacity()).isEqualTo(4);
                assertThat(dispatch.getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
                assertThat(control.getCorePoolSize()).isEqualTo(4);
                assertThat(control.getRemoveOnCancelPolicy()).isTrue();
                assertThat(discovery).isNotSameAs(reconcile);
                assertThat(discovery.getPhase()).isLessThan(Integer.MAX_VALUE - 200);
                try {
                    assertNull(dispatch.submit(inherited::get).get(5, TimeUnit.SECONDS));
                    assertNull(control.schedule(inherited::get, 0, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS));
                    var observed = new CompletableFuture<String>();
                    discovery.schedule(() -> observed.complete(inherited.get()), Instant.now());
                    assertNull(observed.get(5, TimeUnit.SECONDS));
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
        } finally { inherited.remove(); }
        assertThat(dispatchRef.get().isShutdown()).isTrue();
        assertThat(controlRef.get().isShutdown()).isTrue();
        assertThat(schedulerRef.get().getScheduledExecutor().isShutdown()).isTrue();
    }

    @Test void rejects_invalid_bounds_and_preserves_explicit_host_resources() {
        for (String property : new String[]{"dispatch-threads=0", "dispatch-threads=129",
                "dispatch-queue-capacity=129", "dispatch-queue-capacity=-1", "control-threads=0", "control-threads=129"}) {
            runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "stellar.tianji.agent.runtime." + property)
                    .run(context -> assertThat(context).hasFailed());
        }
        var custom = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        try {
            runner.withPropertyValues("stellar.tianji.agent.runtime.enabled=true")
                    .withBean("tianjiAgentDispatchExecutor", ThreadPoolExecutor.class, () -> custom)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean("tianjiAgentDispatchExecutor")).isSameAs(custom);
                    });
        } finally { custom.shutdownNow(); }
    }
}
