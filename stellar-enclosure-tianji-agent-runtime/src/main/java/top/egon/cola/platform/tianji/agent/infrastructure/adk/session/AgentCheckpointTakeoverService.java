package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Internal, explicitly constructed one-shot claim of a verified first-input checkpoint. */
public final class AgentCheckpointTakeoverService {
    public static final String PURPOSE = "TIANJI_AGENT_RUN_RESUME";
    private static final long LEASE_MILLIS = 30_000;
    private static final String APP = "tianji";

    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final AgentCheckpointObservationService checkpoint;
    private final ResumeAuthorization authorization;
    private final TransactionTemplate reader;
    private final TransactionTemplate writer;

    public AgentCheckpointTakeoverService(DataSource source, ResumeAuthorization authorization) {
        this.source = Objects.requireNonNull(source, "source");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        checkpoint = new AgentCheckpointObservationService(source);
        jdbc = new JdbcTemplate(source);
        var manager = new DataSourceTransactionManager(source);
        manager.setEnforceReadOnly(true);
        reader = new TransactionTemplate(manager);
        reader.setReadOnly(true);
        reader.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        reader.setTimeout(2);
        writer = new TransactionTemplate(manager);
        // The match runs after the exact row/advisory locks. READ COMMITTED must see writes
        // to previously absent APP/USER scope rows committed before we acquired that lock.
        writer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        writer.setTimeout(2);
    }

    public boolean usesSourceAndAuthority(DataSource candidate, ResumeAuthorization candidateAuthority) {
        return source == candidate && authorization == candidateAuthority;
    }

    /** The trusted host obtains a fresh, exact current RESUME decision. This port is called outside JDBC transactions. */
    @FunctionalInterface
    public interface ResumeAuthorization {
        Optional<ResumePermit> authorize(ResumeRequest request);
    }

    public record ResumeCommand(String runId, String commandId, long expectedRevision) {
        public ResumeCommand {
            if (!text(runId, 64) || !text(commandId, 128) || expectedRevision < 0 || expectedRevision > Long.MAX_VALUE - 2) {
                throw new IllegalArgumentException("Agent checkpoint takeover command is invalid");
            }
        }

        @Override public String toString() { return "ResumeCommand[redacted]"; }
    }

    public record ResumeRequest(AgentRunAuthorization.Request execution, ResumeCommand command, String cutSha256) {
        public ResumeRequest {
            Objects.requireNonNull(execution, "execution");
            Objects.requireNonNull(command, "command");
            if (!execution.runId().equals(command.runId()) || !execution.sessionRequired()
                    || cutSha256 == null || !cutSha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Agent checkpoint takeover authorization binding is invalid");
            }
        }

        /** Fixed domain separation for an authority binding this exact owner, checkpoint and command to its resource. */
        public String bindingSha256(URI resource) {
            return hash(List.of(PURPOSE, AgentExecutionGrant.fingerprint(execution, resource),
                    command.runId(), command.commandId(), Long.toString(command.expectedRevision()), cutSha256));
        }

        @Override public String toString() { return "ResumeRequest[redacted]"; }
    }

    public record ResumePermit(ResumeRequest request, Instant expiresAt) {
        public ResumePermit {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (expiresAt.isAfter(request.execution().deadlineAt())) {
                throw new IllegalArgumentException("Agent checkpoint takeover authorization expiry is invalid");
            }
        }

        @Override public String toString() { return "ResumePermit[redacted]"; }
    }

    /** A committed acknowledgement deliberately carries no execution capability. */
    public record Acknowledgement(String runId, AgentRunStatusEnum status, int attempt, long revision) { }

    public static final class TakeoverResult {
        private final Acknowledgement acknowledgement;
        private final AtomicReference<Launch> launch;
        private final ResumeAuthorization issuingAuthority;

        private TakeoverResult(Acknowledgement acknowledgement, Launch launch, ResumeAuthorization issuingAuthority) {
            this.acknowledgement = acknowledgement;
            this.launch = new AtomicReference<>(launch);
            this.issuingAuthority = issuingAuthority;
        }

        public Acknowledgement acknowledgement() { return acknowledgement; }

        Optional<Launch> takeLaunch() { return Optional.ofNullable(launch.getAndSet(null)); }

        /** Redeems the in-process winner only for its exact configured execution owner. */
        public Optional<Execution> consumeForExecution(DataSource source, ResumeAuthorization authority, boolean typedWrites) {
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isSynchronizationActive()
                    || (source != null && TransactionSynchronizationManager.hasResource(source))) {
                throw new IllegalStateException("Agent checkpoint execution cannot begin inside a transaction");
            }
            var winning = launch.get();
            if (winning == null) return Optional.empty();
            if (source != winning.source() || authority != issuingAuthority) throw denied();
            if (!launch.compareAndSet(winning, null)) return Optional.empty();
            var store = new PostgresAdkSessionService(source, winning.authorizationRequest().execution().actor(),
                    APP, false, typedWrites);
            return Optional.of(new Execution(winning, winning.bindSession(store), authority));
        }

        @Override public String toString() { return "TakeoverResult[redacted]"; }
    }

    /** A private-construction execution handle for the claimed Run and checkpoint-bound Session. */
    public static final class Execution {
        private final Launch launch;
        private final PostgresAdkSessionService boundSession;
        private final ResumeAuthorization authority;

        private Execution(Launch launch, PostgresAdkSessionService boundSession, ResumeAuthorization authority) {
            this.launch = launch;
            this.boundSession = boundSession;
            this.authority = authority;
        }

        public TrustedAgentActor actor() { return request().actor(); }
        public AgentRunAuthorization.Request request() { return launch.authorizationRequest().execution(); }
        public AgentRunLease runLease() { return launch.runLease(); }
        public PostgresAdkSessionService.SessionLease sessionLease() { return launch.sessionLease(); }
        public PostgresAdkSessionService boundSession() { return boundSession; }
        public String invocationId() { return launch.invocationId(); }

        public boolean matchesClaimed(AgentLedgerService.ExecutionMaterial material) {
            if (material == null || !request().equals(material.authorization())) return false;
            var run = material.run();
            var cut = launch.checkpoint();
            return runLease().runId().equals(run.runId()) && cut.sessionId().equals(run.sessionId())
                    && run.status() == AgentRunStatusEnum.RUNNING && !run.cancelRequested() && run.attempt() == 1
                    && run.revision() == cut.revision() + 2 && run.lastEventSequence() == cut.lastEventSequence() + 2;
        }

        /** Performs a fresh authority call before a model boundary without extending the winning lease. */
        public AgentRunAuthorization.Decision authorizeResume(Instant observedAt) {
            Objects.requireNonNull(observedAt, "observedAt");
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    || TransactionSynchronizationManager.isSynchronizationActive()
                    || TransactionSynchronizationManager.hasResource(launch.source())) {
                throw new IllegalStateException("Agent resume authorization cannot run inside a transaction");
            }
            Instant fixedExpiry = runLease().expiresAt();
            if (!fixedExpiry.isAfter(observedAt)) return AgentRunAuthorization.Decision.UNAVAILABLE;
            var current = authority.authorize(launch.authorizationRequest());
            if (current == null) return AgentRunAuthorization.Decision.UNAVAILABLE;
            if (current.isEmpty()) return AgentRunAuthorization.Decision.DENY;
            var permit = current.orElseThrow();
            return launch.authorizationRequest().equals(permit.request()) && !permit.expiresAt().isBefore(fixedExpiry)
                    ? AgentRunAuthorization.Decision.ALLOW : AgentRunAuthorization.Decision.UNAVAILABLE;
        }

        @Override public String toString() { return "Execution[redacted]"; }
    }

    record Launch(DataSource source, AgentRunLease runLease, PostgresAdkSessionService.SessionLease sessionLease,
                  AgentCheckpointObservationService.ResumeMatch checkpoint, ResumeRequest authorizationRequest) {
        String invocationId() { return checkpoint.invocationId(); }

        PostgresAdkSessionService bindSession(PostgresAdkSessionService store) {
            return store.withResumeCheckpoint(source, sessionLease, checkpoint);
        }

        @Override public String toString() { return "Launch[redacted]"; }
    }

    /** Supplies the exact read-only cut for synchronous grant issuance. An exact committed replay needs no new ticket. */
    public Optional<ResumeRequest> previewAuthorization(TrustedAgentActor actor, ResumeCommand command) {
        requireCommandOwner(actor, command);
        String requestHash = commandHash(actor, command);
        try {
            if (readReceipt(actor, command, requestHash).isPresent()) return Optional.empty();
            var preview = checkpoint.matchForTakeover(actor, new GetAgentRunQuery(command.runId()));
            if (preview.isPresent() && preview.orElseThrow().revision() == command.expectedRevision()) {
                return preview.map(exact -> new ResumeRequest(exact.request(), command, exact.cutSha256()));
            }
            if (readReceipt(actor, command, requestHash).isPresent()) return Optional.empty();
            throw conflict();
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable();
        }
    }

    public TakeoverResult takeOver(TrustedAgentActor actor, ResumeCommand command, String workerId) {
        requireCommandOwner(actor, command);
        if (!text(workerId, 128)) throw new IllegalArgumentException("Agent checkpoint takeover worker is invalid");
        String requestHash = commandHash(actor, command);
        try {
            var replay = readReceipt(actor, command, requestHash);
            if (replay.isPresent()) return replay.get();

            var query = new GetAgentRunQuery(command.runId());
            var preview = checkpoint.matchForTakeover(actor, query);
            if (preview.isEmpty() || preview.get().revision() != command.expectedRevision()) {
                return readReceipt(actor, command, requestHash).orElseThrow(AgentCheckpointTakeoverService::conflict);
            }
            var exact = preview.get();
            var request = new ResumeRequest(exact.request(), command, exact.cutSha256());
            var permit = authorize(request);
            try {
                return Objects.requireNonNull(writer.execute(ignored -> commit(actor, command, workerId, requestHash, exact, permit)));
            } catch (CannotSerializeTransactionException | CannotAcquireLockException raced) {
                // The write transaction has rolled back. An independently committed winner may now have a receipt.
                return readReceipt(actor, command, requestHash).orElseThrow(AgentCheckpointTakeoverService::conflict);
            }
        } catch (DataAccessException | TransactionException failure) {
            throw unavailable();
        }
    }

    private void requireCommandOwner(TrustedAgentActor actor, ResumeCommand command) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.USER
                || "LEGACY".equals(actor.issuerFingerprint())) throw denied();
        Objects.requireNonNull(command, "command");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Agent checkpoint takeover must own its transactions");
        }
    }

    private static String commandHash(TrustedAgentActor actor, ResumeCommand command) {
        return hash(List.of(PURPOSE, actor.identityFingerprint(), command.runId(), command.commandId(),
                Long.toString(command.expectedRevision())));
    }

    private ResumePermit authorize(ResumeRequest request) {
        Optional<ResumePermit> decision;
        try {
            decision = authorization.authorize(request);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        if (decision == null) throw unavailable();
        var permit = decision.orElseThrow(AgentCheckpointTakeoverService::denied);
        if (!request.equals(permit.request())) throw unavailable();
        return permit;
    }

    private Optional<TakeoverResult> readReceipt(TrustedAgentActor actor, ResumeCommand command, String requestHash) {
        return Objects.requireNonNull(reader.execute(ignored -> {
            requirePrimary(true);
            return receipt(actor, command, requestHash);
        }));
    }

    private TakeoverResult commit(TrustedAgentActor actor, ResumeCommand command, String workerId, String requestHash,
                                  AgentCheckpointObservationService.ResumeMatch expected, ResumePermit permit) {
        requirePrimary(false);
        var sessions = new JdbcAdkSessionRepository(jdbc, actor, APP);
        var run = sessions.lockRun(command.runId()).orElseThrow(AgentCheckpointTakeoverService::notFound);
        var replay = receipt(actor, command, requestHash);
        if (replay.isPresent()) return replay.get();
        if (!expected.sessionId().equals(run.sessionId()) || run.epoch() != expected.runEpoch()
                || !"SUSPENDED".equals(run.status()) || run.cancelled() || run.owner() != null || run.expiresAt() != null) {
            throw conflict();
        }
        var session = sessions.session(expected.sessionId(), true).orElseThrow(AgentCheckpointTakeoverService::conflict);
        var oldLease = sessions.lockLease(expected.sessionId());
        if (session.revision() != expected.sessionRevision() || session.lastEventSequence() != expected.sessionSequence()
                || oldLease.epoch() != expected.sessionEpoch() || oldLease.live()
                || !sessions.tryLockScopeForCheckpoint()) throw conflict();
        var actual = checkpoint.matchInTakeoverTransaction(actor, new GetAgentRunQuery(command.runId()));
        if (actual.isEmpty() || !expected.equals(actual.get()) || expected.lastEventSequence() > Long.MAX_VALUE - 2
                || expected.runEpoch() == Long.MAX_VALUE || expected.sessionEpoch() == Long.MAX_VALUE) throw conflict();

        int inserted = jdbc.update("""
                INSERT INTO tianji_agent.agent_run_command_receipt
                    (tenant_id, subject_type, subject_issuer, subject_id, command_id, operation, request_hash, run_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'RESUME', ?, ?, pg_catalog.clock_timestamp())
                ON CONFLICT (tenant_id, subject_type, subject_issuer, subject_id, operation, command_id) DO NOTHING
                """, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                command.commandId(), requestHash, command.runId());
        if (inserted == 0) return receipt(actor, command, requestHash).orElseThrow(AgentCheckpointTakeoverService::unavailable);
        if (inserted != 1) throw unavailable();

        var advanced = advanceRun(actor, command, workerId, expected, permit.expiresAt());
        var runLease = new AgentRunLease(command.runId(), workerId, advanced.epoch(), advanced.expiresAt());
        var sessionLease = new PostgresAdkSessionService(source, actor, APP).acquireCheckpointLeaseInTransaction(
                expected.sessionId(), runLease, expected.sessionEpoch(), advanced.expiresAt());
        appendStatus(actor, command.runId(), advanced.sequence() - 1, advanced.correlationId(), "SUSPENDED", "QUEUED");
        appendStatus(actor, command.runId(), advanced.sequence(), advanced.correlationId(), "QUEUED", "RUNNING");
        var ack = new Acknowledgement(command.runId(), AgentRunStatusEnum.RUNNING, 1, advanced.revision());
        if (jdbc.update("""
                UPDATE ONLY tianji_agent.agent_run_command_receipt
                SET result=pg_catalog.jsonb_build_object('runId', run_id, 'status', 'RUNNING', 'attempt', 1, 'revision', ?)
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND operation='RESUME'
                  AND command_id=? AND run_id=? AND request_hash=? AND result IS NULL
                """, ack.revision(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), command.commandId(), command.runId(), requestHash) != 1) throw unavailable();
        var persisted = receipt(actor, command, requestHash).orElseThrow(AgentCheckpointTakeoverService::unavailable);
        if (!ack.equals(persisted.acknowledgement())) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT pg_catalog.clock_timestamp()<LEAST(CAST(? AS timestamptz),CAST(? AS timestamptz))
                """, Boolean.class, Timestamp.from(runLease.expiresAt()), Timestamp.from(sessionLease.expiresAt())))) throw conflict();
        return new TakeoverResult(ack, new Launch(source, runLease, sessionLease, expected, permit.request()), authorization);
    }

    private void requirePrimary(boolean readOnly) {
        jdbc.execute("SET LOCAL statement_timeout = '2s'");
        jdbc.execute("SET LOCAL lock_timeout = '1s'");
        jdbc.execute("SET LOCAL search_path = pg_catalog, tianji_agent");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('transaction_read_only')=?
                """, Boolean.class, readOnly ? "on" : "off"))) throw unavailable();
    }

    private Optional<TakeoverResult> receipt(TrustedAgentActor actor, ResumeCommand command, String requestHash) {
        var rows = jdbc.query("""
                SELECT receipt.run_id AS receipt_run_id, receipt.request_hash,
                       CASE WHEN pg_catalog.jsonb_typeof(receipt.result->'revision')='number'
                            THEN (receipt.result->>'revision')::bigint END AS ack_revision,
                       receipt.result->>'runId' AS ack_run_id, receipt.result->>'status' AS ack_status,
                       receipt.result->'attempt'='1'::jsonb AS ack_first_attempt
                FROM ONLY tianji_agent.agent_run owned
                LEFT JOIN ONLY tianji_agent.agent_run_command_receipt receipt
                  ON receipt.tenant_id=owned.tenant_id AND receipt.subject_type=owned.subject_type
                  AND receipt.subject_issuer=owned.subject_issuer AND receipt.subject_id=owned.subject_id
                  AND receipt.operation='RESUME' AND receipt.command_id=?
                WHERE owned.tenant_id=? AND owned.subject_type=? AND owned.subject_issuer=? AND owned.subject_id=? AND owned.run_id=?
                LIMIT 2
                """, (rs, index) -> new Receipt(rs.getString("receipt_run_id"), rs.getString("request_hash"),
                        rs.getObject("ack_revision", Long.class), rs.getString("ack_run_id"),
                        rs.getString("ack_status"), rs.getBoolean("ack_first_attempt")),
                command.commandId(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), command.runId());
        if (rows.isEmpty()) throw notFound();
        if (rows.size() != 1) throw unavailable();
        var row = rows.getFirst();
        if (row.runId() == null) return Optional.empty();
        if (!command.runId().equals(row.runId()) || !requestHash.equals(row.requestHash())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT,
                    "Agent takeover command id was already used for a different request");
        }
        if (!command.runId().equals(row.ackRunId()) || !"RUNNING".equals(row.status()) || !row.firstAttempt()
                || row.revision() == null || row.revision() != command.expectedRevision() + 2) throw unavailable();
        return Optional.of(new TakeoverResult(new Acknowledgement(row.runId(), AgentRunStatusEnum.RUNNING, 1, row.revision()), null, null));
    }

    private AdvancedRun advanceRun(TrustedAgentActor actor, ResumeCommand command, String workerId,
                                   AgentCheckpointObservationService.ResumeMatch expected, Instant permitExpiry) {
        Timestamp boundedExpiry = Timestamp.from(permitExpiry.truncatedTo(ChronoUnit.MICROS));
        var rows = jdbc.query("""
                WITH fence_clock AS (SELECT pg_catalog.clock_timestamp() AS now)
                UPDATE ONLY tianji_agent.agent_run AS target
                SET status='RUNNING', wait_reason=NULL, revision=target.revision+2,
                    last_event_sequence=target.last_event_sequence+2, lease_epoch=target.lease_epoch+1, lease_owner=?,
                    lease_expires_at=LEAST(fence_clock.now+(? * interval '1 millisecond'), target.deadline_at, ?)
                FROM fence_clock
                WHERE target.tenant_id=? AND target.subject_type=? AND target.subject_issuer=? AND target.subject_id=?
                  AND target.run_id=? AND target.session_id=? AND target.revision=? AND target.last_event_sequence=?
                  AND target.lease_epoch=? AND target.lease_owner IS NULL AND target.lease_expires_at IS NULL
                  AND target.status='SUSPENDED' AND target.wait_reason='LEASE_EXPIRED' AND target.attempt=1
                  AND target.cancel_requested_at IS NULL AND target.result IS NULL AND target.finished_at IS NULL
                  AND target.error_code IS NULL AND target.deadline_at>fence_clock.now AND ?>fence_clock.now
                RETURNING target.revision, target.last_event_sequence, target.lease_epoch, target.lease_expires_at,
                          target.correlation_id
                """, (rs, index) -> advanced(rs), workerId, LEASE_MILLIS, boundedExpiry,
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                command.runId(), expected.sessionId(), command.expectedRevision(), expected.lastEventSequence(),
                expected.runEpoch(), boundedExpiry);
        if (rows.isEmpty()) throw conflict();
        if (rows.size() != 1) throw unavailable();
        var row = rows.getFirst();
        if (row.revision() != expected.revision() + 2 || row.sequence() != expected.lastEventSequence() + 2
                || row.epoch() != expected.runEpoch() + 1 || row.expiresAt().isAfter(permitExpiry)) throw unavailable();
        return row;
    }

    private void appendStatus(TrustedAgentActor actor, String runId, long sequence, String correlationId,
                              String from, String status) {
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_event
                    (tenant_id, run_id, sequence, event_id, event_type, schema_version, correlation_id, payload, occurred_at)
                VALUES (?, ?, ?, ?, 'RUN_STATUS_CHANGED', '1', ?,
                        pg_catalog.jsonb_build_object('from', CAST(? AS text), 'status', CAST(? AS text),
                                                      'reason', 'CHECKPOINT_RESUME'),
                        pg_catalog.clock_timestamp())
                """, actor.tenantId(), runId, sequence, UUID.randomUUID().toString(), correlationId, from, status) != 1) {
            throw unavailable();
        }
    }

    private static AdvancedRun advanced(ResultSet row) throws SQLException {
        var expiry = row.getTimestamp("lease_expires_at");
        if (expiry == null) throw unavailable();
        return new AdvancedRun(row.getLong("revision"), row.getLong("last_event_sequence"), row.getLong("lease_epoch"),
                expiry.toInstant(), row.getString("correlation_id"));
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.equals(value.trim())
                && value.chars().noneMatch(Character::isISOControl) && StandardCharsets.UTF_8.newEncoder().canEncode(value);
    }

    private static String hash(List<String> fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String field : fields) {
                byte[] value = field.getBytes(StandardCharsets.UTF_8);
                digest.update((byte) (value.length >>> 24));
                digest.update((byte) (value.length >>> 16));
                digest.update((byte) (value.length >>> 8));
                digest.update((byte) value.length);
                digest.update(value);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw unavailable();
        }
    }

    private static AgentLedgerException denied() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Agent checkpoint takeover is not authorized");
    }

    private static AgentLedgerException conflict() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT, "Agent checkpoint takeover boundary changed");
    }

    private static AgentLedgerException notFound() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Agent run not found");
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Agent checkpoint takeover is unavailable");
    }

    private record AdvancedRun(long revision, long sequence, long epoch, Instant expiresAt, String correlationId) { }
    private record Receipt(String runId, String requestHash, Long revision, String ackRunId, String status, boolean firstAttempt) { }
}
