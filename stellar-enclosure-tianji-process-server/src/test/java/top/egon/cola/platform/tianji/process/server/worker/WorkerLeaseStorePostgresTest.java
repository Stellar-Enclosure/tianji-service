package top.egon.cola.platform.tianji.process.server.worker;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.process.server.ProcessApiSchemaVerifier;
import top.egon.cola.platform.tianji.process.server.error.ProcessConflictException;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Uses only an explicitly supplied, empty loopback test database. */
@EnabledIfSystemProperty(named = "tianji.worker.postgres.test.url", matches = ".+")
@Timeout(90)
class WorkerLeaseStorePostgresTest {
    @Test
    void leases_are_database_timed_scoped_fenced_and_replayable() throws Exception {
        String url = System.getProperty("tianji.worker.postgres.test.url");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("integration tests require an explicit loopback tianji_it_ database");
        }
        var source = new DriverManagerDataSource(url);
        var jdbc = new JdbcTemplate(source);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='tianji_process'", Integer.class) != 0) {
            throw new IllegalStateException("integration tests require a fresh database without the process schema");
        }
        Flyway old = flyway(source, "3");
        assertEquals(3, old.migrate().migrationsExecuted);
        old.validate();
        List<Integer> checksums = jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history "
                + "WHERE version IN ('1','2','3') ORDER BY installed_rank", (rs, row) -> rs.getInt(1));
        for (long tenant : List.of(20L, 21L)) {
            jdbc.update("INSERT INTO tianji_process.tianji_process_release "
                            + "(tenant_id,release_key,state,manifest_sha256,publisher_type,publisher_id,created_at) "
                            + "VALUES (?,'release-one','ACTIVE',?,'SERVICE','legacy',clock_timestamp())", tenant, "a".repeat(64));
        }
        var verifier = new ProcessApiSchemaVerifier(source, "tianji_process");
        verifier.verify();
        assertThrows(IllegalStateException.class, verifier::verifyWorker);
        Flyway next = flyway(source, null);
        assertEquals(1, next.migrate().migrationsExecuted);
        next.validate();
        assertEquals(checksums, jdbc.query("SELECT checksum FROM tianji_process.tianji_process_schema_history "
                + "WHERE version IN ('1','2','3') ORDER BY installed_rank", (rs, row) -> rs.getInt(1)));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_process_release WHERE publisher_issuer='LEGACY'", Integer.class));
        verifier.verifyWorker();
        verifySchemaDrift(jdbc, verifier);

        var manager = new DataSourceTransactionManager(source);
        var store = new WorkerLeaseStore(source, manager, "tianji_process");
        assertThrows(IllegalArgumentException.class, () -> new WorkerLeaseStore(source,
                new DataSourceTransactionManager(new DriverManagerDataSource(url)), "tianji_process"));
        assertThrows(IllegalArgumentException.class, () -> WorkerLeaseStore.tokenHash("not-a-token"));
        assertThrows(IllegalArgumentException.class, () -> WorkerLeaseStore.tokenHash("A".repeat(42) + "B"));
        var transaction = new TransactionTemplate(manager);
        var actor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-a", "https://idp.example/20");
        var foreignIssuer = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-a", "https://other.example/20");
        var foreignSubject = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker-b", "https://idp.example/20");
        var foreignTenant = ProcessActor.verified(21, ProcessActor.SubjectType.SERVICE, "worker-a", "https://idp.example/21");
        var first = claim(actor, "job-one", 1, now(jdbc).plusSeconds(90), Duration.ofSeconds(30));
        assertThrows(IllegalStateException.class, () -> store.claim(first));
        assertThrows(IllegalArgumentException.class, () -> claim(new ProcessActor(20, ProcessActor.SubjectType.SERVICE, "worker-a"),
                "legacy-job", 2, now(jdbc).plusSeconds(30), Duration.ofSeconds(20)));
        assertThrows(IllegalArgumentException.class, () -> claim(ProcessActor.verified(20, ProcessActor.SubjectType.USER,
                "worker-a", "https://idp.example/20"), "user-job", 2, now(jdbc).plusSeconds(30), Duration.ofSeconds(20)));
        var lease = transaction.execute(status -> store.claim(first));
        assertNotNull(lease);
        assertEquals("LEASED", lease.state());
        assertTrue(lease.leaseExpiresAt().isAfter(lease.leasedAt()));
        assertFalse(lease.leaseExpiresAt().isAfter(lease.leasedAt().plusSeconds(30)));
        assertFalse(lease.leaseExpiresAt().isAfter(first.nativeExpiresAt()));
        Instant nativeLimit = now(jdbc).plusSeconds(10);
        var limited = claim(actor, "job-native-bound", 7, nativeLimit, Duration.ofSeconds(30));
        var limitedLease = transaction.execute(status -> store.claim(limited));
        assertEquals(nativeLimit, limitedLease.leaseExpiresAt());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("UPDATE tianji_process.tianji_worker_lease_receipt "
                        + "SET state='RETRY_SCHEDULED',result_sha256=?,decided_at=clock_timestamp(),failure_code='BAD' "
                        + "WHERE token_sha256=?", "f".repeat(64), limited.tokenSha256()));
        assertEquals("LEASED", transaction.execute(status -> store.lock(actor, limited.jobId(), limited.tokenSha256())).state());
        assertThrows(ProcessResourceNotFoundException.class, () -> transaction.execute(status -> store.lock(foreignIssuer, first.jobId(), first.tokenSha256())));
        assertThrows(ProcessResourceNotFoundException.class, () -> transaction.execute(status -> store.lock(foreignSubject, first.jobId(), first.tokenSha256())));
        assertThrows(ProcessResourceNotFoundException.class, () -> transaction.execute(status -> store.lock(foreignTenant, first.jobId(), first.tokenSha256())));

        var completed = new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, "c".repeat(64), "receipt-one", null, null);
        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> {
            store.finish(actor, first.jobId(), first.tokenSha256(), completed);
            throw new InjectedFailure();
        }));
        assertEquals("LEASED", transaction.execute(status -> store.lock(actor, first.jobId(), first.tokenSha256())).state());
        var receipt = transaction.execute(status -> store.finish(actor, first.jobId(), first.tokenSha256(), completed));
        assertEquals("COMPLETED", receipt.state());
        expire(jdbc, first.tokenSha256());
        var replay = transaction.execute(status -> store.finish(actor, first.jobId(), first.tokenSha256(), completed));
        assertEquals(receipt.decidedAt(), replay.decidedAt());
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.finish(actor, first.jobId(), first.tokenSha256(),
                new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, "d".repeat(64), "receipt-one", null, null))));
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.finish(actor, first.jobId(), first.tokenSha256(),
                new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.COMPLETED, "c".repeat(64), "different-receipt", null, null))));

        var stale = claim(actor, "job-stale", 3, now(jdbc).plusSeconds(90), Duration.ofSeconds(30));
        transaction.execute(status -> store.claim(stale));
        expire(jdbc, stale.tokenSha256());
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.finish(actor, stale.jobId(), stale.tokenSha256(), completed)));
        var replacement = claim(actor, "job-stale", 4, now(jdbc).plusSeconds(90), Duration.ofSeconds(30));
        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> { store.claim(replacement); throw new InjectedFailure(); }));
        assertEquals("LEASED", transaction.execute(status -> store.lock(actor, stale.jobId(), stale.tokenSha256())).state());
        transaction.execute(status -> store.claim(replacement));
        assertEquals("EXPIRED", transaction.execute(status -> store.lock(actor, stale.jobId(), stale.tokenSha256())).state());
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.finish(actor, stale.jobId(), stale.tokenSha256(), completed)));
        var manual = new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.NEEDS_MANUAL, "e".repeat(64), null,
                WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN");
        assertEquals("NEEDS_MANUAL", transaction.execute(status -> store.finish(actor, replacement.jobId(), replacement.tokenSha256(), manual)).state());
        assertThrows(IllegalArgumentException.class, () -> new WorkerLeaseStore.Finish(WorkerReceiptOutcomeEnum.RETRY_SCHEDULED,
                "f".repeat(64), null, WorkerFailureKindEnum.UNKNOWN, "OUTCOME_UNKNOWN"));

        var rolledBack = claim(actor, "job-rollback", 5, now(jdbc).plusSeconds(90), Duration.ofSeconds(30));
        assertThrows(InjectedFailure.class, () -> transaction.execute(status -> { store.claim(rolledBack); throw new InjectedFailure(); }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_worker_lease_receipt WHERE job_id='job-rollback'", Integer.class));
        var tooLate = claim(actor, "job-late", 6, now(jdbc).minusSeconds(1), Duration.ofSeconds(30));
        assertThrows(ProcessConflictException.class, () -> transaction.execute(status -> store.claim(tooLate)));
        verifyConcurrentClaims(store, manager, actor, jdbc);
        var otherTenantClaim = claim(foreignTenant, "job-race", 30, now(jdbc).plusSeconds(90), Duration.ofSeconds(30));
        assertEquals(21, transaction.execute(status -> store.claim(otherTenantClaim)).tenantId());
        var boundedActor = ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "bounded-worker", "https://idp.example/20");
        Instant nativeExpiry = now(jdbc).plusSeconds(90);
        transaction.executeWithoutResult(status -> {
            for (int number = 40; number < 72; number++) {
                assertTrue(store.hasCapacity(boundedActor, "riskCheck", "1", 32));
                store.claim(claim(boundedActor, "bounded-job-" + number, number, nativeExpiry, Duration.ofSeconds(30)));
            }
            assertFalse(store.hasCapacity(boundedActor, "riskCheck", "1", 32));
            assertFalse(store.hasCapacity(boundedActor, "otherTopic", "1", 32));
        });
    }

    private static void verifySchemaDrift(JdbcTemplate jdbc, ProcessApiSchemaVerifier verifier) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        transaction.executeWithoutResult(status -> {
            jdbc.update("UPDATE tianji_process.tianji_process_schema_history SET checksum=0 WHERE version='4'");
            verifier.verify();
            assertThrows(IllegalStateException.class, verifier::verifyWorker);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            jdbc.execute("ALTER TABLE tianji_process.tianji_worker_lease_receipt DROP CONSTRAINT tianji_worker_lease_state_fields_check");
            jdbc.execute("ALTER TABLE tianji_process.tianji_worker_lease_receipt ADD CONSTRAINT tianji_worker_lease_state_fields_check CHECK (true)");
            assertThrows(IllegalStateException.class, verifier::verifyWorker);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            jdbc.execute("DROP INDEX tianji_process.tianji_worker_lease_active_job_idx");
            assertThrows(IllegalStateException.class, verifier::verifyWorker);
            status.setRollbackOnly();
        });
        verifier.verifyWorker();
    }

    private static void verifyConcurrentClaims(WorkerLeaseStore store, DataSourceTransactionManager manager,
                                               ProcessActor actor, JdbcTemplate jdbc) throws Exception {
        Instant nativeExpiry = now(jdbc).plusSeconds(90);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = List.of(10, 11).stream().map(number -> pool.submit(() -> {
                start.await();
                try {
                    new TransactionTemplate(manager).execute(status -> store.claim(claim(actor, "job-race", number, nativeExpiry, Duration.ofSeconds(30))));
                    return true;
                } catch (ProcessConflictException conflict) { return false; }
            })).toList();
            start.countDown();
            int winners = 0;
            for (Future<Boolean> result : results) if (result.get(15, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM tianji_process.tianji_worker_lease_receipt "
                + "WHERE tenant_id=20 AND job_id='job-race' AND state='LEASED'", Integer.class));
    }

    private static WorkerLeaseStore.Claim claim(ProcessActor actor, String job, int seed, Instant nativeExpiry, Duration duration) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) seed;
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = WorkerLeaseStore.tokenHash(token);
        return new WorkerLeaseStore.Claim(actor, hash, "riskCheck", "1", job, job, WorkerScopeTypeEnum.BPMN, "instance-1",
                "release-one", "definition-1", "worker", "intent-1", "a".repeat(64), "b".repeat(64),
                "tianji-" + hash, nativeExpiry, duration);
    }

    private static Instant now(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    private static void expire(JdbcTemplate jdbc, String hash) {
        assertEquals(1, jdbc.update("UPDATE tianji_process.tianji_worker_lease_receipt SET leased_at=clock_timestamp()-interval '5 seconds', "
                + "lease_expires_at=clock_timestamp()-interval '1 second' WHERE token_sha256=?", hash));
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        var config = Flyway.configure().dataSource(source).schemas("tianji_process").defaultSchema("tianji_process")
                .table("tianji_process_schema_history").locations("classpath:db/tianji-process").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }

    private static final class InjectedFailure extends RuntimeException { }
}
