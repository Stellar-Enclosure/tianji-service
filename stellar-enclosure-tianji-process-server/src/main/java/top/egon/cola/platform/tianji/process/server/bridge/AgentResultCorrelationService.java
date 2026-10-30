package top.egon.cola.platform.tianji.process.server.bridge;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceipt;
import top.egon.cola.platform.tianji.contract.orchestration.AgentResultReceiptStateEnum;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/** Applies an authenticated durable result in the same local transaction as native Message delivery and its execution audit. */
public final class AgentResultCorrelationService {
    private static final Logger log = LoggerFactory.getLogger(AgentResultCorrelationService.class);
    private final SpringProcessEngineConfiguration process;
    private final FlowableWorkerService workers;
    private final AgentResultInbox inbox;
    private final ProcessCommandReceiptStore receipts;
    private final TransactionTemplate transaction;
    private final Duration retryDelay;
    private final int maxFailures;

    public AgentResultCorrelationService(SpringProcessEngineConfiguration process, FlowableWorkerService workers, AgentResultInbox inbox,
                                          Duration retryDelay, int maxFailures) {
        this.process = Objects.requireNonNull(process, "process");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        if (!(process.getTransactionManager() instanceof DataSourceTransactionManager manager) || manager.getDataSource() == null
                || process.getRuntimeService() == null || retryDelay == null || retryDelay.compareTo(Duration.ofMillis(100)) < 0
                || retryDelay.compareTo(Duration.ofHours(1)) > 0 || retryDelay.toNanos() % 1_000_000 != 0 || maxFailures < 1 || maxFailures > 1000) throw invalid();
        FlowableAgentWaitInspector.requireHistory(process);
        workers.requireStorage(process, manager);
        inbox.requireStorage(manager.getDataSource(), manager, process.getDatabaseSchema());
        this.transaction = new TransactionTemplate(manager);
        this.receipts = new ProcessCommandReceiptStore(new JdbcTemplate(manager.getDataSource()), process.getDatabaseSchema());
        this.retryDelay = retryDelay;
        this.maxFailures = maxFailures;
    }

    public AgentResultReceipt apply(AgentResultProducerRegistry.Registration registration, String eventId) {
        requireOwnTransaction();
        try {
            return Objects.requireNonNull(transaction.execute(status -> applyInTransaction(registration, eventId)));
        } catch (RuntimeException failure) {
            try {
                transaction.executeWithoutResult(status -> {
                    var initial = inbox.find(registration, eventId, false);
                    inbox.lockDispatch(registration, initial);
                    var current = inbox.find(registration, eventId, true);
                    if (current.receipt().state() == AgentResultReceiptStateEnum.RECEIVED) {
                        inbox.reschedule(registration, current, retryDelay, maxFailures, true);
                    }
                });
            } catch (RuntimeException unavailable) {
                log.warn("Agent result retry persistence failed: type={}", unavailable.getClass().getName());
            }
            throw failure;
        }
    }

    private AgentResultReceipt applyInTransaction(AgentResultProducerRegistry.Registration registration, String eventId) {
        var initial = inbox.find(registration, eventId, false);
        var dispatch = inbox.lockDispatch(registration, initial);
        var current = inbox.find(registration, eventId, true);
        if (current.receipt().state() != AgentResultReceiptStateEnum.RECEIVED) return current.receipt();
        if (!current.retryNotAfter().isAfter(inbox.databaseNow())) return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_DEADLINE");
        if (current.failures() >= maxFailures) return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "RETRY_EXHAUSTED");
        AgentTerminalEvent event = current.event();
        if (!dispatch.query().equals(new LinkedAgentLookupQuery(event.idempotencyKey(), event.link()))
                || !dispatch.seed().binding().definitionKey().equals(event.definitionKey())
                || dispatch.seed().binding().definitionVersion() != event.definitionVersion()
                || dispatch.runId() != null && !dispatch.runId().equals(event.runId())) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.DEAD, "DISPATCH_MISMATCH");
        }
        if (dispatch.state() == AgentDispatchStore.State.CLOSED || dispatch.state() == AgentDispatchStore.State.CLOSING) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "DISPATCH_CLOSED");
        }
        var observed = workers.inspectAgentDispatch(dispatch);
        boolean active = observed.state() != FlowableAgentWaitInspector.State.PROCESS_ENDED && observed.state() != FlowableAgentWaitInspector.State.TIMER_WON;
        dispatch = inbox.refreshDispatch(dispatch, active);
        if (!active || dispatch.state() == AgentDispatchStore.State.CLOSED || dispatch.state() == AgentDispatchStore.State.CLOSING) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "WAIT_ENDED");
        }
        if (observed.state() == FlowableAgentWaitInspector.State.MESSAGE_CONSUMED) {
            return inbox.finish(registration, current, AgentResultReceiptStateEnum.IGNORED_LATE, "MESSAGE_CONSUMED");
        }
        if (dispatch.state() != AgentDispatchStore.State.ACKNOWLEDGED || dispatch.runId() == null
                || observed.state() != FlowableAgentWaitInspector.State.WAITING) {
            return inbox.reschedule(registration, current, retryDelay, maxFailures, false);
        }
        if (observed.modelWait() == null || observed.executionId() == null || observed.executionId().isBlank()) {
            throw new IllegalStateException("Agent result wait has no exact native execution");
        }
        var kind = ProcessCommandReceiptStore.Kind.AGENT_RESULT;
        receipts.insert(current.producer(), kind, eventId, event.fingerprint(), dispatch.seed().releaseKey(), dispatch.seed().correlationId());
        var audit = receipts.lock(current.producer(), kind, eventId);
        if (!event.fingerprint().equals(audit.requestHash()) || !dispatch.seed().releaseKey().equals(audit.releaseKey())
                || !dispatch.seed().correlationId().equals(audit.correlationId()) || !"PENDING".equals(audit.state()) || audit.resourceId() != null) {
            throw new IllegalStateException("Agent result application audit is inconsistent");
        }
        String target = observed.modelWait().statusVariable();
        Map<String, Object> variables = target == null ? Map.of() : Map.of(target, event.status().name());
        process.getRuntimeService().messageEventReceived(observed.modelWait().messageName(), observed.executionId(), variables);
        receipts.apply(current.producer(), kind, eventId, observed.executionId(), null);
        return inbox.finish(registration, current, AgentResultReceiptStateEnum.APPLIED, "MESSAGE_APPLIED");
    }

    public Reconciliation reconcile(AgentResultProducerRegistry.Registration registration, int limit) {
        requireOwnTransaction();
        int applied = 0;
        int ignored = 0;
        int pending = 0;
        int dead = 0;
        int errors = 0;
        var candidates = inbox.due(registration, limit, retryDelay);
        for (var candidate : candidates) {
            try {
                var receipt = apply(registration, candidate.event().eventId());
                switch (receipt.state()) {
                    case APPLIED -> applied++;
                    case IGNORED_LATE -> ignored++;
                    case DEAD -> dead++;
                    case RECEIVED -> pending++;
                }
            } catch (RuntimeException failure) {
                log.warn("Agent result application failed: type={}", failure.getClass().getName());
                errors++;
            }
        }
        return new Reconciliation(candidates.size(), applied, ignored, pending, dead, errors);
    }

    private static void requireOwnTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Agent result application must own its Process transaction");
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Agent result application configuration is invalid"); }
    public record Reconciliation(int observed, int applied, int ignoredLate, int pending, int dead, int errors) { }
}
