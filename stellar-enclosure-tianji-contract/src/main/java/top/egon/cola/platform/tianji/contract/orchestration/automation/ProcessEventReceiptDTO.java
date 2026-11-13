package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessEventReceiptDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String receiptId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String eventId;
    @NotNull @Pattern(regexp = "RECEIVED|APPLIED|IGNORED_LATE|REJECTED|DEAD")
    private String state;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long activationRevision;
    @NotNull
    private Instant admittedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String instanceId;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull
    private Instant receivedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Instant decidedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}")
    private String decisionCode;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation event receipt")
    public boolean isReceiptConsistent() {
        if (!ProcessLifecycleEventV2.utcMicros(admittedAt) || !admittedAt.equals(receivedAt) || state == null
                || nodeExecutionRef != null && instanceId == null) {
            return false;
        }
        return switch (state) {
            case "RECEIVED" -> instanceId == null && nodeExecutionRef == null && decidedAt == null && decisionCode == null;
            case "APPLIED" -> instanceId != null && !instanceId.isBlank() && validDecisionTime()
                    && "APPLIED".equals(decisionCode);
            case "IGNORED_LATE", "REJECTED", "DEAD" -> validDecisionTime() && !"APPLIED".equals(decisionCode);
            default -> false;
        };
    }

    private boolean validDecisionTime() {
        return ProcessLifecycleEventV2.utcMicros(decidedAt) && !decidedAt.isBefore(receivedAt);
    }
}
