package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real native Runner, transfer Method and in-memory Session; only durable budget/authorization facts are controlled. */
@Timeout(20)
class NativeAgentTransferRunnerTest {
    private static final String HASH = "a".repeat(64), MODEL = "fixture-native-model";
    private static final String ROUTE = "native-route@1#" + HASH, POLICY = "native-policy@1#" + HASH;
    private static final String FINAL = "genuine root final", SUMMARY = "current child summary\nsecond line\n";
    private static final String PRIVATE_FAILURE = "private native callback detail";

    @Test void conditional_transfer_with_safe_provider_id_preserves_text_and_call() {
        for (String child : List.of("quick_research", "deep_research")) {
            try (var rig = new Rig(conditional(), conditionalScript(child, "provider-root", "provider-child", SUMMARY))) {
                List<Event> events = assertDoesNotThrow(rig::run);
                assertConditional(rig, events, child, SUMMARY);
                assertEquals(List.of("provider-root", "provider-child"), rig.tools.stream().map(ToolObservationVO::callId).toList());
                assertEquals(List.of("conditional_root", child, "conditional_root"), rig.modelAuthors());
                verify(rig.budgets, times(3)).reserveBudget(eq(rig.actor), eq(rig.lease), argThat(request -> request.kind() == AgentBudgetRequest.Kind.MODEL));
                verify(rig.budgets, never()).reserveBudget(eq(rig.actor), eq(rig.lease), argThat(request -> request.kind() == AgentBudgetRequest.Kind.TOOL));
            }
        }
    }

    @Test void conditional_transfer_without_provider_id_binds_sdk_id() {
        try (var rig = new Rig(conditional(), conditionalScript("quick_research", null, null, SUMMARY))) {
            assertConditional(rig, assertDoesNotThrow(rig::run), "quick_research", SUMMARY);
            assertEquals(List.of(Optional.empty(), Optional.empty()), rig.providerIds);
            assertActualSdkIds(rig);
        }
    }

    @Test void conditional_transfer_with_empty_provider_id_binds_sdk_id() {
        try (var rig = new Rig(conditional(), conditionalScript("deep_research", "", "", SUMMARY))) {
            assertConditional(rig, assertDoesNotThrow(rig::run), "deep_research", SUMMARY);
            assertEquals(List.of(Optional.of(""), Optional.of("")), rig.providerIds);
            assertActualSdkIds(rig);
        }
    }

    @Test void supervisor_round_trip_is_researcher_then_critic() {
        var script = List.of(
                transfer("supervisor", "researcher", "research-out", null),
                transfer("researcher", "supervisor", "research-back", "research result\n"),
                transfer("supervisor", "critic", "critic-out", null),
                transfer("critic", "supervisor", "critic-back", "critical review\n"),
                finalResponse("supervisor", FINAL));
        try (var rig = new Rig(supervisor(), script)) {
            List<Event> events = assertDoesNotThrow(rig::run);
            assertEquals(List.of("supervisor", "researcher", "supervisor", "critic", "supervisor"), rig.modelAuthors());
            assertEquals(List.of("researcher", "supervisor", "critic", "supervisor"), rig.confirmedTargets);
            assertEquals(Map.of("supervisor", FINAL, "researcher", "research result\n", "critic", "critical review\n"), rig.gate.requireCompletedOutputs());
            assertEquals(4, rig.audits.size());
            for (int index = 0; index < rig.audits.size(); index++) {
                assertEquals(index + 1, ((Number) rig.audits.get(index).payload().get("ordinal")).intValue());
                assertEquals(Set.of("to", "ordinal", "topologyHash", "state"), rig.audits.get(index).payload().keySet());
            }
            Event researchHandoff = transferEvent(events, "researcher"), criticHandoff = transferEvent(events, "critic");
            assertFalse(researchHandoff.finalResponse()); assertFalse(criticHandoff.finalResponse());
            assertFalse(rig.stateAt(researchHandoff).sessionState().containsKey("researcher_output"));
            assertFalse(rig.stateAt(researchHandoff).stateDelta().containsKey("researcher_output"));
            assertFalse(rig.stateAt(criticHandoff).sessionState().containsKey("critic_output"));
            assertFalse(rig.stateAt(criticHandoff).stateDelta().containsKey("critic_output"));
            var state = rig.latestSession().state();
            assertEquals(FINAL, state.get("researcher_output")); assertEquals(FINAL, state.get("critic_output"));
            assertEquals(FINAL, state.get("supervisor_root_output"));
        }
    }

    @Test void child_handoff_is_nonfinal_and_later_root_output_key_is_ignored() {
        var childParts = List.of(Part.builder().text("private model thought").thought(true).build(), Part.fromText(SUMMARY), call("conditional_root", "child-return"));
        var script = List.of(transfer("conditional_root", "quick_research", "root-out", null),
                new ScriptStepVO("quick_research", response(childParts, false)), finalResponse("conditional_root", FINAL));
        try (var rig = new Rig(conditional(), script)) {
            List<Event> events = assertDoesNotThrow(rig::run);
            Event handoff = transferEvent(events, "quick_research");
            assertFalse(handoff.finalResponse()); assertEquals(SUMMARY, visibleText(handoff));
            assertEquals(1, handoff.content().orElseThrow().parts().orElseThrow().stream().filter(part -> part.functionCall().isPresent()).count());
            assertEquals(SUMMARY, rig.gate.requireCompletedOutputs().get("quick_research"));
            assertFalse(rig.stateAt(handoff).sessionState().containsKey("quick_research_output"));
            assertFalse(rig.stateAt(handoff).stateDelta().containsKey("quick_research_output"));
            assertEquals(FINAL, rig.latestSession().state().get("quick_research_output"));
            assertFalse(rig.latestSession().state().containsKey("deep_research_output"));
            assertNotSame(rig.modelContexts.get(0), rig.tools.get(0).wrapper());
            assertSame(rig.modelContexts.get(0).invocationContext(), rig.tools.get(0).nativeContext());
            assertSame(rig.modelContexts.get(1).invocationContext(), rig.tools.get(1).nativeContext());
            InvocationContext firstRoot = rig.modelContexts.get(0).invocationContext(), child = rig.modelContexts.get(1).invocationContext(), returnedRoot = rig.modelContexts.get(2).invocationContext();
            assertNotSame(firstRoot, child); assertNotSame(child, returnedRoot); assertNotSame(firstRoot, returnedRoot);
            assertSame(firstRoot.session(), child.session()); assertSame(firstRoot.session(), returnedRoot.session());
            assertEquals(firstRoot.invocationId(), child.invocationId()); assertEquals(firstRoot.invocationId(), returnedRoot.invocationId());
            assertSame(rig.root, firstRoot.agent()); assertSame(rig.root, returnedRoot.agent());
            assertSame(rig.root.subAgents().getFirst(), child.agent());
        }
    }

    @Test void callback_failure_rethrows_and_sticky_state_rejects_simulated_later_root() {
        try (var rig = new Rig(conditional(), conditionalScript("quick_research", "root-out", "child-back", SUMMARY))) {
            rig.beforeNativeFailure = () -> rig.gate.markFatal(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled source ledger failure"));
            var failure = assertThrows(AgentLedgerException.class, rig::run);
            assertSame(rig.injectedFailure.get(), failure);
            assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, failure.code());
            assertEquals(1, rig.toolErrors.get()); assertEquals(1, rig.emptyToolErrorCompletions.get()); assertNull(rig.toolErrorOutput.get());
            assertSticky(rig, AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED);
        }
    }

    @Test void unknown_authorization_append_failure_is_sanitized_and_remains_sticky() {
        try (var rig = new Rig(conditional(), conditionalScript("quick_research", "root-out", "child-back", SUMMARY))) {
            rig.authorizationFailure = () -> new IllegalStateException(PRIVATE_FAILURE);
            var failure = assertThrows(AgentLedgerException.class, rig::run);
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, failure.code());
            assertNotSame(rig.injectedFailure.get(), failure); assertNull(failure.getCause()); assertFalse(failure.getMessage().contains(PRIVATE_FAILURE));
            assertEquals(1, rig.toolErrors.get()); assertEquals(1, rig.emptyToolErrorCompletions.get());
            assertNull(rig.toolErrorOutput.get()); assertTrue(rig.audits.isEmpty());
            assertSticky(rig, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
        }
    }

    private static void assertConditional(Rig rig, List<Event> events, String child, String summary) {
        assertEquals(Map.of("conditional_root", FINAL, child, summary), rig.gate.requireCompletedOutputs());
        assertEquals(List.of(child, "conditional_root"), rig.confirmedTargets);
        assertEquals(2, rig.audits.size());
        assertEquals(List.of("conditional_root", child), rig.audits.stream().map(AuthorizationWriteVO::author).toList());
        Event handoff = transferEvent(events, child);
        assertFalse(handoff.finalResponse()); assertEquals(summary, visibleText(handoff));
        assertEquals("conditional_root", handoff.content().orElseThrow().parts().orElseThrow().stream().map(Part::functionCall).flatMap(Optional::stream)
                .findFirst().orElseThrow().args().orElseThrow().get("agent_name"));
        assertTrue(events.stream().anyMatch(event -> event.author().equals("conditional_root") && event.finalResponse() && visibleText(event).equals(FINAL)));
        assertFalse(rig.stateAt(handoff).sessionState().containsKey(child + "_output"));
        assertFalse(rig.stateAt(handoff).stateDelta().containsKey(child + "_output"));
        assertEquals(FINAL, rig.latestSession().state().get(child + "_output"));
        assertEquals(FINAL, rig.latestSession().state().get("conditional_root_output"));
        assertTrue(rig.model.steps.isEmpty());
    }

    private static void assertActualSdkIds(Rig rig) {
        assertEquals(2, rig.tools.size());
        for (var tool : rig.tools) {
            assertNotNull(tool.callId()); assertFalse(tool.callId().isBlank()); assertEquals(tool.callId(), tool.wrapper().functionCallId().orElseThrow());
            assertInstanceOf(FunctionTool.class, tool.tool());
        }
    }

    private static void assertSticky(Rig rig, AgentErrorCodeEnum expected) {
        assertEquals(1, rig.modelContexts.size());
        assertThrows(AgentLedgerException.class, () -> rig.gate.requireExpectedAgent(rig.modelContexts.getFirst()));
        assertThrows(AgentLedgerException.class, rig.gate::requireCompletedOutputs);
        var first = rig.modelContexts.getFirst();
        Event plausible = Event.builder().id("plausible-later-root").invocationId(first.invocationId()).author(rig.spec.rootAgentName())
                .content(Content.fromParts(Part.fromText("plausible later root result"))).actions(EventActions.builder().build()).build();
        var projector = new AdkEventProjector();
        assertThrows(AgentLedgerException.class, () -> rig.gate.observeEvent(plausible, projector.project(plausible, rig.agentNames)));
        var control = rig.gate.failureSignal().test();
        control.assertError(error -> error instanceof AgentLedgerException ledger && ledger.code() == expected);
    }

    private static Event transferEvent(List<Event> events, String author) {
        return events.stream().filter(event -> event.author().equals(author) && event.content().flatMap(Content::parts).orElse(List.of()).stream().anyMatch(part -> part.functionCall().isPresent()))
                .findFirst().orElseThrow();
    }
    private static String visibleText(Event event) {
        return event.content().flatMap(Content::parts).orElse(List.of()).stream().filter(part -> !part.thought().orElse(false))
                .map(Part::text).flatMap(Optional::stream).reduce("", String::concat);
    }
    private static ScriptStepVO transfer(String source, String target, String id, String summary) {
        var parts = new ArrayList<Part>(); if (summary != null) parts.add(Part.fromText(summary)); parts.add(call(target, id));
        return new ScriptStepVO(source, response(parts, false));
    }
    private static Part call(String target, String id) {
        var call = FunctionCall.builder().name("transfer_to_agent").args(Map.of("agent_name", target)); if (id != null) call.id(id);
        return Part.builder().functionCall(call.build()).build();
    }
    private static ScriptStepVO finalResponse(String author, String text) { return new ScriptStepVO(author, response(List.of(Part.fromText(text)), true)); }
    private static LlmResponse response(List<Part> parts, boolean turnComplete) {
        return LlmResponse.builder().content(Content.builder().role("model").parts(parts).build()).partial(false).turnComplete(turnComplete).build();
    }
    private static List<ScriptStepVO> conditionalScript(String child, String firstId, String secondId, String summary) {
        return List.of(transfer("conditional_root", child, firstId, null), transfer(child, "conditional_root", secondId, summary), finalResponse("conditional_root", FINAL));
    }
    private static NativeAgentCompilerStrategy.TemplateSpec conditional() {
        return new NativeAgentCompilerStrategy.TemplateSpec("conditional-research-v1", NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                "fixtureModel", MODEL, ROUTE, "1", "1", "conditional_root", List.of(
                new NativeAgentCompilerStrategy.AgentSpec("conditional_root", null, "conditional_root_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec("quick_research", "conditional_root", "quick_research_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec("deep_research", "conditional_root", "deep_research_output", false, true)), 2, 2, List.of("quick_research", "deep_research"));
    }
    private static NativeAgentCompilerStrategy.TemplateSpec supervisor() {
        return new NativeAgentCompilerStrategy.TemplateSpec("supervisor-research-v1", NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                "fixtureModel", MODEL, ROUTE, "1", "1", "supervisor", List.of(
                new NativeAgentCompilerStrategy.AgentSpec("supervisor", null, "supervisor_root_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec("researcher", "supervisor", "researcher_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec("critic", "supervisor", "critic_output", false, true)), 2, 4, List.of("researcher", "critic"));
    }

    private record ScriptStepVO(String author, LlmResponse response) { }
    private record AuthorizationWriteVO(String author, Map<String, Object> payload) { }
    private record ToolObservationVO(String source, String callId, InvocationContext nativeContext, ToolContext wrapper, BaseTool tool) { }
    private record EventStateVO(Event event, Map<String, Object> sessionState, Map<String, Object> stateDelta) { }

    private static final class ScriptModel extends BaseLlm {
        final Deque<ScriptStepVO> steps;
        final List<LlmRequest> requests = new CopyOnWriteArrayList<>();
        Supplier<String> currentAuthor;
        ScriptModel(List<ScriptStepVO> steps) { super(MODEL); this.steps = new ArrayDeque<>(steps); }
        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            return Flowable.defer(() -> {
                ScriptStepVO next; synchronized (steps) { next = steps.pollFirst(); }
                assertNotNull(next, "native model made an unplanned extra call"); assertEquals(next.author(), currentAuthor.get());
                requests.add(request); return Flowable.just(next.response());
            });
        }
        @Override public BaseLlmConnection connect(LlmRequest request) { throw new UnsupportedOperationException("Live mode is outside this fixture"); }
    }

    private static final class Rig implements AutoCloseable {
        final AgentLedgerService ledger = mock(AgentLedgerService.class);
        final AgentBudgetService budgets = mock(AgentBudgetService.class);
        final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
        final NativeAgentCompilerStrategy.TemplateSpec spec;
        final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        final String runId = UUID.randomUUID().toString(), app = "tianji_" + runId.replace('-', '_'), user = "20:USER:alice";
        final Instant deadline = Instant.now().plusSeconds(60);
        final AgentRunLease lease = new AgentRunLease(runId, "native-worker", 1, deadline);
        final ScheduledExecutorService control = Executors.newScheduledThreadPool(1, task -> Thread.ofPlatform().daemon().unstarted(task));
        final InMemorySessionService sessions = new InMemorySessionService();
        final List<CallbackContext> modelContexts = new CopyOnWriteArrayList<>();
        final List<Optional<String>> providerIds = new CopyOnWriteArrayList<>();
        final List<ToolObservationVO> tools = new CopyOnWriteArrayList<>();
        final List<String> confirmedTargets = new CopyOnWriteArrayList<>();
        final List<AuthorizationWriteVO> audits = new CopyOnWriteArrayList<>();
        final List<EventStateVO> eventStates = new CopyOnWriteArrayList<>();
        final AtomicInteger toolErrors = new AtomicInteger(), emptyToolErrorCompletions = new AtomicInteger();
        final AtomicReference<Throwable> toolErrorOutput = new AtomicReference<>();
        final AtomicReference<RuntimeException> injectedFailure = new AtomicReference<>();
        final ScriptModel model;
        final LlmAgent root;
        final NativeAgentGate gate;
        final AdkRunBudgetPlugin plugin;
        final Runner runner;
        final String sessionId;
        final Set<String> agentNames;
        volatile Supplier<RuntimeException> beforeNativeFailure;
        volatile Supplier<RuntimeException> authorizationFailure;

        Rig(NativeAgentCompilerStrategy.TemplateSpec spec, List<ScriptStepVO> steps) {
            this.spec = spec; model = new ScriptModel(steps);
            var children = spec.agents().stream().filter(agent -> agent.parentName() != null).map(agent -> agent(agent, List.of())).toList();
            root = agent(spec.agents().stream().filter(agent -> agent.parentName() == null).findFirst().orElseThrow(), children);
            agentNames = spec.agents().stream().map(NativeAgentCompilerStrategy.AgentSpec::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
            gate = NativeAgentGate.bind(spec, root, spec.topologyHash(POLICY), (author, payload) -> {
                var fail = authorizationFailure;
                if (fail != null) { RuntimeException failure = fail.get(); injectedFailure.set(failure); throw failure; }
                audits.add(new AuthorizationWriteVO(author, Map.copyOf(payload)));
            });
            var policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofSeconds(5), 12, 0, 10000, 10000, null, null, 1, Set.of(), null, null);
            var definition = new AgentDefinitionSpecDTO("native-agent", 1, "CODE_FIRST", spec.codeRef(), "1", "1",
                    new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), ROUTE, POLICY), policy, Map.of());
            var snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("Research", List.of(), Map.of()), null);
            var request = new AgentRunAuthorization.Request(actor, runId, "native-agent", 1, HASH, HASH, HASH, POLICY, false, deadline);
            when(ledger.budgets()).thenReturn(budgets); when(ledger.databaseNow()).thenAnswer(call -> Instant.now());
            when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> {
                AgentBudgetRequest budget = call.getArgument(2);
                return new AgentBudgetReservation(budget.reservationId(), 1, budget.kind(), budget.toolVersion(), budget.inputTokens(), budget.outputTokens(), budget.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
            });
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any())).thenAnswer(call -> {
                AgentBudgetSettlement actual = call.getArgument(3);
                return new AgentBudgetReservation(call.getArgument(2), 1, AgentBudgetRequest.Kind.MODEL, null, 10, 32, BigDecimal.ZERO,
                        AgentBudgetReservation.State.SETTLED, actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false);
            });
            when(estimator.estimate(any(), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 32, BigDecimal.ZERO));
            when(estimator.contextWindow(any(), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow(MODEL, ROUTE, POLICY, 1000, 10));
            when(estimator.actualUsage(any(), any())).thenReturn(new AgentBudgetSettlement(10L, 3L, BigDecimal.ZERO));
            plugin = spy(nativePlugin(snapshot, request));
            observeCallbacks(); model.currentAuthor = () -> modelContexts.getLast().agentName();
            runner = Runner.builder().agent(root).appName(app).sessionService(sessions).plugins(List.of(plugin)).build();
            sessionId = sessions.createSession(app, user).blockingGet().id();
        }
        private AdkRunBudgetPlugin nativePlugin(AgentExecutionSnapshot snapshot, AgentRunAuthorization.Request request) {
            Constructor<AdkRunBudgetPlugin> constructor = assertDoesNotThrow(() -> AdkRunBudgetPlugin.class.getDeclaredConstructor(
                    AgentLedgerService.class, TrustedAgentActor.class, AgentRunLease.class, AgentExecutionSnapshot.class,
                    AgentModelBudgetEstimator.class, AgentExecutionAuthorization.class, Scheduler.class, ScheduledExecutorService.class,
                    DefaultContextAssembler.class, AgentRunAuthorization.Request.class, AgentContextRuntimeOptions.class,
                    AdkGovernedToolBridge.Scope.class, NativeAgentGate.class));
            return assertDoesNotThrow(() -> constructor.newInstance(ledger, actor, lease, snapshot, estimator,
                    new AgentExecutionAuthorization((authorization, boundary) -> AgentRunAuthorization.Decision.ALLOW, request),
                    Schedulers.from(control), control, new DefaultContextAssembler(), request,
                    new AgentContextRuntimeOptions(false, false), null, gate));
        }
        private LlmAgent agent(NativeAgentCompilerStrategy.AgentSpec agent, List<LlmAgent> children) {
            return LlmAgent.builder().name(agent.name()).model(model).instruction("Fixed role: " + agent.name()).outputKey(agent.outputKey())
                    .disallowTransferToParent(agent.disallowTransferToParent()).disallowTransferToPeers(agent.disallowTransferToPeers()).subAgents(children).build();
        }
        @SuppressWarnings("unchecked")
        private void observeCallbacks() {
            doAnswer(call -> { modelContexts.add(call.getArgument(0)); return call.callRealMethod(); }).when(plugin).beforeModelCallback(any(), any());
            doAnswer(call -> {
                LlmResponse response = call.getArgument(1);
                response.content().flatMap(Content::parts).orElse(List.of()).stream().map(Part::functionCall).flatMap(Optional::stream).forEach(function -> providerIds.add(function.id()));
                return call.callRealMethod();
            }).when(plugin).afterModelCallback(any(), any());
            doAnswer(call -> {
                ToolContext context = call.getArgument(2); BaseTool tool = call.getArgument(0);
                tools.add(new ToolObservationVO(context.agentName(), context.functionCallId().orElseThrow(), context.invocationContext(), context, tool));
                Maybe<Map<String, Object>> result = (Maybe<Map<String, Object>>) call.callRealMethod();
                return result.switchIfEmpty(Maybe.defer(() -> {
                    var fail = beforeNativeFailure; if (fail == null) return Maybe.empty();
                    RuntimeException failure = fail.get(); injectedFailure.set(failure); return Maybe.error(failure);
                }));
            }).when(plugin).beforeToolCallback(any(), anyMap(), any());
            doAnswer(call -> {
                ToolContext context = call.getArgument(2);
                confirmedTargets.add(context.eventActions().transferToAgent().orElseThrow());
                return call.callRealMethod();
            }).when(plugin).afterToolCallback(any(), anyMap(), any(), anyMap());
            doAnswer(call -> {
                toolErrors.incrementAndGet(); Maybe<Map<String, Object>> result = (Maybe<Map<String, Object>>) call.callRealMethod();
                return result.doOnComplete(emptyToolErrorCompletions::incrementAndGet).doOnError(toolErrorOutput::set);
            }).when(plugin).onToolErrorCallback(any(), anyMap(), any(), any());
        }
        List<String> modelAuthors() { return modelContexts.stream().map(CallbackContext::agentName).toList(); }
        Session latestSession() { return sessions.getSession(app, user, sessionId, Optional.empty()).blockingGet(); }
        EventStateVO stateAt(Event event) { return eventStates.stream().filter(state -> state.event() == event).findFirst().orElseThrow(); }
        List<Event> run() {
            var projector = new AdkEventProjector();
            return runner.runAsync(user, sessionId, Content.fromParts(Part.fromText("Research")), RunConfig.builder().setAutoCreateSession(false).setMaxLlmCalls(12)
                            .setStreamingMode(RunConfig.StreamingMode.NONE).build())
                    .doOnNext(event -> {
                        gate.observeEvent(event, projector.project(event, agentNames));
                        eventStates.add(new EventStateVO(event, Map.copyOf(latestSession().state()), Map.copyOf(event.actions().stateDelta())));
                    })
                    .toList().timeout(5, TimeUnit.SECONDS).blockingGet();
        }
        @Override public void close() {
            try { runner.close().blockingAwait(); }
            finally { control.shutdownNow(); }
        }
    }
}
