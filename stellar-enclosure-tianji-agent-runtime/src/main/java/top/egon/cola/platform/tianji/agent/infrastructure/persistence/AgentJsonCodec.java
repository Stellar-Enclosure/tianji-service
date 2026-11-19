package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.application.run.TianjiSiExecutionBundleService.FrozenSiBundleVO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class AgentJsonCodec {
    static final int MAX_SNAPSHOT_BYTES = 1_048_576;
    // 4 MiB prepared material, JSON string escaping and the existing bounded request/template envelope.
    static final int MAX_SI_SNAPSHOT_BYTES = 10 * 1_048_576;
    static final int MAX_EVENT_BYTES = 1_048_576;
    static final int MAX_RESULT_BYTES = 2_097_152;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };
    private static final Set<String> RUNTIME_SNAPSHOT_FIELDS = Set.of("runtimeProfile", "snapshotSha256", "ownerSha256",
            "runId", "command", "template");
    private static final Set<String> SI_RUNTIME_SNAPSHOT_FIELDS = Set.of("runtimeProfile", "snapshotSha256", "ownerSha256",
            "runId", "command", "template", "siBundle");
    private static final Set<String> LEGACY_SNAPSHOT_FIELDS = Set.of("definition", "definitionContentSha256",
            "effectivePolicy", "input", "businessKey");

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

    private final ObjectMapper strictRuntime = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                    DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

    private static final Set<String> RUNTIME_RESULT_FIELDS = Set.of("status", "output", "artifactRef", "errorCode",
            "sideEffectOutcome", "resultSha256");
    private static final Set<String> ARTIFACT_FIELDS = Set.of("resourceKey", "contentSha256", "mediaType", "sizeBytes");

    String writeRuntimeResult(RuntimeTerminalResultVO result) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("status", result.status().name());
        fields.put("output", result.output());
        fields.put("artifactRef", result.artifactRef() == null ? null : result.artifactRef().toWire());
        fields.put("errorCode", result.errorCode());
        fields.put("sideEffectOutcome", result.sideEffectOutcome().wire());
        fields.put("resultSha256", result.resultSha256());
        return new String(ProcessTypedJson.canonicalBytes(fields, 16 * 1024), StandardCharsets.UTF_8);
    }

    RuntimeTerminalResultVO readRuntimeResult(String payload) {
        // PostgreSQL jsonb preserves the closed value, not its original key order or whitespace.
        if (payload == null || payload.length() > 128 * 1024) throw invalidRuntimeData();
        try {
            Map<String, Object> fields = strictRuntime.readValue(payload, JSON_OBJECT);
            if (fields == null || !RUNTIME_RESULT_FIELDS.equals(fields.keySet())
                    || !(fields.get("status") instanceof String status)
                    || !(fields.get("sideEffectOutcome") instanceof String effect)
                    || !(fields.get("resultSha256") instanceof String sha)
                    || fields.get("errorCode") != null && !(fields.get("errorCode") instanceof String)) throw invalidRuntimeData();
            var output = fields.get("output") == null ? null : exactObject(fields.get("output"));
            var artifact = fields.get("artifactRef") == null ? null : readRuntimeArtifact(exactObject(fields.get("artifactRef")));
            var result = new RuntimeTerminalResultVO(AgentRunStatusEnum.valueOf(status), output, artifact,
                    (String) fields.get("errorCode"), SideEffectOutcomeEnum.valueOf(effect));
            if (!sha.equals(result.resultSha256())) throw invalidRuntimeData();
            return result;
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            throw invalidRuntimeData();
        }
    }

    AgentResultArtifactRefVO readRuntimeArtifact(Map<String, Object> fields) {
        if (!ARTIFACT_FIELDS.equals(fields.keySet())) throw invalidRuntimeData();
        return strictRuntime.convertValue(fields, AgentResultArtifactRefVO.class);
    }

    record RuntimeTerminalResultVO(AgentRunStatusEnum status, Map<String, Object> output, AgentResultArtifactRefVO artifactRef,
                                   String errorCode, SideEffectOutcomeEnum sideEffectOutcome) {
        RuntimeTerminalResultVO {
            if (output != null) output = exactObject(output);
            AgentTerminalEventV2.resultSha256(status, output, artifactRef, errorCode, sideEffectOutcome);
        }

        String resultSha256() {
            return AgentTerminalEventV2.resultSha256(status, output, artifactRef, errorCode, sideEffectOutcome);
        }
    }

    String runtimeRequestHash(LinkedAgentRuntimeCommand command) {
        return ProcessTypedJson.sha256("tianji-linked-agent-runtime-request-v2",
                ProcessTypedJson.canonicalBytes(commandWire(command), MAX_SNAPSHOT_BYTES));
    }

    RuntimeSnapshotVO runtimeSnapshot(String ownerSha256, String runId, LinkedAgentRuntimeCommand command,
                                      AgentRuntimeTemplateCommand template) {
        return new RuntimeSnapshotVO(ownerSha256, runId, command, template,
                runtimeSnapshotHash(ownerSha256, runId, commandWire(command), template.toWire()), null);
    }

    RuntimeSnapshotVO runtimeSnapshot(String ownerSha256, String runId, LinkedAgentRuntimeCommand command,
                                      AgentRuntimeTemplateCommand template, FrozenSiBundleVO siBundle) {
        FrozenSiBundleVO frozen = FrozenSiBundleVO.fromExactWire(Objects.requireNonNull(siBundle, "siBundle").toWire());
        return new RuntimeSnapshotVO(ownerSha256, runId, command, template,
                runtimeSnapshotHash(ownerSha256, runId, commandWire(command), template.toWire(), frozen), frozen);
    }

    String writeRuntimeSnapshot(RuntimeSnapshotVO snapshot) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("runtimeProfile", "RUNTIME_V2"); wire.put("snapshotSha256", snapshot.snapshotSha256());
        wire.put("ownerSha256", snapshot.ownerSha256()); wire.put("runId", snapshot.runId());
        wire.put("command", commandWire(snapshot.command())); wire.put("template", snapshot.template().toWire());
        if (snapshot.siBundle() != null) wire.put("siBundle", snapshot.siBundle().toWire());
        return canonical(wire, snapshot.siBundle() == null ? MAX_SNAPSHOT_BYTES : MAX_SI_SNAPSHOT_BYTES);
    }

    RuntimeSnapshotVO readRuntimeSnapshot(String json) {
        Map<String, Object> wire = strictObject(json, MAX_SI_SNAPSHOT_BYTES);
        boolean hasSiBundle = SI_RUNTIME_SNAPSHOT_FIELDS.equals(wire.keySet());
        if (!hasSiBundle && json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) throw invalidRuntimeData();
        if ((!hasSiBundle && !RUNTIME_SNAPSHOT_FIELDS.equals(wire.keySet())) || !"RUNTIME_V2".equals(wire.get("runtimeProfile"))
                || !(wire.get("snapshotSha256") instanceof String digest)
                || !(wire.get("ownerSha256") instanceof String ownerSha256)
                || !(wire.get("runId") instanceof String runId)) throw invalidRuntimeData();
        Map<String, Object> commandWire = exactObject(wire.get("command"));
        var command = strictRuntime.convertValue(commandWire, LinkedAgentRuntimeCommand.class);
        var template = AgentRuntimeTemplateCommand.fromExactWire(exactObject(wire.get("template")));
        FrozenSiBundleVO siBundle = null;
        if (hasSiBundle) {
            try {
                Map<String, Object> bundleWire = exactObject(wire.get("siBundle"));
                siBundle = FrozenSiBundleVO.fromExactWire(bundleWire);
                if (!canonical(siBundle.toWire(), MAX_SI_SNAPSHOT_BYTES).equals(canonical(bundleWire, MAX_SI_SNAPSHOT_BYTES))) throw invalidRuntimeData();
            } catch (IllegalArgumentException malformed) {
                throw invalidRuntimeData();
            }
        }
        String expectedHash = siBundle == null
                ? runtimeSnapshotHash(ownerSha256, runId, commandWire, template.toWire())
                : runtimeSnapshotHash(ownerSha256, runId, commandWire, template.toWire(), siBundle);
        if (!command.isLinkedRuntimeConsistent()
                || !canonical(commandWire(command)).equals(canonical(commandWire))
                || !expectedHash.equals(digest)) throw invalidRuntimeData();
        return new RuntimeSnapshotVO(ownerSha256, runId, freezeCommand(command), template, digest, siBundle);
    }

    AgentRuntimeTemplateCommand readRuntimeDefinition(String json) {
        return AgentRuntimeTemplateCommand.fromExactWire(strictObject(json));
    }

    LinkedAgentRuntimeCommand immutableRuntimeCommand(LinkedAgentRuntimeCommand command) {
        if (command == null || !command.isLinkedRuntimeConsistent()) throw new IllegalArgumentException("Invalid runtime submission");
        var copy = strictRuntime.convertValue(commandWire(command), LinkedAgentRuntimeCommand.class);
        if (!copy.isLinkedRuntimeConsistent()) throw new IllegalArgumentException("Invalid runtime submission");
        return freezeCommand(copy);
    }

    AgentLedgerService.RunProfileEnum persistedProfile(String json) {
        Map<String, Object> content = strictObject(json, MAX_SI_SNAPSHOT_BYTES);
        if (!SI_RUNTIME_SNAPSHOT_FIELDS.equals(content.keySet())
                && json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) throw invalidRuntimeData();
        if (content.containsKey("runtimeProfile")) {
            if (!"RUNTIME_V2".equals(content.get("runtimeProfile"))
                    || (!RUNTIME_SNAPSHOT_FIELDS.equals(content.keySet())
                    && !SI_RUNTIME_SNAPSHOT_FIELDS.equals(content.keySet()))) throw invalidRuntimeData();
            return AgentLedgerService.RunProfileEnum.RUNTIME_V2;
        }
        var fields = new TreeSet<>(content.keySet());
        fields.remove("processLink");
        if (!LEGACY_SNAPSHOT_FIELDS.equals(fields)) throw invalidRuntimeData();
        return AgentLedgerService.RunProfileEnum.LEGACY_V1;
    }

    String runtimeSchemaHash(ProcessSchemaVO schema) {
        return HexFormat.of().formatHex(sha256().digest(ProcessTypedJson.canonicalBytes(schema.toWire(), 32_768)));
    }

    private Map<String, Object> commandWire(LinkedAgentRuntimeCommand command) {
        var wire = strictRuntime.convertValue(Objects.requireNonNull(command), JSON_OBJECT);
        wire.put("limits", command.getLimits().toWire());
        return wire;
    }

    private Map<String, Object> strictObject(String json) {
        return strictObject(json, MAX_SNAPSHOT_BYTES);
    }

    private Map<String, Object> strictObject(String json, int maxBytes) {
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > maxBytes) throw invalidRuntimeData();
        try {
            return exactObject(strictRuntime.readValue(json, JSON_OBJECT));
        } catch (JsonProcessingException | IllegalArgumentException malformed) {
            throw invalidRuntimeData();
        }
    }

    private static LinkedAgentRuntimeCommand freezeCommand(LinkedAgentRuntimeCommand command) {
        return command.setInput(exactObject(command.getInput())).setParameters(exactObject(command.getParameters()));
    }

    private static Map<String, Object> exactObject(Object candidate) {
        if (!(candidate instanceof Map<?, ?> values)) throw invalidRuntimeData();
        var copy = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            if (!(key instanceof String text)) throw invalidRuntimeData();
            copy.put(text, freezeValue(value));
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Object freezeValue(Object value) {
        if (value instanceof Map<?, ?>) return exactObject(value);
        if (value instanceof List<?> list) return list.stream().map(AgentJsonCodec::freezeValue).toList();
        return value;
    }

    private static String runtimeSnapshotHash(String ownerSha256, String runId, Map<String, Object> command,
                                              Map<String, Object> template) {
        return ProcessTypedJson.sha256("tianji-agent-runtime-snapshot-v2", ownerSha256.getBytes(StandardCharsets.UTF_8),
                runId.getBytes(StandardCharsets.UTF_8),
                ProcessTypedJson.canonicalBytes(Map.of("command", command, "template", template), MAX_SNAPSHOT_BYTES));
    }

    private static String runtimeSnapshotHash(String ownerSha256, String runId, Map<String, Object> command,
                                              Map<String, Object> template, FrozenSiBundleVO bundle) {
        return ProcessTypedJson.sha256("tianji-agent-runtime-snapshot-si-v1", ownerSha256.getBytes(StandardCharsets.UTF_8),
                runId.getBytes(StandardCharsets.UTF_8), ProcessTypedJson.canonicalBytes(
                        Map.of("command", command, "template", template, "siBundle", bundle.toWire()), MAX_SI_SNAPSHOT_BYTES));
    }

    private static String canonical(Object wire) {
        return canonical(wire, MAX_SNAPSHOT_BYTES);
    }

    private static String canonical(Object wire, int maxBytes) {
        return new String(ProcessTypedJson.canonicalBytes(wire, maxBytes), StandardCharsets.UTF_8);
    }

    private static IllegalStateException invalidRuntimeData() {
        return new IllegalStateException("Stored Agent runtime evidence is invalid");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    record RuntimeSnapshotVO(String ownerSha256, String runId, LinkedAgentRuntimeCommand command,
                             AgentRuntimeTemplateCommand template, String snapshotSha256, FrozenSiBundleVO siBundle) {
        @Override public String toString() { return "AgentJsonCodec.RuntimeSnapshotVO[redacted]"; }
    }

    String definitionHash(AgentDefinitionSpecDTO spec) {
        Map<String, Object> content = new TreeMap<>();
        content.put("executionMode", spec.executionMode());
        content.put("codeRef", spec.codeRef());
        content.put("inputSchemaVersion", spec.inputSchemaVersion());
        content.put("outputSchemaVersion", spec.outputSchemaVersion());
        content.put("dependencies", spec.dependencies());
        content.put("policy", policy(spec.policy()));
        content.put("configuration", spec.configuration());
        return hash(write(content, MAX_SNAPSHOT_BYTES));
    }

    String requestHash(StartAgentRunCommand command) {
        Map<String, Object> content = new TreeMap<>();
        content.put("definitionKey", command.definitionKey());
        content.put("definitionVersion", command.definitionVersion());
        content.put("sessionId", command.sessionId());
        content.put("businessKey", command.businessKey());
        content.put("input", command.input());
        content.put("policyOverride", policyOverride(command.policyOverride()));
        return hash(write(content, MAX_SNAPSHOT_BYTES));
    }

    String linkedRequestHash(StartAgentRunCommand command, AgentProcessLink link) {
        Map<String, Object> content = new TreeMap<>();
        content.put("bindingVersion", 1);
        content.put("startRequestSha256", requestHash(command));
        content.put("idempotencyKey", command.idempotencyKey());
        content.put("correlationId", command.correlationId());
        content.put("processLink", link);
        return hash(write(content, MAX_SNAPSHOT_BYTES));
    }

    String processLinkHash(AgentProcessLink link) {
        return hash(write(Map.of("bindingVersion", 1, "processLink", link), MAX_SNAPSHOT_BYTES));
    }

    String cancelHash(CancelAgentRunCommand command) {
        Map<String, Object> content = new TreeMap<>();
        content.put("runId", command.runId());
        content.put("expectedRevision", command.expectedRevision());
        content.put("reason", command.reason());
        return hash(write(content, MAX_EVENT_BYTES));
    }

    /** Stable V1 binding for the immutable inputs actually used by an execution. */
    String executionSnapshotHash(AgentExecutionSnapshot snapshot) {
        Map<String, Object> content = new TreeMap<>();
        content.put("bindingVersion", snapshot.processLink() == null ? 1 : 2);
        content.put("definitionKey", snapshot.definition().definitionKey());
        content.put("definitionVersion", snapshot.definition().version());
        content.put("definitionContentSha256", snapshot.definitionContentSha256());
        content.put("effectivePolicy", policy(snapshot.effectivePolicy()));
        content.put("input", snapshot.input());
        content.put("businessKey", snapshot.businessKey());
        if (snapshot.processLink() != null) content.put("processLink", snapshot.processLink());
        return hash(write(content, MAX_SNAPSHOT_BYTES));
    }

    String snapshot(AgentDefinitionSpecDTO spec, String definitionHash,
                    ExecutionPolicyDTO effective, StartAgentRunCommand command) {
        return snapshot(spec, definitionHash, effective, command, null);
    }

    String snapshot(AgentDefinitionSpecDTO spec, String definitionHash,
                    ExecutionPolicyDTO effective, StartAgentRunCommand command, AgentProcessLink processLink) {
        Map<String, Object> snapshot = new TreeMap<>();
        snapshot.put("definition", spec);
        snapshot.put("definitionContentSha256", definitionHash);
        snapshot.put("effectivePolicy", policy(effective));
        snapshot.put("input", command.input());
        snapshot.put("businessKey", command.businessKey());
        if (processLink != null) snapshot.put("processLink", processLink);
        return write(snapshot, MAX_SNAPSHOT_BYTES);
    }

    String write(Object value, int maxBytes) {
        try {
            String json = mapper.writeValueAsString(value);
            if (json.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                throw new IllegalArgumentException("JSON payload exceeds its size limit");
            }
            return json;
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("JSON payload cannot be serialized", failure);
        }
    }

    AgentDefinitionSpecDTO readDefinition(String json) {
        return read(json, AgentDefinitionSpecDTO.class);
    }

    AgentExecutionResult readResult(String json) {
        return read(json, AgentExecutionResult.class);
    }

    String resultHash(String result) {
        if (result == null || result.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
            throw new IllegalArgumentException("Agent result exceeds its size limit");
        }
        return hash(result);
    }

    AgentExecutionSnapshot readSnapshot(String json) {
        return read(json, AgentExecutionSnapshot.class);
    }

    CancelAgentRunResult readCancelResult(String json) {
        return read(json, CancelAgentRunResult.class);
    }

    Map<String, Object> readObject(String json) {
        try {
            return mapper.readValue(json, JSON_OBJECT);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("stored event payload is invalid", failure);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("stored Agent ledger data is invalid", failure);
        }
    }

    private static Map<String, Object> policy(ExecutionPolicyDTO policy) {
        Map<String, Object> value = new TreeMap<>();
        value.put("totalTimeout", duration(policy.totalTimeout()));
        value.put("modelIdleTimeout", duration(policy.modelIdleTimeout()));
        value.put("toolTimeout", duration(policy.toolTimeout()));
        value.put("maxModelCalls", policy.maxModelCalls());
        value.put("maxToolCalls", policy.maxToolCalls());
        value.put("maxInputTokens", policy.maxInputTokens());
        value.put("maxOutputTokens", policy.maxOutputTokens());
        value.put("maxEstimatedCost", policy.maxEstimatedCost());
        value.put("currency", policy.currency());
        value.put("maxParallelism", policy.maxParallelism());
        value.put("allowedToolVersions", new TreeSet<>(policy.allowedToolVersions()));
        value.put("retryPolicyRef", policy.retryPolicyRef());
        value.put("approvalPolicyRef", policy.approvalPolicyRef());
        return value;
    }

    private static Map<String, Object> policyOverride(ExecutionPolicyOverrideDTO override) {
        if (override == null) {
            return null;
        }
        Map<String, Object> value = new TreeMap<>();
        value.put("totalTimeout", duration(override.totalTimeout()));
        value.put("modelIdleTimeout", duration(override.modelIdleTimeout()));
        value.put("toolTimeout", duration(override.toolTimeout()));
        value.put("maxModelCalls", override.maxModelCalls());
        value.put("maxToolCalls", override.maxToolCalls());
        value.put("maxInputTokens", override.maxInputTokens());
        value.put("maxOutputTokens", override.maxOutputTokens());
        value.put("maxEstimatedCost", override.maxEstimatedCost());
        value.put("maxParallelism", override.maxParallelism());
        value.put("allowedToolVersions", override.allowedToolVersions() == null
                ? null : new TreeSet<>(override.allowedToolVersions()));
        return value;
    }

    private static String duration(Duration value) {
        return value == null ? null : value.toString();
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }
}
