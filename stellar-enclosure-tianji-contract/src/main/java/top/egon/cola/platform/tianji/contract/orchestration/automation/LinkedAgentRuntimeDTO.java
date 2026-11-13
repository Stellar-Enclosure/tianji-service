package top.egon.cola.platform.tianji.contract.orchestration.automation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class LinkedAgentRuntimeDTO {

    @NotNull @Pattern(regexp = "[^\\p{Cc}]{1,64}")
    private String runId;
    @NotNull @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
    private String nodeExecutionRef;
    @NotNull @Pattern(regexp = "CREATED|QUEUED|RUNNING|WAITING|SUSPENDED|COMPLETED|FAILED|CANCELLED|TIMED_OUT")
    private String state;
    @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER)
    private long revision;
    @NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
    private String idempotencyKey;
}
