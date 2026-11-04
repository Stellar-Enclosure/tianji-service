package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable Case owner/executor facts. Native start and APPLIED command rows must share the caller's local commit. */
public final class CaseStartProvenanceStore {
    private final DataSource source;
    private final String schema;
    private final String table;
    private final JdbcTemplate jdbc;
    private final AtomicBoolean verified = new AtomicBoolean();

    public CaseStartProvenanceStore(DataSource source, String schema) {
        this.source = Objects.requireNonNull(source, "source");
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.schema = schema; table = '"' + schema + "\".tianji_case_start_provenance";
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(5);
    }
    public boolean usesDataSource(DataSource candidate) { return source == candidate; }
    public void verifyStorage() {
        verified.set(false);
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source)); transaction.setReadOnly(true); transaction.setTimeout(15);
        transaction.executeWithoutResult(status -> new ProcessApiSchemaVerifier(source, schema).verifyCase());
        verified.set(true);
    }

    public Entry insert(Seed seed) {
        Objects.requireNonNull(seed, "seed"); requireTransaction();
        int inserted = jdbc.update("INSERT INTO " + table + " (tenant_id,case_instance_id,start_kind,business_key,correlation_id,release_key,case_model_type,case_definition_key,case_engine_definition_id,case_model_sha256,owner_type,owner_issuer,owner_subject,owner_start_command_type,owner_start_command_id,owner_start_request_sha256,executor_type,executor_issuer,executor_subject,case_start_command_type,case_start_command_id,case_start_request_sha256,source_id,parent_process_instance_id,source_occurrence_id,source_element_id,parent_model_type,parent_definition_key,parent_engine_definition_id,parent_model_sha256,created_at) VALUES ("
                        + "?,".repeat(30) + "clock_timestamp())",
                seed.owner().tenantId(), seed.caseInstanceId(), seed.startKind(), seed.businessKey(), seed.correlationId(), seed.releaseKey(), "CMMN", seed.caseDefinitionKey(), seed.caseEngineDefinitionId(), seed.caseModelSha256(),
                seed.owner().subjectType().name(), seed.owner().issuerFingerprint(), seed.owner().subjectId(), seed.ownerStartCommandType(), seed.ownerStartCommandId(), seed.ownerStartRequestSha256(),
                seed.executor().subjectType().name(), seed.executor().issuerFingerprint(), seed.executor().subjectId(), "CASE_START", seed.caseStartCommandId(), seed.caseStartRequestSha256(),
                seed.sourceId(), seed.parentProcessInstanceId(), seed.sourceOccurrenceId(), seed.sourceElementId(), "LINKED_BPMN".equals(seed.startKind()) ? "BPMN" : null, seed.parentDefinitionKey(), seed.parentEngineDefinitionId(), seed.parentModelSha256());
        if (inserted != 1) throw unavailable();
        var result = find(seed.owner().tenantId(), seed.caseInstanceId()).orElseThrow(CaseStartProvenanceStore::unavailable);
        if (!result.seed().equals(seed)) throw unavailable();
        return result;
    }

    public Optional<Entry> find(long tenantId, String caseInstanceId) {
        tenant(tenantId); text(caseInstanceId, 255); requireTransaction();
        var result = one(jdbc.query(joined() + " WHERE p.tenant_id=? AND p.case_instance_id=?", (rs, number) -> entry(rs), tenantId, caseInstanceId));
        if (result.isPresent() && (result.orElseThrow().seed().owner().tenantId() != tenantId || !result.orElseThrow().seed().caseInstanceId().equals(caseInstanceId))) throw unavailable();
        return result;
    }

    public Optional<Entry> byExecutor(ProcessActor executor, String caseStartCommandId) {
        actor(executor); command(caseStartCommandId); requireTransaction();
        var result = one(jdbc.query(joined() + " WHERE p.tenant_id=? AND p.executor_type=? AND p.executor_issuer=? AND p.executor_subject=? AND p.case_start_command_type='CASE_START' AND p.case_start_command_id=?",
                (rs, number) -> entry(rs), executor.tenantId(), executor.subjectType().name(), executor.issuerFingerprint(), executor.subjectId(), caseStartCommandId));
        if (result.isPresent() && (!result.orElseThrow().seed().executor().equals(executor) || !result.orElseThrow().seed().caseStartCommandId().equals(caseStartCommandId))) throw unavailable();
        return result;
    }

    /** Configuration source IDs are audit metadata; renaming one must not create another Case for the same native occurrence. */
    public Optional<Entry> byLinkedOccurrence(long tenantId, String parentProcessInstanceId, String sourceOccurrenceId) {
        tenant(tenantId); text(parentProcessInstanceId, 255); text(sourceOccurrenceId, 255); requireTransaction();
        var result = one(jdbc.query(joined() + " WHERE p.tenant_id=? AND p.start_kind='LINKED_BPMN' AND p.parent_process_instance_id=? AND p.source_occurrence_id=?",
                (rs, number) -> entry(rs), tenantId, parentProcessInstanceId, sourceOccurrenceId));
        if (result.isPresent()) {
            var seed = result.orElseThrow().seed();
            if (seed.owner().tenantId() != tenantId || !"LINKED_BPMN".equals(seed.startKind()) || !parentProcessInstanceId.equals(seed.parentProcessInstanceId()) || !sourceOccurrenceId.equals(seed.sourceOccurrenceId())) throw unavailable();
        }
        return result;
    }

    /** Acquire before any native Case plan-item or CASE HumanTask row fence. A hash collision only serializes unrelated work. */
    public void lockScope(long tenantId, String caseInstanceId) {
        tenant(tenantId); text(caseInstanceId, 255); requireTransaction();
        String hash = ProcessRequestDigest.hash(List.of("TIANJI_CASE_SCOPE_LOCK_V1", Long.toString(tenantId), caseInstanceId), Map.of());
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, ByteBuffer.wrap(HexFormat.of().parseHex(hash)).getLong());
        requireTransaction();
    }

    private void requireTransaction() {
        if (!verified.get() || !TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)
                || Thread.currentThread().isInterrupted()) throw unavailable();
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT pg_is_in_recovery() AND current_setting('server_encoding')='UTF8' AND current_setting('session_replication_role')='origin'", Boolean.class))) throw unavailable();
    }
    private static Optional<Entry> one(List<Entry> rows) {
        if (rows.size() > 1) throw unavailable(); return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private String joined() {
        String commands = '"' + schema + "\".tianji_process_command_receipt", definitions = '"' + schema + "\".tianji_process_release_definition";
        return "SELECT p.*,e.state AS executor_state,e.request_sha256 AS executor_request_sha256,e.resource_id AS executor_resource_id,e.release_key AS executor_release_key,e.correlation_id AS executor_correlation_id,"
                + "o.state AS owner_state,o.request_sha256 AS owner_request_sha256,o.resource_id AS owner_resource_id,o.release_key AS owner_release_key,o.correlation_id AS owner_correlation_id,"
                + "d.engine_definition_id AS bound_case_engine_definition_id,d.model_sha256 AS bound_case_model_sha256,b.engine_definition_id AS bound_parent_engine_definition_id,b.model_sha256 AS bound_parent_model_sha256 FROM " + table + " p "
                + "LEFT JOIN " + commands + " e ON e.tenant_id=p.tenant_id AND e.subject_type=p.executor_type AND e.subject_issuer=p.executor_issuer AND e.subject_id=p.executor_subject AND e.command_type=p.case_start_command_type AND e.command_id=p.case_start_command_id "
                + "LEFT JOIN " + commands + " o ON o.tenant_id=p.tenant_id AND o.subject_type=p.owner_type AND o.subject_issuer=p.owner_issuer AND o.subject_id=p.owner_subject AND o.command_type=p.owner_start_command_type AND o.command_id=p.owner_start_command_id "
                + "LEFT JOIN " + definitions + " d ON d.tenant_id=p.tenant_id AND d.release_key=p.release_key AND d.model_type=p.case_model_type AND d.definition_key=p.case_definition_key "
                + "LEFT JOIN " + definitions + " b ON b.tenant_id=p.tenant_id AND b.release_key=p.release_key AND b.model_type=p.parent_model_type AND b.definition_key=p.parent_definition_key";
    }

    private static Entry entry(ResultSet rs) throws SQLException {
        try {
            long tenant = rs.getLong("tenant_id");
            var owner = new ProcessActor(tenant, ProcessActor.SubjectType.valueOf(rs.getString("owner_type")), rs.getString("owner_subject"), rs.getString("owner_issuer"));
            var executor = new ProcessActor(tenant, ProcessActor.SubjectType.valueOf(rs.getString("executor_type")), rs.getString("executor_subject"), rs.getString("executor_issuer"));
            var seed = new Seed(rs.getString("case_instance_id"), rs.getString("start_kind"), rs.getString("business_key"), rs.getString("correlation_id"), rs.getString("release_key"),
                    rs.getString("case_definition_key"), rs.getString("case_engine_definition_id"), rs.getString("case_model_sha256"), owner,
                    rs.getString("owner_start_command_type"), rs.getString("owner_start_command_id"), rs.getString("owner_start_request_sha256"), executor,
                    rs.getString("case_start_command_id"), rs.getString("case_start_request_sha256"), rs.getString("source_id"), rs.getString("parent_process_instance_id"),
                    rs.getString("source_occurrence_id"), rs.getString("source_element_id"), rs.getString("parent_definition_key"), rs.getString("parent_engine_definition_id"), rs.getString("parent_model_sha256"));
            boolean linked = "LINKED_BPMN".equals(seed.startKind());
            if (!"CMMN".equals(rs.getString("case_model_type")) || !"CASE_START".equals(rs.getString("case_start_command_type"))
                    || !Objects.equals(linked ? "BPMN" : null, rs.getString("parent_model_type"))
                    || !seed.caseEngineDefinitionId().equals(rs.getString("bound_case_engine_definition_id")) || !seed.caseModelSha256().equals(rs.getString("bound_case_model_sha256"))
                    || linked && (!seed.parentEngineDefinitionId().equals(rs.getString("bound_parent_engine_definition_id")) || !seed.parentModelSha256().equals(rs.getString("bound_parent_model_sha256")))) throw unavailable();
            validateCommand(rs, "executor", seed.caseStartRequestSha256(), seed.caseInstanceId(), seed.releaseKey(), seed.correlationId());
            validateCommand(rs, "owner", seed.ownerStartRequestSha256(), linked ? seed.parentProcessInstanceId() : seed.caseInstanceId(), seed.releaseKey(), seed.correlationId());
            return new Entry(seed, rs.getTimestamp("created_at").toInstant());
        } catch (RuntimeException malformed) { throw unavailable(); }
    }
    private static void validateCommand(ResultSet rs, String prefix, String hash, String resource, String release, String correlation) throws SQLException {
        if (!"APPLIED".equals(rs.getString(prefix + "_state")) || !hash.equals(rs.getString(prefix + "_request_sha256")) || !resource.equals(rs.getString(prefix + "_resource_id"))
                || !release.equals(rs.getString(prefix + "_release_key")) || !correlation.equals(rs.getString(prefix + "_correlation_id"))) throw unavailable();
    }

    public record Seed(String caseInstanceId, String startKind, String businessKey, String correlationId, String releaseKey,
                       String caseDefinitionKey, String caseEngineDefinitionId, String caseModelSha256,
                       ProcessActor owner, String ownerStartCommandType, String ownerStartCommandId, String ownerStartRequestSha256,
                       ProcessActor executor, String caseStartCommandId, String caseStartRequestSha256,
                       String sourceId, String parentProcessInstanceId, String sourceOccurrenceId, String sourceElementId,
                       String parentDefinitionKey, String parentEngineDefinitionId, String parentModelSha256) {
        public Seed {
            text(caseInstanceId, 255); text(businessKey, 255); command(correlationId); release(releaseKey); definition(caseDefinitionKey); text(caseEngineDefinitionId, 255); hash(caseModelSha256);
            actor(owner); actor(executor); command(ownerStartCommandId); hash(ownerStartRequestSha256); command(caseStartCommandId); hash(caseStartRequestSha256);
            if (owner.tenantId() != executor.tenantId()) throw invalid();
            if ("DIRECT".equals(startKind)) {
                if (!owner.equals(executor) || !"CASE_START".equals(ownerStartCommandType) || !ownerStartCommandId.equals(caseStartCommandId) || !ownerStartRequestSha256.equals(caseStartRequestSha256)
                        || sourceId != null || parentProcessInstanceId != null || sourceOccurrenceId != null || sourceElementId != null || parentDefinitionKey != null || parentEngineDefinitionId != null || parentModelSha256 != null) throw invalid();
            } else if ("LINKED_BPMN".equals(startKind)) {
                if (executor.subjectType() != ProcessActor.SubjectType.SERVICE || !"START".equals(ownerStartCommandType)) throw invalid();
                command(sourceId); text(parentProcessInstanceId, 255); text(sourceOccurrenceId, 255); definition(sourceElementId); definition(parentDefinitionKey); text(parentEngineDefinitionId, 255); hash(parentModelSha256);
            } else throw invalid();
        }
        @Override public String toString() { return "CaseStartProvenanceStore.Seed[redacted]"; }
    }
    public record Entry(Seed seed, Instant createdAt) {
        public Entry { seed = Objects.requireNonNull(seed, "seed"); createdAt = Objects.requireNonNull(createdAt, "createdAt"); }
        @Override public String toString() { return "CaseStartProvenanceStore.Entry[redacted]"; }
    }
    private static void actor(ProcessActor value) { if (value == null || "LEGACY".equals(value.issuerFingerprint())) throw invalid(); }
    private static void tenant(long value) { if (value < 1) throw invalid(); }
    private static void command(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid(); }
    private static void release(String value) { if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw invalid(); }
    private static void definition(String value) { if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")) throw invalid(); }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private static void text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 32 || character == 127 || Character.isLowSurrogate(character) || Character.isHighSurrogate(character)
                    && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Case start provenance is invalid"); }
    private static IllegalStateException unavailable() { return new IllegalStateException("Case start provenance or storage is unavailable"); }
}
