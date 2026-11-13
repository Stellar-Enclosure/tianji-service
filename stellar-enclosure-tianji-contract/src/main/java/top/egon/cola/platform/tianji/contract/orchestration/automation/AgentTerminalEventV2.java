package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable v2 delivery fact. The receiver still verifies the host identity and stored dispatch. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AgentTerminalEventV2(
        @JsonProperty(required = true) int schemaVersion,
        @NotNull String producerId,
        @NotNull String eventId,
        @Positive long tenantId,
        @NotNull String ownerIssuerSha256,
        @NotNull String ownerSubjectId,
        @NotNull String idempotencyKey,
        @NotNull @Valid AgentProcessLink link,
        @NotNull String definitionKey,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long definitionVersion,
        @NotNull String runId,
        @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long terminalRevision,
        @NotNull String nodeExecutionRef,
        @NotNull String configurationSha256,
        @NotNull String inputSha256,
        @NotNull String outputSchemaSha256,
        @NotNull AgentRunStatusEnum status,
        @NotNull Instant finishedAt,
        @NotNull String resultSha256,
        @JsonProperty(required = true) Map<String, Object> output,
        @JsonProperty(required = true) @Valid AgentResultArtifactRefVO artifactRef,
        @JsonProperty(required = true) String errorCode,
        @NotNull SideEffectOutcomeEnum sideEffectOutcome) {

    private static final int MAX_INLINE_BYTES = 8 * 1024;
    private static final int MAX_ENVELOPE_BYTES = 16 * 1024;
    private static final Instant LAST_WIRE_INSTANT = Instant.parse("9999-12-31T23:59:59.999999Z");

    public AgentTerminalEventV2 {
        require(schemaVersion == 2 && trustedIdentifier(idempotencyKey) && link != null && link.scopeType() == WorkerScopeTypeEnum.BPMN
                && ProcessTypedJson.isId(definitionKey) && version(definitionVersion) && ProcessTypedJson.isId(nodeExecutionRef)
                && ProcessTypedJson.isSha256(configurationSha256) && ProcessTypedJson.isSha256(inputSha256)
                && ProcessTypedJson.isSha256(outputSchemaSha256) && utcMicros(finishedAt)
                && !finishedAt.isBefore(Instant.EPOCH) && !finishedAt.isAfter(LAST_WIRE_INSTANT));
        require(eventIdFor(producerId, tenantId, ownerIssuerSha256, ownerSubjectId, runId, terminalRevision).equals(eventId));
        if (output != null) {
            output = ProcessTypedJson.immutableObject(output, MAX_INLINE_BYTES);
        }
        require(resultSha256(status, output, artifactRef, errorCode, sideEffectOutcome).equals(resultSha256));
    }

    public static String eventIdFor(String producerId, long tenantId, String ownerIssuerSha256, String ownerSubjectId,
                                    String runId, long terminalRevision) {
        require(trustedIdentifier(producerId) && tenantId > 0 && ProcessTypedJson.isSha256(ownerIssuerSha256)
                && opaqueIdentity(ownerSubjectId, 256) && opaqueIdentity(runId, 64) && version(terminalRevision));
        return ProcessTypedJson.sha256("tianji-agent-terminal-id-v2", utf8(producerId), utf8(Long.toString(tenantId)),
                utf8(ownerIssuerSha256), utf8(ownerSubjectId), utf8(runId), utf8(Long.toString(terminalRevision)));
    }

    public static String resultSha256(AgentRunStatusEnum status, Map<String, Object> output, AgentResultArtifactRefVO artifactRef,
                                      String errorCode, SideEffectOutcomeEnum sideEffectOutcome) {
        return ProcessTypedJson.sha256("tianji-agent-terminal-result-v2",
                ProcessTypedJson.canonicalBytes(resultWire(status, output, artifactRef, errorCode, sideEffectOutcome)));
    }

    public String fingerprint() {
        return ProcessTypedJson.sha256("tianji-agent-terminal-event-v2", canonicalBytes());
    }

    public byte[] canonicalBytes() {
        return ProcessTypedJson.canonicalBytes(toWire(), MAX_ENVELOPE_BYTES);
    }

    /** Complete decoded wire, shared by the HTTP receiver and outbox publisher. */
    @JsonValue
    public Map<String, Object> toWire() {
        var wire = resultFields(status, output, artifactRef, errorCode, sideEffectOutcome);
        wire.put("schemaVersion", schemaVersion);
        wire.put("producerId", producerId);
        wire.put("eventId", eventId);
        wire.put("tenantId", tenantId);
        wire.put("ownerIssuerSha256", ownerIssuerSha256);
        wire.put("ownerSubjectId", ownerSubjectId);
        wire.put("idempotencyKey", idempotencyKey);
        wire.put("link", Map.of("sourceId", link.sourceId(), "scopeType", link.scopeType().name(),
                "scopeInstanceId", link.scopeInstanceId(), "activityOccurrenceId", link.activityOccurrenceId(),
                "definitionContentSha256", link.definitionContentSha256(), "dispatchNotAfter", link.dispatchNotAfter().toString()));
        wire.put("definitionKey", definitionKey);
        wire.put("definitionVersion", definitionVersion);
        wire.put("runId", runId);
        wire.put("terminalRevision", terminalRevision);
        wire.put("nodeExecutionRef", nodeExecutionRef);
        wire.put("configurationSha256", configurationSha256);
        wire.put("inputSha256", inputSha256);
        wire.put("outputSchemaSha256", outputSchemaSha256);
        wire.put("finishedAt", finishedAt.toString());
        wire.put("resultSha256", resultSha256);
        return wire;
    }

    private static Map<String, Object> resultWire(AgentRunStatusEnum status, Map<String, Object> output,
            AgentResultArtifactRefVO artifactRef, String errorCode, SideEffectOutcomeEnum sideEffectOutcome) {
        require(status != null && sideEffectOutcome != null);
        boolean accepted = switch (status) {
            case COMPLETED -> (output != null) != (artifactRef != null) && errorCode == null;
            case FAILED, CANCELLED, TIMED_OUT -> output == null && artifactRef == null
                    && errorCode != null && errorCode.matches("[A-Z][A-Z0-9_]{0,63}");
            default -> false;
        };
        require(accepted);
        return resultFields(status, output == null ? null : ProcessTypedJson.immutableObject(output, MAX_INLINE_BYTES),
                artifactRef, errorCode, sideEffectOutcome);
    }

    private static LinkedHashMap<String, Object> resultFields(AgentRunStatusEnum status, Map<String, Object> output,
            AgentResultArtifactRefVO artifactRef, String errorCode, SideEffectOutcomeEnum sideEffectOutcome) {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("status", status.name());
        wire.put("output", output);
        wire.put("artifactRef", artifactRef == null ? null : artifactRef.toWire());
        wire.put("errorCode", errorCode);
        wire.put("sideEffectOutcome", sideEffectOutcome.wire());
        return wire;
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static boolean opaqueIdentity(String value, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.strip())
                || value.chars().anyMatch(Character::isISOControl)) {
            return false;
        }
        ProcessTypedJson.utf8Length(value, 4 * maximum);
        return true;
    }

    private static boolean trustedIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    }

    private static boolean version(long value) {
        return value >= 1 && value <= ProcessTypedJson.V2_MAX_WIRE_INTEGER;
    }

    static boolean utcMicros(Instant value) {
        return value != null && value.getNano() % 1_000 == 0;
    }

    private static void require(boolean accepted) {
        if (!accepted) {
            throw new IllegalArgumentException("Invalid Agent Runtime terminal event");
        }
    }

    @Override
    public String toString() {
        return "AgentTerminalEventV2[schemaVersion=" + schemaVersion + ", eventId=" + eventId + ", status=" + status + "]";
    }

    public enum SideEffectOutcomeEnum {
        NONE, CONFIRMED, UNKNOWN;

        @JsonValue
        public String wire() { return name(); }
    }
}
