package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Validator;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.AutomationWorkerClient;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

@AutoConfiguration
@ConditionalOnProperty(prefix = "stellar.tianji.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkerPollingProperties.class)
public class TianjiWorkerAutoConfiguration {
    @Bean("remoteWorkerPoller")
    @ConditionalOnMissingBean(RemoteWorkerPoller.class)
    RemoteWorkerPoller remoteWorkerPoller(WorkerClient client, WorkerExecutionStore store,
                                          @Nullable List<WorkerHandlerRegistration> handlers,
                                          ObjectProvider<NonTransactionalWorkerRegistration> nonTransactionalHandlers,
                                          ObjectProvider<AutomationWorkerHandlerRegistration> automationHandlers,
                                          @Qualifier("automationWorkerClient") ObjectProvider<AutomationWorkerClient> automationClient,
                                          ObjectProvider<Validator> validators,
                                          @Qualifier("tianjiWorkerScheduler") TaskScheduler scheduler,
                                          @Qualifier("tianjiWorkerExecutor") Executor executor,
                                          WorkerPollingProperties properties) {
        return new RemoteWorkerPoller(client, automationClient.getIfAvailable(), store,
                handlers == null ? List.of() : handlers, nonTransactionalHandlers.orderedStream().toList(),
                automationHandlers.orderedStream().toList(),
                scheduler, executor, properties, Clock.systemUTC(), validators.getIfUnique());
    }
}
