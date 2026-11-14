package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AgentRuntimeTemplateCommand.TechnicalLimitsVO;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Objects;

import static top.egon.cola.platform.tianji.agent.domain.RuntimeBindingBO.RUNTIME_V2;

/** Runtime binding writes participate in the owning Agent ledger transaction. */
@Slf4j
@Component("agentRuntimeBindingStore")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class AgentRuntimeBindingStore {
    private static final int MAX_POLICY_BYTES = 16_384;

    @Qualifier("jdbcTemplate")
    private final JdbcTemplate jdbc;
    private final AgentJsonCodec codec = new AgentJsonCodec();

    public AgentRuntimeBindingStore(DataSource source) {
        this(new JdbcTemplate(Objects.requireNonNull(source, "source")));
    }

    boolean usesDataSource(DataSource candidate) {
        return jdbc.getDataSource() == candidate;
    }

    JdbcTemplate jdbc() {
        return jdbc;
    }

    public RuntimeBindingBO create(
            @NotNull @Valid TrustedAgentActor owner, @NotNull @Valid RuntimeBindingBO request) {
        Objects.requireNonNull(request, "binding");
        var binding = new RuntimeBindingBO(request.getTenantId(), request.getRunId(), request.getProfile(),
                request.getSnapshotSha256(), request.getConfigurationSha256(), request.getTemplateSha256(), request.getPolicy(),
                request.getNodeExecutionRef(), request.getAttemptDeadlineAt(), request.getModelCalls(), request.getToolCalls(),
                request.getActiveCalls(), request.getRevision(), request.getCreatedAt());
        binding.validateForCreate();
        if (owner == null || binding.getTenantId() != owner.tenantId()) throw unavailable();
        var run = lockRun(owner, binding.getRunId());
        var now = now();
        requireSnapshot(run, binding);
        if (!binding.getAttemptDeadlineAt().isAfter(now) || run.cancelled()) {
            throw error(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED);
        }
        String policy = codec.write(binding.getPolicy().toWire(), MAX_POLICY_BYTES);
        jdbc.update("""
                INSERT INTO tianji_agent.agent_runtime_binding
                  (tenant_id,run_id,runtime_profile,snapshot_sha256,configuration_sha256,template_sha256,safety_policy,
                   node_execution_ref,attempt_deadline_at,model_calls,tool_calls,active_calls,revision,created_at)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,0,0,0,1,?)
                ON CONFLICT (tenant_id,run_id) DO NOTHING
                """, owner.tenantId(), binding.getRunId(), binding.getProfile(), binding.getSnapshotSha256(),
                binding.getConfigurationSha256(), binding.getTemplateSha256(), policy, binding.getNodeExecutionRef(),
                utc(binding.getAttemptDeadlineAt()), utc(now));
        var persisted = lockBinding(owner.tenantId(), binding.getRunId());
        if (!persisted.getSnapshotSha256().equals(binding.getSnapshotSha256())
                || !persisted.getConfigurationSha256().equals(binding.getConfigurationSha256())
                || !persisted.getTemplateSha256().equals(binding.getTemplateSha256())
                || !persisted.getPolicy().equals(binding.getPolicy())
                || !persisted.getNodeExecutionRef().equals(binding.getNodeExecutionRef())
                || !persisted.getAttemptDeadlineAt().equals(binding.getAttemptDeadlineAt())) {
            throw error(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT);
        }
        return persisted;
    }

    LockedRunVO lockRun(TrustedAgentActor owner, String runId) {
        requireTransaction();
        if (owner == null || "LEGACY".equals(owner.issuerFingerprint())) throw unavailable();
        var rows = jdbc.query("""
                SELECT status,revision,lease_epoch,lease_owner,lease_expires_at,deadline_at,cancel_requested_at,
                       execution_snapshot->>'runtimeProfile' AS runtime_profile,
                       execution_snapshot->>'snapshotSha256' AS snapshot_sha256
                FROM tianji_agent.agent_run
                WHERE tenant_id=? AND run_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?
                FOR UPDATE
                """, (rs, row) -> new LockedRunVO(rs.getString("status"), rs.getLong("revision"), rs.getLong("lease_epoch"),
                rs.getString("lease_owner"), instant(rs, "lease_expires_at"), instant(rs, "deadline_at"),
                rs.getTimestamp("cancel_requested_at") != null, rs.getString("runtime_profile"), rs.getString("snapshot_sha256")),
                owner.tenantId(), runId, owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId());
        if (rows.size() != 1 || !RUNTIME_V2.equals(rows.getFirst().profile())) throw unavailable();
        return rows.getFirst();
    }

    RuntimeBindingBO lockBinding(long tenantId, String runId) {
        var rows = jdbc.query("""
                SELECT tenant_id,run_id,runtime_profile,snapshot_sha256,configuration_sha256,template_sha256,safety_policy,
                       node_execution_ref,attempt_deadline_at,model_calls,tool_calls,active_calls,revision,created_at
                FROM tianji_agent.agent_runtime_binding WHERE tenant_id=? AND run_id=? FOR UPDATE
                """, this::decode, tenantId, runId);
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst();
    }

    void requireCurrent(LockedRunVO run, RuntimeBindingBO binding, AgentRunLease lease, long revision, Instant now) {
        requireSnapshot(run, binding);
        if (run.revision() != revision) throw error(AgentErrorCodeEnum.TIANJI_REVISION_CONFLICT);
        if (!"RUNNING".equals(run.status()) || run.cancelled()
                || !lease.owner().equals(run.leaseOwner()) || lease.epoch() != run.leaseEpoch()
                || run.leaseExpiresAt() == null || !run.leaseExpiresAt().isAfter(now)) {
            throw error(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED);
        }
        if (!binding.getAttemptDeadlineAt().isAfter(now)) throw error(AgentErrorCodeEnum.TIANJI_DISPATCH_EXPIRED);
    }

    void requireSnapshot(LockedRunVO run, RuntimeBindingBO binding) {
        if (!RUNTIME_V2.equals(binding.getProfile()) || !binding.getSnapshotSha256().equals(run.snapshotSha256())
                || binding.getAttemptDeadlineAt().isAfter(run.deadlineAt())) throw unavailable();
    }

    Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (rs, row) -> instant(rs, "clock_timestamp"));
    }

    void reserve(RuntimeBindingBO binding, int model, int tool) {
        var policy = binding.getPolicy();
        int updated = jdbc.update("""
                UPDATE tianji_agent.agent_runtime_binding
                SET model_calls=model_calls+?,tool_calls=tool_calls+?,active_calls=active_calls+1,revision=revision+1
                WHERE tenant_id=? AND run_id=? AND revision=?
                    AND model_calls<=?-? AND tool_calls<=?-? AND active_calls<?
                """, model, tool, binding.getTenantId(), binding.getRunId(), binding.getRevision(),
                policy.maxModelCalls(), model, policy.maxToolCalls(), tool, policy.maxParallelism());
        if (updated != 1) throw error(AgentErrorCodeEnum.TIANJI_CAPACITY_EXCEEDED);
    }

    void release(RuntimeBindingBO binding) {
        int updated = jdbc.update("""
                UPDATE tianji_agent.agent_runtime_binding SET active_calls=active_calls-1,revision=revision+1
                WHERE tenant_id=? AND run_id=? AND revision=? AND active_calls>0
                """, binding.getTenantId(), binding.getRunId(), binding.getRevision());
        if (updated != 1) throw unavailable();
    }

    private RuntimeBindingBO decode(ResultSet rs, int row) throws SQLException {
        try {
            return new RuntimeBindingBO(rs.getLong("tenant_id"), rs.getString("run_id"), rs.getString("runtime_profile"),
                    rs.getString("snapshot_sha256"), rs.getString("configuration_sha256"), rs.getString("template_sha256"),
                    TechnicalLimitsVO.fromExactWire(codec.readObject(rs.getString("safety_policy"))), rs.getString("node_execution_ref"),
                    instant(rs, "attempt_deadline_at"), rs.getInt("model_calls"), rs.getInt("tool_calls"), rs.getInt("active_calls"),
                    rs.getLong("revision"), instant(rs, "created_at"));
        } catch (IllegalArgumentException | IllegalStateException invalidData) {
            throw unavailable();
        }
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.hasResource(Objects.requireNonNull(jdbc.getDataSource()))) {
            throw new IllegalStateException("Runtime safety storage requires the owning Agent ledger transaction");
        }
    }

    void requireRollback() {
        var holder = (ConnectionHolder) TransactionSynchronizationManager.getResource(jdbc.getDataSource());
        if (holder == null) throw new IllegalStateException("Missing Agent ledger transaction");
        holder.setRollbackOnly();
    }

    static Instant instant(ResultSet rs, String name) throws SQLException {
        var timestamp = rs.getObject(name, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    static AgentLedgerException error(AgentErrorCodeEnum code) {
        return new AgentLedgerException(code, "Agent runtime safety operation rejected");
    }

    static AgentLedgerException unavailable() {
        return error(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE);
    }

    record LockedRunVO(String status, long revision, long leaseEpoch, String leaseOwner, Instant leaseExpiresAt,
                     Instant deadlineAt, boolean cancelled, String profile, String snapshotSha256) { }
}
