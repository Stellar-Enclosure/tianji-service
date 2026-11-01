package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ReadOnlyDecisionToolTransport;
import top.egon.cola.platform.tianji.agent.application.tool.ToolCommittedFactObserver;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchAttemptVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.DispatchWindowVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecordedResultVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.RecorderPreparationVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Owns preparation, dispatch and receipt ordering for fixed nonbillable decision and BPMN START calls. */
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
    private static final ToolCommittedFactObserver NOOP_COMMITTED = status -> { };

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

    public IdempotentStartResultVO executeIdempotentStart(FixedBpmnStartToolTransport transport, PreparationRequestVO request) {
        return executeIdempotentStart(transport, request, NOOP_COMMITTED);
    }

    public IdempotentStartResultVO executeIdempotentStart(FixedBpmnStartToolTransport transport, PreparationRequestVO request, ToolCommittedFactObserver observer) {
        if (observer == null) throw invalidStart();
        if (transport == null || request == null || request.descriptor().policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE
                || request.action() == null || request.intentExpiry() == null || request.estimatedCost() == null || request.estimatedCost().signum() != 0
                || !ProcessClient.START_RESOURCE_KEY.equals(request.descriptor().policy().executionResourceKey())
                || !request.descriptor().output().fields().equals(Map.of("instanceId", ToolSchema.ValueType.STRING))
                || !request.descriptor().output().required().equals(Set.of("instanceId"))) throw invalidStart();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Fixed START recording requires its own transactions");
        if (!verified.get()) throw unavailableStart();
        var observerFailure = new AtomicReference<AgentLedgerException>();
        ToolCommittedFactObserver wrappedObserver = status -> {
            try { observer.onCommitted(status); }
            catch (RuntimeException privateObserver) {
                var safe = new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Committed tool fact observation could not complete");
                observerFailure.compareAndSet(null, safe); throw observerFailure.get();
            }
        };
        RecorderPreparationVO expected = null;
        try {
            var prepared = preparations.prepareOrLoadIdempotentWrite(request); expected = prepared.invocation();
            var snapshot = prepared.action();
            if (expected == null) return startResult(snapshot, null);
            if (!"PREPARED".equals(expected.state())) return startResult(snapshot, startMetadata(expected, prepared.budget()));
            if (!"PREPARED".equals(snapshot.state())) {
                var original = expected;
                var closed = bookkeeping(() -> preparations.closePreparedWriteFailure(request, original, outcome(ToolExecutionOutcome.Status.FAILED_KNOWN, "ACTION_NOT_DISPATCHABLE")))
                        .orElseThrow(ToolInvocationRecorder::unavailableStart);
                return startResult(snapshot, startMetadata(closed, prepared.budget()));
            }
            var transportRequest = new FixedBpmnStartToolTransport.Request(request.binding().run(), request.lease(), request.descriptor(), request.action(), request.invocationId(), expected.dispatchNotAfter());
            var original = expected;
            var own = transport.withVerifiedCall(transportRequest,
                    (executor, operation) -> dispatchAndRecordStart(request, original, prepared.budget(), executor, operation, wrappedObserver));
            if (observerFailure.get() != null) throw observerFailure.get();
            return startResult(snapshot, Objects.requireNonNull(own));
        } catch (FixedBpmnStartToolTransport.Failure rejected) {
            if (observerFailure.get() != null) throw observerFailure.get();
            var safe = new FixedBpmnStartToolTransport.Failure(rejected.kind()); cleanupStart(request, expected, startCleanupCode(rejected.kind()), safe); throw safe;
        } catch (AgentLedgerException rejected) {
            if (observerFailure.get() != null) throw observerFailure.get();
            var safe = new AgentLedgerException(rejected.code(), "Fixed START invocation could not complete");
            cleanupStart(request, expected, rejected.code() == AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED ? "AUTHORIZATION_DENIED" : "AUTHORIZATION_UNAVAILABLE", safe); throw safe;
        } catch (RuntimeException privateFailure) {
            if (observerFailure.get() != null) throw observerFailure.get();
            var safe = unavailableStart(); cleanupStart(request, expected, "AUTHORIZATION_UNAVAILABLE", safe); throw safe;
        }
    }

    private WriteAttemptResultVO dispatchAndRecordStart(PreparationRequestVO request, RecorderPreparationVO expected, AgentBudgetReservation observedBudget,
                                                       ReadOnlyDecisionToolTransport.VerifiedExecutor executor, FixedBpmnStartToolTransport.OneShotStart operation, ToolCommittedFactObserver observer) {
        if (executor == null || operation == null || executor.actor().subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(executor.actor().issuerFingerprint())
                || executor.actor().tenantId() != request.binding().run().actor().tenantId()) throw unavailableStart();
        Instant started = clock.instant(); long startedNanos = nanoTime.getAsLong();
        Instant initialDeadline = earlier(earlier(expected.dispatchNotAfter(), request.binding().run().deadlineAt()), executor.expiresAt());
        if (!initialDeadline.isAfter(started)) throw new FixedBpmnStartToolTransport.Failure(FixedBpmnStartToolTransport.FailureKind.DEADLINE_EXCEEDED);
        var initialWindow = window(started, startedNanos, initialDeadline); checkStart(initialWindow);
        ToolExecutionGrant.EvidenceVO evidence;
        try { evidence = capabilities.current(request.binding(), request.descriptor()).orElseThrow(ToolInvocationRecorder::unavailableStart); }
        catch (RuntimeException privateEvidence) { throw unavailableStart(); }
        checkStart(initialWindow);
        if (!request.binding().equals(evidence.reservation().binding())) throw unavailableStart();
        var window = window(started, startedNanos, earlier(initialDeadline, evidence.expiresAt())); checkStart(window);
        var expectedCheck = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), request.descriptor().reference(), request.descriptor().policy().bindingSha256(),
                request.parametersSha256(), request.action().intentId(), window.callDeadlineAt());
        ToolExecutionAuthorization.CheckResult checked;
        try { checked = authorization.check(expectedCheck, ToolExecutionAuthorization.Boundary.DISPATCH); }
        catch (RuntimeException privateAuthority) { throw unavailableStart(); }
        if (checked == null || !expectedCheck.equals(checked.request()) || checked.boundary() != ToolExecutionAuthorization.Boundary.DISPATCH) throw unavailableStart();
        if (checked.decision() == ToolExecutionAuthorization.Decision.DENY) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current fixed START access was denied");
        var audit = checked.audit();
        if (checked.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit == null || audit.boundary() != checked.boundary() || audit.decision() != checked.decision()
                || audit.subjectType() != request.binding().run().actor().subjectType() || !evidence.grantId().equals(audit.grantId())
                || audit.checkedAt().isBefore(started) || audit.checkedAt().isAfter(clock.instant()) || audit.expiresAt().isBefore(window.callDeadlineAt()) || audit.expiresAt().isAfter(evidence.expiresAt())) throw unavailableStart();
        checkStart(window);
        String nonce = nonce();
        var begin = preparations.beginIdempotentWriteDispatch(request, expected, evidence, checked, executor, ToolInvocationStore.dispatchNonceSha256(nonce),
                outcome(ToolExecutionOutcome.Status.FAILED_KNOWN, "ACTION_NOT_DISPATCHABLE"), window);
        // Metadata cannot prove that this caller closed a loser; another caller may have recorded a different fee.
        if (!begin.started()) return startMetadata(begin.invocation(), observedBudget);
        boolean invoked = false; ToolExecutionOutcome observed;
        try {
            checkStart(window); invoked = true;
            try { observed = startObservation(request, operation.start()); }
            catch (FixedBpmnStartToolTransport.Failure rejected) { observed = startOperationFailure(rejected.kind()); }
            catch (RuntimeException privateOperation) { observed = outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_UNKNOWN"); }
        } catch (RuntimeException notSent) {
            observed = outcome(invoked ? ToolExecutionOutcome.Status.UNKNOWN : ToolExecutionOutcome.Status.FAILED_KNOWN, invoked ? "OUTCOME_UNKNOWN" : "DISPATCH_NOT_SENT");
        }
        var fact = observed;
        var recorded = bookkeeping(() -> preparations.completeIdempotentWrite(request, begin.attempt(), nonce, fact));
        observer.onCommitted(recorded.outcome().status());
        var metadata = recorded.invocation();
        return new WriteAttemptResultVO(metadata.runId(), metadata.invocationId(), metadata.state(), metadata.revision(), metadata.dispatchEpoch(), metadata.dispatchedAt(), metadata.finishedAt(),
                recorded.outcome(), invoked, account(request, recorded.budget()));
    }

    private void cleanupStart(PreparationRequestVO request, RecorderPreparationVO expected, String safeCode, RuntimeException original) {
        try {
            bookkeeping(() -> {
                var closed = preparations.closePreparedWriteFailure(request, expected, outcome(ToolExecutionOutcome.Status.FAILED_KNOWN, safeCode));
                if (closed.isPresent() && closed.get().dispatchEpoch() == 0 && "FAILED_KNOWN".equals(closed.get().state()))
                    original.addSuppressed(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Fixed START accounting was not confirmed"));
                return null;
            });
        } catch (RuntimeException pending) {
            original.addSuppressed(new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Fixed START cleanup could not complete"));
        }
    }

    private void checkStart(DispatchWindowVO window) {
        if (!verified.get()) throw unavailableStart();
        if (Thread.currentThread().isInterrupted()) throw new FixedBpmnStartToolTransport.Failure(FixedBpmnStartToolTransport.FailureKind.CANCELLED);
        if (!window.deadlineAt().isAfter(clock.instant()) || window.startedNanos() + Duration.between(window.startedAt(), window.deadlineAt()).toNanos() - nanoTime.getAsLong() <= 0)
            throw new FixedBpmnStartToolTransport.Failure(FixedBpmnStartToolTransport.FailureKind.DEADLINE_EXCEEDED);
    }

    private static IdempotentStartResultVO startResult(ToolActionIntentStore.PreparedActionResult snapshot, WriteAttemptResultVO own) {
        return new IdempotentStartResultVO(snapshot.intentId(), snapshot.state(), snapshot.revision(), own);
    }
    private static WriteAttemptResultVO startMetadata(RecorderPreparationVO value, AgentBudgetReservation observedBudget) {
        return new WriteAttemptResultVO(value.runId(), value.invocationId(), value.state(), value.revision(), value.dispatchEpoch(), value.dispatchedAt(), value.finishedAt(), null, false,
                settledZero(observedBudget) ? AccountingState.SETTLED : AccountingState.PENDING);
    }
    private static ToolExecutionOutcome startObservation(PreparationRequestVO request, FixedBpmnStartToolTransport.StartObservation observation) {
        try {
            if (observation == null) throw invalidStart();
            return new ToolExecutionOutcome(ToolExecutionOutcome.Status.SUCCEEDED, observation.output(), observation.hostReceiptReference(), null, BigDecimal.ZERO).validated(request.descriptor());
        } catch (RuntimeException invalidResponse) { return outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_INVALID_RESPONSE"); }
    }
    private static ToolExecutionOutcome startOperationFailure(FixedBpmnStartToolTransport.FailureKind kind) {
        return switch (kind) {
            case OPERATION_DENIED -> outcome(ToolExecutionOutcome.Status.REJECTED, "DOWNSTREAM_PERMISSION_DENIED");
            case OPERATION_NOT_SENT, CANCELLED, DEADLINE_EXCEEDED -> outcome(ToolExecutionOutcome.Status.FAILED_KNOWN, "DISPATCH_NOT_SENT");
            case OPERATION_INVALID_RESPONSE -> outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_INVALID_RESPONSE");
            default -> outcome(ToolExecutionOutcome.Status.UNKNOWN, "OUTCOME_UNKNOWN");
        };
    }
    private static String startCleanupCode(FixedBpmnStartToolTransport.FailureKind kind) {
        return switch (kind) {
            case CANCELLED, DEADLINE_EXCEEDED, CREDENTIAL_UNAVAILABLE, RELEASE_UNAVAILABLE, RELEASE_CHANGED -> kind.name();
            default -> "DISPATCH_NOT_SENT";
        };
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

    public record IdempotentStartResultVO(String actionIntentId, String actionStateAtPreparation, long actionRevisionAtPreparation, WriteAttemptResultVO ownAttempt) {
        public IdempotentStartResultVO {
            try {
                if (actionIntentId == null || !UUID.fromString(actionIntentId).toString().equals(actionIntentId) || actionStateAtPreparation == null
                        || !Set.of("PREPARED", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED").contains(actionStateAtPreparation)
                        || actionRevisionAtPreparation < 1) throw invalidStart();
            } catch (RuntimeException invalidValue) { throw invalidStart(); }
        }
        @Override public String toString() { return "ToolInvocationRecorder.IdempotentStartResultVO[redacted]"; }
    }
    public record WriteAttemptResultVO(String runId, UUID invocationId, String state, long revision, long dispatchEpoch, Instant dispatchedAt, Instant finishedAt,
                                       ToolExecutionOutcome outcome, boolean operationInvokedThisCall, AccountingState accountingState) {
        public WriteAttemptResultVO {
            if (runId == null || runId.isBlank() || !runId.equals(runId.trim()) || runId.length() > 64 || invocationId == null || state == null
                    || !Set.of("PREPARED", "DISPATCHING", "SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state)
                    || revision < 1 || dispatchEpoch < 0 || accountingState == null || (dispatchEpoch == 0) != (dispatchedAt == null)
                    || Set.of("SUCCEEDED", "REJECTED", "FAILED_KNOWN", "UNKNOWN").contains(state) != (finishedAt != null)
                    || Set.of("DISPATCHING", "SUCCEEDED", "UNKNOWN").contains(state) && dispatchEpoch == 0
                    || "PREPARED".equals(state) && dispatchEpoch != 0 || operationInvokedThisCall && dispatchEpoch == 0
                    || finishedAt != null && dispatchedAt != null && finishedAt.isBefore(dispatchedAt)
                    || outcome != null && (dispatchEpoch == 0 || !state.equals(outcome.status().name()) || outcome.actualCost() == null || outcome.actualCost().signum() != 0)) throw invalidStart();
            if (outcome != null && outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED) {
                if (!outcome.output().keySet().equals(Set.of("instanceId")) || !(outcome.output().get("instanceId") instanceof String instanceId)) throw invalidStart();
                var normalized = new FixedBpmnStartToolTransport.StartObservation(outcome.businessReceiptId(), instanceId);
                if (!normalized.output().equals(outcome.output())) throw invalidStart();
            }
        }
        @Override public String toString() { return "ToolInvocationRecorder.WriteAttemptResultVO[redacted]"; }
    }

    private static IllegalArgumentException invalidStart() { return new IllegalArgumentException("Fixed START invocation recorder binding is invalid"); }
    private static AgentLedgerException unavailableStart() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Fixed START invocation authority or storage is unavailable"); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Read-only invocation recorder binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Read-only invocation authority or storage is unavailable"); }
}
