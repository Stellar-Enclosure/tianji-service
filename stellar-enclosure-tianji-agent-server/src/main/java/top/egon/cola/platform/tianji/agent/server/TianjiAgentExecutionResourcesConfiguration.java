package top.egon.cola.platform.tianji.agent.server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Dedicated, bounded resources for the standalone host; explicit host beans may replace them. */
@Configuration(proxyBeanMethods = false)
public class TianjiAgentExecutionResourcesConfiguration {
    @Bean(name = "tianjiAgentDispatchExecutor", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "tianjiAgentDispatchExecutor")
    ThreadPoolExecutor tianjiAgentDispatchExecutor(Environment environment) {
        int threads = bounded(environment, "stellar.tianji.agent.runtime.dispatch-threads", 4, 1, 128);
        int capacity = bounded(environment, "stellar.tianji.agent.runtime.dispatch-queue-capacity", 4, 0, 128);
        BlockingQueue<Runnable> queue = capacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(capacity);
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, queue,
                threads("tianji-agent-run-"), new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean(name = "tianjiAgentControlExecutor", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "tianjiAgentControlExecutor")
    ScheduledThreadPoolExecutor tianjiAgentControlExecutor(Environment environment) {
        int count = bounded(environment, "stellar.tianji.agent.runtime.control-threads", 4, 1, 128);
        var executor = new ScheduledThreadPoolExecutor(count, threads("tianji-agent-control-"), new ThreadPoolExecutor.AbortPolicy());
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean(name = "tianjiAgentReconciliationScheduler")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.reconciliation", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "tianjiAgentReconciliationScheduler")
    ThreadPoolTaskScheduler tianjiAgentReconciliationScheduler() {
        return scheduler("tianji-agent-reconcile-");
    }

    @Bean(name = "tianjiAgentDispatchScheduler")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.dispatch", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "tianjiAgentDispatchScheduler")
    ThreadPoolTaskScheduler tianjiAgentDispatchScheduler() {
        return scheduler("tianji-agent-discovery-");
    }

    private static ThreadPoolTaskScheduler scheduler(String name) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadFactory(threads(name));
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        scheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setPhase(Integer.MAX_VALUE - 300);
        return scheduler;
    }

    private static ThreadFactory threads(String name) {
        return Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name(name, 0).factory();
    }

    private static int bounded(Environment environment, String property, int defaultValue, int min, int max) {
        int value = environment.getProperty(property, Integer.class, defaultValue);
        if (value < min || value > max) throw new IllegalArgumentException("TIANJI_AGENT_EXECUTION_RESOURCES_INVALID");
        return value;
    }
}
