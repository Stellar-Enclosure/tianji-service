package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stores only Shoubing-sealed evidence and fences concurrent issuance against the run. */
public final class AgentExecutionGrantStore {
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public AgentExecutionGrantStore(DataSource dataSource, AgentLedgerService ledger) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(dataSource)) throw new IllegalArgumentException("EXECUTION_GRANT_DATA_SOURCE_MISMATCH");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** Reserves a monotonically fenced attempt only while the exact owner's run is still queued. */
    public Optional<AgentExecutionGrant.Reservation> reserve(TrustedAgentActor actor, String runId, URI resource) {
        Objects.requireNonNull(actor, "actor");
        return transact(status -> {
            if (!lock(actor, runId, true)) return Optional.empty();
            var authorization = ledger.executionMaterial(actor, new GetAgentRunQuery(runId)).authorization();
            String binding = AgentExecutionGrant.fingerprint(authorization, resource);
            List<Long> epochs = jdbc.query("""
                    INSERT INTO tianji_agent.agent_run_execution_grant AS stored
                        (tenant_id, run_id, binding_sha256, issue_epoch, updated_at)
                    VALUES (?, ?, ?, 1, clock_timestamp())
                    ON CONFLICT (tenant_id, run_id) DO UPDATE
                    SET issue_epoch = stored.issue_epoch + 1, updated_at = clock_timestamp()
                    WHERE stored.binding_sha256 = EXCLUDED.binding_sha256
                    RETURNING issue_epoch
                    """, (rs, row) -> rs.getLong(1), actor.tenantId(), runId, binding);
            if (epochs.size() != 1) throw new IllegalStateException("EXECUTION_GRANT_BINDING_CONFLICT");
            return Optional.of(new AgentExecutionGrant.Reservation(authorization, resource, epochs.getFirst(), binding));
        });
    }

    /** A delayed response cannot overwrite a later attempt or attach evidence to a cancelled run. */
    public boolean complete(AgentExecutionGrant.Reservation reservation, AgentExecutionGrant.Ticket ticket) {
        Objects.requireNonNull(reservation, "reservation"); Objects.requireNonNull(ticket, "ticket");
        if (ticket.expiresAt().isAfter(reservation.authorization().deadlineAt())) {
            throw new IllegalArgumentException("EXECUTION_GRANT_EXPIRY_INVALID");
        }
        Instant expires = ticket.expiresAt().truncatedTo(ChronoUnit.MICROS);
        var actor = reservation.authorization().actor(); String runId = reservation.authorization().runId();
        Boolean saved = transact(status -> {
            if (!lock(actor, runId, true) || !expires.isAfter(ledger.databaseNow())) return false;
            var actual = ledger.executionMaterial(actor, new GetAgentRunQuery(runId)).authorization();
            if (!actual.equals(reservation.authorization())
                    || !reservation.bindingSha256().equals(AgentExecutionGrant.fingerprint(actual, reservation.resource()))) return false;
            return jdbc.update("""
                    UPDATE tianji_agent.agent_run_execution_grant
                    SET grant_id = ?, ciphertext = ?, expires_at = ?, updated_at = clock_timestamp()
                    WHERE tenant_id = ? AND run_id = ? AND issue_epoch = ? AND binding_sha256 = ?
                      AND (grant_id IS NULL OR grant_id = ?)
                    """, ticket.grantId(), ticket.ciphertext(), Timestamp.from(expires), actor.tenantId(), runId,
                    reservation.epoch(), reservation.bindingSha256(), ticket.grantId()) == 1;
        });
        return Boolean.TRUE.equals(saved);
    }

    /** Reads only evidence for the current unchanged intent and an unexpired active run. */
    public Optional<AgentExecutionGrant.Ticket> current(AgentRunAuthorization.Request authorization, URI resource) {
        Objects.requireNonNull(authorization, "authorization");
        String binding = AgentExecutionGrant.fingerprint(authorization, resource);
        var actor = authorization.actor(); String runId = authorization.runId();
        return transact(status -> {
            if (!lock(actor, runId, false)) return Optional.empty();
            var actual = ledger.executionMaterial(actor, new GetAgentRunQuery(runId)).authorization();
            if (!actual.equals(authorization)) return Optional.empty();
            return jdbc.query("""
                    SELECT grant_id, ciphertext, expires_at FROM tianji_agent.agent_run_execution_grant
                    WHERE tenant_id = ? AND run_id = ? AND binding_sha256 = ? AND expires_at > clock_timestamp()
                    """, (rs, row) -> new AgentExecutionGrant.Ticket(rs.getString(1), rs.getString(2),
                    rs.getTimestamp(3).toInstant()), actor.tenantId(), runId, binding).stream().findFirst();
        });
    }

    private <T> T transact(TransactionCallback<T> work) {
        try { return Objects.requireNonNull(transaction.execute(work)); }
        catch (DataAccessException failure) {
            org.slf4j.LoggerFactory.getLogger(AgentExecutionGrantStore.class).warn(
                    "Execution grant storage unavailable failureType={}", failure.getClass().getSimpleName());
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Execution grant storage is unavailable");
        }
    }

    private boolean lock(TrustedAgentActor actor, String runId, boolean queuedOnly) {
        String states = queuedOnly ? "status = 'QUEUED'" : "status IN ('QUEUED', 'RUNNING')";
        String locking = queuedOnly ? " FOR UPDATE" : " FOR SHARE";
        return !jdbc.query("""
                SELECT 1 FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND cancel_requested_at IS NULL AND deadline_at > clock_timestamp() AND
                """ + states + locking, (rs, row) -> 1, actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), runId).isEmpty();
    }
}
