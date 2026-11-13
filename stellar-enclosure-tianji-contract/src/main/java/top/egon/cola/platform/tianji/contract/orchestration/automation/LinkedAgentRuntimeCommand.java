package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class LinkedAgentRuntimeCommand {

    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String idempotencyKey;
    @NotNull @Valid
    private AgentProcessLink link;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String templateKey;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long templateVersion;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String templateSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String configurationSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String inputSchemaSha256;
    @NotNull @Pattern(regexp = "[0-9a-f]{64}")
    private String outputSchemaSha256;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> input;
    @NotNull @Valid @ToString.Exclude
    private Map<String, Object> parameters;
    @NotNull @Valid
    private TechnicalLimitsVO limits;
    @NotNull
    private Instant attemptDeadlineAt;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String correlationId;

    @JsonIgnore
    @AssertTrue(message = "Invalid linked Agent Runtime command")
    public boolean isLinkedRuntimeConsistent() {
        if (link == null || link.scopeType() != WorkerScopeTypeEnum.BPMN
                || !AgentTerminalEventV2.utcMicros(attemptDeadlineAt) || link.dispatchNotAfter().isAfter(attemptDeadlineAt)) {
            return false;
        }
        try {
            ProcessTypedJson.immutableObject(input, 64 * 1024);
            ProcessTypedJson.immutableObject(parameters, 64 * 1024);
            return true;
        } catch (IllegalArgumentException failure) {
            return false;
        }
    }
}
