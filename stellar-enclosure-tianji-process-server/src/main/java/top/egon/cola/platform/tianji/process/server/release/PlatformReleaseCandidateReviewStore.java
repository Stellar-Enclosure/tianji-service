package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.AdmissionWindow;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Append;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.CandidateRecord;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Decision;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewCommand;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.SignedMaterial;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.SubmissionReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.TrustReference;

final class PlatformReleaseCandidateReviewStore {
    private static final String SCOPE = "tenant_id=? AND channel=? AND candidate_sha256=?";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate write;
    private final String candidate;
    private final String review;

    PlatformReleaseCandidateReviewStore(SpringProcessEngineConfiguration engine, ProcessReleaseService releases) {
        Objects.requireNonNull(engine);
        DataSource source = engine.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        String schema = engine.getDatabaseSchema();
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")
                || source == null || manager.getDataSource() != source || !releases.usesStorage(engine, source, manager)) {
            throw new IllegalArgumentException("Candidate review requires the exact owning Process JDBC storage");
        }
        new ProcessApiSchemaVerifier(source, schema).verifyPlatformCandidateReviews();
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
        candidate = "\"" + schema + "\".tianji_platform_release_candidate";
        review = "\"" + schema + "\".tianji_platform_release_review";
    }

    Instant now() {
        Instant value = jdbc.queryForObject("SELECT CASE WHEN NOT pg_catalog.pg_is_in_recovery()"
                        + " AND pg_catalog.current_setting('session_replication_role')='origin' THEN pg_catalog.clock_timestamp() END",
                (rs, row) -> {
                    Timestamp observed = rs.getTimestamp(1);
                    return observed == null ? null : observed.toInstant();
                });
        if (value == null) throw unavailable();
        return value;
    }

    Optional<CandidateRecord> findCandidate(Scope scope) {
        return one("SELECT * FROM " + candidate + " WHERE " + SCOPE, (rs, row) -> candidate(rs),
                scope.tenantId(), scope.channel(), scope.candidateSha256());
    }

    Optional<ReviewReceipt> findReview(Scope scope) {
        return one("SELECT * FROM " + review + " WHERE " + SCOPE, (rs, row) -> review(rs),
                scope.tenantId(), scope.channel(), scope.candidateSha256());
    }

    Optional<ReviewReceipt> findReviewCommand(Scope scope, ProcessActor actor, String commandId) {
        return one("SELECT * FROM " + review + " WHERE tenant_id=? AND channel=? AND reviewer_issuer=?"
                        + " AND reviewer_subject=? AND command_id=?", (rs, row) -> review(rs),
                scope.tenantId(), scope.channel(), actor.issuerFingerprint(), actor.subjectId(), commandId);
    }

    Append<CandidateRecord> appendCandidate(CandidateRecord record, AdmissionWindow window) {
        return append(window, () -> {
            var receipt = record.submission();
            var scope = receipt.scope();
            var trust = receipt.trust();
            int inserted = jdbc.update("INSERT INTO " + candidate + " (tenant_id,channel,candidate_sha256,submitter_issuer,"
                            + "submitter_subject,envelope,sidecar,envelope_sha256,sidecar_sha256,source_revision,release_version,"
                            + "manifest_sha256,signature_issued_at,signature_expires_at,trust_generation,trust_sha256,signer_key_id,"
                            + "signer_issuer,signer_subject,submit_auth_sha256,owner_read_auth_sha256,owner_observed_at,submitted_at)"
                            + " VALUES (" + "?,".repeat(22) + "pg_catalog.clock_timestamp()) ON CONFLICT DO NOTHING",
                    scope.tenantId(), scope.channel(), scope.candidateSha256(), receipt.submittedBy().issuerFingerprint(),
                    receipt.submittedBy().subjectId(), record.material().envelope(), record.material().sidecar(),
                    receipt.envelopeSha256(), receipt.sidecarSha256(), receipt.sourceRevision(), receipt.releaseVersion(),
                    receipt.manifestSha256(), timestamp(receipt.signatureIssuedAt()), timestamp(receipt.signatureExpiresAt()),
                    trust.generation(), trust.descriptorSha256(), trust.keyId(), trust.signer().issuerFingerprint(),
                    trust.signer().subjectId(), receipt.submissionAuditSha256(), receipt.ownerReadAuditSha256(),
                    timestamp(receipt.ownerObservedAt()));
            return new Append<>(findCandidate(scope).orElseThrow(PlatformReleaseCandidateReviewStore::unavailable), inserted == 1);
        });
    }

    Append<ReviewReceipt> appendReview(ReviewReceipt receipt, AdmissionWindow window) {
        return append(window, () -> {
            var scope = receipt.scope();
            var actor = receipt.reviewer();
            var command = receipt.command();
            int inserted = jdbc.update("INSERT INTO " + review + " (tenant_id,channel,candidate_sha256,reviewer_issuer,"
                            + "reviewer_subject,command_id,decision,review_auth_sha256,independence_auth_sha256,owner_read_auth_sha256,"
                            + "owner_observed_at,decided_at) VALUES (" + "?,".repeat(11) + "pg_catalog.clock_timestamp()) ON CONFLICT DO NOTHING",
                    scope.tenantId(), scope.channel(), scope.candidateSha256(), actor.issuerFingerprint(), actor.subjectId(),
                    command.commandId(), command.decision().name(), receipt.reviewAuditSha256(), receipt.independenceAuditSha256(),
                    receipt.ownerReadAuditSha256(), timestamp(receipt.ownerObservedAt()));
            // A second READ COMMITTED statement sees the winning row after a competing insert.
            var winner = findReviewCommand(scope, actor, command.commandId()).or(() -> findReview(scope))
                    .orElseThrow(PlatformReleaseCandidateReviewStore::unavailable);
            return new Append<>(winner, inserted == 1);
        });
    }

    private <T> Append<T> append(AdmissionWindow window, Supplier<Append<T>> operation) {
        return Objects.requireNonNull(write.execute(status -> {
            jdbc.execute("SET LOCAL statement_timeout='5s'");
            jdbc.execute("SET LOCAL lock_timeout='5s'");
            requireCurrent(window);
            var outcome = operation.get();
            // A conflicting insert may have waited while a grant or the signing window expired.
            if (outcome.created()) requireCurrent(window);
            return outcome;
        }));
    }

    private void requireCurrent(AdmissionWindow window) {
        if (!window.contains(now())) throw new AccessDeniedException("Candidate review authority is outside its admitted window");
    }

    private <T> Optional<T> one(String sql, RowMapper<T> reader, Object... args) {
        var rows = jdbc.query(sql, reader, args);
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private static CandidateRecord candidate(ResultSet rs) throws SQLException {
        Scope scope = scope(rs);
        var submitter = new ProcessActor(scope.tenantId(), SERVICE, rs.getString("submitter_subject"), rs.getString("submitter_issuer"));
        var signer = new ProcessActor(scope.tenantId(), USER, rs.getString("signer_subject"), rs.getString("signer_issuer"));
        var trusted = new TrustReference(rs.getLong("trust_generation"), rs.getString("trust_sha256"), rs.getString("signer_key_id"), signer);
        var receipt = new SubmissionReceipt(scope, submitter, rs.getString("envelope_sha256"), rs.getString("sidecar_sha256"),
                rs.getString("source_revision"), rs.getString("release_version"), rs.getString("manifest_sha256"),
                time(rs, "signature_issued_at"), time(rs, "signature_expires_at"), trusted, rs.getString("submit_auth_sha256"),
                rs.getString("owner_read_auth_sha256"), time(rs, "owner_observed_at"), time(rs, "submitted_at"));
        return new CandidateRecord(receipt, new SignedMaterial(rs.getBytes("envelope"), rs.getBytes("sidecar")));
    }

    private static ReviewReceipt review(ResultSet rs) throws SQLException {
        Scope scope = scope(rs);
        var reviewer = new ProcessActor(scope.tenantId(), USER, rs.getString("reviewer_subject"), rs.getString("reviewer_issuer"));
        var command = new ReviewCommand(rs.getString("command_id"), 0, Decision.valueOf(rs.getString("decision")));
        return new ReviewReceipt(scope, reviewer, command, rs.getString("review_auth_sha256"), rs.getString("independence_auth_sha256"),
                rs.getString("owner_read_auth_sha256"), time(rs, "owner_observed_at"), time(rs, "decided_at"));
    }

    private static Scope scope(ResultSet rs) throws SQLException {
        return new Scope(rs.getLong("tenant_id"), rs.getString("channel"), rs.getString("candidate_sha256"));
    }

    private static Instant time(ResultSet rs, String name) throws SQLException { return rs.getTimestamp(name).toInstant(); }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    private static IllegalStateException unavailable() { return new IllegalStateException("Candidate review storage is unavailable"); }
}
