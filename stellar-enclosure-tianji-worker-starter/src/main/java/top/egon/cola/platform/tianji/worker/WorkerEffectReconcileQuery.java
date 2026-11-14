package top.egon.cola.platform.tianji.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

/** Locates a specific generation at the registered effect authority. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class WorkerEffectReconcileQuery {
    @NotBlank @Pattern(regexp = AutomationWorkerHandlerDescriptor.REGISTERED_ID)
    private String sourceId;
    @Positive
    private long tenantId;
    @NotBlank @Pattern(regexp = AutomationWorkerHandlerDescriptor.REGISTERED_ID)
    private String effectKey;
    @NotBlank @Pattern(regexp = AutomationWorkerHandlerDescriptor.SHA256)
    private String effectFingerprint;
    @NotBlank @Pattern(regexp = AutomationWorkerHandlerDescriptor.CAPABILITY_ID)
    private String capabilityKey;
    @Positive @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long capabilityVersion;
    @NotBlank @Size(max = 128)
    private String handlerVersion;
    @NotBlank @Pattern(regexp = AutomationWorkerHandlerDescriptor.REGISTERED_ID)
    private String targetId;
    @Positive @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long dispatchGeneration;
    @Size(max = 255) @ToString.Exclude
    private String businessReceiptRef;
}
