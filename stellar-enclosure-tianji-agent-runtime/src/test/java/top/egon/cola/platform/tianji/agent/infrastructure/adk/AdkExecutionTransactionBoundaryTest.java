package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdkExecutionTransactionBoundaryTest {
    @Test
    void an_ambient_transaction_is_rejected_before_claiming_or_opening_a_model() {
        var ledger = mock(AgentLedgerService.class); var models = mock(AgentModelBudgetEstimator.class);
        var factory = mock(AgentFlowFactory.class);
        var executor = new AdkAgentRunExecutor(ledger, factory, mock(AgentFlowConfigValidator.class), Map.of(), Map.of(), mock(ScheduledExecutorService.class), models);
        var actor = TrustedAgentActor.verified(20, TrustedAgentActor.SubjectType.USER, "alice", "https://idp.example/20");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> executor.executeQueued(actor, new GetAgentRunQuery("run-one"))); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(ledger, models, factory);
    }
}
