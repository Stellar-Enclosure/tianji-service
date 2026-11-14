package top.egon.cola.platform.tianji.agent.application.run;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.agentflow.adapter.adk.AdkAgentExecutorAdapter.TemplateResolver;
import top.egon.cola.component.agentflow.agentic.AgenticPlanVO;
import top.egon.cola.component.agentflow.api.capability.CallPermitVO;
import top.egon.cola.component.agentflow.api.context.ContextFragmentResult;
import top.egon.cola.component.agentflow.api.context.ContextScopeVO;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionCommand;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ModelCall;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ModelCallRequestVO;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionHandle;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionResult;
import top.egon.cola.component.agentflow.api.execution.AgentExecutor;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextBO;
import top.egon.cola.platform.tianji.agent.application.context.TianjiHostContextConverter;
import top.egon.cola.platform.tianji.agent.application.tool.AgentRuntimeToolExecutionService;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization.Decision;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.OutcomeVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.State;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RuntimeExecutionMaterialVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateConfigurationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

/** Executes one verified, fenced RUNTIME_V2 attempt through the neutral executor. */
@Slf4j
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentRuntimeExecutionService {
    public static final String ADK_EXECUTOR_REF = "adk-default";
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    @Qualifier("tianjiAgentLedgerService")
    private final AgentLedgerService ledger;
    private final AgentRunAuthorization authorization;
    @Qualifier("agentExecutor")
    private final AgentExecutor executor;
    @Qualifier("tianjiHostContextConverter")
    private final TianjiHostContextConverter contextMapper;
    @Qualifier("tianjiAgentControlExecutor")
    private final ScheduledThreadPoolExecutor control;
    @Qualifier("agentRuntimeCallReceiptStore")
    private final AgentRuntimeCallReceiptStore receipts;
    @Qualifier("tianjiRuntimeReceiptTransaction")
    private final TransactionTemplate receiptTransaction;
    @Qualifier("tianjiRuntimeToolExecutionService")
    private final AgentRuntimeToolExecutionService tools;
    private final String workerId = UUID.randomUUID().toString();

    public AgentRuntimeExecutionService(AgentLedgerService ledger, AgentRunAuthorization authorization, AgentExecutor executor,
                                        TianjiHostContextConverter contextMapper, ScheduledThreadPoolExecutor control,
                                        AgentRuntimeCallReceiptStore receipts, TransactionTemplate receiptTransaction) {
        this(ledger, authorization, executor, contextMapper, control, receipts, receiptTransaction, null);
    }

    boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    boolean usesAuthorization(AgentRunAuthorization candidate) { return authorization == candidate; }

    /** Checks the same registry result handed to the adapter against the fixed command. */
    public static TemplateResolver verifiedTemplates(TemplateResolver registry, AgenticTemplateConverter converter) {
        return (expected, scope) -> {
            if (!ADK_EXECUTOR_REF.equals(expected.executorRef())) return null;
            var configuration = TemplateConfigurationVO.fromExactWire(expected.configuration());
            var registered = registry.resolve(expected, scope);
            if (registered == null || !expected.key().equals(registered.key()) || expected.version() != registered.version()
                    || !expected.contentSha256().equals(registered.contentSha256())) return null;
            boolean matches = configuration.agenticPlan() == null
                    ? matchesSingleExecutor(registered.plan(), configuration)
                    : converter.toTarget(configuration.agenticPlan()).equals(registered.plan());
            return matches ? registered : null;
        };
    }

    private static boolean matchesSingleExecutor(AgenticPlanVO registered, TemplateConfigurationVO configuration) {
        if (registered.strategy() != AgenticPlanVO.StrategyEnum.REASONING || registered.agents().size() != 1
                || registered.merge() != AgenticPlanVO.MergeEnum.OUTPUT_AGENT || !registered.sharedFields().isEmpty()
                || !(registered.termination() instanceof AgenticPlanVO.RootTerminalVO)) return false;
        var root = registered.agents().getFirst();
        return root.modelRef().equals(configuration.modelRef())
                && referenceMatches(root.instructionRef(), configuration.instructionRef())
                && root.toolRefs().size() == configuration.toolRefs().size()
                && IntStream.range(0, root.toolRefs().size()).allMatch(index ->
                        referenceMatches(root.toolRefs().get(index), configuration.toolRefs().get(index)));
    }

    private static boolean referenceMatches(AgenticPlanVO.DependencyRefVO registered, TemplateDependencyRefVO pinned) {
        return registered.key().equals(pinned.key()) && registered.version() == pinned.version()
                && registered.contentSha256().equals(pinned.contentSha256());
    }

    public AgentRunDTO executeQueued(@NotNull @Valid TrustedAgentActor actor, @NotNull @Valid GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent Runtime execution requires a transaction-free caller");
        }
        var material = ledger.runtimeExecutionMaterial(actor, query);
        var queued = material.run();
        if (AgentRunTransitions.terminal(queued.status())) return queued;
        if (queued.status() != AgentRunStatusEnum.QUEUED) throw revisionConflict();
        if (!queued.deadlineAt().isAfter(ledger.databaseNow())) {
            return ledger.expireDeadline(actor, query, queued.revision());
        }
        Decision beforeClaim = authorize(material, AgentRunAuthorization.Boundary.CLAIM);
        if (beforeClaim == Decision.DENY) return ledger.suspendQueuedForAuthorization(actor, query, queued.revision());
        if (beforeClaim != Decision.ALLOW) throw unavailable();

        var claim = ledger.claimRuntimeV2(actor, query, queued.revision(), workerId, LEASE_DURATION);
        var claimed = ledger.runtimeExecutionMaterial(actor, query);
        if (AgentRunTransitions.terminal(claimed.run().status())) return claimed.run();
        if (!material.authorization().equals(claimed.authorization()) || !claim.run().equals(claimed.run())) {
            throw revisionConflict();
        }
        Decision afterClaim = authorize(claimed, AgentRunAuthorization.Boundary.MODEL);
        if (afterClaim != Decision.ALLOW) return ledger.suspendForAuthorization(actor, claim.lease(), afterClaim);
        return executeClaim(actor, query, claimed, claim);
    }

    private AgentRunDTO executeClaim(TrustedAgentActor actor, GetAgentRunQuery query,
                                     RuntimeExecutionMaterialVO material, ClaimedAgentRun claim) {
        var cancellation = new CompletableFuture<Void>();
        var stop = new CompletableFuture<LeaseHeartbeat>();
        var scope = new ContextScopeVO("tianji-agent-runtime", Long.toString(actor.tenantId()), actor.identityFingerprint(),
                query.runId(), query.runId(), "EXECUTE");
        var deadline = claim.run().deadlineAt();
        var context = new TianjiHostContextBO(scope, deadline, cancellation,
                (source, requestedScope, permit) -> new ContextFragmentResult(List.of(), "SOURCE_UNAVAILABLE"), null,
                (requestedScope, capability, purpose) -> {
                    boolean exact = switch (purpose) {
                        case "EXECUTE" -> material.template().getExecutorRef().equals(capability);
                        case "MODEL_CALL" -> permitsModel(material, capability);
                        case "TOOL_CALL" -> tools != null && toolRefs(material).anyMatch(ref -> ref.key().equals(capability));
                        default -> false;
                    };
                    if (!scope.equals(requestedScope) || !exact || cancellation.isDone()) throw unavailable();
                    return new CallPermitVO(UUID.randomUUID().toString(), capability, scope.scopeHash(),
                            claim.run().revision(), null, deadline, cancellation::isDone,
                            (permit, currentPurpose) -> purpose.equals(currentPurpose));
                }, request -> beginModelCall(actor, material, claim, scope, cancellation, request));
        if (tools != null) {
            context.setToolCallHandler((request, boundary) -> {
                if (!scope.equals(request.scope()) || !claim.run().runId().equals(request.executionId())
                        || toolRefs(material).noneMatch(ref -> ref.key().equals(request.toolRef().key())
                                && ref.version() == request.toolRef().version()
                                && ref.contentSha256().equals(request.toolRef().contentSha256()))) throw unavailable();
                Runnable current = () -> {
                    if (cancellation.isDone()) throw leaseLost();
                };
                current.run();
                return tools.execute(material, ledger.renewRuntimeLease(actor, claim.lease(), LEASE_DURATION), request, boundary, current);
            });
        }
        var timer = control.scheduleWithFixedDelay(() -> checkLease(actor, claim, stop, cancellation),
                1, 1, TimeUnit.SECONDS);
        AgentExecutionResult result;
        try {
            Duration remaining = Duration.between(ledger.databaseNow(), deadline);
            if (!remaining.isPositive()) {
                result = stoppedResult(query, LeaseHeartbeat.DEADLINE_EXPIRED);
            } else {
                AgentExecutionHandle handle = executor.execute(executionCommand(material), contextMapper.toTarget(context));
                var completion = handle.completion().toCompletableFuture();
                var observed = CompletableFuture.anyOf(completion, stop).get(remaining.toNanos(), TimeUnit.NANOSECONDS);
                result = switch (observed) {
                    case AgentExecutionResult completed -> completed;
                    case LeaseHeartbeat stopped -> stoppedResult(query, stopped);
                    default -> throw unavailable();
                };
            }
        } catch (TimeoutException timedOut) {
            result = stoppedResult(query, LeaseHeartbeat.DEADLINE_EXPIRED);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw leaseLost();
        } catch (ExecutionException failed) {
            result = executionFailed(query, failed.getCause());
        } catch (RuntimeException failure) {
            var reason = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            result = executionFailed(query, reason);
        } finally {
            timer.cancel(false);
            cancellation.complete(null);
        }

        var current = ledger.get(actor, query);
        if (AgentRunTransitions.terminal(current.status())) return current;
        if (stop.getNow(LeaseHeartbeat.ACTIVE) == LeaseHeartbeat.LEASE_LOST) throw leaseLost();
        if (!query.runId().equals(result.executionId())) throw unavailable();
        try {
            return finish(actor, query, material, claim, current, result);
        } catch (AgentLedgerException failure) {
            if (failure.code() != AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED
                    && failure.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) throw failure;
            var latest = ledger.get(actor, query);
            if (AgentRunTransitions.terminal(latest.status())) return latest;
            if (latest.revision() == current.revision()) throw failure;
            return finish(actor, query, material, claim, latest, result);
        }
    }

    private ModelCall beginModelCall(TrustedAgentActor actor, RuntimeExecutionMaterialVO material, ClaimedAgentRun claim,
                                    ContextScopeVO scope, CompletableFuture<Void> cancellation, ModelCallRequestVO request) {
        if (!scope.equals(request.scope()) || !claim.run().runId().equals(request.executionId())
                || !permitsModel(material, request.modelRef())) throw unavailable();
        requireModelAuthorization(material);
        var renewed = ledger.renewRuntimeLease(actor, claim.lease(), LEASE_DURATION);
        Instant deadline = request.deadline().isBefore(renewed.expiresAt()) ? request.deadline() : renewed.expiresAt();
        var call = new RuntimeCallReceiptBO().setTenantId(actor.tenantId()).setRunId(renewed.runId())
                .setCallId(UUID.randomUUID()).setKind(RuntimeCallReceiptBO.Kind.MODEL).setOwner(actor)
                .setLeaseEpoch(renewed.epoch()).setInputSha256(request.inputSha256())
                .setContextMetadata(Map.of("modelRef", request.modelRef(), "sources", List.of())).setDeadlineAt(deadline);
        Runnable verifyCurrent = () -> {
            if (cancellation.isDone()) throw leaseLost();
        };
        long runRevision = claim.run().revision();
        var dispatched = Objects.requireNonNull(receiptTransaction.execute(status -> {
            var prepared = receipts.prepare(actor, renewed, runRevision, call, verifyCurrent);
            return receipts.markDispatched(actor, renewed, runRevision, prepared.getCallId(), prepared.getRevision(), null, verifyCurrent);
        }));
        return outcome -> {
            requireModelAuthorization(material);
            var finalOutcome = new OutcomeVO(outcome.errorCode() == null ? State.SUCCEEDED : State.FAILED, outcome.resultSha256(),
                    tokenCount(outcome.inputTokens()), tokenCount(outcome.outputTokens()), outcome.errorCode());
            receiptTransaction.executeWithoutResult(status -> receipts.finish(actor, renewed, runRevision, dispatched.getCallId(),
                    dispatched.getRevision(), null, finalOutcome, verifyCurrent));
        };
    }

    private void requireModelAuthorization(RuntimeExecutionMaterialVO material) {
        Decision decision = authorize(material, AgentRunAuthorization.Boundary.MODEL);
        if (decision == Decision.DENY) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Agent Runtime model authorization was denied");
        }
        if (decision != Decision.ALLOW) throw unavailable();
    }

    private static boolean permitsModel(RuntimeExecutionMaterialVO material, String ref) {
        var config = material.template().getConfiguration();
        return config.agenticPlan() == null ? config.modelRef().equals(ref)
                : config.agenticPlan().agents().stream().anyMatch(leaf -> leaf.modelRef().equals(ref));
    }

    private static java.util.stream.Stream<TemplateDependencyRefVO> toolRefs(RuntimeExecutionMaterialVO material) {
        var config = material.template().getConfiguration();
        return config.agenticPlan() == null ? config.toolRefs().stream()
                : config.agenticPlan().agents().stream().flatMap(leaf -> leaf.toolRefs().stream());
    }

    private static Long tokenCount(Integer value) { return value == null ? null : value.longValue(); }

    private AgentRunDTO finish(TrustedAgentActor actor, GetAgentRunQuery query, RuntimeExecutionMaterialVO material,
                               ClaimedAgentRun claim, AgentRunDTO current, AgentExecutionResult result) {
        if (!current.deadlineAt().isAfter(ledger.databaseNow())) {
            return ledger.expireDeadline(actor, claim.lease(), query, current.revision(), result.sideEffectOutcome().name());
        }
        if (!current.cancelRequested()) {
            Decision end = authorize(material, AgentRunAuthorization.Boundary.MODEL);
            if (end != Decision.ALLOW) return ledger.suspendForAuthorization(actor, claim.lease(), end);
        }
        return ledger.finish(actor, claim.lease(), current.revision(), terminalResult(current, result));
    }

    private void checkLease(TrustedAgentActor actor, ClaimedAgentRun claim,
                            CompletableFuture<LeaseHeartbeat> stop, CompletableFuture<Void> cancellation) {
        if (cancellation.isDone()) return;
        LeaseHeartbeat heartbeat;
        try {
            heartbeat = Objects.requireNonNull(ledger.heartbeat(actor, claim.lease(), LEASE_DURATION));
        } catch (RuntimeException failure) {
            log.warn("Agent Runtime lease observation failed with type {}", failure.getClass().getSimpleName());
            heartbeat = LeaseHeartbeat.LEASE_LOST;
        }
        if (heartbeat != LeaseHeartbeat.ACTIVE) {
            stop.complete(heartbeat);
            cancellation.complete(null);
        }
    }

    private AgentExecutionResult stoppedResult(GetAgentRunQuery query, LeaseHeartbeat reason) {
        return switch (reason) {
            case CANCEL_REQUESTED -> new AgentExecutionResult(query.runId(), AgentExecutionResult.StatusEnum.CANCELLED,
                    null, "CANCELLED", AgentExecutionResult.SideEffectOutcomeEnum.UNKNOWN, ledger.databaseNow());
            case DEADLINE_EXPIRED -> new AgentExecutionResult(query.runId(), AgentExecutionResult.StatusEnum.TIMED_OUT,
                    null, "DEADLINE_EXCEEDED", AgentExecutionResult.SideEffectOutcomeEnum.UNKNOWN, ledger.databaseNow());
            case LEASE_LOST -> throw leaseLost();
            case ACTIVE -> throw unavailable();
        };
    }

    private AgentExecutionResult executionFailed(GetAgentRunQuery query, Throwable reason) {
        log.debug("Agent Runtime execution failed with type {}", reason.getClass().getSimpleName());
        return new AgentExecutionResult(query.runId(), AgentExecutionResult.StatusEnum.FAILED, null,
                AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name(), AgentExecutionResult.SideEffectOutcomeEnum.NONE,
                ledger.databaseNow());
    }

    private AgentExecutionCommand executionCommand(RuntimeExecutionMaterialVO material) {
        var template = material.template();
        var command = material.command();
        var schemas = new AgentExecutionCommand.SchemaValidator() {
            @Override
            public void validateInput(Map<String, Object> input, Map<String, Object> parameters) {
                template.getInputSchema().validateValue(input);
                template.getParameterSchema().validateValue(parameters);
            }

            @Override
            public void validateOutput(Map<String, Object> output) {
                template.getOutputSchema().validateValue(output);
            }
        };
        var neutralTemplate = new AgentExecutionCommand.TemplateVO(template.getTemplateKey(), template.getTemplateVersion(),
                command.getTemplateSha256(), template.getExecutorRef(), template.getConfiguration().toWire(), schemas);
        var limits = command.getLimits();
        var neutralLimits = new AgentExecutionCommand.TechnicalLimitsVO(limits.totalTimeout(), limits.totalTimeout(),
                limits.modelIdleTimeout(), limits.toolTimeout(), limits.maxModelCalls(), limits.maxToolCalls(), limits.maxParallelism(),
                limits.contextWindowTokens(), limits.maxOutputTokensPerCall());
        return new AgentExecutionCommand(material.run().runId(), neutralTemplate, command.getInput(), command.getParameters(), neutralLimits);
    }

    private Decision authorize(RuntimeExecutionMaterialVO material, AgentRunAuthorization.Boundary boundary) {
        try {
            return Objects.requireNonNullElse(authorization.authorize(material.authorization(), boundary), Decision.UNAVAILABLE);
        } catch (RuntimeException failure) {
            log.warn("Agent Runtime authorization failed at {} with type {}", boundary, failure.getClass().getSimpleName());
            return Decision.UNAVAILABLE;
        }
    }

    private static top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult terminalResult(
            AgentRunDTO current, AgentExecutionResult result) {
        var status = current.cancelRequested() ? AgentRunStatusEnum.CANCELLED : switch (result.status()) {
            case COMPLETED -> AgentRunStatusEnum.COMPLETED;
            case FAILED -> AgentRunStatusEnum.FAILED;
            case CANCELLED -> AgentRunStatusEnum.FAILED;
            case TIMED_OUT -> AgentRunStatusEnum.TIMED_OUT;
        };
        var error = switch (status) {
            case COMPLETED, CANCELLED -> null;
            case TIMED_OUT -> ("MODEL_IDLE_TIMEOUT".equals(result.errorCode()) ? AgentErrorCodeEnum.TIANJI_MODEL_IDLE_TIMEOUT
                    : AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT).name();
            default -> AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name();
        };
        return new top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult(current.runId(), status,
                status == AgentRunStatusEnum.COMPLETED ? result.output() : Map.of(), null, null, null, error,
                result.sideEffectOutcome().name());
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent Runtime execution is unavailable");
    }

    private static AgentLedgerException revisionConflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent Runtime run changed before execution");
    }

    private static AgentLedgerException leaseLost() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent Runtime lease is no longer current");
    }
}
