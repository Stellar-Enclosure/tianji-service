package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import top.egon.cola.platform.tianji.agent.autoconfigure.ModelBudgetProperties;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JTokkitChatModelBudgetEstimatorTest {
    private static final String MODEL = "gpt-4o-mini";

    @Test
    void exact_converter_messages_use_the_selected_encoding_and_content_addressed_route_and_policy() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var descriptor = estimator.descriptors().getFirst(); var snapshot = snapshot(descriptor);
        var request = request("hello world"); var estimate = estimator.estimate(snapshot, request);
        var encoding = Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.O200K_BASE);
        long expected = 16 + 8 + encoding.countTokensOrdinary("user") + encoding.countTokensOrdinary("hello world");
        assertEquals(expected, estimate.inputTokens()); assertEquals(32, estimate.maxOutputTokens());
        assertTrue(descriptor.modelRouteRef().matches("route@1#[0-9a-f]{64}"));
        assertTrue(descriptor.policyVersionRef().matches("policy@1#[0-9a-f]{64}"));
        assertEquals("o200k_base", descriptor.encodingName());
        assertEquals(descriptor.modelRouteRef(), estimator.contextWindow(snapshot, request).modelRouteRef());
        assertEquals(descriptor.policyVersionRef(), estimator.contextPolicy(snapshot, request).policyVersionRef());
        assertEquals(64, estimator.modelAdapterFingerprint(snapshot, request).length());
        assertTrue(estimator.estimate(snapshot, request("中文 <|endoftext|>" )).inputTokens() > 0);
        verify(model, never()).call(any(Prompt.class)); verify(model, never()).stream(any(Prompt.class));
    }

    @Test
    void fixed_tariff_accounting_uses_reported_counts_and_never_invents_missing_usage() {
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model()));
        var snapshot = snapshot(estimator.descriptors().getFirst());
        var usage = LlmResponse.builder().usageMetadata(GenerateContentResponseUsageMetadata.builder().promptTokenCount(100).candidatesTokenCount(50).totalTokenCount(150).build()).build();
        var actual = estimator.actualUsage(snapshot, usage);
        assertEquals(100L, actual.inputTokens()); assertEquals(50L, actual.outputTokens());
        assertEquals(0, new BigDecimal("0.0002").compareTo(actual.actualCost()));
        var missing = estimator.actualUsage(snapshot, LlmResponse.builder().build());
        assertNull(missing.inputTokens()); assertNull(missing.outputTokens()); assertNull(missing.actualCost());
        assertThrows(AgentLedgerException.class, () -> estimator.actualUsage(snapshot, usage.toBuilder()
                .usageMetadata(usage.usageMetadata().orElseThrow().toBuilder().totalTokenCount(151).build()).build()));
    }

    @Test
    void unknown_aliases_changed_defaults_and_reused_version_names_with_different_bodies_fail_closed() {
        assertThrows(AgentLedgerException.class, () -> new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile("unverified-alias", BigDecimal.ONE))), Map.of("modelBean", model())));
        var unspecified = mock(ChatModel.class); when(unspecified.getOptions()).thenReturn(ChatOptions.builder().maxTokens(32).build());
        assertThrows(AgentLedgerException.class, () -> new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", unspecified)));
        assertThrows(AgentLedgerException.class, () -> new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE), profile(MODEL, new BigDecimal("2")))), Map.of("modelBean", model())));
        var options = new AtomicReference<>(ChatOptions.builder().model(MODEL).maxTokens(32).temperature(0.2).build());
        var model = mock(ChatModel.class); when(model.getOptions()).thenAnswer(call -> options.get());
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var snapshot = snapshot(estimator.descriptors().getFirst());
        options.set(ChatOptions.builder().model(MODEL).maxTokens(32).temperature(0.8).build());
        assertThrows(AgentLedgerException.class, () -> estimator.estimate(snapshot, request("hello")));
        verify(model, never()).call(any(Prompt.class)); verify(model, never()).stream(any(Prompt.class));
    }

    @Test
    void converter_output_cap_and_media_rejection_match_the_actual_request() {
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model()));
        var snapshot = snapshot(estimator.descriptors().getFirst());
        var request = request("hello").toBuilder().config(GenerateContentConfig.builder().maxOutputTokens(20).build()).build();
        assertEquals(20, estimator.estimate(snapshot, request).maxOutputTokens());
        var system = request.toBuilder().config(request.config().orElseThrow().toBuilder().systemInstruction(Content.fromParts(Part.fromText("fixed system policy"))).build()).build();
        assertTrue(estimator.estimate(snapshot, system).inputTokens() > estimator.estimate(snapshot, request).inputTokens());
        var media = request.toBuilder().contents(List.of(Content.fromParts(Part.builder().inlineData(Blob.builder().mimeType("image/png").data(new byte[]{1, 2}).build()).build()))).build();
        assertThrows(AgentLedgerException.class, () -> estimator.estimate(snapshot, media));
    }

    @Test
    void openai_options_are_reviewed_without_reading_credentials_headers_or_tool_context() {
        var options = spy(OpenAiChatOptions.builder().model(MODEL).maxRetries(0).maxTokens(32).streamUsage(true).build());
        doThrow(new AssertionError("credential getter must not be inspected")).when(options).getApiKey();
        doThrow(new AssertionError("credential getter must not be inspected")).when(options).getCredential();
        doThrow(new AssertionError("transport headers must not be inspected")).when(options).getCustomHeaders();
        doThrow(new AssertionError("tool execution context must not be inspected")).when(options).getToolContext();
        assertEquals(64, TextModelOptionsView.fingerprint(options).length());
        verify(options, never()).getApiKey(); verify(options, never()).getCredential();
        verify(options, never()).getCustomHeaders(); verify(options, never()).getToolContext();
        assertThrows(AgentLedgerException.class, () -> TextModelOptionsView.fingerprint(OpenAiChatOptions.builder().model(MODEL).maxRetries(1).build()));
        assertThrows(AgentLedgerException.class, () -> TextModelOptionsView.fingerprint(OpenAiChatOptions.builder().model(MODEL).maxRetries(0).maxCompletionTokens(100).build()));
        assertThrows(AgentLedgerException.class, () -> TextModelOptionsView.fingerprint(OpenAiChatOptions.builder().model(MODEL).maxRetries(0).n(2).build()));
        assertThrows(AgentLedgerException.class, () -> TextModelOptionsView.fingerprint(OpenAiChatOptions.builder().model(MODEL).maxRetries(0).extraBody(Map.of("unknown", true)).build()));
    }

    private static ChatModel model() {
        var model = mock(ChatModel.class); when(model.getOptions()).thenReturn(ChatOptions.builder().model(MODEL).maxTokens(32).temperature(0.2).build()); return model;
    }
    static ModelBudgetProperties.Profile profile(String name, BigDecimal inputPrice) {
        return new ModelBudgetProperties.Profile("route", 1, "policy", 1, "modelBean", name, 4096, 64, 128, 8, 16, 32,
                inputPrice, new BigDecimal("2"), "USD", 8, 1, true, true);
    }
    private static LlmRequest request(String text) { return LlmRequest.builder().model(MODEL).contents(List.of(Content.builder().role("user").parts(List.of(Part.fromText(text))).build())).build(); }
    private static AgentExecutionSnapshot snapshot(JTokkitChatModelBudgetEstimator.Descriptor descriptor) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 10000, 1000,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), descriptor.modelRouteRef(), descriptor.policyVersionRef()),
                policy, Map.of("modelName", descriptor.modelName(), "chatModelBeanName", descriptor.modelBeanName()));
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("hello", null, null), null);
    }
}
