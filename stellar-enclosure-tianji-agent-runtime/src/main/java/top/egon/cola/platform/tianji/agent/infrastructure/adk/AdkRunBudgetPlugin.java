package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.Plugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.GenerateContentConfig;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Applies a durable per-run reservation at each native ADK model boundary. */
final class AdkRunBudgetPlugin implements Plugin {
    private static final Logger log = LoggerFactory.getLogger(AdkRunBudgetPlugin.class);
    private final AgentLedgerService ledger;
    private final TrustedAgentActor actor;
    private final AgentRunLease lease;
    private final AgentExecutionSnapshot snapshot;
    private final AgentModelBudgetEstimator estimator;
    private final Scheduler retryScheduler;
    private final ScheduledExecutorService controlExecutor;
    private final Map<String, IdleGuard> idleGuards = new ConcurrentHashMap<>();
    private final Map<String, AgentModelBudgetEstimator.ContextWindow> contextWindows = new ConcurrentHashMap<>();
    private final FlowableProcessor<Boolean> idleSignal = ReplayProcessor.<Boolean>createWithSize(1).toSerialized();

    AdkRunBudgetPlugin(AgentLedgerService ledger, TrustedAgentActor actor, AgentRunLease lease,
                       AgentExecutionSnapshot snapshot, AgentModelBudgetEstimator estimator,
                       Scheduler retryScheduler, ScheduledExecutorService controlExecutor) {
        this.ledger = ledger;
        this.actor = actor;
        this.lease = lease;
        this.snapshot = snapshot;
        this.estimator = estimator;
        this.retryScheduler = retryScheduler;
        this.controlExecutor = controlExecutor;
    }

    @Override
    public String getName() {
        return "tianji-run-budget";
    }

    @Override
    public Maybe<LlmResponse> beforeModelCallback(CallbackContext context, LlmRequest.Builder request) {
        return safeErrors(Maybe.<LlmResponse>defer(() -> {
            LlmRequest modelRequest = request.build();
            AgentModelBudgetEstimator.Estimate estimate = estimate(modelRequest);
            GenerateContentConfig config = request.config().orElseGet(() -> GenerateContentConfig.builder().build());
            int outputLimit = config.maxOutputTokens().map(existing -> Math.min(existing, estimate.maxOutputTokens()))
                    .orElse(estimate.maxOutputTokens());
            if (outputLimit < 1) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, "model output limit is invalid");
            }
            AgentModelBudgetEstimator.ContextWindow window = contextWindow(modelRequest);
            var dependencies = snapshot.definition().dependencies();
            if (!window.modelName().equals(modelRequest.model().orElse(null))
                    || !window.modelRouteRef().equals(dependencies.modelRouteRef())
                    || !window.policyVersionRef().equals(dependencies.policyVersionRef())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        "model context policy does not match the execution snapshot");
            }
            AgentModelBudgetEstimator.ContextWindow first = contextWindows.putIfAbsent(window.modelName(), window);
            if (first != null && !first.equals(window)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        "model context policy changed during the run");
            }
            long available = window.windowTokens() - window.safetyMarginTokens();
            if (outputLimit > available || estimate.inputTokens() > available - outputLimit) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_BUDGET_EXCEEDED,
                        "model context window is exhausted");
            }
            AgentBudgetReservation reservation = ledger.budgets().reserveBudget(actor, lease,
                    new AgentBudgetRequest(context.eventId(), AgentBudgetRequest.Kind.MODEL, null,
                            estimate.inputTokens(), outputLimit, estimate.maximumCost()));
            if (!reservation.newlyCreated()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                        "model call reservation was already used");
            }
            IdleGuard guard = new IdleGuard(snapshot.effectivePolicy().modelIdleTimeout().toNanos());
            if (idleGuards.putIfAbsent(context.eventId(), guard) != null) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                        "model call idle guard was already registered");
            }
            guard.touch();
            request.config(config.toBuilder().maxOutputTokens(outputLimit).build());
            return Maybe.empty();
        }).retryWhen(errors -> errors.flatMap(failure -> {
            if (failure instanceof AgentLedgerException capacity
                    && capacity.code() == AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED) {
                return Flowable.timer(100, TimeUnit.MILLISECONDS, retryScheduler);
            }
            return Flowable.error(failure);
        })));
    }

    @Override
    public Maybe<LlmResponse> afterModelCallback(CallbackContext context, LlmResponse response) {
        return safeErrors(Maybe.defer(() -> {
            if (response.partial().orElse(false)) {
                IdleGuard guard = idleGuards.get(context.eventId());
                if (guard != null) guard.touch();
                return Maybe.empty();
            }
            closeIdleGuard(context.eventId());
            if (response.errorMessage().isPresent() || response.errorCode().isPresent()
                    || response.interrupted().orElse(false)) {
                ledger.budgets().settleBudget(actor, lease, context.eventId(), new AgentBudgetSettlement(null, null, null));
                return Maybe.just(LlmResponse.builder()
                        .errorMessage(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name())
                        .interrupted(true).build());
            }
            AgentBudgetSettlement actual = actualUsage(response);
            if (actual == null) actual = new AgentBudgetSettlement(null, null, null);
            AgentBudgetReservation settled = ledger.budgets().settleBudget(actor, lease, context.eventId(), actual);
            if (settled.state() == AgentBudgetReservation.State.UNKNOWN
                    && (actual.inputTokens() == null || actual.outputTokens() == null
                    || (snapshot.effectivePolicy().maxEstimatedCost() != null && actual.actualCost() == null))) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_USAGE_UNKNOWN,
                        "model usage cannot be verified against its reservation");
            }
            return Maybe.empty();
        }));
    }

    @Override
    public Maybe<LlmResponse> onModelErrorCallback(CallbackContext context, LlmRequest.Builder request,
                                                   Throwable failure) {
        closeIdleGuard(context.eventId());
        log.warn("Agent model stage=CALL outcome=ERROR errorType={}", failure.getClass().getSimpleName());
        return Maybe.just(LlmResponse.builder().errorMessage(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name())
                .interrupted(true).build());
    }

    @Override
    public io.reactivex.rxjava3.core.Completable close() {
        return io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            idleGuards.values().forEach(IdleGuard::close);
            idleGuards.clear();
            idleSignal.onComplete();
        });
    }

    Flowable<Long> idleTimeout() {
        return idleSignal.take(1).flatMap(ignored -> Flowable.error(new ModelIdleTimeout()));
    }

    private void closeIdleGuard(String eventId) {
        IdleGuard guard = idleGuards.remove(eventId);
        if (guard != null) guard.close();
    }

    static final class ModelIdleTimeout extends java.util.concurrent.TimeoutException { }

    private final class IdleGuard {
        private final long timeoutNanos;
        private long lastActivity;
        private boolean closed;
        private ScheduledFuture<?> future;

        private IdleGuard(long timeoutNanos) {
            this.timeoutNanos = Math.max(1, timeoutNanos);
        }

        private synchronized void touch() {
            if (closed) return;
            lastActivity = System.nanoTime();
            if (future == null) {
                future = controlExecutor.schedule(this::fire, timeoutNanos, TimeUnit.NANOSECONDS);
            }
        }

        private synchronized void fire() {
            if (closed) return;
            long remaining = timeoutNanos - (System.nanoTime() - lastActivity);
            if (remaining > 0) {
                future = controlExecutor.schedule(this::fire, remaining, TimeUnit.NANOSECONDS);
            } else {
                closed = true;
                idleSignal.onNext(true);
            }
        }

        private synchronized void close() {
            closed = true;
            if (future != null) future.cancel(false);
        }
    }

    @Override
    public Maybe<Map<String, Object>> beforeToolCallback(BaseTool tool, Map<String, Object> arguments,
                                                          ToolContext context) {
        return Maybe.error(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                "governed tool execution is not available"));
    }

    private AgentModelBudgetEstimator.Estimate estimate(LlmRequest request) {
        try {
            return Objects.requireNonNull(estimator.estimate(snapshot, request), "model budget estimate");
        } catch (RuntimeException failure) {
            throw safeFailure(failure);
        }
    }

    private AgentModelBudgetEstimator.ContextWindow contextWindow(LlmRequest request) {
        try {
            return Objects.requireNonNull(estimator.contextWindow(snapshot, request), "model context window");
        } catch (RuntimeException failure) {
            throw safeFailure(failure);
        }
    }

    private AgentBudgetSettlement actualUsage(LlmResponse response) {
        try {
            return estimator.actualUsage(snapshot, response);
        } catch (RuntimeException failure) {
            throw safeFailure(failure);
        }
    }

    private <T> Maybe<T> safeErrors(Maybe<T> source) {
        return source.onErrorResumeNext(failure -> failure instanceof AgentLedgerException
                ? Maybe.error(failure) : Maybe.error(safeFailure(failure)));
    }

    private static AgentLedgerException safeFailure(Throwable failure) {
        log.warn("Agent budget stage=ESTIMATE_OR_LEDGER outcome=ERROR errorType={}",
                failure.getClass().getSimpleName());
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "model budget boundary failed");
    }
}
