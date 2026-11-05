package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import io.reactivex.rxjava3.core.Scheduler;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.List;

public final class ConditionalResearchAgentFactory implements NativeAgentCompilerStrategy {
    private static final String ROOT_INSTRUCTION = """
            Choose exactly one child for the current task. Transfer to quick_research for a focused lookup or
            deep_research for broader synthesis. After that child transfers back, use its current returned summary
            to produce the final response. Do not transfer to a second child and do not use outputKey state as
            evidence of the child summary.
            """;
    private static final String QUICK_INSTRUCTION = """
            Produce a concise focused research summary for the current task, then call transfer_to_agent exactly
            once with agent_name conditional_root. The summary must be returned in the same complete model response
            as that transfer call. Do not transfer to peers or invoke any other tool.
            """;
    private static final String DEEP_INSTRUCTION = """
            Produce a bounded broader research synthesis for the current task, then call transfer_to_agent exactly
            once with agent_name conditional_root. The synthesis must be returned in the same complete model
            response as that transfer call. Do not transfer to peers or invoke any other tool.
            """;

    private final ChatModel chatModel;
    private final SpringAiModelAdapterFactory adapter;
    private final TemplateSpec templateSpec;

    public ConditionalResearchAgentFactory(NativeAgentTemplateProperties properties,
                                           ChatModel chatModel,
                                           SpringAiModelAdapterFactory adapter) {
        if (properties == null || !properties.enabled() || chatModel == null || adapter == null) throw invalid();
        this.chatModel = chatModel;
        this.adapter = adapter;
        this.templateSpec = new TemplateSpec("conditional-research-v1",
                TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                properties.modelBeanName(), properties.modelName(), properties.modelRouteRef(),
                "1", "1", "conditional_root",
                List.of(
                        new AgentSpec("conditional_root", null, "conditional_root_output", true, true),
                        new AgentSpec("quick_research", "conditional_root", "quick_research_output", false, true),
                        new AgentSpec("deep_research", "conditional_root", "deep_research_output", false, true)),
                2, 2, List.of("quick_research", "deep_research"));
    }

    @Override
    public BaseAgent apply(Scheduler scheduler) {
        if (scheduler == null) throw invalid();
        BaseLlm model = adapter.create(chatModel, templateSpec.modelName());
        LlmAgent quick = LlmAgent.builder().name("quick_research").model(model)
                .instruction(QUICK_INSTRUCTION).outputKey("quick_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        LlmAgent deep = LlmAgent.builder().name("deep_research").model(model)
                .instruction(DEEP_INSTRUCTION).outputKey("deep_research_output")
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        return LlmAgent.builder().name("conditional_root").model(model)
                .instruction(ROOT_INSTRUCTION).outputKey("conditional_root_output")
                .subAgents(quick, deep).disallowTransferToParent(true).disallowTransferToPeers(true).build();
    }

    @Override
    public TemplateSpec templateSpec() {
        return templateSpec;
    }

    @Override
    public boolean usesChatModel(ChatModel candidate) {
        return candidate == chatModel;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Conditional research Agent factory configuration is invalid");
    }
}
