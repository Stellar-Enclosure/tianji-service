package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
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
public class ProcessNodeExecutionDTO {

    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,254}")
    private String nodeId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String occurrenceId;
    @Positive @Max(10)
    private int attempt;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull
    private KindEnum kind;
    @NotNull
    private StateEnum state;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String inputSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String outputSchemaSha256;
    @NotNull
    private Instant startedAt;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Instant finishedAt;
    @NotNull
    private Instant deadlineAt;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,127}")
    private String errorCode;
    @NotNull
    private SideEffectOutcomeEnum sideEffectOutcome;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Pattern(regexp = "[^\\p{Cc}]{1,64}")
    private String agentRunId;
    @JsonInclude(JsonInclude.Include.ALWAYS) @Valid @ToString.Exclude
    private Map<String, Object> output;
    @NotNull
    private List<@NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}") String> eventReceiptIds;
    @NotBlank @Pattern(regexp = "[^\\p{Cc}]{1,128}")
    private String version;
    @NotNull @Size(max = 1)
    private List<@NotNull String> availableActions;

    @JsonIgnore
    @AssertTrue(message = "Invalid automation node observation")
    public boolean isNodeProjectionConsistent() {
        if (state == null || kind == null || !ProcessLifecycleEventV2.utcMicros(startedAt)
                || !ProcessLifecycleEventV2.utcMicros(deadlineAt) || !deadlineAt.isAfter(startedAt)
                || availableActions == null || agentRunId != null && kind != KindEnum.AGENT) {
            return false;
        }
        boolean terminal = switch (state) {
            case COMPLETED, FAILED, TIMED_OUT, CANCELLED -> true;
            default -> false;
        };
        return (!terminal || finishedAt != null)
                && (finishedAt == null || (terminal || state == StateEnum.WAIT_RECOVERY)
                        && ProcessLifecycleEventV2.utcMicros(finishedAt) && !finishedAt.isBefore(startedAt))
                && (output == null || (terminal || state == StateEnum.RESULT_ACCEPTED)
                        && AutomationStartCommand.boundedData(output))
                && (availableActions.isEmpty() || state == StateEnum.WAIT_RECOVERY && List.of("RETRY").equals(availableActions));
    }

    public enum KindEnum {
        HUMAN, SERVICE, RULE, AGENT, EVENT_WAIT, TIMER, SUBPROCESS, LOOP, MULTI_INSTANCE, AND_JOIN;

        @JsonValue
        public String wire() { return name(); }
    }

    public enum StateEnum {
        SCHEDULED, RUNNING, WAITING, RESULT_ACCEPTED, WAIT_RECOVERY, COMPLETED, FAILED, TIMED_OUT, CANCELLED;

        @JsonValue
        public String wire() { return name(); }
    }

    public enum SideEffectOutcomeEnum {
        NONE, CONFIRMED, UNKNOWN;

        @JsonValue
        public String wire() { return name(); }
    }
}
