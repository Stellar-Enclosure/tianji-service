package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchAttemptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchWindowVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecordedResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecorderPreparationVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns preparation, dispatch and receipt ordering for one fixed nonbillable read-only decision call. */
public final class ToolInvocationRecorder {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolInvocationStore preparations;
    private final ToolCapabilityStore capabilities;
    private final ToolExecutionAuthorization authorization;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final SecureRandom random = new SecureRandom();
    private final AtomicBoolean verified = new AtomicBoolean();

    public ToolInvocationRecorder(DataSource source, AgentLedgerService ledger, ToolInvocationStore preparations, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization) {
        this(source, ledger, preparations, capabilities, authorization, Clock.systemUTC(), System::nanoTime);
    }
    ToolInvocationRecorder(DataSource source, AgentLedgerService ledger, ToolInvocationStore preparations, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization, Clock clock, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.preparations = Objects.requireNonNull(preparations, "preparations"); this.capabilities = Objects.requireNonNull(capabilities, "capabilities");
        this.authorization = Objects.requireNonNull(authorization, "authorization"); this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!ledger.usesDataSource(source) || !preparations.usesDataSource(source) || !preparations.usesLedger(ledger) || !preparations.usesCapabilities(capabilities)
                || !preparations.usesAuthorization(authorization) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)) throw invalid();
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }
    public boolean usesPreparations(ToolInvocationStore candidate) { return preparations == candidate; }
    public boolean usesCapabilities(ToolCapabilityStore candidate) { return capabilities == candidate; }
    public boolean usesAuthorization(ToolExecutionAuthorization candidate) { return authorization == candidate; }

    public void verifyStorage() {
        verified.set(false);
        try { new ToolExecutionStorageVerifier(source).verify(); verified.set(true); }
        catch (RuntimeException privateFailure) { throw unavailable(); }
    }

    /** No transport is invoked from an ambient transaction, a repeated in-flight call or an unknown receipt. */
    public RecordResult executeReadOnly(ReadOnlyDecisionToolTransport transport, PreparationRequestVO request) {
        if (transport == null || request == null || request.descriptor().policy().risk() != ToolPolicy.Risk.READ_ONLY || request.action() != null || request.intentExpiry() != null
                || request.estimatedCost() == null || request.estimatedCost().compareTo(BigDecimal.ZERO) != 0) throw invalid();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Read-only invocation recording requires its own transactions");
        if (!verified.get()) throw unavailable();
        RecorderPreparationVO expected = null;
        try {
            var prepared = preparations.prepareOrLoadForRecorder(request); expected = prepared.invocation();
            if (metadataOnly(expected)) return metadata(expected, prepared.budget());
            var transportRequest = new ReadOnlyDecisionToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.arguments(), request.invocationId(), expected.dispatchNotAfter());
            var original = expected;
            return transport.withVerifiedCall(transportRequest, (executor, operation) -> dispatchAndRecord(request, original, prepared.budget(), executor, operation));
        } catch (ReadOnlyDecisionToolTransport.Failure rejected) {
            var safe = new ReadOnlyDecisionToolTransport.Failure(rejected.kind()); cleanup(request, expected, cleanupCode(rejected.kind()), safe); throw safe;
        } catch (AgentLedgerException rejected) {
            var safe = new AgentLedgerException(rejected.code(), "Read-only tool invocation could not complete");
            cleanup(request, expected, rejected.code() == AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED ? "AUTHORIZATION_DENIED" : "AUTHORIZATION_UNAVAILABLE", safe); throw safe;
        } catch (RuntimeException privateFailure) {
            var safe = unavailable(); cleanup(request, expected, "AUTHORIZATION_UNAVAILABLE", safe); throw safe;
        }
    }

    private RecordResult dispatchAndRecord(PreparationRequestVO request, RecorderPreparationVO expected, AgentBudgetReservation originalBudget,
                                          ReadOnlyDecisionToolTransport.VerifiedExecutor executor, ReadOnlyDecisionToolTransport.OneShotDecision operation) {
        if (executor == null || operation == null || executor.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(executor.actor().issuerFingerprint())
                || executor.actor().tenantId() != request.binding().run().actor().tenantId()) throw unavailable();
        Instant started = clock.instant(); long startedNanos = nanoTime.getAsLong();
        Instant initialDeadline = earlier(earlier(expected.dispatchNotAfter(), request.binding().run().deadlineAt()), executor.expiresAt());
        var initialWindow = window(started, startedNanos, initialDeadline); check(initialWindow);
        ToolExecutionGrant.EvidenceVO evidence;
        try { evidence = capabilities.current(request.binding(), request.descriptor()).orElseThrow(ToolInvocationRecorder::unavailable); }
        catch (RuntimeException privateEvidence) { throw unavailable(); }
        check(initialWindow);
        if (!request.binding().equals(evidence.reservation().binding())) throw unavailable();
        var window = window(started, startedNanos, earlier(initialDeadline, evidence.expiresAt())); check(window);
        var expectedCheck = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), request.descriptor().reference(), request.descriptor().policy().bindingSha256(),
                request.parametersSha256(), null, window.callDeadlineAt());
        ToolExecutionAuthorization.CheckResult checked;
        try { checked = authorization.check(expectedCheck, ToolExecutionAuthorization.Boundary.DISPATCH); }
        catch (RuntimeException privateAuthority) { throw unavailable(); }
        if (checked == null || !expectedCheck.equals(checked.request()) || checked.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH) throw unavailable();
        if (checked.decision() == ToolExecutionAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current read-only tool access was denied");
        var audit = checked.audit();
        if (checked.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit == null || audit.boundary() != checked.boundary() || audit.decision() != checked.decision()
                || audit.subjectType() != request.binding().run().actor().subjectType() || !evidence.grantId().equals(audit.grantId())
                || audit.checkedAt().isBefore(started) || audit.checkedAt().isAfter(clock.instant()) || audit.expiresAt().isBefore(window.callDeadlineAt()) || audit.expiresAt().isAfter(evidence.expiresAt())) throw unavailable();
        check(window);
        if (!"PREPARED".equals(expected.state())) return replay(request, expected, evidence, checked, executor, window);
        String nonce = nonce();
        var begin = preparations.beginReadOnlyDispatch(request, expected, evidence, checked, executor, ToolInvocationStore.dispatchNonceSha256(nonce), window);
        if (!begin.started()) {
            if (metadataOnly(begin.invocation())) return metadata(begin.invocation(), originalBudget);
            return replay(request, begin.invocation(), evidence, checked, executor, window);
        }
        boolean invoked = false; ToolExecutionOutcome outcome;
        try {
            check(window);
            invoked = true;
            try { outcome = observation(request, operation.evaluate()); }
            catch (ReadOnlyDecisionToolTransport.Failure rejected) { outcome = operationFailure(rejected.kind()); }
            catch (RuntimeException privateOperation) { outcome = outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_UNKNOWN"); }
        } catch (RuntimeException notSent) {
            outcome = outcome(invoked ? ToolExecutionOutcome.Status.UNKNOWN : ToolExecutionOutcome.Status.FAILED_KNOWN, invoked ? "OUTCOME_UNKNOWN" : "DISPATCH_NOT_SENT");
        }
        var result = complete(request, begin.attempt(), nonce, outcome);
        return result(result, invoked, account(request, result.budget()));
    }

    private RecordResult replay(PreparationRequestVO request, RecorderPreparationVO expected, ToolExecutionGrant.EvidenceVO evidence,
                                ToolExecutionAuthorization.CheckResult checked, ReadOnlyDecisionToolTransport.VerifiedExecutor executor, DispatchWindowVO window) {
        var recorded = preparations.readRecorded(request, expected, evidence, checked, executor, window).orElseThrow(ToolInvocationRecorder::unavailable);
        check(window);
        return result(recorded, false, account(request, recorded.budget()));
    }

    private RecordedResultVO complete(PreparationRequestVO request, DispatchAttemptVO attempt, String nonce, ToolExecutionOutcome outcome) {
        // A verified returned response survives caller cancellation; interruption is restored after the independent fact transaction.
        return bookkeeping(() -> preparations.completeReadOnly(request, attempt, nonce, outcome));
    }

    private AccountingState account(PreparationRequestVO request, AgentBudgetReservation budget) {
        if (settledZero(budget)) return AccountingState.SETTLED;
        try {
            var settled = ledger.budgets().settleBudget(request.binding().run().actor(), request.lease(), request.reservationId(), new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
            return settledZero(settled) ? AccountingState.SETTLED : AccountingState.PENDING;
        } catch (RuntimeException pending) { return AccountingState.PENDING; }
    }

    private void cleanup(PreparationRequestVO request, RecorderPreparationVO expected, String errorCode, RuntimeException original) {
        try {
            bookkeeping(() -> {
                var closed = preparations.closePreparedFailure(request, expected, errorCode);
                if (closed.isPresent() && closed.get().dispatchEpoch() == 0 && "FAILED_KNOWN".equals(closed.get().state())
                        && account(request, null) == AccountingState.PENDING) original.addSuppressed(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Read-only tool accounting remains pending"));
                return null;
            });
        } catch (RuntimeException pending) {
            original.addSuppressed(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Read-only tool cleanup could not complete"));
        }
    }

    private <T> T bookkeeping(Supplier<T> work) {
        boolean interrupted = Thread.interrupted();
        try { return work.get(); }
        finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private DispatchWindowVO window(Instant started, long startedNanos, Instant callDeadline) {
        return new DispatchWindowVO(started, startedNanos, earlier(started.plusSeconds(10), callDeadline), callDeadline);
    }
    private void check(DispatchWindowVO window) {
        if (!verified.get()) throw unavailable();
        if (Thread.currentThread().isInterrupted()) throw new ReadOnlyDecisionToolTransport.Failure(ReadOnlyDecisionToolTransport.FailureKind.CANCELLED);
        if (!window.deadlineAt().isAfter(clock.instant()) || window.startedNanos() + Duration.between(window.startedAt(), window.deadlineAt()).toNanos() - nanoTime.getAsLong() <= 0)
            throw new ReadOnlyDecisionToolTransport.Failure(ReadOnlyDecisionToolTransport.FailureKind.DEADLINE_EXCEEDED);
    }

    private String nonce() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return HexFormat.of().formatHex(bytes); }
    private static boolean metadataOnly(RecorderPreparationVO value) { return "DISPATCHING".equals(value.state()) || "UNKNOWN".equals(value.state()); }
    private static RecordResult metadata(RecorderPreparationVO value, AgentBudgetReservation budget) {
        return new RecordResult(value.runId(), value.invocationId(), value.state(), value.revision(), value.dispatchEpoch(), value.dispatchedAt(), value.finishedAt(), null, false,
                "DISPATCHING".equals(value.state()) || !settledZero(budget) ? AccountingState.PENDING : AccountingState.SETTLED);
    }
    private static RecordResult result(RecordedResultVO value, boolean invoked, AccountingState accounting) {
        var metadata = value.invocation();
        return new RecordResult(metadata.runId(), metadata.invocationId(), metadata.state(), metadata.revision(), metadata.dispatchEpoch(), metadata.dispatchedAt(), metadata.finishedAt(), value.outcome(), invoked, accounting);
    }
    private static boolean settledZero(AgentBudgetReservation value) {
        return value != null && value.state() == AgentBudgetReservation.State.SETTLED && Long.valueOf(0).equals(value.actualInputTokens()) && Long.valueOf(0).equals(value.actualOutputTokens())
                && value.actualCost() != null && value.actualCost().compareTo(BigDecimal.ZERO) == 0;
    }
    private static ToolExecutionOutcome observation(PreparationRequestVO request, ReadOnlyDecisionToolTransport.DecisionObservation observation) {
        try {
            if (observation == null) throw invalid();
            if (observation.status() == ReadOnlyDecisionToolTransport.Status.NO_MATCH) return outcome(ToolExecutionOutcome.Status.REJECTED, "DECISION_NO_MATCH");
            return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, observation.output(), null, null, BigDecimal.ZERO).validated(request.descriptor());
        } catch (RuntimeException invalidResponse) { return outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_INVALID_RESPONSE"); }
    }
    private static ToolExecutionOutcome operationFailure(ReadOnlyDecisionToolTransport.FailureKind kind) {
        return switch (kind) {
            case OPERATION_DENIED -> outcome(ToolExecutionOutcome.Status.REJECTED, "DOWNSTREAM_PERMISSION_DENIED");
            case OPERATION_REJECTED -> outcome(ToolExecutionOutcome.Status.REJECTED, "DOWNSTREAM_REJECTED");
            case CANCELLED, DEADLINE_EXCEEDED -> outcome(ToolExecutionOutcome.Status.FAILED_KNOWN, "DISPATCH_NOT_SENT");
            case OPERATION_INVALID_RESPONSE -> outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_INVALID_RESPONSE");
            default -> outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_UNKNOWN");
        };
    }
    private static ToolExecutionOutcome outcome(ToolExecutionOutcome.Status status, String errorCode) { return new ToolExecutionOutcome(status, Map.of(), null, errorCode, BigDecimal.ZERO); }
    private static String cleanupCode(ReadOnlyDecisionToolTransport.FailureKind kind) {
        return switch (kind) {
            case CANCELLED, DEADLINE_EXCEEDED, CREDENTIAL_UNAVAILABLE, RELEASE_UNAVAILABLE, RELEASE_CHANGED -> kind.name();
            default -> "DISPATCH_NOT_SENT";
        };
    }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }

    public enum AccountingState { SETTLED, PENDING }
    public record RecordResult(String runId, UUID invocationId, String state, long revision, long dispatchEpoch, Instant dispatchedAt, Instant finishedAt,
                               ToolExecutionOutcome outcome, boolean operationInvoked, AccountingState accountingState) {
        public RecordResult {
            if (runId == null || runId.isBlank() || invocationId == null || state == null || !Set.of("PREPARED", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state)
                    || revision < 1 || dispatchEpoch < 0 || accountingState == null || dispatchEpoch == 0 && dispatchedAt != null || dispatchEpoch > 0 && dispatchedAt == null
                    || Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state) != (finishedAt != null)
                    || finishedAt != null && dispatchedAt != null && finishedAt.isBefore(dispatchedAt)
                    || operationInvoked && dispatchEpoch == 0 || outcome != null && (!outcome.status().name().equals(state) || outcome.businessReceiptId() != null || outcome.actualCost() == null || outcome.actualCost().compareTo(BigDecimal.ZERO) != 0)) throw invalid();
        }
        @Override public String toString() { return "ToolInvocationRecorder.RecordResult[redacted]"; }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Read-only invocation recorder binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Read-only invocation authority or storage is unavailable"); }
}
