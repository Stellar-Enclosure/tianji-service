package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
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
import reactor.core.publisher.Flux;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.AgentFlowInMemoryRunner;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.component.agentflow.workflow.AgentWorkflowStrategyFactory;
import top.egon.cola.component.agentflow.workflow.LoopAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.ParallelAgentWorkflowBuilderStrategy;
import top.egon.cola.component.agentflow.workflow.SequentialAgentWorkflowBuilderStrategy;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the governed bridge through the actual ADK runner and Spring AI adapter without external services. */
@Timeout(15)
class AdkReadOnlyDecisionLoopTest {
    private static final Instant NOW = Instant.now();
    private static final ToolDescriptor TOOL = new ToolDescriptor("fixed.decision", 1, "fixed_decision", "Evaluate the published fixed decision.",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("value", ToolSchema.ValueType.STRING), Set.of("value")),
            new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "decision.evaluate", "process.decision", "a".repeat(64), null));

    @Test
    void native_nonstreaming_runner_uses_the_same_scope_plugin_and_typed_tool_roundtrip() {
        try (var rig = new Rig("SINGLE", true)) {
            var material = rig.material();
            try (var scope = rig.bridge.bind(material, rig.lease, event -> { })) {
                var plugin = new AdkRunBudgetPlugin(rig.ledger, rig.actor, rig.lease, material.snapshot(), rig.estimator,
                        new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, material.authorization()),
                        io.reactivex.rxjava3.schedulers.Schedulers.from(rig.control), rig.control, new DefaultContextAssembler(), material.authorization(),
                        new AgentContextRuntimeOptions(false, false), scope);
                var root = LlmAgent.builder().name("writer").instruction("writer")
                        .model(new SpringAiModelAdapterFactory().create(rig.model, "fixture-model")).tools(scope.declaredTools()).build();
                var runner = new AgentFlowInMemoryRunner(root, "tianji_" + rig.query.runId().replace('-', '_'), List.of(plugin));
                try {
                    String user = "20:USER:alice";
                    var session = runner.sessionService().createSession(runner.appName(), user).blockingGet();
                    var config = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.NONE).setMaxLlmCalls(8).setAutoCreateSession(false).build();
                    var events = runner.runAsync(user, session.id(), Content.fromParts(Part.fromText("Use the fixed decision")), config)
                            .toList().timeout(5, TimeUnit.SECONDS).blockingGet();
                    assertEquals(2, rig.model.calls.get()); assertEquals(0, rig.model.streams.get()); assertEquals(1, rig.records.get());
                    assertTrue(events.stream().anyMatch(event -> !event.functionCalls().isEmpty()));
                    assertTrue(events.stream().anyMatch(event -> !event.functionResponses().isEmpty()));
                    assertTrue(rig.model.prompts.getLast().getInstructions().stream().anyMatch(ToolResponseMessage.class::isInstance));
                    assertEquals(2, rig.model.prompts.size());
                } finally { runner.close().blockingAwait(); }
            }
        }
    }

    @Test
    void native_config_loop_records_once_and_feeds_a_typed_response_into_the_second_model_call() {
        try (var rig = new Rig("SINGLE", true)) {
            var result = rig.executor().executeQueued(rig.actor, rig.query);
            assertEquals(AgentRunStatusEnum.COMPLETED, result.status());
            assertEquals(2, rig.model.streams.get()); assertEquals(1, rig.records.get());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome());
            var request = rig.preparations.getFirst();
            assertEquals(Map.of("amount", 7L), request.arguments());
            assertEquals(TOOL, request.descriptor()); assertEquals(BigDecimal.ZERO, request.estimatedCost());
            assertNotEquals("private-native-call-writer", request.invocationId().toString());
            ToolResponseMessage response = (ToolResponseMessage) rig.model.prompts.getLast().getInstructions().stream()
                    .filter(ToolResponseMessage.class::isInstance).findFirst().orElseThrow();
            assertEquals("private-native-call-writer", response.getResponses().getFirst().id());
            assertTrue(response.getResponses().getFirst().responseData().contains("SUCCEEDED"));
            assertTrue(response.getResponses().getFirst().responseData().contains("private-tool-output"));
            assertEquals(List.of("TOOL_STARTED", "TOOL_FINISHED"), rig.events.stream()
                    .map(EventWrite::type).filter(type -> type.startsWith("TOOL_")).toList());
            var toolEvents = rig.events.stream().filter(event -> event.type().startsWith("TOOL_")).toList();
            for (var event : toolEvents) {
                assertEquals("writer", event.author());
                assertEquals(request.invocationId().toString(), event.payload().get("hostInvocationId"));
                assertEquals(TOOL.reference().contentSha256(), ((Map<?, ?>) event.payload().get("toolRef")).get("contentSha256"));
                String serialized = event.payload().toString();
                for (String privateValue : List.of("amount", "private-tool-output", "private-native-call", "nonce", "bearer", "receipt")) {
                    assertFalse(serialized.contains(privateValue), serialized);
                }
            }
            verify(rig.budgets, times(2)).reserveBudget(eq(rig.actor), eq(rig.lease), any());
            verify(rig.budgets, times(2)).settleBudget(eq(rig.actor), eq(rig.lease), anyString(), any());
            verifyNoInteractions(rig.transport);
        }
    }

    @Test
    void sequential_and_parallel_leaves_admit_distinct_contexts_and_serialize_text_and_tool_revisions() {
        for (String mode : List.of("SEQUENTIAL", "PARALLEL")) {
            try (var rig = new Rig(mode, true)) {
                AgentRunDTO result = rig.executor().executeQueued(rig.actor, rig.query);
                assertEquals(AgentRunStatusEnum.COMPLETED, result.status(), mode);
                assertEquals(4, rig.model.streams.get(), mode); assertEquals(2, rig.records.get(), mode);
                assertEquals(2, rig.preparations.stream().map(ToolInvocationStore.PreparationRequestVO::invocationId).distinct().count());
                assertEquals(2, rig.events.stream().filter(event -> event.type().equals("TOOL_FINISHED")).count());
                assertEquals(rig.events.size() + 2, result.revision());
                for (int index = 0; index < rig.events.size(); index++) assertEquals(index + 1, rig.events.get(index).expectedRevision());
                assertEquals("NONE", rig.finished.get().sideEffectOutcome());
            }
        }
    }

    @Test
    void legacy_model_execution_keeps_unknown_and_does_not_resolve_the_optional_bridge() {
        try (var rig = new Rig("SINGLE", false)) {
            var resolved = new AtomicInteger();
            var executor = rig.executor(() -> { resolved.incrementAndGet(); return rig.bridge; });
            assertEquals(0, resolved.get());
            assertEquals(AgentRunStatusEnum.COMPLETED, executor.executeQueued(rig.actor, rig.query).status());
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome()); assertEquals(0, resolved.get());
            assertTrue(executor.usesReadOnlyDecisionTools(rig.bridge)); assertTrue(executor.usesReadOnlyDecisionTools(rig.bridge));
            assertEquals(1, resolved.get());
            assertFalse(rig.legacyExecutor().usesReadOnlyDecisionTools(rig.bridge));
        }
    }

    @Test
    void readonly_scope_retains_none_for_model_failure_and_cancellation() {
        try (var rig = new Rig("SINGLE", true)) {
            rig.model.failure = true;
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertEquals(0, rig.records.get());
        }
        try (var rig = new Rig("SINGLE", true)) {
            rig.model.never = true;
            when(rig.ledger.heartbeat(eq(rig.actor), eq(rig.lease), any())).thenReturn(LeaseHeartbeat.CANCEL_REQUESTED);
            assertEquals(AgentRunStatusEnum.CANCELLED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertEquals(0, rig.records.get());
        }
    }

    @Test
    void a_tool_end_event_failure_suppresses_model_output_after_the_durable_record_without_redispatch() {
        try (var rig = new Rig("SINGLE", true)) {
            rig.failedEventType = "TOOL_FINISHED";
            AgentRunDTO result = rig.executor().executeQueued(rig.actor, rig.query);
            assertEquals(AgentRunStatusEnum.FAILED, result.status());
            assertEquals(1, rig.records.get()); assertEquals(1, rig.model.streams.get());
            assertTrue(rig.finished.get().output().isEmpty()); assertEquals("NONE", rig.finished.get().sideEffectOutcome());
            assertEquals(1, rig.events.stream().filter(event -> event.type().equals("TOOL_STARTED")).count());
            verify(rig.recorder, times(1)).executeReadOnly(eq(rig.transport), any());
        }
    }

    @Test
    void cancellation_and_parallel_failure_seal_run_events_before_a_late_committed_tool_finishes() {
        assertAll(List.of(true, false).stream().map(cancellation -> (org.junit.jupiter.api.function.Executable) () -> {
            try (var rig = new Rig(cancellation ? "SINGLE" : "PARALLEL", true)) {
                var recordEntered = new CountDownLatch(1); var releaseRecord = new CountDownLatch(1);
                var lateEnd = new CountDownLatch(1); var factCommitted = new AtomicBoolean();
                var original = rig.bridge; var bridge = spy(original);
                doAnswer(call -> {
                    AdkReadOnlyDecisionToolBridge.ToolEventSink sink = call.getArgument(2);
                    return original.bind(call.getArgument(0), call.getArgument(1), event -> {
                        try { sink.append(event); }
                        finally { if (event.phase() == AdkReadOnlyDecisionToolBridge.Phase.FINISHED) lateEnd.countDown(); }
                    });
                }).when(bridge).bind(any(), any(), any());
                doAnswer(call -> {
                    ToolInvocationStore.PreparationRequestVO request = call.getArgument(1);
                    rig.preparations.add(request); rig.records.incrementAndGet(); recordEntered.countDown();
                    awaitIgnoringInterrupt(releaseRecord); factCommitted.set(true); return rig.recorded(request);
                }).when(rig.recorder).executeReadOnly(eq(rig.transport), any());
                if (cancellation) when(rig.ledger.heartbeat(eq(rig.actor), eq(rig.lease), any())).thenAnswer(call ->
                        recordEntered.getCount() == 0 ? LeaseHeartbeat.CANCEL_REQUESTED : LeaseHeartbeat.ACTIVE);
                else rig.model.failReviewerAfter = recordEntered;
                doAnswer(call -> {
                    long revision = call.getArgument(2); AgentExecutionResult result = call.getArgument(3);
                    releaseRecord.countDown();
                    assertTrue(lateEnd.await(5, TimeUnit.SECONDS), "disposed native tool must be allowed to finish its committed fact");
                    if (rig.current.get().revision() != revision) throw new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "late tool event changed the terminal revision");
                    rig.finished.set(result); rig.current.set(rig.run(result.status(), revision + 1, rig.current.get().lastEventSequence()));
                    return rig.current.get();
                }).when(rig.ledger).finish(eq(rig.actor), eq(rig.lease), anyLong(), any());
                try {
                    AgentRunDTO result = rig.executor(() -> bridge).executeQueued(rig.actor, rig.query);
                    assertEquals(cancellation ? AgentRunStatusEnum.CANCELLED : AgentRunStatusEnum.FAILED, result.status());
                    assertTrue(factCommitted.get()); assertEquals(1, rig.records.get());
                    assertEquals(0, rig.events.stream().filter(event -> event.type().equals("TOOL_FINISHED")).count());
                    assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertTrue(rig.finished.get().output().isEmpty());
                    verify(rig.recorder, times(1)).executeReadOnly(eq(rig.transport), any());
                } finally { releaseRecord.countDown(); }
            }
        }));
    }

    private static void awaitIgnoringInterrupt(CountDownLatch gate) {
        boolean interrupted = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime(); assertTrue(remaining > 0, "controlled late recorder was never released");
                try { assertTrue(gate.await(remaining, TimeUnit.NANOSECONDS)); return; }
                catch (InterruptedException disposed) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    @Test
    void every_canonical_model_and_tool_callback_is_rejected_before_a_model_call() {
        List<Function<LlmAgent.Builder, LlmAgent.Builder>> callbacks = List.of(
                builder -> builder.beforeModelCallback((context, request) -> Maybe.empty()),
                builder -> builder.afterModelCallback((context, response) -> Maybe.empty()),
                builder -> builder.onModelErrorCallback((context, request, failure) -> Maybe.empty()),
                builder -> builder.beforeToolCallback((invocation, tool, arguments, context) -> Maybe.empty()),
                builder -> builder.afterToolCallback((invocation, tool, arguments, context, output) -> Maybe.empty()),
                builder -> builder.onToolErrorCallback((invocation, tool, arguments, context, failure) -> Maybe.empty()));
        for (var callback : callbacks) {
            try (var rig = new Rig("SINGLE", false)) {
                rig.compiledRoot = callback.apply(LlmAgent.builder().name("writer")
                        .model(new SpringAiModelAdapterFactory().create(rig.model, "fixture-model")).instruction("writer")).build();
                AgentRunDTO result = rig.executor().executeQueued(rig.actor, rig.query);
                assertEquals(AgentRunStatusEnum.FAILED, result.status());
                assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE.name(), rig.finished.get().errorCode());
                assertEquals(0, rig.model.streams.get());
            }
        }
    }

    @Test
    void foreign_same_name_tools_toolsets_and_code_executors_are_rejected() {
        for (String hidden : List.of("FOREIGN", "TOOLSET", "CODE_EXECUTOR")) {
            try (var rig = new Rig("SINGLE", true)) {
                var llm = mock(LlmAgent.class); when(llm.tools()).thenReturn(io.reactivex.rxjava3.core.Single.just(List.of()));
                when(llm.toolsets()).thenReturn(List.of()); when(llm.subAgents()).thenReturn(List.of());
                when(llm.codeExecutor()).thenReturn(Optional.empty());
                if (hidden.equals("FOREIGN")) {
                    var foreign = mock(BaseTool.class); when(foreign.name()).thenReturn(TOOL.name());
                    when(llm.tools()).thenReturn(io.reactivex.rxjava3.core.Single.just(List.of(foreign)));
                } else if (hidden.equals("TOOLSET")) {
                    var toolset = mock(BaseToolset.class); when(llm.toolsets()).thenReturn(List.of(toolset));
                } else {
                    var executor = mock(com.google.adk.codeexecutors.BaseCodeExecutor.class); when(llm.codeExecutor()).thenReturn(Optional.of(executor));
                }
                rig.compiledRoot = llm;
                assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
                assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE.name(), rig.finished.get().errorCode(), hidden);
                assertEquals(0, rig.model.streams.get()); assertEquals(0, rig.records.get());
            }
        }
    }

    @Test
    void code_first_tools_and_model_default_callbacks_remain_closed() {
        try (var rig = new Rig("SINGLE", true)) {
            rig.mode = "CODE_FIRST";
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE.name(), rig.finished.get().errorCode());
            assertEquals(0, rig.records.get());
        }
        try (var rig = new Rig("SINGLE", true)) {
            rig.model.options = ToolCallingChatOptions.builder().model("fixture-model").toolCallbacks(mock(ToolCallback.class)).build();
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE.name(), rig.finished.get().errorCode());
            assertEquals(0, rig.model.streams.get()); assertEquals(0, rig.records.get());
        }
    }

    private record EventWrite(long expectedRevision, String type, String author, Map<String, Object> payload) { }

    private static final class Rig implements AutoCloseable {
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
        final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
        final ReadOnlyDecisionToolTransport transport = mock(ReadOnlyDecisionToolTransport.class);
        final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        final GetAgentRunQuery query = new GetAgentRunQuery(UUID.randomUUID().toString());
        final AgentRunLease lease = new AgentRunLease(query.runId(), "worker", 1, NOW.plusSeconds(60));
        final ScheduledExecutorService control = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().unstarted(task));
        final List<EventWrite> events = new CopyOnWriteArrayList<>();
        final List<ToolInvocationStore.PreparationRequestVO> preparations = new CopyOnWriteArrayList<>();
        final AtomicReference<AgentExecutionResult> finished = new AtomicReference<>();
        final AtomicReference<AgentRunDTO> current = new AtomicReference<>();
        final AtomicInteger records = new AtomicInteger();
        final FakeChatModel model = new FakeChatModel();
        final AdkReadOnlyDecisionToolBridge bridge;
        final String workflow; final boolean tools;
        volatile String failedEventType; String mode = "CONFIG"; BaseAgent compiledRoot;

        Rig(String workflow, boolean tools) {
            this.workflow = workflow; this.tools = tools; model.tools = tools;
            current.set(run(AgentRunStatusEnum.QUEUED, 0, 0));
            when(ledger.get(eq(actor), eq(query))).thenAnswer(call -> current.get());
            when(ledger.databaseNow()).thenReturn(NOW);
            when(ledger.executionMaterial(eq(actor), eq(query))).thenAnswer(call -> material());
            when(ledger.claim(eq(actor), eq(query), eq(0L), anyString(), any())).thenAnswer(call -> {
                current.set(run(AgentRunStatusEnum.RUNNING, 1, 0)); return new ClaimedAgentRun(current.get(), lease);
            });
            when(ledger.heartbeat(eq(actor), eq(lease), any())).thenReturn(LeaseHeartbeat.ACTIVE);
            when(ledger.budgets()).thenReturn(budgets);
            when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> {
                AgentBudgetRequest request = call.getArgument(2);
                return new AgentBudgetReservation(request.reservationId(), 1, request.kind(), null, request.inputTokens(), request.outputTokens(),
                        request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
            });
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any())).thenAnswer(call -> {
                AgentBudgetSettlement actual = call.getArgument(3);
                return new AgentBudgetReservation(call.getArgument(2), 1, AgentBudgetRequest.Kind.MODEL, null, 10, 5, BigDecimal.ZERO,
                        AgentBudgetReservation.State.SETTLED, actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false);
            });
            when(estimator.estimate(any(), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 5, BigDecimal.ZERO));
            when(estimator.contextWindow(any(), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("fixture-model", "route-v1", "policy-v1", 1000, 5));
            when(estimator.actualUsage(any(), any())).thenReturn(new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO));
            when(ledger.appendEvent(eq(actor), eq(lease), anyLong(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
                long revision = call.getArgument(2); String type = call.getArgument(3);
                AgentRunDTO before = current.get(); Thread.yield();
                if (before.revision() != revision) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "controlled revision conflict");
                if (type.equals(failedEventType)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "controlled event failure");
                events.add(new EventWrite(revision, type, call.getArgument(4), Map.copyOf(call.getArgument(5))));
                current.set(run(AgentRunStatusEnum.RUNNING, revision + 1, before.lastEventSequence() + 1)); return current.get();
            });
            when(ledger.finish(eq(actor), eq(lease), anyLong(), any())).thenAnswer(call -> {
                assertEquals(current.get().revision(), (long) call.getArgument(2));
                AgentExecutionResult result = call.getArgument(3); finished.set(result);
                current.set(run(result.status(), current.get().revision() + 1, current.get().lastEventSequence())); return current.get();
            });
            when(recorder.executeReadOnly(eq(transport), any())).thenAnswer(call -> {
                assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                ToolInvocationStore.PreparationRequestVO request = call.getArgument(1); preparations.add(request); records.incrementAndGet();
                return recorded(request);
            });
            bridge = new AdkReadOnlyDecisionToolBridge(recorder, transport, List.of(TOOL), material -> tools
                    ? List.of(new ToolExecutionGrant.BindingVO(material.authorization(), TOOL.reference(), TOOL.policy().bindingSha256(),
                    "TIANJI_TOOL_FIXED_DECISION", URI.create("https://process.example/resource"), TOOL.policy().executionResourceKey(), null)) : List.of());
        }

        AgentLedgerService.ExecutionMaterial material() {
            var policy = new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(5), 8, tools ? 2 : 0,
                    10000, 1000, BigDecimal.TEN, "USD", 2, tools ? Set.of(TOOL.versionKey()) : Set.of(), "retry-v1", "approval-v1");
            var agents = workflow.equals("SINGLE") ? List.of(Map.of("name", "writer", "instruction", "writer"))
                    : List.of(Map.of("name", "writer", "instruction", "writer"), Map.of("name", "reviewer", "instruction", "reviewer"));
            var config = Map.<String, Object>of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model",
                    "rootAgentName", workflow.equals("SINGLE") ? "writer" : "root", "agents", agents, "workflows", workflow.equals("SINGLE")
                    ? List.of() : List.of(Map.of("name", "root", "type", workflow, "subAgentNames", List.of("writer", "reviewer"))));
            var definition = new AgentDefinitionSpecDTO("assistant", 1, mode, mode.equals("CODE_FIRST") ? "trusted" : null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), tools ? List.of(TOOL.reference()) : List.of(), "route-v1", "policy-v1"), policy, config);
            var snapshot = new AgentExecutionSnapshot(definition, "b".repeat(64), policy, new AgentInputDTO("Use the fixed decision", null, null), null);
            var authorization = new AgentRunAuthorization.Request(actor, query.runId(), "assistant", 1, "b".repeat(64), "c".repeat(64),
                    "d".repeat(64), "policy-v1", false, NOW.plusSeconds(60));
            return new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED, 0, 0), snapshot, authorization);
        }

        AdkAgentRunExecutor executor() { return executor(() -> bridge); }
        AdkAgentRunExecutor legacyExecutor() {
            return new AdkAgentRunExecutor(ledger, factory(), mock(AgentFlowConfigValidator.class), Map.of("fixtureModel", model), Map.of(), control,
                    estimator, null, false, (request, boundary) -> AgentRunAuthorization.Decision.ALLOW);
        }
        AdkAgentRunExecutor executor(Supplier<AdkReadOnlyDecisionToolBridge> supplier) {
            var actual = factory(); AgentFlowFactory factory = actual;
            if (compiledRoot != null) {
                factory = mock(AgentFlowFactory.class);
                when(factory.compileRoot(anyString(), any(), anyMap(), anyList())).thenReturn(compiledRoot);
            }
            return new AdkAgentRunExecutor(ledger, factory, mock(AgentFlowConfigValidator.class), Map.of("fixtureModel", model),
                    Map.of("trusted", scheduler -> LlmAgent.builder().name("writer").instruction("writer")
                            .model(new SpringAiModelAdapterFactory().create(model, "fixture-model")).tools(mock(BaseTool.class)).build()),
                    control, estimator, null, false, (request, boundary) -> AgentRunAuthorization.Decision.ALLOW,
                    new DefaultContextAssembler(), new AgentContextRuntimeOptions(false, false), supplier);
        }
        private static AgentFlowFactory factory() {
            return new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of(
                    new SequentialAgentWorkflowBuilderStrategy(), new ParallelAgentWorkflowBuilderStrategy(), new LoopAgentWorkflowBuilderStrategy())));
        }
        private ToolInvocationRecorder.RecordResult recorded(ToolInvocationStore.PreparationRequestVO request) {
            return new ToolInvocationRecorder.RecordResult(query.runId(), request.invocationId(), "SUCCEEDED", 2, 1, NOW, NOW,
                    new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", "private-tool-output"), null, null, BigDecimal.ZERO),
                    true, ToolInvocationRecorder.AccountingState.SETTLED);
        }
        private AgentRunDTO run(AgentRunStatusEnum status, long revision, long events) {
            return new AgentRunDTO(query.runId(), "assistant", 1, null, status, null, false, status == AgentRunStatusEnum.QUEUED ? 0 : 1,
                    revision, events, NOW.plusSeconds(60), NOW, AgentRunTransitions.terminal(status) ? NOW : null);
        }
        @Override public void close() { control.shutdownNow(); }
    }

    private static final class FakeChatModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger(); final AtomicInteger streams = new AtomicInteger(); final List<Prompt> prompts = new CopyOnWriteArrayList<>();
        final Map<String, AtomicInteger> rounds = new ConcurrentHashMap<>();
        ChatOptions options = ToolCallingChatOptions.builder().model("fixture-model").build();
        boolean tools; volatile boolean failure, never; volatile CountDownLatch failReviewerAfter;
        @Override public ChatResponse call(Prompt prompt) { calls.incrementAndGet(); prompts.add(prompt); return next(prompt); }
        @Override public ChatOptions getOptions() { return options; }
        @Override public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.defer(() -> {
                streams.incrementAndGet(); prompts.add(prompt);
                if (failure) return Flux.error(new IllegalStateException("private-model-failure"));
                if (never) return Flux.never();
                return Flux.just(next(prompt));
            });
        }
        private ChatResponse next(Prompt prompt) {
            if (failure) throw new IllegalStateException("private-model-failure");
            String system = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance).map(message -> message.getText() == null ? "" : message.getText()).reduce("", String::concat);
            String leaf = system.contains("reviewer") ? "reviewer" : "writer";
            if (leaf.equals("reviewer") && failReviewerAfter != null) {
                awaitIgnoringInterrupt(failReviewerAfter); throw new IllegalStateException("controlled parallel sibling failure");
            }
            int round = rounds.computeIfAbsent(leaf, key -> new AtomicInteger()).incrementAndGet();
            if (round > 2) throw new AssertionError("governed model loop bound exceeded");
            if (tools && round == 1) return response(AssistantMessage.builder().content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall("private-native-call-" + leaf, "function", TOOL.name(), "{\"amount\":7}"))).build(), "tool_calls");
            return response(new AssistantMessage("Done " + leaf), "stop");
        }
        private static ChatResponse response(AssistantMessage message, String reason) {
            return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason(reason).build())),
                    ChatResponseMetadata.builder().usage(new DefaultUsage(10, 3, 13)).build());
        }
    }
}
