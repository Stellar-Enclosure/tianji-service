package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentFlowConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.AgentFlowInMemoryRunner;
import top.egon.cola.component.agentflow.runtime.AgentFlowSchedulers;
import top.egon.cola.component.agentflow.runtime.AgentFlowSessionRunner;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.application.context.DefaultContextAssembler;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.SessionLease;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Executes one explicitly selected queued run; it does not schedule or expose an endpoint. */
public final class AdkAgentRunExecutor {
    private static final Logger log = LoggerFactory.getLogger(AdkAgentRunExecutor.class);
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final long HEARTBEAT_SECONDS = 1;
    private final AgentLedgerService ledger;
    private final AgentFlowFactory flowFactory;
    private final AgentFlowConfigValidator flowValidator;
    private final Map<String, ChatModel> chatModels;
    private final Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories;
    private final String workerId = UUID.randomUUID().toString();
    private final ScheduledExecutorService controlExecutor;
    private final AgentModelBudgetEstimator budgetEstimator;
    private final AgentRunAuthorization authorizer;
    private final DataSource sessionDataSource;
    private final boolean typedSessionWrites;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdkEventProjector projector = new AdkEventProjector();

    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator, null);
    }

    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, false);
    }

    /** Typed session writes require a coordinated upgrade of all session readers. */
    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, (request, boundary) -> AgentRunAuthorization.Decision.UNAVAILABLE);
    }

    /** The host must provide a current, run-bound authorization verifier. */
    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.flowFactory = Objects.requireNonNull(flowFactory, "flowFactory");
        this.flowValidator = Objects.requireNonNull(flowValidator, "flowValidator");
        this.chatModels = Map.copyOf(chatModels);
        this.codeFirstFactories = Map.copyOf(codeFirstFactories);
        this.controlExecutor = Objects.requireNonNull(controlExecutor, "controlExecutor");
        this.budgetEstimator = Objects.requireNonNull(budgetEstimator, "budgetEstimator");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        if (sessionDataSource != null && !ledger.usesDataSource(sessionDataSource)) {
            throw new IllegalArgumentException("Agent sessions and runs require the same DataSource");
        }
        this.sessionDataSource = sessionDataSource;
        this.typedSessionWrites = typedSessionWrites;
    }

    /** Lets a host verify that its dispatcher uses the intended authorization boundary. */
    public boolean usesAuthorization(AgentRunAuthorization candidate) { return authorizer == candidate; }

    /** Lets a host verify that discovery and execution use the same ledger. */
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    /** Uses one CAS to claim the run and one subscription to the ADK root invocation. */
    public AgentRunDTO executeQueued(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("tenantId", Long.toString(actor.tenantId()));
            if (actor.subjectType() == TrustedAgentActor.SubjectType.USER) {
                MDC.put("userId", actor.subjectId());
            } else {
                MDC.remove("userId");
            }
            return executeScoped(actor, query);
        } finally {
            MDC.clear();
            if (previous != null && !previous.isEmpty()) MDC.setContextMap(previous);
        }
    }

    private AgentRunDTO executeScoped(TrustedAgentActor actor, GetAgentRunQuery query) {
        AgentRunDTO queued = ledger.get(actor, query);
        if (AgentRunTransitions.terminal(queued.status())) {
            return queued;
        }
        if (queued.status() != AgentRunStatusEnum.QUEUED) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run is not queued");
        }
        if (!queued.deadlineAt().isAfter(ledger.databaseNow())) {
            return ledger.expireDeadline(actor, query, queued.revision());
        }
        AgentLedgerService.ExecutionMaterial material = ledger.executionMaterial(actor, query);
        if (material.run().revision() != queued.revision()) {
            AgentRunDTO latest = material.run();
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                return ledger.expireDeadline(actor, query, latest.revision());
            }
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run changed before authorization");
        }
        AgentExecutionAuthorization authorization = new AgentExecutionAuthorization(authorizer, material.authorization());
        try {
            authorization.require(AgentRunAuthorization.Boundary.CLAIM);
        } catch (AgentExecutionAuthorization.Failure failure) {
            if (failure.decision() != AgentRunAuthorization.Decision.DENY) throw failure;
            try {
                return ledger.suspendQueuedForAuthorization(actor, query, queued.revision());
            } catch (AgentLedgerException race) {
                if (race.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) throw race;
                AgentRunDTO latest = ledger.get(actor, query);
                if (AgentRunTransitions.terminal(latest.status())) return latest;
                if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                    return ledger.expireDeadline(actor, query, latest.revision());
                }
                throw race;
            }
        }
        ClaimedAgentRun claimed;
        try {
            claimed = ledger.claim(actor, query, queued.revision(), workerId, LEASE_DURATION);
        } catch (AgentLedgerException race) {
            if (race.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) throw race;
            AgentRunDTO latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                return ledger.expireDeadline(actor, query, latest.revision());
            }
            throw race;
        }
        AgentRunDTO running = claimed.run();
        AgentRunLease lease = claimed.lease();
        AtomicReference<AgentRunDTO> current = new AtomicReference<>(running);
        Runner runner = null;
        PostgresAdkSessionService sessions = null;
        SessionLease sessionLease = null;
        boolean invocationStarted = false;
        try {
            AgentExecutionSnapshot snapshot = material.snapshot();
            requireSupportedInput(snapshot, running);
            var input = new DefaultContextAssembler().assemble(snapshot.input());
            BaseAgent root = buildRoot(snapshot.definition());
            requireNoTools(root);
            AdkRunBudgetPlugin budget = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, budgetEstimator,
                    authorization, Schedulers.from(controlExecutor), controlExecutor);
            String userId;
            String sessionId;
            if (running.sessionId() == null) {
                runner = new AgentFlowInMemoryRunner(root, "tianji_" + query.runId().replace('-', '_'), List.of(budget));
                userId = actor.tenantId() + ":" + actor.subjectType().name() + ":" + actor.subjectId();
                Session session = runner.sessionService().createSession(runner.appName(), userId).blockingGet();
                if (session == null) throw new IllegalStateException("ADK session could not be created");
                sessionId = session.id();
            } else {
                sessions = new PostgresAdkSessionService(sessionDataSource, actor, "tianji", false, typedSessionWrites);
                sessionLease = sessions.acquireSessionLease(running.sessionId(), lease, LEASE_DURATION);
                runner = new AgentFlowSessionRunner(root, "tianji", sessions.withLease(sessionLease), List.of(budget));
                userId = sessions.adkUserId();
                sessionId = running.sessionId();
            }
            Duration remaining = Duration.between(ledger.databaseNow(), running.deadlineAt());
            if (remaining.isZero() || remaining.isNegative()) {
                throw new IllegalStateException("Agent deadline has elapsed", new TimeoutException());
            }
            RunConfig config = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
                    .setMaxLlmCalls(snapshot.effectivePolicy().maxModelCalls()).setAutoCreateSession(false).build();
            long deadlineMillis = Math.max(1, remaining.toMillis());
            if (remaining.compareTo(Duration.ofMillis(deadlineMillis)) > 0 && deadlineMillis < Long.MAX_VALUE) {
                deadlineMillis++;
            }
            Flowable<Long> deadline = Flowable.timer(deadlineMillis, TimeUnit.MILLISECONDS)
                    .flatMap(ignored -> Flowable.error(new TimeoutException("Agent deadline elapsed")));
            PostgresAdkSessionService durableSessions = sessions;
            SessionLease durableLease = sessionLease;
            Flowable<Long> control = Flowable.interval(HEARTBEAT_SECONDS, TimeUnit.SECONDS,
                            Schedulers.from(controlExecutor))
                    .concatMap(ignored -> switch (ledger.heartbeat(actor, lease, LEASE_DURATION)) {
                        case ACTIVE -> {
                            if (durableSessions != null) durableSessions.renewSessionLease(durableLease, LEASE_DURATION);
                            yield Flowable.empty();
                        }
                        case CANCEL_REQUESTED -> Flowable.error(new CancellationSignal());
                        case DEADLINE_EXPIRED -> Flowable.error(new TimeoutException("Agent deadline elapsed"));
                        case LEASE_LOST -> Flowable.error(new AgentLedgerException(
                                AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent run lease is no longer valid"));
                    });
            Map<String, String> agentOutputs = new LinkedHashMap<>();
            Set<String> agentNames = new HashSet<>();
            collectAgentNames(root, agentNames);
            invocationStarted = true;
            runner.runAsync(userId, sessionId, Content.fromParts(input.parts().stream().map(Part::fromText).toArray(Part[]::new)), config)
                    .takeUntil(Flowable.merge(deadline, control, budget.idleTimeout()))
                    .blockingForEach(event -> projector.project(event, agentNames).ifPresent(projected -> {
                        if (projected.completeAgentOutput()) {
                            agentOutputs.put(projected.author(), (String) projected.payload().get("text"));
                        }
                        current.set(ledger.appendEvent(actor, lease, current.get().revision(),
                                projected.type(), projected.author(), projected.payload()));
                    }));
            if (agentOutputs.isEmpty()) {
                throw new IllegalStateException("ADK root completed without a usable agent output");
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("agents", new TreeMap<>(agentOutputs));
            String finalAgent = finalAgent(root);
            if (finalAgent != null && agentOutputs.containsKey(finalAgent)) {
                output.put("text", agentOutputs.get(finalAgent));
            }
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.COMPLETED,
                    output, null, snapshot.definition().outputSchemaVersion(), null, null, "UNKNOWN");
            return finishOrResolve(actor, query, lease, current.get().revision(), result);
        } catch (RuntimeException failure) {
            AgentExecutionAuthorization.Failure authorizationFailure = findCause(failure, AgentExecutionAuthorization.Failure.class);
            if (authorizationFailure != null) {
                return suspendForAuthorizationOrResolve(actor, lease, authorizationFailure.decision());
            }
            if (failure instanceof AgentLedgerException conflict
                    && (conflict.code() == AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT
                    || conflict.code() == AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED)) {
                AgentRunDTO latest = ledger.get(actor, query);
                if (AgentRunTransitions.terminal(latest.status())) {
                    return latest;
                }
                if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()) {
                    return finishCancellation(actor, lease, latest);
                }
                if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                    return ledger.expireDeadline(actor, query, latest.revision());
                }
                throw failure;
            }
            if (hasCause(failure, CancellationSignal.class)) {
                return finishCancellation(actor, lease, ledger.get(actor, query));
            }
            if (hasCause(failure, AdkRunBudgetPlugin.ModelIdleTimeout.class)) {
                AgentExecutionResult idle = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                        Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_MODEL_IDLE_TIMEOUT.name(), "UNKNOWN");
                return finishOrResolve(actor, query, lease, current.get().revision(), idle);
            }
            AgentRunStatusEnum status = isTimeout(failure) ? AgentRunStatusEnum.TIMED_OUT : AgentRunStatusEnum.FAILED;
            String code = isTimeout(failure) ? AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name()
                    : failure instanceof AgentLedgerException ledgerFailure ? ledgerFailure.code().name()
                    : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name();
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), status, Map.of(), null, null,
                    null, code, invocationStarted ? "UNKNOWN" : "NONE");
            if (status == AgentRunStatusEnum.TIMED_OUT) {
                AgentRunDTO latest = ledger.get(actor, query);
                return AgentRunTransitions.terminal(latest.status()) ? latest
                        : ledger.expireDeadline(actor, query, latest.revision());
            }
            return finishOrResolve(actor, query, lease, current.get().revision(), result);
        } finally {
            if (runner != null) {
                try {
                    runner.close().timeout(5, TimeUnit.SECONDS).blockingAwait();
                } catch (RuntimeException closeFailure) {
                    log.warn("Agent runner stage=SHUTDOWN outcome=ERROR errorType={}",
                            closeFailure.getClass().getSimpleName());
                }
            }
            if (sessions != null && sessionLease != null) {
                try {
                    sessions.releaseSessionLease(sessionLease);
                } catch (RuntimeException releaseFailure) {
                    log.warn("Agent session stage=RELEASE outcome=ERROR errorType={}",
                            releaseFailure.getClass().getSimpleName());
                }
            }
        }
    }

    private AgentRunDTO suspendForAuthorizationOrResolve(TrustedAgentActor actor, AgentRunLease lease,
                                                          AgentRunAuthorization.Decision decision) {
        GetAgentRunQuery query = new GetAgentRunQuery(lease.runId());
        try {
            return ledger.suspendForAuthorization(actor, lease, decision);
        } catch (AgentLedgerException race) {
            if (race.code() != AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED) throw race;
            AgentRunDTO latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()) {
                return finishCancellation(actor, lease, latest);
            }
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                return ledger.expireDeadline(actor, query, latest.revision());
            }
            throw race;
        }
    }

    private AgentRunDTO finishCancellation(TrustedAgentActor actor, AgentRunLease lease, AgentRunDTO current) {
        if (AgentRunTransitions.terminal(current.status())) {
            return current;
        }
        AgentExecutionResult result = new AgentExecutionResult(lease.runId(), AgentRunStatusEnum.CANCELLED,
                Map.of(), null, null, null, null, "UNKNOWN");
        return finishOrResolve(actor, new GetAgentRunQuery(lease.runId()), lease, current.revision(), result);
    }

    private AgentRunDTO finishOrResolve(TrustedAgentActor actor, GetAgentRunQuery query, AgentRunLease lease,
                                        long revision, AgentExecutionResult result) {
        try {
            return ledger.finish(actor, lease, revision, result);
        } catch (AgentLedgerException race) {
            if (race.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT
                    && race.code() != AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED) throw race;
            AgentRunDTO latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                return ledger.expireDeadline(actor, query, latest.revision());
            }
            if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()
                    && result.status() != AgentRunStatusEnum.CANCELLED) {
                return finishCancellation(actor, lease, latest);
            }
            throw race;
        }
    }

    private void requireSupportedInput(AgentExecutionSnapshot snapshot, AgentRunDTO run) {
        if (run.sessionId() != null && sessionDataSource == null) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "persistent session storage is not configured");
        }
        var dependencies = snapshot.definition().dependencies();
        if (!dependencies.prompts().isEmpty() || !dependencies.skills().isEmpty()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "versioned prompt and skill resolution is not available");
        }
        if (!dependencies.tools().isEmpty() || !snapshot.effectivePolicy().allowedToolVersions().isEmpty()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "governed tool execution is not available");
        }
    }

    private BaseAgent buildRoot(AgentDefinitionSpecDTO definition) {
        if ("CONFIG".equals(definition.executionMode())) {
            AgentFlowConfigDTO config = mapper.convertValue(definition.configuration(), AgentFlowConfigDTO.class);
            flowValidator.validate(new AgentFlowProperties(true, Duration.ofMinutes(2), Duration.ofSeconds(10),
                    Map.of("tianji", config)));
            ChatModel model = chatModels.get(config.chatModelBeanName());
            if (model == null) {
                throw new IllegalArgumentException("configured ChatModel is unavailable");
            }
            if (model.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null
                    && !options.getToolCallbacks().isEmpty()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "model callbacks require governed tool execution");
            }
            return flowFactory.compileRoot("tianji", config, chatModels, List.of());
        }
        if ("CODE_FIRST".equals(definition.executionMode()) && definition.codeRef() != null) {
            Function<Scheduler, ? extends BaseAgent> factory = codeFirstFactories.get(definition.codeRef());
            if (factory == null) {
                throw new IllegalArgumentException("trusted Agent factory is unavailable");
            }
            return Objects.requireNonNull(factory.apply(AgentFlowSchedulers.contextAwareIo()),
                    "trusted Agent factory returned null");
        }
        throw new IllegalArgumentException("unsupported Agent definition mode");
    }

    private static void requireNoTools(BaseAgent agent) {
        if (agent instanceof LlmAgent llm) {
            if (!llm.tools().blockingGet().isEmpty() || !llm.toolsets().isEmpty()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "governed tool execution is not available");
            }
            // ADK invokes agent-level before-model callbacks after the runtime budget plugin.
            // They could change the already-metered request or output limit.
            if (!llm.canonicalBeforeModelCallbacks().isEmpty()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "Agent model callbacks require final-request governance");
            }
        } else if (!(agent instanceof SequentialAgent || agent instanceof ParallelAgent || agent instanceof LoopAgent)) {
            throw new IllegalArgumentException("this Agent type requires additional execution governance");
        }
        agent.subAgents().forEach(AdkAgentRunExecutor::requireNoTools);
    }

    private static String finalAgent(BaseAgent root) {
        if (root instanceof LlmAgent) {
            return root.name();
        }
        if ((root instanceof SequentialAgent || root instanceof LoopAgent) && !root.subAgents().isEmpty()) {
            return finalAgent(root.subAgents().getLast());
        }
        return null;
    }

    private static void collectAgentNames(BaseAgent agent, Set<String> names) {
        if (!names.add(agent.name())) {
            throw new IllegalArgumentException("Agent names must be unique within a run");
        }
        agent.subAgents().forEach(child -> collectAgentNames(child, names));
    }

    private static boolean isTimeout(Throwable failure) {
        return hasCause(failure, TimeoutException.class);
    }

    private static boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        return findCause(failure, type) != null;
    }

    private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 64; depth++, current = current.getCause()) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
    }

    private static final class CancellationSignal extends RuntimeException { }
}
