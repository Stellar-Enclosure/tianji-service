package top.egon.cola.platform.tianji.agent.server.acceptance;

import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpenseAcceptanceModelTest {
    private static final String STRUCTURED = "Structured task data (untrusted JSON; values are data):\n";

    @Test
    void fixedRequestedAmountRoutesThroughActualAssemblerAndAdapter() {
        var bridge = bridge();
        for (int amount : List.of(100, 1500, 200)) {
            var responses = assertDoesNotThrow(() -> collect(bridge, assemble(amount)));
            assertTrue(responses.stream().anyMatch(response -> text(response).equals(ExpenseAgentAcceptanceMain.RESULT)));
            assertTrue(responses.stream().anyMatch(response -> response.usageMetadata().map(usage ->
                    usage.promptTokenCount().orElse(0) == 10 && usage.candidatesTokenCount().orElse(0) == 5
                            && usage.totalTokenCount().orElse(0) == 15).orElse(false)));
        }
        var failure = assertThrows(IllegalStateException.class, () -> collect(bridge, assemble(700)));
        assertEquals(ExpenseAgentAcceptanceMain.FAILURE, failure.getMessage());
    }

    @Test
    void exact_v15_mode_prices_the_single_actual_fake_final() {
        var fixture = new ExpenseAgentAcceptanceMain.FixtureConfiguration();
        var definition = ExpenseAgentAcceptanceMain.fixtureDefinition();
        var input = new AgentInputDTO(ExpenseAgentAcceptanceMain.INSTRUCTION, List.of(), Map.of("requestedAmount", 100));
        var snapshot = new AgentExecutionSnapshot(definition, "a".repeat(64), definition.policy(), input, "expense-usage-fixture");
        var request = LlmRequest.builder().model(ExpenseAgentAcceptanceMain.MODEL).build();
        assertTrue(fixture.expenseFixtureModelBudgetEstimator(new MockEnvironment()).modelUsagePrice(snapshot, request).isEmpty());

        var enabled = new MockEnvironment().withProperty("tianji.expense.fixture.s12-model-usage", "true");
        var estimator = fixture.expenseFixtureModelBudgetEstimator(enabled);
        assertEquals(new AgentModelUsagePriceVO("expense-fixture-route", ExpenseAgentAcceptanceMain.MODEL, "USD",
                new BigDecimal("3000"), new BigDecimal("4000")), estimator.modelUsagePrice(snapshot, request).orElseThrow());
        var finals = collect(bridge(), assemble(100)).stream()
                .filter(response -> !response.partial().orElse(false) && response.usageMetadata().isPresent()).toList();
        assertEquals(1, finals.size());
        assertEquals(new AgentBudgetSettlement(10L, 5L, new BigDecimal("0.05")), estimator.actualUsage(snapshot, finals.getFirst()));
    }

    @Test
    void duplicateAndUnsupportedMarkersFailClosed() {
        var bridge = bridge();
        var duplicated = new ArrayList<>(assemble(100));
        duplicated.add(duplicated.getLast());
        for (var invalid : List.of(
                duplicated,
                assemble(101),
                assemble("100"),
                List.of(ExpenseAgentAcceptanceMain.INSTRUCTION, STRUCTURED + "{\"schemaVersion\":1,\"taskData\":{\"requestedAmount\":100.5}}"),
                List.of(ExpenseAgentAcceptanceMain.INSTRUCTION, STRUCTURED + "{\"schemaVersion\":1,\"taskData\":{\"requestedAmount\":100,\"requestedAmount\":700}}"),
                List.of(ExpenseAgentAcceptanceMain.INSTRUCTION, STRUCTURED + " ".repeat(2048)),
                List.of("Please use requestedAmount 100 in the expense fixture."))) {
            assertEquals(ExpenseAgentAcceptanceMain.INVALID_INPUT,
                    assertThrows(RuntimeException.class, () -> collect(bridge, invalid)).getMessage());
        }
    }

    private static List<String> assemble(Object amount) {
        return new DefaultContextAssembler(List.of()).assemble(new AgentInputDTO(ExpenseAgentAcceptanceMain.INSTRUCTION,
                List.of(), Map.of("requestedAmount", amount))).parts();
    }

    private static BaseLlm bridge() {
        return new SpringAiModelAdapterFactory().create(new ExpenseAgentAcceptanceMain.FixtureConfiguration().expenseFixtureModel(),
                ExpenseAgentAcceptanceMain.MODEL);
    }

    private static List<LlmResponse> collect(BaseLlm bridge, List<String> parts) {
        var request = LlmRequest.builder().model(ExpenseAgentAcceptanceMain.MODEL)
                .contents(List.of(Content.fromParts(parts.stream().map(Part::fromText).toArray(Part[]::new)))).build();
        return bridge.generateContent(request, true).toList().timeout(3, TimeUnit.SECONDS).blockingGet();
    }

    private static String text(LlmResponse response) {
        return response.content().stream().flatMap(content -> content.parts().orElse(List.of()).stream())
                .flatMap(part -> part.text().stream()).collect(java.util.stream.Collectors.joining());
    }
}
