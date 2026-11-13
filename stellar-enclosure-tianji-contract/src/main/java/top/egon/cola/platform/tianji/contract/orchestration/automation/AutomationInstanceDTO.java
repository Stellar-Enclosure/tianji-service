package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationInstanceDTO {

    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String instanceId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String definitionKey;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
    private String releaseVersion;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long activationRevision;
    @NotNull
    private ProfileEnum profile;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,255}")
    private String businessKey;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String correlationId;
    @NotNull
    private StateEnum state;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String version;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String configurationSha256;
    @NotNull
    private Instant submittedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Instant finishedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid @ToString.Exclude
    private Map<String, Object> output;
    @NotNull @Size(max = 1)
    private List<@NotNull String> availableActions;
    @NotNull @Valid
    private GraphVO graph;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation instance observation")
    public boolean isInstanceProjectionConsistent() {
        if (state == null || !ProcessLifecycleEventV2.utcMicros(submittedAt)
                || graph == null || !graph.definitionKey().equals(definitionKey) || availableActions == null) {
            return false;
        }
        boolean open = state == StateEnum.ACTIVE || state == StateEnum.SUSPENDED;
        return (open ? finishedAt == null : ProcessLifecycleEventV2.utcMicros(finishedAt) && !finishedAt.isBefore(submittedAt))
                && (output == null || state == StateEnum.COMPLETED && AutomationStartCommand.boundedData(output))
                && (availableActions.isEmpty() || open && List.of("CANCEL").equals(availableActions));
    }

    public enum ProfileEnum {
        AUTOMATION_V2;

        @JsonValue
        public String wire() { return name(); }
    }

    public enum StateEnum {
        ACTIVE, SUSPENDED, COMPLETED, FAILED, CANCELLED;

        @JsonValue
        public String wire() { return name(); }
    }
}
