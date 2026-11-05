package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Instruction;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.Model;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BoundedResearchAgentFactoryTest {
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);
    private static final String POLICY = "research-policy@1#" + "b".repeat(64);

    @Test
    void bounded_factory_type_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.agenttemplate.BoundedResearchAgentFactory"));
    }

    @Test
    void bounded_factory_metadata_and_tree_are_exact() {
        var fixture = fixture();
        var root = tree(fixture.factory());
        var spec = assertDoesNotThrow(() -> fixture.factory().templateSpec());
        assertNotNull(spec);
        var children = children(root);
        assertAll(
                () -> assertEquals("bounded_supervisor", root.name()),
                () -> assertNull(root.parentAgent()),
                () -> assertEquals("bounded_supervisor_output", root.outputKey().orElseThrow()),
                () -> assertTrue(root.disallowTransferToParent()),
                () -> assertTrue(root.disallowTransferToPeers()),
                () -> assertEquals(List.of("bounded_researcher", "bounded_critic"), children.stream().map(BaseAgent::name).toList()),
                () -> assertTrue(children.stream().allMatch(child -> child.parentAgent() == root)),
                () -> assertTrue(children.stream().noneMatch(LlmAgent::disallowTransferToParent)),
                () -> assertTrue(children.stream().allMatch(LlmAgent::disallowTransferToPeers)),
                () -> assertEquals(List.of("bounded_researcher_output", "bounded_critic_output"),
                        children.stream().map(child -> child.outputKey().orElseThrow()).toList()),
                () -> assertEquals("bounded-research-v1", spec.codeRef()),
                () -> assertEquals(NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1, spec.topology()),
                () -> assertEquals("researchModel", spec.modelBeanName()),
                () -> assertEquals("gpt-5", spec.modelName()),
                () -> assertEquals(ROUTE, spec.modelRouteRef()),
                () -> assertEquals("1", spec.inputSchemaVersion()),
                () -> assertEquals("2", spec.outputSchemaVersion()),
                () -> assertEquals(2, spec.maxDepth()),
                () -> assertEquals(16, spec.maxTransfers()),
                () -> assertEquals(List.of("bounded_researcher", "bounded_critic"), spec.requiredChildOrder()));
        verifyNoProvider(fixture.model());
    }

    @Test
    void each_apply_builds_a_fresh_tree_with_one_shared_explicit_model() {
        var fixture = fixture();
        var first = tree(fixture.factory());
        var second = tree(fixture.factory());
        BaseLlm firstModel = explicitModel(first), secondModel = explicitModel(second);
        assertAll(
                () -> assertNotSame(first, second),
                () -> assertNotSame(firstModel, secondModel),
                () -> assertTrue(children(first).stream().allMatch(child -> explicitModel(child) == firstModel)),
                () -> assertTrue(children(second).stream().allMatch(child -> explicitModel(child) == secondModel)),
                () -> assertNotSame(first.model().orElseThrow(), children(first).getFirst().model().orElseThrow()));
        verifyNoProvider(fixture.model());
    }

    @Test
    void model_use_is_exact_identity_and_metadata_hash_is_stable() {
        var fixture = fixture();
        var spec = assertDoesNotThrow(() -> fixture.factory().templateSpec());
        assertNotNull(spec);
        String hash = assertDoesNotThrow(() -> spec.topologyHash(POLICY));
        assertNotNull(hash);
        assertAll(
                () -> assertTrue(hash.matches("[0-9a-f]{64}")),
                () -> assertEquals(hash, assertDoesNotThrow(() -> fixture.factory().templateSpec().topologyHash(POLICY))),
                () -> assertTrue(fixture.factory().usesChatModel(fixture.model())),
                () -> assertFalse(fixture.factory().usesChatModel(model())),
                () -> assertFalse(fixture.factory().usesChatModel(null)));
        verifyNoProvider(fixture.model());
    }

    @Test
    void built_agents_have_no_ordinary_tools_toolsets_code_executor_or_callbacks() {
        var fixture = fixture();
        var root = tree(fixture.factory());
        var agents = new ArrayList<LlmAgent>();
        agents.add(root); agents.addAll(children(root));
        for (LlmAgent agent : agents) {
            assertAll(
                    () -> assertTrue(assertDoesNotThrow(() -> agent.tools().blockingGet()).isEmpty()),
                    () -> assertTrue(agent.toolsets().isEmpty()),
                    () -> assertTrue(agent.codeExecutor().isEmpty()),
                    () -> assertTrue(agent.canonicalBeforeModelCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalAfterModelCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalOnModelErrorCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalBeforeToolCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalAfterToolCallbacks().isEmpty()),
                    () -> assertTrue(agent.canonicalOnToolErrorCallbacks().isEmpty()));
        }
        verifyNoProvider(fixture.model());
    }

    @Test
    void published_instructions_require_frozen_tasks_and_untrusted_current_questions() {
        var fixture = fixture();
        var root = tree(fixture.factory());
        String rootText = instruction(root);
        assertAll(
                () -> assertTrue(rootText.contains("schemaVersion")),
                () -> assertTrue(rootText.contains("1..8")),
                () -> assertTrue(rootText.contains("plan is then frozen")),
                () -> assertTrue(rootText.contains("no non-thought text")),
                () -> assertTrue(rootText.contains("outputKey")));
        for (var child : children(root)) {
            String text = instruction(child);
            assertAll(
                    () -> assertTrue(text.contains("TIANJI_UNTRUSTED_TASK_QUESTION_V1")),
                    () -> assertTrue(text.contains("untrusted data")),
                    () -> assertTrue(text.contains("same complete nonpartial model response")),
                    () -> assertTrue(text.contains("agent_name bounded_supervisor")));
        }
        verifyNoProvider(fixture.model());
    }

    @Test
    void factory_requires_decomposition_and_non_null_host_inputs_and_scheduler() {
        var properties = properties(true);
        var model = model();
        var adapter = new SpringAiModelAdapterFactory();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new BoundedResearchAgentFactory(null, model, adapter)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BoundedResearchAgentFactory(properties(false), model, adapter)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BoundedResearchAgentFactory(properties, null, adapter)),
                () -> assertThrows(IllegalArgumentException.class, () -> new BoundedResearchAgentFactory(properties, model, null)));
        var factory = assertDoesNotThrow(() -> new BoundedResearchAgentFactory(properties, model, adapter));
        assertThrows(IllegalArgumentException.class, () -> factory.apply(null));
        verifyNoProvider(model);
    }

    private static FixtureVO fixture() {
        var model = model();
        var factory = assertDoesNotThrow(() -> new BoundedResearchAgentFactory(properties(true), model, new SpringAiModelAdapterFactory()));
        return new FixtureVO(factory, model);
    }

    private static NativeAgentTemplateProperties properties(boolean decomposition) {
        return assertDoesNotThrow(() -> new NativeAgentTemplateProperties(true, "researchModel", "gpt-5", ROUTE, decomposition));
    }

    private static ChatModel model() {
        var model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().model("gpt-5").build());
        return model;
    }

    private static LlmAgent tree(BoundedResearchAgentFactory factory) {
        BaseAgent root = assertDoesNotThrow(() -> factory.apply(Schedulers.trampoline()));
        return assertInstanceOf(LlmAgent.class, root);
    }

    private static List<LlmAgent> children(LlmAgent root) {
        return root.subAgents().stream().map(agent -> assertInstanceOf(LlmAgent.class, agent)).toList();
    }

    private static BaseLlm explicitModel(LlmAgent agent) {
        return assertDoesNotThrow(() -> agent.model().flatMap(Model::model).orElseThrow());
    }

    private static String instruction(LlmAgent agent) {
        var instruction = assertInstanceOf(Instruction.Static.class, agent.instruction());
        String text = instruction.instruction();
        assertNotNull(text);
        return text;
    }

    private static void verifyNoProvider(ChatModel model) {
        verify(model, never()).call(any(Prompt.class));
        verify(model, never()).stream(any(Prompt.class));
    }

    private record FixtureVO(BoundedResearchAgentFactory factory, ChatModel model) { }
}
