package top.egon.cola.platform.tianji.agent.application.context;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.component.agentflow.api.capability.CallPermitProvider;
import top.egon.cola.component.agentflow.api.context.ContextScopeVO;
import top.egon.cola.component.agentflow.api.context.ContextSource;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ModelCallLifecycle;
import top.egon.cola.component.agentflow.api.session.SessionStore;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/** In-process inputs assembled from the verified run and its lease. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreType
public class TianjiHostContextBO {
    @Valid @NotNull private ContextScopeVO verifiedScope;
    @NotNull private Instant deadline;
    @NotNull private CompletionStage<Void> cancellation;
    @NotNull private ContextSource contextSource;
    private SessionStore sessionStore;
    @NotNull private CallPermitProvider callPermitProvider;
    private ModelCallLifecycle modelCallLifecycle;
}
