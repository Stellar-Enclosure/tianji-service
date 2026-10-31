package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Local transaction participant for scoped idempotency receipts. */
public final class ProcessCommandReceiptStore {
    public enum Kind { START, DECISION, AGENT_RESULT, TASK_CLAIM, TASK_UNCLAIM, TASK_TRANSFER, TASK_COMPLETE }

    private final JdbcTemplate jdbc;
    private final String table;

    public ProcessCommandReceiptStore(JdbcTemplate jdbc, String schema) {
        if (jdbc == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Process receipt storage is invalid");
        }
        this.jdbc = jdbc;
        this.table = '"' + schema + "\".tianji_process_command_receipt";
    }

    public boolean insert(ProcessActor actor, Kind kind, String commandId, String requestHash,
                          String releaseKey, String correlationId) {
        return jdbc.update("INSERT INTO " + table + " "
                        + "(tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id, request_sha256, release_key, "
                        + "correlation_id, state, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', clock_timestamp()) "
                        + "ON CONFLICT DO NOTHING", actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                kind.name(), commandId, requestHash, releaseKey, correlationId) == 1;
    }

    public Receipt lock(ProcessActor actor, Kind kind, String commandId) {
        return lockIfPresent(actor, kind, commandId).orElseThrow(ProcessResourceNotFoundException::new);
    }

    public Optional<Receipt> lockIfPresent(ProcessActor actor, Kind kind, String commandId) {
        String result = kind == Kind.DECISION ? "decision_result::text" : "NULL::text";
        List<Receipt> rows = jdbc.query("SELECT request_sha256, release_key, correlation_id, state, resource_id, created_at, "
                        + result + " AS decision_result FROM " + table
                        + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND command_type=? AND command_id=? FOR UPDATE",
                (rs, row) -> new Receipt(rs.getString("request_sha256"), rs.getString("release_key"), rs.getString("correlation_id"),
                        rs.getString("state"), rs.getString("resource_id"), rs.getTimestamp("created_at").toInstant(),
                        rs.getString("decision_result")), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), kind.name(), commandId);
        if (rows.size() > 1) throw new IllegalStateException("Process command receipt is ambiguous"); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public Receipt startByInstance(ProcessActor actor, String instanceId) {
        List<Receipt> rows = jdbc.query("SELECT request_sha256, release_key, correlation_id, state, resource_id, created_at FROM "
                        + table + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND command_type='START' "
                        + "AND state='APPLIED' AND resource_id=?", (rs, row) -> new Receipt(rs.getString("request_sha256"),
                        rs.getString("release_key"), rs.getString("correlation_id"), rs.getString("state"),
                        rs.getString("resource_id"), rs.getTimestamp("created_at").toInstant(), null), actor.tenantId(),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), instanceId);
        return one(rows);
    }

    /** A task approver need not be the starter; origin remains the exact authenticated tenant/issuer namespace. */
    public TaskOrigin taskOrigin(ProcessActor actor, String instanceId) {
        if (actor == null || actor.subjectType() != ProcessActor.SubjectType.USER || "LEGACY".equals(actor.issuerFingerprint())
                || instanceId == null || instanceId.isBlank() || instanceId.length() > 255 || !instanceId.equals(instanceId.trim())) throw new IllegalArgumentException("Task source is invalid");
        List<TaskOrigin> rows = jdbc.query("SELECT subject_type, subject_id, command_id, request_sha256, release_key, correlation_id, created_at FROM "
                        + table + " WHERE tenant_id=? AND subject_issuer=? AND command_type='START' AND state='APPLIED' AND resource_id=?",
                (rs, number) -> new TaskOrigin(new ProcessActor(actor.tenantId(), ProcessActor.SubjectType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"), actor.issuerFingerprint()),
                        rs.getString("command_id"), rs.getString("request_sha256"), rs.getString("release_key"), rs.getString("correlation_id"), instanceId, rs.getTimestamp("created_at").toInstant()),
                actor.tenantId(), actor.issuerFingerprint(), instanceId);
        if (rows.size() != 1) throw new ProcessResourceNotFoundException(); return rows.getFirst();
    }

    public void apply(ProcessActor actor, Kind kind, String commandId, String resourceId, String decisionResult) {
        if (kind == null || (kind == Kind.DECISION) != (decisionResult != null)) throw new IllegalArgumentException("Process receipt result is invalid");
        String resultAssignment = kind == Kind.DECISION ? ", decision_result=CAST(? AS jsonb)" : "";
        Object[] parameters = kind == Kind.DECISION
                ? new Object[] {resourceId, decisionResult, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), kind.name(), commandId}
                : new Object[] {resourceId, actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), kind.name(), commandId};
        if (jdbc.update("UPDATE " + table + " SET state='APPLIED', resource_id=?" + resultAssignment
                + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND command_type=? AND command_id=? AND state='PENDING'",
                parameters) != 1) throw new IllegalStateException("Process command receipt could not be committed");
    }

    private static Receipt one(List<Receipt> rows) {
        if (rows.size() != 1) throw new ProcessResourceNotFoundException();
        return rows.getFirst();
    }

    public record Receipt(String requestHash, String releaseKey, String correlationId, String state,
                          String resourceId, Instant createdAt, String decisionResult) { }
    public record TaskOrigin(ProcessActor requester, String commandId, String requestHash, String releaseKey, String correlationId, String instanceId, Instant createdAt) { }
}
