package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Blob;
import com.google.genai.types.Part;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import top.egon.cola.platform.tianji.agent.autoconfigure.ModelBudgetProperties;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JTokkitChatModelBudgetEstimatorTest {
    private static final String MODEL = "gpt-4o-mini";
    private static final String CODE_REF = "conditional-research-v1";

    @Test
    void native_budget_binding_contract_is_present() {
        assertDoesNotThrow(() -> {
            Class<?> estimator = Class.forName(AgentModelBudgetEstimator.class.getName());
            Class<?> binding = Class.forName(AgentModelBudgetEstimator.class.getName() + "$NativeModelBinding");
            estimator.getMethod("bindNative", AgentExecutionSnapshot.class, binding);
        });
    }

    @Test
    void native_binding_value_rejects_unsafe_fields_and_redacts_the_model() {
        var model = model();
        var binding = new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AgentModelBudgetEstimator.NativeModelBinding(null, "modelBean", MODEL, model)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "model\nBean", MODEL, model)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", " ", model)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, null)));
        assertFalse(binding.toString().contains(model.toString()));
    }

    @Test
    void custom_estimator_native_binding_defaults_to_feature_unavailable() {
        AgentModelBudgetEstimator custom = new AgentModelBudgetEstimator() {
            @Override public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) { throw new AssertionError(); }
            @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) { throw new AssertionError(); }
        };
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var snapshot = codeFirstSnapshot(estimator.descriptors().getFirst(), CODE_REF, Map.of(), "USD");

        assertLedgerFailure(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                () -> custom.bindNative(snapshot, new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model)));
    }

    @Test
    void exact_native_binding_reuses_all_five_config_calculations() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var descriptor = estimator.descriptors().getFirst();
        var configSnapshot = snapshot(descriptor);
        var nativeSnapshot = codeFirstSnapshot(descriptor, CODE_REF, Map.of(), "USD");
        var bound = assertDoesNotThrow(() -> estimator.bindNative(nativeSnapshot,
                new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model)));
        var request = request("hello world");
        var response = LlmResponse.builder().usageMetadata(GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(100).candidatesTokenCount(50).totalTokenCount(150).build()).build();

        assertAll(
                () -> assertEquals(estimator.estimate(configSnapshot, request), bound.estimate(nativeSnapshot, request)),
                () -> assertEquals(estimator.actualUsage(configSnapshot, response), bound.actualUsage(nativeSnapshot, response)),
                () -> assertEquals(estimator.contextWindow(configSnapshot, request), bound.contextWindow(nativeSnapshot, request)),
                () -> assertEquals(estimator.modelAdapterFingerprint(configSnapshot, request), bound.modelAdapterFingerprint(nativeSnapshot, request)),
                () -> assertEquals(estimator.contextPolicy(configSnapshot, request), bound.contextPolicy(nativeSnapshot, request)));
    }

    @Test
    void native_binding_rejects_metadata_profile_identity_and_current_default_drift() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var descriptor = estimator.descriptors().getFirst();
        var snapshot = codeFirstSnapshot(descriptor, CODE_REF, Map.of(), "USD");
        var exact = new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model);

        assertAll(
                () -> assertBindingUnavailable(estimator, codeFirstSnapshot(descriptor, CODE_REF, Map.of("unexpected", true), "USD"), exact),
                () -> assertBindingUnavailable(estimator, snapshot,
                        new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF + ".other", "modelBean", MODEL, model)),
                () -> assertBindingUnavailable(estimator, snapshot,
                        new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "otherBean", MODEL, model)),
                () -> assertBindingUnavailable(estimator, snapshot,
                        new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", "gpt-4o", model)),
                () -> assertBindingUnavailable(estimator, snapshot,
                        new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model())),
                () -> assertBindingUnavailable(estimator, codeFirstSnapshot(descriptor.modelName(), "route@2#" + "b".repeat(64), descriptor.policyVersionRef(), CODE_REF, Map.of(), "USD"), exact),
                () -> assertBindingUnavailable(estimator, codeFirstSnapshot(descriptor.modelName(), descriptor.modelRouteRef(), "policy@2#" + "b".repeat(64), CODE_REF, Map.of(), "USD"), exact),
                () -> assertBindingUnavailable(estimator, codeFirstSnapshot(descriptor, CODE_REF, Map.of(), "CNY"), exact));

        var options = new AtomicReference<ChatOptions>(ChatOptions.builder().model(MODEL).maxTokens(32).temperature(0.2).build());
        var changingModel = mock(ChatModel.class); when(changingModel.getOptions()).thenAnswer(call -> options.get());
        var changingEstimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", changingModel));
        var changingDescriptor = changingEstimator.descriptors().getFirst();
        var changingSnapshot = codeFirstSnapshot(changingDescriptor, CODE_REF, Map.of(), "USD");
        var changingBinding = new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, changingModel);
        var changingBound = assertDoesNotThrow(() -> changingEstimator.bindNative(changingSnapshot, changingBinding));
        options.set(ChatOptions.builder().model(MODEL).maxTokens(32).temperature(0.8).build());
        assertBindingUnavailable(changingEstimator, changingSnapshot, changingBinding);
        assertAllFiveFail(changingBound, changingSnapshot, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void bound_native_view_rejects_equal_copy_and_forged_config_for_all_five_methods() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var descriptor = estimator.descriptors().getFirst();
        var nativeSnapshot = codeFirstSnapshot(descriptor, CODE_REF, Map.of(), "USD");
        var bound = assertDoesNotThrow(() -> estimator.bindNative(nativeSnapshot,
                new AgentModelBudgetEstimator.NativeModelBinding(CODE_REF, "modelBean", MODEL, model)));
        var equalCopy = new AgentExecutionSnapshot(nativeSnapshot.definition(), nativeSnapshot.definitionContentSha256(),
                nativeSnapshot.effectivePolicy(), nativeSnapshot.input(), nativeSnapshot.businessKey(), nativeSnapshot.processLink());
        assertEquals(nativeSnapshot, equalCopy);
        assertNotSame(nativeSnapshot, equalCopy);

        assertAllFiveFail(bound, equalCopy, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
        assertAllFiveFail(bound, snapshot(descriptor), AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void legacy_global_estimator_keeps_plain_code_first_unsupported_for_all_five_methods() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var snapshot = codeFirstSnapshot(estimator.descriptors().getFirst(), CODE_REF, Map.of(), "USD");

        assertAllFiveFail(estimator, snapshot, AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE);
    }

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
    void native_tool_schema_is_counted_by_the_existing_converter_without_changing_model_options() {
        var model = model();
        var estimator = new JTokkitChatModelBudgetEstimator(new ModelBudgetProperties(List.of(profile(MODEL, BigDecimal.ONE))), Map.of("modelBean", model));
        var snapshot = snapshot(estimator.descriptors().getFirst());
        var text = request("hello");
        var tool = new BaseTool("fixed_decision", "Evaluate a fixed reviewed decision") {
            @Override
            public Optional<FunctionDeclaration> declaration() {
                return Optional.of(FunctionDeclaration.builder().name(name()).description(description())
                        .parameters(Schema.builder().type("OBJECT").properties(Map.of("amount",
                                Schema.builder().type("INTEGER").build())).required(List.of("amount")).build()).build());
            }
        };
        var withTool = text.toBuilder().tools(Map.of(tool.name(), tool)).build();
        var baseline = estimator.estimate(snapshot, text);
        var withSchema = estimator.estimate(snapshot, withTool);
        assertTrue(withSchema.inputTokens() > baseline.inputTokens());
        assertEquals(baseline.maxOutputTokens(), withSchema.maxOutputTokens());
        assertEquals(estimator.modelAdapterFingerprint(snapshot, text), estimator.modelAdapterFingerprint(snapshot, withTool));
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
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
    private static AgentExecutionSnapshot codeFirstSnapshot(JTokkitChatModelBudgetEstimator.Descriptor descriptor, String codeRef,
                                                            Map<String, Object> configuration, String currency) {
        return codeFirstSnapshot(descriptor.modelName(), descriptor.modelRouteRef(), descriptor.policyVersionRef(), codeRef, configuration, currency);
    }
    private static AgentExecutionSnapshot codeFirstSnapshot(String modelName, String route, String policyRef, String codeRef,
                                                            Map<String, Object> configuration, String currency) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 10000, 1000,
                BigDecimal.ONE, currency, 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CODE_FIRST", codeRef, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), route, policyRef), policy, configuration);
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO(modelName, null, null), null);
    }
    private static void assertBindingUnavailable(JTokkitChatModelBudgetEstimator estimator, AgentExecutionSnapshot snapshot,
                                                 AgentModelBudgetEstimator.NativeModelBinding binding) {
        assertLedgerFailure(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, () -> estimator.bindNative(snapshot, binding));
    }
    private static void assertAllFiveFail(AgentModelBudgetEstimator estimator, AgentExecutionSnapshot snapshot,
                                          AgentErrorCodeEnum expectedCode) {
        var request = request("hello");
        var response = LlmResponse.builder().build();
        assertAll(
                () -> assertLedgerFailure(expectedCode, () -> estimator.estimate(snapshot, request)),
                () -> assertLedgerFailure(expectedCode, () -> estimator.actualUsage(snapshot, response)),
                () -> assertLedgerFailure(expectedCode, () -> estimator.contextWindow(snapshot, request)),
                () -> assertLedgerFailure(expectedCode, () -> estimator.modelAdapterFingerprint(snapshot, request)),
                () -> assertLedgerFailure(expectedCode, () -> estimator.contextPolicy(snapshot, request)));
    }
    private static void assertLedgerFailure(AgentErrorCodeEnum expectedCode, Executable executable) {
        assertEquals(expectedCode, assertThrows(AgentLedgerException.class, executable).code());
    }
    private static AgentExecutionSnapshot snapshot(JTokkitChatModelBudgetEstimator.Descriptor descriptor) {
        var policy = new ExecutionPolicyDTO(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(2), 4, 0, 10000, 1000,
                BigDecimal.ONE, "USD", 1, Set.of(), "retry", "approval");
        var definition = new AgentDefinitionSpecDTO("assistant", 1, "CONFIG", null, "1", "1", new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), descriptor.modelRouteRef(), descriptor.policyVersionRef()),
                policy, Map.of("modelName", descriptor.modelName(), "chatModelBeanName", descriptor.modelBeanName()));
        return new AgentExecutionSnapshot(definition, "a".repeat(64), policy, new AgentInputDTO("hello", null, null), null);
    }
}
