package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceIntent;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryPreferenceSourceBinding;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Same-primary, epoch-fenced preference intents. No USER bearer or fact body is stored here. */
public final class MemoryPreferenceIntentStore {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AtomicBoolean verified = new AtomicBoolean();

    public MemoryPreferenceIntentStore(DataSource source, AgentLedgerService ledger) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(source)) throw new IllegalArgumentException("Preference intents require the exact Agent ledger DataSource");
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    public boolean usesDataSource(DataSource candidate) { return candidate == source; }
    public boolean usesLedger(AgentLedgerService candidate) { return candidate == ledger; }
    public void verifyStorage() { verified.set(false); new MemoryPreferenceIntentStorageVerifier(source).verify(); verified.set(true); }

    /** A repeated request preserves the first deadline and immutable source; only a current request can reserve a new issue epoch. */
    public Optional<MemoryPreferenceIntent.Reservation> reserve(AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding,
                                                               URI resource, Instant requestedDeadline, int maximumAttempts) {
        Objects.requireNonNull(run, "run"); Objects.requireNonNull(binding, "binding"); Objects.requireNonNull(requestedDeadline, "requestedDeadline");
        if (maximumAttempts < 1 || maximumAttempts > 100) throw invalid();
        UUID id = MemoryPreferenceIntent.intentId(run, binding.scopeId());
        return transact(status -> {
            if (!lockSource(run)) return Optional.empty();
            var existing = read(id, true);
            Instant now = databaseNow();
            if (existing.isEmpty()) {
                Instant deadline = requestedDeadline.truncatedTo(ChronoUnit.MICROS);
                if (!deadline.isAfter(now) || deadline.isAfter(now.plus(Duration.ofHours(8)))) throw invalid();
                var reservation = new MemoryPreferenceIntent.Reservation(run, binding, resource, deadline, 1, id, MemoryPreferenceIntent.fingerprint(run, binding, resource, deadline));
                int inserted = jdbc.update("""
                        INSERT INTO tianji_agent.agent_memory_preference_intent
                            (intent_id,tenant_id,actor_issuer,actor_type,actor_id,run_id,scope_id,binding_id,policy_ref,resource_uri,
                             definition_key,definition_version,definition_sha256,request_sha256,snapshot_sha256,execution_policy_ref,
                             session_required,run_deadline_at,deadline_at,binding_sha256,issue_epoch,state,revision,maximum_attempts,next_attempt_at,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,'RESERVED',1,?,?,?,?) ON CONFLICT (intent_id) DO NOTHING
                        """, id, run.actor().tenantId(), run.actor().issuerFingerprint(), run.actor().subjectType().name(), run.actor().subjectId(), run.runId(), binding.scopeId(), binding.id(), binding.policyRef(), resource.toASCIIString(),
                        run.definitionKey(), run.definitionVersion(), run.definitionSha256(), run.requestSha256(), run.snapshotSha256(), run.policyVersionRef(), run.sessionRequired(), Timestamp.from(run.deadlineAt()), Timestamp.from(deadline), reservation.bindingSha256(), maximumAttempts,
                        Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
                if (inserted == 1) return Optional.of(reservation);
                if (inserted != 0) throw unavailable();
                existing = read(id, true); if (existing.isEmpty()) throw unavailable(); now = databaseNow();
            }
            var row = existing.orElseThrow(); requireBinding(row.reservation(), run, binding, resource);
            if (row.state().terminal() || !row.reservation().deadlineAt().isAfter(now)) return Optional.empty();
            if (row.ticket() != null && row.ticket().expiresAt().isAfter(now)) return Optional.empty();
            if (row.reservation().issueEpoch() == Long.MAX_VALUE || row.leaseEpoch() == Long.MAX_VALUE || row.revision() == Long.MAX_VALUE) throw capacity();
            long epoch = row.reservation().issueEpoch() + 1;
            jdbc.update("""
                    UPDATE tianji_agent.agent_memory_preference_intent SET issue_epoch=?,state='RESERVED',grant_id=NULL,ciphertext=NULL,grant_expires_at=NULL,
                        lease_owner=NULL,lease_expires_at=NULL,lease_epoch=lease_epoch+1,revision=revision+1,next_attempt_at=?,updated_at=?,last_error_code=NULL
                    WHERE intent_id=?
                    """, epoch, Timestamp.from(now), Timestamp.from(now), id);
            var old = row.reservation();
            return Optional.of(new MemoryPreferenceIntent.Reservation(run, binding, resource, old.deadlineAt(), epoch, id, old.bindingSha256()));
        });
    }

    public Optional<MemoryPreferenceIntent.Evidence> currentEvidence(AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding, URI resource) {
        return transact(status -> {
            if (!lockSource(run)) return Optional.empty();
            var found = read(MemoryPreferenceIntent.intentId(run, binding.scopeId()), false); if (found.isEmpty()) return Optional.empty();
            var row = found.orElseThrow(); requireBinding(row.reservation(), run, binding, resource); Instant now = databaseNow();
            if (!row.state().sealed() || row.ticket() == null || !row.ticket().expiresAt().isAfter(now) || !row.reservation().deadlineAt().isAfter(now)) return Optional.empty();
            return Optional.of(new MemoryPreferenceIntent.Evidence(row.reservation(), row.ticket()));
        });
    }

    public boolean complete(MemoryPreferenceIntent.Reservation reservation, AgentExecutionGrant.Ticket ticket) {
        Objects.requireNonNull(reservation, "reservation"); Objects.requireNonNull(ticket, "ticket"); new MemoryPreferenceIntent.Evidence(reservation, ticket);
        Instant expires = ticket.expiresAt().truncatedTo(ChronoUnit.MICROS);
        return transact(status -> {
            if (!lockSource(reservation.run())) return false;
            var found = read(reservation.intentId(), true); if (found.isEmpty()) return false;
            var row = found.orElseThrow(); Instant now = databaseNow();
            if (!row.reservation().equals(reservation) || row.state() != MemoryPreferenceIntent.State.RESERVED || !expires.isAfter(now) || !reservation.deadlineAt().isAfter(now)) return false;
            if (row.revision() == Long.MAX_VALUE) throw capacity();
            return jdbc.update("""
                    UPDATE tianji_agent.agent_memory_preference_intent SET state='READY',grant_id=?,ciphertext=?,grant_expires_at=?,revision=revision+1,
                        next_attempt_at=?,updated_at=?,last_error_code=NULL WHERE intent_id=? AND issue_epoch=? AND state='RESERVED'
                    """, ticket.grantId(), ticket.ciphertext(), Timestamp.from(expires), Timestamp.from(now), Timestamp.from(now), reservation.intentId(), reservation.issueEpoch()) == 1;
        });
    }

    /** Optional issuance failures never change a later reservation or attach evidence from a different epoch. */
    public boolean recordIssueFailure(MemoryPreferenceIntent.Reservation reservation, String reasonCode, boolean definite) {
        Objects.requireNonNull(reservation, "reservation"); reason(reasonCode);
        return transact(status -> {
            var found = read(reservation.intentId(), true); if (found.isEmpty()) return false; var row = found.orElseThrow();
            if (!row.reservation().equals(reservation) || row.state() != MemoryPreferenceIntent.State.RESERVED) return false;
            Instant now = databaseNow();
            if (!reservation.deadlineAt().isAfter(now)) closeRow(row, MemoryPreferenceIntent.State.EXPIRED, "INTENT_EXPIRED", now, null, null);
            else if (definite) closeRow(row, MemoryPreferenceIntent.State.REJECTED, reasonCode, now, null, null);
            else {
                counters(row);
                jdbc.update("UPDATE tianji_agent.agent_memory_preference_intent SET last_error_code=?,revision=revision+1,updated_at=? WHERE intent_id=?", reasonCode, Timestamp.from(now), reservation.intentId());
            }
            return true;
        });
    }

    public List<UUID> candidates(long tenantId, String issuerFingerprint, int limit) {
        origin(tenantId, issuerFingerprint); limit(limit);
        return transact(status -> jdbc.query("""
                SELECT i.intent_id FROM tianji_agent.agent_memory_preference_intent i
                JOIN tianji_agent.agent_run r ON r.tenant_id=i.tenant_id AND r.run_id=i.run_id
                WHERE i.tenant_id=? AND i.actor_issuer=? AND i.state IN ('READY','CLAIMED')
                  AND (i.state='READY' OR i.lease_expires_at<=clock_timestamp()) AND i.next_attempt_at<=clock_timestamp()
                  AND i.deadline_at>clock_timestamp() AND i.grant_expires_at>clock_timestamp() AND i.attempt_count<i.maximum_attempts
                  AND r.subject_type='USER' AND r.subject_issuer=i.actor_issuer AND r.subject_id=i.actor_id
                  AND r.status='COMPLETED' AND r.cancel_requested_at IS NULL AND r.finished_at IS NOT NULL
                ORDER BY i.next_attempt_at,i.intent_id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), tenantId, issuerFingerprint, limit));
    }

    /** Claim only source metadata; input content stays unread until the independent current source permission is proved. */
    public Optional<MemoryPreferenceIntent.Lease> claim(long tenantId, String issuerFingerprint, UUID id, String owner, Duration duration) {
        origin(tenantId, issuerFingerprint); identifier(owner, 128);
        if (duration == null || duration.compareTo(Duration.ofSeconds(1)) < 0 || duration.compareTo(Duration.ofSeconds(30)) > 0 || duration.getNano() % 1_000_000 != 0) throw invalid();
        return transact(status -> {
            var found = read(id, true); if (found.isEmpty()) return Optional.empty(); var row = found.orElseThrow(); Instant now = databaseNow();
            if (row.reservation().run().actor().tenantId() != tenantId || !row.reservation().run().actor().issuerFingerprint().equals(issuerFingerprint)
                    || !row.state().sealed() || row.ticket() == null || !row.ticket().expiresAt().isAfter(now) || !row.reservation().deadlineAt().isAfter(now)
                    || row.nextAttemptAt().isAfter(now) || row.state() == MemoryPreferenceIntent.State.CLAIMED && row.leaseExpiresAt().isAfter(now) || row.attemptCount() >= row.maximumAttempts()) return Optional.empty();
            var run = row.reservation().run();
            var events = jdbc.query("""
                    SELECT e.event_id FROM tianji_agent.agent_run r JOIN tianji_agent.agent_event e ON e.tenant_id=r.tenant_id AND e.run_id=r.run_id AND e.sequence=r.last_event_sequence
                    WHERE r.tenant_id=? AND r.run_id=? AND r.subject_type='USER' AND r.subject_issuer=? AND r.subject_id=? AND r.status='COMPLETED'
                      AND r.cancel_requested_at IS NULL AND r.finished_at IS NOT NULL AND e.event_type='RUN_STATUS_CHANGED' AND e.payload->>'status'='COMPLETED'
                    """, (rs, number) -> rs.getString(1), tenantId, run.runId(), issuerFingerprint, run.actor().subjectId());
            if (events.size() != 1) return Optional.empty();
            if (row.sourceEventId() != null && !row.sourceEventId().equals(events.getFirst())) { closeRow(row, MemoryPreferenceIntent.State.REJECTED, "SOURCE_EVENT_CHANGED", now, null, null); return Optional.empty(); }
            counters(row); Instant expires = now.plus(duration); if (row.ticket().expiresAt().isBefore(expires)) expires = row.ticket().expiresAt(); if (row.reservation().deadlineAt().isBefore(expires)) expires = row.reservation().deadlineAt();
            if (!expires.isAfter(now)) return Optional.empty();
            jdbc.update("""
                    UPDATE tianji_agent.agent_memory_preference_intent SET state='CLAIMED',source_event_id=?,lease_owner=?,lease_epoch=lease_epoch+1,
                        lease_expires_at=?,attempt_count=attempt_count+1,revision=revision+1,updated_at=?,last_error_code=NULL WHERE intent_id=?
                    """, events.getFirst(), owner, Timestamp.from(expires), Timestamp.from(now), id);
            return Optional.of(read(id, true).orElseThrow(MemoryPreferenceIntentStore::unavailable).lease());
        });
    }

    public MemoryPreferenceIntent.Lease sealCommands(MemoryPreferenceIntent.Lease lease, int totalCount, String commandsSha256) {
        if (totalCount < 0 || totalCount > 32 || !hash(commandsSha256)) throw invalid();
        return transact(status -> {
            var row = requireLease(lease, true);
            if (row.totalCount() != null) {
                if (row.totalCount() != totalCount || !row.commandsSha256().equals(commandsSha256)) throw conflict();
                return row.lease();
            }
            counters(row); Instant now = databaseNow();
            jdbc.update("UPDATE tianji_agent.agent_memory_preference_intent SET total_count=?,commands_sha256=?,revision=revision+1,updated_at=? WHERE intent_id=?",
                    totalCount, commandsSha256, Timestamp.from(now), lease.evidence().reservation().intentId());
            return read(lease.evidence().reservation().intentId(), true).orElseThrow(MemoryPreferenceIntentStore::unavailable).lease();
        });
    }

    /** Progress acknowledgement follows the fact commit. Lost acknowledgement replays the same fact receipt at the same index. */
    public Optional<MemoryPreferenceIntent.Lease> acknowledge(MemoryPreferenceIntent.Lease lease) {
        return transact(status -> {
            var row = requireLease(lease, true);
            if (row.totalCount() == null || row.nextIndex() >= row.totalCount()) throw conflict(); counters(row);
            Instant now = databaseNow(); int next = row.nextIndex() + 1;
            if (next == row.totalCount()) {
                closeRow(row, MemoryPreferenceIntent.State.APPLIED, null, now, null, null); return Optional.empty();
            }
            jdbc.update("UPDATE tianji_agent.agent_memory_preference_intent SET next_index=?,revision=revision+1,updated_at=? WHERE intent_id=?", next, Timestamp.from(now), row.reservation().intentId());
            return Optional.of(read(row.reservation().intentId(), true).orElseThrow(MemoryPreferenceIntentStore::unavailable).lease());
        });
    }

    public void defer(MemoryPreferenceIntent.Lease lease, String reasonCode, Duration delay) {
        reason(reasonCode); if (delay == null || delay.compareTo(Duration.ofMillis(100)) < 0 || delay.compareTo(Duration.ofHours(1)) > 0) throw invalid();
        transact(status -> {
            var row = requireLease(lease, true); Instant now = databaseNow();
            if (row.attemptCount() >= row.maximumAttempts()) closeRow(row, MemoryPreferenceIntent.State.DEAD, reasonCode, now, null, null);
            else {
                counters(row);
                jdbc.update("""
                        UPDATE tianji_agent.agent_memory_preference_intent SET state='READY',lease_owner=NULL,lease_expires_at=NULL,lease_epoch=lease_epoch+1,
                            revision=revision+1,next_attempt_at=?,updated_at=?,last_error_code=? WHERE intent_id=?
                        """, Timestamp.from(now.plus(delay)), Timestamp.from(now), reasonCode, row.reservation().intentId());
            }
            return Boolean.TRUE;
        });
    }

    public void finish(MemoryPreferenceIntent.Lease lease, MemoryPreferenceIntent.State state, String reasonCode) {
        if (state != MemoryPreferenceIntent.State.SKIPPED && state != MemoryPreferenceIntent.State.REJECTED && state != MemoryPreferenceIntent.State.DEAD) throw invalid(); reason(reasonCode);
        transact(status -> { var row = requireLease(lease, true); closeRow(row, state, reasonCode, databaseNow(), null, null); return Boolean.TRUE; });
    }

    /** Recheck the exact lease before and after network authorization, without carrying a transaction over that call. */
    public MemoryPreferenceIntent.Lease currentLease(MemoryPreferenceIntent.Lease lease) {
        return transact(status -> requireLease(lease, false).lease());
    }

    /** The issued permit invokes this within the existing fact transaction, holding its share fence until that transaction ends. */
    public void verifyFence(MemoryPreferenceIntent.Lease lease) {
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw new IllegalStateException("Preference fencing requires the verified fact DataSource transaction");
        try { primary(); requireLease(lease, false); }
        catch (DataAccessException failed) { throw unavailable(); }
    }

    public Optional<MemoryPreferenceIntent.View> view(TrustedAgentActor actor, String runId, String scopeId) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.USER) throw invalid(); identifier(runId, 64); identifier(scopeId, 128);
        return transact(status -> {
            var rows = jdbc.query("SELECT * FROM tianji_agent.agent_memory_preference_intent WHERE tenant_id=? AND actor_issuer=? AND actor_type='USER' AND actor_id=? AND run_id=? AND scope_id=?",
                    (rs, number) -> row(rs), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), runId, scopeId);
            if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst().view());
        });
    }

    public MemoryPreferenceIntent.View cancel(TrustedAgentActor actor, UUID id, long expectedRevision, String commandId, String requestSha256) {
        if (actor == null || actor.subjectType() != TrustedAgentActor.SubjectType.USER || expectedRevision < 1 || !hash(requestSha256)) throw invalid(); identifier(commandId, 128);
        return transact(status -> {
            var row = read(id, true).filter(value -> value.reservation().run().actor().equals(actor)).orElseThrow(MemoryPreferenceIntentStore::notFound);
            if (row.state() == MemoryPreferenceIntent.State.CANCELLED) {
                if (!commandId.equals(row.cancelCommandId()) || !requestSha256.equals(row.cancelRequestSha256())) throw conflict(); return row.view();
            }
            if (row.revision() != expectedRevision) throw conflict();
            if (row.state().terminal()) return row.view();
            closeRow(row, MemoryPreferenceIntent.State.CANCELLED, "USER_CANCELLED", databaseNow(), commandId, requestSha256);
            return read(id, true).orElseThrow(MemoryPreferenceIntentStore::unavailable).view();
        });
    }

    /** Bounded technical cleanup never renews authority; an expired grant returns to RESERVED for a new current USER request. */
    public int reconcile(long tenantId, String issuerFingerprint, int limit) {
        origin(tenantId, issuerFingerprint); limit(limit);
        return transact(status -> {
            var rows = jdbc.query("""
                    SELECT i.* FROM tianji_agent.agent_memory_preference_intent i JOIN tianji_agent.agent_run r ON r.tenant_id=i.tenant_id AND r.run_id=i.run_id
                    WHERE i.tenant_id=? AND i.actor_issuer=? AND i.state IN ('RESERVED','READY','CLAIMED')
                      AND (i.deadline_at<=clock_timestamp() OR i.grant_expires_at<=clock_timestamp()
                           OR r.status IN ('FAILED','CANCELLED','TIMED_OUT') OR r.cancel_requested_at IS NOT NULL
                           OR (i.attempt_count>=i.maximum_attempts AND (i.state<>'CLAIMED' OR i.lease_expires_at<=clock_timestamp())))
                    ORDER BY i.deadline_at,i.intent_id LIMIT ? FOR UPDATE OF i SKIP LOCKED
                    """, (rs, number) -> row(rs), tenantId, issuerFingerprint, limit);
            int changed = 0;
            for (var row : rows) {
                Instant now = databaseNow();
                var stopped = jdbc.queryForObject("SELECT status IN ('FAILED','CANCELLED','TIMED_OUT') OR cancel_requested_at IS NOT NULL FROM tianji_agent.agent_run WHERE tenant_id=? AND run_id=?", Boolean.class,
                        tenantId, row.reservation().run().runId());
                if (!row.reservation().deadlineAt().isAfter(now)) closeRow(row, MemoryPreferenceIntent.State.EXPIRED, "INTENT_EXPIRED", now, null, null);
                else if (Boolean.TRUE.equals(stopped)) closeRow(row, MemoryPreferenceIntent.State.SKIPPED, "SOURCE_NOT_COMPLETED", now, null, null);
                else if (row.attemptCount() >= row.maximumAttempts() && (row.state() != MemoryPreferenceIntent.State.CLAIMED || !row.leaseExpiresAt().isAfter(now))) closeRow(row, MemoryPreferenceIntent.State.DEAD, "ATTEMPTS_EXHAUSTED", now, null, null);
                else if (row.ticket() != null && !row.ticket().expiresAt().isAfter(now)) {
                    counters(row); if (row.reservation().issueEpoch() == Long.MAX_VALUE) throw capacity();
                    jdbc.update("""
                            UPDATE tianji_agent.agent_memory_preference_intent SET state='RESERVED',issue_epoch=issue_epoch+1,grant_id=NULL,ciphertext=NULL,grant_expires_at=NULL,
                                lease_owner=NULL,lease_expires_at=NULL,lease_epoch=lease_epoch+1,revision=revision+1,updated_at=?,last_error_code='GRANT_EXPIRED' WHERE intent_id=?
                            """, Timestamp.from(now), row.reservation().intentId());
                } else continue;
                changed++;
            }
            return changed;
        });
    }

    private Row requireLease(MemoryPreferenceIntent.Lease lease, boolean update) {
        Objects.requireNonNull(lease, "lease"); var reservation = lease.evidence().reservation();
        var row = read(reservation.intentId(), update).orElseThrow(MemoryPreferenceIntentStore::leaseExpired); Instant now = databaseNow();
        if (row.state() != MemoryPreferenceIntent.State.CLAIMED || !row.reservation().equals(reservation) || !Objects.equals(row.ticket(), lease.evidence().ticket())
                || !Objects.equals(row.leaseOwner(), lease.owner()) || row.leaseEpoch() != lease.epoch() || row.revision() != lease.revision()
                || !row.leaseExpiresAt().equals(lease.expiresAt()) || !row.leaseExpiresAt().isAfter(now) || !reservation.deadlineAt().isAfter(now) || !row.ticket().expiresAt().isAfter(now)
                || !Objects.equals(row.sourceEventId(), lease.sourceEventId()) || row.nextIndex() != lease.nextIndex() || !Objects.equals(row.totalCount(), lease.totalCount()) || !Objects.equals(row.commandsSha256(), lease.commandsSha256())) throw leaseExpired();
        return row;
    }

    private void closeRow(Row row, MemoryPreferenceIntent.State state, String reasonCode, Instant now, String cancelCommand, String cancelHash) {
        if (!state.terminal()) throw invalid(); if (reasonCode != null) reason(reasonCode); counters(row);
        jdbc.update("""
                UPDATE tianji_agent.agent_memory_preference_intent SET state=?,grant_id=NULL,ciphertext=NULL,grant_expires_at=NULL,
                    lease_owner=NULL,lease_expires_at=NULL,lease_epoch=lease_epoch+1,revision=revision+1,closed_at=?,updated_at=?,last_error_code=?,
                    next_index=?,cancel_command_id=?,cancel_request_sha256=?,cancelled_at=? WHERE intent_id=?
                """, state.name(), Timestamp.from(now), Timestamp.from(now), reasonCode, state == MemoryPreferenceIntent.State.APPLIED ? row.totalCount() : row.nextIndex(), cancelCommand, cancelHash,
                state == MemoryPreferenceIntent.State.CANCELLED ? Timestamp.from(now) : null, row.reservation().intentId());
    }

    private static void counters(Row row) { if (row.leaseEpoch() == Long.MAX_VALUE || row.revision() == Long.MAX_VALUE) throw capacity(); }
    private static void origin(long tenantId, String issuer) { if (tenantId < 1 || !hash(issuer)) throw invalid(); }
    private static void limit(int value) { if (value < 1 || value > 100) throw invalid(); }
    private static void identifier(String value, int maximum) { if (value == null || value.isBlank() || value.length() > maximum || !value.equals(value.trim()) || value.chars().anyMatch(Character::isISOControl) || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid(); }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static void reason(String value) { if (value == null || !value.matches("[A-Z][A-Z0-9_]{0,63}")) throw invalid(); }

    private boolean lockSource(AgentRunAuthorization.Request run) {
        if (run.actor().subjectType() != TrustedAgentActor.SubjectType.USER) throw invalid();
        var rows = jdbc.query("""
                SELECT 1 FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_issuer=? AND subject_type='USER' AND subject_id=? AND run_id=?
                    AND cancel_requested_at IS NULL AND (status='COMPLETED' OR (status IN ('QUEUED','RUNNING') AND deadline_at>clock_timestamp())) FOR SHARE
                """, (rs, row) -> 1, run.actor().tenantId(), run.actor().issuerFingerprint(), run.actor().subjectId(), run.runId());
        if (rows.size() > 1) throw unavailable();
        return rows.size() == 1 && run.equals(ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())).authorization());
    }

    private Optional<Row> read(UUID id, boolean update) {
        var rows = jdbc.query("SELECT * FROM tianji_agent.agent_memory_preference_intent WHERE intent_id=?" + (update ? " FOR UPDATE" : " FOR SHARE"), (rs, number) -> row(rs), id);
        if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static Row row(ResultSet rs) throws SQLException {
        try {
            var actor = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("actor_type")), rs.getString("actor_id"), rs.getString("actor_issuer"));
            var run = new AgentRunAuthorization.Request(actor, rs.getString("run_id"), rs.getString("definition_key"), rs.getLong("definition_version"), rs.getString("definition_sha256"),
                    rs.getString("request_sha256"), rs.getString("snapshot_sha256"), rs.getString("execution_policy_ref"), rs.getBoolean("session_required"), time(rs, "run_deadline_at"));
            var binding = new MemoryPreferenceSourceBinding(rs.getString("binding_id"), actor.tenantId(), actor.issuerFingerprint(), run.definitionKey(), run.definitionVersion(), run.definitionSha256(), rs.getString("scope_id"), rs.getString("policy_ref"));
            var reservation = new MemoryPreferenceIntent.Reservation(run, binding, URI.create(rs.getString("resource_uri")), time(rs, "deadline_at"), rs.getLong("issue_epoch"), rs.getObject("intent_id", UUID.class), rs.getString("binding_sha256"));
            var state = MemoryPreferenceIntent.State.valueOf(rs.getString("state"));
            var ticket = rs.getString("grant_id") == null ? null : new AgentExecutionGrant.Ticket(rs.getString("grant_id"), rs.getString("ciphertext"), time(rs, "grant_expires_at"));
            if (state.sealed() != (ticket != null)) throw unavailable();
            if (ticket != null) new MemoryPreferenceIntent.Evidence(reservation, ticket);
            var result = new Row(reservation, state, rs.getLong("revision"), ticket, rs.getString("source_event_id"), rs.getString("lease_owner"), rs.getLong("lease_epoch"), time(rs, "lease_expires_at"),
                    rs.getInt("next_index"), (Integer) rs.getObject("total_count"), rs.getString("commands_sha256"), rs.getInt("attempt_count"), rs.getInt("maximum_attempts"), time(rs, "next_attempt_at"),
                    rs.getString("last_error_code"), rs.getString("cancel_command_id"), rs.getString("cancel_request_sha256"), time(rs, "cancelled_at"), time(rs, "created_at"), time(rs, "updated_at"), time(rs, "closed_at"));
            if (state == MemoryPreferenceIntent.State.CLAIMED) result.lease();
            return result;
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    private static void requireBinding(MemoryPreferenceIntent.Reservation value, AgentRunAuthorization.Request run, MemoryPreferenceSourceBinding binding, URI resource) {
        if (!value.run().equals(run) || !value.binding().equals(binding) || !value.resource().toASCIIString().equals(resource.toASCIIString())
                || !value.bindingSha256().equals(MemoryPreferenceIntent.fingerprint(run, binding, resource, value.deadlineAt()))) throw conflict();
    }
    private Instant databaseNow() { return Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant(); }
    private void primary() {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
    }
    private <T> T transact(TransactionCallback<T> action) {
        if (!verified.get()) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Preference intent operations require their own transaction");
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                primary(); return action.doInTransaction(status);
            }));
        } catch (DataAccessException failed) { throw unavailable(); }
    }
    private static Instant time(ResultSet rs, String column) throws SQLException { var value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private record Row(MemoryPreferenceIntent.Reservation reservation, MemoryPreferenceIntent.State state, long revision, AgentExecutionGrant.Ticket ticket,
                        String sourceEventId, String leaseOwner, long leaseEpoch, Instant leaseExpiresAt, int nextIndex, Integer totalCount, String commandsSha256,
                        int attemptCount, int maximumAttempts, Instant nextAttemptAt, String lastErrorCode, String cancelCommandId, String cancelRequestSha256,
                        Instant cancelledAt, Instant createdAt, Instant updatedAt, Instant closedAt) {
        Row {
            if (revision < 1 || leaseEpoch < 0 || nextIndex < 0 || nextIndex > 32 || totalCount == null && (nextIndex != 0 || commandsSha256 != null)
                    || totalCount != null && (totalCount < nextIndex || totalCount > 32 || !hash(commandsSha256)) || attemptCount < 0 || maximumAttempts < 1 || maximumAttempts > 100 || attemptCount > maximumAttempts
                    || createdAt == null || updatedAt == null || updatedAt.isBefore(createdAt) || !reservation.deadlineAt().isAfter(createdAt) || nextAttemptAt == null
                    || state.terminal() != (closedAt != null) || closedAt != null && closedAt.isBefore(createdAt) || nextIndex > 0 && sourceEventId == null
                    || state != MemoryPreferenceIntent.State.CLAIMED && (leaseOwner != null || leaseExpiresAt != null)
                    || state == MemoryPreferenceIntent.State.APPLIED && (totalCount == null || totalCount < 1 || nextIndex != totalCount || sourceEventId == null)
                    || state == MemoryPreferenceIntent.State.CANCELLED && (cancelCommandId == null || !hash(cancelRequestSha256) || cancelledAt == null || !cancelledAt.equals(closedAt))
                    || state != MemoryPreferenceIntent.State.CANCELLED && (cancelCommandId != null || cancelRequestSha256 != null || cancelledAt != null)) throw unavailable();
            if (sourceEventId != null) identifier(sourceEventId, 64); if (lastErrorCode != null) reason(lastErrorCode);
        }
        MemoryPreferenceIntent.Lease lease() { return new MemoryPreferenceIntent.Lease(new MemoryPreferenceIntent.Evidence(reservation, ticket), leaseOwner, leaseEpoch, leaseExpiresAt, sourceEventId, nextIndex, totalCount, commandsSha256, revision); }
        MemoryPreferenceIntent.View view() { return new MemoryPreferenceIntent.View(reservation.intentId(), reservation.run().runId(), reservation.binding().scopeId(), reservation.binding().id(), reservation.binding().policyRef(), state, revision, nextIndex, totalCount, attemptCount, lastErrorCode, createdAt, updatedAt, closedAt); }
        @Override public String toString() { return "MemoryPreferenceIntentRow[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Memory preference intent is invalid"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Memory preference source binding changed"); }
    private static AgentLedgerException capacity() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Memory preference intent counter is exhausted"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory preference intent is unavailable or changed"); }
    private static AgentLedgerException leaseExpired() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Memory preference intent lease is no longer current"); }
    private static AgentLedgerException notFound() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_RESOURCE_NOT_FOUND, "Memory preference intent is unavailable in this owner scope"); }
}
