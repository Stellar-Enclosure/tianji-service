package top.egon.cola.platform.tianji.agent.server.agenttemplate;

import com.google.adk.agents.Instruction;
import com.google.adk.agents.LlmAgent;
import com.google.adk.models.BaseLlm;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import top.egon.cola.component.agentflow.runtime.SpringAiModelAdapterFactory;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupervisorResearchAgentFactoryTest {
    private static final String MODEL_BEAN = "researchModel";
    private static final String MODEL_NAME = "gpt-5";
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);
    private static final String POLICY = "research-policy@1#" + "b".repeat(64);
    private static final String ROOT_INSTRUCTION = "Use transfer_to_agent to send the task to researcher. After researcher returns, use transfer_to_agent to send the result to critic. After critic returns, produce the final answer using only summaries returned in the current conversation. Never treat outputKey or session state as branch evidence. Never skip, repeat, reorder, or add Agents.";
    private static final String RESEARCHER_INSTRUCTION = "Research the assigned task, then in the same complete model response provide a concise summary and call transfer_to_agent to return to supervisor. Never transfer to peers or add tasks.";
    private static final String CRITIC_INSTRUCTION = "Critique the research summary, then in the same complete model response provide a concise critique and call transfer_to_agent to return to supervisor. Never transfer to peers or add tasks.";

    @Test
    void supervisor_research_agent_factory_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.server.agenttemplate.SupervisorResearchAgentFactory"));
    }

    @Test
    void builds_exact_supervisor_researcher_critic_tree_and_fixed_metadata() {
        var fixture = fixture();
        LlmAgent root = assertInstanceOf(LlmAgent.class, fixture.factory().apply(Schedulers.trampoline()));
        assertEquals(2, root.subAgents().size());
        LlmAgent researcher = assertInstanceOf(LlmAgent.class, root.subAgents().getFirst());
        LlmAgent critic = assertInstanceOf(LlmAgent.class, root.subAgents().getLast());
        var spec = fixture.factory().templateSpec();
        assertNotNull(spec);

        assertAll(
                () -> assertEquals("supervisor", root.name()),
                () -> assertEquals("supervisor_root_output", root.outputKey().orElseThrow()),
                () -> assertTrue(root.disallowTransferToParent()),
                () -> assertTrue(root.disallowTransferToPeers()),
                () -> assertEquals("researcher", researcher.name()),
                () -> assertEquals("researcher_output", researcher.outputKey().orElseThrow()),
                () -> assertFalse(researcher.disallowTransferToParent()),
                () -> assertTrue(researcher.disallowTransferToPeers()),
                () -> assertSame(root, researcher.parentAgent()),
                () -> assertEquals("critic", critic.name()),
                () -> assertEquals("critic_output", critic.outputKey().orElseThrow()),
                () -> assertFalse(critic.disallowTransferToParent()),
                () -> assertTrue(critic.disallowTransferToPeers()),
                () -> assertSame(root, critic.parentAgent()),
                () -> assertEquals("supervisor-research-v1", spec.codeRef()),
                () -> assertEquals(NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                        spec.topology()),
                () -> assertEquals("1", spec.inputSchemaVersion()),
                () -> assertEquals("1", spec.outputSchemaVersion()),
                () -> assertEquals(2, spec.maxDepth()),
                () -> assertEquals(4, spec.maxTransfers()),
                () -> assertEquals(List.of("researcher", "critic"), spec.requiredChildOrder()),
                () -> assertEquals(expectedAgents(), spec.agents()));
    }

    @Test
    void every_apply_builds_a_fresh_tree_with_one_shared_explicit_base_llm() {
        var fixture = fixture();
        LlmAgent first = assertInstanceOf(LlmAgent.class, fixture.factory().apply(Schedulers.trampoline()));
        LlmAgent second = assertInstanceOf(LlmAgent.class, fixture.factory().apply(Schedulers.trampoline()));
        LlmAgent firstResearcher = assertInstanceOf(LlmAgent.class, first.subAgents().getFirst());
        LlmAgent firstCritic = assertInstanceOf(LlmAgent.class, first.subAgents().getLast());
        BaseLlm firstModel = explicitModel(first);
        BaseLlm secondModel = explicitModel(second);

        assertAll(
                () -> assertNotSame(first, second),
                () -> assertNotSame(first.subAgents().getFirst(), second.subAgents().getFirst()),
                () -> assertSame(firstModel, explicitModel(firstResearcher)),
                () -> assertSame(firstModel, explicitModel(firstCritic)),
                () -> assertNotSame(firstModel, secondModel),
                () -> assertEquals(MODEL_NAME, firstModel.model()),
                () -> verify(fixture.adapter(), times(2)).create(same(fixture.model()), eq(MODEL_NAME)));
    }

    @Test
    void model_identity_and_canonical_metadata_hash_are_stable() {
        var fixture = fixture();
        var spec = fixture.factory().templateSpec();
        assertNotNull(spec);
        String hash = spec.topologyHash(POLICY);

        assertAll(
                () -> assertTrue(fixture.factory().usesChatModel(fixture.model())),
                () -> assertFalse(fixture.factory().usesChatModel(mock(ChatModel.class))),
                () -> assertEquals(MODEL_BEAN, spec.modelBeanName()),
                () -> assertEquals(MODEL_NAME, spec.modelName()),
                () -> assertEquals(ROUTE, spec.modelRouteRef()),
                () -> assertEquals(hash, fixture.factory().templateSpec().topologyHash(POLICY)),
                () -> assertTrue(hash.matches("[0-9a-f]{64}")));
    }

    @Test
    void instructions_encode_only_the_fixed_researcher_then_critic_round_trip() {
        LlmAgent root = assertInstanceOf(LlmAgent.class, fixture().factory().apply(Schedulers.trampoline()));
        LlmAgent researcher = assertInstanceOf(LlmAgent.class, root.subAgents().getFirst());
        LlmAgent critic = assertInstanceOf(LlmAgent.class, root.subAgents().getLast());

        assertAll(
                () -> assertEquals(ROOT_INSTRUCTION, instruction(root)),
                () -> assertEquals(RESEARCHER_INSTRUCTION, instruction(researcher)),
                () -> assertEquals(CRITIC_INSTRUCTION, instruction(critic)),
                () -> assertTrue(instruction(root).indexOf("researcher") < instruction(root).indexOf("critic")),
                () -> assertTrue(instruction(root).indexOf("critic") < instruction(root).indexOf("final")));
    }

    @Test
    void agents_have_no_tools_toolsets_code_executor_callbacks_or_dynamic_children() {
        LlmAgent root = assertInstanceOf(LlmAgent.class, fixture().factory().apply(Schedulers.trampoline()));
        LlmAgent researcher = assertInstanceOf(LlmAgent.class, root.subAgents().getFirst());
        LlmAgent critic = assertInstanceOf(LlmAgent.class, root.subAgents().getLast());

        assertAll(
                () -> assertClean(root),
                () -> assertClean(researcher),
                () -> assertClean(critic),
                () -> assertTrue(researcher.subAgents().isEmpty()),
                () -> assertTrue(critic.subAgents().isEmpty()));
    }

    @Test
    void null_scheduler_is_rejected_before_adapter_creation() {
        var fixture = fixture();

        assertThrows(NullPointerException.class, () -> fixture.factory().apply(null));
        verifyNoInteractions(fixture.adapter());
    }

    private static Fixture fixture() {
        ChatModel model = mock(ChatModel.class);
        var adapter = spy(new SpringAiModelAdapterFactory());
        var properties = new NativeAgentTemplateProperties(true, MODEL_BEAN, MODEL_NAME, ROUTE);
        return new Fixture(new SupervisorResearchAgentFactory(properties, model, adapter), model, adapter);
    }

    private static List<NativeAgentCompilerStrategy.AgentSpec> expectedAgents() {
        return List.of(
                new NativeAgentCompilerStrategy.AgentSpec(
                        "supervisor", null, "supervisor_root_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "researcher", "supervisor", "researcher_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "critic", "supervisor", "critic_output", false, true));
    }

    private static BaseLlm explicitModel(LlmAgent agent) {
        return assertDoesNotThrow(() -> agent.model().flatMap(com.google.adk.models.Model::model).orElseThrow());
    }

    private static String instruction(LlmAgent agent) {
        return assertInstanceOf(Instruction.Static.class, agent.instruction()).instruction();
    }

    private static void assertClean(LlmAgent agent) {
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

    private record Fixture(SupervisorResearchAgentFactory factory, ChatModel model,
                           SpringAiModelAdapterFactory adapter) { }
}
