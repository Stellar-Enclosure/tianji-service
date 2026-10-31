package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolActionRequest;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolDescriptor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.DefinitionResourceRef;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Internal transaction participant. Its owner verifies schema and current independent authority first. */
final class ToolActionIntentSql {
    static final Set<String> STATES = Set.of("PREPARED", "AWAITING_APPROVAL", "DISPATCHING", "APPLIED", "REJECTED", "FAILED_KNOWN", "UNKNOWN", "EXPIRED");
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final JdbcTemplate jdbc;
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();

    ToolActionIntentSql(DataSource source, AgentLedgerService ledger) {
        this.source = Objects.requireNonNull(source, "source"); this.ledger = Objects.requireNonNull(ledger, "ledger");
        if (!ledger.usesDataSource(source)) throw invalid();
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
    }

    Row prepare(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                Instant requestedExpiry, Instant authorityDeadline) {
        return prepareWithStatus(run, lease, action, descriptor, requestedExpiry, authorityDeadline).row();
    }

    PreparationResultVO prepareWithStatus(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor,
                                          Instant requestedExpiry, Instant authorityDeadline) {
        binding(run, lease, action, descriptor); requireRun(run, lease, descriptor, authorityDeadline);
        UUID id = UUID.fromString(action.intentId());
        // Serialize first insertion even when distinct request bodies race the same stable business identity.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        requireRun(run, lease, descriptor, authorityDeadline);
        var existing = read(run.actor(), id, true, authorityDeadline);
        if (existing.isPresent()) {
            same(existing.orElseThrow(), action); requireRun(run, lease, descriptor, authorityDeadline);
            return new PreparationResultVO(existing.orElseThrow(), false);
        }
        Instant now = now(authorityDeadline);
        Instant expiry = Objects.requireNonNull(requestedExpiry, "requestedExpiry").truncatedTo(ChronoUnit.MICROS);
        if (!expiry.isAfter(now) || expiry.isAfter(now.plus(Duration.ofDays(7)))) throw invalid();
        String state = descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED ? "AWAITING_APPROVAL" : "PREPARED";
        int inserted = jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_action_intent
                  (tenant_id,intent_id,owner_type,owner_issuer,owner_id,action_key,business_key,business_version,tool_key,tool_version,tool_sha256,binding_sha256,request_sha256,logical_identity_sha256,
                   descriptor,arguments,origin_run_id,origin_snapshot_sha256,risk,approval_policy_ref,state,revision,side_effect_outcome,created_at,updated_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?,?,?,?,1,'NONE',?,?,?) ON CONFLICT DO NOTHING
                """, run.actor().tenantId(), id, run.actor().subjectType().name(), run.actor().issuerFingerprint(), run.actor().subjectId(), action.actionKey(), action.businessKey(), action.businessVersion(),
                descriptor.key(), descriptor.version(), descriptor.reference().contentSha256(), descriptor.policy().bindingSha256(), action.requestSha256(), logicalIdentity(action), codec.descriptor(descriptor), codec.arguments(descriptor, action.arguments()),
                run.runId(), run.snapshotSha256(), descriptor.policy().risk().name(), descriptor.policy().approvalPolicyRef(), state, Timestamp.from(now), Timestamp.from(now), Timestamp.from(expiry));
        if (inserted != 1) throw unavailable();
        var row = read(run.actor(), id, true, authorityDeadline).orElseThrow(ToolActionIntentSql::unavailable);
        same(row, action); requireRun(run, lease, descriptor, authorityDeadline);
        return new PreparationResultVO(row, true);
    }

    record PreparationResultVO(Row row, boolean newlyCreated) {
        PreparationResultVO { Objects.requireNonNull(row, "row"); }
        @Override public String toString() { return "ToolActionIntentPreparation[redacted]"; }
    }

    Optional<Row> current(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action,
                          ToolDescriptor descriptor, Instant authorityDeadline) {
        binding(run, lease, action, descriptor); requireRun(run, lease, descriptor, authorityDeadline);
        var found = read(run.actor(), UUID.fromString(action.intentId()), false, authorityDeadline);
        found.ifPresent(row -> same(row, action)); requireRun(run, lease, descriptor, authorityDeadline); return found;
    }

    private Optional<Row> read(TrustedAgentActor owner, UUID id, boolean lock, Instant deadline) {
        now(deadline);
        var rows = jdbc.query("SELECT * FROM tianji_agent.agent_tool_action_intent WHERE tenant_id=? AND owner_type=? AND owner_issuer=? AND owner_id=? AND intent_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, number) -> row(rs), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), id);
        now(deadline); if (rows.size() > 1) throw unavailable();
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private void requireRun(AgentRunAuthorization.Request run, AgentRunLease lease, ToolDescriptor descriptor, Instant deadline) {
        now(deadline);
        var expiries = jdbc.query("""
                SELECT lease_expires_at FROM tianji_agent.agent_run WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                  AND cancel_requested_at IS NULL FOR SHARE
                """, (rs, number) -> rs.getTimestamp("lease_expires_at").toInstant(), run.actor().tenantId(), run.actor().subjectType().name(), run.actor().issuerFingerprint(), run.actor().subjectId(), run.runId(), lease.owner(), lease.epoch());
        Instant checked = now(deadline);
        if (expiries.size() != 1 || !expiries.getFirst().isAfter(checked) || !run.deadlineAt().isAfter(checked)) throw leaseLost();
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(run.actor(), new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException malformedSource) { throw unavailable(); }
        if (!run.equals(material.authorization())) throw unavailable();
        if (!material.snapshot().definition().dependencies().tools().contains(descriptor.reference())
                || !material.snapshot().effectivePolicy().allowedToolVersions().contains(descriptor.versionKey()) || material.snapshot().effectivePolicy().maxToolCalls() < 1
                || descriptor.policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !Objects.equals(descriptor.policy().approvalPolicyRef(), material.snapshot().effectivePolicy().approvalPolicyRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool is not allowed by the fixed run policy");
        }
        // The WHERE predicate may have been evaluated before a lock wait. The captured DB value, not the claim hint, is authoritative.
        if (!expiries.getFirst().isAfter(now(deadline))) throw leaseLost();
    }

    private Instant now(Instant deadline) {
        requireTransaction();
        if (Thread.currentThread().isInterrupted() || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp time = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (time == null || deadline == null || !deadline.isAfter(time.toInstant())) throw unavailable();
        return time.toInstant();
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
    }

    private Row row(ResultSet rs) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            var reference = new DefinitionResourceRef(rs.getString("tool_key"), rs.getLong("tool_version"), rs.getString("tool_sha256"));
            var descriptor = codec.descriptor(rs.getString("descriptor"), reference);
            var action = new ToolActionRequest(owner, descriptor, rs.getString("business_key"), rs.getString("business_version"), codec.arguments(rs.getString("arguments"), descriptor));
            if (!action.intentId().equals(rs.getObject("intent_id", UUID.class).toString()) || !logicalIdentity(action).equals(rs.getString("logical_identity_sha256"))
                    || !action.requestSha256().equals(rs.getString("request_sha256")) || !action.actionKey().equals(rs.getString("action_key"))
                    || !descriptor.policy().bindingSha256().equals(rs.getString("binding_sha256")) || !descriptor.policy().risk().name().equals(rs.getString("risk"))
                    || !Objects.equals(descriptor.policy().approvalPolicyRef(), rs.getString("approval_policy_ref"))) throw unavailable();
            return new Row(action, descriptor, rs.getString("origin_run_id"), rs.getString("origin_snapshot_sha256"), rs.getString("state"), rs.getLong("revision"),
                    rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getTimestamp("expires_at").toInstant());
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    private static void binding(AgentRunAuthorization.Request run, AgentRunLease lease, ToolActionRequest action, ToolDescriptor descriptor) {
        if (run == null || lease == null || action == null || descriptor == null || !run.actor().equals(action.owner())
                || !run.runId().equals(lease.runId()) || !descriptor.reference().equals(action.tool())) throw invalid();
        sameAction(new ToolActionRequest(run.actor(), descriptor, action.businessKey(), action.businessVersion(), action.arguments()), action);
    }
    private static void same(Row row, ToolActionRequest action) { sameAction(row.action(), action); }
    private static void sameAction(ToolActionRequest stored, ToolActionRequest action) {
        if (!stored.owner().equals(action.owner()) || !stored.intentId().equals(action.intentId()) || !stored.requestSha256().equals(action.requestSha256())
                || !stored.actionKey().equals(action.actionKey()) || !stored.businessKey().equals(action.businessKey()) || !stored.businessVersion().equals(action.businessVersion())
                || !stored.tool().equals(action.tool()) || !stored.policy().equals(action.policy()) || !stored.arguments().equals(action.arguments())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool action intent was already prepared with different inputs");
        }
    }
    private static String logicalIdentity(ToolActionRequest action) {
        return OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1", action.owner().identityFingerprint(), action.actionKey(), action.businessKey(), action.businessVersion()), Map.of());
    }

    record Row(ToolActionRequest action, ToolDescriptor descriptor, String originRunId, String originSnapshotSha256, String state, long revision, Instant createdAt, Instant updatedAt, Instant expiresAt) {
        Row {
            if (action == null || descriptor == null || !descriptor.reference().equals(action.tool()) || originRunId == null || originRunId.isBlank()
                    || originSnapshotSha256 == null || !originSnapshotSha256.matches("[0-9a-f]{64}") || !STATES.contains(state) || revision < 1
                    || createdAt == null || updatedAt == null || expiresAt == null || updatedAt.isBefore(createdAt)
                    || !expiresAt.isAfter(createdAt) || expiresAt.isAfter(createdAt.plus(Duration.ofDays(7)))) throw invalid();
        }
        @Override public String toString() { return "ToolActionIntentRow[redacted]"; }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Tool action source or binding is invalid"); }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool action source or storage is unavailable"); }
    private static AgentLedgerException leaseLost() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Tool action run lease is no longer valid"); }
}
