package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolPolicy;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationRequestVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparationResult;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolInvocationStore.PreparedInvocationResult;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Internal same-source transaction participant. The owner verifies schema and current authority before entering. */
final class ToolInvocationSql {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore capabilities;
    private final JdbcTemplate jdbc;
    private final ToolActionIntentSql intents;
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();

    ToolInvocationSql(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
        this.source = source; this.ledger = ledger; this.capabilities = capabilities;
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
        intents = new ToolActionIntentSql(source, ledger);
    }

    PreparationResult prepare(PreparationRequestVO request, ToolExecutionGrant.EvidenceVO expected, ToolExecutionAuthorization.AuditVO audit, Instant authorityDeadline) {
        var live = requireRun(request, authorityDeadline);
        var current = capabilities.currentInTransaction(request.binding(), request.descriptor()).orElseThrow(ToolInvocationSql::unavailable);
        if (!expected.equals(current) || !current.expiresAt().isAfter(now(authorityDeadline))) throw unavailable();
        ToolActionIntentSql.PreparationResultVO action = request.action() == null ? null
                : intents.prepareWithStatus(request.binding().run(), request.lease(), request.action(), request.descriptor(), request.intentExpiry(), authorityDeadline);
        var actionRow = action == null ? null : action.row();
        var existing = read(request, current, live, actionRow, authorityDeadline);
        if (existing.isPresent()) {
            requireRun(request, authorityDeadline);
            return new PreparationResult(existing.orElseThrow(), projection(actionRow));
        }
        if (action != null && !action.newlyCreated()) {
            requireRun(request, authorityDeadline);
            return new PreparationResult(null, projection(actionRow));
        }
        Instant created = now(authorityDeadline).truncatedTo(ChronoUnit.MICROS);
        Instant notAfter = dispatchDeadline(created, live.toolTimeout(), request, current, actionRow);
        if (!notAfter.isAfter(now(authorityDeadline))) throw unavailable();
        var budget = ledger.budgets().reserveBudget(request.binding().run().actor(), request.lease(),
                new AgentBudgetRequest(request.reservationId(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0, request.estimatedCost()));
        if (!budget.newlyCreated()) throw conflict();
        boolean waiting = request.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED;
        String state = waiting ? "AWAITING_APPROVAL" : "PREPARED";
        String outcome = waiting ? codec.outcome(request.descriptor(), new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO)) : null;
        var owner = request.binding().run().actor();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_invocation
                  (tenant_id,run_id,invocation_id,owner_type,owner_issuer,owner_id,capability_id,capability_binding_sha256,capability_issue_epoch,lease_epoch,reservation_id,
                   tool_key,tool_version,tool_sha256,binding_sha256,parameters_sha256,risk,intent_id,state,revision,dispatch_epoch,authorization_audit,side_effect_outcome,outcome,
                   created_at,updated_at,dispatch_not_after,finished_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,1,0,CAST(? AS jsonb),'NONE',CAST(? AS jsonb),?,?,?,?)
                """, owner.tenantId(), request.binding().run().runId(), request.invocationId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                UUID.fromString(request.binding().capabilityId()), request.binding().bindingSha256(), current.reservation().issueEpoch(), request.lease().epoch(), request.reservationId(),
                request.descriptor().key(), request.descriptor().version(), request.descriptor().reference().contentSha256(), request.descriptor().policy().bindingSha256(), request.parametersSha256(),
                request.descriptor().policy().risk().name(), request.action() == null ? null : UUID.fromString(request.action().intentId()), state, codec.audit(audit), outcome,
                Timestamp.from(created), Timestamp.from(created), Timestamp.from(notAfter), waiting ? Timestamp.from(created) : null) != 1) throw unavailable();
        if (waiting) {
            var settled = ledger.budgets().settleBudget(owner, request.lease(), request.reservationId(), new AgentBudgetSettlement(0L, 0L, BigDecimal.ZERO));
            if (settled.state() != AgentBudgetReservation.State.SETTLED) throw unavailable();
        }
        var inserted = read(request, current, live, actionRow, authorityDeadline).orElseThrow(ToolInvocationSql::unavailable);
        requireRun(request, authorityDeadline);
        if (!notAfter.isAfter(now(authorityDeadline))) throw unavailable();
        var result = new PreparedInvocationResult(inserted.runId(), inserted.invocationId(), inserted.reservationId(), inserted.state(), inserted.revision(),
                inserted.capabilityIssueEpoch(), inserted.createdAt(), inserted.dispatchNotAfter(), inserted.finishedAt(), true);
        return new PreparationResult(result, projection(actionRow));
    }

    private RunSourceVO requireRun(PreparationRequestVO request, Instant deadline) {
        now(deadline); var run = request.binding().run(); var owner = run.actor(); var lease = request.lease();
        var expiries = jdbc.query("""
                SELECT lease_expires_at FROM tianji_agent.agent_run
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                  AND cancel_requested_at IS NULL FOR UPDATE
                """, (rs, row) -> rs.getTimestamp("lease_expires_at").toInstant(), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), run.runId(), lease.owner(), lease.epoch());
        Instant checked = now(deadline);
        if (expiries.size() != 1 || !expiries.getFirst().isAfter(checked) || !run.deadlineAt().isAfter(checked)) throw leaseLost();
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(owner, new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException privateSource) { throw unavailable(); }
        if (material == null || !run.equals(material.authorization()) || material.snapshot() == null) throw unavailable();
        var policy = material.snapshot().effectivePolicy();
        if (!material.snapshot().definition().dependencies().tools().contains(request.descriptor().reference())
                || !policy.allowedToolVersions().contains(request.descriptor().versionKey()) || policy.maxToolCalls() < 1
                || request.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED && !Objects.equals(policy.approvalPolicyRef(), request.descriptor().policy().approvalPolicyRef())) {
            throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Tool is not allowed by the fixed run policy");
        }
        if (!expiries.getFirst().isAfter(now(deadline))) throw leaseLost();
        return new RunSourceVO(policy.toolTimeout());
    }

    private Optional<PreparedInvocationResult> read(PreparationRequestVO request, ToolExecutionGrant.EvidenceVO evidence, RunSourceVO live, ToolActionIntentSql.Row action, Instant deadline) {
        now(deadline); var owner = request.binding().run().actor();
        var rows = jdbc.query("""
                SELECT i.*,b.lease_epoch AS budget_lease_epoch,b.kind AS budget_kind,b.tool_version AS budget_tool_version,
                       b.input_tokens AS budget_input_tokens,b.output_tokens AS budget_output_tokens,b.estimated_cost AS budget_estimated_cost,
                       b.state AS budget_state,b.actual_input_tokens AS budget_actual_input_tokens,b.actual_output_tokens AS budget_actual_output_tokens,b.actual_cost AS budget_actual_cost
                FROM tianji_agent.agent_tool_invocation i
                JOIN tianji_agent.agent_budget_reservation b ON b.tenant_id=i.tenant_id AND b.run_id=i.run_id AND b.reservation_id=i.reservation_id
                WHERE i.tenant_id=? AND i.owner_type=? AND i.owner_issuer=? AND i.owner_id=? AND i.run_id=? AND i.invocation_id=? FOR UPDATE OF i,b
                """, (rs, number) -> row(rs, request, evidence, live, action), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), request.binding().run().runId(), request.invocationId());
        now(deadline); if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private PreparedInvocationResult row(ResultSet rs, PreparationRequestVO request, ToolExecutionGrant.EvidenceVO evidence, RunSourceVO live, ToolActionIntentSql.Row action) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            if (!owner.equals(request.binding().run().actor()) || !request.binding().run().runId().equals(rs.getString("run_id"))) throw unavailable();
            if (!request.invocationId().equals(rs.getObject("invocation_id", UUID.class)) || !request.reservationId().equals(rs.getString("reservation_id"))
                    || request.lease().epoch() != rs.getLong("lease_epoch") || !request.descriptor().key().equals(rs.getString("tool_key"))
                    || request.descriptor().version() != rs.getLong("tool_version") || !request.descriptor().reference().contentSha256().equals(rs.getString("tool_sha256"))
                    || !request.descriptor().policy().bindingSha256().equals(rs.getString("binding_sha256")) || !request.parametersSha256().equals(rs.getString("parameters_sha256"))
                    || !request.descriptor().policy().risk().name().equals(rs.getString("risk"))
                    || !Objects.equals(request.action() == null ? null : UUID.fromString(request.action().intentId()), rs.getObject("intent_id", UUID.class))
                    || !UUID.fromString(request.binding().capabilityId()).equals(rs.getObject("capability_id", UUID.class))
                    || !request.binding().bindingSha256().equals(rs.getString("capability_binding_sha256")) || evidence.reservation().issueEpoch() != rs.getLong("capability_issue_epoch")
                    || !sameCost(request.estimatedCost(), rs.getBigDecimal("budget_estimated_cost"))) throw conflict();
            Instant created = time(rs, "created_at"), updated = time(rs, "updated_at"), notAfter = time(rs, "dispatch_not_after"), finished = time(rs, "finished_at");
            if (created == null || updated == null || updated.isBefore(created) || notAfter == null
                    || !notAfter.equals(dispatchDeadline(created, live.toolTimeout(), request, evidence, action)) || finished != null && updated.isBefore(finished)) throw unavailable();
            var audit = codec.audit(rs.getString("authorization_audit"));
            if (audit.boundary() != ToolExecutionAuthorization.Boundary.PREPARE || audit.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit.subjectType() != owner.subjectType()
                    || !evidence.grantId().equals(audit.grantId()) || !audit.expiresAt().isAfter(created) || audit.expiresAt().isAfter(evidence.expiresAt())) throw unavailable();
            if (rs.getLong("dispatch_epoch") != 0 || time(rs, "dispatched_at") != null || !"NONE".equals(rs.getString("side_effect_outcome"))
                    || rs.getString("dispatch_nonce_sha256") != null || rs.getString("executor_issuer") != null || rs.getString("executor_id") != null
                    || rs.getString("business_receipt_id") != null || rs.getString("error_code") != null) throw unavailable();
            if (rs.getLong("budget_lease_epoch") != request.lease().epoch() || !"TOOL".equals(rs.getString("budget_kind"))
                    || !request.descriptor().versionKey().equals(rs.getString("budget_tool_version")) || rs.getLong("budget_input_tokens") != 0 || rs.getLong("budget_output_tokens") != 0) throw unavailable();
            String state = rs.getString("state"), outcome = rs.getString("outcome");
            if ("PREPARED".equals(state)) {
                if (request.descriptor().policy().risk() == ToolPolicy.Risk.APPROVAL_REQUIRED || finished != null || outcome != null || !"RESERVED".equals(rs.getString("budget_state"))
                        || rs.getObject("budget_actual_input_tokens", Long.class) != null || rs.getObject("budget_actual_output_tokens", Long.class) != null || rs.getBigDecimal("budget_actual_cost") != null) throw unavailable();
            } else if ("AWAITING_APPROVAL".equals(state)) {
                var expected = new ToolExecutionOutcome(ToolExecutionOutcome.Status.AWAITING_APPROVAL, Map.of(), null, null, BigDecimal.ZERO);
                if (request.descriptor().policy().risk() != ToolPolicy.Risk.APPROVAL_REQUIRED || finished == null || !expected.equals(codec.outcome(outcome, request.descriptor()))
                        || !"SETTLED".equals(rs.getString("budget_state")) || !Long.valueOf(0).equals(rs.getObject("budget_actual_input_tokens", Long.class))
                        || !Long.valueOf(0).equals(rs.getObject("budget_actual_output_tokens", Long.class)) || !sameCost(BigDecimal.ZERO, rs.getBigDecimal("budget_actual_cost"))) throw unavailable();
            } else throw unavailable();
            return new PreparedInvocationResult(request.binding().run().runId(), request.invocationId(), request.reservationId(), state, rs.getLong("revision"),
                    rs.getLong("capability_issue_epoch"), created, notAfter, finished, false);
        } catch (AgentLedgerException scopedFailure) { throw scopedFailure; }
        catch (RuntimeException privateContent) { throw unavailable(); }
    }

    private Instant now(Instant deadline) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (Thread.currentThread().isInterrupted() || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null || deadline == null || !deadline.isAfter(value.toInstant())) throw unavailable(); return value.toInstant();
    }

    private static Instant dispatchDeadline(Instant created, Duration timeout, PreparationRequestVO request, ToolExecutionGrant.EvidenceVO evidence, ToolActionIntentSql.Row action) {
        Instant deadline = created.plus(timeout);
        if (request.binding().run().deadlineAt().isBefore(deadline)) deadline = request.binding().run().deadlineAt();
        if (evidence.expiresAt().isBefore(deadline)) deadline = evidence.expiresAt();
        if (action != null && action.expiresAt().isBefore(deadline)) deadline = action.expiresAt();
        return deadline.truncatedTo(ChronoUnit.MICROS);
    }
    private static ToolActionIntentStore.PreparedActionResult projection(ToolActionIntentSql.Row row) {
        return row == null ? null : new ToolActionIntentStore.PreparedActionResult(row.action().intentId(), row.action().requestSha256(), row.action().tool(), row.descriptor().policy().risk(),
                row.state(), row.revision(), row.originRunId(), row.createdAt(), row.expiresAt());
    }
    private static Instant time(ResultSet rs, String name) throws SQLException { var value = rs.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private static boolean sameCost(BigDecimal left, BigDecimal right) { return left == null ? right == null : right != null && left.compareTo(right) == 0; }
    private record RunSourceVO(Duration toolTimeout) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool invocation source or storage is unavailable"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool invocation identity was already used for different values"); }
    private static AgentLedgerException leaseLost() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Tool invocation run lease is no longer valid"); }
}
