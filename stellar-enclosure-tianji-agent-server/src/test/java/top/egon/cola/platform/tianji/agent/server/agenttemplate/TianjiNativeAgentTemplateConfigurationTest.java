package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TianjiNativeAgentTemplateConfigurationTest {
    private static final String PREFIX = "stellar.tianji.agent.native-templates.";
    private static final String FACTORIES = "tianjiAgentCodeFirstFactories";
    private static final String MODEL_BEAN = "reviewedModel";
    private static final String MODEL = "gpt-4o-mini";
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);

    @Test
    void native_template_properties_are_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.agenttemplate.NativeAgentTemplateProperties"));
    }

    @Test
    void native_template_configuration_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.agenttemplate.TianjiNativeAgentTemplateConfiguration"));
    }

    @Test
    void base_yaml_has_the_complete_default_off_native_template_key_set() throws Exception {
        var source = new YamlPropertySourceLoader().load("agent-server", new ClassPathResource("application.yml")).getFirst();
        assertAll(
                () -> assertEquals(Boolean.FALSE, source.getProperty(PREFIX + "enabled")),
                () -> assertEquals("", source.getProperty(PREFIX + "model-bean-name")),
                () -> assertEquals("", source.getProperty(PREFIX + "model-name")),
                () -> assertEquals("", source.getProperty(PREFIX + "model-route-ref")));
    }

    @Test
    void disabled_configuration_does_not_bind_empty_properties_or_contribute_a_factory_map() {
        runner(Map.of()).withPropertyValues(PREFIX + "enabled=false", PREFIX + "model-bean-name=",
                PREFIX + "model-name=", PREFIX + "model-route-ref=").run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean(FACTORIES));
            assertTrue(context.getBeansOfType(NativeAgentTemplateProperties.class).isEmpty());
        });
        runner(Map.of()).run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean(FACTORIES));
        });
    }

    @Test
    void enabled_configuration_contributes_exactly_two_immutable_function_strategies_to_the_named_consumer() {
        ChatModel model = model(MODEL);
        enabled(runner(Map.of(MODEL_BEAN, model))).withUserConfiguration(ConsumerConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertTrue(context.containsBean(FACTORIES));
            Map<?, ?> factories = assertDoesNotThrow(() -> context.getBean(FACTORIES, Map.class));
            assertEquals(Set.of("conditional-research-v1", "supervisor-research-v1"), factories.keySet());
            assertTrue(factories.values().stream().allMatch(NativeAgentCompilerStrategy.class::isInstance));
            assertTrue(factories.values().stream().allMatch(Function.class::isInstance));
            assertSame(factories, context.getBean("nativeFactoryConsumer"));
            assertThrows(UnsupportedOperationException.class, factories::clear);
            verify(model, atLeastOnce()).getOptions();
        });
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
    }

    @Test
    void enabling_requires_the_exact_qualified_model_map_and_adapter_without_creating_either() {
        ChatModel model = model(MODEL);
        enabled(runner(Map.of("otherModel", model)))
                .run(context -> assertNotNull(context.getStartupFailure()));
        enabled(new ApplicationContextRunner().withUserConfiguration(TianjiNativeAgentTemplateConfiguration.class)
                .withBean("agentFlowChatModels", Map.class, () -> Map.of(MODEL_BEAN, model)))
                .run(context -> assertNotNull(context.getStartupFailure()));
        enabled(new ApplicationContextRunner().withUserConfiguration(TianjiNativeAgentTemplateConfiguration.class)
                .withBean("unqualifiedModels", Map.class, () -> Map.of(MODEL_BEAN, model))
                .withBean("springAiModelAdapterFactory", SpringAiModelAdapterFactory.class, SpringAiModelAdapterFactory::new))
                .run(context -> assertNotNull(context.getStartupFailure()));
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
    }

    @Test
    void mismatched_missing_options_and_callback_bearing_models_fail_closed() {
        ChatModel mismatch = model("different-model");
        enabled(runner(Map.of(MODEL_BEAN, mismatch))).run(context -> assertNotNull(context.getStartupFailure()));
        ChatModel noOptions = mock(ChatModel.class);
        enabled(runner(Map.of(MODEL_BEAN, noOptions))).run(context -> assertNotNull(context.getStartupFailure()));
        var options = mock(ToolCallingChatOptions.class);
        when(options.getModel()).thenReturn(MODEL);
        when(options.getToolCallbacks()).thenReturn(List.of(mock(ToolCallback.class)));
        ChatModel callbackModel = mock(ChatModel.class);
        when(callbackModel.getOptions()).thenReturn(options);
        enabled(runner(Map.of(MODEL_BEAN, callbackModel))).run(context -> assertNotNull(context.getStartupFailure()));
        verify(callbackModel, never()).call(any(Prompt.class));
        verify(callbackModel, never()).stream(any(Prompt.class));
    }

    @Test
    void enabled_configuration_rejects_unknown_properties() {
        enabled(runner(Map.of(MODEL_BEAN, model(MODEL)))).withPropertyValues(PREFIX + "unreviewed-option=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void properties_reject_disabled_or_unsafe_metadata_without_normalizing_it() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(false, MODEL_BEAN, MODEL, ROUTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(true, null, MODEL, ROUTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(true, " " + MODEL_BEAN, MODEL, ROUTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(true, MODEL_BEAN, "private\nmodel", ROUTE)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(true, MODEL_BEAN, MODEL, "route-v1")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new NativeAgentTemplateProperties(true, MODEL_BEAN, MODEL, "route@0#" + "a".repeat(64))));
        var valid = assertDoesNotThrow(() -> new NativeAgentTemplateProperties(true, MODEL_BEAN, MODEL, ROUTE));
        assertEquals(MODEL_BEAN, valid.modelBeanName());
        assertEquals(MODEL, valid.modelName());
        assertEquals(ROUTE, valid.modelRouteRef());
    }

    private static ApplicationContextRunner runner(Map<String, ChatModel> models) {
        return new ApplicationContextRunner().withUserConfiguration(TianjiNativeAgentTemplateConfiguration.class)
                .withBean("agentFlowChatModels", Map.class, () -> models)
                .withBean("springAiModelAdapterFactory", SpringAiModelAdapterFactory.class, SpringAiModelAdapterFactory::new);
    }

    private static ApplicationContextRunner enabled(ApplicationContextRunner runner) {
        return runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "model-bean-name=" + MODEL_BEAN,
                PREFIX + "model-name=" + MODEL, PREFIX + "model-route-ref=" + ROUTE);
    }

    private static ChatModel model(String name) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().model(name).maxTokens(64).build());
        return model;
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {
        @Bean(name = "nativeFactoryConsumer")
        Map<String, Function<Scheduler, ? extends BaseAgent>> consumer(
                @Qualifier(FACTORIES) Map<String, Function<Scheduler, ? extends BaseAgent>> factories) {
            return factories;
        }
    }
}
