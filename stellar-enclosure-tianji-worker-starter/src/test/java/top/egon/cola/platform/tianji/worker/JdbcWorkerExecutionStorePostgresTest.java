package top.egon.cola.platform.tianji.worker;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerBusinessResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerContractDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against an explicitly supplied fresh loopback database and disposable role. */
@EnabledIfSystemProperty(named = "tianji.worker.business.postgres.test.url", matches = ".+")
@Timeout(90)
class JdbcWorkerExecutionStorePostgresTest {
    @Test
    void business_write_and_receipt_are_atomic_and_replayed_after_lost_acknowledgement() throws Exception {
        String url = System.getProperty("tianji.worker.business.postgres.test.url");
        String user = System.getProperty("tianji.worker.business.postgres.test.runtime-user", "");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}")
                || !user.matches("tianji_it_worker_[0-9a-f]{12}")) throw new IllegalArgumentException("fresh loopback database and temporary role required");
        var bootstrapSource = new DriverManagerDataSource(url);
        var bootstrap = new JdbcTemplate(bootstrapSource);
        if (bootstrap.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname='business'", Integer.class) != 0) {
            throw new IllegalStateException("test database already contains the business schema");
        }
        bootstrap.execute("CREATE SCHEMA business");
        bootstrap.execute("CREATE TABLE business.test_counter (tenant_id bigint PRIMARY KEY, value bigint NOT NULL)");
        bootstrap.update("INSERT INTO business.test_counter (tenant_id,value) VALUES (20,0),(21,0)");
        var absent = new WorkerExecutionSchemaVerifier(bootstrapSource, "business");
        assertThrows(IllegalStateException.class, absent::verify);
        Flyway migrations = Flyway.configure().dataSource(bootstrapSource).schemas("business").defaultSchema("business")
                .table("tianji_worker_schema_history").baselineVersion("0").baselineOnMigrate(true)
                .locations("classpath:db/tianji-worker").target("1").cleanDisabled(true).load();
        assertEquals(1, migrations.migrate().migrationsExecuted);
        migrations.validate();
        assertEquals(0L, counter(bootstrap, 20));
        bootstrap.execute("GRANT USAGE ON SCHEMA business TO \"" + user + "\"");
        bootstrap.execute("GRANT SELECT,INSERT,UPDATE ON business.tianji_worker_execution_receipt TO \"" + user + "\"");
        bootstrap.execute("GRANT SELECT,UPDATE ON business.test_counter TO \"" + user + "\"");
        bootstrap.execute("GRANT SELECT ON business.tianji_worker_schema_history TO \"" + user + "\"");
        assertEquals(Boolean.FALSE, bootstrap.queryForObject("SELECT has_schema_privilege(?, 'business', 'CREATE')", Boolean.class, user));
        var runtime = new DriverManagerDataSource(url + "?currentSchema=business");
        runtime.setUsername(user);
        var manager = new DataSourceTransactionManager(runtime);
        var verifier = new WorkerExecutionSchemaVerifier(runtime, "business");
        verifier.verify();
        assertThrows(IllegalStateException.class, verifier::verifyNonTransactional);
        assertEquals(0, bootstrap.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='business' "
                + "AND table_name='tianji_worker_execution_receipt' AND column_name LIKE '%token%'", Integer.class));
        verifySchemaDrift(bootstrap, verifier);
        var store = new JdbcWorkerExecutionStore(runtime, manager, "business", "process-east", 20,
                Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> new JdbcWorkerExecutionStore(runtime,
                new DataSourceTransactionManager(bootstrapSource), "business", "process-east", 20, Duration.ofSeconds(10), Duration.ofSeconds(1)));
        assertFalse(List.of(WorkerExecutionContext.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getName().equalsIgnoreCase("leaseToken")));

        Instant now = now(bootstrap);
        WorkerTaskDTO task = task(20, "job-1", "intent-1", 10, now, 1, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        var descriptorV1 = new WorkerHandlerDescriptor(task.contract(), "handler-v1");
        var descriptorV2 = new WorkerHandlerDescriptor(task.contract(), "handler-v2");
        AtomicInteger invocations = new AtomicInteger();
        TransactionalWorkerHandler handler = (context, jdbc) -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(java.util.Set.of("process-east", "process-west").contains(context.sourceId()));
            assertFalse(context.toString().contains("private-fixture"));
            invocations.incrementAndGet();
            jdbc.update("UPDATE business.test_counter SET value=value+? WHERE tenant_id=?", context.input().get("amount"), context.tenantId());
            return new WorkerBusinessResultDTO("business-receipt", Map.of("approved", true));
        };
        verifyConcurrentCommit(store, task, descriptorV1, handler, bootstrap, invocations);

        var nextLease = task(20, "job-1", "intent-1", 10, now(bootstrap), 2, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        var replay = store.execute(nextLease, descriptorV2, handler);
        assertTrue(replay.replayed());
        assertEquals("handler-v1", replay.handlerVersion());
        assertEquals(1, invocations.get());
        assertEquals(10L, counter(bootstrap, 20));
        var changed = task(20, "job-1", "intent-1", 11, now(bootstrap), 3, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        assertThrows(WorkerExecutionConflictException.class, () -> store.execute(changed, descriptorV2, handler));
        assertEquals(1, invocations.get());
        assertThrows(IllegalArgumentException.class, () -> store.execute(task(21, "job-1", "intent-1", 10, now(bootstrap), 4,
                WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT), descriptorV1, handler));
        assertThrows(IllegalArgumentException.class, () -> store.execute(task(20, "job-1", "intent-1", 10, now(bootstrap), 5,
                WorkerEffectSafetyEnum.EXTERNAL_IDEMPOTENT), descriptorV1, handler));
        assertThrows(IllegalArgumentException.class, () -> store.execute(task(20, "job-1", "intent-1", 10, now(bootstrap), 5,
                WorkerEffectSafetyEnum.READ_ONLY), descriptorV1, handler));
        var wrongContract = new WorkerHandlerDescriptor(new WorkerContractDTO(
                "riskCheck", "1", WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER, "description", ProcessValueTypeEnum.STRING),
                Map.of("approved", ProcessValueTypeEnum.STRING)), "handler-v1");
        assertThrows(IllegalArgumentException.class, () -> store.execute(task, wrongContract, handler));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(manager).execute(status -> store.execute(task, descriptorV1, handler)));

        var west = new JdbcWorkerExecutionStore(runtime, manager, "business", "process-west", 20, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertFalse(west.execute(nextLease, descriptorV1, handler).replayed());
        assertEquals(20L, counter(bootstrap, 20));
        var tenant21 = new JdbcWorkerExecutionStore(runtime, manager, "business", "process-east", 21, Duration.ofSeconds(10), Duration.ofSeconds(1));
        assertFalse(tenant21.execute(task(21, "job-1", "intent-1", 10, now(bootstrap), 6,
                WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT), descriptorV1, handler).replayed());
        assertEquals(10L, counter(bootstrap, 21));

        verifyRollbackAndDeadline(store, descriptorV1, handler, bootstrap);

        bootstrap.update("UPDATE business.tianji_worker_execution_receipt SET output_json='{}'::jsonb "
                + "WHERE source_id='process-east' AND tenant_id=20 AND idempotency_key='intent-1'");
        assertThrows(WorkerExecutionConflictException.class, () -> store.execute(nextLease, descriptorV2, handler));
        assertEquals(20L, counter(bootstrap, 20));
    }

    private static void verifyConcurrentCommit(JdbcWorkerExecutionStore store, WorkerTaskDTO task,
                                               WorkerHandlerDescriptor descriptorV1, TransactionalWorkerHandler handler,
                                               JdbcTemplate bootstrap, AtomicInteger invocations) throws Exception {
        var start = new CountDownLatch(1);
        List<WorkerExecutionReceipt> results;
        try (var pool = Executors.newFixedThreadPool(8)) {
            List<Future<WorkerExecutionReceipt>> futures = java.util.stream.IntStream.range(0, 20).mapToObj(index -> pool.submit(() -> {
                start.await();
                return store.execute(task, descriptorV1, handler);
            })).toList();
            start.countDown();
            results = new java.util.ArrayList<>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, invocations.get());
        assertEquals(10L, counter(bootstrap, 20));
        assertEquals(1, results.stream().filter(result -> !result.replayed()).count());
        assertTrue(results.stream().allMatch(result -> result.result().equals(results.getFirst().result())));
        assertEquals(1, bootstrap.queryForObject("SELECT count(*) FROM business.tianji_worker_execution_receipt WHERE tenant_id=20", Integer.class));
    }

    private static void verifyRollbackAndDeadline(JdbcWorkerExecutionStore store, WorkerHandlerDescriptor descriptorV1,
                                                  TransactionalWorkerHandler handler, JdbcTemplate bootstrap) {
        var failed = task(20, "job-fail", "intent-fail", 10, now(bootstrap), 7, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        assertThrows(InjectedFailure.class, () -> store.execute(failed, descriptorV1, (context, jdbc) -> {
            jdbc.update("UPDATE business.test_counter SET value=value+10 WHERE tenant_id=20");
            throw new InjectedFailure();
        }));
        assertEquals(20L, counter(bootstrap, 20));
        assertEquals(0, receiptCount(bootstrap, "intent-fail"));
        var wrongOutput = task(20, "job-output", "intent-output", 10, now(bootstrap), 11, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        assertThrows(IllegalArgumentException.class, () -> store.execute(wrongOutput, descriptorV1, (context, jdbc) -> {
            jdbc.update("UPDATE business.test_counter SET value=value+10 WHERE tenant_id=20");
            return new WorkerBusinessResultDTO("bad-output", Map.of("approved", "not-a-boolean"));
        }));
        assertEquals(20L, counter(bootstrap, 20));
        assertEquals(0, receiptCount(bootstrap, "intent-output"));

        bootstrap.execute("""
                CREATE FUNCTION business.test_receipt_failure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.business_receipt_id='injected-failure' THEN RAISE EXCEPTION 'receipt failure'; END IF;
                  RETURN NEW;
                END $$
                """);
        bootstrap.execute("CREATE TRIGGER test_receipt_failure BEFORE INSERT ON business.tianji_worker_execution_receipt "
                + "FOR EACH ROW EXECUTE FUNCTION business.test_receipt_failure()");
        var failedReceipt = task(20, "job-receipt", "intent-receipt", 10, now(bootstrap), 8, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT);
        assertThrows(DataAccessException.class, () -> store.execute(failedReceipt, descriptorV1, (context, jdbc) -> {
            jdbc.update("UPDATE business.test_counter SET value=value+10 WHERE tenant_id=20");
            return new WorkerBusinessResultDTO("injected-failure", Map.of("approved", true));
        }));
        assertEquals(20L, counter(bootstrap, 20));
        assertEquals(0, receiptCount(bootstrap, "intent-receipt"));
        bootstrap.execute("DROP TRIGGER test_receipt_failure ON business.tianji_worker_execution_receipt");

        var expired = taskWithTimes(20, "job-expired", "intent-expired", 10, now(bootstrap).minusSeconds(10), now(bootstrap).minusSeconds(1), 9);
        assertThrows(WorkerExecutionDeadlineException.class, () -> store.execute(expired, descriptorV1, handler));
        assertEquals(20L, counter(bootstrap, 20));
        var slow = taskWithTimes(20, "job-slow", "intent-slow", 10, now(bootstrap), now(bootstrap).plusMillis(2200), 10);
        assertThrows(WorkerExecutionDeadlineException.class, () -> store.execute(slow, descriptorV1, (context, jdbc) -> {
            jdbc.update("UPDATE business.test_counter SET value=value+10 WHERE tenant_id=20");
            jdbc.queryForObject("SELECT pg_sleep(1.5)", Object.class);
            return new WorkerBusinessResultDTO("slow-receipt", Map.of("approved", true));
        }));
        assertEquals(20L, counter(bootstrap, 20));
        assertEquals(0, receiptCount(bootstrap, "intent-slow"));
    }

    private static void verifySchemaDrift(JdbcTemplate bootstrap, WorkerExecutionSchemaVerifier verifier) {
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(bootstrap.getDataSource()));
        transaction.executeWithoutResult(status -> {
            bootstrap.update("UPDATE business.tianji_worker_schema_history SET checksum=0 WHERE version='1'");
            var sameTransactionVerifier = new WorkerExecutionSchemaVerifier(bootstrap.getDataSource(), "business");
            assertThrows(IllegalStateException.class, sameTransactionVerifier::verify);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            bootstrap.execute("ALTER TABLE business.tianji_worker_execution_receipt DROP CONSTRAINT tianji_worker_execution_receipt_pkey");
            var sameTransactionVerifier = new WorkerExecutionSchemaVerifier(bootstrap.getDataSource(), "business");
            assertThrows(IllegalStateException.class, sameTransactionVerifier::verify);
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status -> {
            bootstrap.execute("ALTER TABLE business.tianji_worker_execution_receipt DROP CONSTRAINT tianji_worker_execution_receipt_output_json_check");
            var sameTransactionVerifier = new WorkerExecutionSchemaVerifier(bootstrap.getDataSource(), "business");
            assertThrows(IllegalStateException.class, sameTransactionVerifier::verify);
            status.setRollbackOnly();
        });
        verifier.verify();
    }

    private static WorkerTaskDTO task(long tenant, String job, String key, int amount, Instant now, int seed, WorkerEffectSafetyEnum safety) {
        return new WorkerTaskDTO(job, job, WorkerScopeTypeEnum.BPMN, "process-1", null, tenant, "riskCheck", "1", safety,
                Map.of("amount", ProcessValueTypeEnum.INTEGER, "description", ProcessValueTypeEnum.STRING), Map.of("approved", ProcessValueTypeEnum.BOOLEAN),
                "order-1", "trace-1", key, now, now.plusSeconds(60), Map.of("amount", amount, "description", "private-fixture"), token(seed));
    }
    private static WorkerTaskDTO taskWithTimes(long tenant, String job, String key, int amount, Instant acquired, Instant expires, int seed) {
        return new WorkerTaskDTO(job, job, WorkerScopeTypeEnum.BPMN, "process-1", null, tenant, "riskCheck", "1",
                WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("amount", ProcessValueTypeEnum.INTEGER, "description", ProcessValueTypeEnum.STRING), Map.of("approved", ProcessValueTypeEnum.BOOLEAN),
                "order-1", "trace-1", key, acquired, expires,
                Map.of("amount", amount, "description", "private-fixture"), token(seed));
    }
    private static String token(int seed) { byte[] bytes = new byte[32]; bytes[0] = (byte) seed; return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static Instant now(JdbcTemplate jdbc) { return jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant(); }
    private static long counter(JdbcTemplate jdbc, long tenant) { return jdbc.queryForObject("SELECT value FROM business.test_counter WHERE tenant_id=?", Long.class, tenant); }
    private static int receiptCount(JdbcTemplate jdbc, String key) {
        return jdbc.queryForObject("SELECT count(*) FROM business.tianji_worker_execution_receipt WHERE source_id='process-east' AND tenant_id=20 AND idempotency_key=?", Integer.class, key);
    }
    private static final class InjectedFailure extends RuntimeException { }
}
