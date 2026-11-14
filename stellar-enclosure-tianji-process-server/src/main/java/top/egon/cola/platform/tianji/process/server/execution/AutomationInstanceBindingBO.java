package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationAdmissionProofVO;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationInstanceBindingBO {
    public enum AdmissionKind { DIRECT, EVENT }

    @Min(1) private long tenantId;
    @NotBlank private String instanceId;
    @NotBlank private String releaseKey;
    @NotBlank private String definitionKey;
    @NotNull private ProcessActor owner;
    @NotNull @ToString.Exclude private Map<String, Object> effectiveManifest;
    @NotBlank private String configurationSha256;
    @NotBlank private String startRequestSha256;
    @NotNull private Instant submittedAt;
    @Min(1) private long activationRevision;
    @NotBlank private String sourceCommandId;
    @NotBlank private String resolvedStartSha256;
    @NotNull private AdmissionKind admissionKind;
    private String sourceEventReceiptId;
    @NotNull @ToString.Exclude private AutomationAdmissionProofVO proof;
    @NotNull private Instant admittedAt;
}
