package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.agents.LlmAgent;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.agentflow.workflow.LoopAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.ParallelAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.SequentialAgentWorkflowBuilderStrategy;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.dtp.executor.virtual.BoundedVirtualThreadExecutor;
import top.egon.cola.platform.tianji.agent.application.run.AgentRunDispatcher;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentFlowImportOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.LegacyAgentFlowImporter;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum.*;

@EnabledIfSystemProperty(named = "tianji.postgres.test.url", matches = ".+")
@Timeout(90)
class AdkAgentRunExecutorPostgresTest {
    private static ValidatorFactory validators;
    private static AgentFlowConfigValidator validator;
    private static AgentFlowFactory flowFactory;
    private static AgentLedgerService ledger;
    private static ScheduledExecutorService controlExecutor;

    @BeforeAll
    static void setUp() {
        validators = Validation.buildDefaultValidatorFactory();
        validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
        flowFactory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of(
                new SequentialAgentWorkflowBuilderStrategy(), new ParallelAgentWorkflowBuilderStrategy(),
                new LoopAgentWorkflowBuilderStrategy())));
        ledger = new AgentLedgerService(PostgresTestDatabase.dataSource());
        controlExecutor = Executors.newScheduledThreadPool(2,
                task -> Thread.ofPlatform().daemon().name("agent-test-control").unstarted(task));
    }

    @AfterAll
    static void tearDown() {
        validators.close();
        controlExecutor.shutdownNow();
    }

    @Test
    void sequential_root_finishes_all_children_before_marking_the_run_complete() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(10)), "review"));
        GetAgentRunQuery query = start(actor, "review", "key");
        AgentRunDTO result = executor(model, Map.of()).executeQueued(actor, query);

        assertEquals(COMPLETED, result.status());
        assertEquals(2, model.streams.get());
        assertEquals(30, model.maxTokens.get());
        var output = ledger.getResult(actor, query).orElseThrow();
        assertEquals("WRITE done", output.output().get("text"));
        assertEquals("UNKNOWN", output.sideEffectOutcome());
        var events = ledger.events(actor, new AgentEventsQuery(query.runId(), 0, 100));
        assertEquals(2, events.stream().filter(event -> event.type().equals("AGENT_OUTPUT")).count());
        assertEquals("RUN_STATUS_CHANGED", events.getLast().type());
        assertEquals("COMPLETED", events.getLast().payload().get("status"));
        assertTrue(events.stream().filter(event -> event.type().equals("AGENT_OUTPUT"))
                .allMatch(event -> event.sequence() < events.getLast().sequence()));
        assertEquals(events.size(), result.lastEventSequence());
    }

    @Test
    void parallel_root_returns_both_outputs_without_inventing_a_single_final_text() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("PARALLEL", policy(Duration.ofSeconds(10)), "parallel"));
        GetAgentRunQuery query = start(actor, "parallel", "key");
        assertEquals(COMPLETED, executor(model, Map.of()).executeQueued(actor, query).status());
        Map<String, Object> output = ledger.getResult(actor, query).orElseThrow().output();
        assertFalse(output.containsKey("text"));
        assertEquals(Set.of("planner", "writer"), ((Map<?, ?>) output.get("agents")).keySet());
    }

    @Test
    void parallel_branches_wait_for_budget_capacity_instead_of_exceeding_it() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        model.script = prompt -> Flux.defer(() -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            started.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    return Flux.error(new IllegalStateException("test model was not released"));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Flux.error(interrupted);
            } finally {
                active.decrementAndGet();
            }
            return FixtureModel.textStream("WRITE done");
        });
        TrustedAgentActor actor = actor();
        Map<String, Object> configuration = Map.of(
                "chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", "root",
                "agents", List.of(Map.of("name", "first", "instruction", "write"),
                        Map.of("name", "second", "instruction", "write"),
                        Map.of("name", "third", "instruction", "write")),
                "workflows", List.of(Map.of("type", "PARALLEL", "name", "root",
                        "subAgentNames", List.of("first", "second", "third"))));
        ledger.publish(actor, new AgentDefinitionSpecDTO("parallel-three", 1, "CONFIG", null, "1", "1",
                dependencies(), policy(Duration.ofSeconds(10)), configuration));
        GetAgentRunQuery query = start(actor, "parallel-three", "key");
        var executor = executor(model, Map.of());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<AgentRunDTO> running = pool.submit(() -> executor.executeQueued(actor, query));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertEquals(2, model.streams.get());
            } finally {
                release.countDown();
            }
            assertEquals(COMPLETED, running.get(10, TimeUnit.SECONDS).status());
        }
        assertEquals(3, model.streams.get());
        assertTrue(maximum.get() <= 2);
    }

    @Test
    void code_first_uses_only_the_registered_factory_and_the_same_ledger() {
        FixtureModel model = new FixtureModel();
        AtomicInteger created = new AtomicInteger();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, codeSpec("trusted-v1", "code"));
        GetAgentRunQuery query = start(actor, "code", "key");
        var executor = executor(model, Map.of("trusted-v1", scheduler -> {
            created.incrementAndGet();
            return LlmAgent.builder().name("trusted").instruction("write")
                    .model(new SpringAiModelAdapterFactory().create(model, "fixture-model")).build();
        }));
        assertEquals(COMPLETED, executor.executeQueued(actor, query).status());
        assertEquals(1, created.get());
        assertEquals("WRITE done", ledger.getResult(actor, query).orElseThrow().output().get("text"));

        TrustedAgentActor other = actor();
        ledger.publish(other, codeSpec("unregistered", "unknown"));
        GetAgentRunQuery unknown = start(other, "unknown", "key");
        assertEquals(FAILED, executor.executeQueued(other, unknown).status());
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", ledger.getResult(other, unknown).orElseThrow().errorCode());
    }

    @Test
    void code_first_parallel_factory_uses_the_provided_context_scheduler() {
        FixtureModel model = new FixtureModel();
        List<String> tenants = new java.util.concurrent.CopyOnWriteArrayList<>();
        model.script = prompt -> Flux.defer(() -> {
            tenants.add(MDC.get("tenantId"));
            return FixtureModel.textStream("WRITE done");
        });
        TrustedAgentActor actor = actor();
        ledger.publish(actor, codeSpec("parallel-v1", "code-parallel"));
        GetAgentRunQuery query = start(actor, "code-parallel", "key");
        var executor = executor(model, Map.of("parallel-v1", scheduler -> {
            var adapter = new SpringAiModelAdapterFactory();
            return com.google.adk.agents.ParallelAgent.builder().name("parallel").scheduler(scheduler)
                    .subAgents(LlmAgent.builder().name("first").instruction("write")
                                    .model(adapter.create(model, "fixture-model")).build(),
                            LlmAgent.builder().name("second").instruction("write")
                                    .model(adapter.create(model, "fixture-model")).build()).build();
        }));
        assertEquals(COMPLETED, executor.executeQueued(actor, query).status());
        assertEquals(List.of(Long.toString(actor.tenantId()), Long.toString(actor.tenantId())), tenants);
    }

    @Test
    void imported_legacy_yaml_can_be_published_and_executed_from_the_persisted_snapshot() {
        String yaml = """
                egon:
                  cola:
                    component:
                      agent-flow:
                        enabled: true
                        flows:
                          legacy-flow:
                            chat-model-bean-name: fixtureModel
                            model-name: ${MODEL_NAME:fixture-model}
                            root-agent-name: writer
                            agents:
                              - name: writer
                                instruction: write
                                output-key: report
                """;
        var options = new AgentFlowImportOptions("legacy-flow", "imported", 1, "1", "1", dependencies(),
                policy(Duration.ofSeconds(10)), Map.of());
        AgentDefinitionSpecDTO imported = new LegacyAgentFlowImporter(validator)
                .importFlow(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)), options);
        TrustedAgentActor actor = actor();
        ledger.publish(actor, imported);
        GetAgentRunQuery query = start(actor, "imported", "key");
        assertEquals(COMPLETED, executor(new FixtureModel(), Map.of()).executeQueued(actor, query).status());
        assertEquals("WRITE done", ledger.getResult(actor, query).orElseThrow().output().get("text"));
    }

    @Test
    void deadline_cancels_the_model_subscription_and_records_timeout() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        model.script = prompt -> Flux.defer(() -> {
            subscribed.countDown();
            return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
        });
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(2), Duration.ofSeconds(10)), "timeout"));
        GetAgentRunQuery query = start(actor, "timeout", "key");
        assertEquals(TIMED_OUT, executor(model, Map.of()).executeQueued(actor, query).status());
        assertTrue(subscribed.await(1, TimeUnit.SECONDS));
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        assertEquals("TIANJI_RUN_TIMED_OUT", ledger.getResult(actor, query).orElseThrow().errorCode());
    }

    @Test
    void model_idle_timeout_stops_the_subscription_before_the_total_deadline() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        model.script = prompt -> Flux.defer(() -> {
            subscribed.countDown();
            return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
        });
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(10), Duration.ofSeconds(1)), "idle"));
        GetAgentRunQuery query = start(actor, "idle", "key");
        assertEquals(TIMED_OUT, executor(model, Map.of()).executeQueued(actor, query).status());
        assertTrue(subscribed.await(1, TimeUnit.SECONDS));
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        assertEquals("TIANJI_MODEL_IDLE_TIMEOUT", ledger.getResult(actor, query).orElseThrow().errorCode());
        assertTrue(ledger.get(actor, query).deadlineAt().isAfter(ledger.databaseNow()));
    }

    @Test
    void model_idle_deadline_is_refreshed_by_each_stream_frame() {
        FixtureModel model = new FixtureModel();
        model.script = prompt -> Flux.interval(Duration.ofMillis(300)).take(5)
                .map(index -> FixtureModel.response(index == 4 ? "done" : "a", index == 4 ? "stop" : ""));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(10), Duration.ofSeconds(1)), "active-stream"));
        GetAgentRunQuery query = start(actor, "active-stream", "key");
        assertEquals(COMPLETED, executor(model, Map.of()).executeQueued(actor, query).status());
    }

    @Test
    void one_active_parallel_branch_does_not_hide_another_branchs_idle_timeout() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch cancelled = new CountDownLatch(2);
        model.script = prompt -> {
            String system = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance)
                    .map(message -> message.getText() == null ? "" : message.getText()).reduce("", String::concat);
            if (system.contains("plan")) return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
            return Flux.interval(Duration.ofMillis(200))
                    .map(index -> FixtureModel.response("tick", ""))
                    .doOnCancel(cancelled::countDown);
        };
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("PARALLEL", policy(Duration.ofSeconds(10), Duration.ofSeconds(1)), "parallel-idle"));
        GetAgentRunQuery query = start(actor, "parallel-idle", "key");
        assertEquals(TIMED_OUT, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals("TIANJI_MODEL_IDLE_TIMEOUT", ledger.getResult(actor, query).orElseThrow().errorCode());
        assertTrue(cancelled.await(3, TimeUnit.SECONDS));
    }

    @Test
    void persisted_cancellation_stops_the_active_model_and_records_a_terminal_result() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        model.script = prompt -> Flux.defer(() -> {
            subscribed.countDown();
            return Flux.<ChatResponse>never().doOnCancel(cancelled::countDown);
        });
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("cancel"));
        GetAgentRunQuery query = start(actor, "cancel", "key");
        var executor = executor(model, Map.of());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<AgentRunDTO> running = pool.submit(() -> executor.executeQueued(actor, query));
            assertTrue(subscribed.await(5, TimeUnit.SECONDS));
            AgentRunDTO active = ledger.get(actor, query);
            assertTrue(ledger.cancel(actor, new CancelAgentRunCommand(query.runId(), "cancel-command",
                    active.revision(), "stop")).accepted());
            assertEquals(CANCELLED, running.get(8, TimeUnit.SECONDS).status());
        }
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        assertEquals("UNKNOWN", ledger.getResult(actor, query).orElseThrow().sideEffectOutcome());
        assertEquals("UNKNOWN", new JdbcTemplate(PostgresTestDatabase.dataSource()).queryForObject("""
                SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id = ? AND run_id = ?
                """, String.class, actor.tenantId(), query.runId()));
    }

    @Test
    void dispatching_an_already_cancelled_run_does_not_start_a_model() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("cancel-before-dispatch"));
        GetAgentRunQuery query = start(actor, "cancel-before-dispatch", "key");
        ledger.cancel(actor, new CancelAgentRunCommand(query.runId(), "cancel", 0, "stop"));
        assertEquals(CANCELLED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals(0, model.streams.get());
    }

    @Test
    void a_run_that_expires_in_the_queue_never_starts_the_model() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("expired"));
        AgentRunDTO queued = ledger.start(actor, new StartAgentRunCommand("expired", 1, null, "key",
                null, null, new AgentInputDTO("question", List.of(), Map.of()), null));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        new JdbcTemplate(PostgresTestDatabase.dataSource()).update("""
                UPDATE tianji_agent.agent_run SET deadline_at = clock_timestamp() - interval '1 second'
                WHERE tenant_id = ? AND run_id = ?
                """, actor.tenantId(), query.runId());
        var executor = executor(model, Map.of());
        assertEquals(TIMED_OUT, executor.executeQueued(actor, query).status());
        assertEquals(0, model.streams.get());
        assertEquals(2, ledger.events(actor, new AgentEventsQuery(query.runId(), 0, 10)).size());
    }

    @Test
    void a_model_with_uncontrolled_callbacks_is_rejected_before_it_runs() {
        FixtureModel model = new FixtureModel();
        model.options = ToolCallingChatOptions.builder().model("fixture-model")
                .toolCallbacks(mock(ToolCallback.class)).build();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(10)), "tool-check"));
        GetAgentRunQuery query = start(actor, "tool-check", "key");
        assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals(0, model.streams.get());
    }

    @Test
    void persistent_session_request_returns_an_explicit_unavailable_result() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(10)), "session"));
        PostgresAdkSessionService sessions = new PostgresAdkSessionService(PostgresTestDatabase.dataSource(), actor, "tianji");
        sessions.createSession("tianji", sessions.adkUserId(), Map.of(), "saved-session").blockingGet();
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand("session", 1, "saved-session", "key",
                null, null, new AgentInputDTO("question", List.of(), Map.of()), null));
        GetAgentRunQuery query = new GetAgentRunQuery(run.runId());
        assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals("TIANJI_FEATURE_NOT_AVAILABLE", ledger.getResult(actor, query).orElseThrow().errorCode());
        assertEquals(0, model.streams.get());
    }

    @Test
    void keeps_a_large_multibyte_final_report_complete() {
        FixtureModel model = new FixtureModel();
        String report = "研".repeat(200_000);
        model.script = prompt -> Flux.just(FixtureModel.response(report, "stop", 200_000));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("large-report", policyWithOutput(300_000)));
        GetAgentRunQuery query = start(actor, "large-report", "key");
        assertEquals(COMPLETED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals(report, ledger.getResult(actor, query).orElseThrow().output().get("text"));
    }

    @Test
    void root_completion_with_only_blank_output_is_not_success() {
        FixtureModel model = new FixtureModel();
        model.script = prompt -> Flux.just(FixtureModel.response("   ", "stop"));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("blank"));
        GetAgentRunQuery query = start(actor, "blank", "key");
        assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", ledger.getResult(actor, query).orElseThrow().errorCode());
    }

    @Test
    void missing_model_usage_fails_closed_and_keeps_the_reservation_unknown() {
        FixtureModel model = new FixtureModel();
        model.script = prompt -> Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("answer"),
                ChatGenerationMetadata.builder().finishReason("stop").build()))));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("unknown-usage"));
        GetAgentRunQuery query = start(actor, "unknown-usage", "key");
        assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals("TIANJI_USAGE_UNKNOWN", ledger.getResult(actor, query).orElseThrow().errorCode());
        assertEquals("UNKNOWN", new JdbcTemplate(PostgresTestDatabase.dataSource()).queryForObject("""
                SELECT state FROM tianji_agent.agent_budget_reservation WHERE tenant_id = ? AND run_id = ?
                """, String.class, actor.tenantId(), query.runId()));
    }

    @Test
    void provider_usage_above_the_reserved_bound_fails_the_run() {
        FixtureModel model = new FixtureModel();
        model.script = prompt -> Flux.just(FixtureModel.response("answer", "stop", 31));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("over-usage"));
        GetAgentRunQuery query = start(actor, "over-usage", "key");
        assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
        assertEquals("TIANJI_BUDGET_EXCEEDED", ledger.getResult(actor, query).orElseThrow().errorCode());
    }

    @Test
    void estimator_failure_does_not_leak_its_message_into_logs_or_result() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("estimator-error"));
        GetAgentRunQuery query = start(actor, "estimator-error", "key");
        AgentModelBudgetEstimator badEstimator = new AgentModelBudgetEstimator() {
            @Override
            public Estimate estimate(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot,
                                     com.google.adk.models.LlmRequest request) {
                throw new IllegalStateException("private-estimator-sentinel");
            }
            @Override
            public AgentBudgetSettlement actualUsage(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot,
                                                      com.google.adk.models.LlmResponse response) {
                return null;
            }
        };
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            var executor = new AdkAgentRunExecutor(ledger, flowFactory, validator, Map.of("fixtureModel", model),
                    Map.of(), controlExecutor, badEstimator);
            assertEquals(FAILED, executor.executeQueued(actor, query).status());
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", ledger.getResult(actor, query).orElseThrow().errorCode());
        } finally {
            root.detachAppender(captured);
            captured.stop();
        }
        String logText = captured.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(event -> event.getFormattedMessage() + (event.getThrowableProxy() == null ? ""
                        : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .reduce("", String::concat);
        assertFalse(logText.contains("private-estimator-sentinel"));
    }

    @Test
    void provider_failure_is_recorded_without_leaking_its_message() {
        FixtureModel model = new FixtureModel();
        model.script = prompt -> Flux.error(new IllegalStateException("private-provider-sentinel"));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("provider-error"));
        GetAgentRunQuery query = start(actor, "provider-error", "key");
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            assertEquals(FAILED, executor(model, Map.of()).executeQueued(actor, query).status());
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", ledger.getResult(actor, query).orElseThrow().errorCode());
        } finally {
            root.detachAppender(captured);
            captured.stop();
        }
        String logText = captured.list.stream().filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .map(event -> event.getFormattedMessage() + (event.getThrowableProxy() == null ? ""
                        : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .reduce("", String::concat);
        assertFalse(logText.contains("private-provider-sentinel"));
    }

    @Test
    void native_model_error_response_is_replaced_before_it_reaches_events_or_logs() {
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, codeSpec("native-error", "native-error-flow"));
        GetAgentRunQuery query = start(actor, "native-error-flow", "key");
        var nativeModel = new com.google.adk.models.BaseLlm("fixture-model") {
            @Override
            public io.reactivex.rxjava3.core.Flowable<com.google.adk.models.LlmResponse> generateContent(
                    com.google.adk.models.LlmRequest request, boolean stream) {
                return io.reactivex.rxjava3.core.Flowable.just(com.google.adk.models.LlmResponse.builder()
                        .errorMessage("private-native-sentinel").build());
            }
            @Override
            public com.google.adk.models.BaseLlmConnection connect(com.google.adk.models.LlmRequest request) {
                throw new UnsupportedOperationException();
            }
        };
        var executor = executor(model, Map.of("native-error", scheduler -> LlmAgent.builder()
                .name("native").instruction("write").model(nativeModel).build()));
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        root.addAppender(captured);
        try {
            assertEquals(FAILED, executor.executeQueued(actor, query).status());
            assertEquals("TIANJI_DEPENDENCY_UNAVAILABLE", ledger.getResult(actor, query).orElseThrow().errorCode());
        } finally {
            root.detachAppender(captured);
            captured.stop();
        }
        String logText = captured.list.stream().map(event -> event.getFormattedMessage()
                        + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy())))
                .reduce("", String::concat);
        assertFalse(logText.contains("private-native-sentinel"));
        assertFalse(ledger.events(actor, new AgentEventsQuery(query.runId(), 0, 100)).toString()
                .contains("private-native-sentinel"));
    }

    @Test
    void duplicate_dispatch_does_not_subscribe_a_second_time() throws Exception {
        FixtureModel model = new FixtureModel();
        CountDownLatch subscribed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        model.script = prompt -> Flux.defer(() -> {
            subscribed.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    return Flux.error(new IllegalStateException("test model was not released"));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Flux.error(interrupted);
            }
            return FixtureModel.textStream("ONE done");
        });
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("single"));
        GetAgentRunQuery query = start(actor, "single", "key");
        var executor = executor(model, Map.of());
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> executor.executeQueued(actor, query));
            try {
                assertTrue(subscribed.await(5, TimeUnit.SECONDS));
                assertThrows(AgentLedgerException.class, () -> executor.executeQueued(actor, query));
            } finally {
                release.countDown();
            }
            assertEquals(COMPLETED, first.get(15, TimeUnit.SECONDS).status());
        }
        assertEquals(1, model.streams.get());
    }

    @Test
    void bounded_dispatch_installs_the_verified_tenant_and_clears_untrusted_user_context() throws Exception {
        FixtureModel model = new FixtureModel();
        Map<String, String> observedTenants = new ConcurrentHashMap<>();
        Map<String, String> observedUsers = new ConcurrentHashMap<>();
        model.script = prompt -> Flux.defer(() -> {
            String input = prompt.getInstructions().stream().map(message -> message.getText() == null ? "" : message.getText())
                    .filter(text -> text.startsWith("question-")).findFirst().orElseThrow();
            observedTenants.put(input, java.util.Objects.toString(MDC.get("tenantId"), "missing"));
            observedUsers.put(input, java.util.Objects.toString(MDC.get("userId"), "missing"));
            return FixtureModel.textStream("WRITE done");
        });
        TrustedAgentActor user = actor();
        TrustedAgentActor service = new TrustedAgentActor(PostgresTestDatabase.nextTenant(),
                TrustedAgentActor.SubjectType.SERVICE, "service-client");
        ledger.publish(user, singleSpec("user-flow"));
        ledger.publish(service, singleSpec("service-flow"));
        GetAgentRunQuery userRun = startWithText(user, "user-flow", "question-user");
        GetAgentRunQuery serviceRun = startWithText(service, "service-flow", "question-service");
        var worker = executor(model, Map.of());
        try (BoundedVirtualThreadExecutor pool = new BoundedVirtualThreadExecutor("agent-context-test", 2);
             AgentRunDispatcher dispatcher = new AgentRunDispatcher(pool, worker, ledger)) {
            MDC.put("tenantId", "untrusted-tenant");
            MDC.put("userId", "untrusted-user");
            var first = dispatcher.dispatch(user, userRun).toCompletableFuture();
            var second = dispatcher.dispatch(service, serviceRun).toCompletableFuture();
            assertEquals(COMPLETED, first.get(10, TimeUnit.SECONDS).status());
            assertEquals(COMPLETED, second.get(10, TimeUnit.SECONDS).status());
            assertEquals(Long.toString(user.tenantId()), observedTenants.get("question-user"));
            assertEquals(Long.toString(service.tenantId()), observedTenants.get("question-service"));
            assertEquals(user.subjectId(), observedUsers.get("question-user"));
            assertEquals("missing", observedUsers.get("question-service"));
            assertEquals("untrusted-tenant", MDC.get("tenantId"));
            assertEquals("untrusted-user", MDC.get("userId"));
        } finally {
            MDC.clear();
        }
    }

    private static AdkAgentRunExecutor executor(FixtureModel model,
            Map<String, java.util.function.Function<io.reactivex.rxjava3.core.Scheduler,
                    ? extends com.google.adk.agents.BaseAgent>> factories) {
        return new AdkAgentRunExecutor(ledger, flowFactory, validator, Map.of("fixtureModel", model), factories,
                controlExecutor, estimator());
    }

    private static AgentDefinitionSpecDTO configSpec(String workflow, ExecutionPolicyDTO policy, String key) {
        Map<String, Object> configuration = Map.of(
                "chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", "root",
                "agents", List.of(Map.of("name", "planner", "instruction", "plan", "outputKey", "plan"),
                        Map.of("name", "writer", "instruction", "write", "outputKey", "report")),
                "workflows", List.of(Map.of("type", workflow, "name", "root",
                        "subAgentNames", List.of("planner", "writer"))));
        return new AgentDefinitionSpecDTO(key, 1, "CONFIG", null, "1", "1", dependencies(), policy, configuration);
    }

    private static AgentDefinitionSpecDTO singleSpec(String key) {
        return singleSpec(key, policy(Duration.ofSeconds(10)));
    }

    private static AgentDefinitionSpecDTO singleSpec(String key, ExecutionPolicyDTO policy) {
        Map<String, Object> configuration = Map.of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model",
                "rootAgentName", "writer", "agents", List.of(Map.of("name", "writer", "instruction", "write")));
        return new AgentDefinitionSpecDTO(key, 1, "CONFIG", null, "1", "1", dependencies(), policy, configuration);
    }

    private static AgentDefinitionSpecDTO codeSpec(String codeRef, String key) {
        return new AgentDefinitionSpecDTO(key, 1, "CODE_FIRST", codeRef, "1", "1", dependencies(),
                policy(Duration.ofSeconds(10)), Map.of());
    }

    private static DefinitionDependenciesDTO dependencies() {
        return new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "fixture-route", "policy-v1");
    }

    private static ExecutionPolicyDTO policy(Duration timeout) {
        return policy(timeout, Duration.ofSeconds(1));
    }

    private static ExecutionPolicyDTO policy(Duration timeout, Duration idle) {
        return new ExecutionPolicyDTO(timeout, idle, Duration.ofSeconds(1), 8, 0,
                1000, 500, BigDecimal.ONE, "USD", 2, Set.of(), "retry-v1", "approval-v1");
    }

    private static ExecutionPolicyDTO policyWithOutput(long maxOutputTokens) {
        return new ExecutionPolicyDTO(Duration.ofSeconds(10), Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 0,
                1000, maxOutputTokens, BigDecimal.ONE, "USD", 2, Set.of(), "retry-v1", "approval-v1");
    }

    private static AgentModelBudgetEstimator estimator() {
        return new AgentModelBudgetEstimator() {
            @Override
            public Estimate estimate(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot,
                                     com.google.adk.models.LlmRequest request) {
                return new Estimate(20, snapshot.definition().definitionKey().equals("large-report") ? 200_000 : 30,
                        new BigDecimal("0.10"));
            }

            @Override
            public AgentBudgetSettlement actualUsage(top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot snapshot,
                                                      com.google.adk.models.LlmResponse response) {
                return response.usageMetadata().map(usage -> new AgentBudgetSettlement(
                        usage.promptTokenCount().map(Integer::longValue).orElse(null),
                        usage.candidatesTokenCount().map(Integer::longValue).orElse(null), new BigDecimal("0.05")))
                        .orElseGet(() -> new AgentBudgetSettlement(null, null, null));
            }
        };
    }

    private static TrustedAgentActor actor() {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, "fixture-user");
    }

    private static GetAgentRunQuery start(TrustedAgentActor actor, String definitionKey, String key) {
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand(definitionKey, 1, null, key,
                null, "trace-fixture", new AgentInputDTO("question", List.of(), Map.of()), null));
        return new GetAgentRunQuery(run.runId());
    }

    private static GetAgentRunQuery startWithText(TrustedAgentActor actor, String definitionKey, String text) {
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand(definitionKey, 1, null, "key",
                null, "trace-fixture", new AgentInputDTO(text, List.of(), Map.of()), null));
        return new GetAgentRunQuery(run.runId());
    }

    private static final class FixtureModel implements ChatModel {
        final AtomicInteger streams = new AtomicInteger();
        final AtomicReference<Integer> maxTokens = new AtomicReference<>();
        ChatOptions options = ToolCallingChatOptions.builder().model("fixture-model").build();
        Function<Prompt, Flux<ChatResponse>> script = prompt -> {
            String instructions = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance)
                    .map(message -> message.getText() == null ? "" : message.getText())
                    .reduce("", (a, b) -> a + " " + b).toLowerCase();
            return textStream(instructions.contains("plan") ? "PLAN done" : "WRITE done");
        };

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new AssertionError("streaming execution must not call the synchronous model");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                streams.incrementAndGet();
                maxTokens.set(prompt.getOptions().getMaxTokens());
                return script.apply(prompt);
            });
        }

        @Override
        public ChatOptions getOptions() {
            return options;
        }

        static Flux<ChatResponse> textStream(String value) {
            int split = value.indexOf(' ') + 1;
            return Flux.just(response(value.substring(0, split), ""), response(value.substring(split), "stop"));
        }

        private static ChatResponse response(String text, String finishReason) {
            return response(text, finishReason, 5);
        }

        private static ChatResponse response(String text, String finishReason, int outputTokens) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason(finishReason).build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(10, outputTokens, 10 + outputTokens)).build());
        }
    }
}
