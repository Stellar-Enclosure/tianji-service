package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.google.adk.agents.LlmAgent;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.agentflow.workflow.LoopAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.ParallelAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.SequentialAgentWorkflowBuilderStrategy;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentFlowImportOptions;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.LegacyAgentFlowImporter;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
    private static final Clock CLOCK = Clock.systemUTC();

    @BeforeAll
    static void setUp() {
        validators = Validation.buildDefaultValidatorFactory();
        validator = new AgentFlowConfigValidator(new ValidationUtils(validators.getValidator()));
        flowFactory = new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of(
                new SequentialAgentWorkflowBuilderStrategy(), new ParallelAgentWorkflowBuilderStrategy(),
                new LoopAgentWorkflowBuilderStrategy())));
        ledger = new AgentLedgerService(PostgresTestDatabase.dataSource(), CLOCK);
    }

    @AfterAll
    static void tearDown() {
        validators.close();
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
    void code_first_uses_only_the_registered_factory_and_the_same_ledger() {
        FixtureModel model = new FixtureModel();
        AtomicInteger created = new AtomicInteger();
        TrustedAgentActor actor = actor();
        ledger.publish(actor, codeSpec("trusted-v1", "code"));
        GetAgentRunQuery query = start(actor, "code", "key");
        var executor = executor(model, Map.of("trusted-v1", () -> {
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
        ledger.publish(actor, configSpec("SEQUENTIAL", policy(Duration.ofSeconds(2)), "timeout"));
        GetAgentRunQuery query = start(actor, "timeout", "key");
        assertEquals(TIMED_OUT, executor(model, Map.of()).executeQueued(actor, query).status());
        assertTrue(subscribed.await(1, TimeUnit.SECONDS));
        assertTrue(cancelled.await(2, TimeUnit.SECONDS));
        assertEquals("TIANJI_RUN_TIMED_OUT", ledger.getResult(actor, query).orElseThrow().errorCode());
    }

    @Test
    void a_run_that_expires_in_the_queue_never_starts_the_model() {
        Clock submitted = Clock.fixed(Instant.parse("2026-10-28T00:00:00Z"), ZoneOffset.UTC);
        AgentLedgerService earlier = new AgentLedgerService(PostgresTestDatabase.dataSource(), submitted);
        FixtureModel model = new FixtureModel();
        TrustedAgentActor actor = actor();
        earlier.publish(actor, singleSpec("expired"));
        AgentRunDTO queued = earlier.start(actor, new StartAgentRunCommand("expired", 1, null, "key",
                null, null, new AgentInputDTO("question", List.of(), Map.of()), null));
        GetAgentRunQuery query = new GetAgentRunQuery(queued.runId());
        var executor = new AdkAgentRunExecutor(earlier, flowFactory, validator, Map.of("fixtureModel", model),
                Map.of(), Clock.offset(submitted, Duration.ofMinutes(1)));
        assertEquals(TIMED_OUT, executor.executeQueued(actor, query).status());
        assertEquals(0, model.streams.get());
        assertEquals(2, earlier.events(actor, new AgentEventsQuery(query.runId(), 0, 10)).size());
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
        model.script = prompt -> Flux.just(FixtureModel.response(report, "stop"));
        TrustedAgentActor actor = actor();
        ledger.publish(actor, singleSpec("large-report"));
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

    private static AdkAgentRunExecutor executor(FixtureModel model,
            Map<String, java.util.function.Supplier<? extends com.google.adk.agents.BaseAgent>> factories) {
        return new AdkAgentRunExecutor(ledger, flowFactory, validator, Map.of("fixtureModel", model), factories, CLOCK);
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
        Map<String, Object> configuration = Map.of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model",
                "rootAgentName", "writer", "agents", List.of(Map.of("name", "writer", "instruction", "write")));
        return new AgentDefinitionSpecDTO(key, 1, "CONFIG", null, "1", "1", dependencies(),
                policy(Duration.ofSeconds(10)), configuration);
    }

    private static AgentDefinitionSpecDTO codeSpec(String codeRef, String key) {
        return new AgentDefinitionSpecDTO(key, 1, "CODE_FIRST", codeRef, "1", "1", dependencies(),
                policy(Duration.ofSeconds(10)), Map.of());
    }

    private static DefinitionDependenciesDTO dependencies() {
        return new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "fixture-route", "policy-v1");
    }

    private static ExecutionPolicyDTO policy(Duration timeout) {
        return new ExecutionPolicyDTO(timeout, Duration.ofSeconds(1), Duration.ofSeconds(1), 8, 0,
                1000, 500, BigDecimal.ONE, "USD", 2, Set.of(), "retry-v1", "approval-v1");
    }

    private static TrustedAgentActor actor() {
        return new TrustedAgentActor(PostgresTestDatabase.nextTenant(), USER, "fixture-user");
    }

    private static GetAgentRunQuery start(TrustedAgentActor actor, String definitionKey, String key) {
        AgentRunDTO run = ledger.start(actor, new StartAgentRunCommand(definitionKey, 1, null, key,
                null, "trace-fixture", new AgentInputDTO("question", List.of(), Map.of()), null));
        return new GetAgentRunQuery(run.runId());
    }

    private static final class FixtureModel implements ChatModel {
        final AtomicInteger streams = new AtomicInteger();
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
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
                    ChatGenerationMetadata.builder().finishReason(finishReason).build())));
        }
    }
}
