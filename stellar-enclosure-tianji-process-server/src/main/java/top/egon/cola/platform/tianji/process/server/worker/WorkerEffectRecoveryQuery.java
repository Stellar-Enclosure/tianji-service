package top.egon.cola.platform.tianji.process.server.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Protected locator for a read-only authority observation of a pinned Worker effect. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder(toBuilder = true)
public class WorkerEffectRecoveryQuery {
    static final String SHA256 = "[0-9a-f]{64}";
    static final String REGISTERED_ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    static final String NODE_REF = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";

    @NotNull @ToString.Exclude private ProcessActor actor;
    @NotNull @Pattern(regexp = REGISTERED_ID) private String sourceId;
    @NotBlank @Size(max = 255) private String instanceId;
    @NotNull @Pattern(regexp = NODE_REF) private String nodeExecutionRef;
    @NotNull @Pattern(regexp = REGISTERED_ID) private String effectKey;
    @NotNull @Pattern(regexp = AutomationNodeBindingVO.CAPABILITY_KEY_PATTERN) private String capabilityKey;
    @NotBlank @Size(max = 128) private String handlerVersion;
    @Min(1) private long nodeRevision;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) private long capabilityVersion;
    @Min(1) @Max(10) private int attempt;
    @NotNull @Pattern(regexp = SHA256) private String effectFingerprint;
    @NotNull @Pattern(regexp = SHA256) private String outputSchemaSha256;
    @NotNull private Instant nodeDeadlineAt;
    @Pattern(regexp = REGISTERED_ID) private String targetId;
    @Size(max = 255) @ToString.Exclude private String businessReceiptRef;

    public String sha256() {
        if (actor == null || !matches(actor.issuerFingerprint(), SHA256) || !matches(sourceId, REGISTERED_ID)
                || !opaque(instanceId, 255) || !matches(nodeExecutionRef, NODE_REF) || !matches(effectKey, REGISTERED_ID)
                || !AutomationNodeBindingVO.isCapabilityKey(capabilityKey) || !opaque(handlerVersion, 128)
                || nodeRevision < 1 || capabilityVersion < 1 || capabilityVersion > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || attempt < 1 || attempt > 10 || !matches(effectFingerprint, SHA256)
                || !matches(outputSchemaSha256, SHA256) || !micros(nodeDeadlineAt)
                || targetId != null && !matches(targetId, REGISTERED_ID)
                || businessReceiptRef != null && !opaque(businessReceiptRef, 255)) {
            throw new IllegalArgumentException("Invalid Worker recovery query");
        }
        var fields = new LinkedHashMap<String, Object>();
        fields.put("actor", Map.of("tenantId", actor.tenantId(), "subjectType", actor.subjectType().name(),
                "issuerFingerprint", actor.issuerFingerprint(), "subjectId", actor.subjectId()));
        fields.put("sourceId", sourceId);
        fields.put("instanceId", instanceId);
        fields.put("nodeExecutionRef", nodeExecutionRef);
        fields.put("effectKey", effectKey);
        fields.put("capabilityKey", capabilityKey);
        fields.put("handlerVersion", handlerVersion);
        fields.put("nodeRevision", nodeRevision);
        fields.put("capabilityVersion", capabilityVersion);
        fields.put("attempt", attempt);
        fields.put("effectFingerprint", effectFingerprint);
        fields.put("outputSchemaSha256", outputSchemaSha256);
        fields.put("nodeDeadlineAt", nodeDeadlineAt.toString());
        fields.put("targetId", targetId);
        fields.put("businessReceiptRef", businessReceiptRef);
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_RECOVERY_QUERY_V2", ProcessTypedJson.canonicalBytes(fields));
    }

    static boolean matches(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    static boolean opaque(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max
                && value.chars().noneMatch(Character::isISOControl);
    }

    static boolean micros(Instant instant) {
        return instant != null && !instant.isBefore(Instant.EPOCH) && instant.getNano() % 1_000 == 0;
    }
}
