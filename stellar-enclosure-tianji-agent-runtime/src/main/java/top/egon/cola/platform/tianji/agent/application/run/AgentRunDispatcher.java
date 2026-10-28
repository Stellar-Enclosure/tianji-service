package top.egon.cola.platform.tianji.agent.application.run;

import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.AdkAgentRunExecutor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.time.Duration;
import java.util.Objects;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Submits one local attempt per run to a host-owned bounded executor. */
public final class AgentRunDispatcher implements AutoCloseable {
    private final Executor executor;
    private final AdkAgentRunExecutor worker;
    private final AgentLedgerService ledger;
    private final ConcurrentHashMap<RunKey, CompletableFuture<AgentRunDTO>> active = new ConcurrentHashMap<>();
    private final Object monitor = new Object();
    private boolean closed;

    public AgentRunDispatcher(Executor executor, AdkAgentRunExecutor worker, AgentLedgerService ledger) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.worker = Objects.requireNonNull(worker, "worker");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /** Discovers a bounded batch; rejected runs remain queued for a later poll. */
    public List<CompletionStage<AgentRunDTO>> dispatchQueued(int limit) {
        return ledger.pendingRuns(limit).stream().map(run -> dispatch(run.actor(), run.query())).toList();
    }

    /** Cancelling a returned wait does not cancel the underlying Agent run. */
    public CompletionStage<AgentRunDTO> dispatch(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        RunKey key = new RunKey(actor, query.runId());
        synchronized (monitor) {
            if (closed) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        "Agent dispatcher is closed");
            }
            CompletableFuture<AgentRunDTO> existing = active.get(key);
            if (existing != null) return existing.minimalCompletionStage();
            CompletableFuture<AgentRunDTO> result = new CompletableFuture<>();
            active.put(key, result);
            try {
                executor.execute(() -> {
                    try {
                        result.complete(worker.executeQueued(actor, query));
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                        if (failure instanceof Error error) throw error;
                    } finally {
                        active.remove(key, result);
                    }
                });
            } catch (RejectedExecutionException rejected) {
                active.remove(key, result);
                result.completeExceptionally(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED,
                        "Agent dispatcher has no available capacity"));
            } catch (RuntimeException failure) {
                active.remove(key, result);
                result.completeExceptionally(failure);
            } catch (Error failure) {
                active.remove(key, result);
                throw failure;
            }
            return result.minimalCompletionStage();
        }
    }

    /** Stops admission; the caller still owns the executor and the active runs. */
    @Override
    public void close() {
        synchronized (monitor) {
            closed = true;
        }
    }

    public boolean awaitQuiescence(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()
                || timeout.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("quiescence timeout must be positive");
        }
        CompletableFuture<?>[] snapshot;
        synchronized (monitor) {
            if (!closed) throw new IllegalStateException("close the dispatcher before awaiting quiescence");
            snapshot = active.values().toArray(CompletableFuture[]::new);
        }
        try {
            CompletableFuture.allOf(snapshot).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
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

    private record RunKey(TrustedAgentActor actor, String runId) { }
}
