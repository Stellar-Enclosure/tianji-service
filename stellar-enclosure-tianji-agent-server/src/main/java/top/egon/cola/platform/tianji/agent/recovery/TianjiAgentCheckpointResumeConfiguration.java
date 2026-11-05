package top.egon.cola.platform.tianji.agent.recovery;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.application.run.AgentCheckpointResumeDispatcher;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunReconciler;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentContextRuntimeOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointResumePrimaryVerifier;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.server.security.TianjiExecutionGrantAuthorizer;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** The trusted host imports this configuration explicitly after provisioning recovery resources. */
@Configuration(proxyBeanMethods = false)
public class TianjiAgentCheckpointResumeConfiguration {
    @Bean(name = "tianjiCheckpointResumeHost", destroyMethod = "close")
    ResumeHost tianjiCheckpointResumeHost(@Qualifier("tianjiCheckpointResumeBinding") ResumeBinding binding,
            DataSource ordinary, AgentLedgerService ordinaryLedger, TianjiExecutionGrantAuthorizer modelAuthority,
            AgentRunReconciler reconciler,
            AgentFlowFactory flowFactory, AgentFlowConfigValidator flowValidator,
            @Qualifier("agentFlowChatModels") Map<String, ChatModel> models,
            @Qualifier("tianjiAgentControlExecutor") ScheduledThreadPoolExecutor control,
            AgentModelBudgetEstimator estimator, Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.runtime.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.execution-authorization.enabled", Boolean.class, false)
                || !environment.getProperty("stellar.tianji.agent.reconciliation.enabled", Boolean.class, false)
                || !ordinaryLedger.usesDataSource(ordinary) || !modelAuthority.usesLedger(ordinaryLedger)
                || !reconciler.usesLedger(ordinaryLedger)
                || control.isShutdown() || models.isEmpty()) {
            throw new IllegalStateException("TIANJI_CHECKPOINT_RESUME_HOST_PREREQUISITES_INVALID");
        }
        if (ordinaryLedger.modelUsageEnabled()
                || environment.getProperty("stellar.tianji.agent.storage.model-usage-enabled", Boolean.class, false)) {
            throw new IllegalStateException("TIANJI_CHECKPOINT_RESUME_MODEL_USAGE_UNAVAILABLE");
        }

        var recovery = binding.recoverySource();
        var authority = binding.resumeAuthority();
        AgentCheckpointResumePrimaryVerifier.verify(ordinary, recovery, binding.recoveryRole());
        var takeover = new AgentCheckpointTakeoverService(recovery, authority);
        var worker = AdkAgentRunExecutor.forRuntime(new AgentLedgerService(recovery), flowFactory, flowValidator,
                models, Map.of(), control, estimator, recovery,
                environment.getProperty("stellar.tianji.agent.runtime.typed-session-writes", Boolean.class, false),
                modelAuthority, new DefaultContextAssembler(), new AgentContextRuntimeOptions(false, false), null, false);
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false).name("tianji-agent-resume-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        return new ResumeHost(new AgentCheckpointResumeDispatcher(executor, worker, takeover, recovery, authority),
                executor, reconciler);
    }

    public record ResumeBinding(DataSource recoverySource, String recoveryRole, ResumeAuthorization resumeAuthority) {
        public ResumeBinding {
            Objects.requireNonNull(recoverySource, "recoverySource");
            Objects.requireNonNull(resumeAuthority, "resumeAuthority");
            if (recoveryRole == null || !recoveryRole.matches("[A-Za-z_][A-Za-z0-9_.-]{0,62}")) {
                throw new IllegalArgumentException("TIANJI_CHECKPOINT_RESUME_ROLE_INVALID");
            }
        }

        @Override public String toString() { return "ResumeBinding[redacted]"; }
    }

    public static final class ResumeHost implements AutoCloseable {
        private static final Duration SHUTDOWN_WAIT = Duration.ofSeconds(5);
        private final AgentCheckpointResumeDispatcher dispatcher;
        private final ThreadPoolExecutor executor;
        private final AgentRunReconciler reconciler;

        private ResumeHost(AgentCheckpointResumeDispatcher dispatcher, ThreadPoolExecutor executor,
                           AgentRunReconciler reconciler) {
            this.dispatcher = dispatcher;
            this.executor = executor;
            this.reconciler = reconciler;
        }

        public CompletionStage<Acknowledgement> dispatch(TrustedAgentActor actor, ResumeCommand command) {
            if (!reconciler.isRunning()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        "Agent checkpoint reconciliation is unavailable");
            }
            return dispatcher.dispatch(actor, command);
        }

        @Override
        public void close() {
            boolean drained = false;
            try {
                dispatcher.close();
                drained = dispatcher.awaitQuiescence(SHUTDOWN_WAIT);
            } finally {
                if (drained) executor.shutdown();
                else executor.shutdownNow();
            }
        }
    }
}
