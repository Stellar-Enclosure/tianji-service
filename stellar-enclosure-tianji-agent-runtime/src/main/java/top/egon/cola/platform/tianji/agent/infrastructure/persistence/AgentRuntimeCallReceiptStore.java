package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.CapabilityVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.DispatchVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.Kind;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.OutcomeVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.SourceResultVO;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO.State;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore.error;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore.instant;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore.unavailable;
import static top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentRuntimeBindingStore.utc;

/** Technical call mutations compose with the owning ledger and action in one local transaction. */
@Slf4j
@Component("agentRuntimeCallReceiptStore")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentRuntimeCallReceiptStore {
    private static final int MAX_CONTEXT_BYTES = 65_536;
    private static final int MAX_AUTH_BYTES = 16_384;
    private static final Set<String> AUTHORIZATION_FIELDS = Set.of("decisionId", "checkedAt", "expiresAt", "scopeSha256", "decision");
    private static final String SELECT_CALL = """
            SELECT owner_type,owner_issuer,owner_id,lease_epoch,kind,
                   capability_id,capability_binding_sha256,capability_issue_epoch,grant_id,
                   tool_key,tool_version,tool_sha256,backend_binding_sha256,risk,
                   input_sha256,context_metadata,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id,
                   source_action_revision,source_result_sha256,state,revision,deadline_at,result_sha256,input_tokens,output_tokens,
                   error_code,created_at,dispatched_at,finished_at
            FROM tianji_agent.agent_runtime_call_receipt WHERE tenant_id=? AND run_id=? AND call_id=?
            """;

    @Qualifier("agentRuntimeBindingStore")
    private final AgentRuntimeBindingStore bindings;
    private final AgentJsonCodec codec = new AgentJsonCodec();
    private final ToolExecutionJsonCodec toolCodec = new ToolExecutionJsonCodec();

    public AgentRuntimeCallReceiptStore(DataSource source, AgentRuntimeBindingStore bindings) {
        if (source == null || bindings == null || !bindings.usesDataSource(source)) {
            throw new IllegalArgumentException("Runtime stores must share the Agent ledger DataSource");
        }
        this.bindings = bindings;
    }

    public RuntimeCallReceiptBO prepare(
            @NotNull @Valid TrustedAgentActor owner,
            @NotNull @Valid AgentRunLease lease,
            @Positive long runRevision,
            @NotNull @Valid RuntimeCallReceiptBO request,
            @NotNull Runnable verifyCurrentPermit) {
        return prepare(owner, lease, runRevision, request, verifyCurrentPermit, false);
    }

    private RuntimeCallReceiptBO prepare(TrustedAgentActor owner, AgentRunLease lease, long runRevision,
                                         RuntimeCallReceiptBO request, Runnable verifyCurrentPermit, boolean resultReplay) {
        Objects.requireNonNull(request, "request");
        var copy = copyRequest(request);
        copy.validateForPrepare();
        if (!copy.getOwner().equals(owner) || lease == null || copy.getLeaseEpoch() != lease.epoch()
                || !copy.getRunId().equals(lease.runId())) throw unavailable();
        String context = codec.write(copy.getContextMetadata(), MAX_CONTEXT_BYTES);
        var locked = lockRunAndBinding(owner, lease);
        var action = lockAction(owner, copy);
        lockCapability(owner, copy, true);
        var original = read(owner.tenantId(), lease.runId(), copy.getCallId(), true);
        var now = current(locked, lease, runRevision, copy, verifyCurrentPermit);
        if (copy.getKind() == Kind.TOOL_RESULT_READ) verifySource(copy, action, now);
        if (original != null) {
            if (resultReplay) return replayResultAccess(original, copy, action);
            if (!sameRequest(original, copy, context)) throw error(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT);
            return original;
        }
        if (copy.isWrite()) requireDispatchableAction(action, now);
        var capability = copy.getCapability();
        var source = copy.getSourceResult();
        // Result disclosure consumes a tool operation and shares the technical concurrency ceiling.
        bindings.reserve(locked.binding, copy.getKind() == Kind.MODEL ? 1 : 0,
                copy.getKind() == Kind.MODEL ? 0 : 1);
        int inserted = bindings.jdbc().update("""
                INSERT INTO tianji_agent.agent_runtime_call_receipt
                  (tenant_id,run_id,call_id,kind,owner_type,owner_issuer,owner_id,lease_epoch,capability_id,
                   capability_binding_sha256,capability_issue_epoch,grant_id,tool_key,tool_version,tool_sha256,
                   backend_binding_sha256,risk,input_sha256,context_metadata,intent_id,source_action_revision,
                   source_result_sha256,state,revision,deadline_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'PREPARED',1,?,?)
                """, owner.tenantId(), lease.runId(), copy.getCallId(), copy.getKind().name(), owner.subjectType().name(),
                owner.issuerFingerprint(), owner.subjectId(), lease.epoch(),
                capability == null ? null : capability.id(), capability == null ? null : capability.bindingSha256(),
                capability == null ? null : capability.issueEpoch(), capability == null ? null : capability.grantId(),
                capability == null ? null : capability.toolKey(), capability == null ? null : capability.toolVersion(),
                capability == null ? null : capability.toolSha256(), capability == null ? null : capability.backendBindingSha256(),
                capability == null ? null : capability.risk().name(), copy.getInputSha256(), context, copy.getIntentId(),
                source == null ? null : source.revision(), source == null ? null : source.resultSha256(),
                utc(copy.getDeadlineAt()), utc(now));
        if (inserted != 1) throw unavailable();
        return requireReceipt(owner.tenantId(), lease.runId(), copy.getCallId());
    }

    RuntimeCallReceiptBO recordResultAccess(TrustedAgentActor owner, AgentRunLease lease, long runRevision,
                                            RuntimeCallReceiptBO request, Runnable verifyCurrentPermit) {
        if (request.getKind() != Kind.TOOL_RESULT_READ) throw invalid();
        var prepared = prepare(owner, lease, runRevision, request, verifyCurrentPermit, true);
        if (prepared.getState() == State.SUCCEEDED) return prepared;
        var dispatched = markDispatched(owner, lease, runRevision, request.getCallId(),
                prepared.getRevision(), null, verifyCurrentPermit);
        return finish(owner, lease, runRevision, request.getCallId(), dispatched.getRevision(), null,
                new OutcomeVO(State.SUCCEEDED, request.getSourceResult().resultSha256(), null, null, null), verifyCurrentPermit);
    }

    public RuntimeCallReceiptBO markDispatched(
            @NotNull @Valid TrustedAgentActor owner,
            @NotNull @Valid AgentRunLease lease,
            @Positive long runRevision,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            @Valid DispatchVO dispatch,
            @NotNull Runnable verifyCurrentPermit) {
        return markDispatched(owner, lease, runRevision, callId, expectedRevision, dispatch, verifyCurrentPermit, null);
    }

    /** Business action CAS runs after the receipt update so its immediate composite FK can match. */
    public RuntimeCallReceiptBO markDispatched(
            @NotNull @Valid TrustedAgentActor owner,
            @NotNull @Valid AgentRunLease lease,
            @Positive long runRevision,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            @Valid DispatchVO dispatch,
            @NotNull Runnable verifyCurrentPermit,
            Consumer<RuntimeCallReceiptBO> recordBusinessDispatch) {
        var locked = lockExisting(owner, lease, callId, true);
        var receipt = locked.receipt;
        expect(receipt, lease, expectedRevision, State.PREPARED);
        if (receipt.isWrite() != (dispatch != null) || receipt.isWrite() != (recordBusinessDispatch != null)) throw invalid();
        var now = current(locked.execution, lease, runRevision, receipt, verifyCurrentPermit);
        if (receipt.getKind() == Kind.TOOL_RESULT_READ) verifySource(receipt, locked.action, now);
        if (receipt.isWrite()) requireDispatchableAction(locked.action, now);

        int updated = bindings.jdbc().update("""
                UPDATE tianji_agent.agent_runtime_call_receipt
                SET state='DISPATCHED',revision=revision+1,dispatched_at=?,dispatch_epoch=?,dispatch_nonce_sha256=?,
                    executor_issuer=?,executor_id=?
                WHERE tenant_id=? AND run_id=? AND call_id=? AND revision=? AND state='PREPARED'
                """, utc(now), dispatch == null ? null : dispatch.epoch(), dispatch == null ? null : dispatch.nonceSha256(),
                dispatch == null ? null : dispatch.executorIssuer(), dispatch == null ? null : dispatch.executorId(),
                owner.tenantId(), lease.runId(), callId, expectedRevision);
        if (updated != 1) throw closed();
        var result = requireReceipt(owner.tenantId(), lease.runId(), callId);
        if (recordBusinessDispatch != null) {
            try {
                recordBusinessDispatch.accept(copyRequest(result));
                var action = lockAction(owner, receipt);
                requireActionDispatch(action, result, "DISPATCHING");
                var after = bindings.now();
                requireWindow(new LockedExecutionState(bindings.lockRun(owner, lease.runId()), locked.execution.binding),
                        lease, runRevision, receipt, after);
                if (!action.expiresAt().isAfter(after)) throw error(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED);
            } catch (RuntimeException | Error failure) {
                bindings.requireRollback();
                throw failure;
            }
        }
        return result;
    }

    public RuntimeCallReceiptBO finish(
            @NotNull @Valid TrustedAgentActor owner,
            @NotNull @Valid AgentRunLease lease,
            @Positive long runRevision,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            @Valid DispatchVO dispatch,
            @NotNull @Valid OutcomeVO outcome,
            @NotNull Runnable verifyCurrentPermit) {
        return finish(owner, lease, runRevision, callId, expectedRevision, dispatch, outcome, verifyCurrentPermit, null);
    }

    /** A dispatched write completes its exact business action under the same locks as its technical receipt. */
    public RuntimeCallReceiptBO finish(
            @NotNull @Valid TrustedAgentActor owner,
            @NotNull @Valid AgentRunLease lease,
            @Positive long runRevision,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            @Valid DispatchVO dispatch,
            @NotNull @Valid OutcomeVO outcome,
            @NotNull Runnable verifyCurrentPermit,
            Consumer<RuntimeCallReceiptBO> recordBusinessOutcome) {
        var locked = lockExisting(owner, lease, callId, true);
        var receipt = locked.receipt;
        Objects.requireNonNull(outcome, "outcome");
        if (receipt.getState() != State.DISPATCHED && (receipt.getState() != State.PREPARED || outcome.state() != State.FAILED)) throw closed();
        expect(receipt, lease, expectedRevision, receipt.getState());
        if (!Objects.equals(receipt.getDispatch(), dispatch)) throw closed();
        boolean dispatchedWrite = receipt.isWrite() && receipt.getState() == State.DISPATCHED;
        if (dispatchedWrite != (recordBusinessOutcome != null)) throw invalid();
        var now = current(locked.execution, lease, runRevision, receipt, verifyCurrentPermit);
        if (receipt.getKind() == Kind.TOOL_RESULT_READ && outcome.state() == State.SUCCEEDED) {
            verifySource(receipt, locked.action, now);
            if (!receipt.getSourceResult().resultSha256().equals(outcome.resultSha256())) throw closed();
        }
        try {
            if (dispatchedWrite) {
                requireActionDispatch(locked.action, receipt, "DISPATCHING");
                recordBusinessOutcome.accept(copyRequest(receipt));
                requireActionOutcome(lockAction(owner, receipt), receipt, outcome);
            }
            return terminal(locked.execution.binding, receipt, outcome, now, true);
        } catch (RuntimeException | Error failure) {
            if (dispatchedWrite) bindings.requireRollback();
            throw failure;
        }
    }

    /**
     * Outcome loss may only be reconciled for the original write dispatch. The caller supplies
     * the authority of the exact business effect and updates the shared action in this transaction.
     */
    public RuntimeCallReceiptBO reconcile(
            @NotNull @Valid TrustedAgentActor owner,
            @NotBlank @Size(max = 64) String runId,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            @Valid DispatchVO dispatch,
            @NotNull @Valid OutcomeVO outcome,
            @NotNull Runnable verifyAuthoritativeOutcome) {
        var locked = lockExisting(owner, runId, callId, false);
        var receipt = locked.receipt;
        if (!receipt.isWrite() || receipt.getState() != State.UNKNOWN || receipt.getRevision() != expectedRevision
                || !Objects.equals(receipt.getDispatch(), dispatch) || outcome.state() == State.UNKNOWN) throw closed();
        requireActionDispatch(locked.action, receipt, "UNKNOWN");
        try {
            Objects.requireNonNull(verifyAuthoritativeOutcome, "verifyAuthoritativeOutcome").run();
            requireActionOutcome(lockAction(owner, receipt), receipt, outcome);
            return terminal(locked.execution.binding, receipt, outcome, bindings.now(), false);
        } catch (RuntimeException | Error failure) {
            bindings.requireRollback();
            throw failure;
        }
    }

    /** Recover an exact pending receipt on cancellation, deadline or loss of its original lease. */
    public RuntimeCallReceiptBO recover(
            @NotNull @Valid TrustedAgentActor owner,
            @NotBlank @Size(max = 64) String runId,
            @NotNull UUID callId,
            @Positive long expectedRevision) {
        return recover(owner, runId, callId, expectedRevision, null);
    }

    /** A dispatched write is recovered with the corresponding shared action CAS in this transaction. */
    public RuntimeCallReceiptBO recover(
            @NotNull @Valid TrustedAgentActor owner,
            @NotBlank @Size(max = 64) String runId,
            @NotNull UUID callId,
            @Positive long expectedRevision,
            Consumer<RuntimeCallReceiptBO> recordUnknownBusinessOutcome) {
        var locked = lockExisting(owner, runId, callId, false);
        var receipt = locked.receipt;
        var run = locked.execution.run;
        var now = bindings.now();
        if (receipt.getRevision() != expectedRevision || !Set.of(State.PREPARED, State.DISPATCHED).contains(receipt.getState())
                || !run.cancelled() && "RUNNING".equals(run.status()) && run.leaseEpoch() == receipt.getLeaseEpoch()
                    && run.leaseExpiresAt() != null && run.leaseExpiresAt().isAfter(now) && receipt.getDeadlineAt().isAfter(now)) throw closed();
        boolean dispatchedWrite = receipt.isWrite() && receipt.getState() == State.DISPATCHED;
        if (dispatchedWrite != (recordUnknownBusinessOutcome != null)) throw invalid();
        var state = receipt.getState() == State.PREPARED ? State.FAILED : State.UNKNOWN;
        var outcome = new OutcomeVO(state, null, null, null, state == State.FAILED ? "RUNTIME_CALL_CLOSED" : "RUNTIME_OUTCOME_UNKNOWN");
        try {
            if (dispatchedWrite) {
                requireActionDispatch(locked.action, receipt, "DISPATCHING");
                recordUnknownBusinessOutcome.accept(copyRequest(receipt));
                requireActionOutcome(lockAction(owner, receipt), receipt, outcome);
            }
            return terminal(locked.execution.binding, receipt, outcome, now, true);
        } catch (RuntimeException | Error failure) {
            if (dispatchedWrite) bindings.requireRollback();
            throw failure;
        }
    }

    private RuntimeCallReceiptBO terminal(RuntimeBindingBO binding, RuntimeCallReceiptBO receipt, OutcomeVO outcome,
                                           Instant now, boolean releaseActive) {
        if (receipt.getKind() != Kind.MODEL && (outcome.inputTokens() != null || outcome.outputTokens() != null)
                || receipt.getState() == State.PREPARED && (outcome.inputTokens() != null || outcome.outputTokens() != null)) throw invalid();
        int updated = bindings.jdbc().update("""
                UPDATE tianji_agent.agent_runtime_call_receipt
                SET state=?,revision=revision+1,result_sha256=?,input_tokens=?,output_tokens=?,error_code=?,finished_at=?
                WHERE tenant_id=? AND run_id=? AND call_id=? AND revision=? AND state=?
                """, outcome.state().name(), outcome.resultSha256(), outcome.inputTokens(), outcome.outputTokens(),
                outcome.errorCode(), utc(now), receipt.getTenantId(), receipt.getRunId(), receipt.getCallId(),
                receipt.getRevision(), receipt.getState().name());
        if (updated != 1) throw closed();
        if (releaseActive) {
            try {
                bindings.release(binding);
            } catch (RuntimeException | Error failure) {
                bindings.requireRollback();
                throw failure;
            }
        }
        return requireReceipt(receipt.getTenantId(), receipt.getRunId(), receipt.getCallId());
    }

    private LockedExecutionState lockRunAndBinding(TrustedAgentActor owner, AgentRunLease lease) {
        return lockRunAndBinding(owner, Objects.requireNonNull(lease, "lease").runId());
    }

    private LockedExecutionState lockRunAndBinding(TrustedAgentActor owner, String runId) {
        var run = bindings.lockRun(owner, runId);
        var binding = bindings.lockBinding(owner.tenantId(), runId);
        bindings.requireSnapshot(run, binding);
        return new LockedExecutionState(run, binding);
    }

    private LockedCallState lockExisting(TrustedAgentActor owner, AgentRunLease lease, UUID callId, boolean currentCapability) {
        return lockExisting(owner, Objects.requireNonNull(lease, "lease").runId(), callId, currentCapability);
    }

    private LockedCallState lockExisting(TrustedAgentActor owner, String runId, UUID callId, boolean currentCapability) {
        var locked = lockRunAndBinding(owner, runId);
        // The run lock serializes its receipts; probe only to discover the action/capability keys.
        var original = requireReceipt(owner.tenantId(), runId, callId);
        if (!owner.equals(original.getOwner())) throw unavailable();
        var action = lockAction(owner, original);
        lockCapability(owner, original, currentCapability);
        var receipt = read(owner.tenantId(), runId, callId, true);
        if (receipt == null || !receipt.equals(original)) throw closed();
        return new LockedCallState(locked, action, receipt);
    }

    private Instant current(LockedExecutionState locked, AgentRunLease lease, long runRevision, RuntimeCallReceiptBO receipt,
                            Runnable verifyCurrentPermit) {
        var before = bindings.now();
        requireWindow(locked, lease, runRevision, receipt, before);
        // Only trusted host-local permission evidence belongs in this callback. Transport is after commit.
        Objects.requireNonNull(verifyCurrentPermit, "verifyCurrentPermit").run();
        var after = bindings.now();
        requireWindow(locked, lease, runRevision, receipt, after);
        return after;
    }

    private void requireWindow(LockedExecutionState locked, AgentRunLease lease, long runRevision, RuntimeCallReceiptBO receipt, Instant now) {
        bindings.requireCurrent(locked.run, locked.binding, lease, runRevision, now);
        if (!receipt.getDeadlineAt().isAfter(now) || receipt.getDeadlineAt().isAfter(locked.binding.getAttemptDeadlineAt())
                || receipt.getDeadlineAt().isAfter(locked.run.leaseExpiresAt())) {
            throw error(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED);
        }
    }

    private ActionRowVO lockAction(TrustedAgentActor owner, RuntimeCallReceiptBO receipt) {
        if (receipt.getIntentId() == null) return null;
        var capability = receipt.getCapability();
        var rows = bindings.jdbc().query("""
                SELECT action_profile,state,revision,request_sha256,descriptor,result,expires_at,dispatch_run_id,dispatch_call_id,
                       dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id
                FROM tianji_agent.agent_tool_action_intent
                WHERE tenant_id=? AND intent_id=? AND owner_type=? AND owner_issuer=? AND owner_id=?
                    AND tool_key=? AND tool_version=? AND tool_sha256=? AND binding_sha256=? AND risk=?
                FOR UPDATE
                """, (rs, row) -> new ActionRowVO(rs.getString("action_profile"), rs.getString("state"), rs.getLong("revision"),
                rs.getString("request_sha256"), rs.getString("descriptor"), rs.getString("result"), instant(rs, "expires_at"), rs.getString("dispatch_run_id"),
                rs.getObject("dispatch_call_id", UUID.class), dispatch(rs)),
                owner.tenantId(), receipt.getIntentId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                capability.toolKey(), capability.toolVersion(), capability.toolSha256(), capability.backendBindingSha256(), capability.risk().name());
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst();
    }

    private void lockCapability(TrustedAgentActor owner, RuntimeCallReceiptBO receipt, boolean current) {
        var capability = receipt.getCapability();
        if (capability == null) return;
        var rows = bindings.jdbc().query("""
                SELECT issue_epoch,grant_id,expires_at,deadline_at FROM tianji_agent.agent_tool_capability
                WHERE tenant_id=? AND run_id=? AND owner_type=? AND owner_issuer=? AND owner_id=?
                    AND capability_id=? AND binding_sha256=? AND tool_key=? AND tool_version=?
                    AND tool_sha256=? AND backend_binding_sha256=? FOR UPDATE
                """, (rs, row) -> new CapabilityRowVO(rs.getLong("issue_epoch"), rs.getString("grant_id"), instant(rs, "expires_at"),
                instant(rs, "deadline_at")), owner.tenantId(), receipt.getRunId(), owner.subjectType().name(),
                owner.issuerFingerprint(), owner.subjectId(), capability.id(), capability.bindingSha256(), capability.toolKey(),
                capability.toolVersion(), capability.toolSha256(), capability.backendBindingSha256());
        if (rows.size() != 1) throw unavailable();
        var stored = rows.getFirst();
        if (current && (stored.epoch() != capability.issueEpoch() || !Objects.equals(stored.grantId(), capability.grantId())
                || receipt.getDeadlineAt().isAfter(stored.deadline()) || stored.expiry() != null && receipt.getDeadlineAt().isAfter(stored.expiry()))) {
            throw unavailable();
        }
    }

    private void verifySource(RuntimeCallReceiptBO receipt, ActionRowVO action, Instant now) {
        var expected = receipt.getSourceResult();
        if (action == null || !"APPLIED".equals(action.state()) || action.revision() != expected.revision()
                || !expected.resultSha256().equals(sourceResultSha256(receipt, action))) throw unavailable();
        var metadata = receipt.getContextMetadata().get("authorization");
        if (!(metadata instanceof Map<?, ?> authorization) || !authorization.keySet().equals(AUTHORIZATION_FIELDS)
                || !"ALLOW".equals(authorization.get("decision"))
                || !(authorization.get("decisionId") instanceof String decisionId) || decisionId.isBlank() || decisionId.length() > 128
                || !(authorization.get("scopeSha256") instanceof String scope) || !scope.matches("[0-9a-f]{64}")
                || !(authorization.get("checkedAt") instanceof String checkedText) || !checkedText.endsWith("Z")
                || !(authorization.get("expiresAt") instanceof String expiresText) || !expiresText.endsWith("Z")) throw unavailable();
        try {
            codec.write(authorization, MAX_AUTH_BYTES);
            var checkedAt = Instant.parse(checkedText);
            var expiresAt = Instant.parse(expiresText);
            if (checkedAt.isAfter(now) || !expiresAt.isAfter(now) || receipt.getDeadlineAt().isAfter(expiresAt)) throw unavailable();
        } catch (IllegalArgumentException | java.time.DateTimeException malformed) {
            throw unavailable();
        }
    }

    private String sourceResultSha256(RuntimeCallReceiptBO receipt, ActionRowVO action) {
        var descriptor = actionDescriptor(receipt, action);
        return toolCodec.sourceResultSha256(receipt.getIntentId().toString(), action.revision(), action.requestSha256(),
                ToolExecutionJsonCodec.ActionProfile.valueOf(action.profile()), descriptor, successfulOutcome(action, descriptor));
    }

    private ToolDescriptor actionDescriptor(RuntimeCallReceiptBO receipt, ActionRowVO action) {
        var capability = receipt.getCapability();
        return toolCodec.descriptor(action.descriptor(),
                new DefinitionResourceRef(capability.toolKey(), capability.toolVersion(), capability.toolSha256()));
    }

    private ToolExecutionOutcome successfulOutcome(ActionRowVO action, ToolDescriptor descriptor) {
        var outcome = actionOutcome(action, descriptor);
        if (outcome.status() != ToolExecutionOutcome.Status.SUCCEEDED) throw unavailable();
        return outcome;
    }

    private ToolExecutionOutcome actionOutcome(ActionRowVO action, ToolDescriptor descriptor) {
        return toolCodec.outcome(ToolExecutionJsonCodec.ActionProfile.valueOf(action.profile()), action.result(), descriptor);
    }

    private static void requireDispatchableAction(ActionRowVO action, Instant now) {
        if (action == null || !"RUNTIME_V2".equals(action.profile())
                || !Set.of("PREPARED", "AWAITING_APPROVAL").contains(action.state())) throw closed();
        if (!action.expiresAt().isAfter(now)) throw error(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED);
    }

    private void requireActionOutcome(ActionRowVO action, RuntimeCallReceiptBO receipt, OutcomeVO outcome) {
        var expected = switch (outcome.state()) {
            case SUCCEEDED -> ToolExecutionOutcome.Status.SUCCEEDED;
            case FAILED -> action != null && "REJECTED".equals(action.state())
                    ? ToolExecutionOutcome.Status.REJECTED : ToolExecutionOutcome.Status.FAILED_KNOWN;
            case UNKNOWN -> ToolExecutionOutcome.Status.UNKNOWN;
            default -> throw invalid();
        };
        requireActionDispatch(action, receipt, expected == ToolExecutionOutcome.Status.SUCCEEDED ? "APPLIED" : expected.name());
        var descriptor = actionDescriptor(receipt, action);
        var recorded = actionOutcome(action, descriptor);
        if (recorded.status() != expected || !Objects.equals(outcome.errorCode(), recorded.errorCode())) throw closed();
        if (expected == ToolExecutionOutcome.Status.SUCCEEDED && !outcome.resultSha256().equals(
                toolCodec.runtimeWriteResultSha256(receipt.getIntentId().toString(), action.requestSha256(), descriptor, recorded))) {
            throw closed();
        }
    }

    private static void requireActionDispatch(ActionRowVO action, RuntimeCallReceiptBO receipt, String state) {
        if (action == null || !"RUNTIME_V2".equals(action.profile()) || !state.equals(action.state())
                || !receipt.getRunId().equals(action.runId()) || !receipt.getCallId().equals(action.callId())
                || !receipt.getDispatch().equals(action.dispatch())) throw closed();
    }

    private static void expect(RuntimeCallReceiptBO receipt, AgentRunLease lease, long expectedRevision, State state) {
        if (receipt.getLeaseEpoch() != lease.epoch()) throw error(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED);
        if (receipt.getRevision() != expectedRevision || receipt.getState() != state) throw closed();
    }

    private RuntimeCallReceiptBO copyRequest(RuntimeCallReceiptBO receipt) {
        var metadata = receipt.getContextMetadata();
        return new RuntimeCallReceiptBO(receipt.getTenantId(), receipt.getRunId(), receipt.getCallId(), receipt.getKind(),
                receipt.getOwner(), receipt.getLeaseEpoch(), receipt.getCapability(), receipt.getInputSha256(),
                metadata == null ? null : codec.readObject(codec.write(metadata, MAX_CONTEXT_BYTES)),
                receipt.getIntentId(), receipt.getDispatch(), receipt.getSourceResult(), receipt.getState(), receipt.getRevision(),
                receipt.getDeadlineAt(), receipt.getResultSha256(), receipt.getInputTokens(), receipt.getOutputTokens(),
                receipt.getErrorCode(), receipt.getCreatedAt(), receipt.getDispatchedAt(), receipt.getFinishedAt());
    }

    private boolean sameRequest(RuntimeCallReceiptBO receipt, RuntimeCallReceiptBO request, String context) {
        return sameRequestIdentity(receipt, request)
                && codec.write(receipt.getContextMetadata(), MAX_CONTEXT_BYTES).equals(context)
                && receipt.getDeadlineAt().equals(request.getDeadlineAt());
    }

    private RuntimeCallReceiptBO replayResultAccess(RuntimeCallReceiptBO original, RuntimeCallReceiptBO request, ActionRowVO action) {
        if (!sameRequestIdentity(original, request)) throw error(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT);
        if (original.getState() != State.SUCCEEDED || original.getFinishedAt() == null
                || !request.getSourceResult().resultSha256().equals(original.getResultSha256())) throw closed();
        verifySource(original, action, original.getFinishedAt());
        var priorAudit = (Map<?, ?>) original.getContextMetadata().get("authorization");
        var currentAudit = (Map<?, ?>) request.getContextMetadata().get("authorization");
        if (!priorAudit.get("scopeSha256").equals(currentAudit.get("scopeSha256"))
                || !Objects.equals(original.getContextMetadata().get("sources"), request.getContextMetadata().get("sources"))) {
            throw error(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT);
        }
        return original;
    }

    private static boolean sameRequestIdentity(RuntimeCallReceiptBO receipt, RuntimeCallReceiptBO request) {
        return receipt.getKind() == request.getKind() && receipt.getOwner().equals(request.getOwner())
                && receipt.getLeaseEpoch() == request.getLeaseEpoch()
                && Objects.equals(receipt.getCapability(), request.getCapability())
                && receipt.getInputSha256().equals(request.getInputSha256())
                && Objects.equals(receipt.getIntentId(), request.getIntentId())
                && Objects.equals(receipt.getSourceResult(), request.getSourceResult());
    }

    private RuntimeCallReceiptBO requireReceipt(long tenantId, String runId, UUID callId) {
        var value = read(tenantId, runId, callId, false);
        if (value == null) throw unavailable();
        return value;
    }

    private RuntimeCallReceiptBO read(long tenantId, String runId, UUID callId, boolean lock) {
        var rows = bindings.jdbc().query(SELECT_CALL + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> decode(rs, tenantId, runId, callId), tenantId, runId, callId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private RuntimeCallReceiptBO decode(ResultSet rs, long tenantId, String runId, UUID callId) throws SQLException {
        var owner = new TrustedAgentActor(tenantId, TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")),
                rs.getString("owner_id"), rs.getString("owner_issuer"));
        var capabilityId = rs.getObject("capability_id", UUID.class);
        var capability = capabilityId == null ? null : new CapabilityVO(capabilityId, rs.getString("capability_binding_sha256"),
                rs.getLong("capability_issue_epoch"), rs.getString("grant_id"), rs.getString("tool_key"), rs.getLong("tool_version"),
                rs.getString("tool_sha256"), rs.getString("backend_binding_sha256"), ToolPolicy.Risk.valueOf(rs.getString("risk")));
        var sourceRevision = rs.getObject("source_action_revision", Long.class);
        var sourceResult = sourceRevision == null ? null : new SourceResultVO(sourceRevision, rs.getString("source_result_sha256"));
        var receipt = new RuntimeCallReceiptBO(tenantId, runId, callId, Kind.valueOf(rs.getString("kind")), owner, rs.getLong("lease_epoch"),
                capability, rs.getString("input_sha256"), codec.readObject(rs.getString("context_metadata")), rs.getObject("intent_id", UUID.class),
                dispatch(rs), sourceResult, State.valueOf(rs.getString("state")), rs.getLong("revision"), instant(rs, "deadline_at"),
                rs.getString("result_sha256"), rs.getObject("input_tokens", Long.class), rs.getObject("output_tokens", Long.class),
                rs.getString("error_code"), instant(rs, "created_at"), instant(rs, "dispatched_at"), instant(rs, "finished_at"));
        receipt.validateContextMetadata();
        return receipt;
    }

    private static DispatchVO dispatch(ResultSet rs) throws SQLException {
        long epoch = rs.getLong("dispatch_epoch");
        return epoch == 0 ? null : new DispatchVO(epoch, rs.getString("dispatch_nonce_sha256"), rs.getString("executor_issuer"),
                rs.getString("executor_id"));
    }

    private static AgentLedgerException closed() {
        return error(AgentErrorCodeEnum.TIANJI_DISPATCH_CLOSED);
    }

    private static AgentLedgerException invalid() {
        return error(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND);
    }

    private static final class LockedExecutionState {
        private final AgentRuntimeBindingStore.LockedRunVO run;
        private final RuntimeBindingBO binding;

        private LockedExecutionState(AgentRuntimeBindingStore.LockedRunVO run, RuntimeBindingBO binding) {
            this.run = run;
            this.binding = binding;
        }
    }

    private static final class LockedCallState {
        private final LockedExecutionState execution;
        private final ActionRowVO action;
        private final RuntimeCallReceiptBO receipt;

        private LockedCallState(LockedExecutionState execution, ActionRowVO action, RuntimeCallReceiptBO receipt) {
            this.execution = execution;
            this.action = action;
            this.receipt = receipt;
        }
    }

    private record CapabilityRowVO(long epoch, String grantId, Instant expiry, Instant deadline) { }
    private record ActionRowVO(String profile, String state, long revision, String requestSha256, String descriptor,
                             String result, Instant expiresAt, String runId, UUID callId, DispatchVO dispatch) { }
}
