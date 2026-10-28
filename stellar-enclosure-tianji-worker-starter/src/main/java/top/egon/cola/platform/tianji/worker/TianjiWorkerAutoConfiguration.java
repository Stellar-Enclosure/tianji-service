package top.egon.cola.platform.tianji.worker;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerClient;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;

@AutoConfiguration
@ConditionalOnProperty(prefix = "stellar.tianji.worker", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WorkerPollingProperties.class)
public class TianjiWorkerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(RemoteWorkerPoller.class)
    RemoteWorkerPoller remoteWorkerPoller(WorkerClient client, WorkerExecutionStore store,
                                          List<WorkerHandlerRegistration> handlers,
                                          @Qualifier("tianjiWorkerScheduler") TaskScheduler scheduler,
                                          @Qualifier("tianjiWorkerExecutor") Executor executor,
                                          WorkerPollingProperties properties) {
        return new RemoteWorkerPoller(client, store, handlers, scheduler, executor, properties, Clock.systemUTC());
    }
}
