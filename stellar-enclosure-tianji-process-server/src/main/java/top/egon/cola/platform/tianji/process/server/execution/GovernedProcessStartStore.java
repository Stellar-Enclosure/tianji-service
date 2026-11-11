package top.egon.cola.platform.tianji.process.server.execution;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.PlatformReleaseTenantFence;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

/** Owns selection and provenance reads and participates in the native START transaction. */
final class GovernedProcessStartStore {
    private static final String POINTER_COLUMNS = "a.tenant_id,a.channel,a.revision,a.candidate_sha256,"
            + "a.process_release_version,a.process_manifest_sha256,a.process_definition_key,a.process_model_sha256,"
            + "a.worker_topic,a.worker_schema_version,a.worker_binding_sha256";
    private static final String CLOCK = "SELECT CASE WHEN NOT pg_catalog.pg_is_in_recovery()"
            + " AND pg_catalog.current_setting('session_replication_role')='origin'";
    private final JdbcTemplate jdbc;
    private final String activation;
    private final String provenance;
    private final String receipts;

    GovernedProcessStartStore(DataSource source, String schema) {
        new ProcessApiSchemaVerifier(source, schema).verifyGovernedProcessStart();
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        String namespace = '"' + schema + "\".";
        activation = namespace + "tianji_platform_release_activation";
        provenance = namespace + "tianji_governed_process_start";
        receipts = namespace + "tianji_process_command_receipt";
    }

    void begin(long tenantId) {
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        PlatformReleaseTenantFence.acquireShared(jdbc, tenantId);
    }

    Instant now() { return databaseTime(CLOCK + " THEN pg_catalog.clock_timestamp() END"); }

    Instant transactionTime() {
        return databaseTime(CLOCK + " AND pg_catalog.current_setting('transaction_isolation')='read committed'"
                + " AND pg_catalog.current_setting('transaction_read_only')='off' THEN pg_catalog.clock_timestamp() END");
    }

    Optional<Pointer> current(long tenantId, String channel) {
        return one("SELECT " + POINTER_COLUMNS + " FROM " + activation
                        + " a WHERE a.tenant_id=? AND a.channel=? ORDER BY a.revision DESC LIMIT 1",
                GovernedProcessStartStore::readPointer, tenantId, channel);
    }

    /** A hint to avoid reopening current Worker evidence for an owned retry. The transaction still locks the receipt. */
    boolean hasReceipt(ProcessActor actor, String commandId) {
        Boolean present = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM " + receipts
                        + " WHERE tenant_id=? AND subject_type=? AND subject_issuer=? AND subject_id=?"
                        + " AND command_type='START' AND command_id=?)", Boolean.class,
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId);
        if (present == null) throw unavailable();
        return present;
    }

    Optional<Provenance> find(ProcessActor actor, String commandId) {
        return one("SELECT " + POINTER_COLUMNS + ",p.governed_request_sha256,p.resolved_request_sha256,p.instance_id"
                        + " FROM " + provenance + " p LEFT JOIN " + activation + " a ON a.tenant_id=p.tenant_id"
                        + " AND a.channel=p.channel AND a.revision=p.activation_revision"
                        + " WHERE p.tenant_id=? AND p.subject_type=? AND p.subject_issuer=? AND p.subject_id=?"
                        + " AND p.command_type='START' AND p.command_id=?",
                (rs, row) -> new Provenance(readPointer(rs, row), rs.getString("governed_request_sha256"),
                        rs.getString("resolved_request_sha256"), rs.getString("instance_id")),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId);
    }

    void append(ProcessActor actor, String commandId, String correlationId, Pointer pointer,
                String governedHash, String resolvedHash, String authorizationHash, String instanceId) {
        int inserted = jdbc.update("INSERT INTO " + provenance + " (tenant_id,subject_type,subject_issuer,subject_id,"
                        + "command_type,command_id,channel,activation_revision,governed_request_sha256,resolved_request_sha256,"
                        + "authorization_sha256,instance_id,created_at)"
                        + " SELECT r.tenant_id,r.subject_type,r.subject_issuer,r.subject_id,r.command_type,r.command_id,"
                        + "a.channel,a.revision,?,?,?,r.resource_id,pg_catalog.clock_timestamp()"
                        + " FROM " + receipts + " r JOIN " + activation + " a ON a.tenant_id=r.tenant_id"
                        + " AND a.channel=? AND a.revision=? AND a.candidate_sha256=CAST(? AS char(64))"
                        + " AND a.process_release_version=r.release_key"
                        + " WHERE r.tenant_id=? AND r.subject_type=? AND r.subject_issuer=? AND r.subject_id=?"
                        + " AND r.command_type='START' AND r.command_id=? AND r.correlation_id=? AND r.state='APPLIED'"
                        + " AND r.request_sha256=CAST(? AS char(64)) AND r.resource_id=?",
                governedHash, resolvedHash, authorizationHash, pointer.channel(), pointer.revision(), pointer.candidateHash(),
                actor.tenantId(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId,
                correlationId, resolvedHash, instanceId);
        if (inserted != 1) throw unavailable();
    }

    private Instant databaseTime(String sql) {
        Instant value = jdbc.queryForObject(sql, (rs, row) -> {
            var timestamp = rs.getTimestamp(1);
            return timestamp == null ? null : timestamp.toInstant();
        });
        if (value == null) throw unavailable();
        return value;
    }

    private <T> Optional<T> one(String sql, RowMapper<T> mapper, Object... arguments) {
        var rows = jdbc.query(sql, mapper, arguments);
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private static Pointer readPointer(ResultSet rs, int row) throws SQLException {
        return new Pointer(rs.getLong("tenant_id"), rs.getString("channel"), rs.getLong("revision"),
                rs.getString("candidate_sha256"), rs.getString("process_release_version"), rs.getString("process_manifest_sha256"),
                rs.getString("process_definition_key"), rs.getString("process_model_sha256"), rs.getString("worker_topic"),
                rs.getString("worker_schema_version"), rs.getString("worker_binding_sha256"));
    }

    static boolean hash(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Governed Process START provenance is unavailable");
    }

    record Pointer(long tenantId, String channel, long revision, String candidateHash, String releaseKey, String manifestHash,
                   String definitionKey, String modelHash, String workerTopic, String workerSchemaVersion, String workerBindingHash) {
        Pointer {
            if (tenantId <= 0 || channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}$") || revision < 1
                    || !hash(candidateHash) || releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || !hash(manifestHash) || definitionKey == null || !definitionKey.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || !hash(modelHash) || !hash(workerBindingHash)) throw unavailable();
            ProcessModelValidator.nativeWorkerTopic(workerTopic, workerSchemaVersion);
        }
    }

    record Provenance(Pointer pointer, String governedHash, String resolvedHash, String instanceId) {
        Provenance {
            if (pointer == null || !hash(governedHash) || !hash(resolvedHash)
                    || instanceId == null || instanceId.isBlank()) throw unavailable();
        }
    }
}
