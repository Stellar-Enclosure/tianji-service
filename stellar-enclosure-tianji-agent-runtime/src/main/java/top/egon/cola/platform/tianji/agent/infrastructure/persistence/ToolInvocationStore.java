package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

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
                var value = invocations.prepare(request, first, audit, window.deadlineAt());
                check(window); return value;
            }));
            check(window); return prepared;
        } catch (AgentLedgerException failure) {
            throw new AgentLedgerException(failure.code(), "Tool invocation preparation is unavailable or changed");
        } catch (RuntimeException privateFailure) { throw unavailable(); }
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
