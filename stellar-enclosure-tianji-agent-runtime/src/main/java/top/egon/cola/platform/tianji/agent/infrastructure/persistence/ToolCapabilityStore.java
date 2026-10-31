package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentServiceExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Persists independent tool-capability evidence; stored metadata does not authenticate or authorize a tool call. */
public final class ToolCapabilityStore {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final AtomicBoolean verified = new AtomicBoolean();

    public ToolCapabilityStore(DataSource source, AgentLedgerService ledger) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(source)) throw new IllegalArgumentException("Tool capabilities require the exact Agent ledger DataSource");
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setTimeout(10);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public boolean usesLedger(AgentLedgerService candidate) { return ledger == candidate; }
    public void verifyStorage() {
        verified.set(false);
        try { new ToolExecutionStorageVerifier(source).verify(); verified.set(true); }
        catch (DataAccessException | TransactionException failure) { throw unavailable(); }
    }

    /** Reserves an issuance epoch, never another slot when immutable purpose or source metadata drift. */
    public Optional<ToolExecutionGrant.ReservationVO> reserve(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor) {
        requireDescriptor(binding, descriptor);
        return transact(status -> {
            if (!lockSource(binding, descriptor, true)) return Optional.empty();
            var actor = binding.run().actor(); var subject = binding.serviceSubject(); Instant now = currentTime(binding.run());
            int inserted = jdbc.update("""
                    INSERT INTO tianji_agent.agent_tool_capability
                        (tenant_id,run_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,purpose,
                         resource_uri,authorization_resource_key,source_client_id,source_app_id,source_business_code,source_application_code,
                         source_environment,source_resource_uri,capability_id,binding_sha256,issue_epoch,created_at,updated_at,deadline_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,?,?,?)
                    ON CONFLICT (tenant_id,run_id,tool_key,tool_version) DO NOTHING
                    """, actor.tenantId(), binding.run().runId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    binding.tool().key(), binding.tool().version(), binding.tool().contentSha256(), binding.backendBindingSha256(), binding.purpose(),
                    binding.resource().toASCIIString(), binding.authorizationResourceKey(), subject == null ? null : subject.clientId(), subject == null ? null : subject.appId(),
                    subject == null ? null : subject.businessCode(), subject == null ? null : subject.applicationCode(), subject == null ? null : subject.environment(),
                    subject == null ? null : subject.resourceUri().toASCIIString(), UUID.fromString(binding.capabilityId()), binding.bindingSha256(),
                    Timestamp.from(now), Timestamp.from(now), Timestamp.from(binding.run().deadlineAt()));
            var row = read(binding).orElseThrow(ToolCapabilityStore::unavailable); same(row.binding(), binding); now = currentTime(binding.run());
            if (inserted == 1) {
                if (row.issueEpoch() != 1 || row.evidence() != null) throw unavailable();
                return Optional.of(new ToolExecutionGrant.ReservationVO(binding, 1));
            }
            if (inserted != 0) throw unavailable();
            if (row.evidence() != null && row.evidence().expiresAt().isAfter(now)) return Optional.empty();
            if (row.issueEpoch() == Long.MAX_VALUE) throw capacity();
            long next = row.issueEpoch() + 1;
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_capability SET issue_epoch=?,grant_id=NULL,ciphertext=NULL,expires_at=NULL,updated_at=?
                    WHERE tenant_id=? AND run_id=? AND tool_key=? AND tool_version=? AND owner_type=? AND owner_issuer=? AND owner_id=?
                      AND binding_sha256=? AND issue_epoch=?
                    """, next, Timestamp.from(now), actor.tenantId(), binding.run().runId(), binding.tool().key(), binding.tool().version(),
                    actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), binding.bindingSha256(), row.issueEpoch()) != 1) throw unavailable();
            currentTime(binding.run());
            return Optional.of(new ToolExecutionGrant.ReservationVO(binding, next));
        });
    }

    /** An old issuance response cannot attach to a later epoch, changed binding or no-longer-queued source. */
    public boolean attach(ToolExecutionGrant.EvidenceVO evidence, ToolDescriptor descriptor) {
        if (evidence == null) throw invalid(); var reservation = evidence.reservation(); var binding = reservation.binding();
        requireDescriptor(binding, descriptor);
        var normalized = new ToolExecutionGrant.EvidenceVO(reservation, evidence.grantId(), evidence.ciphertext(), evidence.expiresAt().truncatedTo(ChronoUnit.MICROS));
        return transact(status -> {
            if (!lockSource(binding, descriptor, true) || !normalized.expiresAt().isAfter(currentTime(binding.run()))) return false;
            var found = read(binding); if (found.isEmpty()) return false;
            var row = found.orElseThrow(); same(row.binding(), binding); Instant now = currentTime(binding.run());
            if (row.issueEpoch() != reservation.issueEpoch() || !normalized.expiresAt().isAfter(now)) return false;
            if (row.evidence() != null) {
                if (!row.evidence().equals(normalized)) throw conflict();
                return true;
            }
            var actor = binding.run().actor();
            if (jdbc.update("""
                    UPDATE tianji_agent.agent_tool_capability SET grant_id=?,ciphertext=?,expires_at=?,updated_at=?
                    WHERE tenant_id=? AND run_id=? AND tool_key=? AND tool_version=? AND owner_type=? AND owner_issuer=? AND owner_id=?
                      AND binding_sha256=? AND issue_epoch=? AND grant_id IS NULL AND ciphertext IS NULL AND expires_at IS NULL
                    """, normalized.grantId(), normalized.ciphertext(), Timestamp.from(normalized.expiresAt()), Timestamp.from(now),
                    actor.tenantId(), binding.run().runId(), binding.tool().key(), binding.tool().version(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    binding.bindingSha256(), reservation.issueEpoch()) != 1) throw unavailable();
            if (!normalized.expiresAt().isAfter(currentTime(binding.run()))) throw unavailable();
            return true;
        });
    }

    /** Returns internal sealed evidence only. Its presence is not a current tool permission decision. */
    public Optional<ToolExecutionGrant.EvidenceVO> current(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor) {
        requireDescriptor(binding, descriptor);
        return transact(status -> currentInTransaction(binding, descriptor));
    }

    /** Internal participant; its owner fences the live run before taking this capability lock. */
    Optional<ToolExecutionGrant.EvidenceVO> currentInTransaction(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor) {
        requireDescriptor(binding, descriptor);
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (!lockSource(binding, descriptor, false)) return Optional.empty();
        var found = read(binding); if (found.isEmpty()) return Optional.empty();
        var row = found.orElseThrow(); same(row.binding(), binding); Instant now = currentTime(binding.run());
        if (!verified.get()) throw unavailable();
        return row.evidence() == null || !row.evidence().expiresAt().isAfter(now) ? Optional.empty() : Optional.of(row.evidence());
    }

    private boolean lockSource(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor, boolean queuedOnly) {
        var run = binding.run(); var actor = run.actor(); databaseNow();
        String states = queuedOnly ? "status='QUEUED'" : "status IN ('QUEUED','RUNNING')";
        var deadlines = jdbc.query("SELECT deadline_at FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?"
                        + " AND cancel_requested_at IS NULL AND deadline_at>clock_timestamp() AND " + states + " FOR SHARE",
                (rs, number) -> rs.getTimestamp("deadline_at").toInstant(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), run.runId());
        if (deadlines.size() > 1) throw unavailable();
        if (deadlines.isEmpty() || !deadlines.getFirst().equals(run.deadlineAt()) || !deadlines.getFirst().isAfter(databaseNow())) return false;
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(actor, new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException malformedSource) { throw unavailable(); }
        if (material == null) throw unavailable();
        if (!run.equals(material.authorization())) return false;
        var snapshot = material.snapshot(); var policy = snapshot.effectivePolicy();
        if (!snapshot.definition().dependencies().tools().contains(binding.tool()) || !policy.allowedToolVersions().contains(descriptor.versionKey()) || policy.maxToolCalls() < 1
                || descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !Objects.equals(descriptor.policy().approvalPolicyRef(), policy.approvalPolicyRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool version or policy is not allowed by the immutable execution snapshot");
        }
        return run.deadlineAt().isAfter(databaseNow());
    }

    private Optional<StoredCapabilityResult> read(ToolExecutionGrant.BindingVO binding) {
        var actor = binding.run().actor();
        var rows = jdbc.query("""
                SELECT * FROM tianji_agent.agent_tool_capability
                WHERE tenant_id=? AND run_id=? AND tool_key=? AND tool_version=? AND owner_type=? AND owner_issuer=? AND owner_id=? FOR UPDATE
                """, (rs, number) -> row(rs, binding.run()), actor.tenantId(), binding.run().runId(), binding.tool().key(), binding.tool().version(),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId());
        if (rows.size() > 1) throw unavailable();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private StoredCapabilityResult row(ResultSet rs, AgentRunAuthorization.Request run) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            if (!owner.equals(run.actor()) || !run.runId().equals(rs.getString("run_id")) || !run.deadlineAt().equals(time(rs, "deadline_at"))) throw unavailable();
            String client = rs.getString("source_client_id"), app = rs.getString("source_app_id"), business = rs.getString("source_business_code"),
                    application = rs.getString("source_application_code"), environment = rs.getString("source_environment"), resource = rs.getString("source_resource_uri");
            AgentServiceExecutionGrant.Subject subject = client == null && app == null && business == null && application == null && environment == null && resource == null
                    ? null : new AgentServiceExecutionGrant.Subject(client, app, business, application, environment, URI.create(resource));
            var tool = new DefinitionResourceRef(rs.getString("tool_key"), rs.getLong("tool_version"), rs.getString("tool_sha256"));
            var binding = new ToolExecutionGrant.BindingVO(run, tool, rs.getString("backend_binding_sha256"), rs.getString("purpose"), URI.create(rs.getString("resource_uri")), rs.getString("authorization_resource_key"), subject);
            if (!binding.capabilityId().equals(rs.getObject("capability_id", UUID.class).toString()) || !binding.bindingSha256().equals(rs.getString("binding_sha256"))) throw unavailable();
            long epoch = rs.getLong("issue_epoch"); var reservation = new ToolExecutionGrant.ReservationVO(binding, epoch);
            String grant = rs.getString("grant_id"), ciphertext = rs.getString("ciphertext"); Instant expires = time(rs, "expires_at");
            var evidence = grant == null && ciphertext == null && expires == null ? null : new ToolExecutionGrant.EvidenceVO(reservation, grant, ciphertext, expires);
            Instant created = time(rs, "created_at"), updated = time(rs, "updated_at");
            if (created == null || updated == null || updated.isBefore(created) || !run.deadlineAt().isAfter(created) || evidence != null && !evidence.expiresAt().isAfter(created)) throw unavailable();
            return new StoredCapabilityResult(binding, epoch, evidence, created, updated);
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    private Instant currentTime(AgentRunAuthorization.Request run) {
        Instant now = databaseNow(); if (!run.deadlineAt().isAfter(now)) throw unavailable(); return now;
    }
    private Instant databaseNow() {
        if (Thread.currentThread().isInterrupted() || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class); if (value == null) throw unavailable(); return value.toInstant();
    }
    private <T> T transact(TransactionCallback<T> action) {
        if (!verified.get()) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Tool capability operations require their own transaction");
        try {
            return Objects.requireNonNull(transaction.execute(status -> {
                databaseNow(); jdbc.queryForObject("SELECT set_config('search_path','pg_catalog,tianji_agent,public',true)", String.class);
                T result = action.doInTransaction(status); if (!verified.get()) throw unavailable(); return result;
            }));
        } catch (DataAccessException | TransactionException failure) { throw unavailable(); }
    }
    private static void requireDescriptor(ToolExecutionGrant.BindingVO binding, ToolDescriptor descriptor) {
        if (binding == null || descriptor == null || !binding.tool().equals(descriptor.reference()) || !binding.backendBindingSha256().equals(descriptor.policy().bindingSha256())) throw invalid();
    }
    private static void same(ToolExecutionGrant.BindingVO stored, ToolExecutionGrant.BindingVO requested) {
        if (!stored.equals(requested) || !stored.bindingSha256().equals(requested.bindingSha256())) throw conflict();
    }
    private static Instant time(ResultSet rs, String column) throws SQLException { Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private record StoredCapabilityResult(ToolExecutionGrant.BindingVO binding, long issueEpoch, ToolExecutionGrant.EvidenceVO evidence, Instant createdAt, Instant updatedAt) {
        @Override public String toString() { return "StoredToolCapability[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool capability descriptor or binding is invalid"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool capability source or immutable binding changed"); }
    private static AgentLedgerException capacity() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED, "Tool capability issue epoch is exhausted"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool capability storage is unavailable or changed"); }
}
