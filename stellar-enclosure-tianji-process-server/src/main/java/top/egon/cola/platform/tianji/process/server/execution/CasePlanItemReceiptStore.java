package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.CaseOperationResult;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemActionEnum;
import top.egon.cola.platform.tianji.contract.orchestration.CasePlanItemStateEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
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

/** Immutable plan-item results participate in the command owner's local native-action transaction. */
public final class CasePlanItemReceiptStore {
    private final DataSource source;
    private final String table;
    private final String commands;
    private final String origins;
    private final String definitions;
    private final JdbcTemplate jdbc;
    private final CaseStartProvenanceStore provenance;
    private final AtomicBoolean verified = new AtomicBoolean();

    public CasePlanItemReceiptStore(DataSource source, String schema) {
        this.source = Objects.requireNonNull(source, "source");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        String prefix = '"' + schema + "\".";
        table = prefix + "tianji_case_plan_item_receipt"; commands = prefix + "tianji_process_command_receipt";
        origins = prefix + "tianji_case_start_provenance"; definitions = prefix + "tianji_process_release_definition";
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5); provenance = new CaseStartProvenanceStore(source, schema);
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public void verifyStorage() {
        verified.set(false);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        provenance.verifyStorage(); verified.set(true);
    }

    /** The application has already serialized the command, Case scope and native mutation, in that order. */
    public void insert(CaseContext context, Entry entry) {
        Instant now = current(context); Objects.requireNonNull(entry, "entry");
        if (!entry.actor().equals(context.actor()) || entry.authVersion() != context.authVersion()
                || entry.policyVersion() != context.policyVersion() || !context.roleIds().contains(entry.authorizedRoleId())) throw unavailable();
        var seed = source(context, entry);
        var result = entry.result();
        if (result.occurredAt().isAfter(now)) throw unavailable();
        List<Base> pending = jdbc.query("SELECT state,request_sha256,release_key,correlation_id,resource_id FROM " + commands
                        + " WHERE tenant_id=? AND subject_type='USER' AND subject_issuer=? AND subject_id=? AND command_type='CASE_PLAN_ITEM' AND command_id=? FOR UPDATE",
                (rs, row) -> new Base(rs.getString("state"), rs.getString("request_sha256"), rs.getString("release_key"), rs.getString("correlation_id"), rs.getString("resource_id")),
                context.actor().tenantId(), context.actor().issuerFingerprint(), context.actor().subjectId(), result.commandId());
        if (pending.size() != 1) throw unavailable();
        var base = pending.getFirst();
        if (!entry.requestHash().equals(base.requestHash())) throw new ProcessConflictException();
        if (!"PENDING".equals(base.state()) || base.resourceId() != null || !seed.releaseKey().equals(base.releaseKey())
                || !seed.correlationId().equals(base.correlationId())) throw unavailable();
        if (jdbc.update("INSERT INTO " + table + " (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id,request_sha256,case_instance_id,plan_item_instance_id,plan_item_definition_key,action,release_key,definition_key,previous_state,previous_version,resulting_state,resulting_version,occurred_at,model_type,definition_sha256,engine_definition_id,authorized_role_id,auth_version,policy_version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                context.actor().tenantId(), "USER", context.actor().issuerFingerprint(), context.actor().subjectId(), "CASE_PLAN_ITEM", result.commandId(), entry.requestHash(),
                result.caseInstanceId(), result.planItemInstanceId(), result.planItemDefinitionKey(), result.action().name(), result.releaseVersion(), result.definitionKey(),
                result.previousState().name(), result.previousVersion(), result.resultingState().name(), result.resultingVersion(), Timestamp.from(result.occurredAt()),
                "CMMN", entry.modelSha256(), entry.engineDefinitionId(), Long.parseLong(entry.authorizedRoleId()), entry.authVersion(), entry.policyVersion()) != 1) throw unavailable();
        current(context);
    }

    /** Discovery/replay is not authorization: the caller must recheck the current released lane under the shared Case scope. */
    public Optional<Entry> find(CaseContext context, String commandId, String requestHash) {
        current(context);
        if (commandId == null || !commandId.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}") || !hash(requestHash)) throw invalid();
        var rows = jdbc.query("SELECT r.*,b.state AS base_state,b.resource_id AS base_resource_id,b.request_sha256 AS base_request_sha256,b.release_key AS base_release_key,"
                        + "b.correlation_id AS base_correlation_id,p.correlation_id AS source_correlation_id FROM " + table + " r "
                        + "LEFT JOIN " + commands + " b ON b.tenant_id=r.tenant_id AND b.subject_type=r.subject_type AND b.subject_issuer=r.subject_issuer AND b.subject_id=r.subject_id AND b.command_type=r.command_type AND b.command_id=r.command_id "
                        + "AND b.state='APPLIED' AND b.resource_id=r.plan_item_instance_id AND b.request_sha256=r.request_sha256 AND b.release_key=r.release_key "
                        + "LEFT JOIN " + origins + " p ON p.tenant_id=r.tenant_id AND p.case_instance_id=r.case_instance_id AND b.correlation_id=p.correlation_id "
                        + "LEFT JOIN " + definitions + " d ON d.tenant_id=r.tenant_id AND d.release_key=r.release_key AND d.model_type=r.model_type AND d.definition_key=r.definition_key AND d.model_sha256=r.definition_sha256 AND d.engine_definition_id=r.engine_definition_id "
                        + "WHERE r.tenant_id=? AND r.subject_type='USER' AND r.subject_issuer=? AND r.subject_id=? AND r.command_type='CASE_PLAN_ITEM' AND r.command_id=?",
                (rs, row) -> entry(rs), context.actor().tenantId(), context.actor().issuerFingerprint(), context.actor().subjectId(), commandId);
        if (rows.size() > 1) throw unavailable();
        if (rows.isEmpty()) { current(context); return Optional.empty(); }
        var result = rows.getFirst();
        if (!result.requestHash().equals(requestHash)) throw new ProcessConflictException();
        if (!result.actor().equals(context.actor()) || !commandId.equals(result.result().commandId())) throw unavailable();
        source(context, result); current(context);
        return Optional.of(result);
    }

    private CaseStartProvenanceStore.Seed source(CaseContext context, Entry entry) {
        var seed = provenance.find(context.actor().tenantId(), entry.result().caseInstanceId()).orElseThrow(CasePlanItemReceiptStore::unavailable).seed();
        if (!context.actor().issuerFingerprint().equals(seed.owner().issuerFingerprint())
                || !entry.result().caseInstanceId().equals(seed.caseInstanceId()) || !entry.result().releaseVersion().equals(seed.releaseKey())
                || !entry.result().definitionKey().equals(seed.caseDefinitionKey()) || !entry.modelSha256().equals(seed.caseModelSha256())
                || !entry.engineDefinitionId().equals(seed.caseEngineDefinitionId())) throw unavailable();
        return seed;
    }
    private Instant current(CaseContext context) {
        Objects.requireNonNull(context, "context"); context.requireResource("tianji.api.process.case.actPlanItem");
        if (context.actor().subjectType() != ProcessActor.SubjectType.USER || !verified.get()
                || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
        Instant now = Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class)).toInstant();
        if (Thread.currentThread().isInterrupted() || !context.deadlineAt().isAfter(now)) throw unavailable();
        return now;
    }
    private static Entry entry(ResultSet rs) throws SQLException {
        try {
            if (!"USER".equals(rs.getString("subject_type")) || !"CASE_PLAN_ITEM".equals(rs.getString("command_type")) || !"CMMN".equals(rs.getString("model_type"))) throw unavailable();
            var actor = new ProcessActor(rs.getLong("tenant_id"), ProcessActor.SubjectType.USER, rs.getString("subject_id"), rs.getString("subject_issuer"));
            var result = new CaseOperationResult(rs.getString("command_id"), rs.getString("case_instance_id"), rs.getString("plan_item_instance_id"), rs.getString("plan_item_definition_key"),
                    CasePlanItemActionEnum.valueOf(rs.getString("action")), rs.getString("release_key"), rs.getString("definition_key"), CasePlanItemStateEnum.valueOf(rs.getString("previous_state")),
                    rs.getString("previous_version"), CasePlanItemStateEnum.valueOf(rs.getString("resulting_state")), rs.getString("resulting_version"), rs.getTimestamp("occurred_at").toInstant());
            var entry = new Entry(actor, rs.getString("request_sha256"), result, rs.getString("definition_sha256"), rs.getString("engine_definition_id"), Long.toString(rs.getLong("authorized_role_id")), rs.getLong("auth_version"), rs.getLong("policy_version"));
            if (!"APPLIED".equals(rs.getString("base_state")) || !result.planItemInstanceId().equals(rs.getString("base_resource_id"))
                    || !entry.requestHash().equals(rs.getString("base_request_sha256")) || !result.releaseVersion().equals(rs.getString("base_release_key"))
                    || rs.getString("source_correlation_id") == null || !rs.getString("source_correlation_id").equals(rs.getString("base_correlation_id"))) throw unavailable();
            return entry;
        } catch (RuntimeException malformed) { throw unavailable(); }
    }

    public record Entry(ProcessActor actor, String requestHash, CaseOperationResult result,
                        String modelSha256, String engineDefinitionId, String authorizedRoleId,
                        long authVersion, long policyVersion) {
        public Entry {
            if (actor == null || actor.subjectType() != ProcessActor.SubjectType.USER || "LEGACY".equals(actor.issuerFingerprint())
                    || !hash(requestHash) || result == null || !hash(modelSha256) || !id(engineDefinitionId, 255)
                    || authorizedRoleId == null || !authorizedRoleId.matches("[1-9][0-9]{0,18}") || authVersion < 0 || policyVersion < 0
                    || result.occurredAt().getNano() % 1_000 != 0) throw invalid();
            try { if (Long.parseLong(authorizedRoleId) < 1) throw invalid(); }
            catch (NumberFormatException malformed) { throw invalid(); }
        }
        @Override public String toString() { return "CasePlanItemReceiptStore.Entry[redacted]"; }
    }
    private record Base(String state, String requestHash, String releaseKey, String correlationId, String resourceId) { }
    private static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
    private static boolean id(String value, int limit) {
        if (value == null || value.isBlank() || value.length() > limit || !value.equals(value.trim())) return false;
        for (int i = 0; i < value.length(); i++) { char c = value.charAt(i);
            if (c < 32 || c == 127 || Character.isLowSurrogate(c) || Character.isHighSurrogate(c)
                    && (i + 1 == value.length() || !Character.isLowSurrogate(value.charAt(++i)))) return false;
        }
        return true;
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case plan-item receipt is invalid"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Case plan-item receipt or storage is unavailable"); }
}
