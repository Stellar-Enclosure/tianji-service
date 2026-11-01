package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.application.tool.FixedBpmnStartToolTransport;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolSchema;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessClient;

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

/** Current-authorized access to an original known START result; stored access facts are not authority. */
public final class ToolResultAccessStore {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore capabilities;
    private final ToolExecutionAuthorization authorization;
    private final ToolResultAccessSql accesses;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final AtomicBoolean verified = new AtomicBoolean();

    public ToolResultAccessStore(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization) {
        this(source, ledger, capabilities, authorization, Clock.systemUTC(), System::nanoTime);
    }
    ToolResultAccessStore(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities, ToolExecutionAuthorization authorization, Clock clock, LongSupplier nanoTime) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities"); this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.clock = Objects.requireNonNull(clock, "clock"); this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (!ledger.usesDataSource(source) || !capabilities.usesDataSource(source) || !capabilities.usesLedger(ledger)) throw invalid();
        accesses = new ToolResultAccessSql(source, ledger, capabilities);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); transaction.setReadOnly(false);
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }
    public boolean usesCapabilities(ToolCapabilityStore candidate) { return capabilities == candidate; }
    public boolean usesAuthorization(ToolExecutionAuthorization candidate) { return authorization == candidate; }
    public void verifyStorage() {
        verified.set(false);
        try { new ToolExecutionStorageVerifier(source).verifyResultAccess(); verified.set(true); }
        catch (RuntimeException privateFailure) { throw unavailable(); }
    }

    RecordedAccessVO recordKnownStartResultAccess(StartResultAccessRequestVO request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool result access requires its own transaction");
        if (request == null) throw invalid();
        Instant started = clock.instant(); long startedNanos = nanoTime.getAsLong();
        var initial = window(started, startedNanos, earlier(started.plusSeconds(10), request.binding().run().deadlineAt()));
        checkDisclosureWindow(initial);
        try {
            var material = ledger.executionMaterial(request.binding().run().actor(), new GetAgentRunQuery(request.binding().run().runId()));
            if (material == null || !request.binding().run().equals(material.authorization()) || material.snapshot() == null
                    || !request.binding().run().runId().equals(material.run().runId())) throw unavailable();
            var policy = material.snapshot().effectivePolicy();
            if (!material.snapshot().definition().dependencies().tools().contains(request.descriptor().reference())
                    || !policy.allowedToolVersions().contains(request.descriptor().versionKey()) || policy.maxToolCalls() < 1) throw denied();
            checkDisclosureWindow(initial);
            var first = capabilities.current(request.binding(), request.descriptor()).orElseThrow(ToolResultAccessStore::unavailable);
            if (!request.binding().equals(first.reservation().binding())) throw unavailable();
            var window = window(started, startedNanos, earlier(initial.deadlineAt(), earlier(started.plus(policy.toolTimeout()), first.expiresAt())));
            checkDisclosureWindow(window);
            var expected = new ToolExecutionAuthorization.Request(request.binding().run(), request.lease(), request.descriptor().reference(),
                    request.descriptor().policy().bindingSha256(), request.parametersSha256(), request.action().intentId(), window.deadlineAt());
            ToolExecutionAuthorization.CheckResult checked;
            try { checked = authorization.check(expected, ToolExecutionAuthorization.Boundary.RECONCILE); }
            catch (RuntimeException privateAuthority) { throw unavailable(); }
            if (checked == null || !expected.equals(checked.request()) || checked.boundary() != ToolExecutionAuthorization.Boundary.RECONCILE) throw unavailable();
            if (checked.decision() == ToolExecutionAuthorization.Decision.DENY) throw denied();
            var audit = checked.audit();
            if (checked.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit == null
                    || audit.boundary() != ToolExecutionAuthorization.Boundary.RECONCILE || audit.decision() != ToolExecutionAuthorization.Decision.ALLOW
                    || audit.subjectType() != request.binding().run().actor().subjectType() || !first.grantId().equals(audit.grantId())
                    || audit.checkedAt().isBefore(started) || audit.checkedAt().isAfter(clock.instant())
                    || audit.expiresAt().isBefore(window.deadlineAt()) || audit.expiresAt().isAfter(first.expiresAt())) throw unavailable();
            checkDisclosureWindow(window);
            var result = Objects.requireNonNull(transaction.execute(status -> {
                checkDisclosureWindow(window);
                var value = accesses.recordKnownStartResultAccess(request, first, audit, policy.toolTimeout(), window);
                checkDisclosureWindow(window); return value;
            }));
            checkDisclosureWindow(window); return result;
        } catch (AgentLedgerException failure) { throw new AgentLedgerException(failure.code(), "Tool result access is unavailable or changed"); }
        catch (RuntimeException privateFailure) { throw unavailable(); }
    }
    void checkDisclosureWindow(DisclosureWindowVO window) {
        if (window == null || !verified.get() || Thread.currentThread().isInterrupted() || !window.deadlineAt().isAfter(clock.instant())
                || window.nanoDeadline() - nanoTime.getAsLong() <= 0) throw unavailable();
    }
    private static DisclosureWindowVO window(Instant started, long startedNanos, Instant deadline) {
        return new DisclosureWindowVO(deadline, startedNanos + Duration.between(started, deadline).toNanos());
    }
    private static Instant earlier(Instant left, Instant right) { return left.isBefore(right) ? left : right; }

    static void knownStartOutcome(ToolExecutionOutcome outcome) {
        if (outcome == null) throw invalid();
        if (outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED) {
            if (!outcome.output().keySet().equals(Set.of("instanceId")) || !(outcome.output().get("instanceId") instanceof String instance)) throw invalid();
            new FixedBpmnStartToolTransport.StartObservation(outcome.businessReceiptId(), instance);
        } else if (!Set.of(ToolExecutionOutcome.Status.REJECTED, ToolExecutionOutcome.Status.FAILED_KNOWN).contains(outcome.status())
                || !outcome.output().isEmpty() || outcome.businessReceiptId() != null || outcome.errorCode() == null) throw invalid();
    }
    private static boolean zero(BigDecimal value) { return value != null && value.signum() == 0; }
    static boolean zeroAccessBudget(AgentBudgetReservation budget) {
        if (budget == null || budget.reservationId() == null || budget.leaseEpoch() < 1 || budget.kind() != AgentBudgetRequest.Kind.TOOL || budget.toolVersion() == null
                || budget.inputTokens() != 0 || budget.outputTokens() != 0 || !zero(budget.estimatedCost()) || budget.state() == null
                || budget.actualInputTokens() != null && budget.actualInputTokens() != 0 || budget.actualOutputTokens() != null && budget.actualOutputTokens() != 0
                || budget.actualCost() != null && !zero(budget.actualCost())) return false;
        return switch (budget.state()) {
            case RESERVED -> budget.actualInputTokens() == null && budget.actualOutputTokens() == null && budget.actualCost() == null;
            case SETTLED -> Long.valueOf(0).equals(budget.actualInputTokens()) && Long.valueOf(0).equals(budget.actualOutputTokens()) && zero(budget.actualCost());
            case UNKNOWN -> true;
        };
    }

    public record StartResultAccessRequestVO(ToolExecutionGrant.BindingVO binding, AgentRunLease lease, ToolDescriptor descriptor, ToolActionRequest action, UUID accessId) {
        public StartResultAccessRequestVO {
            if (binding == null || lease == null || descriptor == null || action == null || accessId == null
                    || !binding.run().runId().equals(lease.runId()) || !binding.run().actor().equals(action.owner())
                    || !descriptor.reference().equals(binding.tool()) || !descriptor.reference().equals(action.tool())
                    || !descriptor.policy().bindingSha256().equals(binding.backendBindingSha256()) || !descriptor.policy().equals(action.policy())
                    || descriptor.policy().risk() != ToolPolicy.Risk.IDEMPOTENT_WRITE || !ProcessClient.START_RESOURCE_KEY.equals(descriptor.policy().executionResourceKey())
                    || !descriptor.output().fields().equals(Map.of("instanceId", ToolSchema.ValueType.STRING)) || !descriptor.output().required().equals(Set.of("instanceId"))) throw invalid();
            var rebuilt = new ToolActionRequest(binding.run().actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
            if (!rebuilt.intentId().equals(action.intentId()) || !rebuilt.requestSha256().equals(action.requestSha256()) || !rebuilt.arguments().equals(action.arguments())) throw invalid();
        }
        public String reservationId() { return "tool:" + accessId; }
        public String parametersSha256() { return descriptor.parametersSha256(action.arguments()); }
        @Override public String toString() { return "ToolResultAccessStore.StartResultAccessRequestVO[redacted]"; }
    }
    record RecordedAccessVO(String runId, UUID accessId, String actionIntentId, long sourceActionRevision, String sourceResultSha256,
                            ToolExecutionOutcome sourceOutcome, AgentBudgetReservation accessBudget, DisclosureWindowVO window) {
        RecordedAccessVO {
            if (runId == null || runId.isBlank() || runId.length() > 64 || accessId == null || !canonicalUuid(actionIntentId) || sourceActionRevision < 1
                    || sourceResultSha256 == null || !sourceResultSha256.matches("[0-9a-f]{64}") || !zeroAccessBudget(accessBudget)
                    || !("tool:" + accessId).equals(accessBudget.reservationId()) || window == null) throw invalid();
            knownStartOutcome(sourceOutcome);
        }
        @Override public String toString() { return "ToolResultAccessStore.RecordedAccessVO[redacted]"; }
    }
    record DisclosureWindowVO(Instant deadlineAt, long nanoDeadline) {
        DisclosureWindowVO { if (deadlineAt == null) throw invalid(); }
        @Override public String toString() { return "ToolResultAccessStore.DisclosureWindowVO[redacted]"; }
    }
    private static boolean canonicalUuid(String value) { try { return value != null && UUID.fromString(value).toString().equals(value); } catch (IllegalArgumentException malformed) { return false; } }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool result access values are invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool result access is unavailable or changed"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current tool result access was denied"); }
}
