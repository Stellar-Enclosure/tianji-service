package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionAuthorization;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionGrant;
import top.egon.cola.platform.tianji.agent.domain.tool.ToolExecutionOutcome;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.DisclosureWindowVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.RecordedAccessVO;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolResultAccessStore.StartResultAccessRequestVO;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;
import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import javax.sql.DataSource;
import java.math.BigDecimal;
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
import java.util.UUID;

/** Internal same-source transaction participant for immutable result-access facts and their local budget. */
final class ToolResultAccessSql {
    private final DataSource source;
    private final AgentLedgerService ledger;
    private final ToolCapabilityStore capabilities;
    private final JdbcTemplate jdbc;
    private final ToolActionIntentSql intents;
    private final ToolExecutionJsonCodec codec = new ToolExecutionJsonCodec();

    ToolResultAccessSql(DataSource source, AgentLedgerService ledger, ToolCapabilityStore capabilities) {
        this.source = source; this.ledger = ledger; this.capabilities = capabilities;
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); intents = new ToolActionIntentSql(source, ledger);
    }
    RecordedAccessVO recordKnownStartResultAccess(StartResultAccessRequestVO request, ToolExecutionGrant.EvidenceVO expected,
            ToolExecutionAuthorization.AuditVO originalAudit, Duration expectedToolTimeout, DisclosureWindowVO window) {
        Instant deadline = window.deadlineAt();
        requireRun(request, expectedToolTimeout, deadline);
        var current = capabilities.currentInTransaction(request.binding(), request.descriptor()).orElseThrow(ToolResultAccessSql::unavailable);
        if (!current.equals(expected) || !current.reservation().binding().equals(request.binding()) || !current.expiresAt().isAfter(now(deadline))
                || originalAudit == null || originalAudit.boundary() != ToolExecutionAuthorization.Boundary.RECONCILE
                || originalAudit.decision() != ToolExecutionAuthorization.Decision.ALLOW || originalAudit.subjectType() != request.binding().run().actor().subjectType()
                || !originalAudit.grantId().equals(current.grantId()) || originalAudit.expiresAt().isBefore(deadline)
                || originalAudit.expiresAt().isAfter(current.expiresAt())) throw unavailable();
        // This unlocked probe only rejects reuse of an existing host ID for another target before a possibly missing action is locked.
        var existing = read(request, deadline, false);
        var action = intents.lockForWrite(request.action(), request.descriptor(), deadline);
        if (!action.action().requestSha256().equals(request.action().requestSha256()) || !action.action().intentId().equals(request.action().intentId())
                || !action.descriptor().equals(request.descriptor())) throw conflict();
        var outcome = action.outcome();
        try {
            if (!(action.state().equals("APPLIED") && outcome != null && outcome.status() == ToolExecutionOutcome.Status.SUCCEEDED
                    || action.state().equals("REJECTED") && outcome != null && outcome.status() == ToolExecutionOutcome.Status.REJECTED
                    || action.state().equals("FAILED_KNOWN") && outcome != null && outcome.status() == ToolExecutionOutcome.Status.FAILED_KNOWN)) throw unavailable();
            outcome = outcome.validated(request.descriptor()); ToolResultAccessStore.knownStartOutcome(outcome);
        } catch (AgentLedgerException scopedFailure) { throw scopedFailure; }
        catch (RuntimeException privateSource) { throw unavailable(); }
        String fingerprint = OrchestrationRequestDigests.hash(List.of("TIANJI_TOOL_RESULT_ACCESS_SOURCE_V1", action.action().intentId(), Long.toString(action.revision()),
                action.action().requestSha256(), codec.outcome(request.descriptor(), outcome)), Map.of());
        if (existing.isPresent()) {
            var stored = read(request, deadline, true).orElseThrow(ToolResultAccessSql::unavailable);
            sameSource(stored, action.revision(), fingerprint); requireRun(request, expectedToolTimeout, deadline);
            return result(request, action.revision(), fingerprint, outcome, stored.budget(), window);
        }
        if (!jdbc.query("SELECT invocation_id FROM tianji_agent.agent_tool_invocation WHERE tenant_id=? AND run_id=? AND invocation_id=?",
                (rs, number) -> rs.getObject("invocation_id", UUID.class), request.binding().run().actor().tenantId(), request.binding().run().runId(), request.accessId()).isEmpty()) throw conflict();
        now(deadline);
        var budget = ledger.budgets().reserveBudget(request.binding().run().actor(), request.lease(),
                new AgentBudgetRequest(request.reservationId(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0, BigDecimal.ZERO));
        if (!budget.newlyCreated()) throw conflict();
        var audit = new ToolExecutionAuthorization.AuditVO(originalAudit.schemaVersion(), originalAudit.localCheckId(), originalAudit.boundary(), originalAudit.decision(),
                originalAudit.subjectType(), originalAudit.grantId(), originalAudit.authVersion(), originalAudit.catalogVersion(), originalAudit.policyVersion(),
                originalAudit.checkedAt().truncatedTo(ChronoUnit.MICROS), originalAudit.expiresAt().truncatedTo(ChronoUnit.MICROS));
        Instant created = now(deadline).truncatedTo(ChronoUnit.MICROS);
        if (created.isBefore(audit.checkedAt()) || !created.isBefore(audit.expiresAt())) throw unavailable();
        var owner = request.binding().run().actor();
        if (jdbc.update("""
                INSERT INTO tianji_agent.agent_tool_result_access
                  (tenant_id,run_id,access_id,reservation_id,owner_type,owner_issuer,owner_id,capability_id,capability_binding_sha256,capability_issue_epoch,lease_epoch,
                   tool_key,tool_version,tool_sha256,backend_binding_sha256,parameters_sha256,intent_id,request_sha256,risk,source_action_revision,source_result_sha256,
                   authorization_audit,authorization_checked_at,authorization_expires_at,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?)
                """, owner.tenantId(), request.binding().run().runId(), request.accessId(), request.reservationId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(),
                UUID.fromString(request.binding().capabilityId()), request.binding().bindingSha256(), current.reservation().issueEpoch(), request.lease().epoch(), request.descriptor().key(),
                request.descriptor().version(), request.descriptor().reference().contentSha256(), request.descriptor().policy().bindingSha256(), request.parametersSha256(),
                UUID.fromString(request.action().intentId()), request.action().requestSha256(), request.descriptor().policy().risk().name(), action.revision(), fingerprint,
                codec.audit(audit), Timestamp.from(audit.checkedAt()), Timestamp.from(audit.expiresAt()), Timestamp.from(created)) != 1) throw unavailable();
        var inserted = read(request, deadline, true).orElseThrow(ToolResultAccessSql::unavailable); sameSource(inserted, action.revision(), fingerprint);
        requireRun(request, expectedToolTimeout, deadline);
        return result(request, action.revision(), fingerprint, outcome, inserted.budget(), window);
    }

    private void requireRun(StartResultAccessRequestVO request, Duration expectedTimeout, Instant deadline) {
        now(deadline); var run = request.binding().run(); var owner = run.actor(); var lease = request.lease();
        var expiries = jdbc.query("""
                SELECT lease_expires_at FROM tianji_agent.agent_run
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND run_id=?
                  AND status='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_expires_at>clock_timestamp() AND deadline_at>clock_timestamp()
                  AND cancel_requested_at IS NULL FOR UPDATE
                """, (rs, number) -> rs.getTimestamp("lease_expires_at").toInstant(), owner.tenantId(), owner.subjectType().name(), owner.issuerFingerprint(), owner.subjectId(), run.runId(), lease.owner(), lease.epoch());
        Instant checked = now(deadline);
        if (expiries.size() != 1 || !expiries.getFirst().isAfter(checked) || !run.deadlineAt().isAfter(checked)) throw leaseLost();
        AgentLedgerService.ExecutionMaterial material;
        try { material = ledger.executionMaterial(owner, new GetAgentRunQuery(run.runId())); }
        catch (RuntimeException privateSource) { throw unavailable(); }
        if (material == null || !run.equals(material.authorization()) || material.snapshot() == null || !run.runId().equals(material.run().runId())) throw unavailable();
        var policy = material.snapshot().effectivePolicy();
        if (!material.snapshot().definition().dependencies().tools().contains(request.descriptor().reference())
                || !policy.allowedToolVersions().contains(request.descriptor().versionKey()) || policy.maxToolCalls() < 1) throw denied();
        if (!policy.toolTimeout().equals(expectedTimeout)) throw unavailable();
        if (!expiries.getFirst().isAfter(now(deadline))) throw leaseLost();
    }

    private Optional<AccessRowVO> read(StartResultAccessRequestVO request, Instant deadline, boolean withBudget) {
        now(deadline);
        String sql = withBudget ? """
                SELECT a.*,b.lease_epoch AS budget_lease_epoch,b.kind AS budget_kind,b.tool_version AS budget_tool_version,
                       b.input_tokens AS budget_input_tokens,b.output_tokens AS budget_output_tokens,b.estimated_cost AS budget_estimated_cost,
                       b.state AS budget_state,b.actual_input_tokens AS budget_actual_input_tokens,b.actual_output_tokens AS budget_actual_output_tokens,b.actual_cost AS budget_actual_cost
                FROM tianji_agent.agent_tool_result_access a
                JOIN tianji_agent.agent_budget_reservation b ON b.tenant_id=a.tenant_id AND b.run_id=a.run_id AND b.reservation_id=a.reservation_id
                WHERE a.tenant_id=? AND a.run_id=? AND a.access_id=? FOR UPDATE OF b
                """ : "SELECT a.* FROM tianji_agent.agent_tool_result_access a WHERE a.tenant_id=? AND a.run_id=? AND a.access_id=?";
        var rows = jdbc.query(sql, (rs, number) -> row(rs, request, withBudget), request.binding().run().actor().tenantId(), request.binding().run().runId(), request.accessId());
        now(deadline); if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }
    private AccessRowVO row(ResultSet rs, StartResultAccessRequestVO request, boolean withBudget) throws SQLException {
        try {
            var owner = new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"), rs.getString("owner_issuer"));
            if (!owner.equals(request.binding().run().actor()) || !request.binding().run().runId().equals(rs.getString("run_id"))
                    || !request.accessId().equals(rs.getObject("access_id", UUID.class)) || !request.reservationId().equals(rs.getString("reservation_id"))
                    || !UUID.fromString(request.binding().capabilityId()).equals(rs.getObject("capability_id", UUID.class))
                    || !request.binding().bindingSha256().equals(rs.getString("capability_binding_sha256")) || request.lease().epoch() != rs.getLong("lease_epoch")
                    || !request.descriptor().key().equals(rs.getString("tool_key")) || request.descriptor().version() != rs.getLong("tool_version")
                    || !request.descriptor().reference().contentSha256().equals(rs.getString("tool_sha256"))
                    || !request.descriptor().policy().bindingSha256().equals(rs.getString("backend_binding_sha256")) || !request.parametersSha256().equals(rs.getString("parameters_sha256"))
                    || !UUID.fromString(request.action().intentId()).equals(rs.getObject("intent_id", UUID.class)) || !request.action().requestSha256().equals(rs.getString("request_sha256"))
                    || !request.descriptor().policy().risk().name().equals(rs.getString("risk"))) throw conflict();
            long revision = rs.getLong("source_action_revision"); String sha256 = rs.getString("source_result_sha256");
            if (rs.getLong("capability_issue_epoch") < 1 || revision < 1 || sha256 == null || !sha256.matches("[0-9a-f]{64}")) throw unavailable();
            var audit = codec.audit(rs.getString("authorization_audit")); Instant checked = time(rs, "authorization_checked_at"), expires = time(rs, "authorization_expires_at"), created = time(rs, "created_at");
            if (audit.boundary() != ToolExecutionAuthorization.Boundary.RECONCILE || audit.decision() != ToolExecutionAuthorization.Decision.ALLOW || audit.subjectType() != owner.subjectType()
                    || !audit.checkedAt().equals(checked) || !audit.expiresAt().equals(expires) || !micros(checked) || !micros(expires) || !micros(created)
                    || created.isBefore(checked) || !created.isBefore(expires)) throw unavailable();
            return new AccessRowVO(revision, sha256, withBudget ? budget(rs, request) : null);
        } catch (AgentLedgerException scopedFailure) { throw scopedFailure; }
        catch (RuntimeException privateContent) { throw unavailable(); }
    }
    private AgentBudgetReservation budget(ResultSet rs, StartResultAccessRequestVO request) throws SQLException {
        if (rs.getLong("budget_lease_epoch") != request.lease().epoch() || !"TOOL".equals(rs.getString("budget_kind")) || !request.descriptor().versionKey().equals(rs.getString("budget_tool_version"))
                || rs.getLong("budget_input_tokens") != 0 || rs.getLong("budget_output_tokens") != 0) throw unavailable();
        var budget = new AgentBudgetReservation(request.reservationId(), request.lease().epoch(), AgentBudgetRequest.Kind.TOOL, request.descriptor().versionKey(), 0, 0,
                rs.getBigDecimal("budget_estimated_cost"), AgentBudgetReservation.State.valueOf(rs.getString("budget_state")), rs.getObject("budget_actual_input_tokens", Long.class),
                rs.getObject("budget_actual_output_tokens", Long.class), rs.getBigDecimal("budget_actual_cost"), false);
        if (!ToolResultAccessStore.zeroAccessBudget(budget)) throw unavailable(); return budget;
    }
    private Instant now(Instant deadline) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()
                || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null || deadline == null || !deadline.isAfter(value.toInstant())) throw unavailable(); return value.toInstant();
    }
    private static RecordedAccessVO result(StartResultAccessRequestVO request, long revision, String fingerprint, ToolExecutionOutcome outcome, AgentBudgetReservation budget, DisclosureWindowVO window) {
        return new RecordedAccessVO(request.binding().run().runId(), request.accessId(), request.action().intentId(), revision, fingerprint, outcome, budget, window);
    }
    private static void sameSource(AccessRowVO row, long revision, String sha256) { if (row.sourceRevision() != revision || !row.sourceSha256().equals(sha256)) throw unavailable(); }
    private static Instant time(ResultSet rs, String name) throws SQLException { var value = rs.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private static boolean micros(Instant value) { return value != null && value.getNano() % 1_000 == 0; }
    private record AccessRowVO(long sourceRevision, String sourceSha256, AgentBudgetReservation budget) { }
    private static AgentLedgerException unavailable() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE, "Tool result access is unavailable or changed"); }
    private static AgentLedgerException conflict() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_IDEMPOTENCY_CONFLICT, "Tool result access identity changed"); }
    private static AgentLedgerException leaseLost() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_LEASE_EXPIRED, "Tool result access run lease is no longer valid"); }
    private static AgentLedgerException denied() { return new AgentLedgerException(AgentErrorCodeEnum.TIANJI_PERMISSION_DENIED, "Current tool result access was denied"); }
}
