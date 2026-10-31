package top.egon.cola.platform.tianji.process.server.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskActionReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.HumanTaskScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.Kind;
import top.egon.cola.platform.tianji.process.server.execution.ProcessCommandReceiptStore.TaskOrigin;
import top.egon.cola.platform.tianji.process.server.execution.ProcessRequestDigest;
import top.egon.cola.platform.tianji.process.server.identity.HumanTaskContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.task.HumanTaskActionResult.Entry;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transaction participant for scoped task results. Native mutation, base APPLIED and this immutable reference must share one commit. */
public final class HumanTaskReceiptStore {
    private final DataSource source;
    private final String schema;
    private final String table;
    private final JdbcTemplate jdbc;
    private final ProcessCommandReceiptStore commands;
    private final AtomicBoolean verified = new AtomicBoolean();

    public HumanTaskReceiptStore(DataSource source, String schema) {
        this.source = Objects.requireNonNull(source, "source"); if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw new IllegalArgumentException("Task receipt storage is invalid");
        this.schema = schema; table = '"' + schema + "\".tianji_human_task_receipt"; jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); commands = new ProcessCommandReceiptStore(jdbc, schema);
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public void verifyStorage() { verified.set(false); new HumanTaskStorageVerifier(source, schema).verify(); verified.set(true); }

    /** Same actor/operation/command is serialized even before its first receipt exists. Hash collisions only serialize unrelated commands. */
    public Optional<Entry> lockCommand(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String requestHash) {
        require(context, action, commandId, requestHash);
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_HUMAN_TASK_COMMAND_LOCK_V1", Long.toString(context.actor().tenantId()), context.actor().issuerFingerprint(), context.actor().subjectId(), action.name(), commandId), Map.of());
        long key = ByteBuffer.wrap(HexFormat.of().parseHex(hash)).getLong();
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, key); current(context);
        var receipt = commands.lockIfPresent(context.actor(), kind(action), commandId); if (receipt.isEmpty()) return Optional.empty(); var base = receipt.orElseThrow();
        if (!requestHash.equals(base.requestHash())) throw new ProcessConflictException();
        if (!"APPLIED".equals(base.state()) || base.resourceId() == null) throw unavailable();
        var rows = jdbc.query(joined() + " WHERE h.tenant_id=? AND h.subject_type='USER' AND h.subject_issuer=? AND h.subject_id=? AND h.command_type=? AND h.command_id=?",
                (rs, number) -> entry(rs), context.actor().tenantId(), context.actor().issuerFingerprint(), context.actor().subjectId(), kind(action).name(), commandId);
        current(context);
        if (rows.size() != 1) throw unavailable(); var result = rows.getFirst();
        if (!result.actor().equals(context.actor()) || !result.requestHash().equals(requestHash) || !result.receipt().taskId().equals(base.resourceId()) || !result.receipt().releaseVersion().equals(base.releaseKey())
                || !result.origin().correlationId().equals(base.correlationId()) || result.receipt().action() != action) throw unavailable();
        return Optional.of(result);
    }

    public void reserve(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String requestHash, HumanTaskSnapshot before) {
        require(context, action, commandId, requestHash); Objects.requireNonNull(before, "before");
        if (context.actor().tenantId() != before.origin().requester().tenantId() || !context.actor().issuerFingerprint().equals(before.origin().requester().issuerFingerprint())) throw unavailable();
        if (!commands.insert(context.actor(), kind(action), commandId, requestHash, before.origin().releaseKey(), before.origin().correlationId())) throw new ProcessConflictException(); current(context);
    }

    public void apply(HumanTaskContext context, Entry entry) {
        var result = entry.receipt(); require(context, result.action(), result.commandId(), entry.requestHash());
        if (!entry.actor().equals(context.actor()) || entry.authVersion() != context.authVersion() || entry.policyVersion() != context.policyVersion() || result.scopeType() != HumanTaskScopeTypeEnum.PROCESS) throw unavailable();
        if (entry.authorizedRoleId() != null && !context.roleIds().contains(entry.authorizedRoleId())
                || result.action() == HumanTaskActionEnum.CLAIM && !context.userKey().equals(entry.resultingAssignee())
                || (result.action() == HumanTaskActionEnum.UNCLAIM || result.action() == HumanTaskActionEnum.COMPLETE) && !context.userKey().equals(entry.previousAssignee())) throw unavailable();
        var pending = commands.lock(context.actor(), kind(result.action()), result.commandId());
        if (!pending.requestHash().equals(entry.requestHash()) || !"PENDING".equals(pending.state()) || !pending.releaseKey().equals(result.releaseVersion()) || !pending.correlationId().equals(entry.origin().correlationId())) throw new ProcessConflictException();
        Instant now = current(context); if (result.occurredAt().isAfter(now) || result.occurredAt().isBefore(entry.origin().createdAt())) throw unavailable();
        int inserted = jdbc.update("INSERT INTO " + table + " (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id,request_sha256,task_id,process_instance_id,release_key,model_type,definition_key,definition_sha256,engine_definition_id,task_definition_key,task_kind,lane_key,previous_version,resulting_version,approval_receipt_id,approved,requester_type,requester_issuer,requester_id,start_command_type,start_command_id,start_request_sha256,correlation_id,authorized_role_id,auth_version,policy_version,previous_assignee,resulting_assignee,occurred_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                context.actor().tenantId(), "USER", context.actor().issuerFingerprint(), context.actor().subjectId(), kind(result.action()).name(), result.commandId(), entry.requestHash(), result.taskId(), result.scopeId(), result.releaseVersion(), "BPMN", result.definitionKey(), entry.modelSha256(), entry.engineDefinitionId(), result.taskDefinitionKey(), result.kind().name(), result.lane(), result.previousVersion(), result.resultingVersion(), result.approvalReceiptId(), result.approved(), entry.origin().requester().subjectType().name(), entry.origin().requester().issuerFingerprint(), entry.origin().requester().subjectId(), "START", entry.origin().commandId(), entry.origin().requestHash(), entry.origin().correlationId(), entry.authorizedRoleId() == null ? null : Long.parseLong(entry.authorizedRoleId()), entry.authVersion(), entry.policyVersion(), entry.previousAssignee(), entry.resultingAssignee(), Timestamp.from(result.occurredAt()));
        if (inserted != 1) throw unavailable();
        commands.apply(context.actor(), kind(result.action()), result.commandId(), result.taskId(), null); current(context);
    }

    public Instant current(HumanTaskContext context) {
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Instant now = Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant();
        if (Thread.currentThread().isInterrupted() || !context.deadlineAt().isAfter(now)) throw unavailable(); return now;
    }
    private void require(HumanTaskContext context, HumanTaskActionEnum action, String commandId, String requestHash) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(action, "action"); context.requireResource("tianji.api.process.task." + action.name().toLowerCase(java.util.Locale.ROOT));
        if (commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || requestHash == null || !requestHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Task command reference is invalid"); current(context);
    }
    private static Kind kind(HumanTaskActionEnum action) { return Kind.valueOf("TASK_" + action.name()); }
    private String joined() {
        String commandsTable = '"' + schema + "\".tianji_process_command_receipt"; String definitions = '"' + schema + "\".tianji_process_release_definition";
        return "SELECT h.*,s.created_at AS source_created_at FROM " + table + " h JOIN " + commandsTable + " a ON a.tenant_id=h.tenant_id AND a.subject_type=h.subject_type AND a.subject_issuer=h.subject_issuer AND a.subject_id=h.subject_id AND a.command_type=h.command_type AND a.command_id=h.command_id AND a.state='APPLIED' AND a.request_sha256=h.request_sha256 AND a.resource_id=h.task_id AND a.release_key=h.release_key AND a.correlation_id=h.correlation_id "
                + "JOIN " + commandsTable + " s ON s.tenant_id=h.tenant_id AND s.subject_type=h.requester_type AND s.subject_issuer=h.requester_issuer AND s.subject_id=h.requester_id AND s.command_type='START' AND s.command_id=h.start_command_id AND s.state='APPLIED' AND s.request_sha256=h.start_request_sha256 AND s.resource_id=h.process_instance_id AND s.release_key=h.release_key AND s.correlation_id=h.correlation_id "
                + "JOIN " + definitions + " d ON d.tenant_id=h.tenant_id AND d.release_key=h.release_key AND d.model_type=h.model_type AND d.definition_key=h.definition_key AND d.model_sha256=h.definition_sha256 AND d.engine_definition_id=h.engine_definition_id";
    }
    private static Entry entry(ResultSet rs) throws SQLException {
        try {
            var actor = new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.USER, rs.getString("subject_id"), rs.getString("subject_issuer"));
            var requester = new ProcessActor(actor.tenantId(), ProcessActor.SubjectType.valueOf(rs.getString("requester_type")), rs.getString("requester_id"), rs.getString("requester_issuer"));
            var result = new HumanTaskActionReceiptDTO(rs.getString("command_id"), rs.getString("task_id"), HumanTaskActionEnum.valueOf(rs.getString("command_type").substring(5)), HumanTaskScopeTypeEnum.PROCESS,
                    rs.getString("process_instance_id"), rs.getString("release_key"), rs.getString("definition_key"), rs.getString("task_definition_key"), HumanTaskKindEnum.valueOf(rs.getString("task_kind")), rs.getString("lane_key"), rs.getString("previous_version"), rs.getString("resulting_version"), rs.getString("approval_receipt_id"), (Boolean) rs.getObject("approved"), rs.getTimestamp("occurred_at").toInstant());
            var origin = new TaskOrigin(requester, rs.getString("start_command_id"), rs.getString("start_request_sha256"), result.releaseVersion(), rs.getString("correlation_id"), result.scopeId(), rs.getTimestamp("source_created_at").toInstant());
            Object role = rs.getObject("authorized_role_id");
            return new Entry(actor, rs.getString("request_sha256"), result, origin, rs.getString("definition_sha256"), rs.getString("engine_definition_id"), role == null ? null : role.toString(), rs.getLong("auth_version"), rs.getLong("policy_version"), rs.getString("previous_assignee"), rs.getString("resulting_assignee"));
        } catch (RuntimeException malformed) { throw unavailable(); }
    }
    private static IllegalStateException unavailable() { return new IllegalStateException("Human task command result or storage is unavailable"); }
}
