package top.egon.cola.platform.tianji.agent.server.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.dispatch", name = "enabled", havingValue = "true")
public class TianjiGrantDispatchConfiguration {
    @Bean(destroyMethod = "close")
    TianjiGrantDispatchPoller tianjiGrantDispatchPoller(AgentLedgerService ledger, AgentRunDispatcher dispatcher,
            TianjiExecutionGrantAuthorizer authorizer, @Qualifier("tianjiAgentDispatchScheduler") TaskScheduler scheduler,
            Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.execution-authorization.enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_DISPATCH_REQUIRES_EXECUTION_AUTHORIZATION");
        }
        int batchSize = environment.getProperty("stellar.tianji.agent.dispatch.batch-size", Integer.class, 64);
        int maxInFlight = environment.getProperty("stellar.tianji.agent.dispatch.max-in-flight", Integer.class, 4);
        long intervalMillis = environment.getProperty("stellar.tianji.agent.dispatch.interval-ms", Long.class, 2000L);
        long shutdownMillis = environment.getProperty("stellar.tianji.agent.dispatch.shutdown-timeout-ms", Long.class, 5000L);
        return new TianjiGrantDispatchPoller(ledger, dispatcher, authorizer, scheduler, batchSize, maxInFlight,
                Duration.ofMillis(intervalMillis), Duration.ofMillis(shutdownMillis));
    }
}
