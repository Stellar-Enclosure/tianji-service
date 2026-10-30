package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.application.context.ContextProvider;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdkContextWindowPlanTest {
    private final AgentExecutionSnapshot snapshot = snapshot();
    private final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
    private final AgentModelBudgetEstimator.ContextWindow window = new AgentModelBudgetEstimator.ContextWindow("model", "route", "policy", 8, 1);
    private final AgentModelBudgetEstimator.ContextPolicy policy = new AgentModelBudgetEstimator.ContextPolicy("policy", 8, 1, true, true);

    @Test
    void lower_priority_evidence_is_removed_before_old_history_and_required_instructions_remain_unchanged() {
        var original = request(List.of(text("user", "old"), text("model", "answer"), text("user", "current")));
        var high = source("high"); var low = source("low");
        var candidate = original.toBuilder().contents(List.of(original.contents().get(0), original.contents().get(1), original.contents().get(2),
                text("user", AdkRunBudgetPlugin.sourceText(high) + AdkRunBudgetPlugin.sourceText(low))));
        setup(policy);
        when(estimator.estimate(eq(snapshot), any())).thenAnswer(call -> {
            String content = allText(call.getArgument(1)); return estimate(content.contains("low") ? 8 : 5);
        });
        var result = new AdkContextWindowPlan(original, List.of(high, low), policy).fit(candidate, snapshot, estimator, window, estimate(8), 1);
        assertEquals(List.of(high), result.sources()); assertEquals(1, result.decisions().size());
        assertEquals("low", result.decisions().getFirst().sourceId());
        assertTrue(allText(candidate.build()).contains("old")); assertTrue(allText(candidate.build()).contains("current"));
        assertEquals(original.config().orElseThrow().systemInstruction(), candidate.build().config().orElseThrow().systemInstruction());
        assertNotEquals(result.decisions().getFirst().beforeSha256(), result.decisions().getFirst().afterSha256());
    }

    @Test
    void an_old_tool_turn_is_removed_whole_while_the_current_task_and_its_tool_response_are_retained() {
        var original = request(List.of(text("user", "old"), call("old-call"), response("old-call"), text("model", "old answer"),
                text("user", "current"), call("current-call"), response("current-call")));
        var candidate = original.toBuilder(); setup(policy);
        when(estimator.estimate(eq(snapshot), any())).thenAnswer(call -> estimate(((LlmRequest) call.getArgument(1)).contents().size()));
        var result = new AdkContextWindowPlan(original, List.of(), policy).fit(candidate, snapshot, estimator, window, estimate(7), 1);
        assertEquals(3, candidate.build().contents().size()); assertEquals("current", allText(candidate.build()));
        assertEquals("current-call", candidate.build().contents().get(1).parts().orElseThrow().getFirst().functionCall().orElseThrow().id().orElseThrow());
        assertEquals("current-call", candidate.build().contents().get(2).parts().orElseThrow().getFirst().functionResponse().orElseThrow().id().orElseThrow());
        assertEquals(4, result.decisions().getFirst().droppedItems());
        assertEquals("OLD_COMPLETE_TURN", result.decisions().getFirst().reason());
        assertEquals(7, original.contents().size());
    }

    @Test
    void unmatched_tools_and_required_context_fail_closed_instead_of_leaving_orphan_results() {
        var original = request(List.of(text("user", "old"), call("one"), response("other"), text("user", "current")));
        setup(policy);
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> new AdkContextWindowPlan(original, List.of(), policy).fit(original.toBuilder(), snapshot, estimator, window, estimate(20), 1)).code());
        var currentOnly = request(List.of(text("user", "current"), call("one"), response("one")));
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> new AdkContextWindowPlan(currentOnly, List.of(), policy).fit(currentOnly.toBuilder(), snapshot, estimator, window, estimate(20), 1)).code());
    }

    @Test
    void tool_results_with_commentary_are_not_mistaken_for_a_new_user_turn_and_options_require_audit_capture() {
        assertThrows(IllegalArgumentException.class, () -> new AgentContextRuntimeOptions(false, true));
        var commentary = Content.builder().role("user").parts(List.of(Part.fromText("tool commentary"),
                Part.builder().functionResponse(FunctionResponse.builder().id("old").name("lookup").response(Map.of("ok", true)).build()).build())).build();
        var original = request(List.of(text("user", "old user"), call("old"), commentary, text("user", "current")));
        setup(policy); when(estimator.estimate(eq(snapshot), any())).thenAnswer(call -> estimate(((LlmRequest) call.getArgument(1)).contents().size()));
        var small = new AgentModelBudgetEstimator.ContextWindow("model", "route", "policy", 3, 1);
        when(estimator.contextWindow(eq(snapshot), any())).thenReturn(small);
        var candidate = original.toBuilder();
        new AdkContextWindowPlan(original, List.of(), policy).fit(candidate, snapshot, estimator, small, estimate(4), 1);
        assertEquals(List.of(original.contents().getLast()), candidate.build().contents());
    }

    @Test
    void step_limits_and_policy_or_window_drift_are_checked_after_each_full_request_estimate() {
        var original = request(List.of(text("user", "current")));
        var candidate = original.toBuilder().contents(List.of(original.contents().getFirst(), text("user", "evidence")));
        var limited = new AgentModelBudgetEstimator.ContextPolicy("policy", 1, 1, true, false); setup(limited);
        when(estimator.estimate(eq(snapshot), any())).thenReturn(estimate(20));
        assertEquals(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED, assertThrows(AgentLedgerException.class,
                () -> new AdkContextWindowPlan(original, List.of(source("first"), source("last")), limited)
                        .fit(candidate, snapshot, estimator, window, estimate(20), 1)).code());
        setup(policy); when(estimator.estimate(eq(snapshot), any())).thenReturn(estimate(1));
        when(estimator.contextWindow(eq(snapshot), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow("model", "route", "policy", 9, 1));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> new AdkContextWindowPlan(original, List.of(source("last")), policy)
                        .fit(original.toBuilder(), snapshot, estimator, window, estimate(20), 1)).code());
    }

    private void setup(AgentModelBudgetEstimator.ContextPolicy selected) {
        when(estimator.contextPolicy(eq(snapshot), any())).thenReturn(selected);
        when(estimator.contextWindow(eq(snapshot), any())).thenReturn(window);
    }
    private static AgentModelBudgetEstimator.Estimate estimate(long tokens) { return new AgentModelBudgetEstimator.Estimate(tokens, 1, new BigDecimal("0.1")); }
    private static LlmRequest request(List<Content> contents) {
        return LlmRequest.builder().model("model").contents(contents)
                .config(GenerateContentConfig.builder().systemInstruction(Content.fromParts(Part.fromText("required system"))).build()).build();
    }
    private static Content text(String role, String text) { return Content.builder().role(role).parts(List.of(Part.fromText(text))).build(); }
    private static Content call(String id) { return Content.builder().role("model").parts(List.of(Part.builder().functionCall(FunctionCall.builder().id(id).name("lookup").args(Map.of()).build()).build())).build(); }
    private static Content response(String id) { return Content.builder().role("user").parts(List.of(Part.builder().functionResponse(FunctionResponse.builder().id(id).name("lookup").response(Map.of("ok", true)).build()).build())).build(); }
    private static String allText(LlmRequest request) { return request.contents().stream().flatMap(content -> content.parts().orElse(List.of()).stream()).map(part -> part.text().orElse("")).reduce("", (a, b) -> a + b); }
    private static ContextProvider.Fragment source(String id) { return new ContextProvider.Fragment(ContextProvider.Kind.RAG, id, "1", "d".repeat(64), "document", id, Instant.EPOCH); }
    private static AgentExecutionSnapshot snapshot() {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 1000, 100,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        return new AgentExecutionSnapshot(new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "route", "policy"), policy, Map.of()), "a".repeat(64), policy, new AgentInputDTO("current", null, null), null);
    }
}
