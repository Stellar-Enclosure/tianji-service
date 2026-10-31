package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport.VerifiedExecutor;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Preparation facts only; constructing or replaying a result does not permit dispatch. */
public final class ToolInvocationStore {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore capabilities;
    private final ToolExecutionAuthorization authorization;
    private final ToolInvocationSql invocations;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final AtomicBoolean verified = new AtomicBoolean();
    private static final Set<String> CLEANUP_CODES = Set.of("CANCELLED", "DEADLINE_EXCEEDED", "CREDENTIAL_UNAVAILABLE", "RELEASE_UNAVAILABLE", "RELEASE_CHANGED", "AUTHORIZATION_DENIED", "AUTHORIZATION_UNAVAILABLE", "DISPATCH_NOT_SENT");

    public ToolInvocationStore(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization) {
        this(source, ledger, capabilities, authorization, Clock.systemUTC(), System::nanoTime);
    }
    ToolInvocationStore(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization, Clock clock, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities"); this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)) throw invalid();
        invocations = new ToolInvocationSql(source, ledger, capabilities);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }
    public boolean usesCapabilities(ToolCapabilityStore candidate) { return capabilities == candidate; }
    public boolean usesAuthorization(ToolExecutionAuthorization candidate) { return authorization == candidate; }

    public void verifyStorage() {
        verified.set(false);
        try { new ToolExecutionStorageVerifier(source).verify(); verified.set(true); }
        catch (RuntimeException privateFailure) { throw unavailable(); }
    }

    /** Prepares at most one host invocation and its budget; it neither sends a transport nor grants dispatch authority. */
    public PreparationResult prepare(PreparationRequestVO request) {
        var prepared = prepareInternal(request, ToolInvocationSql.Projection.LEGACY_PREPARATION);
        var row = prepared.invocation();
        var metadata = row == null ? null : row.invocation();
        return new PreparationResult(metadata == null ? null : new PreparedInvocationResult(metadata.runId(), metadata.invocationId(), metadata.reservationId(), metadata.state(), metadata.revision(),
                metadata.capabilityIssueEpoch(), metadata.createdAt(), metadata.dispatchNotAfter(), metadata.finishedAt(), prepared.newlyCreated()), prepared.action());
    }

    private ToolInvocationSql.PreparedRowsVO prepareInternal(PreparationRequestVO request, ToolInvocationSql.Projection projection) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool invocation preparation requires its own transaction");
        if (request == null) throw invalid();
        if (!verified.get()) throw unavailable();
        Instant startedAt = clock.instant(); long startedNanos = nanoTime.getAsLong();
        var initialWindow = window(startedAt, startedNanos, request.binding().run().deadlineAt());
        try {
            check(initialWindow);
            var first = capabilities.current(request.binding(), request.descriptor()).orElseThrow(ToolInvocationStore::unavailable);
            check(initialWindow);
            if (!request.binding().equals(first.reservation().binding())) throw unavailable();
            var window = window(startedAt, startedNanos, earlier(initialWindow.deadlineAt(), first.expiresAt()));
            check(window);
            var expected = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), request.descriptor().reference(),
                    request.descriptor().policy().bindingSha256(), request.parametersSha256(), request.action() == null ? null : request.action().intentId(), window.deadlineAt());
            ToolExecutionAuthorization.CheckResult result;
            try { result = authorization.check(expected, ToolExecutionAuthorization.Boundary.PREPARE); }
            catch (RuntimeException privateFailure) { throw unavailable(); }
            if (result == null || !expected.equals(result.request()) || result.boundary() != ToolExecutionAuthorization.Boundary.PREPARE) throw unavailable();
            if (result.decision() == ToolExecutionAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current tool invocation access was denied");
            var audit = result.audit();
            if (result.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit == null
                    || audit.boundary() != result.boundary() || audit.decision() != result.decision() || audit.subjectType() != expected.run().actor().subjectType()
                    || !first.grantId().equals(audit.grantId()) || audit.checkedAt().isBefore(startedAt) || audit.checkedAt().isAfter(clock.instant())
                    || audit.expiresAt().isBefore(window.deadlineAt()) || audit.expiresAt().isAfter(first.expiresAt())) throw unavailable();
            check(window);
            var prepared = Objects.requireNonNull(transaction.execute(status -> {
                check(window);
                var value = invocations.prepare(request, first, audit, window.deadlineAt(), projection);
                check(window); return value;
            }));
            if (projection == ToolInvocationSql.Projection.LEGACY_PREPARATION) check(window);
            return prepared;
        } catch (AgentLedgerException failure) {
            throw new AgentLedgerException(failure.code(), "Tool invocation preparation is unavailable or changed");
        } catch (RuntimeException privateFailure) { throw unavailable(); }
    }

    RecorderPreparationResultVO prepareOrLoadForRecorder(PreparationRequestVO request) {
        readOnly(request);
        var rows = prepareInternal(request, ToolInvocationSql.Projection.RECORDER_METADATA);
        if (rows.invocation() == null) throw unavailable();
        return new RecorderPreparationResultVO(rows.invocation().invocation(), rows.invocation().budget());
    }
    DispatchBeginResultVO beginReadOnlyDispatch(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                               ToolExecutionAuthorization.CheckResult checked, VerifiedExecutor executor, String nonceSha256, DispatchWindowVO window) {
        readOnly(request); if (!hash(nonceSha256)) throw invalid();
        validateDispatch(request, expected, evidence, checked, executor, window);
        return transact(() -> checkDispatch(window), () -> invocations.beginReadOnlyDispatch(request, expected, evidence, checked.audit(), executor, nonceSha256, window.callDeadlineAt(), window.deadlineAt()));
    }
    Optional<RecordedResultVO> readRecorded(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                          ToolExecutionAuthorization.CheckResult checked, VerifiedExecutor executor, DispatchWindowVO window) {
        readOnly(request); validateDispatch(request, expected, evidence, checked, executor, window);
        var result = transact(() -> checkDispatch(window), () -> invocations.readRecorded(request, expected, evidence, checked.audit(), executor, window.callDeadlineAt(), window.deadlineAt()));
        checkDispatch(window); return result;
    }
    RecordedResultVO completeReadOnly(PreparationRequestVO request, DispatchAttemptVO attempt, String plainNonceHex, ToolExecutionOutcome outcome) {
        readOnly(request); if (attempt == null || outcome == null) throw invalid();
        if (!dispatchNonceSha256(plainNonceHex).equals(attempt.dispatchNonceSha256())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Dispatch completion proof changed");
        var window = factWindow();
        return transact(() -> checkFact(window), () -> invocations.completeReadOnly(request, attempt, outcome, window.deadlineAt()));
    }
    Optional<RecorderPreparationVO> closePreparedFailure(PreparationRequestVO request, RecorderPreparationVO expected, String safeCode) {
        readOnly(request); if (safeCode == null || !CLEANUP_CODES.contains(safeCode)) throw invalid();
        var window = factWindow();
        return transact(() -> checkFact(window), () -> invocations.closePreparedFailure(request, expected, safeCode, window.deadlineAt()));
    }
    static String dispatchNonceSha256(String plainNonceHex) {
        if (!hash(plainNonceHex)) throw invalid();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(HexFormat.of().parseHex(plainNonceHex))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("Dispatch completion proof is unavailable"); }
    }

    private void validateDispatch(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                  ToolExecutionAuthorization.CheckResult checked, VerifiedExecutor executor, DispatchWindowVO window) {
        checkDispatch(window);
        if (expected == null || evidence == null || checked == null || executor == null || !request.binding().equals(evidence.reservation().binding())
                || !request.binding().run().runId().equals(expected.runId()) || !request.invocationId().equals(expected.invocationId()) || !request.reservationId().equals(expected.reservationId())
                || executor.actor().tenantId() != request.binding().run().actor().tenantId()) throw unavailable();
        Instant deadline = earlier(expected.dispatchNotAfter(), earlier(request.binding().run().deadlineAt(), earlier(evidence.expiresAt(), executor.expiresAt())));
        var original = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), request.descriptor().reference(), request.descriptor().policy().bindingSha256(), request.parametersSha256(), null, deadline);
        if (!deadline.equals(window.callDeadlineAt()) || !original.equals(checked.request()) || checked.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH) throw unavailable();
        if (checked.decision() == ToolExecutionAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current tool dispatch access was denied");
        var audit = checked.audit();
        if (checked.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit == null || audit.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH
                || audit.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit.subjectType() != request.binding().run().actor().subjectType()
                || !evidence.grantId().equals(audit.grantId()) || audit.checkedAt().isBefore(window.startedAt()) || audit.checkedAt().isAfter(clock.instant())
                || audit.expiresAt().isBefore(deadline) || audit.expiresAt().isAfter(evidence.expiresAt())) throw unavailable();
        checkDispatch(window);
    }

    private <T> T transact(Runnable guard, Supplier<T> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool invocation operations require their own transaction");
        guard.run();
        try {
            return Objects.requireNonNull(transaction.execute(status -> { guard.run(); T value = operation.get(); guard.run(); return value; }));
        } catch (AgentLedgerException failure) { throw new AgentLedgerException(failure.code(), "Tool invocation operation is unavailable or changed"); }
        catch (RuntimeException privateFailure) { throw unavailable(); }
    }
    private void checkDispatch(DispatchWindowVO window) {
        if (window == null || !verified.get() || Thread.currentThread().isInterrupted() || !window.deadlineAt().isAfter(clock.instant())
                || window.startedNanos() + Duration.between(window.startedAt(), window.deadlineAt()).toNanos() - nanoTime.getAsLong() <= 0) throw unavailable();
    }
    private WindowVO factWindow() { Instant started = clock.instant(); return new WindowVO(started.plusSeconds(10), nanoTime.getAsLong() + Duration.ofSeconds(10).toNanos()); }
    private void checkFact(WindowVO window) {
        if (!verified.get() || !window.deadlineAt().isAfter(clock.instant()) || window.nanoDeadline() - nanoTime.getAsLong() <= 0) throw unavailable();
    }
    private static void readOnly(PreparationRequestVO request) {
        if (request == null || request.descriptor().policy().risk() != ToolPolicy.Risk.READ_ONLY || request.action() != null
                || request.estimatedCost() == null || request.estimatedCost().signum() != 0) throw invalid();
    }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    record RecorderPreparationVO(String runId, UUID invocationId, String reservationId, String state, long revision, long dispatchEpoch,
                                 long capabilityIssueEpoch, Instant createdAt, Instant dispatchNotAfter, Instant dispatchedAt, Instant finishedAt) {
        RecorderPreparationVO {
            if (runId == null || runId.isBlank() || !runId.equals(runId.trim()) || runId.length() > 64 || invocationId == null || !("tool:" + invocationId).equals(reservationId)
                    || state == null || !Set.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state)
                    || revision < 1 || capabilityIssueEpoch < 1 || dispatchEpoch < 0 || createdAt == null || dispatchNotAfter == null || !dispatchNotAfter.isAfter(createdAt)
                    || (dispatchEpoch == 0) != (dispatchedAt == null) || dispatchedAt != null && (dispatchedAt.isBefore(createdAt) || !dispatchedAt.isBefore(dispatchNotAfter))
                    || finishedAt != null && (finishedAt.isBefore(createdAt) || dispatchedAt != null && finishedAt.isBefore(dispatchedAt))
                    || Set.of("PREPARED", "AWAITING_APPROVAL").contains(state) && dispatchEpoch != 0
                    || Set.of("DISPATCHING", "SUCCEEDED", "UNKNOWN").contains(state) && dispatchEpoch == 0
                    || Set.of("PREPARED", "DISPATCHING").contains(state) != (finishedAt == null)) throw invalid();
        }
        @Override public String toString() { return "ToolInvocationStore.RecorderPreparationVO[redacted]"; }
    }
    record RecorderPreparationResultVO(RecorderPreparationVO invocation, AgentBudgetReservation budget) {
        RecorderPreparationResultVO { if (invocation == null || budget == null) throw invalid(); }
        @Override public String toString() { return "ToolInvocationStore.RecorderPreparationResultVO[redacted]"; }
    }
    record DispatchWindowVO(Instant startedAt, long startedNanos, Instant deadlineAt, Instant callDeadlineAt) {
        DispatchWindowVO {
            if (startedAt == null || deadlineAt == null || callDeadlineAt == null || !deadlineAt.isAfter(startedAt)
                    || !deadlineAt.equals(earlier(startedAt.plusSeconds(10), callDeadlineAt))) throw invalid();
        }
        @Override public String toString() { return "ToolInvocationStore.DispatchWindowVO[redacted]"; }
    }
    record DispatchAttemptVO(String runId, UUID invocationId, long dispatchEpoch, long capabilityIssueEpoch, String dispatchNonceSha256,
                             TrustedAgentActor executor, ToolExecutionAuthorization.AuditVO authorizationAudit, Instant dispatchedAt, Instant callDeadlineAt) {
        DispatchAttemptVO {
            if (runId == null || runId.isBlank() || invocationId == null || dispatchEpoch < 1 || capabilityIssueEpoch < 1 || !hash(dispatchNonceSha256)
                    || executor == null || executor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(executor.issuerFingerprint())
                    || authorizationAudit == null || authorizationAudit.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH || authorizationAudit.decision() != ToolExecutionAuthorization.Decision.ALLOW
                    || dispatchedAt == null || callDeadlineAt == null || !callDeadlineAt.isAfter(dispatchedAt) || authorizationAudit.expiresAt().isBefore(callDeadlineAt)) throw invalid();
        }
        @Override public String toString() { return "ToolInvocationStore.DispatchAttemptVO[redacted]"; }
    }
    record DispatchBeginResultVO(RecorderPreparationVO invocation, DispatchAttemptVO attempt) {
        DispatchBeginResultVO { if (invocation == null || attempt != null && (!invocation.runId().equals(attempt.runId()) || !invocation.invocationId().equals(attempt.invocationId())
                || !"DISPATCHING".equals(invocation.state()) || invocation.dispatchEpoch() != attempt.dispatchEpoch() || invocation.capabilityIssueEpoch() != attempt.capabilityIssueEpoch())) throw invalid(); }
        boolean started() { return attempt != null; }
        @Override public String toString() { return "ToolInvocationStore.DispatchBeginResultVO[redacted]"; }
    }
    record RecordedResultVO(RecorderPreparationVO invocation, ToolExecutionOutcome outcome, AgentBudgetReservation budget) {
        RecordedResultVO { if (invocation == null || outcome == null || budget == null || !invocation.state().equals(outcome.status().name())) throw invalid(); }
        @Override public String toString() { return "ToolInvocationStore.RecordedResultVO[redacted]"; }
    }

    private WindowVO window(Instant startedAt, long startedNanos, Instant bound) {
        Instant deadline = earlier(startedAt.plusSeconds(10), bound);
        if (!deadline.isAfter(startedAt)) throw unavailable();
        return new WindowVO(deadline, startedNanos + Duration.between(startedAt, deadline).toNanos());
    }
    private void check(WindowVO window) {
        if (!verified.get() || Thread.currentThread().isInterrupted() || !window.deadlineAt().isAfter(clock.instant())
                || window.nanoDeadline() - nanoTime.getAsLong() <= 0) throw unavailable();
    }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    private record WindowVO(Instant deadlineAt, long nanoDeadline) { }

    /** Trusted-host input; no field accepts a model tool-call ID, caller-supplied digest or dispatch proof. */
    public record PreparationRequestVO(ToolExecutionGrant.BindingVO binding, AgentRunLease lease, ToolDescriptor descriptor, Map<String, Object> arguments,
                                       ToolActionRequest action, UUID invocationId, BigDecimal estimatedCost, Instant intentExpiry) {
        public PreparationRequestVO {
            try {
                if (binding == null || lease == null || descriptor == null || invocationId == null || !binding.run().runId().equals(lease.runId())
                        || !binding.tool().equals(descriptor.reference()) || !binding.backendBindingSha256().equals(descriptor.policy().bindingSha256())) throw invalid();
                arguments = descriptor.input().values(arguments);
                if (descriptor.policy().risk() == ToolPolicy.Risk.READ_ONLY) {
                    if (action != null || intentExpiry != null) throw invalid();
                } else {
                    if (action == null || intentExpiry == null || !binding.run().actor().equals(action.owner()) || !descriptor.policy().equals(action.policy())
                            || !descriptor.reference().equals(action.tool()) || !arguments.equals(action.arguments())) throw invalid();
                    var reconstructed = new ToolActionRequest(binding.run().actor(), descriptor, action.businessKey(), action.businessVersion(), arguments);
                    if (!reconstructed.intentId().equals(action.intentId()) || !reconstructed.requestSha256().equals(action.requestSha256())) throw invalid();
                }
                new AgentBudgetRequest("tool:" + invocationId, AgentBudgetRequest.Kind.TOOL, descriptor.versionKey(), 0, 0, estimatedCost);
            } catch (RuntimeException malformed) { throw invalid(); }
        }
        public String reservationId() { return "tool:" + invocationId; }
        public String parametersSha256() {
            return descriptor.parametersSha256(arguments);
        }
        @Override public String toString() { return "ToolInvocationStore.PreparationRequestVO[redacted]"; }
    }

    /** The original immutable preparation projection. newlyCreated reports insertion, never execution permission. */
    public record PreparedInvocationResult(String runId, UUID invocationId, String reservationId, String state, long revision, long capabilityIssueEpoch,
                                           Instant createdAt, Instant dispatchNotAfter, Instant finishedAt, boolean newlyCreated) {
        public PreparedInvocationResult {
            if (runId == null || runId.isBlank() || !runId.equals(runId.trim()) || runId.length() > 64 || invocationId == null
                    || !("tool:" + invocationId).equals(reservationId) || state == null || !Set.of("PREPARED", "AWAITING_APPROVAL").contains(state) || revision < 1 || capabilityIssueEpoch < 1
                    || createdAt == null || dispatchNotAfter == null || !dispatchNotAfter.isAfter(createdAt)
                    || "PREPARED".equals(state) && finishedAt != null || "AWAITING_APPROVAL".equals(state) && (finishedAt == null || finishedAt.isBefore(createdAt))) throw invalid();
        }
        @Override public String toString() { return "ToolInvocationStore.PreparedInvocationResult[redacted]"; }
    }

    public record PreparationResult(PreparedInvocationResult invocation, ToolActionIntentStore.PreparedActionResult action) {
        public PreparationResult { if (invocation == null && action == null) throw invalid(); }
        @Override public String toString() { return "ToolInvocationStore.PreparationResult[redacted]"; }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool invocation preparation binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool invocation authority or storage is unavailable"); }
}
