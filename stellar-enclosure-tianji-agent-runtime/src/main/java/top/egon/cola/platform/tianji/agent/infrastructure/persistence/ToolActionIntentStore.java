package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
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

    public Optional<PreparedActionResult> current(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor) {
        var window = authorize(run, lease, action, descriptor);
        return transact(window, () -> intents.current(run, lease, action, descriptor, window.deadlineAt(), window.storage().profile()).map(ToolActionIntentStore::projection));
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
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool preparation requires its own transaction");
        var storage = requireVerifiedStorage();
        if (run == null || lease == null || action == null || descriptor == null || !run.actor().equals(action.owner())
                || !run.runId().equals(lease.runId()) || !descriptor.reference().equals(action.tool())) throw invalid();
        var reconstructed = new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments());
        if (!reconstructed.requestSha256().equals(action.requestSha256()) || !reconstructed.intentId().equals(action.intentId())) throw invalid();
        Instant now = clock.instant(); Instant deadline = now.plusSeconds(10);
        if (run.deadlineAt().isBefore(deadline)) deadline = run.deadlineAt();
        if (!deadline.isAfter(now)) throw unavailable();
        var window = new WindowVO(deadline, nanoTime.getAsLong() + Duration.between(now, deadline).toNanos(), storage);
        var request = new ToolExecutionAuthorization.Request(run, lease, descriptor.reference(), descriptor.policy().bindingSha256(), action.requestSha256(), action.intentId(), deadline);
        ToolExecutionAuthorization.Decision decision;
        try { decision = authorization.authorize(request, ToolExecutionAuthorization.Boundary.PREPARE); }
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
