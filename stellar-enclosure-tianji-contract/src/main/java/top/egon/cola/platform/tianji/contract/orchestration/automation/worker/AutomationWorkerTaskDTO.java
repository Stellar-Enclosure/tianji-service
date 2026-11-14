package top.egon.cola.platform.tianji.contract.orchestration.automation.worker;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationNodeBindingVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationWorkerTaskDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String taskId;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String activityOccurrenceId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{43}") @ToString.Exclude
    private String leaseToken;
    @NotNull @Pattern(regexp = AutomationNodeBindingVO.CAPABILITY_KEY_PATTERN)
    private String capabilityKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long capabilityVersion;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> input;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String inputSha256;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String effectKey;
    @NotNull
    private Instant attemptDeadlineAt;
    @NotNull
    private Instant leaseExpiresAt;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String handlerVersion;
    @NotNull
    private WorkerEffectSafetyEnum effectMode;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String targetId;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String inputSchemaSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String outputSchemaSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String effectFingerprint;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid @ToString.Exclude
    private WorkerEffectRecoveryVO effectRecovery;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String taskContractSha256;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation worker task")
    public boolean isTaskContractConsistent() {
        try {
            taskWire();
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }

    public void verifyContractHash(long trustedTenantId, String trustedSourceId) {
        if (!contractSha256(trustedTenantId, trustedSourceId).equals(taskContractSha256)) {
            throw new IllegalArgumentException("Worker task contract digest mismatch");
        }
    }

    /** Binds a task to the tenant and source configured by the trusted host. */
    public String contractSha256(long trustedTenantId, String trustedSourceId) {
        if (trustedTenantId < 1 || !registeredIdentifier(trustedSourceId)) {
            throw new IllegalArgumentException("Invalid worker task scope");
        }
        return ProcessTypedJson.sha256("AUTOMATION_WORKER_TASK_V2",
                Long.toString(trustedTenantId).getBytes(StandardCharsets.UTF_8), trustedSourceId.getBytes(StandardCharsets.UTF_8),
                ProcessTypedJson.canonicalBytes(taskWire(), 72 * 1024));
    }

    public String leaseTokenSha256() {
        return leaseTokenSha256(leaseToken);
    }

    /** Matches the persisted lease digest without depending on the Process host. */
    public static String leaseTokenSha256(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("Invalid worker lease token");
        }
        byte[] bytes = Base64.getUrlDecoder().decode(token);
        if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) {
            throw new IllegalArgumentException("Invalid worker lease token");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private Map<String, Object> taskWire() {
        if (!plainText(taskId, 255) || !plainText(activityOccurrenceId, 255) || !identifier(nodeExecutionRef)
                || !AutomationNodeBindingVO.isCapabilityKey(capabilityKey) || capabilityVersion < 1 || capabilityVersion > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !plainText(handlerVersion, 128) || !registeredIdentifier(effectKey)
                || !WorkerEffectRecoveryVO.utcMicros(attemptDeadlineAt) || !WorkerEffectRecoveryVO.utcMicros(leaseExpiresAt)
                || !AutomationWorkerBusinessResultDTO.isHash(inputSha256)
                || !AutomationWorkerBusinessResultDTO.isHash(inputSchemaSha256)
                || !AutomationWorkerBusinessResultDTO.isHash(outputSchemaSha256)
                || !AutomationWorkerBusinessResultDTO.isHash(effectFingerprint) || !validEffect()) {
            throw new IllegalArgumentException("Invalid worker task contract");
        }
        AutomationWorkerBusinessResultDTO.canonicalData(input);
        var wire = new LinkedHashMap<String, Object>();
        wire.put("taskId", taskId);
        wire.put("activityOccurrenceId", activityOccurrenceId);
        wire.put("nodeExecutionRef", nodeExecutionRef);
        wire.put("leaseToken", leaseTokenSha256());
        wire.put("capabilityKey", capabilityKey);
        wire.put("capabilityVersion", capabilityVersion);
        wire.put("handlerVersion", handlerVersion);
        wire.put("effectMode", effectMode.name());
        wire.put("targetId", targetId);
        wire.put("inputSchemaSha256", inputSchemaSha256);
        wire.put("outputSchemaSha256", outputSchemaSha256);
        wire.put("effectFingerprint", effectFingerprint);
        wire.put("input", input);
        wire.put("inputSha256", inputSha256);
        wire.put("effectKey", effectKey);
        wire.put("attemptDeadlineAt", attemptDeadlineAt.toString());
        wire.put("leaseExpiresAt", leaseExpiresAt.toString());
        wire.put("effectRecovery", effectRecovery == null ? null : effectRecovery.toWire());
        return wire;
    }

    private boolean validEffect() {
        if (effectMode == null) {
            return false;
        }
        if (effectMode == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT) {
            return targetId == null && (effectRecovery == null || effectRecovery.mode() == WorkerEffectRecoveryVO.Mode.REPLAY_APPLIED);
        }
        return registeredIdentifier(targetId) && (effectRecovery == null || effectMode == WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT);
    }

    private static boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    }

    private static boolean registeredIdentifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    }

    private static boolean plainText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum
                && value.chars().noneMatch(Character::isISOControl);
    }
}
