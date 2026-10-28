package top.egon.cola.platform.tianji.worker;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;

import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated loopback database and a role without schema creation privileges. */
@EnabledIfSystemProperty(named = "tianji.worker.nontransactional.postgres.test.url", matches = ".+")
@Timeout(120)
class NonTransactionalWorkerPostgresTest {
    @Test void durable_intents_fence_retries_and_preserve_the_external_idempotency_contract() throws Exception {
        String url = System.getProperty("tianji.worker.nontransactional.postgres.test.url");
        String user = System.getProperty("tianji.worker.business.postgres.test.runtime-user", "");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !user.matches("tianji_it_worker_[0-9a-f]{12}")) throw new IllegalArgumentException("fresh loopback database and temporary role required");
        var bootstrapSource = new DriverManagerDataSource(url); var jdbc = new JdbcTemplate(bootstrapSource);
        if (jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='business'", Integer.class) != 0) {
            throw new IllegalStateException("test database already contains the business schema");
        }
        jdbc.execute("CREATE SCHEMA business");
        var old = flyway(bootstrapSource, "1");
        assertEquals(1, old.migrate().migrationsExecuted); old.validate();
        Integer oldChecksum = jdbc.queryForObject("SELECT checksum FROM business.tianji_worker_schema_history WHERE version='1'", Integer.class);
        var ownerVerifier = new WorkerExecutionSchemaVerifier(bootstrapSource, "business");
        ownerVerifier.verify(); assertThrows(IllegalStateException.class, ownerVerifier::verifyNonTransactional);
        var current = flyway(bootstrapSource, null);
        assertEquals(1, current.migrate().migrationsExecuted); current.validate();
        assertEquals(oldChecksum, jdbc.queryForObject("SELECT checksum FROM business.tianji_worker_schema_history WHERE version='1'", Integer.class));
        jdbc.execute("GRANT USAGE ON SCHEMA business TO \"" + user + "\"");
        jdbc.execute("GRANT SELECT,INSERT ON business.tianji_worker_execution_receipt TO \"" + user + "\"");
        jdbc.execute("GRANT SELECT,INSERT ON business.tianji_worker_nontransactional_intent TO \"" + user + "\"");
        jdbc.execute("GRANT SELECT ON business.tianji_worker_schema_history TO \"" + user + "\"");
        assertEquals(Boolean.FALSE, jdbc.queryForObject("SELECT has_schema_privilege(?, 'business', 'CREATE')", Boolean.class, user));
        var source = new DriverManagerDataSource(url + "?currentSchema=business"); source.setUsername(user);
        var verifier = new WorkerExecutionSchemaVerifier(source, "business");
        verifier.verifyNonTransactional();
        jdbc.execute("REVOKE INSERT ON business.tianji_worker_nontransactional_intent FROM \"" + user + "\"");
        verifier.verifyIntentReadable(); assertThrows(IllegalStateException.class, verifier::verifyNonTransactional);
        jdbc.execute("GRANT INSERT ON business.tianji_worker_nontransactional_intent TO \"" + user + "\"");
        verifySchemaDrift(bootstrapSource);
        var manager = new DataSourceTransactionManager(source);
        jdbc.execute("REVOKE SELECT ON business.tianji_worker_nontransactional_intent FROM \"" + user + "\"");
        var missingRead = new JdbcWorkerExecutionStore(source, manager, "business", "process-east", 20,
                Duration.ofSeconds(10), Duration.ofSeconds(1));
        var local = task(20, "job-no-read", "no-read", 10, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, now(jdbc), 16);
        var calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> missingRead.execute(local, new WorkerHandlerDescriptor(local.contract(), "v1"),
                (context, operations) -> { calls.incrementAndGet(); return result(1); }));
        assertEquals(0, calls.get());
        jdbc.execute("GRANT SELECT ON business.tianji_worker_nontransactional_intent TO \"" + user + "\"");
        var store = new JdbcWorkerExecutionStore(source, manager, "business", "process-east", 20,
                Duration.ofSeconds(10), Duration.ofSeconds(1));
        store.requireNonTransactional();
        verifyLostExternalResponse(store, manager, source, jdbc);
        verifyConcurrentExternalCalls(store, jdbc);
        verifyReadOnlyCanonicalResult(store, jdbc);
        verifyDeadlineAndReceiptFailure(store, jdbc);
        verifySourceAndTenantIsolation(store, source, manager, jdbc);
    }

    private static void verifyLostExternalResponse(JdbcWorkerExecutionStore store, DataSourceTransactionManager manager,
                                                    DriverManagerDataSource source, JdbcTemplate jdbc) {
        var remote = new ConcurrentHashMap<String, WorkerBusinessResultDTO>(); var effects = new AtomicInteger();
        var invocations = new AtomicInteger(); var first = new AtomicBoolean(true); var observedKey = new AtomicReference<String>();
        var initial = task(20, "job-lost", "lost", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 1);
        var v1 = new WorkerHandlerDescriptor(initial.contract(), "handler-v1");
        NonTransactionalWorkerHandler handler = (context, key) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, intentCount(jdbc, "process-east", 20, "lost"));
            assertFalse(key.contains(context.idempotencyKey()));
            observedKey.compareAndSet(null, key); assertEquals(observedKey.get(), key);
            invocations.incrementAndGet();
            var result = remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet()));
            if (first.getAndSet(false)) throw new WorkerHandlerFailureException(WorkerFailureKindEnum.RETRYABLE, "REMOTE_RESPONSE_LOST");
            return result;
        };
        assertThrows(WorkerHandlerFailureException.class, () -> store.executeNonTransactional(initial, v1, "orders-v1", handler));
        assertEquals(1, effects.get()); assertEquals(1, intentCount(jdbc, "process-east", 20, "lost"));
        assertEquals(0, receiptCount(jdbc, "process-east", 20, "lost"));
        var next = task(20, "job-lost", "lost", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 2);
        var v2 = new WorkerHandlerDescriptor(next.contract(), "handler-v2");
        assertThrows(WorkerExecutionConflictException.class, () -> store.executeNonTransactional(next, v2, "orders-v1", handler));
        assertThrows(WorkerExecutionConflictException.class, () -> store.executeNonTransactional(next, v1, "orders-v2", handler));
        var changed = task(20, "job-lost", "lost", 11, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 3);
        assertThrows(WorkerExecutionConflictException.class, () -> store.executeNonTransactional(changed, v1, "orders-v1", handler));
        var transactional = task(20, "job-lost", "lost", 10, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, now(jdbc), 4);
        var transactionalCalls = new AtomicInteger();
        assertThrows(WorkerExecutionConflictException.class, () -> store.execute(transactional,
                new WorkerHandlerDescriptor(transactional.contract(), "handler-v1"), (context, operations) -> {
                    transactionalCalls.incrementAndGet(); return result(1);
                }));
        assertEquals(0, transactionalCalls.get()); assertEquals(1, invocations.get());
        var success = store.executeNonTransactional(next, v1, "orders-v1", handler);
        assertFalse(success.replayed()); assertEquals(1, effects.get()); assertEquals(2, invocations.get());
        assertEquals(1L, success.result().output().get("sequence"));
        var replay = store.executeNonTransactional(next, v2, "orders-v1", (context, key) -> { throw new AssertionError("must replay"); });
        assertTrue(replay.replayed()); assertEquals("handler-v1", replay.handlerVersion());
        assertEquals(success.result(), replay.result());
        assertThrows(WorkerExecutionConflictException.class, () -> store.executeNonTransactional(next, v2, "orders-v2", handler));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status ->
                store.executeNonTransactional(next, v1, "orders-v1", handler)));
        assertFalse(TransactionSynchronizationManager.hasResource(source));
        var localFirst = task(20, "job-local-first", "local-first", 10, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT, now(jdbc), 14);
        store.execute(localFirst, new WorkerHandlerDescriptor(localFirst.contract(), "local-v1"), (context, operations) -> result(1));
        var conflictingRemote = task(20, "job-local-first", "local-first", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 15);
        assertThrows(WorkerExecutionConflictException.class, () -> store.executeNonTransactional(conflictingRemote,
                new WorkerHandlerDescriptor(conflictingRemote.contract(), "remote-v1"), "orders-v1", handler));
        assertEquals(0, intentCount(jdbc, "process-east", 20, "local-first"));
    }

    private static void verifyConcurrentExternalCalls(JdbcWorkerExecutionStore store, JdbcTemplate jdbc) throws Exception {
        var task = task(20, "job-concurrent", "concurrent", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 5);
        var descriptor = new WorkerHandlerDescriptor(task.contract(), "v1");
        var remote = new ConcurrentHashMap<String, WorkerBusinessResultDTO>(); var effects = new AtomicInteger();
        var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        NonTransactionalWorkerHandler handler = (context, key) -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet()));
        };
        List<WorkerExecutionReceipt> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<WorkerExecutionReceipt>> futures = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> store.executeNonTransactional(task, descriptor, "orders-v1", handler))).toList();
            try { assertTrue(entered.await(10, TimeUnit.SECONDS)); } finally { release.countDown(); }
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, effects.get());
        assertEquals(1, results.stream().filter(value -> !value.replayed()).count());
        assertTrue(results.stream().allMatch(value -> value.result().equals(results.getFirst().result())));
        assertEquals(1, receiptCount(jdbc, "process-east", 20, "concurrent"));

        var differing = task(20, "job-different", "different", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 6);
        var differingDescriptor = new WorkerHandlerDescriptor(differing.contract(), "v1");
        var both = new CountDownLatch(2); var proceed = new CountDownLatch(1); var sequence = new AtomicInteger();
        NonTransactionalWorkerHandler inconsistent = (context, key) -> {
            int value = sequence.incrementAndGet(); both.countDown();
            try { assertTrue(proceed.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return result(value);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> store.executeNonTransactional(differing, differingDescriptor, "orders-v1", inconsistent));
            var two = executor.submit(() -> store.executeNonTransactional(differing, differingDescriptor, "orders-v1", inconsistent));
            try { assertTrue(both.await(10, TimeUnit.SECONDS)); } finally { proceed.countDown(); }
            int failures = 0;
            for (var future : List.of(one, two)) {
                try { future.get(20, TimeUnit.SECONDS); }
                catch (ExecutionException failure) { assertInstanceOf(WorkerExecutionConflictException.class, failure.getCause()); failures++; }
            }
            assertEquals(1, failures);
        }
    }

    private static void verifyReadOnlyCanonicalResult(JdbcWorkerExecutionStore store, JdbcTemplate jdbc) throws Exception {
        var task = task(20, "job-read", "read", 10, WorkerEffectSafetyEnum.READ_ONLY, now(jdbc), 7);
        var descriptor = new WorkerHandlerDescriptor(task.contract(), "read-v1");
        var both = new CountDownLatch(2); var proceed = new CountDownLatch(1); var sequence = new AtomicInteger();
        NonTransactionalWorkerHandler handler = (context, key) -> {
            int value = sequence.incrementAndGet(); both.countDown();
            try { assertTrue(proceed.await(10, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return result(value);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> store.executeNonTransactional(task, descriptor, "catalog-v1", handler));
            var two = executor.submit(() -> store.executeNonTransactional(task, descriptor, "catalog-v1", handler));
            try { assertTrue(both.await(10, TimeUnit.SECONDS)); } finally { proceed.countDown(); }
            var first = one.get(20, TimeUnit.SECONDS); var second = two.get(20, TimeUnit.SECONDS);
            assertNotEquals(first.replayed(), second.replayed()); assertEquals(first.result(), second.result());
        }
    }

    private static void verifyDeadlineAndReceiptFailure(JdbcWorkerExecutionStore store, JdbcTemplate jdbc) {
        var now = now(jdbc);
        var shortLease = taskWithTimes(20, "job-slow", "slow", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT,
                now, now.plusMillis(2200), 8);
        var descriptor = new WorkerHandlerDescriptor(shortLease.contract(), "v1");
        var effects = new AtomicInteger(); var remote = new ConcurrentHashMap<String, WorkerBusinessResultDTO>();
        NonTransactionalWorkerHandler slow = (context, key) -> {
            var value = remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet()));
            try { Thread.sleep(1500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
            return value;
        };
        assertThrows(WorkerExecutionDeadlineException.class, () -> store.executeNonTransactional(shortLease, descriptor, "orders-v1", slow));
        assertEquals(1, effects.get()); assertEquals(0, receiptCount(jdbc, "process-east", 20, "slow"));
        var retried = task(20, "job-slow", "slow", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 9);
        assertFalse(store.executeNonTransactional(retried, descriptor, "orders-v1",
                (context, key) -> remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet()))).replayed());
        assertEquals(1, effects.get());

        jdbc.execute("""
                CREATE FUNCTION business.test_nontransactional_receipt_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.idempotency_key='receipt-fail' THEN RAISE EXCEPTION 'injected'; END IF; RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER test_nontransactional_receipt_failure BEFORE INSERT ON business.tianji_worker_execution_receipt "
                + "FOR EACH ROW EXECUTE FUNCTION business.test_nontransactional_receipt_failure()");
        var fail = task(20, "job-receipt", "receipt-fail", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 10);
        var failDescriptor = new WorkerHandlerDescriptor(fail.contract(), "v1");
        assertThrows(org.springframework.dao.DataAccessException.class, () -> store.executeNonTransactional(fail, failDescriptor, "orders-v1",
                (context, key) -> remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet()))));
        assertEquals(1, intentCount(jdbc, "process-east", 20, "receipt-fail"));
        assertEquals(0, receiptCount(jdbc, "process-east", 20, "receipt-fail"));
        jdbc.execute("DROP TRIGGER test_nontransactional_receipt_failure ON business.tianji_worker_execution_receipt");
        var failRetry = task(20, "job-receipt", "receipt-fail", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 11);
        int before = effects.get();
        store.executeNonTransactional(failRetry, failDescriptor, "orders-v1",
                (context, key) -> remote.computeIfAbsent(key, ignored -> result(effects.incrementAndGet())));
        assertEquals(before, effects.get());
    }

    private static void verifySourceAndTenantIsolation(JdbcWorkerExecutionStore east, DriverManagerDataSource source,
                                                        DataSourceTransactionManager manager, JdbcTemplate jdbc) {
        var west = new JdbcWorkerExecutionStore(source, manager, "business", "process-west", 20, Duration.ofSeconds(10), Duration.ofSeconds(1));
        var otherTenant = new JdbcWorkerExecutionStore(source, manager, "business", "process-east", 21, Duration.ofSeconds(10), Duration.ofSeconds(1));
        var keys = ConcurrentHashMap.<String>newKeySet();
        NonTransactionalWorkerHandler handler = (context, key) -> { assertTrue(keys.add(key)); return result(1); };
        var task20 = task(20, "job-isolated", "isolated", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 12);
        var task21 = task(21, "job-isolated", "isolated", 10, WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT, now(jdbc), 13);
        var descriptor = new WorkerHandlerDescriptor(task20.contract(), "v1");
        east.executeNonTransactional(task20, descriptor, "orders-v1", handler);
        west.executeNonTransactional(task20, descriptor, "orders-v1", handler);
        otherTenant.executeNonTransactional(task21, descriptor, "orders-v1", handler);
        assertEquals(3, keys.size());
    }

    private static void verifySchemaDrift(DriverManagerDataSource source) {
        var jdbc = new JdbcTemplate(source); var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        for (String change : List.of("UPDATE business.tianji_worker_schema_history SET checksum=0 WHERE version='2'",
                "ALTER TABLE business.tianji_worker_nontransactional_intent DROP CONSTRAINT tianji_worker_nontransactional_intent_pkey")) {
            transaction.executeWithoutResult(status -> {
                jdbc.execute(change);
                assertThrows(IllegalStateException.class, () -> new WorkerExecutionSchemaVerifier(source, "business").verifyNonTransactional());
                status.setRollbackOnly();
            });
        }
    }

    private static Flyway flyway(DriverManagerDataSource source, String target) {
        var config = Flyway.configure().dataSource(source).schemas("business").defaultSchema("business")
                .table("tianji_worker_schema_history").baselineVersion("0").baselineOnMigrate(true)
                .locations("classpath:db/tianji-worker").cleanDisabled(true);
        if (target != null) config.target(target);
        return config.load();
    }
    private static WorkerBusinessResultDTO result(int sequence) {
        return new WorkerBusinessResultDTO("receipt-" + sequence, Map.of("approved", true, "sequence", BigInteger.valueOf(sequence)));
    }
    private static WorkerTaskDTO task(long tenant, String job, String key, int amount, WorkerEffectSafetyEnum safety, Instant now, int seed) {
        return taskWithTimes(tenant, job, key, amount, safety, now, now.plusSeconds(60), seed);
    }
    private static WorkerTaskDTO taskWithTimes(long tenant, String job, String key, int amount, WorkerEffectSafetyEnum safety,
                                                Instant acquired, Instant expires, int seed) {
        byte[] bytes = new byte[32]; bytes[0] = (byte) seed;
        return new WorkerTaskDTO(job, job, WorkerScopeTypeEnum.BPMN, "process-1", null, tenant, "remote", "1", safety,
                Map.of("amount", ProcessValueTypeEnum.INTEGER), Map.of("approved", ProcessValueTypeEnum.BOOLEAN, "sequence", ProcessValueTypeEnum.INTEGER),
                "order-1", "trace-1", key, acquired, expires, Map.of("amount", amount), Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
    private static Instant now(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant(); }
    private static int intentCount(JdbcTemplate jdbc, String source, long tenant, String key) {
        return jdbc.queryForObject("SELECT count(*) FROM business.tianji_worker_nontransactional_intent WHERE source_id=? AND tenant_id=? AND idempotency_key=?", Integer.class, source, tenant, key);
    }
    private static int receiptCount(JdbcTemplate jdbc, String source, long tenant, String key) {
        return jdbc.queryForObject("SELECT count(*) FROM business.tianji_worker_execution_receipt WHERE source_id=? AND tenant_id=? AND idempotency_key=?", Integer.class, source, tenant, key);
    }
}
