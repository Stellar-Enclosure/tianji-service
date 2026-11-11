package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.springai.MessageConverter;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.ModelType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.cola.platform.tianji.agent.autoconfigure.ModelBudgetProperties;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentModelUsagePriceVO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reviewed text routes use the exact Agent Flow converter, a named BPE encoding and explicit conservative framing allowances. */
public final class JTokkitChatModelBudgetEstimator implements AgentModelBudgetEstimator {
    private static final String ADAPTER_VERSION = "SPRING_AI_2_ADK_1_13_JTOKKIT_1_1_TEXT_V1";
    private final MessageConverter converter = new MessageConverter(new ObjectMapper());
    private final Map<Key, Bound> profiles;
    private final List<Descriptor> descriptors;

    public JTokkitChatModelBudgetEstimator(ModelBudgetProperties properties, Map<String, ChatModel> models) {
        if (properties.profiles().isEmpty()) throw unavailable();
        var registry = Encodings.newLazyEncodingRegistry();
        Map<Key, Bound> bindings = new HashMap<>(); List<Descriptor> descriptions = new ArrayList<>();
        Map<String, String> versions = new HashMap<>();
        for (var profile : properties.profiles()) {
            ChatModel model = Objects.requireNonNull(models.get(profile.modelBeanName()), "configured ChatModel is unavailable");
            ModelType type = ModelType.fromName(profile.modelName()).filter(value -> value.name().startsWith("GPT_")).orElseThrow(JTokkitChatModelBudgetEstimator::unsupported);
            if (profile.windowTokens() > type.getMaxContextLength()) throw unsupported();
            var encoding = registry.getEncodingForModel(type);
            String defaults = TextModelOptionsView.fingerprint(model.getOptions());
            if (!profile.modelName().equals(model.getOptions().getModel())) throw unsupported();
            String routeHash = AdkSemanticJsonDigest.fingerprint("TIANJI_TEXT_ROUTE_V1", List.of(ADAPTER_VERSION, model.getClass().getName(), profile.modelBeanName(), profile.modelName(), encoding.getName(), defaults));
            String policyHash = policyHash(profile);
            sameVersion(versions, "route:" + profile.routeKey() + ':' + profile.routeVersion(), routeHash);
            sameVersion(versions, "policy:" + profile.policyKey() + ':' + profile.policyVersion(), policyHash);
            String route = profile.routeKey() + '@' + profile.routeVersion() + '#' + routeHash;
            String policy = profile.policyKey() + '@' + profile.policyVersion() + '#' + policyHash;
            var descriptor = new Descriptor(profile.modelBeanName(), profile.modelName(), route, policy, encoding.getName(), routeHash, policyHash);
            if (bindings.putIfAbsent(new Key(profile.modelName(), route, policy), new Bound(profile, model, encoding, defaults, descriptor)) != null) throw unavailable();
            descriptions.add(descriptor);
        }
        profiles = Map.copyOf(bindings); descriptors = List.copyOf(descriptions);
    }

    public List<Descriptor> descriptors() { return descriptors; }

    @Override public AgentModelBudgetEstimator bindNative(AgentExecutionSnapshot snapshot, NativeModelBinding binding) {
        if (snapshot == null || binding == null || !"CODE_FIRST".equals(snapshot.definition().executionMode())
                || !snapshot.definition().configuration().isEmpty()
                || !Objects.equals(snapshot.definition().codeRef(), binding.codeRef())) throw unavailable();
        var dependencies = snapshot.definition().dependencies();
        Bound bound = profiles.get(new Key(binding.modelName(), dependencies.modelRouteRef(), dependencies.policyVersionRef()));
        if (bound == null || !bound.profile().modelBeanName().equals(binding.modelBeanName())
                || bound.model() != binding.model()
                || !bound.profile().currency().equals(snapshot.effectivePolicy().currency())) throw unavailable();
        current(bound);
        return new NativeBoundEstimator(snapshot, bound);
    }

    @Override public Estimate estimate(AgentExecutionSnapshot snapshot, LlmRequest request) {
        Bound bound = require(snapshot, request.model().orElse(null));
        return estimate(bound, request);
    }
    private Estimate estimate(Bound bound, LlmRequest request) {
        Prompt prompt = converter.toLlmPrompt(request, bound.model().getOptions());
        current(bound);
        if (prompt.getOptions() != null && prompt.getOptions().getModel() != null && !bound.profile().modelName().equals(prompt.getOptions().getModel())) throw unsupported();
        if (prompt.getOptions() instanceof StructuredOutputChatOptions structured && structured.getOutputSchema() != null) throw unsupported();
        long input = bound.profile().requestOverheadTokens();
        for (var message : prompt.getInstructions()) {
            if (message instanceof MediaContent media && !media.getMedia().isEmpty()) throw unsupported();
            input = Math.addExact(input, bound.profile().messageOverheadTokens());
            input = add(input, bound.encoding(), message.getMessageType().getValue(), message.getText());
            if (message instanceof AssistantMessage assistant) for (var call : assistant.getToolCalls()) {
                input = Math.addExact(input, bound.profile().toolOverheadTokens()); input = add(input, bound.encoding(), call.id(), call.type(), call.name(), call.arguments());
            }
            if (message instanceof ToolResponseMessage tools) for (var response : tools.getResponses()) {
                input = Math.addExact(input, bound.profile().toolOverheadTokens()); input = add(input, bound.encoding(), response.id(), response.name(), response.responseData());
            }
        }
        if (prompt.getOptions() instanceof ToolCallingChatOptions tools && tools.getToolCallbacks() != null) for (var callback : tools.getToolCallbacks()) {
            var definition = callback.getToolDefinition();
            input = Math.addExact(input, bound.profile().toolOverheadTokens()); input = add(input, bound.encoding(), definition.name(), definition.description(), definition.inputSchema());
        }
        int output = bound.profile().maxOutputTokens();
        Integer configuredOutput = prompt.getOptions() == null ? null : prompt.getOptions().getMaxTokens();
        if (configuredOutput == null) configuredOutput = bound.model().getOptions().getMaxTokens();
        if (configuredOutput != null) output = Math.min(output, configuredOutput);
        return new Estimate(input, output, cost(bound.profile(), input, output));
    }

    @Override public Optional<AgentModelUsagePriceVO> modelUsagePrice(AgentExecutionSnapshot snapshot, LlmRequest request) {
        Bound bound = require(snapshot, request.model().orElse(null));
        var profile = bound.profile();
        return Optional.of(new AgentModelUsagePriceVO(bound.descriptor().modelRouteRef(), profile.modelName(),
                profile.currency(), profile.inputRatePerMillion(), profile.outputRatePerMillion()));
    }

    @Override public ContextWindow contextWindow(AgentExecutionSnapshot snapshot, LlmRequest request) {
        return contextWindow(require(snapshot, request.model().orElse(null)));
    }
    private static ContextWindow contextWindow(Bound bound) {
        var profile = bound.profile();
        return new ContextWindow(profile.modelName(), bound.descriptor().modelRouteRef(), bound.descriptor().policyVersionRef(), profile.windowTokens(), profile.safetyMarginTokens());
    }
    @Override public String modelAdapterFingerprint(AgentExecutionSnapshot snapshot, LlmRequest request) {
        return modelAdapterFingerprint(require(snapshot, request.model().orElse(null)));
    }
    private static String modelAdapterFingerprint(Bound bound) {
        return AdkSemanticJsonDigest.fingerprint(ADAPTER_VERSION, List.of(bound.descriptor().routeSha256(), bound.descriptor().policySha256()));
    }
    @Override public ContextPolicy contextPolicy(AgentExecutionSnapshot snapshot, LlmRequest request) {
        return contextPolicy(require(snapshot, request.model().orElse(null)));
    }
    private static ContextPolicy contextPolicy(Bound bound) {
        var profile = bound.profile();
        return new ContextPolicy(bound.descriptor().policyVersionRef(), profile.maxTrimSteps(), profile.retainRecentUserTurns(), profile.trimEvidence(), profile.trimHistory());
    }
    @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot snapshot, LlmResponse response) {
        return actualUsage(require(snapshot, modelName(snapshot)), response);
    }
    private static AgentBudgetSettlement actualUsage(Bound bound, LlmResponse response) {
        var usage = response.usageMetadata().orElse(null);
        if (usage == null || usage.promptTokenCount().isEmpty() || usage.candidatesTokenCount().isEmpty()) return new AgentBudgetSettlement(null, null, null);
        long input = usage.promptTokenCount().orElseThrow(); long output = usage.candidatesTokenCount().orElseThrow();
        if (input < 0 || output < 0 || usage.totalTokenCount().isPresent() && usage.totalTokenCount().orElseThrow() != input + output) throw unavailable();
        // This is the published accounting tariff, not a claim about the supplier invoice or cache discounts.
        return new AgentBudgetSettlement(input, output, cost(bound.profile(), input, output));
    }

    private Bound require(AgentExecutionSnapshot snapshot, String modelName) {
        if (!"CONFIG".equals(snapshot.definition().executionMode()) || !Objects.equals(modelName, modelName(snapshot))) throw unsupported();
        Bound bound = configBinding(snapshot.definition(), modelName, snapshot.effectivePolicy().currency());
        current(bound);
        return bound;
    }

    void requirePublishedConfigBinding(AgentDefinitionSpecDTO definition, String modelName, ChatModel selectedModel) {
        Bound bound = configBinding(definition, modelName, definition.policy().currency());
        if (bound.model() != selectedModel) throw unavailable();
        current(bound);
    }

    private Bound configBinding(AgentDefinitionSpecDTO definition, String modelName, String currency) {
        var dependencies = definition.dependencies();
        Bound bound = profiles.get(new Key(modelName, dependencies.modelRouteRef(), dependencies.policyVersionRef()));
        if (bound == null || !bound.profile().modelBeanName().equals(definition.configuration().get("chatModelBeanName"))
                || !bound.profile().currency().equals(currency)) throw unavailable();
        return bound;
    }
    private final class NativeBoundEstimator implements AgentModelBudgetEstimator {
        private final AgentExecutionSnapshot boundSnapshot;
        private final Bound bound;

        private NativeBoundEstimator(AgentExecutionSnapshot snapshot, Bound bound) {
            this.boundSnapshot = snapshot;
            this.bound = bound;
        }

        @Override public Estimate estimate(AgentExecutionSnapshot candidate, LlmRequest request) {
            requireSnapshot(candidate); requireModel(request.model().orElse(null)); current(bound);
            return JTokkitChatModelBudgetEstimator.this.estimate(bound, request);
        }
        @Override public AgentBudgetSettlement actualUsage(AgentExecutionSnapshot candidate, LlmResponse response) {
            requireSnapshot(candidate); current(bound); return JTokkitChatModelBudgetEstimator.actualUsage(bound, response);
        }
        @Override public ContextWindow contextWindow(AgentExecutionSnapshot candidate, LlmRequest request) {
            requireSnapshot(candidate); requireModel(request.model().orElse(null)); current(bound);
            return JTokkitChatModelBudgetEstimator.contextWindow(bound);
        }
        @Override public String modelAdapterFingerprint(AgentExecutionSnapshot candidate, LlmRequest request) {
            requireSnapshot(candidate); requireModel(request.model().orElse(null)); current(bound);
            return JTokkitChatModelBudgetEstimator.modelAdapterFingerprint(bound);
        }
        @Override public ContextPolicy contextPolicy(AgentExecutionSnapshot candidate, LlmRequest request) {
            requireSnapshot(candidate); requireModel(request.model().orElse(null)); current(bound);
            return JTokkitChatModelBudgetEstimator.contextPolicy(bound);
        }
        private void requireModel(String modelName) {
            if (!bound.profile().modelName().equals(modelName)) throw unsupported();
        }
        private void requireSnapshot(AgentExecutionSnapshot candidate) {
            // A semantically equal copy is not the execution authority bound by the executor.
            if (candidate != boundSnapshot) throw unavailable();
        }
    }
    private static String modelName(AgentExecutionSnapshot snapshot) {
        Object model = snapshot.definition().configuration().get("modelName"); return model instanceof String value ? value : null;
    }
    private static void current(Bound bound) {
        if (!bound.defaultsSha256().equals(TextModelOptionsView.fingerprint(bound.model().getOptions()))) throw unavailable();
    }
    private static long add(long current, Encoding encoding, String... values) {
        for (var value : values) if (value != null && !value.isEmpty()) current = Math.addExact(current, encoding.countTokensOrdinary(value));
        return current;
    }
    private static BigDecimal cost(ModelBudgetProperties.Profile profile, long input, long output) {
        return profile.inputRatePerMillion().multiply(BigDecimal.valueOf(input)).add(profile.outputRatePerMillion().multiply(BigDecimal.valueOf(output)))
                .divide(new BigDecimal("1000000"), 8, RoundingMode.CEILING);
    }
    private static String policyHash(ModelBudgetProperties.Profile profile) {
        return AdkSemanticJsonDigest.fingerprint("TIANJI_TEXT_ACCOUNTING_POLICY_V1", List.of(Long.toString(profile.windowTokens()), Long.toString(profile.safetyMarginTokens()),
                Integer.toString(profile.maxOutputTokens()), Integer.toString(profile.messageOverheadTokens()), Integer.toString(profile.requestOverheadTokens()), Integer.toString(profile.toolOverheadTokens()),
                profile.inputRatePerMillion().toPlainString(), profile.outputRatePerMillion().toPlainString(), profile.currency(), Integer.toString(profile.maxTrimSteps()),
                Integer.toString(profile.retainRecentUserTurns()), Boolean.toString(profile.trimEvidence()), Boolean.toString(profile.trimHistory())));
    }
    private static void sameVersion(Map<String, String> versions, String key, String hash) {
        String previous = versions.putIfAbsent(key, hash); if (previous != null && !previous.equals(hash)) throw unavailable();
    }
    public record Descriptor(String modelBeanName, String modelName, String modelRouteRef, String policyVersionRef, String encodingName,
                              String routeSha256, String policySha256) { }
    private record Key(String modelName, String modelRouteRef, String policyVersionRef) { }
    private record Bound(ModelBudgetProperties.Profile profile, ChatModel model, Encoding encoding, String defaultsSha256, Descriptor descriptor) { }
    private static AgentLedgerException unsupported() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Model is outside the reviewed text-tokenizer profile"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Versioned model budget binding is unavailable or changed"); }
}
