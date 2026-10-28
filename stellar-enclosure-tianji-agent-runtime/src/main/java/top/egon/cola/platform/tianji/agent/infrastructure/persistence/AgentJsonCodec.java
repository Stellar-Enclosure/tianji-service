package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class AgentJsonCodec {
    static final int MAX_SNAPSHOT_BYTES = 1_048_576;
    static final int MAX_EVENT_BYTES = 1_048_576;
    static final int MAX_RESULT_BYTES = 2_097_152;
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() { };

    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .build();

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
