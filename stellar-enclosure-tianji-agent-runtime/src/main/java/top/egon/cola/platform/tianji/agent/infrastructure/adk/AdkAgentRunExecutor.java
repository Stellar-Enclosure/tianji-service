package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.LoopAgent;
import com.google.adk.agents.ParallelAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import top.egon.cola.component.agentflow.autoconfigure.AgentFlowProperties;
import top.egon.cola.component.agentflow.config.AgentFlowConfigDTO;
import top.egon.cola.component.agentflow.config.AgentFlowConfigValidator;
import top.egon.cola.component.agentflow.runtime.AgentFlowFactory;
import top.egon.cola.component.agentflow.runtime.AgentFlowInMemoryRunner;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Executes one explicitly selected queued run; it does not schedule or expose an endpoint. */
public final class AdkAgentRunExecutor {
    private static final Logger log = LoggerFactory.getLogger(AdkAgentRunExecutor.class);
    private final AgentLedgerService ledger;
    private final AgentFlowFactory flowFactory;
    private final AgentFlowConfigValidator flowValidator;
    private final Map<String, ChatModel> chatModels;
    private final Map<String, Supplier<? extends BaseAgent>> codeFirstFactories;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdkEventProjector projector = new AdkEventProjector();

    public AdkAgentRunExecutor(AgentLedgerService ledger, AgentFlowFactory flowFactory,
                               AgentFlowConfigValidator flowValidator, Map<String, ChatModel> chatModels,
                               Map<String, Supplier<? extends BaseAgent>> codeFirstFactories, Clock clock) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.flowFactory = Objects.requireNonNull(flowFactory, "flowFactory");
        this.flowValidator = Objects.requireNonNull(flowValidator, "flowValidator");
        this.chatModels = Map.copyOf(chatModels);
        this.codeFirstFactories = Map.copyOf(codeFirstFactories);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Uses one CAS to claim the run and one subscription to the ADK root invocation. */
    public AgentRunDTO executeQueued(TrustedAgentActor actor, GetAgentRunQuery query) {
        AgentRunDTO queued = ledger.get(actor, query);
        if (queued.status() != AgentRunStatusEnum.QUEUED) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run is not queued");
        }
        if (!queued.deadlineAt().isAfter(clock.instant())) {
            AgentExecutionResult expired = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                    Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(), "NONE");
            return ledger.transition(actor, query, queued.revision(), AgentRunStatusEnum.QUEUED,
                    AgentRunStatusEnum.TIMED_OUT, null, expired);
        }
        AgentRunDTO running = ledger.transition(actor, query, queued.revision(), AgentRunStatusEnum.QUEUED,
                AgentRunStatusEnum.RUNNING, null, null);
        AtomicReference<AgentRunDTO> current = new AtomicReference<>(running);
        AgentFlowInMemoryRunner runner = null;
        try {
            AgentExecutionSnapshot snapshot = ledger.snapshot(actor, query);
            requireSupportedInput(snapshot, running);
            BaseAgent root = buildRoot(snapshot.definition());
            requireNoTools(root);
            runner = new AgentFlowInMemoryRunner(root, "tianji_" + query.runId().replace('-', '_'));
            String userId = actor.tenantId() + ":" + actor.subjectType().name() + ":" + actor.subjectId();
            Session session = runner.sessionService().createSession(runner.appName(), userId).blockingGet();
            if (session == null) {
                throw new IllegalStateException("ADK session could not be created");
            }
            Duration remaining = Duration.between(clock.instant(), running.deadlineAt());
            if (remaining.isZero() || remaining.isNegative()) {
                throw new IllegalStateException("Agent deadline has elapsed", new TimeoutException());
            }
            RunConfig config = RunConfig.builder().setStreamingMode(RunConfig.StreamingMode.SSE)
                    .setMaxLlmCalls(snapshot.effectivePolicy().maxModelCalls()).build();
            Flowable<Long> deadline = Flowable.timer(Math.max(1, remaining.toMillis()), TimeUnit.MILLISECONDS)
                    .flatMap(ignored -> Flowable.error(new TimeoutException("Agent deadline elapsed")));
            Map<String, String> agentOutputs = new LinkedHashMap<>();
            Set<String> agentNames = new HashSet<>();
            collectAgentNames(root, agentNames);
            runner.runAsync(userId, session.id(), Content.fromParts(Part.fromText(snapshot.input().text())), config)
                    .takeUntil(deadline)
                    .blockingForEach(event -> projector.project(event, agentNames).ifPresent(projected -> {
                        if (projected.completeAgentOutput()) {
                            agentOutputs.put(projected.author(), (String) projected.payload().get("text"));
                        }
                        current.set(ledger.appendEvent(actor, query, current.get().revision(),
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
            return ledger.transition(actor, query, current.get().revision(), AgentRunStatusEnum.RUNNING,
                    AgentRunStatusEnum.COMPLETED, null, result);
        } catch (RuntimeException failure) {
            if (failure instanceof AgentLedgerException conflict
                    && conflict.code() == AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) {
                AgentRunDTO latest = ledger.get(actor, query);
                if (AgentRunTransitions.terminal(latest.status())) {
                    return latest;
                }
                throw failure;
            }
            AgentRunStatusEnum status = isTimeout(failure) ? AgentRunStatusEnum.TIMED_OUT : AgentRunStatusEnum.FAILED;
            String code = isTimeout(failure) ? AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name()
                    : failure instanceof AgentLedgerException ledgerFailure ? ledgerFailure.code().name()
                    : AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name();
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), status, Map.of(), null, null,
                    null, code, "UNKNOWN");
            return ledger.transition(actor, query, current.get().revision(), AgentRunStatusEnum.RUNNING,
                    status, null, result);
        } finally {
            if (runner != null) {
                try {
                    runner.close().timeout(5, TimeUnit.SECONDS).blockingAwait();
                } catch (RuntimeException closeFailure) {
                    log.warn("Agent runner stage=SHUTDOWN outcome=ERROR errorType={}",
                            closeFailure.getClass().getSimpleName());
                }
            }
        }
    }

    private void requireSupportedInput(AgentExecutionSnapshot snapshot, AgentRunDTO run) {
        if (run.sessionId() != null || snapshot.input().text() == null || snapshot.input().text().isBlank()
                || !snapshot.input().artifactRefs().isEmpty() || !snapshot.input().taskData().isEmpty()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                    "this runtime currently supports only one-shot text input");
        }
        if (!snapshot.effectivePolicy().allowedToolVersions().isEmpty()) {
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
            Supplier<? extends BaseAgent> factory = codeFirstFactories.get(definition.codeRef());
            if (factory == null) {
                throw new IllegalArgumentException("trusted Agent factory is unavailable");
            }
            return Objects.requireNonNull(factory.get(), "trusted Agent factory returned null");
        }
        throw new IllegalArgumentException("unsupported Agent definition mode");
    }

    private static void requireNoTools(BaseAgent agent) {
        if (agent instanceof LlmAgent llm) {
            if (!llm.tools().blockingGet().isEmpty() || !llm.toolsets().isEmpty()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                        "governed tool execution is not available");
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
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 64; depth++, current = current.getCause()) {
            if (current instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
