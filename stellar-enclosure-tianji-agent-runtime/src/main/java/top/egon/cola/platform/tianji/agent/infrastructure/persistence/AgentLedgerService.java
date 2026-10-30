package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.application.run.AgentTerminalEventPublisher;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionSnapshot;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunTransitions;
import top.egon.cola.platform.tianji.agent.domain.ClaimedAgentRun;
import top.egon.cola.platform.tianji.agent.domain.ExecutionPolicyReducer;
import top.egon.cola.platform.tianji.agent.domain.LeaseHeartbeat;
import top.egon.cola.platform.tianji.agent.domain.PendingAgentRun;
import top.egon.cola.platform.tianji.agent.domain.GrantReadyAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;

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
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;

/**
 * Local Agent ledger. Callers must authenticate and authorize before constructing the trusted actor;
 * this service is not an HTTP entry point and is not automatically enabled.
 */
public final class AgentLedgerService {
    private final DataSource dataSource;
    private final JdbcAgentLedgerRepository repository;
    private final AgentJsonCodec json = new AgentJsonCodec();
    private final TransactionTemplate transaction;
    private final AgentBudgetService budgetService;
    private final AgentTerminalEventPublisher terminalPublisher;
    private final String terminalProducerId;

    public AgentLedgerService(DataSource dataSource) {
        this(dataSource, (AgentTerminalEventPublisher) null);
    }

    private AgentLedgerService(DataSource dataSource, AgentTerminalEventPublisher terminalPublisher) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        String producerId = terminalPublisher == null ? null : terminalPublisher.producerId();
        if (terminalPublisher != null && (!terminalPublisher.usesDataSource(dataSource) || producerId == null
                || !producerId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}"))) {
            throw new IllegalArgumentException("Agent terminal publisher must use the ledger DataSource and a fixed producer");
        }
        this.terminalPublisher = terminalPublisher;
        this.terminalProducerId = producerId;
        this.repository = new JdbcAgentLedgerRepository(new JdbcTemplate(dataSource), json);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.budgetService = new AgentBudgetService(this, repository, transaction);
    }

    public static AgentLedgerService withTerminalEvents(DataSource dataSource, AgentTerminalEventPublisher publisher) {
        return new AgentLedgerService(dataSource, Objects.requireNonNull(publisher, "publisher"));
    }

    public boolean hasTerminalPublisher(AgentTerminalEventPublisher publisher) {
        return terminalPublisher != null && terminalPublisher == publisher;
    }

    /** Reads an immutable linked terminal reference on the owning primary before delivery opens a network call. */
    public Optional<AgentTerminalEvent> terminalReference(TrustedAgentActor actor, GetAgentRunQuery query, Duration budget) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || query == null || budget == null || budget.compareTo(Duration.ofSeconds(1)) < 0 || budget.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Linked terminal reference requires a verified owner and bounded read");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Terminal delivery reference must own its snapshot transaction");
        var read = new TransactionTemplate(transaction.getTransactionManager());
        // Keep primary routing even when a host routes read-only transactions to replicas.
        read.setReadOnly(false);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(Math.toIntExact(budget.toSeconds()));
        return read.execute(status -> terminalReference(actor, requireRun(actor, query)));
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

    /** Optional capture is provisioned by an operator and must be ready before execution starts. */
    public void verifyContextSnapshotStorage() {
        new AgentStorageVerifier(dataSource).verifyContextSnapshots();
    }

    /** Historical metadata gives neither source bodies nor a capability to reuse an old authorization. */
    public AgentContextSnapshotPageDTO contextSnapshots(TrustedAgentActor actor, AgentContextSnapshotsQuery query) {
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(query, "query");
        return Objects.requireNonNull(transaction.execute(status -> {
            if (!repository.ownedRunExists(actor, query.runId())) throw new AgentLedgerException(
                    AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Agent run was not found");
            var rows = repository.contextSnapshots(actor, query);
            boolean more = rows.size() > query.limit();
            var items = rows.stream().limit(query.limit()).map(JdbcAgentLedgerRepository.ContextRow::snapshot).toList();
            return new AgentContextSnapshotPageDTO(items, more ? items.getLast().modelCallNumber() : null);
        }));
    }

    /** Session and run fencing must use the exact same transaction-bound DataSource. */
    public boolean usesDataSource(DataSource candidate) {
        return dataSource == candidate;
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
        return start(actor, command, null);
    }

    /** Called only while the linked-submission fence is locked in the same transaction. */
    AgentRunDTO startLinked(TrustedAgentActor actor, LinkedAgentStartCommand command) {
        Objects.requireNonNull(command, "command");
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Linked Agent submission requires a verified service and its fence transaction");
        }
        return start(actor, command.start(), command.link());
    }

    private AgentRunDTO start(TrustedAgentActor actor, StartAgentRunCommand command, AgentProcessLink processLink) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        String requestHash = processLink == null ? json.requestHash(command) : json.linkedRequestHash(command, processLink);
        return transaction.execute(status -> {
            if (processLink != null) requireDispatchTime(processLink);
            Optional<JdbcAgentLedgerRepository.StoredRun> existing =
                    repository.byIdempotencyKey(actor, command.idempotencyKey());
            if (existing.isPresent()) {
                if (processLink != null && !processLink.equals(json.readSnapshot(existing.get().snapshotJson()).processLink())) {
                    throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Linked Agent request changed");
                }
                return sameRequest(existing.get(), requestHash);
            }
            if (command.sessionId() != null && !repository.lockOwnedSession(actor, command.sessionId())) {
                throw notFound();
            }
            JdbcAgentLedgerRepository.DefinitionRow definition = repository.definition(actor.tenantId(),
                    command.definitionKey(), command.definitionVersion()).orElseThrow(AgentLedgerService::notFound);
            if (processLink != null && !definition.hash().equals(processLink.definitionContentSha256())) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT, "Linked Agent definition changed");
            }
            ExecutionPolicyDTO policy = ExecutionPolicyReducer.reduce(definition.specification().policy(),
                    command.policyOverride());
            String snapshot = json.snapshot(definition.specification(), definition.hash(), policy, command, processLink);
            Instant now = now();
            if (processLink != null && !processLink.dispatchNotAfter().isAfter(now)) throw dispatchExpired();
            String runId = UUID.randomUUID().toString();
            int inserted = repository.insertRun(actor, runId, command.definitionKey(), command.definitionVersion(),
                    command.idempotencyKey(), requestHash, command.sessionId(), command.correlationId(),
                    now.plus(policy.totalTimeout()), snapshot, now);
            if (inserted == 0) {
                if (processLink != null) requireDispatchTime(processLink);
                return sameRequest(repository.byIdempotencyKey(actor, command.idempotencyKey()).orElseThrow(),
                        requestHash);
            }
            repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(), runId, 1,
                    "RUN_QUEUED", null, null, null, now, command.correlationId(), "1",
                    Map.of("status", AgentRunStatusEnum.QUEUED.name())));
            if (processLink != null) requireDispatchTime(processLink);
            return repository.run(actor, runId).orElseThrow().run();
        });
    }

    void requireDispatchTime(AgentProcessLink link) {
        Instant current = now();
        if (!link.dispatchNotAfter().isAfter(current)) throw dispatchExpired();
        if (link.dispatchNotAfter().isAfter(current.plus(Duration.ofMinutes(15)))) {
            throw invalid("Linked Agent dispatch deadline exceeds its maximum window");
        }
    }

    private static AgentLedgerException dispatchExpired() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED, "Linked Agent dispatch has expired");
    }

    AgentRunDTO lockForLinkedClose(TrustedAgentActor actor, String runId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Linked Agent close requires its fence transaction");
        }
        return repository.runForUpdate(actor, runId).orElseThrow(AgentLedgerService::notFound).run();
    }

    public AgentRunDTO get(TrustedAgentActor actor, GetAgentRunQuery query) {
        return requireRun(actor, query).run();
    }

    /** Pages only runs owned by the authenticated actor; the cursor is exclusive. */
    public AgentRunPageDTO list(TrustedAgentActor actor, ListAgentRunsQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        RunCursor cursor = query.cursor() == null ? null : decodeCursor(query.cursor());
        List<AgentRunDTO> rows = repository.list(actor, cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.runId(), query.limit() + 1);
        boolean more = rows.size() > query.limit();
        List<AgentRunDTO> items = List.copyOf(more ? rows.subList(0, query.limit()) : rows);
        String next = more ? encodeCursor(items.getLast()) : null;
        return new AgentRunPageDTO(items, next);
    }

    private static String encodeCursor(AgentRunDTO run) {
        String value = "1|" + run.createdAt() + "|" + run.runId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static RunCursor decodeCursor(String value) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 3 || !"1".equals(fields[0])) throw new IllegalArgumentException();
            Instant createdAt = Instant.parse(fields[1]);
            String runId = UUID.fromString(fields[2]).toString();
            if (!runId.equals(fields[2]) || createdAt.isBefore(Instant.EPOCH)
                    || createdAt.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) {
                throw new IllegalArgumentException();
            }
            java.sql.Timestamp.from(createdAt);
            return new RunCursor(createdAt, runId);
        } catch (IllegalArgumentException | DateTimeException | ArithmeticException invalid) {
            throw new IllegalArgumentException("invalid Agent run cursor");
        }
    }

    private record RunCursor(Instant createdAt, String runId) { }

    public Optional<AgentExecutionResult> getResult(TrustedAgentActor actor, GetAgentRunQuery query) {
        String result = requireRun(actor, query).resultJson();
        return result == null ? Optional.empty() : Optional.of(json.readResult(result));
    }

    public AgentExecutionSnapshot snapshot(TrustedAgentActor actor, GetAgentRunQuery query) {
        return verifiedSnapshot(actor, requireRun(actor, query));
    }

    /** Reads the run, execution input and its authorization binding from the same stored row. */
    public ExecutionMaterial executionMaterial(TrustedAgentActor actor, GetAgentRunQuery query) {
        JdbcAgentLedgerRepository.StoredRun stored = requireRun(actor, query);
        AgentExecutionSnapshot snapshot = verifiedSnapshot(actor, stored);
        var run = stored.run();
        var authorization = new AgentRunAuthorization.Request(actor, run.runId(), run.definitionKey(),
                run.definitionVersion(), snapshot.definitionContentSha256(), stored.requestHash(),
                json.executionSnapshotHash(snapshot), snapshot.definition().dependencies().policyVersionRef(),
                run.sessionId() != null, run.deadlineAt());
        return new ExecutionMaterial(run, snapshot, authorization);
    }

    public record ExecutionMaterial(AgentRunDTO run, AgentExecutionSnapshot snapshot,
                                    AgentRunAuthorization.Request authorization) {
        @Override public String toString() { return "AgentLedgerService.ExecutionMaterial[redacted]"; }
    }

    private AgentExecutionSnapshot verifiedSnapshot(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun run) {
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

    /** Fences the active worker when current authorization cannot be established. */
    public AgentRunDTO suspendForAuthorization(TrustedAgentActor actor, AgentRunLease lease,
                                               AgentRunAuthorization.Decision decision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        if (decision == null || decision == AgentRunAuthorization.Decision.ALLOW) {
            throw invalid("invalid Agent authorization suspension reason");
        }
        String reason = decision == AgentRunAuthorization.Decision.DENY
                ? "AUTHORIZATION_DENIED" : "AUTHORIZATION_UNAVAILABLE";
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, new GetAgentRunQuery(lease.runId()));
            JdbcAgentLedgerRepository.StoredRun updated = repository.suspendAuthorization(actor, lease, reason)
                    .orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                            "Agent run lease or state is no longer valid"));
            repository.markOutstandingBudgetUnknown(actor.tenantId(), lease.runId());
            appendStatusEvent(actor, previous, updated, AgentRunStatusEnum.RUNNING, AgentRunStatusEnum.SUSPENDED, reason);
            return updated.run();
        });
    }

    /** A confirmed denial removes a queued run from automatic discovery without discarding it. */
    public AgentRunDTO suspendQueuedForAuthorization(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        if (expectedRevision < 0) throw invalid("invalid Agent authorization revision");
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireRun(actor, query);
            JdbcAgentLedgerRepository.StoredRun updated = repository.suspendQueuedAuthorization(actor, query.runId(), expectedRevision)
                    .orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                            "Agent run changed before authorization suspension"));
            appendStatusEvent(actor, previous, updated, AgentRunStatusEnum.QUEUED, AgentRunStatusEnum.SUSPENDED,
                    "AUTHORIZATION_DENIED");
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

    /** Discovers only USER runs with unexpired sealed evidence; execution still reauthorizes each candidate. */
    public List<GrantReadyAgentRun> grantReadyRuns(GrantReadyAgentRun.Cursor after, int limit) {
        return grantReadyRuns(after, limit, false);
    }

    /** SERVICE discovery is opt-in and still requires current authorization for every candidate. */
    public List<GrantReadyAgentRun> grantReadyRuns(GrantReadyAgentRun.Cursor after, int limit, boolean includeService) {
        if (limit < 1 || limit > 1000) throw invalid("queue batch limit is invalid");
        return List.copyOf(repository.grantReadyRuns(after, limit, includeService));
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
        if (terminalPublisher != null && AgentRunTransitions.terminal(to)) terminalReference(actor, updated).ifPresent(terminalPublisher::publish);
    }

    private Optional<AgentTerminalEvent> terminalReference(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        if (terminalProducerId == null || !AgentRunTransitions.terminal(stored.run().status())) return Optional.empty();
        AgentExecutionSnapshot snapshot = verifiedSnapshot(actor, stored);
        if (snapshot.processLink() == null) return Optional.empty();
        if (actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Linked terminal reference requires the owning service transaction");
        }
        var run = stored.run();
        AgentExecutionResult result = json.readResult(stored.resultJson());
        if (!run.runId().equals(result.runId()) || result.status() != run.status() || run.finishedAt() == null
                || !snapshot.definitionContentSha256().equals(snapshot.processLink().definitionContentSha256())) {
            throw new IllegalStateException("Linked terminal result does not match its run");
        }
        String key = repository.linkedSubmissionKey(actor, run.runId(), json.processLinkHash(snapshot.processLink()))
                .orElseThrow(() -> new IllegalStateException("Linked terminal submission evidence is missing"));
        var query = new LinkedAgentLookupQuery(key, snapshot.processLink());
        return Optional.of(new AgentTerminalEvent(1, terminalProducerId,
                AgentTerminalEvent.eventIdFor(terminalProducerId, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), run.runId(), run.revision()),
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), query.idempotencyKey(), query.link(), run.definitionKey(), run.definitionVersion(),
                run.runId(), run.revision(), run.status(), run.finishedAt(), json.resultHash(stored.resultJson()), result.errorCode(),
                result.sideEffectOutcome() == null ? "UNKNOWN" : result.sideEffectOutcome()));
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
