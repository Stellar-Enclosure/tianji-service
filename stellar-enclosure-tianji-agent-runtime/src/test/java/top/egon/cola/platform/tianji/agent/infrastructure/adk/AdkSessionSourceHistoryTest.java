package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.events.EventCompaction;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentContextRoleEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkSessionSourceHistoryTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final String HASH = "a".repeat(64);
    private final TrustedAgentActor actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
    private final AgentRunLease lease = new AgentRunLease("run", "worker", 1, NOW.plusSeconds(60));
    private final ExecutionPolicyDTO policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100, BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
    private final AgentDefinitionSpecDTO definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of());
    private final AgentExecutionSnapshot snapshot = new AgentExecutionSnapshot(definition, HASH, policy, new AgentInputDTO("current", null, null), null);
    private final AgentRunAuthorization.Request authorization = new AgentRunAuthorization.Request(actor, "run", "assistant", 1, HASH, HASH, HASH, "policy", true, NOW.plusSeconds(60));
    private final AgentContextSourceDTO source = new AgentContextSourceDTO(AgentContextSourceKindEnum.MEMORY, AgentContextRoleEnum.USER, "memory:fact", "1", "opaque-memory-reference", HASH, HASH, 10, NOW);
    private final AgentLedgerService ledger = mock(AgentLedgerService.class);
    private final AgentBudgetService budgets = mock(AgentBudgetService.class);
    private final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
    private final ContextProvider provider = mock(ContextProvider.class);
    private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform().daemon().unstarted(task));
    private final CallbackContext callback = mock(CallbackContext.class);
    private AdkRunBudgetPlugin plugin;

    @BeforeEach void setup() {
        when(ledger.databaseNow()).thenReturn(NOW); when(ledger.budgets()).thenReturn(budgets);
        when(ledger.sessionContextHistory(eq(actor), eq(lease), eq(authorization), eq("session"), any())).thenReturn(history(true, false, true));
        when(provider.historicalKinds()).thenReturn(Set.of(ContextProvider.Kind.MEMORY)); when(provider.provide(any())).thenReturn(List.of());
        when(estimator.estimate(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(20, 10, BigDecimal.ONE));
        when(estimator.contextWindow(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("model", "route", "policy", 100, 5));
        when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> { AgentBudgetRequest request = call.getArgument(2); return new AgentBudgetReservation(request.reservationId(), 1,
                request.kind(), null, request.inputTokens(), request.outputTokens(), request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true); });
        when(callback.eventId()).thenReturn("new-call"); when(callback.sessionId()).thenReturn("session"); when(callback.events()).thenReturn(List.of(modelEvent("old-invocation")));
        plugin = plugin(new DefaultContextAssembler(List.of(provider)));
    }
    @AfterEach void close() { if (plugin != null) plugin.close().blockingAwait(); control.shutdownNow(); }

    @Test void historical_sources_are_checked_with_current_authorization_before_new_sources_estimation_or_budget() {
        assertNull(plugin.beforeModelCallback(callback, request()).blockingGet());
        var order = inOrder(ledger, provider, estimator, budgets); order.verify(ledger).sessionContextHistory(eq(actor), eq(lease), eq(authorization), eq("session"), any());
        order.verify(provider).revalidateHistory(argThat(value -> value.historyValidated() && value.authorization().equals(authorization)), eq(List.of(source)));
        order.verify(provider).provide(argThat(ContextProvider.Request::historyValidated)); order.verify(estimator).estimate(eq(snapshot), any()); order.verify(budgets).reserveBudget(eq(actor), eq(lease), any());
        doThrow(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "current source revoked")).when(provider).revalidateHistory(any(), anyList());
        assertEquals(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        verify(estimator, times(1)).estimate(eq(snapshot), any()); verify(budgets, times(1)).reserveBudget(eq(actor), eq(lease), any());
    }

    @Test void disabling_the_provider_does_not_allow_old_source_derived_history_to_bypass_revalidation() {
        plugin.close().blockingAwait(); plugin = plugin(new DefaultContextAssembler());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        verifyNoInteractions(estimator, budgets);
    }

    @Test void unknown_coverage_shared_state_compaction_and_untracked_events_fail_before_model_budget() {
        for (var history : List.of(history(false, false, true), history(true, true, true), history(false, false, false))) {
            doReturn(history).when(ledger).sessionContextHistory(any(), any(), any(), anyString(), any());
            assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        }
        doReturn(history(true, false, true)).when(ledger).sessionContextHistory(any(), any(), any(), anyString(), any());
        doReturn(List.of(modelEvent("unknown-invocation"))).when(callback).events();
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        var compacted = modelEvent("old-invocation").toBuilder().actions(EventActions.builder().compaction(EventCompaction.builder().startTimestamp(1).endTimestamp(2)
                .compactedContent(Content.fromParts(Part.fromText("old summary"))).build()).build()).build();
        doReturn(List.of(compacted)).when(callback).events();
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        verifyNoInteractions(estimator, budgets); verify(provider, never()).provide(any());
    }

    @Test void legacy_plain_sessions_keep_their_existing_behavior_without_external_sources() {
        plugin.close().blockingAwait(); plugin = plugin(new DefaultContextAssembler());
        doReturn(new AgentLedgerService.ContextHistory(List.of(), Set.of(), false, false, false)).when(ledger).sessionContextHistory(any(), any(), any(), anyString(), any());
        assertNull(plugin.beforeModelCallback(callback, request()).blockingGet()); verify(budgets).reserveBudget(eq(actor), eq(lease), any());
    }

    @Test void a_missing_or_lost_session_fence_cannot_reuse_history_or_reserve_a_model_call() {
        plugin.close().blockingAwait();
        plugin = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, estimator, new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                Schedulers.from(control), control, new DefaultContextAssembler(List.of(provider)), authorization);
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        var checks = new java.util.concurrent.atomic.AtomicInteger();
        plugin.bindSessionFence(() -> { if (checks.incrementAndGet() == 2) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "controlled Session lease loss"); });
        assertEquals(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, assertThrows(AgentLedgerException.class, () -> plugin.beforeModelCallback(callback, request()).blockingGet()).code());
        assertEquals(2, checks.get()); verifyNoInteractions(budgets);
    }

    private AgentLedgerService.ContextHistory history(boolean complete, boolean shared, boolean tracked) { return new AgentLedgerService.ContextHistory(List.of(source), Set.of("old-invocation"), complete, shared, tracked); }
    private AdkRunBudgetPlugin plugin(DefaultContextAssembler assembler) {
        var plugin = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, estimator, new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization), Schedulers.from(control), control, assembler, authorization);
        plugin.bindSessionFence(() -> { }); return plugin;
    }
    private static Event modelEvent(String invocation) { return Event.builder().id("old-event").invocationId(invocation).author("assistant").content(Content.builder().role("model").parts(List.of(Part.fromText("old derived answer"))).build()).build(); }
    private static LlmRequest.Builder request() { return LlmRequest.builder().model("model").contents(List.of(Content.builder().role("user").parts(List.of(Part.fromText("current"))).build()))
            .config(GenerateContentConfig.builder().maxOutputTokens(10).build()); }
}
