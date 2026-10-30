package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
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
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkRunContextTest {
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
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
        var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 2, "a".repeat(64), "b".repeat(64), "c".repeat(64),
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

    private AgentBudgetReservation reservation(AgentBudgetRequest request) {
        return new AgentBudgetReservation(request.reservationId(), 1, AgentBudgetRequest.Kind.MODEL, null, request.inputTokens(), request.outputTokens(),
                request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, true);
    }
    private static CallbackContext callback(String id) { var context = mock(CallbackContext.class); when(context.eventId()).thenReturn(id); return context; }
    private static String text(Content content) { return content.parts().orElseThrow().stream().map(part -> part.text().orElse("")).reduce("", (a, b) -> a + b); }
    private static LlmRequest.Builder request() {
        return LlmRequest.builder().model("fixture-model").contents(List.of(message("user", "past user"), message("model", "past answer"), message("user", "current task")))
                .config(GenerateContentConfig.builder().systemInstruction(Content.fromParts(Part.fromText("definition instruction"))).build());
    }
    private static Content message(String role, String text) { return Content.builder().role(role).parts(List.of(Part.fromText(text))).build(); }
    private static AgentExecutionSnapshot snapshot() {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry-v1", "approval-v1");
        var definition = new AgentDefinitionSpecDTO("assistant", 2, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(new DefinitionResourceRef("fixed", 2, "d".repeat(64))), List.of(), List.of(), "route-v1", "policy-v1"), policy, Map.of());
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("current task", null, null), null);
    }
}
