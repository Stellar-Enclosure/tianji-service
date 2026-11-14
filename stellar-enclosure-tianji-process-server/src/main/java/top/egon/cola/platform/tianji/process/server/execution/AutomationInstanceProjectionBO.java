package top.egon.cola.platform.tianji.process.server.execution;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationInstanceDTO.StateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.GraphVO;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Builder
public class AutomationInstanceProjectionBO {
    @NotNull @Valid @ToString.Exclude private AutomationInstanceBindingBO binding;
    @NotNull private String businessKey;
    @NotNull private String correlationId;
    @NotNull private StateEnum state;
    @NotNull private String version;
    private Instant finishedAt;
    @ToString.Exclude private Map<String, Object> authorizedOutput;
    @NotNull private List<String> availableActions;
    @NotNull @Valid private GraphVO graph;
}
