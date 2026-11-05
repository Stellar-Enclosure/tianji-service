package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
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
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Finite actual Runner execution with scripted model and in-memory ledger facts. */
@Timeout(15)
class AdkNativeAgentCompletionTest {
    private static final String ROUTE = "route@1#" + "a".repeat(64);
    private static final String POLICY = "policy@1#" + "b".repeat(64);

    @Test void plain_function_keeps_legacy_output_and_never_binds_native() {
        try (var rig = new Rig(false, "root-only")) {
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.execute().status());
            assertEquals("root final", rig.finished.get().output().get("text"));
            verify(rig.estimator, never()).bindNative(any(), any());
        }
    }

    @Test void conditional_collects_only_the_current_selected_child_and_root() {
        for (String branch : List.of("quick_research", "deep_research")) try (var rig = new Rig(false, branch)) {
            rig.nativeMode = true;
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.execute().status(), branch);
            assertEquals(Map.of("conditional_root", "root final", branch, "child summary"), rig.finished.get().output().get("agents"));
            assertEquals("root final", rig.finished.get().output().get("text"));
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
            verify(rig.estimator).bindNative(same(rig.snapshot), argThat(value -> value.model() == rig.chatModel));
            verify(rig.bound, times(3)).estimate(same(rig.snapshot), any());
            verify(rig.estimator, never()).estimate(any(), any());
            assertEquals(2, rig.events.stream().filter(row -> row.get("type").equals("NATIVE_TRANSFER_AUTHORIZED")).count());
        }
    }

    @Test void supervisor_requires_both_ordered_current_child_summaries() {
        try (var rig = new Rig(true, "supervisor")) {
            rig.nativeMode = true;
            assertEquals(AgentRunStatusEnum.COMPLETED, rig.execute().status());
            assertEquals(Map.of("supervisor", "root final", "researcher", "research summary", "critic", "critic summary"), rig.finished.get().output().get("agents"));
            assertEquals(5, rig.model.calls.get());
            assertEquals(4, rig.events.stream().filter(row -> row.get("type").equals("NATIVE_TRANSFER_AUTHORIZED")).count());
        }
    }

    @Test void plausible_root_without_a_confirmed_child_cannot_complete() {
        try (var rig = new Rig(false, "root-only")) {
            rig.nativeMode = true;
            assertEquals(AgentRunStatusEnum.FAILED, rig.execute().status());
            assertTrue(rig.finished.get().output().isEmpty());
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
        }
    }

    @Test void schema_route_and_configuration_drift_fail_before_any_model_call() {
        for (String mutation : List.of("schema", "route", "configuration")) try (var rig = new Rig(false, "root-only")) {
            rig.nativeMode = true;
            rig.mutation = mutation;
            assertEquals(AgentRunStatusEnum.FAILED, rig.execute().status(), mutation);
            assertEquals(0, rig.model.calls.get(), mutation);
            assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
            verify(rig.estimator, never()).bindNative(any(), any());
        }
    }

    @Test void same_name_different_child_model_identity_fails_before_budget_binding() {
        try (var rig = new Rig(false, "root-only")) {
            rig.nativeMode = true;
            rig.foreignChildModel = true;
            assertEquals(AgentRunStatusEnum.FAILED, rig.execute().status());
            assertEquals(0, rig.model.calls.get());
            verify(rig.estimator, never()).bindNative(any(), any());
        }
    }

    @Test void gate_authorization_signal_keeps_both_safe_suspension_decisions() {
        for (var decision : List.of(AgentRunAuthorization.Decision.DENY, AgentRunAuthorization.Decision.UNAVAILABLE)) {
            try (var rig = new Rig(false, "root-only"); var gateBinding = mockStatic(NativeAgentGate.class)) {
                rig.nativeMode = true;
                var gate = mock(NativeAgentGate.class);
                when(gate.failureSignal()).thenReturn(Flowable.error(new AgentExecutionAuthorization.Failure(decision)));
                gateBinding.when(() -> NativeAgentGate.bind(any(), any(), anyString(), any())).thenReturn(gate);
                assertEquals(AgentRunStatusEnum.SUSPENDED, rig.execute().status());
                verify(rig.ledger).suspendForAuthorization(eq(rig.actor), eq(rig.lease), eq(decision));
                verify(gate, atLeastOnce()).close();
            }
        }
    }

    @Test void native_callback_timeout_before_the_run_deadline_uses_fenced_finish() {
        try (var rig = new Rig(false, "root-only"); var gateBinding = mockStatic(NativeAgentGate.class)) {
            rig.nativeMode = true;
            var gate = mock(NativeAgentGate.class);
            when(gate.failureSignal()).thenReturn(Flowable.error(
                    new RuntimeException("safe boundary", new TimeoutException("safe callback timeout"))));
            gateBinding.when(() -> NativeAgentGate.bind(any(), any(), anyString(), any())).thenReturn(gate);

            assertEquals(AgentRunStatusEnum.TIMED_OUT, assertDoesNotThrow(rig::execute).status());
            assertTrue(rig.deadline.isAfter(rig.now));
            verify(rig.ledger).finish(eq(rig.actor), eq(rig.lease), anyLong(),
                    argThat(result -> result.status() == AgentRunStatusEnum.TIMED_OUT));
            verify(rig.ledger, never()).expireDeadline(eq(rig.actor), eq(rig.lease), eq(rig.query), anyLong(), anyString());
        }
    }

    @Test void gate_ledger_timeout_and_policy_signals_keep_existing_terminal_resolution() {
        for (String kind : List.of("revision", "lease", "timeout", "policy")) {
            try (var rig = new Rig(false, "root-only"); var gateBinding = mockStatic(NativeAgentGate.class)) {
                rig.nativeMode = true;
                rig.cancelAtClaim = kind.equals("revision") || kind.equals("lease");
                RuntimeException failure = switch (kind) {
                    case "revision" -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "safe boundary");
                    case "lease" -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "safe boundary");
                    case "timeout" -> new RuntimeException("safe boundary", new TimeoutException("safe timeout"));
                    default -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "safe boundary");
                };
                var gate = mock(NativeAgentGate.class);
                when(gate.failureSignal()).thenReturn(Flowable.error(failure));
                gateBinding.when(() -> NativeAgentGate.bind(any(), any(), anyString(), any())).thenReturn(gate);
                AgentRunStatusEnum expected = rig.cancelAtClaim ? AgentRunStatusEnum.CANCELLED
                        : kind.equals("timeout") ? AgentRunStatusEnum.TIMED_OUT : AgentRunStatusEnum.FAILED;
                assertEquals(expected, assertDoesNotThrow(rig::execute).status(), kind);
                assertEquals("UNKNOWN", rig.finished.get().sideEffectOutcome());
            }
        }
    }

    private static final class Rig implements AutoCloseable {
        final Instant now = Instant.now(), deadline = now.plusSeconds(60);
        final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        final GetAgentRunQuery query = new GetAgentRunQuery(UUID.randomUUID().toString());
        final AgentRunLease lease = new AgentRunLease(query.runId(), "worker", 1, deadline);
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class), bound = mock(AgentModelBudgetEstimator.class);
        final ChatModel chatModel = mock(ChatModel.class);
        final ScheduledExecutorService control = Executors.newScheduledThreadPool(2, task -> Thread.ofPlatform().daemon().unstarted(task));
        final AtomicReference<AgentRunDTO> current = new AtomicReference<>();
        final AtomicReference<AgentExecutionResult> finished = new AtomicReference<>();
        final List<Map<String,Object>> events = new CopyOnWriteArrayList<>();
        final ScriptModel model;
        final boolean supervisor;
        boolean nativeMode, foreignChildModel, cancelAtClaim;
        String mutation;
        AgentExecutionSnapshot snapshot;

        Rig(boolean supervisor, String script) {
            this.supervisor = supervisor;
            model = new ScriptModel(script);
            current.set(run(AgentRunStatusEnum.QUEUED, 0, 0));
            when(chatModel.getOptions()).thenReturn(ChatOptions.builder().model("fixture-model").build());
            when(ledger.get(actor, query)).thenAnswer(call -> current.get());
            when(ledger.databaseNow()).thenReturn(now);
            when(ledger.executionMaterial(actor, query)).thenAnswer(call -> material());
            when(ledger.claim(eq(actor), eq(query), eq(0L), anyString(), any())).thenAnswer(call -> {
                current.set(run(AgentRunStatusEnum.RUNNING, 1, 0)); return new ClaimedAgentRun(current.get(), lease);
            });
            when(ledger.heartbeat(eq(actor), eq(lease), any())).thenReturn(LeaseHeartbeat.ACTIVE);
            when(ledger.budgets()).thenReturn(budgets);
            when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> {
                AgentBudgetRequest value = call.getArgument(2);
                return new AgentBudgetReservation(value.reservationId(), 1, value.kind(), null, value.inputTokens(), value.outputTokens(), value.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
            });
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any())).thenReturn(
                    new AgentBudgetReservation("settled", 1, AgentBudgetRequest.Kind.MODEL, null, 10, 5, BigDecimal.ZERO, AgentBudgetReservation.State.SETTLED, 10L, 3L, BigDecimal.ZERO, false));
            for (var selected : List.of(estimator, bound)) {
                when(selected.estimate(any(), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 5, BigDecimal.ZERO));
                when(selected.contextWindow(any(), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("fixture-model", ROUTE, POLICY, 1000, 5));
                when(selected.actualUsage(any(), any())).thenReturn(new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO));
            }
            when(estimator.bindNative(any(), any())).thenReturn(bound);
            when(ledger.appendEvent(eq(actor), eq(lease), anyLong(), anyString(), anyString(), anyMap())).thenAnswer(call -> {
                long revision = call.getArgument(2); assertEquals(current.get().revision(), revision);
                events.add(Map.of("type", call.getArgument(3), "payload", Map.copyOf(call.getArgument(5))));
                current.set(run(AgentRunStatusEnum.RUNNING, revision + 1, current.get().lastEventSequence() + 1)); return current.get();
            });
            when(ledger.finish(eq(actor), eq(lease), anyLong(), any())).thenAnswer(call -> finish(call.getArgument(3)));
            when(ledger.suspendForAuthorization(eq(actor), eq(lease), any())).thenAnswer(call -> {
                current.set(run(AgentRunStatusEnum.SUSPENDED, current.get().revision() + 1, current.get().lastEventSequence())); return current.get();
            });
            when(ledger.expireDeadline(eq(actor), eq(lease), eq(query), anyLong(), anyString())).thenAnswer(call -> {
                if (current.get().deadlineAt().isAfter(now)) throw new AgentLedgerException(
                        AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Database deadline has not expired");
                return finish(new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT, Map.of(),
                        null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(), call.getArgument(4)));
            });
        }

        AgentRunDTO execute() {
            Function<Scheduler, ? extends BaseAgent> factory = nativeMode ? new NativeAgentCompilerStrategy() {
                @Override public TemplateSpec templateSpec() { return spec(); }
                @Override public boolean usesChatModel(ChatModel candidate) { return candidate == chatModel; }
                @Override public BaseAgent apply(Scheduler scheduler) { return tree(); }
            } : scheduler -> LlmAgent.builder().name("legacy_root").model(model).build();
            return new AdkAgentRunExecutor(ledger, mock(AgentFlowFactory.class), mock(AgentFlowConfigValidator.class),
                    Map.of("fixtureModel", chatModel), Map.of(spec().codeRef(), factory), control, estimator, null, false,
                    (request, boundary) -> AgentRunAuthorization.Decision.ALLOW).executeQueued(actor, query);
        }
        NativeAgentCompilerStrategy.TemplateSpec spec() {
            String root = supervisor ? "supervisor" : "conditional_root";
            List<String> children = supervisor ? List.of("researcher", "critic") : List.of("quick_research", "deep_research");
            List<NativeAgentCompilerStrategy.AgentSpec> agents = new ArrayList<>();
            agents.add(new NativeAgentCompilerStrategy.AgentSpec(root, null, supervisor ? "supervisor_root_output" : "conditional_root_output", true, true));
            children.forEach(name -> agents.add(new NativeAgentCompilerStrategy.AgentSpec(name, root, name + "_output", false, true)));
            return new NativeAgentCompilerStrategy.TemplateSpec(supervisor ? "supervisor-research-v1" : "conditional-research-v1",
                    supervisor ? NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1 : NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                    "fixtureModel", "fixture-model", ROUTE, "1", "1", root, agents, 2, supervisor ? 4 : 2, children);
        }
        BaseAgent tree() {
            var spec = spec();
            var children = spec.agents().stream().filter(row -> row.parentName() != null).map(row -> LlmAgent.builder()
                    .name(row.name()).outputKey(row.outputKey()).model(foreignChildModel ? new ScriptModel("root-only") : model)
                    .disallowTransferToParent(false).disallowTransferToPeers(true).build()).toList();
            return LlmAgent.builder().name(spec.rootAgentName()).outputKey(supervisor ? "supervisor_root_output" : "conditional_root_output")
                    .model(model).subAgents(children.toArray(BaseAgent[]::new)).disallowTransferToParent(true).disallowTransferToPeers(true).build();
        }
        AgentLedgerService.ExecutionMaterial material() {
            var policy = new ExecutionPolicyDTO(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(2), 8, 0, 10000, 1000, BigDecimal.TEN, "USD", 1, Set.of(), "retry", null);
            var definition = new AgentDefinitionSpecDTO("assistant", 1, "CODE_FIRST", spec().codeRef(), "schema".equals(mutation) ? "2" : "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route".equals(mutation) ? "route@2#" + "c".repeat(64) : ROUTE, POLICY), policy,
                    "configuration".equals(mutation) ? Map.of("unreviewed", true) : Map.of());
            snapshot = new AgentExecutionSnapshot(definition, "d".repeat(64), policy, new AgentInputDTO("Research", null, null), null);
            var authorization = new AgentRunAuthorization.Request(actor, query.runId(), "assistant", 1, "d".repeat(64), "e".repeat(64), "f".repeat(64), POLICY, false, deadline);
            return new AgentLedgerService.ExecutionMaterial(run(AgentRunStatusEnum.QUEUED, 0, 0), snapshot, authorization);
        }
        AgentRunDTO finish(AgentExecutionResult result) {
            finished.set(result); current.set(run(result.status(), current.get().revision() + 1, current.get().lastEventSequence())); return current.get();
        }
        AgentRunDTO run(AgentRunStatusEnum status, long revision, long sequence) {
            return new AgentRunDTO(query.runId(), "assistant", 1, null, status, null, status == AgentRunStatusEnum.RUNNING && cancelAtClaim,
                    status == AgentRunStatusEnum.QUEUED ? 0 : 1, revision, sequence, deadline, now, AgentRunTransitions.terminal(status) ? now : null);
        }
        @Override public void close() { control.shutdownNow(); }
    }

    private static final class ScriptModel extends BaseLlm {
        final AtomicInteger calls = new AtomicInteger();
        final String script;
        ScriptModel(String script) { super("fixture-model"); this.script = script; }
        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            int call = calls.getAndIncrement();
            if (script.equals("supervisor")) return Flowable.just(switch (call) {
                case 0 -> transfer("researcher", null, call);
                case 1 -> transfer("supervisor", "research summary", call);
                case 2 -> transfer("critic", null, call);
                case 3 -> transfer("supervisor", "critic summary", call);
                default -> finalText();
            });
            if (!script.equals("root-only")) return Flowable.just(switch (call) {
                case 0 -> transfer(script, null, call);
                case 1 -> transfer("conditional_root", "child summary", call);
                default -> finalText();
            });
            return Flowable.just(finalText());
        }
        private static LlmResponse transfer(String target, String text, int call) {
            List<Part> parts = new ArrayList<>(); if (text != null) parts.add(Part.fromText(text));
            parts.add(Part.builder().functionCall(FunctionCall.builder().id("transfer-" + call).name("transfer_to_agent").args(Map.of("agent_name", target)).build()).build());
            return LlmResponse.builder().content(Content.builder().role("model").parts(parts).build()).partial(false).turnComplete(false).build();
        }
        private static LlmResponse finalText() { return LlmResponse.builder().content(Content.builder().role("model").parts(Part.fromText("root final")).build()).partial(false).turnComplete(true).build(); }
        @Override public BaseLlmConnection connect(LlmRequest request) { throw new UnsupportedOperationException(); }
    }
}
