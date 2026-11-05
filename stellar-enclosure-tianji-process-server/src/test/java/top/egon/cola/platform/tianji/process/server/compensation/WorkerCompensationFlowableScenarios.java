package top.egon.cola.platform.tianji.process.server.compensation;

import jakarta.validation.Validation;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flywaydb.core.Flyway;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.platform.tianji.contract.orchestration.AcquireWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.CompleteWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.FailWorkerTaskCommand;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerContractDTO;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerEffectSafetyEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerFailureKindEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerReceiptOutcomeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerScopeTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.WorkerTaskDTO;
import top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.ExpectedOperationVO;
import top.egon.cola.platform.tianji.process.server.execution.ProcessExecutionService;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ProcessReleaseService;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.Type;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.WorkerEffect;
import top.egon.cola.platform.tianji.process.server.worker.FlowableWorkerService;
import top.egon.cola.platform.tianji.process.server.worker.WorkerLeaseStore;
import top.egon.cola.platform.tianji.process.server.worker.WorkerTopicRegistry;
import top.egon.cola.platform.tianji.worker.JdbcWorkerExecutionStore;
import top.egon.cola.platform.tianji.worker.WorkerExecutionConflictException;
import top.egon.cola.platform.tianji.worker.WorkerExecutionReceipt;
import top.egon.cola.platform.tianji.worker.WorkerHandlerDescriptor;

import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.RESERVE_TOPIC;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.SCHEMA;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.SCHEMA_VERSION;
import static top.egon.cola.platform.tianji.process.server.compensation.CompensationBusinessFixture.UNDO_TOPIC;

/** Future manual PG-701 checks invoked by the existing guarded Process Server integration test. */
public final class WorkerCompensationFlowableScenarios {
    private static final String BUSINESS_URL_PROPERTY = "tianji.worker.business.postgres.test.url";
    private static final String BUSINESS_ROLE_PROPERTY = "tianji.worker.business.postgres.test.runtime-user";
    private static final String PROCESS_URL_PROPERTY = "tianji.process.postgres.test.url";
    private static final String PROCESS_ROLE_PROPERTY = "tianji.process.postgres.test.runtime-user";
    private static final String LOOPBACK_URL = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/tianji_it_[a-z0-9_]{1,40}";
    private static final String SOURCE_ID = "integration-process";
    private static final long TENANT = 20;
    private static final String ISSUER = "https://idp.example/20";
    private static final String WORKER_ID = "worker-compensation-worker";
    private static final String DEFINITION = "workerCompensation";
    private static final String DOWNSTREAM_TOPIC = "downstreamRejectable";
    private static final String REJECTION = "RESERVATION_REJECTED";
    private static final String ORIGINAL_TABLE = SCHEMA + ".original_receipt";
    private static final String UNDO_TABLE = SCHEMA + ".compensation_receipt";
    private static final String OBJECT_TABLE = SCHEMA + ".business_object";
    private static final String TECHNICAL_TABLE = SCHEMA + ".tianji_worker_execution_receipt";
    private static final String FORCED_FAILURE_KEY = "compensation-forced-receipt-failure";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration WAIT_LIMIT = Duration.ofSeconds(15);
    private static final Duration RESERVE_LEASE = Duration.ofSeconds(15);
    private static final Duration LEASE_RESET_LIMIT = Duration.ofSeconds(22);

    private WorkerCompensationFlowableScenarios() { }

    public static void verifyIfConfigured(ConfigurableApplicationContext app, JdbcTemplate processBootstrap,
                                          String processRuntimeUser) throws Exception {
        String businessUrl = System.getProperty(BUSINESS_URL_PROPERTY);
        if (businessUrl == null) return;
        String businessUser = System.getProperty(BUSINESS_ROLE_PROPERTY, "");
        String processUrl = System.getProperty(PROCESS_URL_PROPERTY, "");
        if (!businessUrl.matches(LOOPBACK_URL) || !businessUser.matches("tianji_it_worker_[0-9a-f]{12}")
                || !processUrl.matches(LOOPBACK_URL) || processRuntimeUser == null
                || !processRuntimeUser.matches("tianji_it_runtime_[0-9a-f]{12}")
                || !processRuntimeUser.equals(System.getProperty(PROCESS_ROLE_PROPERTY))
                || !(processBootstrap.getDataSource() instanceof DriverManagerDataSource processSource)
                || !processUrl.equals(processSource.getUrl())
                || !processRuntimeUser.equals(app.getEnvironment().getProperty("spring.datasource.username"))
                || !(processUrl + "?currentSchema=tianji_process").equals(app.getEnvironment().getProperty("spring.datasource.url"))) {
            throw new IllegalArgumentException("isolated loopback Process and Business databases and temporary roles required");
        }

        var bootstrapSource = new DriverManagerDataSource(businessUrl);
        var business = new JdbcTemplate(bootstrapSource);
        business.setQueryTimeout(5);
        if (databaseIdentity(processBootstrap).equals(databaseIdentity(business))) {
            throw new IllegalArgumentException("the compensation fixture requires separate Process and Business databases");
        }
        requireLimitedBusinessRole(business, businessUser);
        if (business.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname=?", Integer.class, SCHEMA) != 0) {
            throw new IllegalStateException("compensation fixture schema already exists");
        }

        var process = app.getBean(SpringProcessEngineConfiguration.class);
        var dmn = app.getBean(SpringDmnEngineConfiguration.class);
        var validator = new ProcessModelValidator();
        var registry = new WorkerTopicRegistry(List.of(registration(RESERVE_TOPIC), registration(DOWNSTREAM_TOPIC), registration(UNDO_TOPIC)));
        var releases = new ProcessReleaseService(process, dmn, validator, false, registry);
        var runs = new ProcessExecutionService(process, dmn, validator);
        var workers = new FlowableWorkerService(process, dmn, validator, registry);
        var runtime = new DriverManagerDataSource(businessUrl + "?currentSchema=" + SCHEMA);
        runtime.setUsername(businessUser);
        try (var owned = OwnedSchema.create(bootstrapSource, business);
             var validators = Validation.buildDefaultValidatorFactory()) {
            provision(bootstrapSource, business, businessUser);
            var validation = new ValidationUtils(validators.getValidator());
            var store = newStore(runtime);
            verifyNativeLifecycle(app, processBootstrap, business, releases, runs, workers, runtime, store, validation);
            verifyConcurrentOriginal(business, runtime, store, validation);
            verifyTechnicalFailureRollback(business, store, validation);
            verifyObjectDrift(business, store, validation);
            verifyDurability(businessUrl, businessUser, business, validation);
        }
    }

    private static String databaseIdentity(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT inet_server_addr()::text || ':' || inet_server_port() || '/' || current_database()", String.class);
    }

    private static void requireLimitedBusinessRole(JdbcTemplate business, String user) {
        Boolean limited = business.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=? AND rolcanlogin AND NOT rolsuper
                    AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls)
                  AND NOT EXISTS (SELECT 1 FROM pg_auth_members m JOIN pg_roles r ON r.oid=m.member WHERE r.rolname=?)
                  AND NOT EXISTS (SELECT 1 FROM pg_database d JOIN pg_roles r ON r.oid=d.datdba
                      WHERE d.datname=current_database() AND r.rolname=?)
                  AND current_user <> ?
                """, Boolean.class, user, user, user, user);
        if (!Boolean.TRUE.equals(limited) || Boolean.TRUE.equals(business.queryForObject(
                "SELECT has_database_privilege(?, current_database(), 'CREATE')", Boolean.class, user))) {
            throw new IllegalArgumentException("a limited temporary Business database runtime role is required");
        }
    }

    private static void provision(DriverManagerDataSource bootstrapSource, JdbcTemplate business, String user) {
        CompensationBusinessFixture.createBusinessTables(business);
        Flyway flyway = Flyway.configure().dataSource(bootstrapSource).schemas(SCHEMA).defaultSchema(SCHEMA)
                .table("tianji_worker_schema_history").baselineVersion("0").baselineOnMigrate(true)
                .locations("classpath:db/tianji-worker").target("2").cleanDisabled(true).load();
        assertEquals(2, flyway.migrate().migrationsExecuted);
        flyway.validate();

        String role = '"' + user + '"';
        business.execute("GRANT USAGE ON SCHEMA " + SCHEMA + " TO " + role);
        business.execute("GRANT SELECT, UPDATE (version, reserved_amount) ON " + OBJECT_TABLE + " TO " + role);
        business.execute("GRANT SELECT, INSERT ON " + ORIGINAL_TABLE + ", " + UNDO_TABLE + ", " + TECHNICAL_TABLE + " TO " + role);
        // PostgreSQL requires one UPDATE column privilege for SELECT FOR UPDATE. The fixture trigger rejects UPDATE statements.
        business.execute("GRANT UPDATE (recorded_at) ON " + ORIGINAL_TABLE + " TO " + role);
        business.execute("GRANT SELECT ON " + SCHEMA + ".tianji_worker_nontransactional_intent, "
                + SCHEMA + ".tianji_worker_schema_history TO " + role);
        assertEquals(Boolean.FALSE, business.queryForObject(
                "SELECT has_schema_privilege(?, ?, 'CREATE')", Boolean.class, user, SCHEMA));
        assertEquals(Boolean.TRUE, business.queryForObject(
                "SELECT has_column_privilege(?, ?, 'recorded_at', 'UPDATE')", Boolean.class, user, ORIGINAL_TABLE));
        assertEquals("A", business.queryForObject("SELECT tgenabled::text FROM pg_trigger "
                        + "WHERE tgrelid=CAST(? AS regclass) AND tgname=? AND NOT tgisinternal",
                String.class, ORIGINAL_TABLE, "original_receipt_immutable_update"));
        assertEquals(1, business.queryForObject("""
                SELECT count(*) FROM pg_attribute a WHERE a.attrelid=CAST(? AS regclass) AND a.attnum>0 AND NOT a.attisdropped
                  AND has_column_privilege(?, a.attrelid, a.attnum, 'UPDATE')
                """, Integer.class, ORIGINAL_TABLE, user));
        for (String receiptTable : List.of(ORIGINAL_TABLE, UNDO_TABLE, TECHNICAL_TABLE)) {
            assertEquals(Boolean.FALSE, business.queryForObject(
                    "SELECT has_table_privilege(?, ?, 'UPDATE,DELETE,TRUNCATE,TRIGGER')", Boolean.class, user, receiptTable));
        }
        assertEquals(Boolean.FALSE, business.queryForObject("SELECT has_any_column_privilege(?, ?, 'UPDATE')",
                Boolean.class, user, UNDO_TABLE));
    }

    private static WorkerTopicRegistry.Registration registration(String topic) {
        return new WorkerTopicRegistry.Registration(TENANT, ISSUER, WORKER_ID, topic, SCHEMA_VERSION,
                Map.of("operationId", ValueType.STRING), Map.of(), Map.of("operationId", Set.of("operationId")),
                Map.of(), 1, RESERVE_TOPIC.equals(topic) ? RESERVE_LEASE : Duration.ofMinutes(2),
                Duration.ofMillis(200), WorkerEffect.TRANSACTIONAL_RECEIPT);
    }

    private static JdbcWorkerExecutionStore newStore(DriverManagerDataSource source) {
        return new JdbcWorkerExecutionStore(source, new DataSourceTransactionManager(source), SCHEMA, SOURCE_ID,
                TENANT, Duration.ofSeconds(20), Duration.ofSeconds(6));
    }

    private static void verifyNativeLifecycle(ConfigurableApplicationContext app, JdbcTemplate processDb, JdbcTemplate business,
                                              ProcessReleaseService releases, ProcessExecutionService runs, FlowableWorkerService workers,
                                              DriverManagerDataSource runtimeSource, JdbcWorkerExecutionStore store,
                                              ValidationUtils validation) throws Exception {
        var owner = ProcessActor.verified(TENANT, ProcessActor.SubjectType.USER, "worker-compensation-owner", ISSUER);
        var worker = ProcessActor.verified(TENANT, ProcessActor.SubjectType.SERVICE, WORKER_ID, ISSUER);
        byte[] xml = new ClassPathResource("fixtures/worker-compensation.bpmn20.xml").getContentAsByteArray();
        String release = "worker-compensation-" + UUID.randomUUID();
        releases.publish(owner, release, List.of(new ProcessReleaseService.ModelResource(
                Type.BPMN, DEFINITION, xml, Map.of("operationId", ValueType.STRING))));
        UUID operation = UUID.randomUUID();
        String businessKey = "compensation-" + operation;
        String correlation = "trace-" + operation;
        String instanceId = runs.start(owner, new ProcessExecutionService.StartCommand(release, DEFINITION, businessKey,
                businessKey, correlation, Map.of("operationId", operation.toString()))).instanceId();
        var runtime = app.getBean(RuntimeService.class);
        var management = app.getBean(ManagementService.class);
        var history = app.getBean(HistoryService.class);
        var expected = new ExpectedOperationVO(SOURCE_ID, TENANT, instanceId, businessKey, correlation, operation,
                "native-reservation", 10, 2, 3);
        var fixture = new CompensationBusinessFixture(expected, validation);
        try {
            seed(business, expected);
            var reserve = awaitWorker(workers, worker, RESERVE_TOPIC, instanceId);
            var original = store.execute(reserve, descriptor(RESERVE_TOPIC), fixture.originalHandler());
            assertFalse(original.replayed());
            assertEquals(Map.of(), original.result().output());
            assertObject(business, expected, 11, 5);
            assertBusinessReceipt(business, ORIGINAL_TABLE, "original_receipt_id", expected, original, 10, 11, 2, 5);
            assertTechnicalReceipt(business, reserve, original);
            verifyOriginalReceiptPrivileges(runtimeSource, expected);
            assertEquals("LEASED", leaseState(processDb, reserve));
            assertEquals(1, management.createExternalWorkerJobQuery().jobId(reserve.taskId()).count());
            assertEquals(0, subscriptions(runtime, instanceId));
            assertEquals(0, history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).activityId("reserve").finished().count());

            // Simulate loss before ProcessDB received completion, then let Flowable reset the expired lease.
            await().atMost(LEASE_RESET_LIMIT).pollInterval(POLL_INTERVAL).until(() ->
                    management.createExternalWorkerJobQuery().jobId(reserve.taskId()).unlocked().count() == 1);
            var retry = awaitWorker(workers, worker, RESERVE_TOPIC, instanceId);
            assertEquals("EXPIRED", leaseState(processDb, reserve));
            assertEquals(reserve.taskId(), retry.taskId());
            assertEquals(reserve.activityOccurrenceId(), retry.activityOccurrenceId());
            assertEquals(reserve.idempotencyKey(), retry.idempotencyKey());
            assertNotEquals(WorkerLeaseStore.tokenHash(reserve.leaseToken()), WorkerLeaseStore.tokenHash(retry.leaseToken()));
            var replay = store.execute(retry, descriptor(RESERVE_TOPIC), (context, jdbc) -> {
                throw new AssertionError("technical receipt replay reached the reserve handler");
            });
            assertTrue(replay.replayed());
            assertEquals(original.result(), replay.result());
            assertEquals(original.committedAt(), replay.committedAt());
            assertTechnicalReceipt(business, retry, replay);
            assertObject(business, expected, 11, 5);

            var command = completion(retry, replay);
            var accepted = workers.complete(worker, command);
            assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, accepted.outcome());
            assertEquals(retry.taskId(), accepted.taskId());
            assertEquals(retry.activityOccurrenceId(), accepted.activityOccurrenceId());
            assertEquals("COMPLETED", leaseState(processDb, retry));
            assertEquals(accepted, workers.complete(worker, command));
            await().atMost(WAIT_LIMIT).pollInterval(POLL_INTERVAL).until(() ->
                    history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).activityId("reserve").finished().count() == 1
                            && subscriptions(runtime, instanceId) == 1);
            var downstream = awaitWorker(workers, worker, DOWNSTREAM_TOPIC, instanceId);
            assertEquals("downstream", management.createExternalWorkerJobQuery().jobId(downstream.taskId()).singleResult().getElementId());
            var rejection = new FailWorkerTaskCommand(downstream.taskId(), downstream.activityOccurrenceId(), downstream.leaseToken(),
                    WorkerFailureKindEnum.BUSINESS_REJECTED, REJECTION);
            var rejected = workers.fail(worker, rejection);
            assertEquals(WorkerReceiptOutcomeEnum.BUSINESS_REJECTED, rejected.outcome());
            assertEquals(rejected, workers.fail(worker, rejection));
            assertEquals("BUSINESS_REJECTED", leaseState(processDb, downstream));
            assertEquals(REJECTION, processDb.queryForObject("SELECT failure_code FROM tianji_process.tianji_worker_lease_receipt "
                    + "WHERE tenant_id=? AND token_sha256=?", String.class, TENANT, WorkerLeaseStore.tokenHash(downstream.leaseToken())));

            var undo = awaitWorker(workers, worker, UNDO_TOPIC, instanceId);
            assertEquals("undoReserve", management.createExternalWorkerJobQuery().jobId(undo.taskId()).singleResult().getElementId());
            assertEquals(1, history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).activityId("throwUndo").count());
            assertEquals(0, businessReceiptCount(business, UNDO_TABLE, expected));
            assertEquals(0, technicalReceiptCount(business, undo));
            assertObject(business, expected, 11, 5);

            var compensated = store.execute(undo, descriptor(UNDO_TOPIC), fixture.undoHandler());
            assertFalse(compensated.replayed());
            assertEquals(Map.of(), compensated.result().output());
            assertObject(business, expected, 12, 2);
            assertBusinessReceipt(business, UNDO_TABLE, "compensation_receipt_id", expected, compensated, 11, 12, 5, 2);
            assertEquals(original.result().businessReceiptId(), business.queryForObject("SELECT original_receipt_id FROM " + UNDO_TABLE
                    + " WHERE source_id=? AND tenant_id=? AND operation_id=?", String.class, SOURCE_ID, TENANT, expected.operationId()));
            assertTechnicalReceipt(business, undo, compensated);
            assertEquals("LEASED", leaseState(processDb, undo));
            var undoAck = workers.complete(worker, completion(undo, compensated));
            assertEquals(WorkerReceiptOutcomeEnum.COMPLETED, undoAck.outcome());
            assertEquals("COMPLETED", leaseState(processDb, undo));

            // Native history and terminal state are observed independently from the business receipts.
            await().atMost(WAIT_LIMIT).pollInterval(POLL_INTERVAL).until(() ->
                    history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).activityId("compEnd").finished().count() == 1
                            && history.createHistoricProcessInstanceQuery().processInstanceId(instanceId).finished().count() == 1);
            assertEquals(ProcessExecutionService.State.COMPLETED, runs.get(owner, instanceId).state());
            assertEquals(0, history.createHistoricActivityInstanceQuery().processInstanceId(instanceId).activityId("normalEnd").count());

            var newReserve = localTask(business, expected, RESERVE_TOPIC, "native-reserve-replay");
            var originalAgain = store.execute(newReserve, descriptor(RESERVE_TOPIC), fixture.originalHandler());
            assertFalse(originalAgain.replayed());
            assertEquals(original.result(), originalAgain.result());
            assertTechnicalReceipt(business, newReserve, originalAgain);
            var newUndo = localTask(business, expected, UNDO_TOPIC, "native-undo-replay");
            var undoAgain = store.execute(newUndo, descriptor(UNDO_TOPIC), fixture.undoHandler());
            assertFalse(undoAgain.replayed());
            assertEquals(compensated.result(), undoAgain.result());
            assertTechnicalReceipt(business, newUndo, undoAgain);
            assertEquals(1, businessReceiptCount(business, ORIGINAL_TABLE, expected));
            assertEquals(1, businessReceiptCount(business, UNDO_TABLE, expected));
            assertObject(business, expected, 12, 2);
        } finally {
            if (runtime.createProcessInstanceQuery().processInstanceId(instanceId).processInstanceTenantId(Long.toString(TENANT)).count() == 1) {
                runtime.deleteProcessInstance(instanceId, "TEST_FIXTURE_CLEANUP");
            }
        }
    }

    private static long subscriptions(RuntimeService runtime, String instanceId) {
        return runtime.createEventSubscriptionQuery().eventType("compensate").activityId("reserve")
                .tenantId(Long.toString(TENANT)).processInstanceId(instanceId).count();
    }

    private static WorkerTaskDTO awaitWorker(FlowableWorkerService workers, ProcessActor worker, String topic, String instanceId) {
        var selected = new AtomicReference<WorkerTaskDTO>();
        var request = new AcquireWorkerTaskCommand(topic, SCHEMA_VERSION);
        await().atMost(WAIT_LIMIT).pollInterval(POLL_INTERVAL).until(() -> {
            var result = workers.acquire(worker, request);
            if (result.task() == null) return false;
            selected.set(result.task());
            return true;
        });
        var task = selected.get();
        assertEquals(instanceId, task.processInstanceId());
        return task;
    }

    private static CompleteWorkerTaskCommand completion(WorkerTaskDTO task, WorkerExecutionReceipt receipt) {
        return new CompleteWorkerTaskCommand(task.taskId(), task.activityOccurrenceId(), task.leaseToken(),
                receipt.result().businessReceiptId(), receipt.result().output());
    }

    private static String leaseState(JdbcTemplate jdbc, WorkerTaskDTO task) {
        return jdbc.queryForObject("SELECT state FROM tianji_process.tianji_worker_lease_receipt "
                + "WHERE tenant_id=? AND job_id=? AND token_sha256=?", String.class,
                TENANT, task.taskId(), WorkerLeaseStore.tokenHash(task.leaseToken()));
    }

    private static void verifyOriginalReceiptPrivileges(DriverManagerDataSource source, ExpectedOperationVO expected) {
        var runtime = new JdbcTemplate(source);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        String receipt = "SELECT recorded_at FROM " + ORIGINAL_TABLE + " WHERE source_id=? AND tenant_id=? AND operation_id=?";
        var locked = transaction.execute(status -> {
            status.setRollbackOnly();
            return runtime.queryForObject(receipt + " FOR UPDATE", OffsetDateTime.class, SOURCE_ID, TENANT, expected.operationId());
        });
        assertNotNull(locked);
        var failure = assertThrows(DataIntegrityViolationException.class, () -> transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            runtime.update("UPDATE " + ORIGINAL_TABLE + " SET recorded_at=recorded_at+interval '1 second' "
                    + "WHERE source_id=? AND tenant_id=? AND operation_id=?", SOURCE_ID, TENANT, expected.operationId());
        }));
        assertSqlState("23514", failure);
        var persisted = runtime.queryForObject(receipt, OffsetDateTime.class, SOURCE_ID, TENANT, expected.operationId());
        assertNotNull(persisted);
        assertEquals(locked.toInstant(), persisted.toInstant());
    }

    private static void verifyConcurrentOriginal(JdbcTemplate business, DriverManagerDataSource runtimeSource,
                                                 JdbcWorkerExecutionStore store, ValidationUtils validation) throws Exception {
        var expected = localOperation("concurrent");
        var fixture = new CompensationBusinessFixture(expected, validation);
        seed(business, expected);
        var first = localTask(business, expected, RESERVE_TOPIC, "concurrent-first");
        var second = localTask(business, expected, RESERVE_TOPIC, "concurrent-second");
        // A connection using the same runtime role can inspect the competing backend's active query.
        var observer = new JdbcTemplate(runtimeSource);
        observer.setQueryTimeout(5);
        var firstReady = new CompletableFuture<Integer>();
        var secondReady = new CompletableFuture<Integer>();
        var commitFirst = new CountDownLatch(1);
        var handler = fixture.originalHandler();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var firstResult = pool.submit(() -> store.execute(first, descriptor(RESERVE_TOPIC), (context, jdbc) -> {
                var result = handler.execute(context, jdbc);
                firstReady.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                awaitCommitSignal(commitFirst);
                return result;
            }));
            try {
                int firstBackend = firstReady.get(5, TimeUnit.SECONDS);
                var secondResult = pool.submit(() -> store.execute(second, descriptor(RESERVE_TOPIC), (context, jdbc) -> {
                    secondReady.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    return handler.execute(context, jdbc);
                }));
                int secondBackend = secondReady.get(5, TimeUnit.SECONDS);
                assertNotEquals(firstBackend, secondBackend);
                await().atMost(Duration.ofSeconds(5)).pollInterval(POLL_INTERVAL).until(() -> Boolean.TRUE.equals(observer.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM pg_stat_activity
                            WHERE pid=? AND wait_event_type='Lock' AND position('business_object' IN query)>0
                              AND ?=ANY(pg_blocking_pids(pid)))
                        """, Boolean.class, secondBackend, firstBackend)));
                assertEquals(0, businessReceiptCount(business, ORIGINAL_TABLE, expected));
                commitFirst.countDown();
                var original = firstResult.get(10, TimeUnit.SECONDS);
                var replay = secondResult.get(10, TimeUnit.SECONDS);
                assertFalse(original.replayed());
                assertFalse(replay.replayed());
                assertEquals(original.result(), replay.result());
                assertTechnicalReceipt(business, first, original);
                assertTechnicalReceipt(business, second, replay);
                assertEquals(1, businessReceiptCount(business, ORIGINAL_TABLE, expected));
                assertObject(business, expected, 11, 5);
            } finally {
                commitFirst.countDown();
            }
        }
    }

    private static void awaitCommitSignal(CountDownLatch commitFirst) {
        try {
            if (!commitFirst.await(12, TimeUnit.SECONDS)) throw new AssertionError("the original transaction was not released");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("the original transaction was interrupted", interrupted);
        }
    }

    private static void verifyTechnicalFailureRollback(JdbcTemplate business, JdbcWorkerExecutionStore store, ValidationUtils validation) {
        var expected = localOperation("rollback");
        var fixture = new CompensationBusinessFixture(expected, validation);
        seed(business, expected);
        var rejectedTask = localTask(business, expected, RESERVE_TOPIC, FORCED_FAILURE_KEY);
        business.execute("ALTER TABLE " + TECHNICAL_TABLE + " ADD CONSTRAINT force_test_receipt_failure "
                + "CHECK (idempotency_key <> '" + FORCED_FAILURE_KEY + "')");
        try {
            var handlerCompleted = new AtomicBoolean();
            var handler = fixture.originalHandler();
            var failure = assertThrows(DataIntegrityViolationException.class, () ->
                    store.execute(rejectedTask, descriptor(RESERVE_TOPIC), (context, jdbc) -> {
                        var result = handler.execute(context, jdbc);
                        handlerCompleted.set(true);
                        return result;
                    }));
            assertTrue(handlerCompleted.get());
            assertSqlState("23514", failure);
            assertObject(business, expected, 10, 2);
            assertEquals(0, businessReceiptCount(business, ORIGINAL_TABLE, expected));
            assertEquals(0, technicalReceiptCount(business, rejectedTask));
        } finally {
            business.execute("ALTER TABLE " + TECHNICAL_TABLE + " DROP CONSTRAINT force_test_receipt_failure");
        }
        var succeeded = store.execute(rejectedTask, descriptor(RESERVE_TOPIC), fixture.originalHandler());
        assertBusinessReceipt(business, ORIGINAL_TABLE, "original_receipt_id", expected, succeeded, 10, 11, 2, 5);
        assertTechnicalReceipt(business, rejectedTask, succeeded);
        assertObject(business, expected, 11, 5);
    }

    private static void verifyObjectDrift(JdbcTemplate business, JdbcWorkerExecutionStore store, ValidationUtils validation) {
        var expected = localOperation("drift");
        var fixture = new CompensationBusinessFixture(expected, validation);
        seed(business, expected);
        var original = localTask(business, expected, RESERVE_TOPIC, "drift-original");
        var reserved = store.execute(original, descriptor(RESERVE_TOPIC), fixture.originalHandler());
        assertTechnicalReceipt(business, original, reserved);
        assertEquals(1, business.update("UPDATE " + OBJECT_TABLE + " SET version=12,reserved_amount=8 "
                + "WHERE source_id=? AND tenant_id=? AND object_id=? AND version=11 AND reserved_amount=5",
                SOURCE_ID, TENANT, expected.objectId()));
        var undo = localTask(business, expected, UNDO_TOPIC, "drift-undo");
        assertThrows(WorkerExecutionConflictException.class, () -> store.execute(undo, descriptor(UNDO_TOPIC), fixture.undoHandler()));
        assertEquals(0, businessReceiptCount(business, UNDO_TABLE, expected));
        assertEquals(0, technicalReceiptCount(business, undo));
        assertObject(business, expected, 12, 8);
    }

    private static void verifyDurability(String businessUrl, String businessUser, JdbcTemplate business, ValidationUtils validation) {
        var expected = localOperation("durability");
        seed(business, expected);
        var fixture = new CompensationBusinessFixture(expected, validation);
        var firstSource = new DriverManagerDataSource(businessUrl + "?currentSchema=" + SCHEMA);
        firstSource.setUsername(businessUser);
        var firstStore = newStore(firstSource);
        var reserve = localTask(business, expected, RESERVE_TOPIC, "durable-reserve");
        var undo = localTask(business, expected, UNDO_TOPIC, "durable-undo");
        var original = firstStore.execute(reserve, descriptor(RESERVE_TOPIC), fixture.originalHandler());
        var compensated = firstStore.execute(undo, descriptor(UNDO_TOPIC), fixture.undoHandler());

        // Recreate the Worker-side objects and read the receipts through a fresh BusinessDB connection.
        var reopened = new DriverManagerDataSource(businessUrl + "?currentSchema=" + SCHEMA);
        reopened.setUsername(businessUser);
        var reader = new JdbcTemplate(reopened);
        var recovered = newStore(reopened);
        var originalReplay = recovered.execute(reserve, descriptor(RESERVE_TOPIC), (context, jdbc) -> {
            throw new AssertionError("persisted original technical receipt was bypassed");
        });
        var compensationReplay = recovered.execute(undo, descriptor(UNDO_TOPIC), (context, jdbc) -> {
            throw new AssertionError("persisted undo technical receipt was bypassed");
        });
        assertTrue(originalReplay.replayed());
        assertTrue(compensationReplay.replayed());
        assertEquals(original.result(), originalReplay.result());
        assertEquals(original.committedAt(), originalReplay.committedAt());
        assertEquals(compensated.result(), compensationReplay.result());
        assertEquals(compensated.committedAt(), compensationReplay.committedAt());
        assertBusinessReceipt(reader, ORIGINAL_TABLE, "original_receipt_id", expected, originalReplay, 10, 11, 2, 5);
        assertBusinessReceipt(reader, UNDO_TABLE, "compensation_receipt_id", expected, compensationReplay, 11, 12, 5, 2);
        assertTechnicalReceipt(reader, reserve, originalReplay);
        assertTechnicalReceipt(reader, undo, compensationReplay);
        assertObject(reader, expected, 12, 2);
    }

    private static void assertSqlState(String sqlState, DataIntegrityViolationException failure) {
        var sqlFailure = assertInstanceOf(SQLException.class, failure.getMostSpecificCause());
        assertEquals(sqlState, sqlFailure.getSQLState());
    }

    private static ExpectedOperationVO localOperation(String scenario) {
        return new ExpectedOperationVO(SOURCE_ID, TENANT, "local-process-" + scenario, "local-business-" + scenario,
                "local-trace-" + scenario, UUID.randomUUID(), "reservation-" + scenario, 10, 2, 3);
    }

    /** Local Store probes never issue Process Server commands. */
    private static WorkerTaskDTO localTask(JdbcTemplate business, ExpectedOperationVO expected, String topic, String key) {
        var databaseTime = business.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        assertNotNull(databaseTime);
        var now = databaseTime.toInstant();
        return new WorkerTaskDTO("local-task-" + key, "local-occurrence-" + key, WorkerScopeTypeEnum.BPMN,
                expected.processInstanceId(), null, TENANT, topic, SCHEMA_VERSION, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("operationId", ProcessValueTypeEnum.STRING), Map.of(), expected.businessKey(), expected.correlationId(),
                key, now, now.plusSeconds(120), Map.of("operationId", expected.operationId().toString()), "A".repeat(43));
    }

    private static WorkerHandlerDescriptor descriptor(String topic) {
        return new WorkerHandlerDescriptor(new WorkerContractDTO(topic, SCHEMA_VERSION, WorkerEffectSafetyEnum.TRANSACTIONAL_RECEIPT,
                Map.of("operationId", ProcessValueTypeEnum.STRING), Map.of()), "compensation-v1");
    }

    private static void seed(JdbcTemplate jdbc, ExpectedOperationVO expected) {
        assertEquals(1, jdbc.update("INSERT INTO " + OBJECT_TABLE + " (source_id,tenant_id,object_id,version,reserved_amount) VALUES (?,?,?,?,?)",
                SOURCE_ID, TENANT, expected.objectId(), expected.expectedVersion(), expected.expectedReserved()));
    }

    private static void assertObject(JdbcTemplate jdbc, ExpectedOperationVO expected, long version, long reserved) {
        assertEquals(List.of(version, reserved), jdbc.queryForObject("SELECT version,reserved_amount FROM " + OBJECT_TABLE
                        + " WHERE source_id=? AND tenant_id=? AND object_id=?", (row, index) -> List.of(row.getLong(1), row.getLong(2)),
                SOURCE_ID, TENANT, expected.objectId()));
    }

    private static int businessReceiptCount(JdbcTemplate jdbc, String table, ExpectedOperationVO expected) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE source_id=? AND tenant_id=? AND operation_id=?",
                Integer.class, SOURCE_ID, TENANT, expected.operationId());
    }

    private static void assertBusinessReceipt(JdbcTemplate jdbc, String table, String idColumn, ExpectedOperationVO expected,
                                              WorkerExecutionReceipt receipt, long beforeVersion, long afterVersion,
                                              long beforeReserved, long afterReserved) {
        assertEquals(1, businessReceiptCount(jdbc, table, expected));
        var storedId = jdbc.queryForObject("SELECT " + idColumn + ", recorded_at FROM " + table
                        + " WHERE source_id=? AND tenant_id=? AND operation_id=? AND process_instance_id=? AND business_key=? "
                        + "AND correlation_id=? AND object_id=? AND before_version=? AND after_version=? AND before_reserved=? AND after_reserved=?",
                (row, index) -> {
                    var recordedAt = row.getObject("recorded_at", OffsetDateTime.class).toInstant();
                    assertEquals(0, recordedAt.getNano() % 1_000);
                    return row.getString(idColumn);
                }, SOURCE_ID, TENANT, expected.operationId(), expected.processInstanceId(), expected.businessKey(), expected.correlationId(),
                expected.objectId(), beforeVersion, afterVersion, beforeReserved, afterReserved);
        assertEquals(receipt.result().businessReceiptId(), storedId);
    }

    private static int technicalReceiptCount(JdbcTemplate jdbc, WorkerTaskDTO task) {
        return jdbc.queryForObject("SELECT count(*) FROM " + TECHNICAL_TABLE + " WHERE source_id=? AND tenant_id=? AND idempotency_key=?",
                Integer.class, SOURCE_ID, TENANT, task.idempotencyKey());
    }

    private static void assertTechnicalReceipt(JdbcTemplate jdbc, WorkerTaskDTO task, WorkerExecutionReceipt receipt) {
        assertEquals(1, technicalReceiptCount(jdbc, task));
        assertEquals(receipt.result().businessReceiptId(), jdbc.queryForObject("SELECT business_receipt_id FROM " + TECHNICAL_TABLE
                        + " WHERE source_id=? AND tenant_id=? AND idempotency_key=? AND task_id=? AND occurrence_id=? AND topic=? "
                        + "AND schema_version=? AND output_json='{}'::jsonb",
                String.class, SOURCE_ID, TENANT, task.idempotencyKey(), task.taskId(), task.activityOccurrenceId(), task.topic(), SCHEMA_VERSION));
    }

    private record OwnedSchema(JdbcTemplate business, long oid) implements AutoCloseable {
        static OwnedSchema create(DriverManagerDataSource source, JdbcTemplate business) {
            long oid = new TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
                business.execute("CREATE SCHEMA " + SCHEMA);
                return business.queryForObject("SELECT oid::bigint FROM pg_namespace WHERE nspname=? "
                        + "AND nspowner=(SELECT oid FROM pg_roles WHERE rolname=current_user)", Long.class, SCHEMA);
            });
            return new OwnedSchema(business, oid);
        }

        @Override
        public void close() {
            if (!Boolean.TRUE.equals(business.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname=? "
                    + "AND oid::bigint=? AND nspowner=(SELECT oid FROM pg_roles WHERE rolname=current_user))",
                    Boolean.class, SCHEMA, oid))) {
                throw new IllegalStateException("compensation fixture schema ownership changed");
            }
            business.execute("DROP SCHEMA " + SCHEMA + " CASCADE");
        }
    }
}
