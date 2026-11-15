package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
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
import top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSourceKindEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentEventsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentExecutionResult;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunPageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentUsageDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentUsageKindEnum;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunCommand;
import top.egon.cola.platform.tianji.contract.agent.CancelAgentRunResult;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotsQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentContextSnapshotPageDTO;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsQuery;
import top.egon.cola.platform.tianji.contract.agent.ListAgentRunsByCorrelationQuery;
import top.egon.cola.platform.tianji.contract.agent.StartAgentRunCommand;
import top.egon.cola.platform.tianji.contract.orchestration.AgentProcessLink;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentStartCommand;
import top.egon.cola.platform.tianji.contract.orchestration.LinkedAgentLookupQuery;
import top.egon.cola.platform.tianji.contract.orchestration.AgentTerminalEvent;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentResultArtifactRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentTerminalEventV2.SideEffectOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessSchemaVO;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactEvidenceVO.DecisionEnum;
import top.egon.cola.platform.tianji.agent.application.run.AgentResultArtifactQuery;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentJsonCodec.RuntimeTerminalResultVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.LinkedAgentRuntimeCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TemplateDependencyRefVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;

/**
 * Local Agent ledger. Callers must authenticate and authorize before constructing the trusted actor;
 * this service is not an HTTP entry point and is not automatically enabled.
 */
public final class AgentLedgerService {
    private static final Logger log = LoggerFactory.getLogger(AgentLedgerService.class);
    static final int QUEUED_BACKLOG_CAP = 257;
    private static final int CORRELATION_PAGE_SIZE = 20;
    private final DataSource dataSource;
    private final JdbcAgentLedgerRepository repository;
    private final AgentJsonCodec json = new AgentJsonCodec();
    private final TransactionTemplate transaction;
    private final AgentBudgetService budgetService;
    private final AgentRuntimeBindingStore runtimeBindings;
    private final AgentRuntimeCallReceiptStore runtimeCallReceipts;
    private final ToolActionIntentSql runtimeToolActions;
    private final AgentTerminalEventPublisher terminalPublisher;
    private final String terminalProducerId;
    private final List<String> modelUsageRoutes;
    private final Consumer<Duration> initialClaimTiming;
    private final AtomicBoolean initialClaimTimingFailureReported = new AtomicBoolean();

    public AgentLedgerService(DataSource dataSource) {
        this(dataSource, (AgentTerminalEventPublisher) null);
    }

    private AgentLedgerService(DataSource dataSource, AgentTerminalEventPublisher terminalPublisher) {
        this(dataSource, terminalPublisher, List.of(), null);
    }

    private AgentLedgerService(DataSource dataSource, AgentTerminalEventPublisher terminalPublisher,
                               List<String> modelUsageRoutes, Consumer<Duration> initialClaimTiming) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.modelUsageRoutes = List.copyOf(modelUsageRoutes);
        this.initialClaimTiming = initialClaimTiming;
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
        this.runtimeBindings = new AgentRuntimeBindingStore(dataSource);
        this.runtimeCallReceipts = new AgentRuntimeCallReceiptStore(dataSource, runtimeBindings);
        this.runtimeToolActions = new ToolActionIntentSql(dataSource, this);
    }

    /** Trusted host opt-in. Original construction paths continue without any V15 check. */
    public static AgentLedgerService withModelUsage(DataSource dataSource, AgentTerminalEventPublisher publisher,
                                                     List<String> exactRouteRefs) {
        return withModelUsage(dataSource, publisher, exactRouteRefs, null);
    }

    public static AgentLedgerService withModelUsage(DataSource dataSource, AgentTerminalEventPublisher publisher,
                                                     List<String> exactRouteRefs, Consumer<Duration> initialClaimTiming) {
        if (exactRouteRefs == null || exactRouteRefs.isEmpty() || exactRouteRefs.size() > 32) {
            throw invalid("Agent model usage routes are invalid");
        }
        var routes = new java.util.ArrayList<>(exactRouteRefs);
        var distinct = new java.util.HashSet<String>();
        for (String route : routes) {
            if (route == null || route.isBlank() || route.length() > 256 || !route.equals(route.trim())
                    || route.indexOf('*') >= 0 || route.indexOf('?') >= 0
                    || route.chars().anyMatch(Character::isISOControl)
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(route) || !distinct.add(route)) {
                throw invalid("Agent model usage routes are invalid");
            }
        }
        // Validate an optional publisher's exact source in the unchanged constructor before catalog access.
        var ledger = new AgentLedgerService(dataSource, publisher, routes, initialClaimTiming);
        new AgentStorageVerifier(dataSource).verifyModelUsage();
        return ledger;
    }

    /** Trusted host opt-in for elapsed time on the first committed QUEUED-to-RUNNING claim. */
    public static AgentLedgerService withInitialClaimTiming(DataSource dataSource, AgentTerminalEventPublisher publisher,
                                                            Consumer<Duration> initialClaimTiming) {
        return new AgentLedgerService(dataSource, publisher, List.of(),
                Objects.requireNonNull(initialClaimTiming, "initialClaimTiming"));
    }

    public static AgentLedgerService withTerminalEvents(DataSource dataSource, AgentTerminalEventPublisher publisher) {
        return new AgentLedgerService(dataSource, Objects.requireNonNull(publisher, "publisher"));
    }

    public boolean hasTerminalPublisher(AgentTerminalEventPublisher publisher) {
        return terminalPublisher != null && terminalPublisher == publisher;
    }

    /** Reads the immutable legacy reference on the owning primary before delivery opens a network call. */
    public Optional<AgentTerminalEvent> terminalReference(TrustedAgentActor actor, GetAgentRunQuery query, Duration budget) {
        return readTerminal(actor, query, budget, stored -> terminalReference(actor, stored));
    }

    /** Reads only the exact committed V2 fact, never the public result projection. */
    public Optional<AgentTerminalEventV2> terminalReferenceV2(TrustedAgentActor actor, GetAgentRunQuery query, Duration budget) {
        return readTerminal(actor, query, budget, stored -> terminalReferenceV2(actor, stored));
    }

    private <T> Optional<T> readTerminal(TrustedAgentActor actor, GetAgentRunQuery query, Duration budget,
                                           Function<JdbcAgentLedgerRepository.StoredRun, Optional<T>> projection) {
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
        return read.execute(status -> projection.apply(requireRun(actor, query)));
    }

    /** Observes the exact owner's recorded recovery evidence on the bound Agent primary. */
    public AgentRecoveryDispositionDTO inspectRecovery(TrustedAgentActor actor, GetAgentRunQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (query == null || !modelUsageText(query.runId(), 64)) {
            throw invalid("Agent recovery query is invalid");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent recovery must own its snapshot transaction");
        }
        var read = new TransactionTemplate(transaction.getTransactionManager());
        // Route to the owning primary before setting physical read-only mode inside the transaction.
        read.setReadOnly(false);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
        try {
            return Objects.requireNonNull(read.execute(status -> {
                Instant observedAt = repository.beginRecoveryObservation();
                var run = repository.recoveryRun(actor, query.runId()).orElseThrow(AgentLedgerService::notFound);
                if (!query.runId().equals(run.runId())) throw recoveryUnavailable();
                var reason = recoveryReason(run, observedAt);
                var effect = reason == AgentRecoveryReasonEnum.TERMINAL_RECORDED
                        ? recoveryEffect(run.terminalEffect()) : AgentRecoveryEffectEnum.UNVERIFIED;
                var tool = repository.recoveryToolReadable()
                        ? recoveryToolEvidence(repository.recoveryToolRows(actor, query.runId())) : toolEvidenceUnavailable();
                return new AgentRecoveryDispositionDTO(run.runId(),
                        run.revision() != null && run.revision() >= 0 ? run.revision() : null,
                        run.attempt() != null && run.attempt() >= 0 ? run.attempt() : null, observedAt,
                        recoveryLifecycle(reason), reason, effect, tool,
                        effect == AgentRecoveryEffectEnum.RECORDED_NONE && Boolean.TRUE.equals(tool.hasUnresolvedWrite()));
            }));
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            throw recoveryUnavailable();
        }
    }

    private static AgentRecoveryReasonEnum recoveryReason(JdbcAgentLedgerRepository.RecoveryRunRow run,
                                                           Instant observedAt) {
        if (run.status() == null || run.revision() == null || run.revision() < 0
                || run.attempt() == null || run.attempt() < 0 || run.deadlineAt() == null) {
            return AgentRecoveryReasonEnum.SOURCE_UNAVAILABLE;
        }
        AgentRunStatusEnum status;
        try {
            status = AgentRunStatusEnum.valueOf(run.status());
        } catch (IllegalArgumentException failure) {
            return AgentRecoveryReasonEnum.SOURCE_UNAVAILABLE;
        }
        if (AgentRunTransitions.terminal(status)) return AgentRecoveryReasonEnum.TERMINAL_RECORDED;
        if (switch (status) {
            case QUEUED, RUNNING, WAITING, SUSPENDED -> !run.deadlineAt().isAfter(observedAt);
            default -> false;
        }) {
            return AgentRecoveryReasonEnum.DEADLINE_DUE;
        }
        return switch (status) {
            case QUEUED -> AgentRecoveryReasonEnum.QUEUE_DISCOVERY;
            case RUNNING -> {
                if (run.invalidLeaseTime()) yield AgentRecoveryReasonEnum.FENCE_UNVERIFIED;
                if (run.leaseExpiresAt() == null) yield AgentRecoveryReasonEnum.LEASE_MISSING;
                if (!run.hasLeaseOwner() || !run.hasLeaseEpoch() || run.attempt() == 0) {
                    yield AgentRecoveryReasonEnum.FENCE_UNVERIFIED;
                }
                if (!run.leaseExpiresAt().isAfter(observedAt)) yield AgentRecoveryReasonEnum.LEASE_EXPIRED;
                yield run.cancelRequested() ? AgentRecoveryReasonEnum.CANCEL_REQUESTED : AgentRecoveryReasonEnum.CURRENT_LEASE;
            }
            case SUSPENDED -> switch (run.safeWaitReason()) {
                case "LEASE_EXPIRED" -> AgentRecoveryReasonEnum.LEASE_EXPIRED;
                case "AUTHORIZATION_DENIED" -> AgentRecoveryReasonEnum.AUTHORIZATION_DENIED;
                case "AUTHORIZATION_UNAVAILABLE" -> AgentRecoveryReasonEnum.AUTHORIZATION_UNAVAILABLE;
                case null, default -> AgentRecoveryReasonEnum.SUSPENSION_UNVERIFIED;
            };
            case CREATED, WAITING -> AgentRecoveryReasonEnum.LEGACY_WAIT_UNVERIFIED;
            default -> AgentRecoveryReasonEnum.SOURCE_UNAVAILABLE;
        };
    }

    private static AgentRecoveryLifecycleEnum recoveryLifecycle(AgentRecoveryReasonEnum reason) {
        return switch (reason) {
            case QUEUE_DISCOVERY -> AgentRecoveryLifecycleEnum.QUEUED;
            case CURRENT_LEASE, CANCEL_REQUESTED -> AgentRecoveryLifecycleEnum.ACTIVE;
            case DEADLINE_DUE, LEASE_MISSING, LEASE_EXPIRED, AUTHORIZATION_DENIED, AUTHORIZATION_UNAVAILABLE ->
                    AgentRecoveryLifecycleEnum.STALLED;
            case TERMINAL_RECORDED -> AgentRecoveryLifecycleEnum.TERMINAL;
            case FENCE_UNVERIFIED, SUSPENSION_UNVERIFIED, LEGACY_WAIT_UNVERIFIED, SOURCE_UNAVAILABLE ->
                    AgentRecoveryLifecycleEnum.UNVERIFIED;
        };
    }

    private static AgentRecoveryEffectEnum recoveryEffect(String effect) {
        return switch (effect) {
            case "NONE" -> AgentRecoveryEffectEnum.RECORDED_NONE;
            case "CONFIRMED" -> AgentRecoveryEffectEnum.RECORDED_CONFIRMED;
            case "UNKNOWN" -> AgentRecoveryEffectEnum.UNKNOWN;
            case null, default -> AgentRecoveryEffectEnum.UNVERIFIED;
        };
    }

    private static AgentRecoveryToolEvidenceDTO recoveryToolEvidence(List<JdbcAgentLedgerRepository.RecoveryToolRow> rows) {
        if (rows.size() > 65 || rows.stream().anyMatch(row -> !validRecoveryToolRow(row))) return toolEvidenceUnavailable();
        boolean truncated = rows.size() > 64;
        boolean dispatching = false;
        boolean unknown = false;
        boolean unresolved = false;
        boolean receipt = false;
        for (var row : rows.subList(0, Math.min(64, rows.size()))) {
            dispatching |= "DISPATCHING".equals(row.state());
            unknown |= "UNKNOWN".equals(row.state());
            unresolved |= "UNKNOWN".equals(row.sideEffectOutcome());
            receipt |= Boolean.TRUE.equals(row.hasLocalReceipt());
        }
        return new AgentRecoveryToolEvidenceDTO(
                truncated ? AgentRecoveryToolCoverageEnum.TRUNCATED : AgentRecoveryToolCoverageEnum.COMPLETE,
                recoveryFlag(dispatching, truncated), recoveryFlag(unknown, truncated),
                recoveryFlag(unresolved, truncated), recoveryFlag(receipt, truncated));
    }

    private static boolean validRecoveryToolRow(JdbcAgentLedgerRepository.RecoveryToolRow row) {
        if (!row.ownerMatches() || row.state() == null || row.risk() == null || row.hasLocalReceipt() == null) return false;
        boolean readOnly = "READ_ONLY".equals(row.risk());
        boolean approvalRequired = "APPROVAL_REQUIRED".equals(row.risk());
        if (!readOnly && !approvalRequired && !"IDEMPOTENT_WRITE".equals(row.risk())) return false;
        String effect = switch (row.state()) {
            case "PREPARED", "REJECTED", "FAILED_KNOWN" -> "NONE";
            case "AWAITING_APPROVAL" -> approvalRequired ? "NONE" : null;
            case "DISPATCHING", "UNKNOWN" -> readOnly ? "NONE" : "UNKNOWN";
            case "SUCCEEDED" -> readOnly ? "NONE" : "CONFIRMED";
            default -> null;
        };
        return effect != null && effect.equals(row.sideEffectOutcome())
                && row.hasLocalReceipt() == (!readOnly && "SUCCEEDED".equals(row.state()));
    }

    private static Boolean recoveryFlag(boolean observed, boolean truncated) {
        if (observed) return Boolean.TRUE;
        return truncated ? null : Boolean.FALSE;
    }

    private static AgentRecoveryToolEvidenceDTO toolEvidenceUnavailable() {
        return new AgentRecoveryToolEvidenceDTO(AgentRecoveryToolCoverageEnum.UNAVAILABLE, null, null, null, null);
    }

    private static AgentLedgerException recoveryUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent recovery evidence is unavailable");
    }

    public enum AgentRecoveryLifecycleEnum { QUEUED, ACTIVE, STALLED, TERMINAL, UNVERIFIED }

    public enum AgentRecoveryReasonEnum {
        QUEUE_DISCOVERY, CURRENT_LEASE, CANCEL_REQUESTED, DEADLINE_DUE, LEASE_MISSING, LEASE_EXPIRED,
        AUTHORIZATION_DENIED, AUTHORIZATION_UNAVAILABLE, FENCE_UNVERIFIED, SUSPENSION_UNVERIFIED,
        LEGACY_WAIT_UNVERIFIED, TERMINAL_RECORDED, SOURCE_UNAVAILABLE
    }

    public enum AgentRecoveryEffectEnum { RECORDED_NONE, RECORDED_CONFIRMED, UNKNOWN, UNVERIFIED }

    public enum AgentRecoveryToolCoverageEnum { COMPLETE, TRUNCATED, UNAVAILABLE }

    public record AgentRecoveryToolEvidenceDTO(AgentRecoveryToolCoverageEnum coverage, Boolean hasDispatching,
                                               Boolean hasUnknownResult, Boolean hasUnresolvedWrite,
                                               Boolean hasLocalReceipt) { }

    public record AgentRecoveryDispositionDTO(String runId, Long revision, Integer attempt, Instant observedAt,
                                              AgentRecoveryLifecycleEnum lifecycle, AgentRecoveryReasonEnum reason,
                                              AgentRecoveryEffectEnum agentEffect, AgentRecoveryToolEvidenceDTO toolEvidence,
                                              boolean evidenceConflict) { }

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

    /** Reads provenance under the current run fence; it does not reuse an older run's permission or hold locks during source I/O. */
    public ContextHistory sessionContextHistory(TrustedAgentActor actor, AgentRunLease lease, AgentRunAuthorization.Request current, String sessionId, Duration timeout) {
        Objects.requireNonNull(current, "current"); new top.egon.cola.platform.tianji.contract.agent.GetAgentSessionQuery(sessionId);
        if (TransactionSynchronizationManager.isActualTransactionActive() || !actor.equals(current.actor()) || !lease.runId().equals(current.runId()) || !current.sessionRequired()
                || timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Session source history request is invalid");
        var read = new TransactionTemplate(transaction.getTransactionManager()); read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read.setTimeout(Math.max(1, (int) ((timeout.toNanos() + 999_999_999) / 1_000_000_000)));
        return Objects.requireNonNull(read.execute(status -> {
            if (!repository.lockLeaseForSettlement(actor, lease)) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Session source history requires the current run lease");
            var material = executionMaterial(actor, new GetAgentRunQuery(current.runId()));
            if (!current.equals(material.authorization()) || !sessionId.equals(material.run().sessionId()) || !current.deadlineAt().isAfter(now())) throw new AgentLedgerException(
                    AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Session source history does not match the current run");
            var history = repository.sessionContextHistory(actor, sessionId); var sources = new LinkedHashMap<List<String>, AgentContextSourceDTO>(); var invocations = new HashSet<String>();
            var external = Set.of(AgentContextSourceKindEnum.PROMPT, AgentContextSourceKindEnum.SKILL, AgentContextSourceKindEnum.RAG, AgentContextSourceKindEnum.MEMORY);
            for (var row : history.rows()) {
                var metadata = row.snapshot().metadata();
                if (sessionId.equals(metadata.nativeSessionId()) && !row.snapshot().capturedAt().isBefore(history.generation())) invocations.add(metadata.nativeInvocationId());
                for (var source : metadata.sources()) if (external.contains(source.kind())) {
                    var key = List.of(source.kind().name(), source.provenanceId()); var previous = sources.putIfAbsent(key, source);
                    if (previous != null && (!previous.sourceId().equals(source.sourceId()) || !previous.sourceVersion().equals(source.sourceVersion()) || !previous.sourceSha256().equals(source.sourceSha256())
                            || !previous.modelTextSha256().equals(source.modelTextSha256()) || previous.utf8Bytes() != source.utf8Bytes())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Historical source projections conflict");
                    if (sources.size() > 256) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REQUEST_TOO_LARGE, "Session has too many historical context sources");
                }
            }
            if (!repository.lockLeaseForSettlement(actor, lease) || !current.deadlineAt().isAfter(now())) throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Session source history lost its run fence");
            return new ContextHistory(List.copyOf(sources.values()), Set.copyOf(invocations), history.complete(), history.sharedState(), history.tracked());
        }));
    }

    public record ContextHistory(List<AgentContextSourceDTO> sources, Set<String> invocationIds, boolean complete, boolean sharedState, boolean tracked) {
        public ContextHistory { sources = List.copyOf(sources); invocationIds = Set.copyOf(invocationIds); if (sources.size() > 256 || invocationIds.size() > 4096) throw new IllegalArgumentException("Session context history is invalid"); }
        @Override public String toString() { return "SessionContextHistory[redacted]"; }
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
            var stored = repository.definitionEvidence(actor.tenantId(), specification.definitionKey(), specification.version())
                    .orElseThrow(AgentLedgerService::runtimeUnavailable);
            if (!stored.hash().equals(hash)) throw definitionConflict();
            return definition(json.readDefinition(stored.specification()), stored.hash());
        });
    }

    /**
     * Host-authorized local API-012 persistence. Dependency observation runs outside the database transaction.
     * The final predicate consults only bounded local proof and the supplied database time.
     */
    public AgentRuntimeTemplateDTO publishRuntimeTemplate(TrustedAgentActor actor, AgentRuntimeTemplateCommand request,
                                                         BooleanSupplier newAdmissionEnabled,
                                                         RuntimeTemplatePublicationAuthority authority) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent publication service required");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw runtimeUnavailable();
        if (request == null || !request.isTemplateConsistent()) throw invalid("Invalid Agent runtime template");
        byte[] normalized = request.canonicalBytes();
        if (normalized.length > AgentJsonCodec.MAX_SNAPSHOT_BYTES) throw invalid("Agent runtime template exceeds its representation");
        String specification = new String(normalized, StandardCharsets.UTF_8);
        var template = json.readRuntimeDefinition(specification);
        var replay = runtimePublicationReplay(actor, template);
        if (replay.isPresent()) return replay.get();

        Predicate<Instant> permission;
        try {
            requireTemplateAdmission(newAdmissionEnabled);
            permission = Objects.requireNonNull(authority).verify(actor, json.readRuntimeDefinition(specification));
            if (permission == null) throw runtimeUnavailable();
        } catch (RuntimeException unavailable) {
            var committed = runtimePublicationReplay(actor, template);
            if (committed.isPresent()) return committed.get();
            throw unavailable;
        }
        return transaction.execute(status -> {
            var prior = runtimePublicationReplay(actor, template);
            if (prior.isPresent()) return prior.get();
            requireTemplateAdmission(newAdmissionEnabled);
            var now = now();
            if (!permission.test(now)) throw runtimeUnavailable();
            repository.insertDefinitionFamily(actor, template.getTemplateKey(), now);
            int inserted = repository.insertDefinitionVersion(actor, template.getTemplateKey(), template.getTemplateVersion(),
                    template.contentSha256(), specification, now);
            if (inserted == 1) {
                requireTemplateAdmission(newAdmissionEnabled);
                if (!permission.test(now())) throw runtimeUnavailable();
            }
            return runtimePublicationReplay(actor, template).orElseThrow(AgentLedgerService::runtimeUnavailable);
        });
    }

    private Optional<AgentRuntimeTemplateDTO> runtimePublicationReplay(TrustedAgentActor actor, AgentRuntimeTemplateCommand template) {
        return repository.definitionEvidence(actor.tenantId(), template.getTemplateKey(), template.getTemplateVersion()).map(row -> {
            try {
                if (json.readObject(row.specification()).containsKey("runtimeProfile")) {
                    var stored = json.readRuntimeDefinition(row.specification());
                    if (!stored.contentSha256().equals(row.hash()) || !template.getTemplateKey().equals(stored.getTemplateKey())
                            || template.getTemplateVersion() != stored.getTemplateVersion()) throw runtimeUnavailable();
                    if (!template.contentSha256().equals(row.hash())) throw definitionConflict();
                    return new AgentRuntimeTemplateDTO(stored.getTemplateKey(), stored.getTemplateVersion(), row.hash(),
                            stored.getRuntimeProfile(), row.createdAt());
                }
                var existing = json.readDefinition(row.specification());
                if (!template.getTemplateKey().equals(existing.definitionKey()) || template.getTemplateVersion() != existing.version()
                        || !row.hash().equals(json.definitionHash(existing))) throw runtimeUnavailable();
                throw definitionConflict();
            } catch (IllegalArgumentException | IllegalStateException malformed) {
                throw runtimeUnavailable();
            }
        });
    }

    private static void requireTemplateAdmission(BooleanSupplier admission) {
        if (admission == null || !admission.getAsBoolean()) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "New Agent runtime template publication is disabled");
        }
    }

    @FunctionalInterface
    public interface RuntimeTemplatePublicationAuthority {
        Predicate<Instant> verify(TrustedAgentActor actor, AgentRuntimeTemplateCommand template);
    }

    private static AgentLedgerException definitionConflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEFINITION_VERSION_CONFLICT,
                "Definition version already exists with different content");
    }

    /** Reads one committed publication from the owning primary after the host authorizes publication evidence for the tenant. */
    public PublishedDefinitionEvidenceDTO publishedDefinitionEvidence(TrustedAgentActor actor, String definitionKey, long version) {
        return readVerifiedPublication(actor, definitionKey, version).evidence();
    }

    VerifiedPublishedDefinition readVerifiedPublication(TrustedAgentActor actor, String definitionKey, long version) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (definitionKey == null || definitionKey.isBlank() || definitionKey.length() > 128 || version <= 0
                || !definitionKey.equals(definitionKey.strip()) || definitionKey.chars().anyMatch(Character::isISOControl)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(definitionKey)) {
            throw invalid("Agent publication query is invalid");
        }
        if (Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw publicationUnavailable();
        }

        var read = new TransactionTemplate(transaction.getTransactionManager());
        // Select the primary before switching the physical transaction to read-only mode.
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
        try {
            return read.execute(status -> {
                Instant observedAt = repository.beginPrimaryReadObservation();
                if (observedAt == null) throw publicationUnavailable();
                var row = repository.definition(actor.tenantId(), definitionKey, version).orElseThrow(AgentLedgerService::notFound);
                var spec = row.specification();
                if (spec == null || !definitionKey.equals(spec.definitionKey()) || version != spec.version()
                        || !json.definitionHash(spec).equals(row.hash()) || Thread.currentThread().isInterrupted()) {
                    throw publicationUnavailable();
                }
                var dependencies = spec.dependencies();
                boolean optionalDependenciesPresent = !dependencies.prompts().isEmpty() || !dependencies.skills().isEmpty()
                        || !dependencies.tools().isEmpty();
                var evidence = new PublishedDefinitionEvidenceDTO(actor.tenantId(), definitionKey, version, row.hash(), spec.executionMode(),
                        spec.inputSchemaVersion(), spec.outputSchemaVersion(), dependencies.modelRouteRef(),
                        dependencies.policyVersionRef(), optionalDependenciesPresent, observedAt);
                return new VerifiedPublishedDefinition(evidence, spec);
            });
        } catch (DataAccessException | TransactionException | IllegalArgumentException | IllegalStateException failure) {
            throw publicationUnavailable();
        }
    }

    /** Immutable owner evidence for the stored definition and its declared references. */
    public record PublishedDefinitionEvidenceDTO(long tenantId, String definitionKey, long definitionVersion,
                                                 String definitionContentSha256, String executionMode,
                                                 String inputSchemaVersion, String outputSchemaVersion,
                                                 String modelRouteRef, String policyVersionRef,
                                                 boolean optionalDependenciesPresent, Instant observedAt) {
        @Override public String toString() { return "PublishedDefinitionEvidenceDTO[redacted]"; }
    }

    record VerifiedPublishedDefinition(PublishedDefinitionEvidenceDTO evidence, AgentDefinitionSpecDTO definition) {
        @Override public String toString() { return "VerifiedPublishedDefinition[redacted]"; }
    }

    private static AgentLedgerException publicationUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent publication evidence is unavailable");
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
                    now.plus(policy.totalTimeout()), snapshot, 0L, now);
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

    /** Lists the authenticated owner's exact correlation within a bounded primary snapshot. */
    public AgentRunPageDTO listByCorrelation(TrustedAgentActor actor, ListAgentRunsByCorrelationQuery query) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (query == null) throw invalid("Agent run correlation query is invalid");
        if (Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw correlationUnavailable();
        }

        String binding = json.resultHash("TIANJI_AGENT_RUN_CORRELATION_CURSOR_V1:"
                + json.write(List.of(actor.identityFingerprint(), query.correlationId(), query.fromInclusive().toString(),
                query.toExclusive().toString()), 4_096));
        var cursor = query.cursor() == null ? null : decodeCorrelationCursor(query.cursor(), binding, query);
        var read = new TransactionTemplate(transaction.getTransactionManager());
        // Route to the owning primary before setting physical read-only mode inside the transaction.
        read.setReadOnly(false);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
        try {
            return read.execute(status -> {
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || !TransactionSynchronizationManager.hasResource(dataSource)) {
                    throw correlationUnavailable();
                }
                Instant observedAt = repository.beginPrimaryReadObservation();
                if (observedAt == null) throw correlationUnavailable();
                if (query.toExclusive().isAfter(observedAt)) throw invalid("Agent run correlation window exceeds the source clock");
                if (cursor != null && !repository.hasCorrelationAnchor(actor, query, cursor.createdAt(), cursor.runId())) {
                    throw invalid("Agent run correlation cursor is invalid");
                }
                var rows = repository.correlationRuns(actor, query, cursor == null ? null : cursor.createdAt(),
                        cursor == null ? null : cursor.runId(), CORRELATION_PAGE_SIZE + 1);
                return correlationPage(rows, query, cursor, binding);
            });
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            throw correlationUnavailable();
        }
    }

    private static AgentRunPageDTO correlationPage(List<JdbcAgentLedgerRepository.CorrelationRunRow> rows,
                                                   ListAgentRunsByCorrelationQuery query, RunCursor cursor, String binding) {
        if (rows.size() > CORRELATION_PAGE_SIZE + 1) throw correlationUnavailable();
        var seen = new HashSet<String>();
        var previous = cursor;
        if (cursor != null) seen.add(cursor.runId());
        for (var row : rows) {
            var run = row.run();
            var created = run.createdAt();
            boolean ordered = previous == null || created.isBefore(previous.createdAt())
                    || created.equals(previous.createdAt()) && run.runId().compareTo(previous.runId()) < 0;
            if (!query.correlationId().equals(row.correlationId()) || !run.runId().equals(row.storedRunId())
                    || !correlationRunId(run.runId()) || !seen.add(run.runId()) || !ordered
                    || created.isBefore(query.fromInclusive()) || !created.isBefore(query.toExclusive())
                    || created.getNano() % 1_000 != 0) {
                throw correlationUnavailable();
            }
            previous = new RunCursor(created, run.runId());
        }
        var items = rows.stream().limit(CORRELATION_PAGE_SIZE)
                .map(JdbcAgentLedgerRepository.CorrelationRunRow::run).toList();
        String next = rows.size() > CORRELATION_PAGE_SIZE ? encodeCorrelationCursor(binding,
                items.getLast().createdAt(), items.getLast().runId()) : null;
        return new AgentRunPageDTO(items, next);
    }

    private static RunCursor decodeCorrelationCursor(String value, String binding, ListAgentRunsByCorrelationQuery query) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value.substring(3)), StandardCharsets.US_ASCII);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 3 || !binding.equals(fields[0]) || !correlationRunId(fields[2])) {
                throw new IllegalArgumentException();
            }
            Instant created = ListAgentRunsByCorrelationQuery.parseUtc(fields[1]);
            if (created.isBefore(query.fromInclusive()) || !created.isBefore(query.toExclusive())
                    || !value.equals(encodeCorrelationCursor(binding, created, fields[2]))) {
                throw new IllegalArgumentException();
            }
            return new RunCursor(created, fields[2]);
        } catch (IllegalArgumentException malformed) {
            throw invalid("Agent run correlation cursor is invalid");
        }
    }

    private static String encodeCorrelationCursor(String binding, Instant createdAt, String runId) {
        String value = binding + "|" + createdAt + "|" + runId;
        return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean correlationRunId(String runId) {
        return runId.matches("[A-Za-z0-9_-]{1,64}");
    }

    private static AgentLedgerException correlationUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent run correlation is unavailable");
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

    public boolean modelUsageEnabled() {
        return !modelUsageRoutes.isEmpty();
    }

    public boolean acceptsModelUsage(String executionMode, String routeRef) {
        return "CONFIG".equals(executionMode) && routeRef != null && modelUsageRoutes.contains(routeRef);
    }

    public boolean usesModelUsageRoutes(List<String> exactRouteRefs) {
        return modelUsageEnabled() && modelUsageRoutes.equals(exactRouteRefs);
    }

    public AgentModelUsageRunDTO getModelUsage(TrustedAgentActor actor, GetAgentRunQuery query, String afterReservationId) {
        requireModelUsageReader(actor);
        if (query == null || !modelUsageText(query.runId(), 64)
                || afterReservationId != null && !modelUsageText(afterReservationId, 128)) {
            throw invalid("Agent model usage query is invalid");
        }
        return modelUsageRead(() -> {
            Instant observedAt = now();
            var owned = repository.modelUsageRun(actor, query.runId()).orElseThrow(AgentLedgerService::notFound);
            if (!query.runId().equals(owned.runId())) throw modelUsageUnavailable();
            return projectModelUsage(actor, owned, observedAt, afterReservationId, 100);
        });
    }

    public AgentModelUsagePageDTO listModelUsageByCorrelation(TrustedAgentActor actor, AgentModelUsageCorrelationQuery query) {
        requireModelUsageReader(actor);
        if (query == null) throw invalid("Agent model usage query is invalid");
        String binding = json.resultHash("TIANJI_AGENT_MODEL_USAGE_CURSOR_V1:"
                + json.write(List.of(actor.identityFingerprint(), query.correlationId(), query.fromInclusive().toString(),
                query.toExclusive().toString()), 4_096));
        var cursor = query.cursor();
        if (cursor != null && (!binding.equals(cursor.bindingSha256()) || cursor.createdAt().isBefore(query.fromInclusive())
                || !cursor.createdAt().isBefore(query.toExclusive()))) {
            throw invalid("Agent model usage cursor is invalid");
        }
        return modelUsageRead(() -> {
            Instant observedAt = now();
            if (query.toExclusive().isAfter(observedAt)) throw invalid("Agent model usage window exceeds the source clock");
            var rows = repository.modelUsageRuns(actor, query.correlationId(), query.fromInclusive(), query.toExclusive(),
                    cursor == null ? null : cursor.createdAt(), cursor == null ? null : cursor.runId(), 21);
            if (rows.size() > 21) throw modelUsageUnavailable();
            var accepted = rows.subList(0, Math.min(rows.size(), 20));
            var results = accepted.stream().map(row -> {
                if (!query.correlationId().equals(row.correlationId()) || row.createdAt() == null
                        || row.createdAt().isBefore(query.fromInclusive()) || !row.createdAt().isBefore(query.toExclusive())) {
                    throw modelUsageUnavailable();
                }
                return projectModelUsage(actor, row, observedAt, null, 5);
            }).toList();
            var next = rows.size() > 20
                    ? new AgentModelUsageCursorDTO(accepted.getLast().createdAt(), accepted.getLast().runId(), binding) : null;
            return new AgentModelUsagePageDTO(results, next, observedAt);
        });
    }

    private void requireModelUsageReader(TrustedAgentActor actor) {
        if (!modelUsageEnabled()) throw modelUsageDisabled();
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Agent model usage must own its snapshot transaction");
        }
    }

    private <T> T modelUsageRead(java.util.function.Supplier<T> operation) {
        var read = new TransactionTemplate(transaction.getTransactionManager());
        // As in terminalReference, this is a SELECT-only path pinned to the owning primary.
        read.setReadOnly(false);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
        try {
            return Objects.requireNonNull(read.execute(status -> operation.get()));
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            throw modelUsageUnavailable();
        }
    }

    private AgentModelUsageRunDTO projectModelUsage(TrustedAgentActor actor, JdbcAgentLedgerRepository.ModelUsageRunRow run,
                                                  Instant observedAt, String afterReservationId, int limit) {
        if (!modelUsageText(run.runId(), 64) || run.createdAt() == null) throw modelUsageUnavailable();
        var rows = repository.modelUsageSlots(actor, run.runId(), afterReservationId, limit + 1);
        if (rows.size() > limit + 1) throw modelUsageUnavailable();
        var items = rows.stream().limit(limit).map(slot -> projectModelUsageSlot(run, slot, observedAt)).toList();
        String next = rows.size() > limit ? items.getLast().reservationId() : null;
        return new AgentModelUsageRunDTO(run.runId(), run.correlationId(), run.createdAt(), observedAt, items, next);
    }

    static AgentModelUsageSlotDTO projectModelUsageSlot(JdbcAgentLedgerRepository.ModelUsageRunRow run,
                                                        JdbcAgentLedgerRepository.ModelUsageSlotRow slot, Instant observedAt) {
        if (!modelUsageText(slot.reservationId(), 128) || slot.reservationEpoch() < 1 || slot.reservedAt() == null) {
            throw modelUsageUnavailable();
        }
        if (!slot.tracked()) {
            return new AgentModelUsageSlotDTO(slot.reservationId(), null, null, AgentModelUsageStateEnum.NOT_INSTRUMENTED,
                    null, null, slot.reservedAt(), null);
        }
        if (slot.usageEpoch() == null || slot.usageEpoch() != slot.reservationEpoch() || slot.createdAt() == null
                || !modelUsageText(slot.modelRouteRef(), 256) || !modelUsageText(slot.modelName(), 512)) throw modelUsageUnavailable();

        AgentModelUsageStateEnum state;
        try {
            state = AgentModelUsageStateEnum.valueOf(slot.state());
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw modelUsageUnavailable();
        }
        String reason = slot.safeReason();
        AgentUsageDTO actual = null;
        switch (state) {
            case NOT_INSTRUMENTED -> throw modelUsageUnavailable();
            case PENDING -> {
                requireNoActualUsage(slot);
                if (reason != null) throw modelUsageUnavailable();
                if (!"RESERVED".equals(slot.budgetState()) || !"RUNNING".equals(run.status())
                        || run.leaseEpoch() != slot.reservationEpoch() || !run.hasLeaseOwner() || run.leaseExpiresAt() == null
                        || !run.leaseExpiresAt().isAfter(observedAt)) {
                    state = AgentModelUsageStateEnum.UNKNOWN;
                    reason = "RESERVATION_ENDED";
                }
            }
            case UNKNOWN -> {
                requireNoActualUsage(slot);
                if (reason == null || !Set.of("MISSING_USAGE", "INVALID_USAGE", "MODEL_ERROR").contains(reason)) {
                    throw modelUsageUnavailable();
                }
            }
            case PRICED, UNPRICED -> actual = projectActualModelUsage(state, slot);
        }
        return new AgentModelUsageSlotDTO(slot.reservationId(), slot.modelRouteRef(), slot.modelName(), state, actual,
                reason, slot.createdAt(), slot.measuredAt());
    }

    private static void requireNoActualUsage(JdbcAgentLedgerRepository.ModelUsageSlotRow slot) {
        if (slot.inputTokens() != null || slot.outputTokens() != null || slot.actualCost() != null || slot.measuredAt() != null) {
            throw modelUsageUnavailable();
        }
    }

    private static AgentUsageDTO projectActualModelUsage(AgentModelUsageStateEnum state,
                                                        JdbcAgentLedgerRepository.ModelUsageSlotRow slot) {
        Long input = slot.inputTokens(), output = slot.outputTokens();
        if (input == null || output == null || input < 0 || output < 0 || Long.MAX_VALUE - input < output
                || slot.measuredAt() == null || slot.measuredAt().isBefore(slot.createdAt())) throw modelUsageUnavailable();
        boolean hasPrice = slot.currency() != null && slot.currency().matches("[A-Z]{3}")
                && slot.priceVersionRef() != null && slot.priceVersionRef().matches("[0-9a-f]{64}");
        if (state == AgentModelUsageStateEnum.PRICED) {
            if (!hasPrice || slot.safeReason() != null || slot.actualCost() == null || slot.actualCost().signum() < 0) {
                throw modelUsageUnavailable();
            }
        } else {
            boolean missing = "PRICE_MISSING".equals(slot.safeReason()) && slot.currency() == null && slot.priceVersionRef() == null;
            boolean overflow = "PRICE_OVERFLOW".equals(slot.safeReason()) && hasPrice;
            if ((!missing && !overflow) || slot.actualCost() != null) throw modelUsageUnavailable();
        }
        return new AgentUsageDTO(AgentUsageKindEnum.ACTUAL, input, output, input + output, slot.actualCost(),
                slot.currency(), slot.priceVersionRef());
    }

    private static boolean modelUsageText(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit && value.equals(value.trim())
                && value.codePoints().noneMatch(c -> c < 32 || c == 127
                || c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE);
    }

    private static boolean modelUsageTime(Instant value) {
        return value != null && !value.isBefore(Instant.EPOCH)
                && !value.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
                && value.equals(value.truncatedTo(ChronoUnit.MICROS));
    }

    private static AgentLedgerException modelUsageDisabled() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE, "Agent model usage is not enabled");
    }

    private static AgentLedgerException modelUsageUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent model usage is unavailable");
    }

    public record AgentModelUsageCorrelationQuery(String correlationId, Instant fromInclusive, Instant toExclusive,
                                                  AgentModelUsageCursorDTO cursor) {
        public AgentModelUsageCorrelationQuery {
            if (!modelUsageText(correlationId, 128) || !modelUsageTime(fromInclusive) || !modelUsageTime(toExclusive)
                    || !fromInclusive.isBefore(toExclusive) || Duration.between(fromInclusive, toExclusive).compareTo(Duration.ofDays(31)) > 0) {
                throw invalid("Agent model usage correlation query is invalid");
            }
        }
    }

    public record AgentModelUsageCursorDTO(Instant createdAt, String runId, String bindingSha256) {
        public AgentModelUsageCursorDTO {
            if (!modelUsageTime(createdAt) || !modelUsageText(runId, 64)
                    || bindingSha256 == null || !bindingSha256.matches("[0-9a-f]{64}")) {
                throw invalid("Agent model usage cursor is invalid");
            }
        }
    }

    public enum AgentModelUsageStateEnum { NOT_INSTRUMENTED, PENDING, UNKNOWN, UNPRICED, PRICED }

    public record AgentModelUsageSlotDTO(String reservationId, String modelRouteRef, String modelName,
                                        AgentModelUsageStateEnum state, AgentUsageDTO usage, String safeReason,
                                        Instant createdAt, Instant measuredAt) { }

    public record AgentModelUsageRunDTO(String runId, String correlationId, Instant createdAt, Instant observedAt,
                                       List<AgentModelUsageSlotDTO> slots, String nextReservationId) {
        public AgentModelUsageRunDTO { slots = List.copyOf(slots); }
    }

    public record AgentModelUsagePageDTO(List<AgentModelUsageRunDTO> runs, AgentModelUsageCursorDTO nextCursor,
                                        Instant observedAt) {
        public AgentModelUsagePageDTO { runs = List.copyOf(runs); }
    }

    public Optional<AgentExecutionResult> getResult(TrustedAgentActor actor, GetAgentRunQuery query) {
        return transaction.execute(status -> {
            var stored = requireRun(actor, query);
            if (stored.resultJson() == null) return Optional.empty();
            if (runtimeProfile(stored) == RunProfileEnum.LEGACY_V1) return Optional.of(json.readResult(stored.resultJson()));
            var verified = verifyRuntime(actor, stored);
            var result = readRuntimeTerminal(stored, verified);
            // The old public DTO has a string artifact slot and cannot expose the V2 reference faithfully.
            return Optional.of(new AgentExecutionResult(stored.run().runId(), result.status(),
                    result.output() == null ? Map.of() : result.output(), null, null, null, result.errorCode(),
                    result.sideEffectOutcome().wire()));
        });
    }

    public AgentExecutionSnapshot snapshot(TrustedAgentActor actor, GetAgentRunQuery query) {
        return verifiedSnapshot(actor, requireRun(actor, query));
    }

    public enum RunProfileEnum { LEGACY_V1, RUNTIME_V2 }

    public RunProfileEnum verifyPersistedProfile(TrustedAgentActor actor, GetAgentRunQuery query) {
        return transaction.execute(status -> {
            var stored = requireLockedRun(actor, query);
            return verifyRunProfile(actor, stored).profile();
        });
    }

    /** Trusted internal entry point called within the linked-submission fence transaction. */
    public AgentRunDTO startLinkedRuntimeV2(TrustedAgentActor actor, LinkedAgentRuntimeCommand command) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new IllegalStateException("Linked Agent runtime submission requires its owning service transaction");
        }
        var input = json.immutableRuntimeCommand(command);
        requireRuntimeCommand(input);
        String requestSha256 = json.runtimeRequestHash(input);
        var existing = repository.byIdempotencyKey(actor, input.getIdempotencyKey());
        if (existing.isPresent()) return replayRuntimeStart(actor, existing.get(), requestSha256);

        if (terminalPublisher != null && terminalPublisher.runtimeDestination(actor, input.getLink().sourceId()) == null) throw runtimeUnavailable();
        requireDispatchTime(input.getLink());
        var template = runtimeTemplate(actor, input.getTemplateKey(), input.getTemplateVersion());
        validateRuntimePins(input, template);
        var createdAt = now();
        if (!input.getLink().dispatchNotAfter().isAfter(createdAt)) throw dispatchExpired();
        Instant effectiveDeadline = runtimeDeadline(createdAt, input);
        if (!effectiveDeadline.isAfter(createdAt)) throw dispatchExpired();
        String runId = UUID.randomUUID().toString();
        var snapshot = json.runtimeSnapshot(actor.identityFingerprint(), runId, input, template);
        int inserted = repository.insertRun(actor, runId, input.getTemplateKey(), input.getTemplateVersion(),
                input.getIdempotencyKey(), requestSha256, null, input.getCorrelationId(), effectiveDeadline,
                json.writeRuntimeSnapshot(snapshot), 1L, createdAt);
        if (inserted != 1) return replayRuntimeStart(actor,
                repository.byIdempotencyKey(actor, input.getIdempotencyKey()).orElseThrow(AgentLedgerService::runtimeUnavailable), requestSha256);
        runtimeBindings.create(actor, new RuntimeBindingBO().setTenantId(actor.tenantId()).setRunId(runId).setProfile("RUNTIME_V2")
                .setSnapshotSha256(snapshot.snapshotSha256()).setTemplateSha256(input.getTemplateSha256())
                .setConfigurationSha256(input.getConfigurationSha256()).setNodeExecutionRef(input.getNodeExecutionRef())
                .setAttemptDeadlineAt(effectiveDeadline).setPolicy(input.getLimits()));
        repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(), runId, 1,
                "RUN_QUEUED", null, null, null, createdAt, input.getCorrelationId(), "1", Map.of("status", "QUEUED")));
        requireDispatchTime(input.getLink());
        return requireRun(actor, new GetAgentRunQuery(runId)).run();
    }

    private AgentRunDTO replayRuntimeStart(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun existing, String requestSha256) {
        sameRequest(existing, requestSha256);
        var stored = requireLockedRun(actor, new GetAgentRunQuery(existing.run().runId()));
        verifyRuntime(actor, stored);
        return stored.run();
    }

    public RuntimeExecutionMaterialVO runtimeExecutionMaterial(TrustedAgentActor actor, GetAgentRunQuery query) {
        return transaction.execute(status -> runtimeMaterial(actor, requireLockedRun(actor, query)));
    }

    public ClaimedAgentRun claimRuntimeV2(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                         String owner, Duration leaseDuration) {
        return claim(actor, query, expectedRevision, owner, leaseDuration, RunProfileEnum.RUNTIME_V2);
    }

    public record RuntimeExecutionMaterialVO(AgentRunDTO run, AgentRuntimeTemplateCommand template,
                                             LinkedAgentRuntimeCommand command, String snapshotSha256,
                                             AgentRunAuthorization.Request authorization) {
        @Override public String toString() { return "AgentLedgerService.RuntimeExecutionMaterialVO[redacted]"; }
    }

    public record LinkedRunEvidenceVO(AgentRunDTO run, AgentProcessLink processLink, AgentRunAuthorization.Request authorization) {
        @Override public String toString() { return "AgentLedgerService.LinkedRunEvidenceVO[redacted]"; }
    }

    public record ToolRunEvidenceVO(AgentRunDTO run, AgentRunAuthorization.Request authorization,
                                    AgentProcessLink processLink, List<DefinitionResourceRef> toolRefs,
                                    int maxToolCalls, String legacyApprovalPolicyRef, RunProfileEnum profile,
                                    AgentRunLease activeLease) {
        public ToolRunEvidenceVO {
            toolRefs = List.copyOf(toolRefs);
        }

        @Override public String toString() { return "AgentLedgerService.ToolRunEvidenceVO[redacted]"; }
    }

    /** Reads the fixed tool scope and live lease under the owned run fence. */
    public ToolRunEvidenceVO toolRunEvidence(TrustedAgentActor actor, GetAgentRunQuery query) {
        var read = new TransactionTemplate(transaction.getTransactionManager());
        read.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        read.setTimeout(5);
        return read.execute(status -> {
            var stored = requireLockedRun(actor, query);
            var profile = runtimeProfile(stored);
            if (profile == RunProfileEnum.RUNTIME_V2) {
                var material = runtimeMaterial(actor, stored);
                return new ToolRunEvidenceVO(material.run(), material.authorization(), material.command().getLink(),
                        runtimeToolRefs(material.template()), material.command().getLimits().maxToolCalls(), null,
                        profile, activeToolLease(stored));
            }
            var material = executionMaterial(actor, stored);
            var snapshot = material.snapshot();
            var policy = snapshot.effectivePolicy();
            var tools = snapshot.definition().dependencies().tools().stream()
                    .filter(ref -> policy.allowedToolVersions().contains(ref.key() + '@' + ref.version()))
                    .toList();
            return new ToolRunEvidenceVO(material.run(), material.authorization(), snapshot.processLink(),
                    exactToolRefs(tools), policy.maxToolCalls(), policy.approvalPolicyRef(), profile, activeToolLease(stored));
        });
    }

    private AgentRunLease activeToolLease(JdbcAgentLedgerRepository.StoredRun stored) {
        var run = stored.run();
        if (run.status() != AgentRunStatusEnum.RUNNING || run.cancelRequested()) return null;
        var clock = now();
        if (stored.leaseExpiresAt() == null || !stored.leaseExpiresAt().isAfter(clock)
                || !run.deadlineAt().isAfter(clock)) return null;
        return new AgentRunLease(run.runId(), stored.leaseOwner(), stored.leaseEpoch(), stored.leaseExpiresAt());
    }

    static List<DefinitionResourceRef> runtimeToolRefs(AgentRuntimeTemplateCommand template) {
        var config = template.getConfiguration();
        var plan = config.agenticPlan();
        var refs = plan == null ? config.toolRefs().stream()
                : plan.agents().stream().flatMap(leaf -> leaf.toolRefs().stream());
        return exactToolRefs(refs.map(AgentLedgerService::toolRef).toList());
    }

    private static DefinitionResourceRef toolRef(TemplateDependencyRefVO ref) {
        return new DefinitionResourceRef(ref.key(), ref.version(), ref.contentSha256());
    }

    private static List<DefinitionResourceRef> exactToolRefs(List<DefinitionResourceRef> refs) {
        var tools = new LinkedHashMap<ToolVersionVO, DefinitionResourceRef>();
        for (var ref : refs) {
            var previous = tools.putIfAbsent(new ToolVersionVO(ref.key(), ref.version()), ref);
            if (previous != null && !previous.equals(ref)) throw runtimeUnavailable();
        }
        return List.copyOf(tools.values());
    }

    private record ToolVersionVO(String key, long version) { }

    public LinkedRunEvidenceVO linkedRunEvidence(TrustedAgentActor actor, GetAgentRunQuery query) {
        return transaction.execute(status -> {
            var stored = requireLockedRun(actor, query);
            if (runtimeProfile(stored) == RunProfileEnum.RUNTIME_V2) {
                var material = runtimeMaterial(actor, stored);
                return new LinkedRunEvidenceVO(material.run(), material.command().getLink(), material.authorization());
            }
            var legacy = executionMaterial(actor, stored);
            return new LinkedRunEvidenceVO(legacy.run(), legacy.snapshot().processLink(), legacy.authorization());
        });
    }

    private RuntimeExecutionMaterialVO runtimeMaterial(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        var pinned = verifyRuntime(actor, stored).snapshot();
        var run = stored.run();
        var authorization = new AgentRunAuthorization.Request(actor, run.runId(), run.definitionKey(), run.definitionVersion(),
                pinned.command().getTemplateSha256(), stored.requestHash(), pinned.snapshotSha256(),
                pinned.command().getConfigurationSha256(), false, run.deadlineAt());
        return new RuntimeExecutionMaterialVO(run, pinned.template(), pinned.command(), pinned.snapshotSha256(), authorization);
    }

    private VerifiedRunProfileVO verifyRunProfile(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        if (runtimeProfile(stored) == RunProfileEnum.RUNTIME_V2) return verifyRuntime(actor, stored);
        verifiedSnapshot(actor, stored);
        return new VerifiedRunProfileVO(RunProfileEnum.LEGACY_V1, null, null);
    }

    private RunProfileEnum runtimeProfile(JdbcAgentLedgerRepository.StoredRun stored) {
        try {
            return json.persistedProfile(stored.snapshotJson());
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            throw runtimeUnavailable();
        }
    }

    private VerifiedRunProfileVO verifyRuntime(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        try {
            if (actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || runtimeProfile(stored) != RunProfileEnum.RUNTIME_V2) {
                throw runtimeUnavailable();
            }
            var snapshot = json.readRuntimeSnapshot(stored.snapshotJson());
            var run = stored.run();
            var command = snapshot.command();
            var binding = runtimeBindings.lockBinding(actor.tenantId(), run.runId());
            if (!actor.identityFingerprint().equals(snapshot.ownerSha256()) || !run.runId().equals(snapshot.runId())
                    || !run.definitionKey().equals(command.getTemplateKey()) || run.definitionVersion() != command.getTemplateVersion()
                    || run.sessionId() != null || !Objects.equals(stored.correlationId(), command.getCorrelationId())
                    || !run.deadlineAt().equals(runtimeDeadline(run.createdAt(), command))
                    || !stored.requestHash().equals(json.runtimeRequestHash(command))
                    || !snapshot.snapshotSha256().equals(binding.getSnapshotSha256())
                    || !command.getTemplateSha256().equals(binding.getTemplateSha256())
                    || !command.getConfigurationSha256().equals(binding.getConfigurationSha256())
                    || !command.getLimits().equals(binding.getPolicy())
                    || !command.getNodeExecutionRef().equals(binding.getNodeExecutionRef())
                    || !run.deadlineAt().equals(binding.getAttemptDeadlineAt())
                    || !snapshot.template().contentSha256().equals(command.getTemplateSha256())) throw runtimeUnavailable();
            var template = runtimeTemplate(actor, run.definitionKey(), run.definitionVersion());
            if (!snapshot.template().equals(template)) throw runtimeUnavailable();
            validateRuntimePins(command, template);
            return new VerifiedRunProfileVO(RunProfileEnum.RUNTIME_V2, snapshot, binding);
        } catch (DataAccessException | IllegalArgumentException | IllegalStateException malformed) {
            throw runtimeUnavailable();
        }
    }

    AgentRuntimeTemplateCommand runtimeTemplate(TrustedAgentActor actor, String key, long version) {
        var row = repository.definitionEvidence(actor.tenantId(), key, version).orElseThrow(AgentLedgerService::notFound);
        try {
            var template = json.readRuntimeDefinition(row.specification());
            if (!key.equals(template.getTemplateKey()) || version != template.getTemplateVersion()
                    || !row.hash().equals(template.contentSha256())) throw runtimeUnavailable();
            return template;
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            throw runtimeUnavailable();
        }
    }

    public AgentRuntimeTemplateCommand readVerifiedRuntimeTemplate(TrustedAgentActor actor, String key, long version,
                                                                  String expectedContentSha256) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Verified Agent owner is required");
        }
        if (!wireId(key, "[A-Za-z][A-Za-z0-9_.:-]{0,127}") || version < 1 || version > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !wireId(expectedContentSha256, "[0-9a-f]{64}")) throw invalid("Agent runtime template query is invalid");
        if (TransactionSynchronizationManager.isActualTransactionActive() || Thread.currentThread().isInterrupted()) {
            throw runtimeUnavailable();
        }
        var read = new TransactionTemplate(transaction.getTransactionManager());
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(2);
        try {
            return read.execute(status -> {
                if (repository.beginPrimaryReadObservation() == null) throw runtimeUnavailable();
                var template = runtimeTemplate(actor, key, version);
                if (!expectedContentSha256.equals(template.contentSha256())) throw runtimeUnavailable();
                return template;
            });
        } catch (DataAccessException | TransactionException unavailable) {
            throw runtimeUnavailable();
        }
    }

    private static void requireRuntimeCommand(LinkedAgentRuntimeCommand command) {
        if (!command.isLinkedRuntimeConsistent() || !wireId(command.getIdempotencyKey(), "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                || !wireId(command.getNodeExecutionRef(), "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || !wireId(command.getTemplateKey(), "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || !wireId(command.getCorrelationId(), "[A-Za-z][A-Za-z0-9_.:-]{0,127}")
                || command.getTemplateVersion() < 1 || command.getTemplateVersion() > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                || !wireId(command.getTemplateSha256(), "[0-9a-f]{64}")
                || !wireId(command.getConfigurationSha256(), "[0-9a-f]{64}")
                || !wireId(command.getInputSchemaSha256(), "[0-9a-f]{64}")
                || !wireId(command.getOutputSchemaSha256(), "[0-9a-f]{64}")
                || command.getLimits() == null) throw invalid("Linked Agent runtime command is invalid");
    }

    private static boolean wireId(String value, String pattern) {
        return value != null && value.matches(pattern);
    }

    private static Instant runtimeDeadline(Instant createdAt, LinkedAgentRuntimeCommand command) {
        Instant attemptDeadline = command.getAttemptDeadlineAt();
        Duration remaining = Duration.between(createdAt, attemptDeadline);
        Duration maxDuration = command.getLimits().totalTimeout();
        return remaining.compareTo(maxDuration) <= 0 ? attemptDeadline : createdAt.plus(maxDuration).truncatedTo(ChronoUnit.MICROS);
    }

    private void validateRuntimePins(LinkedAgentRuntimeCommand command, AgentRuntimeTemplateCommand template) {
        if (!template.contentSha256().equals(command.getTemplateSha256())
                || !command.getLink().definitionContentSha256().equals(command.getTemplateSha256())
                || !json.runtimeSchemaHash(template.getInputSchema()).equals(command.getInputSchemaSha256())
                || !json.runtimeSchemaHash(template.getOutputSchema()).equals(command.getOutputSchemaSha256())) {
            throw runtimeUnavailable();
        }
        var limits = command.getLimits();
        var maximum = template.getLimits();
        if (limits.totalTimeout().compareTo(maximum.totalTimeout()) > 0
                || limits.modelIdleTimeout().compareTo(maximum.modelIdleTimeout()) > 0
                || limits.toolTimeout().compareTo(maximum.toolTimeout()) > 0 || limits.maxModelCalls() > maximum.maxModelCalls()
                || limits.maxToolCalls() > maximum.maxToolCalls() || limits.maxParallelism() > maximum.maxParallelism()
                || limits.contextWindowTokens() > maximum.contextWindowTokens()
                || limits.maxOutputTokensPerCall() > maximum.maxOutputTokensPerCall()) throw invalid("Agent runtime limits exceed the published template");
        requireCanonicalValue(template.getInputSchema().normalizeValue(command.getInput()), command.getInput());
        requireCanonicalValue(template.getParameterSchema().normalizeValue(command.getParameters()), command.getParameters());
    }

    private static void requireCanonicalValue(Object normalized, Object input) {
        if (!java.util.Arrays.equals(ProcessTypedJson.canonicalBytes(normalized), ProcessTypedJson.canonicalBytes(input))) {
            throw invalid("Agent runtime input is not canonical for the published template");
        }
    }

    private record VerifiedRunProfileVO(RunProfileEnum profile, AgentJsonCodec.RuntimeSnapshotVO snapshot,
                                        RuntimeBindingBO binding) { }

    /** Reads the run, execution input and its authorization binding from the same stored row. */
    public ExecutionMaterial executionMaterial(TrustedAgentActor actor, GetAgentRunQuery query) {
        return executionMaterial(actor, requireRun(actor, query));
    }

    private ExecutionMaterial executionMaterial(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
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
        if (runtimeProfile(run) != RunProfileEnum.LEGACY_V1) throw new RuntimeProfileRequiredException();
        AgentExecutionSnapshot snapshot;
        JdbcAgentLedgerRepository.DefinitionRow definition;
        try {
            snapshot = json.readSnapshot(run.snapshotJson());
            definition = repository.definition(actor.tenantId(), run.run().definitionKey(), run.run().definitionVersion())
                    .orElseThrow(AgentLedgerService::runtimeUnavailable);
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            throw runtimeUnavailable();
        }
        if (!snapshot.definition().definitionKey().equals(run.run().definitionKey())
                || snapshot.definition().version() != run.run().definitionVersion()
                || !definition.hash().equals(snapshot.definitionContentSha256())
                || !definition.hash().equals(json.definitionHash(definition.specification()))
                || !definition.hash().equals(json.definitionHash(snapshot.definition()))) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                    "Agent execution snapshot does not match its definition");
        }
        return snapshot;
    }

    static final class RuntimeProfileRequiredException extends AgentLedgerException {
        private RuntimeProfileRequiredException() {
            super(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent runtime execution evidence is unavailable");
        }
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
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            verifyRunProfile(actor, previous);
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
        return claim(actor, query, expectedRevision, owner, leaseDuration, RunProfileEnum.LEGACY_V1);
    }

    private ClaimedAgentRun claim(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                  String owner, Duration leaseDuration, RunProfileEnum expectedProfile) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        long leaseMillis = leaseMillis(leaseDuration);
        if (owner == null || owner.isBlank() || owner.length() > 128 || expectedRevision < 0) {
            throw invalid("invalid Agent run claim");
        }
        String normalizedOwner = owner.trim();
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            if (verifyRunProfile(actor, previous).profile() != expectedProfile) throw runtimeUnavailable();
            JdbcAgentLedgerRepository.StoredRun claimed = repository.claim(actor, query.runId(), expectedRevision,
                    normalizedOwner, leaseMillis).orElseThrow(() -> new AgentLedgerException(
                            AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent run cannot be claimed"));
            Instant claimRecordedAt = appendStatusEvent(actor, previous, claimed, AgentRunStatusEnum.QUEUED,
                    AgentRunStatusEnum.RUNNING, null);
            var response = new ClaimedAgentRun(claimed.run(), new AgentRunLease(query.runId(), normalizedOwner,
                    claimed.leaseEpoch(), claimed.leaseExpiresAt()));
            observeInitialClaim(previous.run(), claimed.run(), claimRecordedAt);
            return response;
        });
    }

    private void observeInitialClaim(AgentRunDTO previous, AgentRunDTO claimed, Instant claimRecordedAt) {
        if (initialClaimTiming == null || previous.status() != AgentRunStatusEnum.QUEUED
                || claimed.status() != AgentRunStatusEnum.RUNNING || previous.attempt() != 0 || claimed.attempt() != 1
                || !previous.createdAt().equals(claimed.createdAt()) || claimRecordedAt.isBefore(previous.createdAt())
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.hasResource(dataSource)) return;

        Duration elapsed = Duration.between(previous.createdAt(), claimRecordedAt);
        try {
            elapsed.toNanos(); // The metrics backend represents the recorded duration in nanoseconds.
        } catch (ArithmeticException unrepresentable) {
            return;
        }
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        try {
                            initialClaimTiming.accept(elapsed);
                        } catch (RuntimeException unavailable) {
                            reportInitialClaimTimingUnavailable();
                        }
                    }
                }
            });
        } catch (RuntimeException unavailable) {
            reportInitialClaimTimingUnavailable();
        }
    }

    private void reportInitialClaimTimingUnavailable() {
        if (initialClaimTimingFailureReported.compareAndSet(false, true)) {
            log.warn("Agent initial claim timing observation unavailable");
        }
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

    /** Refreshes the exact current generation before the next bounded runtime call. */
    public AgentRunLease renewRuntimeLease(TrustedAgentActor actor, AgentRunLease lease, Duration leaseDuration) {
        return transaction.execute(status -> {
            var stored = requireLockedRun(actor, new GetAgentRunQuery(lease.runId()));
            verifyRuntime(actor, stored);
            var renewed = repository.renew(actor, lease, leaseMillis(leaseDuration)).orElseThrow(() ->
                    new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent Runtime lease is no longer current"));
            return new AgentRunLease(lease.runId(), lease.owner(), lease.epoch(), renewed.leaseExpiresAt());
        });
    }

    private static final ProcessSchemaVO RUNTIME_ARTIFACT_SCHEMA = ProcessSchemaVO.fromWire(Map.of(
            "type", "object", "additionalProperties", false,
            "properties", Map.of(
                    "resourceKey", Map.of("type", "string", "maxLength", 128, "pattern", "^[A-Za-z][A-Za-z0-9_.:-]{0,127}$"),
                    "contentSha256", Map.of("type", "string", "maxLength", 64, "pattern", "^[0-9a-f]{64}$"),
                    "mediaType", Map.of("type", "string", "maxLength", 127, "pattern", "^[a-z0-9.+-]{1,63}/[a-z0-9.+-]{1,63}$"),
                    "sizeBytes", Map.of("type", "integer", "minimum", 0, "maximum", ProcessTypedJson.V2_MAX_WIRE_INTEGER)),
            "required", List.of("resourceKey", "contentSha256", "mediaType", "sizeBytes")));
    private static final Duration ARTIFACT_PROOF_BUDGET = Duration.ofSeconds(3);
    private static final int ARTIFACT_PROOF_ATTEMPTS = 2;
    private static final String INVALID_RUNTIME_OUTPUT = "INVALID_OUTPUT";
    private static final AgentResultArtifactEvidenceVO ARTIFACT_UNAVAILABLE =
            new AgentResultArtifactEvidenceVO(DecisionEnum.UNAVAILABLE, null, null, null, null);

    public static boolean runtimeArtifactOutput(ProcessSchemaVO schema) {
        return "object".equals(schema.type()) && Boolean.FALSE.equals(schema.additionalProperties())
                && RUNTIME_ARTIFACT_SCHEMA.properties().equals(schema.properties())
                && Set.copyOf(RUNTIME_ARTIFACT_SCHEMA.required()).equals(Set.copyOf(schema.required()));
    }

    /** Commits a terminal result only for the current, unexpired lease. */
    public AgentRunDTO finish(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision, AgentExecutionResult result) {
        return finish(actor, lease, expectedRevision, result, null);
    }

    /** Resolves the original candidate once or twice under one bounded, transaction-free PREPARE window. */
    public AgentRunDTO finishRuntimeArtifact(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision,
                                            AgentExecutionResult result) {
        validateFinish(actor, lease, expectedRevision, result);
        if (result.status() != AgentRunStatusEnum.COMPLETED || TransactionSynchronizationManager.isActualTransactionActive()) {
            throw runtimeUnavailable();
        }
        var query = new GetAgentRunQuery(lease.runId());
        var observation = transaction.execute(status -> {
            var stored = requireLockedRun(actor, query);
            var verified = verifyRuntime(actor, stored);
            var schema = verified.snapshot().template().getOutputSchema();
            if (!runtimeArtifactOutput(schema)) throw runtimeUnavailable();
            var current = now();
            if (stored.run().revision() != expectedRevision) throw revisionConflict();
            requireCurrentArtifactLease(stored, lease, current);
            AgentResultArtifactRefVO reference;
            try {
                reference = json.readRuntimeArtifact(normalizeRuntimeOutput(schema, result.output()));
            } catch (IllegalArgumentException | IllegalStateException invalidOutput) {
                return new ArtifactObservationVO(null, INVALID_RUNTIME_OUTPUT);
            }
            if (terminalPublisher == null) return new ArtifactObservationVO(null, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name());
            var command = verifyTerminalFence(actor, stored, verified);
            String source = command.getLink().sourceId();
            String destination = terminalPublisher.runtimeDestination(actor, source);
            if (destination == null) throw runtimeUnavailable();
            Instant deadline = earlier(stored.leaseExpiresAt(), earlier(stored.run().deadlineAt(), current.plus(ARTIFACT_PROOF_BUDGET)));
            return new ArtifactObservationVO(new AgentResultArtifactQuery(AgentResultArtifactQuery.PurposeEnum.PREPARE_TERMINAL,
                    actor, source, terminalProducerId, destination, stored.run().runId(), command.getNodeExecutionRef(),
                    command.getOutputSchemaSha256(), reference, deadline), null);
        });
        if (observation.errorCode() != null) return finish(actor, lease, expectedRevision, failureFor(result, observation.errorCode()), null);

        var request = observation.query();
        var proof = ARTIFACT_UNAVAILABLE;
        for (int attempt = 0; attempt < ARTIFACT_PROOF_ATTEMPTS && now().isBefore(request.getDeadlineAt()); attempt++) {
            try {
                proof = terminalPublisher.verifyArtifact(copyArtifactQuery(request));
                if (proof == null) proof = ARTIFACT_UNAVAILABLE;
            } catch (RuntimeException unavailable) {
                proof = ARTIFACT_UNAVAILABLE;
            }
            if (proof.decision() == DecisionEnum.DENY || proof.permits(request, now())) break;
        }
        if (proof.decision() == DecisionEnum.DENY) {
            return finish(actor, lease, expectedRevision, failureFor(result, AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED.name()), null);
        }
        return finish(actor, lease, expectedRevision, result, new PreparedArtifactVO(request, proof));
    }

    private AgentRunDTO finish(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision,
                               AgentExecutionResult result, PreparedArtifactVO artifact) {
        validateFinish(actor, lease, expectedRevision, result);
        return transaction.execute(status -> {
            var previous = requireLockedRun(actor, new GetAgentRunQuery(lease.runId()));
            var profile = verifyRunProfile(actor, previous);
            RuntimeTerminalResultVO runtime = profile.profile() == RunProfileEnum.RUNTIME_V2
                    ? runtimeTerminalForFinish(actor, previous, profile, lease, result, artifact) : null;
            String resultJson = runtime == null ? json.write(result, AgentJsonCodec.MAX_RESULT_BYTES) : json.writeRuntimeResult(runtime);
            AgentRunStatusEnum terminal = runtime == null ? result.status() : runtime.status();
            String errorCode = runtime == null ? result.errorCode() : runtime.errorCode();
            var updated = repository.finishLeased(actor, lease, expectedRevision, terminal, resultJson, errorCode)
                    .orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                            "Agent run lease or revision is no longer valid"));
            closeOutstanding(actor, previous, profile);
            appendStatusEvent(actor, previous, updated, AgentRunStatusEnum.RUNNING, terminal, null);
            return updated.run();
        });
    }

    private static void validateFinish(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision, AgentExecutionResult result) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(result, "result");
        if (expectedRevision < 0 || !lease.runId().equals(result.runId()) || !AgentRunTransitions.terminal(result.status())) {
            throw invalid("invalid leased Agent result");
        }
    }

    private RuntimeTerminalResultVO runtimeTerminalForFinish(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored,
            VerifiedRunProfileVO verified, AgentRunLease lease, AgentExecutionResult result, PreparedArtifactVO artifact) {
        if (result.status() != AgentRunStatusEnum.COMPLETED) return runtimeNonSuccess(result);
        var schema = verified.snapshot().template().getOutputSchema();
        SideEffectOutcomeEnum effect = runtimeEffect(result);
        Map<String, Object> output;
        try {
            if (result.artifactRef() != null) throw invalid("invalid runtime artifact");
            output = normalizeRuntimeOutput(schema, result.output());
            if (!runtimeArtifactOutput(schema)) {
                return new RuntimeTerminalResultVO(result.status(), output, null, null, effect);
            }
        } catch (IllegalArgumentException invalidOutput) {
            return runtimeNonSuccess(failureFor(result, INVALID_RUNTIME_OUTPUT));
        }
        if (artifact == null) throw runtimeUnavailable();
        var current = now();
        requireCurrentArtifactLease(stored, lease, current);
        var command = verifyTerminalFence(actor, stored, verified);
        var request = artifact.query();
        var reference = json.readRuntimeArtifact(output);
        Instant leaseAndAttempt = earlier(stored.leaseExpiresAt(), stored.run().deadlineAt());
        if (request.getPurpose() != AgentResultArtifactQuery.PurposeEnum.PREPARE_TERMINAL
                || !actor.equals(request.getOwner()) || !command.getLink().sourceId().equals(request.getSourceId())
                || !terminalProducerId.equals(request.getProducerId())
                || !Objects.equals(terminalPublisher.runtimeDestination(actor, request.getSourceId()), request.getDestinationId())
                || !stored.run().runId().equals(request.getRunId()) || !command.getNodeExecutionRef().equals(request.getNodeExecutionRef())
                || !command.getOutputSchemaSha256().equals(request.getOutputSchemaSha256()) || !reference.equals(request.getArtifactRef())
                || request.getDeadlineAt().isAfter(leaseAndAttempt) || !artifact.evidence().permits(request, now())) {
            return runtimeNonSuccess(failureFor(result, AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name()));
        }
        return new RuntimeTerminalResultVO(result.status(), null, reference, null, effect);
    }

    private static AgentExecutionResult failureFor(AgentExecutionResult attempted, String code) {
        return new AgentExecutionResult(attempted.runId(), AgentRunStatusEnum.FAILED, Map.of(), null, null, null,
                code, attempted.sideEffectOutcome());
    }

    private static RuntimeTerminalResultVO runtimeNonSuccess(AgentExecutionResult result) {
        String error = Objects.requireNonNullElseGet(result.errorCode(), () -> switch (result.status()) {
            case CANCELLED -> "CANCELLED";
            case TIMED_OUT -> AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name();
            default -> AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE.name();
        });
        return new RuntimeTerminalResultVO(result.status(), null, null, error, runtimeEffect(result));
    }

    private static SideEffectOutcomeEnum runtimeEffect(AgentExecutionResult result) {
        return result.sideEffectOutcome() == null ? SideEffectOutcomeEnum.UNKNOWN : SideEffectOutcomeEnum.valueOf(result.sideEffectOutcome());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeRuntimeOutput(ProcessSchemaVO schema, Map<String, Object> output) {
        // The published runtime schema is a closed object and this typed result still needs its 8 KiB canonical bound.
        if (!(schema.normalizeValue(output) instanceof Map<?, ?> normalized)) throw new IllegalArgumentException("Invalid Agent output");
        ProcessTypedJson.canonicalBytes(normalized, 8 * 1024);
        return (Map<String, Object>) normalized;
    }

    private static Instant earlier(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static void requireCurrentArtifactLease(JdbcAgentLedgerRepository.StoredRun stored, AgentRunLease lease, Instant now) {
        if (stored.run().status() != AgentRunStatusEnum.RUNNING || stored.run().cancelRequested()
                || stored.leaseEpoch() != lease.epoch() || !lease.owner().equals(stored.leaseOwner())
                || stored.leaseExpiresAt() == null || !stored.leaseExpiresAt().isAfter(now)
                || !stored.run().deadlineAt().isAfter(now)) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Agent Runtime artifact lease is no longer valid");
        }
    }

    private static AgentResultArtifactQuery copyArtifactQuery(AgentResultArtifactQuery query) {
        return new AgentResultArtifactQuery(query.getPurpose(), query.getOwner(), query.getSourceId(), query.getProducerId(),
                query.getDestinationId(), query.getRunId(), query.getNodeExecutionRef(), query.getOutputSchemaSha256(),
                query.getArtifactRef(), query.getDeadlineAt());
    }

    private record ArtifactObservationVO(AgentResultArtifactQuery query, String errorCode) { }
    private record PreparedArtifactVO(AgentResultArtifactQuery query, AgentResultArtifactEvidenceVO evidence) { }

    /** Database time, rather than a worker's clock, decides when a deadline has expired. */
    public AgentRunDTO expireDeadline(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            var profile = verifyRunProfile(actor, previous);
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                    Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(),
                    previous.run().attempt() == 0 ? "NONE" : "UNKNOWN");
            JdbcAgentLedgerRepository.StoredRun updated = repository.expireDeadline(actor, query.runId(),
                    expectedRevision, writeTerminalFailure(profile, result)).orElseThrow(
                    () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                            "Agent run deadline or revision has changed"));
            closeOutstanding(actor, previous, profile);
            appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.TIMED_OUT, null);
            return updated.run();
        });
    }

    /** Expires the current execution generation with a trusted, frozen tool-ledger effect. */
    public AgentRunDTO expireDeadline(TrustedAgentActor actor, AgentRunLease lease, GetAgentRunQuery query,
                                      long expectedRevision, String sideEffectOutcome) {
        if (actor == null || lease == null || query == null || expectedRevision < 0
                || !lease.runId().equals(query.runId()) || sideEffectOutcome == null
                || !Set.of("CONFIRMED", "NONE", "UNKNOWN").contains(sideEffectOutcome)) {
            throw invalid("invalid leased Agent deadline result");
        }
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            var profile = verifyRunProfile(actor, previous);
            AgentExecutionResult result = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.TIMED_OUT,
                    Map.of(), null, null, null, AgentErrorCodeEnum.TIANJI_RUN_TIMED_OUT.name(), sideEffectOutcome);
            JdbcAgentLedgerRepository.StoredRun updated = repository.expireDeadline(actor, lease,
                    expectedRevision, writeTerminalFailure(profile, result)).orElseThrow(
                    () -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                            "Agent run deadline or execution generation has changed"));
            closeOutstanding(actor, previous, profile);
            appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.TIMED_OUT, null);
            return updated.run();
        });
    }

    /** An expired worker is fenced and suspended for reconciliation, never silently retried. */
    public AgentRunDTO suspendExpiredLease(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return transaction.execute(status -> {
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            var profile = verifyRunProfile(actor, previous);
            JdbcAgentLedgerRepository.StoredRun updated = repository.suspendExpiredLease(actor, query.runId(),
                    expectedRevision).orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT,
                    "Agent run lease is not eligible for reconciliation"));
            closeOutstanding(actor, previous, profile);
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
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, new GetAgentRunQuery(lease.runId()));
            var profile = verifyRunProfile(actor, previous);
            JdbcAgentLedgerRepository.StoredRun updated = repository.suspendAuthorization(actor, lease, reason)
                    .orElseThrow(() -> new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED,
                            "Agent run lease or state is no longer valid"));
            closeOutstanding(actor, previous, profile);
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
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            verifyRunProfile(actor, previous);
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

    /** A technical, bounded count of unexpired and uncancelled queued runs on the owning primary. */
    public int sampleQueuedBacklog() {
        if (Thread.currentThread().isInterrupted() || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(dataSource)) {
            throw queuedBacklogUnavailable();
        }
        var sample = new TransactionTemplate(transaction.getTransactionManager());
        // Bind the primary before PostgreSQL sets physical read-only mode.
        sample.setReadOnly(false);
        sample.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        sample.setTimeout(1);
        try {
            return sample.execute(status -> {
                if (!TransactionSynchronizationManager.isActualTransactionActive()
                        || !TransactionSynchronizationManager.hasResource(dataSource)) throw queuedBacklogUnavailable();
                Integer count = repository.sampleQueuedBacklog();
                if (count == null || count < 0 || count > QUEUED_BACKLOG_CAP) throw queuedBacklogUnavailable();
                return count;
            });
        } catch (org.springframework.dao.DataAccessException | org.springframework.transaction.TransactionException failure) {
            throw queuedBacklogUnavailable();
        }
    }

    private static AgentLedgerException queuedBacklogUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                "Agent queued backlog observation is unavailable");
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
            JdbcAgentLedgerRepository.StoredRun previous = requireLockedRun(actor, query);
            var profile = verifyRunProfile(actor, previous);
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
                        previous.run().revision(), writeTerminalFailure(profile, expired))
                        .orElseThrow(AgentLedgerService::revisionConflict);
                closeOutstanding(actor, previous, profile);
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
                        if (profile.profile() == RunProfileEnum.RUNTIME_V2) closeOutstanding(actor, previous, profile);
                        repository.insertEvent(actor.tenantId(), new AgentEventDTO(UUID.randomUUID().toString(),
                                query.runId(), updated.run().lastEventSequence(), "CANCEL_REQUESTED", null, null,
                                null, now(), previous.correlationId(), "1", Map.of("reason", command.reason())));
                    }
                } else {
                    AgentExecutionResult cancelled = new AgentExecutionResult(query.runId(), AgentRunStatusEnum.CANCELLED,
                            Map.of(), null, null, null, null, previous.run().attempt() == 0 ? "NONE" : "UNKNOWN");
                    updated = repository.cancelNotRunning(actor, query.runId(), command.expectedRevision(),
                            writeTerminalFailure(profile, cancelled)).orElseThrow(AgentLedgerService::revisionConflict);
                    closeOutstanding(actor, previous, profile);
                    appendStatusEvent(actor, previous, updated, previous.run().status(), AgentRunStatusEnum.CANCELLED, null);
                }
                result = new CancelAgentRunResult(query.runId(), true, updated.run().status(), updated.run().revision(),
                        updated.cancelRequestedAt());
            }
            repository.completeCancelReceipt(actor, command.commandId(), json.write(result, AgentJsonCodec.MAX_EVENT_BYTES));
            return result;
        });
    }

    private String writeTerminalFailure(VerifiedRunProfileVO profile, AgentExecutionResult failure) {
        return profile.profile() == RunProfileEnum.RUNTIME_V2 ? json.writeRuntimeResult(runtimeNonSuccess(failure))
                : json.write(failure, AgentJsonCodec.MAX_RESULT_BYTES);
    }

    /** Unfenced lifecycle changes are retired; use claim, finish, cancellation or reconciliation. */
    @Deprecated
    public AgentRunDTO transition(TrustedAgentActor actor, GetAgentRunQuery query, long expectedRevision,
                                  AgentRunStatusEnum from, AgentRunStatusEnum to, String waitReason,
                                  AgentExecutionResult result) {
        throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_FEATURE_NOT_AVAILABLE,
                "unfenced Agent run transitions are unavailable");
    }

    private Instant appendStatusEvent(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun previous,
                                      JdbcAgentLedgerRepository.StoredRun updated, AgentRunStatusEnum from,
                                      AgentRunStatusEnum to, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", from.name());
        payload.put("status", to.name());
        if (reason != null) {
            payload.put("reason", reason);
        }
        var event = new AgentEventDTO(UUID.randomUUID().toString(),
                updated.run().runId(), updated.run().lastEventSequence(), "RUN_STATUS_CHANGED", null, null,
                null, now(), previous.correlationId(), "1", payload);
        repository.insertEvent(actor.tenantId(), event);
        if (terminalPublisher != null && AgentRunTransitions.terminal(to)) {
            if (runtimeProfile(updated) == RunProfileEnum.RUNTIME_V2) {
                terminalPublisher.publish(terminalReferenceV2(actor, updated).orElseThrow(AgentLedgerService::runtimeUnavailable));
            } else {
                terminalReference(actor, updated).ifPresent(terminalPublisher::publish);
            }
        }
        return event.occurredAt();
    }

    private Optional<AgentTerminalEvent> terminalReference(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        if (terminalProducerId == null || !AgentRunTransitions.terminal(stored.run().status())) return Optional.empty();
        // V2 has its own terminal event contract. Existing publishers consume only the v1 envelope.
        if (runtimeProfile(stored) == RunProfileEnum.RUNTIME_V2) return Optional.empty();
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

    private Optional<AgentTerminalEventV2> terminalReferenceV2(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored) {
        var run = stored.run();
        if (terminalProducerId == null || !AgentRunTransitions.terminal(run.status())
                || runtimeProfile(stored) != RunProfileEnum.RUNTIME_V2) return Optional.empty();
        if (run.finishedAt() == null || actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE
                || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw runtimeUnavailable();
        }
        var verified = verifyRuntime(actor, stored);
        var command = verifyTerminalFence(actor, stored, verified);
        if (terminalPublisher.runtimeDestination(actor, command.getLink().sourceId()) == null) throw runtimeUnavailable();
        var result = readRuntimeTerminal(stored, verified);
        var expectedInput = verified.snapshot().template().getInputSchema().normalizeValue(command.getInput());
        String inputSha256 = ProcessTypedJson.sha256("AUTOMATION_NODE_INPUT_V2",
                command.getInputSchemaSha256().getBytes(StandardCharsets.UTF_8), ProcessTypedJson.canonicalBytes(expectedInput));
        return Optional.of(new AgentTerminalEventV2(2, terminalProducerId,
                AgentTerminalEventV2.eventIdFor(terminalProducerId, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(),
                        run.runId(), run.revision()), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(),
                command.getIdempotencyKey(), command.getLink(), run.definitionKey(), run.definitionVersion(), run.runId(), run.revision(),
                command.getNodeExecutionRef(), command.getConfigurationSha256(), inputSha256, command.getOutputSchemaSha256(),
                result.status(), run.finishedAt(), result.resultSha256(), result.output(), result.artifactRef(), result.errorCode(),
                result.sideEffectOutcome()));
    }

    private LinkedAgentRuntimeCommand verifyTerminalFence(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun stored,
                                                          VerifiedRunProfileVO verified) {
        var command = verified.snapshot().command();
        if (!repository.hasRuntimeTerminalFence(actor, stored.run().runId(), command.getIdempotencyKey(),
                json.processLinkHash(command.getLink()), stored.requestHash())) throw runtimeUnavailable();
        return command;
    }

    private RuntimeTerminalResultVO readRuntimeTerminal(JdbcAgentLedgerRepository.StoredRun stored, VerifiedRunProfileVO verified) {
        try {
            var result = json.readRuntimeResult(stored.resultJson());
            var run = stored.run();
            if (run.status() != result.status() || run.finishedAt() == null) throw runtimeUnavailable();
            if (result.status() == AgentRunStatusEnum.COMPLETED) {
                var schema = verified.snapshot().template().getOutputSchema();
                boolean artifact = runtimeArtifactOutput(schema);
                if (artifact != (result.artifactRef() != null)) throw runtimeUnavailable();
                var wire = artifact ? result.artifactRef().toWire() : result.output();
                var normalized = normalizeRuntimeOutput(schema, wire);
                if (!Arrays.equals(ProcessTypedJson.canonicalBytes(wire), ProcessTypedJson.canonicalBytes(normalized))) throw runtimeUnavailable();
            }
            return result;
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            throw runtimeUnavailable();
        }
    }

    private static long leaseMillis(Duration duration) {
        if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0
                || duration.compareTo(Duration.ofMinutes(5)) > 0) {
            throw invalid("lease duration must be between one second and five minutes");
        }
        return duration.toMillis();
    }

    private void closeOutstanding(TrustedAgentActor actor, JdbcAgentLedgerRepository.StoredRun run,
                                  VerifiedRunProfileVO profile) {
        if (profile.profile() == RunProfileEnum.LEGACY_V1) {
            repository.markOutstandingBudgetUnknown(actor.tenantId(), run.run().runId());
            return;
        }
        var binding = profile.binding();
        var runId = run.run().runId();
        var pending = repository.pendingRuntimeCalls(actor, runId, run.leaseEpoch(), binding.getActiveCalls());
        var cleanupDeadline = now().plusSeconds(10);
        for (var call : pending) {
            runtimeCallReceipts.recover(actor, runId, call.callId(), call.revision(), call.dispatchedWrite()
                    ? receipt -> runtimeToolActions.closeRuntimeDispatch(receipt, now(), cleanupDeadline)
                    : null);
        }
        var finalBinding = runtimeBindings.lockBinding(actor.tenantId(), runId);
        if (finalBinding.getActiveCalls() != 0 || finalBinding.getRevision() - binding.getRevision() != pending.size()) {
            throw runtimeUnavailable();
        }
    }

    private JdbcAgentLedgerRepository.StoredRun requireLockedRun(TrustedAgentActor actor, GetAgentRunQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        return repository.runForUpdate(actor, query.runId()).orElseThrow(AgentLedgerService::notFound);
    }

    private static AgentLedgerException runtimeUnavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent runtime evidence is unavailable");
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
