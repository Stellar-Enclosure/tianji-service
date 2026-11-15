package top.egon.cola.platform.tianji.agent.server.integration.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceExtraction;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.infrastructure.memory.PostgresMemoryService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.MemoryPreferenceIntentStore;
import top.egon.cola.platform.tianji.agent.server.security.TianjiMemoryPreferenceWriteAuthorization;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Bounded orchestration over the existing intent/fact transactions; it creates no scheduler or authority refresh. */
@Slf4j
public final class MemoryPreferenceWork {
    private final MemoryPreferenceIntentStore store;
    private final PostgresMemoryService memory;
    private final TianjiMemoryPreferenceWriteAuthorization authority;
    private final List<Origin> origins;
    private final Duration leaseDuration;
    private final Duration retryDelay;
    private final Clock clock;
    private final String owner = "memory-preferences-" + java.util.UUID.randomUUID();

    public MemoryPreferenceWork(DataSource source, AgentLedgerService ledger, MemoryPreferenceIntentStore store, PostgresMemoryService memory,
            TianjiMemoryPreferenceWriteAuthorization authority, List<Origin> origins, Duration leaseDuration, Duration retryDelay, Clock clock) {
        this.store = Objects.requireNonNull(store, "store"); this.memory = Objects.requireNonNull(memory, "memory"); this.authority = Objects.requireNonNull(authority, "authority");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration"); this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay"); this.clock = Objects.requireNonNull(clock, "clock");
        if (source == null || ledger == null || !ledger.usesDataSource(source) || !store.usesDataSource(source) || !store.usesLedger(ledger) || !memory.usesDataSource(source) || !authority.usesLedger(ledger)
                || origins == null || origins.isEmpty() || origins.size() > 1000 || new HashSet<>(origins).size() != origins.size()
                || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0 || leaseDuration.compareTo(Duration.ofSeconds(30)) > 0 || leaseDuration.getNano() % 1_000_000 != 0
                || retryDelay.compareTo(Duration.ofMillis(100)) < 0 || retryDelay.compareTo(Duration.ofHours(1)) > 0) throw new IllegalArgumentException("Memory preference work binding or timing is invalid");
        this.origins = List.copyOf(origins);
    }
    public List<Origin> origins() { return origins; }

    public Result apply(Origin origin, int maximumRecords, int maximumFacts, Instant requestedDeadline, BooleanSupplier admitted) {
        if (!origins.contains(origin) || maximumRecords < 1 || maximumRecords > 100 || maximumFacts < 1 || maximumFacts > 100) throw new IllegalArgumentException("Memory preference work bound is invalid");
        Objects.requireNonNull(requestedDeadline, "requestedDeadline"); Objects.requireNonNull(admitted, "admitted");
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory preference work requires no ambient transaction");
        Instant maximum = clock.instant().plusSeconds(30); Instant deadline = requestedDeadline.isBefore(maximum) ? requestedDeadline : maximum;
        long remaining = Duration.between(clock.instant(), deadline).toNanos(); if (remaining <= 0) return new Result(0, 0, 0, 0); long monotonic = System.nanoTime() + remaining;
        if (!live(deadline, monotonic, admitted)) return new Result(0, 0, 0, 0);
        int reconciled = store.reconcile(origin.tenantId(), origin.issuerFingerprint(), maximumRecords);
        int selected = 0, attempted = 0, acknowledged = 0;
        if (reconciled >= maximumRecords || !live(deadline, monotonic, admitted)) return new Result(0, 0, 0, reconciled);
        var candidates = store.candidates(origin.tenantId(), origin.issuerFingerprint(), maximumRecords - reconciled);
        for (var id : candidates) {
            if (selected + reconciled >= maximumRecords || attempted >= maximumFacts || !live(deadline, monotonic, admitted)) break;
            selected++;
            MemoryPreferenceIntent.Lease lease = null;
            try {
                var claimed = store.claim(origin.tenantId(), origin.issuerFingerprint(), id, owner, leaseDuration); if (claimed.isEmpty()) continue;
                lease = claimed.orElseThrow();
                if (!live(deadline, monotonic, admitted)) { store.defer(lease, "ADMISSION_STOPPED", retryDelay); continue; }
                var prepared = authority.prepare(lease, deadline); var commands = prepared.commands();
                lease = store.sealCommands(lease, commands.size(), PostgresMemoryService.preferenceCommandListSha256(commands));
                if (prepared.outcome() != MemoryPreferenceExtraction.Outcome.ELIGIBLE) {
                    store.finish(lease, prepared.outcome() == MemoryPreferenceExtraction.Outcome.NO_DECLARATION ? MemoryPreferenceIntent.State.SKIPPED : MemoryPreferenceIntent.State.REJECTED,
                            "EXTRACTION_" + prepared.outcome().name()); continue;
                }
                if (lease.nextIndex() >= commands.size()) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory preference progress changed");
                while (lease != null && attempted < maximumFacts && live(deadline, monotonic, admitted)) {
                    var command = commands.get(lease.nextIndex()); var context = authority.permit(lease, command, deadline);
                    if (!live(deadline, monotonic, admitted)) break;
                    attempted++;
                    memory.upsert(context, command);
                    // A committed fact has its original receipt. If local admission closed, the next lease replays it.
                    if (!live(deadline, monotonic, admitted)) break;
                    var next = store.acknowledge(lease); acknowledged++; lease = next.orElse(null);
                }
                if (lease != null) store.defer(lease, "BUDGET_YIELD", retryDelay);
            } catch (AgentLedgerException failure) {
                if (lease != null) recover(lease, failure.code());
                else log.warn("Memory preference stage=CLAIM outcome=UNAVAILABLE errorCode={}", failure.code());
            } catch (RuntimeException failure) {
                log.warn("Memory preference stage=APPLY outcome=UNAVAILABLE errorType={}", failure.getClass().getSimpleName());
                if (lease != null) recover(lease, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
            }
        }
        return new Result(selected, attempted, acknowledged, reconciled);
    }

    private void recover(MemoryPreferenceIntent.Lease lease, AgentErrorCodeEnum code) {
        if (code == AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED) return;
        try {
            switch (code) {
                case TIANJI_PERMISSION_DENIED, TIANJI_RESOURCE_NOT_FOUND -> store.finish(lease, MemoryPreferenceIntent.State.REJECTED, "WRITE_PERMISSION_DENIED");
                case TIANJI_IDEMPOTENCY_CONFLICT, TIANJI_REVISION_CONFLICT, TIANJI_INVALID_COMMAND -> store.finish(lease, MemoryPreferenceIntent.State.DEAD, "WRITE_CONFLICT");
                default -> store.defer(lease, "WRITE_UNAVAILABLE", retryDelay);
            }
        } catch (AgentLedgerException stale) {
            if (stale.code() != AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED) log.warn("Memory preference stage=RECOVER outcome=UNAVAILABLE errorCode={}", stale.code());
        } catch (RuntimeException unavailable) { log.warn("Memory preference stage=RECOVER outcome=UNAVAILABLE errorType={}", unavailable.getClass().getSimpleName()); }
    }
    private boolean live(Instant deadline, long monotonic, BooleanSupplier admitted) { return !Thread.currentThread().isInterrupted() && clock.instant().isBefore(deadline) && monotonic - System.nanoTime() > 0 && admitted.getAsBoolean(); }

    public record Origin(long tenantId, String issuerFingerprint) {
        public Origin { if (tenantId < 1 || issuerFingerprint == null || !issuerFingerprint.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Memory preference origin is invalid"); }
    }
    /** Attempted facts bound admission, including an unknown commit; acknowledgement counts only observed durable progress. */
    public record Result(int selectedIntents, int attemptedFacts, int acknowledgedFacts, int reconciled) {
        public Result { if (selectedIntents < 0 || selectedIntents > 100 || attemptedFacts < 0 || attemptedFacts > 100 || acknowledgedFacts < 0 || acknowledgedFacts > attemptedFacts || reconciled < 0 || selectedIntents + reconciled > 100) throw new IllegalArgumentException("Memory preference work result is invalid"); }
    }
}
