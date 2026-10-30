package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Reads only reviewed semantic getters. Credentials, transport headers and tool execution context are never inspected. */
final class TextModelOptionsView {
    private static final JsonMapper JSON = JsonMapper.builder().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    private static final Set<String> COMMON = Set.of("getModel", "getFrequencyPenalty", "getMaxTokens", "getPresencePenalty", "getStopSequences", "getTemperature", "getTopK", "getTopP");
    private static final Set<String> TRANSPORT = Set.of("getBaseUrl", "getApiKey", "getCredential", "getMicrosoftDeploymentName", "getDeploymentName",
            "getMicrosoftFoundryServiceVersion", "getOrganizationId", "isMicrosoftFoundry", "isGitHubModels", "getTimeout", "getProxy", "getCustomHeaders", "getToolContext", "getClass");
    private static final Set<String> OPENAI = Set.of("getLogitBias", "getLogprobs", "getTopLogprobs", "getMaxCompletionTokens", "getN", "getOutputModalities",
            "getOutputAudio", "getResponseFormat", "getStreamOptions", "getSeed", "getStop", "getToolChoice", "getUser", "getParallelToolCalls", "getStore", "getStrict",
            "getMetadata", "getReasoningEffort", "getVerbosity", "getServiceTier", "getPromptCacheKey", "getReplayReasoningContent", "getExtraBody", "getOutputSchema", "getMaxRetries");
    private static final Set<String> UNSUPPORTED = Set.of("getMaxCompletionTokens", "getOutputAudio", "getResponseFormat", "getToolChoice", "getReasoningEffort", "getExtraBody", "getOutputSchema");

    static String fingerprint(ChatOptions options) {
        if (options == null) throw unsupported();
        String type = options.getClass().getName();
        boolean openai = type.equals("org.springframework.ai.openai.OpenAiChatOptions");
        if (!openai && !Set.of("org.springframework.ai.chat.prompt.DefaultChatOptions", "org.springframework.ai.model.tool.DefaultToolCallingChatOptions",
                "org.springframework.ai.model.tool.DefaultStructuredOutputChatOptions").contains(type)) throw unsupported();
        if (options instanceof ToolCallingChatOptions tools && tools.getToolCallbacks() != null && !tools.getToolCallbacks().isEmpty()) throw unsupported();
        if (options instanceof StructuredOutputChatOptions structured && structured.getOutputSchema() != null) throw unsupported();
        if (openai) {
            for (var name : List.of("isMicrosoftFoundry", "isGitHubModels", "getMicrosoftDeploymentName", "getDeploymentName")) {
                try {
                    Object value = options.getClass().getMethod(name).invoke(options);
                    if (Boolean.TRUE.equals(value) || value instanceof String text && !text.isBlank()) throw unsupported();
                } catch (ReflectiveOperationException failure) { throw unsupported(); }
            }
        }
        Map<String, Object> values = new TreeMap<>(); values.put("optionSchema", openai ? "SPRING_AI_OPENAI_TEXT_2_0_V1" : "SPRING_AI_COMMON_TEXT_2_0_V1");
        for (Method method : options.getClass().getMethods()) {
            String name = method.getName();
            if (method.getParameterCount() != 0 || (!name.startsWith("get") && !name.startsWith("is"))) continue;
            if (TRANSPORT.contains(name) || name.equals("getToolCallbacks")) continue;
            if (!COMMON.contains(name) && !(openai && OPENAI.contains(name)) && !name.equals("getOutputSchema")) throw unsupported();
            try {
                Object value = method.invoke(options);
                if (openai && UNSUPPORTED.contains(name) && value != null && (!(value instanceof Map<?, ?> map) || !map.isEmpty())) throw unsupported();
                if (name.equals("getN") && value != null && !Integer.valueOf(1).equals(value)) throw unsupported();
                if (name.equals("getMaxRetries") && !Integer.valueOf(0).equals(value)) throw unsupported();
                if (name.equals("getOutputModalities") && value != null && !List.of("text").equals(value) && !List.of().equals(value)) throw unsupported();
                if (name.equals("getReplayReasoningContent") && Boolean.TRUE.equals(value)) throw unsupported();
                values.put(name, value);
            } catch (ReflectiveOperationException failure) { throw unsupported(); }
        }
        try { return AdkSemanticJsonDigest.json(JSON.writeValueAsString(values), false).sha256(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException failure) { throw unsupported(); }
    }

    private static AgentLedgerException unsupported() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Model defaults are outside the reviewed text-budget projection");
    }
}
