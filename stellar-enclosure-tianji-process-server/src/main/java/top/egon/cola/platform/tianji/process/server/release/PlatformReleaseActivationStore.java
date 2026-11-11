package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.Action;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ActivationReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseActivationService.ChangeCommand;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.AdmissionWindow;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;

/** Owning ProcessDB append-only history; its latest channel revision is the active pointer. */
final class PlatformReleaseActivationStore {
    static final String TENANT_FENCE_PREFIX = "tianji-platform-release-activation-v1:";
    private static final String CLOCK = "SELECT CASE WHEN NOT pg_catalog.pg_is_in_recovery()"
            + " AND pg_catalog.current_setting('session_replication_role')='origin'";
    private static final String CLOCK_RESULT = " THEN pg_catalog.clock_timestamp() END";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate write;
    private final String activation;
    private final String candidate;
    private final String review;

    PlatformReleaseActivationStore(SpringProcessEngineConfiguration engine, ProcessReleaseService releases) {
        Objects.requireNonNull(engine);
        DataSource source = engine.getDataSource();
        if (source instanceof TransactionAwareDataSourceProxy proxy) source = proxy.getTargetDataSource();
        String schema = engine.getDatabaseSchema();
        if (!(engine.getTransactionManager() instanceof DataSourceTransactionManager manager)
                || schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")
                || source == null || manager.getDataSource() != source || !releases.usesStorage(engine, source, manager)) {
            throw new IllegalArgumentException("Platform activation requires the exact owning Process JDBC storage");
        }
        new ProcessApiSchemaVerifier(source, schema).verifyPlatformActivation();
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(5);
        write = new TransactionTemplate(manager);
        write.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        write.setTimeout(10);
        String namespace = "\"" + schema + "\".";
        activation = namespace + "tianji_platform_release_activation";
        candidate = namespace + "tianji_platform_release_candidate";
        review = namespace + "tianji_platform_release_review";
    }

    Instant now() {
        return databaseTime(CLOCK + CLOCK_RESULT);
    }

    Optional<ActivationReceipt> findCommand(Scope scope, ProcessActor actor, String commandId) {
        if (scope.tenantId() != actor.tenantId()) throw denied();
        return one("SELECT * FROM " + activation
                        + " WHERE tenant_id=? AND channel=? AND actor_type=? AND actor_issuer=? AND actor_subject=? AND command_id=?",
                scope.tenantId(), scope.channel(), actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), commandId);
    }

    Optional<ActivationReceipt> current(long tenantId, String channel) {
        if (tenantId <= 0 || channel == null || !channel.matches("[a-z][a-z0-9._-]{0,63}")) throw denied();
        return one("SELECT * FROM " + activation + " WHERE tenant_id=? AND channel=? ORDER BY revision DESC LIMIT 1",
                tenantId, channel);
    }

    ActivationReceipt append(ActivationReceipt proposal, AdmissionWindow window) {
        if (proposal.activatedAt() != null || proposal.previousCandidateSha256() != null) throw unavailable();
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Platform activation requires an independent transaction boundary");
        }
        return Objects.requireNonNull(write.execute(status -> appendInTransaction(proposal, window)));
    }

    private ActivationReceipt appendInTransaction(ActivationReceipt proposal, AdmissionWindow window) {
        var command = proposal.command();
        var scope = command.target();
        jdbc.execute("SET LOCAL statement_timeout='5s'");
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        // READ COMMITTED obtains a fresh snapshot for the following command and pointer statements.
        jdbc.query("SELECT pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(?, 0))",
                rs -> { }, TENANT_FENCE_PREFIX + scope.tenantId());
        requireCurrent(window);

        var existing = findCommand(scope, proposal.actor(), command.commandId());
        if (existing.isPresent()) {
            var prior = existing.orElseThrow();
            if (!command.equals(prior.command()) || !proposal.actor().equals(prior.actor())) throw new ProcessConflictException();
            requireCurrent(window);
            return prior;
        }

        var current = current(scope.tenantId(), scope.channel()).orElse(null);
        if (command.expectedRevision() != (current == null ? 0 : current.revision())) {
            throw new ProcessVersionConflictException();
        }
        String previous = current == null ? null : current.command().target().candidateSha256();
        if (command.action() == Action.ROLLBACK
                && (current == null || !scope.candidateSha256().equals(current.previousCandidateSha256()))) {
            throw new ProcessVersionConflictException();
        }
        if (current != null && (!current.processDefinitionKey().equals(proposal.processDefinitionKey())
                || scope.candidateSha256().equals(previous))) throw new ProcessConflictException();
        if (incompatibleRoute(proposal)) throw new ProcessConflictException();
        requireCurrent(window);
        var persisted = insertAccepted(proposal, previous);
        requireCurrent(window);
        return persisted;
    }

    private boolean incompatibleRoute(ActivationReceipt proposal) {
        long tenant = proposal.command().target().tenantId();
        String topic = proposal.workerTopic(), version = proposal.workerSchemaVersion(), digest = proposal.workerBindingSha256();
        String boundRoute = " FROM " + activation + " WHERE tenant_id=? AND worker_topic=? AND worker_schema_version=?";
        var conflict = jdbc.queryForObject("SELECT EXISTS (SELECT 1" + boundRoute
                        + " AND worker_binding_sha256 < CAST(? AS char(64))) OR EXISTS (SELECT 1" + boundRoute + " AND worker_binding_sha256 > CAST(? AS char(64)))",
                Boolean.class, tenant, topic, version, digest, tenant, topic, version, digest);
        if (conflict == null) throw unavailable();
        return conflict;
    }

    private ActivationReceipt insertAccepted(ActivationReceipt proposal, String previous) {
        var command = proposal.command();
        var scope = command.target();
        var actor = proposal.actor();
        return one("INSERT INTO " + activation + " (tenant_id,channel,revision,candidate_sha256,previous_candidate_sha256,"
                        + "action,actor_type,actor_issuer,actor_subject,command_id,process_release_version,process_manifest_sha256,"
                        + "process_definition_key,process_model_sha256,worker_topic,worker_schema_version,worker_binding_sha256,"
                        + "authorization_sha256,dependency_evidence_sha256,trust_generation,activated_at)"
                        + " SELECT " + "?,".repeat(20) + "pg_catalog.clock_timestamp() FROM " + review + " r JOIN " + candidate + " c"
                        + " USING (tenant_id,channel,candidate_sha256)"
                        + " WHERE r.tenant_id=? AND r.channel=? AND r.candidate_sha256=CAST(? AS char(64)) AND r.decision='ACCEPT'"
                        + " AND c.release_version=? AND c.manifest_sha256=CAST(? AS char(64)) AND c.trust_generation=? RETURNING *",
                scope.tenantId(), scope.channel(), proposal.revision(), scope.candidateSha256(), previous, command.action().name(),
                actor.subjectType().name(), actor.issuerFingerprint(), actor.subjectId(), command.commandId(),
                proposal.processReleaseVersion(), proposal.processManifestSha256(), proposal.processDefinitionKey(), proposal.processModelSha256(),
                proposal.workerTopic(), proposal.workerSchemaVersion(), proposal.workerBindingSha256(),
                proposal.authorizationSha256(), proposal.dependencyEvidenceSha256(), proposal.trustGeneration(),
                scope.tenantId(), scope.channel(), scope.candidateSha256(), proposal.processReleaseVersion(),
                proposal.processManifestSha256(), proposal.trustGeneration()).orElseThrow(PlatformReleaseActivationStore::denied);
    }

    private void requireCurrent(AdmissionWindow window) {
        var now = databaseTime(CLOCK + " AND pg_catalog.current_setting('transaction_isolation')='read committed'"
                + " AND pg_catalog.current_setting('transaction_read_only')='off'" + CLOCK_RESULT);
        if (!window.contains(now)) throw denied();
    }

    private Instant databaseTime(String sql) {
        Instant value = jdbc.queryForObject(sql, (rs, row) -> {
            Timestamp observed = rs.getTimestamp(1);
            return observed == null ? null : observed.toInstant();
        });
        if (value == null) throw unavailable();
        return value;
    }

    private Optional<ActivationReceipt> one(String sql, Object... args) {
        var rows = jdbc.query(sql, (RowMapper<ActivationReceipt>) PlatformReleaseActivationStore::read, args);
        if (rows.size() > 1) throw unavailable();
        return rows.stream().findFirst();
    }

    private static ActivationReceipt read(ResultSet rs, int row) throws SQLException {
        var scope = new Scope(rs.getLong("tenant_id"), rs.getString("channel"), rs.getString("candidate_sha256"));
        var actor = new ProcessActor(scope.tenantId(), ProcessActor.SubjectType.valueOf(rs.getString("actor_type")),
                rs.getString("actor_subject"), rs.getString("actor_issuer"));
        var command = new ChangeCommand(scope, rs.getString("command_id"), rs.getLong("revision") - 1,
                Action.valueOf(rs.getString("action")));
        return new ActivationReceipt(command, actor, rs.getString("previous_candidate_sha256"),
                rs.getString("process_release_version"), rs.getString("process_manifest_sha256"),
                rs.getString("process_definition_key"), rs.getString("process_model_sha256"), rs.getString("worker_topic"),
                rs.getString("worker_schema_version"), rs.getString("worker_binding_sha256"), rs.getString("authorization_sha256"),
                rs.getString("dependency_evidence_sha256"), rs.getLong("trust_generation"),
                rs.getTimestamp("activated_at").toInstant());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Current reviewed platform activation evidence is required");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Platform activation storage is unavailable");
    }
}
