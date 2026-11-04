package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CancelProcessResult;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessInstanceStatusEnum;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable detailed process-cancellation audit participant. */
public final class ProcessCancellationReceiptStore {
    private static final String CANCEL = "PROCESS_CANCEL";
    private static final String START = "START";
    private static final String BPMN = "BPMN";

    private final DataSource source;
    private final String schema;
    private final String table;
    private final String commands;
    private final String definitions;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean verified = new AtomicBoolean();

    public ProcessCancellationReceiptStore(DataSource source, String schema) {
        this.source = Objects.requireNonNull(source, "source");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.schema = schema;
        String prefix = '"' + schema + "\".";
        table = prefix + "tianji_process_cancel_receipt";
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
        new ProcessApiSchemaVerifier(source, schema).verifyCancellation();
        verified.set(true);
    }

    /** Replay discovery is immutable and never substitutes for current authorization by the caller. */
    public Optional<Entry> find(ProcessActor actor, String commandId, String requestHash) {
        actor(actor);
        command(commandId);
        hash(requestHash);
        current();
        List<Entry> rows = jdbc.query(joined()
                        + " WHERE b.tenant_id=? AND b.subject_type=? AND b.subject_issuer=? AND b.subject_id=? "
                        + "AND b.command_type='PROCESS_CANCEL' AND b.command_id=? AND b.state='APPLIED'",
                (rs, row) -> entry(rs), actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(),
                actor.subjectId(), commandId);
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) {
            current();
            return Optional.empty();
        }
        Entry result = rows.getFirst();
        if (!result.actor().equals(actor) || !result.command().commandId().equals(commandId)) throw unavailable();
        if (!result.requestHash().equals(requestHash)) throw new ProcessConflictException();
        current();
        return Optional.of(result);
    }

    /** The caller owns the local transaction containing native deletion and both receipt writes. */
    public void insert(Entry entry) {
        Objects.requireNonNull(entry, "entry");
        Instant now = current();
        if (entry.start().createdAt().isAfter(now) || entry.result().decidedAt().isAfter(now)) throw unavailable();
        List<Authority> rows = jdbc.query("SELECT b.state AS base_state,b.resource_id AS base_resource_id,b.request_sha256 AS base_request_sha256,"
                        + "b.release_key AS base_release_key,b.correlation_id AS base_correlation_id,s.state AS start_state,"
                        + "s.subject_type AS start_subject_type,s.subject_issuer AS start_subject_issuer,s.subject_id AS start_subject_id,"
                        + "s.resource_id AS start_resource_id,s.release_key AS start_release_key,s.correlation_id AS start_correlation_id,"
                        + "s.created_at AS start_created_at,d.engine_definition_id AS bound_engine_definition_id,d.model_sha256 AS bound_definition_sha256 "
                        + "FROM (SELECT * FROM " + commands + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=? "
                        + "AND command_type=? AND command_id=? FOR UPDATE) b JOIN " + commands + " s ON s.tenant_id=b.tenant_id "
                        + "AND s.subject_type=b.subject_type AND s.subject_issuer=b.subject_issuer AND s.subject_id=b.subject_id "
                        + "AND s.command_type=? AND s.command_id=? AND s.request_sha256=? JOIN " + definitions + " d ON d.tenant_id=b.tenant_id "
                        + "AND d.release_key=? AND d.model_type=? AND d.definition_key=?",
                (rs, row) -> authority(rs), entry.actor().tenantId(), entry.actor().subjectType().name(),
                entry.actor().issuerFingerprint(), entry.actor().subjectId(), CANCEL, entry.command().commandId(),
                START, entry.start().commandId(), entry.start().requestHash(), entry.start().releaseKey(), BPMN,
                entry.definitionKey());
        if (rows.size() != 1 || !rows.getFirst().matches(entry)) throw unavailable();
        if (jdbc.update("INSERT INTO " + table + " (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id,"
                        + "request_sha256,process_instance_id,start_command_type,start_command_id,start_request_sha256,release_key,"
                        + "model_type,definition_key,engine_definition_id,definition_sha256,business_key,correlation_id,expected_version,"
                        + "reason,accepted,result_status,result_version,result_finished_at,decided_at,agent_closure_scheduled) VALUES ("
                        + "?,".repeat(25) + "?)",
                entry.actor().tenantId(), entry.actor().subjectType().name(), entry.actor().issuerFingerprint(),
                entry.actor().subjectId(), CANCEL, entry.command().commandId(), entry.requestHash(),
                entry.command().instanceId(), START, entry.start().commandId(), entry.start().requestHash(),
                entry.start().releaseKey(), BPMN, entry.definitionKey(), entry.engineDefinitionId(),
                entry.definitionSha256(), entry.businessKey(), entry.start().correlationId(),
                entry.command().expectedVersion(), entry.command().reason(), entry.result().accepted(),
                entry.result().status().name(), entry.result().resultingVersion(),
                Timestamp.from(entry.result().finishedAt()), Timestamp.from(entry.result().decidedAt()),
                entry.result().linkedAgentClosureScheduled()) != 1) throw unavailable();
        current();
    }

    private String joined() {
        return "SELECT r.*,b.state AS base_state,b.resource_id AS base_resource_id,b.request_sha256 AS base_request_sha256,"
                + "b.release_key AS base_release_key,b.correlation_id AS base_correlation_id,s.state AS start_state,"
                + "s.subject_type AS start_subject_type,s.subject_issuer AS start_subject_issuer,s.subject_id AS start_subject_id,"
                + "s.resource_id AS start_resource_id,s.release_key AS start_release_key,s.correlation_id AS start_correlation_id,"
                + "s.created_at AS start_created_at,d.engine_definition_id AS bound_engine_definition_id,d.model_sha256 AS bound_definition_sha256 "
                + "FROM " + commands + " b LEFT JOIN " + table + " r ON r.tenant_id=b.tenant_id AND r.subject_type=b.subject_type "
                + "AND r.subject_issuer=b.subject_issuer AND r.subject_id=b.subject_id AND r.command_type=b.command_type "
                + "AND r.command_id=b.command_id LEFT JOIN " + commands + " s ON s.tenant_id=r.tenant_id "
                + "AND s.subject_type=r.subject_type AND s.subject_issuer=r.subject_issuer AND s.subject_id=r.subject_id "
                + "AND s.command_type=r.start_command_type AND s.command_id=r.start_command_id "
                + "AND s.request_sha256=r.start_request_sha256 LEFT JOIN " + definitions + " d ON d.tenant_id=r.tenant_id "
                + "AND d.release_key=r.release_key AND d.model_type=r.model_type AND d.definition_key=r.definition_key";
    }

    private Instant current() {
        requireTransaction();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() "
                + "AND current_setting('server_encoding')='UTF8' "
                + "AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Timestamp value = jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class);
        if (value == null) throw unavailable();
        return value.toInstant();
    }

    private void requireTransaction() {
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.hasResource(source) || Thread.currentThread().isInterrupted()) {
            throw unavailable();
        }
    }

    private static Authority authority(ResultSet rs) throws SQLException {
        try {
            return new Authority(rs.getString("base_state"), rs.getString("base_resource_id"),
                    rs.getString("base_request_sha256"), rs.getString("base_release_key"),
                    rs.getString("base_correlation_id"), rs.getString("start_state"),
                    rs.getString("start_subject_type"), rs.getString("start_subject_issuer"),
                    rs.getString("start_subject_id"), rs.getString("start_resource_id"),
                    rs.getString("start_release_key"), rs.getString("start_correlation_id"),
                    Objects.requireNonNull(rs.getTimestamp("start_created_at")).toInstant(),
                    rs.getString("bound_engine_definition_id"), rs.getString("bound_definition_sha256"));
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        try {
            if (!CANCEL.equals(rs.getString("command_type")) || !START.equals(rs.getString("start_command_type"))
                    || !BPMN.equals(rs.getString("model_type"))) throw unavailable();
            ProcessActor actor = new ProcessActor(rs.getLong("tenant_id"),
                    ProcessActor.SubjectType.valueOf(rs.getString("subject_type")), rs.getString("subject_id"),
                    rs.getString("subject_issuer"));
            CancelProcessCommand command = new CancelProcessCommand(rs.getString("process_instance_id"),
                    rs.getString("command_id"), rs.getString("expected_version"), rs.getString("reason"));
            ProcessCommandReceiptStore.TaskOrigin start = new ProcessCommandReceiptStore.TaskOrigin(
                    new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.valueOf(rs.getString("start_subject_type")),
                            rs.getString("start_subject_id"), rs.getString("start_subject_issuer")),
                    rs.getString("start_command_id"), rs.getString("start_request_sha256"),
                    rs.getString("release_key"), rs.getString("correlation_id"),
                    rs.getString("process_instance_id"), Objects.requireNonNull(rs.getTimestamp("start_created_at")).toInstant());
            CancelProcessResult result = new CancelProcessResult(rs.getString("process_instance_id"),
                    rs.getString("command_id"), rs.getBoolean("accepted"),
                    ProcessInstanceStatusEnum.valueOf(rs.getString("result_status")), rs.getString("result_version"),
                    Objects.requireNonNull(rs.getTimestamp("result_finished_at")).toInstant(),
                    Objects.requireNonNull(rs.getTimestamp("decided_at")).toInstant(),
                    rs.getBoolean("agent_closure_scheduled"));
            Entry entry = new Entry(actor, rs.getString("request_sha256"), command, start,
                    rs.getString("definition_key"), rs.getString("engine_definition_id"),
                    rs.getString("definition_sha256"), rs.getString("business_key"), result);
            Authority authority = authority(rs);
            if (!authority.matchesApplied(entry)) throw unavailable();
            return entry;
        } catch (RuntimeException malformed) {
            throw unavailable();
        }
    }

    public record Entry(ProcessActor actor, String requestHash, CancelProcessCommand command,
                        ProcessCommandReceiptStore.TaskOrigin start, String definitionKey,
                        String engineDefinitionId, String definitionSha256, String businessKey,
                        CancelProcessResult result) {
        public Entry {
            if (actor == null || "LEGACY".equals(actor.issuerFingerprint()) || !hashValue(requestHash)
                    || command == null || start == null || result == null || !actor.equals(start.requester())
                    || !command.instanceId().equals(start.instanceId())
                    || !command.instanceId().equals(result.instanceId())
                    || !command.commandId().equals(result.commandId()) || !commandValue(start.commandId())
                    || !hashValue(start.requestHash()) || !releaseValue(start.releaseKey())
                    || !commandValue(start.correlationId()) || start.createdAt() == null
                    || start.createdAt().getNano() % 1_000 != 0 || !definitionValue(definitionKey)
                    || !textValue(engineDefinitionId, 255) || !hashValue(definitionSha256)
                    || !textValue(businessKey, 255) || result.finishedAt().getNano() % 1_000 != 0
                    || result.decidedAt().getNano() % 1_000 != 0) throw invalid();
        }

        @Override
        public String toString() {
            return "ProcessCancellationReceiptStore.Entry[redacted]";
        }
    }

    private record Authority(String baseState, String baseResourceId, String baseRequestHash,
                             String baseReleaseKey, String baseCorrelationId, String startState,
                             String startSubjectType, String startSubjectIssuer, String startSubjectId,
                             String startResourceId, String startReleaseKey, String startCorrelationId,
                             Instant startCreatedAt, String boundEngineDefinitionId,
                             String boundDefinitionSha256) {
        private boolean matches(Entry entry) {
            return "PENDING".equals(baseState) && baseResourceId == null
                    && entry.requestHash().equals(baseRequestHash)
                    && entry.start().releaseKey().equals(baseReleaseKey)
                    && entry.start().correlationId().equals(baseCorrelationId)
                    && startMatches(entry) && definitionMatches(entry);
        }

        private boolean matchesApplied(Entry entry) {
            return "APPLIED".equals(baseState) && entry.command().instanceId().equals(baseResourceId)
                    && entry.requestHash().equals(baseRequestHash)
                    && entry.start().releaseKey().equals(baseReleaseKey)
                    && entry.start().correlationId().equals(baseCorrelationId)
                    && startMatches(entry) && definitionMatches(entry);
        }

        private boolean startMatches(Entry entry) {
            return "APPLIED".equals(startState)
                    && entry.start().requester().subjectType().name().equals(startSubjectType)
                    && entry.start().requester().issuerFingerprint().equals(startSubjectIssuer)
                    && entry.start().requester().subjectId().equals(startSubjectId)
                    && entry.start().instanceId().equals(startResourceId)
                    && entry.start().releaseKey().equals(startReleaseKey)
                    && entry.start().correlationId().equals(startCorrelationId)
                    && entry.start().createdAt().equals(startCreatedAt);
        }

        private boolean definitionMatches(Entry entry) {
            return entry.engineDefinitionId().equals(boundEngineDefinitionId)
                    && entry.definitionSha256().equals(boundDefinitionSha256);
        }
    }

    private static void actor(ProcessActor actor) {
        if (actor == null || "LEGACY".equals(actor.issuerFingerprint())) throw invalid();
    }

    private static void command(String value) {
        if (!commandValue(value)) throw invalid();
    }

    private static void hash(String value) {
        if (!hashValue(value)) throw invalid();
    }

    private static boolean commandValue(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    }

    private static boolean releaseValue(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    }

    private static boolean definitionValue(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    }

    private static boolean hashValue(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean textValue(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character)
                    || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Process cancellation receipt is invalid");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process cancellation receipt or storage is unavailable");
    }
}
