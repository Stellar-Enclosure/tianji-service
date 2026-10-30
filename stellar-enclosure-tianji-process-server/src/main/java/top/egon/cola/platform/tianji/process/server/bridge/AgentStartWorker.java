package top.egon.cola.platform.tianji.process.server.bridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentClient;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentSubmissionDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerAcquireStatusEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Short submit/ack orchestration. A host owns scheduling and a SERVICE client bound to this operator registration. */
public final class AgentStartWorker {
    private static final Logger log = LoggerFactory.getLogger(AgentStartWorker.class);
    private final AgentDispatchBinding binding;
    private final FlowableWorkerService workers;
    private final AgentDispatchStore store;
    private final LinkedAgentClient client;

    public AgentStartWorker(AgentDispatchBinding binding, FlowableWorkerService workers, AgentDispatchStore store,
                            LinkedAgentClient client) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.store = Objects.requireNonNull(store, "store");
        this.client = Objects.requireNonNull(client, "client");
    }

    public Outcome runOnce() {
        return runOnce(() -> true);
    }

    public Outcome runOnce(BooleanSupplier admission) {
        requireNoTransaction();
        Objects.requireNonNull(admission, "admission");
        if (!admission.getAsBoolean()) return Outcome.DEFERRED;
        var acquired = workers.acquireAgentDispatch(binding);
        if (acquired.status() == WorkerAcquireStatusEnum.EMPTY) return Outcome.EMPTY;
        var task = acquired.task();
        var intent = workers.prepareAgentDispatch(binding.actor(), task, binding, store);
        intent = store.refresh(intent.seed().key(), workers.isAgentDispatchActive(intent));
        if (intent.state() != AgentDispatchStore.State.CLOSING && intent.state() != AgentDispatchStore.State.CLOSED) {
            var resolved = client.resolve(intent.query());
            if (!intent.query().equals(resolved.query())) throw new ProcessConflictException();
            boolean submitRequired = !resolved.found();
            if (resolved.found()) {
                intent = store.record(intent.seed().key(), resolved.query(), resolved.submission());
                // A run can commit before its execution grant. Replay submit to finish queued admission before ACK.
                submitRequired = !resolved.submission().closed() && resolved.submission().run().status() == AgentRunStatusEnum.QUEUED;
            }
            if (submitRequired) {
                if (!admission.getAsBoolean()) return Outcome.DEFERRED;
                // Resolve may have taken the remaining submission window. The database clock decides admission again.
                intent = store.refresh(intent.seed().key(), workers.isAgentDispatchActive(intent));
                if (intent.state() != AgentDispatchStore.State.CLOSING && intent.state() != AgentDispatchStore.State.CLOSED) {
                    var started = client.submit(intent.command());
                    intent = store.record(intent.seed().key(), started.query(), new LinkedAgentSubmissionDTO(false, started.run()));
                }
            }
        }
        intent = store.refresh(intent.seed().key(), workers.isAgentDispatchActive(intent));
        if (intent.state() == AgentDispatchStore.State.CLOSING || intent.state() == AgentDispatchStore.State.CLOSED) {
            if (intent.state() == AgentDispatchStore.State.CLOSING) close(intent);
            workers.failAgentDispatch(binding.actor(), new FailWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(),
                    WorkerFailureKindEnum.UNKNOWN, "TIANJI_AGENT_DISPATCH_CLOSED"));
            return Outcome.CLOSED;
        }
        if (intent.runId() == null) throw new ProcessConflictException();
        workers.completeAgentDispatch(binding.actor(), task, binding, store, intent.runId());
        return Outcome.ACKNOWLEDGED;
    }

    /** Keep invoking after disabling new acquisition; unknown submissions still need a confirmed close fence. */
    public Reconciliation reconcileOnce(int limit, Duration observationInterval) {
        requireNoTransaction();
        int observed = 0;
        int closed = 0;
        int failed = 0;
        for (var candidate : store.due(binding, limit, observationInterval)) {
            observed++;
            try {
                var current = store.refresh(candidate.seed().key(), workers.isAgentDispatchActive(candidate));
                if (current.state() == AgentDispatchStore.State.CLOSING) {
                    close(current);
                    closed++;
                }
            } catch (RuntimeException failure) {
                // Remote bodies, task input and credentials are not diagnostic fields.
                log.warn("Agent dispatch reconciliation failed: {}", failure.getClass().getSimpleName());
                failed++;
            }
        }
        return new Reconciliation(observed, closed, failed);
    }

    private void close(AgentDispatchStore.Intent intent) {
        var result = client.close(intent.query());
        store.record(intent.seed().key(), result.query(), result.submission());
    }

    private static void requireNoTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent network dispatch cannot run inside a database transaction");
        }
    }

    public enum Outcome { EMPTY, ACKNOWLEDGED, CLOSED, DEFERRED }
    public record Reconciliation(int observed, int closed, int failed) { }
}
