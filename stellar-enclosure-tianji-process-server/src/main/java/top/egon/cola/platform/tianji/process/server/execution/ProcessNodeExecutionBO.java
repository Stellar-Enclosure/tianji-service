package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.KindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessNodeExecutionDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.worker.WorkerEffectRecoveryVO;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class ProcessNodeExecutionBO {
    @Min(1) private long tenantId;
    @NotBlank private String instanceId;
    @NotBlank private String occurrenceId;
    @Min(1) @Max(10) private int attempt;
    @NotBlank private String nodeExecutionRef;
    @NotBlank private String nodeId;
    @NotNull private KindEnum kind;
    @NotBlank private String parentScopeId;
    @NotBlank private String attemptScopeId;
    @Min(0) private Integer branchIndex;
    @Min(0) private Integer iterationIndex;
    private boolean excluded;
    private String nativeJobId;
    private String nativeSubscriptionId;
    private String capabilityKey;
    @Min(1) private Long capabilityVersion;
    private String effectKey;
    @NotNull @ToString.Exclude private Map<String, Object> input;
    @NotBlank private String inputSha256;
    @NotBlank private String inputSchemaSha256;
    @NotBlank private String outputSchemaSha256;
    @NotBlank private String configurationSha256;
    @NotNull private StateEnum state;
    @Min(1) private long revision;
    @ToString.Exclude private Map<String, Object> output;
    private String outputSha256;
    private String agentRunId;
    @NotNull private SideEffectOutcomeEnum sideEffectOutcome;
    private String errorCode;
    @NotNull private Instant startedAt;
    @NotNull private Instant nodeDeadlineAt;
    @NotNull private Instant attemptDeadlineAt;
    private Instant acceptedAt;
    private Instant finishedAt;
    private String retryCommandId;
    private String retryRequestSha256;
    @Valid @ToString.Exclude private WorkerEffectRecoveryVO workerEffectRecovery;
}
