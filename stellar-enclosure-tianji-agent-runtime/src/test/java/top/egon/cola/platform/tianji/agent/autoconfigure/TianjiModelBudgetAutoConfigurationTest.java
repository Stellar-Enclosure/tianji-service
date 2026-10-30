package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.JTokkitChatModelBudgetEstimator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TianjiModelBudgetAutoConfigurationTest {
    @Test
    void estimator_is_absent_by_default_and_an_unconfigured_enable_fails_closed() {
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiModelBudgetAutoConfiguration.class));
        runner.run(context -> { assertNull(context.getStartupFailure()); assertTrue(context.getBeansOfType(AgentModelBudgetEstimator.class).isEmpty()); });
        runner.withPropertyValues("stellar.tianji.agent.model-budgets.enabled=true").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void configured_text_profile_binds_and_a_host_estimator_retains_precedence() {
        var runner = new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(TianjiModelBudgetAutoConfiguration.class))
                .withUserConfiguration(Models.class).withPropertyValues("stellar.tianji.agent.model-budgets.enabled=true",
                        "stellar.tianji.agent.model-budgets.profiles[0].route-key=route", "stellar.tianji.agent.model-budgets.profiles[0].route-version=1",
                        "stellar.tianji.agent.model-budgets.profiles[0].policy-key=policy", "stellar.tianji.agent.model-budgets.profiles[0].policy-version=1",
                        "stellar.tianji.agent.model-budgets.profiles[0].model-bean-name=modelBean", "stellar.tianji.agent.model-budgets.profiles[0].model-name=gpt-4o-mini",
                        "stellar.tianji.agent.model-budgets.profiles[0].window-tokens=4096", "stellar.tianji.agent.model-budgets.profiles[0].safety-margin-tokens=64",
                        "stellar.tianji.agent.model-budgets.profiles[0].max-output-tokens=128", "stellar.tianji.agent.model-budgets.profiles[0].input-rate-per-million=1",
                        "stellar.tianji.agent.model-budgets.profiles[0].output-rate-per-million=2", "stellar.tianji.agent.model-budgets.profiles[0].currency=USD");
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            assertInstanceOf(JTokkitChatModelBudgetEstimator.class, context.getBean(AgentModelBudgetEstimator.class));
            assertEquals(1, context.getBean(JTokkitChatModelBudgetEstimator.class).descriptors().size());
        });
        var supplied = mock(AgentModelBudgetEstimator.class);
        runner.withBean(AgentModelBudgetEstimator.class, () -> supplied).run(context -> {
            assertNull(context.getStartupFailure()); assertSame(supplied, context.getBean(AgentModelBudgetEstimator.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class Models {
        @Bean Map<String, ChatModel> agentFlowChatModels() {
            var model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(ChatOptions.builder().model("gpt-4o-mini").maxTokens(32).build());
            return Map.of("modelBean", model);
        }
    }
}
