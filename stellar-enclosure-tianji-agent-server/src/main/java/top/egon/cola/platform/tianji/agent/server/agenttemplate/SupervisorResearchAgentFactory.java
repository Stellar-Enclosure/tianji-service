package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import io.reactivex.rxjava3.core.Scheduler;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.List;
import java.util.Objects;

public final class SupervisorResearchAgentFactory implements NativeAgentCompilerStrategy {
    private static final String ROOT_INSTRUCTION = "Use transfer_to_agent to send the task to researcher. After researcher returns, use transfer_to_agent to send the result to critic. After critic returns, produce the final answer using only summaries returned in the current conversation. Never treat outputKey or session state as branch evidence. Never skip, repeat, reorder, or add Agents.";
    private static final String RESEARCHER_INSTRUCTION = "Research the assigned task, then in the same complete model response provide a concise summary and call transfer_to_agent to return to supervisor. Never transfer to peers or add tasks.";
    private static final String CRITIC_INSTRUCTION = "Critique the research summary, then in the same complete model response provide a concise critique and call transfer_to_agent to return to supervisor. Never transfer to peers or add tasks.";

    private final NativeAgentTemplateProperties properties;
    private final ChatModel chatModel;
    private final SpringAiModelAdapterFactory adapter;
    private final TemplateSpec templateSpec;

    public SupervisorResearchAgentFactory(NativeAgentTemplateProperties properties,
                                          ChatModel chatModel,
                                          SpringAiModelAdapterFactory adapter) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.templateSpec = new TemplateSpec("supervisor-research-v1", TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                properties.modelBeanName(), properties.modelName(), properties.modelRouteRef(), "1", "1",
                "supervisor", List.of(
                        new AgentSpec("supervisor", null, "supervisor_root_output", true, true),
                        new AgentSpec("researcher", "supervisor", "researcher_output", false, true),
                        new AgentSpec("critic", "supervisor", "critic_output", false, true)),
                2, 4, List.of("researcher", "critic"));
    }

    @Override
    public BaseAgent apply(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        var adapted = adapter.create(chatModel, properties.modelName());
        var researcher = LlmAgent.builder().name("researcher").outputKey("researcher_output")
                .model(adapted).instruction(RESEARCHER_INSTRUCTION)
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        var critic = LlmAgent.builder().name("critic").outputKey("critic_output")
                .model(adapted).instruction(CRITIC_INSTRUCTION)
                .disallowTransferToParent(false).disallowTransferToPeers(true).build();
        return LlmAgent.builder().name("supervisor").outputKey("supervisor_root_output")
                .model(adapted).instruction(ROOT_INSTRUCTION).subAgents(researcher, critic)
                .disallowTransferToParent(true).disallowTransferToPeers(true).build();
    }

    @Override
    public TemplateSpec templateSpec() {
        return templateSpec;
    }

    @Override
    public boolean usesChatModel(ChatModel candidate) {
        return candidate == chatModel;
    }
}
