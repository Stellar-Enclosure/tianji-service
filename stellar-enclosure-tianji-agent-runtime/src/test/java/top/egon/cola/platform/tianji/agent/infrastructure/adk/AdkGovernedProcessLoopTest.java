package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.schedulers.Schedulers;
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
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
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
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessRecorder;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual native Runner and Spring AI adapter, with controlled ledger/Recorder facts and no external services. */
@Timeout(20)
class AdkGovernedProcessLoopTest {
    private static final Instant NOW = Instant.now();
    private static final String HASH = "a".repeat(64), RECEIPT = "tianji:ps-start:v1:" + "b".repeat(64), INSTANCE = "private-process-instance";
    private static final ToolDescriptor READ = tool("fixed.decision", "fixed_decision", ToolPolicy.Risk.READ_ONLY, DecisionClient.EXECUTE_RESOURCE_KEY, "value");
    private static final ToolDescriptor START = tool("fixed.start", "fixed_start", ToolPolicy.Risk.IDEMPOTENT_WRITE, ProcessClient.START_RESOURCE_KEY, "instanceId");
    private static final ToolDescriptor APPROVED = new ToolDescriptor("fixed.approved", 1, "fixed_approved", "Approved fixed.approved",
            new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
            new ToolSchema(Map.of("instanceId", ToolSchema.ValueType.STRING), Set.of("instanceId")),
            new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "fixed.approved.action", ProcessClient.START_RESOURCE_KEY, HASH, "approval-v1"));

    @Test void native_streaming_loop_uses_one_union_and_returns_only_the_authorized_instance() {
        try (var rig = new Rig("SINGLE", 2)) {
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(3, rig.model.streams.get()); assertEquals(1, rig.reads.get()); assertEquals(1, rig.writes.get()); assertEquals(1, rig.delegates.get());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome()); assertTrue(rig.scopeClosed.get());
            var request = rig.preparations.getFirst();
            assertEquals("invoice-1", request.action().businessKey()); assertEquals("business-v1", request.action().businessVersion());
            assertEquals(Map.of("amount", 7L), request.arguments()); assertNotEquals("private-native-writer-2", request.invocationId().toString());
            assertTrue(request.intentExpiry().isAfter(request.binding().run().deadlineAt()));
            ToolResponseMessage response = rig.model.prompts.getLast().getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).reduce((first, second) -> second).orElseThrow();
            String body = response.getResponses().getLast().responseData(); assertTrue(body.contains(INSTANCE));
            for (var hidden : List.of(RECEIPT, "actualCost", "actionIntentId", "business-v1")) assertFalse(body.contains(hidden));
            assertEquals(4, rig.events.stream().filter(event -> event.type().startsWith("TOOL_")).count());
            for (var event : rig.events) if (event.type().startsWith("TOOL_")) {
                String serialized = event.payload().toString();
                for (var hidden : List.of(INSTANCE, RECEIPT, "amount", "private-native", "nonce", "bearer", "business-v1")) assertFalse(serialized.contains(hidden));
            }
            for (int index = 0; index < rig.events.size(); index++) assertEquals(index + 1, rig.events.get(index).revision());
        }
    }

    @Test void fresh_and_original_result_access_share_one_entry_budget_and_a_stable_business_intent() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name(), START.name())); rig.replaySecond = true;
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(2, rig.writes.get()); assertEquals(1, rig.accesses.get()); assertEquals(1, rig.delegates.get());
            assertEquals(1, rig.preparations.stream().map(value -> value.action().intentId()).distinct().count());
            assertEquals(2, rig.preparations.stream().map(ToolInvocationStore.PreparationRequestVO::invocationId).distinct().count());
            assertEquals(rig.preparations.getLast().invocationId(), rig.accessRequests.getFirst().accessId());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
            var last = rig.events.stream().filter(event -> event.type().equals("TOOL_FINISHED")).reduce((a, b) -> b).orElseThrow();
            assertEquals("SUCCEEDED", last.payload().get("state")); assertFalse(last.payload().containsKey("dispatchEpoch"));
        }
    }

    @Test void read_and_start_entries_use_one_shared_limit_before_any_second_recorder_call() {
        try (var rig = new Rig("SINGLE", 1)) {
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED.name(), rig.finished.get().errorCode());
            assertEquals(1, rig.reads.get()); assertEquals(0, rig.writes.get()); assertEquals(0, rig.delegates.get());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome());
        }
    }

    @Test void actual_admitted_native_context_counts_malformed_metadata_and_access_failures_without_refund() {
        for (String variant : List.of("malformed", "metadata", "access", "preflight")) try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(READ.name()));
            var material = rig.material(); var scope = rig.bridge.bindScope(material, rig.lease, event -> { });
            var captured = new AtomicReference<ToolContext>();
            var plugin = spy(new AdkRunBudgetPlugin(rig.ledger, rig.actor, rig.lease, material.snapshot(), rig.estimator,
                    new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, material.authorization()),
                    Schedulers.from(rig.control), rig.control, new DefaultContextAssembler(), material.authorization(), new AgentContextRuntimeOptions(false, false), scope));
            doAnswer(call -> { captured.set(call.getArgument(2)); return call.callRealMethod(); }).when(plugin).beforeToolCallback(any(), anyMap(), any());
            var root = LlmAgent.builder().name("writer").instruction("writer").model(new SpringAiModelAdapterFactory().create(rig.model, "fixture-model"))
                    .tools(scope.declaredTools()).build();
            var runner = new AgentFlowInMemoryRunner(root, "tianji_" + rig.query.runId().replace('-', '_'), List.of(plugin));
            try {
                var session = runner.sessionService().createSession(runner.appName(), "20:USER:alice").blockingGet();
                runner.runAsync("20:USER:alice", session.id(), Content.fromParts(Part.fromText("Read")), RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.NONE)
                        .setMaxLlmCalls(8).setAutoCreateSession(false).build()).toList().timeout(5, TimeUnit.SECONDS).blockingGet();
                assertNotNull(captured.get()); assertEquals(1, rig.reads.get());
                rig.ownMetadata = variant.equals("metadata"); rig.metadataOnly = variant.equals("access"); rig.accessDenied = variant.equals("access"); rig.preflightDenied = variant.equals("preflight");
                var nativeStart = scope.declaredTools().stream().filter(value -> value.name().equals(START.name())).findFirst().orElseThrow();
                var arguments = variant.equals("malformed") ? Map.<String, Object>of("amount", "bad") : Map.<String, Object>of("amount", 7);
                assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(nativeStart, arguments, captured.get()).blockingGet());
                var nativeRead = scope.declaredTools().stream().filter(value -> value.name().equals(READ.name())).findFirst().orElseThrow();
                var failure = assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(nativeRead, Map.of("amount", 7), captured.get()).blockingGet());
                assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, failure.code(), variant);
                assertEquals(1, rig.reads.get()); assertEquals(0, rig.delegates.get());
            } finally { scope.close(); runner.close().blockingAwait(); }
        }
    }

    @Test void actual_session_fence_is_checked_again_immediately_before_the_start_delegate() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            var base = rig.material(); var sourceRun = base.run(); var sourceAuth = base.authorization();
            var sessionRun = new AgentRunDTO(sourceRun.runId(), sourceRun.definitionKey(), sourceRun.definitionVersion(), "session", sourceRun.status(),
                    sourceRun.waitReason(), sourceRun.cancelRequested(), sourceRun.attempt(), sourceRun.revision(), sourceRun.lastEventSequence(), sourceRun.deadlineAt(), sourceRun.createdAt(), sourceRun.finishedAt());
            var authorization = new AgentRunAuthorization.Request(sourceAuth.actor(), sourceAuth.runId(), sourceAuth.definitionKey(), sourceAuth.definitionVersion(),
                    sourceAuth.definitionSha256(), sourceAuth.requestSha256(), sourceAuth.snapshotSha256(), sourceAuth.policyVersionRef(), true, sourceAuth.deadlineAt());
            var material = new AgentLedgerService.ExecutionMaterial(sessionRun, base.snapshot(), authorization);
            var scope = rig.bridge.bindScope(material, rig.lease, event -> { });
            when(rig.ledger.sessionContextHistory(eq(rig.actor), eq(rig.lease), eq(authorization), eq("session"), any()))
                    .thenReturn(new AgentLedgerService.ContextHistory(List.of(), Set.of(), true, false, true));
            var plugin = new AdkRunBudgetPlugin(rig.ledger, rig.actor, rig.lease, material.snapshot(), rig.estimator,
                    new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization), Schedulers.from(rig.control), rig.control,
                    new DefaultContextAssembler(), authorization, new AgentContextRuntimeOptions(false, false), scope);
            var live = new AtomicBoolean(true); var checks = new AtomicInteger();
            plugin.bindSessionFence(() -> { checks.incrementAndGet(); if (!live.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled Session fence"); });
            rig.beforeTransport = () -> live.set(false);
            var root = LlmAgent.builder().name("writer").instruction("writer").model(new SpringAiModelAdapterFactory().create(rig.model, "fixture-model"))
                    .tools(scope.declaredTools()).build();
            var runner = new AgentFlowInMemoryRunner(root, "tianji", List.of(plugin));
            try {
                var session = runner.sessionService().createSession("tianji", "USER:alice", Map.of(), "session").blockingGet();
                var failure = assertThrows(AgentLedgerException.class, () -> runner.runAsync("USER:alice", session.id(), Content.fromParts(Part.fromText("Start")),
                        RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.NONE).setMaxLlmCalls(8).setAutoCreateSession(false).build())
                        .toList().timeout(5, TimeUnit.SECONDS).blockingGet());
                assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, failure.code()); assertTrue(checks.get() >= 4);
                assertEquals(1, rig.writes.get()); assertEquals(0, rig.delegates.get()); assertEquals(1, rig.model.calls.get());
            } finally { scope.close(); runner.close().blockingAwait(); }
        }
    }

    @Test void parallel_leaf_contexts_share_the_entry_limit() {
        try (var rig = new Rig("PARALLEL", 1)) {
            rig.model.scripts.put("writer", List.of(START.name())); rig.model.scripts.put("reviewer", List.of(READ.name()));
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED.name(), rig.finished.get().errorCode());
            assertTrue(rig.reads.get() + rig.writes.get() <= 1); assertTrue(rig.delegates.get() <= 1);
        }
    }

    @Test void committed_business_facts_survive_accounting_event_and_model_failures() {
        for (String failure : List.of("account", "event", "model")) try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            rig.pendingAccounting = failure.equals("account"); rig.failedEventType = failure.equals("event") ? "TOOL_FINISHED" : null;
            rig.model.failAfterTools = failure.equals("model");
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome(), failure); assertEquals(1, rig.delegates.get());
            assertTrue(rig.finished.get().output().isEmpty());
        }
    }

    @Test void unresolved_business_fact_stays_unknown_and_unused_declared_start_stays_none() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name())); rig.unknown = true;
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome()); assertEquals(1, rig.delegates.get());
        }
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of());
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertEquals(0, rig.writes.get());
        }
    }

    @Test void every_terminal_result_closes_the_scope_before_ledger_finish() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            rig.onFinish = result -> assertTrue(rig.scopeClosed.get(), "Scope must freeze before terminal CAS");
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
        }
    }

    @Test void cancellation_and_parallel_failure_freeze_before_late_committed_tool_facts() {
        for (boolean cancellation : List.of(false, true)) try (var rig = new Rig(cancellation ? "SINGLE" : "PARALLEL", 2)) {
            rig.model.scripts.put("writer", List.of(START.name())); rig.blockWrite = true;
            if (cancellation) when(rig.ledger.heartbeat(eq(rig.actor), eq(rig.lease), any())).thenAnswer(call ->
                    rig.writeEntered.getCount() == 0 ? LeaseHeartbeat.CANCEL_REQUESTED : LeaseHeartbeat.ACTIVE);
            else { rig.model.scripts.put("reviewer", List.of()); rig.model.failReviewerAfter = rig.writeEntered; }
            rig.onFinish = result -> {
                assertTrue(rig.scopeClosed.get(), "Freeze cannot wait until Runner cleanup"); rig.releaseWrite.countDown();
                try { assertTrue(rig.lateFact.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
                assertEquals("UNKNOWN", result.sideEffectOutcome());
            };
            try {
                assertEquals(cancellation ? AgentRunStatusEnum.CANCELLED : AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
                assertEquals(1, rig.delegates.get()); assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
                assertEquals(0, rig.events.stream().filter(value -> value.type().equals("TOOL_FINISHED")).count());
            } finally { rig.releaseWrite.countDown(); }
        }
    }

    @Test void model_idle_after_committed_start_preserves_confirmed_effect() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.idle = Duration.ofMillis(200); rig.model.scripts.put("writer", List.of(START.name())); rig.model.neverAfterTools = true;
            assertEquals(AgentRunStatusEnum.TIMED_OUT, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_MODEL_IDLE_TIMEOUT.name(), rig.finished.get().errorCode());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
        }
    }

    @Test void hard_deadline_uses_the_current_generation_fence_and_the_frozen_known_effect() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name())); rig.model.neverAfterTools = true;
            rig.afterWrite = () -> rig.databaseNow.set(rig.deadline.plusSeconds(1));
            when(rig.ledger.heartbeat(eq(rig.actor), eq(rig.lease), any())).thenAnswer(call -> rig.writes.get() > 0 ? LeaseHeartbeat.DEADLINE_EXPIRED : LeaseHeartbeat.ACTIVE);
            assertEquals(AgentRunStatusEnum.TIMED_OUT, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
            verify(rig.ledger).expireDeadline(eq(rig.actor), eq(rig.lease), eq(rig.query), anyLong(), eq("CONFIRMED"));
            verify(rig.ledger, never()).expireDeadline(eq(rig.actor), eq(rig.query), anyLong());
        }
    }

    @Test void an_old_scope_cannot_apply_its_confirmed_effect_to_a_new_execution_generation() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name())); rig.model.neverAfterTools = true;
            rig.afterWrite = () -> { rig.actualEpoch.incrementAndGet(); rig.databaseNow.set(rig.deadline.plusSeconds(1)); };
            when(rig.ledger.heartbeat(eq(rig.actor), eq(rig.lease), any())).thenAnswer(call -> rig.writes.get() > 0 ? LeaseHeartbeat.DEADLINE_EXPIRED : LeaseHeartbeat.ACTIVE);
            assertEquals(AgentRunStatusEnum.TIMED_OUT, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
            verify(rig.ledger).expireDeadline(eq(rig.actor), eq(rig.lease), eq(rig.query), anyLong(), eq("CONFIRMED"));
            verify(rig.ledger).expireDeadline(eq(rig.actor), eq(rig.query), anyLong());
        }
    }

    @Test void authorization_stop_freezes_known_effect_and_a_deadline_race_keeps_the_same_generation_fence() {
        for (boolean deadlineRace : List.of(false, true)) try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            rig.authorizer = (request, boundary) -> boundary == AgentRunAuthorization.Boundary.MODEL && rig.writes.get() > 0
                    ? AgentRunAuthorization.Decision.DENY : AgentRunAuthorization.Decision.ALLOW;
            when(rig.ledger.suspendForAuthorization(eq(rig.actor), eq(rig.lease), eq(AgentRunAuthorization.Decision.DENY))).thenAnswer(call -> {
                assertTrue(rig.scopeClosed.get()); assertEquals("CONFIRMED", rig.boundScope.get().sideEffectOutcome());
                if (deadlineRace) { rig.databaseNow.set(rig.deadline.plusSeconds(1)); throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled suspend race"); }
                rig.current.set(rig.run(AgentRunStatusEnum.SUSPENDED, rig.current.get().revision() + 1, rig.current.get().lastEventSequence()));
                return rig.current.get();
            });
            assertEquals(deadlineRace ? AgentRunStatusEnum.TIMED_OUT : AgentRunStatusEnum.SUSPENDED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(1, rig.delegates.get()); assertEquals(1, rig.model.streams.get());
            if (deadlineRace) {
                assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
                verify(rig.ledger).expireDeadline(eq(rig.actor), eq(rig.lease), eq(rig.query), anyLong(), eq("CONFIRMED"));
            } else assertNull(rig.finished.get(), "Authorization suspension does not manufacture a terminal result");
        }
    }

    @Test void a_completion_lease_race_uses_the_frozen_effect_when_the_absolute_deadline_has_passed() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            rig.onFinish = result -> { rig.databaseNow.set(rig.deadline.plusSeconds(1)); throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled finish race"); };
            assertEquals(AgentRunStatusEnum.TIMED_OUT, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals("CONFIRMED", rig.finished.get().sideEffectOutcome());
            verify(rig.ledger).expireDeadline(eq(rig.actor), eq(rig.lease), eq(rig.query), anyLong(), eq("CONFIRMED"));
            verify(rig.ledger, never()).expireDeadline(eq(rig.actor), eq(rig.query), anyLong());
        }
    }

    @Test void a_terminal_race_preserves_the_already_committed_result() {
        try (var rig = new Rig("SINGLE", 2)) {
            rig.model.scripts.put("writer", List.of(START.name()));
            var original = new AgentExecutionResult(rig.query.runId(), AgentRunStatusEnum.CANCELLED, Map.of(), null, null, null, null, "UNKNOWN");
            rig.afterWrite = () -> { rig.finished.set(original); rig.current.set(rig.run(AgentRunStatusEnum.CANCELLED, rig.current.get().revision() + 1, rig.current.get().lastEventSequence())); };
            assertEquals(AgentRunStatusEnum.CANCELLED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertSame(original, rig.finished.get()); assertTrue(rig.scopeClosed.get());
        }
    }

    @Test void approval_wait_finished_event_requires_zero_epoch_pending_accounting_and_no_error() {
        var id = UUID.randomUUID();
        var value = new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                "AWAITING_APPROVAL", 0L, ToolInvocationRecorder.AccountingState.PENDING, null);
        assertEquals("AdkGovernedToolBridge.ToolEventVO[redacted]", value.toString());
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                        "AWAITING_APPROVAL", null, ToolInvocationRecorder.AccountingState.PENDING, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                        "AWAITING_APPROVAL", 1L, ToolInvocationRecorder.AccountingState.PENDING, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                        "AWAITING_APPROVAL", 0L, null, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                        "AWAITING_APPROVAL", 0L, ToolInvocationRecorder.AccountingState.SETTLED, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new AdkGovernedToolBridge.ToolEventVO(AdkGovernedToolBridge.Phase.FINISHED, "writer", APPROVED.reference(), id,
                        "AWAITING_APPROVAL", 0L, ToolInvocationRecorder.AccountingState.PENDING, "PRIVATE_ERROR")));
    }

    @Test void actual_native_approval_wait_returns_only_safe_status_and_none_effect() {
        try (var rig = new Rig("SINGLE", 2, true)) {
            rig.model.scripts.put("writer", List.of(APPROVED.name()));
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(1, rig.writes.get()); assertEquals(1, rig.receiptSelections.get()); assertEquals(0, rig.approvalCalls.get()); assertEquals(0, rig.delegates.get());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertEquals(2, rig.model.streams.get());
            ToolResponseMessage response = rig.model.prompts.getLast().getInstructions().stream().filter(ToolResponseMessage.class::isInstance)
                    .map(ToolResponseMessage.class::cast).findFirst().orElseThrow();
            String body = response.getResponses().getFirst().responseData(); assertTrue(body.contains("AWAITING_APPROVAL"));
            for (String hidden : List.of("approval-ref", "approval-v1", "consumer", "source", "amount", "business-v1", RECEIPT)) assertFalse(body.contains(hidden));
            var event = rig.events.stream().filter(value -> value.type().equals("TOOL_FINISHED")).findFirst().orElseThrow();
            assertEquals("AWAITING_APPROVAL", event.payload().get("state")); assertEquals(0L, event.payload().get("dispatchEpoch"));
            assertEquals("PENDING", event.payload().get("accountingState")); assertFalse(event.payload().containsKey("errorCode"));
        }
    }

    @Test void approval_wait_consumes_one_plugin_entry_and_never_starts_a_second_hidden_loop() {
        try (var rig = new Rig("SINGLE", 1, true)) {
            rig.model.scripts.put("writer", List.of(APPROVED.name(), APPROVED.name()));
            assertEquals(AgentRunStatusEnum.FAILED, rig.executor().executeQueued(rig.actor, rig.query).status());
            assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED.name(), rig.finished.get().errorCode());
            assertEquals(1, rig.writes.get()); assertEquals(1, rig.receiptSelections.get()); assertEquals(0, rig.delegates.get());
            assertEquals("NONE", rig.finished.get().sideEffectOutcome()); assertEquals(2, rig.model.streams.get());
        }
    }

    @Test void explicit_approval_scope_forwards_bounded_deadline_and_native_fence_without_legacy_fallback() {
        try (var rig = new Rig("SINGLE", 2, true)) {
            rig.approvedMode = ApprovedMode.SUCCESS; rig.hostApprovalReceipt = "approval-ref";
            var scope = rig.bridge.bindScope(rig.material(), rig.lease, event -> { }); var fence = new AtomicInteger();
            var tool = scope.declaredTools().stream().filter(value -> value.name().equals(APPROVED.name())).findFirst().orElseThrow();
            var value = scope.execute(tool, Map.of("amount", 7), UUID.randomUUID(), "writer", fence::incrementAndGet);
            assertEquals(Map.of("status", "SUCCEEDED", "output", Map.of("instanceId", INSTANCE)), value);
            assertEquals(3, fence.get()); assertEquals(1, rig.boundedDelegates.get()); assertEquals(0, rig.legacyDelegates.get());
            assertEquals(rig.deadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS), rig.forwardedDeadline.get()); scope.close();
        }
    }

    @Test void known_approval_terminal_result_uses_fresh_access_without_another_start_or_approval_call() {
        try (var rig = new Rig("SINGLE", 2, true)) {
            rig.approvedMode = ApprovedMode.REPLAY; rig.hostApprovalReceipt = "approval-ref";
            var scope = rig.bridge.bindScope(rig.material(), rig.lease, event -> { });
            var tool = scope.declaredTools().stream().filter(value -> value.name().equals(APPROVED.name())).findFirst().orElseThrow();
            var value = scope.execute(tool, Map.of("amount", 7), UUID.randomUUID(), "writer", () -> { });
            assertEquals(Map.of("status", "SUCCEEDED", "output", Map.of("instanceId", INSTANCE)), value);
            assertEquals(1, rig.accesses.get()); assertEquals(0, rig.approvalCalls.get()); assertEquals(0, rig.delegates.get()); scope.close();
        }
    }

    @Test void approval_observer_truth_survives_outer_mask_and_scope_freeze_rejects_late_rewrite() {
        try (var rig = new Rig("SINGLE", 2, true)) {
            rig.approvedMode = ApprovedMode.OUTER_MASK; rig.hostApprovalReceipt = "approval-ref";
            var scope = rig.bridge.bindScope(rig.material(), rig.lease, event -> { });
            var tool = scope.declaredTools().stream().filter(value -> value.name().equals(APPROVED.name())).findFirst().orElseThrow();
            assertThrows(AgentLedgerException.class, () -> scope.execute(tool, Map.of("amount", 7), UUID.randomUUID(), "writer", () -> { }));
            assertEquals("CONFIRMED", scope.sideEffectOutcome()); scope.close();
            rig.lateObserver.get().onCommitted(ToolExecutionOutcome.Status.UNKNOWN); assertEquals("CONFIRMED", scope.sideEffectOutcome());
        }
        try (var rig = new Rig("SINGLE", 2, true)) {
            var scope = rig.bridge.bindScope(rig.material(), rig.lease, event -> { });
            var tool = scope.declaredTools().stream().filter(value -> value.name().equals(APPROVED.name())).findFirst().orElseThrow();
            assertEquals(Map.of("status", "AWAITING_APPROVAL"), scope.execute(tool, Map.of("amount", 7), UUID.randomUUID(), "writer", () -> { }));
            assertEquals("NONE", scope.sideEffectOutcome()); scope.close();
            rig.lateObserver.get().onCommitted(ToolExecutionOutcome.Status.SUCCEEDED); assertEquals("NONE", scope.sideEffectOutcome());
        }
    }

    @Test void root_affinity_uses_one_lazy_selected_bridge_and_old_no_tool_execution_stays_compatible() {
        try (var rig = new Rig("SINGLE", 0)) {
            rig.tools = false; rig.model.scripts.put("writer", List.of());
            var executor = rig.executor(); assertEquals(0, rig.resolutions.get());
            assertEquals(AgentRunStatusEnum.COMPLETED, executor.executeQueued(rig.actor, rig.query).status());
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome()); assertEquals(0, rig.resolutions.get());
            assertTrue(executor.usesGovernedProcessTools(rig.bridge)); assertTrue(executor.usesGovernedProcessTools(rig.bridge)); assertEquals(1, rig.resolutions.get());
            var old = new AdkReadOnlyDecisionToolBridge(rig.recorder, rig.decision, List.of(READ), material -> List.of(binding(material.authorization(), READ)));
            assertFalse(executor.usesReadOnlyDecisionTools(old)); assertEquals(1, rig.resolutions.get());
        }
    }

    private static ToolDescriptor tool(String key, String name, ToolPolicy.Risk risk, String resource, String output) {
        return new ToolDescriptor(key, 1, name, "Fixed " + name, new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("amount")),
                new ToolSchema(Map.of(output, ToolSchema.ValueType.STRING), Set.of(output)), new ToolPolicy(risk, key + ".action", resource, HASH, null));
    }
    private static ToolExecutionGrant.BindingVO binding(AgentRunAuthorization.Request run, ToolDescriptor descriptor) {
        return new ToolExecutionGrant.BindingVO(run, descriptor.reference(), descriptor.policy().bindingSha256(), "TIANJI_TOOL_NATIVE_PROCESS", URI.create("https://process.example/resource"), descriptor.policy().executionResourceKey(), null);
    }
    private record EventWrite(long revision, String type, Map<String, Object> payload) { }
    private enum ApprovedMode { WAIT, SUCCESS, REPLAY, OUTER_MASK }

    private static final class Rig implements AutoCloseable {
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
        final ToolInvocationRecorder recorder = mock(ToolInvocationRecorder.class);
        final ToolResultAccessRecorder access = mock(ToolResultAccessRecorder.class);
        final ReadOnlyDecisionToolTransport decision = mock(ReadOnlyDecisionToolTransport.class);
        final FixedToolApprovalVerifier approvalVerifier = mock(FixedToolApprovalVerifier.class);
        final AdkGovernedProcessToolBridge.ApprovalReceiptResolver approvalReceipts = mock(AdkGovernedProcessToolBridge.ApprovalReceiptResolver.class);
        final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        final GetAgentRunQuery query = new GetAgentRunQuery(UUID.randomUUID().toString());
        final Instant deadline = NOW.plusSeconds(60);
        final AgentRunLease lease = new AgentRunLease(query.runId(), "worker", 1, deadline);
        final ScheduledExecutorService control = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().unstarted(task));
        final List<EventWrite> events = new CopyOnWriteArrayList<>();
        final List<ToolInvocationStore.PreparationRequestVO> preparations = new CopyOnWriteArrayList<>();
        final List<ToolResultAccessStore.StartResultAccessRequestVO> accessRequests = new CopyOnWriteArrayList<>();
        final AtomicReference<AgentRunDTO> current = new AtomicReference<>();
        final AtomicReference<AgentExecutionResult> finished = new AtomicReference<>();
        final AtomicReference<Instant> databaseNow = new AtomicReference<>(NOW);
        final AtomicLong actualEpoch = new AtomicLong(1);
        final AtomicInteger reads = new AtomicInteger(), writes = new AtomicInteger(), delegates = new AtomicInteger(), accesses = new AtomicInteger(), resolutions = new AtomicInteger();
        final AtomicInteger receiptSelections = new AtomicInteger(), approvalCalls = new AtomicInteger(), boundedDelegates = new AtomicInteger(), legacyDelegates = new AtomicInteger();
        final AtomicBoolean scopeClosed = new AtomicBoolean();
        final AtomicReference<AdkGovernedToolBridge.Scope> boundScope = new AtomicReference<>();
        final AtomicReference<Instant> forwardedDeadline = new AtomicReference<>();
        final AtomicReference<ToolCommittedFactObserver> lateObserver = new AtomicReference<>();
        final CountDownLatch writeEntered = new CountDownLatch(1), releaseWrite = new CountDownLatch(1), lateFact = new CountDownLatch(1);
        final FakeChatModel model = new FakeChatModel();
        final FixedBpmnStartToolTransport start;
        final AdkGovernedProcessToolBridge bridge;
        final String workflow; final int maxEntries; final boolean approved; final ToolDescriptor writeTool;
        boolean tools = true; Duration idle = Duration.ofSeconds(5);
        volatile boolean replaySecond, metadataOnly, ownMetadata, accessDenied, preflightDenied, pendingAccounting, unknown, blockWrite;
        volatile String failedEventType;
        volatile ApprovedMode approvedMode = ApprovedMode.WAIT;
        volatile String hostApprovalReceipt;
        Runnable beforeTransport = () -> { }, afterWrite = () -> { }; Consumer<AgentExecutionResult> onFinish = value -> { };
        AgentRunAuthorization authorizer = (request, boundary) -> AgentRunAuthorization.Decision.ALLOW;

        Rig(String workflow, int maxEntries) { this(workflow, maxEntries, false); }
        Rig(String workflow, int maxEntries, boolean approved) {
            this.workflow = workflow; this.maxEntries = maxEntries; this.approved = approved; this.writeTool = approved ? APPROVED : START;
            current.set(run(AgentRunStatusEnum.QUEUED, 0, 0));
            model.scripts.put("writer", List.of(READ.name(), writeTool.name()));
            when(ledger.get(actor, query)).thenAnswer(call -> current.get()); when(ledger.databaseNow()).thenAnswer(call -> databaseNow.get());
            when(ledger.executionMaterial(actor, query)).thenAnswer(call -> material());
            when(ledger.claim(eq(actor), eq(query), eq(0L), anyString(), any())).thenAnswer(call -> { current.set(run(AgentRunStatusEnum.RUNNING, 1, 0)); return new ClaimedAgentRun(current.get(), lease); });
            when(ledger.heartbeat(eq(actor), eq(lease), any())).thenReturn(LeaseHeartbeat.ACTIVE); when(ledger.budgets()).thenReturn(budgets);
            when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> {
                AgentBudgetRequest request = call.getArgument(2);
                return new AgentBudgetReservation(request.reservationId(), 1, request.kind(), request.toolVersion(), request.inputTokens(), request.outputTokens(), request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
            });
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any())).thenAnswer(call -> {
                AgentBudgetSettlement actual = call.getArgument(3);
                return new AgentBudgetReservation(call.getArgument(2), 1, AgentBudgetRequest.Kind.MODEL, null, 10, 5, BigDecimal.ZERO, AgentBudgetReservation.State.SETTLED, actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false);
            });
            when(estimator.estimate(any(), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 5, BigDecimal.ZERO));
            when(estimator.contextWindow(any(), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("fixture-model", "route-v1", "policy-v1", 1000, 5));
            when(estimator.actualUsage(any(), any())).thenReturn(new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO));
            when(ledger.appendEvent(eq(actor), eq(lease), anyLong(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
                long revision = call.getArgument(2); var before = current.get(); String type = call.getArgument(3);
                if (before.status() != AgentRunStatusEnum.RUNNING || before.revision() != revision) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled event fence");
                if (type.equals(failedEventType)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "controlled event failure");
                events.add(new EventWrite(revision, type, Map.copyOf(call.getArgument(5)))); current.set(run(AgentRunStatusEnum.RUNNING, revision + 1, before.lastEventSequence() + 1)); return current.get();
            });
            when(ledger.finish(eq(actor), eq(lease), anyLong(), any())).thenAnswer(call -> {
                if (current.get().revision() != (long) call.getArgument(2) || actualEpoch.get() != lease.epoch() || AgentRunTransitions.terminal(current.get().status())) throw conflict();
                AgentExecutionResult result = call.getArgument(3); onFinish.accept(result); return terminal(result);
            });
            when(ledger.expireDeadline(eq(actor), eq(query), anyLong())).thenAnswer(call -> terminal(timeout("UNKNOWN")));
            when(ledger.expireDeadline(eq(actor), eq(lease), eq(query), anyLong(), anyString())).thenAnswer(call -> {
                if (current.get().revision() != (long) call.getArgument(3) || actualEpoch.get() != lease.epoch()) throw conflict();
                assertTrue(scopeClosed.get()); return terminal(timeout(call.getArgument(4)));
            });
            when(recorder.usesLedger(ledger)).thenReturn(true); when(access.usesLedger(ledger)).thenReturn(true);
            when(recorder.executeReadOnly(eq(decision), any())).thenAnswer(call -> {
                reads.incrementAndGet(); var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class);
                return new ToolInvocationRecorder.RecordResult(query.runId(), request.invocationId(), "SUCCEEDED", 2, 1, NOW, NOW,
                        new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("value", "private-read-output"), null, null, BigDecimal.ZERO), true, ToolInvocationRecorder.AccountingState.SETTLED);
            });
            start = new FixedBpmnStartToolTransport() {
                @Override public <T> T withVerifiedCall(Request request, Callback<T> callback) {
                    beforeTransport.run();
                    return callback.apply(new ReadOnlyDecisionToolTransport.VerifiedExecutor(TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.SERVICE, "process-service", "https://service.example/20"), deadline), new OneShotStart() {
                        @Override public StartObservation start() { legacyDelegates.incrementAndGet(); return execute(); }
                        @Override public StartObservation start(Instant callDeadlineAt) {
                            boundedDelegates.incrementAndGet(); forwardedDeadline.set(callDeadlineAt); return execute();
                        }
                        private StartObservation execute() {
                            delegates.incrementAndGet(); writeEntered.countDown(); if (blockWrite) awaitIgnoringInterrupt(releaseWrite);
                            return new StartObservation(RECEIPT, INSTANCE);
                        }
                    });
                }
            };
            when(recorder.executeIdempotentStart(any(), any(), any())).thenAnswer(call -> {
                var request = call.getArgument(1, ToolInvocationStore.PreparationRequestVO.class); preparations.add(request); int number = writes.incrementAndGet();
                if (preflightDenied) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "controlled preparation denial");
                if (metadataOnly || replaySecond && number > 1) return new ToolInvocationRecorder.IdempotentStartResultVO(request.action().intentId(), "APPLIED", 3, null);
                if (ownMetadata) return new ToolInvocationRecorder.IdempotentStartResultVO(request.action().intentId(), "DISPATCHING", 2,
                        new ToolInvocationRecorder.WriteAttemptResultVO(query.runId(), request.invocationId(), "DISPATCHING", 2, 1, NOW, null, null, false, ToolInvocationRecorder.AccountingState.PENDING));
                call.getArgument(0, FixedBpmnStartToolTransport.class).withVerifiedCall(new FixedBpmnStartToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.action(), request.invocationId(), deadline),
                        (executor, operation) -> operation.start());
                var status = unknown ? ToolExecutionOutcome.Status.UNKNOWN : ToolExecutionOutcome.Status.SUCCEEDED;
                call.getArgument(2, ToolCommittedFactObserver.class).onCommitted(status); lateFact.countDown(); afterWrite.run();
                var outcome = unknown ? new ToolExecutionOutcome(status, Map.of(), null, "OUTCOME_UNKNOWN", BigDecimal.ZERO)
                        : new ToolExecutionOutcome(status, Map.of("instanceId", INSTANCE), RECEIPT, null, BigDecimal.ZERO);
                return new ToolInvocationRecorder.IdempotentStartResultVO(request.action().intentId(), "PREPARED", 1,
                        new ToolInvocationRecorder.WriteAttemptResultVO(query.runId(), request.invocationId(), status.name(), 3, 1, NOW, NOW, outcome, true,
                                pendingAccounting ? ToolInvocationRecorder.AccountingState.PENDING : ToolInvocationRecorder.AccountingState.SETTLED));
            });
            when(approvalReceipts.resolve(any())).thenAnswer(call -> {
                receiptSelections.incrementAndGet(); var request = call.getArgument(0, AdkGovernedProcessToolBridge.ApprovalReceiptRequestVO.class);
                assertEquals(APPROVED, request.descriptor()); assertEquals(request.run().actor(), request.action().owner()); return hostApprovalReceipt;
            });
            when(recorder.executeApprovedStart(any(), any(), any(), nullable(String.class), any())).thenAnswer(call -> {
                assertSame(approvalVerifier, call.getArgument(0));
                var transport = call.getArgument(1, FixedBpmnStartToolTransport.class);
                var request = call.getArgument(2, ToolInvocationStore.PreparationRequestVO.class); preparations.add(request); writes.incrementAndGet();
                var observer = call.getArgument(4, ToolCommittedFactObserver.class); lateObserver.set(observer);
                if (approvedMode == ApprovedMode.WAIT) {
                    observer.onCommitted(ToolExecutionOutcome.Status.AWAITING_APPROVAL);
                    return new ToolInvocationRecorder.ApprovedStartResultVO(request.action().intentId(), "AWAITING_APPROVAL", 1, null);
                }
                if (approvedMode == ApprovedMode.REPLAY) {
                    return new ToolInvocationRecorder.ApprovedStartResultVO(request.action().intentId(), "APPLIED", 3, null);
                }
                approvalCalls.incrementAndGet();
                Instant bounded = deadline.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
                transport.withVerifiedCall(new FixedBpmnStartToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.action(), request.invocationId(), bounded),
                        (executor, operation) -> operation.start(bounded));
                observer.onCommitted(ToolExecutionOutcome.Status.SUCCEEDED); lateFact.countDown(); afterWrite.run();
                if (approvedMode == ApprovedMode.OUTER_MASK) throw new FixedToolApprovalVerifier.Failure(FixedToolApprovalVerifier.FailureKind.APPROVAL_UNAVAILABLE);
                var outcome = new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, BigDecimal.ZERO);
                return new ToolInvocationRecorder.ApprovedStartResultVO(request.action().intentId(), "DISPATCHING", 2,
                        new ToolInvocationRecorder.WriteAttemptResultVO(query.runId(), request.invocationId(), "SUCCEEDED", 3, 1, NOW, NOW, outcome, true,
                                ToolInvocationRecorder.AccountingState.SETTLED));
            });
            when(access.recordKnownStartResultAccess(any(), any())).thenAnswer(call -> {
                accesses.incrementAndGet(); var request = call.getArgument(0, ToolResultAccessStore.StartResultAccessRequestVO.class); accessRequests.add(request);
                if (accessDenied) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "controlled access denial");
                call.getArgument(1, ToolCommittedFactObserver.class).onCommitted(ToolExecutionOutcome.Status.SUCCEEDED);
                return new ToolResultAccessRecorder.StartResultAccessVO(query.runId(), request.accessId(), request.action().intentId(), 3, HASH,
                        new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, Map.of("instanceId", INSTANCE), RECEIPT, null, new BigDecimal("0.0300")), ToolInvocationRecorder.AccountingState.SETTLED);
            });
            var actual = approved
                    ? new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, List.of(READ, writeTool),
                    value -> tools ? List.of(binding(value.authorization(), READ), binding(value.authorization(), writeTool)) : List.of(),
                    request -> new AdkGovernedProcessToolBridge.StartActionContextVO("business-v1", Duration.ofDays(1)), approvalVerifier, approvalReceipts)
                    : new AdkGovernedProcessToolBridge(ledger, recorder, decision, start, access, List.of(READ, writeTool),
                    value -> tools ? List.of(binding(value.authorization(), READ), binding(value.authorization(), writeTool)) : List.of(),
                    request -> new AdkGovernedProcessToolBridge.StartActionContextVO("business-v1", Duration.ofDays(1)));
            bridge = spy(actual);
            doAnswer(call -> {
                var scope = actual.bindScope(call.getArgument(0), call.getArgument(1), call.getArgument(2)); boundScope.set(scope);
                return new AdkGovernedToolBridge.Scope() {
                    @Override public List<BaseTool> declaredTools() { return scope.declaredTools(); }
                    @Override public Map<String, Object> execute(BaseTool tool, Map<String, Object> arguments, UUID id, String author, Runnable fence) { return scope.execute(tool, arguments, id, author, fence); }
                    @Override public String sideEffectOutcome() { return scope.sideEffectOutcome(); }
                    @Override public void close() { scopeClosed.set(true); scope.close(); }
                };
            }).when(bridge).bindScope(any(), any(), any());
        }

        AgentLedgerService.ExecutionMaterial material() {
            var policy = new ExecutionPolicyDTO(Duration.ofSeconds(60), idle, Duration.ofSeconds(5), 8, tools ? maxEntries : 0, 10000, 1000,
                    BigDecimal.TEN, "USD", 2, tools ? Set.of(READ.versionKey(), writeTool.versionKey()) : Set.of(), "retry-v1", approved ? "approval-v1" : null);
            var agents = workflow.equals("SINGLE") ? List.of(Map.of("name", "writer", "instruction", "writer"))
                    : List.of(Map.of("name", "writer", "instruction", "writer"), Map.of("name", "reviewer", "instruction", "reviewer"));
            var config = Map.<String, Object>of("chatModelBeanName", "fixtureModel", "modelName", "fixture-model", "rootAgentName", workflow.equals("SINGLE") ? "writer" : "root",
                    "agents", agents, "workflows", workflow.equals("SINGLE") ? List.of() : List.of(Map.of("name", "root", "type", workflow, "subAgentNames", List.of("writer", "reviewer"))));
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), tools ? List.of(READ.reference(), writeTool.reference()) : List.of(), "route-v1", "policy-v1"), policy, config);
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("Use the reviewed tools", null, null), "invoice-1");
            var authorization = new AgentRunAuthorization.Request(actor, query.runId(), "assistant", 1, HASH, "c".repeat(64), "d".repeat(64), "policy-v1", false, deadline);
            return new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED, 0, 0), snapshot, authorization);
        }
        AdkAgentRunExecutor executor() {
            return new AdkAgentRunExecutor(ledger, factory(), mock(AgentFlowConfigValidator.class), Map.of("fixtureModel", model), Map.of(), control, estimator,
                    null, false, authorizer, new DefaultContextAssembler(), new AgentContextRuntimeOptions(false, false),
                    () -> { resolutions.incrementAndGet(); return bridge; });
        }
        private AgentRunDTO terminal(AgentExecutionResult result) { finished.set(result); current.set(run(result.status(), current.get().revision() + 1, current.get().lastEventSequence())); return current.get(); }
        private AgentExecutionResult timeout(String effect) { return new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT, Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(), effect); }
        private AgentRunDTO run(AgentRunStatusEnum status, long revision, long sequence) {
            return new AgentRunDTO(query.runId(), "assistant", 1, null, status, null, false, status == AgentRunStatusEnum.QUEUED ? 0 : 1, revision, sequence, deadline, NOW, AgentRunTransitions.terminal(status) ? NOW : null);
        }
        @Override public void close() { releaseWrite.countDown(); control.shutdownNow(); }
    }

    private static AgentFlowFactory factory() {
        return new AgentFlowFactory(new SpringAiModelAdapterFactory(), new AgentWorkflowStrategyFactory(List.of(new SequentialAgentWorkflowBuilderStrategy(), new ParallelAgentWorkflowBuilderStrategy(), new LoopAgentWorkflowBuilderStrategy())));
    }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "controlled deadline generation conflict"); }
    private static void awaitIgnoringInterrupt(CountDownLatch gate) {
        boolean interrupted = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try {
            while (true) {
                long remaining = deadline - System.nanoTime(); assertTrue(remaining > 0, "controlled operation was not released");
                try { assertTrue(gate.await(remaining, TimeUnit.NANOSECONDS)); return; }
                catch (InterruptedException cancelled) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static final class FakeChatModel implements ChatModel {
        final AtomicInteger calls = new AtomicInteger(), streams = new AtomicInteger();
        final List<Prompt> prompts = new CopyOnWriteArrayList<>();
        final Map<String, List<String>> scripts = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> rounds = new ConcurrentHashMap<>();
        volatile boolean neverAfterTools, failAfterTools;
        volatile CountDownLatch failReviewerAfter;
        @Override public ChatOptions getOptions() { return ToolCallingChatOptions.builder().model("fixture-model").build(); }
        @Override public ChatResponse call(Prompt prompt) { calls.incrementAndGet(); prompts.add(prompt); return next(prompt).blockFirst(); }
        @Override public Flux<ChatResponse> stream(Prompt prompt) { return Flux.defer(() -> { streams.incrementAndGet(); prompts.add(prompt); return next(prompt); }); }
        private Flux<ChatResponse> next(Prompt prompt) {
            String instructions = prompt.getInstructions().stream().filter(SystemMessage.class::isInstance).map(message -> message.getText() == null ? "" : message.getText()).reduce("", String::concat);
            String leaf = instructions.contains("reviewer") ? "reviewer" : "writer";
            if (leaf.equals("reviewer") && failReviewerAfter != null) { awaitIgnoringInterrupt(failReviewerAfter); return Flux.error(new IllegalStateException("controlled sibling failure")); }
            int round = rounds.computeIfAbsent(leaf, ignored -> new AtomicInteger()).incrementAndGet(); var script = scripts.getOrDefault(leaf, List.of());
            if (round > 5) throw new AssertionError("native loop bound exceeded");
            if (round <= script.size()) return Flux.just(response(AssistantMessage.builder().content("").toolCalls(List.of(
                    new AssistantMessage.ToolCall("private-native-" + leaf + "-" + round, "function", script.get(round - 1), "{\"amount\":7}"))).build(), "tool_calls"));
            if (neverAfterTools) return Flux.never(); if (failAfterTools) return Flux.error(new IllegalStateException("private model failure"));
            return Flux.just(response(new AssistantMessage("Done " + leaf), "stop"));
        }
        private static ChatResponse response(AssistantMessage message, String reason) {
            return new ChatResponse(List.of(new Generation(message, ChatGenerationMetadata.builder().finishReason(reason).build())), ChatResponseMetadata.builder().usage(new DefaultUsage(10, 3, 13)).build());
        }
    }
}
