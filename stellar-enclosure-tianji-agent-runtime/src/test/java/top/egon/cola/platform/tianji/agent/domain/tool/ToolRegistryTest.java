package top.egon.cola.platform.tianji.agent.domain.tool;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {
    private static final ToolSchema INPUT = new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER, "confirm", ToolSchema.ValueType.BOOLEAN, "comment", ToolSchema.ValueType.STRING), Set.of("amount", "confirm"));
    private static final ToolSchema OUTPUT = new ToolSchema(Map.of("reference", ToolSchema.ValueType.STRING), Set.of("reference"));
    private static final ToolPolicy READ = new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "process.get", "tianji.api.process.instance.get", "a".repeat(64), null);

    @Test void schema_copies_and_normalizes_only_bounded_declared_primitive_fields() {
        var properties = new LinkedHashMap<>(INPUT.fields()); var required = new java.util.HashSet<>(INPUT.required()); var schema = new ToolSchema(properties, required); properties.clear(); required.clear();
        assertEquals(3, schema.fields().size()); assertEquals(2, schema.required().size());
        assertEquals(Map.of("amount", 20L, "confirm", true), schema.values(Map.of("amount", BigInteger.valueOf(20), "confirm", true)));
        assertThrows(UnsupportedOperationException.class, () -> schema.fields().clear()); assertThrows(UnsupportedOperationException.class, () -> schema.required().clear());
        assertThrows(UnsupportedOperationException.class, () -> schema.values(Map.of("amount", 20, "confirm", true)).clear());
        for (var invalid : List.of(Map.<String, Object>of("amount", "20", "confirm", true), Map.<String, Object>of("amount", 20, "confirm", "true"), Map.<String, Object>of("amount", 20.0, "confirm", true),
                Map.<String, Object>of("amount", 20, "confirm", true, "tenantId", 21), Map.<String, Object>of("amount", 20), Map.<String, Object>of("amount", 20, "confirm", true, "comment", Map.of("value", "nested")),
                Map.<String, Object>of("amount", 1_000_000_000_000_000L, "confirm", true), Map.<String, Object>of("amount", 20, "confirm", true, "comment", "x".repeat(1025)),
                Map.<String, Object>of("amount", 20, "confirm", true, "comment", String.valueOf((char) 0xd800)))) assertThrows(IllegalArgumentException.class, () -> schema.values(invalid));
        assertThrows(IllegalArgumentException.class, () -> new ToolSchema(Map.of("amount", ToolSchema.ValueType.INTEGER), Set.of("missing")));
    }

    @Test void fingerprint_is_order_independent_and_covers_schema_required_fields_policy_binding_and_display_metadata() {
        var tool = descriptor("process.get", 1, "get_process", READ); var reversed = new LinkedHashMap<String, ToolSchema.ValueType>();
        INPUT.fields().entrySet().stream().sorted(Map.Entry.<String, ToolSchema.ValueType>comparingByKey().reversed()).forEach(entry -> reversed.put(entry.getKey(), entry.getValue()));
        assertEquals(tool.reference(), new ToolDescriptor(tool.key(), tool.version(), tool.name(), tool.description(), new ToolSchema(reversed, INPUT.required()), OUTPUT, READ).reference());
        assertNotEquals(tool.reference(), new ToolDescriptor(tool.key(), tool.version(), tool.name(), "changed description", INPUT, OUTPUT, READ).reference());
        assertNotEquals(tool.reference(), new ToolDescriptor(tool.key(), tool.version(), tool.name(), tool.description(), new ToolSchema(INPUT.fields(), Set.of("amount")), OUTPUT, READ).reference());
        var moved = new ToolPolicy(READ.risk(), READ.actionKey(), READ.executionResourceKey(), "b".repeat(64), null);
        assertNotEquals(tool.reference(), descriptor(tool.key(), tool.version(), tool.name(), moved).reference());
        assertNotEquals(tool.reference(), descriptor(tool.key(), 2, tool.name(), READ).reference());
        assertEquals("process.get@1", tool.versionKey());
    }

    @Test void selected_refs_have_exact_hashes_unique_function_names_and_an_immutable_sorted_visible_set() {
        var first = descriptor("process.get", 1, "a_get_process", READ); var second = descriptor("decision.get", 1, "z_get_decision", READ); var registrations = new ArrayList<>(List.of(second, first));
        var registry = new ToolRegistry(registrations); registrations.clear(); var policy = policy(Set.of(first.versionKey(), second.versionKey()), 5, null);
        var selected = registry.visible(snapshot(List.of(first.reference(), second.reference()), policy, policy));
        assertEquals(List.of(first.name(), second.name()), new ArrayList<>(selected.keySet())); assertThrows(UnsupportedOperationException.class, selected::clear);
        var bad = new DefinitionResourceRef(first.key(), first.version(), "f".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(bad, second.reference()), policy, policy)));
        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(first, first)));
        var alias = descriptor("process.get", 2, first.name(), READ); var ambiguous = policy(Set.of(first.versionKey(), alias.versionKey()), 5, null);
        assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of(first, alias)).visible(snapshot(List.of(first.reference(), alias.reference()), ambiguous, ambiguous)));
        assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(first.reference(), first.reference()), policy, policy)));
    }

    @Test void a_narrower_allowlist_or_zero_tool_budget_hides_unavailable_tools_without_admitting_an_undeclared_or_expanded_version() {
        var read = descriptor("process.get", 1, "get_process", READ); var hidden = descriptor("process.write", 1, "write_process", READ);
        var registry = new ToolRegistry(List.of(read)); var parent = policy(Set.of(read.versionKey(), hidden.versionKey()), 5, null); var narrow = policy(Set.of(read.versionKey()), 2, null);
        assertEquals(Set.of(read.name()), registry.visible(snapshot(List.of(read.reference(), hidden.reference()), parent, narrow)).keySet());
        assertEquals(Map.of(), registry.visible(snapshot(List.of(read.reference(), hidden.reference()), parent, policy(parent.allowedToolVersions(), 0, null))));
        assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(read.reference(), hidden.reference()), narrow, parent)));
        assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(read.reference()), parent, parent)));
        assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(read.reference()), narrow, policy(narrow.allowedToolVersions(), 3, null))));
    }

    @Test void approval_required_metadata_cannot_lose_or_replace_the_definition_policy_reference() {
        var risk = new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "process.start", "tianji.api.process.instance.start", "a".repeat(64), "approval.v1"); var tool = descriptor("process.start", 1, "start_process", risk);
        var registry = new ToolRegistry(List.of(tool)); var approvedPolicy = policy(Set.of(tool.versionKey()), 5, "approval.v1");
        assertEquals(Set.of(tool.name()), registry.visible(snapshot(List.of(tool.reference()), approvedPolicy, approvedPolicy)).keySet());
        var missing = policy(approvedPolicy.allowedToolVersions(), 5, null); assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(tool.reference()), missing, missing)));
        var replaced = policy(approvedPolicy.allowedToolVersions(), 5, "approval.v2"); assertThrows(IllegalArgumentException.class, () -> registry.visible(snapshot(List.of(tool.reference()), approvedPolicy, replaced)));
        assertThrows(IllegalArgumentException.class, () -> new ToolPolicy(ToolPolicy.Risk.READ_ONLY, "read", "resource", "a".repeat(64), "approval.v1"));
        assertThrows(IllegalArgumentException.class, () -> new ToolPolicy(ToolPolicy.Risk.APPROVAL_REQUIRED, "write", "resource", "a".repeat(64), null));
    }

    private static ToolDescriptor descriptor(String key, long version, String name, ToolPolicy policy) { return new ToolDescriptor(key, version, name, "Fixed registered tool", INPUT, OUTPUT, policy); }
    private static ExecutionPolicyDTO policy(Set<String> versions, int toolCalls, String approval) { return new ExecutionPolicyDTO(Duration.ofMinutes(5), Duration.ofSeconds(30), Duration.ofSeconds(20), 10, toolCalls, 1000, 500, null, null, 4, versions, "retry.v1", approval); }
    private static AgentExecutionSnapshot snapshot(List<DefinitionResourceRef> refs, ExecutionPolicyDTO parent, ExecutionPolicyDTO effective) {
        var definition = new AgentDefinitionSpecDTO("tools", 1, "CODE_FIRST", "trusted", "input.v1", "output.v1", new DefinitionDependenciesDTO(List.of(), List.of(), refs, "model", "policy"), parent, Map.of());
        return new AgentExecutionSnapshot(definition, "a".repeat(64), effective, new AgentInputDTO("Question", List.of(), Map.of()), "business");
    }
}
