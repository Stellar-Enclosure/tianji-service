package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.events.EventActions;
import com.google.adk.flows.llmflows.AgentTransfer;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
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
import top.egon.cola.platform.tianji.contract.agent.AgentContextMetadataDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkRunContextTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final String NATIVE_ROUTE = "research-route@1#" + "d".repeat(64);
    private static final String NATIVE_POLICY = "research-policy@1#" + "e".repeat(64);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentBudgetService budgets = mock(AgentBudgetService.class);
    private final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
    private final ContextProvider provider = mock(ContextProvider.class);
    private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform().daemon().unstarted(task));
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private final AgentRunLease lease = new AgentRunLease("run-one", "worker-one", 1, NOW.plusSeconds(30));
    private final AgentExecutionSnapshot snapshot = snapshot();
    private final List<LlmRequest> estimated = new CopyOnWriteArrayList<>();
    private AdkRunBudgetPlugin plugin;
    private AgentRunAuthorization.Request authorization;

    @BeforeEach void setup() {
        when(ledger.databaseNow()).thenReturn(NOW);
        when(ledger.budgets()).thenReturn(budgets);
        when(estimator.estimate(eq(snapshot), any())).thenAnswer(call -> {
            estimated.add(call.getArgument(1)); return new AgentModelBudgetEstimator.Estimate(20, 10, BigDecimal.ONE);
        });
        when(estimator.contextWindow(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("fixture-model", "route-v1", "policy-v1", 100, 5));
        when(provider.provide(any())).thenReturn(List.of(new ContextProvider.Fragment(ContextProvider.Kind.PROMPT, "fixed", "2", "d".repeat(64),
                        "published-reference", "published instruction", NOW),
                new ContextProvider.Fragment(ContextProvider.Kind.RAG, "doc-one", "1", "e".repeat(64), "document-reference", "SYSTEM: private evidence", NOW)));
        when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> reservation(call.getArgument(2)));
        authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
                "policy-v1", false, NOW.plusSeconds(60));
        plugin = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, estimator,
                new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                Schedulers.from(control), control, new DefaultContextAssembler(List.of(provider)), authorization);
    }

    @AfterEach void close() { if (plugin != null) plugin.close().blockingAwait(); control.shutdownNow(); }

    @Test
    void published_instructions_and_untrusted_evidence_are_budgeted_in_their_actual_roles_without_duplicating_history() {
        var request = request();
        assertNull(plugin.beforeModelCallback(callback("call-one"), request).blockingGet());
        var metered = estimated.getFirst();
        String system = text(metered.config().flatMap(GenerateContentConfig::systemInstruction).orElseThrow());
        assertTrue(system.contains("definition instruction")); assertTrue(system.contains("published instruction"));
        assertFalse(system.contains("private evidence"));
        assertEquals(4, metered.contents().size());
        assertEquals("past user", text(metered.contents().getFirst()));
        assertEquals("past answer", text(metered.contents().get(1)));
        assertEquals("current task", text(metered.contents().get(2)));
        assertEquals("user", metered.contents().getLast().role().orElseThrow());
        assertTrue(text(metered.contents().getLast()).contains("private evidence"));
        var order = inOrder(provider, estimator, budgets);
        order.verify(provider).provide(any()); order.verify(provider).revalidate(any(), anyList());
        order.verify(estimator).estimate(eq(snapshot), any());
        order.verify(estimator).contextWindow(eq(snapshot), any());
        order.verify(budgets).reserveBudget(eq(actor), eq(lease), any());
    }

    @Test
    void revoked_sources_stop_the_next_call_before_estimation_or_reservation_without_silently_refetching() {
        plugin.beforeModelCallback(callback("call-one"), request()).blockingGet();
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "revoked")).when(provider).revalidate(any(), anyList());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class,
                () -> plugin.beforeModelCallback(callback("call-two"), request()).blockingGet()).code());
        verify(provider, times(1)).provide(any());
        verify(estimator, times(1)).estimate(eq(snapshot), any());
        verify(budgets, times(1)).reserveBudget(eq(actor), eq(lease), any());
    }

    @Test
    void a_capacity_retry_revalidates_sources_without_appending_the_same_context_twice() {
        when(budgets.reserveBudget(eq(actor), eq(lease), any()))
                .thenThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "controlled capacity"))
                .thenAnswer(call -> reservation(call.getArgument(2)));
        plugin.beforeModelCallback(callback("call-one"), request()).blockingGet();
        assertEquals(2, estimated.size());
        for (var request : estimated) {
            assertEquals(4, request.contents().size());
            var system = request.config().flatMap(GenerateContentConfig::systemInstruction).orElseThrow();
            assertEquals(1, system.parts().orElseThrow().stream().filter(part -> part.text().orElse("").equals("published instruction")).count());
        }
        verify(provider, times(1)).provide(any()); verify(provider, times(2)).revalidate(any(), anyList());
    }

    @Test
    void enabled_capture_uses_the_atomic_budget_method_with_the_actual_output_limit_and_stable_retry_metadata() {
        snapshots();
        when(provider.policyFingerprint(any())).thenReturn("f".repeat(64));
        when(estimator.modelAdapterFingerprint(eq(snapshot), any())).thenReturn("e".repeat(64));
        var captured = new CopyOnWriteArrayList<AgentContextMetadataDTO>();
        when(budgets.reserveBudget(eq(actor), eq(lease), any(), any())).thenAnswer(call -> {
            captured.add(call.getArgument(3));
            if (captured.size() == 1) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "controlled capacity");
            return reservation(call.getArgument(2));
        });
        var request = request();
        plugin.beforeModelCallback(callback("call-one"), request).blockingGet();
        assertEquals(2, captured.size()); assertEquals(captured.getFirst(), captured.getLast());
        assertEquals(10, request.build().config().orElseThrow().maxOutputTokens().orElseThrow());
        assertEquals(10, captured.getLast().effectiveOutputLimit());
        assertEquals(actor.identityFingerprint(), captured.getLast().ownerSha256());
        assertEquals("call-one", captured.getLast().reservationId());
        verify(budgets, never()).reserveBudget(any(), any(), any());
    }

    @Test
    void capture_rejects_a_missing_versioned_adapter_identity_before_reserving_any_budget() {
        snapshots();
        when(provider.policyFingerprint(any())).thenReturn("f".repeat(64));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> plugin.beforeModelCallback(callback("call-one"), request()).blockingGet()).code());
        verifyNoInteractions(budgets);
    }

    @Test
    void trimming_records_actual_selection_and_capacity_retries_do_not_restore_removed_evidence_or_history() {
        plugin.close().blockingAwait();
        plugin = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, estimator,
                new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                Schedulers.from(control), control, new DefaultContextAssembler(List.of(provider)), authorization, new AgentContextRuntimeOptions(true, true));
        var policy = new AgentModelBudgetEstimator.ContextPolicy("policy-v1", 8, 1, true, true);
        when(estimator.contextPolicy(eq(snapshot), any())).thenReturn(policy);
        when(provider.policyFingerprint(any())).thenReturn("f".repeat(64));
        when(estimator.modelAdapterFingerprint(eq(snapshot), any())).thenReturn("e".repeat(64));
        when(estimator.estimate(eq(snapshot), any())).thenAnswer(call ->
                new AgentModelBudgetEstimator.Estimate(((LlmRequest) call.getArgument(1)).contents().size() * 30L, 10, BigDecimal.ONE));
        var captured = new CopyOnWriteArrayList<AgentContextMetadataDTO>();
        when(budgets.reserveBudget(eq(actor), eq(lease), any(), any())).thenAnswer(call -> {
            captured.add(call.getArgument(3));
            if (captured.size() == 1) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "controlled capacity");
            return reservation(call.getArgument(2));
        });
        var request = request(); plugin.beforeModelCallback(callback("call-one"), request).blockingGet();
        assertEquals(1, request.build().contents().size()); assertEquals("current task", text(request.build().contents().getFirst()));
        assertEquals(2, captured.size()); assertEquals(captured.getFirst(), captured.getLast());
        assertEquals(2, captured.getLast().trims().size()); assertEquals(policy.fingerprint(), captured.getLast().trimmingPolicySha256());
        assertTrue(captured.getLast().sources().stream().noneMatch(source -> source.kind() == top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum.RAG));
        assertTrue(text(request.build().config().flatMap(GenerateContentConfig::systemInstruction).orElseThrow()).contains("published instruction"));
    }

    private void snapshots() {
        plugin.close().blockingAwait();
        plugin = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, estimator,
                new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                Schedulers.from(control), control, new DefaultContextAssembler(List.of(provider)), authorization, true);
    }

    @Test
    void tool_handoff_uses_the_admitted_leaf_identity_across_distinct_callback_wrappers_on_context_io() {
        var scope = tools(); var nativeContext = nativeContext("writer", false);
        var thread = new AtomicReference<Thread>();
        var tool = scope.declaredTools().getFirst();
        when(scope.execute(eq(tool), anyMap(), any(), eq("writer"))).thenAnswer(call -> {
            thread.set(Thread.currentThread()); return Map.of("status", "SUCCEEDED", "output", Map.of("value", "done"));
        });
        plugin.beforeModelCallback(modelContext(nativeContext, "call-one"), request()).blockingGet();
        var result = plugin.beforeToolCallback(tool, Map.of("amount", 7L), toolContext(nativeContext)).blockingGet();
        assertEquals("SUCCEEDED", result.get("status")); assertNotSame(Thread.currentThread(), thread.get());
        var id = org.mockito.ArgumentCaptor.forClass(java.util.UUID.class);
        verify(scope).execute(eq(tool), eq(Map.of("amount", 7L)), id.capture(), eq("writer"));
        assertNotNull(id.getValue()); assertNotEquals("private-native-call-id", id.getValue().toString());
    }

    @Test
    void an_equal_looking_foreign_context_or_same_name_foreign_tool_never_reaches_the_scope() {
        var scope = tools(); var admitted = nativeContext("writer", false);
        plugin.beforeModelCallback(modelContext(admitted, "call-one"), request()).blockingGet();
        BaseTool tool = scope.declaredTools().getFirst(); var foreign = nativeContext("writer", false);
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(tool, Map.of(), toolContext(foreign)).blockingGet());
        String toolName = tool.name(); var sameName = mock(BaseTool.class); when(sameName.name()).thenReturn(toolName);
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(sameName, Map.of(), toolContext(admitted)).blockingGet());
        verify(scope, never()).execute(any(), anyMap(), any(), anyString());
    }

    @Test
    void every_leaf_is_admitted_individually_and_close_revokes_all_retained_contexts() {
        var scope = tools(); var first = nativeContext("writer", false); var second = nativeContext("reviewer", false);
        when(scope.execute(any(), anyMap(), any(), anyString())).thenReturn(Map.of("status", "SUCCEEDED"));
        plugin.beforeModelCallback(modelContext(first, "round-one"), request()).blockingGet();
        plugin.beforeModelCallback(modelContext(second, "round-two"), request()).blockingGet();
        plugin.beforeModelCallback(modelContext(first, "round-three"), request()).blockingGet();
        var tool = scope.declaredTools().getFirst();
        assertNotNull(plugin.beforeToolCallback(tool, Map.of(), toolContext(first)).blockingGet());
        assertNotNull(plugin.beforeToolCallback(tool, Map.of(), toolContext(second)).blockingGet());
        plugin.close().blockingAwait();
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(tool, Map.of(), toolContext(first)).blockingGet());
        assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(modelContext(nativeContext("late", false), "after-close"), request()).blockingGet());
        verify(scope, times(2)).execute(any(), anyMap(), any(), anyString());
    }

    @Test
    void native_context_identity_admission_is_bounded_even_when_the_budget_fixture_still_accepts_reservations() {
        var scope = tools();
        for (int index = 0; index < snapshot.effectivePolicy().maxModelCalls(); index++) {
            assertNull(plugin.beforeModelCallback(modelContext(nativeContext("leaf_" + index, false), "call_" + index), request()).blockingGet());
        }
        var overflow = nativeContext("overflow", false);
        assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(modelContext(overflow, "overflow"), request()).blockingGet());
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(scope.declaredTools().getFirst(), Map.of(), toolContext(overflow)).blockingGet());
        verify(budgets, times(snapshot.effectivePolicy().maxModelCalls())).reserveBudget(eq(actor), eq(lease), any());
        verify(scope, never()).execute(any(), anyMap(), any(), anyString());
    }

    @Test
    void failed_model_governance_and_changed_native_session_identity_do_not_admit_tool_calls() {
        var scope = tools(); var denied = nativeContext("writer", false);
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "revoked"))
                .when(provider).revalidate(any(), anyList());
        assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(modelContext(denied, "denied"), request()).blockingGet());
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(scope.declaredTools().getFirst(), Map.of(), toolContext(denied)).blockingGet());
        reset(provider); when(provider.provide(any())).thenReturn(List.of(new ContextProvider.Fragment(ContextProvider.Kind.PROMPT,
                "fixed", "2", "d".repeat(64), "published-reference", "published instruction", NOW)));
        tools();
        assertNull(plugin.beforeModelCallback(modelContext(nativeContext("writer", false), "healthy"), request()).blockingGet());
        clearInvocations(budgets);
        var bad = nativeContext("writer", false); when(bad.appName()).thenReturn("foreign-app");
        assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(modelContext(bad, "foreign"), request()).blockingGet());
        verifyNoInteractions(budgets);
        verify(scope, never()).execute(any(), anyMap(), any(), anyString());
    }

    @Test
    void durable_session_fencing_is_rechecked_before_tool_dispatch_after_model_admission() {
        authorization = new AgentRunAuthorization.Request(actor, authorization.runId(), authorization.definitionKey(), authorization.definitionVersion(),
                authorization.definitionSha256(), authorization.requestSha256(), authorization.snapshotSha256(), authorization.policyVersionRef(), true, authorization.deadlineAt());
        var scope = tools(); var live = new AtomicBoolean(true);
        when(ledger.sessionContextHistory(eq(actor), eq(lease), eq(authorization), eq("session"), any()))
                .thenReturn(new AgentLedgerService.ContextHistory(List.of(), Set.of(), true, false, true));
        plugin.bindSessionFence(() -> { if (!live.get()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "fenced"); });
        var context = nativeContext("writer", true);
        plugin.beforeModelCallback(modelContext(context, "call-one"), request()).blockingGet(); live.set(false);
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class,
                () -> plugin.beforeToolCallback(scope.declaredTools().getFirst(), Map.of(), toolContext(context)).blockingGet()).code());
        verify(scope, never()).execute(any(), anyMap(), any(), anyString());
    }

    @Test
    void native_gate_tracks_without_tool_scope_and_preserves_old_constructor_surface() {
        var fixture = nativeFixture(false, (author, payload) -> { });
        NativeAgentGate gate = spy(fixture.gate());
        var authorizer = allowingAuthorization();
        installNativePlugin(gate, authorizer, authorization);

        assertDoesNotThrow(() -> AdkRunBudgetPlugin.class.getDeclaredConstructor(
                AgentLedgerService.class, TrustedAgentActor.class, AgentRunLease.class,
                AgentExecutionSnapshot.class, AgentModelBudgetEstimator.class, AgentExecutionAuthorization.class,
                Scheduler.class, ScheduledExecutorService.class, DefaultContextAssembler.class,
                AgentRunAuthorization.Request.class, AgentContextRuntimeOptions.class, AdkGovernedToolBridge.Scope.class));
        CallbackContext context = nativeCallback(fixture.rootContext(), "native-root");
        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(context, request()).blockingGet()));
        verify(budgets).reserveBudget(eq(actor), eq(lease), any());
        verify(gate, times(2)).requireExpectedAgent(same(context));
    }

    @Test
    void known_unexpected_agent_fails_before_authorization_estimation_or_reservation() {
        var fixture = nativeFixture(false, (author, payload) -> { });
        var authorizer = allowingAuthorization();
        installNativePlugin(fixture.gate(), authorizer, authorization);
        clearInvocations(authorizer, estimator, budgets);
        InvocationContext earlyChild = fixture.rootContext().toBuilder().agent(fixture.tree().deep()).build();

        assertThrows(AgentLedgerException.class,
                () -> plugin.beforeModelCallback(nativeCallback(earlyChild, "early-child"), request()).blockingGet());
        verify(authorizer, never()).authorize(any(), eq(AgentRunAuthorization.Boundary.MODEL));
        verifyNoInteractions(estimator, budgets);
    }

    @Test
    void confirmed_return_to_root_allows_distinct_per_agent_contexts_but_not_equal_looking_foreign_identity()
            throws Exception {
        prepareNativeSettlement();
        var fixture = nativeFixture(false, (author, payload) -> { });
        installNativePlugin(fixture.gate(), allowingAuthorization(), authorization);
        InvocationContext root = fixture.rootContext();
        InvocationContext child = root.toBuilder().agent(fixture.tree().quick()).build();
        InvocationContext returnedRoot = child.toBuilder().agent(fixture.tree().root()).build();

        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(
                nativeCallback(root, "root-model"), request()).blockingGet()));
        nativeTransfer(root, "root-model", "quick_research", "root-call", null);
        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(
                nativeCallback(child, "child-model"), request()).blockingGet()));
        nativeTransfer(child, "child-model", "conditional_root", "child-call", "current summary");
        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(
                nativeCallback(returnedRoot, "root-return"), request()).blockingGet()));

        InvocationContext copiedChild = child.toBuilder().build();
        assertNotSame(child, copiedChild);
        assertThrows(AgentLedgerException.class, () -> plugin.beforeToolCallback(transferTool(),
                Map.of("agent_name", "conditional_root"), nativeToolContext(copiedChild, "foreign-call")).blockingGet());
    }

    @Test
    void complete_native_response_is_observed_only_after_durable_settlement() {
        prepareNativeSettlement();
        var fixture = nativeFixture(false, (author, payload) -> { });
        NativeAgentGate gate = spy(fixture.gate());
        installNativePlugin(gate, allowingAuthorization(), authorization);
        CallbackContext context = nativeCallback(fixture.rootContext(), "root-model");
        LlmResponse response = nativeTransferResponse("quick_research", "root-call", null);
        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(context, request()).blockingGet()));
        clearInvocations(budgets, gate);

        assertNull(assertDoesNotThrow(() -> plugin.afterModelCallback(context, response).blockingGet()));

        var order = inOrder(budgets, gate);
        order.verify(budgets).settleBudget(eq(actor), eq(lease), eq("root-model"), any());
        order.verify(gate).observeResponse(same(context), same(response));

        var unadmitted = nativeFixture(false, (author, payload) -> { });
        installNativePlugin(unadmitted.gate(), allowingAuthorization(), authorization);
        var partial = LlmResponse.builder().partial(true).turnComplete(false).build();
        assertThrows(AgentLedgerException.class, () -> plugin.afterModelCallback(
                nativeCallback(unadmitted.rootContext(), "unadmitted-partial"), partial).blockingGet());
    }

    @Test
    void native_before_tool_reauthorizes_model_and_refences_session_without_ordinary_tool_accounting()
            throws Exception {
        prepareNativeSettlement();
        authorization = new AgentRunAuthorization.Request(actor, authorization.runId(), authorization.definitionKey(),
                authorization.definitionVersion(), authorization.definitionSha256(), authorization.requestSha256(),
                authorization.snapshotSha256(), authorization.policyVersionRef(), true, authorization.deadlineAt());
        when(ledger.sessionContextHistory(eq(actor), eq(lease), eq(authorization), eq("session"), any()))
                .thenReturn(new AgentLedgerService.ContextHistory(List.of(), Set.of(), true, false, true));
        var audits = new AtomicInteger();
        var fixture = nativeFixture(true, (author, payload) -> audits.incrementAndGet());
        var authorizer = allowingAuthorization();
        installNativePlugin(fixture.gate(), authorizer, authorization);
        var fences = new AtomicInteger();
        plugin.bindSessionFence(fences::incrementAndGet);
        CallbackContext model = nativeCallback(fixture.rootContext(), "root-model");
        assertNull(assertDoesNotThrow(() -> plugin.beforeModelCallback(model, request()).blockingGet()));
        assertNull(assertDoesNotThrow(() -> plugin.afterModelCallback(
                model, nativeTransferResponse("quick_research", "root-call", null)).blockingGet()));

        assertNull(assertDoesNotThrow(() -> plugin.beforeToolCallback(transferTool(),
                Map.of("agent_name", "quick_research"),
                nativeToolContext(fixture.rootContext(), "root-call")).blockingGet()));

        assertEquals(1, audits.get());
        assertEquals(3, fences.get());
        verify(authorizer, times(2)).authorize(eq(authorization), eq(AgentRunAuthorization.Boundary.MODEL));
        assertEquals(0, snapshot.effectivePolicy().maxToolCalls());
    }

    @Test
    void native_error_callbacks_keep_gate_typed_safe_fatal_classification() throws Exception {
        var deniedFixture = nativeFixture(false, (author, payload) -> { });
        installNativePlugin(deniedFixture.gate(), allowingAuthorization(), authorization);
        var denied = new AgentExecutionAuthorization.Failure(AgentRunAuthorization.Decision.DENY);
        assertNull(plugin.onToolErrorCallback(transferTool(), Map.of("agent_name", "quick_research"),
                nativeToolContext(deniedFixture.rootContext(), "denied-call"), denied).blockingGet());
        deniedFixture.gate().failureSignal().test().assertError(error ->
                error instanceof AgentExecutionAuthorization.Failure value
                        && value.decision() == AgentRunAuthorization.Decision.DENY);

        var rawToolFixture = nativeFixture(false, (author, payload) -> { });
        installNativePlugin(rawToolFixture.gate(), allowingAuthorization(), authorization);
        var rawToolFailure = assertThrows(AgentLedgerException.class, () -> plugin.onToolErrorCallback(
                transferTool(), Map.of("agent_name", "quick_research"),
                nativeToolContext(rawToolFixture.rootContext(), "unknown-tool-call"),
                new IllegalStateException("private-tool-sentinel")).blockingGet());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, rawToolFailure.code());
        assertNull(rawToolFailure.getCause());
        assertFalse(rawToolFailure.getMessage().contains("private-tool-sentinel"));
        rawToolFixture.gate().failureSignal().test().assertError(error ->
                error instanceof AgentLedgerException value
                        && value.code() == AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE
                        && value.getCause() == null
                        && !value.getMessage().contains("private-tool-sentinel"));

        var unknownFixture = nativeFixture(false, (author, payload) -> { });
        installNativePlugin(unknownFixture.gate(), allowingAuthorization(), authorization);
        var response = plugin.onModelErrorCallback(nativeCallback(unknownFixture.rootContext(), "model-error"),
                request(), new IllegalStateException("private-sentinel")).blockingGet();
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(),
                response.errorMessage().orElseThrow());
        assertTrue(response.interrupted().orElseThrow());
        unknownFixture.gate().failureSignal().test().assertError(error ->
                error instanceof AgentLedgerException value
                        && value.code() == AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE
                        && !value.getMessage().contains("private-sentinel"));
    }

    @Test
    void legacy_constructor_keeps_ordinary_tool_scope_execution_unchanged() {
        var scope = tools();
        var context = nativeContext("writer", false);
        var tool = scope.declaredTools().getFirst();
        when(scope.execute(eq(tool), anyMap(), any(), eq("writer"))).thenReturn(Map.of("status", "SUCCEEDED"));

        plugin.beforeModelCallback(modelContext(context, "legacy-model"), request()).blockingGet();
        assertEquals("SUCCEEDED", plugin.beforeToolCallback(tool, Map.of(), toolContext(context))
                .blockingGet().get("status"));
        verify(scope).execute(eq(tool), eq(Map.of()), any(), eq("writer"));
    }

    private AdkReadOnlyDecisionToolBridge.RunScope tools() {
        plugin.close().blockingAwait();
        var scope = mock(AdkReadOnlyDecisionToolBridge.RunScope.class); var tool = mock(BaseTool.class);
        when(tool.name()).thenReturn("fixed_decision"); when(scope.declaredTools()).thenReturn(List.of(tool));
        doAnswer(call -> {
            ((Runnable) call.getArgument(4)).run();
            return scope.execute(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3));
        }).when(scope).execute(any(), anyMap(), any(), anyString(), any(Runnable.class));
        var toolSnapshot = snapshot(2);
        when(estimator.estimate(eq(toolSnapshot), any())).thenAnswer(call -> {
            estimated.add(call.getArgument(1)); return new AgentModelBudgetEstimator.Estimate(20, 10, BigDecimal.ONE);
        });
        when(estimator.contextWindow(eq(toolSnapshot), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("fixture-model", "route-v1", "policy-v1", 100, 5));
        plugin = new AdkRunBudgetPlugin(ledger, actor, lease, toolSnapshot, estimator,
                new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                Schedulers.from(control), control, new DefaultContextAssembler(List.of(provider)), authorization,
                new AgentContextRuntimeOptions(false, false), scope);
        return scope;
    }

    private AdkRunBudgetPlugin installNativePlugin(NativeAgentGate gate, AgentRunAuthorization authorizer,
                                                    AgentRunAuthorization.Request request) {
        plugin.close().blockingAwait();
        Constructor<AdkRunBudgetPlugin> constructor = assertDoesNotThrow(() ->
                AdkRunBudgetPlugin.class.getDeclaredConstructor(
                        AgentLedgerService.class, TrustedAgentActor.class, AgentRunLease.class,
                        AgentExecutionSnapshot.class, AgentModelBudgetEstimator.class,
                        AgentExecutionAuthorization.class, Scheduler.class, ScheduledExecutorService.class,
                        DefaultContextAssembler.class, AgentRunAuthorization.Request.class,
                        AgentContextRuntimeOptions.class, AdkGovernedToolBridge.Scope.class, NativeAgentGate.class));
        plugin = assertDoesNotThrow(() -> constructor.newInstance(ledger, actor, lease, snapshot, estimator,
                new AgentExecutionAuthorization(authorizer, request), Schedulers.from(control), control,
                new DefaultContextAssembler(List.of(provider)), request,
                new AgentContextRuntimeOptions(false, false), null, gate));
        return plugin;
    }

    private AgentRunAuthorization allowingAuthorization() {
        var authorizer = mock(AgentRunAuthorization.class);
        when(authorizer.authorize(any(), any())).thenReturn(AgentRunAuthorization.Decision.ALLOW);
        return authorizer;
    }

    private void prepareNativeSettlement() {
        when(estimator.actualUsage(eq(snapshot), any())).thenReturn(
                new AgentBudgetSettlement(20L, 5L, BigDecimal.ONE));
        when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any())).thenAnswer(call ->
                new AgentBudgetReservation(call.getArgument(2), 1, AgentBudgetRequest.Kind.MODEL,
                        null, 20, 10, BigDecimal.ONE, AgentBudgetReservation.State.SETTLED,
                        20L, 5L, BigDecimal.ONE, false));
    }

    private void nativeTransfer(InvocationContext context, String eventId, String target,
                                String callId, String branchText) throws Exception {
        assertDoesNotThrow(() -> {
            plugin.afterModelCallback(nativeCallback(context, eventId),
                    nativeTransferResponse(target, callId, branchText)).blockingGet();
            ToolContext toolContext = nativeToolContext(context, callId);
            plugin.beforeToolCallback(transferTool(), Map.of("agent_name", target), toolContext).blockingGet();
            AgentTransfer.transferToAgent(target, toolContext);
            plugin.afterToolCallback(transferTool(), Map.of("agent_name", target), toolContext, Map.of()).blockingGet();
        });
    }

    private NativeFixture nativeFixture(boolean durable, NativeAgentGate.AuthorizationAppender appender) {
        var model = new ScriptedLlm("fixture-model");
        var quick = LlmAgent.builder().name("quick_research").model(model).outputKey("quick_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var deep = LlmAgent.builder().name("deep_research").model(model).outputKey("deep_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var root = LlmAgent.builder().name("conditional_root").model(model).outputKey("conditional_root_output")
                .subAgents(quick, deep).disallowTransferToParent(true).disallowTransferToPeers(true).build();
        var tree = new NativeTree(root, quick, deep);
        var spec = nativeSpec();
        var gate = NativeAgentGate.bind(spec, root, spec.topologyHash(NATIVE_POLICY), appender);
        String app = durable ? "tianji" : "tianji_run_one";
        String user = durable ? "USER:alice" : "20:USER:alice";
        Session session = Session.builder("session").appName(app).userId(user).state(Map.of()).build();
        InvocationContext context = InvocationContext.builder().sessionService(mock(BaseSessionService.class))
                .agent(root).session(session).invocationId("invocation").build();
        return new NativeFixture(gate, tree, context);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec nativeSpec() {
        return new NativeAgentCompilerStrategy.TemplateSpec("conditional-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                "researchModel", "fixture-model", NATIVE_ROUTE, "1", "1", "conditional_root",
                List.of(
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "conditional_root", null, "conditional_root_output", true, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "quick_research", "conditional_root", "quick_research_output", false, true),
                        new NativeAgentCompilerStrategy.AgentSpec(
                                "deep_research", "conditional_root", "deep_research_output", false, true)),
                2, 2, List.of("quick_research", "deep_research"));
    }

    private InvocationContext nativeContext(String name, boolean durable) {
        var context = mock(InvocationContext.class);
        String app = durable ? "tianji" : "tianji_run_one";
        String user = durable ? "USER:alice" : "20:USER:alice";
        var session = Session.builder("session").appName(app).userId(user).state(Map.of()).build();
        when(context.appName()).thenReturn(app); when(context.userId()).thenReturn(user);
        when(context.session()).thenReturn(session); when(context.invocationId()).thenReturn("invocation");
        var agent = mock(LlmAgent.class); when(agent.name()).thenReturn(name); when(context.agent()).thenReturn(agent);
        return context;
    }

    private static CallbackContext modelContext(InvocationContext nativeContext, String eventId) {
        String name = nativeContext.agent().name(); String user = nativeContext.userId();
        var context = callback(eventId); when(context.invocationContext()).thenReturn(nativeContext);
        when(context.agentName()).thenReturn(name); when(context.userId()).thenReturn(user);
        return context;
    }

    private static ToolContext toolContext(InvocationContext nativeContext) {
        String name = nativeContext.agent().name(); String invocation = nativeContext.invocationId();
        String user = nativeContext.userId(); String session = nativeContext.session().id();
        var context = mock(ToolContext.class); when(context.invocationContext()).thenReturn(nativeContext);
        when(context.agentName()).thenReturn(name); when(context.invocationId()).thenReturn(invocation);
        when(context.userId()).thenReturn(user); when(context.sessionId()).thenReturn(session);
        when(context.functionCallId()).thenReturn(java.util.Optional.of("private-native-call-id")); return context;
    }

    private static CallbackContext nativeCallback(InvocationContext context, String eventId) {
        return new CallbackContext(context, EventActions.builder().build(), eventId);
    }

    private static ToolContext nativeToolContext(InvocationContext context, String callId) {
        return ToolContext.builder(context).actions(EventActions.builder().build())
                .functionCallId(callId).eventId("tool-" + callId).build();
    }

    private static FunctionTool transferTool() throws Exception {
        return FunctionTool.create(AgentTransfer.class.getMethod(
                "transferToAgent", String.class, ToolContext.class));
    }

    private static LlmResponse nativeTransferResponse(String target, String callId, String text) {
        var call = FunctionCall.builder().name("transfer_to_agent").args(Map.of("agent_name", target));
        if (callId != null) call.id(callId);
        List<Part> parts = new ArrayList<>();
        if (text != null) parts.add(Part.fromText(text));
        parts.add(Part.builder().functionCall(call.build()).build());
        return LlmResponse.builder().content(Content.builder().role("model").parts(parts).build())
                .partial(false).turnComplete(false).build();
    }

    private AgentBudgetReservation reservation(AgentBudgetRequest request) {
        return new AgentBudgetReservation(request.reservationId(), 1, AgentBudgetRequest.Kind.MODEL, null, request.inputTokens(), request.outputTokens(),
                request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
    }
    private static CallbackContext callback(String id) {
        var context = mock(CallbackContext.class); when(context.eventId()).thenReturn(id);
        when(context.agentName()).thenReturn("writer"); when(context.sessionId()).thenReturn("session");
        when(context.invocationId()).thenReturn("invocation"); when(context.events()).thenReturn(List.of());
        return context;
    }
    private static String text(Content content) { return content.parts().orElseThrow().stream().map(part -> part.text().orElse("")).reduce("", (a, b) -> a + b); }
    private static LlmRequest.Builder request() {
        return LlmRequest.builder().model("fixture-model").contents(List.of(message("user", "past user"), message("model", "past answer"), message("user", "current task")))
                .config(GenerateContentConfig.builder().systemInstruction(Content.fromParts(Part.fromText("definition instruction"))).build());
    }
    private static Content message(String role, String text) { return Content.builder().role(role).parts(List.of(Part.fromText(text))).build(); }
    private static AgentExecutionSnapshot snapshot() { return snapshot(0); }
    private static AgentExecutionSnapshot snapshot(int toolCalls) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, toolCalls, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(new DefinitionResourceRef("fixed", 2, "d".repeat(64))), List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("current task", null, null), null);
    }

    private record NativeTree(LlmAgent root, LlmAgent quick, LlmAgent deep) { }
    private record NativeFixture(NativeAgentGate gate, NativeTree tree, InvocationContext rootContext) { }

    private static final class ScriptedLlm extends BaseLlm {
        private ScriptedLlm(String model) { super(model); }
        @Override public Flowable<LlmResponse> generateContent(LlmRequest request, boolean stream) {
            return Flowable.never();
        }
        @Override public BaseLlmConnection connect(LlmRequest request) {
            throw new UnsupportedOperationException();
        }
    }
}
