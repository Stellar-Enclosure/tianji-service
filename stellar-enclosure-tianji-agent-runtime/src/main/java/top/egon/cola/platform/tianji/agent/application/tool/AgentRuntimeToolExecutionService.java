package top.egon.cola.platform.tianji.agent.application.tool;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ToolCallBoundary;
import top.egon.cola.component.agentflow.api.execution.AgentExecutionContextVO.ToolCallRequestVO;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.OutcomeVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.State;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome.Status;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy.Risk;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService.RuntimeExecutionMaterialVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeCallReceiptStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolActionIntentStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolCapabilityStore;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.DecisionClient;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Governs a tool call from a claimed Runtime V2 node through the fixed host capability. */
@Slf4j
@Validated
@RequiredArgsConstructor
public class AgentRuntimeToolExecutionService {
    @Qualifier("tianjiAgentLedgerService")
    private final AgentLedgerService ledger;
    @Qualifier("toolCapabilityStore")
    private final ToolCapabilityStore capabilities;
    @Qualifier("toolActionIntentStore")
    private final ToolActionIntentStore actions;
    @Qualifier("governedProcessToolResultAccessStore")
    private final ToolResultAccessStore results;
    @Qualifier("tianjiToolGrantAuthorizer")
    private final ToolExecutionAuthorization authorization;
    @Qualifier("tianjiGovernedProcessDecisionTransport")
    private final ReadOnlyDecisionToolTransport decisions;
    @Qualifier("tianjiGovernedProcessMcpTransport")
    private final ReadOnlyMcpToolTransport mcp;
    @Qualifier("tianjiGovernedProcessStartTransport")
    private final FixedBpmnStartToolTransport starts;
    @Qualifier("tianjiGovernedProcessApprovalVerifier")
    private final FixedToolApprovalVerifier approvals;
    @Qualifier("tianjiRuntimeToolSelection")
    private final ToolSelection toolSelection;
    @Qualifier("tianjiRuntimeToolActionIdentity")
    private final ActionIdentity actionIdentity;
    @Qualifier("tianjiRuntimeToolApprovalReceipt")
    private final ApprovalReceipt approvalReceipt;
    @Qualifier("agentRuntimeCallReceiptStore")
    private final AgentRuntimeCallReceiptStore receipts;
    @Qualifier("tianjiRuntimeToolTransaction")
    private final TransactionTemplate transaction;

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    @FunctionalInterface
    public interface ToolSelection {
        SelectedToolVO select(AgentLedgerService.ToolRunEvidenceVO source, DefinitionResourceRef reference);
    }

    public record SelectedToolVO(ToolDescriptor descriptor, ToolExecutionGrant.BindingVO binding) {
        public SelectedToolVO {
            Objects.requireNonNull(descriptor, "descriptor");
            Objects.requireNonNull(binding, "binding");
            if (!descriptor.reference().equals(binding.tool())
                    || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256())) throw unavailable();
        }
    }

    /** Host configuration resolves the original business key/version from verified process material. */
    @FunctionalInterface
    public interface ActionIdentity {
        ActionIdentityVO resolve(RuntimeExecutionMaterialVO material, ToolDescriptor descriptor);
    }

    public record ActionIdentityVO(String businessKey, String businessVersion, Duration retention) {
        public ActionIdentityVO {
            if (businessKey == null || businessKey.isBlank() || businessVersion == null || businessVersion.isBlank()
                    || retention == null || !retention.isPositive() || retention.compareTo(Duration.ofDays(7)) > 0) throw unavailable();
        }
        @Override public String toString() { return "ActionIdentityVO[redacted]"; }
    }

    /** Returns a host-selected receipt reference. Only the fixed verifier authenticates it. */
    @FunctionalInterface
    public interface ApprovalReceipt {
        String resolve(RuntimeExecutionMaterialVO material, ToolDescriptor descriptor, ToolActionRequest action);
    }

    public Map<String, Object> execute(@NotNull @Valid RuntimeExecutionMaterialVO material, @NotNull @Valid AgentRunLease lease,
                                        @NotNull @Valid ToolCallRequestVO request, @NotNull ToolCallBoundary boundary,
                                        @NotNull Runnable requireCurrent) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        requireCurrent.run();
        var run = material.authorization();
        var ref = new DefinitionResourceRef(request.toolRef().key(), request.toolRef().version(), request.toolRef().contentSha256());
        var source = ledger.toolRunEvidence(run.actor(), new GetAgentRunQuery(run.runId()));
        if (!run.runId().equals(request.executionId()) || !run.equals(source.authorization())
                || !Objects.equals(material.command().getLink(), source.processLink()) || source.run().revision() != material.run().revision()
                || source.activeLease() == null || source.activeLease().epoch() != lease.epoch()
                || !source.activeLease().owner().equals(lease.owner()) || !source.toolRefs().contains(ref)
                || source.maxToolCalls() < 1) throw denied();
        var selected = toolSelection.select(source, ref);
        if (selected == null || !run.equals(selected.binding().run()) || !ref.equals(selected.descriptor().reference())
                || request.write() != (selected.descriptor().policy().risk() != Risk.READ_ONLY)) throw denied();
        var evidence = capabilities.current(selected.binding(), selected.descriptor()).orElseThrow(AgentRuntimeToolExecutionService::unavailable);
        if (!selected.binding().equals(evidence.reservation().binding())) throw unavailable();
        var deadline = earliest(request.deadline(), run.deadlineAt(), source.activeLease().expiresAt(), evidence.expiresAt());
        if (!deadline.isAfter(ledger.databaseNow())) throw unavailable();
        var arguments = selected.descriptor().input().values(request.arguments());
        var call = new ToolCallVO(material, lease, selected, evidence, UUID.randomUUID(), arguments, deadline, boundary, requireCurrent);
        return request.write() ? write(call) : read(call);
    }

    private Map<String, Object> read(ToolCallVO call) {
        return switch (call.descriptor().policy().executionResourceKey()) {
            case DecisionClient.EXECUTE_RESOURCE_KEY -> {
                if (decisions == null) throw unavailable();
                var request = new ReadOnlyDecisionToolTransport.Request(call.run(), call.lease(), call.descriptor(),
                        call.arguments(), call.callId(), call.deadline());
                yield decisions.withVerifiedCall(request, (executor, operation) -> read(call, executor, ignored -> {
                    var observed = Objects.requireNonNull(operation.evaluate());
                    return observed.status() == ReadOnlyDecisionToolTransport.Status.MATCHED
                            ? success(observed.output(), null) : failure(Status.REJECTED, "DECISION_NO_MATCH");
                }));
            }
            case ReadOnlyMcpToolTransport.EXECUTE_RESOURCE_KEY -> {
                if (mcp == null) throw unavailable();
                var request = new ReadOnlyMcpToolTransport.Request(call.run(), call.lease(), call.descriptor(),
                        call.arguments(), call.callId(), call.deadline());
                yield mcp.withVerifiedCall(request, (executor, operation) -> {
                    if (operation == null) throw unavailable();
                    return read(call, executor, deadline -> {
                        var observed = Objects.requireNonNull(operation.call(deadline));
                        return switch (observed.status()) {
                            case SUCCESS -> success(observed.output(), null);
                            case TOOL_ERROR -> failure(Status.FAILED_KNOWN, "DOWNSTREAM_TOOL_ERROR");
                            case UNKNOWN -> failure(Status.UNKNOWN, "RUNTIME_OUTCOME_UNKNOWN");
                        };
                    });
                });
            }
            default -> throw denied();
        };
    }

    private Map<String, Object> read(ToolCallVO call, ReadOnlyDecisionToolTransport.VerifiedExecutor executor, ReadOperation operation) {
        var deadline = verifyExecutor(call, executor);
        requireToolAuthorization(call, null, deadline, ToolExecutionAuthorization.Boundary.DISPATCH);
        var request = receipt(call, null, deadline);
        var dispatched = inTransaction(() -> {
            var prepared = receipts.prepare(call.run().actor(), call.lease(), call.revision(), request, call.requireCurrent());
            return receipts.markDispatched(call.run().actor(), call.lease(), call.revision(), call.callId(),
                    prepared.getRevision(), null, call.requireCurrent());
        });
        ToolExecutionOutcome result;
        try {
            call.boundary().beforeSend();
            result = verifyOutput(call, operation.call(deadline));
        } catch (RuntimeException failure) {
            result = failure(Status.UNKNOWN, "RUNTIME_OUTCOME_UNKNOWN");
        }
        var terminal = readOutcome(result);
        requireToolAuthorization(call, null, deadline, ToolExecutionAuthorization.Boundary.DISPATCH);
        inTransaction(() -> receipts.finish(call.run().actor(), call.lease(), call.revision(), call.callId(),
                dispatched.getRevision(), null, terminal, call.requireCurrent()));
        return modelOutput(result);
    }

    private Map<String, Object> write(ToolCallVO call) {
        if (starts == null || actionIdentity == null || !ProcessClient.START_RESOURCE_KEY.equals(call.descriptor().policy().executionResourceKey())) {
            throw denied();
        }
        var identity = actionIdentity.resolve(call.material(), call.descriptor());
        if (identity == null) throw denied();
        var action = new ToolActionRequest(call.run().actor(), call.descriptor(), identity.businessKey(), identity.businessVersion(), call.arguments());
        var existing = actions.prepareRuntime(call.run(), call.lease(), action, call.descriptor(),
                ledger.databaseNow().plus(identity.retention()), call.deadline());
        if ("APPLIED".equals(existing.state()) && call.descriptor().policy().risk() == Risk.IDEMPOTENT_WRITE) {
            var replay = results.recordRuntimeStartResultAccess(new ToolResultAccessStore.StartResultAccessRequestVO(
                    call.selected().binding(), call.lease(), call.descriptor(), action, call.callId()),
                    call.deadline(), call.requireCurrent());
            return call.boundary().verifyOutput(replay.sourceOutcome().output());
        }
        if ("PREPARED".equals(existing.state()) && call.descriptor().policy().risk() == Risk.IDEMPOTENT_WRITE) {
            return sendWrite(call, action, null);
        }
        if (!"AWAITING_APPROVAL".equals(existing.state()) || approvals == null || approvalReceipt == null
                || call.descriptor().policy().risk() != Risk.APPROVAL_REQUIRED) throw unavailable();
        var reference = approvalReceipt.resolve(call.material(), call.descriptor(), action);
        if (reference == null) throw denied();
        var request = new FixedToolApprovalVerifier.Request(call.run(), call.lease(), call.descriptor(), action, reference, call.deadline());
        return approvals.withVerifiedApproval(request, proof -> sendWrite(call, action, proof));
    }

    private Map<String, Object> sendWrite(ToolCallVO call, ToolActionRequest action, FixedToolApprovalVerifier.ApprovedObservation proof) {
        var request = new FixedBpmnStartToolTransport.Request(call.run(), call.lease(), call.descriptor(), action, call.callId(), call.deadline());
        return starts.withVerifiedCall(request, (executor, operation) -> {
            var deadline = verifyExecutor(call, executor);
            if (proof != null) deadline = earliest(deadline, proof.expiresAt());
            var pending = receipt(call, action, deadline);
            var prepared = inTransaction(() -> receipts.prepare(call.run().actor(), call.lease(), call.revision(), pending, call.requireCurrent()));
            var dispatch = new RuntimeCallReceiptBO.DispatchVO(1, OrchestrationRequestDigests.hash(
                    List.of("TIANJI_RUNTIME_TOOL_DISPATCH_V2", UUID.randomUUID().toString()), Map.of()),
                    executor.actor().issuerFingerprint(), executor.actor().subjectId());
            var dispatched = actions.dispatchRuntime(call.run(), call.lease(), call.revision(), action, call.descriptor(),
                    prepared, dispatch, proof, call.requireCurrent());
            ToolExecutionOutcome result;
            try {
                var observed = operation.start(deadline, call.boundary()::beforeSend);
                result = verifyOutput(call, success(observed.output(), observed.hostReceiptReference()));
            } catch (FixedBpmnStartToolTransport.Failure failure) {
                result = switch (failure.kind()) {
                    case OPERATION_NOT_SENT -> failure(Status.FAILED_KNOWN, "DISPATCH_NOT_SENT");
                    case OPERATION_DENIED -> failure(Status.REJECTED, "DOWNSTREAM_PERMISSION_DENIED");
                    default -> failure(Status.UNKNOWN, "RUNTIME_OUTCOME_UNKNOWN");
                };
            } catch (RuntimeException failure) {
                result = failure(Status.UNKNOWN, "RUNTIME_OUTCOME_UNKNOWN");
            }
            actions.finishRuntime(call.run(), call.lease(), call.revision(), action, call.descriptor(), dispatched, result, call.requireCurrent());
            if (result.status() == Status.REJECTED) call.boundary().recordKnownNoEffect();
            return modelOutput(result);
        });
    }

    private void requireToolAuthorization(ToolCallVO call, ToolActionRequest action, Instant deadline, ToolExecutionAuthorization.Boundary boundary) {
        call.requireCurrent().run();
        var expected = new ToolExecutionAuthorization.Request(call.run(), call.lease(), call.descriptor().reference(),
                call.descriptor().policy().bindingSha256(), call.descriptor().parametersSha256(call.arguments()),
                action == null ? null : action.intentId(), deadline);
        var check = authorization.check(expected, boundary);
        if (check == null || !expected.equals(check.request()) || check.boundary() != boundary) throw unavailable();
        if (check.decision() == ToolExecutionAuthorization.Decision.DENY) throw denied();
        if (check.decision() != ToolExecutionAuthorization.Decision.ALLOW || check.audit() == null
                || !call.evidence().grantId().equals(check.audit().grantId()) || check.audit().expiresAt().isBefore(deadline)) throw unavailable();
        call.requireCurrent().run();
    }

    private static RuntimeCallReceiptBO receipt(ToolCallVO call, ToolActionRequest action, Instant deadline) {
        var descriptor = call.descriptor();
        var binding = call.selected().binding();
        var capability = new RuntimeCallReceiptBO.CapabilityVO(UUID.fromString(binding.capabilityId()), binding.bindingSha256(),
                call.evidence().reservation().issueEpoch(), call.evidence().grantId(), descriptor.key(), descriptor.version(),
                descriptor.reference().contentSha256(), binding.backendBindingSha256(), descriptor.policy().risk());
        return new RuntimeCallReceiptBO().setTenantId(call.run().actor().tenantId()).setRunId(call.run().runId())
                .setCallId(call.callId()).setKind(RuntimeCallReceiptBO.Kind.TOOL).setOwner(call.run().actor())
                .setLeaseEpoch(call.lease().epoch()).setCapability(capability).setInputSha256(descriptor.parametersSha256(call.arguments()))
                .setIntentId(action == null ? null : UUID.fromString(action.intentId())).setContextMetadata(Map.of()).setDeadlineAt(deadline);
    }

    private Instant verifyExecutor(ToolCallVO call, ReadOnlyDecisionToolTransport.VerifiedExecutor executor) {
        if (executor == null || executor.actor().tenantId() != call.run().actor().tenantId()) throw denied();
        var deadline = earliest(call.deadline(), executor.expiresAt());
        if (!deadline.isAfter(ledger.databaseNow())) throw unavailable();
        return deadline;
    }

    private static Instant earliest(Instant first, Instant... remaining) {
        var result = first;
        for (var instant : remaining) {
            if (instant.isBefore(result)) result = instant;
        }
        return result.truncatedTo(ChronoUnit.MICROS);
    }

    private <T> T inTransaction(java.util.function.Supplier<T> operation) {
        return Objects.requireNonNull(transaction.execute(status -> operation.get()));
    }

    private static ToolExecutionOutcome verifyOutput(ToolCallVO call, ToolExecutionOutcome outcome) {
        var validated = outcome.validated(call.descriptor());
        if (validated.status() == Status.SUCCEEDED) call.boundary().verifyOutput(validated.output());
        return validated;
    }

    private static ToolExecutionOutcome success(Map<String, Object> output, String receipt) {
        return new ToolExecutionOutcome(Status.SUCCEEDED, output, receipt, null, null);
    }

    private static ToolExecutionOutcome failure(Status status, String code) {
        return new ToolExecutionOutcome(status, Map.of(), null, code, null);
    }

    private static OutcomeVO readOutcome(ToolExecutionOutcome result) {
        return switch (result.status()) {
            case SUCCEEDED -> new OutcomeVO(State.SUCCEEDED, OrchestrationRequestDigests.hash(
                    List.of("TIANJI_RUNTIME_READ_TOOL_RESULT_V2"), result.output()), null, null, null);
            case UNKNOWN -> new OutcomeVO(State.UNKNOWN, null, null, null, result.errorCode());
            case REJECTED, FAILED_KNOWN -> new OutcomeVO(State.FAILED, null, null, null, result.errorCode());
            default -> throw unavailable();
        };
    }

    private static Map<String, Object> modelOutput(ToolExecutionOutcome result) {
        if (result.status() == Status.SUCCEEDED) return result.output();
        if (result.status() == Status.REJECTED && "DOWNSTREAM_PERMISSION_DENIED".equals(result.errorCode())) throw denied();
        throw unavailable();
    }

    private static AgentLedgerException denied() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Agent Runtime tool permission was denied");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent Runtime tool execution was unavailable");
    }

    @FunctionalInterface
    private interface ReadOperation {
        ToolExecutionOutcome call(Instant deadline);
    }

    private record ToolCallVO(RuntimeExecutionMaterialVO material, AgentRunLease lease, SelectedToolVO selected,
                              ToolExecutionGrant.EvidenceVO evidence, UUID callId, Map<String, Object> arguments,
                              Instant deadline, ToolCallBoundary boundary, Runnable requireCurrent) {
        AgentRunAuthorization.Request run() { return material.authorization(); }
        ToolDescriptor descriptor() { return selected.descriptor(); }
        long revision() { return material.run().revision(); }
        @Override public String toString() { return "ToolCallVO[redacted]"; }
    }
}
