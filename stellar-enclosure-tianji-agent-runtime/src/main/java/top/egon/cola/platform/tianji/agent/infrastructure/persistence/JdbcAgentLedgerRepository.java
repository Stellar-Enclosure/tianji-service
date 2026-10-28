package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.agent.AgentEventDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunDTO;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

final class JdbcAgentLedgerRepository {
    private final JdbcTemplate jdbc;
    private final AgentJsonCodec json;

    JdbcAgentLedgerRepository(JdbcTemplate jdbc, AgentJsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
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
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ? AND idempotency_key = ?
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.subjectId(), key)
                .stream().findFirst();
    }

    Optional<StoredRun> run(TrustedAgentActor actor, String runId) {
        return jdbc.query("""
                SELECT * FROM tianji_agent.agent_run
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ? AND run_id = ?
                """, runMapper(), actor.tenantId(), actor.subjectType().name(), actor.subjectId(), runId)
                .stream().findFirst();
    }

    int insertRun(TrustedAgentActor actor, String runId, String definitionKey, long definitionVersion,
                  String idempotencyKey, String requestHash, String sessionId, String correlationId,
                  Instant deadline, String snapshot, Instant now) {
        return jdbc.update("""
                INSERT INTO tianji_agent.agent_run
                    (tenant_id, run_id, definition_key, definition_version, subject_type, subject_id,
                     idempotency_key, request_hash, session_id, correlation_id, status, revision,
                     last_event_sequence, deadline_at, execution_snapshot, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'QUEUED', 0, 1, ?, CAST(? AS jsonb), ?)
                ON CONFLICT (tenant_id, subject_type, subject_id, idempotency_key) DO NOTHING
                """, actor.tenantId(), runId, definitionKey, definitionVersion, actor.subjectType().name(),
                actor.subjectId(), idempotencyKey, requestHash, sessionId, correlationId,
                Timestamp.from(deadline), snapshot, Timestamp.from(now));
    }

    Optional<StoredRun> transition(TrustedAgentActor actor, String runId, long expectedRevision,
                                   AgentRunStatusEnum from, AgentRunStatusEnum to, String waitReason,
                                   String resultJson, String errorCode, Instant finishedAt) {
        return jdbc.query("""
                UPDATE tianji_agent.agent_run
                SET status = ?, revision = revision + 1, last_event_sequence = last_event_sequence + 1,
                    attempt = attempt + CASE WHEN ? = 'RUNNING' THEN 1 ELSE 0 END,
                    wait_reason = ?, result = CAST(? AS jsonb), error_code = ?, finished_at = ?
                WHERE tenant_id = ? AND subject_type = ? AND subject_id = ? AND run_id = ?
                  AND revision = ? AND status = ?
                RETURNING *
                """, runMapper(), to.name(), to.name(), waitReason, resultJson, errorCode,
                finishedAt == null ? null : Timestamp.from(finishedAt), actor.tenantId(),
                actor.subjectType().name(), actor.subjectId(), runId, expectedRevision, from.name())
                .stream().findFirst();
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
        return (rs, row) -> new StoredRun(mapRun(rs), rs.getString("request_hash"),
                rs.getString("result"), rs.getString("correlation_id"));
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
    record StoredRun(AgentRunDTO run, String requestHash, String resultJson, String correlationId) { }
}
