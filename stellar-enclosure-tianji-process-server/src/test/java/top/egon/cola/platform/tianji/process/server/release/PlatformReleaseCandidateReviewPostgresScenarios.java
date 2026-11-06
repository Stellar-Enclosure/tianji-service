package top.egon.cola.platform.tianji.process.server.release;

import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessVersionConflictException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.SERVICE;
import static top.egon.cola.platform.tianji.process.server.identity.ProcessActor.SubjectType.USER;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Action;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Authorization;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Decision;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Grant;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewCommand;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.ReviewReceipt;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.Scope;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.SignedMaterial;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.TrustLease;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseCandidateReviewService.TrustReference;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.CANDIDATE_PIN;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.SIDECAR;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST;
import static top.egon.cola.platform.tianji.process.server.release.PlatformReleaseSignatureVerifierTest.TRUST_PIN;

/** Package access to the frozen public vectors. The operator test owns database and role safety. */
public final class PlatformReleaseCandidateReviewPostgresScenarios {
    private static final String SCHEMA = "tianji_process";
    private static final String CANDIDATE_TABLE = SCHEMA + ".tianji_platform_release_candidate";
    private static final String REVIEW_TABLE = SCHEMA + ".tianji_platform_release_review";
    private static final Scope SCOPE = new Scope(20, "expense", CANDIDATE_PIN);
    private static final String RELEASE = "expense-v1";
    private static final String ISSUER = "https://process.example.test";
    private static final ProcessActor SUBMITTER = ProcessActor.verified(20, SERVICE, "publisher", ISSUER);
    private static final ProcessActor SIGNER = ProcessActor.verified(20, USER, "signer", ISSUER);
    private static final ProcessActor FIRST = ProcessActor.verified(20, USER, "reviewer", ISSUER);
    private static final ProcessActor SECOND = ProcessActor.verified(20, USER, "reviewer", "https://second.example.test");
    private static final ReviewCommand ACCEPT = new ReviewCommand("review-1", 0, Decision.ACCEPT);
    private static final ReviewCommand REJECT = new ReviewCommand("review-1", 0, Decision.REJECT);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PlatformReleaseCandidateReviewPostgresScenarios() { }

    public static void verify(JdbcTemplate owner, DataSource runtimeSource) throws Exception {
        var runtime = new JdbcTemplate(runtimeSource);
        runtime.setQueryTimeout(3);
        var proof = PlatformReleaseSignatureVerifier.verifyBytes(CANDIDATE.getBytes(UTF_8), SIDECAR.getBytes(UTF_8),
                TRUST.getBytes(UTF_8), CANDIDATE_PIN, TRUST_PIN, SCOPE.tenantId(), SCOPE.channel(),
                Clock.fixed(databaseNow(runtime), ZoneOffset.UTC));
        assertEquals(RELEASE, proof.releaseVersion());
        insertFixtureRelease(owner, proof);
        var authorities = new FixtureAuthorities(runtime, proof.keyId());
        var service = service(runtimeSource, authorities);
        var material = new SignedMaterial(CANDIDATE.getBytes(UTF_8), SIDECAR.getBytes(UTF_8));

        var submitted = service.submit(SUBMITTER, SCOPE, material);
        assertEquals(proof.manifestSha(), submitted.manifestSha256());
        assertEquals(proof.envelopeSha(), submitted.envelopeSha256());
        assertEquals(SIGNER, submitted.trust().signer());
        assertEquals(SUBMITTER, submitted.submittedBy());
        assertEquals(1, count(owner, CANDIDATE_TABLE));

        try (var executor = Executors.newFixedThreadPool(2)) {
            authorities.expiringRead.set(true);
            Future<ReviewReceipt> expired;
            try (var blocker = lockReviewInShareMode(owner)) {
                expired = executor.submit(() -> service.review(FIRST, SCOPE, ACCEPT));
                assertEquals(1, awaitBlockedWriters(owner, 1, List.of(expired)).size());
                Instant deadline = authorities.expiringReadDeadline.get(1, TimeUnit.SECONDS);
                awaitDatabaseDeadline(owner, deadline);
                blocker.commit();
            }
            var expiredFailure = assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> expired.get(7, TimeUnit.SECONDS));
            assertTrue(expiredFailure.getCause() instanceof AccessDeniedException);
            assertEquals("Candidate review authority is outside its admitted window", expiredFailure.getCause().getMessage());
            assertEquals(0, count(owner, REVIEW_TABLE), "expired post-insert transaction must roll back");
            assertEquals(1, count(owner, CANDIDATE_TABLE));
            authorities.expiringRead.set(false);

            Future<ReviewOutcome> first;
            Future<ReviewOutcome> second;
            try (var blocker = lockReviewInShareMode(owner)) {
                first = executor.submit(() -> review(service, FIRST, ACCEPT));
                second = executor.submit(() -> review(service, SECOND, REJECT));
                assertEquals(2, awaitBlockedWriters(owner, 2, List.of(first, second)).size(),
                        "two distinct backend connections reached INSERT before either could commit");
                blocker.commit();
            }
            var outcomes = List.of(first.get(7, TimeUnit.SECONDS), second.get(7, TimeUnit.SECONDS));
            var committed = outcomes.stream().filter(outcome -> outcome.receipt() != null).toList();
            assertEquals(1, committed.size());
            assertEquals(1, outcomes.stream().filter(ReviewOutcome::versionConflict).count());
            var winner = committed.getFirst().receipt();
            assertEquals(1, winner.revision());
            assertEquals(1, count(owner, REVIEW_TABLE));
            assertEquals(winner.reviewer().equals(FIRST) ? ACCEPT : REJECT, winner.command());

            authorities.trustEnabled.set(false);
            assertEquals(winner, service.review(winner.reviewer(), SCOPE, winner.command()));
            var changed = new ReviewCommand(winner.command().commandId(), 0,
                    winner.command().decision() == Decision.ACCEPT ? Decision.REJECT : Decision.ACCEPT);
            assertThrows(ProcessConflictException.class, () -> service.review(winner.reviewer(), SCOPE, changed));
            authorities.readEnabled.set(false);
            assertThrows(AccessDeniedException.class, () -> service.review(winner.reviewer(), SCOPE, winner.command()));
            authorities.readEnabled.set(true);
            var historical = service.get(winner.reviewer(), SCOPE);
            assertEquals(submitted, historical.submission());
            assertEquals(winner, historical.review().orElseThrow());
            assertArrayEquals(material.envelope(), historical.material().envelope());
            assertArrayEquals(material.sidecar(), historical.material().sidecar());
            assertThrows(AccessDeniedException.class,
                    () -> service.get(winner.reviewer(), new Scope(SCOPE.tenantId(), "other", SCOPE.candidateSha256())));
        }

        assertSqlState(runtime, "23503", """
                INSERT INTO tianji_process.tianji_platform_release_review
                (tenant_id,channel,candidate_sha256,reviewer_issuer,reviewer_subject,command_id,decision,
                 review_auth_sha256,independence_auth_sha256,owner_read_auth_sha256,owner_observed_at,decided_at)
                SELECT tenant_id,'other',candidate_sha256,reviewer_issuer,reviewer_subject,command_id,decision,
                       review_auth_sha256,independence_auth_sha256,owner_read_auth_sha256,owner_observed_at,decided_at
                FROM tianji_process.tianji_platform_release_review
                """);
        for (String table : List.of(CANDIDATE_TABLE, REVIEW_TABLE)) {
            assertSqlState(runtime, "42501", "UPDATE " + table + " SET candidate_sha256=candidate_sha256");
            assertSqlState(runtime, "42501", "DELETE FROM " + table);
            assertSqlState(runtime, "42501", "TRUNCATE " + table);
            assertEquals(1, count(owner, table));
        }
    }

    private static PlatformReleaseCandidateReviewService service(DataSource source, FixtureAuthorities authorities) {
        var manager = new DataSourceTransactionManager(source);
        // Only engine configuration affinity is simulated. The release query, store and transaction manager use actual PostgreSQL.
        var engine = mock(SpringProcessEngineConfiguration.class);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(manager);
        when(engine.getDatabaseSchema()).thenReturn(SCHEMA);
        when(engine.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(engine.getBeans()).thenReturn(Map.of());
        var dmn = mock(SpringDmnEngineConfiguration.class);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDatabaseSchema()).thenReturn(SCHEMA);
        when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(dmn.getBeans()).thenReturn(Map.of());
        var releaseReader = new ProcessReleaseService(engine, dmn, new ProcessModelValidator());
        return new PlatformReleaseCandidateReviewService(engine, releaseReader, authorities::trust, authorities::grant);
    }

    private static void insertFixtureRelease(JdbcTemplate owner, PlatformReleaseSignatureVerifier.VerifiedCandidate proof) {
        // Seed only the exact verified owner catalog tuple. Engine definitions are not deployed by this storage test.
        assertEquals(1, owner.update("""
                INSERT INTO tianji_process.tianji_process_release
                (tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,publisher_issuer,created_at)
                VALUES (?,?,'ACTIVE',?,'SERVICE',?,?,pg_catalog.clock_timestamp())
                """, SCOPE.tenantId(), RELEASE, proof.manifestSha(), SUBMITTER.subjectId(), SUBMITTER.issuerFingerprint()));
        for (var model : proof.processModels()) {
            assertEquals(1, owner.update("""
                    INSERT INTO tianji_process.tianji_process_release_definition
                    (tenant_id,release_key,model_type,definition_key,engine_definition_id,engine_deployment_id,
                     engine_version,model_sha256,input_schema)
                    VALUES (?,?,?,?,?,?,1,?,CAST(? AS jsonb))
                    """, SCOPE.tenantId(), RELEASE, model.type().name(), model.definitionKey(),
                    "fixture-definition-" + model.definitionKey(), "fixture-deployment-" + model.definitionKey(),
                    model.contentSha256(), JSON.writeValueAsString(model.inputs())));
        }
    }

    private static ReviewOutcome review(PlatformReleaseCandidateReviewService service, ProcessActor actor, ReviewCommand command) {
        try {
            return new ReviewOutcome(service.review(actor, SCOPE, command), false);
        } catch (ProcessVersionConflictException expected) {
            return new ReviewOutcome(null, true);
        }
    }

    private static Connection lockReviewInShareMode(JdbcTemplate owner) throws SQLException {
        var source = owner.getDataSource();
        assertNotNull(source);
        Connection connection = source.getConnection();
        try {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(2);
                statement.execute("LOCK TABLE " + REVIEW_TABLE + " IN SHARE MODE");
            }
            return connection;
        } catch (SQLException failure) {
            try {
                connection.close();
            } catch (SQLException closing) {
                failure.addSuppressed(closing);
            }
            throw failure;
        }
    }

    private static List<Integer> awaitBlockedWriters(JdbcTemplate owner, int expected, List<? extends Future<?>> workers)
            throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        do {
            var waiters = owner.queryForList("""
                    SELECT DISTINCT pid FROM pg_catalog.pg_locks
                    WHERE locktype='relation' AND relation=CAST(? AS regclass)
                      AND database=(SELECT oid FROM pg_catalog.pg_database WHERE datname=current_database())
                      AND mode='RowExclusiveLock' AND NOT granted
                    """, Integer.class, REVIEW_TABLE);
            if (waiters.size() == expected) return waiters;
            if (workers.stream().anyMatch(Future::isDone)) throw new AssertionError("review finished before reaching the blocked insert");
            Thread.sleep(10);
        } while (System.nanoTime() < limit);
        throw new AssertionError("expected independently blocked review INSERT connections");
    }

    private static void awaitDatabaseDeadline(JdbcTemplate owner, Instant deadline) throws InterruptedException {
        long limit = System.nanoTime() + Duration.ofSeconds(4).toNanos();
        while (databaseNow(owner).isBefore(deadline)) {
            if (System.nanoTime() >= limit) throw new AssertionError("database clock did not reach the fixture deadline");
            Thread.sleep(10);
        }
    }

    private static Instant databaseNow(JdbcTemplate jdbc) {
        var value = jdbc.queryForObject("SELECT pg_catalog.clock_timestamp()", OffsetDateTime.class);
        assertNotNull(value);
        return value.toInstant();
    }

    private static int count(JdbcTemplate jdbc, String table) {
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        assertNotNull(rows);
        return rows;
    }

    private static void assertSqlState(JdbcTemplate runtime, String sqlState, String sql) {
        var failure = assertThrows(DataAccessException.class, () -> runtime.execute(sql));
        assertTrue(failure.getMostSpecificCause() instanceof SQLException);
        assertEquals(sqlState, ((SQLException) failure.getMostSpecificCause()).getSQLState());
    }

    private record ReviewOutcome(ReviewReceipt receipt, boolean versionConflict) { }

    private static final class FixtureAuthorities {
        private final JdbcTemplate runtime;
        private final String keyId;
        private final AtomicBoolean trustEnabled = new AtomicBoolean(true);
        private final AtomicBoolean readEnabled = new AtomicBoolean(true);
        private final AtomicBoolean expiringRead = new AtomicBoolean();
        private final CompletableFuture<Instant> expiringReadDeadline = new CompletableFuture<>();

        private FixtureAuthorities(JdbcTemplate runtime, String keyId) {
            this.runtime = runtime;
            this.keyId = keyId;
        }

        private TrustLease trust(Scope scope) {
            if (!trustEnabled.get() || !SCOPE.equals(scope)) return null;
            Instant now = databaseNow(runtime);
            return new TrustLease(scope, new TrustReference(1, TRUST_PIN, keyId, SIGNER),
                    TRUST.getBytes(UTF_8), now, now.plusSeconds(30));
        }

        private Grant grant(Authorization request) {
            if (!SCOPE.equals(request.scope())) return null;
            var actor = request.actor();
            boolean reviewer = FIRST.equals(actor) || SECOND.equals(actor);
            boolean allowed = switch (request.action()) {
                case SUBMIT -> SUBMITTER.equals(actor);
                case REVIEW -> reviewer;
                case READ_CANDIDATE -> readEnabled.get() && (reviewer || SUBMITTER.equals(actor));
                case READ_PROCESS_RELEASE -> RELEASE.equals(request.releaseVersion()) && (reviewer || SUBMITTER.equals(actor));
                case REVIEW_INDEPENDENT -> reviewer && RELEASE.equals(request.releaseVersion())
                        && SIGNER.equals(request.signer()) && SUBMITTER.equals(request.submitter());
            };
            if (!allowed) return null;
            Instant now = databaseNow(runtime);
            boolean expiring = expiringRead.get() && FIRST.equals(actor) && request.action() == Action.READ_PROCESS_RELEASE;
            Instant deadline = now.plusSeconds(expiring ? 3 : 30);
            if (expiring) expiringReadDeadline.complete(deadline);
            String audit = PlatformReleaseArtifactValidator.hash((actor.issuerFingerprint() + ":" + actor.subjectId()
                    + ":" + request.action().name()).getBytes(UTF_8));
            return new Grant(request, audit, now, deadline);
        }
    }
}
