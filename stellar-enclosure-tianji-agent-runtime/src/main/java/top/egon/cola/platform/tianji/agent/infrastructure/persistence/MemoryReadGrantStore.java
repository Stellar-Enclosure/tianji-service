package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.memory.MemoryReadGrant;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/** Epoch-fenced sealed evidence. It never treats the ordinary run grant as authorization to read Memory. */
public final class MemoryReadGrantStore {
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public MemoryReadGrantStore(DataSource source, AgentLedgerService ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger"); if (!ledger.usesDataSource(source)) throw new IllegalArgumentException("Memory grants require the matching Agent ledger source");
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
    }

    public Optional<MemoryReadGrant.Reservation> reserve(AgentRunAuthorization.Request run, URI resource, MemoryReadGrant.Scope scope) {
        String fingerprint = MemoryReadGrant.fingerprint(run, resource, scope); var intent = MemoryReadGrant.intentId(run, scope);
        return transact(status -> {
            if (!lock(run, true)) return Optional.empty();
            if (!ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())).authorization().equals(run)) return Optional.empty();
            var epochs = jdbc.query("""
                    INSERT INTO tianji_agent.agent_memory_run_grant AS stored
                        (tenant_id,run_id,source_binding_sha256,owner_issuer,owner_type,owner_id,scope_id,actor_type,actor_id,binding_sha256,intent_id,issue_epoch,updated_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,1,clock_timestamp())
                    ON CONFLICT (tenant_id,run_id,source_binding_sha256) DO UPDATE SET issue_epoch=stored.issue_epoch+1,updated_at=clock_timestamp()
                    WHERE stored.binding_sha256=EXCLUDED.binding_sha256 AND stored.owner_issuer=EXCLUDED.owner_issuer
                      AND stored.owner_type=EXCLUDED.owner_type AND stored.owner_id=EXCLUDED.owner_id AND stored.scope_id=EXCLUDED.scope_id
                      AND stored.actor_type=EXCLUDED.actor_type AND stored.actor_id=EXCLUDED.actor_id AND stored.intent_id=EXCLUDED.intent_id
                      AND stored.issue_epoch<9223372036854775807 AND (stored.expires_at IS NULL OR stored.expires_at<=clock_timestamp())
                    RETURNING issue_epoch
                    """, (rs, row) -> rs.getLong(1), run.actor().tenantId(), run.runId(), scope.sourceBindingSha256(), run.actor().issuerFingerprint(), scope.owner().type().name(),
                    scope.owner().id(), scope.scopeId(), run.actor().subjectType().name(), run.actor().subjectId(), fingerprint, intent);
            if (epochs.isEmpty()) {
                if (currentLocked(run, fingerprint, scope).isPresent()) return Optional.empty(); throw unavailable();
            }
            if (epochs.size() != 1) throw unavailable();
            return Optional.of(new MemoryReadGrant.Reservation(run, resource, scope, epochs.getFirst(), intent, fingerprint));
        });
    }

    public boolean complete(MemoryReadGrant.Reservation reservation, AgentExecutionGrant.Ticket ticket) {
        Objects.requireNonNull(reservation, "reservation"); Objects.requireNonNull(ticket, "ticket"); var run = reservation.run(); var scope = reservation.scope();
        if (ticket.expiresAt().isAfter(run.deadlineAt())) throw new IllegalArgumentException("Memory grant expiry is outside its run deadline");
        var expires = ticket.expiresAt().truncatedTo(ChronoUnit.MICROS);
        return transact(status -> {
            if (!lock(run, true) || !expires.isAfter(ledger.databaseNow()) || !ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())).authorization().equals(run)) return false;
            return jdbc.update("""
                    UPDATE tianji_agent.agent_memory_run_grant SET grant_id=?,ciphertext=?,expires_at=?,updated_at=clock_timestamp()
                    WHERE tenant_id=? AND run_id=? AND source_binding_sha256=? AND binding_sha256=? AND intent_id=? AND issue_epoch=?
                      AND owner_issuer=? AND owner_type=? AND owner_id=? AND scope_id=? AND actor_type=? AND actor_id=?
                      AND (expires_at IS NULL OR expires_at<=clock_timestamp())
                    """, ticket.grantId(), ticket.ciphertext(), Timestamp.from(expires), run.actor().tenantId(), run.runId(), scope.sourceBindingSha256(), reservation.bindingSha256(),
                    reservation.intentId(), reservation.epoch(), run.actor().issuerFingerprint(), scope.owner().type().name(), scope.owner().id(), scope.scopeId(), run.actor().subjectType().name(), run.actor().subjectId()) == 1;
        });
    }

    public Optional<AgentExecutionGrant.Ticket> current(AgentRunAuthorization.Request run, URI resource, MemoryReadGrant.Scope scope) {
        String fingerprint = MemoryReadGrant.fingerprint(run, resource, scope);
        return transact(status -> {
            if (!lock(run, false) || !ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())).authorization().equals(run)) return Optional.empty();
            return currentLocked(run, fingerprint, scope);
        });
    }

    private Optional<AgentExecutionGrant.Ticket> currentLocked(AgentRunAuthorization.Request run, String fingerprint, MemoryReadGrant.Scope scope) {
        var tickets = jdbc.query("""
                SELECT grant_id,ciphertext,expires_at FROM tianji_agent.agent_memory_run_grant
                WHERE tenant_id=? AND run_id=? AND source_binding_sha256=? AND binding_sha256=? AND owner_issuer=?
                  AND owner_type=? AND owner_id=? AND scope_id=? AND actor_type=? AND actor_id=? AND intent_id=? AND expires_at>clock_timestamp()
                """, (rs, row) -> new AgentExecutionGrant.Ticket(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()), run.actor().tenantId(), run.runId(), scope.sourceBindingSha256(), fingerprint,
                run.actor().issuerFingerprint(), scope.owner().type().name(), scope.owner().id(), scope.scopeId(), run.actor().subjectType().name(), run.actor().subjectId(), MemoryReadGrant.intentId(run, scope));
        if (tickets.size() > 1) throw unavailable(); return tickets.isEmpty() ? Optional.empty() : Optional.of(tickets.getFirst());
    }
    private boolean lock(AgentRunAuthorization.Request run, boolean queued) {
        String states = queued ? "status='QUEUED'" : "status IN ('QUEUED','RUNNING')";
        var found = jdbc.query("SELECT 1 FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_issuer=? AND subject_type=? AND subject_id=? AND run_id=?"
                + " AND cancel_requested_at IS NULL AND deadline_at>clock_timestamp() AND " + states + (queued ? " FOR UPDATE" : " FOR SHARE"),
                (rs, row) -> 1, run.actor().tenantId(), run.actor().issuerFingerprint(), run.actor().subjectType().name(), run.actor().subjectId(), run.runId());
        if (found.size() > 1) throw unavailable(); return !found.isEmpty();
    }
    private <T> T transact(TransactionCallback<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Memory grant operations require their own transaction");
        try { return Objects.requireNonNull(transaction.execute(action)); }
        catch (DataAccessException failure) { throw unavailable(); }
    }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Memory read grant evidence is unavailable"); }
}
