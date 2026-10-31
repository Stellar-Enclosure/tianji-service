package top.egon.cola.platform.tianji.agent.autoconfigure;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowAutoConfiguration;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkReadOnlyDecisionToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentContextRuntimeOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;

/** Assembles the runtime only when the host supplies its storage, authorization and execution resources. */
@AutoConfiguration(after = AgentFlowAutoConfiguration.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime", name = "enabled", havingValue = "true")
public class TianjiAgentRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentLedgerService.class)
    AgentLedgerService tianjiAgentLedgerService(DataSource dataSource) {
        return new AgentLedgerService(dataSource);
    }

    @Bean(name = "tianjiAgentCodeFirstFactories")
    @ConditionalOnMissingBean(name = "tianjiAgentCodeFirstFactories")
    Map<String, Function<Scheduler, ? extends BaseAgent>> tianjiAgentCodeFirstFactories() {
        return Map.of();
    }

    @Bean
    AdkAgentRunExecutor tianjiAdkAgentRunExecutor(AgentLedgerService ledger, DataSource dataSource,
            AgentFlowFactory flowFactory, AgentFlowConfigValidator flowValidator,
            @Qualifier("agentFlowChatModels") Map<String, ChatModel> chatModels,
            @Qualifier("tianjiAgentCodeFirstFactories") Map<String, Function<Scheduler, ? extends BaseAgent>> factories,
            @Qualifier("tianjiAgentControlExecutor") ScheduledThreadPoolExecutor control,
            AgentModelBudgetEstimator estimator, AgentRunAuthorization authorizer, Environment environment,
            ObjectProvider<DefaultContextAssembler> contexts,
            @Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge> tools) {
        if (!ledger.usesDataSource(dataSource)) throw new IllegalStateException("TIANJI_RUNTIME_DATA_SOURCE_MISMATCH");
        if (chatModels.isEmpty() && factories.isEmpty()) throw new IllegalStateException("TIANJI_RUNTIME_MODEL_REQUIRED");
        if (chatModels.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || factories.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalStateException("TIANJI_RUNTIME_MODEL_CONFIGURATION_INVALID");
        }
        validateControlExecutor(control);
        boolean snapshots = environment.getProperty("stellar.tianji.agent.context.snapshots.enabled", Boolean.class, false);
        boolean trimming = environment.getProperty("stellar.tianji.agent.context.trimming.enabled", Boolean.class, false);
        var contextOptions = new AgentContextRuntimeOptions(snapshots, trimming);
        if (!snapshots) new AgentStorageVerifier(dataSource).verify();
        boolean typedWrites = environment.getProperty("stellar.tianji.agent.runtime.typed-session-writes", Boolean.class, false);
        boolean readOnlyTools = environment.getProperty("stellar.tianji.agent.tools.read-only-decision.enabled", Boolean.class, false);
        return new AdkAgentRunExecutor(ledger, flowFactory, flowValidator, chatModels, factories, control, estimator,
                dataSource, typedWrites, authorizer, contexts.getIfAvailable(DefaultContextAssembler::new), contextOptions,
                readOnlyTools ? tools::getObject : null);
    }

    @Bean(destroyMethod = "close")
    AgentRunDispatcher tianjiAgentRunDispatcher(@Qualifier("tianjiAgentDispatchExecutor") ThreadPoolExecutor dispatch,
            @Qualifier("tianjiAgentControlExecutor") ScheduledThreadPoolExecutor control,
            AdkAgentRunExecutor worker, AgentLedgerService ledger) {
        validateDispatchExecutor(dispatch, control);
        return new AgentRunDispatcher(dispatch, worker, ledger);
    }

    private static void validateControlExecutor(ScheduledThreadPoolExecutor control) {
        if (control.isShutdown() || control.getCorePoolSize() < 1 || control.getCorePoolSize() > 128
                || !control.getRemoveOnCancelPolicy()
                || !(control.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy)) {
            throw new IllegalStateException("TIANJI_RUNTIME_CONTROL_EXECUTOR_INVALID");
        }
    }

    private static void validateDispatchExecutor(ThreadPoolExecutor dispatch, ScheduledThreadPoolExecutor control) {
        long queueCapacity = (long) dispatch.getQueue().size() + dispatch.getQueue().remainingCapacity();
        if (dispatch == control || dispatch.isShutdown() || dispatch.getMaximumPoolSize() < 1
                || dispatch.getMaximumPoolSize() > 128 || queueCapacity > 128
                || !(dispatch.getRejectedExecutionHandler() instanceof ThreadPoolExecutor.AbortPolicy)) {
            throw new IllegalStateException("TIANJI_RUNTIME_DISPATCH_EXECUTOR_INVALID");
        }
    }
}
