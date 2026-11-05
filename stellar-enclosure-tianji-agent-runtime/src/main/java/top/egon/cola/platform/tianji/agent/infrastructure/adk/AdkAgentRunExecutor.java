package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.Execution;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.ResumeAuthorization;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.AgentCheckpointTakeoverService.TakeoverResult;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.FirstInputCheckpointBO;
import top.egon.cola.platform.tianji.agent.infrastructure.adk.session.PostgresAdkSessionService.SessionLease;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentStorageVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/** Executes an explicitly selected Run or trusted checkpoint claim. */
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
    private final String checkpointEngineVersion;
    private final DefaultContextAssembler contextAssembler;
    private final AgentContextRuntimeOptions contextOptions;
    private final Supplier<? extends AdkGovernedToolBridge> governedToolSupplier;
    private volatile AdkGovernedToolBridge governedToolBridge;
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
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, authorizer, new DefaultContextAssembler());
    }

    /** Context providers are selected by trusted host code and preserve the existing failure-closed default. */
    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
                               DefaultContextAssembler contextAssembler) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, authorizer, contextAssembler, false);
    }

    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
                               DefaultContextAssembler contextAssembler, boolean contextSnapshots) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, authorizer, contextAssembler, new AgentContextRuntimeOptions(contextSnapshots, false));
    }

    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
                               DefaultContextAssembler contextAssembler, AgentContextRuntimeOptions contextOptions) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, authorizer, contextAssembler, contextOptions, null);
    }

    /** Governed tools are resolved after the host has installed its telemetry policy. */
    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
                               DefaultContextAssembler contextAssembler, AgentContextRuntimeOptions contextOptions,
                               Supplier<? extends AdkGovernedToolBridge> governedTools) {
        this(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories, controlExecutor, budgetEstimator,
                sessionDataSource, typedSessionWrites, authorizer, contextAssembler, contextOptions, governedTools, false);
    }

    /** Trusted runtime assembly keeps the existing constructor contracts and disabled defaults. */
    public static AdkAgentRunExecutor forRuntime(AgentLedgerService ledger, AgentFlowFactory flowFactory,
            AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
            Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
            ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
            DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
            DefaultContextAssembler contextAssembler, AgentContextRuntimeOptions contextOptions,
            Supplier<? extends AdkGovernedToolBridge> governedTools, boolean checkpointCaptureEnabled) {
        return new AdkAgentRunExecutor(ledger, flowFactory, flowValidator, chatModels, codeFirstFactories,
                controlExecutor, budgetEstimator, sessionDataSource, typedSessionWrites, authorizer, contextAssembler,
                contextOptions, governedTools, checkpointCaptureEnabled);
    }

    private AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Function<Scheduler, ? extends BaseAgent>> codeFirstFactories,
                               ScheduledExecutorService controlExecutor, AgentModelBudgetEstimator budgetEstimator,
                               DataSource sessionDataSource, boolean typedSessionWrites, AgentRunAuthorization authorizer,
                               DefaultContextAssembler contextAssembler, AgentContextRuntimeOptions contextOptions,
                               Supplier<? extends AdkGovernedToolBridge> governedTools, boolean checkpointCaptureEnabled) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.flowFactory = Objects.requireNonNull(flowFactory, "flowFactory");
        this.flowValidator = Objects.requireNonNull(flowValidator, "flowValidator");
        this.chatModels = Map.copyOf(chatModels);
        this.codeFirstFactories = Map.copyOf(codeFirstFactories);
        this.controlExecutor = Objects.requireNonNull(controlExecutor, "controlExecutor");
        this.budgetEstimator = Objects.requireNonNull(budgetEstimator, "budgetEstimator");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.contextAssembler = Objects.requireNonNull(contextAssembler, "contextAssembler");
        this.contextOptions = Objects.requireNonNull(contextOptions, "contextOptions");
        this.governedToolSupplier = governedTools;
        if (sessionDataSource != null && !ledger.usesDataSource(sessionDataSource)) {
            throw new IllegalArgumentException("Agent sessions and runs require the same DataSource");
        }
        this.sessionDataSource = sessionDataSource;
        this.typedSessionWrites = typedSessionWrites;
        if (checkpointCaptureEnabled) {
            if (sessionDataSource == null) throw new IllegalArgumentException("Checkpoint capture requires durable Agent storage");
            checkpointEngineVersion = checkpointEngineVersion();
            new AgentStorageVerifier(sessionDataSource).verifyCheckpointCapture();
        } else {
            checkpointEngineVersion = null;
        }
        if (contextOptions.snapshots()) ledger.verifyContextSnapshotStorage();
    }

    /** Lets a host verify that its dispatcher uses the intended authorization boundary. */
    public boolean usesAuthorization(AgentRunAuthorization candidate) { return authorizer == candidate; }

    public boolean usesReadOnlyDecisionTools(AdkReadOnlyDecisionToolBridge candidate) {
        return candidate != null && governedToolSupplier != null && governedTools() == candidate;
    }

    public boolean usesGovernedProcessTools(AdkGovernedProcessToolBridge candidate) {
        return candidate != null && governedToolSupplier != null && governedTools() == candidate;
    }

    private AdkGovernedToolBridge governedTools() {
        var selected = governedToolBridge;
        if (selected == null) {
            synchronized (this) {
                selected = governedToolBridge;
                if (selected == null) {
                    try { selected = Objects.requireNonNull(governedToolSupplier.get(), "read-only decision tools"); }
                    catch (RuntimeException failure) {
                        throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "governed read-only tools are unavailable");
                    }
                    governedToolBridge = selected;
                }
            }
        }
        return selected;
    }

    /** Lets a host verify that discovery and execution use the same ledger. */
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    public boolean usesSessionDataSource(DataSource candidate) {
        return candidate != null && candidate == sessionDataSource;
    }

    /** Uses one CAS to claim the run and one subscription to the ADK root invocation. */
    public AgentRunDTO executeQueued(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Agent execution cannot run inside a database transaction");
        return withExecutionContext(actor, () -> executeScoped(actor, query));
    }

    /** Executes the one winning, explicitly authorized checkpoint claim in this process. */
    public Optional<AgentRunDTO> executeResume(TakeoverResult takeover, ResumeAuthorization authority) {
        Objects.requireNonNull(takeover, "takeover");
        Objects.requireNonNull(authority, "authority");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent execution cannot run inside a database transaction");
        }
        if (sessionDataSource == null) throw resumeUnavailable();
        checkpointEngineVersion();
        return takeover.consumeForExecution(sessionDataSource, authority, typedSessionWrites)
                .map(resume -> withExecutionContext(resume.actor(), () -> executeResumed(resume)));
    }

    private AgentRunDTO executeResumed(Execution resume) {
        var actor = resume.actor();
        var lease = resume.runLease();
        var query = new GetAgentRunQuery(lease.runId());
        boolean entered = false;
        try {
            var material = ledger.executionMaterial(actor, query);
            if (!resume.matchesClaimed(material)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent resumed run changed before execution");
            }
            var authorization = new AgentExecutionAuthorization((request, boundary) -> {
                if (boundary != AgentRunAuthorization.Boundary.MODEL || !resume.request().equals(request)) {
                    return AgentRunAuthorization.Decision.DENY;
                }
                var decision = resume.authorizeResume(ledger.databaseNow());
                return decision == AgentRunAuthorization.Decision.ALLOW
                        ? authorizer.authorize(request, boundary) : decision;
            }, material.authorization());
            var claimed = new ClaimedAgentRun(material.run(), lease);
            entered = true;
            return runClaimed(actor, query, material, authorization, claimed, resume);
        } finally {
            if (!entered) releaseSession(resume.boundSession(), resume.sessionLease());
        }
    }

    private AgentRunDTO withExecutionContext(TrustedAgentActor actor, Supplier<AgentRunDTO> execution) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("tenantId", Long.toString(actor.tenantId()));
            if (actor.subjectType() == TrustedAgentActor.SubjectType.USER) {
                MDC.put("userId", actor.subjectId());
            } else {
                MDC.remove("userId");
            }
            return execution.get();
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
        return runClaimed(actor, query, material, authorization, claimed, null);
    }

    private AgentRunDTO runClaimed(TrustedAgentActor actor, GetAgentRunQuery query,
                                  AgentLedgerService.ExecutionMaterial material, AgentExecutionAuthorization authorization,
                                  ClaimedAgentRun claimed, Execution resume) {
        AgentRunDTO running = claimed.run();
        AgentRunLease lease = claimed.lease();
        AtomicReference<AgentRunDTO> current = new AtomicReference<>(running);
        RunEventSink eventSink = new RunEventSink(actor, lease, current);
        Runner runner = null;
        PostgresAdkSessionService sessions = resume == null ? null : resume.boundSession();
        SessionLease sessionLease = resume == null ? null : resume.sessionLease();
        AdkGovernedToolBridge.Scope toolScope = null;
        NativeAgentGate nativeGate = null;
        boolean nativeSelected = "CODE_FIRST".equals(material.snapshot().definition().executionMode())
                && material.snapshot().definition().codeRef() != null
                && codeFirstFactories.get(material.snapshot().definition().codeRef()) instanceof NativeAgentCompilerStrategy;
        boolean invocationStarted = false;
        try {
            AgentExecutionSnapshot snapshot = material.snapshot();
            requireSupportedInput(snapshot, running);
            if (resume != null && !checkpointRequestEligible(material, running)) throw resumeUnavailable();
            Content userInput = resume == null
                    ? Content.fromParts(contextAssembler.assemble(snapshot.input()).parts().stream().map(Part::fromText).toArray(Part[]::new))
                    : null;
            var dependencies = snapshot.definition().dependencies();
            NativeAgentCompilerStrategy nativeCompiler = nativeSelected
                    ? (NativeAgentCompilerStrategy) codeFirstFactories.get(snapshot.definition().codeRef()) : null;
            NativeAgentCompilerStrategy.TemplateSpec nativeSpec = nativeCompiler == null ? null
                    : requireNativeDefinition(snapshot, nativeCompiler);
            if (!dependencies.tools().isEmpty() || !snapshot.effectivePolicy().allowedToolVersions().isEmpty()) {
                toolScope = governedTools().bindScope(material, lease, eventSink::tool);
            }
            List<BaseTool> tools = toolScope == null ? List.of() : toolScope.declaredTools();
            BaseAgent root = buildRoot(snapshot.definition(), tools);
            if (resume != null && !checkpointRootEligible(root)) throw resumeUnavailable();
            Set<String> agentNames = new HashSet<>();
            validateAgentTopology(root, agentNames, new HashSet<>());
            requireGovernedTools(root, tools);
            AgentModelBudgetEstimator selectedEstimator = budgetEstimator;
            if (nativeCompiler != null) {
                ChatModel model = chatModels.get(nativeSpec.modelBeanName());
                String transferEventType = nativeSpec.topology() == NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1
                        ? "NATIVE_TASK_TRANSFER_AUTHORIZED" : "NATIVE_TRANSFER_AUTHORIZED";
                nativeGate = NativeAgentGate.bind(nativeSpec, root, nativeSpec.topologyHash(dependencies.policyVersionRef()),
                        (author, payload) -> eventSink.append(transferEventType, author, payload));
                selectedEstimator = Objects.requireNonNull(budgetEstimator.bindNative(snapshot,
                        new AgentModelBudgetEstimator.NativeModelBinding(nativeSpec.codeRef(), nativeSpec.modelBeanName(), nativeSpec.modelName(), model)),
                        "Native model budget binding is unavailable");
            }
            AdkRunBudgetPlugin budget = new AdkRunBudgetPlugin(ledger, actor, lease, snapshot, selectedEstimator,
                    authorization, Schedulers.from(controlExecutor), controlExecutor, contextAssembler, material.authorization(), contextOptions, toolScope, nativeGate);
            String userId;
            String sessionId;
            if (resume != null) {
                requireResumeFence(resume);
                userId = sessions.adkUserId();
                sessionId = running.sessionId();
                budget.bindResumeContext(userId, sessionId, resume.invocationId());
                runner = AgentFlowSessionRunner.withExplicitResume(root, "tianji", sessions, List.of(budget));
            } else if (running.sessionId() == null) {
                runner = new AgentFlowInMemoryRunner(root, "tianji_" + query.runId().replace('-', '_'), List.of(budget));
                userId = actor.tenantId() + ":" + actor.subjectType().name() + ":" + actor.subjectId();
                Session session = runner.sessionService().createSession(runner.appName(), userId).blockingGet();
                if (session == null) throw new IllegalStateException("ADK session could not be created");
                sessionId = session.id();
            } else {
                sessions = new PostgresAdkSessionService(sessionDataSource, actor, "tianji", false, typedSessionWrites);
                sessionLease = sessions.acquireSessionLease(running.sessionId(), lease, LEASE_DURATION);
                var checkpoint = firstInputCheckpoint(material, running, root, userInput);
                var boundSessions = checkpoint == null ? sessions.withLease(sessionLease) : sessions.withLease(sessionLease, checkpoint);
                runner = new AgentFlowSessionRunner(root, "tianji", boundSessions, List.of(budget));
                userId = sessions.adkUserId();
                sessionId = running.sessionId();
            }
            boolean cappedByLease = resume != null && lease.expiresAt().isBefore(running.deadlineAt());
            Instant executionDeadline = cappedByLease ? lease.expiresAt() : running.deadlineAt();
            Duration remaining = Duration.between(ledger.databaseNow(), executionDeadline);
            if (remaining.isZero() || remaining.isNegative()) {
                if (cappedByLease) throw resumeLeaseExpired();
                throw new IllegalStateException("Agent deadline has elapsed", new TimeoutException());
            }
            RunConfig config = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
                    .setMaxLlmCalls(snapshot.effectivePolicy().maxModelCalls()).setAutoCreateSession(false).build();
            long deadlineMillis = Math.max(1, remaining.toMillis());
            if (remaining.compareTo(Duration.ofMillis(deadlineMillis)) > 0 && deadlineMillis < Long.MAX_VALUE) {
                deadlineMillis++;
            }
            Flowable<Long> deadline = resume == null
                    ? Flowable.timer(deadlineMillis, TimeUnit.MILLISECONDS)
                        .flatMap(ignored -> Flowable.error(new TimeoutException("Agent deadline elapsed")))
                    : Flowable.timer(remaining.toNanos(), TimeUnit.NANOSECONDS)
                        .flatMap(ignored -> Flowable.error(cappedByLease ? resumeLeaseExpired() : new TimeoutException("Agent deadline elapsed")));
            PostgresAdkSessionService durableSessions = sessions;
            SessionLease durableLease = sessionLease;
            if (resume != null) budget.bindSessionFence(() -> requireResumeFence(resume));
            else if (durableSessions != null) budget.bindSessionFence(() -> durableSessions.verifySessionLease(durableLease));
            Flowable<Long> ticks = Flowable.interval(HEARTBEAT_SECONDS, TimeUnit.SECONDS, Schedulers.from(controlExecutor));
            Flowable<Long> control = resume != null ? ticks.concatMap(ignored -> observeResumeControl(actor, query, resume))
                    : ticks.concatMap(ignored -> switch (ledger.heartbeat(actor, lease, LEASE_DURATION)) {
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
            NativeAgentGate activeNativeGate = nativeGate;
            invocationStarted = true;
            Flowable<Event> invocation = resume == null ? runner.runAsync(userId, sessionId, userInput, config)
                    : runner.runAsync(userId, sessionId, resume.invocationId(), null, config, null);
            invocation.takeUntil(Flowable.merge(deadline, control, budget.idleTimeout(),
                            activeNativeGate == null ? Flowable.never() : activeNativeGate.failureSignal()))
                    .blockingForEach(event -> {
                        if (resume != null && !resume.invocationId().equals(event.invocationId())) throw resumeUnavailable();
                        var projection = projector.project(event, agentNames);
                        if (activeNativeGate != null) activeNativeGate.observeEvent(event, projection);
                        projection.ifPresent(projected -> {
                            if (projected.completeAgentOutput()) {
                                agentOutputs.put(projected.author(), (String) projected.payload().get("text"));
                            }
                            eventSink.append(projected.type(), projected.author(), projected.payload());
                        });
                    });
            if (activeNativeGate != null) {
                agentOutputs.clear();
                agentOutputs.putAll(activeNativeGate.requireCompletedOutputs());
            }
            String sideEffectOutcome = sealAndFreeze(eventSink, toolScope, "UNKNOWN");
            if (agentOutputs.isEmpty()) {
                throw new IllegalStateException("ADK root completed without a usable agent output");
            }
            if (activeNativeGate == null && toolScope == null) {
                requireStaticParallelOutputs(root, agentOutputs.keySet());
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("agents", new TreeMap<>(agentOutputs));
            String finalAgent = finalAgent(root);
            if (finalAgent != null && agentOutputs.containsKey(finalAgent)) {
                output.put("text", agentOutputs.get(finalAgent));
            }
            if (activeNativeGate != null
                    && nativeSpec.topology() == NativeAgentCompilerStrategy.TransferTopologyEnum.BOUNDED_RESEARCH_V1) {
                List<Map<String, Object>> tasks = activeNativeGate.requireCompletedTasks().stream()
                        .map(task -> Map.<String, Object>of("taskId", task.taskId(), "agent", task.agent(), "summary", task.summary()))
                        .toList();
                output.put("tasks", tasks);
            }
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.COMPLETED,
                    output, null, snapshot.definition().outputSchemaVersion(), null, null, sideEffectOutcome);
            return finishOrResolve(actor, query, lease, current.get().revision(), result);
        } catch (RuntimeException failure) {
            String sideEffectOutcome = sealAndFreeze(eventSink, toolScope, "UNKNOWN");
            AgentExecutionAuthorization.Failure authorizationFailure = findCause(failure, AgentExecutionAuthorization.Failure.class);
            if (authorizationFailure != null) {
                return suspendForAuthorizationOrResolve(actor, lease, authorizationFailure.decision(), sideEffectOutcome);
            }
            if (failure instanceof AgentLedgerException conflict
                    && (conflict.code() == AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT
                    || conflict.code() == AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED)) {
                AgentRunDTO latest = ledger.get(actor, query);
                if (AgentRunTransitions.terminal(latest.status())) {
                    return latest;
                }
                if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()) {
                    return finishCancellation(actor, lease, latest, sideEffectOutcome);
                }
                if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                    return expireDeadlineOrResolve(actor, query, lease, latest.revision(), sideEffectOutcome);
                }
                throw failure;
            }
            if (hasCause(failure, CancellationSignal.class)) {
                return finishCancellation(actor, lease, ledger.get(actor, query), sideEffectOutcome);
            }
            if (hasCause(failure, AdkRunBudgetPlugin.ModelIdleTimeout.class)) {
                AgentExecutionResult idle = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                        Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_MODEL_IDLE_TIMEOUT.name(), sideEffectOutcome);
                return finishOrResolve(actor, query, lease, current.get().revision(), idle);
            }
            AgentRunStatusEnum status = isTimeout(failure) ? AgentRunStatusEnum.TIMED_OUT : AgentRunStatusEnum.FAILED;
            String code = isTimeout(failure) ? AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name()
                    : failure instanceof AgentLedgerException ledgerFailure ? ledgerFailure.code().name()
                    : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name();
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), status, Map.of(), null, null,
                    null, code, !nativeSelected && !invocationStarted && (toolScope == null || toolScope.declaredTools().isEmpty()) ? "NONE" : sideEffectOutcome);
            if (status == AgentRunStatusEnum.TIMED_OUT) {
                AgentRunDTO latest = ledger.get(actor, query);
                if (AgentRunTransitions.terminal(latest.status())) return latest;
                // A native callback can time out while the database run deadline is still in the future.
                if (nativeSelected && latest.deadlineAt().isAfter(ledger.databaseNow())) {
                    return finishOrResolve(actor, query, lease, latest.revision(), result);
                }
                return expireDeadlineOrResolve(actor, query, lease, latest.revision(), sideEffectOutcome);
            }
            return finishOrResolve(actor, query, lease, current.get().revision(), result);
        } finally {
            eventSink.seal();
            if (nativeGate != null) nativeGate.close();
            if (toolScope != null) toolScope.close();
            if (runner != null) {
                try {
                    runner.close().timeout(5, TimeUnit.SECONDS).blockingAwait();
                } catch (RuntimeException closeFailure) {
                    log.warn("Agent runner stage=SHUTDOWN outcome=ERROR errorType={}",
                            closeFailure.getClass().getSimpleName());
                }
            }
            releaseSession(sessions, sessionLease);
        }
    }

    private Flowable<Long> observeResumeControl(TrustedAgentActor actor, GetAgentRunQuery query, Execution resume)
            throws TimeoutException {
        var latest = ledger.get(actor, query);
        Instant observedAt = ledger.databaseNow();
        if (!AgentRunTransitions.terminal(latest.status()) && !latest.deadlineAt().isAfter(observedAt)) {
            throw new TimeoutException("Agent deadline elapsed");
        }
        if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()) {
            throw new CancellationSignal();
        }
        requireResumeFence(resume);
        return Flowable.empty();
    }

    private void requireResumeFence(Execution resume) {
        Instant observedAt = ledger.databaseNow();
        if (!resume.runLease().expiresAt().isAfter(observedAt) || !resume.sessionLease().expiresAt().isAfter(observedAt)) {
            throw resumeLeaseExpired();
        }
        resume.boundSession().verifySessionLease(resume.sessionLease());
    }

    private static AgentLedgerException resumeLeaseExpired() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent resume lease is no longer valid");
    }

    private static AgentLedgerException resumeUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Agent checkpoint resume is unavailable");
    }

    private static void releaseSession(PostgresAdkSessionService sessions, SessionLease lease) {
        if (sessions == null || lease == null) return;
        try {
            sessions.releaseSessionLease(lease);
        } catch (RuntimeException releaseFailure) {
            log.warn("Agent session stage=RELEASE outcome=ERROR errorType={}", releaseFailure.getClass().getSimpleName());
        }
    }

    private String sealAndFreeze(RunEventSink sink, AdkGovernedToolBridge.Scope scope, String legacyEffect) {
        sink.seal();
        if (scope == null) return legacyEffect;
        scope.close();
        return scope.declaredTools().isEmpty() ? legacyEffect : scope.sideEffectOutcome();
    }

    private AgentRunDTO expireDeadlineOrResolve(TrustedAgentActor actor, GetAgentRunQuery query, AgentRunLease lease,
                                               long revision, String sideEffectOutcome) {
        try {
            return ledger.expireDeadline(actor, lease, query, revision, sideEffectOutcome);
        } catch (AgentLedgerException race) {
            if (race.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) throw race;
            AgentRunDTO latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) return ledger.expireDeadline(actor, query, latest.revision());
            throw race;
        }
    }

    private AgentRunDTO suspendForAuthorizationOrResolve(TrustedAgentActor actor, AgentRunLease lease,
                                                          AgentRunAuthorization.Decision decision, String sideEffectOutcome) {
        GetAgentRunQuery query = new GetAgentRunQuery(lease.runId());
        try {
            return ledger.suspendForAuthorization(actor, lease, decision);
        } catch (AgentLedgerException race) {
            if (race.code() != AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED) throw race;
            AgentRunDTO latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()) {
                return finishCancellation(actor, lease, latest, sideEffectOutcome);
            }
            if (!latest.deadlineAt().isAfter(ledger.databaseNow())) {
                return expireDeadlineOrResolve(actor, query, lease, latest.revision(), sideEffectOutcome);
            }
            throw race;
        }
    }

    private AgentRunDTO finishCancellation(TrustedAgentActor actor, AgentRunLease lease, AgentRunDTO current, String sideEffectOutcome) {
        if (AgentRunTransitions.terminal(current.status())) {
            return current;
        }
        AgentExecutionResult result = new AgentExecutionResult(lease.runId(), AgentRunStatusEnum.CANCELLED,
                Map.of(), null, null, null, null, sideEffectOutcome);
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
                return expireDeadlineOrResolve(actor, query, lease, latest.revision(), result.sideEffectOutcome());
            }
            if (latest.status() == AgentRunStatusEnum.RUNNING && latest.cancelRequested()
                    && result.status() != AgentRunStatusEnum.CANCELLED) {
                return finishCancellation(actor, lease, latest, result.sideEffectOutcome());
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
        if ((!dependencies.prompts().isEmpty() || !dependencies.skills().isEmpty()) && !contextAssembler.hasProviders()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "versioned prompt and skill resolution is not available");
        }
        if ((!dependencies.tools().isEmpty() || !snapshot.effectivePolicy().allowedToolVersions().isEmpty())
                && (!"CONFIG".equals(snapshot.definition().executionMode()) || governedToolSupplier == null)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "governed tool execution is not available");
        }
    }

    private NativeAgentCompilerStrategy.TemplateSpec requireNativeDefinition(AgentExecutionSnapshot snapshot,
                                                                             NativeAgentCompilerStrategy compiler) {
        var definition = snapshot.definition();
        var spec = Objects.requireNonNull(compiler.templateSpec(), "Native template metadata is unavailable");
        ChatModel model = chatModels.get(spec.modelBeanName());
        if (!"CODE_FIRST".equals(definition.executionMode()) || !spec.codeRef().equals(definition.codeRef())
                || !definition.configuration().isEmpty() || !definition.dependencies().tools().isEmpty()
                || !snapshot.effectivePolicy().allowedToolVersions().isEmpty() || snapshot.effectivePolicy().maxToolCalls() != 0
                || !spec.inputSchemaVersion().equals(definition.inputSchemaVersion())
                || !spec.outputSchemaVersion().equals(definition.outputSchemaVersion())
                || !spec.modelRouteRef().equals(definition.dependencies().modelRouteRef())
                || model == null || !compiler.usesChatModel(model) || model.getOptions() == null
                || !spec.modelName().equals(model.getOptions().getModel())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Native template binding is unavailable");
        }
        if (model.getOptions() instanceof ToolCallingChatOptions options && options.getToolCallbacks() != null
                && !options.getToolCallbacks().isEmpty()) throw unsupportedAgentCallbacks();
        return spec;
    }

    private FirstInputCheckpointBO firstInputCheckpoint(AgentLedgerService.ExecutionMaterial material,
                                                       AgentRunDTO running, BaseAgent root, Content userInput) {
        if (checkpointEngineVersion == null || !checkpointRequestEligible(material, running) || !checkpointRootEligible(root)) {
            return null;
        }
        return new FirstInputCheckpointBO(material.authorization(), running, userInput, checkpointEngineVersion);
    }

    private boolean checkpointRequestEligible(AgentLedgerService.ExecutionMaterial material, AgentRunDTO running) {
        var request = material.authorization();
        var snapshot = material.snapshot();
        var definition = snapshot.definition();
        var dependencies = definition.dependencies();
        var policy = snapshot.effectivePolicy();
        return running.attempt() == 1 && !contextAssembler.hasProviders()
                && !contextOptions.snapshots() && !contextOptions.trimming()
                && request.actor().subjectType() == TrustedAgentActor.SubjectType.USER
                && !"LEGACY".equals(request.actor().issuerFingerprint()) && request.sessionRequired()
                && "CONFIG".equals(definition.executionMode())
                && dependencies.prompts().isEmpty() && dependencies.skills().isEmpty() && dependencies.tools().isEmpty()
                && policy.maxToolCalls() == 0 && policy.allowedToolVersions().isEmpty()
                && snapshot.processLink() == null && snapshot.input().artifactRefs().isEmpty()
                && snapshot.input().taskData().isEmpty() && snapshot.input().text() != null
                && !snapshot.input().text().isBlank();
    }

    private static boolean checkpointRootEligible(BaseAgent root) {
        return root instanceof LlmAgent agent && agent.subAgents().isEmpty() && agent.outputKey().isEmpty();
    }

    private static String checkpointEngineVersion() {
        try {
            var resources = Runner.class.getClassLoader().getResources("META-INF/maven/com.google.adk/google-adk/pom.properties");
            if (!resources.hasMoreElements()) throw checkpointEngineUnavailable();
            var resource = resources.nextElement();
            if (resources.hasMoreElements()) throw checkpointEngineUnavailable();
            var properties = new Properties();
            try (var stream = resource.openStream()) {
                byte[] bytes = stream.readNBytes(4097);
                if (bytes.length > 4096) throw checkpointEngineUnavailable();
                properties.load(new ByteArrayInputStream(bytes));
            }
            String version = properties.getProperty("version");
            if (!"com.google.adk".equals(properties.getProperty("groupId"))
                    || !"google-adk".equals(properties.getProperty("artifactId")) || !"1.13.0".equals(version)) {
                throw checkpointEngineUnavailable();
            }
            return version;
        } catch (IOException | IllegalArgumentException failure) {
            throw checkpointEngineUnavailable();
        }
    }

    private static IllegalStateException checkpointEngineUnavailable() {
        return new IllegalStateException("TIANJI_AGENT_CHECKPOINT_ENGINE_UNAVAILABLE");
    }

    private BaseAgent buildRoot(AgentDefinitionSpecDTO definition, List<BaseTool> tools) {
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
            return flowFactory.compileRoot("tianji", config, chatModels, tools);
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

    private static void requireGovernedTools(BaseAgent agent, List<BaseTool> expectedTools) {
        if (agent instanceof LlmAgent llm) {
            if (!llm.toolsets().isEmpty() || llm.codeExecutor().isPresent()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "Agent tools require the fixed governed declarations");
            }
            List<BaseTool> actualTools = llm.tools().blockingGet();
            if (actualTools.size() != expectedTools.size()) throw unsupportedAgentCallbacks();
            for (int index = 0; index < actualTools.size(); index++) {
                if (actualTools.get(index) != expectedTools.get(index)) throw unsupportedAgentCallbacks();
            }
            // Agent callbacks can change already-metered requests or governed tool results.
            if (!llm.canonicalBeforeModelCallbacks().isEmpty() || !llm.canonicalAfterModelCallbacks().isEmpty()
                    || !llm.canonicalOnModelErrorCallbacks().isEmpty() || !llm.canonicalBeforeToolCallbacks().isEmpty()
                    || !llm.canonicalAfterToolCallbacks().isEmpty() || !llm.canonicalOnToolErrorCallbacks().isEmpty()) throw unsupportedAgentCallbacks();
        } else if (!(agent instanceof SequentialAgent || agent instanceof ParallelAgent || agent instanceof LoopAgent)) {
            throw new IllegalArgumentException("this Agent type requires additional execution governance");
        }
        agent.subAgents().forEach(child -> requireGovernedTools(child, expectedTools));
    }

    private static AgentLedgerException unsupportedAgentCallbacks() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Agent callbacks and tools require final-request governance");
    }

    private final class RunEventSink {
        private final TrustedAgentActor actor;
        private final AgentRunLease lease;
        private final AtomicReference<AgentRunDTO> current;
        private boolean sealed;

        private RunEventSink(TrustedAgentActor actor, AgentRunLease lease, AtomicReference<AgentRunDTO> current) {
            this.actor = actor; this.lease = lease; this.current = current;
        }
        private synchronized void append(String type, String author, Map<String, Object> payload) {
            if (sealed) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent run event stream is closed");
            current.set(ledger.appendEvent(actor, lease, current.get().revision(), type, author, payload));
        }
        private synchronized void seal() { sealed = true; }
        private void tool(AdkGovernedToolBridge.ToolEventVO event) {
            Map<String, Object> payload = new LinkedHashMap<>();
            var tool = event.toolRef();
            payload.put("toolRef", Map.of("key", tool.key(), "version", tool.version(), "contentSha256", tool.contentSha256()));
            payload.put("hostInvocationId", event.hostInvocationId().toString()); payload.put("state", event.state());
            if (!"UNAVAILABLE".equals(event.state()) && event.dispatchEpoch() != null) payload.put("dispatchEpoch", event.dispatchEpoch());
            if (event.accountingState() != null) payload.put("accountingState", event.accountingState().name());
            if (event.errorCode() != null) payload.put("errorCode", event.errorCode());
            append(event.phase() == AdkGovernedToolBridge.Phase.STARTED ? "TOOL_STARTED" : "TOOL_FINISHED", event.author(), Map.copyOf(payload));
        }
    }

    private static void requireStaticParallelOutputs(BaseAgent root, Set<String> outputs) {
        Set<String> required = new HashSet<>();
        if (collectStaticParallelOutputs(root, false, required) && !outputs.containsAll(required)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    "ADK root completed without every required parallel branch output");
        }
    }

    private static boolean collectStaticParallelOutputs(BaseAgent agent, boolean terminalRequired, Set<String> required) {
        if (!agent.beforeAgentCallback().isEmpty() || !agent.afterAgentCallback().isEmpty()) {
            return false;
        }
        var children = agent.subAgents();
        if (agent.getClass() == LlmAgent.class && children.isEmpty()) {
            if (terminalRequired) required.add(agent.name());
            return true;
        }
        boolean parallel = agent.getClass() == ParallelAgent.class;
        if ((!parallel && agent.getClass() != SequentialAgent.class) || children.isEmpty()) {
            return false;
        }
        for (int index = 0; index < children.size(); index++) {
            // All parallel branches are required. A sequential branch contributes its last child's output.
            boolean childTerminalRequired = parallel || (terminalRequired && index == children.size() - 1);
            if (!collectStaticParallelOutputs(children.get(index), childTerminalRequired, required)) return false;
        }
        return true;
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

    private static void validateAgentTopology(BaseAgent agent, Set<String> names, Set<String> outputKeys) {
        if (!names.add(agent.name())) {
            throw new IllegalArgumentException("Agent names must be unique within a run");
        }
        if (agent instanceof LlmAgent llm) {
            String key = llm.outputKey().orElse(null);
            if (key != null && !outputKeys.add(key)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "Agent output keys must be unique within a run");
            }
        }
        agent.subAgents().forEach(child -> validateAgentTopology(child, names, outputKeys));
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
