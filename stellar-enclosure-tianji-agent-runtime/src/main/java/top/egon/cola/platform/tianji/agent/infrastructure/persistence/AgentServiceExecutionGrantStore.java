package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

/** Stores only sealed SERVICE evidence and serializes admission with the process dispatch fence. */
public final class AgentServiceExecutionGrantStore {
    private final DataSource dataSource;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AgentJsonCodec json = new AgentJsonCodec();

    public AgentServiceExecutionGrantStore(DataSource dataSource, AgentLedgerService ledger) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(dataSource)) throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_DATA_SOURCE_MISMATCH");
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }

    /** Does not migrate or repair storage; it verifies the provisioned V8 schema. */
    public void requireSchema() { new AgentStorageVerifier(dataSource).verifyServiceGrants(); }

    /** An active ticket or a closed process fence prevents a new reservation. */
    public Optional<AgentServiceExecutionGrant.Reservation> reserve(TrustedAgentActor actor, String runId, URI resource,
                                                                    AgentServiceExecutionGrant.Subject subject) {
        requireOwnedTransaction();
        Objects.requireNonNull(actor, "actor"); Objects.requireNonNull(subject, "subject");
        return transact(status -> {
            lockTimeout();
            var fence = lockFence(actor, runId, true);
            if (fence.isEmpty() || !lockRun(actor, runId, true)) return Optional.empty();
            var material = ledger.executionMaterial(actor, new GetAgentRunQuery(runId));
            if (!matches(fence.get(), material)) throw unavailable();
            var authorization = material.authorization();
            String binding = AgentServiceExecutionGrant.fingerprint(authorization, resource, subject);
            List<Long> epochs = jdbc.query("""
                    INSERT INTO tianji_agent.agent_run_service_execution_grant AS stored
                        (tenant_id, run_id, subject_client_id, subject_app_id, subject_business_code, subject_application_code,
                         subject_environment, subject_resource_uri, binding_sha256, issue_epoch, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, clock_timestamp())
                    ON CONFLICT (tenant_id, run_id) DO UPDATE
                    SET issue_epoch = stored.issue_epoch + 1, updated_at = clock_timestamp()
                    WHERE stored.binding_sha256 = EXCLUDED.binding_sha256
                      AND stored.subject_client_id = EXCLUDED.subject_client_id AND stored.subject_app_id = EXCLUDED.subject_app_id
                      AND stored.subject_business_code = EXCLUDED.subject_business_code
                      AND stored.subject_application_code = EXCLUDED.subject_application_code
                      AND stored.subject_environment = EXCLUDED.subject_environment AND stored.subject_resource_uri = EXCLUDED.subject_resource_uri
                      AND (stored.expires_at IS NULL OR stored.expires_at <= clock_timestamp())
                    RETURNING issue_epoch
                    """, (rs, row) -> rs.getLong(1), actor.tenantId(), runId, subject.clientId(), subject.appId(),
                    subject.businessCode(), subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString(), binding);
            if (epochs.isEmpty()) {
                boolean existing = !jdbc.query("""
                        SELECT 1 FROM tianji_agent.agent_run_service_execution_grant
                        WHERE tenant_id = ? AND run_id = ? AND binding_sha256 = ? AND subject_client_id = ? AND subject_app_id = ?
                          AND subject_business_code = ? AND subject_application_code = ? AND subject_environment = ?
                          AND subject_resource_uri = ? AND expires_at > clock_timestamp()
                        """, (rs, row) -> 1, actor.tenantId(), runId, binding, subject.clientId(), subject.appId(), subject.businessCode(),
                        subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()).isEmpty();
                if (existing) return Optional.empty();
                throw new IllegalStateException("SERVICE_EXECUTION_GRANT_BINDING_CONFLICT");
            }
            if (epochs.size() != 1) throw new IllegalStateException("SERVICE_EXECUTION_GRANT_BINDING_CONFLICT");
            return Optional.of(new AgentServiceExecutionGrant.Reservation(authorization, resource, subject, epochs.getFirst(), binding));
        });
    }

    /** A late response cannot attach evidence after a close, cancellation or later reservation. */
    public boolean complete(AgentServiceExecutionGrant.Reservation reservation, AgentServiceExecutionGrant.Ticket ticket) {
        requireOwnedTransaction();
        Objects.requireNonNull(reservation, "reservation"); Objects.requireNonNull(ticket, "ticket");
        if (ticket.expiresAt().isAfter(reservation.authorization().deadlineAt())) {
            throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_EXPIRY_INVALID");
        }
        Instant expires = ticket.expiresAt().truncatedTo(ChronoUnit.MICROS);
        var actor = reservation.authorization().actor(); var subject = reservation.subject(); String runId = reservation.authorization().runId();
        Boolean saved = transact(status -> {
            lockTimeout();
            var fence = lockFence(actor, runId, true);
            if (fence.isEmpty() || !lockRun(actor, runId, true) || !expires.isAfter(ledger.databaseNow())) return false;
            var material = ledger.executionMaterial(actor, new GetAgentRunQuery(runId));
            if (!matches(fence.get(), material) || !material.authorization().equals(reservation.authorization())
                    || !reservation.bindingSha256().equals(AgentServiceExecutionGrant.fingerprint(material.authorization(), reservation.resource(), subject))) {
                return false;
            }
            return jdbc.update("""
                    UPDATE tianji_agent.agent_run_service_execution_grant
                    SET grant_id = ?, ciphertext = ?, expires_at = ?, updated_at = clock_timestamp()
                    WHERE tenant_id = ? AND run_id = ? AND issue_epoch = ? AND binding_sha256 = ?
                      AND subject_client_id = ? AND subject_app_id = ? AND subject_business_code = ? AND subject_application_code = ?
                      AND subject_environment = ? AND subject_resource_uri = ? AND (grant_id IS NULL OR grant_id = ?)
                      AND (expires_at IS NULL OR expires_at <= clock_timestamp())
                    """, ticket.grantId(), ticket.ciphertext(), Timestamp.from(expires), actor.tenantId(), runId,
                    reservation.epoch(), reservation.bindingSha256(), subject.clientId(), subject.appId(), subject.businessCode(),
                    subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString(), ticket.grantId()) == 1;
        });
        return Boolean.TRUE.equals(saved);
    }

    /** Reads evidence only while the same process fence, service tuple and run remain active. */
    public Optional<AgentServiceExecutionGrant.Ticket> current(AgentRunAuthorization.Request authorization, URI resource,
                                                              AgentServiceExecutionGrant.Subject subject) {
        requireOwnedTransaction();
        Objects.requireNonNull(authorization, "authorization"); Objects.requireNonNull(subject, "subject");
        String binding = AgentServiceExecutionGrant.fingerprint(authorization, resource, subject);
        var actor = authorization.actor(); String runId = authorization.runId();
        return transact(status -> {
            lockTimeout();
            var fence = lockFence(actor, runId, false);
            if (fence.isEmpty() || !lockRun(actor, runId, false)) return Optional.empty();
            var material = ledger.executionMaterial(actor, new GetAgentRunQuery(runId));
            if (!matches(fence.get(), material) || !material.authorization().equals(authorization)) return Optional.empty();
            return jdbc.query("""
                    SELECT grant_id, ciphertext, expires_at FROM tianji_agent.agent_run_service_execution_grant
                    WHERE tenant_id = ? AND run_id = ? AND binding_sha256 = ? AND subject_client_id = ? AND subject_app_id = ?
                      AND subject_business_code = ? AND subject_application_code = ? AND subject_environment = ?
                      AND subject_resource_uri = ? AND expires_at > clock_timestamp()
                    """, (rs, row) -> {
                try { return new AgentServiceExecutionGrant.Ticket(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()); }
                catch (IllegalArgumentException | NullPointerException malformed) { throw unavailable(); }
            }, actor.tenantId(), runId, binding, subject.clientId(), subject.appId(), subject.businessCode(),
                    subject.applicationCode(), subject.environment(), subject.resourceUri().toASCIIString()).stream().findFirst();
        });
    }

    private Optional<Fence> lockFence(TrustedAgentActor actor, String runId, boolean write) {
        if (actor.subjectType() != TrustedAgentActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) {
            throw new IllegalArgumentException("SERVICE_EXECUTION_GRANT_ACTOR_INVALID");
        }
        List<Fence> rows = jdbc.query("""
                SELECT link_sha256, request_sha256 FROM tianji_agent.agent_process_dispatch
                WHERE tenant_id = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ? AND state = 'OPEN'
                """ + (write ? " FOR UPDATE" : " FOR SHARE"),
                (rs, row) -> new Fence(rs.getString(1), rs.getString(2)), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), runId);
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private boolean lockRun(TrustedAgentActor actor, String runId, boolean queuedOnly) {
        String states = queuedOnly ? "status = 'QUEUED'" : "status IN ('QUEUED', 'RUNNING')";
        return !jdbc.query("""
                SELECT 1 FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = 'SERVICE' AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND cancel_requested_at IS NULL AND deadline_at > clock_timestamp() AND
                """ + states + (queuedOnly ? " FOR UPDATE" : " FOR SHARE"), (rs, row) -> 1,
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), runId).isEmpty();
    }

    private boolean matches(Fence fence, AgentLedgerService.ExecutionMaterial material) {
        var link = material.snapshot().processLink();
        return link != null && fence.linkSha256() != null && fence.linkSha256().equals(json.processLinkHash(link))
                && fence.requestSha256() != null && fence.requestSha256().equals(material.authorization().requestSha256());
    }

    private void lockTimeout() { jdbc.queryForObject("SELECT set_config('lock_timeout', '5000ms', true)", String.class); }

    private static void requireOwnedTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("SERVICE_EXECUTION_GRANT_OWN_TRANSACTION_REQUIRED");
        }
    }

    private <T> T transact(TransactionCallback<T> work) {
        try { return Objects.requireNonNull(transaction.execute(work)); }
        catch (DataAccessException failure) {
            org.slf4j.LoggerFactory.getLogger(AgentServiceExecutionGrantStore.class).warn(
                    "Service execution grant storage unavailable failureType={}", failure.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private static AgentLedgerException unavailable() {
        return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Service execution grant storage is unavailable");
    }
    private record Fence(String linkSha256, String requestSha256) { }
}
