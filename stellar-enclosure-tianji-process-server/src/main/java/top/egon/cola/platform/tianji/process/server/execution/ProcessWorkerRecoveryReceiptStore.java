package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerCommand;
import top.egon.cola.platform.tianji.contract.orchestration.RecoverProcessWorkerResult;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Receipt;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable Process Worker recovery audit in the caller's owned transaction. */
public final class ProcessWorkerRecoveryReceiptStore {
    private static final String KIND = "PROCESS_WORKER_RECOVER";
    private static final String START = "START";
    private static final String BPMN = "BPMN";
    private static final String POLICY = "READ_ONLY_EXHAUSTED_RETRY_V1";
    private static final String AUDIT_COLUMNS = "tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id, "
            + "request_sha256, process_instance_id, start_command_type, start_command_id, start_request_sha256, release_key, "
            + "model_type, definition_key, engine_definition_id, definition_sha256, business_key, correlation_id, job_id, occurrence_id, "
            + "execution_id, element_id, deadletter_revision, expected_version, evidence_sha256, admission_policy, failure_code, "
            + "failure_decided_at, reason, result_status, retries, decided_at";
    private static final String AUTHORITY_COLUMNS = "b.state AS base_state, b.resource_id AS base_resource_id,"
            + " b.request_sha256 AS base_request_sha256, b.release_key AS base_release_key, b.correlation_id AS base_correlation_id,"
            + " b.created_at AS base_created_at, b.decision_result IS NULL AS base_no_decision,"
            + " s.tenant_id AS start_tenant_id, s.state AS start_state, s.subject_type AS start_subject_type,"
            + " s.subject_issuer AS start_subject_issuer, s.subject_id AS start_subject_id,"
            + " s.command_id AS bound_start_command_id, s.request_sha256 AS bound_start_request_sha256,"
            + " s.resource_id AS start_resource_id, s.release_key AS start_release_key, s.correlation_id AS start_correlation_id,"
            + " s.created_at AS start_created_at, d.engine_definition_id AS bound_engine_definition_id,"
            + " d.model_sha256 AS bound_definition_sha256";

    private final DataSource source;
    private final String schema;
    private final String audit;
    private final String commands;
    private final String definitions;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean verified = new AtomicBoolean();

    public ProcessWorkerRecoveryReceiptStore(DataSource source, String schema) {
        this.source = Objects.requireNonNull(source, "source");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.schema = schema;
        String prefix = '"' + schema + "\".";
        audit = prefix + "tianji_process_worker_recovery_receipt";
        commands = prefix + "tianji_process_command_receipt";
        definitions = prefix + "tianji_process_release_definition";
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
    }

    public boolean usesDataSource(DataSource candidate) {
        return source == candidate;
    }

    public void verifyStorage() {
        verified.set(false);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        new ProcessApiSchemaVerifier(source, schema).verifyWorkerRecovery();
        verified.set(true);
    }

    /** The caller has already authorized and locked the current exact START and command. */
    public RecoverProcessWorkerResult find(ProcessActor actor, TaskOrigin start, Receipt receipt,
                                           RecoverProcessWorkerCommand command, String requestHash) {
        requireBoundTransaction();
        if (!validStart(start) || !start.requester().equals(actor) || command == null
                || !start.instanceId().equals(command.instanceId()) || !hash(requestHash)
                || !matchesReceipt(receipt, "APPLIED", command.jobId(), requestHash, start)) throw unavailable();
        String sql = "SELECT a." + AUDIT_COLUMNS.replace(", ", ", a.") + ", " + AUTHORITY_COLUMNS
                + " FROM " + audit + " a JOIN " + commands + " b ON b.tenant_id=a.tenant_id"
                + " AND b.subject_type=a.subject_type AND b.subject_issuer=a.subject_issuer AND b.subject_id=a.subject_id"
                + " AND b.command_type=a.command_type AND b.command_id=a.command_id AND b.state='APPLIED'"
                + " JOIN " + commands + " s ON s.tenant_id=a.tenant_id AND s.subject_type=a.subject_type"
                + " AND s.subject_issuer=a.subject_issuer AND s.subject_id=a.subject_id"
                + " AND s.command_type=a.start_command_type AND s.command_id=a.start_command_id"
                + " AND s.request_sha256=a.start_request_sha256 AND s.state='APPLIED'"
                + " JOIN " + definitions + " d ON d.tenant_id=a.tenant_id AND d.release_key=a.release_key"
                + " AND d.model_type=a.model_type AND d.definition_key=a.definition_key"
                + " WHERE a.tenant_id=? AND a.subject_type=? AND a.subject_issuer=? AND a.subject_id=?"
                + " AND a.command_type='PROCESS_WORKER_RECOVER' AND a.command_id=? LIMIT 2";
        List<AuditRead> rows = jdbc.query(sql, (rs, row) -> readAudit(rs), actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), command.commandId());
        if (rows.size() != 1) throw unavailable();
        var read = rows.getFirst();
        var entry = read.entry();
        if (!read.authority().base().equals(receipt) || !read.authority().matches(entry)
                || !entry.actor().equals(actor) || !entry.start().equals(start)
                || !entry.command().equals(command) || !entry.requestHash().equals(requestHash)) throw unavailable();
        return entry.result();
    }

    public boolean hasAccepted(TaskOrigin start, String jobId) {
        requireBoundTransaction();
        if (!validStart(start) || !safeText(jobId, 255)) throw unavailable();
        ProcessActor actor = start.requester();
        List<String> rows = jdbc.query("SELECT job_id FROM " + audit
                        + " WHERE job_id=? AND tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
                        + " AND process_instance_id=? LIMIT 2",
                (rs, row) -> rs.getString("job_id"), jobId, actor.tenantId(), actor.subjectType().name(),
                actor.issuerFingerprint(), actor.subjectId(), start.instanceId());
        if (rows.size() > 1 || rows.size() == 1 && !jobId.equals(rows.getFirst())) throw unavailable();
        return !rows.isEmpty();
    }

    public Instant decisionTime() {
        requireBoundTransaction();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null || !microseconds(value.toInstant())) throw unavailable();
        return value.toInstant();
    }

    /** Returns false only for the source-global job uniqueness collision. */
    public boolean insert(Entry entry) {
        if (entry == null || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw unavailable();
        requireBoundTransaction();
        var actor = entry.actor();
        var start = entry.start();
        var command = entry.command();
        var result = entry.result();
        List<Authority> rows = jdbc.query("SELECT " + AUTHORITY_COLUMNS
                        + " FROM " + commands + " b JOIN " + commands + " s ON s.tenant_id=b.tenant_id"
                        + " AND s.subject_type=b.subject_type AND s.subject_issuer=b.subject_issuer AND s.subject_id=b.subject_id"
                        + " AND s.command_type='START' AND s.command_id=? AND s.request_sha256=? AND s.state='APPLIED'"
                        + " JOIN " + definitions + " d ON d.tenant_id=b.tenant_id AND d.release_key=? AND d.model_type='BPMN'"
                        + " AND d.definition_key=? WHERE b.tenant_id=? AND b.subject_type=? AND b.subject_issuer=? AND b.subject_id=?"
                        + " AND b.command_type='PROCESS_WORKER_RECOVER' AND b.command_id=? AND b.state='PENDING' LIMIT 2",
                (rs, row) -> authority(rs), start.commandId(), start.requestHash(), start.releaseKey(), entry.definitionKey(),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), command.commandId());
        if (rows.size() != 1 || !rows.getFirst().matches(entry)
                || !matchesReceipt(rows.getFirst().base(), "PENDING", null, entry.requestHash(), start)) throw unavailable();

        int inserted = jdbc.update("INSERT INTO " + audit + " (" + AUDIT_COLUMNS + ") VALUES (" + "?,".repeat(31)
                        + "?) ON CONFLICT (job_id) DO NOTHING",
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), KIND, command.commandId(),
                entry.requestHash(), command.instanceId(), START, start.commandId(), start.requestHash(), start.releaseKey(), BPMN,
                entry.definitionKey(), entry.engineDefinitionId(), entry.definitionSha256(), entry.businessKey(), start.correlationId(),
                command.jobId(), entry.occurrenceId(), entry.executionId(), entry.elementId(), entry.deadletterRevision(),
                command.expectedVersion(), entry.evidenceSha256(), entry.admissionPolicy(), entry.failureCode(),
                Timestamp.from(entry.failureDecidedAt()), command.reason(), result.status().name(), result.retries(),
                Timestamp.from(result.decidedAt()));
        if (inserted == 0) return false;
        if (inserted != 1) throw unavailable();
        return true;
    }

    private void requireBoundTransaction() {
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()) {
            throw unavailable();
        }
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8'"
                + " AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }

    private static AuditRead readAudit(ResultSet rs) throws SQLException {
        try {
            if (!KIND.equals(rs.getString("command_type")) || !START.equals(rs.getString("start_command_type"))
                    || !BPMN.equals(rs.getString("model_type"))) throw unavailable();
            Authority bound = authority(rs);
            ProcessActor actor = new ProcessActor(rs.getLong("tenant_id"),
                    ProcessActor.SubjectType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"),
                    rs.getString("subject_issuer"));
            String instanceId = rs.getString("process_instance_id"), jobId = rs.getString("job_id");
            String commandId = rs.getString("command_id"), occurrenceId = rs.getString("occurrence_id");
            var command = new RecoverProcessWorkerCommand(instanceId, jobId, commandId,
                    rs.getString("expected_version"), rs.getString("reason"));
            var start = new TaskOrigin(actor, rs.getString("start_command_id"), rs.getString("start_request_sha256"),
                    rs.getString("release_key"), rs.getString("correlation_id"), instanceId, bound.start().createdAt());
            var result = new RecoverProcessWorkerResult(instanceId, jobId, occurrenceId, commandId,
                    RecoverProcessWorkerResult.StatusEnum.valueOf(rs.getString("result_status")), rs.getInt("retries"),
                    timestamp(rs, "decided_at"));
            var entry = new Entry(actor, rs.getString("request_sha256"), command, start, rs.getString("definition_key"),
                    rs.getString("engine_definition_id"), rs.getString("definition_sha256"), rs.getString("business_key"),
                    occurrenceId, rs.getString("execution_id"), rs.getString("element_id"), rs.getInt("deadletter_revision"),
                    rs.getString("evidence_sha256"), rs.getString("admission_policy"), rs.getString("failure_code"),
                    timestamp(rs, "failure_decided_at"), result);
            return new AuditRead(entry, bound);
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static Authority authority(ResultSet rs) throws SQLException {
        try {
            if (!rs.getBoolean("base_no_decision") || !"APPLIED".equals(rs.getString("start_state"))) throw unavailable();
            var base = new Receipt(rs.getString("base_request_sha256"), rs.getString("base_release_key"),
                    rs.getString("base_correlation_id"), rs.getString("base_state"), rs.getString("base_resource_id"),
                    timestamp(rs, "base_created_at"), null);
            var actor = new ProcessActor(rs.getLong("start_tenant_id"),
                    ProcessActor.SubjectType.valueOf(rs.getString("start_subject_type")), rs.getString("start_subject_id"),
                    rs.getString("start_subject_issuer"));
            var start = new TaskOrigin(actor, rs.getString("bound_start_command_id"), rs.getString("bound_start_request_sha256"),
                    rs.getString("start_release_key"), rs.getString("start_correlation_id"), rs.getString("start_resource_id"),
                    timestamp(rs, "start_created_at"));
            return new Authority(base, start, rs.getString("bound_engine_definition_id"), rs.getString("bound_definition_sha256"));
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static Instant timestamp(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        if (value == null || !microseconds(value.toInstant())) throw unavailable();
        return value.toInstant();
    }

    private static boolean matchesReceipt(Receipt receipt, String state, String resource, String requestHash, TaskOrigin start) {
        return receipt != null && state.equals(receipt.state()) && Objects.equals(resource, receipt.resourceId())
                && requestHash.equals(receipt.requestHash()) && start.releaseKey().equals(receipt.releaseKey())
                && start.correlationId().equals(receipt.correlationId()) && microseconds(receipt.createdAt())
                && receipt.decisionResult() == null;
    }

    public record Entry(ProcessActor actor, String requestHash, RecoverProcessWorkerCommand command,
                        TaskOrigin start, String definitionKey, String engineDefinitionId, String definitionSha256,
                        String businessKey, String occurrenceId, String executionId, String elementId,
                        int deadletterRevision, String evidenceSha256, String admissionPolicy, String failureCode,
                        Instant failureDecidedAt, RecoverProcessWorkerResult result) {
        public Entry {
            if (!validStart(start) || !start.requester().equals(actor) || !hash(requestHash) || command == null || result == null
                    || !start.instanceId().equals(command.instanceId()) || !command.instanceId().equals(result.instanceId())
                    || !command.commandId().equals(result.commandId()) || !command.jobId().equals(result.jobId())
                    || !command.jobId().equals(occurrenceId) || !occurrenceId.equals(result.occurrenceId())
                    || command.jobId().codePointCount(0, command.jobId().length()) > 64
                    || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || !safeText(engineDefinitionId, 255) || !hash(definitionSha256) || !safeText(businessKey, 255)
                    || !safeText(executionId, 255) || !safeText(elementId, 128) || deadletterRevision <= 0
                    || !hash(evidenceSha256) || !POLICY.equals(admissionPolicy)
                    || failureCode == null || !failureCode.matches("[A-Z][A-Z0-9_]{0,63}")
                    || !microseconds(failureDecidedAt) || !microseconds(result.decidedAt())) throw invalid();
        }

        @Override
        public String toString() {
            return "ProcessWorkerRecoveryReceiptStore.Entry[redacted]";
        }
    }

    private record AuditRead(Entry entry, Authority authority) { }

    private record Authority(Receipt base, TaskOrigin start, String definitionId, String definitionSha256) {
        private boolean matches(Entry entry) {
            return entry.start().equals(start) && entry.engineDefinitionId().equals(definitionId)
                    && entry.definitionSha256().equals(definitionSha256);
        }
    }

    private static boolean validStart(TaskOrigin start) {
        return start != null && start.requester() != null && !"LEGACY".equals(start.requester().issuerFingerprint())
                && safeText(start.instanceId(), 255) && !".".equals(start.instanceId()) && !"..".equals(start.instanceId())
                && start.commandId() != null && start.commandId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                && start.correlationId() != null && start.correlationId().matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")
                && start.releaseKey() != null && start.releaseKey().matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                && hash(start.requestHash()) && microseconds(start.createdAt());
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean safeText(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }

    private static boolean microseconds(Instant value) {
        return value != null && value.getNano() % 1_000 == 0;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process Worker recovery receipt is invalid");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process Worker recovery storage is unavailable");
    }
}
