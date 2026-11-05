package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class NativeAgentCompilerStrategyTest {
    private static final String ROUTE = "research-route@1#" + "a".repeat(64);
    private static final String OTHER_ROUTE = "research-route@2#" + "d".repeat(64);
    private static final String POLICY = "research-policy@1#" + "b".repeat(64);
    private static final String OTHER_POLICY = "research-policy@2#" + "c".repeat(64);

    @Test
    void native_agent_compiler_strategy_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy"));
    }

    @Test
    void native_agent_template_spec_is_present() {
        assertDoesNotThrow(() -> Class.forName(
                "top.egon.cola.platform.tianji.agent.infrastructure.adk.NativeAgentCompilerStrategy$TemplateSpec"));
    }

    @Test
    void bounded_topology_is_present() {
        assertDoesNotThrow(() -> NativeAgentCompilerStrategy.TransferTopologyEnum.valueOf("BOUNDED_RESEARCH_V1"));
    }

    @Test
    void preserves_the_function_map_abi_without_marking_plain_functions_as_native() {
        Function<Scheduler, ? extends BaseAgent> plainFunction = ignored -> null;

        assertAll(
                () -> assertTrue(Function.class.isAssignableFrom(NativeAgentCompilerStrategy.class)),
                () -> assertFalse(NativeAgentCompilerStrategy.class.isInstance(plainFunction)),
                () -> assertTrue(NativeAgentCompilerStrategy.AgentSpec.class.isRecord()),
                () -> assertTrue(NativeAgentCompilerStrategy.TemplateSpec.class.isRecord()),
                () -> assertArrayEquals(new NativeAgentCompilerStrategy.TransferTopologyEnum[]{
                                NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                                NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                                NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1},
                        NativeAgentCompilerStrategy.TransferTopologyEnum.values()));
    }

    @Test
    void exposes_the_two_exact_fixed_topology_contracts() {
        var conditional = conditionalSpec();
        var supervisor = supervisorSpec();

        assertAll(
                () -> assertEquals("conditional-research-v1", conditional.codeRef()),
                () -> assertEquals("conditional_root", conditional.rootAgentName()),
                () -> assertEquals(List.of("quick_research", "deep_research"), conditional.requiredChildOrder()),
                () -> assertEquals(2, conditional.maxDepth()),
                () -> assertEquals(2, conditional.maxTransfers()),
                () -> assertEquals(conditionalAgents(), conditional.agents()),
                () -> assertEquals("supervisor-research-v1", supervisor.codeRef()),
                () -> assertEquals("supervisor", supervisor.rootAgentName()),
                () -> assertEquals(List.of("researcher", "critic"), supervisor.requiredChildOrder()),
                () -> assertEquals(2, supervisor.maxDepth()),
                () -> assertEquals(4, supervisor.maxTransfers()),
                () -> assertEquals(supervisorAgents(), supervisor.agents()),
                () -> assertEquals("1", conditional.inputSchemaVersion()),
                () -> assertEquals("1", conditional.outputSchemaVersion()),
                () -> assertEquals("1", supervisor.inputSchemaVersion()),
                () -> assertEquals("1", supervisor.outputSchemaVersion()));
    }

    @Test
    void defensively_copies_and_exposes_immutable_metadata_collections() {
        var mutableAgents = new ArrayList<>(conditionalAgents());
        var mutableOrder = new ArrayList<>(List.of("quick_research", "deep_research"));
        var copied = conditionalSpec(mutableAgents, mutableOrder);
        mutableAgents.clear();
        mutableOrder.clear();
        var immutable = conditionalSpec();

        assertAll(
                () -> assertEquals(3, copied.agents().size()),
                () -> assertEquals(List.of("quick_research", "deep_research"), copied.requiredChildOrder()),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> immutable.agents().add(conditionalAgents().getFirst())),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> immutable.requiredChildOrder().add("unknown")));
    }

    @Test
    void rejects_representative_unsafe_fields_outputs_flags_schema_caps_and_order() {
        var duplicateOutput = new ArrayList<>(conditionalAgents());
        duplicateOutput.set(1, new NativeAgentCompilerStrategy.AgentSpec(
                "quick_research", "conditional_root", "conditional_root_output", false, true));
        var wrongFlags = new ArrayList<>(conditionalAgents());
        wrongFlags.set(1, new NativeAgentCompilerStrategy.AgentSpec(
                "quick_research", "conditional_root", "quick_research_output", true, false));

        assertAll(
                () -> assertInvalid(() -> new NativeAgentCompilerStrategy.AgentSpec(
                        " quick_research", "conditional_root", "quick_research_output", false, true)),
                () -> assertInvalid(() -> new NativeAgentCompilerStrategy.AgentSpec(
                        "quick\u0000research", "conditional_root", "quick_research_output", false, true)),
                () -> assertInvalid(() -> conditionalSpec(duplicateOutput,
                        List.of("quick_research", "deep_research"))),
                () -> assertInvalid(() -> new NativeAgentCompilerStrategy.AgentSpec(
                        "deep_research", "conditional_root", "app:deep_research_output", false, true)),
                () -> assertInvalid(() -> conditionalSpec(wrongFlags,
                        List.of("quick_research", "deep_research"))),
                () -> assertInvalid(() -> conditionalSpec("2", "1", conditionalAgents(), 2, 2,
                        List.of("quick_research", "deep_research"))),
                () -> assertInvalid(() -> conditionalSpec("1", "1", conditionalAgents(), 3, 2,
                        List.of("quick_research", "deep_research"))),
                () -> assertInvalid(() -> supervisorSpec(supervisorAgents(), List.of("critic", "researcher"))),
                () -> assertInvalid(() -> conditionalSpec().topologyHash(" " + POLICY)));
    }

    @Test
    void canonical_hash_covers_all_semantic_fields_and_ignores_agent_row_order_and_identity() {
        var spec = conditionalSpec();
        var reordered = conditionalSpec(List.of(
                conditionalAgents().get(2), conditionalAgents().getFirst(), conditionalAgents().get(1)),
                List.of("quick_research", "deep_research"));
        String hash = spec.topologyHash(POLICY);
        String expected = AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_AGENT_TOPOLOGY_V1", List.of(
                "conditional-research-v1", "CONDITIONAL_RESEARCH_V1",
                "researchModel", "gpt-5", ROUTE, POLICY, "1", "1", "conditional_root",
                "3",
                "conditional_root", "", "conditional_root_output", "true", "true",
                "deep_research", "conditional_root", "deep_research_output", "false", "true",
                "quick_research", "conditional_root", "quick_research_output", "false", "true",
                "2", "2", "2", "quick_research", "deep_research"));

        assertAll(
                () -> assertNotNull(hash),
                () -> assertTrue(hash.matches("[0-9a-f]{64}")),
                () -> assertEquals(expected, hash),
                () -> assertEquals(hash, reordered.topologyHash(POLICY)),
                () -> assertNotEquals(hash, spec.topologyHash(OTHER_POLICY)),
                () -> assertNotEquals(hash, supervisorSpec().topologyHash(POLICY)));
    }

    @Test
    void canonical_hash_changes_with_each_model_binding_field() {
        String hash = conditionalSpec().topologyHash(POLICY);

        assertAll(
                () -> assertNotEquals(hash,
                        conditionalSpecWithModel("otherModel", "gpt-5", ROUTE).topologyHash(POLICY)),
                () -> assertNotEquals(hash,
                        conditionalSpecWithModel("researchModel", "gpt-5-mini", ROUTE).topologyHash(POLICY)),
                () -> assertNotEquals(hash,
                        conditionalSpecWithModel("researchModel", "gpt-5", OTHER_ROUTE).topologyHash(POLICY)));
    }

    @Test
    void exposes_the_exact_bounded_topology_and_schema_tuple() {
        var bounded = assertDoesNotThrow(() -> boundedSpec());
        assertNotNull(bounded);
        assertAll(
                () -> assertEquals("bounded-research-v1", bounded.codeRef()),
                () -> assertEquals(NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1, bounded.topology()),
                () -> assertEquals("researchModel", bounded.modelBeanName()),
                () -> assertEquals("gpt-5", bounded.modelName()),
                () -> assertEquals(ROUTE, bounded.modelRouteRef()),
                () -> assertEquals("1", bounded.inputSchemaVersion()),
                () -> assertEquals("2", bounded.outputSchemaVersion()),
                () -> assertEquals("bounded_supervisor", bounded.rootAgentName()),
                () -> assertEquals(boundedAgents(), bounded.agents()),
                () -> assertEquals(2, bounded.maxDepth()),
                () -> assertEquals(16, bounded.maxTransfers()),
                () -> assertEquals(List.of("bounded_researcher", "bounded_critic"), bounded.requiredChildOrder()));
    }

    @Test
    void bounded_metadata_is_defensively_copied_and_immutable() {
        var agents = new ArrayList<>(boundedAgents());
        var pool = new ArrayList<>(List.of("bounded_researcher", "bounded_critic"));
        var bounded = assertDoesNotThrow(() -> boundedSpec(agents, pool));
        assertNotNull(bounded);
        agents.clear();
        pool.clear();
        assertAll(
                () -> assertEquals(boundedAgents(), bounded.agents()),
                () -> assertEquals(List.of("bounded_researcher", "bounded_critic"), bounded.requiredChildOrder()),
                () -> assertThrows(UnsupportedOperationException.class, () -> bounded.agents().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> bounded.requiredChildOrder().clear()));
    }

    @Test
    void rejects_non_exact_bounded_tuples_without_relaxing_legacy_schemas() {
        var sharedKey = new ArrayList<>(boundedAgents());
        sharedKey.set(1, new NativeAgentCompilerStrategy.AgentSpec(
                "bounded_researcher", "bounded_supervisor", "bounded_supervisor_output", false, true));
        var wrongFlags = new ArrayList<>(boundedAgents());
        wrongFlags.set(2, new NativeAgentCompilerStrategy.AgentSpec(
                "bounded_critic", "bounded_supervisor", "bounded_critic_output", true, false));
        var wrongParent = new ArrayList<>(boundedAgents());
        wrongParent.set(1, new NativeAgentCompilerStrategy.AgentSpec(
                "bounded_researcher", "other_parent", "bounded_researcher_output", false, true));
        var unknownChild = new ArrayList<>(boundedAgents());
        unknownChild.set(2, new NativeAgentCompilerStrategy.AgentSpec(
                "unknown", "bounded_supervisor", "unknown_output", false, true));
        var pool = List.of("bounded_researcher", "bounded_critic");
        assertAll(
                () -> assertInvalid(() -> boundedSpec("other-v1", "1", "2", "bounded_supervisor", boundedAgents(), 2, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "1", "bounded_supervisor", boundedAgents(), 2, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "2", "2", "bounded_supervisor", boundedAgents(), 2, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "2", "other_root", boundedAgents(), 2, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "2", "bounded_supervisor", boundedAgents(), 1, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "2", "bounded_supervisor", boundedAgents(), 3, 16, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "2", "bounded_supervisor", boundedAgents(), 2, 15, pool)),
                () -> assertInvalid(() -> boundedSpec("bounded-research-v1", "1", "2", "bounded_supervisor", boundedAgents(), 2, 17, pool)),
                () -> assertInvalid(() -> boundedSpec(boundedAgents(), List.of("bounded_critic", "bounded_researcher"))),
                () -> assertInvalid(() -> boundedSpec(sharedKey, pool)),
                () -> assertInvalid(() -> boundedSpec(wrongFlags, pool)),
                () -> assertInvalid(() -> boundedSpec(wrongParent, pool)),
                () -> assertInvalid(() -> boundedSpec(unknownChild, pool)),
                () -> assertInvalid(() -> boundedSpec(boundedAgents().subList(0, 2), pool)),
                () -> assertInvalid(() -> new NativeAgentCompilerStrategy.AgentSpec(
                        "bounded_critic", "bounded_supervisor", "user:bounded_critic_output", false, true)),
                () -> assertInvalid(() -> conditionalSpec("1", "2", conditionalAgents(), 2, 2,
                        List.of("quick_research", "deep_research"))),
                () -> assertInvalid(() -> new NativeAgentCompilerStrategy.TemplateSpec("supervisor-research-v1",
                        NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                        "researchModel", "gpt-5", ROUTE, "1", "2", "supervisor", supervisorAgents(), 2, 4,
                        List.of("researcher", "critic"))));
    }

    @Test
    void bounded_canonical_hash_is_framed_and_independent_of_agent_row_order() {
        var bounded = assertDoesNotThrow(() -> boundedSpec());
        assertNotNull(bounded);
        var reordered = assertDoesNotThrow(() -> boundedSpec(List.of(
                boundedAgents().get(2), boundedAgents().getFirst(), boundedAgents().get(1)),
                List.of("bounded_researcher", "bounded_critic")));
        assertNotNull(reordered);
        String hash = assertDoesNotThrow(() -> bounded.topologyHash(POLICY));
        assertNotNull(hash);
        assertAll(
                () -> assertTrue(hash.matches("[0-9a-f]{64}")),
                () -> assertEquals("b84f8a2f22144ad5bf20baee2d20718e963c06ecee90db510a751a0e55807c29", hash),
                () -> assertEquals(hash, assertDoesNotThrow(() -> reordered.topologyHash(POLICY))),
                () -> assertNotEquals(hash, assertDoesNotThrow(() -> bounded.topologyHash(OTHER_POLICY))),
                () -> assertNotEquals(hash, assertDoesNotThrow(() -> conditionalSpec().topologyHash(POLICY))),
                () -> assertNotEquals(hash, assertDoesNotThrow(() -> supervisorSpec().topologyHash(POLICY))));
    }

    @Test
    void legacy_topology_hashes_remain_the_baseline_golden_values() {
        assertAll(
                () -> assertEquals("8ed9bfc9b922e5eab0993ce4d9fb0d89ea12190a269ac7db3dfe0c34b9479876",
                        assertDoesNotThrow(() -> conditionalSpec().topologyHash(POLICY))),
                () -> assertEquals("2a77e2ceed3356091a140cb7a6bf91a6cc49a920340015d0ff2c15ddd5132657",
                        assertDoesNotThrow(() -> supervisorSpec().topologyHash(POLICY))));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec boundedSpec() {
        return boundedSpec(boundedAgents(), List.of("bounded_researcher", "bounded_critic"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec boundedSpec(
            List<NativeAgentCompilerStrategy.AgentSpec> agents, List<String> requiredChildOrder) {
        return boundedSpec("bounded-research-v1", "1", "2", "bounded_supervisor", agents, 2, 16, requiredChildOrder);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec boundedSpec(
            String codeRef, String inputSchemaVersion, String outputSchemaVersion, String rootAgentName,
            List<NativeAgentCompilerStrategy.AgentSpec> agents, int maxDepth, int maxTransfers,
            List<String> requiredChildOrder) {
        return new NativeAgentCompilerStrategy.TemplateSpec(codeRef,
                NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, inputSchemaVersion, outputSchemaVersion,
                rootAgentName, agents, maxDepth, maxTransfers, requiredChildOrder);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec conditionalSpec() {
        return conditionalSpec(conditionalAgents(), List.of("quick_research", "deep_research"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec conditionalSpec(
            List<NativeAgentCompilerStrategy.AgentSpec> agents, List<String> requiredChildOrder) {
        return conditionalSpec("1", "1", agents, 2, 2, requiredChildOrder);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec conditionalSpec(
            String inputSchemaVersion, String outputSchemaVersion,
            List<NativeAgentCompilerStrategy.AgentSpec> agents, int maxDepth, int maxTransfers,
            List<String> requiredChildOrder) {
        return new NativeAgentCompilerStrategy.TemplateSpec("conditional-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, inputSchemaVersion, outputSchemaVersion,
                "conditional_root", agents, maxDepth, maxTransfers, requiredChildOrder);
    }

    private static NativeAgentCompilerStrategy.TemplateSpec conditionalSpecWithModel(
            String modelBeanName, String modelName, String modelRouteRef) {
        return new NativeAgentCompilerStrategy.TemplateSpec("conditional-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.CONDITIONAL_RESEARCH_V1,
                modelBeanName, modelName, modelRouteRef, "1", "1", "conditional_root",
                conditionalAgents(), 2, 2, List.of("quick_research", "deep_research"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec supervisorSpec() {
        return supervisorSpec(supervisorAgents(), List.of("researcher", "critic"));
    }

    private static NativeAgentCompilerStrategy.TemplateSpec supervisorSpec(
            List<NativeAgentCompilerStrategy.AgentSpec> agents, List<String> requiredChildOrder) {
        return new NativeAgentCompilerStrategy.TemplateSpec("supervisor-research-v1",
                NativeAgentCompilerStrategy.TransferTopologyEnum.SUPERVISOR_RESEARCH_V1,
                "researchModel", "gpt-5", ROUTE, "1", "1", "supervisor",
                agents, 2, 4, requiredChildOrder);
    }

    private static List<NativeAgentCompilerStrategy.AgentSpec> conditionalAgents() {
        return List.of(
                new NativeAgentCompilerStrategy.AgentSpec(
                        "conditional_root", null, "conditional_root_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "quick_research", "conditional_root", "quick_research_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "deep_research", "conditional_root", "deep_research_output", false, true));
    }

    private static List<NativeAgentCompilerStrategy.AgentSpec> supervisorAgents() {
        return List.of(
                new NativeAgentCompilerStrategy.AgentSpec(
                        "supervisor", null, "supervisor_root_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "researcher", "supervisor", "researcher_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "critic", "supervisor", "critic_output", false, true));
    }

    private static List<NativeAgentCompilerStrategy.AgentSpec> boundedAgents() {
        return List.of(
                new NativeAgentCompilerStrategy.AgentSpec(
                        "bounded_supervisor", null, "bounded_supervisor_output", true, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "bounded_researcher", "bounded_supervisor", "bounded_researcher_output", false, true),
                new NativeAgentCompilerStrategy.AgentSpec(
                        "bounded_critic", "bounded_supervisor", "bounded_critic_output", false, true));
    }

    private static void assertInvalid(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }
}
