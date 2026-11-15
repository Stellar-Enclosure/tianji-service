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
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
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
    private final AgentDispatchStore.TypedRoute typedRoute;

    public AgentStartWorker(AgentDispatchBinding binding, FlowableWorkerService workers, AgentDispatchStore store,
                            LinkedAgentClient client) {
        this.binding = Objects.requireNonNull(binding, "binding");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.store = Objects.requireNonNull(store, "store");
        this.client = Objects.requireNonNull(client, "client");
        this.typedRoute = null;
    }

    public AgentStartWorker(AgentDispatchStore.TypedRoute route, FlowableWorkerService workers, AgentDispatchStore store,
                            LinkedAgentClient client) {
        this.binding = null;
        this.workers = Objects.requireNonNull(workers, "workers");
        this.store = Objects.requireNonNull(store, "store");
        this.client = Objects.requireNonNull(client, "client");
        this.typedRoute = Objects.requireNonNull(route, "route");
    }

    public Outcome runOnce() {
        return runOnce(() -> true);
    }

    public Outcome runOnce(BooleanSupplier admission) {
        requireNoTransaction();
        Objects.requireNonNull(admission, "admission");
        if (!admission.getAsBoolean()) return Outcome.DEFERRED;
        if (typedRoute != null) return runTypedOnce(admission);
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

    private Outcome runTypedOnce(BooleanSupplier admission) {
        var acquired = workers.acquireTypedAgentDispatch(typedRoute);
        if (acquired.status() == WorkerAcquireStatusEnum.EMPTY) return Outcome.EMPTY;
        var task = acquired.task();
        var prepared = workers.prepareTypedAgentDispatch(typedRoute, task, store);
        var intent = refreshTyped(prepared.intent());
        if (open(intent)) {
            var resolved = client.resolve(intent.query());
            if (!intent.query().equals(resolved.query())) throw new ProcessConflictException();
            boolean submitRequired = !resolved.found();
            if (resolved.found()) {
                var evidence = resolved.submission();
                intent = store.record(intent.seed().key(), resolved.query(), evidence);
                // A queued Run can be visible before its execution grant is stored.
                submitRequired = !evidence.closed() && evidence.run().status() == AgentRunStatusEnum.QUEUED;
            }
            if (submitRequired) {
                if (!admission.getAsBoolean()) return Outcome.DEFERRED;
                intent = refreshTyped(intent);
                if (open(intent)) {
                    var command = prepared.command();
                    if (!intent.query().idempotencyKey().equals(command.getIdempotencyKey())
                            || !intent.query().link().equals(command.getLink())) throw new ProcessConflictException();
                    var submitted = client.submitRuntime(command);
                    AgentDispatchStore.requireRuntimeOutcome(intent, submitted);
                    intent = store.recordTyped(intent.seed().key(), submitted);
                }
            }
        }
        intent = refreshTyped(intent);
        if (!open(intent)) {
            if (intent.state() == AgentDispatchStore.State.CLOSING) close(intent);
            return Outcome.CLOSED;
        }
        if (intent.runId() == null) throw new ProcessConflictException();
        workers.completeTypedAgentDispatch(typedRoute, task, store, intent.runId());
        return Outcome.ACKNOWLEDGED;
    }

    private AgentDispatchStore.Intent refreshTyped(AgentDispatchStore.Intent intent) {
        return store.refresh(intent.seed().key(), workers.isTypedAgentDispatchActive(intent));
    }

    private static boolean open(AgentDispatchStore.Intent intent) {
        return intent.state() != AgentDispatchStore.State.CLOSING && intent.state() != AgentDispatchStore.State.CLOSED;
    }

    /** Keep invoking after disabling new acquisition; unknown submissions still need a confirmed close fence. */
    public Reconciliation reconcileOnce(int limit, Duration observationInterval) {
        requireNoTransaction();
        int observed = 0;
        int closed = 0;
        int failed = 0;
        var due = typedRoute == null ? store.due(binding, limit, observationInterval) : store.dueTyped(typedRoute, limit, observationInterval);
        for (var candidate : due) {
            observed++;
            try {
                var current = typedRoute == null
                        ? store.refresh(candidate.seed().key(), workers.isAgentDispatchActive(candidate))
                        : refreshTyped(candidate);
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
    public record TypedDispatch(AgentDispatchStore.Intent intent, LinkedAgentRuntimeCommand command) {
        public TypedDispatch {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(command, "command");
            var seed = intent.seed();
            if (!seed.typed() || !intent.query().idempotencyKey().equals(command.getIdempotencyKey())
                    || !intent.query().link().equals(command.getLink())
                    || !seed.inputs().get("nodeExecutionRef").equals(command.getNodeExecutionRef())
                    || !seed.inputs().get("configurationSha256").equals(command.getConfigurationSha256())
                    || !seed.binding().definitionKey().equals(command.getTemplateKey())
                    || seed.binding().definitionVersion() != command.getTemplateVersion()
                    || !seed.binding().definitionContentSha256().equals(command.getTemplateSha256())
                    || !seed.correlationId().equals(command.getCorrelationId())
                    || !command.isLinkedRuntimeConsistent()) throw new ProcessConflictException();
        }
    }
}
