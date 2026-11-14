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
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyOverrideDTO;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
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
    static final int MAX_EVENT_BYTES = 1_048_576;
    static final int MAX_RESULT_BYTES = 2_097_152;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };
    private static final Set<String> RUNTIME_SNAPSHOT_FIELDS = Set.of("runtimeProfile", "snapshotSha256", "ownerSha256",
            "runId", "command", "template");
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

    String runtimeRequestHash(LinkedAgentRuntimeCommand command) {
        return ProcessTypedJson.sha256("tianji-linked-agent-runtime-request-v2",
                ProcessTypedJson.canonicalBytes(commandWire(command), MAX_SNAPSHOT_BYTES));
    }

    RuntimeSnapshotVO runtimeSnapshot(String ownerSha256, String runId, LinkedAgentRuntimeCommand command,
                                      AgentRuntimeTemplateCommand template) {
        return new RuntimeSnapshotVO(ownerSha256, runId, command, template,
                runtimeSnapshotHash(ownerSha256, runId, commandWire(command), template.toWire()));
    }

    String writeRuntimeSnapshot(RuntimeSnapshotVO snapshot) {
        return canonical(Map.of("runtimeProfile", "RUNTIME_V2", "snapshotSha256", snapshot.snapshotSha256(),
                "ownerSha256", snapshot.ownerSha256(), "runId", snapshot.runId(),
                "command", commandWire(snapshot.command()), "template", snapshot.template().toWire()));
    }

    RuntimeSnapshotVO readRuntimeSnapshot(String json) {
        Map<String, Object> wire = strictObject(json);
        if (!RUNTIME_SNAPSHOT_FIELDS.equals(wire.keySet()) || !"RUNTIME_V2".equals(wire.get("runtimeProfile"))
                || !(wire.get("snapshotSha256") instanceof String digest)
                || !(wire.get("ownerSha256") instanceof String ownerSha256)
                || !(wire.get("runId") instanceof String runId)) throw invalidRuntimeData();
        Map<String, Object> commandWire = exactObject(wire.get("command"));
        var command = strictRuntime.convertValue(commandWire, LinkedAgentRuntimeCommand.class);
        var template = AgentRuntimeTemplateCommand.fromExactWire(exactObject(wire.get("template")));
        if (!command.isLinkedRuntimeConsistent()
                || !canonical(commandWire(command)).equals(canonical(commandWire))
                || !runtimeSnapshotHash(ownerSha256, runId, commandWire, template.toWire()).equals(digest)) throw invalidRuntimeData();
        return new RuntimeSnapshotVO(ownerSha256, runId, freezeCommand(command), template, digest);
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
        Map<String, Object> content = strictObject(json);
        if (content.containsKey("runtimeProfile")) {
            if (!"RUNTIME_V2".equals(content.get("runtimeProfile"))
                    || !RUNTIME_SNAPSHOT_FIELDS.equals(content.keySet())) throw invalidRuntimeData();
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
        if (json == null || json.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES) throw invalidRuntimeData();
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

    private static String canonical(Object wire) {
        return new String(ProcessTypedJson.canonicalBytes(wire, MAX_SNAPSHOT_BYTES), StandardCharsets.UTF_8);
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
                             AgentRuntimeTemplateCommand template, String snapshotSha256) { }

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
