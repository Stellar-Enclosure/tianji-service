package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.FixedToolApprovalVerifier.ApprovedObservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.RuntimeCallReceiptBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Current-authorized preparation metadata and internal historical facts; neither is a dispatch permit. */
@Slf4j
public final class ToolActionIntentStore {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolExecutionAuthorization authorization;
    private final ToolActionIntentSql intents;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final AtomicReference<VerifiedStorageVO> verified = new AtomicReference<>();

    public ToolActionIntentStore(DataSource source, AgentLedgerService ledger, ToolExecutionAuthorization authorization) {
        this(source, ledger, authorization, Clock.systemUTC(), System::nanoTime);
    }
    ToolActionIntentStore(DataSource source, AgentLedgerService ledger, ToolExecutionAuthorization authorization, Clock clock, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.authorization = Objects.requireNonNull(authorization, "authorization"); this.clock = Objects.requireNonNull(clock, "clock");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!ledger.usesDataSource(source)) throw new IllegalArgumentException("Tool preparation requires the exact Agent ledger DataSource");
        intents = new ToolActionIntentSql(source, ledger);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); transaction.setReadOnly(false);
    }
    public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    public boolean usesLedger(AgentLedgerService candidate) { return candidate == ledger; }
    public boolean usesAuthorization(ToolExecutionAuthorization candidate) { return candidate == authorization; }
    public synchronized void verifyStorage() {
        verified.set(null);
        var profile = new ToolExecutionStorageVerifier(source).verifyAndReturnProfile();
        if (profile == null) throw unavailable();
        verified.set(new VerifiedStorageVO(profile));
        log.debug("Tool action journal verified for schema profile {}", profile);
    }

    public PreparedActionResult prepare(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                                        ToolDescriptor descriptor, Instant requestedExpiry) {
        Objects.requireNonNull(requestedExpiry, "requestedExpiry");
        var window = authorize(run, lease, action, descriptor);
        return transact(window, () -> projection(intents.prepare(run, lease, action, descriptor, requestedExpiry, window.deadlineAt(), window.storage().profile())));
    }

    public PreparedActionResult prepareRuntime(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                                               ToolDescriptor descriptor, Instant requestedExpiry, Instant callDeadline) {
        Objects.requireNonNull(requestedExpiry, "requested expiry");
        var window = authorize(run, lease, action, descriptor, ToolExecutionAuthorization.Boundary.PREPARE, null,
                Objects.requireNonNull(callDeadline, "call deadline"));
        return transact(window, () -> {
            requireRuntimeAuthorization(run);
            return projection(intents.prepare(run, lease, action, descriptor, requestedExpiry, window.deadlineAt(), window.storage().profile()));
        });
    }

    public Optional<PreparedActionResult> current(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor) {
        var window = authorize(run, lease, action, descriptor);
        return transact(window, () -> intents.current(run, lease, action, descriptor, window.deadlineAt(), window.storage().profile()).map(ToolActionIntentStore::projection));
    }

    /**
     * Commits the exact active call and shared effect before the host may invoke its fixed transport.
     * Approval was obtained by the host outside this transaction and must match the prepared action.
     */
    public RuntimeCallReceiptBO dispatchRuntime(AgentRunAuthorization.Request run, AgentRunLease lease, long runRevision,
                                                ToolActionRequest action, ToolDescriptor descriptor, RuntimeCallReceiptBO prepared,
                                                RuntimeCallReceiptBO.DispatchVO dispatch, ApprovedObservation approval,
                                                Runnable verifyLocalPermit) {
        requireRuntimeCall(run, lease, action, descriptor, prepared, RuntimeCallReceiptBO.State.PREPARED);
        if (dispatch == null || (descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED) != (approval != null)) throw invalid();
        var window = authorize(run, lease, action, descriptor, ToolExecutionAuthorization.Boundary.DISPATCH, prepared);
        return transact(window, () -> {
            requireRuntimeAuthorization(run);
            return runtimeCalls().markDispatched(run.actor(), lease, runRevision, prepared.getCallId(),
                prepared.getRevision(), dispatch, localPermit(window, verifyLocalPermit), actual -> {
                    requireRuntimeCall(run, lease, action, descriptor, actual, RuntimeCallReceiptBO.State.DISPATCHED);
                    requireSameCallCapability(prepared, actual);
                    var locked = lockRuntimeAction(action, descriptor, window.deadlineAt());
                    var executor = new TrustedAgentActor(run.actor().tenantId(), TrustedAgentActor.SubjectType.SERVICE,
                            dispatch.executorId(), dispatch.executorIssuer());
                    var exactDispatch = new ToolActionIntentSql.DispatchVO(actual.getRunId(), null, actual.getCallId(),
                            dispatch.epoch(), dispatch.nonceSha256(), executor);
                    if (approval == null) {
                        intents.beginWrite(locked, exactDispatch, actual.getDispatchedAt(), window.deadlineAt());
                    } else {
                        intents.beginApprovedWrite(locked, approval, exactDispatch, actual.getDispatchedAt(), window.deadlineAt());
                    }
                });
        });
    }

    /** Records the known or unknown result of the original fired dispatch with its technical receipt. */
    public RuntimeCallReceiptBO finishRuntime(AgentRunAuthorization.Request run, AgentRunLease lease, long runRevision,
                                              ToolActionRequest action, ToolDescriptor descriptor, RuntimeCallReceiptBO dispatched,
                                              ToolExecutionOutcome result, Runnable verifyLocalPermit) {
        requireRuntimeCall(run, lease, action, descriptor, dispatched, RuntimeCallReceiptBO.State.DISPATCHED);
        if (dispatched.getDispatch() == null || result == null || result.actualCost() != null) throw invalid();
        var outcome = result.validated(descriptor);
        var code = switch (outcome.status()) {
            case SUCCEEDED -> RuntimeCallReceiptBO.State.SUCCEEDED;
            case REJECTED, FAILED_KNOWN -> RuntimeCallReceiptBO.State.FAILED;
            case UNKNOWN -> RuntimeCallReceiptBO.State.UNKNOWN;
            default -> throw invalid();
        };
        String digest = code == RuntimeCallReceiptBO.State.SUCCEEDED
                ? new ToolExecutionJsonCodec().runtimeWriteResultSha256(action.intentId(), action.requestSha256(), descriptor, outcome) : null;
        var technical = new RuntimeCallReceiptBO.OutcomeVO(code, digest, null, null, outcome.errorCode());
        var window = authorize(run, lease, action, descriptor, ToolExecutionAuthorization.Boundary.DISPATCH, dispatched);
        return transact(window, () -> {
            requireRuntimeAuthorization(run);
            return runtimeCalls().finish(run.actor(), lease, runRevision, dispatched.getCallId(),
                dispatched.getRevision(), dispatched.getDispatch(), technical, localPermit(window, verifyLocalPermit), actual -> {
                    requireRuntimeCall(run, lease, action, descriptor, actual, RuntimeCallReceiptBO.State.DISPATCHED);
                    requireSameCallCapability(dispatched, actual);
                    var row = lockRuntimeAction(action, descriptor, window.deadlineAt());
                    var finishedAt = ledger.databaseNow();
                    if (descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED) {
                        intents.completeApprovedWrite(row, outcome, finishedAt, window.deadlineAt());
                    } else {
                        intents.completeWrite(row, outcome, finishedAt, window.deadlineAt());
                    }
                });
        });
    }

    private void requireRuntimeAuthorization(AgentRunAuthorization.Request expected) {
        var current = ledger.runtimeExecutionMaterial(expected.actor(), new GetAgentRunQuery(expected.runId()));
        if (!expected.equals(current.authorization())) throw unavailable();
    }

    private static void requireSameCallCapability(RuntimeCallReceiptBO expected, RuntimeCallReceiptBO actual) {
        if (!expected.getCapability().equals(actual.getCapability()) || !expected.getDeadlineAt().equals(actual.getDeadlineAt())) {
            throw unavailable();
        }
    }

    private AgentRuntimeCallReceiptStore runtimeCalls() {
        return new AgentRuntimeCallReceiptStore(source, new AgentRuntimeBindingStore(source));
    }

    private ToolActionIntentSql.Row lockRuntimeAction(ToolActionRequest action, ToolDescriptor descriptor, Instant deadline) {
        var row = descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED
                ? intents.lockForRuntimeApproval(action, descriptor, deadline) : intents.lockForRuntimeWrite(action, descriptor, deadline);
        if (row.profile() != ToolExecutionJsonCodec.ActionProfile.RUNTIME_V2) throw unavailable();
        return row;
    }

    private Runnable localPermit(WindowVO window, Runnable current) {
        Objects.requireNonNull(current, "runtime tool permit");
        return () -> {
            check(window);
            current.run();
            check(window);
        };
    }

    private static void requireRuntimeCall(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                                           ToolDescriptor descriptor, RuntimeCallReceiptBO call, RuntimeCallReceiptBO.State state) {
        if (run == null || lease == null || action == null || descriptor == null || call == null || call.getState() != state
                || call.getKind() != RuntimeCallReceiptBO.Kind.TOOL || !run.actor().equals(call.getOwner())
                || !run.actor().equals(action.owner()) || run.actor().tenantId() != call.getTenantId()
                || !run.runId().equals(call.getRunId()) || !lease.runId().equals(call.getRunId())
                || lease.epoch() != call.getLeaseEpoch() || call.getCallId() == null
                || !UUID.fromString(action.intentId()).equals(call.getIntentId())
                || !descriptor.parametersSha256(action.arguments()).equals(call.getInputSha256())) throw invalid();
        var capability = call.getCapability();
        if (capability == null || !descriptor.key().equals(capability.toolKey()) || descriptor.version() != capability.toolVersion()
                || !descriptor.reference().contentSha256().equals(capability.toolSha256())
                || !descriptor.policy().bindingSha256().equals(capability.backendBindingSha256())
                || descriptor.policy().risk() != capability.risk() || descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY) throw invalid();
    }

    /** Internal stored facts only; exposing their result body requires a separate current authorization boundary. */
    Optional<ToolActionIntentSql.Row> historical(ToolActionRequest action, ToolDescriptor descriptor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool history requires its own transaction");
        var storage = requireVerifiedStorage();
        if (action == null || descriptor == null || !descriptor.reference().equals(action.tool())) throw invalid();
        var reconstructed = new ToolActionRequest(action.owner(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
        if (!reconstructed.requestSha256().equals(action.requestSha256()) || !reconstructed.intentId().equals(action.intentId())
                || !descriptor.policy().equals(action.policy())) throw invalid();
        Instant now = clock.instant(); long startedNanos = nanoTime.getAsLong();
        var window = new WindowVO(now.plusSeconds(10), startedNanos + Duration.ofSeconds(10).toNanos(), storage);
        return transact(window, () -> intents.historical(action, descriptor, window.deadlineAt(), window.storage().profile()));
    }

    private WindowVO authorize(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor) {
        return authorize(run, lease, action, descriptor, ToolExecutionAuthorization.Boundary.PREPARE, null);
    }

    private WindowVO authorize(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                               ToolExecutionAuthorization.Boundary boundary, RuntimeCallReceiptBO call) {
        return authorize(run, lease, action, descriptor, boundary, call, null);
    }

    private WindowVO authorize(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                               ToolExecutionAuthorization.Boundary boundary, RuntimeCallReceiptBO call, Instant prepareDeadline) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool preparation requires its own transaction");
        var storage = requireVerifiedStorage();
        if (run == null || lease == null || action == null || descriptor == null || !run.actor().equals(action.owner())
                || !run.runId().equals(lease.runId()) || !descriptor.reference().equals(action.tool())) throw invalid();
        var reconstructed = new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
        if (!reconstructed.requestSha256().equals(action.requestSha256()) || !reconstructed.intentId().equals(action.intentId())) throw invalid();
        Instant now = clock.instant(); Instant deadline = now.plusSeconds(10);
        Instant callDeadline = call == null ? null : call.getDeadlineAt();
        if (call != null && (callDeadline == null || callDeadline.isAfter(run.deadlineAt())
                || call.getCapability().grantId() == null)) throw invalid();
        if (run.deadlineAt().isBefore(deadline)) deadline = run.deadlineAt();
        if (callDeadline != null && callDeadline.isBefore(deadline)) deadline = callDeadline;
        if (prepareDeadline != null && prepareDeadline.isBefore(deadline)) deadline = prepareDeadline;
        if (!deadline.isAfter(now)) throw unavailable();
        if ((boundary != ToolExecutionAuthorization.Boundary.PREPARE || prepareDeadline != null)
                && storage.profile() != ToolExecutionStorageVerifier.SchemaProfile.V17) throw unavailable();
        var window = new WindowVO(deadline, nanoTime.getAsLong() + Duration.between(now, deadline).toNanos(), storage);
        String authorityParameters = call == null && prepareDeadline == null
                ? action.requestSha256() : descriptor.parametersSha256(action.arguments());
        var request = new ToolExecutionAuthorization.Request(run, lease, descriptor.reference(), descriptor.policy().bindingSha256(),
                authorityParameters, action.intentId(), call == null ? deadline : callDeadline);
        ToolExecutionAuthorization.Decision decision;
        try {
            if (call == null) {
                decision = authorization.authorize(request, boundary);
            } else {
                var checked = authorization.check(request, boundary);
                if (checked == null || !request.equals(checked.request()) || checked.boundary() != boundary) throw unavailable();
                decision = checked.decision();
                if (decision == ToolExecutionAuthorization.Decision.ALLOW) {
                    var audit = checked.audit();
                    if (audit == null || !call.getCapability().grantId().equals(audit.grantId())
                            || audit.checkedAt().isBefore(now) || audit.checkedAt().isAfter(clock.instant())
                            || audit.expiresAt().isBefore(callDeadline)) throw unavailable();
                }
            }
        }
        catch (RuntimeException uncertain) { throw unavailable(); }
        if (decision == ToolExecutionAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current tool preparation access was denied");
        if (decision != ToolExecutionAuthorization.Decision.ALLOW) throw unavailable();
        check(window); return window;
    }

    private <T> T transact(WindowVO window, Supplier<T> operation) {
        check(window);
        try {
            T result = Objects.requireNonNull(transaction.execute(status -> {
                check(window); T value = operation.get(); check(window); return value;
            }));
            check(window); return result;
        } catch (DataAccessException | TransactionException failure) { throw unavailable(); }
    }
    private void check(WindowVO window) {
        if (Thread.currentThread().isInterrupted() || verified.get() != window.storage() || !window.deadlineAt().isAfter(clock.instant())
                || window.nanoDeadline() - nanoTime.getAsLong() <= 0) throw unavailable();
    }
    private VerifiedStorageVO requireVerifiedStorage() {
        var storage = verified.get();
        if (storage == null) throw unavailable();
        return storage;
    }
    private static PreparedActionResult projection(ToolActionIntentSql.Row row) {
        return new PreparedActionResult(row.action().intentId(), row.action().requestSha256(), row.action().tool(), row.descriptor().policy().risk(),
                row.state(), row.revision(), row.originRunId(), row.createdAt(), row.expiresAt());
    }

    public record PreparedActionResult(String intentId, String requestSha256, DefinitionResourceRef toolRef, ToolPolicy.Risk risk,
                                       String state, long revision, String originRunId, Instant createdAt, Instant expiresAt) {
        public PreparedActionResult {
            if (intentId == null || requestSha256 == null || !requestSha256.matches("[0-9a-f]{64}") || toolRef == null || risk == null
                    || risk == ToolPolicy.Risk.READ_ONLY || state == null || !ToolActionIntentSql.STATES.contains(state) || revision < 1
                    || originRunId == null || originRunId.isBlank() || createdAt == null || expiresAt == null || !expiresAt.isAfter(createdAt)) throw invalid();
        }
        @Override public String toString() { return "ToolActionIntentStore.PreparedActionResult[redacted]"; }
    }
    private record VerifiedStorageVO(ToolExecutionStorageVerifier.SchemaProfile profile) { }
    private record WindowVO(Instant deadlineAt, long nanoDeadline, VerifiedStorageVO storage) { }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool preparation binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool preparation authority or storage is unavailable"); }
}
