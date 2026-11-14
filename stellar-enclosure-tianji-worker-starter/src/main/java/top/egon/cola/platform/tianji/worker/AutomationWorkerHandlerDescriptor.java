package top.egon.cola.platform.tianji.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

public record AutomationWorkerHandlerDescriptor(
        @NotBlank @Pattern(regexp = CAPABILITY_ID) String capabilityKey,
        @Positive @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long capabilityVersion,
        @NotNull @Pattern(regexp = SHA256) String inputSchemaSha256,
        @NotNull @Pattern(regexp = SHA256) String outputSchemaSha256,
        @NotNull WorkerEffectSafetyEnum effectMode,
        @NotBlank @Size(max = 128) String handlerVersion,
        @Pattern(regexp = REGISTERED_ID) String targetId) {

    static final String CAPABILITY_ID = "[A-Za-z][A-Za-z0-9_.:-]{0,127}";
    static final String REGISTERED_ID = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}";
    static final String SHA256 = "[0-9a-f]{64}";

    public AutomationWorkerHandlerDescriptor {
        if (capabilityKey == null || !capabilityKey.matches(CAPABILITY_ID) || capabilityVersion < 1
                || capabilityVersion > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || inputSchemaSha256 == null || !inputSchemaSha256.matches(SHA256)
                || outputSchemaSha256 == null || !outputSchemaSha256.matches(SHA256) || effectMode == null
                || handlerVersion == null || handlerVersion.isBlank() || handlerVersion.length() > 128
                || handlerVersion.chars().anyMatch(Character::isISOControl)
                || (effectMode == WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT ? targetId != null
                        : targetId == null || !targetId.matches(REGISTERED_ID))) {
            throw new IllegalArgumentException("Worker automation handler descriptor is invalid");
        }
        ProcessTypedJson.canonicalBytes(handlerVersion);
    }
}
