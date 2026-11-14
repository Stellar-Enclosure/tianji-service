package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceCorrelationPageDTO;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Local transaction participant for scoped idempotency receipts. */
public final class ProcessCommandReceiptStore {
    public enum Kind { START, DECISION, AGENT_RESULT, TASK_CLAIM, TASK_UNCLAIM, TASK_TRANSFER, TASK_COMPLETE, CASE_START, CASE_PLAN_ITEM, CASE_COMPLETION_SERVICE, PROCESS_CANCEL, PROCESS_WORKER_RECOVER, WORKER_REPORT_V2 }

    private static final String CORRELATION_SCOPE = " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
            + " AND command_type='START' AND state='APPLIED' AND resource_id IS NOT NULL"
            + " AND correlation_id COLLATE \"C\"=? AND created_at>=? AND created_at<?";
    private final JdbcTemplate jdbc;
    private final String schema;
    private final String table;

    public ProcessCommandReceiptStore(JdbcTemplate jdbc, String schema) {
        if (jdbc == null || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("Process receipt storage is invalid");
        }
        this.jdbc = jdbc;
        this.schema = schema;
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

    public Optional<Receipt> findWorkerReport(ProcessActor actor, String commandId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())
                || actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())
                || commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) {
            throw new IllegalArgumentException("Invalid Worker report lookup");
        }
        var rows = jdbc.query("SELECT request_sha256,release_key,correlation_id,state,resource_id,created_at FROM " + table
                        + " WHERE tenant_id=? AND subject_type='SERVICE' AND subject_issuer=? AND subject_id=?"
                        + " AND command_type='WORKER_REPORT_V2' AND command_id=?",
                (rs, row) -> new Receipt(rs.getString("request_sha256"), rs.getString("release_key"),
                        rs.getString("correlation_id"), rs.getString("state"), rs.getString("resource_id"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(), null),
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), commandId);
        if (rows.size() > 1) throw new IllegalStateException("Process Worker report receipt is ambiguous");
        return rows.stream().findFirst();
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

    Optional<Receipt> findOwnedStart(ProcessActor actor, String commandId) {
        return jdbc.query("SELECT request_sha256,release_key,correlation_id,state,resource_id,created_at FROM " + table
                        + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
                        + " AND command_type='START' AND command_id=?",
                (rs, number) -> new Receipt(rs.getString("request_sha256"), rs.getString("release_key"), rs.getString("correlation_id"),
                        rs.getString("state"), rs.getString("resource_id"), rs.getObject("created_at", OffsetDateTime.class).toInstant(), null),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId).stream().findFirst();
    }

    boolean hasAutomationRelease(long tenantId, String releaseKey) {
        String extensions = schema + ".tianji_process_automation_release";
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_catalog.has_table_privilege(pg_catalog.to_regclass(?)::oid,'SELECT')", Boolean.class, extensions))
                && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + extensions + " s"
                        + " WHERE s.tenant_id=? AND s.release_key=?)", Boolean.class, tenantId, releaseKey));
    }

    boolean hasAutomationRootInput(long tenantId, String releaseKey) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM %s.tianji_process_release_definition
                  WHERE tenant_id=? AND release_key=? AND model_type='BPMN'
                    AND input_schema=jsonb_build_object('automationBindingRef','STRING'))
                """.formatted(schema), Boolean.class, tenantId, releaseKey));
    }

    Optional<StartReceiptObservation> findAppliedStart(ProcessActor actor, String commandId, String requestSha256) {
        var rows = jdbc.query("SELECT resource_id, created_at FROM " + table
                        + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
                        + " AND command_type='START' AND command_id=? AND request_sha256 COLLATE \"C\"=? AND state='APPLIED' LIMIT 2",
                (rs, row) -> new StartReceiptObservation(rs.getString("resource_id"),
                        rs.getObject("created_at", OffsetDateTime.class)),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId, requestSha256);
        if (rows.size() > 1) throw new IllegalStateException("Process START receipt evidence is unavailable");
        return rows.stream().findFirst();
    }

    Instant beginReceiptObservation() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(jdbc.getDataSource())
                || Thread.currentThread().isInterrupted()) throw correlationUnavailable();
        // Keep routing on the owning primary while making the physical transaction SELECT-only.
        jdbc.execute("SET TRANSACTION READ ONLY");
        jdbc.execute("SET LOCAL statement_timeout = '2s'");
        var observed = jdbc.queryForObject("""
                SELECT pg_catalog.clock_timestamp()
                FROM pg_catalog.pg_namespace ns
                JOIN pg_catalog.pg_class rel ON rel.relnamespace=ns.oid
                WHERE ns.nspname=? AND rel.relname='tianji_process_command_receipt'
                  AND pg_catalog.current_schema()=ns.nspname
                  AND rel.relkind='r' AND rel.relpersistence='p' AND NOT rel.relispartition
                  AND NOT rel.relrowsecurity AND NOT rel.relforcerowsecurity
                  AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_inherits
                                  WHERE inhrelid=rel.oid OR inhparent=rel.oid)
                  AND pg_catalog.has_schema_privilege(ns.oid,'USAGE')
                  AND pg_catalog.has_table_privilege(rel.oid,'SELECT')
                  AND NOT pg_catalog.pg_is_in_recovery()
                  AND pg_catalog.current_setting('server_encoding')='UTF8'
                  AND pg_catalog.current_setting('session_replication_role')='origin'
                  AND pg_catalog.current_setting('transaction_read_only')='on'
                  AND pg_catalog.current_setting('transaction_isolation')='repeatable read'
                """, OffsetDateTime.class, schema);
        if (observed == null) throw correlationUnavailable();
        return observed.toInstant();
    }

    boolean hasCorrelationAnchor(ProcessActor actor, String correlationId, Instant from, Instant until,
                                 Instant submittedAt, String instanceId, boolean automationAware) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + correlationScope(automationAware)
                        + " AND created_at=? AND resource_id COLLATE \"C\"=?)", Boolean.class,
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                correlationId, utc(from), utc(until), utc(submittedAt), instanceId));
    }

    List<CorrelationReceipt> listCorrelationReceipts(ProcessActor actor, String correlationId, Instant from, Instant until,
                                                     Instant beforeSubmittedAt, String beforeInstanceId, boolean automationAware) {
        String sql = "SELECT resource_id, release_key, created_at FROM " + correlationScope(automationAware);
        String order = " ORDER BY created_at DESC, resource_id COLLATE \"C\" DESC LIMIT "
                + (ProcessInstanceCorrelationPageDTO.PAGE_SIZE + 1);
        var fromUtc = utc(from);
        var untilUtc = utc(until);
        if (beforeSubmittedAt == null) {
            return jdbc.query(sql + order, ProcessCommandReceiptStore::readCorrelationReceipt,
                    actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(),
                    correlationId, fromUtc, untilUtc);
        }
        return jdbc.query(sql + " AND (created_at,resource_id COLLATE \"C\") < (?,?)" + order,
                ProcessCommandReceiptStore::readCorrelationReceipt, actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), correlationId, fromUtc, untilUtc, utc(beforeSubmittedAt), beforeInstanceId);
    }

    private String correlationScope(boolean automationAware) {
        String extensions = schema + ".tianji_process_automation_release";
        if (!automationAware || !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_catalog.has_table_privilege(pg_catalog.to_regclass(?)::oid,'SELECT')", Boolean.class, extensions))) {
            return table + CORRELATION_SCOPE;
        }
        return table + " stored" + CORRELATION_SCOPE + " AND NOT EXISTS (SELECT 1 FROM " + extensions
                + " a WHERE a.tenant_id=stored.tenant_id AND a.release_key=stored.release_key)";
    }

    private static CorrelationReceipt readCorrelationReceipt(java.sql.ResultSet rs, int number) throws java.sql.SQLException {
        var createdAt = rs.getObject("created_at", OffsetDateTime.class);
        if (createdAt == null) throw correlationUnavailable();
        return new CorrelationReceipt(rs.getString("resource_id"), rs.getString("release_key"), createdAt.toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static IllegalStateException correlationUnavailable() {
        return new IllegalStateException("Process correlation evidence is unavailable");
    }

    public TaskOrigin readStartByInstance(ProcessActor actor, String instanceId) {
        return exactStartByInstance(actor, instanceId, false);
    }

    public TaskOrigin lockStartByInstance(ProcessActor actor, String instanceId) {
        return exactStartByInstance(actor, instanceId, true);
    }

    private TaskOrigin exactStartByInstance(ProcessActor actor, String instanceId, boolean lock) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint()) || instanceId == null || instanceId.isBlank()
                || instanceId.length() > 255 || !instanceId.equals(instanceId.trim())) {
            throw new IllegalArgumentException("Process start source is invalid");
        }
        List<TaskOrigin> rows = jdbc.query("SELECT subject_type,subject_issuer,subject_id,command_id,request_sha256,release_key,correlation_id,resource_id,created_at FROM "
                        + table + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? AND command_type='START' "
                        + "AND state='APPLIED' AND resource_id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, number) -> new TaskOrigin(new ProcessActor(actor.tenantId(), ProcessActor.SubjectType.valueOf(rs.getString("subject_type")),
                        rs.getString("subject_id"), rs.getString("subject_issuer")), rs.getString("command_id"),
                        rs.getString("request_sha256"), rs.getString("release_key"), rs.getString("correlation_id"),
                        rs.getString("resource_id"), rs.getTimestamp("created_at").toInstant()), actor.tenantId(),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), instanceId);
        if (rows.size() != 1 || !rows.getFirst().requester().equals(actor)
                || !rows.getFirst().instanceId().equals(instanceId)) throw new ProcessResourceNotFoundException();
        return rows.getFirst();
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

    record CorrelationReceipt(String instanceId, String releaseVersion, Instant submittedAt) { }

    record StartReceiptObservation(String instanceId, OffsetDateTime recordedAt) { }

    public record Receipt(String requestHash, String releaseKey, String correlationId, String state,
                          String resourceId, Instant createdAt, String decisionResult) { }
    public record TaskOrigin(ProcessActor requester, String commandId, String requestHash, String releaseKey, String correlationId, String instanceId, Instant createdAt) { }
}
