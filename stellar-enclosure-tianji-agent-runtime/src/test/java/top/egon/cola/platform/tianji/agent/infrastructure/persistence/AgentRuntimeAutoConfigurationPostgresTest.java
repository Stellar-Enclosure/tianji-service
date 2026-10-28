package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.api.AgentFlowService;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowAutoConfiguration;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.autoconfigure.TianjiAgentRuntimeAutoConfiguration;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AgentRuntimeAutoConfigurationPostgresTest {
    private static DriverManagerDataSource source;
    private static final AtomicInteger CODE_MODEL_CALLS = new AtomicInteger();

    @BeforeAll static void setup() { source = PostgresTestDatabase.dataSource(); }

    @Test void assembles_the_existing_runner_without_starting_a_scheduler() {
        var dispatch = dispatchPool(); var control = controlPool(); var calls = new AtomicInteger();
        var checks = new AtomicInteger();
        AgentRunAuthorization authorizer = (request, boundary) -> { checks.incrementAndGet(); return AgentRunAuthorization.Decision.ALLOW; };
        try {
            runner(dispatch, control, authorizer, estimator(), model(calls)).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(AgentFlowService.class);
                var ledger = context.getBean(AgentLedgerService.class);
                var dispatcher = context.getBean(AgentRunDispatcher.class);
                assertTrue(ledger.usesDataSource(source));
                assertTrue(dispatcher.usesAuthorization(authorizer));
                assertEquals(0, dispatch.getTaskCount());
                assertEquals(0, control.getTaskCount());
                var actor = TrustedAgentActor.verified(PostgresTestDatabase.nextTenant(), TrustedAgentActor.SubjectType.USER,
                        "alice", "https://idp.example/tenant");
                var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                        5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
                ledger.publish(actor, new AgentDefinitionSpecDTO("configured", 1, "CONFIG", null, "1", "1",
                        new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route-v1", "policy-v1"), policy,
                        Map.of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", "writer",
                                "agents", List.of(Map.of("name", "writer", "instruction", "write")))));
                var run = ledger.start(actor, new StartAgentRunCommand("configured", 1, null, "request", null, null,
                        new AgentInputDTO("input", List.of(), Map.of()), null));
                try {
                    var completed = dispatcher.dispatch(actor, new GetAgentRunQuery(run.runId())).toCompletableFuture()
                            .get(20, TimeUnit.SECONDS);
                    assertEquals(AgentRunStatusEnum.COMPLETED, completed.status());
                } catch (Exception failure) { throw new AssertionError(failure); }
                assertEquals(1, calls.get());
                assertTrue(checks.get() >= 2);
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void rejects_missing_authorization_or_estimator() {
        var dispatch = dispatchPool(); var control = controlPool();
        try {
            runner(dispatch, control, null, estimator(), model(new AtomicInteger())).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("AgentRunAuthorization");
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
        dispatch = dispatchPool(); control = controlPool();
        try {
            runner(dispatch, control, allow(), null, model(new AtomicInteger())).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("AgentModelBudgetEstimator");
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void rejects_unsafe_executor_configuration() {
        var control = controlPool();
        var unbounded = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        try {
            runner(unbounded, control, allow(), estimator(), model(new AtomicInteger())).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_RUNTIME_DISPATCH_EXECUTOR_INVALID");
            });
        } finally { unbounded.shutdownNow(); control.shutdownNow(); }
        var callerRuns = dispatchPool(); control = controlPool();
        callerRuns.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            runner(callerRuns, control, allow(), estimator(), model(new AtomicInteger())).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_RUNTIME_DISPATCH_EXECUTOR_INVALID");
            });
        } finally { callerRuns.shutdownNow(); control.shutdownNow(); }
        var dispatch = dispatchPool(); control = controlPool(); control.setRemoveOnCancelPolicy(false);
        try {
            runner(dispatch, control, allow(), estimator(), model(new AtomicInteger())).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_RUNTIME_CONTROL_EXECUTOR_INVALID");
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void rejects_startup_without_a_model_or_trusted_code_factory() {
        var dispatch = dispatchPool(); var control = controlPool();
        try {
            runner(dispatch, control, allow(), estimator(), null).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_RUNTIME_MODEL_REQUIRED");
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void assembles_a_host_owned_code_first_factory_without_a_named_model() {
        var dispatch = dispatchPool(); var control = controlPool();
        CODE_MODEL_CALLS.set(0);
        try {
            runner(dispatch, control, allow(), estimator(), null).withUserConfiguration(CodeFirstConfiguration.class).run(context -> {
                assertThat(context).hasNotFailed();
                var ledger = context.getBean(AgentLedgerService.class);
                var actor = TrustedAgentActor.verified(PostgresTestDatabase.nextTenant(), TrustedAgentActor.SubjectType.USER,
                        "alice", "https://idp.example/tenant");
                var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20), Duration.ofSeconds(10),
                        5, 0, 1000, 500, new BigDecimal("2.00"), "USD", 2, Set.of(), null, null);
                ledger.publish(actor, new AgentDefinitionSpecDTO("code", 1, "CODE_FIRST", "trusted-v1", "1", "1",
                        new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of()));
                var run = ledger.start(actor, new StartAgentRunCommand("code", 1, null, "request", null, null,
                        new AgentInputDTO("input", List.of(), Map.of()), null));
                try {
                    var completed = context.getBean(AgentRunDispatcher.class).dispatch(actor, new GetAgentRunQuery(run.runId()))
                            .toCompletableFuture().get(20, TimeUnit.SECONDS);
                    assertEquals(AgentRunStatusEnum.COMPLETED, completed.status());
                } catch (Exception failure) { throw new AssertionError(failure); }
                assertEquals(1, CODE_MODEL_CALLS.get());
            });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    @Test void rejects_an_existing_ledger_from_another_data_source() {
        var dispatch = dispatchPool(); var control = controlPool();
        var differentSource = new DriverManagerDataSource(source.getUrl());
        try {
            runner(dispatch, control, allow(), estimator(), model(new AtomicInteger()))
                    .withBean(AgentLedgerService.class, () -> new AgentLedgerService(differentSource)).run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasStackTraceContaining("TIANJI_RUNTIME_DATA_SOURCE_MISMATCH");
                    });
        } finally { dispatch.shutdownNow(); control.shutdownNow(); }
    }

    private static ApplicationContextRunner runner(ThreadPoolExecutor dispatch, ScheduledThreadPoolExecutor control,
            AgentRunAuthorization authorization, AgentModelBudgetEstimator estimator, ChatModel model) {
        var runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AgentFlowAutoConfiguration.class, TianjiAgentRuntimeAutoConfiguration.class))
                .withPropertyValues("stellar.tianji.agent.runtime.enabled=true", "egon.cola.component.agent-flow.enabled=true",
                        "egon.cola.component.agent-flow-registry.enabled=false")
                .withBean(DataSource.class, () -> source)
                .withBean("tianjiAgentDispatchExecutor", ThreadPoolExecutor.class, () -> dispatch)
                .withBean("tianjiAgentControlExecutor", ScheduledThreadPoolExecutor.class, () -> control);
        if (model != null) runner = runner.withBean("fixtureModel", ChatModel.class, () -> model);
        if (authorization != null) runner = runner.withBean(AgentRunAuthorization.class, () -> authorization);
        if (estimator != null) runner = runner.withBean(AgentModelBudgetEstimator.class, () -> estimator);
        return runner;
    }

    private static ThreadPoolExecutor dispatchPool() {
        return new ThreadPoolExecutor(1, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(2),
                Thread.ofPlatform().daemon().name("agent-assembly-test-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ScheduledThreadPoolExecutor controlPool() {
        var control = new ScheduledThreadPoolExecutor(2, Thread.ofPlatform().daemon().name("agent-control-test-", 0).factory());
        control.setRemoveOnCancelPolicy(true);
        return control;
    }

    private static AgentRunAuthorization allow() { return (request, boundary) -> AgentRunAuthorization.Decision.ALLOW; }

    private static ChatModel model(AtomicInteger calls) {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().model("fixture-model").build());
        when(model.stream(any(Prompt.class))).thenAnswer(ignored -> Flux.defer(() -> {
            calls.incrementAndGet();
            var generation = new Generation(new AssistantMessage("done"),
                    ChatGenerationMetadata.builder().finishReason("stop").build());
            return Flux.just(new ChatResponse(List.of(generation), ChatResponseMetadata.builder()
                    .usage(new DefaultUsage(10, 5, 15)).build()));
        }));
        return model;
    }

    private static AgentModelBudgetEstimator estimator() {
        return new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot snapshot, com.google.adk.models.LlmRequest request) {
                return new Estimate(20, 30, new BigDecimal("0.10"));
            }
            @Override public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, com.google.adk.models.LlmRequest request) {
                return new ContextWindow(request.model().orElseThrow(), snapshot.definition().dependencies().modelRouteRef(),
                        snapshot.definition().dependencies().policyVersionRef(), 100_000, 10);
            }
            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, com.google.adk.models.LlmResponse response) {
                return new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05"));
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class CodeFirstConfiguration {
        @Bean(name = "tianjiAgentCodeFirstFactories")
        Map<String, java.util.function.Function<io.reactivex.rxjava3.core.Scheduler, ? extends com.google.adk.agents.BaseAgent>> factories() {
            var adapted = new top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory()
                    .create(model(CODE_MODEL_CALLS), "fixture-model");
            return Map.of("trusted-v1", scheduler -> com.google.adk.agents.LlmAgent.builder()
                    .name("writer").model(adapted).instruction("write").build());
        }
    }
}
