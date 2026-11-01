package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationRecorder.AccountingState;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.StartResultAccessRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Records local access accounting separately from the immutable original START result. */
public final class ToolResultAccessRecorder {
    private final ToolResultAccessStore store;
    private final AgentLedgerService ledger;
    private static final ToolCommittedFactObserver NOOP_COMMITTED = status -> { };

    public ToolResultAccessRecorder(ToolResultAccessStore store, AgentLedgerService ledger) {
        this.store = Objects.requireNonNull(store, "store"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!store.usesLedger(ledger)) throw invalid();
    }
    public boolean usesStore(ToolResultAccessStore candidate) { return store == candidate; }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    public StartResultAccessVO recordKnownStartResultAccess(StartResultAccessRequestVO request) {
        return recordKnownStartResultAccess(request, NOOP_COMMITTED);
    }

    public StartResultAccessVO recordKnownStartResultAccess(StartResultAccessRequestVO request, ToolCommittedFactObserver observer) {
        if (observer == null) throw invalid();
        if (request == null) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("START result access requires its own transactions");
        var fact = store.recordKnownStartResultAccess(request); var budget = fact.accessBudget();
        if (!request.binding().run().runId().equals(fact.runId()) || !request.accessId().equals(fact.accessId())
                || !request.action().intentId().equals(fact.actionIntentId()) || budget == null || !request.reservationId().equals(budget.reservationId())
                || budget.leaseEpoch() != request.lease().epoch() || budget.kind() != AgentBudgetRequest.Kind.TOOL || !request.descriptor().versionKey().equals(budget.toolVersion())
                || budget.inputTokens() != 0 || budget.outputTokens() != 0 || budget.estimatedCost() == null || budget.estimatedCost().signum() != 0) throw unavailable();
        try { observer.onCommitted(fact.sourceOutcome().status()); }
        catch (RuntimeException privateObserver) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Committed tool fact observation could not complete");
        }
        AccountingState accounting = settledZero(budget) ? AccountingState.SETTLED : AccountingState.PENDING;
        if (accounting != AccountingState.SETTLED) {
            try {
                var settled = ledger.budgets().settleBudget(request.binding().run().actor(), request.lease(), request.reservationId(), new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
                accounting = settledZero(settled) ? AccountingState.SETTLED : AccountingState.PENDING;
            } catch (RuntimeException pending) { accounting = AccountingState.PENDING; }
        }
        // Accounting never extends the authority window that allowed this particular disclosure.
        store.checkDisclosureWindow(fact.window());
        return new StartResultAccessVO(fact.runId(), fact.accessId(), fact.actionIntentId(), fact.sourceActionRevision(), fact.sourceResultSha256(), fact.sourceOutcome(), accounting);
    }

    public record StartResultAccessVO(String runId, UUID accessId, String actionIntentId, long sourceActionRevision, String sourceResultSha256,
                                      ToolExecutionOutcome sourceOutcome, AccountingState accessAccountingState) {
        public StartResultAccessVO {
            try {
                if (runId == null || runId.isBlank() || !runId.equals(runId.trim()) || runId.length() > 64 || accessId == null || actionIntentId == null
                        || !UUID.fromString(actionIntentId).toString().equals(actionIntentId) || sourceActionRevision < 1 || sourceResultSha256 == null
                        || !sourceResultSha256.matches("[0-9a-f]{64}") || sourceOutcome == null || accessAccountingState == null
                        || !Set.of(ToolExecutionOutcome.Status.SUCCEEDED, ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN).contains(sourceOutcome.status())) throw invalid();
                if (sourceOutcome.status() == ToolExecutionOutcome.Status.SUCCEEDED) {
                    if (!sourceOutcome.output().keySet().equals(Set.of("instanceId")) || !(sourceOutcome.output().get("instanceId") instanceof String instanceId)) throw invalid();
                    var normalized = new FixedBpmnStartToolTransport.StartObservation(sourceOutcome.businessReceiptId(), instanceId);
                    if (!normalized.output().equals(sourceOutcome.output())) throw invalid();
                }
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "ToolResultAccessRecorder.StartResultAccessVO[redacted]"; }
    }

    private static boolean settledZero(AgentBudgetReservation budget) {
        return budget != null && budget.state() == AgentBudgetReservation.State.SETTLED && Long.valueOf(0).equals(budget.actualInputTokens())
                && Long.valueOf(0).equals(budget.actualOutputTokens()) && budget.actualCost() != null && budget.actualCost().signum() == 0;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("START result-access binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "START result-access accounting identity is unavailable"); }
}
