package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.SiResourceVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.VersionIdentityVO;
import top.egon.cola.component.tianshu.model.si.DdcSiDependencyManifestVO.EdgeVO;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentInputDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionDependenciesDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    static final String SI_PROMPT_SPEC = "{\"promptText\":\"Approved governed instruction\"}";
    static final String SI_PROMPT_DIGEST = rawSiDigest(SI_PROMPT_SPEC);

    @Test
    void native_authorization_and_future_unknown_event_types_keep_the_existing_json_envelope() {
        Map<String, Object> payload = Map.of("to", "quick_research", "ordinal", 1,
                "topologyHash", "a".repeat(64), "state", "AUTHORIZED");
        for (String type : List.of("NATIVE_TRANSFER_AUTHORIZED", "FUTURE_REVIEWED_EVENT")) {
            var event = new AgentEventDTO("event-1", "run-1", 1, type, "conditional_root", null, null,
                    Instant.parse("2026-11-05T00:00:00.123456Z"), "trace-1", "1", payload);
            var stored = codec.readObject(codec.write(event, AgentJsonCodec.MAX_EVENT_BYTES));
            assertEquals(type, stored.get("type"));
            assertEquals("1", stored.get("payloadSchemaVersion"));
            assertEquals(payload, stored.get("payload"));
            assertEquals(Set.of("to", "ordinal", "topologyHash", "state"), event.payload().keySet());
            assertEquals("2026-11-05T00:00:00.123456Z", stored.get("occurredAt"));
        }
    }

    @Test
    void schema_two_ordered_task_maps_round_trip_through_the_existing_json_contract() {
        List<Map<String, Object>> tasks = List.of(
                Map.of("taskId", "first", "agent", "bounded_researcher", "summary", "one"),
                Map.of("taskId", "second", "agent", "bounded_researcher", "summary", "two"));
        var result = assertDoesNotThrow(() -> new AgentExecutionResult("run-1", AgentRunStatusEnum.COMPLETED,
                Map.of("text", "final", "agents", Map.of("bounded_supervisor", "final", "bounded_researcher", "two"),
                        "tasks", tasks), null, "2", null, null, "UNKNOWN"));
        String json = assertDoesNotThrow(() -> codec.write(result, AgentJsonCodec.MAX_RESULT_BYTES));
        var decoded = assertDoesNotThrow(() -> codec.readResult(json));
        assertEquals(result, decoded);
        var storedTasks = assertInstanceOf(List.class, decoded.output().get("tasks"));
        assertEquals(tasks, storedTasks);
        for (Object task : storedTasks) {
            var map = assertInstanceOf(Map.class, task);
            assertEquals(Set.of("taskId", "agent", "summary"), map.keySet());
        }
        assertFalse(json.contains("question"));
        assertThrows(UnsupportedOperationException.class, () -> decoded.output().put("tasks", List.of()));
        assertThrows(UnsupportedOperationException.class, storedTasks::clear);
    }

    @Test
    void raw_task_records_are_rejected_before_the_codec_and_old_schema_one_is_unchanged() {
        record RawTaskVO(String taskId, String agent, String summary) { }
        assertThrows(IllegalArgumentException.class, () -> new AgentExecutionResult("run-1",
                AgentRunStatusEnum.COMPLETED, Map.of("tasks", List.of(new RawTaskVO("first", "bounded_researcher", "one"))),
                null, "2", null, null, "UNKNOWN"));
        var legacy = new AgentExecutionResult("run-1", AgentRunStatusEnum.COMPLETED,
                Map.of("text", "final", "agents", Map.of("conditional_root", "final")),
                null, "1", null, null, "UNKNOWN");
        String legacyJson = assertDoesNotThrow(() -> codec.write(legacy, AgentJsonCodec.MAX_RESULT_BYTES));
        assertEquals(legacy, codec.readResult(legacyJson));
        assertFalse(legacyJson.contains("tasks"));
        assertThrows(IllegalArgumentException.class, () -> codec.write(
                Map.of("summary", "汉".repeat(AgentJsonCodec.MAX_RESULT_BYTES / 3 + 1)), AgentJsonCodec.MAX_RESULT_BYTES));
    }

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

    @Test
    void historical_runtime_snapshot_keeps_original_hash_without_inventing_si_evidence() throws Exception {
        var template = runtimeTemplate("a".repeat(64));
        var command = runtimeCommand(template);
        var original = codec.runtimeSnapshot("b".repeat(64), "run-history", command, template);
        String stored = codec.writeRuntimeSnapshot(original);
        assertEquals(Set.of("runtimeProfile", "snapshotSha256", "ownerSha256", "runId", "command", "template"),
                codec.readObject(stored).keySet());
        var loaded = codec.readRuntimeSnapshot(stored);
        assertEquals("bc6dbfaae66889153d1b935b507370ba3dc8c344a0a77ea20828ad22a1c9d95f", original.snapshotSha256());
        assertEquals(original.snapshotSha256(), loaded.snapshotSha256());
        assertEquals(stored, codec.writeRuntimeSnapshot(loaded));
        assertNull(loaded.getClass().getDeclaredMethod("siBundle").invoke(loaded),
                "pre-SI persisted history must remain explicitly uninstrumented");
    }

    @Test
    void si_snapshot_binds_the_complete_manifest_and_all_material_under_its_hash() throws Exception {
        var template = runtimeTemplate(SI_PROMPT_DIGEST);
        var command = runtimeCommand(template);
        var bundle = frozenBundle(31L, template, SI_PROMPT_SPEC);
        var snapshot = codec.runtimeSnapshot("b".repeat(64), "run-frozen", command, template, bundle);
        String stored = codec.writeRuntimeSnapshot(snapshot);
        var loaded = codec.readRuntimeSnapshot(stored);
        assertEquals(bundle, loaded.siBundle());
        assertEquals(bundle.closureDigest(), loaded.siBundle().closureDigest());
        assertEquals(snapshot.snapshotSha256(), loaded.snapshotSha256());
        assertEquals(AgentLedgerService.RunProfileEnum.RUNTIME_V2, codec.persistedProfile(stored));
        assertNotEquals(codec.runtimeSnapshot("b".repeat(64), "run-frozen", command, template).snapshotSha256(),
                snapshot.snapshotSha256());
        var altered = frozenBundle(31L, template, "{\"promptText\":\"A different immutable instruction\"}");
        assertNotEquals(snapshot.snapshotSha256(),
                codec.runtimeSnapshot("b".repeat(64), "run-frozen", command, template, altered).snapshotSha256());
        assertFalse(snapshot.toString().contains("Approved governed instruction"));
        assertFalse(stored.contains("accessToken"));
        assertFalse(stored.contains("clientSecret"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void si_snapshot_rejects_missing_null_unknown_numeric_and_tampered_material() throws Exception {
        var template = runtimeTemplate(SI_PROMPT_DIGEST);
        var snapshot = codec.runtimeSnapshot("b".repeat(64), "run-frozen", runtimeCommand(template), template,
                frozenBundle(31L, template, SI_PROMPT_SPEC));
        String stored = codec.writeRuntimeSnapshot(snapshot);
        for (String mutation : List.of("null", "remove", "digest", "body", "version", "numeric", "unknown")) {
            Map<String, Object> wire = codec.readObject(stored);
            Map<String, Object> bundle = (Map<String, Object>) wire.get("siBundle");
            Map<String, Object> manifest = (Map<String, Object>) bundle.get("manifest");
            switch (mutation) {
                case "null" -> wire.put("siBundle", null);
                case "remove" -> wire.remove("siBundle");
                case "digest" -> manifest.put("closureDigest", "f".repeat(64));
                case "body" -> ((Map<String, Object>) ((List<?>) bundle.get("resources")).getFirst())
                        .put("specJson", "{\"modelKey\":\"another-model\"}");
                case "version" -> ((Map<String, Object>) manifest.get("root")).put("versionNo", "2");
                case "numeric" -> ((Map<String, Object>) manifest.get("root")).put("versionId", 201);
                case "unknown" -> bundle.put("accessToken", "untrusted-never-accepted");
                default -> throw new AssertionError(mutation);
            }
            assertThrows(IllegalStateException.class,
                    () -> codec.readRuntimeSnapshot(codec.write(wire, AgentJsonCodec.MAX_SNAPSHOT_BYTES)), mutation);
        }
    }

    @Test
    void only_new_si_snapshots_have_the_larger_bounded_material_budget() throws Exception {
        var template = runtimeTemplate(SI_PROMPT_DIGEST);
        var small = frozenBundle(31L, template, SI_PROMPT_SPEC);
        String skillSpec = "{\"skillMarkdown\":\"" + "x".repeat(1_048_000) + "\"}";
        var skill = new VersionIdentityVO("31", "103", "203", "1", DdcSiKindEnum.SKILL, "test", rawSiDigest(skillSpec));
        var nodes = new java.util.ArrayList<>(small.manifest().nodes()); nodes.add(skill);
        var edges = new java.util.ArrayList<>(small.manifest().edges()); edges.add(new EdgeVO("201", "203"));
        var manifest = DdcSiDependencyManifestVO.create(small.manifest().root(), null, nodes, edges);
        var resources = new java.util.ArrayList<>(small.resources());
        resources.add(new SiResourceVO(skill, "large-approved-skill", "agent-owner", skillSpec));
        var large = new FrozenSiBundleVO(small.bizCode(), small.namespaceCode(), small.envCode(),
                small.ownerAppCode(), small.callerAppCode(), manifest, resources);
        var command = runtimeCommand(template);
        String stored = codec.writeRuntimeSnapshot(codec.runtimeSnapshot("b".repeat(64), "run-large", command, template, large));
        assertTrue(stored.getBytes(StandardCharsets.UTF_8).length > AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        assertEquals(large, codec.readRuntimeSnapshot(stored).siBundle());
        assertEquals(AgentLedgerService.RunProfileEnum.RUNTIME_V2, codec.persistedProfile(stored));
        command.setInput(Map.of("oversized", "x".repeat(AgentJsonCodec.MAX_SNAPSHOT_BYTES)));
        assertThrows(IllegalArgumentException.class, () -> codec.runtimeRequestHash(command));
        assertThrows(IllegalStateException.class,
                () -> codec.readRuntimeSnapshot(stored + " ".repeat(AgentJsonCodec.MAX_SI_SNAPSHOT_BYTES)));
    }

    static FrozenSiBundleVO frozenBundle(long tenantId, AgentRuntimeTemplateCommand template, String promptSpec) {
        String promptDigest = rawSiDigest(promptSpec);
        String agentSpec = "{\"modelKey\":\"" + template.getConfiguration().modelRef()
                + "\",\"systemInstruction\":\"Use the approved prompt\"}";
        var root = new VersionIdentityVO(Long.toString(tenantId), "101", "201",
                Long.toString(template.getTemplateVersion()), DdcSiKindEnum.AGENT, "test", rawSiDigest(agentSpec));
        var prompt = new VersionIdentityVO(Long.toString(tenantId), "102", "202",
                Long.toString(template.getConfiguration().instructionRef().version()), DdcSiKindEnum.PROMPT,
                "test", promptDigest);
        var manifest = DdcSiDependencyManifestVO.create(root, null, List.of(prompt), List.of(new EdgeVO("201", "202")));
        return new FrozenSiBundleVO("fixture-biz", "default", "test", "agent-owner", "tianji-caller", manifest,
                List.of(new SiResourceVO(root, template.getTemplateKey(), "agent-owner", agentSpec),
                        new SiResourceVO(prompt, template.getConfiguration().instructionRef().key(), "agent-owner", promptSpec)));
    }

    static String rawSiDigest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unsupportedRuntime) {
            throw new IllegalStateException(unsupportedRuntime);
        }
    }

    static AgentRuntimeTemplateCommand runtimeTemplate(String instructionDigest) {
        var empty = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                "required", List.of(), "additionalProperties", false));
        var limits = new AgentRuntimeTemplateCommand.TechnicalLimitsVO(Duration.ofMinutes(1),
                Duration.ofSeconds(10), Duration.ofSeconds(10), 2, 2, 1, 4096, 1024);
        return new AgentRuntimeTemplateCommand("billing", 1,
                AgentRuntimeTemplateCommand.RuntimeProfileEnum.RUNTIME_V2, "agent-executor", empty, empty, empty,
                new AgentRuntimeTemplateCommand.TemplateConfigurationVO(
                        new AgentRuntimeTemplateCommand.TemplateDependencyRefVO("approved-instructions", 1,
                                instructionDigest), "registered-model", List.of(), null), limits);
    }

    static LinkedAgentRuntimeCommand runtimeCommand(AgentRuntimeTemplateCommand template) throws Exception {
        Instant deadline = Instant.parse("2026-11-19T23:00:00Z");
        String schemaDigest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(ProcessTypedJson.canonicalBytes(template.getInputSchema().toWire())));
        return new LinkedAgentRuntimeCommand("runtime-history", new AgentProcessLink("process-source",
                WorkerScopeTypeEnum.BPMN, "instance-history", "occurrence-history", template.contentSha256(),
                deadline.minusSeconds(30)), "node-history", template.getTemplateKey(), template.getTemplateVersion(),
                template.contentSha256(), "c".repeat(64), schemaDigest, schemaDigest, Map.of(), Map.of(),
                template.getLimits(), deadline, "correlation-history");
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
