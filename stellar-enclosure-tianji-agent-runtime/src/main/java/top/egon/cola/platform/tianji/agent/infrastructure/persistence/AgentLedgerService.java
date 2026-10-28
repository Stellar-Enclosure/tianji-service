package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.ExecutionPolicyReducer;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.PendingAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Local Agent ledger. Callers must authenticate and authorize before constructing the trusted actor;
 * this service is not an HTTP entry point and is not automatically enabled.
 */
public final class AgentLedgerService {
    private final JdbcAgentLedgerRepository repository;
    private final AgentJsonCodec json = new AgentJsonCodec();
    private final TransactionTemplate transaction;
    private final AgentBudgetService budgetService;
    public AgentLedgerService(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.repository = new JdbcAgentLedgerRepository(new JdbcTemplate(dataSource), json);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.budgetService = new AgentBudgetService(this, repository, transaction);
    }

    /** Retained for existing callers; PostgreSQL is the authoritative clock for the ledger. */
    @Deprecated
    public AgentLedgerService(DataSource dataSource, Clock clock) {
        this(dataSource);
        Objects.requireNonNull(clock, "clock");
    }

    public AgentBudgetService budgets() {
        return budgetService;
    }

    public Instant databaseNow() {
        return now();
    }

    /** Inserts a new immutable version, or returns the existing version if its content is identical. */
    public AgentDefinitionDTO publish(TrustedAgentActor actor, AgentDefinitionSpecDTO specification) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(specification, "specification");
        String hash = json.definitionHash(specification);
        String content = json.write(specification, AgentJsonCodec.MAX_SNAPSHOT_BYTES);
        return transaction.execute(status -> {
            Instant now = now();
            repository.insertDefinitionFamily(actor, specification.definitionKey(), now);
            repository.insertDefinitionVersion(actor, specification.definitionKey(), specification.version(),
                    hash, content, now);
            JdbcAgentLedgerRepository.DefinitionRow stored = repository.definition(actor.tenantId(),
                    specification.definitionKey(), specification.version()).orElseThrow();
            if (!stored.hash().equals(hash)) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT,
                        "definition version already exists with different content");
            }
            return definition(stored.specification(), stored.hash());
        });
    }

    /** Atomically records the queued run and its first event; no model is called in this transaction. */
    public AgentRunDTO start(TrustedAgentActor actor, StartAgentRunCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String requestHash = json.requestHash(command);
        return transaction.execute(status -> {
            Optional<JdbcAgentLedgerRepository.StoredRun> existing =
                    repository.byIdempotencyKey(actor, command.idempotencyKey());
            if (existing.isPresent()) {
                return sameRequest(existing.get(), requestHash);
            }
            JdbcAgentLedgerRepository.DefinitionRow definition = repository.definition(actor.tenantId(),
                    command.definitionKey(), command.definitionVersion()).orElseThrow(AgentLedgerService::notFound);
            ExecutionPolicyDTO policy = ExecutionPolicyReducer.reduce(definition.specification().policy(),
                    command.policyOverride());
            String snapshot = json.snapshot(definition.specification(), definition.hash(), policy, command);
            Instant now = now();
            String runId = UUID.randomUUID().toString();
            int inserted = repository.insertRun(actor, runId, command.definitionKey(), command.definitionVersion(),
                    command.idempotencyKey(), requestHash, command.sessionId(), command.correlationId(),
                    now.plus(policy.totalTimeout()), snapshot, now);
            if (inserted == 0) {
                return sameRequest(repository.byIdempotencyKey(actor, command.idempotencyKey()).orElseThrow(),
                        requestHash);
            }
            repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(), runId, 1,
                    "RUN_QUEUED", null, null, null, now, command.correlationId(), "1",
                    Map.of("status", AgentRunStatusEnum.QUEUED.name())));
            return repository.run(actor, runId).orElseThrow().run();
        });
    }

    public AgentRunDTO get(TrustedAgentActor actor, GetAgentRunQuery query) {
        return requireRun(actor, query).run();
    }

    public Optional<AgentExecutionResult> getResult(TrustedAgentActor actor, GetAgentRunQuery query) {
        String result = requireRun(actor, query).resultJson();
        return result == null ? Optional.empty() : Optional.of(json.readResult(result));
    }

    public AgentExecutionSnapshot snapshot(TrustedAgentActor actor, GetAgentRunQuery query) {
        JdbcAgentLedgerRepository.StoredRun run = requireRun(actor, query);
        AgentExecutionSnapshot snapshot = json.readSnapshot(run.snapshotJson());
        JdbcAgentLedgerRepository.DefinitionRow definition = repository.definition(actor.tenantId(),
                run.run().definitionKey(), run.run().definitionVersion()).orElseThrow(AgentLedgerService::notFound);
        if (!snapshot.definition().definitionKey().equals(run.run().definitionKey())
                || snapshot.definition().version() != run.run().definitionVersion()
                || !definition.hash().equals(snapshot.definitionContentSha256())
                || !definition.hash().equals(json.definitionHash(snapshot.definition()))) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    "Agent execution snapshot does not match its definition");
        }
        return snapshot;
    }

    public List<AgentEventDTO> events(TrustedAgentActor actor, AgentEventsQuery query) {
        Objects.requireNonNull(query, "query");
        requireRun(actor, new GetAgentRunQuery(query.runId()));
        return List.copyOf(repository.events(actor.tenantId(), query.runId(), query.afterSequence(), query.limit()));
    }

    /** Appends one projected event only while the expected run revision remains active. */
    public AgentRunDTO appendEvent(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision,
                                   String type, String author, Map<String, Object> payload) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        GetAgentRunQuery query = new GetAgentRunQuery(lease.runId());
        if (expectedRevision < 0) {
            throw invalid("invalid Agent run revision");
        }
        Instant now = now();
        AgentEventDTO validated = new AgentEventDTO(UUID.randomUUID().toString(), query.runId(), 1,
                type, author, null, null, now, null, "1", payload);
        json.write(validated.payload(), AgentJsonCodec.MAX_EVENT_BYTES);
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            JdbcAgentLedgerRepository.StoredRun updated = repository.appendRunningEvent(actor, lease,
                    expectedRevision).orElseThrow(() -> new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run revision or state changed"));
            repository.insertEvent(actor.tenantId(), new AgentEventDTO(validated.eventId(), query.runId(),
                    updated.run().lastEventSequence(), validated.type(), validated.author(), null, null, now,
                    previous.correlationId(), "1", validated.payload()));
            return updated.run();
        });
    }

    /** Claims a queued run with a database-timed lease and monotonically increasing fencing epoch. */
    public ClaimedAgentRun claim(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                 String owner, Duration leaseDuration) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        long leaseMillis = leaseMillis(leaseDuration);
        if (owner == null || owner.isBlank() || owner.length() > 128 || expectedRevision < 0) {
            throw invalid("invalid Agent run claim");
        }
        String normalizedOwner = owner.trim();
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            JdbcAgentLedgerRepository.StoredRun claimed = repository.claim(actor, query.runId(), expectedRevision,
                    normalizedOwner, leaseMillis).orElseThrow(() -> new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run cannot be claimed"));
            appendStatusEvent(actor, previous, claimed, AgentRunStatusEnum.QUEUED, AgentRunStatusEnum.RUNNING, null);
            return new ClaimedAgentRun(claimed.run(), new AgentRunLease(query.runId(), normalizedOwner,
                    claimed.leaseEpoch(), claimed.leaseExpiresAt()));
        });
    }

    public LeaseHeartbeat heartbeat(TrustedAgentActor actor, AgentRunLease lease, Duration leaseDuration) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        if (repository.renew(actor, lease, leaseMillis(leaseDuration)).isPresent()) {
            return LeaseHeartbeat.ACTIVE;
        }
        JdbcAgentLedgerRepository.StoredRun current = requireRun(actor, new GetAgentRunQuery(lease.runId()));
        Instant now = now();
        if (!current.run().deadlineAt().isAfter(now) && !AgentRunTransitions.terminal(current.run().status())) {
            return LeaseHeartbeat.DEADLINE_EXPIRED;
        }
        if (current.run().status() == AgentRunStatusEnum.RUNNING && current.run().cancelRequested()
                && lease.owner().equals(current.leaseOwner()) && lease.epoch() == current.leaseEpoch()
                && current.leaseExpiresAt() != null && current.leaseExpiresAt().isAfter(now)) {
            return LeaseHeartbeat.CANCEL_REQUESTED;
        }
        return LeaseHeartbeat.LEASE_LOST;
    }

    /** Commits a terminal result only for the current, unexpired lease. */
    public AgentRunDTO finish(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision,
                              AgentExecutionResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(result, "result");
        if (expectedRevision < 0 || !lease.runId().equals(result.runId())
                || !(result.status() == AgentRunStatusEnum.COMPLETED || result.status() == AgentRunStatusEnum.FAILED
                || result.status() == AgentRunStatusEnum.CANCELLED || result.status() == AgentRunStatusEnum.TIMED_OUT)) {
            throw invalid("invalid leased Agent result");
        }
        String resultJson = json.write(result, AgentJsonCodec.MAX_RESULT_BYTES);
        return transaction.execute(status -> {
            GetAgentRunQuery query = new GetAgentRunQuery(lease.runId());
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            JdbcAgentLedgerRepository.StoredRun updated = repository.finishLeased(actor, lease, expectedRevision,
                    result.status(), resultJson, result.errorCode()).orElseThrow(() -> new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent run lease or revision is no longer valid"));
            repository.markOutstandingBudgetUnknown(actor.tenantId(), lease.runId());
            appendStatusEvent(actor, previous, updated, AgentRunStatusEnum.RUNNING, result.status(), null);
            return updated.run();
        });
    }

    /** Database time, rather than a worker's clock, decides when a deadline has expired. */
    public AgentRunDTO expireDeadline(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                    Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(),
                    previous.run().attempt() == 0 ? "NONE" : "UNKNOWN");
            JdbcAgentLedgerRepository.StoredRun updated = repository.expireDeadline(actor, query.runId(),
                    expectedRevision, json.write(result, AgentJsonCodec.MAX_RESULT_BYTES)).orElseThrow(
                    () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                            "Agent run deadline or revision has changed"));
            repository.markOutstandingBudgetUnknown(actor.tenantId(), query.runId());
            appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.TIMED_OUT, null);
            return updated.run();
        });
    }

    /** An expired worker is fenced and suspended for reconciliation, never silently retried. */
    public AgentRunDTO suspendExpiredLease(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            JdbcAgentLedgerRepository.StoredRun updated = repository.suspendExpiredLease(actor, query.runId(),
                    expectedRevision).orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    "Agent run lease is not eligible for reconciliation"));
            repository.markOutstandingBudgetUnknown(actor.tenantId(), query.runId());
            appendStatusEvent(actor, previous, updated, AgentRunStatusEnum.RUNNING,
                    AgentRunStatusEnum.SUSPENDED, "LEASE_EXPIRED");
            return updated.run();
        });
    }

    /** Processes a bounded batch; callers schedule it with their managed executor. */
    public int reconcileDue(int limit) {
        if (limit < 1 || limit > 1000) {
            throw invalid("reconciliation batch limit is invalid");
        }
        int reconciled = 0;
        for (JdbcAgentLedgerRepository.ReconciliationCandidate candidate : repository.dueRuns(limit)) {
            try {
                GetAgentRunQuery query = new GetAgentRunQuery(candidate.runId());
                if (candidate.deadlineExpired()) {
                    expireDeadline(candidate.actor(), query, candidate.revision());
                } else {
                    suspendExpiredLease(candidate.actor(), query, candidate.revision());
                }
                reconciled++;
            } catch (AgentLedgerException race) {
                if (race.code() != AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT) {
                    throw race;
                }
            }
        }
        return reconciled;
    }

    /** Internal queue discovery; the returned owner comes from the committed run record. */
    public List<PendingAgentRun> pendingRuns(int limit) {
        if (limit < 1 || limit > 1000) throw invalid("queue batch limit is invalid");
        return List.copyOf(repository.queuedRuns(limit));
    }

    /** Records cancellation intent and its idempotent receipt in the same transaction. */
    public CancelAgentRunResult cancel(TrustedAgentActor actor, CancelAgentRunCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String hash = json.cancelHash(command);
        GetAgentRunQuery query = new GetAgentRunQuery(command.runId());
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            Optional<JdbcAgentLedgerRepository.CancelReceipt> existing = repository.cancelReceipt(actor, command.commandId());
            if (existing.isPresent()) {
                return sameCancellation(existing.get(), hash);
            }
            if (repository.insertCancelReceipt(actor, command.commandId(), hash, query.runId(), now()) == 0) {
                return sameCancellation(repository.cancelReceipt(actor, command.commandId()).orElseThrow(), hash);
            }
            CancelAgentRunResult result;
            if (AgentRunTransitions.terminal(previous.run().status())) {
                result = new CancelAgentRunResult(query.runId(), false, previous.run().status(),
                        previous.run().revision(), previous.cancelRequestedAt() == null ? now() : previous.cancelRequestedAt());
            } else if (!previous.run().deadlineAt().isAfter(now())) {
                AgentExecutionResult expired = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                        Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(),
                        previous.run().attempt() == 0 ? "NONE" : "UNKNOWN");
                JdbcAgentLedgerRepository.StoredRun updated = repository.expireDeadline(actor, query.runId(),
                        previous.run().revision(), json.write(expired, AgentJsonCodec.MAX_RESULT_BYTES))
                        .orElseThrow(AgentLedgerService::revisionConflict);
                repository.markOutstandingBudgetUnknown(actor.tenantId(), query.runId());
                appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.TIMED_OUT, null);
                result = new CancelAgentRunResult(query.runId(), false, updated.run().status(), updated.run().revision(), now());
            } else {
                if (previous.run().revision() != command.expectedRevision()) {
                    throw revisionConflict();
                }
                JdbcAgentLedgerRepository.StoredRun updated;
                if (previous.run().status() == AgentRunStatusEnum.RUNNING) {
                    if (previous.run().cancelRequested()) {
                        updated = previous;
                    } else {
                        updated = repository.requestRunningCancellation(actor, query.runId(), command.expectedRevision())
                                .orElseThrow(AgentLedgerService::revisionConflict);
                        repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(),
                                query.runId(), updated.run().lastEventSequence(), "CANCEL_REQUESTED", null, null,
                                null, now(), previous.correlationId(), "1", Map.of("reason", command.reason())));
                    }
                } else {
                    AgentExecutionResult cancelled = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.CANCELLED,
                            Map.of(), null, null, null, null, previous.run().attempt() == 0 ? "NONE" : "UNKNOWN");
                    updated = repository.cancelNotRunning(actor, query.runId(), command.expectedRevision(),
                            json.write(cancelled, AgentJsonCodec.MAX_RESULT_BYTES)).orElseThrow(AgentLedgerService::revisionConflict);
                    repository.markOutstandingBudgetUnknown(actor.tenantId(), query.runId());
                    appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.CANCELLED, null);
                }
                result = new CancelAgentRunResult(query.runId(), true, updated.run().status(), updated.run().revision(),
                        updated.cancelRequestedAt());
            }
            repository.completeCancelReceipt(actor, command.commandId(), json.write(result, AgentJsonCodec.MAX_EVENT_BYTES));
            return result;
        });
    }

    /** Unfenced lifecycle changes are retired; use claim, finish, cancellation or reconciliation. */
    @Deprecated
    public AgentRunDTO transition(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                  AgentRunStatusEnum from, AgentRunStatusEnum to, String waitReason,
                                  AgentExecutionResult result) {
        throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                "unfenced Agent run transitions are unavailable");
    }

    private void appendStatusEvent(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun previous,
                                   JdbcAgentLedgerRepository.StoredRun updated, AgentRunStatusEnum from,
                                   AgentRunStatusEnum to, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from.name());
        payload.put("status", to.name());
        if (reason != null) {
            payload.put("reason", reason);
        }
        repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(),
                updated.run().runId(), updated.run().lastEventSequence(), "RUN_STATUS_CHANGED", null, null,
                null, now(), previous.correlationId(), "1", payload));
    }

    private static long leaseMillis(Duration duration) {
        if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0
                || duration.compareTo(Duration.ofMinutes(5)) > 0) {
            throw invalid("lease duration must be between one second and five minutes");
        }
        return duration.toMillis();
    }

    private JdbcAgentLedgerRepository.StoredRun requireRun(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return repository.run(actor, query.runId()).orElseThrow(AgentLedgerService::notFound);
    }

    private static AgentRunDTO sameRequest(JdbcAgentLedgerRepository.StoredRun run, String hash) {
        if (!run.requestHash().equals(hash)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                    "idempotency key was already used for a different request");
        }
        return run.run();
    }

    private CancelAgentRunResult sameCancellation(JdbcAgentLedgerRepository.CancelReceipt receipt, String hash) {
        if (!receipt.requestHash().equals(hash)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                    "cancellation command id was already used for a different request");
        }
        if (receipt.resultJson() == null) {
            throw new IllegalStateException("committed cancellation receipt is incomplete");
        }
        return json.readCancelResult(receipt.resultJson());
    }

    private static AgentLedgerException revisionConflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                "Agent run revision or state changed");
    }

    private static AgentDefinitionDTO definition(AgentDefinitionSpecDTO spec, String hash) {
        return new AgentDefinitionDTO(spec.definitionKey(), spec.version(), hash, spec.executionMode(),
                spec.codeRef(), spec.inputSchemaVersion(), spec.outputSchemaVersion(), spec.dependencies());
    }

    private static AgentLedgerException notFound() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND,
                "Agent resource is not available in the current scope");
    }

    private static AgentLedgerException invalid(String message) {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_INVALID_COMMAND, message);
    }

    private Instant now() {
        return repository.databaseNow().truncatedTo(ChronoUnit.MICROS);
    }
}
