package top.egon.cola.platform.tianji.agent.autoconfigure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowAutoConfiguration;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AgentModelBudgetEstimator;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.JTokkitChatModelBudgetEstimator;

import java.util.Map;

/** Supplies a concrete text estimator only for explicitly reviewed versioned profiles. */
@AutoConfiguration(after = AgentFlowAutoConfiguration.class, before = TianjiAgentRuntimeAutoConfiguration.class)
@ConditionalOnProperty(prefix = "stellar.tianji.agent.model-budgets", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ModelBudgetProperties.class)
public class TianjiModelBudgetAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(AgentModelBudgetEstimator.class)
    AgentModelBudgetEstimator tianjiTextModelBudgetEstimator(ModelBudgetProperties properties,
            @Qualifier("agentFlowChatModels") Map<String, ChatModel> models) {
        return new JTokkitChatModelBudgetEstimator(properties, models);
    }
}
