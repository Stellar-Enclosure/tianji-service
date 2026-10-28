package top.egon.cola.platform.tianji.process.server.worker;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** JDBC lease evidence. The caller owns the transaction and must fence the native job in that transaction. */
public final class WorkerLeaseStore {
    private final DataSource source;
    private final JdbcTemplate jdbc;
    private final String table;

    public WorkerLeaseStore(DataSource source, DataSourceTransactionManager transactionManager, String schema) {
        if (source == null || transactionManager == null || transactionManager.getDataSource() != source
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) throw invalid();
        this.source = source;
        this.jdbc = new JdbcTemplate(source);
        this.table = '"' + schema + "\".tianji_worker_lease_receipt";
    }

    public Lease claim(Claim claim) {
        requireTransaction();
        Objects.requireNonNull(claim, "claim");
        ProcessActor actor = claim.actor();
        jdbc.update("UPDATE " + table + " SET state='EXPIRED', decided_at=clock_timestamp() "
                        + "WHERE tenant_id=? AND job_id=? AND state='LEASED' AND lease_expires_at<=clock_timestamp()",
                actor.tenantId(), claim.jobId());
        String sql = "WITH observed AS (SELECT clock_timestamp() AS now), bounded AS ("
                + "SELECT now, LEAST(now + (? * interval '1 millisecond'), CAST(? AS timestamptz)) AS expires FROM observed) "
                + "INSERT INTO " + table + " (token_sha256, tenant_id, subject_issuer, subject_id, topic, schema_version, "
                + "job_id, occurrence_id, scope_type, scope_id, release_key, definition_id, element_id, idempotency_key, "
                + "model_sha256, input_sha256, native_owner, leased_at, lease_expires_at, state) "
                + "SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,now,expires,'LEASED' FROM bounded WHERE expires > now RETURNING *";
        try {
            List<Lease> rows = jdbc.query(sql, (rs, row) -> read(rs), claim.duration().toMillis(), Timestamp.from(claim.nativeExpiresAt()),
                    claim.tokenSha256(), actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), claim.topic(), claim.schemaVersion(),
                    claim.jobId(), claim.occurrenceId(), claim.scopeType().name(), claim.scopeId(), claim.releaseKey(), claim.definitionId(),
                    claim.elementId(), claim.idempotencyKey(), claim.modelSha256(), claim.inputSha256(), claim.nativeOwner());
            if (rows.size() != 1) throw conflict();
            return rows.getFirst();
        } catch (DuplicateKeyException duplicate) {
            // The database transaction is aborted on a uniqueness conflict; the caller must roll it back.
            throw conflict();
        }
    }

    /** The adapter must lock and verify the native job before locking a live lease. */
    public Lease lock(ProcessActor actor, String jobId, String tokenSha256) {
        requireTransaction();
        requireWorker(actor);
        id(jobId, 255);
        hash(tokenSha256);
        List<Lease> rows = jdbc.query("SELECT * FROM " + table + " WHERE token_sha256=? AND tenant_id=? "
                        + "AND subject_issuer=? AND subject_id=? AND job_id=? FOR UPDATE",
                (rs, row) -> read(rs), tokenSha256, actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), jobId);
        if (rows.size() != 1) throw unavailable();
        return rows.getFirst();
    }

    /** Replays an identical terminal operation; the native operation and this update must share the caller's transaction. */
    public Lease finish(ProcessActor actor, String jobId, String tokenSha256, Finish finish) {
        requireTransaction();
        Objects.requireNonNull(finish, "finish");
        Lease current = lock(actor, jobId, tokenSha256);
        if (!"LEASED".equals(current.state())) {
            if (finish.outcome().name().equals(current.state()) && finish.resultSha256().equals(current.resultSha256())
                    && Objects.equals(finish.businessReceiptId(), current.businessReceiptId())
                    && Objects.equals(finish.failureKind(), current.failureKind())
                    && Objects.equals(finish.failureCode(), current.failureCode())) return current;
            throw conflict();
        }
        List<Lease> rows = jdbc.query("UPDATE " + table + " SET state=?,result_sha256=?,decided_at=clock_timestamp(), "
                        + "business_receipt_id=?,failure_kind=?,failure_code=? WHERE token_sha256=? AND tenant_id=? "
                        + "AND subject_issuer=? AND subject_id=? AND job_id=? AND state='LEASED' "
                        + "AND lease_expires_at>clock_timestamp() RETURNING *", (rs, row) -> read(rs),
                finish.outcome().name(), finish.resultSha256(), finish.businessReceiptId(),
                finish.failureKind() == null ? null : finish.failureKind().name(), finish.failureCode(), tokenSha256,
                actor.tenantId(), actor.issuerFingerprint(), actor.subjectId(), jobId);
        if (rows.size() != 1) throw conflict();
        return rows.getFirst();
    }

    public static String tokenHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(token)) throw invalid();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (IllegalArgumentException malformed) {
            throw invalid();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static Lease read(ResultSet rs) throws SQLException {
        String failure = rs.getString("failure_kind");
        Timestamp decided = rs.getTimestamp("decided_at");
        return new Lease(rs.getString("token_sha256"), rs.getLong("tenant_id"), rs.getString("subject_issuer"), rs.getString("subject_id"),
                rs.getString("topic"), rs.getString("schema_version"), rs.getString("job_id"), rs.getString("occurrence_id"),
                WorkerScopeTypeEnum.valueOf(rs.getString("scope_type")), rs.getString("scope_id"), rs.getString("release_key"),
                rs.getString("definition_id"), rs.getString("element_id"), rs.getString("idempotency_key"), rs.getString("model_sha256"),
                rs.getString("input_sha256"), rs.getString("native_owner"), rs.getTimestamp("leased_at").toInstant(),
                rs.getTimestamp("lease_expires_at").toInstant(), rs.getString("state"), rs.getString("result_sha256"),
                decided == null ? null : decided.toInstant(), rs.getString("business_receipt_id"),
                failure == null ? null : WorkerFailureKindEnum.valueOf(failure), rs.getString("failure_code"));
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.hasResource(source)) {
            throw new IllegalStateException("Worker lease operations require the owning JDBC transaction");
        }
    }

    private static void requireWorker(ProcessActor actor) {
        if (actor == null || actor.subjectType() != ProcessActor.SubjectType.SERVICE || "LEGACY".equals(actor.issuerFingerprint())) throw invalid();
    }
    private static void hash(String value) { if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid(); }
    private static void id(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max || !value.equals(value.trim())) throw invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127 || Character.isLowSurrogate(c)
                    || Character.isHighSurrogate(c) && (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++i)))) throw invalid();
        }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Worker lease request is invalid"); }
    private static ProcessConflictException conflict() { return new ProcessConflictException(); }
    private static ProcessResourceNotFoundException unavailable() { return new ProcessResourceNotFoundException(); }

    public record Claim(ProcessActor actor, String tokenSha256, String topic, String schemaVersion, String jobId,
                        String occurrenceId, WorkerScopeTypeEnum scopeType, String scopeId, String releaseKey,
                        String definitionId, String elementId, String idempotencyKey, String modelSha256, String inputSha256,
                        String nativeOwner, Instant nativeExpiresAt, Duration duration) {
        public Claim {
            requireWorker(actor);
            hash(tokenSha256);
            if (topic == null || !topic.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || schemaVersion == null || !schemaVersion.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || releaseKey == null || !releaseKey.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")
                    || elementId == null || !elementId.matches("[A-Za-z_][A-Za-z0-9_.-]{0,127}")
                    || idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")) throw invalid();
            id(jobId, 255);
            id(occurrenceId, 255);
            id(scopeId, 255);
            id(definitionId, 255);
            Objects.requireNonNull(scopeType, "scopeType");
            hash(modelSha256);
            hash(inputSha256);
            if (!Objects.equals(nativeOwner, "tianji-" + tokenSha256) || nativeExpiresAt == null || duration == null
                    || duration.compareTo(Duration.ofSeconds(1)) < 0 || duration.compareTo(Duration.ofMinutes(15)) > 0
                    || duration.toMillis() * 1_000_000L != duration.toNanos()) throw invalid();
        }
    }

    public record Finish(WorkerReceiptOutcomeEnum outcome, String resultSha256, String businessReceiptId,
                         WorkerFailureKindEnum failureKind, String failureCode) {
        public Finish {
            Objects.requireNonNull(outcome, "outcome");
            hash(resultSha256);
            if (outcome == WorkerReceiptOutcomeEnum.COMPLETED) {
                id(businessReceiptId, 255);
                if (failureKind != null || failureCode != null) throw invalid();
            } else {
                if (businessReceiptId != null || failureKind == null || failureCode == null
                        || !failureCode.matches("[A-Z][A-Z0-9_]{0,63}")
                        || outcome == WorkerReceiptOutcomeEnum.RETRY_SCHEDULED && failureKind != WorkerFailureKindEnum.RETRYABLE
                        || outcome == WorkerReceiptOutcomeEnum.BUSINESS_REJECTED && failureKind != WorkerFailureKindEnum.BUSINESS_REJECTED) throw invalid();
            }
        }
    }

    public record Lease(String tokenSha256, long tenantId, String subjectIssuer, String subjectId, String topic,
                        String schemaVersion, String jobId, String occurrenceId, WorkerScopeTypeEnum scopeType, String scopeId,
                        String releaseKey, String definitionId, String elementId, String idempotencyKey, String modelSha256,
                        String inputSha256, String nativeOwner, Instant leasedAt, Instant leaseExpiresAt, String state,
                        String resultSha256, Instant decidedAt, String businessReceiptId, WorkerFailureKindEnum failureKind,
                        String failureCode) { }
}
