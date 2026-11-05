package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import top.egon.cola.platform.tianji.agent.autoconfigure.ModelBudgetProperties;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService.ModelUsageFinalVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService.ModelUsageSeedVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentBudgetService.ModelUsageUnknownReason;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentModelUsageCaptureTest {
    private static final String MODEL = "gpt-4o-mini";
    private static final String FIXTURE_ROUTE = "expense-route@1#" + "a".repeat(64);
    private static final String PRICE_GOLDEN = "1382ec7bcc847c290eb476b5c067c8e386293d5e0ad17d7870e1b999ce0a1092";

    @Test
    void boundJtokkitTariffIsExactAndStable() {
        ChatModel model = model();
        var estimator = estimator(model, new BigDecimal("0.50000000"));
        var descriptor = estimator.descriptors().getFirst();
        var snapshot = config(descriptor, descriptor.modelRouteRef(), descriptor.policyVersionRef(), "USD");
        var request = request(MODEL);

        var frozen = estimator.modelUsagePrice(snapshot, request);
        assertTrue(frozen.isPresent(), "An exact current CONFIG binding supplies a frozen tariff");
        var price = frozen.orElseThrow();
        var window = estimator.contextWindow(snapshot, request);
        assertEquals(window.modelRouteRef(), price.routeRef());
        assertEquals(window.modelName(), price.modelName());
        assertEquals("USD", price.currency());
        assertEquals(new BigDecimal("0.5"), price.inputRatePerMillion());
        assertEquals(new BigDecimal("2"), price.outputRatePerMillion());
        assertEquals(price, estimator.modelUsagePrice(snapshot, request).orElseThrow());
        assertEquals(price.priceVersionRef(), estimator.modelUsagePrice(snapshot, request).orElseThrow().priceVersionRef());

        var changed = estimator(model, new BigDecimal("0.51"));
        var changedDescriptor = changed.descriptors().getFirst();
        var changedSnapshot = config(changedDescriptor, changedDescriptor.modelRouteRef(), changedDescriptor.policyVersionRef(), "USD");
        var changedPrice = changed.modelUsagePrice(changedSnapshot, request).orElseThrow();
        assertEquals(price.routeRef(), changedPrice.routeRef());
        assertNotEquals(price.priceVersionRef(), changedPrice.priceVersionRef());
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
    }

    @Test
    void tariffVersionUsesExactIdentityAndCanonicalRateValues() {
        var price = price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.50000000"), new BigDecimal("2.0000"));
        var same = price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.5"), new BigDecimal("2"));
        assertEquals(same, price);
        assertEquals(PRICE_GOLDEN, price.priceVersionRef());
        assertEquals(PRICE_GOLDEN, same.priceVersionRef());
        for (var other : List.of(
                price(FIXTURE_ROUTE + "-different", MODEL, "USD", new BigDecimal("0.5"), new BigDecimal("2")),
                price(FIXTURE_ROUTE, "gpt-4o", "USD", new BigDecimal("0.5"), new BigDecimal("2")),
                price(FIXTURE_ROUTE, MODEL, "CNY", new BigDecimal("0.5"), new BigDecimal("2")),
                price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.6"), new BigDecimal("2")),
                price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.5"), new BigDecimal("3")))) {
            assertNotEquals(PRICE_GOLDEN, other.priceVersionRef());
        }

        var profileBoundary = new BigDecimal("999999999999999999E+6");
        var frozen = price(FIXTURE_ROUTE, MODEL, "USD", profileBoundary, new BigDecimal("0E-8"));
        var databaseRoundTrip = price(FIXTURE_ROUTE, MODEL, "USD", profileBoundary.setScale(8), BigDecimal.ZERO);
        assertEquals(frozen, databaseRoundTrip);
        assertEquals(frozen.priceVersionRef(), databaseRoundTrip.priceVersionRef());
        assertEquals(BigDecimal.ZERO, databaseRoundTrip.outputRatePerMillion());
    }

    @Test
    void rejectsMissingUnsafeOrUnrepresentableTariffFacts() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> price("route\nref", MODEL, "USD", BigDecimal.ONE, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, "\uD800", "USD", BigDecimal.ONE, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price("r".repeat(257), MODEL, "USD", BigDecimal.ONE, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, "m".repeat(513), "USD", BigDecimal.ONE, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "usd", BigDecimal.ONE, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "USD", null, BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "USD", BigDecimal.ONE, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("-0.01"), BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.000000001"), BigDecimal.ONE)),
                () -> assertThrows(IllegalArgumentException.class, () -> price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("1E+24"), BigDecimal.ONE)));
    }

    @Test
    void existingBindingStillFencesTariffAndOtherEstimatorPathsHaveNoPrice() {
        var options = new AtomicReference<>(options(0.2));
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenAnswer(call -> options.get());
        var estimator = estimator(model, BigDecimal.ONE);
        var descriptor = estimator.descriptors().getFirst();
        var snapshot = config(descriptor, descriptor.modelRouteRef(), descriptor.policyVersionRef(), "USD");
        var request = request(MODEL);
        assertTrue(estimator.modelUsagePrice(snapshot, request).isPresent());

        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> estimator.modelUsagePrice(config(descriptor, FIXTURE_ROUTE, descriptor.policyVersionRef(), "USD"), request)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> estimator.modelUsagePrice(config(descriptor, descriptor.modelRouteRef(), "other-policy", "USD"), request)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> estimator.modelUsagePrice(config(descriptor, descriptor.modelRouteRef(), descriptor.policyVersionRef(), "CNY"), request)).code());
        assertEquals(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, assertThrows(AgentLedgerException.class,
                () -> estimator.modelUsagePrice(snapshot, request("gpt-4o"))).code());

        var nativeSnapshot = snapshot(descriptor.modelRouteRef(), descriptor.policyVersionRef(), "USD", "CODE_FIRST", "fixture-native",
                Map.of());
        var nativeBinding = new AgentModelBudgetEstimator.NativeModelBinding("fixture-native", "modelBean", MODEL, model);
        assertTrue(estimator.bindNative(nativeSnapshot, nativeBinding).modelUsagePrice(nativeSnapshot, request).isEmpty());
        var legacy = new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot source, LlmRequest input) { throw new AssertionError(); }
            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot source, LlmResponse input) { throw new AssertionError(); }
        };
        assertTrue(legacy.modelUsagePrice(snapshot, request).isEmpty());

        options.set(options(0.8));
        assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, assertThrows(AgentLedgerException.class,
                () -> estimator.modelUsagePrice(snapshot, request)).code());
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
    }

    private static AgentModelUsagePriceVO price(String route, String model, String currency, BigDecimal input, BigDecimal output) {
        return new AgentModelUsagePriceVO(route, model, currency, input, output);
    }

    private static JTokkitChatModelBudgetEstimator estimator(ChatModel model, BigDecimal inputRate) {
        var profile = JTokkitChatModelBudgetEstimatorTest.profile(MODEL, inputRate);
        return new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile)), Map.of("modelBean", model));
    }

    private static AgentExecutionSnapshot config(JTokkitChatModelBudgetEstimator.Descriptor descriptor, String route,
                                                 String policyVersion, String currency) {
        return snapshot(route, policyVersion, currency, "CONFIG", null,
                Map.of("modelName", descriptor.modelName(), "chatModelBeanName", descriptor.modelBeanName()));
    }

    private static AgentExecutionSnapshot snapshot(String route, String policyVersion, String currency, String mode,
                                                    String codeRef, Map<String, Object> configuration) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0,
                10_000, 1_000, BigDecimal.ONE, currency, 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, mode, codeRef, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), route, policyVersion), policy, configuration);
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("fixture", null, null), null);
    }

    private static ChatModel model() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(options(0.2));
        return model;
    }

    private static ChatOptions options(double temperature) {
        return ChatOptions.builder().model(MODEL).maxTokens(32).temperature(temperature).build();
    }

    private static LlmRequest request(String model) {
        return LlmRequest.builder().model(model).build();
    }
    @Test
    void onlyAdmittedConfigFinalAddsUsage() {
        try (var fixture = new CaptureFixture("CONFIG", true)) {
            fixture.before("exact-call");
            var seed = new ModelUsageSeedVO(FIXTURE_ROUTE, MODEL, fixture.price);
            verify(fixture.budgets).reserveBudget(eq(fixture.actor), eq(fixture.lease), any(), isNull(), eq(seed));
            assertNull(fixture.after("exact-call", response(3, 2, 5, false)));
            verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "exact-call", fixture.actual,
                    new ModelUsageFinalVO(FIXTURE_ROUTE, MODEL, 3L, 2L, null));

            fixture.before("bad-total");
            assertNull(fixture.after("bad-total", response(3, 2, 6, false)));
            verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "bad-total", fixture.actual,
                    new ModelUsageFinalVO(FIXTURE_ROUTE, MODEL, null, null, ModelUsageUnknownReason.INVALID_USAGE));

            fixture.before("absent-usage");
            assertNull(fixture.after("absent-usage", LlmResponse.builder().build()));
            verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "absent-usage", fixture.actual,
                    new ModelUsageFinalVO(FIXTURE_ROUTE, MODEL, null, null, ModelUsageUnknownReason.MISSING_USAGE));

            fixture.before("known-error");
            var failure = fixture.after("known-error", LlmResponse.builder().errorMessage("synthetic error").build());
            assertEquals(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(), failure.errorMessage().orElseThrow());
            verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "known-error",
                    new AgentBudgetSettlement(null, null, null),
                    new ModelUsageFinalVO(FIXTURE_ROUTE, MODEL, null, null, ModelUsageUnknownReason.MODEL_ERROR));
        }
        for (String mode : List.of("CONFIG", "CODE_FIRST")) {
            try (var fixture = new CaptureFixture(mode, false)) {
                fixture.before("unpriced-path");
                assertNull(fixture.after("unpriced-path", response(3, 2, 5, false)));
                verify(fixture.budgets).reserveBudget(eq(fixture.actor), eq(fixture.lease), any());
                verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "unpriced-path", fixture.actual);
                verify(fixture.estimator, never()).modelUsagePrice(any(), any());
                verify(fixture.budgets, never()).reserveBudget(any(), any(), any(), any(), any());
                verify(fixture.budgets, never()).settleBudget(any(), any(), anyString(), any(), any());
            }
        }
    }

    @Test
    void partialAndClosedCallbacksDoNotRecord() {
        try (var fixture = new CaptureFixture("CONFIG", true)) {
            fixture.before("closing-call");
            assertNull(fixture.after("closing-call", response(3, 2, 5, true)));
            verify(fixture.budgets, never()).settleBudget(any(), any(), anyString(), any(), any());
            verify(fixture.budgets, never()).settleBudget(any(), any(), anyString(), any());

            fixture.plugin.close().blockingAwait();
            assertNull(fixture.after("closing-call", response(3, 2, 5, false)));
            verify(fixture.budgets, never()).settleBudget(any(), any(), anyString(), any(), any());
            verify(fixture.budgets).settleBudget(fixture.actor, fixture.lease, "closing-call", fixture.actual);
        }
    }

    private static LlmResponse response(int input, int output, int total, boolean partial) {
        return LlmResponse.builder().partial(partial).usageMetadata(GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(input).candidatesTokenCount(output).totalTokenCount(total).build()).build();
    }

    private static final class CaptureFixture implements AutoCloseable {
        private final Instant now = Instant.parse("2026-11-05T05:50:00Z");
        private final TrustedAgentActor actor = TrustedAgentActor.verified(42, TrustedAgentActor.SubjectType.USER, "owner", "https://idp.example/42");
        private final AgentRunLease lease = new AgentRunLease("run-one", "worker-one", 1, now.plusSeconds(30));
        private final AgentLedgerService ledger = mock(AgentLedgerService.class);
        private final AgentBudgetService budgets = mock(AgentBudgetService.class);
        private final AgentModelBudgetEstimator estimator = mock(AgentModelBudgetEstimator.class);
        private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(task -> Thread.ofPlatform().daemon().unstarted(task));
        private final AgentBudgetSettlement actual = new AgentBudgetSettlement(3L, 2L, new BigDecimal("0.1"));
        private final AgentModelUsagePriceVO price = price(FIXTURE_ROUTE, MODEL, "USD", new BigDecimal("0.5"), new BigDecimal("2"));
        private final AdkRunBudgetPlugin plugin;

        private CaptureFixture(String mode, boolean admitted) {
            String code = "CONFIG".equals(mode) ? null : "fixture-native";
            Map<String, Object> configuration = "CONFIG".equals(mode) ? Map.of("modelName", MODEL, "chatModelBeanName", "modelBean") : Map.of();
            var source = snapshot(FIXTURE_ROUTE, "policy-v1", "USD", mode, code, configuration);
            var authorization = new AgentRunAuthorization.Request(actor, "run-one", "assistant", 1,
                    "a".repeat(64), "b".repeat(64), "c".repeat(64), "policy-v1", false, now.plusSeconds(60));
            when(ledger.databaseNow()).thenReturn(now);
            when(ledger.budgets()).thenReturn(budgets);
            when(ledger.acceptsModelUsage("CONFIG", FIXTURE_ROUTE)).thenReturn(admitted);
            when(estimator.estimate(eq(source), any())).thenReturn(new AgentModelBudgetEstimator.Estimate(10, 8, BigDecimal.ONE));
            when(estimator.contextWindow(eq(source), any())).thenReturn(new AgentModelBudgetEstimator.ContextWindow(MODEL, FIXTURE_ROUTE, "policy-v1", 100, 5));
            when(estimator.modelUsagePrice(eq(source), any())).thenReturn(Optional.of(price));
            when(estimator.actualUsage(eq(source), any())).thenReturn(actual);
            when(budgets.reserveBudget(eq(actor), eq(lease), any())).thenAnswer(call -> reservation(call.getArgument(2), true));
            when(budgets.reserveBudget(eq(actor), eq(lease), any(), isNull(), any())).thenAnswer(call -> reservation(call.getArgument(2), true));
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any()))
                    .thenAnswer(call -> settled(call.getArgument(2)));
            when(budgets.settleBudget(eq(actor), eq(lease), anyString(), any(), any()))
                    .thenAnswer(call -> settled(call.getArgument(2)));
            plugin = new AdkRunBudgetPlugin(ledger, actor, lease, source, estimator,
                    new AgentExecutionAuthorization((request, boundary) -> AgentRunAuthorization.Decision.ALLOW, authorization),
                    Schedulers.from(control), control, new DefaultContextAssembler(List.of()), authorization);
        }

        private AgentBudgetReservation reservation(AgentBudgetRequest request, boolean created) {
            return new AgentBudgetReservation(request.reservationId(), lease.epoch(), request.kind(), null, request.inputTokens(),
                    request.outputTokens(), request.estimatedCost(), AgentBudgetReservation.State.RESERVED, null, null, null, created);
        }

        private AgentBudgetReservation settled(String eventId) {
            return new AgentBudgetReservation(eventId, lease.epoch(), AgentBudgetRequest.Kind.MODEL, null, 10, 8,
                    BigDecimal.ONE, AgentBudgetReservation.State.SETTLED, actual.inputTokens(), actual.outputTokens(), actual.actualCost(), false);
        }

        private void before(String id) {
            assertNull(plugin.beforeModelCallback(callback(id), LlmRequest.builder().model(MODEL)).blockingGet());
        }

        private LlmResponse after(String id, LlmResponse response) {
            return plugin.afterModelCallback(callback(id), response).blockingGet();
        }

        private static CallbackContext callback(String id) {
            var context = mock(CallbackContext.class);
            when(context.eventId()).thenReturn(id);
            return context;
        }

        @Override public void close() { plugin.close().blockingAwait(); control.shutdownNow(); }
    }
}
