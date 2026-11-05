package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Model;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ConditionalResearchAgentFactoryTest {
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);
    private static final String POLICY = "research-policy@1#" + "b".repeat(64);

    @Test
    void conditional_research_agent_factory_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.agenttemplate.ConditionalResearchAgentFactory"));
    }

    @Test
    void builds_the_exact_conditional_tree_and_template_metadata() {
        var fixture = fixture();
        var root = assertInstanceOf(LlmAgent.class, fixture.factory().apply(Schedulers.trampoline()));
        var spec = fixture.factory().templateSpec();
        assertNotNull(spec);
        List<LlmAgent> children = children(root);

        assertAll(
                () -> assertEquals("conditional_root", root.name()),
                () -> assertTrue(root.parentAgent() == null),
                () -> assertEquals("conditional_root_output", root.outputKey().orElseThrow()),
                () -> assertTrue(root.disallowTransferToParent()),
                () -> assertTrue(root.disallowTransferToPeers()),
                () -> assertEquals(List.of("quick_research", "deep_research"),
                        children.stream().map(BaseAgent::name).toList()),
                () -> assertTrue(children.stream().allMatch(child -> child.parentAgent() == root)),
                () -> assertEquals(List.of("quick_research_output", "deep_research_output"),
                        children.stream().map(child -> child.outputKey().orElseThrow()).toList()),
                () -> assertTrue(children.stream().noneMatch(LlmAgent::disallowTransferToParent)),
                () -> assertTrue(children.stream().allMatch(LlmAgent::disallowTransferToPeers)),
                () -> assertEquals("conditional-research-v1", spec.codeRef()),
                () -> assertEquals(NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                        spec.topology()),
                () -> assertEquals("researchModel", spec.modelBeanName()),
                () -> assertEquals("gpt-5", spec.modelName()),
                () -> assertEquals(ROUTE, spec.modelRouteRef()),
                () -> assertEquals("1", spec.inputSchemaVersion()),
                () -> assertEquals("1", spec.outputSchemaVersion()),
                () -> assertEquals(2, spec.maxDepth()),
                () -> assertEquals(2, spec.maxTransfers()),
                () -> assertEquals(List.of("quick_research", "deep_research"), spec.requiredChildOrder()));
    }

    @Test
    void each_apply_builds_a_fresh_tree_with_one_shared_explicit_model() {
        var factory = fixture().factory();
        var first = assertInstanceOf(LlmAgent.class, factory.apply(Schedulers.trampoline()));
        var second = assertInstanceOf(LlmAgent.class, factory.apply(Schedulers.trampoline()));
        BaseLlm firstModel = explicitModel(first);
        BaseLlm secondModel = explicitModel(second);

        assertAll(
                () -> assertNotSame(first, second),
                () -> assertNotSame(firstModel, secondModel),
                () -> assertTrue(children(first).stream().allMatch(child -> explicitModel(child) == firstModel)),
                () -> assertTrue(children(second).stream().allMatch(child -> explicitModel(child) == secondModel)),
                () -> assertNotSame(first.model().orElseThrow(), children(first).getFirst().model().orElseThrow()));
    }

    @Test
    void topology_hash_is_stable_and_model_use_is_exact_object_identity() {
        var fixture = fixture();
        var otherModel = mock(ChatModel.class);
        var spec = fixture.factory().templateSpec();
        assertNotNull(spec);
        String first = spec.topologyHash(POLICY);

        assertAll(
                () -> assertEquals(first, fixture.factory().templateSpec().topologyHash(POLICY)),
                () -> assertTrue(first.matches("[0-9a-f]{64}")),
                () -> assertTrue(fixture.factory().usesChatModel(fixture.model())),
                () -> assertFalse(fixture.factory().usesChatModel(otherModel)),
                () -> assertFalse(fixture.factory().usesChatModel(null)));
    }

    @Test
    void built_agents_declare_no_tools_toolsets_code_executor_or_callbacks() {
        var root = assertInstanceOf(LlmAgent.class, fixture().factory().apply(Schedulers.trampoline()));
        List<LlmAgent> agents = new ArrayList<>();
        agents.add(root);
        agents.addAll(children(root));

        for (LlmAgent agent : agents) {
            assertAll(
                    () -> assertTrue(agent.tools().blockingGet().isEmpty()),
                    () -> assertTrue(agent.toolsets().isEmpty()),
                    () -> assertTrue(agent.codeExecutor().isEmpty()),
                    () -> assertTrue(agent.canonicalBeforeModelCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalAfterModelCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalOnModelErrorCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalBeforeToolCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalAfterToolCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalOnToolErrorCallbacks().isEmpty()));
        }
    }

    @Test
    void rejects_a_null_scheduler_without_creating_a_tree() {
        assertThrows(RuntimeException.class, () -> fixture().factory().apply(null));
    }

    private static Fixture fixture() {
        ChatModel model = mock(ChatModel.class);
        var properties = new NativeAgentTemplateProperties(
                true, "researchModel", "gpt-5", ROUTE);
        return new Fixture(new ConditionalResearchAgentFactory(
                properties, model, new SpringAiModelAdapterFactory()), model);
    }

    private static List<LlmAgent> children(LlmAgent root) {
        return root.subAgents().stream().map(agent -> assertInstanceOf(LlmAgent.class, agent)).toList();
    }

    private static BaseLlm explicitModel(LlmAgent agent) {
        return agent.model().flatMap(Model::model).orElseThrow();
    }

    private record Fixture(ConditionalResearchAgentFactory factory, ChatModel model) {
    }
}
