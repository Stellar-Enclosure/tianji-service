package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.BaseAgent;
import io.reactivex.rxjava3.core.Scheduler;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public interface NativeAgentCompilerStrategy extends Function<Scheduler, BaseAgent> {
    TemplateSpec templateSpec();

    boolean usesChatModel(ChatModel candidate);

    enum TransferTopologyEnum {
        CONDITIONAL_RESEARCH_V1,
        SUPERVISOR_RESEARCH_V1,
        BOUNDED_RESEARCH_V1
    }

    record AgentSpec(String name, String parentName, String outputKey,
                     boolean disallowTransferToParent, boolean disallowTransferToPeers) {
        public AgentSpec {
            name = requireSafe(name, "Agent name", 128);
            if (parentName != null) parentName = requireSafe(parentName, "Agent parent name", 128);
            outputKey = requireSafe(outputKey, "Agent output key", 128);
            if (outputKey.startsWith("app:") || outputKey.startsWith("user:")
                    || outputKey.startsWith("temp:")) {
                throw invalid();
            }
        }
    }

    record TemplateSpec(String codeRef, TransferTopologyEnum topology,
                        String modelBeanName, String modelName, String modelRouteRef,
                        String inputSchemaVersion, String outputSchemaVersion,
                        String rootAgentName, List<AgentSpec> agents, int maxDepth,
                        int maxTransfers, List<String> requiredChildOrder) {
        public TemplateSpec {
            codeRef = requireSafe(codeRef, "Template code reference", 256);
            if (topology == null) throw invalid();
            modelBeanName = requireSafe(modelBeanName, "Model bean name", 256);
            modelName = requireSafe(modelName, "Model name", 512);
            modelRouteRef = requireVersionedRef(modelRouteRef, "Model route reference");
            if (!"1".equals(inputSchemaVersion)
                    || !(topology == TransferTopologyEnum.BOUNDED_RESEARCH_V1 ? "2" : "1").equals(outputSchemaVersion)) {
                throw invalid();
            }
            rootAgentName = requireSafe(rootAgentName, "Root Agent name", 128);
            if (agents == null || requiredChildOrder == null) throw invalid();
            try {
                agents = List.copyOf(agents);
                requiredChildOrder = List.copyOf(requiredChildOrder);
            } catch (NullPointerException failure) {
                throw invalid();
            }
            requiredChildOrder.forEach(value -> requireSafe(value, "Required child name", 128));
            validateTopology(codeRef, topology, rootAgentName, agents, maxDepth, maxTransfers,
                    requiredChildOrder);
        }

        public String topologyHash(String snapshotPolicyVersionRef) {
            String policyVersionRef = requireVersionedRef(snapshotPolicyVersionRef, "Policy version reference");
            List<String> fields = new ArrayList<>();
            fields.add(codeRef);
            fields.add(topology.name());
            fields.add(modelBeanName);
            fields.add(modelName);
            fields.add(modelRouteRef);
            fields.add(policyVersionRef);
            fields.add(inputSchemaVersion);
            fields.add(outputSchemaVersion);
            fields.add(rootAgentName);
            fields.add(Integer.toString(agents.size()));
            agents.stream().sorted(Comparator.comparing(AgentSpec::name)).forEach(agent -> {
                fields.add(agent.name());
                fields.add(agent.parentName() == null ? "" : agent.parentName());
                fields.add(agent.outputKey());
                fields.add(Boolean.toString(agent.disallowTransferToParent()));
                fields.add(Boolean.toString(agent.disallowTransferToPeers()));
            });
            fields.add(Integer.toString(maxDepth));
            fields.add(Integer.toString(maxTransfers));
            fields.add(Integer.toString(requiredChildOrder.size()));
            fields.addAll(requiredChildOrder);
            return AdkSemanticJsonDigest.fingerprint("TIANJI_NATIVE_AGENT_TOPOLOGY_V1", fields);
        }
    }

    private static void validateTopology(String codeRef, TransferTopologyEnum topology,
                                         String rootAgentName, List<AgentSpec> agents,
                                         int maxDepth, int maxTransfers,
                                         List<String> requiredChildOrder) {
        if (agents.size() != 3 || maxDepth != 2) throw invalid();
        Set<String> names = new HashSet<>();
        Set<String> outputKeys = new HashSet<>();
        for (AgentSpec agent : agents) {
            if (!names.add(agent.name()) || !outputKeys.add(agent.outputKey())) throw invalid();
        }
        switch (topology) {
            case CONDITIONAL_RESEARCH_V1 -> {
                if (!"conditional-research-v1".equals(codeRef)
                        || !"conditional_root".equals(rootAgentName) || maxTransfers != 2
                        || !requiredChildOrder.equals(List.of("quick_research", "deep_research"))) {
                    throw invalid();
                }
                requireAgent(agents, "conditional_root", null, "conditional_root_output", true, true);
                requireAgent(agents, "quick_research", "conditional_root", "quick_research_output", false, true);
                requireAgent(agents, "deep_research", "conditional_root", "deep_research_output", false, true);
            }
            case SUPERVISOR_RESEARCH_V1 -> {
                if (!"supervisor-research-v1".equals(codeRef)
                        || !"supervisor".equals(rootAgentName) || maxTransfers != 4
                        || !requiredChildOrder.equals(List.of("researcher", "critic"))) {
                    throw invalid();
                }
                requireAgent(agents, "supervisor", null, "supervisor_root_output", true, true);
                requireAgent(agents, "researcher", "supervisor", "researcher_output", false, true);
                requireAgent(agents, "critic", "supervisor", "critic_output", false, true);
            }
            case BOUNDED_RESEARCH_V1 -> {
                if (!"bounded-research-v1".equals(codeRef)
                        || !"bounded_supervisor".equals(rootAgentName) || maxTransfers != 16
                        || !requiredChildOrder.equals(List.of("bounded_researcher", "bounded_critic"))) {
                    throw invalid();
                }
                requireAgent(agents, "bounded_supervisor", null, "bounded_supervisor_output", true, true);
                requireAgent(agents, "bounded_researcher", "bounded_supervisor", "bounded_researcher_output", false, true);
                requireAgent(agents, "bounded_critic", "bounded_supervisor", "bounded_critic_output", false, true);
            }
        }
    }

    private static void requireAgent(List<AgentSpec> agents, String name, String parentName,
                                     String outputKey, boolean disallowParent, boolean disallowPeers) {
        AgentSpec expected = new AgentSpec(name, parentName, outputKey, disallowParent, disallowPeers);
        if (agents.stream().noneMatch(expected::equals)) throw invalid();
    }

    private static String requireVersionedRef(String value, String name) {
        String safe = requireSafe(value, name, 256);
        if (!safe.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}@[1-9][0-9]{0,18}#[0-9a-f]{64}")) {
            throw invalid();
        }
        return safe;
    }

    private static String requireSafe(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim())) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isISOControl(current) || Character.isLowSurrogate(current)) throw invalid();
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index))) throw invalid();
            }
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Native Agent template metadata is invalid");
    }
}
