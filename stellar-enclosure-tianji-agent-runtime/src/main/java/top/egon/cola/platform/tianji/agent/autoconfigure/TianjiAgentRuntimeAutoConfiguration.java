package top.egon.cola.platform.tianji.agent.autoconfigure;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter;
import top.egon.cola.component.agentflow.adapter.mcp.McpToolAdapter;
import top.egon.cola.component.agentflow.adapter.springai.SpringAiExecutionModelAdapter;
import top.egon.cola.component.agentflow.agentic.AgenticPlanCompiler;
import top.egon.cola.component.agentflow.api.session.SessionStore;
import top.egon.cola.component.agentflow.autoconfigure.AgentExecutionAutoConfiguration;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowAutoConfiguration;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.execution.DefaultAgentExecutor;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.application.run.AgentRuntimeExecutionService;
import top.egon.cola.platform.tianji.agent.application.run.AgenticTemplateConverter;
import top.egon.cola.platform.tianji.agent.application.run.AgenticTemplateConverterImpl;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverter;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverterImpl;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkGovernedProcessToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkReadOnlyDecisionToolBridge;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentContextRuntimeOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;

/** Assembles the runtime only when the host supplies its storage, authorization and execution resources. */
@AutoConfiguration(after = {AgentFlowAutoConfiguration.class, AgentExecutionAutoConfiguration.class})
@ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime", name = "enabled", havingValue = "true")
@Import({AgenticTemplateConverterImpl.class, TianjiHostContextConverterImpl.class})
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
            @Qualifier("tianjiAdkGovernedProcessTools") ObjectProvider<AdkGovernedProcessToolBridge> governedProcessTools,
            @Qualifier("tianjiAdkReadOnlyDecisionTools") ObjectProvider<AdkReadOnlyDecisionToolBridge> tools) {
        if (!ledger.usesDataSource(dataSource)) throw new IllegalStateException("TIANJI_RUNTIME_DATA_SOURCE_MISMATCH");
        if (chatModels.isEmpty() && factories.isEmpty()) throw new IllegalStateException("TIANJI_RUNTIME_MODEL_REQUIRED");
        if (chatModels.keySet().stream().anyMatch(key -> key == null || key.isBlank())
                || factories.keySet().stream().anyMatch(key -> key == null || key.isBlank())) {
            throw new IllegalStateException("TIANJI_RUNTIME_MODEL_CONFIGURATION_INVALID");
        }
        validateControlExecutor(control);
        boolean readOnlyTools = environment.getProperty("stellar.tianji.agent.tools.read-only-decision.enabled", Boolean.class, false);
        boolean governedProcessToolsEnabled = environment.getProperty("stellar.tianji.agent.tools.governed-process.enabled", Boolean.class, false);
        if (readOnlyTools && governedProcessToolsEnabled) throw new IllegalStateException("TIANJI_TOOL_RUNTIME_MODE_CONFLICT");
        boolean snapshots = environment.getProperty("stellar.tianji.agent.context.snapshots.enabled", Boolean.class, false);
        boolean trimming = environment.getProperty("stellar.tianji.agent.context.trimming.enabled", Boolean.class, false);
        var contextOptions = new AgentContextRuntimeOptions(snapshots, trimming);
        boolean checkpointCapture = environment.getProperty("stellar.tianji.agent.recovery.checkpoint-capture.enabled", Boolean.class, false);
        if (!snapshots && !checkpointCapture) new AgentStorageVerifier(dataSource).verify();
        boolean typedWrites = environment.getProperty("stellar.tianji.agent.runtime.typed-session-writes", Boolean.class, false);
        return AdkAgentRunExecutor.forRuntime(ledger, flowFactory, flowValidator, chatModels, factories, control, estimator,
                dataSource, typedWrites, authorizer, contexts.getIfAvailable(DefaultContextAssembler::new), contextOptions,
                governedProcessToolsEnabled ? governedProcessTools::getObject : readOnlyTools ? tools::getObject : null, checkpointCapture);
    }

    @Bean(destroyMethod = "close")
    AgentRunDispatcher tianjiAgentRunDispatcher(@Qualifier("tianjiAgentDispatchExecutor") ThreadPoolExecutor dispatch,
            @Qualifier("tianjiAgentControlExecutor") ScheduledThreadPoolExecutor control,
            @Qualifier("tianjiAdkAgentRunExecutor") AdkAgentRunExecutor worker, AgentLedgerService ledger,
            @Qualifier("agentRuntimeExecutionService") ObjectProvider<AgentRuntimeExecutionService> runtimeV2) {
        validateDispatchExecutor(dispatch, control);
        return new AgentRunDispatcher(dispatch, worker, ledger, runtimeV2.getIfAvailable());
    }

    @Bean("agentRuntimeExecutionService")
    @ConditionalOnBean(name = {"agentExecutor", "agentExecutionTemplateResolver"})
    AgentRuntimeExecutionService agentRuntimeExecutionService(DataSource dataSource, AgentLedgerService ledger,
            AgentRunAuthorization authorization,
            @Qualifier("agentExecutionTemplateResolver") AdkAgentExecutorAdapter.TemplateResolver hostRegistry,
            @Qualifier("agentExecutionClock") Clock clock, @Qualifier("defaultValidator") Validator validator,
            @Qualifier("agentExecutionTimers") ScheduledExecutorService timers,
            @Qualifier("agentFlowProperties") AgentFlowProperties flowProperties,
            ListableBeanFactory beans,
            @Qualifier("agenticTemplateConverter") AgenticTemplateConverter templateConverter,
            @Qualifier("tianjiHostContextConverter") TianjiHostContextConverter hostConverter,
            @Qualifier("tianjiAgentControlExecutor") ScheduledThreadPoolExecutor control) {
        if (!ledger.usesDataSource(dataSource)) throw new IllegalStateException("TIANJI_RUNTIME_DATA_SOURCE_MISMATCH");
        new AgentStorageVerifier(dataSource).verifyRuntimeStorage();
        String storeRef = flowProperties.session().storeRef();
        SessionStore sessions = beans.getBean("inMemory".equals(storeRef) ? "agentSessionStore" : storeRef, SessionStore.class);
        var adk = new AdkAgentExecutorAdapter(new AgenticPlanCompiler(clock), new SpringAiExecutionModelAdapter(),
                new McpToolAdapter(), AgentRuntimeExecutionService.verifiedTemplates(hostRegistry, templateConverter));
        var executor = new DefaultAgentExecutor(clock, validator,
                new top.egon.cola.component.agentflow.context.DefaultContextAssembler(clock, validator), timers, sessions,
                Map.of(AgentRuntimeExecutionService.ADK_EXECUTOR_REF, adk));
        var receipts = new AgentRuntimeCallReceiptStore(dataSource, new AgentRuntimeBindingStore(dataSource));
        var receiptTransaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        receiptTransaction.setTimeout(5);
        return new AgentRuntimeExecutionService(ledger, authorization, executor, hostConverter, control, receipts, receiptTransaction);
    }

    @Bean("tianjiAgentRuntimeV2AdmissionGuard")
    @ConditionalOnProperty(prefix = "stellar.tianji.agent.runtime-v2", name = "enabled", havingValue = "true")
    SmartInitializingSingleton tianjiAgentRuntimeV2AdmissionGuard(
            @Qualifier("agentRuntimeExecutionService") ObjectProvider<AgentRuntimeExecutionService> runtime) {
        return () -> {
            if (runtime.getIfAvailable() == null) throw new IllegalStateException("TIANJI_AGENT_RUNTIME_V2_NOT_READY");
        };
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
