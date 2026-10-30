package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetRequest;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetReservation;
import top.egon.cola.platform.tianji.agent.domain.AgentBudgetSettlement;
import top.egon.cola.platform.tianji.agent.domain.AgentRunLease;
import top.egon.cola.platform.tianji.agent.domain.PendingAgentRun;
import top.egon.cola.platform.tianji.agent.domain.GrantReadyAgentRun;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.agent.GetAgentRunQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import top.egon.cola.platform.tianji.contract.agent.ExecutionPolicyDTO;

final class JdbcAgentLedgerRepository {
    private static final String RUN_COLUMNS = "run_id, definition_key, definition_version, session_id, status, wait_reason, "
            + "cancel_requested_at, attempt, revision, last_event_sequence, deadline_at, created_at, finished_at";
    private final JdbcTemplate jdbc;
    private final AgentJsonCodec json;

    JdbcAgentLedgerRepository(JdbcTemplate jdbc, AgentJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
    }

    Optional<String> linkedSubmissionKey(TrustedAgentActor actor, String runId, String linkSha256) {
        List<String> keys = jdbc.query("SELECT idempotency_key FROM tianji_agent.agent_process_dispatch "
                        + "WHERE tenant_id=? AND subject_issuer=? AND subject_id=? AND run_id=? AND link_sha256=?",
                (rs, row) -> rs.getString(1), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), runId, linkSha256);
        if (keys.size() > 1) throw new IllegalStateException("Linked terminal submission evidence is ambiguous");
        return keys.stream().findFirst();
    }

    void insertDefinitionFamily(TrustedAgentActor actor, String key, Instant now) {
        jdbc.update("""
                INSERT INTO tianji_agent.agent_definition (tenant_id, definition_key, created_at)
                VALUES (?, ?, ?) ON CONFLICT (tenant_id, definition_key) DO NOTHING
                """, actor.tenantId(), key, Timestamp.from(now));
    }

    int insertDefinitionVersion(TrustedAgentActor actor, String key, long version, String hash,
                                String specification, Instant now) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_definition_version
                    (tenant_id, definition_key, version, content_sha256, specification, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (tenant_id, definition_key, version) DO NOTHING
                """, actor.tenantId(), key, version, hash, specification, Timestamp.from(now));
    }

    Optional<DefinitionRow> definition(long tenantId, String key, long version) {
        return jdbc.query("""
                SELECT content_sha256, specification::text AS specification
                FROM tianji_agent.agent_definition_version
                WHERE tenant_id = ? AND definition_key = ? AND version = ?
                """, (rs, row) -> new DefinitionRow(rs.getString("content_sha256"),
                json.readDefinition(rs.getString("specification"))), tenantId, key, version).stream().findFirst();
    }

    Optional<StoredRun> byIdempotencyKey(TrustedAgentActor actor, String key) {
        return jdbc.query("""
                SELECT * FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND idempotency_key = ?
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), key)
                .stream().findFirst();
    }

    boolean lockOwnedSession(TrustedAgentActor actor, String sessionId) {
        return !jdbc.query("""
                SELECT 1 FROM tianji_agent.agent_session
                WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND app_name='tianji' AND session_id=?
                FOR KEY SHARE
                """, (rs, row) -> 1, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), sessionId).isEmpty();
    }

    Optional<StoredRun> run(TrustedAgentActor actor, String runId) {
        return jdbc.query("""
                SELECT * FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), runId)
                .stream().findFirst();
    }

    Optional<StoredRun> runForUpdate(TrustedAgentActor actor, String runId) {
        return jdbc.query("""
                SELECT * FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                FOR UPDATE
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), runId)
                .stream().findFirst();
    }

    List<AgentRunDTO> list(TrustedAgentActor actor, Instant beforeCreatedAt, String beforeRunId, int limit) {
        String base = "SELECT " + RUN_COLUMNS + " FROM tianji_agent.agent_run "
                + "WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? ";
        if (beforeCreatedAt == null) {
            return jdbc.query(base + "ORDER BY created_at DESC, run_id DESC LIMIT ?", (rs, row) -> mapRun(rs),
                    actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), limit);
        }
        return jdbc.query(base + "AND (created_at,run_id) < (?,?) ORDER BY created_at DESC, run_id DESC LIMIT ?",
                (rs, row) -> mapRun(rs), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                Timestamp.from(beforeCreatedAt), beforeRunId, limit);
    }

    int insertRun(TrustedAgentActor actor, String runId, String definitionKey, long definitionVersion,
                  String idempotencyKey, String requestHash, String sessionId, String correlationId,
                  Instant deadline, String snapshot, Instant now) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_run
                    (tenant_id, run_id, definition_key, definition_version, subject_type, subject_issuer, subject_id,
                     idempotency_key, request_hash, session_id, correlation_id, status, revision,
                     last_event_sequence, deadline_at, execution_snapshot, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 1, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (tenant_id, subject_type, subject_issuer, subject_id, idempotency_key) DO NOTHING
                """, actor.tenantId(), runId, definitionKey, definitionVersion, actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), idempotencyKey, requestHash, sessionId, correlationId,
                Timestamp.from(deadline), snapshot, Timestamp.from(now));
    }

    Optional<StoredRun> claim(TrustedAgentActor actor, String runId, long expectedRevision,
                              String owner, long leaseMillis) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'RUNNING', revision = revision + 1, last_event_sequence = last_event_sequence + 1,
                    attempt = attempt + 1, lease_epoch = lease_epoch + 1, lease_owner = ?,
                    lease_expires_at = LEAST(clock_timestamp() + (? * interval '1 millisecond'), deadline_at)
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = 'QUEUED' AND cancel_requested_at IS NULL
                  AND deadline_at > clock_timestamp()
                RETURNING *
                """, runMapper(), owner, leaseMillis, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    Optional<StoredRun> renew(TrustedAgentActor actor, AgentRunLease lease, long leaseMillis) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET lease_expires_at = LEAST(clock_timestamp() + (? * interval '1 millisecond'), deadline_at)
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                  AND cancel_requested_at IS NULL
                RETURNING *
                """, runMapper(), leaseMillis, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                lease.runId(), lease.owner(), lease.epoch()).stream().findFirst();
    }

    Optional<StoredRun> appendRunningEvent(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET revision = revision + 1, last_event_sequence = last_event_sequence + 1
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                  AND cancel_requested_at IS NULL
                RETURNING *
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                lease.runId(), expectedRevision, lease.owner(), lease.epoch()).stream().findFirst();
    }

    Optional<StoredRun> finishLeased(TrustedAgentActor actor, AgentRunLease lease, long expectedRevision,
                                      AgentRunStatusEnum status, String resultJson, String errorCode) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = ?, revision = revision + 1, last_event_sequence = last_event_sequence + 1,
                    result = CAST(? AS jsonb), error_code = ?, finished_at = clock_timestamp(),
                    lease_owner = NULL, lease_expires_at = NULL, active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                  AND ((? = 'CANCELLED' AND cancel_requested_at IS NOT NULL)
                    OR (? <> 'CANCELLED' AND cancel_requested_at IS NULL))
                RETURNING *
                """, runMapper(), status.name(), resultJson, errorCode, actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), lease.runId(), expectedRevision, lease.owner(), lease.epoch(), status.name(),
                status.name()).stream().findFirst();
    }

    Optional<StoredRun> expireDeadline(TrustedAgentActor actor, String runId, long expectedRevision,
                                       String resultJson) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'TIMED_OUT', revision = revision + 1, last_event_sequence = last_event_sequence + 1,
                    result = CAST(? AS jsonb), error_code = 'TIANJI_RUN_TIMED_OUT', finished_at = clock_timestamp(),
                    lease_epoch = lease_epoch + 1, lease_owner = NULL, lease_expires_at = NULL,
                    active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status IN ('QUEUED', 'RUNNING', 'WAITING', 'SUSPENDED')
                  AND deadline_at <= clock_timestamp()
                RETURNING *
                """, runMapper(), resultJson, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    Optional<StoredRun> suspendExpiredLease(TrustedAgentActor actor, String runId, long expectedRevision) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'SUSPENDED', wait_reason = 'LEASE_EXPIRED', revision = revision + 1,
                    last_event_sequence = last_event_sequence + 1, lease_epoch = lease_epoch + 1,
                    lease_owner = NULL, lease_expires_at = NULL, active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = 'RUNNING'
                  AND (lease_expires_at IS NULL OR lease_expires_at <= clock_timestamp())
                  AND deadline_at > clock_timestamp()
                RETURNING *
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    Optional<StoredRun> suspendAuthorization(TrustedAgentActor actor, AgentRunLease lease, String reason) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'SUSPENDED', wait_reason = ?, revision = revision + 1,
                    last_event_sequence = last_event_sequence + 1, lease_epoch = lease_epoch + 1,
                    lease_owner = NULL, lease_expires_at = NULL, active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                  AND cancel_requested_at IS NULL
                RETURNING *
                """, runMapper(), reason, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), lease.runId(), lease.owner(), lease.epoch()).stream().findFirst();
    }

    Optional<StoredRun> suspendQueuedAuthorization(TrustedAgentActor actor, String runId, long expectedRevision) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'SUSPENDED', wait_reason = 'AUTHORIZATION_DENIED', revision = revision + 1,
                    last_event_sequence = last_event_sequence + 1, lease_epoch = lease_epoch + 1,
                    lease_owner = NULL, lease_expires_at = NULL, active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'QUEUED' AND revision = ? AND cancel_requested_at IS NULL
                  AND deadline_at > clock_timestamp()
                RETURNING *
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    List<ReconciliationCandidate> dueRuns(int limit) {
        return jdbc.query("""
                SELECT tenant_id, subject_type, subject_issuer, subject_id, run_id, revision,
                       (deadline_at <= clock_timestamp()) AS deadline_expired
                FROM tianji_agent.agent_run
                WHERE status IN ('QUEUED', 'RUNNING', 'WAITING', 'SUSPENDED')
                  AND (deadline_at <= clock_timestamp()
                    OR (status = 'RUNNING' AND (lease_expires_at IS NULL OR lease_expires_at <= clock_timestamp())))
                ORDER BY deadline_at, created_at, run_id LIMIT ?
                """, (rs, row) -> new ReconciliationCandidate(
                new TrustedAgentActor(rs.getLong("tenant_id"),
                        TrustedAgentActor.SubjectType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"), rs.getString("subject_issuer")),
                rs.getString("run_id"), rs.getLong("revision"), rs.getBoolean("deadline_expired")), limit);
    }

    List<PendingAgentRun> queuedRuns(int limit) {
        return jdbc.query("""
                SELECT tenant_id, subject_type, subject_issuer, subject_id, run_id
                FROM tianji_agent.agent_run
                WHERE status = 'QUEUED' AND cancel_requested_at IS NULL AND deadline_at > clock_timestamp()
                ORDER BY created_at, run_id LIMIT ?
                """, (rs, row) -> new PendingAgentRun(new TrustedAgentActor(rs.getLong("tenant_id"),
                TrustedAgentActor.SubjectType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"), rs.getString("subject_issuer")),
                new GetAgentRunQuery(rs.getString("run_id"))), limit);
    }

    List<GrantReadyAgentRun> grantReadyRuns(GrantReadyAgentRun.Cursor after, int limit) {
        return grantReadyRuns(after, limit, false);
    }

    List<GrantReadyAgentRun> grantReadyRuns(GrantReadyAgentRun.Cursor after, int limit, boolean includeService) {
        String sql = includeService ? """
                SELECT r.tenant_id, r.subject_type, r.subject_issuer, r.subject_id, r.run_id, r.created_at
                FROM tianji_agent.agent_run r
                WHERE r.status = 'QUEUED' AND r.subject_issuer <> 'LEGACY'
                  AND r.cancel_requested_at IS NULL AND r.deadline_at > clock_timestamp()
                  AND ((r.subject_type = 'USER' AND EXISTS (
                        SELECT 1 FROM tianji_agent.agent_run_execution_grant g
                        WHERE g.tenant_id=r.tenant_id AND g.run_id=r.run_id
                          AND g.grant_id IS NOT NULL AND g.ciphertext IS NOT NULL AND g.expires_at > clock_timestamp()))
                    OR (r.subject_type = 'SERVICE' AND EXISTS (
                        SELECT 1 FROM tianji_agent.agent_run_service_execution_grant g
                        JOIN tianji_agent.agent_process_dispatch p ON p.tenant_id=g.tenant_id AND p.run_id=g.run_id
                        WHERE g.tenant_id=r.tenant_id AND g.run_id=r.run_id AND g.subject_client_id=r.subject_id
                          AND p.subject_issuer=r.subject_issuer AND p.subject_id=r.subject_id AND p.state='OPEN'
                          AND g.grant_id IS NOT NULL AND g.ciphertext IS NOT NULL AND g.expires_at > clock_timestamp())))
                """ : """
                SELECT r.tenant_id, r.subject_type, r.subject_issuer, r.subject_id, r.run_id, r.created_at
                FROM tianji_agent.agent_run r
                JOIN tianji_agent.agent_run_execution_grant g ON g.tenant_id = r.tenant_id AND g.run_id = r.run_id
                WHERE r.status = 'QUEUED' AND r.subject_type = 'USER' AND r.subject_issuer <> 'LEGACY'
                  AND r.cancel_requested_at IS NULL AND r.deadline_at > clock_timestamp()
                  AND g.grant_id IS NOT NULL AND g.ciphertext IS NOT NULL AND g.expires_at > clock_timestamp()
                """;
        var mapper = (org.springframework.jdbc.core.RowMapper<GrantReadyAgentRun>) (rs, row) -> new GrantReadyAgentRun(
                new TrustedAgentActor(rs.getLong("tenant_id"), TrustedAgentActor.SubjectType.valueOf(rs.getString("subject_type")),
                        rs.getString("subject_id"), rs.getString("subject_issuer")), new GetAgentRunQuery(rs.getString("run_id")),
                rs.getTimestamp("created_at").toInstant());
        if (after == null) return jdbc.query(sql + " ORDER BY r.created_at, r.tenant_id, r.run_id LIMIT ?", mapper, limit);
        return jdbc.query(sql + " AND (r.created_at, r.tenant_id, r.run_id) > (?, ?, ?) ORDER BY r.created_at, r.tenant_id, r.run_id LIMIT ?",
                mapper, Timestamp.from(after.createdAt()), after.tenantId(), after.runId(), limit);
    }

    int insertCancelReceipt(TrustedAgentActor actor, String commandId, String hash, String runId, Instant now) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_run_command_receipt
                    (tenant_id, subject_type, subject_issuer, subject_id, command_id, operation, request_hash, run_id, created_at)
                VALUES (?, ?, ?, ?, ?, 'CANCEL', ?, ?, ?)
                ON CONFLICT (tenant_id, subject_type, subject_issuer, subject_id, operation, command_id) DO NOTHING
                """, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId, hash, runId,
                Timestamp.from(now));
    }

    Optional<CancelReceipt> cancelReceipt(TrustedAgentActor actor, String commandId) {
        return jdbc.query("""
                SELECT request_hash, result::text AS result FROM tianji_agent.agent_run_command_receipt
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND operation = 'CANCEL' AND command_id = ?
                """, (rs, row) -> new CancelReceipt(rs.getString("request_hash"), rs.getString("result")),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId).stream().findFirst();
    }

    void completeCancelReceipt(TrustedAgentActor actor, String commandId, String resultJson) {
        int updated = jdbc.update("""
                UPDATE tianji_agent.agent_run_command_receipt SET result = CAST(? AS jsonb)
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND operation = 'CANCEL' AND command_id = ?
                """, resultJson, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId);
        if (updated != 1) {
            throw new IllegalStateException("Agent cancellation receipt is missing");
        }
    }

    Optional<StoredRun> requestRunningCancellation(TrustedAgentActor actor, String runId, long expectedRevision) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET cancel_requested_at = clock_timestamp(), revision = revision + 1,
                    last_event_sequence = last_event_sequence + 1
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = 'RUNNING' AND cancel_requested_at IS NULL
                RETURNING *
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    Optional<StoredRun> cancelNotRunning(TrustedAgentActor actor, String runId, long expectedRevision,
                                         String resultJson) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = 'CANCELLED', cancel_requested_at = COALESCE(cancel_requested_at, clock_timestamp()),
                    revision = revision + 1, last_event_sequence = last_event_sequence + 1,
                    result = CAST(? AS jsonb), finished_at = clock_timestamp(),
                    lease_epoch = lease_epoch + 1, lease_owner = NULL, lease_expires_at = NULL,
                    active_model_calls = 0, active_tool_calls = 0
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status IN ('CREATED', 'QUEUED', 'WAITING', 'SUSPENDED')
                RETURNING *
                """, runMapper(), resultJson, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, expectedRevision).stream().findFirst();
    }

    int insertBudgetReservation(TrustedAgentActor actor, AgentRunLease lease, AgentBudgetRequest request, Instant now) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_budget_reservation
                    (tenant_id, run_id, reservation_id, lease_epoch, kind, tool_version, input_tokens, output_tokens,
                     estimated_cost, state, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RESERVED', ?)
                ON CONFLICT (tenant_id, run_id, reservation_id) DO NOTHING
                """, actor.tenantId(), lease.runId(), request.reservationId(), lease.epoch(), request.kind().name(),
                request.toolVersion(), request.inputTokens(), request.outputTokens(), request.estimatedCost(), Timestamp.from(now));
    }

    Optional<AgentBudgetReservation> budgetReservation(TrustedAgentActor actor, String runId,
                                                        String reservationId, boolean lock) {
        String sql = """
                SELECT b.* FROM tianji_agent.agent_budget_reservation b
                JOIN tianji_agent.agent_run r ON r.tenant_id = b.tenant_id AND r.run_id = b.run_id
                WHERE r.tenant_id = ? AND r.subject_type = ? AND r.subject_issuer = ? AND r.subject_id = ?
                  AND b.run_id = ? AND b.reservation_id = ?
                """ + (lock ? " FOR UPDATE OF b" : "");
        return jdbc.query(sql, (rs, row) -> new AgentBudgetReservation(rs.getString("reservation_id"),
                rs.getLong("lease_epoch"), AgentBudgetRequest.Kind.valueOf(rs.getString("kind")),
                rs.getString("tool_version"), rs.getLong("input_tokens"), rs.getLong("output_tokens"), rs.getBigDecimal("estimated_cost"),
                AgentBudgetReservation.State.valueOf(rs.getString("state")),
                rs.getObject("actual_input_tokens", Long.class), rs.getObject("actual_output_tokens", Long.class),
                rs.getBigDecimal("actual_cost"), false), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                runId, reservationId).stream().findFirst();
    }

    boolean validLease(TrustedAgentActor actor, AgentRunLease lease) {
        Boolean valid = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM tianji_agent.agent_run
                  WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                    AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                    AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                    AND cancel_requested_at IS NULL)
                """, Boolean.class, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                lease.runId(), lease.owner(), lease.epoch());
        return Boolean.TRUE.equals(valid);
    }

    boolean lockLeaseForSettlement(TrustedAgentActor actor, AgentRunLease lease) {
        return !jdbc.query("""
                SELECT run_id FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp()
                FOR UPDATE
                """, (rs, row) -> rs.getString("run_id"), actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), lease.runId(), lease.owner(), lease.epoch()).isEmpty();
    }

    int reserveBudget(TrustedAgentActor actor, AgentRunLease lease, AgentBudgetRequest request,
                      ExecutionPolicyDTO policy) {
        BigDecimal cost = request.estimatedCost() == null ? BigDecimal.ZERO : request.estimatedCost();
        BigDecimal maxCost = policy.maxEstimatedCost() == null ? BigDecimal.ZERO : policy.maxEstimatedCost();
        return jdbc.update("""
                UPDATE tianji_agent.agent_run
                SET model_calls_reserved = model_calls_reserved + CASE WHEN ? = 'MODEL' THEN 1 ELSE 0 END,
                    tool_calls_reserved = tool_calls_reserved + CASE WHEN ? = 'TOOL' THEN 1 ELSE 0 END,
                    active_model_calls = active_model_calls + CASE WHEN ? = 'MODEL' THEN 1 ELSE 0 END,
                    active_tool_calls = active_tool_calls + CASE WHEN ? = 'TOOL' THEN 1 ELSE 0 END,
                    input_tokens_reserved = input_tokens_reserved + ?, output_tokens_reserved = output_tokens_reserved + ?,
                    estimated_cost_reserved = estimated_cost_reserved + ?
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp() AND deadline_at > clock_timestamp()
                  AND cancel_requested_at IS NULL
                  AND (? <> 'MODEL' OR model_calls_reserved < ?)
                  AND (? <> 'TOOL' OR tool_calls_reserved < ?)
                  AND active_model_calls::bigint + active_tool_calls < ?
                  AND input_tokens_reserved <= ? - ? AND output_tokens_reserved <= ? - ?
                  AND (? = false OR estimated_cost_reserved <= ? - ?)
                """, request.kind().name(), request.kind().name(), request.kind().name(), request.kind().name(),
                request.inputTokens(), request.outputTokens(), cost,
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), lease.runId(), lease.owner(), lease.epoch(),
                request.kind().name(), policy.maxModelCalls(), request.kind().name(), policy.maxToolCalls(),
                policy.maxParallelism(),
                policy.maxInputTokens(), request.inputTokens(), policy.maxOutputTokens(), request.outputTokens(),
                policy.maxEstimatedCost() != null, maxCost, cost);
    }

    BudgetCounters budgetCounters(TrustedAgentActor actor, String runId) {
        return jdbc.queryForObject("""
                SELECT model_calls_reserved, tool_calls_reserved, active_model_calls, active_tool_calls,
                       input_tokens_reserved, output_tokens_reserved, estimated_cost_reserved
                FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                """, (rs, row) -> new BudgetCounters(rs.getInt("model_calls_reserved"), rs.getInt("tool_calls_reserved"),
                rs.getInt("active_model_calls"), rs.getInt("active_tool_calls"), rs.getLong("input_tokens_reserved"),
                rs.getLong("output_tokens_reserved"), rs.getBigDecimal("estimated_cost_reserved")),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), runId);
    }

    int adjustBudget(TrustedAgentActor actor, AgentRunLease lease, AgentBudgetRequest.Kind kind,
                     long inputDelta, long outputDelta, BigDecimal costDelta) {
        return jdbc.update("""
                UPDATE tianji_agent.agent_run
                SET input_tokens_reserved = input_tokens_reserved + ?, output_tokens_reserved = output_tokens_reserved + ?,
                    estimated_cost_reserved = estimated_cost_reserved + ?,
                    active_model_calls = active_model_calls - CASE WHEN ? = 'MODEL' THEN 1 ELSE 0 END,
                    active_tool_calls = active_tool_calls - CASE WHEN ? = 'TOOL' THEN 1 ELSE 0 END
                WHERE tenant_id = ? AND subject_type = ? AND subject_issuer = ? AND subject_id = ? AND run_id = ?
                  AND status = 'RUNNING' AND lease_owner = ? AND lease_epoch = ?
                  AND lease_expires_at > clock_timestamp()
                  AND input_tokens_reserved + ? >= 0 AND output_tokens_reserved + ? >= 0
                  AND estimated_cost_reserved + ? >= 0
                  AND (? <> 'MODEL' OR active_model_calls > 0) AND (? <> 'TOOL' OR active_tool_calls > 0)
                """, inputDelta, outputDelta, costDelta, kind.name(), kind.name(), actor.tenantId(),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), lease.runId(), lease.owner(), lease.epoch(),
                inputDelta, outputDelta, costDelta, kind.name(), kind.name());
    }

    void settleBudget(long tenantId, String runId, String reservationId, AgentBudgetReservation.State state,
                      AgentBudgetSettlement actual, Instant now) {
        int updated = jdbc.update("""
                UPDATE tianji_agent.agent_budget_reservation
                SET state = ?, actual_input_tokens = ?, actual_output_tokens = ?, actual_cost = ?, settled_at = ?
                WHERE tenant_id = ? AND run_id = ? AND reservation_id = ? AND state = 'RESERVED'
                """, state.name(), actual.inputTokens(), actual.outputTokens(), actual.actualCost(), Timestamp.from(now),
                tenantId, runId, reservationId);
        if (updated != 1) {
            throw new IllegalStateException("Agent budget reservation was not available for settlement");
        }
    }

    void markOutstandingBudgetUnknown(long tenantId, String runId) {
        jdbc.update("""
                UPDATE tianji_agent.agent_budget_reservation
                SET state = 'UNKNOWN', settled_at = clock_timestamp()
                WHERE tenant_id = ? AND run_id = ? AND state = 'RESERVED'
                """, tenantId, runId);
    }

    void insertEvent(long tenantId, AgentEventDTO event) {
        jdbc.update("""
                INSERT INTO tianji_agent.agent_event
                    (tenant_id, run_id, sequence, event_id, event_type, schema_version, author, branch,
                     tool_call_id, correlation_id, payload, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, tenantId, event.runId(), event.sequence(), event.eventId(), event.type(),
                event.payloadSchemaVersion(), event.author(), event.branch(), event.toolCallId(), event.correlationId(),
                json.write(event.payload(), AgentJsonCodec.MAX_EVENT_BYTES), Timestamp.from(event.occurredAt()));
    }

    List<AgentEventDTO> events(long tenantId, String runId, long afterSequence, int limit) {
        return jdbc.query("""
                SELECT event_id, run_id, sequence, event_type, schema_version, author, branch, tool_call_id,
                       correlation_id, payload::text AS payload, occurred_at
                FROM tianji_agent.agent_event
                WHERE tenant_id = ? AND run_id = ? AND sequence > ?
                ORDER BY sequence ASC LIMIT ?
                """, (rs, row) -> new AgentEventDTO(rs.getString("event_id"), rs.getString("run_id"),
                        rs.getLong("sequence"), rs.getString("event_type"), rs.getString("author"),
                        rs.getString("branch"), rs.getString("tool_call_id"),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getString("correlation_id"),
                        rs.getString("schema_version"),
                        json.readObject(rs.getString("payload"))), tenantId, runId, afterSequence, limit);
    }

    private RowMapper<StoredRun> runMapper() {
        return (rs, row) -> {
            Timestamp leaseExpires = rs.getTimestamp("lease_expires_at");
            Timestamp cancelRequested = rs.getTimestamp("cancel_requested_at");
            return new StoredRun(mapRun(rs), rs.getString("request_hash"), rs.getString("result"),
                    rs.getString("correlation_id"), rs.getString("execution_snapshot"), rs.getString("lease_owner"),
                    rs.getLong("lease_epoch"), leaseExpires == null ? null : leaseExpires.toInstant(),
                    cancelRequested == null ? null : cancelRequested.toInstant());
        };
    }

    private static AgentRunDTO mapRun(ResultSet rs) throws SQLException {
        Timestamp finished = rs.getTimestamp("finished_at");
        return new AgentRunDTO(rs.getString("run_id"), rs.getString("definition_key"),
                rs.getLong("definition_version"), rs.getString("session_id"),
                AgentRunStatusEnum.valueOf(rs.getString("status")), rs.getString("wait_reason"),
                rs.getTimestamp("cancel_requested_at") != null, rs.getInt("attempt"), rs.getLong("revision"),
                rs.getLong("last_event_sequence"), rs.getTimestamp("deadline_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), finished == null ? null : finished.toInstant());
    }

    record DefinitionRow(String hash, top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO specification) { }
    record StoredRun(AgentRunDTO run, String requestHash, String resultJson, String correlationId, String snapshotJson,
                     String leaseOwner, long leaseEpoch, Instant leaseExpiresAt, Instant cancelRequestedAt) { }
    record CancelReceipt(String requestHash, String resultJson) { }
    record ReconciliationCandidate(TrustedAgentActor actor, String runId, long revision, boolean deadlineExpired) { }
    record BudgetCounters(int modelCalls, int toolCalls, int activeModels, int activeTools,
                          long inputTokens, long outputTokens, BigDecimal cost) { }
}
