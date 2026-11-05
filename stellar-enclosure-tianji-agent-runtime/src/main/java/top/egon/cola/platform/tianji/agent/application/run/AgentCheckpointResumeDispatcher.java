package top.egon.cola.platform.tianji.agent.application.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Acknowledgement;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeCommand;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Optional internal dispatch on a dedicated, host-owned bounded executor. */
public final class AgentCheckpointResumeDispatcher implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AgentCheckpointResumeDispatcher.class);
    private static final int MAX_CAPACITY = 128;

    private final ThreadPoolExecutor executor;
    private final AdkAgentRunExecutor worker;
    private final AgentCheckpointTakeoverService takeover;
    private final DataSource source;
    private final ResumeAuthorization authority;
    private final String workerId = UUID.randomUUID().toString();
    private final Object monitor = new Object();
    private final Set<ResumeTask> active = new HashSet<>();
    private boolean closed;

    public AgentCheckpointResumeDispatcher(ThreadPoolExecutor executor, AdkAgentRunExecutor worker,
                                           AgentCheckpointTakeoverService takeover, DataSource source,
                                           ResumeAuthorization authority) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.takeover = Objects.requireNonNull(takeover, "takeover");
        this.source = Objects.requireNonNull(source, "source");
        this.authority = Objects.requireNonNull(authority, "authority");
        long queueCapacity = (long) executor.getQueue().size() + executor.getQueue().remainingCapacity();
        if (executor.isShutdown() || executor.getMaximumPoolSize() > MAX_CAPACITY || queueCapacity > MAX_CAPACITY
                || executor.getRejectedExecutionHandler().getClass() != ThreadPoolExecutor.AbortPolicy.class) {
            throw new IllegalArgumentException("Agent checkpoint dispatch requires a bounded aborting executor");
        }
        if (!worker.usesSessionDataSource(source) || !takeover.usesSourceAndAuthority(source, authority)) {
            throw new IllegalArgumentException("Agent checkpoint dispatch execution binding does not match");
        }
    }

    /**
     * Reports the durable acknowledgement after the local attempt. Caller waits do not own the task.
     * Dependents that block on other host work should run asynchronously.
     */
    public CompletionStage<Acknowledgement> dispatch(TrustedAgentActor actor, ResumeCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Agent checkpoint dispatch must start outside a transaction");
        }
        var task = new ResumeTask(actor, command);
        synchronized (monitor) {
            if (closed) throw unavailable();
            active.add(task);
            try {
                executor.execute(task);
            } catch (RejectedExecutionException rejected) {
                active.remove(task);
                executor.remove(task);
                task.fail(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED,
                        "Agent checkpoint dispatcher has no available capacity"));
            } catch (RuntimeException | Error failure) {
                active.remove(task);
                executor.remove(task);
                task.fail(failure);
                if (failure instanceof Error error) throw error;
            }
        }
        return task.acknowledgement.minimalCompletionStage();
    }

    /** Stops admission and removes only this dispatcher's unstarted tasks. Started work drains. */
    @Override
    public void close() {
        var unstarted = new ArrayList<ResumeTask>();
        synchronized (monitor) {
            if (closed) return;
            closed = true;
            var iterator = active.iterator();
            while (iterator.hasNext()) {
                var task = iterator.next();
                if (!task.started) {
                    iterator.remove();
                    unstarted.add(task);
                }
            }
        }
        unstarted.forEach(executor::remove);
        for (var task : unstarted) {
            task.fail(unavailable());
        }
    }

    public boolean awaitQuiescence(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("quiescence timeout must be positive");
        }
        CompletableFuture<?>[] drained;
        synchronized (monitor) {
            if (!closed) throw new IllegalStateException("close the checkpoint dispatcher before awaiting quiescence");
            drained = active.stream().map(task -> task.drained).toArray(CompletableFuture[]::new);
        }
        try {
            CompletableFuture.allOf(drained).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (ExecutionException completedWithFailure) {
            return true;
        } catch (TimeoutException timedOut) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Agent checkpoint dispatcher is unavailable");
    }

    private final class ResumeTask implements Runnable {
        private final TrustedAgentActor actor;
        private final ResumeCommand command;
        private final CompletableFuture<Acknowledgement> acknowledgement = new CompletableFuture<>();
        private final CompletableFuture<Void> drained = new CompletableFuture<>();
        private boolean started;

        private ResumeTask(TrustedAgentActor actor, ResumeCommand command) {
            this.actor = actor;
            this.command = command;
        }

        @Override
        public void run() {
            synchronized (monitor) {
                if (started || !active.contains(this)) return;
                started = true;
            }
            Acknowledgement committed = null;
            Throwable failure = null;
            try {
                if (Thread.currentThread().isInterrupted()) throw unavailable();
                var result = takeover.takeOver(actor, command, workerId);
                committed = result.acknowledgement();
                if (Thread.currentThread().isInterrupted()) throw unavailable();
                worker.executeResume(result, authority);
            } catch (RuntimeException | Error executionFailure) {
                failure = executionFailure;
                if (committed != null) {
                    log.warn("Agent checkpoint execution after claim failed errorType={}",
                            executionFailure.getClass().getSimpleName());
                }
                if (executionFailure instanceof Error error) throw error;
            } finally {
                synchronized (monitor) {
                    active.remove(this);
                }
                // A dependent callback can wait for shutdown. Publish the drain before notifying it.
                drained.complete(null);
                if (committed != null) acknowledgement.complete(committed);
                else acknowledgement.completeExceptionally(failure == null ? unavailable() : failure);
            }
        }

        private void fail(Throwable failure) {
            drained.complete(null);
            acknowledgement.completeExceptionally(failure);
        }
    }
}
