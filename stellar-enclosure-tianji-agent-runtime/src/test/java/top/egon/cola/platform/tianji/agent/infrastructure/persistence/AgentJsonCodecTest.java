package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentJsonCodecTest {
    private final AgentJsonCodec codec = new AgentJsonCodec();

    @Test
    void definition_hash_is_stable_across_map_and_tool_set_order() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 2);
        first.put("a", Map.of("b", 1));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", Map.of("b", 1));
        second.put("z", 2);
        AgentDefinitionSpecDTO a = spec(1, first, Set.of("read@1", "search@1"));
        AgentDefinitionSpecDTO b = spec(2, second, Set.of("search@1", "read@1"));
        assertEquals(codec.definitionHash(a), codec.definitionHash(b));
        assertEquals(a, codec.readDefinition(codec.write(a, AgentJsonCodec.MAX_SNAPSHOT_BYTES)));
        assertNotEquals(codec.definitionHash(a), codec.definitionHash(spec(1, Map.of("z", 3), Set.of("read@1"))));
    }

    @Test
    void idempotency_hash_ignores_correlation_but_includes_business_input_and_policy() {
        AgentInputDTO input = new AgentInputDTO("question", List.of(), Map.of("x", 1));
        StartAgentRunCommand a = new StartAgentRunCommand("review", 1, null, "key", "business", "trace-a", input, null);
        StartAgentRunCommand b = new StartAgentRunCommand("review", 1, null, "key", "business", "trace-b", input, null);
        StartAgentRunCommand different = new StartAgentRunCommand("review", 1, null, "key", "other", "trace-a", input, null);
        StartAgentRunCommand limited = new StartAgentRunCommand("review", 1, null, "key", "business", "trace-a", input,
                new ExecutionPolicyOverrideDTO(Duration.ofSeconds(5), null, null, null, null, null, null, null, null, null));
        assertEquals(codec.requestHash(a), codec.requestHash(b));
        assertNotEquals(codec.requestHash(a), codec.requestHash(different));
        assertNotEquals(codec.requestHash(a), codec.requestHash(limited));
    }

    @Test
    void execution_authorization_fingerprint_is_canonical_and_binds_the_effective_input() {
        AgentDefinitionSpecDTO definition = spec(1, Map.of("mode", "safe"), Set.of("search@1", "read@1"));
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("z", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("z", 2);
        String definitionHash = codec.definitionHash(definition);
        var a = new AgentExecutionSnapshot(definition, definitionHash, definition.policy(),
                new AgentInputDTO("question", List.of(), first), "business");
        var b = new AgentExecutionSnapshot(definition, definitionHash, definition.policy(),
                new AgentInputDTO("question", List.of(), second), "business");
        var altered = new AgentExecutionSnapshot(definition, definitionHash, definition.policy(),
                new AgentInputDTO("other", List.of(), second), "business");
        assertEquals(codec.executionSnapshotHash(a), codec.executionSnapshotHash(b));
        assertNotEquals(codec.executionSnapshotHash(a), codec.executionSnapshotHash(altered));
    }

    @Test
    void rejects_payloads_over_the_utf8_size_limit() {
        assertThrows(IllegalArgumentException.class, () -> codec.write(Map.of("value", "汉".repeat(50)), 100));
    }

    @Test
    void linked_snapshots_bind_the_process_occurrence_without_changing_legacy_snapshot_format() {
        var definition = spec(1, Map.of("mode", "safe"), Set.of());
        var definitionHash = codec.definitionHash(definition);
        var command = new StartAgentRunCommand("review", 1, null, "key", "business", "trace-a",
                new AgentInputDTO("question", List.of(), Map.of()), null);
        String legacy = codec.snapshot(definition, definitionHash, definition.policy(), command);
        assertFalse(legacy.contains("processLink"));
        var legacySnapshot = codec.readSnapshot(legacy);
        assertNull(legacySnapshot.processLink());
        assertEquals("df2e036acbff36ef8b11d7c8e17c9fb4c0306ace5b09039ab7167561a2c2b241",
                codec.executionSnapshotHash(legacySnapshot));
        var link = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance-1", "occurrence-1",
                definitionHash, Instant.parse("2026-10-30T00:00:00.123456Z"));
        String linked = codec.snapshot(definition, definitionHash, definition.policy(), command, link);
        var linkedSnapshot = codec.readSnapshot(linked);
        assertEquals(link, linkedSnapshot.processLink());
        assertNotEquals(codec.executionSnapshotHash(legacySnapshot), codec.executionSnapshotHash(linkedSnapshot));
        var changed = new AgentProcessLink("process-east", WorkerScopeTypeEnum.BPMN, "instance-1", "occurrence-2",
                definitionHash, link.dispatchNotAfter());
        assertNotEquals(codec.linkedRequestHash(command, link), codec.linkedRequestHash(command, changed));
        assertNotEquals(codec.processLinkHash(link), codec.processLinkHash(changed));
        var differentTrace = new StartAgentRunCommand("review", 1, null, "key", "business", "trace-b", command.input(), null);
        assertNotEquals(codec.linkedRequestHash(command, link), codec.linkedRequestHash(differentTrace, link));
    }

    static AgentDefinitionSpecDTO spec(long version, Map<String, Object> configuration, Set<String> tools) {
        ExecutionPolicyDTO policy = new ExecutionPolicyDTO(Duration.ofMinutes(1), Duration.ofSeconds(20),
                Duration.ofSeconds(10), 5, 2, 1000, 500, new BigDecimal("2.00"), "USD", 2,
                tools, "retry-v1", "approval-v1");
        return new AgentDefinitionSpecDTO("review", version, "SEQUENTIAL", null, "1", "1",
                new DefinitionDependenciesDTO(List.of(), List.of(), List.of(), "model-route", "policy-v1"),
                policy, configuration);
    }
}
