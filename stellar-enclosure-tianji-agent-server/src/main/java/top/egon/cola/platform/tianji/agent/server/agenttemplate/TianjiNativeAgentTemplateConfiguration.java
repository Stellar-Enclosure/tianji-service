package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;

import java.util.Map;
import java.util.function.Function;

/** Contributes reviewed Code-First factories without creating a model client or enabling the runtime. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.native-templates", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NativeAgentTemplateProperties.class)
public class TianjiNativeAgentTemplateConfiguration {
    @Bean(name = "tianjiAgentCodeFirstFactories")
    Map<String, Function<Scheduler, ? extends BaseAgent>> tianjiAgentCodeFirstFactories(
            NativeAgentTemplateProperties properties,
            @Qualifier("agentFlowChatModels") Map<String, ChatModel> models,
            @Qualifier("springAiModelAdapterFactory") SpringAiModelAdapterFactory adapter) {
        ChatModel model = models.get(properties.modelBeanName());
        ChatOptions options = model == null ? null : model.getOptions();
        if (options == null || !properties.modelName().equals(options.getModel())
                || options instanceof ToolCallingChatOptions tools && tools.getToolCallbacks() != null
                && !tools.getToolCallbacks().isEmpty()) {
            throw new IllegalStateException("TIANJI_NATIVE_TEMPLATE_MODEL_BINDING_INVALID");
        }
        return Map.of("conditional-research-v1", new ConditionalResearchAgentFactory(properties, model, adapter),
                "supervisor-research-v1", new SupervisorResearchAgentFactory(properties, model, adapter));
    }
}
